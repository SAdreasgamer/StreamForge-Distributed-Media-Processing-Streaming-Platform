CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

CREATE TABLE videos (
    id                UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    title             VARCHAR(500)   NOT NULL,
    description       TEXT,
    original_filename VARCHAR(500)   NOT NULL,
    content_type      VARCHAR(100)   NOT NULL,
    file_size_bytes   BIGINT         NOT NULL DEFAULT 0,
    status            VARCHAR(20)    NOT NULL DEFAULT 'PENDING',
    storage_path      VARCHAR(1000),
    duration_seconds  DOUBLE PRECISION,
    width             INTEGER,
    height            INTEGER,
    fps               DOUBLE PRECISION,
    codec             VARCHAR(50),
    bitrate_kbps      INTEGER,
    audio_codec       VARCHAR(50),
    thumbnail_path    VARCHAR(1000),
    error_message     TEXT,
    created_at        TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_videos_status ON videos(status);
CREATE INDEX idx_videos_created_at ON videos(created_at DESC);
