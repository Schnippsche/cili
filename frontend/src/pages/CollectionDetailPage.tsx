import {useState} from 'react';
import {useNavigate, useParams} from 'react-router-dom';
import {Box, Button, CircularProgress, IconButton, Tooltip, Typography} from '@mui/material';
import ArrowBackIcon from '@mui/icons-material/ArrowBack';
import SummarizeIcon from '@mui/icons-material/Summarize';
import AppShell from '../components/layout/AppShell';
import ResourceGrid from '../components/resource/ResourceGrid';
import TestimonialCard from '../components/testimonial/TestimonialCard';
import ReportPreviewDialog from '../components/testimonial/ReportPreviewDialog';
import {
  useCollection,
  useCollectionItems,
  useCollectionTestimonials,
  useRemoveFromCollection,
  useRemoveTestimonialFromCollection,
} from '../hooks/useCollections';

export default function CollectionDetailPage() {
  const {id} = useParams<{ id: string }>();
  const collectionId = Number(id);
  const navigate = useNavigate();
  const [reportOpen, setReportOpen] = useState(false);

  const {data: collection, isLoading: collLoading} = useCollection(collectionId);
  const {data: resources = [], isLoading: resLoading} = useCollectionItems(collectionId);
  const {data: testimonials = [], isLoading: testLoading} = useCollectionTestimonials(collectionId);
  const removeMutation = useRemoveFromCollection();
  const removeTestimonialMutation = useRemoveTestimonialFromCollection();

  const isLoading = collLoading || resLoading || testLoading;

  return (
      <AppShell>
        <Box sx={{p: 3}}>
          <Box sx={{display: 'flex', alignItems: 'center', gap: 1, mb: 3}}>
            <Tooltip title="Zurück zu Sammlungen">
              <IconButton onClick={() => navigate('/collections')}>
                <ArrowBackIcon/>
              </IconButton>
            </Tooltip>
            <Typography variant="h5" fontWeight="bold">
              {collection?.name ?? '…'}
            </Typography>
            <Typography variant="body2" color="text.secondary" sx={{ml: 1}}>
              ({resources.length} {resources.length === 1 ? 'Ressource' : 'Ressourcen'})
            </Typography>
            {!isLoading && resources.length === 0 && testimonials.length > 0 && (
                <Tooltip title="Sammlung als druckbaren Bericht zusammenstellen">
                  <Button
                      startIcon={<SummarizeIcon/>}
                      size="small"
                      variant="outlined"
                      onClick={() => setReportOpen(true)}
                      sx={{ml: 'auto'}}
                  >
                    Bericht generieren
                  </Button>
                </Tooltip>
            )}
          </Box>

          {isLoading && <CircularProgress/>}

          {!isLoading && resources.length === 0 && testimonials.length === 0 && (
              <Typography color="text.secondary">
                Diese Sammlung ist leer. Füge Ressourcen oder Erfahrungsberichte über das
                Kontextmenü hinzu.
              </Typography>
          )}

          {!isLoading && resources.length > 0 && (
              <ResourceGrid
                  resources={resources}
                  onRemoveFromCollection={(resourceId: number) =>
                      removeMutation.mutate({collectionId, resourceId})
                  }
              />
          )}

          {!isLoading && testimonials.length > 0 && (
              <Box sx={{mt: resources.length > 0 ? 4 : 0}}>
                <Typography variant="h6" sx={{mb: 2}}>
                  Erfahrungsberichte ({testimonials.length})
                </Typography>
                {testimonials.map(t => (
                    <TestimonialCard
                        key={t.id}
                        testimonial={t}
                        currentUserId={0}
                        isAdmin={false}
                        canWrite={false}
                        canDelete={false}
                        onEdit={() => {
                        }}
                        onDelete={() => {
                        }}
                        onRemoveFromCollection={(testimonialId: number) =>
                            removeTestimonialMutation.mutate({collectionId, testimonialId})
                        }
                    />
                ))}
              </Box>
          )}

          <ReportPreviewDialog
              open={reportOpen}
              q={collection?.name ?? ''}
              collectionId={collectionId}
              onClose={() => setReportOpen(false)}
          />
        </Box>
      </AppShell>
  );
}
