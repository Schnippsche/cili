import {
  Alert,
  Box,
  Button,
  CircularProgress,
  InputAdornment,
  Pagination,
  TextField,
  ToggleButton,
  ToggleButtonGroup,
  Tooltip,
  Typography,
} from '@mui/material';
import SearchIcon from '@mui/icons-material/Search';
import AddIcon from '@mui/icons-material/Add';
import PlaylistAddIcon from '@mui/icons-material/PlaylistAdd';
import SummarizeIcon from '@mui/icons-material/Summarize';
import ReportPreviewDialog from './ReportPreviewDialog';
import BulkAddToCollectionDialog from '../resource/BulkAddToCollectionDialog';
import {useMutation, useQuery, useQueryClient} from '@tanstack/react-query';
import {useDeferredValue, useEffect, useRef, useState} from 'react';
import {useSelector} from 'react-redux';
import {
  createTestimonial,
  deleteTestimonial,
  getTestimonial,
  listTestimonials,
  type TestimonialFormData,
  updateTestimonial,
} from '../../api/testimonials';
import type {RootState} from '../../store/store';
import type {TestimonialDto} from '../../types/api';
import TestimonialCard from './TestimonialCard';
import TestimonialForm from './TestimonialForm';

function hasPendingThumbnail(testimonials: TestimonialDto[]): boolean {
  return testimonials.some(t =>
      t.attachments.some(a => a.thumbnailStatus === 'PENDING' || a.thumbnailStatus === 'PROCESSING')
  );
}

interface Props {
  canWrite: boolean;
  canDelete: boolean;
  highlightId?: number;
}

