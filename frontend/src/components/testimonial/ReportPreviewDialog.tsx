import {useEffect, useRef, useState} from 'react';
import {
  Alert,
  AppBar,
  Box,
  Button,
  CircularProgress,
  Dialog,
  IconButton,
  Toolbar,
  Tooltip,
  Typography,
} from '@mui/material';
import CloseIcon from '@mui/icons-material/Close';
import PrintIcon from '@mui/icons-material/Print';
import {getCollectionReportPreview, getReportPreview} from '../../api/report';

interface Props {
  open: boolean;
  q: string;
  collectionId?: number;
  onClose: () => void;
}

export default function ReportPreviewDialog({open, q, collectionId, onClose}: Readonly<Props>) {
  const [html, setHtml] = useState<string | null>(null);
  const [loadingPreview, setLoadingPreview] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const iframeRef = useRef<HTMLIFrameElement>(null);

  useEffect(() => {
    if (!open) return;
    let cancelled = false;
    setHtml(null);
    setError(null);
    setLoadingPreview(true);
    (collectionId != null ? getCollectionReportPreview(collectionId) : getReportPreview(q))
    .then(h => {
      if (!cancelled) setHtml(h);
    })
    .catch(() => {
      if (!cancelled) setError('Vorschau konnte nicht geladen werden.');
    })
    .finally(() => {
      if (!cancelled) setLoadingPreview(false);
    });
    return () => {
      cancelled = true;
    };
  }, [open, q, collectionId]);

  function handlePrint() {
    iframeRef.current?.contentWindow?.print();
  }

  return (
      <Dialog fullScreen open={open} onClose={onClose}>
        <AppBar sx={{position: 'relative'}}>
          <Toolbar sx={{gap: 1}}>
            <Typography variant="h6" sx={{
              flex: 1,
              overflow: 'hidden',
              textOverflow: 'ellipsis',
              whiteSpace: 'nowrap'
            }}>
              Erfahrungsbericht{q ? ` – „${q}"` : ''}
            </Typography>

            <Button
                color="inherit"
                variant="outlined"
                size="small"
                startIcon={<PrintIcon/>}
                disabled={!html || loadingPreview}
                onClick={handlePrint}
            >
              Drucken
            </Button>

            <Tooltip title="Schließen">
              <IconButton color="inherit" onClick={onClose} edge="end" aria-label="Schließen">
                <CloseIcon/>
              </IconButton>
            </Tooltip>
          </Toolbar>
        </AppBar>

        <Box sx={{height: 'calc(100% - 64px)', display: 'flex', flexDirection: 'column'}}>
          {error && (
              <Alert severity="error" onClose={() => setError(null)} sx={{m: 1}}>
                {error}
              </Alert>
          )}
          {loadingPreview && (
              <Box sx={{display: 'flex', justifyContent: 'center', mt: 6}}>
                <CircularProgress/>
              </Box>
          )}
          {html && (
              <iframe
                  ref={iframeRef}
                  srcDoc={html}
                  style={{flex: 1, border: 'none', width: '100%'}}
                  title="Berichts-Vorschau"
              />
          )}
        </Box>
      </Dialog>
  );
}
