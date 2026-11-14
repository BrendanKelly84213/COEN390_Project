# sht31.py
import logging
import time
import os
import random

log = logging.getLogger("sensors")

CMD_MEASURE = (0x24, 0x00)

DEFAULT_FAKE_ALERT_TEMPERATURE_C = 15.0
DEFAULT_FAKE_ALERT_HUMIDITY_PCT = 85.0


def _sensor_mode() -> str:
    return (os.getenv("SENSOR_MODE") or "").strip().lower()


def _env_float(name: str, default: float) -> float:
    raw = os.getenv(name)
    if raw is None or raw.strip() == "":
        return default
    try:
        return float(raw)
    except ValueError:
        log.warning("Invalid %s=%r; using %.1f", name, raw, default)
        return default

def _parse_int_list(value: str | None, default: list[int]) -> list[int]:
    if value is None or value.strip() == "":
        return default

    parsed: list[int] = []
    for item in value.split(","):
        item = item.strip()
        if item:
            parsed.append(int(item, 0))

    return parsed or default

def get_sensor_configs() -> list[tuple[int, int]]:
    buses = _parse_int_list(os.getenv("SENSOR_BUSES"), [1])
    addresses = _parse_int_list(os.getenv("SENSOR_ADDRESSES"), [0x44])

    if len(buses) == 1 and len(addresses) > 1:
        return [(buses[0], addr) for addr in addresses]
    if len(addresses) == 1 and len(buses) > 1:
        return [(bus, addresses[0]) for bus in buses]
    if len(buses) == len(addresses):
        return list(zip(buses, addresses))

    count = min(len(buses), len(addresses))
    return [(buses[i], addresses[i]) for i in range(count)]

def sht31_read(bus_num: int, addr: int = 0x44) -> tuple[float, float]:
    mode = _sensor_mode()
    if mode == "fake_alert":
        temp_c = _env_float(
            "FAKE_ALERT_TEMPERATURE_C", DEFAULT_FAKE_ALERT_TEMPERATURE_C
        )
        humidity_pct = _env_float(
            "FAKE_ALERT_HUMIDITY_PCT", DEFAULT_FAKE_ALERT_HUMIDITY_PCT
        )
        return round(temp_c, 2), round(humidity_pct, 2)

    if mode == "fake":
        return round(random.uniform(20, 25), 2), round(random.uniform(40, 60), 2)

    from smbus2 import SMBus, i2c_msg

    with SMBus(bus_num) as bus:
        write_msg = i2c_msg.write(addr, list(CMD_MEASURE))
        bus.i2c_rdwr(write_msg)
        time.sleep(0.02)

        read_msg = i2c_msg.read(addr, 6)
        bus.i2c_rdwr(read_msg)
        data = list(read_msg)

    raw_t = (data[0] << 8) | data[1]
    raw_h = (data[3] << 8) | data[4]

    temp_c = -45.0 + (175.0 * raw_t / 65535.0)
    humidity_pct = 100.0 * raw_h / 65535.0

    return temp_c, humidity_pct

def read_all_sensors() -> list[dict]:
    readings: list[dict] = []
    for index, (bus_num, addr) in enumerate(get_sensor_configs(), start=1):
        temp_c, humidity_pct = sht31_read(bus_num, addr)
        readings.append(
            {
                "id": f"sensor_{index}",
                "bus_num": bus_num,
                "address": f"0x{addr:02X}",
                "temperature_c": round(temp_c, 2),
                "humidity_pct": round(humidity_pct, 2),
            }
        )
    return readings
