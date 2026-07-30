import { Box, CircularProgress, IconButton, Modal, Tooltip } from '@mui/material';
import CloseIcon from '@mui/icons-material/Close';
import ChevronLeftIcon from '@mui/icons-material/ChevronLeft';
import ChevronRightIcon from '@mui/icons-material/ChevronRight';
import { useEffect } from 'react';
import type { TestimonialAttachmentDto } from '../../types/api';
import { getThumbnailUrl, getStreamUrl } from '../../api/resources';
import { useAuthenticatedUrl } from '../../hooks/useAuthenticatedUrl';
import VideoPlayer from '../viewer/VideoPlayer';
import AudioPlayer from '../viewer/AudioPlayer';

interface Props {
  images: TestimonialAttachmentDto[];
  index: number;
  onClose: () => void;
  onNavigate: (index: number) => void;
}

export default function TestimonialLightbox({ images, index, onClose, onNavigate }: Readonly<Props>) {
  const current = images[index];
  const isImage = current?.mimeType?.startsWith('image/');
  const isVideo = current?.mimeType?.startsWith('video/');
  const isAudio = current?.mimeType?.startsWith('audio/');

  const thumbnailUrl = useAuthenticatedUrl(
    current && isImage ? getThumbnailUrl(current.id, 'large') : null
  );

  // For video/audio, get the stream URL (relative path, will be fetched via axiosClient)
  const streamUrl = current && (isVideo || isAudio) ? getStreamUrl(current.id) : null;
  const authenticatedStreamUrl = useAuthenticatedUrl(streamUrl);

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
    <Modal open onClose={onClose}>
      <Box sx={{ position: 'absolute', inset: 0, display: 'flex', alignItems: 'center', justifyContent: 'center', bgcolor: 'rgba(0,0,0,0.85)' }}>
        <Tooltip title="Schließen">
          <IconButton aria-label="schließen" onClick={onClose}
            sx={{ position: 'absolute', top: 16, right: 16, color: 'white', zIndex: 1 }}>
            <CloseIcon />
          </IconButton>
        </Tooltip>

        <Tooltip title="Vorheriges Element">
          <span>
            <IconButton aria-label="vorheriges Element"
              onClick={() => onNavigate(index - 1)} disabled={index === 0}
              sx={{ position: 'absolute', left: 16, top: '50%', transform: 'translateY(-50%)', color: 'white', zIndex: 1 }}>
              <ChevronLeftIcon sx={{ fontSize: 48 }} />
            </IconButton>
          </span>
        </Tooltip>

        <Box sx={{ maxWidth: '90vw', maxHeight: '90vh', overflow: 'auto' }}>
          {isImage && (
            thumbnailUrl
              ? <img src={thumbnailUrl} alt={current.originalName}
                  style={{ maxWidth: '90vw', maxHeight: '90vh', objectFit: 'contain', display: 'block', imageOrientation: 'from-image' }} />
              : <CircularProgress sx={{ color: 'white' }} />
          )}
          {isVideo && (
            authenticatedStreamUrl
              ? <VideoPlayer src={authenticatedStreamUrl} mimeType={current.mimeType} />
              : <CircularProgress sx={{ color: 'white' }} />
          )}
          {isAudio && (
            authenticatedStreamUrl
              ? <AudioPlayer src={authenticatedStreamUrl} title={current.originalName} />
              : <CircularProgress sx={{ color: 'white' }} />
          )}
        </Box>

        <Tooltip title="Nächstes Element">
          <span>
            <IconButton aria-label="nächstes Element"
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
