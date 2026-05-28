CREATE TABLE video_variants (
    variant_id      UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    video_id        UUID          NOT NULL REFERENCES videos(id) ON DELETE CASCADE,
    resolution      VARCHAR(20)   NOT NULL,
    width           INTEGER       NOT NULL,
    height          INTEGER       NOT NULL,
    bitrate_kbps    INTEGER       NOT NULL,
    manifest_path   VARCHAR(1000) NOT NULL,
    storage_path    VARCHAR(1000) NOT NULL,
    file_size_bytes BIGINT        NOT NULL DEFAULT 0,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_video_variants_video ON video_variants(video_id);
