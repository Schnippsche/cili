import {useState} from 'react';
import {useParams} from 'react-router-dom';
import {useQuery} from '@tanstack/react-query';
import {
  Alert,
  Box,
  Card,
  CardActionArea,
  CardContent,
  CardMedia,
  CircularProgress,
  Container,
  Dialog,
  DialogContent,
  IconButton,
  Tooltip,
  Typography,
} from '@mui/material';
import CloseIcon from '@mui/icons-material/Close';
import BrokenImageIcon from '@mui/icons-material/BrokenImage';
import {
  CollectionShareExpiredError,
  collectionShareStreamUrl,
  collectionShareSubtitleUrl,
  collectionShareThumbnailUrl,
  getCollectionShareInfo,
} from '../api/collectionShare';
import type {PublicTestimonialDto, SharedResourceItem} from '../types/api';
import VideoPlayer from '../components/viewer/VideoPlayer';
import PdfViewer from '../components/viewer/PdfViewer';

export default function SharedCollectionPage() {
  const {token} = useParams<{ token: string }>();
  const [selected, setSelected] = useState<SharedResourceItem | null>(null);

  const {data: info, isLoading, isError, error} = useQuery({
    queryKey: ['sharedCollection', token],
    queryFn: () => getCollectionShareInfo(token!),
    retry: false,
    enabled: !!token,
  });

  if (isLoading) {
    return (
        <Box sx={{display: 'flex', justifyContent: 'center', mt: 10}}>
          <CircularProgress/>
        </Box>
    );
  }

  if (isError || !info) {
    const expired = error instanceof CollectionShareExpiredError;
    return (
        <Container maxWidth="sm" sx={{mt: 10, textAlign: 'center'}}>
          <Typography variant="h5" gutterBottom>
            {expired ? 'Link abgelaufen' : 'Link ungültig'}
          </Typography>
          <Typography color="text.secondary">
            {expired
                ? 'Dieser Freigabe-Link ist abgelaufen. Bitte fordere einen neuen Link an.'
                : 'Dieser Link ist ungültig oder wurde widerrufen.'}
          </Typography>
        </Container>
    );
  }

  const expiresAt = new Date(info.expiresAt).toLocaleDateString('de-DE', {
    day: '2-digit', month: '2-digit', year: 'numeric',
  });

  return (
      <Container maxWidth="lg" sx={{mt: 4, mb: 6}}>
        <Typography variant="h4" fontWeight="bold" gutterBottom>
          {info.collectionName}
        </Typography>
        <Alert severity="info" sx={{mb: 3}}>
          Verfügbar bis {expiresAt}
        </Alert>

        {info.resources.length > 0 && (
            <Box sx={{
              display: 'grid',
              gridTemplateColumns: 'repeat(auto-fill, minmax(160px, 1fr))',
              gap: 2,
              mb: 4
            }}>
              {info.resources.map(r => (
                  <ResourceTile key={r.id} resource={r} token={token!} onSelect={setSelected}/>
              ))}
            </Box>
        )}

        {info.testimonials.length > 0 && (
            <Box>
              <Typography variant="h6" sx={{mb: 2}}>
                Erfahrungsberichte ({info.testimonials.length})
              </Typography>
              {info.testimonials.map(t => (
                  <SharedTestimonialCard key={t.id} testimonial={t} token={token!}/>
              ))}
            </Box>
        )}

        {selected && (
            <Dialog
                open
                onClose={() => setSelected(null)}
                maxWidth="lg"
                fullWidth
                PaperProps={{sx: {bgcolor: 'black'}}}
            >
              <DialogContent sx={{p: 0, position: 'relative'}}>
                <Tooltip title="Schließen">
                  <IconButton
                      onClick={() => setSelected(null)}
                      sx={{position: 'absolute', top: 8, right: 8, color: 'white', zIndex: 1}}
                      aria-label="Schließen"
                  >
                    <CloseIcon/>
                  </IconButton>
                </Tooltip>
                <ResourceViewer resource={selected} token={token!}/>
              </DialogContent>
            </Dialog>
        )}
      </Container>
  );
}

function ResourceTile({resource, token, onSelect}: {
  resource: SharedResourceItem;
  token: string;
  onSelect: (r: SharedResourceItem) => void;
}) {
  return (
      <Card variant="outlined" sx={{'&:hover': {boxShadow: 2}}}>
        <CardActionArea onClick={() => onSelect(resource)}>
          {resource.hasThumbnail ? (
              <CardMedia
                  component="img"
                  height="120"
                  image={collectionShareThumbnailUrl(token, resource.id)}
                  alt={resource.originalName}
                  sx={{objectFit: 'cover'}}
              />
          ) : (
              <Box sx={{
                height: 120,
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                bgcolor: 'action.hover'
              }}>
                <BrokenImageIcon color="disabled"/>
              </Box>
          )}
          <CardContent sx={{p: 1}}>
            <Typography variant="caption" noWrap title={resource.originalName}>
              {resource.originalName}
            </Typography>
          </CardContent>
        </CardActionArea>
      </Card>
  );
}

