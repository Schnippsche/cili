import {useDeferredValue, useState} from 'react';
import {useQuery} from '@tanstack/react-query';
import {
  Alert,
  Box,
  CircularProgress,
  Container,
  InputAdornment,
  Pagination,
  TextField,
  ToggleButton,
  ToggleButtonGroup,
  Typography,
} from '@mui/material';
import SearchIcon from '@mui/icons-material/Search';
import {listPublicTestimonials} from '../api/publicTestimonials';
import PublicTestimonialCard from '../components/testimonial/PublicTestimonialCard';
import TestimonialDisclaimer from '../components/testimonial/TestimonialDisclaimer';

export default function PublicTestimonialsPage() {
  const [q, setQ] = useState('');
  const deferredQ = useDeferredValue(q);
  const [sourceFilter, setSourceFilter] = useState<'Mensch' | 'Tier' | ''>('');
  const [page, setPage] = useState(0);

  const {data, isLoading, error} = useQuery({
    queryKey: ['public-testimonials', deferredQ, sourceFilter, page],
    queryFn: () => listPublicTestimonials({
      q: deferredQ || undefined, source: sourceFilter || undefined, page, size: 25,
    }),
  });

  const testimonials = data?.content ?? [];

  return (
      <Container maxWidth="md" sx={{py: 4}}>
        <Typography variant="h4" fontWeight="bold" gutterBottom>
          Erfahrungsberichte unserer Anwender — für Mensch und Tier
        </Typography>

        <TestimonialDisclaimer/>

        {isLoading && (
            <Box sx={{display: 'flex', justifyContent: 'center', mt: 6}}>
              <CircularProgress/>
            </Box>
        )}

        {error && <Alert severity="error" sx={{mt: 2}}>Erfahrungsberichte konnten nicht geladen
          werden.</Alert>}

        {!isLoading && !error && (
            <>
              <ToggleButtonGroup
                  value={sourceFilter}
                  exclusive
                  onChange={(_, val) => {
                    setSourceFilter(val ?? '');
                    setPage(0);
                  }}
                  size="small"
                  sx={{mb: 2}}
              >
                <ToggleButton value="">Beide</ToggleButton>
                <ToggleButton value="Mensch">Mensch</ToggleButton>
                <ToggleButton value="Tier">Tier</ToggleButton>
              </ToggleButtonGroup>

              <TextField
                  size="small"
                  placeholder="Suchen nach Name, Text oder Schlagwort…"
                  value={q}
                  onChange={e => {
                    setQ(e.target.value);
                    setPage(0);
                  }}
                  fullWidth
                  sx={{mb: 1}}
                  slotProps={{
                    input: {
                      startAdornment: (
                          <InputAdornment position="start">
                            <SearchIcon fontSize="small"/>
                          </InputAdornment>
                      ),
                    },
                  }}
              />

              {data && (
                  <Typography variant="caption" color="text.secondary"
                              sx={{mb: 2, display: 'block'}}>
                    {data.page.totalElements} Einträge · Seite {page + 1} / {data.page.totalPages}
                  </Typography>
              )}

              {testimonials.length === 0 && (
                  <Typography color="text.secondary" sx={{mt: 2}}>
                    {q || sourceFilter ? 'Keine Ergebnisse für diese Auswahl.' : 'Noch keine Erfahrungsberichte vorhanden.'}
                  </Typography>
              )}

              {testimonials.map(t => (
                  <PublicTestimonialCard key={t.id} testimonial={t}/>
              ))}

              {data && data.page.totalPages > 1 && (
                  <Box sx={{display: 'flex', justifyContent: 'center', mt: 3}}>
                    <Pagination
                        count={data.page.totalPages}
                        page={page + 1}
                        onChange={(_, p) => setPage(p - 1)}
                    />
                  </Box>
              )}
            </>
        )}
      </Container>
  );
}
