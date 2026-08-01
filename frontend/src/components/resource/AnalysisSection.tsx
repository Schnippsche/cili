import {
  Alert,
  Box,
  Button,
  CircularProgress,
  ToggleButton,
  ToggleButtonGroup,
  Tooltip,
  Typography
} from '@mui/material';
import AutoAwesomeIcon from '@mui/icons-material/AutoAwesome';
import RefreshIcon from '@mui/icons-material/Refresh';
import CheckCircleOutlineIcon from '@mui/icons-material/CheckCircleOutline';
import {useEffect, useRef, useState} from 'react';
import Markdown from 'react-markdown';
import {useQueryClient} from '@tanstack/react-query';
import {
  useActiveAnalysisJobs,
  useAiSummaries,
  useAnalyzeResource,
  useSubtitleTracks
} from '../../hooks/useResources';

interface Props {
  resourceId: number;
}

const langNames = new Intl.DisplayNames(['de'], {type: 'language'});

function languageLabel(code: string): string {
  try {
    return langNames.of(code) ?? code;
  } catch {
    return code;
  }
}

export default function AnalysisSection({resourceId}: Readonly<Props>) {
  const qc = useQueryClient();
  const {data: tracks = [], isLoading: tracksLoading} = useSubtitleTracks(resourceId);
  const {data: summaries = [], isLoading: summariesLoading} = useAiSummaries(resourceId);
  const analyze = useAnalyzeResource();
  const {data: activeJobs = []} = useActiveAnalysisJobs(resourceId);
  const [selectedLang, setSelectedLang] = useState<string | null>(null);

  // Summaries neu laden wenn Jobs von laufend → fertig wechseln
  const prevRunning = useRef(false);
  useEffect(() => {
    const isRunningNow = activeJobs.some(j => j.status === 'PENDING' || j.status === 'RUNNING');
    if (prevRunning.current && !isRunningNow) {
      void qc.invalidateQueries({queryKey: ['aiSummaries', resourceId]});
    }
    prevRunning.current = isRunningNow;
  }, [activeJobs, qc, resourceId]);

  useEffect(() => {
    analyze.reset();
  }, [resourceId]); // eslint-disable-line react-hooks/exhaustive-deps

  const analyzableTracks = tracks.filter(t => t.hasTextContent);

  useEffect(() => {
    if (selectedLang == null && analyzableTracks.length > 0) {
      const pref = analyzableTracks.find(t => t.languageCode === 'de') ?? analyzableTracks[0];
      setSelectedLang(pref.languageCode);
    }
  }, [analyzableTracks, selectedLang]);

  if (tracksLoading || summariesLoading) return null;
  if (analyzableTracks.length === 0) return null;

  const jobForLang = (lang: string | null) => activeJobs.find(j => {
    try {
      return JSON.parse(j.result ?? '{}').languageCode === lang;
    } catch {
      return false;
    }
  });

  const activeJobForLang = jobForLang(selectedLang);
  const isRunning = activeJobForLang?.status === 'PENDING' || activeJobForLang?.status === 'RUNNING';
  const isFailed = activeJobForLang?.status === 'FAILED';

  const cachedSummary = summaries.find(s => s.languageCode === selectedLang);

  const handleGenerate = (force: boolean) => {
    if (!selectedLang) return;
    analyze.mutate(
        {id: resourceId, languageCode: selectedLang, force},
        {onSuccess: () => void qc.invalidateQueries({queryKey: ['analysisJobs', resourceId]})}
    );
  };

  const statusLabel = activeJobForLang?.status === 'PENDING' ? 'In Warteschlange…' : 'Analysiere…';

  return (
      <Box sx={{display: 'flex', flexDirection: 'column', gap: 1.5}}>
        {analyzableTracks.length > 1 && (
            <ToggleButtonGroup
                value={selectedLang}
                exclusive
                onChange={(_, v) => {
                  if (v) setSelectedLang(v);
                }}
                size="small"
            >
              {analyzableTracks.map(t => {
                const hasSummary = summaries.some(s => s.languageCode === t.languageCode);
                const jobRunning = (() => {
                  const j = jobForLang(t.languageCode);
                  return j?.status === 'PENDING' || j?.status === 'RUNNING';
                })();
                return (
                    <Tooltip key={t.languageCode} title={languageLabel(t.languageCode)} arrow>
                      <ToggleButton value={t.languageCode} sx={{gap: 0.5}}>
                        {t.languageCode.toUpperCase()}
                        {jobRunning
                            ? <CircularProgress size={12}/>
                            : hasSummary &&
                            <CheckCircleOutlineIcon sx={{fontSize: 14, color: 'success.main'}}/>}
                      </ToggleButton>
                    </Tooltip>
                );
              })}
            </ToggleButtonGroup>
        )}

        {isFailed && <Alert severity="error" sx={{py: 0}}>Analyse fehlgeschlagen.</Alert>}

        {cachedSummary ? (
            <>
              <Typography variant="body2" component="div">
                <Markdown>{cachedSummary.summary}</Markdown>
              </Typography>
              <Box>
                <Button
                    size="small"
                    startIcon={isRunning ? <CircularProgress size={14}/> : <RefreshIcon/>}
                    disabled={isRunning}
                    onClick={() => handleGenerate(true)}
                >
                  {isRunning ? statusLabel : 'Neu erstellen'}
                </Button>
              </Box>
            </>
        ) : (
            <Box sx={{display: 'flex', gap: 1, flexWrap: 'wrap'}}>
              <Button
                  variant="outlined"
                  size="small"
                  startIcon={isRunning ? <CircularProgress size={14}/> : <AutoAwesomeIcon/>}
                  disabled={isRunning}
                  onClick={() => handleGenerate(false)}
              >
                {isRunning ? statusLabel : 'Zusammenfassung erstellen'}
              </Button>
            </Box>
        )}
      </Box>
  );
}
