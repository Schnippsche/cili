import {
  Alert, Box, Button, Chip, FormControl, IconButton, InputLabel, MenuItem,
  Select, Snackbar, Table, TableBody, TableCell, TableContainer, TableHead,
  TableRow, Tooltip, Typography, Paper, CircularProgress,
} from '@mui/material';
import DeleteOutlineIcon from '@mui/icons-material/DeleteOutline';
import RefreshIcon from '@mui/icons-material/Refresh';
import TelegramIcon from '@mui/icons-material/Send';
import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import * as adminApi from '../../api/admin';
import type { JobStatus, ProcessingJobDto } from '../../types/api';

// ── Status-Chip ──────────────────────────────────────────────────────────────

const STATUS_COLOR: Record<JobStatus, 'default' | 'primary' | 'success' | 'error' | 'warning'> = {
  PENDING:   'default',
  RUNNING:   'primary',
  DONE:      'success',
  FAILED:    'error',
  CANCELLED: 'warning',
};

const STATUS_LABEL: Record<JobStatus, string> = {
  PENDING:   'Wartend',
  RUNNING:   'Läuft',
  DONE:      'Fertig',
  FAILED:    'Fehler',
  CANCELLED: 'Abgebrochen',
};

const TYPE_LABEL: Record<string, string> = {
  THUMBNAIL:          'Thumbnail',
  TRANSCODE:          'Transkodierung',
  VIDEO_TRANSCODE:    'Video-Konvertierung',
  WAV_EXTRACT:        'WAV-Extraktion',
  WHISPER_TRANSCRIBE: 'Transkription',
  OCR:                'OCR',
  PREVIEW:            'Vorschau',
  VIDEO_ANALYSIS:     'Video-Analyse',
  AUDIO_NORMALIZE:    'Audio-Normalisierung',
  SUBTITLE_TRANSLATE: 'Untertitel-Übersetzung',
  DOCUMENT_TRANSLATE: 'Dokument-Übersetzung',
  OLLAMA_ANALYSIS:    'Ollama Analyse',
  TELEGRAM_IMPORT:    'Telegram Import',
  VIDEO_URL_IMPORT:   'Video-URL Import',
};

function fmt(iso: string | null): string {
  if (!iso) return '—';
  return new Date(iso).toLocaleString('de-DE', { dateStyle: 'short', timeStyle: 'medium' });
}

// ── Hauptkomponente ───────────────────────────────────────────────────────────

