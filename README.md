# Sensor Project

## API Contract at api/openapi.yaml

```http
GET /api/v1/sensor_data
```

Example response

```json
{
    "sensors": [
        {
            "id": "sensor_1",
            "bus_num": 1,
            "address": "0x44",
            "temperature_c": 23.68,
            "humidity_pct": 42.87
        }
    ],
    "timestamp": "2026-10-29T16:40:11.980986Z"
}
```

## Backend

### Run on Raspberry Pi (Linux)

```bash
cd backend
python3 -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt

# with sensor hooked up
uvicorn app.main:app --host 0.0.0.0 --port 8000

# with no sensor
SENSOR_MODE=fake uvicorn app.main:app --host 0.0.0.0 --port 8000
```

## Configuration (environment variables)

The backend reads the following environment variables:

- `SENSOR_MODE`
    - `fake` = return generated demo values (no hardware required)
    - unset/other = use real SHT31 via I2C

- `SENSOR_BUSES`
    - Comma-separated bus numbers, for example: `1` or `1,0`
    - maps to Linux I2C device numbers:
        - `i2c-1` -> `1`
        - `i2c-0` -> `0`
    - for example `SENSOR_BUSES=1`, i2c-1 is selected, one bus
    - `SENSOR_BUSES=1,0`, i2c-1 and i2c-0 is selected

- `SENSOR_ADDRESSES`
    - Example: `SENSOR_ADDRESSES=0x44,0x45` pr `SENSOR_ADDRESSES=0x44,0x44` for 2 buses
    - `SENSOR_ADDRESSES=0x44` for 1 bus

- `POLL_INTERVAL_S`
    - Polling interval in seconds for background reads (default: `2`).

### Bus/address pairing rules

Given `SENSOR_BUSES` and `SENSOR_ADDRESSES`, the backend builds sensor targets as follows:

1. One bus + many addresses -> use the same bus for all addresses.
2. Many buses + one address -> use the same address on all buses.
3. Same count on both sides -> pair by position.
4. Different counts (>1 on both sides) -> pair up to the shortest list.

Examples:

- `SENSOR_BUSES=1` and `SENSOR_ADDRESSES=0x44` -> `(1,0x44)`
- `SENSOR_BUSES=1` and `SENSOR_ADDRESSES=0x44,0x45` -> `(1,0x44), (1,0x45)`
- `SENSOR_BUSES=1,0` and `SENSOR_ADDRESSES=0x44` -> `(1,0x44), (0,0x44)`
- `SENSOR_BUSES=1,0` and `SENSOR_ADDRESSES=0x44,0x45` -> `(1,0x44), (0,0x45)`
