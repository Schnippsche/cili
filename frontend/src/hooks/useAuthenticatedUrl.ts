import { useEffect, useState } from 'react';
import axiosClient from '../api/axiosClient';

export function useAuthenticatedUrl(url: string | null, retries = 0): string | null {
  const [blobUrl, setBlobUrl] = useState<string | null>(null);

  useEffect(() => {
    if (!url) return;
    let objectUrl: string | null = null;
    let cancelled = false;
    let attempt = 0;
    const timers: ReturnType<typeof setTimeout>[] = [];

    const tryFetch = () => {
      axiosClient.get(url, { responseType: 'blob' })
        .then(response => {
          if (cancelled) return;
          objectUrl = URL.createObjectURL(response.data);
          setBlobUrl(objectUrl);
        })
        .catch(() => {
          if (cancelled || attempt >= retries) return;
          attempt++;
          const t = setTimeout(tryFetch, 3000 * attempt);
          timers.push(t);
        });
    };

    tryFetch();

    return () => {
      cancelled = true;
      timers.forEach(clearTimeout);
      if (objectUrl) URL.revokeObjectURL(objectUrl);
      setBlobUrl(null);
    };
  }, [url, retries]);

  return blobUrl;
}
