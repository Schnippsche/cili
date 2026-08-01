import {useRef, useState} from 'react';
import {
  Alert,
  Box,
  Button,
  CircularProgress,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Divider,
  IconButton,
  TextField,
  Tooltip,
  Typography,
} from '@mui/material';
import ContentCopyIcon from '@mui/icons-material/ContentCopy';
import AccessTimeIcon from '@mui/icons-material/AccessTime';
import {useMutation, useQuery, useQueryClient} from '@tanstack/react-query';
import {createShare, getShare, getShareConfig, revokeShare} from '../../api/share';

interface Props {
  resourceId: number;
}

function formatExpiry(expiresAt: string): string {
  const exp = new Date(expiresAt);
  const now = new Date();
  const diffDays = Math.ceil((exp.getTime() - now.getTime()) / (1000 * 60 * 60 * 24));
  const dateStr = exp.toLocaleDateString('de-DE', {
    day: '2-digit',
    month: '2-digit',
    year: 'numeric'
  });
  if (diffDays <= 0) return `Abgelaufen am ${dateStr}`;
  if (diffDays === 1) return `Noch 1 Tag gültig (bis ${dateStr})`;
  return `Noch ${diffDays} Tage gültig (bis ${dateStr})`;
}

export default function ShareSection({resourceId}: Readonly<Props>) {
  const qc = useQueryClient();
  const [confirmRevoke, setConfirmRevoke] = useState(false);
  const [copyState, setCopyState] = useState<'idle' | 'copied' | 'manual'>('idle');
  const urlInputRef = useRef<HTMLInputElement>(null);

  const {data: token, isLoading} = useQuery({
    queryKey: ['share', resourceId],
    queryFn: () => getShare(resourceId),
  });

  const {data: shareConfig} = useQuery({
    queryKey: ['share-config'],
    queryFn: getShareConfig,
    staleTime: Infinity,
  });

  const validityDays = token?.validityDays ?? shareConfig?.validityDays ?? 90;

  const create = useMutation({
    mutationFn: () => createShare(resourceId),
    onSuccess: () => void qc.invalidateQueries({queryKey: ['share', resourceId]}),
  });

  const revoke = useMutation({
    mutationFn: () => revokeShare(resourceId),
    onSuccess: () => {
      setConfirmRevoke(false);
      void qc.invalidateQueries({queryKey: ['share', resourceId]});
    },
  });

  const origin = shareConfig?.baseUrl ?? globalThis.location.origin;
  const shareUrl = token
      ? `${origin}${import.meta.env.BASE_URL}share/${token.token}`
      : null;

  const handleCopy = async () => {
    if (!shareUrl) return;

    let success = false;
    if (urlInputRef.current) {
      urlInputRef.current.focus();
      urlInputRef.current.select();
      try {
        success = !!document.execCommand('copy');
      } catch { /* ignore */
      }
    }
    if (!success && navigator.clipboard?.writeText) {
      try {
        await navigator.clipboard.writeText(shareUrl);
        success = true;
      } catch { /* ignore */
      }
    }
    if (success) {
      setCopyState('copied');
      setTimeout(() => setCopyState('idle'), 2000);
    } else {
      urlInputRef.current?.select();
      setCopyState('manual');
      setTimeout(() => setCopyState('idle'), 5000);
    }
  };

  return (
      <Box>
        <Divider sx={{my: 2}}/>
        <Typography variant="subtitle2" gutterBottom>Freigabe-Link</Typography>

        {isLoading && <CircularProgress size={20}/>}

        {!isLoading && !token && (
            <Box sx={{display: 'flex', flexDirection: 'column', gap: 1}}>
              <Button
                  variant="outlined" size="small"
                  onClick={() => create.mutate()}
                  disabled={create.isPending}
              >
                {create.isPending ? <CircularProgress size={16}/> : 'Link generieren'}
              </Button>
              <Typography variant="caption" color="text.secondary"
                          sx={{display: 'flex', alignItems: 'center', gap: 0.5}}>
                <AccessTimeIcon sx={{fontSize: 13}}/>
                Links sind {validityDays} Tage gültig
              </Typography>
            </Box>
        )}

        {!isLoading && token && shareUrl && (
            <Box sx={{display: 'flex', flexDirection: 'column', gap: 1}}>
              <Box sx={{display: 'flex', gap: 1, alignItems: 'center'}}>
                <TextField size="small" fullWidth value={shareUrl}
                           inputRef={urlInputRef}
                           slotProps={{input: {readOnly: true}}}/>
                <Tooltip title="Link kopieren">
                  <IconButton size="small" onClick={handleCopy}>
                    <ContentCopyIcon fontSize="small"/>
                  </IconButton>
                </Tooltip>
              </Box>
              {copyState === 'copied' &&
                  <Alert severity="success" sx={{py: 0}}>Link kopiert!</Alert>}
              {copyState === 'manual' &&
                  <Alert severity="info" sx={{py: 0}}>URL markiert – Strg+C zum Kopieren
                    drücken</Alert>}
              <Tooltip title="Nach Ablauf wird der Link automatisch ungültig"
                       placement="bottom-start">
                <Typography variant="caption" color="text.secondary"
                            sx={{
                              display: 'flex',
                              alignItems: 'center',
                              gap: 0.5,
                              cursor: 'default',
                              width: 'fit-content'
                            }}>
                  <AccessTimeIcon sx={{fontSize: 13}}/>
                  {formatExpiry(token.expiresAt)}
                </Typography>
              </Tooltip>
              <Button
                  variant="outlined" color="error" size="small"
                  onClick={() => setConfirmRevoke(true)}
              >
                Link widerrufen
              </Button>
            </Box>
        )}

        <Dialog open={confirmRevoke} onClose={() => setConfirmRevoke(false)} maxWidth="xs"
                fullWidth>
          <DialogTitle>Link widerrufen</DialogTitle>
          <DialogContent>
            Freigabe-Link wirklich widerrufen? Der Link wird sofort ungültig.
          </DialogContent>
          <DialogActions>
            <Button onClick={() => setConfirmRevoke(false)}>Abbrechen</Button>
            <Button
                variant="contained" color="error"
                onClick={() => revoke.mutate()}
                disabled={revoke.isPending}
            >
              Widerrufen
            </Button>
          </DialogActions>
        </Dialog>
      </Box>
  );
}
