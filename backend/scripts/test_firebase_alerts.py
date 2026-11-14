"""Integration check for Firestore-stored alert thresholds.

This reads the real ``settings/alerts`` document through the same
:class:`~app.firebase_store.FirebaseStore` used by the backend, creates a
synthetic reading that violates those thresholds, and prints the alert(s) that
would be published.

By default nothing is written to Firestore. Pass ``--publish`` to write the
alert document(s) to the configured alerts collection.

Usage::

    cd backend
    export FIREBASE_CREDENTIALS=/path/to/service-account.json
    python -m scripts.test_firebase_alerts

    # Actually publish the test alert(s)
    python -m scripts.test_firebase_alerts --publish
"""

from __future__ import annotations

import argparse
from datetime import datetime, timezone

from app.alerts import AlertThresholds, evaluate_readings
from app.firebase_store import FirebaseStore


def _has_thresholds(thresholds: AlertThresholds) -> bool:
    return any(
        value is not None
        for value in (
            thresholds.temperature_min_c,
            thresholds.temperature_max_c,
            thresholds.humidity_min_pct,
            thresholds.humidity_max_pct,
        )
    )


def _violating_value(
    minimum: float | None,
    maximum: float | None,
    fallback: float,
) -> tuple[float, str]:
    if minimum is not None:
        return round(minimum - 1.0, 2), f"below minimum {minimum:g}"
    if maximum is not None:
        return round(maximum + 1.0, 2), f"above maximum {maximum:g}"
    return fallback, "no threshold configured, using demo value"


def _synthetic_reading(
    thresholds: AlertThresholds,
    sensor_id: str,
    temperature_override: float | None,
    humidity_override: float | None,
) -> tuple[dict, list[str]]:
    temperature, temperature_reason = _violating_value(
        thresholds.temperature_min_c,
        thresholds.temperature_max_c,
        15.0,
    )
    humidity, humidity_reason = _violating_value(
        thresholds.humidity_min_pct,
        thresholds.humidity_max_pct,
        85.0,
    )

    if temperature_override is not None:
        temperature = temperature_override
        temperature_reason = "manual override"
    if humidity_override is not None:
        humidity = humidity_override
        humidity_reason = "manual override"

    reading = {
        "id": sensor_id,
        "bus_num": 1,
        "address": "0x44",
        "temperature_c": temperature,
        "humidity_pct": humidity,
    }
    notes = [
        f"temperature={temperature:g} °C ({temperature_reason})",
        f"humidity={humidity:g} % ({humidity_reason})",
    ]
    return reading, notes


def main() -> int:
    parser = argparse.ArgumentParser(
        description=(
            "Read user-defined alert thresholds from Firestore and generate a "
            "test alert. Dry-run unless --publish is given."
        )
    )
    parser.add_argument(
        "--publish",
        action="store_true",
        help="write the generated alert document(s) to Firestore",
    )
    parser.add_argument(
        "--sensor-id",
        default="sensor_1",
        help="sensor ID to use in the test reading (default: sensor_1)",
    )
    parser.add_argument(
        "--temperature",
        type=float,
        default=None,
        help="override the synthetic temperature in °C",
    )
    parser.add_argument(
        "--humidity",
        type=float,
        default=None,
        help="override the synthetic humidity in %%",
    )
    args = parser.parse_args()

    store = FirebaseStore.from_env()
    if not store.enabled:
        print(
            "Firebase is disabled. Set FIREBASE_CREDENTIALS (and optionally "
            "FIREBASE_PROJECT_ID), then run this again."
        )
        return 1

    thresholds = store.get_alert_thresholds(force=True)
    if thresholds is None:
        print("Could not read thresholds from Firestore. Check the backend log.")
        return 1

    print(
        "Thresholds from "
        f"{store.alert_thresholds_document}:"
    )
    print(
        f"  enabled={thresholds.enabled}, "
        f"temperature_min_c={thresholds.temperature_min_c}, "
        f"temperature_max_c={thresholds.temperature_max_c}, "
        f"humidity_min_pct={thresholds.humidity_min_pct}, "
        f"humidity_max_pct={thresholds.humidity_max_pct}"
    )

    if not thresholds.enabled:
        print("Alert thresholds are disabled (enabled=false); nothing to test.")
        return 1
    if not _has_thresholds(thresholds):
        print(
            "No thresholds are configured. Create settings/alerts in "
            "Firestore with numeric threshold fields first."
        )
        return 1

    reading, notes = _synthetic_reading(
        thresholds,
        args.sensor_id,
        args.temperature,
        args.humidity,
    )
    print("Synthetic reading:")
    for note in notes:
        print(f"  {note}")

    timestamp = datetime.now(timezone.utc)
    alerts = evaluate_readings([reading], thresholds, timestamp)
    if not alerts:
        print(
            "The synthetic reading did not violate any configured threshold. "
            "Use --temperature/--humidity to choose a value manually."
        )
        return 1

    print(f"Generated {len(alerts)} alert(s):")
    for alert in alerts:
        print(f"  [{alert.alert_type}] {alert.message}")

    if not args.publish:
        print(
            "\nDry run only. Re-run with --publish to write these alert "
            "document(s) to Firestore."
        )
        return 0

    if not store.upload_alerts(alerts, timestamp):
        print("Publishing failed. Check the backend log for the Firestore error.")
        return 1

    print(
        f"\nPublished {len(alerts)} alert(s) to the "
        f"{store.alerts_collection} collection."
    )
    print("Firebase Console -> Firestore Database -> Data -> alerts")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
