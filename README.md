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

# with sensor hooked up (defaults to 1 sensor on 1 bus, i2c-1 address 0x44)
uvicorn app.main:app --host 0.0.0.0 --port 8000

# with 2 sensors hooked up on 2 buses (i2c-1,i2c-0)
export SENSOR_BUSES=1,0
export SENSOR_ADDRESSES=0x44,0x44
uvicorn app.main:app --host 0.0.0.0 --port 8000

# with no sensor 
SENSOR_MODE=fake uvicorn app.main:app --host 0.0.0.0 --port 8000

# with no sensor, but deliberately out-of-range readings so alerts fire
SENSOR_MODE=fake_alert uvicorn app.main:app --host 0.0.0.0 --port 8000
```

## Configuration (environment variables)

The backend reads the following environment variables:

- `SENSOR_MODE`
    - `fake` = return generated demo values (no hardware required)
    - `fake_alert` = return fixed out-of-range values to test alerts
      (defaults: `15.0 °C`, `85.0 %`)
    - unset/other = use real SHT31 via I2C

- `FAKE_ALERT_TEMPERATURE_C`
    - Temperature used by `SENSOR_MODE=fake_alert` (default: `15.0`).

- `FAKE_ALERT_HUMIDITY_PCT`
    - Humidity used by `SENSOR_MODE=fake_alert` (default: `85.0`).

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

## Firebase (Cloud Firestore)

Firebase is already configured for this project. Cloud upload is optional: the
backend serves the local API whether or not Firebase credentials are set.

### Run with Firebase

Set the service-account path and project ID, then start the backend:

```bash
cd backend
source .venv/bin/activate

export FIREBASE_CREDENTIALS=/etc/coen390/firebase-service-account.json
export FIREBASE_PROJECT_ID=your-firebase-project-id
uvicorn app.main:app --host 0.0.0.0 --port 8000
```

If application default credentials are already configured, setting
`FIREBASE_PROJECT_ID` alone is enough. The service-account file must not be
committed to the repository.

### Run without Firebase

Leave `FIREBASE_CREDENTIALS` and `FIREBASE_PROJECT_ID` unset, or disable uploads
explicitly:

```bash
cd backend
source .venv/bin/activate
FIREBASE_ENABLED=false uvicorn app.main:app --host 0.0.0.0 --port 8000
```

With no credentials, uploads are skipped automatically. The local API keeps
serving readings, and upload failures are logged without stopping polling.

### Firestore layout

Each sensor gets its own document plus a measurements subcollection:

```text
sensors/{sensor_id}
    id, bus_num, address, temperature_c, humidity_pct,
    timestamp, updated_at

sensors/{sensor_id}/measurements/{measurement_id}
    sensor_id, bus_num, address, temperature_c, humidity_pct,
    timestamp, uploaded_at

alerts/{alert_id}
    sensor_id, type, condition, value, threshold, unit, message,
    timestamp, created_at

settings/alerts
    enabled, temperature_min_c, temperature_max_c,
    humidity_min_pct, humidity_max_pct
