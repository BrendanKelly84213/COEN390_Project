import asyncio
import logging
import os
from contextlib import asynccontextmanager
from datetime import datetime, timezone

from fastapi import FastAPI, HTTPException
from pydantic import BaseModel

from .alerts import AlertMonitor
from .firebase_store import FirebaseStore
from .sht31 import read_all_sensors

log = logging.getLogger("sensor-api")
POLL_INTERVAL_S = float(os.getenv("POLL_INTERVAL_S", "2"))


class SensorReading(BaseModel):
    id: str
    bus_num: int
    address: str
    temperature_c: float
    humidity_pct: float


class SensorBundle(BaseModel):
    sensors: list[SensorReading]
    timestamp: datetime


async def poll_loop(app: FastAPI):
    """Read all configured sensors in the background so requests never touch the I2C bus."""
    while True:
        try:
            readings = await asyncio.to_thread(read_all_sensors)
            bundle = SensorBundle(
                sensors=[
                    SensorReading(
                        id=item["id"],
                        bus_num=item["bus_num"],
                        address=item["address"],
                        temperature_c=item["temperature_c"],
                        humidity_pct=item["humidity_pct"],
                    )
                    for item in readings
                ],
                timestamp=datetime.now(timezone.utc),
            )
            app.state.latest = bundle
        except Exception:
            log.exception("Sensor read failed")
        else:
            firebase = getattr(app.state, "firebase", None)
            if firebase is not None and firebase.enabled:
                try:
                    thresholds = await asyncio.to_thread(
                        firebase.get_alert_thresholds
                    )
                    alerts = []
                    active_keys = None
                    if thresholds is not None:
                        alerts, active_keys = app.state.alert_monitor.evaluate(
                            readings, thresholds, bundle.timestamp
                        )

                    uploaded = await asyncio.to_thread(
                        firebase.upload_measurements,
                        readings,
                        bundle.timestamp,
                        alerts,
                    )
                    if uploaded:
                        if active_keys is not None:
                            app.state.alert_monitor.mark_published(
                                active_keys, alerts, bundle.timestamp
                            )
                        if alerts:
                            log.info("Published %d alert(s) to Firestore", len(alerts))
                            try:
                                sent = await asyncio.to_thread(
                                    firebase.send_alert_notifications,
                                    alerts,
                                )
                                if sent:
                                    log.info(
                                        "Sent %d push notification(s)",
                                        sent,
                                    )
                            except Exception:
                                log.exception("Unexpected push notification error")
                    else:
                        log.warning(
                            "Firebase upload failed; local API is unaffected"
                        )
                except Exception:
                    log.exception("Unexpected Firebase upload error")

        await asyncio.sleep(POLL_INTERVAL_S)


@asynccontextmanager
async def lifespan(app: FastAPI):
    app.state.latest = None
    app.state.firebase = FirebaseStore.from_env()
    app.state.alert_monitor = AlertMonitor.from_env()
    task = asyncio.create_task(poll_loop(app))
    try:
        yield
    finally:
        task.cancel()
        try:
            await task
        except asyncio.CancelledError:
            pass


app = FastAPI(title="Sensor API", version="1.0.0", lifespan=lifespan)


@app.get("/api/v1/sensor_data", response_model=SensorBundle)
async def sensor_data():
    if app.state.latest is None:
        raise HTTPException(status_code=503, detail="No reading available yet")
    return app.state.latest
