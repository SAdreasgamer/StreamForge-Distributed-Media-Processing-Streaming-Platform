const STATUS_CONFIG = {
  PENDING: {
    label: 'Pending',
    color: 'bg-status-pending/15 text-status-pending border-status-pending/30',
    dot: 'bg-status-pending',
  },
  UPLOADED: {
    label: 'Uploaded',
    color: 'bg-status-pending/15 text-status-pending border-status-pending/30',
    dot: 'bg-status-pending',
  },
  PROCESSING: {
    label: 'Processing',
    color: 'bg-status-processing/15 text-status-processing border-status-processing/30',
    dot: 'bg-status-processing animate-pulse-glow',
  },
  METADATA_EXTRACTED: {
    label: 'Processing',
    color: 'bg-status-processing/15 text-status-processing border-status-processing/30',
    dot: 'bg-status-processing animate-pulse-glow',
  },
  TRANSCODE_COMPLETED: {
    label: 'Processing',
    color: 'bg-status-processing/15 text-status-processing border-status-processing/30',
    dot: 'bg-status-processing animate-pulse-glow',
  },
  THUMBNAIL_COMPLETED: {
    label: 'Processing',
    color: 'bg-status-processing/15 text-status-processing border-status-processing/30',
    dot: 'bg-status-processing animate-pulse-glow',
  },
  PROCESSED: {
    label: 'Ready',
    color: 'bg-status-processed/15 text-status-processed border-status-processed/30',
    dot: 'bg-status-processed',
  },
  FAILED: {
    label: 'Failed',
    color: 'bg-status-failed/15 text-status-failed border-status-failed/30',
    dot: 'bg-status-failed',
  },
};

export default function StatusBadge({ status }) {
  const config = STATUS_CONFIG[status] || STATUS_CONFIG.PENDING;

  return (
    <span className={`inline-flex items-center gap-1.5 rounded-full border px-2.5 py-0.5 text-xs font-semibold ${config.color}`}>
      <span className={`h-1.5 w-1.5 rounded-full ${config.dot}`} />
      {config.label}
    </span>
  );
}
