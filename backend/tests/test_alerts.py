import os
import unittest
from datetime import datetime, timedelta, timezone
from unittest import mock

from app.alerts import AlertMonitor, AlertThresholds, evaluate_readings
from app.sht31 import read_all_sensors

NOW = datetime(2026, 11, 13, 12, 0, tzinfo=timezone.utc)


def reading(temperature: float, humidity: float, sensor_id: str = "sensor_1") -> dict:
    return {
        "id": sensor_id,
        "temperature_c": temperature,
        "humidity_pct": humidity,
    }


class AlertThresholdsTests(unittest.TestCase):
    def test_parses_valid_values_and_ignores_invalid_ones(self) -> None:
        thresholds = AlertThresholds.from_mapping(
            {
                "enabled": True,
                "temperature_min_c": 20,
                "temperature_max_c": "not a number",
                "humidity_min_pct": None,
                "humidity_max_pct": 70.5,
            }
        )

        self.assertTrue(thresholds.enabled)
        self.assertEqual(thresholds.temperature_min_c, 20.0)
        self.assertIsNone(thresholds.temperature_max_c)
        self.assertIsNone(thresholds.humidity_min_pct)
        self.assertEqual(thresholds.humidity_max_pct, 70.5)

    def test_missing_document_means_no_thresholds(self) -> None:
        thresholds = AlertThresholds.from_mapping(None)

        self.assertTrue(thresholds.enabled)
        self.assertIsNone(thresholds.temperature_min_c)
        self.assertIsNone(thresholds.temperature_max_c)
        self.assertIsNone(thresholds.humidity_min_pct)
        self.assertIsNone(thresholds.humidity_max_pct)

    def test_false_enabled_flag(self) -> None:
        thresholds = AlertThresholds.from_mapping({"enabled": "false"})

        self.assertFalse(thresholds.enabled)


class EvaluateReadingsTests(unittest.TestCase):
    def test_publishes_below_and_above_alerts(self) -> None:
        thresholds = AlertThresholds(
            temperature_min_c=20.0,
            humidity_max_pct=60.0,
        )

        alerts = evaluate_readings([reading(19.5, 70.0)], thresholds, NOW)

        self.assertEqual(len(alerts), 2)
        by_type = {alert.alert_type: alert for alert in alerts}

        temperature = by_type["temperature"]
        self.assertEqual(temperature.sensor_id, "sensor_1")
        self.assertEqual(temperature.condition, "below")
        self.assertEqual(temperature.value, 19.5)
        self.assertEqual(temperature.threshold, 20.0)
        self.assertEqual(temperature.unit, "°C")
        self.assertEqual(temperature.timestamp, NOW)

        humidity = by_type["humidity"]
        self.assertEqual(humidity.condition, "above")
        self.assertEqual(humidity.value, 70.0)
        self.assertEqual(humidity.threshold, 60.0)
        self.assertEqual(humidity.unit, "%")

    def test_exactly_at_threshold_does_not_alert(self) -> None:
        thresholds = AlertThresholds(
            temperature_min_c=20.0,
            temperature_max_c=30.0,
            humidity_min_pct=40.0,
            humidity_max_pct=60.0,
        )

        alerts = evaluate_readings([reading(20.0, 60.0)], thresholds, NOW)

        self.assertEqual(alerts, [])

    def test_disabled_thresholds_produce_no_alerts(self) -> None:
        thresholds = AlertThresholds(temperature_min_c=20.0, enabled=False)

        alerts = evaluate_readings([reading(10.0, 50.0)], thresholds, NOW)

        self.assertEqual(alerts, [])

    def test_alert_document_matches_firestore_shape(self) -> None:
        thresholds = AlertThresholds(temperature_min_c=20.0)

        alert = evaluate_readings([reading(19.5, 50.0)], thresholds, NOW)[0]
        document = alert.to_document()

        self.assertEqual(document["sensor_id"], "sensor_1")
        self.assertEqual(document["type"], "temperature")
        self.assertEqual(document["condition"], "below")
        self.assertEqual(document["value"], 19.5)
        self.assertEqual(document["threshold"], 20.0)
        self.assertEqual(document["unit"], "°C")
        self.assertIn("below the configured minimum", document["message"])
        self.assertEqual(document["timestamp"], NOW)


