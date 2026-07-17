import { useDeferredValue, useEffect, useState } from 'react';
import {
  Alert, Box, CircularProgress, Container,
  InputAdornment, TextField, Typography,
} from '@mui/material';
import SearchIcon from '@mui/icons-material/Search';
import type { PublicTestimonialDto } from '../types/api';
import { listPublicTestimonials } from '../api/publicTestimonials';
import PublicTestimonialCard from '../components/testimonial/PublicTestimonialCard';

function matchesQuery(t: PublicTestimonialDto, q: string): boolean {
  const lower = q.toLowerCase();
  return (
    t.authorName.toLowerCase().includes(lower) ||
    t.text.toLowerCase().includes(lower) ||
    (t.tags?.toLowerCase().includes(lower) ?? false)
  );
}

export default function PublicTestimonialsPage() {
  const [testimonials, setTestimonials] = useState<PublicTestimonialDto[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [q, setQ] = useState('');
  const deferredQ = useDeferredValue(q);

  useEffect(() => {
    listPublicTestimonials()
      .then(data => setTestimonials(data))
      .catch(() => setError('Erfahrungsberichte konnten nicht geladen werden.'))
      .finally(() => setLoading(false));
  }, []);

  const filtered = deferredQ
    ? testimonials.filter(t => matchesQuery(t, deferredQ))
    : testimonials;

  return (
    <Container maxWidth="md" sx={{ py: 4 }}>
      <Typography variant="h4" fontWeight="bold" gutterBottom>
        Erfahrungsberichte von Anwendern mit Cili Produkten
      </Typography>

      {loading && (
        <Box sx={{ display: 'flex', justifyContent: 'center', mt: 6 }}>
          <CircularProgress />
        </Box>
      )}

      {error && <Alert severity="error" sx={{ mt: 2 }}>{error}</Alert>}

      {!loading && !error && (
        <>
          <TextField
            size="small"
            placeholder="Suchen nach Name, Text oder Schlagwort…"
            value={q}
            onChange={e => setQ(e.target.value)}
            fullWidth
            sx={{ mb: 1 }}
            slotProps={{
              input: {
                startAdornment: (
                  <InputAdornment position="start">
                    <SearchIcon fontSize="small" />
                  </InputAdornment>
                ),
              },
            }}
          />

          {testimonials.length > 0 && (
            <Typography variant="caption" color="text.secondary" sx={{ mb: 2, display: 'block' }}>
              {deferredQ
                ? `${filtered.length} von ${testimonials.length} Einträgen`
                : `${testimonials.length} Einträge`}
            </Typography>
          )}

          {filtered.length === 0 && (
            <Typography color="text.secondary" sx={{ mt: 2 }}>
              {q ? `Keine Ergebnisse für „${q}"` : 'Noch keine Erfahrungsberichte vorhanden.'}
            </Typography>
          )}

          {filtered.map(t => (
            <PublicTestimonialCard key={t.id} testimonial={t} />
          ))}
        </>
      )}
    </Container>
  );
}
