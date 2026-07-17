import { Alert, Box, Button, Chip, CircularProgress, LinearProgress, List, ListItem, ListItemText, Typography } from '@mui/material';
import CheckCircleOutlineIcon from '@mui/icons-material/CheckCircleOutline';
import ErrorOutlineIcon from '@mui/icons-material/ErrorOutline';
import SubtitlesOutlinedIcon from '@mui/icons-material/SubtitlesOutlined';
import type { UploadEntry } from '../../hooks/useUpload';

export default function UploadProgress({ uploads, onClear }: Readonly<{ uploads: UploadEntry[]; onClear: () => void }>) {
  const allDone = uploads.every(u => u.status !== 'uploading');
  return (
    <Box sx={{ mt: 2 }}>
      <Box sx={{ display: 'flex', justifyContent: 'space-between', mb: 1 }}>
        <Typography variant="subtitle2">Uploads</Typography>
        {allDone && <Button size="small" onClick={onClear}>Schließen</Button>}
      </Box>
      <List dense disablePadding>
        {uploads.map(u => (
          <ListItem key={u.id} disableGutters sx={{ flexDirection: 'column', alignItems: 'stretch' }}>
            <Box sx={{ display: 'flex', alignItems: 'center', width: '100%' }}>
              <ListItemText
                primary={u.fileName}
                secondary={u.status === 'uploading' ? <LinearProgress variant="determinate" value={u.progress} /> : u.error} />
              {u.status === 'done'      && <CheckCircleOutlineIcon color="success" fontSize="small" />}
              {u.status === 'error'     && <ErrorOutlineIcon color="error" fontSize="small" />}
              {u.status === 'uploading' && <Chip label={`${u.progress}%`} size="small" />}
            </Box>
            {u.codecWarning && (
              <Alert severity="warning" sx={{ py: 0, fontSize: '0.75rem' }}>{u.codecWarning}</Alert>
            )}
            {u.subtitleFile && (
              <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.75, pl: 2.5, mt: 0.25 }}>
                <SubtitlesOutlinedIcon sx={{ fontSize: 14, color: 'text.disabled' }} />
                <Typography variant="caption" color="text.secondary" sx={{ flexGrow: 1 }}>{u.subtitleFile}</Typography>
                {u.subtitleStatus === 'pending'   && <Chip label="wartend" size="small" sx={{ height: 16, fontSize: '0.65rem' }} />}
                {u.subtitleStatus === 'uploading' && <CircularProgress size={12} />}
                {u.subtitleStatus === 'done'      && <CheckCircleOutlineIcon color="success" sx={{ fontSize: 14 }} />}
                {u.subtitleStatus === 'error'     && (
                  <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.5 }}>
                    <ErrorOutlineIcon color="error" sx={{ fontSize: 14 }} />
                    {u.subtitleError && <Typography variant="caption" color="error">{u.subtitleError}</Typography>}
                  </Box>
                )}
              </Box>
            )}
          </ListItem>
        ))}
      </List>
    </Box>
  );
}
