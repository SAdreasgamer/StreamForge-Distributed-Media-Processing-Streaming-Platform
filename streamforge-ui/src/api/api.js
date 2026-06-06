const API_BASE = '/api';

export async function listVideos(page = 0, size = 20) {
  const res = await fetch(`${API_BASE}/videos?page=${page}&size=${size}&sort=createdAt,desc`);
  if (!res.ok) throw new Error('Failed to fetch videos');
  return res.json();
}

export async function getVideoDetail(id) {
  const res = await fetch(`${API_BASE}/videos/${id}`);
  if (!res.ok) throw new Error('Failed to fetch video detail');
  return res.json();
}

export async function getPlayback(id) {
  const res = await fetch(`${API_BASE}/videos/${id}/playback`);
  if (!res.ok) throw new Error('Failed to fetch playback info');
  return res.json();
}

export async function getStatus(id) {
  const res = await fetch(`${API_BASE}/videos/${id}/status`);
  if (!res.ok) throw new Error('Failed to fetch status');
  return res.json();
}

export async function createUploadSession(title, description, contentType) {
  const res = await fetch(`${API_BASE}/uploads/sessions`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ title, description, contentType }),
  });
  if (!res.ok) {
    const err = await res.json().catch(() => ({}));
    throw new Error(err.message || 'Failed to create upload session');
  }
  return res.json();
}

export function uploadFile(sessionId, file, onProgress) {
  return new Promise((resolve, reject) => {
    const xhr = new XMLHttpRequest();
    const formData = new FormData();
    formData.append('file', file);

    xhr.upload.addEventListener('progress', (e) => {
      if (e.lengthComputable && onProgress) {
        onProgress(Math.round((e.loaded / e.total) * 100));
      }
    });

    xhr.addEventListener('load', () => {
      if (xhr.status >= 200 && xhr.status < 300) {
        resolve(JSON.parse(xhr.responseText));
      } else {
        try {
          const err = JSON.parse(xhr.responseText);
          reject(new Error(err.message || 'Upload failed'));
        } catch {
          reject(new Error('Upload failed'));
        }
      }
    });

    xhr.addEventListener('error', () => reject(new Error('Network error during upload')));
    xhr.addEventListener('abort', () => reject(new Error('Upload aborted')));

    xhr.open('POST', `${API_BASE}/uploads/${sessionId}/file`);
    xhr.send(formData);
  });
}

export async function deleteVideo(id) {
  const res = await fetch(`${API_BASE}/videos/${id}`, { method: 'DELETE' });
  if (!res.ok) throw new Error('Failed to delete video');
}

export async function reprocessVideo(id) {
  const res = await fetch(`${API_BASE}/videos/${id}/reprocess`, { method: 'POST' });
  if (!res.ok) throw new Error('Failed to reprocess video');
  return res.json();
}