export default function TestimonialList({canWrite, canDelete, highlightId}: Readonly<Props>) {
  const queryClient = useQueryClient();
  const currentUser = useSelector((s: RootState) => s.auth.user);
  const isAdmin = currentUser?.role === 'ADMIN';
  const highlightRef = useRef<HTMLDivElement>(null);

  const {data: highlightedItem} = useQuery({
    queryKey: ['testimonial', highlightId],
    queryFn: () => getTestimonial(highlightId!),
    enabled: highlightId != null,
    refetchInterval: (query) => hasPendingThumbnail(query.state.data ? [query.state.data] : []) ? 3000 : false,
  });

  useEffect(() => {
    if (highlightedItem && highlightRef.current) {
      highlightRef.current.scrollIntoView({behavior: 'smooth', block: 'start'});
    }
  }, [highlightedItem]);

  const [q, setQ] = useState('');
  const deferredQ = useDeferredValue(q);
  const [page, setPage] = useState(0);
  const [sourceFilter, setSourceFilter] = useState<'Mensch' | 'Tier' | ''>('');
  const [formOpen, setFormOpen] = useState(false);
  const [reportOpen, setReportOpen] = useState(false);
  const [bulkDialogOpen, setBulkDialogOpen] = useState(false);
  const [editTarget, setEditTarget] = useState<TestimonialDto | null>(null);

  const {data, isLoading} = useQuery({
    queryKey: ['testimonials', deferredQ, sourceFilter, page],
    queryFn: () => listTestimonials({
      q: deferredQ || undefined,
      source: sourceFilter || undefined,
      page,
      size: 25
    }),
    refetchInterval: (query) => hasPendingThumbnail(query.state.data?.content ?? []) ? 3000 : false,
  });

  const createMut = useMutation({
    mutationFn: createTestimonial,
    onSuccess: () => void queryClient.invalidateQueries({queryKey: ['testimonials']}),
  });

  const updateMut = useMutation({
    mutationFn: ({id, req}: { id: number; req: Parameters<typeof updateTestimonial>[1] }) =>
        updateTestimonial(id, req),
    onSuccess: () => void queryClient.invalidateQueries({queryKey: ['testimonials']}),
  });

  const deleteMut = useMutation({
    mutationFn: deleteTestimonial,
    onSuccess: () => void queryClient.invalidateQueries({queryKey: ['testimonials']}),
  });

  function openCreate() {
    setEditTarget(null);
    setFormOpen(true);
  }

  function openEdit(t: TestimonialDto) {
    setEditTarget(t);
    setFormOpen(true);
  }

  async function handleSave(data: TestimonialFormData) {
    if (editTarget) {
      return await updateMut.mutateAsync({id: editTarget.id, req: data});
    } else {
      return await createMut.mutateAsync(data);
    }
  }

  return (
      <Box id="testimonials">
        <Box sx={{display: 'flex', flexWrap: 'wrap', gap: 1, mb: 2}}>
          {deferredQ && (data?.page.totalElements ?? 0) > 0 && (
              <Tooltip title="Aktuelles Suchergebnis als druckbaren Bericht zusammenstellen">
                <Button startIcon={<SummarizeIcon/>} size="small" variant="outlined"
                        onClick={() => setReportOpen(true)}>
                  Bericht generieren
                </Button>
              </Tooltip>
          )}
          {deferredQ && (data?.content.length ?? 0) > 0 && (
              <Tooltip
                  title="Alle Treffer des aktuellen Suchergebnisses zu einer Sammlung hinzufügen">
                <Button startIcon={<PlaylistAddIcon/>} size="small" variant="outlined"
                        onClick={() => setBulkDialogOpen(true)}>
                  Alle in Sammlung
                </Button>
              </Tooltip>
          )}
          {canWrite && (
              <Tooltip title="Neuen Erfahrungsbericht anlegen">
                <Button startIcon={<AddIcon/>} size="small" variant="outlined" onClick={openCreate}>
                  Neuen Eintrag erfassen
                </Button>
              </Tooltip>
          )}
        </Box>

        {data && (
            <Typography variant="caption" color="text.secondary" sx={{mb: 1, display: 'block'}}>
              {data.page.totalElements} Einträge · Seite {page + 1} / {data.page.totalPages}
            </Typography>
        )}

        {highlightedItem && (
            <Box ref={highlightRef} sx={{
              mb: 3,
              border: '2px solid',
              borderColor: 'primary.main',
              borderRadius: 2,
              p: 0.5
            }}>
              <Typography variant="caption" color="primary" sx={{px: 1}}>Suchergebnis</Typography>
              <TestimonialCard
                  testimonial={highlightedItem}
                  currentUserId={currentUser?.id ?? 0}
                  isAdmin={isAdmin}
                  canWrite={canWrite}
                  canDelete={canDelete}
                  onEdit={openEdit}
                  onDelete={id => deleteMut.mutate(id)}
              />
            </Box>
        )}

        <ToggleButtonGroup
            value={sourceFilter}
            exclusive
            onChange={(_, val) => {
              setSourceFilter(val ?? '');
              setPage(0);
            }}
            size="small"
            sx={{
              mb: 2,
              '& .MuiToggleButton-root.Mui-selected': {
                bgcolor: 'primary.main',
                color: 'primary.contrastText',
                '&:hover': {bgcolor: 'primary.dark'},
              },
            }}
        >
          <ToggleButton value="">Beide</ToggleButton>
          <ToggleButton value="Mensch">Mensch</ToggleButton>
          <ToggleButton value="Tier">Tier</ToggleButton>
        </ToggleButtonGroup>

        <Alert severity="info" sx={{mb: 2}}>
          Die Suche durchsucht den Namen, den Text und die Tags der Erfahrungsberichte. Sind Video-
          oder Audiodateien angehängt, werden auch deren automatisch erzeugte Untertitel in die
          Suche einbezogen. Über die Auswahl oben kannst Du die Ergebnisse zusätzlich auf
          Erfahrungsberichte von Menschen oder Tieren eingrenzen.
          Suchergebnisse lassen sich außerdem als Bericht exportieren, um sie einfach weiterzugeben.
          Die <strong>globale Suche</strong> durchsucht darüber hinaus auch Dateien, beispielsweise
          Videos und Dokumente in Ordnern.
        </Alert>

        <TextField
            size="small"
            placeholder="Volltextsuche (z. B. 'haut' findet 'Hauterkrankung' und 'Bindehaut')..."
            value={q}
            onChange={e => {
              setQ(e.target.value);
              setPage(0);
            }}
            fullWidth
            sx={{mb: 2}}
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

        {isLoading && <CircularProgress size={24}/>}

        {(data?.page.totalPages ?? 0) > 1 && (
            <Pagination
                count={data?.page.totalPages ?? 0}
                page={page + 1}
                onChange={(_, p) => setPage(p - 1)}
                sx={{mb: 2}}
            />
        )}

        {data?.content.map(t => (
            <TestimonialCard
                key={t.id}
                testimonial={t}
                currentUserId={currentUser?.id ?? 0}
                isAdmin={isAdmin}
                canWrite={canWrite}
                canDelete={canDelete}
                onEdit={openEdit}
                onDelete={id => deleteMut.mutate(id)}
            />
        ))}

        {(data?.page.totalPages ?? 0) > 1 && (
            <Pagination
                count={data?.page.totalPages ?? 0}
                page={page + 1}
                onChange={(_, p) => setPage(p - 1)}
                sx={{mt: 2}}
            />
        )}

        {data?.content.length === 0 && !isLoading && (
            <Typography color="text.secondary" variant="body2">
              {q ? `Keine Ergebnisse für „${q}"` : 'Noch keine Erfahrungsberichte vorhanden.'}
            </Typography>
        )}

        <ReportPreviewDialog
            open={reportOpen}
            q={q}
            onClose={() => setReportOpen(false)}
        />

        {bulkDialogOpen && (
            <BulkAddToCollectionDialog
                open
                resourceIds={[]}
                testimonialIds={data?.content.map(t => t.id) ?? []}
                onClose={() => setBulkDialogOpen(false)}
                defaultName={deferredQ ? `Erfahrungsberichte zu ${deferredQ}` : undefined}
            />
        )}

        <TestimonialForm
            open={formOpen}
            initial={editTarget}
            onSave={handleSave}
            onClose={() => setFormOpen(false)}
        />
      </Box>
  );
}
