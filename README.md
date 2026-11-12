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

## Firebase (Cloud Firestore, optional)

When credentials are configured, the backend uploads every poll to Cloud
Firestore. Without credentials it still serves the local API and simply skips
cloud uploads, so fake mode and offline development keep working.

### Setup

1. Create/select a project in the [Firebase console](https://console.firebase.google.com/)
   and enable **Cloud Firestore**.
2. Go to **Project settings -> Service accounts -> Generate new private key**.
3. Save the JSON file outside the repository, for example
   `/etc/coen390/firebase-service-account.json`. Never commit it.
4. Export the credentials and start the backend:

```bash
export FIREBASE_CREDENTIALS=/etc/coen390/firebase-service-account.json
export FIREBASE_PROJECT_ID=your-firebase-project-id
uvicorn app.main:app --host 0.0.0.0 --port 8000
```

### Firestore layout

Each sensor gets its own document plus a measurements subcollection:

```text
sensors/{sensor_id}
    id, bus_num, address, temperature_c, humidity_pct,
    timestamp, updated_at

sensors/{sensor_id}/measurements/{measurement_id}
    sensor_id, bus_num, address, temperature_c, humidity_pct,
    timestamp, uploaded_at
```

- `timestamp` is when the Pi recorded the reading (UTC).
- `updated_at` / `uploaded_at` are Firestore server timestamps.

Android can read the sensor document for the latest value, or query the
`measurements` subcollection for history.

### Firebase environment variables

- `FIREBASE_ENABLED` - `true` by default; set `false` to disable uploads.
- `FIREBASE_CREDENTIALS` - path to a service account JSON file.
  `GOOGLE_APPLICATION_CREDENTIALS` is also accepted.
- `FIREBASE_PROJECT_ID` - optional project override; if set without a credentials
  file, application default credentials are used.
- `FIREBASE_SENSORS_COLLECTION` - top-level collection name (default: `sensors`).
- `FIREBASE_MEASUREMENTS_SUBCOLLECTION` - history subcollection (default:
  `measurements`).
- `FIREBASE_MAX_ATTEMPTS` - upload attempts per poll (default: `3`).
- `FIREBASE_RETRY_BACKOFF_S` - base retry delay in seconds (default: `0.5`).

If Firebase is not configured or an upload fails after all retries, the error is
logged and the sensor API continues serving readings.

## Android app (Firestore)

The Android app reads Cloud Firestore directly. It no longer polls the Pi's
HTTP API.

### Required config: `google-services.json`

The Pi's `service-account.json` is a **server-side secret** and must not be
added to the Android project. The app needs the public Firebase client config:

1. Firebase console -> **Project settings -> Your apps -> Add app -> Android**.
2. Use package name `com.example.frontend`.
3. Download `google-services.json`.
4. Place it at `android/app/google-services.json`.
5. Sync Gradle and run the app.

`google-services.json` is git-ignored. The Google Services Gradle plugin is
applied only when that file exists, so the project still builds before Firebase
is configured; the app then shows setup instructions instead of crashing.

### Firestore rules

Deploy [`firestore.rules`](firestore.rules), or paste it into
**Firebase console -> Firestore -> Rules**. The rules allow the Android app to
read and deny client writes. The Pi's Admin SDK bypasses these rules, so the
backend can still write.

### What the app shows

- Latest reading for every sensor from the `sensors` collection.
- Tap a sensor card to load the newest 20 entries from
  `sensors/{sensor_id}/measurements`.
- Firestore snapshot listeners update the screen automatically as the Pi
  uploads new readings.
