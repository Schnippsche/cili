import { Box, Card, CardContent, Chip, IconButton, Stack, Tooltip, Typography } from '@mui/material';
import DeleteIcon from '@mui/icons-material/Delete';
import EditIcon from '@mui/icons-material/Edit';
import BookmarkAddOutlinedIcon from '@mui/icons-material/BookmarkAddOutlined';
import BookmarkRemoveOutlinedIcon from '@mui/icons-material/BookmarkRemoveOutlined';
import PersonIcon from '@mui/icons-material/Person';
import PetsIcon from '@mui/icons-material/Pets';
import { useState } from 'react';
import type { TestimonialDto, TestimonialImageDto } from '../../types/api';
import { getThumbnailUrl } from '../../api/resources';
import { useAuthenticatedUrl } from '../../hooks/useAuthenticatedUrl';
import TestimonialLightbox from './TestimonialLightbox';
import AddToCollectionDialog from '../resource/AddToCollectionDialog';

interface TileProps {
  image: TestimonialImageDto;
  onClick: () => void;
}

function ImageTile({ image, onClick }: Readonly<TileProps>) {
  const url = useAuthenticatedUrl(getThumbnailUrl(image.id, 'small'));
  return (
    <Box
      component="img"
      src={url ?? undefined}
      alt={image.originalName}
      onClick={onClick}
      sx={{
        width: 80, height: 80, objectFit: 'cover', borderRadius: 1,
        cursor: 'pointer', bgcolor: 'action.hover',
        '&:hover': { opacity: 0.85, transform: 'scale(1.03)', transition: 'all .15s' },
      }}
    />
  );
}

interface Props {
  testimonial: TestimonialDto;
  currentUserId: number;
  isAdmin: boolean;
  canWrite: boolean;
  canDelete: boolean;
  onEdit: (t: TestimonialDto) => void;
  onDelete: (id: number) => void;
  onRemoveFromCollection?: (testimonialId: number) => void;
}

export default function TestimonialCard({ testimonial, currentUserId, isAdmin, canWrite, canDelete, onEdit, onDelete, onRemoveFromCollection }: Readonly<Props>) {
  const [expanded, setExpanded] = useState(false);
  const [lightboxIndex, setLightboxIndex] = useState<number | null>(null);
  const [collectionOpen, setCollectionOpen] = useState(false);
  const isOwner = testimonial.userId === currentUserId;
  const showEdit   = canWrite  && (isAdmin || isOwner);
  const showDelete = canDelete && (isAdmin || isOwner);
  const showAddToCollection = !onRemoveFromCollection;
  const isLong = testimonial.text.length > 200;
  const displayText = isLong && !expanded ? testimonial.text.slice(0, 200) + '…' : testimonial.text;
  const tagList = testimonial.tags ? testimonial.tags.split(',').map(t => t.trim()).filter(Boolean) : [];

  return (
    <Card variant="outlined" sx={{ mb: 2 }}>
      <CardContent>
        {/* Zeile 1: Author links, Aktionsbuttons rechts */}
        <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <Stack direction="row" alignItems="center" gap={1}>
            {testimonial.source === 'Tier' ? (
              <Chip icon={<PetsIcon />} label="Tier" size="small" color="success" />
            ) : (
              <Chip icon={<PersonIcon />} label="Mensch" size="small" color="primary" />
            )}
            <Typography variant="subtitle1" fontWeight="bold">{testimonial.authorName}</Typography>
          </Stack>
          <Box sx={{ flexShrink: 0, ml: 1 }}>
            {showEdit && (
              <Tooltip title="Bearbeiten">
                <IconButton size="small" aria-label="Bearbeiten" onClick={() => onEdit(testimonial)}>
                  <EditIcon fontSize="small" />
                </IconButton>
              </Tooltip>
            )}
            {showDelete && (
              <Tooltip title="Löschen">
                <IconButton size="small" aria-label="Löschen" onClick={() => onDelete(testimonial.id)}>
                  <DeleteIcon fontSize="small" />
                </IconButton>
              </Tooltip>
            )}
            {showAddToCollection && (
              <Tooltip title="Zur Sammlung hinzufügen">
                <IconButton size="small" aria-label="Zur Sammlung hinzufügen" onClick={() => setCollectionOpen(true)}>
                  <BookmarkAddOutlinedIcon fontSize="small" />
                </IconButton>
              </Tooltip>
            )}
            {onRemoveFromCollection && (
              <Tooltip title="Aus Sammlung entfernen">
                <IconButton size="small" aria-label="Aus Sammlung entfernen" onClick={() => onRemoveFromCollection(testimonial.id)}>
                  <BookmarkRemoveOutlinedIcon fontSize="small" />
                </IconButton>
              </Tooltip>
            )}
          </Box>
        </Box>
        {/* Zeile 2: Datum und Tags */}
        <Box sx={{ display: 'flex', flexWrap: 'wrap', alignItems: 'center', gap: 0.5, mt: 0.25 }}>
          <Typography variant="caption" color="text.secondary">
            {new Date(testimonial.createdAt).toLocaleDateString('de-DE')}
          </Typography>
          {tagList.map(tag => (
            <Chip key={tag} label={tag} size="small" color="info" sx={{ opacity: 0.85 }} />
          ))}
        </Box>

        <Typography variant="body2" sx={{ mt: 1, whiteSpace: 'pre-wrap' }}>
          {displayText}
        </Typography>
        {isLong && (
          <Typography variant="caption" color="primary"
            sx={{ cursor: 'pointer', mt: 0.5, display: 'block' }}
            onClick={() => setExpanded(e => !e)}>
            {expanded ? 'Weniger anzeigen' : 'Mehr anzeigen'}
          </Typography>
        )}

        {testimonial.images.length > 0 && (
          <Stack direction="row" flexWrap="wrap" gap={1} sx={{ mt: 1.5 }}>
            {testimonial.images.map((img, idx) => (
              <ImageTile
                key={img.id}
                image={img}
                onClick={() => setLightboxIndex(idx)}
              />
            ))}
          </Stack>
        )}
      </CardContent>

      {lightboxIndex !== null && (
        <TestimonialLightbox
          images={testimonial.images}
          index={lightboxIndex}
          onClose={() => setLightboxIndex(null)}
          onNavigate={setLightboxIndex}
        />
      )}

      <AddToCollectionDialog
        open={collectionOpen}
        itemId={testimonial.id}
        itemType="testimonial"
        onClose={() => setCollectionOpen(false)}
      />
    </Card>
  );
}
