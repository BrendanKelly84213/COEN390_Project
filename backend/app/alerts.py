"""Temperature and humidity alert evaluation.

Alert thresholds are user-defined and stored in Firestore so they can be
changed without redeploying the Raspberry Pi backend. The default location is
``settings/alerts``::

    {
      "enabled": true,
      "temperature_min_c": 20.0,
      "temperature_max_c": 30.0,
      "humidity_min_pct": 40.0,
      "humidity_max_pct": 70.0
    }

A missing or ``null`` threshold disables that side of the check. For example,
``temperature_min_c: 20`` creates an alert when the temperature is below 20 °C.

This module only evaluates readings and builds alert documents. Uploading them
to Firestore is handled by :mod:`app.firebase_store`.
"""

from __future__ import annotations

import logging
import math
import os
from dataclasses import dataclass
from datetime import datetime, timezone
from typing import Any, Iterable, Mapping

log = logging.getLogger("alerts")

_DISABLED_VALUES = {"0", "false", "no", "off"}

# A condition is identified by the sensor, metric, direction, and threshold.
# Including the threshold means changing a threshold re-arms the alert.
AlertKey = tuple[str, str, str, float]


def _as_utc(value: datetime) -> datetime:
    if value.tzinfo is None:
        return value.replace(tzinfo=timezone.utc)
    return value.astimezone(timezone.utc)


def _number(value: Any) -> float | None:
    """Return a finite float, or None for missing/invalid Firestore values."""
    if isinstance(value, bool) or not isinstance(value, (int, float)):
        return None
    number = float(value)
    return number if math.isfinite(number) else None


def _boolean(value: Any, default: bool) -> bool:
    if value is None:
        return default
    if isinstance(value, bool):
        return value
    if isinstance(value, str):
        return value.strip().lower() not in _DISABLED_VALUES
    return bool(value)


@dataclass(frozen=True)
class AlertThresholds:
    """User-configured limits for one sensor environment."""

    temperature_min_c: float | None = None
    temperature_max_c: float | None = None
    humidity_min_pct: float | None = None
    humidity_max_pct: float | None = None
    enabled: bool = True

    @classmethod
    def from_mapping(cls, data: Mapping[str, Any] | None) -> "AlertThresholds":
        data = data or {}
        return cls(
            temperature_min_c=_number(data.get("temperature_min_c")),
            temperature_max_c=_number(data.get("temperature_max_c")),
            humidity_min_pct=_number(data.get("humidity_min_pct")),
            humidity_max_pct=_number(data.get("humidity_max_pct")),
            enabled=_boolean(data.get("enabled"), True),
        )


@dataclass(frozen=True)
class Alert:
    """One threshold violation ready to be written to Firestore."""

    sensor_id: str
    alert_type: str
    condition: str
    value: float
    threshold: float
    unit: str
    message: str
    timestamp: datetime

    @property
    def key(self) -> AlertKey:
        return (self.sensor_id, self.alert_type, self.condition, self.threshold)

    def to_document(self) -> dict[str, Any]:
        return {
            "sensor_id": self.sensor_id,
            "type": self.alert_type,
            "condition": self.condition,
            "value": round(self.value, 2),
            "threshold": self.threshold,
            "unit": self.unit,
            "message": self.message,
            "timestamp": self.timestamp,
        }


def _rules(
    thresholds: AlertThresholds,
) -> Iterable[tuple[str, str, float, str, str]]:
    """Yield (type, reading field, threshold, direction, unit) checks."""
    if thresholds.temperature_min_c is not None:
        yield (
            "temperature",
            "temperature_c",
            thresholds.temperature_min_c,
            "below",
            "°C",
        )
    if thresholds.temperature_max_c is not None:
        yield (
            "temperature",
            "temperature_c",
            thresholds.temperature_max_c,
            "above",
            "°C",
        )
    if thresholds.humidity_min_pct is not None:
        yield ("humidity", "humidity_pct", thresholds.humidity_min_pct, "below", "%")
    if thresholds.humidity_max_pct is not None:
        yield ("humidity", "humidity_pct", thresholds.humidity_max_pct, "above", "%")


