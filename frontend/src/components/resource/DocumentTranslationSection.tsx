import { useState, useEffect, useRef } from 'react';
import {
  Alert, Box, Button, CircularProgress, Divider,
  FormControl, InputLabel, Link, MenuItem, Select, Typography,
} from '@mui/material';
import CheckCircleOutlineIcon from '@mui/icons-material/CheckCircleOutline';
import ErrorOutlineIcon from '@mui/icons-material/ErrorOutline';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import {
  getActiveDocumentTranslationJobs,
  requestDocumentTranslation,
} from '../../api/resources';
import type { ProcessingJobDto } from '../../types/api';
import { useLanguageOptions } from '../../hooks/useLanguageOptions';

interface Props {
  resourceId: number;
  folderId: number;
  canTranslateSubtitles: boolean;
  readonly?: boolean;
}

export default function DocumentTranslationSection({
  resourceId,
  folderId,
  canTranslateSubtitles,
  readonly = false,
}: Readonly<Props>) {
  const qc = useQueryClient();
  const { data: languages = [], isLoading: langsLoading } = useLanguageOptions();
  const translationLangs = languages.filter(l => l.translationSupported);

  const [sourceLang, setSourceLang] = useState('');
  const [targetLang, setTargetLang] = useState('');
  const [activeJob, setActiveJob] = useState<ProcessingJobDto | null>(null);
  const [error, setError] = useState<string | null>(null);
  const pollRef = useRef<ReturnType<typeof setInterval> | null>(null);

  const { data: initialJobs } = useQuery({
    queryKey: ['docTranslationJobs', resourceId],
    queryFn: () => getActiveDocumentTranslationJobs(resourceId),
    staleTime: 0,
    enabled: canTranslateSubtitles && !readonly,
  });

  useEffect(() => {
    if (!initialJobs?.length) return;
    const live = initialJobs.find(j => j.status === 'PENDING' || j.status === 'RUNNING');
    setActiveJob(live ?? initialJobs[0]);
  }, [initialJobs]);

  useEffect(() => {
    if (pollRef.current) clearInterval(pollRef.current);
    if (!activeJob) return;
    if (activeJob.status === 'DONE' || activeJob.status === 'FAILED'
        || activeJob.status === 'CANCELLED') {
      if (activeJob.status === 'DONE') {
        void qc.invalidateQueries({ queryKey: ['resources', folderId] });
      }
      return;
    }
    pollRef.current = setInterval(async () => {
      try {
        const jobs = await getActiveDocumentTranslationJobs(resourceId);
        const updated = jobs.find(j => j.id === activeJob.id);
        if (!updated) return;
        setActiveJob(updated);
        if (updated.status === 'DONE') {
          void qc.invalidateQueries({ queryKey: ['resources', folderId] });
        }
      } catch {
        setError('Verbindung unterbrochen.');
        clearInterval(pollRef.current!);
      }
    }, 3000);
    return () => { if (pollRef.current) clearInterval(pollRef.current); };
  }, [activeJob?.id, activeJob?.status, resourceId, folderId, qc]);

  if (!canTranslateSubtitles || readonly) return null;

  const isRunning = activeJob?.status === 'PENDING' || activeJob?.status === 'RUNNING';
  const canSubmit = !!sourceLang && !!targetLang && sourceLang !== targetLang && !isRunning;

  const getLangLabel = (code: string) =>
    languages.find(l => l.code === code)?.label ?? code.toUpperCase();

  const submit = async () => {
    if (!sourceLang || !targetLang) return;
    setError(null);
    try {
      const { jobId } = await requestDocumentTranslation(resourceId, sourceLang, targetLang);
      const placeholder: ProcessingJobDto = {
        id: jobId, resourceId, type: 'DOCUMENT_TRANSLATE', status: 'PENDING',
        attempts: 0, maxAttempts: 3, errorMessage: null, result: null,
        startedAt: null, finishedAt: null,
        createdAt: new Date().toISOString(), updatedAt: new Date().toISOString(),
      };
      setActiveJob(placeholder);
    } catch (err: any) {
      setError(err.response?.data?.message ?? 'Fehler beim Starten der Übersetzung.');
    }
  };

  return (
    <>
      <Divider sx={{ my: 1 }} />
      <Typography variant="subtitle2" gutterBottom>Dokument übersetzen</Typography>

      <Box sx={{ display: 'flex', flexDirection: 'column', gap: 1.5 }}>
        <FormControl size="small" fullWidth>
          <InputLabel>Quellsprache</InputLabel>
          <Select value={sourceLang} label="Quellsprache"
            onChange={e => setSourceLang(e.target.value)} disabled={langsLoading}>
            {translationLangs.map(l => (
              <MenuItem key={l.code} value={l.code}>{l.label}</MenuItem>
            ))}
          </Select>
        </FormControl>

        <FormControl size="small" fullWidth>
          <InputLabel>Zielsprache</InputLabel>
          <Select value={targetLang} label="Zielsprache"
            onChange={e => setTargetLang(e.target.value)} disabled={langsLoading}>
            {translationLangs.map(l => (
              <MenuItem key={l.code} value={l.code} disabled={l.code === sourceLang}>
                {l.label}
              </MenuItem>
            ))}
          </Select>
        </FormControl>

        <Button variant="outlined" size="small" disabled={!canSubmit} onClick={submit}>
          Übersetzen
        </Button>

        {error && <Alert severity="error" onClose={() => setError(null)}>{error}</Alert>}

        {activeJob && (
          <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.75, mt: 0.5 }}>
            {isRunning && (
              <>
                <CircularProgress size={14} />
                <Typography variant="caption">
                  Läuft… ({getLangLabel(sourceLang)} → {getLangLabel(targetLang)})
                </Typography>
              </>
            )}
            {activeJob.status === 'DONE' && (() => {
              let targetResourceId: number | null = null;
              let tgtLang = '';
              try {
                const r = JSON.parse(activeJob.result ?? '{}');
                targetResourceId = r.targetResourceId ?? null;
                tgtLang = r.targetLang ?? '';
              } catch { /* ignore */ }
              return (
                <>
                  <CheckCircleOutlineIcon color="success" sx={{ fontSize: 16 }} />
                  <Typography variant="caption">
                    Abgeschlossen ({getLangLabel(tgtLang || targetLang)})
                    {targetResourceId && (
                      <> – <Link href={`#resource-${targetResourceId}`} underline="hover">
                        Ergebnis im Ordner
                      </Link></>
                    )}
                  </Typography>
                </>
              );
            })()}
            {activeJob.status === 'FAILED' && (
              <>
                <ErrorOutlineIcon color="error" sx={{ fontSize: 16 }} />
                <Typography variant="caption" color="error.main">
                  Fehlgeschlagen: {activeJob.errorMessage ?? 'Unbekannter Fehler'}
                </Typography>
              </>
            )}
          </Box>
        )}
      </Box>
    </>
  );
}
