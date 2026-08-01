import {
  Alert,
  Box,
  Button,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  IconButton,
  InputAdornment,
  TextField,
  Tooltip,
} from '@mui/material';
import ContentCopyIcon from '@mui/icons-material/ContentCopy';
import {useRef, useState} from 'react';
import {useQuery} from '@tanstack/react-query';
import {getShareConfig} from '../../api/share';

interface Props {
  testimonialId: number;
  authorName: string;
  onClose: () => void;
}

export default function TestimonialShareDialog({testimonialId, authorName, onClose}: Readonly<Props>) {
  const [copied, setCopied] = useState(false);
  const urlInputRef = useRef<HTMLInputElement>(null);

  const {data: shareConfig} = useQuery({
    queryKey: ['share-config'],
    queryFn: getShareConfig,
    staleTime: Infinity,
  });

  const origin = shareConfig?.baseUrl ?? globalThis.location.origin;
  const shareUrl = `${origin}${import.meta.env.BASE_URL}erfahrungsberichte/${testimonialId}`;

  const handleCopy = async () => {
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

  return (
      <Dialog open onClose={onClose} maxWidth="sm" fullWidth>
        <DialogTitle>Erfahrungsbericht teilen — „{authorName}"</DialogTitle>
        <DialogContent>
          <Box sx={{display: 'flex', flexDirection: 'column', gap: 2}}>
            <TextField
                label="Öffentlicher Link"
                value={shareUrl}
                inputRef={urlInputRef}
                slotProps={{
                  input: {
                    endAdornment: (
                        <InputAdornment position="end">
                          <Tooltip title="Link kopieren">
                            <IconButton onClick={handleCopy} edge="end" aria-label="Link kopieren">
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
          </Box>
        </DialogContent>
        <DialogActions>
          <Button onClick={onClose}>Schließen</Button>
        </DialogActions>
      </Dialog>
  );
}
