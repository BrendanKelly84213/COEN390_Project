import unittest
from datetime import datetime, timezone

from app.alerts import AlertThresholds, evaluate_readings
from app.firebase_store import FirebaseStore

NOW = datetime(2026, 11, 13, 12, 0, tzinfo=timezone.utc)


class FakeSnapshot:
    def __init__(self, data):
        self._data = data
        self.exists = data is not None

    def to_dict(self):
        return dict(self._data) if self._data is not None else None


class FakeDocument:
    def __init__(self, documents, path):
        self._documents = documents
        self._path = path

    def get(self):
        return FakeSnapshot(self._documents.get(self._path))

    def set(self, data, merge=False):
        if merge:
            self._documents.setdefault(self._path, {}).update(data)
        else:
            self._documents[self._path] = dict(data)

    def collection(self, name):
        return FakeCollection(self._documents, f"{self._path}/{name}")


class FakeCollection:
    def __init__(self, documents, path):
        self._documents = documents
        self._path = path

    def document(self, document_id=None):
        if document_id is None:
            document_id = f"auto_{len(self._documents)}"
        return FakeDocument(self._documents, f"{self._path}/{document_id}")


class FakeBatch:
    def __init__(self, documents):
        self._documents = documents
        self._operations = []

    def set(self, reference, data, merge=False):
        self._operations.append((reference, data, merge))

    def commit(self):
        for reference, data, merge in self._operations:
            reference.set(data, merge=merge)


class FakeClient:
    def __init__(self):
        self.documents = {}
        self.batches = 0

    def collection(self, name):
        return FakeCollection(self.documents, name)

    def batch(self):
        self.batches += 1
        return FakeBatch(self.documents)


class FakeNotification:
    def __init__(self, title=None, body=None):
        self.title = title
        self.body = body


class FakeAndroidNotification:
    def __init__(self, **kwargs):
        self.kwargs = kwargs


class FakeAndroidConfig:
    def __init__(self, **kwargs):
        self.kwargs = kwargs


class FakeMessage:
    def __init__(self, **kwargs):
        self.kwargs = kwargs


class FakeMessaging:
    Notification = FakeNotification
    AndroidNotification = FakeAndroidNotification
    AndroidConfig = FakeAndroidConfig
    Message = FakeMessage

    def __init__(self):
        self.sent = []

    def send(self, message, app=None):
        self.sent.append((message, app))


class FirebaseStoreAlertTests(unittest.TestCase):
    def setUp(self) -> None:
        self.client = FakeClient()
        self.messaging = FakeMessaging()
        self.store = FirebaseStore(
            self.client,
            messaging_module=self.messaging,
            alerts_collection="alerts",
            alert_thresholds_document="settings/alerts",
            max_attempts=1,
            retry_backoff_s=0,
        )

    def test_reads_thresholds_and_caches_until_forced(self) -> None:
        self.client.documents["settings/alerts"] = {"temperature_min_c": 20}

        thresholds = self.store.get_alert_thresholds()
        self.assertEqual(thresholds.temperature_min_c, 20.0)

        self.client.documents["settings/alerts"]["temperature_min_c"] = 18
        cached = self.store.get_alert_thresholds()
        self.assertEqual(cached.temperature_min_c, 20.0)

        refreshed = self.store.get_alert_thresholds(force=True)
        self.assertEqual(refreshed.temperature_min_c, 18.0)

    def test_missing_threshold_document_means_no_checks(self) -> None:
        thresholds = self.store.get_alert_thresholds(force=True)

        self.assertIsNotNone(thresholds)
        self.assertIsNone(thresholds.temperature_min_c)
        self.assertIsNone(thresholds.temperature_max_c)

    def test_uploads_alerts_in_same_batch_as_readings(self) -> None:
        thresholds = AlertThresholds(temperature_min_c=20.0)
        alerts = evaluate_readings(
            [
                {
                    "id": "sensor_1",
                    "bus_num": 1,
                    "address": "0x44",
                    "temperature_c": 19.5,
                    "humidity_pct": 50.0,
                }
            ],
            thresholds,
            NOW,
        )

        uploaded = self.store.upload_measurements(
            [
                {
                    "id": "sensor_1",
                    "bus_num": 1,
                    "address": "0x44",
                    "temperature_c": 19.5,
                    "humidity_pct": 50.0,
                }
            ],
            NOW,
            alerts,
        )

        self.assertTrue(uploaded)
        self.assertEqual(self.client.batches, 1)

        alert_documents = [
            data
            for path, data in self.client.documents.items()
            if path.startswith("alerts/")
        ]
        self.assertEqual(len(alert_documents), 1)
        alert = alert_documents[0]
        self.assertEqual(alert["sensor_id"], "sensor_1")
        self.assertEqual(alert["type"], "temperature")
        self.assertEqual(alert["condition"], "below")
        self.assertEqual(alert["value"], 19.5)
        self.assertEqual(alert["threshold"], 20.0)

        self.assertIn("sensors/sensor_1", self.client.documents)
        self.assertTrue(
            any(
                path.startswith("sensors/sensor_1/measurements/")
                for path in self.client.documents
            )
        )

    def test_sends_push_notification_to_alert_topic(self) -> None:
        alerts = evaluate_readings(
            [
                {
                    "id": "sensor_1",
                    "bus_num": 1,
                    "address": "0x44",
                    "temperature_c": 19.5,
                    "humidity_pct": 50.0,
                }
            ],
            AlertThresholds(temperature_min_c=20.0),
            NOW,
        )

        sent = self.store.send_alert_notifications(alerts)

        self.assertEqual(sent, 1)
        message, app = self.messaging.sent[0]
        self.assertIsNone(app)
        self.assertEqual(message.kwargs["topic"], "alerts")
        self.assertEqual(
            message.kwargs["notification"].title,
            "Low temperature alert",
        )
        self.assertEqual(
            message.kwargs["notification"].body,
            "Temperature 19.50 °C is below the configured minimum of 20.00 °C",
        )
        self.assertEqual(message.kwargs["data"]["type"], "temperature")
        self.assertEqual(message.kwargs["data"]["value"], "19.50")
        self.assertEqual(
            message.kwargs["android"].kwargs["notification"].kwargs["channel_id"],
            "alerts",
        )


if __name__ == "__main__":
    unittest.main()
