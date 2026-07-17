import { Box, CircularProgress, IconButton, Modal, Tooltip, Typography } from '@mui/material';
import CloseIcon from '@mui/icons-material/Close';
import ChevronLeftIcon from '@mui/icons-material/ChevronLeft';
import ChevronRightIcon from '@mui/icons-material/ChevronRight';
import { useEffect, useRef } from 'react';
import type { ResourceDto } from '../../types/api';
import { getThumbnailUrl } from '../../api/resources';
import { useAuthenticatedUrl } from '../../hooks/useAuthenticatedUrl';

interface Props {
  images: ResourceDto[];
  currentId: number;
  onClose: () => void;
  onNavigate: (id: number) => void;
}

export default function ImageLightbox({ images, currentId, onClose, onNavigate }: Readonly<Props>) {
  const currentIndex = images.findIndex(img => img.id === currentId);
  const current = images[currentIndex];
  const prev = currentIndex > 0 ? images[currentIndex - 1] : null;
  const next = currentIndex < images.length - 1 ? images[currentIndex + 1] : null;

  const blobUrl = useAuthenticatedUrl(
    current ? getThumbnailUrl(current.id, 'large', current.storedName) : null
  );

  const touchStartX = useRef<number | null>(null);

  const handleTouchStart = (e: React.TouchEvent) => {
    touchStartX.current = e.changedTouches[0].clientX;
  };

  const handleTouchEnd = (e: React.TouchEvent) => {
    if (touchStartX.current === null) return;
    const delta = e.changedTouches[0].clientX - touchStartX.current;
    touchStartX.current = null;
    if (delta > 50 && prev)  onNavigate(prev.id);
    if (delta < -50 && next) onNavigate(next.id);
  };

  useEffect(() => {
    const handler = (e: KeyboardEvent) => {
      if (e.key === 'ArrowLeft'  && prev)  onNavigate(prev.id);
      if (e.key === 'ArrowRight' && next)  onNavigate(next.id);
      if (e.key === 'Escape')              onClose();
    };
    globalThis.addEventListener('keydown', handler);
    return () => globalThis.removeEventListener('keydown', handler);
  }, [prev, next, onNavigate, onClose]);

  if (!current) return null;

  return (
    <Modal open onClose={onClose}>
      <Box
        sx={{ position: 'absolute', inset: 0, display: 'flex', alignItems: 'center', justifyContent: 'center' }}
        onTouchStart={handleTouchStart}
        onTouchEnd={handleTouchEnd}
      >
        <Tooltip title="Schließen">
          <IconButton
            aria-label="schließen"
            onClick={onClose}
            sx={{ position: 'absolute', top: 16, right: 16, color: 'white', zIndex: 1 }}
          >
            <CloseIcon />
          </IconButton>
        </Tooltip>

        <Box sx={{
          position: 'absolute', top: 16, left: 64, right: 64,
          textAlign: 'center', color: 'white', zIndex: 1, pointerEvents: 'none',
        }}>
          <Typography variant="subtitle1" noWrap>
            {current.metadata?.title || current.originalName}
          </Typography>
        </Box>

        <Tooltip title="Vorheriges Bild">
          <span>
            <IconButton
              aria-label="vorheriges Bild"
              onClick={() => prev && onNavigate(prev.id)}
              disabled={!prev}
              sx={{ position: 'absolute', left: 16, top: '50%', transform: 'translateY(-50%)', color: 'white', zIndex: 1 }}
            >
              <ChevronLeftIcon sx={{ fontSize: 48 }} />
            </IconButton>
          </span>
        </Tooltip>

        {blobUrl
          ? <img src={blobUrl} alt={current.originalName} style={{ maxWidth: '90vw', maxHeight: '90vh', objectFit: 'contain', display: 'block', imageOrientation: 'from-image' }} />
          : <CircularProgress sx={{ color: 'white' }} />
        }

        <Tooltip title="Nächstes Bild">
          <span>
            <IconButton
              aria-label="nächstes Bild"
              onClick={() => next && onNavigate(next.id)}
              disabled={!next}
              sx={{ position: 'absolute', right: 16, top: '50%', transform: 'translateY(-50%)', color: 'white', zIndex: 1 }}
            >
              <ChevronRightIcon sx={{ fontSize: 48 }} />
            </IconButton>
          </span>
        </Tooltip>
      </Box>
    </Modal>
  );
}
