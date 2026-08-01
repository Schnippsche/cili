import {type ReactNode, useEffect, useMemo, useRef, useState} from 'react';
import {Box, IconButton, InputBase, MenuItem, Select, Tooltip, Typography} from '@mui/material';
import SearchIcon from '@mui/icons-material/Search';
import type {SubtitleTrackDto} from '../../types/api';
import {getSubtitleText} from '../../api/resources';
import {useLanguageOptions} from '../../hooks/useLanguageOptions';

export interface Cue {
  startTime: number;
  endTime: number;
  text: string;
}


/**
 * Resolve the cue a search snippet refers to from its timestamp.
 * The backend sends `floor(cue.startTime)` as an integer second
 * (SearchService.toSeconds), so a cue starting at 1076.02s arrives as 1076.
 */
export function findCueIndexForTimestamp(cues: Cue[], timestampSeconds: number): number {
  if (!cues.length) return -1;
  // Reconstruct the cue whose start was truncated to this second. Without this,
  // a timestamp of 1076 (from a cue starting at 1076.02) would match the
  // *previous* cue, whose endTime (1076.02) is still > 1076.
  const byStart = cues.findIndex(c => Math.floor(c.startTime) === timestampSeconds);
  if (byStart >= 0) return byStart;
  // Fallback: the cue actually containing the timestamp, else the last cue.
  const containing = cues.findIndex(c => c.endTime > timestampSeconds);
  return containing >= 0 ? containing : cues.length - 1;
}

function parseSubtitle(content: string): Cue[] {
  const cues: Cue[] = [];
  const lines = content.split(/\r?\n/);
  let i = 0;
  while (i < lines.length) {
    const m = lines[i].match(/^(\d{1,2}:\d{2}:\d{2}[.,]\d{3})\s*-->\s*(\d{1,2}:\d{2}:\d{2}[.,]\d{3})/);
    if (m) {
      const startTime = parseTime(m[1]);
      const endTime = parseTime(m[2]);
      const text: string[] = [];
      i++;
      while (i < lines.length && lines[i].trim() !== '') {
        text.push(lines[i].trim());
        i++;
      }
      const joined = text.join(' ').trim();
      if (joined) cues.push({startTime, endTime, text: joined});
    }
    i++;
  }
  return cues;
}

function parseTime(s: string): number {
  const [h, min, sec] = s.replace(',', '.').split(':');
  return Number.parseInt(h) * 3600 + Number.parseInt(min) * 60 + Number.parseFloat(sec);
}

function formatTime(t: number): string {
  const h = Math.floor(t / 3600);
  const m = Math.floor((t % 3600) / 60);
  const s = Math.floor(t % 60);
  return h > 0
      ? `${h}:${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}`
      : `${m}:${String(s).padStart(2, '0')}`;
}

function highlight(text: string, query: string): ReactNode {
  if (!query) return text;
  const idx = text.toLowerCase().indexOf(query.toLowerCase());
  if (idx < 0) return text;
  return (
      <>
        {text.slice(0, idx)}
        <Box component="mark" sx={{
          bgcolor: 'warning.main',
          color: 'warning.contrastText',
          borderRadius: '2px',
          px: '1px'
        }}>
          {text.slice(idx, idx + query.length)}
        </Box>
        {text.slice(idx + query.length)}
      </>
  );
}

interface Props {
  tracks: SubtitleTrackDto[];
  getCurrentTime: () => number;
  onSeek: (t: number) => void;
  searchQuery?: string;
  initialLanguage?: string;
  initialTime?: number;
  // Override how cue text is fetched (e.g. token-based loader on the public share page).
  // Defaults to the authenticated resource endpoint.
  loadText?: (track: SubtitleTrackDto) => Promise<string>;
}