def evaluate_readings(
    readings: Iterable[Mapping[str, Any]],
    thresholds: AlertThresholds,
    timestamp: datetime,
) -> list[Alert]:
    """Return every threshold violation in one batch of sensor readings."""
    if not thresholds.enabled:
        return []

    observed_at = _as_utc(timestamp)
    alerts: list[Alert] = []

    for sensor in readings:
        sensor_id = str(sensor.get("id") or "unknown")
        for alert_type, field, threshold, condition, unit in _rules(thresholds):
            value = _number(sensor.get(field))
            if value is None:
                continue

            if condition == "below":
                violated = value < threshold
                comparator = "below the configured minimum"
            else:
                violated = value > threshold
                comparator = "above the configured maximum"

            if not violated:
                continue

            label = "Temperature" if alert_type == "temperature" else "Humidity"
            alerts.append(
                Alert(
                    sensor_id=sensor_id,
                    alert_type=alert_type,
                    condition=condition,
                    value=value,
                    threshold=threshold,
                    unit=unit,
                    message=(
                        f"{label} {value:.2f} {unit} is {comparator} "
                        f"of {threshold:.2f} {unit}"
                    ),
                    timestamp=observed_at,
                )
            )

    return alerts


class AlertMonitor:
    """Keeps alerts from being republished on every poll.

    A new alert is produced when a reading first crosses a threshold. While the
    condition remains true it is repeated at most once every
    ``ALERT_REPEAT_S`` seconds (default 300). Set that value to ``0`` to disable
    repeat reminders entirely. The state is only committed after the
    corresponding Firestore write succeeds, so failed uploads are retried.
    """

    def __init__(self, repeat_after_s: float = 300.0) -> None:
        repeat_after_s = float(repeat_after_s)
        self._repeat_after_s = repeat_after_s if repeat_after_s > 0 else None
        self._last_published: dict[AlertKey, datetime] = {}

    @classmethod
    def from_env(cls) -> "AlertMonitor":
        raw = (os.getenv("ALERT_REPEAT_S") or "300").strip()
        try:
            repeat_after_s = float(raw)
        except ValueError:
            log.warning("Invalid ALERT_REPEAT_S=%r; using 300", raw)
            repeat_after_s = 300.0
        return cls(repeat_after_s)

    def evaluate(
        self,
        readings: Iterable[Mapping[str, Any]],
        thresholds: AlertThresholds,
        timestamp: datetime,
    ) -> tuple[list[Alert], set[AlertKey]]:
        """Return alerts to publish now, plus all currently active conditions."""
        if not thresholds.enabled:
            return [], set()

        observed_at = _as_utc(timestamp)
        active: set[AlertKey] = set()
        alerts: list[Alert] = []

        for alert in evaluate_readings(readings, thresholds, observed_at):
            key = alert.key
            active.add(key)

            last = self._last_published.get(key)
            if last is None:
                alerts.append(alert)
                continue
            if self._repeat_after_s is None:
                continue
            elapsed = (observed_at - last).total_seconds()
            if elapsed >= self._repeat_after_s:
                alerts.append(alert)

        return alerts, active

    def mark_published(
        self,
        active_keys: set[AlertKey],
        alerts: list[Alert],
        timestamp: datetime,
    ) -> None:
        """Commit monitor state after a successful Firestore batch."""
        published_at = _as_utc(timestamp)

        # Drop state for conditions that are no longer violating a threshold so
        # the next crossing produces a fresh alert.
        self._last_published = {
            key: value
            for key, value in self._last_published.items()
            if key in active_keys
        }
        for alert in alerts:
            self._last_published[alert.key] = published_at
