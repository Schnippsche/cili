import {
  Alert,
  AlertTitle,
  Box,
  Button,
  CircularProgress,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  LinearProgress,
  Menu,
  MenuItem,
  Snackbar,
  TextField,
  Tooltip,
  Typography
} from '@mui/material';
import {useEffect, useMemo, useState} from 'react';
import {useSelector} from 'react-redux';
import {useQuery, useQueryClient} from '@tanstack/react-query';
import * as adminApi from '../../api/admin';
import {
  useCreateFolder,
  useFolderPermissions,
  useMoveFolder,
  useTrashFolder,
  useUpdateFolder
} from '../../hooks/useFolders';
import type {RootState} from '../../store/store';
import type {FolderDto, ProcessingJobDto} from '../../types/api';
import MovePicker from '../common/MovePicker';
import VideoImportDialog from './VideoImportDialog';

interface Props {
  parentId?: number;
  folderId?: number;
  canWrite: boolean;
  canUpload?: boolean;
  contextFolder: FolderDto | null;
  menuAnchor: HTMLElement | null;
  onMenuClose: () => void;
}

export default function FolderActions({
                                        parentId,
                                        folderId,
                                        canWrite,
                                        canUpload = false,
                                        contextFolder,
                                        menuAnchor,
                                        onMenuClose
                                      }: Readonly<Props>) {
  const isAdmin = useSelector((s: RootState) => s.auth.user?.role === 'ADMIN');
  const qc = useQueryClient();
  const {data: ctxPerms} = useFolderPermissions(contextFolder?.id);
  const canRenameCtx = isAdmin || (ctxPerms?.permissions.includes('WRITE') ?? false);
  const canDeleteCtx = isAdmin || (ctxPerms?.permissions.includes('DELETE') ?? false);
  const [createOpen, setCreateOpen] = useState(false);
  const [renameOpen, setRenameOpen] = useState(false);
  const [trashOpen, setTrashOpen] = useState(false);
  const [moveOpen, setMoveOpen] = useState(false);
  const [videoImportOpen, setVideoImportOpen] = useState(false);
  const [activeImportJob, setActiveImportJob] = useState<ProcessingJobDto | null>(null);
  const [doneSnack, setDoneSnack] = useState<{
    open: boolean;
    severity: 'success' | 'error';
    msg: string
  }>(
      {open: false, severity: 'success', msg: ''},
  );

  const isImportRunning = activeImportJob?.status === 'PENDING' || activeImportJob?.status === 'RUNNING';

  const {data: polledJob} = useQuery({
    queryKey: ['job', activeImportJob?.id],
    queryFn: () => adminApi.getJob(activeImportJob!.id),
    enabled: activeImportJob !== null && isImportRunning,
    refetchInterval: 3000,
  });

  interface ImportProgress {
    phase: 'download' | 'processing' | 'uploading';
    progress?: number;
  }

  const importProgress = useMemo<ImportProgress | null>(() => {
    if (!polledJob?.result) return null;
    try {
      return JSON.parse(polledJob.result) as ImportProgress;
    } catch {
      return null;
    }
  }, [polledJob?.result]);

  useEffect(() => {
    if (!polledJob) return;
    if (polledJob.status === 'DONE') {
      setActiveImportJob(null);
      setDoneSnack({open: true, severity: 'success', msg: 'Video erfolgreich hochgeladen.'});
      void qc.invalidateQueries({queryKey: ['resources', 'folder', folderId]});
    } else if (polledJob.status === 'FAILED' || polledJob.status === 'CANCELLED') {
      setActiveImportJob(null);
      setDoneSnack({
        open: true,
        severity: 'error',
        msg: polledJob.errorMessage ?? 'Download fehlgeschlagen.'
      });
    } else {
      setActiveImportJob(polledJob);
    }
  }, [polledJob]);
  const [actionFolder, setActionFolder] = useState<FolderDto | null>(null);
  const [name, setName] = useState('');
  const createFolder = useCreateFolder();
  const updateFolder = useUpdateFolder();
  const trashFolder = useTrashFolder();
  const moveFolder = useMoveFolder();

  const handleCreate = async () => {
    if (!name.trim()) return;
    await createFolder.mutateAsync({name: name.trim(), parentId});
    setCreateOpen(false);
    setName('');
  };

  const handleRename = async () => {
    if (!actionFolder || !name.trim()) return;
    await updateFolder.mutateAsync({id: actionFolder.id, req: {name: name.trim()}});
    setRenameOpen(false);
    setName('');
    onMenuClose();
  };

  return (
      <>
        {canUpload && folderId != null && (
            <Tooltip title="Video von einer URL herunterladen (z.B. Loom, YouTube, Vimeo)">
              <Button variant="outlined" size="small" onClick={() => setVideoImportOpen(true)}>
                Direkt-Upload
              </Button>
            </Tooltip>
        )}
        {canWrite && <Button variant="outlined" size="small" onClick={() => setCreateOpen(true)}>Neuer
          Ordner</Button>}

        <Menu anchorEl={menuAnchor} open={Boolean(menuAnchor)} onClose={onMenuClose}>
          {canRenameCtx && <MenuItem onClick={() => {
            setActionFolder(contextFolder);
            setName(contextFolder?.name ?? '');
            setRenameOpen(true);
            onMenuClose();
          }}>Umbenennen</MenuItem>}
          {canDeleteCtx && (
              <MenuItem onClick={() => {
                setActionFolder(contextFolder);
                setMoveOpen(true);
                onMenuClose();
              }}>
                Verschieben
              </MenuItem>
          )}
          {canDeleteCtx && <MenuItem sx={{color: 'error.main'}} onClick={() => {
            setActionFolder(contextFolder);
            setTrashOpen(true);
            onMenuClose();
          }}>In Papierkorb</MenuItem>}
        </Menu>

        <Dialog open={createOpen} onClose={() => setCreateOpen(false)} maxWidth="xs" fullWidth>
          <DialogTitle>Neuer Ordner</DialogTitle>
          <DialogContent>
            <TextField autoFocus fullWidth label="Ordnername" value={name}
                       onChange={e => setName(e.target.value)} sx={{mt: 1}}/>
          </DialogContent>
          <DialogActions>
            <Button onClick={() => setCreateOpen(false)}>Abbrechen</Button>
            <Button variant="contained" onClick={handleCreate}
                    disabled={!name.trim()}>Erstellen</Button>
          </DialogActions>
        </Dialog>

        <Dialog open={renameOpen} onClose={() => setRenameOpen(false)} maxWidth="xs" fullWidth>
          <DialogTitle>Ordner umbenennen</DialogTitle>
          <DialogContent>
            <TextField autoFocus fullWidth label="Neuer Name" value={name}
                       onChange={e => setName(e.target.value)} sx={{mt: 1}}/>
          </DialogContent>
          <DialogActions>
            <Button onClick={() => setRenameOpen(false)}>Abbrechen</Button>
            <Button variant="contained" onClick={handleRename}
                    disabled={!name.trim()}>Umbenennen</Button>
          </DialogActions>
        </Dialog>

        <Dialog open={trashOpen} onClose={() => setTrashOpen(false)} maxWidth="xs" fullWidth>
          <DialogTitle>Ordner in Papierkorb verschieben?</DialogTitle>
          <DialogContent>
            Der Ordner <strong>{actionFolder?.name}</strong> und alle enthaltenen Inhalte werden in
            den Papierkorb verschoben.
          </DialogContent>
          <DialogActions>
            <Button onClick={() => setTrashOpen(false)}>Abbrechen</Button>
            <Button variant="contained" color="error" onClick={() => {
              if (actionFolder) {
                trashFolder.mutate(actionFolder.id);
                setTrashOpen(false);
              }
            }}>
              In Papierkorb
            </Button>
          </DialogActions>
        </Dialog>

        <MovePicker
            open={moveOpen}
            title={`"${actionFolder?.name}" verschieben`}
            excludeId={actionFolder?.id}
            onClose={() => setMoveOpen(false)}
            onConfirm={async (targetId) => {
              if (!actionFolder) return;
              await moveFolder.mutateAsync({id: actionFolder.id, newParentId: targetId});
              setMoveOpen(false);
            }}
        />

        {folderId != null && (
            <VideoImportDialog
                open={videoImportOpen}
                folderId={folderId}
                onClose={() => setVideoImportOpen(false)}
                onStarted={(job: ProcessingJobDto) => setActiveImportJob(job)}
            />
        )}

        {/* Laufender Import */}
        <Snackbar open={isImportRunning} anchorOrigin={{vertical: 'bottom', horizontal: 'center'}}>
          <Alert severity="info" icon={<CircularProgress size={16}/>} sx={{minWidth: 320}}>
            <AlertTitle sx={{mb: 0.5}}>Video-Import läuft</AlertTitle>
            {importProgress?.phase === 'download' && importProgress.progress != null ? (
                <Box>
                  <Typography variant="body2"
                              sx={{mb: 0.5}}>Download: {importProgress.progress}%</Typography>
                  <LinearProgress variant="determinate" value={importProgress.progress}
                                  sx={{borderRadius: 1}}/>
                </Box>
            ) : importProgress?.phase === 'processing' ? (
                <Box>
                  <Typography variant="body2" sx={{mb: 0.5}}>Video wird verarbeitet…</Typography>
                  <LinearProgress sx={{borderRadius: 1}}/>
                </Box>
            ) : importProgress?.phase === 'uploading' ? (
                <Box>
                  <Typography variant="body2" sx={{mb: 0.5}}>Wird hochgeladen…</Typography>
                  <LinearProgress sx={{borderRadius: 1}}/>
                </Box>
            ) : (
                <Typography variant="body2">Wird vorbereitet…</Typography>
            )}
          </Alert>
        </Snackbar>

        {/* Abschluss-Meldung */}
        <Snackbar
            open={doneSnack.open}
            autoHideDuration={6000}
            onClose={() => setDoneSnack(s => ({...s, open: false}))}
            anchorOrigin={{vertical: 'bottom', horizontal: 'center'}}
        >
          <Alert severity={doneSnack.severity}
                 onClose={() => setDoneSnack(s => ({...s, open: false}))}>
            {doneSnack.msg}
          </Alert>
        </Snackbar>
      </>
  );
}
