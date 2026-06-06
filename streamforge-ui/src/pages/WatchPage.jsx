import { useState, useEffect } from 'react';
import { useParams, Link } from 'react-router-dom';
import { getPlayback } from '../api/api';
import VideoPlayer from '../components/VideoPlayer';

export default function WatchPage() {
  const { id } = useParams();
  const [playback, setPlayback] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  useEffect(() => {
    const fetchPlayback = async () => {
      try {
        const data = await getPlayback(id);
        setPlayback(data);
      } catch (err) {
        setError(err.message);
      } finally {
        setLoading(false);
      }
    };
    fetchPlayback();
  }, [id]);

  if (loading) {
    return (
      <div className="flex min-h-[60vh] items-center justify-center">
        <div className="h-10 w-10 rounded-full border-2 border-accent border-t-transparent animate-spin" />
      </div>
    );
  }

  if (error || !playback) {
    return (
      <div className="flex min-h-[60vh] items-center justify-center">
        <div className="text-center">
          <div className="mx-auto mb-4 flex h-16 w-16 items-center justify-center rounded-full bg-status-failed/10">
            <span className="text-2xl">🎬</span>
          </div>
          <p className="text-sm font-semibold text-status-failed">
            {error || 'Video not available for playback'}
          </p>
          <p className="mt-1 text-xs text-text-secondary">
            Make sure the video has been fully processed
          </p>
          <Link to={`/videos/${id}`} className="mt-4 inline-block text-sm text-accent hover:underline">
            ← Back to video detail
          </Link>
        </div>
      </div>
    );
  }

  // Build the streaming URL through our proxy
  const streamUrl = `/api/stream/${id}/master.m3u8`;

  return (
    <div className="mx-auto max-w-5xl">
      {/* Back */}
      <Link
        to={`/videos/${id}`}
        className="mb-4 inline-flex items-center gap-1.5 text-sm text-text-secondary hover:text-text-primary transition-colors no-underline"
      >
        <svg className="h-4 w-4" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
          <polyline points="15 18 9 12 15 6" />
        </svg>
        Back to details
      </Link>

      {/* Player */}
      <VideoPlayer
        src={streamUrl}
        poster={playback.thumbnailUrl}
        title={playback.title}
      />

      {/* Info below player */}
      <div className="mt-4 rounded-xl glass-card p-5">
        <h1 className="text-xl font-bold text-text-primary">{playback.title}</h1>

        {/* Variant info */}
        {playback.variants && playback.variants.length > 0 && (
          <div className="mt-3 flex flex-wrap gap-2">
            {playback.variants.map((v, i) => (
              <span
                key={i}
                className="rounded-full border border-border px-3 py-1 text-xs font-medium text-text-secondary"
              >
                {v.resolution} · {v.bitrateKbps} kbps
              </span>
            ))}
          </div>
        )}
      </div>
    </div>
  );
}
