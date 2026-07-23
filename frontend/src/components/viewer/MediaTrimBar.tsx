import { useEffect, useRef, useState } from 'react';
import { Box, Button, CircularProgress, Stack, TextField } from '@mui/material';
import PlayCircleOutlineIcon from '@mui/icons-material/PlayCircleOutline';
import ContentCutIcon from '@mui/icons-material/ContentCut';
import type Player from 'video.js/dist/types/player';

interface Props {
  player: Player | null;
  creating: boolean;
  defaultTitle: string;
  onCreateClip: (startMs: number, endMs: number, title: string) => void;
}

function formatTimecode(ms: number, useHours: boolean): string {
  const totalSeconds = Math.floor(ms / 1000);
  const h = Math.floor(totalSeconds / 3600);
  const m = Math.floor((totalSeconds % 3600) / 60);
  const s = totalSeconds % 60;
  if (useHours) {
    return `${h}:${m.toString().padStart(2, '0')}:${s.toString().padStart(2, '0')}`;
  }
  return `${m}:${s.toString().padStart(2, '0')}`;
}

// Parst Freitext-Zeitangaben (1, 2 oder 3 mit ":" getrennte Teile: s / m:s / h:m:s).
// Gibt Millisekunden zurück, ungeklemmt — Klemmen auf [0, durationMs] passiert beim Aufrufer.
function parseTimecodeRaw(text: string): number | null {
  const parts = text.split(':');
  if (parts.length < 1 || parts.length > 3) return null;
  if (!parts.every((p) => /^\d+$/.test(p))) return null;
  const nums = parts.map((p) => parseInt(p, 10));
  if (nums.length === 1) {
    return nums[0] * 1000;
  }
  if (nums.length === 2) {
    const [m, s] = nums;
    if (s >= 60) return null;
    return (m * 60 + s) * 1000;
  }
  const [h, m, s] = nums;
  if (m >= 60 || s >= 60) return null;
  return (h * 3600 + m * 60 + s) * 1000;
}

export default function MediaTrimBar({ player, creating, defaultTitle, onCreateClip }: Readonly<Props>) {
  const [durationMs, setDurationMs] = useState(0);
  const [startMs, setStartMs] = useState(0);
  const [endMs, setEndMs] = useState(0);
  const [startText, setStartText] = useState('');
  const [endText, setEndText] = useState('');
  const endMsRef = useRef(endMs);
  endMsRef.current = endMs;
  const isPreviewingRef = useRef(false);

  const useHours = durationMs >= 3_600_000;

  // Annahme: VideoPlayer disposed/erstellt player pro Video neu (siehe VideoPlayer.tsx useEffect[src,subtitles]) —
  // player-Identität ändert sich bei jedem neuen Video, .one() registriert sich daher korrekt neu.
  useEffect(() => {
    if (!player) return;
    const initRange = () => {
      const d = (player.duration() ?? 0) * 1000;
      if (d > 0) {
        const uh = d >= 3_600_000;
        setDurationMs(d);
        setStartMs(0);
        setEndMs(d);
        setStartText(formatTimecode(0, uh));
        setEndText(formatTimecode(d, uh));
      }
    };
    if (player.duration()) initRange();
    player.one('loadedmetadata', initRange);
    return () => { player.off('loadedmetadata', initRange); };
  }, [player]);

  useEffect(() => {
    if (!player) return;
    const onTimeUpdate = () => {
      if (isPreviewingRef.current && (player.currentTime() ?? 0) * 1000 >= endMsRef.current) {
        player.pause();
        isPreviewingRef.current = false;
      }
    };
    player.on('timeupdate', onTimeUpdate);
    return () => { player.off('timeupdate', onTimeUpdate); };
  }, [player]);

  if (!player || durationMs === 0) return null;

  const startValid = parseTimecodeRaw(startText) !== null;
  const endValid = parseTimecodeRaw(endText) !== null;

  const handlePreview = () => {
    player.currentTime(startMs / 1000);
    isPreviewingRef.current = true;
    void player.play();
  };

  const handleStartChange = (text: string) => {
    setStartText(text);
    const raw = parseTimecodeRaw(text);
    if (raw === null) return;
    const clamped = Math.min(Math.max(raw, 0), durationMs);
    setStartMs(clamped);
    if (clamped !== raw) setStartText(formatTimecode(clamped, useHours));
  };

  const handleEndChange = (text: string) => {
    setEndText(text);
    const raw = parseTimecodeRaw(text);
    if (raw === null) return;
    const clamped = Math.min(Math.max(raw, 0), durationMs);
    setEndMs(clamped);
    if (clamped !== raw) setEndText(formatTimecode(clamped, useHours));
  };

  const canAct = !creating && startValid && endValid;

  return (
    <Box sx={{ mt: 1, px: 1 }}>
      <Stack direction="row" spacing={2} alignItems="center">
        <TextField
          label="Start"
          size="small"
          value={startText}
          onChange={(e) => handleStartChange(e.target.value)}
          sx={{ width: 96 }}
        />
        <TextField
          label="Ende"
          size="small"
          value={endText}
          onChange={(e) => handleEndChange(e.target.value)}
          sx={{ width: 96 }}
        />
        <Button size="small" disabled={!canAct} startIcon={<PlayCircleOutlineIcon />} onClick={handlePreview}>
          Vorschau
        </Button>
        <Button
          size="small"
          variant="contained"
          disabled={!canAct || endMs <= startMs}
          startIcon={creating ? <CircularProgress size={14} /> : <ContentCutIcon />}
          onClick={() => onCreateClip(
            startMs,
            endMs,
            `${defaultTitle} Ausschnitt ${formatTimecode(startMs, useHours)} - ${formatTimecode(endMs, useHours)}`,
          )}
        >
          {creating ? 'Clip wird erstellt…' : 'Clip erstellen'}
        </Button>
      </Stack>
    </Box>
  );
}
