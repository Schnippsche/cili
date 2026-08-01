import {
  Button,
  CircularProgress,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  TextField,
} from '@mui/material';
import {useState} from 'react';
import axiosClient from '../../api/axiosClient';
import type {ProcessingJobDto} from '../../types/api';

interface Props {
  open: boolean;
  folderId: number;
  onClose: () => void;
  onStarted: (job: ProcessingJobDto) => void;
}

export default function VideoImportDialog({open, folderId, onClose, onStarted}: Readonly<Props>) {
  const [url, setUrl] = useState('');
  const [password, setPassword] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const reset = () => {
    setUrl('');
    setPassword('');
    setError(null);
  };

  const handleClose = () => {
    reset();
    onClose();
  };

  const handleSubmit = async () => {
    if (!url.trim()) return;
    setLoading(true);
    setError(null);
    try {
      const {data} = await axiosClient.post<ProcessingJobDto>(
          `/folders/${folderId}/video-import`,
          {url: url.trim(), password: password.trim() || null},
      );
      reset();
      onClose();
      onStarted(data);
    } catch (e: unknown) {
      const msg = (e as { response?: { data?: { message?: string } } })
          ?.response?.data?.message ?? 'Download konnte nicht gestartet werden.';
      setError(msg);
    } finally {
      setLoading(false);
    }
  };

  return (
      <Dialog open={open} onClose={handleClose} maxWidth="sm" fullWidth>
        <DialogTitle>Video-URL herunterladen</DialogTitle>
        <DialogContent
            sx={{display: 'flex', flexDirection: 'column', gap: 2, pt: '16px !important'}}>
          <TextField
              autoFocus
              fullWidth
              label="Video-URL"
              placeholder="https://www.loom.com/share/…"
              value={url}
              onChange={e => setUrl(e.target.value)}
              onKeyDown={e => e.key === 'Enter' && !loading && url.trim() && handleSubmit()}
              error={!!error}
              helperText={error ?? 'Loom, YouTube, Vimeo und viele weitere Plattformen werden unterstützt.'}
          />
          <TextField
              fullWidth
              label="Passwort (optional)"
              type="password"
              value={password}
              onChange={e => setPassword(e.target.value)}
              helperText="Nur ausfüllen wenn das Video passwortgeschützt ist."
          />
        </DialogContent>
        <DialogActions>
          <Button onClick={handleClose} disabled={loading}>Abbrechen</Button>
          <Button
              variant="contained"
              onClick={handleSubmit}
              disabled={!url.trim() || loading}
              startIcon={loading ? <CircularProgress size={16}/> : undefined}
          >
            {loading ? 'Startet…' : 'Download starten'}
          </Button>
        </DialogActions>
      </Dialog>
  );
}
