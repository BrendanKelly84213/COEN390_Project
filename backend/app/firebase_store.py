"""Firebase Firestore uploads for sensor measurements.

The backend runs fine without Firebase. Cloud upload is enabled only when
credentials are configured; upload failures are logged and never stop the
local sensor API.
"""

from __future__ import annotations

import logging
import os
import time
from datetime import datetime, timezone
from typing import Any

from .alerts import Alert, AlertThresholds

log = logging.getLogger("firebase")

_DISABLED_VALUES = {"0", "false", "no", "off"}


def _env_bool(name: str, default: bool) -> bool:
    raw = os.getenv(name)
    if raw is None or raw.strip() == "":
        return default
    return raw.strip().lower() not in _DISABLED_VALUES


def _env_int(name: str, default: int, minimum: int = 1) -> int:
    raw = os.getenv(name)
    if raw is None or raw.strip() == "":
        return default
    try:
        value = int(raw)
    except ValueError:
        log.warning("Invalid %s=%r; using %d", name, raw, default)
        return default
    return max(minimum, value)


def _env_float(name: str, default: float) -> float:
    raw = os.getenv(name)
    if raw is None or raw.strip() == "":
        return default
    try:
        return max(0.0, float(raw))
    except ValueError:
        log.warning("Invalid %s=%r; using %.1f", name, raw, default)
        return default


def _as_utc(value: datetime) -> datetime:
    if value.tzinfo is None:
        return value.replace(tzinfo=timezone.utc)
    return value.astimezone(timezone.utc)


