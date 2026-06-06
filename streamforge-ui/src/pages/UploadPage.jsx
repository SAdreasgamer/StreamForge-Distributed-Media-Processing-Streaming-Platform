import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { createUploadSession, uploadFile } from '../api/api';
import { useProcessingStatus } from '../hooks/useProcessingStatus';
import UploadDropzone from '../components/UploadDropzone';
import ProcessingTracker from '../components/ProcessingTracker';

const PHASE = {
  SELECT: 'select',
  UPLOADING: 'uploading',
  PROCESSING: 'processing',
};

export default function UploadPage() {
  const navigate = useNavigate();
  const [phase, setPhase] = useState(PHASE.SELECT);
  const [file, setFile] = useState(null);
  const [title, setTitle] = useState('');
  const [description, setDescription] = useState('');
  const [uploadProgress, setUploadProgress] = useState(0);
  const [videoId, setVideoId] = useState(null);
  const [error, setError] = useState(null);

  const { currentStatus, metadata, variants, error: wsError, isCompleted, isFailed } =
    useProcessingStatus(phase === PHASE.PROCESSING ? videoId : null);

  const handleFileSelected = (selectedFile) => {
    setFile(selectedFile);
    if (!title) {
      // Auto-fill title from filename (remove extension)
      const name = selectedFile.name.replace(/\.[^/.]+$/, '').replace(/[_-]/g, ' ');
      setTitle(name.charAt(0).toUpperCase() + name.slice(1));
    }
  };

  const handleUpload = async () => {
    if (!file || !title.trim()) return;
    setError(null);

    try {
      // Phase 1: Create upload session
      setPhase(PHASE.UPLOADING);
      const session = await createUploadSession(title.trim(), description.trim(), file.type);
      setVideoId(session.videoId);

      // Phase 2: Upload file with progress tracking
      await uploadFile(session.sessionId, file, setUploadProgress);

      // Phase 3: Switch to processing tracker (WebSocket connects)
      setPhase(PHASE.PROCESSING);
    } catch (err) {
      setError(err.message);
      setPhase(PHASE.SELECT);
    }
  };

  const handleReset = () => {
    setPhase(PHASE.SELECT);
    setFile(null);
    setTitle('');
    setDescription('');
    setUploadProgress(0);
    setVideoId(null);
    setError(null);
  };

  return (
    <div className="mx-auto max-w-2xl">
      {/* Header */}
      <div className="mb-8">
        <h1 className="text-2xl font-bold text-text-primary">Upload Video</h1>
        <p className="mt-1 text-sm text-text-secondary">
          Upload a video and watch it process in real-time
        </p>
      </div>

      {/* Phase: Select */}
      {phase === PHASE.SELECT && (
        <div className="space-y-6">
          <UploadDropzone onFileSelected={handleFileSelected} disabled={false} />

          {file && (
            <div className="space-y-4 rounded-xl glass-card p-6">
              <div>
                <label className="mb-1.5 block text-xs font-medium uppercase tracking-wider text-text-secondary">
                  Title *
                </label>
                <input
                  type="text"
                  value={title}
                  onChange={(e) => setTitle(e.target.value)}
                  placeholder="Enter video title"
                  maxLength={500}
                  className="w-full rounded-lg border border-border bg-surface-elevated px-4 py-2.5 text-sm text-text-primary placeholder-text-muted outline-none transition-colors focus:border-accent focus:ring-1 focus:ring-accent/30"
                />
              </div>

              <div>
                <label className="mb-1.5 block text-xs font-medium uppercase tracking-wider text-text-secondary">
                  Description
                </label>
                <textarea
                  value={description}
                  onChange={(e) => setDescription(e.target.value)}
                  placeholder="Enter video description (optional)"
                  rows={3}
                  maxLength={2000}
                  className="w-full rounded-lg border border-border bg-surface-elevated px-4 py-2.5 text-sm text-text-primary placeholder-text-muted outline-none transition-colors focus:border-accent focus:ring-1 focus:ring-accent/30 resize-none"
                />
              </div>

              <button
                onClick={handleUpload}
                disabled={!title.trim()}
                className="w-full rounded-lg bg-gradient-to-r from-accent to-accent-cyan py-3 text-sm font-semibold text-white transition-all duration-300 hover:shadow-lg hover:shadow-accent/25 disabled:opacity-40 disabled:cursor-not-allowed"
              >
                Upload & Process
              </button>
            </div>
          )}

          {error && (
            <div className="rounded-lg border border-status-failed/30 bg-status-failed/10 p-4">
              <p className="text-sm font-semibold text-status-failed">Upload Failed</p>
              <p className="mt-1 text-xs text-status-failed/80">{error}</p>
            </div>
          )}
        </div>
      )}

      {/* Phase: Uploading */}
      {phase === PHASE.UPLOADING && (
        <div className="rounded-xl glass-card p-8">
          <div className="text-center">
            <div className="mx-auto mb-4 h-12 w-12 rounded-full border-2 border-accent border-t-transparent animate-spin" />
            <h3 className="text-sm font-semibold text-text-primary">Uploading...</h3>
            <p className="mt-1 text-xs text-text-secondary">{file?.name}</p>
          </div>

          {/* Progress bar */}
          <div className="mt-6">
            <div className="flex items-center justify-between mb-2">
              <span className="text-xs text-text-secondary">Upload progress</span>
              <span className="text-xs font-semibold text-accent">{uploadProgress}%</span>
            </div>
            <div className="h-2 w-full overflow-hidden rounded-full bg-surface-elevated">
              <div
                className="h-full rounded-full bg-gradient-to-r from-accent to-accent-cyan transition-all duration-300"
                style={{ width: `${uploadProgress}%` }}
              />
            </div>
          </div>
        </div>
      )}

      {/* Phase: Processing */}
      {phase === PHASE.PROCESSING && (
        <div className="space-y-6">
          {/* Connection status */}
          <div className="flex items-center gap-2 rounded-lg bg-surface-card px-4 py-2">
            <span className="h-2 w-2 rounded-full bg-status-processed animate-pulse-glow" />
            <span className="text-xs text-text-secondary">Connected to live processing feed</span>
          </div>

          {/* Processing tracker */}
          <ProcessingTracker
            currentStatus={currentStatus}
            metadata={metadata}
            variants={variants}
            error={wsError}
          />

          {/* Actions */}
          {isCompleted && (
            <div className="flex gap-3">
              <button
                onClick={() => navigate(`/watch/${videoId}`)}
                className="flex-1 rounded-lg bg-gradient-to-r from-accent to-accent-cyan py-3 text-sm font-semibold text-white transition-all duration-300 hover:shadow-lg hover:shadow-accent/25"
              >
                🎬 Watch Now
              </button>
              <button
                onClick={() => navigate(`/videos/${videoId}`)}
                className="rounded-lg border border-border bg-surface-card px-6 py-3 text-sm font-medium text-text-secondary transition-colors hover:bg-surface-elevated hover:text-text-primary"
              >
                Details
              </button>
            </div>
          )}

          {isFailed && (
            <button
              onClick={handleReset}
              className="w-full rounded-lg border border-status-failed/30 bg-status-failed/10 py-3 text-sm font-semibold text-status-failed transition-colors hover:bg-status-failed/20"
            >
              Try Again
            </button>
          )}

          <button
            onClick={handleReset}
            className="w-full rounded-lg border border-border py-2.5 text-xs font-medium text-text-muted transition-colors hover:bg-surface-elevated hover:text-text-secondary"
          >
            Upload Another Video
          </button>
        </div>
      )}
    </div>
  );
}
