import { useEffect, useRef, useState } from 'react';
import { Box, Button, CircularProgress, Slider, Stack, TextField, Typography } from '@mui/material';
import PlayCircleOutlineIcon from '@mui/icons-material/PlayCircleOutline';
import ContentCutIcon from '@mui/icons-material/ContentCut';
import type Player from 'video.js/dist/types/player';

interface Props {
  player: Player | null;
  creating: boolean;
  defaultTitle: string;
  onCreateClip: (startMs: number, endMs: number, title: string) => void;
}

function formatMs(ms: number): string {
  const totalSeconds = Math.floor(ms / 1000);
  const m = Math.floor(totalSeconds / 60);
  const s = totalSeconds % 60;
  return `${m}:${s.toString().padStart(2, '0')}`;
}

export default function MediaTrimBar({ player, creating, defaultTitle, onCreateClip }: Readonly<Props>) {
  const [durationMs, setDurationMs] = useState(0);
  const [range, setRange] = useState<[number, number]>([0, 0]);
  const [title, setTitle] = useState(defaultTitle);
  const rangeRef = useRef(range);
  rangeRef.current = range;
  const isPreviewingRef = useRef(false);

  // Annahme: VideoPlayer disposed/erstellt player pro Video neu (siehe VideoPlayer.tsx useEffect[src,subtitles]) —
  // player-Identität ändert sich bei jedem neuen Video, .one() registriert sich daher korrekt neu.
  useEffect(() => {
    if (!player) return;
    const initRange = () => {
      const d = (player.duration() ?? 0) * 1000;
      if (d > 0) {
        setDurationMs(d);
        setRange([0, d]);
      }
    };
    if (player.duration()) initRange();
    player.one('loadedmetadata', initRange);
    return () => { player.off('loadedmetadata', initRange); };
  }, [player]);

  useEffect(() => {
    if (!player) return;
    const onTimeUpdate = () => {
      if (isPreviewingRef.current && (player.currentTime() ?? 0) * 1000 >= rangeRef.current[1]) {
        player.pause();
        isPreviewingRef.current = false;
      }
    };
    player.on('timeupdate', onTimeUpdate);
    return () => { player.off('timeupdate', onTimeUpdate); };
  }, [player]);

  if (!player || durationMs === 0) return null;

  const [startMs, endMs] = range;

  const handlePreview = () => {
    player.currentTime(startMs / 1000);
    isPreviewingRef.current = true;
    void player.play();
  };

  const handleRangeChange = (_: Event, value: number | number[], activeThumb: number) => {
    const next = value as [number, number];
    setRange(next);
    // Live-Scrub: Video springt beim Ziehen sofort an die Position des bewegten Handles,
    // damit man den Punkt sieht ohne die ganze Vorschau abspielen zu müssen.
    isPreviewingRef.current = false;
    player.pause();
    player.currentTime(next[activeThumb] / 1000);
  };

  return (
    <Box sx={{ mt: 1, px: 1 }}>
      <TextField
        label="Titel"
        size="small"
        fullWidth
        value={title}
        onChange={(e) => setTitle(e.target.value)}
        sx={{ mb: 1 }}
      />
      <Stack direction="row" spacing={2} alignItems="center">
        <Typography variant="caption" sx={{ minWidth: 40 }}>{formatMs(startMs)}</Typography>
        <Slider
          value={range}
          getAriaLabel={(index) => (index === 0 ? 'Startzeit' : 'Endzeit')}
          getAriaValueText={formatMs}
          min={0}
          max={durationMs}
          step={100}
          onChange={handleRangeChange}
          disableSwap
          size="small"
          sx={{ flexGrow: 1 }}
        />
        <Typography variant="caption" sx={{ minWidth: 40 }}>{formatMs(endMs)}</Typography>
      </Stack>
      <Stack direction="row" spacing={1} justifyContent="flex-end" sx={{ mt: 1 }}>
        <Button size="small" disabled={creating} startIcon={<PlayCircleOutlineIcon />} onClick={handlePreview}>
          Vorschau
        </Button>
        <Button
          size="small"
          variant="contained"
          disabled={creating || endMs <= startMs}
          startIcon={creating ? <CircularProgress size={14} /> : <ContentCutIcon />}
          onClick={() => onCreateClip(startMs, endMs, title)}
        >
          {creating ? 'Clip wird erstellt…' : 'Clip erstellen'}
        </Button>
      </Stack>
    </Box>
  );
}
