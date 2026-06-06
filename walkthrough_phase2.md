# StreamForge — Phase 2: Decoupled Kafka & Async Worker Walkthrough

We have successfully migrated the StreamForge platform from a monolith into a decoupled, event-driven microservices architecture using Apache Kafka and a Multi-Module Maven project structure.

## Changes Made

### 1. Multi-Module Project Structure
We transformed the monolith root directory into a parent POM (`packaging: pom`) and created three submodules:
1. **`common`**: A library submodule housing shared JPA entities (`Video`, `UploadSession`, `VideoVariant`), enums, database migration scripts (Flyway), and object storage configs.
2. **`upload-service`**: Exposes client-facing REST APIs for upload sessions, files ingestion, video lists, and HLS streaming proxy. It publishes event payloads to Kafka.
3. **`worker-service`**: Headless backend consumer that listens to Kafka topics, runs FFmpeg/FFprobe transcoding pipelines synchronously on Kafka listener threads, updates statuses, and writes outputs back to object storage.

### 2. Kafka Decoupling & Event Producer
- Integrated `spring-kafka` into the project.
- Created `UploadEventProducer` in `upload-service` which publishes a JSON-serialized `VideoUploadedEvent` to the `media.uploaded` topic upon successful upload session completion.
- Replaced the direct, synchronous/asynchronous `ProcessingService` calls in `upload-service` with event publication.

### 3. Kafka Consumer & Idempotency Lock
- Created `MediaEventConsumer` in `worker-service` listening to the `media.uploaded` topic.
- Implemented **Idempotent processing** by creating a `processing_jobs` table in the shared database.
- Before starting a job, the consumer generates a unique key `videoId:TRANSCODE` and checks the database:
  - If a job is already `RUNNING` or `COMPLETED`, the duplicate Kafka message is safely discarded.
  - If a job is `FAILED` or new, it updates/inserts the state to `RUNNING` and proceeds.
  - Marks the job as `COMPLETED` on success and `FAILED` with error details on failure.

### 4. Resilient Error Handling & DLQ Configuration
- Configured a `DefaultErrorHandler` with a 3-attempt exponential backoff retry policy (initial interval: 2 seconds, multiplier: 2.0).
- Registered a `DeadLetterPublishingRecoverer` to route exhausted failures to the dead-letter topic `media.uploaded.DLT`.
- Implemented a DLT listener (`consumeVideoUploadedDlt`) inside `MediaEventConsumer` to log DLQ entries for manual review.
- Configured producer serializes inside `worker-service` (`application.yml`) to support publishing JSON-serialized event payloads to the DLT without casting errors.

---

## Validation Results

We verified the complete decoupled Kafka ingestion flow successfully:

### 1. Ingesting and Processing a Video
1. **Created a new upload session**:
   ```bash
   curl -X POST http://localhost:8080/api/uploads/sessions \
     -H "Content-Type: application/json" \
     -d '{"title":"Test Video 2","description":"Testing 2","contentType":"video/mp4"}'
   ```
   *Response*:
   ```json
   {"sessionId":"73733760-e353-4e4f-bcdd-5d497b8aee4a","videoId":"9b346c11-008c-411b-8ca4-9644499f1993", ...}
   ```

2. **Uploaded a 10-second video file**:
   ```bash
   curl -X POST http://localhost:8080/api/uploads/sessions/73733760-e353-4e4f-bcdd-5d497b8aee4a/file \
     -F "file=@test.mp4;type=video/mp4"
   ```
   *Response*: `202 Accepted` with state `UPLOADED`.

3. **Observed worker-service logs**:
   - Consumed the `media.uploaded` event.
   - Initialized and saved `ProcessingJob` state as `RUNNING`.
   - Extracted video metadata: `640x360 @ 30.0fps`.
   - Transcoded HLS quality variants (`1080p`, `720p`, `480p`).
   - Generated poster thumbnail image.
   - Updated `ProcessingJob` state to `COMPLETED` and `Video` state to `PROCESSED`.

4. **Retrieved processed details**:
   ```bash
   curl http://localhost:8080/api/videos/9b346c11-008c-411b-8ca4-9644499f1993
   ```
   *Response*: `PROCESSED` status with all variant details.

### 2. Idempotency Lock Verification
We simulated an event redelivery for video ID `555bea72-62ec-460b-a3b3-d7d9dce450ac` after it was completed:
- **Result**: The consumer correctly detected the completed status, logged `Discarding duplicate message. Processing job for video ID: 555bea72-62ec-460b-a3b3-d7d9dce450ac is currently COMPLETED`, and safely skipped processing.

### 3. Kafka DLQ Routing & Thumbnail Fix Verification
- **Issue Discovered**: When a video had a duration of exactly 5.0 seconds (common for short test videos), seeking to `00:00:05` to extract a poster thumbnail failed inside FFmpeg (exit code 234) because the timestamp was at the absolute end of the file.
- **Resilience Triggered**: As expected, the consumer failed, retried 3 times under the exponential backoff policy, and routed the message to `media.uploaded.DLT` where it was logged.
- **Fix Applied**: We adjusted [ThumbnailService.java](file:///Users/shreyanand/dev_proj/streamForage/worker-service/src/main/java/com/streamforge/service/ThumbnailService.java#L40-L44) to evaluate `video.getDurationSeconds() <= 5.0` (rather than strictly `< 5.0`). This dynamically shifts the extraction timestamp to 25% of the video duration for any video of 5 seconds or less, ensuring a valid frame is captured.
- **E2E Success**: After rebuilding and restarting the `worker-service`, we triggered reprocessing of the failed video using the `POST /api/videos/{id}/reprocess` endpoint. The worker consumed the new event, processed all HLS variants, successfully generated the thumbnail at `00:00:01`, and updated the database to `PROCESSED`.
