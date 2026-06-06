function formatDuration(seconds) {
  if (!seconds) return '--';
  const mins = Math.floor(seconds / 60);
  const secs = Math.floor(seconds % 60);
  return `${mins}m ${secs}s`;
}

function formatBitrate(kbps) {
  if (!kbps) return '--';
  if (kbps >= 1000) return `${(kbps / 1000).toFixed(1)} Mbps`;
  return `${kbps} Kbps`;
}

function formatFileSize(bytes) {
  if (!bytes) return '--';
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  if (bytes < 1024 * 1024 * 1024) return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
  return `${(bytes / (1024 * 1024 * 1024)).toFixed(2)} GB`;
}

const METADATA_FIELDS = [
  { key: 'codec', label: 'Video Codec', icon: '🎥', format: (v) => v?.toUpperCase() || '--' },
  { key: 'audioCodec', label: 'Audio Codec', icon: '🔊', format: (v) => v?.toUpperCase() || '--' },
  { key: 'resolution', label: 'Resolution', icon: '📐', format: (_, d) => d.width && d.height ? `${d.width}×${d.height}` : '--' },
  { key: 'fps', label: 'Frame Rate', icon: '⚡', format: (v) => v ? `${v} FPS` : '--' },
  { key: 'bitrateKbps', label: 'Bitrate', icon: '📶', format: (v) => formatBitrate(v) },
  { key: 'durationSeconds', label: 'Duration', icon: '⏱️', format: (v) => formatDuration(v) },
  { key: 'fileSizeBytes', label: 'File Size', icon: '💾', format: (v) => formatFileSize(v) },
  { key: 'originalFilename', label: 'Filename', icon: '📁', format: (v) => v || '--' },
];

export default function MetadataPanel({ video }) {
  return (
    <div className="grid grid-cols-2 gap-3 sm:grid-cols-4">
      {METADATA_FIELDS.map((field) => {
        const value = field.format(video[field.key], video);
        return (
          <div
            key={field.key}
            className="rounded-lg glass-card p-3 transition-colors duration-200 hover:bg-surface-hover"
          >
            <div className="mb-1 text-base">{field.icon}</div>
            <p className="text-[10px] font-medium uppercase tracking-wider text-text-muted">
              {field.label}
            </p>
            <p className="mt-0.5 text-sm font-semibold text-text-primary truncate" title={value}>
              {value}
            </p>
          </div>
        );
      })}
    </div>
  );
}