export default function TranscriptPanel({
                                          tracks,
                                          getCurrentTime,
                                          onSeek,
                                          searchQuery,
                                          initialLanguage,
                                          initialTime,
                                          loadText
                                        }: Readonly<Props>) {
  const {data: languageOptions = []} = useLanguageOptions();
  const langLabel = (code: string) =>
      languageOptions.find(l => l.code.toLowerCase() === code.toLowerCase())?.label ?? code.toUpperCase();

  const initialTrack = initialLanguage
      ? (tracks.find(t => t.languageCode === initialLanguage) ?? tracks[0])
      : tracks[0];
  const [selectedTrackId, setSelectedTrackId] = useState<number>(initialTrack?.id ?? 0);
  const isOff = selectedTrackId === -1;

  const [currentTime, setCurrentTime] = useState(0);

  useEffect(() => {
    if (isOff) return;
    let frame: number;
    let last = -1;
    const tick = () => {
      const t = Math.floor(getCurrentTime() * 10) / 10;
      if (t !== last) {
        last = t;
        setCurrentTime(t);
      }
      frame = requestAnimationFrame(tick);
    };
    frame = requestAnimationFrame(tick);
    return () => cancelAnimationFrame(frame);
  }, [getCurrentTime, isOff]);

  // Re-enable subtitles when navigating to a specific cue via search result
  useEffect(() => {
    if ((initialTime != null || initialLanguage != null) && isOff) {
      const target = initialLanguage
          ? (tracks.find(t => t.languageCode === initialLanguage) ?? tracks[0])
          : tracks[0];
      if (target) setSelectedTrackId(target.id);
    }
  }, [initialTime, initialLanguage]);

  const [rawText, setRawText] = useState<string | null>(null);
  const [localQuery, setLocalQuery] = useState('');
  const [localMatchIdx, setLocalMatchIdx] = useState(-1);
  const [highlightedCueIdx, setHighlightedCueIdx] = useState<number | null>(null);
  // Cue the user clicked: held active until the player actually reaches it
  // (the player's reported time lags/rounds below the clicked cue's start).
  const [pinnedCueIdx, setPinnedCueIdx] = useState<number | null>(null);
  const listRef = useRef<HTMLDivElement>(null);
  const didAutoSeek = useRef(false);

  const selectedTrack = tracks.find(t => t.id === selectedTrackId) ?? tracks[0];

  useEffect(() => {
    if (!selectedTrack) return;
    setRawText(null);
    setPinnedCueIdx(null);
    didAutoSeek.current = false;
    const load = loadText
        ? loadText(selectedTrack)
        : getSubtitleText(selectedTrack.resourceId, selectedTrack.id);
    load
    .then(setRawText)
    .catch(() => setRawText(''));
  }, [selectedTrack?.id]);

  const cues = useMemo(() => rawText ? parseSubtitle(rawText) : [], [rawText]);

  const playingCueIdx = useMemo(
      () => cues.findIndex(c => currentTime >= c.startTime && currentTime < c.endTime),
      [cues, currentTime]
  );

  // Release the pin once playback has actually advanced into the clicked cue.
  useEffect(() => {
    if (pinnedCueIdx !== null && playingCueIdx === pinnedCueIdx) {
      setPinnedCueIdx(null);
    }
  }, [playingCueIdx, pinnedCueIdx]);

  const activeCueIdx = pinnedCueIdx ?? playingCueIdx;

  // Auto-seek + scroll when transcript loads
  useEffect(() => {
    if (!cues.length || didAutoSeek.current) return;
    let targetIdx = -1;
    if (initialTime != null) {
      targetIdx = findCueIndexForTimestamp(cues, initialTime);
      if (targetIdx < 0) return;
      didAutoSeek.current = true;
      onSeek(cues[targetIdx].startTime);
    } else if (searchQuery) {
      targetIdx = cues.findIndex(c => c.text.toLowerCase().includes(searchQuery.toLowerCase()));
      if (targetIdx < 0) return;
      didAutoSeek.current = true;
      onSeek(cues[targetIdx].startTime);
    }
    if (targetIdx >= 0) {
      setPinnedCueIdx(targetIdx);
      const el = listRef.current?.querySelector<HTMLElement>(`[data-cue-idx="${targetIdx}"]`);
      el?.scrollIntoView({block: 'center', behavior: 'smooth'});
    }
  }, [cues, searchQuery, initialTime]);

  // Auto-scroll active cue into view during playback
  useEffect(() => {
    if (activeCueIdx < 0 || !listRef.current) return;
    const el = listRef.current.querySelector<HTMLElement>(`[data-cue-idx="${activeCueIdx}"]`);
    el?.scrollIntoView({block: 'nearest', behavior: 'smooth'});
  }, [activeCueIdx]);


  const localMatchIndices = useMemo(() => {
    if (!localQuery.trim()) return [];
    const q = localQuery.toLowerCase();
    return cues.reduce<number[]>((acc, c, i) => {
      if (c.text.toLowerCase().includes(q)) acc.push(i);
      return acc;
    }, []);
  }, [cues, localQuery]);

  function scrollToCue(cueIdx: number) {
    const el = listRef.current?.querySelector<HTMLElement>(`[data-cue-idx="${cueIdx}"]`);
    el?.scrollIntoView({block: 'center', behavior: 'smooth'});
  }

  function handleSearch() {
    if (!localMatchIndices.length) return;
    const next = localMatchIdx >= localMatchIndices.length - 1 ? 0 : localMatchIdx + 1;
    setLocalMatchIdx(next);
    const cueIdx = localMatchIndices[next];
    setHighlightedCueIdx(cueIdx);
    onSeek(cues[cueIdx].startTime);
    scrollToCue(cueIdx);
  }

  if (!tracks.length) return null;

  return (
      <Box sx={{
        mt: 1.5,
        border: '1px solid',
        borderColor: 'divider',
        borderRadius: 1,
        overflow: 'hidden'
      }}>
        <Box sx={{
          display: 'flex',
          alignItems: 'center',
          gap: 1,
          px: 1.5,
          py: 0.75,
          bgcolor: 'action.hover',
          borderBottom: '1px solid',
          borderColor: 'divider'
        }}>
          <Typography variant="caption" color="text.secondary">Untertitel</Typography>
          <Box
              component="form"
              onSubmit={e => {
                e.preventDefault();
                handleSearch();
              }}
              sx={{
                display: 'flex',
                alignItems: 'center',
                flexGrow: 1,
                mx: 0.5,
                bgcolor: 'background.paper',
                border: '1px solid',
                borderColor: 'divider',
                borderRadius: 1,
                px: 0.5,
                height: 24,
                opacity: isOff ? 0.4 : 1
              }}
          >
            <InputBase
                value={localQuery}
                onChange={e => {
                  setLocalQuery(e.target.value);
                  setLocalMatchIdx(-1);
                  setHighlightedCueIdx(null);
                }}
                placeholder="Suchen…"
                disabled={isOff}
                inputProps={{'aria-label': 'Untertitel durchsuchen'}}
                sx={{flex: 1, fontSize: '0.72rem', px: 0.5}}
            />
            <Tooltip title="Suchen">
            <span>
              <IconButton type="submit" size="small" sx={{p: 0.25}}
                          disabled={isOff || !localQuery.trim()}>
                <SearchIcon sx={{fontSize: '0.95rem'}}/>
              </IconButton>
            </span>
            </Tooltip>
          </Box>
          <Select
              size="small"
              value={selectedTrackId}
              onChange={e => setSelectedTrackId(Number(e.target.value))}
              sx={{fontSize: '0.75rem', height: 28}}
          >
            {tracks.map(t => (
                <MenuItem key={t.id} value={t.id}>{langLabel(t.languageCode)}</MenuItem>
            ))}
            <MenuItem value={-1}>Untertitel aus</MenuItem>
          </Select>
        </Box>
        {!isOff && <Box ref={listRef} sx={{maxHeight: 220, overflowY: 'auto', p: 0.5}}>
          {rawText === null && (
              <Typography variant="caption" color="text.secondary"
                          sx={{p: 1, display: 'block'}}>Lade…</Typography>
          )}
          {cues.map((cue, idx) => {
            const isActive = idx === activeCueIdx;
            const isLocalCurrentMatch = idx === highlightedCueIdx;
            const isPropMatch = !localQuery.trim() && searchQuery
                ? cue.text.toLowerCase().includes(searchQuery.toLowerCase())
                : false;
            let bgColor: string = 'transparent';
            if (isActive) bgColor = 'primary.main';
            else if (isLocalCurrentMatch) bgColor = 'warning.main';
            else if (isPropMatch) bgColor = 'warning.light';

            let hoverBgColor: string = 'action.hover';
            if (isActive) hoverBgColor = 'primary.dark';
            else if (isLocalCurrentMatch) hoverBgColor = 'warning.dark';
            const showHighlight = (isLocalCurrentMatch || isPropMatch) && !isActive;
            const activeQuery = isLocalCurrentMatch ? localQuery : (searchQuery ?? '');
            return (
                <Box
                    key={idx}
                    data-cue-idx={idx}
                    onClick={() => {
                      setPinnedCueIdx(idx);
                      onSeek(cue.startTime);
                    }}
                    sx={{
                      display: 'flex', gap: 1, alignItems: 'baseline',
                      px: 1, py: 0.4, borderRadius: 0.5, cursor: 'pointer',
                      bgcolor: bgColor,
                      color: isActive ? 'primary.contrastText' : 'text.primary',
                      '&:hover': {bgcolor: hoverBgColor},
                    }}
                >
                  <Typography variant="caption" sx={{
                    flexShrink: 0,
                    opacity: 0.7,
                    fontVariantNumeric: 'tabular-nums',
                    minWidth: 36
                  }}>
                    {formatTime(cue.startTime)}
                  </Typography>
                  <Typography variant="body2" sx={{fontSize: '0.8rem'}}>
                    {showHighlight ? highlight(cue.text, activeQuery) : cue.text}
                  </Typography>
                </Box>
            );
          })}
          {rawText !== null && cues.length === 0 && (
              <Typography variant="caption" color="text.secondary" sx={{p: 1, display: 'block'}}>Kein
                Transkript verfügbar.</Typography>
          )}
        </Box>}
      </Box>
  );
}
