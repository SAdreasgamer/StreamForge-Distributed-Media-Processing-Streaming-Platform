# StreamForge — Startup & Testing Guide

> Complete guide to start, run, and test the StreamForge video processing platform.

---

## Table of Contents

- [Prerequisites](#prerequisites)
- [Quick Start](#quick-start)
- [Verify Infrastructure](#verify-infrastructure)
- [DataGrip — Database Dashboard](#datagrip--database-dashboard)
- [API Endpoints Reference](#api-endpoints-reference)
- [Postman Setup](#postman-setup)
- [Test Cases](#test-cases)
  - [1. Health Check](#1-health-check)
  - [2. Upload Session Tests](#2-upload-session-tests)
  - [3. File Upload Tests](#3-file-upload-tests)
  - [4. Video Listing Tests](#4-video-listing-tests)
  - [5. Video Detail Tests](#5-video-detail-tests)
  - [6. Processing Status Tests](#6-processing-status-tests)
  - [7. Playback Tests](#7-playback-tests)
  - [8. Thumbnail Tests](#8-thumbnail-tests)
  - [9. Delete Tests](#9-delete-tests)
  - [10. Error Handling Tests](#10-error-handling-tests)
- [Full End-to-End Workflow](#full-end-to-end-workflow)
- [Troubleshooting](#troubleshooting)

---

## Prerequisites

| Tool | Version | Purpose |
|------|---------|---------|
| **Java** | 21+ | Runtime (project compiles to Java 21 bytecode) |
| **Maven** | 3.9+ | Build tool (wrapper included: `./mvnw`) |
| **Docker Desktop** | Latest | PostgreSQL + MinIO containers |
| **FFmpeg** | 6.0+ | Video transcoding & metadata extraction |
| **Postman** | Latest | API testing |

### Install FFmpeg (macOS)

```bash
brew install ffmpeg
```

Verify:

```bash
ffmpeg -version
ffprobe -version
```

---

## Quick Start

### Step 1: Start Docker Desktop

Open Docker Desktop from your Applications, or:

```bash
open -a Docker
```

Wait ~30 seconds for the engine to initialize.

### Step 2: Start Infrastructure (PostgreSQL + MinIO)

```bash
cd /Users/shreyanand/dev_proj/streamForage
docker compose up -d
```

### Step 3: Verify Containers Are Healthy

```bash
docker ps --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}"
```

Expected output:

```
NAMES                  STATUS                    PORTS
streamforge-postgres   Up X seconds (healthy)    0.0.0.0:5433->5432/tcp
streamforge-minio      Up X seconds (healthy)    0.0.0.0:9000-9001->9000-9001/tcp
```

### Step 4: Start the Application

```bash
./mvnw clean spring-boot:run
```

Wait for:

```
Started StreamForgeApplication in X.XX seconds
```

### Step 5: Verify Everything Works

```bash
curl http://localhost:8080/actuator/health
# Expected: {"status":"UP"}
```

---

## Verify Infrastructure

### PostgreSQL

```bash
docker exec streamforge-postgres psql -U streamforge -d streamforge -c "\dt"
```

You should see 3 tables: `videos`, `upload_sessions`, `video_variants`

### MinIO Console

- **URL:** http://localhost:9001
- **Username:** `minioadmin`
- **Password:** `minioadmin123`
- **Buckets:** `streamforge-raw`, `streamforge-processed`

### Swagger UI

- **URL:** http://localhost:8080/swagger-ui/index.html
- **API Docs:** http://localhost:8080/v3/api-docs

---

## DataGrip — Database Dashboard

Connect JetBrains DataGrip to the Dockerized PostgreSQL to get a full visual dashboard of your data.

### Step 1: Add a New Data Source

1. Open DataGrip → **File → New → Data Source → PostgreSQL**
2. Fill in the connection details:

| Field | Value |
|-------|-------|
| **Name** | `StreamForge (Docker)` |
| **Host** | `localhost` |
| **Port** | `5433` |
| **Database** | `streamforge` |
| **User** | `streamforge` |
| **Password** | `streamforge_dev` |
| **URL** | `jdbc:postgresql://localhost:5433/streamforge` |

> ⚠️ The port is **5433**, not the default 5432. We mapped it to 5433 to avoid conflict with any locally installed PostgreSQL.

3. Click **Test Connection** — you should see a green checkmark ✅
4. Click **OK** to save

### Step 2: Download the Driver (First Time)

If prompted, click **Download** to install the PostgreSQL JDBC driver. DataGrip handles this automatically.

### Step 3: Explore the Schema

Once connected, expand the tree:

```
📁 StreamForge (Docker)
└── 📁 streamforge (database)
    └── 📁 public (schema)
        ├── 📄 videos
        ├── 📄 upload_sessions
        ├── 📄 video_variants
        └── 📄 flyway_schema_history
```

Double-click any table to open it in the **Data Editor** with full CRUD capabilities.

### Step 4: Useful Queries

Create a new **Console** (`Ctrl+Shift+Q` / `Cmd+Shift+Q`) and run these queries to monitor your platform:

#### 📊 Video Dashboard — All Videos with Status

```sql
SELECT
    id,
    title,
    status,
    original_filename,
    pg_size_pretty(file_size_bytes) AS file_size,
    duration_seconds,
    width || 'x' || height AS resolution,
    codec,
    audio_codec,
    created_at,
    updated_at
FROM videos
ORDER BY created_at DESC;
```

#### 📤 Upload Sessions — Active & Expired

```sql
SELECT
    s.session_id,
    v.title AS video_title,
    s.status,
    pg_size_pretty(s.file_size_bytes) AS uploaded_size,
    s.total_chunks,
    s.uploaded_chunks,
    s.expires_at,
    CASE
        WHEN s.status = 'ACTIVE' AND s.expires_at < NOW() THEN '⚠️ EXPIRED (not cleaned up)'
        WHEN s.status = 'ACTIVE' THEN '✅ ACTIVE'
        WHEN s.status = 'COMPLETED' THEN '✔️ DONE'
        ELSE s.status
    END AS session_health,
    s.created_at
FROM upload_sessions s
JOIN videos v ON s.video_id = v.id
ORDER BY s.created_at DESC;
```

#### 🎬 Video Variants — Transcoded Outputs

```sql
SELECT
    v.title,
    vv.resolution,
    vv.width || 'x' || vv.height AS dimensions,
    vv.bitrate_kbps || ' kbps' AS bitrate,
    pg_size_pretty(vv.file_size_bytes) AS variant_size,
    vv.manifest_path,
    vv.created_at
FROM video_variants vv
JOIN videos v ON vv.video_id = v.id
ORDER BY v.title, vv.width DESC;
```

#### 📈 Processing Statistics

```sql
SELECT
    status,
    COUNT(*) AS count,
    pg_size_pretty(SUM(file_size_bytes)) AS total_size,
    ROUND(AVG(duration_seconds)::numeric, 2) AS avg_duration_sec
FROM videos
GROUP BY status
ORDER BY count DESC;
```

#### 🚨 Failed Videos — Error Report

```sql
SELECT
    id,
    title,
    error_message,
    updated_at AS failed_at
FROM videos
WHERE status = 'FAILED'
ORDER BY updated_at DESC;
```

#### 🔄 Flyway Migration History

```sql
SELECT
    installed_rank,
    version,
    description,
    type,
    script,
    installed_on,
    success
FROM flyway_schema_history
ORDER BY installed_rank;
```

### Step 5: Create Saved Queries (Dashboard Tabs)

For a proper dashboard experience in DataGrip:

1. **Save each query** above as a file: `File → Save As` → name them:
   - `dashboard_videos.sql`
   - `dashboard_sessions.sql`
   - `dashboard_variants.sql`
   - `dashboard_stats.sql`
   - `dashboard_errors.sql`

2. **Pin the result tabs** — right-click a result tab → **Pin Tab**. This keeps your dashboard views open.

3. **Auto-refresh** — click the ⚙️ gear icon on any result tab → set **Auto-sync** to refresh periodically while testing.

4. **Column formatting** — right-click column headers to sort, filter, or add conditional coloring.

### Step 6: DataGrip Keyboard Shortcuts

| Action | Mac | Windows |
|--------|-----|--------|
| Execute query | `Cmd + Enter` | `Ctrl + Enter` |
| New console | `Cmd + Shift + Q` | `Ctrl + Shift + Q` |
| Navigate to table | `Cmd + N` | `Ctrl + N` |
| Refresh schema | `Cmd + Shift + R` | `Ctrl + Shift + R` |
| Toggle output panel | `Cmd + 4` | `Alt + 4` |

### Alternative: pgAdmin (Free)

If you don't have a DataGrip license, you can add pgAdmin as a Docker container:

```bash
docker run -d \
  --name streamforge-pgadmin \
  -e PGADMIN_DEFAULT_EMAIL=admin@streamforge.local \
  -e PGADMIN_DEFAULT_PASSWORD=admin \
  -p 5050:80 \
  --network streamforage_default \
  dpage/pgadmin4:latest
```

Then open http://localhost:5050 and add a server with:

| Field | Value |
|-------|-------|
| Host | `streamforge-postgres` (container name, since same network) |
| Port | `5432` (internal port) |
| Database | `streamforge` |
| Username | `streamforge` |
| Password | `streamforge_dev` |

---

## API Endpoints Reference

| # | Method | Endpoint | Description |
|---|--------|----------|-------------|
| 1 | `GET` | `/actuator/health` | Health check |
| 2 | `POST` | `/api/uploads/sessions` | Create upload session |
| 3 | `GET` | `/api/uploads/sessions/{sessionId}` | Get session status |
| 4 | `POST` | `/api/uploads/{sessionId}/file` | Upload video file |
| 5 | `GET` | `/api/videos` | List all videos (paginated) |
| 6 | `GET` | `/api/videos/{id}` | Get video details |
| 7 | `GET` | `/api/videos/{id}/status` | Get processing status |
| 8 | `GET` | `/api/videos/{id}/playback` | Get playback URLs |
| 9 | `GET` | `/api/videos/{id}/thumbnail` | Get thumbnail redirect |
| 10 | `DELETE` | `/api/videos/{id}` | Delete video |

---

## Postman Setup

### Environment Variables

Create a Postman environment called **"StreamForge Local"** with these variables:

| Variable | Initial Value | Description |
|----------|--------------|-------------|
| `base_url` | `http://localhost:8080` | API base URL |
| `session_id` | *(empty)* | Auto-set after creating session |
| `video_id` | *(empty)* | Auto-set after uploading |

### Collection Structure

```
📁 StreamForge API Tests
├── 📁 1. Health & Smoke
│   └── GET Health Check
├── 📁 2. Upload Session
│   ├── POST Create Session — Valid
│   ├── POST Create Session — Missing Title
│   ├── POST Create Session — Invalid Content Type
│   └── GET  Get Session Status
├── 📁 3. File Upload
│   ├── POST Upload Video — Valid MP4
│   ├── POST Upload Video — Invalid File Type
│   ├── POST Upload Video — Empty File
│   └── POST Upload Video — Expired Session
├── 📁 4. Videos
│   ├── GET  List Videos — Default Pagination
│   ├── GET  List Videos — Custom Page Size
│   ├── GET  Video Details
│   ├── GET  Video Details — Not Found
│   ├── GET  Processing Status
│   ├── GET  Playback URLs
│   ├── GET  Thumbnail
│   └── DELETE Delete Video
└── 📁 5. Error Handling
    ├── GET  Invalid UUID
    ├── GET  Non-existent Video
    └── POST Validation Errors
```

---

## Test Cases

---

### 1. Health Check

#### TC-01: Application Health

```
GET {{base_url}}/actuator/health
```

**Expected Response:** `200 OK`

```json
{
  "status": "UP"
}
```

**Postman Tests Script:**

```javascript
pm.test("Status is 200", () => pm.response.to.have.status(200));
pm.test("Status is UP", () => {
    const body = pm.response.json();
    pm.expect(body.status).to.eql("UP");
});
```

---

### 2. Upload Session Tests

#### TC-02: Create Upload Session — Valid

```
POST {{base_url}}/api/uploads/sessions
Content-Type: application/json
```

**Body:**

```json
{
  "title": "My First Video",
  "description": "A sample test video upload",
  "contentType": "video/mp4"
}
```

**Expected Response:** `201 Created`

```json
{
  "sessionId": "uuid-here",
  "videoId": "uuid-here",
  "status": "ACTIVE",
  "expiresAt": "2026-05-27T...",
  "createdAt": "2026-05-26T..."
}
```

**Postman Tests Script:**

```javascript
pm.test("Status is 201", () => pm.response.to.have.status(201));
pm.test("Session is ACTIVE", () => {
    const body = pm.response.json();
    pm.expect(body.status).to.eql("ACTIVE");
    pm.expect(body.sessionId).to.not.be.null;
    pm.expect(body.videoId).to.not.be.null;
    // Save for next requests
    pm.environment.set("session_id", body.sessionId);
    pm.environment.set("video_id", body.videoId);
});
```

---

#### TC-03: Create Upload Session — Missing Title (Validation Error)

```
POST {{base_url}}/api/uploads/sessions
Content-Type: application/json
```

**Body:**

```json
{
  "description": "No title provided",
  "contentType": "video/mp4"
}
```

**Expected Response:** `400 Bad Request`

```json
{
  "code": "VALIDATION_ERROR",
  "message": "title: Title is required",
  "timestamp": "..."
}
```

**Postman Tests Script:**

```javascript
pm.test("Status is 400", () => pm.response.to.have.status(400));
pm.test("Error code is VALIDATION_ERROR", () => {
    pm.expect(pm.response.json().code).to.eql("VALIDATION_ERROR");
});
```

---

#### TC-04: Create Upload Session — Invalid Content Type

```
POST {{base_url}}/api/uploads/sessions
Content-Type: application/json
```

**Body:**

```json
{
  "title": "Bad Content Type",
  "contentType": "image/png"
}
```

**Expected Response:** `400 Bad Request`

```json
{
  "code": "INVALID_FILE_TYPE",
  "message": "Invalid file type: image/png. Allowed: video/mp4, video/webm, video/quicktime, video/x-msvideo, video/x-matroska",
  "timestamp": "..."
}
```

**Postman Tests Script:**

```javascript
pm.test("Status is 400", () => pm.response.to.have.status(400));
pm.test("Error code is INVALID_FILE_TYPE", () => {
    pm.expect(pm.response.json().code).to.eql("INVALID_FILE_TYPE");
});
```

---

#### TC-05: Get Upload Session Status

```
GET {{base_url}}/api/uploads/sessions/{{session_id}}
```

**Expected Response:** `200 OK`

```json
{
  "sessionId": "{{session_id}}",
  "videoId": "{{video_id}}",
  "status": "ACTIVE",
  "expiresAt": "...",
  "createdAt": "..."
}
```

**Postman Tests Script:**

```javascript
pm.test("Status is 200", () => pm.response.to.have.status(200));
pm.test("Session matches", () => {
    const body = pm.response.json();
    pm.expect(body.sessionId).to.eql(pm.environment.get("session_id"));
    pm.expect(body.status).to.be.oneOf(["ACTIVE", "COMPLETED"]);
});
```

---

#### TC-06: Get Upload Session — Non-Existent

```
GET {{base_url}}/api/uploads/sessions/00000000-0000-0000-0000-000000000000
```

**Expected Response:** `410 Gone`

```json
{
  "code": "SESSION_EXPIRED",
  "message": "Upload session expired or not found: 00000000-0000-0000-0000-000000000000",
  "timestamp": "..."
}
```

---

### 3. File Upload Tests

#### TC-07: Upload Video File — Valid MP4

> **Prerequisite:** Run TC-02 first to get a `session_id`.

```
POST {{base_url}}/api/uploads/{{session_id}}/file
Content-Type: multipart/form-data
```

**Body (form-data):**

| Key | Type | Value |
|-----|------|-------|
| `file` | File | Select any `.mp4` file from your machine |

**Expected Response:** `202 Accepted`

```json
{
  "id": "uuid-here",
  "title": "My First Video",
  "status": "UPLOADED",
  "thumbnailUrl": null,
  "durationSeconds": null,
  "resolution": null,
  "fileSizeBytes": 12345678,
  "createdAt": "..."
}
```

**Postman Tests Script:**

```javascript
pm.test("Status is 202", () => pm.response.to.have.status(202));
pm.test("Video is UPLOADED", () => {
    const body = pm.response.json();
    pm.expect(body.status).to.eql("UPLOADED");
    pm.expect(body.fileSizeBytes).to.be.above(0);
    pm.environment.set("video_id", body.id);
});
```

---

#### TC-08: Upload Video — Invalid File Type (e.g., .txt)

```
POST {{base_url}}/api/uploads/{{session_id}}/file
Content-Type: multipart/form-data
```

**Body (form-data):**

| Key | Type | Value |
|-----|------|-------|
| `file` | File | Select a `.txt` or `.jpg` file |

**Expected Response:** `400 Bad Request`

```json
{
  "code": "INVALID_FILE_TYPE",
  "message": "Invalid file type: text/plain. Allowed: ...",
  "timestamp": "..."
}
```

---

#### TC-09: Upload Video — Reuse Completed Session

> Use the same `session_id` from TC-07 (already completed).

```
POST {{base_url}}/api/uploads/{{session_id}}/file
```

**Expected Response:** `410 Gone`

```json
{
  "code": "SESSION_EXPIRED",
  "message": "Upload session expired or not found: ...",
  "timestamp": "..."
}
```

---

### 4. Video Listing Tests

#### TC-10: List Videos — Default Pagination

```
GET {{base_url}}/api/videos
```

**Expected Response:** `200 OK`

```json
{
  "content": [
    {
      "id": "...",
      "title": "My First Video",
      "status": "UPLOADED | PROCESSING | PROCESSED | FAILED",
      "thumbnailUrl": "...",
      "durationSeconds": 30.5,
      "resolution": "1920x1080",
      "fileSizeBytes": 12345678,
      "createdAt": "..."
    }
  ],
  "totalElements": 1,
  "totalPages": 1,
  "size": 20,
  "number": 0,
  "first": true,
  "last": true
}
```

**Postman Tests Script:**

```javascript
pm.test("Status is 200", () => pm.response.to.have.status(200));
pm.test("Response is paginated", () => {
    const body = pm.response.json();
    pm.expect(body).to.have.property("content");
    pm.expect(body).to.have.property("totalElements");
    pm.expect(body).to.have.property("totalPages");
    pm.expect(body.content).to.be.an("array");
});
```

---

#### TC-11: List Videos — Custom Page Size

```
GET {{base_url}}/api/videos?page=0&size=5&sort=createdAt,desc
```

**Expected Response:** `200 OK` — with `size: 5` in response.

**Postman Tests Script:**

```javascript
pm.test("Page size is 5", () => {
    pm.expect(pm.response.json().size).to.eql(5);
});
```

---

#### TC-12: List Videos — Empty Database

> Run this test before uploading any videos (or after deleting all).

```
GET {{base_url}}/api/videos
```

**Expected Response:** `200 OK`

```json
{
  "content": [],
  "totalElements": 0,
  "empty": true
}
```

---

### 5. Video Detail Tests

#### TC-13: Get Video Details — Valid ID

```
GET {{base_url}}/api/videos/{{video_id}}
```

**Expected Response:** `200 OK`

```json
{
  "id": "...",
  "title": "My First Video",
  "description": "A sample test video upload",
  "status": "PROCESSED",
  "originalFilename": "sample.mp4",
  "fileSizeBytes": 12345678,
  "durationSeconds": 30.5,
  "width": 1920,
  "height": 1080,
  "fps": 29.97,
  "codec": "h264",
  "bitrateKbps": 5000,
  "audioCodec": "aac",
  "thumbnailUrl": "https://...",
  "errorMessage": null,
  "variants": [
    {
      "resolution": "1080p",
      "width": 1920,
      "height": 1080,
      "bitrateKbps": 5000,
      "playbackUrl": "https://..."
    },
    {
      "resolution": "720p",
      "width": 1280,
      "height": 720,
      "bitrateKbps": 2500,
      "playbackUrl": "https://..."
    }
  ],
  "createdAt": "...",
  "updatedAt": "..."
}
```

**Postman Tests Script:**

```javascript
pm.test("Status is 200", () => pm.response.to.have.status(200));
pm.test("Video has required fields", () => {
    const body = pm.response.json();
    pm.expect(body.id).to.not.be.null;
    pm.expect(body.title).to.not.be.empty;
    pm.expect(body.status).to.be.oneOf(["PENDING", "UPLOADED", "PROCESSING", "PROCESSED", "FAILED"]);
    pm.expect(body.createdAt).to.not.be.null;
});
```

---

#### TC-14: Get Video Details — Not Found

```
GET {{base_url}}/api/videos/00000000-0000-0000-0000-000000000000
```

**Expected Response:** `404 Not Found`

```json
{
  "code": "NOT_FOUND",
  "message": "Video not found: 00000000-0000-0000-0000-000000000000",
  "timestamp": "..."
}
```

**Postman Tests Script:**

```javascript
pm.test("Status is 404", () => pm.response.to.have.status(404));
pm.test("Error code is NOT_FOUND", () => {
    pm.expect(pm.response.json().code).to.eql("NOT_FOUND");
});
```

---

#### TC-15: Get Video Details — Invalid UUID Format

```
GET {{base_url}}/api/videos/not-a-valid-uuid
```

**Expected Response:** `400 Bad Request`

---

### 6. Processing Status Tests

#### TC-16: Get Processing Status

```
GET {{base_url}}/api/videos/{{video_id}}/status
```

**Expected Response:** `200 OK`

```json
{
  "videoId": "...",
  "status": "PROCESSING",
  "errorMessage": null,
  "updatedAt": "..."
}
```

**Postman Tests Script:**

```javascript
pm.test("Status is 200", () => pm.response.to.have.status(200));
pm.test("Has valid processing status", () => {
    const body = pm.response.json();
    pm.expect(body.status).to.be.oneOf(["PENDING", "UPLOADED", "PROCESSING", "PROCESSED", "FAILED"]);
    pm.expect(body.videoId).to.eql(pm.environment.get("video_id"));
});
```

---

#### TC-17: Poll Processing Status Until Complete

> Use this to monitor async processing. Set up a Postman Runner or manually re-send.

```
GET {{base_url}}/api/videos/{{video_id}}/status
```

**Check progression:** `UPLOADED → PROCESSING → PROCESSED` (or `FAILED`)

**Postman Tests Script (for runner):**

```javascript
const body = pm.response.json();
if (body.status === "PROCESSING" || body.status === "UPLOADED") {
    // Not done yet — will re-run in collection runner
    postman.setNextRequest("TC-17: Poll Processing Status");
} else {
    pm.test("Processing completed", () => {
        pm.expect(body.status).to.be.oneOf(["PROCESSED", "FAILED"]);
    });
    if (body.status === "FAILED") {
        console.log("Error:", body.errorMessage);
    }
}
```

---

### 7. Playback Tests

#### TC-18: Get Playback URLs — Processed Video

> **Prerequisite:** Video must be in `PROCESSED` status.

```
GET {{base_url}}/api/videos/{{video_id}}/playback
```

**Expected Response:** `200 OK`

```json
{
  "videoId": "...",
  "title": "My First Video",
  "masterManifestUrl": "https://localhost:9000/streamforge-processed/...",
  "variants": [
    {
      "resolution": "1080p",
      "width": 1920,
      "height": 1080,
      "bitrateKbps": 5000,
      "playbackUrl": "https://..."
    }
  ],
  "thumbnailUrl": "https://..."
}
```

**Postman Tests Script:**

```javascript
pm.test("Status is 200", () => pm.response.to.have.status(200));
pm.test("Has master manifest", () => {
    const body = pm.response.json();
    pm.expect(body.masterManifestUrl).to.include("master.m3u8");
    pm.expect(body.variants).to.be.an("array").that.is.not.empty;
});
pm.test("Variants have playback URLs", () => {
    const variants = pm.response.json().variants;
    variants.forEach(v => {
        pm.expect(v.playbackUrl).to.include("playlist.m3u8");
        pm.expect(v.resolution).to.be.oneOf(["1080p", "720p", "480p"]);
    });
});
```

---

#### TC-19: Get Playback URLs — Video Not Ready

> Use a video that is still `PROCESSING` or `UPLOADED`.

```
GET {{base_url}}/api/videos/{{video_id}}/playback
```

**Expected Response:** `409 Conflict`

```json
{
  "code": "CONFLICT",
  "message": "Video not ready. Status: PROCESSING",
  "timestamp": "..."
}
```

---

### 8. Thumbnail Tests

#### TC-20: Get Thumbnail — Processed Video

```
GET {{base_url}}/api/videos/{{video_id}}/thumbnail
```

**Expected Response:** `302 Found` (redirect to MinIO presigned URL)

- Check the `Location` header — it should contain a presigned MinIO URL.

**Postman Tests Script:**

```javascript
pm.test("Status is 302 redirect", () => pm.response.to.have.status(302));
pm.test("Location header has presigned URL", () => {
    const location = pm.response.headers.get("Location");
    pm.expect(location).to.include("streamforge-processed");
    pm.expect(location).to.include("poster.jpg");
});
```

> **Note:** Disable "Automatically follow redirects" in Postman Settings to see the 302.

---

#### TC-21: Get Thumbnail — Not Available

```
GET {{base_url}}/api/videos/00000000-0000-0000-0000-000000000000/thumbnail
```

**Expected Response:** `404 Not Found`

---

### 9. Delete Tests

#### TC-22: Delete Video — Valid

> **Caution:** This permanently deletes the video and all associated storage objects.

```
DELETE {{base_url}}/api/videos/{{video_id}}
```

**Expected Response:** `204 No Content` (empty body)

**Postman Tests Script:**

```javascript
pm.test("Status is 204", () => pm.response.to.have.status(204));
pm.test("Body is empty", () => {
    pm.expect(pm.response.text()).to.be.empty;
});
```

---

#### TC-23: Delete Video — Verify Deletion

```
GET {{base_url}}/api/videos/{{video_id}}
```

**Expected Response:** `404 Not Found`

---

#### TC-24: Delete Video — Non-Existent

```
DELETE {{base_url}}/api/videos/00000000-0000-0000-0000-000000000000
```

**Expected Response:** `404 Not Found`

---

### 10. Error Handling Tests

#### TC-25: Invalid JSON Body

```
POST {{base_url}}/api/uploads/sessions
Content-Type: application/json
```

**Body (raw):**

```
{ this is not valid json }
```

**Expected Response:** `400 Bad Request`

---

#### TC-26: Title Exceeds Max Length (500 chars)

```
POST {{base_url}}/api/uploads/sessions
Content-Type: application/json
```

**Body:**

```json
{
  "title": "AAAAAAAAAA... (501+ characters)",
  "contentType": "video/mp4"
}
```

**Expected Response:** `400 Bad Request`

```json
{
  "code": "VALIDATION_ERROR",
  "message": "title: Title must be at most 500 characters",
  "timestamp": "..."
}
```

---

#### TC-27: Missing Content-Type Header

```
POST {{base_url}}/api/uploads/sessions
(no Content-Type header)
```

**Body (raw):**

```json
{"title": "Test", "contentType": "video/mp4"}
```

**Expected Response:** `415 Unsupported Media Type`

---

#### TC-28: Wrong HTTP Method

```
PUT {{base_url}}/api/videos
```

**Expected Response:** `405 Method Not Allowed`

---

## Full End-to-End Workflow

Run these tests **in sequence** for a complete E2E flow:

```
Step 1 → TC-01  Health Check
Step 2 → TC-02  Create Upload Session ✓ saves session_id & video_id
Step 3 → TC-05  Verify Session is ACTIVE
Step 4 → TC-07  Upload Video File ✓ triggers async processing
Step 5 → TC-16  Check Status (should be PROCESSING)
Step 6 → TC-17  Poll Status Until PROCESSED (wait ~30-120s)
Step 7 → TC-13  Get Full Video Details (metadata populated)
Step 8 → TC-18  Get Playback URLs (HLS manifest)
Step 9 → TC-20  Get Thumbnail (302 redirect)
Step 10 → TC-10 List All Videos (video appears)
Step 11 → TC-22 Delete Video
Step 12 → TC-23 Verify Deletion (404)
```

### Postman Collection Runner

1. Import all test cases into a Postman collection
2. Set the **"StreamForge Local"** environment
3. Run the collection with a **1-second delay** between requests
4. For TC-17 (polling), set **iteration count = 10** with **3-second delay**

---

## Troubleshooting

### App won't start — "role streamforge does not exist"

You have a local PostgreSQL running on port 5432 intercepting connections. StreamForge uses port **5433**. If you changed it, verify `application.yml`:

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5433/streamforge
```

### Docker containers won't start

```bash
# Reset everything
docker compose down -v
docker compose up -d
```

### Processing fails — "FFmpeg/FFprobe not found"

```bash
# Install FFmpeg
brew install ffmpeg

# Verify
which ffmpeg    # should output /opt/homebrew/bin/ffmpeg
which ffprobe   # should output /opt/homebrew/bin/ffprobe
```

### Swagger UI shows "Failed to load API definition"

Ensure the app is running and hit:

```bash
curl http://localhost:8080/v3/api-docs | head -1
```

If it returns JSON starting with `{"openapi":"3.1.0"`, Swagger is working. Hard-refresh the browser (`Cmd + Shift + R`).

### MinIO buckets not created

```bash
docker exec streamforge-minio mc alias set local http://localhost:9000 minioadmin minioadmin123
docker exec streamforge-minio mc mb local/streamforge-raw --ignore-existing
docker exec streamforge-minio mc mb local/streamforge-processed --ignore-existing
```

### Port conflicts

```bash
# Check what's using a port
lsof -i :8080   # App
lsof -i :5433   # PostgreSQL (Docker)
lsof -i :9000   # MinIO API
lsof -i :9001   # MinIO Console
```

---

## Quick Reference — cURL Commands

```bash
# 1. Health
curl http://localhost:8080/actuator/health

# 2. Create session
curl -X POST http://localhost:8080/api/uploads/sessions \
  -H "Content-Type: application/json" \
  -d '{"title":"Test Video","description":"Testing","contentType":"video/mp4"}'

# 3. Upload file (replace SESSION_ID and path)
curl -X POST http://localhost:8080/api/uploads/SESSION_ID/file \
  -F "file=@/path/to/your/video.mp4"

# 4. List videos
curl http://localhost:8080/api/videos

# 5. Get video details
curl http://localhost:8080/api/videos/VIDEO_ID

# 6. Check processing status
curl http://localhost:8080/api/videos/VIDEO_ID/status

# 7. Get playback URLs
curl http://localhost:8080/api/videos/VIDEO_ID/playback

# 8. Delete video
curl -X DELETE http://localhost:8080/api/videos/VIDEO_ID
```

---

*Last updated: 2026-05-26*
