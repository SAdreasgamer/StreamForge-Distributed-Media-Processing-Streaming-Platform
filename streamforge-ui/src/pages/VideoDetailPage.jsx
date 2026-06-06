import { useState, useEffect } from 'react';
import { useParams, useNavigate, Link } from 'react-router-dom';
import { getVideoDetail, deleteVideo, reprocessVideo } from '../api/api';
import { useProcessingStatus } from '../hooks/useProcessingStatus';
import StatusBadge from '../components/StatusBadge';
import MetadataPanel from '../components/MetadataPanel';
import ProcessingTracker from '../components/ProcessingTracker';

export default function VideoDetailPage() {
  const { id } = useParams();
  const navigate = useNavigate();
  const [video, setVideo] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [showDeleteConfirm, setShowDeleteConfirm] = useState(false);
  const [deleting, setDeleting] = useState(false);

  // Connect WebSocket only if video is not yet processed
  const needsLiveUpdates = video && video.status !== 'PROCESSED' && video.status !== 'FAILED';
  const { currentStatus, metadata: wsMetadata, variants: wsVariants, error: wsError } =
    useProcessingStatus(needsLiveUpdates ? id : null);

  const fetchVideo = async () => {
    setLoading(true);
    try {
      const data = await getVideoDetail(id);
      setVideo(data);
    } catch (err) {
      setError(err.message);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchVideo();
  }, [id]);

  // Re-fetch when processing completes via WebSocket
  useEffect(() => {
    if (currentStatus === 'PROCESSED' || currentStatus === 'FAILED') {
      fetchVideo();
    }
  }, [currentStatus]);

  const handleDelete = async () => {
    setDeleting(true);
    try {
      await deleteVideo(id);
      navigate('/');
    } catch (err) {
      setError(err.message);
      setDeleting(false);
      setShowDeleteConfirm(false);
    }
  };

  const handleReprocess = async () => {
    try {
      await reprocessVideo(id);
      fetchVideo();
    } catch (err) {
      setError(err.message);
    }
  };

  if (loading) {
    return (
      <div className="flex min-h-[60vh] items-center justify-center">
        <div className="h-10 w-10 rounded-full border-2 border-accent border-t-transparent animate-spin" />
      </div>
    );
  }

  if (error || !video) {
    return (
      <div className="flex min-h-[60vh] items-center justify-center">
        <div className="text-center">
          <p className="text-sm font-semibold text-status-failed">
            {error || 'Video not found'}
          </p>
          <Link to="/" className="mt-4 inline-block text-sm text-accent hover:underline">
            ← Back to dashboard
          </Link>
        </div>
      </div>
    );
  }

  const displayStatus = currentStatus || video.status;

  return (
    <div className="mx-auto max-w-4xl">
      {/* Back button */}
      <Link to="/" className="mb-6 inline-flex items-center gap-1.5 text-sm text-text-secondary hover:text-text-primary transition-colors no-underline">
        <svg className="h-4 w-4" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
          <polyline points="15 18 9 12 15 6" />
        </svg>
        Back to dashboard
      </Link>

      {/* Header */}
      <div className="mb-6 flex items-start justify-between gap-4">
        <div>
          <div className="flex items-center gap-3">
            <h1 className="text-2xl font-bold text-text-primary">{video.title}</h1>
            <StatusBadge status={displayStatus} />
          </div>
          {video.description && (
            <p className="mt-2 text-sm text-text-secondary">{video.description}</p>
          )}
          <p className="mt-1 text-xs text-text-muted">
            Uploaded {new Date(video.createdAt).toLocaleDateString('en-US', {
              year: 'numeric', month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit',
            })}
          </p>
        </div>
      </div>

      {/* Thumbnail */}
      {video.thumbnailUrl && (
        <div className="mb-6 overflow-hidden rounded-xl">
          <img
            src={video.thumbnailUrl}
            alt={video.title}
            className="aspect-video w-full object-cover"
          />
        </div>
      )}

      {/* Metadata Panel */}
      <div className="mb-6">
        <h2 className="mb-3 text-sm font-semibold uppercase tracking-wider text-text-secondary">
          Technical Details
        </h2>
        <MetadataPanel video={video} />
      </div>

      {/* Variants */}
      {video.variants && video.variants.length > 0 && (
        <div className="mb-6">
          <h2 className="mb-3 text-sm font-semibold uppercase tracking-wider text-text-secondary">
            HLS Variants
          </h2>
          <div className="grid grid-cols-1 gap-3 sm:grid-cols-3">
            {video.variants.map((variant, i) => (
              <div key={i} className="rounded-lg glass-card p-4">
                <p className="text-lg font-bold text-accent">{variant.resolution}</p>
                <p className="text-xs text-text-secondary">
                  {variant.width}×{variant.height} · {variant.bitrateKbps} kbps
                </p>
              </div>
            ))}
          </div>
        </div>
      )}

      {/* Live processing tracker */}
      {needsLiveUpdates && (
        <div className="mb-6">
          <ProcessingTracker
            currentStatus={currentStatus}
            metadata={wsMetadata}
            variants={wsVariants}
            error={wsError}
          />
        </div>
      )}

      {/* Actions */}
      <div className="flex flex-wrap gap-3">
        {displayStatus === 'PROCESSED' && (
          <Link
            to={`/watch/${video.id}`}
            className="inline-flex items-center gap-2 rounded-lg bg-gradient-to-r from-accent to-accent-cyan px-6 py-2.5 text-sm font-semibold text-white transition-all duration-300 hover:shadow-lg hover:shadow-accent/25 no-underline"
          >
            <svg className="h-4 w-4" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
              <polygon points="5 3 19 12 5 21 5 3" />
            </svg>
            Watch
          </Link>
        )}

        {displayStatus === 'FAILED' && (
          <button
            onClick={handleReprocess}
            className="inline-flex items-center gap-2 rounded-lg bg-status-pending/15 px-5 py-2.5 text-sm font-semibold text-status-pending transition-colors hover:bg-status-pending/25"
          >
            🔄 Reprocess
          </button>
        )}

        <button
          onClick={() => setShowDeleteConfirm(true)}
          className="inline-flex items-center gap-2 rounded-lg border border-status-failed/30 px-5 py-2.5 text-sm font-medium text-status-failed transition-colors hover:bg-status-failed/10"
        >
          Delete
        </button>
      </div>

      {/* Delete confirmation modal */}
      {showDeleteConfirm && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 backdrop-blur-sm">
          <div className="mx-4 w-full max-w-sm rounded-xl bg-surface-card border border-border p-6 shadow-2xl">
            <h3 className="text-lg font-bold text-text-primary">Delete Video</h3>
            <p className="mt-2 text-sm text-text-secondary">
              Are you sure you want to delete "<strong>{video.title}</strong>"? This action cannot be undone.
            </p>
            <div className="mt-6 flex gap-3">
              <button
                onClick={() => setShowDeleteConfirm(false)}
                disabled={deleting}
                className="flex-1 rounded-lg border border-border py-2.5 text-sm font-medium text-text-secondary transition-colors hover:bg-surface-elevated"
              >
                Cancel
              </button>
              <button
                onClick={handleDelete}
                disabled={deleting}
                className="flex-1 rounded-lg bg-status-failed py-2.5 text-sm font-semibold text-white transition-colors hover:bg-status-failed/80 disabled:opacity-50"
              >
                {deleting ? 'Deleting...' : 'Delete'}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