function ResourceViewer({resource, token}: { resource: SharedResourceItem; token: string }) {
  const src = collectionShareStreamUrl(token, resource.id);
  const isVideo = resource.mimeType.startsWith('video/');
  const isAudio = resource.mimeType.startsWith('audio/');
  const isImage = resource.mimeType.startsWith('image/');
  const isDocument = resource.mimeType === 'application/pdf'
      || resource.mimeType.includes('officedocument')
      || resource.mimeType.includes('opendocument');

  const subtitles = resource.subtitles.map(t => ({
    src: collectionShareSubtitleUrl(token, resource.id, t.id),
    label: t.label ?? t.languageCode,
    language: t.languageCode,
  }));

  if (isVideo || isAudio) {
    return (
        <Box sx={{bgcolor: 'black', p: 2}}>
          <VideoPlayer src={src} mimeType={resource.mimeType} audioOnly={isAudio}
                       subtitles={subtitles}/>
        </Box>
    );
  }
  if (isImage) {
    return (
        <Box sx={{textAlign: 'center', bgcolor: 'black', p: 2}}>
          <Box
              component="img"
              src={src}
              alt={resource.originalName}
              sx={{maxWidth: '100%', maxHeight: '85vh', objectFit: 'contain'}}
          />
        </Box>
    );
  }
  if (isDocument) {
    return <PdfViewer src={src}/>;
  }
  return (
      <Box sx={{p: 4, color: 'white', textAlign: 'center'}}>
        <Typography>Dieser Dateityp kann nicht direkt angezeigt werden.</Typography>
      </Box>
  );
}

function SharedTestimonialCard({testimonial, token}: {
  testimonial: PublicTestimonialDto;
  token: string;
}) {
  const [lightboxIndex, setLightboxIndex] = useState<number | null>(null);
  const tagList = testimonial.tags
      ? testimonial.tags.split(',').map(t => t.trim()).filter(Boolean)
      : [];

  return (
      <Card variant="outlined" sx={{mb: 2}}>
        <CardContent>
          <Typography variant="subtitle1" fontWeight="bold">{testimonial.authorName}</Typography>
          <Box sx={{display: 'flex', flexWrap: 'wrap', alignItems: 'center', gap: 0.5, mt: 0.25}}>
            <Typography variant="caption" color="text.secondary">
              {new Date(testimonial.createdAt).toLocaleDateString('de-DE', {
                day: '2-digit',
                month: '2-digit',
                year: 'numeric'
              })}
            </Typography>
            {tagList.map(tag => (
                <Box key={tag} component="span" sx={{
                  bgcolor: 'info.light',
                  borderRadius: 1,
                  px: 0.75,
                  py: 0.25,
                  fontSize: '0.7rem'
                }}>
                  {tag}
                </Box>
            ))}
          </Box>
          <Typography variant="body2"
                      sx={{mt: 1, whiteSpace: 'pre-wrap'}}>{testimonial.text}</Typography>
          {testimonial.attachments.length > 0 && (
              <Box sx={{display: 'flex', flexWrap: 'wrap', gap: 1, mt: 1.5}}>
                {testimonial.attachments.map((img, idx) => (
                    <Box
                        key={img.id}
                        component="img"
                        src={collectionShareThumbnailUrl(token, img.id)}
                        alt={img.originalName}
                        onClick={() => setLightboxIndex(idx)}
                        sx={{
                          width: 80, height: 80, objectFit: 'cover', borderRadius: 1,
                          cursor: 'pointer', '&:hover': {opacity: 0.85},
                        }}
                    />
                ))}
              </Box>
          )}
        </CardContent>

        {lightboxIndex !== null && (
            <Dialog open onClose={() => setLightboxIndex(null)} maxWidth="lg"
                    PaperProps={{sx: {bgcolor: 'rgba(0,0,0,0.9)'}}}>
              <DialogContent sx={{p: 0, position: 'relative', textAlign: 'center'}}>
                <Tooltip title="Schließen">
                  <IconButton onClick={() => setLightboxIndex(null)}
                              sx={{
                                position: 'absolute',
                                top: 8,
                                right: 8,
                                color: 'white',
                                zIndex: 1
                              }}
                              aria-label="Schließen">
                    <CloseIcon/>
                  </IconButton>
                </Tooltip>
                <Box
                    component="img"
                    src={collectionShareStreamUrl(token, testimonial.attachments[lightboxIndex].id)}
                    alt={testimonial.attachments[lightboxIndex].originalName}
                    sx={{
                      maxWidth: '90vw',
                      maxHeight: '90vh',
                      objectFit: 'contain',
                      display: 'block'
                    }}
                />
              </DialogContent>
            </Dialog>
        )}
      </Card>
  );
}
