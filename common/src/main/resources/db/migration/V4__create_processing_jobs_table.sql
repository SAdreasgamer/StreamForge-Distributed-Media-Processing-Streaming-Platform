CREATE TABLE processing_jobs (
    id UUID PRIMARY KEY,
    video_id UUID NOT NULL REFERENCES videos(id) ON DELETE CASCADE,
    job_type VARCHAR(50) NOT NULL, -- METADATA, TRANSCODE, THUMBNAIL
    status VARCHAR(20) NOT NULL,    -- PENDING, RUNNING, COMPLETED, FAILED
    retry_count INT DEFAULT 0,
    idempotency_key VARCHAR(255) UNIQUE NOT NULL,
    error_message TEXT,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);
CREATE INDEX idx_processing_jobs_video ON processing_jobs(video_id);
