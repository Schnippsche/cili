import {useEffect, useRef, useState} from 'react';
import {Box, Button, CircularProgress, Link, Typography} from '@mui/material';
import * as pdfjsLib from 'pdfjs-dist';

pdfjsLib.GlobalWorkerOptions.workerSrc = new URL(
    'pdfjs-dist/build/pdf.worker.min.mjs', import.meta.url
).toString();

export default function PdfViewer({src, onClose}: Readonly<{ src: string; onClose?: () => void }>) {
  const canvasRef = useRef<HTMLCanvasElement>(null);
  const pdfRef = useRef<pdfjsLib.PDFDocumentProxy | null>(null);
  const [ready, setReady] = useState(false);
  const [page, setPage] = useState(1);
  const [pageCount, setPageCount] = useState(0);
  const [error, setError] = useState<string | null>(null);

  async function renderPage(pdf: pdfjsLib.PDFDocumentProxy, num: number) {
    const p = await pdf.getPage(num);
    const canvas = canvasRef.current;
    if (!canvas) return;
    const vp = p.getViewport({scale: 1.5});
    canvas.width = vp.width;
    canvas.height = vp.height;
    const ctx = canvas.getContext('2d');
    if (!ctx) return;
    await p.render({canvasContext: ctx, viewport: vp}).promise;
  }

  useEffect(() => {
    let cancelled = false;
    setReady(false);
    setError(null);
    pdfjsLib.getDocument(src).promise.then(async (pdf) => {
      if (cancelled) return;
      pdfRef.current = pdf;
      setPageCount(pdf.numPages);
      await renderPage(pdf, 1);
      if (!cancelled) setReady(true);
    }).catch(err => {
      if (!cancelled) setError(err.message);
    });
    return () => {
      cancelled = true;
    };
  }, [src]);

  useEffect(() => {
    if (pdfRef.current) renderPage(pdfRef.current, page);
  }, [page]);

  return (
      <Box sx={{position: 'relative'}}>
        {!ready && !error && (
            <Box sx={{display: 'flex', justifyContent: 'center', mt: 4}}>
              <CircularProgress/>
            </Box>
        )}
        {error && <Typography color="error">Failed to load PDF: {error}</Typography>}
        {/* Canvas must always stay mounted so canvasRef.current is valid when renderPage runs */}
        <Box sx={{display: ready ? 'flex' : 'none', gap: 1, mb: 1, alignItems: 'center'}}>
          <Button size="small" disabled={page <= 1}
                  onClick={() => setPage(p => p - 1)}>Zurück</Button>
          <Typography variant="body2">{page} / {pageCount}</Typography>
          <Button size="small" disabled={page >= pageCount}
                  onClick={() => setPage(p => p + 1)}>Weiter</Button>
          {onClose && (
              <Link component="button" variant="body2" onClick={onClose} sx={{ml: 'auto'}}>
                Ansicht schließen
              </Link>
          )}
        </Box>
        <canvas ref={canvasRef} style={{maxWidth: '100%', display: ready ? 'block' : 'none'}}/>
      </Box>
  );
}
