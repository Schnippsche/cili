import { Box, IconButton, Modal, Tooltip } from '@mui/material';
import CloseIcon from '@mui/icons-material/Close';
import ChevronLeftIcon from '@mui/icons-material/ChevronLeft';
import ChevronRightIcon from '@mui/icons-material/ChevronRight';
import { useEffect } from 'react';
import type { TestimonialImageDto } from '../../types/api';
import { publicImageUrl } from '../../api/publicTestimonials';

interface Props {
  images: TestimonialImageDto[];
  index: number;
  onClose: () => void;
  onNavigate: (index: number) => void;
}

export default function PublicTestimonialLightbox({ images, index, onClose, onNavigate }: Readonly<Props>) {
  const current = images[index];

  useEffect(() => {
    const handler = (e: KeyboardEvent) => {
      if (e.key === 'ArrowLeft'  && index > 0)                 onNavigate(index - 1);
      if (e.key === 'ArrowRight' && index < images.length - 1) onNavigate(index + 1);
      if (e.key === 'Escape')                                   onClose();
    };
    globalThis.addEventListener('keydown', handler);
    return () => globalThis.removeEventListener('keydown', handler);
  }, [index, images.length, onNavigate, onClose]);

  if (!current) return null;

  return (
    <Modal open onClose={onClose} role="dialog">
      <Box sx={{ position: 'absolute', inset: 0, display: 'flex', alignItems: 'center', justifyContent: 'center', bgcolor: 'rgba(0,0,0,0.85)' }}>
        <Tooltip title="Schließen">
          <IconButton aria-label="schließen" onClick={onClose}
            sx={{ position: 'absolute', top: 16, right: 16, color: 'white', zIndex: 1 }}>
            <CloseIcon />
          </IconButton>
        </Tooltip>

        <Tooltip title="Vorheriges Bild">
          <span>
            <IconButton aria-label="vorheriges Bild"
              onClick={() => onNavigate(index - 1)} disabled={index === 0}
              sx={{ position: 'absolute', left: 16, top: '50%', transform: 'translateY(-50%)', color: 'white', zIndex: 1 }}>
              <ChevronLeftIcon sx={{ fontSize: 48 }} />
            </IconButton>
          </span>
        </Tooltip>

        <img
          src={publicImageUrl(current.id, 'large')}
          alt={current.originalName}
          style={{ maxWidth: '90vw', maxHeight: '90vh', objectFit: 'contain', display: 'block', imageOrientation: 'from-image' }}
        />

        <Tooltip title="Nächstes Bild">
          <span>
            <IconButton aria-label="nächstes Bild"
              onClick={() => onNavigate(index + 1)} disabled={index === images.length - 1}
              sx={{ position: 'absolute', right: 16, top: '50%', transform: 'translateY(-50%)', color: 'white', zIndex: 1 }}>
              <ChevronRightIcon sx={{ fontSize: 48 }} />
            </IconButton>
          </span>
        </Tooltip>
      </Box>
    </Modal>
  );
}
