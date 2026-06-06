import { Link } from 'react-router-dom';
import StatusBadge from './StatusBadge';

function formatDuration(seconds) {
  if (!seconds) return '--:--';
  const mins = Math.floor(seconds / 60);
  const secs = Math.floor(seconds % 60);
  return `${mins}:${secs.toString().padStart(2, '0')}`;
}

function formatFileSize(bytes) {
  if (!bytes) return '--';
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  if (bytes < 1024 * 1024 * 1024) return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
  return `${(bytes / (1024 * 1024 * 1024)).toFixed(2)} GB`;
}

export default function VideoCard({ video }) {
  const thumbnailUrl = video.thumbnailUrl;

  return (
    <Link
      to={`/videos/${video.id}`}
      className="group block rounded-xl glass-card overflow-hidden transition-all duration-300 hover:border-accent/40 hover:shadow-lg hover:shadow-accent/5 hover:-translate-y-1 no-underline"
    >
      {/* Thumbnail */}
      <div className="relative aspect-video overflow-hidden bg-surface-elevated">
        {thumbnailUrl ? (
          <img
            src={thumbnailUrl}
            alt={video.title}
            className="h-full w-full object-cover transition-transform duration-500 group-hover:scale-105"
          />
        ) : (
          <div className="flex h-full w-full items-center justify-center bg-gradient-to-br from-accent/10 to-accent-cyan/10">
            <svg className="h-12 w-12 text-text-muted" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5">
              <rect x="2" y="2" width="20" height="20" rx="2" />
              <polygon points="10 8 16 12 10 16 10 8" />
            </svg>
          </div>
        )}

        {/* Duration overlay */}
        {video.durationSeconds && (
          <span className="absolute bottom-2 right-2 rounded bg-black/75 px-1.5 py-0.5 text-xs font-medium text-white backdrop-blur-sm">
            {formatDuration(video.durationSeconds)}
          </span>
        )}
      </div>

      {/* Info */}
      <div className="p-4">
        <h3 className="mb-2 text-sm font-semibold text-text-primary line-clamp-2 group-hover:text-accent transition-colors duration-200">
          {video.title}
        </h3>
        <div className="flex items-center justify-between">
          <StatusBadge status={video.status} />
          <span className="text-xs text-text-muted">{formatFileSize(video.fileSizeBytes)}</span>
        </div>
      </div>
    </Link>
  );
}
