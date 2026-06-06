import { useState, useEffect, useRef, useCallback } from 'react';
import SockJS from 'sockjs-client';
import Stomp from 'stompjs';

const PROCESSING_STAGES = [
  'PROCESSING',
  'METADATA_EXTRACTED',
  'TRANSCODE_COMPLETED',
  'THUMBNAIL_COMPLETED',
  'PROCESSED',
];

export function useProcessingStatus(videoId) {
  const [events, setEvents] = useState([]);
  const [currentStatus, setCurrentStatus] = useState(null);
  const [metadata, setMetadata] = useState(null);
  const [variants, setVariants] = useState(null);
  const [thumbnailPath, setThumbnailPath] = useState(null);
  const [error, setError] = useState(null);
  const [connected, setConnected] = useState(false);
  const clientRef = useRef(null);
  const subscriptionRef = useRef(null);

  const disconnect = useCallback(() => {
    if (subscriptionRef.current) {
      try { subscriptionRef.current.unsubscribe(); } catch (e) { /* ignore */ }
      subscriptionRef.current = null;
    }
    if (clientRef.current && clientRef.current.connected) {
      try { clientRef.current.disconnect(); } catch (e) { /* ignore */ }
      clientRef.current = null;
    }
    setConnected(false);
  }, []);

  useEffect(() => {
    if (!videoId) return;

    const socket = new SockJS('/ws/connect');
    const client = Stomp.over(socket);
    client.debug = null; // Disable noisy STOMP debug logs

    client.connect({}, () => {
      setConnected(true);
      clientRef.current = client;

      const subscription = client.subscribe(
        `/topic/video/${videoId}/status`,
        (message) => {
          try {
            const event = JSON.parse(message.body);
            setEvents((prev) => [...prev, event]);
            setCurrentStatus(event.status);

            if (event.metadata) setMetadata(event.metadata);
            if (event.variants) setVariants(event.variants);
            if (event.thumbnailPath) setThumbnailPath(event.thumbnailPath);
            if (event.status === 'FAILED') setError(event.errorMessage || event.message);
          } catch (e) {
            console.error('Failed to parse WebSocket message:', e);
          }
        }
      );
      subscriptionRef.current = subscription;
    }, (err) => {
      console.error('WebSocket connection error:', err);
      setConnected(false);
    });

    return () => disconnect();
  }, [videoId, disconnect]);

  const completedStages = PROCESSING_STAGES.filter((stage) => {
    const stageIndex = PROCESSING_STAGES.indexOf(stage);
    const currentIndex = PROCESSING_STAGES.indexOf(currentStatus);
    return currentIndex >= stageIndex;
  });

  return {
    events,
    currentStatus,
    metadata,
    variants,
    thumbnailPath,
    error,
    connected,
    completedStages,
    stages: PROCESSING_STAGES,
    isProcessing: currentStatus && currentStatus !== 'PROCESSED' && currentStatus !== 'FAILED',
    isCompleted: currentStatus === 'PROCESSED',
    isFailed: currentStatus === 'FAILED',
    disconnect,
  };
}
