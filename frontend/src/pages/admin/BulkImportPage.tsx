import { useEffect, useRef, useState } from 'react';
import { Typography, Button, Stack, Alert } from '@mui/material';
import AppShell from '../../components/layout/AppShell';
import MovePicker from '../../components/common/MovePicker';
import BulkImportPanel from '../../components/admin/BulkImportPanel';
import { useBulkImportUpload, rootSegmentOf } from '../../hooks/useBulkImportUpload';
import type { BulkImportItemStatus } from '../../types/api';

const SUPPORTED_EXT_HINT = new Set([
  'mp4', 'mkv', 'avi', 'mov', 'webm', 'flv', 'wmv', 'ts', 'ogv', 'm4v',
  'mp3', 'wav', 'flac', 'ogg', 'm4a',
  'jpg', 'jpeg', 'png', 'gif', 'webp',
  'pdf', 'docx', 'doc', 'odt', 'rtf',
  'vtt', 'srt', 'txt',
]);

const TERMINAL_STATUSES = new Set<BulkImportItemStatus>(['DONE', 'SKIPPED', 'FAILED']);
const JOB_ID_STORAGE_KEY = 'cili.bulkImport.jobId';

function fileExt(name: string): string {
  const dot = name.lastIndexOf('.');
  return dot >= 0 ? name.slice(dot + 1).toLowerCase() : '';
}

export default function BulkImportPage() {
  const inputRef = useRef<HTMLInputElement>(null);
  const [selectedFiles, setSelectedFiles] = useState<File[]>([]);
  const [rootName, setRootName] = useState<string>('');
  const [pickerOpen, setPickerOpen] = useState(false);
  const [starting, setStarting] = useState(false);
  const [resumeError, setResumeError] = useState<string | null>(null);
  const { jobId, items, running, start, resume } = useBulkImportUpload();

  useEffect(() => {
    if (inputRef.current) {
      inputRef.current.setAttribute('webkitdirectory', '');
    }
  }, []);

  useEffect(() => {
    if (!jobId) return;
    const allTerminal = items.length > 0 && items.every(i => TERMINAL_STATUSES.has(i.status));
    if (allTerminal) {
      localStorage.removeItem(JOB_ID_STORAGE_KEY);
    } else {
      localStorage.setItem(JOB_ID_STORAGE_KEY, jobId);
    }
  }, [jobId, items]);

  const resumableJobId = !jobId ? localStorage.getItem(JOB_ID_STORAGE_KEY) : null;

  const isFinished = !running && jobId !== null && items.length > 0
    && items.every(i => TERMINAL_STATUSES.has(i.status));
  const doneCount = items.filter(i => i.status === 'DONE').length;
  const skippedCount = items.filter(i => i.status === 'SKIPPED').length;
  const failedCount = items.filter(i => i.status === 'FAILED').length;

  const handleFolderChosen = () => inputRef.current?.click();

  const handleFilesSelected: React.ChangeEventHandler<HTMLInputElement> = e => {
    const files = Array.from(e.target.files ?? []);
    setSelectedFiles(files);
    setRootName(files[0] ? rootSegmentOf(files[0]) : '');
    if (resumableJobId && files.length > 0) {
      setResumeError(null);
      resume(resumableJobId, files).catch((err: unknown) => {
        const message = (err as { message?: string })?.message ?? 'Fortsetzen des Imports fehlgeschlagen';
        setResumeError(message);
      });
    }
  };

  const handleDiscardStuckJob = () => {
    localStorage.removeItem(JOB_ID_STORAGE_KEY);
    setResumeError(null);
    setSelectedFiles([]);
    setRootName('');
  };

  const handleTargetChosen = async (targetFolderId: number) => {
    if (starting) return;
    setStarting(true);
    setPickerOpen(false);
    try {
      await start(targetFolderId, rootName, selectedFiles);
    } finally {
      setStarting(false);
    }
  };

  const preFilterHint = selectedFiles.filter(f => !SUPPORTED_EXT_HINT.has(fileExt(f.name))).length;

  return (
    <AppShell>
      <Typography variant="h5" gutterBottom>Bulk-Ordner-Import</Typography>

      <Stack spacing={2} sx={{ maxWidth: 600 }}>
        {resumableJobId && !resumeError && (
          <Alert severity="warning">
            Ein vorheriger Import wurde nicht abgeschlossen. Denselben Quellordner erneut
            auswählen, um fortzufahren.
          </Alert>
        )}
        {resumeError && (
          <Alert
            severity="error"
            action={
              <Button color="inherit" size="small" onClick={handleDiscardStuckJob}>
                Neu starten
              </Button>
            }
          >
            Fortsetzen des vorherigen Imports fehlgeschlagen: {resumeError}
          </Alert>
        )}
        <input
          ref={inputRef}
          data-testid="folder-input"
          type="file"
          multiple
          hidden
          onChange={handleFilesSelected}
        />
        <Button variant="outlined" onClick={handleFolderChosen} disabled={running}>
          Ordner auswählen
        </Button>
        {selectedFiles.length > 0 && (
          <Typography variant="body2">
            {selectedFiles.length} Dateien aus &quot;{rootName}&quot; ausgewählt
            {preFilterHint > 0 && ` — ${preFilterHint} davon vermutlich nicht unterstützt`}
          </Typography>
        )}
        <Button
          variant="contained"
          disabled={selectedFiles.length === 0 || running || starting || !!resumableJobId}
          onClick={() => setPickerOpen(true)}
        >
          Zielordner wählen
        </Button>

        {items.length > 0 && (
          <>
            {running && <Alert severity="info">Import läuft …</Alert>}
            {isFinished && (
              <Alert severity={failedCount > 0 ? 'warning' : 'success'}>
                Import abgeschlossen: {doneCount} fertig, {skippedCount} übersprungen,{' '}
                {failedCount} fehlgeschlagen.
              </Alert>
            )}
            <BulkImportPanel items={items} />
          </>
        )}
      </Stack>

      <MovePicker
        open={pickerOpen}
        title="Zielordner für den Import wählen"
        onClose={() => setPickerOpen(false)}
        onConfirm={handleTargetChosen}
      />
    </AppShell>
  );
}
