import { useState, useRef } from 'react';

export default function UploadDropzone({ onFileSelected, disabled }) {
  const [isDragging, setIsDragging] = useState(false);
  const [selectedFile, setSelectedFile] = useState(null);
  const inputRef = useRef(null);

  const handleDragOver = (e) => {
    e.preventDefault();
    if (!disabled) setIsDragging(true);
  };

  const handleDragLeave = (e) => {
    e.preventDefault();
    setIsDragging(false);
  };

  const handleDrop = (e) => {
    e.preventDefault();
    setIsDragging(false);
    if (disabled) return;

    const file = e.dataTransfer.files[0];
    if (file && file.type.startsWith('video/')) {
      setSelectedFile(file);
      onFileSelected(file);
    }
  };

  const handleClick = () => {
    if (!disabled) inputRef.current?.click();
  };

  const handleFileChange = (e) => {
    const file = e.target.files[0];
    if (file) {
      setSelectedFile(file);
      onFileSelected(file);
    }
  };

  const formatSize = (bytes) => {
    if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
    if (bytes < 1024 * 1024 * 1024) return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
    return `${(bytes / (1024 * 1024 * 1024)).toFixed(2)} GB`;
  };

  return (
    <div
      onDragOver={handleDragOver}
      onDragLeave={handleDragLeave}
      onDrop={handleDrop}
      onClick={handleClick}
      className={`relative flex cursor-pointer flex-col items-center justify-center rounded-xl border-2 border-dashed p-12 transition-all duration-300 ${
        disabled
          ? 'cursor-not-allowed border-border bg-surface-card opacity-50'
          : isDragging
          ? 'border-accent bg-accent/5 shadow-lg shadow-accent/10'
          : selectedFile
          ? 'border-status-processed/50 bg-status-processed/5'
          : 'border-border hover:border-accent/50 hover:bg-surface-elevated'
      }`}
    >
      <input
        ref={inputRef}
        type="file"
        accept="video/*"
        onChange={handleFileChange}
        className="hidden"
        disabled={disabled}
      />

      {selectedFile ? (
        <>
          <div className="mb-3 flex h-14 w-14 items-center justify-center rounded-full bg-status-processed/15">
            <svg className="h-7 w-7 text-status-processed" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
              <path d="M20 6L9 17l-5-5" />
            </svg>
          </div>
          <p className="text-sm font-semibold text-text-primary">{selectedFile.name}</p>
          <p className="mt-1 text-xs text-text-secondary">{formatSize(selectedFile.size)} · {selectedFile.type}</p>
          {!disabled && (
            <p className="mt-2 text-xs text-text-muted">Click or drop to change file</p>
          )}
        </>
      ) : (
        <>
          <div className="mb-4 flex h-16 w-16 items-center justify-center rounded-full bg-accent/10">
            <svg className="h-8 w-8 text-accent" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round">
              <path d="M21 15v4a2 2 0 01-2 2H5a2 2 0 01-2-2v-4" />
              <polyline points="17 8 12 3 7 8" />
              <line x1="12" y1="3" x2="12" y2="15" />
            </svg>
          </div>
          <p className="text-sm font-semibold text-text-primary">
            {isDragging ? 'Drop your video here' : 'Drag & drop a video file'}
          </p>
          <p className="mt-1 text-xs text-text-secondary">or click to browse · MP4, WebM, MOV up to 2GB</p>
        </>
      )}
    </div>
  );
}
