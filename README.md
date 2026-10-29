# Sensor Project

## API Contract at api/openapi.yaml

```http
GET /api/v1/sensor_data
```

Example return 

```json

{
    "temperature_c": 23.68, 
    "humidity_pct":42.87,
    "timestamp":"2026-10-29T16:40:11.980986Z"
}
```

## Backend

```bash
cd backend
python3 -m venv .venv && source .venv/bin/activate
pip install -r requirements.txt

# with sensor hooked up 
uvicorn app.main:app --host 0.0.0.0 --port 8000

# with no sensor 
SENSOR_MODE=fake uvicorn app.main:app --host 0.0.0.0 --port 8000
```