export default function JobTable() {
  const qc = useQueryClient();
  const [statusFilter, setStatusFilter] = useState<JobStatus | ''>('');
  const [page, setPage] = useState(0);
  const size = 50;
  const [snack, setSnack] = useState<{ open: boolean; msg: string; severity: 'success' | 'error' }>({
    open: false, msg: '', severity: 'success',
  });

  const { data, isLoading, isFetching } = useQuery({
    queryKey: ['admin-jobs', page, statusFilter],
    queryFn:  () => adminApi.listJobs(page, size, statusFilter || undefined),
    refetchInterval: 5000,
  });

  const { data: sources } = useQuery({
    queryKey: ['telegram-sources'],
    queryFn:  adminApi.listTelegramSources,
    staleTime: 5 * 60 * 1000,
  });

  const deleteOne = useMutation({
    mutationFn: (id: number) => adminApi.deleteJob(id),
    onSuccess:  () => void qc.invalidateQueries({ queryKey: ['admin-jobs'] }),
  });

  const deleteCompleted = useMutation({
    mutationFn: adminApi.deleteCompletedJobs,
    onSuccess:  () => { void qc.invalidateQueries({ queryKey: ['admin-jobs'] }); setPage(0); },
  });

  const triggerTelegram = useMutation({
    mutationFn: (source: string) => adminApi.triggerTelegramImport(source),
    onSuccess: (_data, source) => {
      void qc.invalidateQueries({ queryKey: ['admin-jobs'] });
      const label = sources?.find(s => s.name === source)?.label ?? source;
      setSnack({ open: true, msg: `Telegram-Import (${label}) gestartet`, severity: 'success' });
    },
    onError: (err: unknown) => {
      const msg = (err as { response?: { data?: { message?: string } } })
        ?.response?.data?.message ?? 'Import konnte nicht gestartet werden';
      setSnack({ open: true, msg, severity: 'error' });
    },
  });

  const jobs: ProcessingJobDto[] = data?.content ?? [];
  const total = data?.totalElements ?? 0;
  const totalPages = data?.totalPages ?? 1;

  return (
    <Box>
      {/* Toolbar */}
      <Box sx={{ display: 'flex', gap: 2, mb: 2, alignItems: 'center', flexWrap: 'wrap' }}>
        <FormControl size="small" sx={{ minWidth: 160 }}>
          <InputLabel>Status-Filter</InputLabel>
          <Select
            value={statusFilter}
            label="Status-Filter"
            onChange={e => { setStatusFilter(e.target.value as JobStatus | ''); setPage(0); }}
          >
            <MenuItem value="">Alle</MenuItem>
            {(Object.keys(STATUS_LABEL) as JobStatus[]).map(s => (
              <MenuItem key={s} value={s}>{STATUS_LABEL[s]}</MenuItem>
            ))}
          </Select>
        </FormControl>

        <Typography variant="body2" color="text.secondary" sx={{ flexGrow: 1 }}>
          {total} Jobs gesamt
          {isFetching && !isLoading && (
            <CircularProgress size={12} sx={{ ml: 1, verticalAlign: 'middle' }} />
          )}
        </Typography>

        <Tooltip title="Aktualisieren">
          <IconButton size="small" onClick={() => void qc.invalidateQueries({ queryKey: ['admin-jobs'] })}>
            <RefreshIcon fontSize="small" />
          </IconButton>
        </Tooltip>

        {(sources ?? []).map(source => (
          <Button
            key={source.name}
            variant="outlined"
            size="small"
            startIcon={<TelegramIcon fontSize="small" />}
            disabled={triggerTelegram.isPending}
            onClick={() => triggerTelegram.mutate(source.name)}
          >
            {source.label}
          </Button>
        ))}

        <Button
          variant="outlined"
          color="error"
          size="small"
          disabled={deleteCompleted.isPending}
          onClick={() => deleteCompleted.mutate()}
        >
          Abgeschlossene löschen
        </Button>
      </Box>

      <Snackbar
        open={snack.open}
        autoHideDuration={4000}
        onClose={() => setSnack(s => ({ ...s, open: false }))}
        anchorOrigin={{ vertical: 'bottom', horizontal: 'center' }}
      >
        <Alert severity={snack.severity} onClose={() => setSnack(s => ({ ...s, open: false }))}>
          {snack.msg}
        </Alert>
      </Snackbar>

      {/* Tabelle */}
      <TableContainer component={Paper} variant="outlined" sx={{ overflowX: 'auto' }}>
        <Table size="small">
          <TableHead>
            <TableRow sx={{ '& th': { fontWeight: 600 } }}>
              <TableCell>ID</TableCell>
              <TableCell>Typ</TableCell>
              <TableCell>Quelle</TableCell>
              <TableCell>Status</TableCell>
              <TableCell>Resource</TableCell>
              <TableCell>Versuche</TableCell>
              <TableCell>Erstellt</TableCell>
              <TableCell>Gestartet</TableCell>
              <TableCell>Fertig</TableCell>
              <TableCell>Fehler</TableCell>
              <TableCell align="center"></TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {isLoading && (
              <TableRow>
                <TableCell colSpan={11} align="center" sx={{ py: 4 }}>
                  <CircularProgress size={24} />
                </TableCell>
              </TableRow>
            )}
            {!isLoading && jobs.length === 0 && (
              <TableRow>
                <TableCell colSpan={11} align="center" sx={{ py: 4, color: 'text.secondary' }}>
                  Keine Jobs gefunden
                </TableCell>
              </TableRow>
            )}
            {jobs.map(job => (
              <TableRow key={job.id} hover>
                <TableCell sx={{ fontFamily: 'monospace', color: 'text.secondary' }}>{job.id}</TableCell>
                <TableCell>{TYPE_LABEL[job.type] ?? job.type}</TableCell>
                <TableCell sx={{ color: 'text.secondary' }}>
                  {sources?.find(s => s.name === job.source)?.label ?? job.source ?? '—'}
                </TableCell>
                <TableCell>
                  <Chip
                    label={STATUS_LABEL[job.status]}
                    color={STATUS_COLOR[job.status]}
                    size="small"
                    variant={job.status === 'RUNNING' ? 'filled' : 'outlined'}
                  />
                </TableCell>
                <TableCell sx={{ fontFamily: 'monospace' }}>{job.resourceId}</TableCell>
                <TableCell>{job.attempts}/{job.maxAttempts}</TableCell>
                <TableCell sx={{ whiteSpace: 'nowrap' }}>{fmt(job.createdAt)}</TableCell>
                <TableCell sx={{ whiteSpace: 'nowrap' }}>{fmt(job.startedAt)}</TableCell>
                <TableCell sx={{ whiteSpace: 'nowrap' }}>{fmt(job.finishedAt)}</TableCell>
                <TableCell sx={{ maxWidth: 200 }}>
                  {job.errorMessage ? (
                    <Tooltip title={job.errorMessage}>
                      <Typography variant="body2" noWrap color="error.main" sx={{ cursor: 'help' }}>
                        {job.errorMessage}
                      </Typography>
                    </Tooltip>
                  ) : '—'}
                </TableCell>
                <TableCell align="center">
                  <Tooltip title="Job löschen">
                    <span>
                      <IconButton
                        size="small"
                        color="error"
                        disabled={deleteOne.isPending}
                        onClick={() => deleteOne.mutate(job.id)}
                      >
                        <DeleteOutlineIcon fontSize="small" />
                      </IconButton>
                    </span>
                  </Tooltip>
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </TableContainer>

      {/* Pagination */}
      {totalPages > 1 && (
        <Box sx={{ display: 'flex', justifyContent: 'center', alignItems: 'center', gap: 2, mt: 2 }}>
          <Button size="small" disabled={page === 0} onClick={() => setPage(p => p - 1)}>Zurück</Button>
          <Typography variant="body2">{page + 1} / {totalPages}</Typography>
          <Button size="small" disabled={page >= totalPages - 1} onClick={() => setPage(p => p + 1)}>Weiter</Button>
        </Box>
      )}
    </Box>
  );
}
