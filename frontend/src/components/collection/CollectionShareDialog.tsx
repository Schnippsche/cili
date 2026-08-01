import {
  Alert,
  Box,
  Button,
  CircularProgress,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  IconButton,
  InputAdornment,
  TextField,
  Tooltip,
  Typography,
} from '@mui/material';
import ContentCopyIcon from '@mui/icons-material/ContentCopy';
import {useRef, useState} from 'react';
import {useMutation, useQuery, useQueryClient} from '@tanstack/react-query';
import {
  createCollectionShare,
  getCollectionShare,
  revokeCollectionShare,
} from '../../api/collectionShare';
import {getShareConfig} from '../../api/share';
import type {CollectionDto} from '../../types/api';

interface Props {
  collection: CollectionDto;
  onClose: () => void;
}

export default function CollectionShareDialog({collection, onClose}: Readonly<Props>) {
  const qc = useQueryClient();
  const [copied, setCopied] = useState(false);
  const urlInputRef = useRef<HTMLInputElement>(null);

  const {data: shareToken, isLoading, isError} = useQuery({
    queryKey: ['collections', collection.id, 'share'],
    queryFn: () => getCollectionShare(collection.id),
  });

  const {data: shareConfig} = useQuery({
    queryKey: ['share-config'],
    queryFn: getShareConfig,
    staleTime: Infinity,
  });

  const createMutation = useMutation({
    mutationFn: () => createCollectionShare(collection.id),
    onSuccess: () => qc.invalidateQueries({queryKey: ['collections', collection.id, 'share']}),
  });

  const revokeMutation = useMutation({
    mutationFn: () => revokeCollectionShare(collection.id),
    onSuccess: () => qc.invalidateQueries({queryKey: ['collections', collection.id, 'share']}),
  });

  const origin = shareConfig?.baseUrl ?? globalThis.location.origin;
  const shareUrl = shareToken
      ? `${origin}${import.meta.env.BASE_URL}share/collection/${shareToken.token}`
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
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    }
  };

  const expiresAt = shareToken
      ? new Date(shareToken.expiresAt).toLocaleDateString('de-DE', {
        day: '2-digit',
        month: '2-digit',
        year: 'numeric'
      })
      : null;

  return (
      <Dialog open onClose={onClose} maxWidth="sm" fullWidth>
        <DialogTitle>Sammlung teilen — „{collection.name}"</DialogTitle>
        <DialogContent>
          {isLoading && <CircularProgress size={24}/>}

          {isError && (
              <Alert severity="error">Fehler beim Laden des Links. Bitte Dialog schließen und erneut
                öffnen.</Alert>
          )}

          {!isLoading && !isError && !shareToken && (
              <Box>
                <Typography variant="body2" color="text.secondary" sx={{mb: 2}}>
                  Erstelle einen öffentlichen Link, mit dem Interessenten die Sammlung ohne Login
                  einsehen können.
                  Der Link ist {createMutation.data?.validityDays ?? 90} Tage gültig.
                </Typography>
                <Button
                    variant="contained"
                    onClick={() => createMutation.mutate()}
                    disabled={createMutation.isPending}
                >
                  Link erstellen
                </Button>
              </Box>
          )}

          {!isLoading && !isError && shareToken && shareUrl && (
              <Box sx={{display: 'flex', flexDirection: 'column', gap: 2}}>
                <TextField
                    label="Freigabe-Link"
                    value={shareUrl}
                    inputRef={urlInputRef}
                    slotProps={{
                      input: {
                        endAdornment: (
                            <InputAdornment position="end">
                              <Tooltip title="Link kopieren">
                                <IconButton onClick={handleCopy} edge="end"
                                            aria-label="Link kopieren">
                                  <ContentCopyIcon/>
                                </IconButton>
                              </Tooltip>
                            </InputAdornment>
                        ),
                      },
                      htmlInput: {readOnly: true},
                    }}
                />
                {copied && <Alert severity="success" sx={{py: 0}}>Link kopiert!</Alert>}
                <Typography variant="caption" color="text.secondary">
                  Gültig bis {expiresAt}
                </Typography>
              </Box>
          )}
        </DialogContent>
        <DialogActions>
          {shareToken && (
              <>
                <Button
                    color="warning"
                    onClick={() => revokeMutation.mutate()}
                    disabled={revokeMutation.isPending}
                >
                  Link widerrufen
                </Button>
                <Button
                    onClick={() => createMutation.mutate()}
                    disabled={createMutation.isPending}
                >
                  Link erneuern
                </Button>
              </>
          )}
          <Button onClick={onClose}>Schließen</Button>
        </DialogActions>
      </Dialog>
  );
}
