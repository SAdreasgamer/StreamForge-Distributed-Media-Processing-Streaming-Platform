CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

CREATE TABLE upload_sessions (
    session_id      UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    video_id        UUID         NOT NULL,
    status          VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    total_chunks    INTEGER      NOT NULL DEFAULT 0,
    uploaded_chunks INTEGER      NOT NULL DEFAULT 0,
    file_size_bytes BIGINT       NOT NULL DEFAULT 0,
    content_type    VARCHAR(100),
    expires_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_upload_sessions_video ON upload_sessions(video_id);
CREATE INDEX idx_upload_sessions_status ON upload_sessions(status);
CREATE INDEX idx_upload_sessions_expires ON upload_sessions(expires_at) WHERE status = 'ACTIVE';
