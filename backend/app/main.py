import asyncio
import logging
import os
from contextlib import asynccontextmanager
from datetime import datetime, timezone

from fastapi import FastAPI, HTTPException
from pydantic import BaseModel

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
            app.state.latest = SensorBundle(
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
        except Exception:
            log.exception("Sensor read failed")
        await asyncio.sleep(POLL_INTERVAL_S)


@asynccontextmanager
async def lifespan(app: FastAPI):
    app.state.latest = None
    task = asyncio.create_task(poll_loop(app))
    yield
    task.cancel()


app = FastAPI(title="Sensor API", version="1.0.0", lifespan=lifespan)


@app.get("/api/v1/sensor_data", response_model=SensorBundle)
async def sensor_data():
    if app.state.latest is None:
        raise HTTPException(status_code=503, detail="No reading available yet")
    return app.state.latest