```

- `timestamp` is when the Pi recorded the reading (UTC).
- `updated_at` / `uploaded_at` / `created_at` are Firestore server timestamps.
- `alerts.type` is `temperature` or `humidity`; `alerts.condition` is `below`
  or `above`.

Android can read the sensor document for the latest value, or query the
`measurements` subcollection for history.

### Alerts

Alerts are published to the `alerts` collection by the Pi. The thresholds come
from the `settings/alerts` document, so they can be changed without redeploying
the backend:

```json
{
  "enabled": true,
  "temperature_min_c": 20,
  "temperature_max_c": 30,
  "humidity_min_pct": 40,
  "humidity_max_pct": 70
}
```

- A minimum alerts when the reading is **below** the value; a maximum alerts
  when it is **above** the value. Omit a field (or set it to `null`) to disable
  that side of the check.
- If the document is missing, no alerts are generated.
- An alert is written when a reading first crosses a threshold. If the
  condition persists, it is repeated at most once every `ALERT_REPEAT_S`
  seconds (default `300`). The monitor re-arms when the reading returns to the
  normal range.
- Alerts are written in the same Firestore batch as the measurements. If the
  batch fails, both are retried on the next poll.

There is deliberately no HTTP endpoint for submitting thresholds yet. For now,
create or edit `settings/alerts` in the Firebase console. The Pi reads it with
the Admin SDK, so no client write access is required.

To test alerts without hardware, set `SENSOR_MODE=fake_alert`. With the example
thresholds above, the default fake values (`15 °C`, `85 %`) trigger both a
low-temperature and a high-humidity alert. Override the fake values if your
thresholds are different:

```bash
SENSOR_MODE=fake_alert \
FAKE_ALERT_TEMPERATURE_C=35 \
FAKE_ALERT_HUMIDITY_PCT=20 \
uvicorn app.main:app --host 0.0.0.0 --port 8000
```

### Test the live Firestore threshold path

Use the integration script to read the real `settings/alerts` document, create
a reading that violates it, and optionally publish the resulting alert. It is a
dry run unless `--publish` is passed:

```bash
cd backend
export FIREBASE_CREDENTIALS=service-account.json
export FIREBASE_PROJECT_ID=sidwatch-e7f1f

# Read thresholds and show the alert(s) that would be sent
python -m scripts.test_firebase_alerts

# Actually write the alert document(s) to Firestore
python -m scripts.test_firebase_alerts --sensor-id sensor_test --publish
```

The script prints the thresholds it read and the generated alert details. After
`--publish`, check **Firebase Console → Firestore Database → Data → `alerts`**.
Use `--temperature` or `--humidity` to force a specific reading instead of the
automatic just-outside-the-threshold value.

### Firebase environment variables

- `FIREBASE_ENABLED` - `true` by default; set `false` to disable uploads.
- `FIREBASE_CREDENTIALS` - path to a service account JSON file.
  `GOOGLE_APPLICATION_CREDENTIALS` is also accepted.
- `FIREBASE_PROJECT_ID` - optional project override; if set without a credentials
  file, application default credentials are used.
- `FIREBASE_SENSORS_COLLECTION` - top-level collection name (default: `sensors`).
- `FIREBASE_MEASUREMENTS_SUBCOLLECTION` - history subcollection (default:
  `measurements`).
- `FIREBASE_ALERTS_COLLECTION` - alert collection name (default: `alerts`).
- `FIREBASE_ALERT_THRESHOLDS_DOCUMENT` - threshold document path (default:
  `settings/alerts`).
- `FIREBASE_ALERT_THRESHOLDS_REFRESH_S` - how often to re-read thresholds from
  Firestore, in seconds (default: `60`).
- `ALERT_REPEAT_S` - how often to repeat an alert while a condition persists, in
  seconds (default: `300`).
- `FIREBASE_MAX_ATTEMPTS` - upload attempts per poll (default: `3`).
- `FIREBASE_RETRY_BACKOFF_S` - base retry delay in seconds (default: `0.5`).

If Firebase is not configured or an upload fails after all retries, the error is
logged and the sensor API continues serving readings.

## Android app (Firestore)

The Android app reads Cloud Firestore directly. It does not poll the Pi's HTTP
API.

### Run

1. Make sure `android/app/google-services.json` is present. It is git-ignored,
   so use the project's copy.
2. Open the `android/` directory in Android Studio, sync Gradle, and run the app.

The Google Services Gradle plugin is applied when that file exists. Without it,
the app still builds but shows a configuration message. Use
`google-services.json` for the app; keep the Pi's service-account JSON on the
server.

The deployed [`firestore.rules`](firestore.rules) allow client reads and deny
client writes; the Pi writes with the Admin SDK.

### What the app shows

- Latest reading for every sensor from the `sensors` collection.
- Tap a sensor card to load the newest 20 entries from
  `sensors/{sensor_id}/measurements`.
- Firestore snapshot listeners update the screen automatically as the Pi
  uploads new readings.
