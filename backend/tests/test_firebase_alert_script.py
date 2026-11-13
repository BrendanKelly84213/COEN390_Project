import unittest

from app.alerts import AlertThresholds
from scripts.test_firebase_alerts import (
    _has_thresholds,
    _synthetic_reading,
    _violating_value,
)


class SyntheticReadingTests(unittest.TestCase):
    def test_has_thresholds_detects_configured_values(self) -> None:
        self.assertFalse(_has_thresholds(AlertThresholds()))
        self.assertTrue(_has_thresholds(AlertThresholds(temperature_min_c=20.0)))

    def test_violating_value_prefers_minimum_then_maximum(self) -> None:
        self.assertEqual(_violating_value(20.0, 30.0, 15.0)[0], 19.0)
        self.assertEqual(_violating_value(None, 30.0, 15.0)[0], 31.0)
        self.assertEqual(_violating_value(None, None, 15.0)[0], 15.0)

    def test_synthetic_reading_violates_configured_limits(self) -> None:
        thresholds = AlertThresholds(
            temperature_min_c=20.0,
            humidity_max_pct=70.0,
        )

        reading, _ = _synthetic_reading(thresholds, "sensor_test", None, None)

        self.assertLess(reading["temperature_c"], 20.0)
        self.assertGreater(reading["humidity_pct"], 70.0)
        self.assertEqual(reading["id"], "sensor_test")

    def test_synthetic_reading_honours_overrides(self) -> None:
        thresholds = AlertThresholds(temperature_min_c=20.0)

        reading, _ = _synthetic_reading(thresholds, "sensor_1", 35.0, 20.0)

        self.assertEqual(reading["temperature_c"], 35.0)
        self.assertEqual(reading["humidity_pct"], 20.0)


if __name__ == "__main__":
    unittest.main()
