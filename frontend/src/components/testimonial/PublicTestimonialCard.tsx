import { Box, Card, CardContent, Chip, Stack, Typography } from '@mui/material';
import { useState } from 'react';
import PersonIcon from '@mui/icons-material/Person';
import PetsIcon from '@mui/icons-material/Pets';
import type { PublicTestimonialDto } from '../../types/api';
import { publicImageUrl } from '../../api/publicTestimonials';
import PublicTestimonialLightbox from './PublicTestimonialLightbox';

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
        <Stack direction="row" alignItems="center" gap={1}>
          {testimonial.source === 'Tier' ? (
            <Chip icon={<PetsIcon />} label="Tier" size="small" color="success" />
          ) : (
            <Chip icon={<PersonIcon />} label="Mensch" size="small" color="primary" />
          )}
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
            {testimonial.attachments.map((img, idx) => (
              <Box
                key={img.id}
                component="img"
                src={publicImageUrl(img.id, 'small')}
                alt={img.originalName}
                onClick={() => setLightboxIndex(idx)}
                sx={{
                  width: 80, height: 80, objectFit: 'cover', borderRadius: 1,
                  cursor: 'pointer', bgcolor: 'action.hover',
                  '&:hover': { opacity: 0.85, transform: 'scale(1.03)', transition: 'all .15s' },
                }}
              />
            ))}
          </Stack>
        )}
      </CardContent>

      {lightboxIndex !== null && (
        <PublicTestimonialLightbox
          images={testimonial.attachments}
          index={lightboxIndex}
          onClose={() => setLightboxIndex(null)}
          onNavigate={setLightboxIndex}
        />
      )}
    </Card>
  );
}
