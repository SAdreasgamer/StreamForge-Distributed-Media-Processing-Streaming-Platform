const STEPS = [
  { key: 'PROCESSING', label: 'Processing', icon: '⚙️', description: 'Pipeline initiated' },
  { key: 'METADATA_EXTRACTED', label: 'Metadata', icon: '📊', description: 'Resolution, codec, FPS extracted' },
  { key: 'TRANSCODE_COMPLETED', label: 'Transcoding', icon: '🎬', description: 'HLS 1080p / 720p / 480p' },
  { key: 'THUMBNAIL_COMPLETED', label: 'Thumbnail', icon: '🖼️', description: 'Poster image generated' },
  { key: 'PROCESSED', label: 'Complete', icon: '✅', description: 'Ready to stream' },
];

export default function ProcessingTracker({ currentStatus, metadata, variants, error }) {
  const isFailed = currentStatus === 'FAILED';
  const currentIndex = STEPS.findIndex((s) => s.key === currentStatus);

  return (
    <div className="rounded-xl glass-card p-6">
      <h3 className="mb-6 text-sm font-semibold uppercase tracking-wider text-text-secondary">
        Processing Pipeline
      </h3>

      {/* Steps */}
      <div className="space-y-0">
        {STEPS.map((step, index) => {
          const isCompleted = currentIndex >= index;
          const isActive = currentIndex === index && !isFailed;
          const isPending = currentIndex < index;

          return (
            <div key={step.key} className="flex gap-4">
              {/* Vertical line + circle */}
              <div className="flex flex-col items-center">
                <div
                  className={`flex h-8 w-8 shrink-0 items-center justify-center rounded-full border-2 text-sm transition-all duration-500 ${
                    isFailed && isActive
                      ? 'border-status-failed bg-status-failed/20 text-status-failed'
                      : isCompleted
                      ? 'border-status-processed bg-status-processed/20 text-status-processed'
                      : isActive
                      ? 'border-accent bg-accent/20 text-accent animate-pulse-glow'
                      : 'border-border bg-surface-card text-text-muted'
                  }`}
                >
                  {isFailed && isActive ? '✕' : isCompleted ? '✓' : step.icon}
                </div>
                {index < STEPS.length - 1 && (
                  <div
                    className={`w-0.5 grow min-h-8 transition-colors duration-500 ${
                      isCompleted && !isFailed ? 'bg-status-processed/50' : 'bg-border'
                    }`}
                  />
                )}
              </div>

              {/* Content */}
              <div className={`pb-6 ${isPending ? 'opacity-40' : ''}`}>
                <p className={`text-sm font-semibold ${
                  isFailed && isActive ? 'text-status-failed' : isCompleted ? 'text-text-primary' : 'text-text-muted'
                }`}>
                  {step.label}
                </p>
                <p className="text-xs text-text-secondary">{step.description}</p>

                {/* Metadata details */}
                {step.key === 'METADATA_EXTRACTED' && isCompleted && metadata && (
                  <div className="mt-2 flex flex-wrap gap-2">
                    {metadata.width && metadata.height && (
                      <span className="rounded bg-surface-elevated px-2 py-0.5 text-xs text-accent">
                        {metadata.width}×{metadata.height}
                      </span>
                    )}
                    {metadata.codec && (
                      <span className="rounded bg-surface-elevated px-2 py-0.5 text-xs text-accent-cyan">
                        {metadata.codec.toUpperCase()}
                      </span>
                    )}
                    {metadata.fps && (
                      <span className="rounded bg-surface-elevated px-2 py-0.5 text-xs text-status-pending">
                        {metadata.fps} FPS
                      </span>
                    )}
                    {metadata.durationSeconds && (
                      <span className="rounded bg-surface-elevated px-2 py-0.5 text-xs text-status-processed">
                        {Math.floor(metadata.durationSeconds / 60)}:{Math.floor(metadata.durationSeconds % 60).toString().padStart(2, '0')}
                      </span>
                    )}
                  </div>
                )}

                {/* Variant details */}
                {step.key === 'TRANSCODE_COMPLETED' && isCompleted && variants && (
                  <div className="mt-2 flex flex-wrap gap-2">
                    {variants.map((v, i) => (
                      <span key={i} className="rounded bg-surface-elevated px-2 py-0.5 text-xs text-accent">
                        {v.resolution} · {v.bitrateKbps}kbps
                      </span>
                    ))}
                  </div>
                )}
              </div>
            </div>
          );
        })}
      </div>

      {/* Error message */}
      {isFailed && error && (
        <div className="mt-4 rounded-lg border border-status-failed/30 bg-status-failed/10 p-3">
          <p className="text-xs font-semibold text-status-failed">Processing Failed</p>
          <p className="mt-1 text-xs text-status-failed/80">{error}</p>
        </div>
      )}
    </div>
  );
}
