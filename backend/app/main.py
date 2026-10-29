import asyncio
import logging
import os
from contextlib import asynccontextmanager
from datetime import datetime, timezone

from fastapi import FastAPI, HTTPException
from pydantic import BaseModel

from sht31 import sht31_read

log = logging.getLogger("sensor-api")
POLL_INTERVAL_S = float(os.getenv("POLL_INTERVAL_S", "2"))


class SensorData(BaseModel):
    temperature_c: float
    humidity_pct: float
    timestamp: datetime


async def poll_loop(app: FastAPI):
    """Read the sensor in the background so requests never touch the I2C bus."""
    while True:
        try:
            t, h = await asyncio.to_thread(sht31_read)
            app.state.latest = SensorData(
                temperature_c=t,
                humidity_pct=h,
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


@app.get("/api/v1/sensor_data", response_model=SensorData)
async def sensor_data():
    if app.state.latest is None:
        raise HTTPException(status_code=503, detail="No reading available yet")
    return app.state.latest