class FakeAlertModeTests(unittest.TestCase):
    def test_fake_alert_readings_cross_default_thresholds(self) -> None:
        with mock.patch.dict(
            os.environ, {"SENSOR_MODE": "fake_alert"}, clear=True
        ):
            readings = read_all_sensors()

        self.assertEqual(readings[0]["temperature_c"], 15.0)
        self.assertEqual(readings[0]["humidity_pct"], 85.0)

        thresholds = AlertThresholds(
            temperature_min_c=20.0,
            humidity_max_pct=70.0,
        )
        alerts = evaluate_readings(readings, thresholds, NOW)

        self.assertEqual(
            {alert.alert_type for alert in alerts},
            {"temperature", "humidity"},
        )

    def test_fake_alert_values_can_be_overridden(self) -> None:
        with mock.patch.dict(
            os.environ,
            {
                "SENSOR_MODE": "fake_alert",
                "FAKE_ALERT_TEMPERATURE_C": "35",
                "FAKE_ALERT_HUMIDITY_PCT": "20",
            },
            clear=True,
        ):
            readings = read_all_sensors()

        self.assertEqual(readings[0]["temperature_c"], 35.0)
        self.assertEqual(readings[0]["humidity_pct"], 20.0)

        thresholds = AlertThresholds(
            temperature_max_c=30.0,
            humidity_min_pct=40.0,
        )
        alerts = evaluate_readings(readings, thresholds, NOW)

        self.assertEqual(
            {alert.alert_type for alert in alerts},
            {"temperature", "humidity"},
        )

    def test_invalid_fake_alert_override_uses_default(self) -> None:
        with mock.patch.dict(
            os.environ,
            {
                "SENSOR_MODE": "fake_alert",
                "FAKE_ALERT_TEMPERATURE_C": "not a number",
            },
            clear=True,
        ):
            readings = read_all_sensors()

        self.assertEqual(readings[0]["temperature_c"], 15.0)


class AlertMonitorTests(unittest.TestCase):
    def setUp(self) -> None:
        self.monitor = AlertMonitor(repeat_after_s=300)
        self.thresholds = AlertThresholds(temperature_min_c=20.0)

    def publish(self, at: datetime, value: float) -> list:
        alerts, active = self.monitor.evaluate(
            [reading(value, 50.0)], self.thresholds, at
        )
        self.monitor.mark_published(active, alerts, at)
        return alerts

    def test_does_not_repeat_until_repeat_interval(self) -> None:
        self.assertEqual(len(self.publish(NOW, 19.0)), 1)
        self.assertEqual(len(self.publish(NOW + timedelta(seconds=60), 19.0)), 0)
        self.assertEqual(len(self.publish(NOW + timedelta(seconds=300), 19.0)), 1)

    def test_rearms_after_condition_clears(self) -> None:
        self.assertEqual(len(self.publish(NOW, 19.0)), 1)
        self.assertEqual(len(self.publish(NOW + timedelta(seconds=10), 22.0)), 0)
        self.assertEqual(len(self.publish(NOW + timedelta(seconds=20), 19.0)), 1)

    def test_failed_upload_does_not_commit_monitor_state(self) -> None:
        alerts, active = self.monitor.evaluate(
            [reading(19.0, 50.0)], self.thresholds, NOW
        )
        self.assertEqual(len(alerts), 1)

        # Simulate a failed Firestore write: mark_published is not called.
        retry_alerts, retry_active = self.monitor.evaluate(
            [reading(19.0, 50.0)], self.thresholds, NOW + timedelta(seconds=2)
        )

        self.assertEqual(len(retry_alerts), 1)
        self.assertEqual(retry_active, active)


if __name__ == "__main__":
    unittest.main()
