import {Alert, Box, CircularProgress, Container, Typography} from '@mui/material';
import {useParams, Link as RouterLink} from 'react-router-dom';
import {useQuery} from '@tanstack/react-query';
import {getPublicTestimonial} from '../api/publicTestimonials';
import PublicTestimonialCard from '../components/testimonial/PublicTestimonialCard';

export default function PublicTestimonialDetailPage() {
  const {id} = useParams<{ id: string }>();
  const testimonialId = Number(id);

  const {data, isLoading, error} = useQuery({
    queryKey: ['public-testimonial', testimonialId],
    queryFn: () => getPublicTestimonial(testimonialId),
    enabled: Number.isFinite(testimonialId),
  });

  return (
      <Container maxWidth="md" sx={{py: 4}}>
        <Typography variant="body2" sx={{mb: 2}}>
          <RouterLink to="/erfahrungsberichte">← Zurück zur Übersicht</RouterLink>
        </Typography>

        {isLoading && (
            <Box sx={{display: 'flex', justifyContent: 'center', mt: 6}}>
              <CircularProgress/>
            </Box>
        )}

        {error && (
            <Alert severity="error" sx={{mt: 2}}>
              Erfahrungsbericht nicht gefunden oder wurde gelöscht.
            </Alert>
        )}

        {!isLoading && !error && data && (
            <PublicTestimonialCard testimonial={data}/>
        )}
      </Container>
  );
}
