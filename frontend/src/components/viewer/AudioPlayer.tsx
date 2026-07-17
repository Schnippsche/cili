import { Box, Typography } from '@mui/material';
import MusicNoteIcon from '@mui/icons-material/MusicNote';

interface Props { src: string; title?: string; }

export default function AudioPlayer({ src, title }: Readonly<Props>) {
  return (
    <Box sx={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 2, py: 3 }}>
      <MusicNoteIcon sx={{ fontSize: 80, color: 'text.disabled' }} />
      {title && <Typography variant="subtitle1" noWrap sx={{ maxWidth: '100%' }}>{title}</Typography>}
      <Box component="audio" controls src={src} sx={{ width: '100%', maxWidth: 480 }} />
    </Box>
  );
}
