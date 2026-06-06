import { useEffect, useRef, useState } from 'react';
import Hls from 'hls.js';

export default function VideoPlayer({ src, poster, title }) {
  const videoRef = useRef(null);
  const hlsRef = useRef(null);
  const [levels, setLevels] = useState([]);
  const [currentLevel, setCurrentLevel] = useState(-1);
  const [isReady, setIsReady] = useState(false);

  useEffect(() => {
    if (!src || !videoRef.current) return;

    const video = videoRef.current;

    if (Hls.isSupported()) {
      const hls = new Hls({
        enableWorker: true,
        lowLatencyMode: false,
      });

      hls.loadSource(src);
      hls.attachMedia(video);

      hls.on(Hls.Events.MANIFEST_PARSED, (_, data) => {
        setLevels(data.levels);
        setIsReady(true);
      });

      hls.on(Hls.Events.LEVEL_SWITCHED, (_, data) => {
        setCurrentLevel(data.level);
      });

      hls.on(Hls.Events.ERROR, (_, data) => {
        if (data.fatal) {
          switch (data.type) {
            case Hls.ErrorTypes.NETWORK_ERROR:
              hls.startLoad();
              break;
            case Hls.ErrorTypes.MEDIA_ERROR:
              hls.recoverMediaError();
              break;
            default:
              hls.destroy();
              break;
          }
        }
      });

      hlsRef.current = hls;

      return () => {
        hls.destroy();
        hlsRef.current = null;
      };
    } else if (video.canPlayType('application/vnd.apple.mpegurl')) {
      // Safari native HLS
      video.src = src;
      video.addEventListener('loadedmetadata', () => setIsReady(true));
    }
  }, [src]);

  const handleQualityChange = (levelIndex) => {
    if (hlsRef.current) {
      hlsRef.current.currentLevel = levelIndex;
    }
  };

  return (
    <div className="overflow-hidden rounded-xl bg-black">
      {/* Video element */}
      <video
        ref={videoRef}
        poster={poster}
        controls
        className="aspect-video w-full bg-black"
        playsInline
      />

      {/* Quality selector */}
      {isReady && levels.length > 0 && (
        <div className="flex items-center gap-3 border-t border-white/10 bg-surface-card px-4 py-2.5">
          <span className="text-xs font-medium text-text-secondary">Quality:</span>
          <div className="flex gap-1.5">
            <button
              onClick={() => handleQualityChange(-1)}
              className={`rounded px-2.5 py-1 text-xs font-medium transition-all duration-200 ${
                currentLevel === -1
                  ? 'bg-accent text-white'
                  : 'bg-surface-elevated text-text-secondary hover:bg-surface-hover hover:text-text-primary'
              }`}
            >
              Auto
            </button>
            {levels.map((level, index) => (
              <button
                key={index}
                onClick={() => handleQualityChange(index)}
                className={`rounded px-2.5 py-1 text-xs font-medium transition-all duration-200 ${
                  currentLevel === index
                    ? 'bg-accent text-white'
                    : 'bg-surface-elevated text-text-secondary hover:bg-surface-hover hover:text-text-primary'
                }`}
              >
                {level.height}p
              </button>
            ))}
          </div>
          {title && (
            <span className="ml-auto text-xs text-text-muted truncate max-w-xs">{title}</span>
          )}
        </div>
      )}
    </div>
  );
}
