# StreamForge — Microservices Startup & Testing Guide

> Detailed guide to start, run, and validate the distributed StreamForge video processing and streaming platform.

---

## Table of Contents

- [System Architecture & Port Map](#system-architecture--port-map)
- [Prerequisites](#prerequisites)
- [Step 1: Start Infrastructure (Docker Compose)](#step-1-start-infrastructure-docker-compose)
- [Step 2: Start Microservices (Local JVM)](#step-2-start-microservices-local-jvm)
- [Step 3: Verify Deployment & Discovery](#step-3-verify-deployment--discovery)
- [API Gateway Routing Table](#api-gateway-routing-table)
- [Test Cases](#test-cases)
  - [1. Health Check](#1-health-check)
  - [2. Upload Session Tests](#2-upload-session-tests)
  - [3. File Upload Tests](#3-file-upload-tests)
  - [4. Video Listing Tests](#4-video-listing-tests)
  - [5. Video Detail Tests](#5-video-detail-tests)
  - [6. Processing Status Tests](#6-processing-status-tests)
  - [7. Playback & Thumbnail Tests](#7-playback--thumbnail-tests)
  - [8. Delete Tests](#8-delete-tests)
  - [9. Error Handling & Validation Tests](#9-error-handling--validation-tests)
- [Full End-to-End Test Workflow](#full-end-to-end-test-workflow)
- [Troubleshooting](#troubleshooting)

---

## System Architecture & Port Map

The platform is split into 6 independent JVM services and 6 infrastructure containers:

| Service / Container | Internal/Host Port | Database / Storage Link | Description |
| :--- | :---: | :--- | :--- |
| **Eureka Server** | `8761` | *N/A* | Dynamic service discovery registry |
| **API Gateway** | `8080` | *N/A* | Single entry point, path routing, CORS |
| **Upload Service** | `8081` | PostgreSQL `5433` (`upload_db`) | Session setup & raw media file ingestion |
| **Catalog Service** | `8082` | PostgreSQL `5434` (`catalog_db`) & Redis | Video catalog metadata & variant listings |
| **Notification Service**| `8083` | Kafka Listener | WebSocket server for real-time status broadcasts |
| **Worker Service** | `8084` | PostgreSQL `5435` (`worker_db`) | Headless transcoder, FFmpeg/FFprobe runner |
| **Redis** | `6379` | Cache & type-hinted serialization | Video detail caching & eviction listener |
| **Kafka Broker** | `9092` | Event Backbone | Topic routing: `media.uploaded`, `media.processing.status` |
| **MinIO Object Store** | `9000` / `9001` | Raw & Processed Buckets | S3-compatible media file hosting |

---

## Prerequisites

Ensure the following tools are installed on your development machine:

* **Java 21+** (compiles to Java 21 bytecode)
* **Maven 3.9+** (wrapper `./mvnw` is included in root)
* **Docker Desktop** (to run isolated databases, Kafka, Redis, and MinIO)
* **FFmpeg 6.0+** (required on system path for the Worker Service to transcode videos)

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

## Step 1: Start Infrastructure (Docker Compose)

The microservices rely on PostgreSQL databases, Redis, Kafka, and MinIO. Start them in Docker:

```bash
# Navigate to the workspace root
cd /Users/shreyanand/dev_proj/streamForage

# Spin up the containers in detached mode
docker compose up -d
```

### Verify Container Health
Wait ~20 seconds and run:
```bash
docker ps --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}"
```
Expected output showing **healthy** instances:
```text
NAMES                          STATUS                    PORTS
streamforge-worker-postgres    Up X seconds (healthy)    0.0.0.0:5435->5432/tcp
streamforge-catalog-postgres   Up X seconds (healthy)    0.0.0.0:5434->5432/tcp
streamforge-upload-postgres    Up X seconds (healthy)    0.0.0.0:5433->5432/tcp
streamforge-redis              Up X seconds (healthy)    0.0.0.0:6379->6379/tcp
streamforge-minio              Up X seconds (healthy)    0.0.0.0:9000-9001->9000-9001/tcp
streamforge-kafka              Up X seconds (healthy)    0.0.0.0:9092->9092/tcp
```

---

## Step 2: Start Microservices (Local JVM)

Start the microservices in the following chronological order to allow Eureka registration to sync smoothly. You can launch them in separate terminal tabs or runs:

### 1. Eureka Discovery Server
```bash
./mvnw -pl eureka-server spring-boot:run
```
*Wait for: `Started EurekaServerApplication in X.XX seconds`*

### 2. Upload Service
```bash
./mvnw -pl upload-service spring-boot:run
```

### 3. Catalog Service
```bash
./mvnw -pl catalog-service spring-boot:run
```

### 4. Worker Service
```bash
./mvnw -pl worker-service spring-boot:run
```

### 5. Notification Service
```bash
./mvnw -pl notification-service spring-boot:run
```

### 6. API Gateway
```bash
./mvnw -pl api-gateway spring-boot:run
```
*Wait for: `Netty started on port 8080 (http)`*

---

## Step 3: Verify Deployment & Discovery

1. **Eureka Dashboard**: Open `http://localhost:8761` in your browser. You should see 5 registered application instances:
   - `API-GATEWAY`
   - `UPLOAD-SERVICE`
   - `CATALOG-SERVICE`
   - `WORKER-SERVICE`
   - `NOTIFICATION-SERVICE`
2. **MinIO Admin Console**: Open `http://localhost:9001` (User: `minioadmin` / Pass: `minioadmin123`). Ensure `streamforge-raw` and `streamforge-processed` buckets exist.
3. **Gateway Actuator Health Check**:
   ```bash
   curl http://localhost:8080/actuator/health
   # Expected: {"status":"UP"}
   ```

---

## API Gateway Routing Table

All external client applications should connect exclusively to the **API Gateway** on port `8080`. The Gateway routes traffic downstream:

| Path Prefix | Destination Service | Protocol | Key Features |
| :--- | :--- | :---: | :--- |
| `/api/uploads/**` | `UPLOAD-SERVICE` | HTTP | Ingestion sessions and file chunk streams |
| `/api/videos/**` | `CATALOG-SERVICE` | HTTP | Catalog listing, metadata retrieval, deletions |
| `/api/stream/**` | `CATALOG-SERVICE` | HTTP | HLS chunk playback routing |
| `/ws/**` | `NOTIFICATION-SERVICE`| WebSocket (STOMP) | Real-time status update broadcasts |

---

## Test Cases

These test cases can be run using Postman, curl, or any REST client. All requests must route through port `8080`.

---

### 1. Health Check

#### TC-01: Gateway Health Check
```http
GET http://localhost:8080/actuator/health
```
**Expected Response:** `200 OK`
```json
{
  "status": "UP"
}
```

---

### 2. Upload Session Tests

#### TC-02: Create Upload Session — Valid
```http
POST http://localhost:8080/api/uploads/sessions
Content-Type: application/json

{
  "title": "Verification Test Video",
  "description": "Distributed microservices testing",
  "contentType": "video/mp4"
}
```
**Expected Response:** `201 Created`
```json
{
  "sessionId": "8cfa2303-a5a7-4e31-8c7c-8e05c722064a",
  "videoId": "5b0b3ba8-e597-4d0c-a7ba-6114eecebc76",
  "status": "ACTIVE",
  "expiresAt": "2026-05-30T21:20:00Z",
  "createdAt": "2026-05-29T21:20:00Z"
}
```
*(Save `sessionId` and `videoId` for subsequent requests).*

#### TC-03: Create Session — Missing Title
```http
POST http://localhost:8080/api/uploads/sessions
Content-Type: application/json

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
  "timestamp": "2026-05-30T..."
}
```

#### TC-04: Create Session — Invalid Content Type
```http
POST http://localhost:8080/api/uploads/sessions
Content-Type: application/json

{
  "title": "Invalid Content Type Video",
  "contentType": "image/png"
}
```
**Expected Response:** `400 Bad Request`
```json
{
  "code": "INVALID_FILE_TYPE",
  "message": "Invalid file type: image/png. Allowed: video/mp4, video/webm, video/quicktime, video/x-msvideo, video/x-matroska",
  "timestamp": "2026-05-30T..."
}
```

#### TC-05: Get Upload Session Status — Valid
```http
GET http://localhost:8080/api/uploads/sessions/{{sessionId}}
```
**Expected Response:** `200 OK`
```json
{
  "sessionId": "8cfa2303-a5a7-4e31-8c7c-8e05c722064a",
  "videoId": "5b0b3ba8-e597-4d0c-a7ba-6114eecebc76",
  "status": "ACTIVE",
  "expiresAt": "...",
  "createdAt": "..."
}
```

#### TC-06: Get Upload Session Status — Non-existent
```http
GET http://localhost:8080/api/uploads/sessions/00000000-0000-0000-0000-000000000000
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
> Note: Specify the file mime-type explicitly as `video/mp4`. If using curl, use `;type=video/mp4`.
```http
POST http://localhost:8080/api/uploads/{{sessionId}}/file
Content-Type: multipart/form-data

file: [test_video.mp4]
```
**Expected Response:** `202 Accepted`
```json
{
  "id": "5b0b3ba8-e597-4d0c-a7ba-6114eecebc76",
  "title": null,
  "status": "UPLOADED",
  "thumbnailUrl": null,
  "durationSeconds": null,
  "resolution": null,
  "fileSizeBytes": 21590,
  "createdAt": "2026-05-29T..."
}
```

#### TC-08: Upload Video — Invalid File Type (.txt)
```http
POST http://localhost:8080/api/uploads/{{sessionId}}/file
Content-Type: multipart/form-data

file: [invalid_type.txt]
```
**Expected Response:** `400 Bad Request`
```json
{
  "code": "INVALID_FILE_TYPE",
  "message": "Invalid file type: text/plain. Allowed: video/mp4, ...",
  "timestamp": "..."
}
```

#### TC-09: Upload Video — Reuse Completed Session
```http
POST http://localhost:8080/api/uploads/{{sessionId}}/file
Content-Type: multipart/form-data

file: [test_video.mp4]
```
**Expected Response:** `410 Gone`
```json
{
  "code": "SESSION_EXPIRED",
  "message": "Upload session expired or not found: {{sessionId}}",
  "timestamp": "..."
}
```

---

### 4. Video Listing Tests

#### TC-10: List Videos — Default Pagination
```http
GET http://localhost:8080/api/videos
```
**Expected Response:** `200 OK`
```json
{
  "content": [
    {
      "id": "5b0b3ba8-e597-4d0c-a7ba-6114eecebc76",
      "title": "Verification Test Video",
      "status": "PROCESSED",
      "thumbnailUrl": "http://localhost:9000/...",
      "durationSeconds": 1.0,
      "resolution": "640x360",
      "fileSizeBytes": 21590,
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

#### TC-11: List Videos — Custom Page Size
```http
GET http://localhost:8080/api/videos?page=0&size=5&sort=createdAt,desc
```
**Expected Response:** `200 OK` (returns JSON where `"size": 5`).

---

### 5. Video Detail Tests

#### TC-12: Get Video Details — Valid ID
```http
GET http://localhost:8080/api/videos/{{videoId}}
```
**Expected Response:** `200 OK`
```json
{
  "id": "5b0b3ba8-e597-4d0c-a7ba-6114eecebc76",
  "title": "Verification Test Video",
  "description": "Distributed microservices testing",
  "status": "PROCESSED",
  "originalFilename": "test_video.mp4",
  "fileSizeBytes": 21590,
  "durationSeconds": 1.0,
  "width": 640,
  "height": 360,
  "fps": 30.0,
  "codec": "h264",
  "bitrateKbps": 521,
  "audioCodec": "aac",
  "thumbnailUrl": "http://localhost:9000/streamforge-processed/5b0b3ba8-e597-4d0c-a7ba-6114eecebc76/thumbnails/poster.jpg?...",
  "errorMessage": null,
  "variants": [
    {
      "resolution": "1080p",
      "width": 1920,
      "height": 1080,
      "bitrateKbps": 5000,
      "playbackUrl": "http://localhost:8080/api/stream/..."
    },
    {
      "resolution": "720p",
      "width": 1280,
      "height": 720,
      "bitrateKbps": 2500,
      "playbackUrl": "http://localhost:8080/api/stream/..."
    },
    {
      "resolution": "480p",
      "width": 854,
      "height": 480,
      "bitrateKbps": 1000,
      "playbackUrl": "http://localhost:8080/api/stream/..."
    }
  ],
  "createdAt": "...",
  "updatedAt": "..."
}
```
> **Redis Caching Note**: The first request fetches details from the PostgreSQL database container. Subsequent calls fetch the value directly from the Redis container. You will see response times drop to sub-40ms.

#### TC-13: Get Video Details — Not Found
```http
GET http://localhost:8080/api/videos/00000000-0000-0000-0000-000000000000
```
**Expected Response:** `404 Not Found`
```json
{
  "code": "NOT_FOUND",
  "message": "Video not found: 00000000-0000-0000-0000-000000000000",
  "timestamp": "..."
}
```

---

### 6. Processing Status Tests

#### TC-14: Get Processing Status
```http
GET http://localhost:8080/api/videos/{{videoId}}/status
```
**Expected Response:** `200 OK`
```json
{
  "videoId": "5b0b3ba8-e597-4d0c-a7ba-6114eecebc76",
  "status": "PROCESSING",
  "errorMessage": null,
  "updatedAt": "..."
}
```

#### TC-15: Poll Processing Status Until Complete
Set up a polling loop hitting `GET http://localhost:8080/api/videos/{{videoId}}/status`.
The status sequence should progress:
`PENDING` $\rightarrow$ `UPLOADED` $\rightarrow$ `PROCESSING` $\rightarrow$ `PROCESSED` (or `FAILED` if encoding fails).

---

### 7. Playback & Thumbnail Tests

#### TC-16: Get Playback URLs — Processed Video
```http
GET http://localhost:8080/api/videos/{{videoId}}/playback
```
**Expected Response:** `200 OK`
```json
{
  "videoId": "5b0b3ba8-e597-4d0c-a7ba-6114eecebc76",
  "title": "Verification Test Video",
  "masterManifestUrl": "http://localhost:9000/streamforge-processed/5b0b3ba8-e597-4d0c-a7ba-6114eecebc76/hls/master.m3u8?...",
  "variants": [
    {
      "resolution": "1080p",
      "width": 1920,
      "height": 1080,
      "bitrateKbps": 5000,
      "playbackUrl": "http://localhost:9000/streamforge-processed/5b0b3ba8-e597-4d0c-a7ba-6114eecebc76/hls/1080p/playlist.m3u8?..."
    },
    ...
  ],
  "thumbnailUrl": "http://localhost:9000/streamforge-processed/..."
}
```

#### TC-17: Get Playback URLs — Video Not Ready
> Target a video still in `PROCESSING` or `UPLOADED` state.
```http
GET http://localhost:8080/api/videos/{{videoId}}/playback
```
**Expected Response:** `409 Conflict`
```json
{
  "code": "CONFLICT",
  "message": "Video not ready. Status: PROCESSING",
  "timestamp": "..."
}
```

#### TC-18: Get Thumbnail — Redirect to Storage
```http
GET http://localhost:8080/api/videos/{{videoId}}/thumbnail
```
**Expected Response:** `302 Found` (redirect to MinIO presigned URL).
*Look for the `Location` header in the HTTP headers, which contains a presigned MinIO URL pointing to `poster.jpg`.*

---

### 8. Delete Tests

#### TC-19: Delete Video — Valid
```http
DELETE http://localhost:8080/api/videos/{{videoId}}
```
**Expected Response:** `204 No Content` (Empty body).
> **Storage Cleanup Check**: MinIO buckets should have deleted the directories `streamforge-raw/{{videoId}}` and `streamforge-processed/{{videoId}}`. Redis cache key `video:{{videoId}}:meta` is also evicted.

#### TC-20: Delete Video — Verify Deletion
```http
GET http://localhost:8080/api/videos/{{videoId}}
```
**Expected Response:** `404 Not Found`

#### TC-21: Delete Video — Non-existent
```http
DELETE http://localhost:8080/api/videos/00000000-0000-0000-0000-000000000000
```
**Expected Response:** `404 Not Found`

---

### 9. Error Handling & Validation Tests

#### TC-22: Invalid JSON Body
```http
POST http://localhost:8080/api/uploads/sessions
Content-Type: application/json

{ this is malformed json }
```
**Expected Response:** `400 Bad Request`

#### TC-23: Parameter Type Mismatch (Invalid UUID Format)
```http
GET http://localhost:8080/api/videos/not-a-valid-uuid
```
**Expected Response:** `400 Bad Request`
```json
{
  "code": "BAD_REQUEST",
  "message": "Parameter 'id' should be of type 'UUID'",
  "timestamp": "..."
}
```

#### TC-24: Title Exceeds Max Length (500 chars)
```http
POST http://localhost:8080/api/uploads/sessions
Content-Type: application/json

{
  "title": "A...[repeated 501 times]",
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

## Full End-to-End Test Workflow

Follow this sequence to test a complete video cycle end-to-end:

```text
Step 1: Check System Health                      --> TC-01
Step 2: Initialize Session                        --> TC-02 (saves sessionId & videoId)
Step 3: Verify Status is ACTIVE                  --> TC-05
Step 4: Upload Media File (triggers transcode)    --> TC-07
Step 5: Check status is PROCESSING               --> TC-14
Step 6: Poll status until status is PROCESSED    --> TC-15
Step 7: Retrieve Video Details (Redis test 1)    --> TC-12
Step 8: Retrieve Video Details again (cache hit) --> TC-12 (verify <40ms speed)
Step 9: Get HLS Playback URLs                    --> TC-16
Step 10: Fetch Thumbnail                         --> TC-18 (verify 302 location header)
Step 11: List catalog videos                     --> TC-10 (verify video is listed)
Step 12: Delete Video                            --> TC-19 (verify DB, MinIO & cache are wiped)
Step 13: Confirm deletion                        --> TC-20 (should return 404)
```

---

## Troubleshooting

### Docker Database Ports conflict
If your PostgreSQL containers fail to start, make sure you don't have local postgres engines running on the mapped host ports:
* `5433` (Upload Service DB)
* `5434` (Catalog Service DB)
* `5435` (Worker Service DB)
To release a port locally:
```bash
sudo lsof -i :5433
# kill the PID listed
```

### Video Stuck in `PENDING` or `UPLOADED`
If polling the status remains at `UPLOADED` and never transitions to `PROCESSING`/`PROCESSED`:
1. Check that the **Worker Service** is running locally and connected to Kafka on `localhost:9092`.
2. Check the logs of the Worker Service. If it throws `FFmpeg/FFprobe not found`, ensure `ffmpeg` is installed on your host system path.
3. Check the **Kafka** logs using:
   ```bash
   docker logs streamforge-kafka
   ```

### "No mapping for POST /api/uploads/..."
If you get a `404` or `500` error during file uploads:
1. Verify the URL is `/api/uploads/{sessionId}/file` (NOT `/api/uploads/sessions/{sessionId}/file`).
2. Make sure you pass the correct multipart parameter name (`"file"`).
3. If running curl, specify `type=video/mp4` explicitly to pass `FileValidator` validations.