class FirebaseStore:
    """Writes sensor readings and alerts to Firestore.

    Firestore layout::

        sensors/{sensor_id}                     # latest reading
        sensors/{sensor_id}/measurements/{id}   # historical readings
        alerts/{alert_id}                       # threshold violations
        settings/alerts                         # user-defined thresholds
    """

    def __init__(
        self,
        client: Any | None,
        firestore_module: Any | None = None,
        *,
        sensors_collection: str = "sensors",
        measurements_subcollection: str = "measurements",
        alerts_collection: str = "alerts",
        alert_thresholds_document: str = "settings/alerts",
        alert_thresholds_refresh_s: float = 60.0,
        max_attempts: int = 3,
        retry_backoff_s: float = 0.5,
    ) -> None:
        self._client = client
        self._sensors_collection = sensors_collection
        self._measurements_subcollection = measurements_subcollection
        self._alerts_collection = alerts_collection
        self._alert_thresholds_document = alert_thresholds_document
        self._alert_thresholds_refresh_s = max(0.0, alert_thresholds_refresh_s)
        self._alert_thresholds: AlertThresholds | None = None
        self._alert_thresholds_read_at = 0.0
        self._max_attempts = max(1, max_attempts)
        self._retry_backoff_s = max(0.0, retry_backoff_s)
        self._server_timestamp = (
            firestore_module.SERVER_TIMESTAMP if firestore_module is not None else None
        )

    @property
    def enabled(self) -> bool:
        return self._client is not None

    @property
    def alerts_collection(self) -> str:
        return self._alerts_collection

    @property
    def alert_thresholds_document(self) -> str:
        return self._alert_thresholds_document

    @classmethod
    def from_env(cls) -> "FirebaseStore":
        """Build a store from environment variables.

        If Firebase is disabled or misconfigured, returns a disabled store so
        the local API keeps working.
        """
        sensors_collection = (
            os.getenv("FIREBASE_SENSORS_COLLECTION", "sensors").strip() or "sensors"
        )
        measurements_subcollection = (
            os.getenv("FIREBASE_MEASUREMENTS_SUBCOLLECTION", "measurements").strip()
            or "measurements"
        )
        alerts_collection = (
            os.getenv("FIREBASE_ALERTS_COLLECTION", "alerts").strip() or "alerts"
        )
        alert_thresholds_document = (
            os.getenv("FIREBASE_ALERT_THRESHOLDS_DOCUMENT", "settings/alerts").strip()
            or "settings/alerts"
        )
        options = dict(
            sensors_collection=sensors_collection,
            measurements_subcollection=measurements_subcollection,
            alerts_collection=alerts_collection,
            alert_thresholds_document=alert_thresholds_document,
            alert_thresholds_refresh_s=_env_float(
                "FIREBASE_ALERT_THRESHOLDS_REFRESH_S", 60.0
            ),
            max_attempts=_env_int("FIREBASE_MAX_ATTEMPTS", 3),
            retry_backoff_s=_env_float("FIREBASE_RETRY_BACKOFF_S", 0.5),
        )

        if not _env_bool("FIREBASE_ENABLED", True):
            log.info("Firebase upload disabled (FIREBASE_ENABLED=false)")
            return cls(None, **options)

        credentials_path = (
            os.getenv("FIREBASE_CREDENTIALS")
            or os.getenv("GOOGLE_APPLICATION_CREDENTIALS")
            or ""
        ).strip()
        project_id = (os.getenv("FIREBASE_PROJECT_ID") or "").strip()

        if not credentials_path and not project_id:
            log.info(
                "Firebase upload disabled: set FIREBASE_CREDENTIALS to a service "
                "account JSON file (or FIREBASE_PROJECT_ID to use application "
                "default credentials)"
            )
            return cls(None, **options)

        if credentials_path and not os.path.isfile(credentials_path):
            log.error(
                "Firebase upload disabled: credentials file not found: %s",
                credentials_path,
            )
            return cls(None, **options)

        try:
            import firebase_admin
            from firebase_admin import credentials, firestore
        except ImportError:
            log.warning(
                "Firebase upload disabled: firebase-admin is not installed "
                "(pip install -r requirements.txt)"
            )
            return cls(None, **options)

        try:
            if credentials_path:
                credential = credentials.Certificate(credentials_path)
            else:
                # Let firebase-admin use application default credentials.
                credential = None

            app_options = {"projectId": project_id} if project_id else None
            try:
                app = firebase_admin.get_app()
            except ValueError:
                app = firebase_admin.initialize_app(credential, app_options)

            client = firestore.client(app=app)
        except Exception:
            log.exception("Firebase upload disabled: client initialisation failed")
            return cls(None, **options)

        log.info(
            "Firebase upload enabled (project=%s, collection=%s, measurements=%s, "
            "alerts=%s, thresholds=%s)",
            getattr(app, "project_id", project_id or "unknown"),
            sensors_collection,
            measurements_subcollection,
            alerts_collection,
            alert_thresholds_document,
        )
        return cls(client, firestore, **options)

    def get_alert_thresholds(self, *, force: bool = False) -> AlertThresholds | None:
        """Read the user-defined thresholds from Firestore.

        Returns the last known thresholds when a refresh fails, or None if no
        thresholds have ever been read. A missing document means "no thresholds
        configured" rather than an error.
        """
        if not self.enabled:
            return None

        now = time.monotonic()
        cache_is_fresh = (
            self._alert_thresholds is not None
            and now - self._alert_thresholds_read_at
            < self._alert_thresholds_refresh_s
        )
        if not force and cache_is_fresh:
            return self._alert_thresholds

        try:
            snapshot = self._alert_thresholds_ref().get()
            data = snapshot.to_dict() if getattr(snapshot, "exists", False) else None
            thresholds = AlertThresholds.from_mapping(data)
        except Exception:
            log.exception("Could not read alert thresholds from Firestore")
            return self._alert_thresholds

        self._alert_thresholds = thresholds
        self._alert_thresholds_read_at = now
        return thresholds

    def _alert_thresholds_ref(self) -> Any:
        parts = [part for part in self._alert_thresholds_document.split("/") if part]
        if len(parts) != 2:
            raise ValueError(
                "FIREBASE_ALERT_THRESHOLDS_DOCUMENT must be a "
                "collection/document path"
            )
        return self._client.collection(parts[0]).document(parts[1])

    def upload_alerts(self, alerts: list[Alert], timestamp: datetime) -> bool:
        """Upload only alert documents (used by the integration test script)."""
        return self.upload_measurements([], timestamp, alerts)

    def upload_measurements(
        self,
        sensors: list[dict[str, Any]],
        timestamp: datetime,
        alerts: list[Alert] | None = None,
    ) -> bool:
        """Upload one reading per sensor and any alerts in the same batch.

        Returns True if the Firestore batch committed, False if uploads are
        disabled or all retry attempts failed.
        """
        if not self.enabled:
            return False

        alerts = alerts or []
        if not sensors and not alerts:
            return True

        recorded_at = _as_utc(timestamp)
        for attempt in range(1, self._max_attempts + 1):
            try:
                self._write_batch(sensors, recorded_at, alerts)
                return True
            except Exception:
                if attempt >= self._max_attempts:
                    log.exception(
                        "Firebase upload failed after %d attempt(s); "
                        "%d reading(s) and %d alert(s) dropped",
                        attempt,
                        len(sensors),
                        len(alerts),
                    )
                    return False

                delay = self._retry_backoff_s * (2 ** (attempt - 1))
                log.warning(
                    "Firebase upload attempt %d/%d failed; retrying in %.1fs",
                    attempt,
                    self._max_attempts,
                    delay,
                )
                if delay:
                    time.sleep(delay)

        return False

    def _write_batch(
        self,
        sensors: list[dict[str, Any]],
        recorded_at: datetime,
        alerts: list[Alert],
    ) -> None:
        batch = self._client.batch()
        upload_time = self._server_timestamp

        for sensor in sensors:
            sensor_id = str(sensor["id"])
            reading = {
                "sensor_id": sensor_id,
                "bus_num": sensor["bus_num"],
                "address": sensor["address"],
                "temperature_c": sensor["temperature_c"],
                "humidity_pct": sensor["humidity_pct"],
                "timestamp": recorded_at,
                "uploaded_at": upload_time,
            }

            sensor_ref = self._client.collection(self._sensors_collection).document(
                sensor_id
            )
            batch.set(
                sensor_ref.collection(self._measurements_subcollection).document(),
                reading,
            )
            # Keep the latest reading on the sensor document for cheap reads.
            batch.set(
                sensor_ref,
                {
                    "id": sensor_id,
                    "bus_num": sensor["bus_num"],
                    "address": sensor["address"],
                    "temperature_c": sensor["temperature_c"],
                    "humidity_pct": sensor["humidity_pct"],
                    "timestamp": recorded_at,
                    "updated_at": upload_time,
                },
                merge=True,
            )

        for alert in alerts:
            document = alert.to_document()
            document["created_at"] = upload_time
            batch.set(
                self._client.collection(self._alerts_collection).document(),
                document,
            )

        batch.commit()
