import { Box, Card, CardContent, Chip, Stack, Typography } from '@mui/material';
import { useState } from 'react';
import PersonIcon from '@mui/icons-material/Person';
import PetsIcon from '@mui/icons-material/Pets';
import PlayArrowIcon from '@mui/icons-material/PlayArrow';
import MusicNoteIcon from '@mui/icons-material/MusicNote';
import type { PublicTestimonialDto, TestimonialAttachmentDto } from '../../types/api';
import { publicImageUrl } from '../../api/publicTestimonials';
import PublicTestimonialLightbox from './PublicTestimonialLightbox';

interface AttachmentTileProps {
  attachment: TestimonialAttachmentDto;
  onClick: () => void;
}

function AttachmentTile({ attachment, onClick }: Readonly<AttachmentTileProps>) {
  const isImage = attachment.mimeType?.startsWith('image/');
  const isVideo = attachment.mimeType?.startsWith('video/');
  const isAudio = attachment.mimeType?.startsWith('audio/');

  return (
    <Box
      onClick={onClick}
      sx={{
        position: 'relative',
        width: 80,
        height: 80,
        borderRadius: 1,
        cursor: 'pointer',
        bgcolor: 'action.hover',
        '&:hover': { opacity: 0.85, transform: 'scale(1.03)', transition: 'all .15s' },
        overflow: 'hidden',
      }}
    >
      {isImage && (
        <Box
          component="img"
          src={publicImageUrl(attachment.id, 'small')}
          alt={attachment.originalName}
          sx={{
            width: 80,
            height: 80,
            objectFit: 'cover',
          }}
        />
      )}
      {isVideo && (
        <>
          {attachment.thumbnailStatus === 'DONE' && (
            <Box
              component="img"
              src={publicImageUrl(attachment.id, 'small')}
              alt={attachment.originalName}
              sx={{
                width: 80,
                height: 80,
                objectFit: 'cover',
              }}
            />
          )}
          <Box
            sx={{
              position: 'absolute',
              inset: 0,
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              bgcolor: 'rgba(0,0,0,0.3)',
            }}
          >
            <PlayArrowIcon sx={{ fontSize: 32, color: 'white' }} />
          </Box>
        </>
      )}
      {isAudio && (
        <Box
          sx={{
            width: 80,
            height: 80,
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
          }}
        >
          <MusicNoteIcon sx={{ fontSize: 40, color: 'text.secondary' }} />
        </Box>
      )}
    </Box>
  );
}

interface Props {
  testimonial: PublicTestimonialDto;
}

export default function PublicTestimonialCard({ testimonial }: Readonly<Props>) {
  const [lightboxIndex, setLightboxIndex] = useState<number | null>(null);
  const tagList = testimonial.tags
    ? testimonial.tags.split(',').map(t => t.trim()).filter(Boolean)
    : [];

  return (
    <Card variant="outlined" sx={{ mb: 2 }}>
      <CardContent>
        <Stack direction="row" alignItems="center" flexWrap="wrap" gap={1}>
          {testimonial.human && <Chip icon={<PersonIcon />} label="Mensch" size="small" color="primary" />}
          {testimonial.animal && <Chip icon={<PetsIcon />} label="Tier" size="small" color="success" />}
          <Typography variant="subtitle1" fontWeight="bold">{testimonial.authorName}</Typography>
        </Stack>

        <Box sx={{ display: 'flex', flexWrap: 'wrap', alignItems: 'center', gap: 0.5, mt: 0.25 }}>
          <Typography variant="caption" color="text.secondary">
            {new Date(testimonial.createdAt).toLocaleDateString('de-DE', { day: '2-digit', month: '2-digit', year: 'numeric' })}
          </Typography>
          {tagList.map(tag => (
            <Chip key={tag} label={tag} size="small" color="info" sx={{ opacity: 0.85 }} />
          ))}
        </Box>

        <Typography variant="body2" sx={{ mt: 1, whiteSpace: 'pre-wrap' }}>
          {testimonial.text}
        </Typography>

        {testimonial.attachments.length > 0 && (
          <Stack direction="row" flexWrap="wrap" gap={1} sx={{ mt: 1.5 }}>
            {testimonial.attachments.map((attachment, idx) => (
              <AttachmentTile
                key={attachment.id}
                attachment={attachment}
                onClick={() => setLightboxIndex(idx)}
              />
            ))}
          </Stack>
        )}
      </CardContent>

      {lightboxIndex !== null && (
        <PublicTestimonialLightbox
          images={testimonial.attachments}
          testimonialId={testimonial.id}
          index={lightboxIndex}
          onClose={() => setLightboxIndex(null)}
          onNavigate={setLightboxIndex}
        />
      )}
    </Card>
  );
}
