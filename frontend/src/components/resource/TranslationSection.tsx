import {useEffect, useRef, useState} from 'react';
import {
  Alert,
  Box,
  Button,
  CircularProgress,
  Dialog,
  DialogActions,
  DialogContent,
  DialogContentText,
  DialogTitle,
  Divider,
  FormControl,
  InputLabel,
  MenuItem,
  Select,
  Typography,
} from '@mui/material';
import CheckCircleOutlineIcon from '@mui/icons-material/CheckCircleOutline';
import ErrorOutlineIcon from '@mui/icons-material/ErrorOutline';
import {useQuery, useQueryClient} from '@tanstack/react-query';
import {
  getActiveTranslationJobs,
  getSubtitleTracks,
  requestTranslation,
} from '../../api/resources';
import type {ProcessingJobDto} from '../../types/api';
import {useLanguageOptions} from '../../hooks/useLanguageOptions';

interface Props {
  resourceId: number;
  canTranslateSubtitles: boolean;
  readonly?: boolean;
}

export default function TranslationSection({
                                             resourceId,
                                             canTranslateSubtitles,
                                             readonly = false,
                                           }: Readonly<Props>) {
  // ── Hooks (must be called unconditionally — Rules of Hooks) ───────────────
  const qc = useQueryClient();

  // Existing subtitle tracks for the "Von" dropdown
  const {data: tracks = []} = useQuery({
    queryKey: ['subtitles', resourceId],
    queryFn: () => getSubtitleTracks(resourceId),
    enabled: canTranslateSubtitles && !readonly,
  });

  const [sourceLang, setSourceLang] = useState('');
  const [targetLang, setTargetLang] = useState('');

  // Active/recent job (PENDING | RUNNING | DONE | FAILED)
  const [activeJob, setActiveJob] = useState<ProcessingJobDto | null>(null);
  // targetLang for the active job (stored at submit time; unknown for restored jobs)
  const [activeJobLang, setActiveJobLang] = useState('');

  // Overwrite-dialog state
  const [overwriteDialog, setOverwriteDialog] = useState<{
    targetLang: string;
    sourceTrackId: number;
  } | null>(null);

  const [error, setError] = useState<string | null>(null);
  const pollRef = useRef<ReturnType<typeof setInterval> | null>(null);

  // ── On mount: restore any running/recent job ───────────────────────────────
  const {data: initialJobs} = useQuery({
    queryKey: ['translationJobs', resourceId],
    queryFn: () => getActiveTranslationJobs(resourceId),
    staleTime: 0,
    enabled: canTranslateSubtitles && !readonly,
  });

  const {data: languages = [], isLoading: langsLoading} = useLanguageOptions();

  const getLangLabel = (code: string) =>
      languages.find(l => l.code === code)?.label ?? code.toUpperCase();

  useEffect(() => {
    if (!initialJobs?.length) return;
    // Prefer running/pending job; otherwise show most recent result
    const live = initialJobs.find(
        j => j.status === 'PENDING' || j.status === 'RUNNING',
    );
    setActiveJob(live ?? initialJobs[0]);
    setActiveJobLang('');  // targetLang not available from DTO
  }, [initialJobs]);

  // ── Polling: update activeJob status every 3 s ────────────────────────────
  useEffect(() => {
    if (pollRef.current) clearInterval(pollRef.current);
    if (!activeJob) return;
    if (activeJob.status === 'DONE' || activeJob.status === 'FAILED'
        || activeJob.status === 'CANCELLED') {
      if (activeJob.status === 'DONE') {
        void qc.invalidateQueries({queryKey: ['subtitles', resourceId]});
      }
      return;
    }
    pollRef.current = setInterval(async () => {
      try {
        const jobs = await getActiveTranslationJobs(resourceId);
        const updated = jobs.find(j => j.id === activeJob.id);
        if (!updated) return;
        setActiveJob(updated);
        if (updated.status === 'DONE') {
          void qc.invalidateQueries({queryKey: ['subtitles', resourceId]});
        }
      } catch {
        setError('Verbindung unterbrochen.');
        clearInterval(pollRef.current!);
      }
    }, 3000);
    return () => {
      if (pollRef.current) clearInterval(pollRef.current);
    };
  }, [activeJob?.id, activeJob?.status, resourceId, qc]);

  // ── Guard — after all hooks ────────────────────────────────────────────────
  if (!canTranslateSubtitles || readonly) return null;

  // ── Derived state ─────────────────────────────────────────────────────────
  const sourceTrack = tracks.find(t => t.languageCode === sourceLang);
  const isRunning = activeJob?.status === 'PENDING' || activeJob?.status === 'RUNNING';
  const canSubmit = !!sourceTrack && !!targetLang
      && sourceLang !== targetLang && !isRunning;

  // ── Submit translation request ────────────────────────────────────────────
  const submit = async (overwrite = false) => {
    if (!sourceTrack || !targetLang) return;
    setError(null);
    try {
      const {jobId} = await requestTranslation(
          resourceId, sourceTrack.id, targetLang, overwrite,
      );
      // Create a local placeholder job until first poll updates it
      const placeholder: ProcessingJobDto = {
        id: jobId, resourceId, type: 'SUBTITLE_TRANSLATE', source: null, status: 'PENDING',
        attempts: 0, maxAttempts: 3, errorMessage: null, result: null,
        startedAt: null, finishedAt: null,
        createdAt: new Date().toISOString(), updatedAt: new Date().toISOString(),
      };
      setActiveJob(placeholder);
      setActiveJobLang(targetLang);
    } catch (err: any) {
      if (err.response?.status === 409) {
        setOverwriteDialog({targetLang, sourceTrackId: sourceTrack.id});
      } else {
        setError(err.response?.data?.message ?? 'Fehler beim Starten der Übersetzung.');
      }
    }
  };

  // ── Render ────────────────────────────────────────────────────────────────
  const displayLang = activeJobLang || targetLang;

  return (
      <>
        <Divider sx={{my: 1}}/>
        <Typography variant="subtitle2" gutterBottom>Untertitel übersetzen</Typography>

        {tracks.length === 0 ? (
            <Typography variant="body2" color="text.secondary" sx={{mb: 1}}>
              Noch keine Untertitel.
            </Typography>
        ) : (
            <Box sx={{display: 'flex', flexDirection: 'column', gap: 1.5}}>
              {/* Source language — only tracks that already exist */}
              <FormControl size="small" fullWidth>
                <InputLabel>Von</InputLabel>
                <Select
                    value={sourceLang}
                    label="Von"
                    onChange={e => setSourceLang(e.target.value)}
                >
                  {tracks.map(t => (
                      <MenuItem key={t.id} value={t.languageCode}>
                        {getLangLabel(t.languageCode)}
                        {t.label ? ` – ${t.label}` : ''}
                      </MenuItem>
                  ))}
                </Select>
              </FormControl>

              {/* Target language — DB-driven list, translation-supported only; source lang disabled */}
              <FormControl size="small" fullWidth>
                <InputLabel>Nach</InputLabel>
                <Select
                    value={targetLang}
                    label="Nach"
                    onChange={e => setTargetLang(e.target.value)}
                    disabled={langsLoading}
                >
                  {languages
                  .filter(l => l.translationSupported)
                  .map(l => (
                      <MenuItem key={l.code} value={l.code} disabled={l.code === sourceLang}>
                        {l.label}
                      </MenuItem>
                  ))}
                </Select>
              </FormControl>

              <Button
                  variant="outlined"
                  size="small"
                  disabled={!canSubmit}
                  onClick={() => submit(false)}
              >
                Übersetzen
              </Button>

              {error && (
                  <Alert severity="error" onClose={() => setError(null)}>{error}</Alert>
              )}

              {/* Job status indicator */}
              {activeJob && (
                  <Box sx={{display: 'flex', alignItems: 'center', gap: 0.75, mt: 0.5}}>
                    {isRunning && (
                        <>
                          <CircularProgress size={14}/>
                          <Typography variant="caption">
                            Läuft…{displayLang ? ` (→ ${getLangLabel(displayLang)})` : ''}
                          </Typography>
                        </>
                    )}
                    {activeJob.status === 'DONE' && (
                        <>
                          <CheckCircleOutlineIcon color="success" sx={{fontSize: 16}}/>
                          <Typography variant="caption">
                            Abgeschlossen{displayLang ? ` (→ ${getLangLabel(displayLang)})` : ''}
                          </Typography>
                        </>
                    )}
                    {activeJob.status === 'FAILED' && (
                        <>
                          <ErrorOutlineIcon color="error" sx={{fontSize: 16}}/>
                          <Typography variant="caption" color="error.main">
                            Fehlgeschlagen: {activeJob.errorMessage ?? 'Unbekannter Fehler'}
                          </Typography>
                        </>
                    )}
                  </Box>
              )}
            </Box>
        )}

        {/* Overwrite confirmation dialog */}
        <Dialog
            open={!!overwriteDialog}
            onClose={() => setOverwriteDialog(null)}
        >
          <DialogTitle>Track überschreiben?</DialogTitle>
          <DialogContent>
            <DialogContentText>
              Ein Untertitel-Track für „{getLangLabel(overwriteDialog?.targetLang ?? '')}"{' '}
              existiert bereits. Möchten Sie ihn überschreiben?
            </DialogContentText>
          </DialogContent>
          <DialogActions>
            <Button onClick={() => setOverwriteDialog(null)}>Abbrechen</Button>
            <Button
                color="warning"
                onClick={() => {
                  if (!overwriteDialog) return;
                  const tgt = overwriteDialog.targetLang;
                  setOverwriteDialog(null);
                  setTargetLang(tgt);
                  submit(true);
                }}
            >
              Überschreiben
            </Button>
          </DialogActions>
        </Dialog>
      </>
  );
}
