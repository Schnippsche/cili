import {useState} from 'react';
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
  List,
  ListItem,
  ListItemText,
  Snackbar,
  Tooltip,
  Typography,
} from '@mui/material';
import DeleteForeverOutlinedIcon from '@mui/icons-material/DeleteForeverOutlined';
import RestoreFromTrashOutlinedIcon from '@mui/icons-material/RestoreFromTrashOutlined';
import AppShell from '../components/layout/AppShell';
import {usePurgeFolder, useRestoreFolder, useTrash} from '../hooks/useFolders';
import type {FolderDto} from '../types/api';

function formatDate(iso: string | null): string {
  if (!iso) return '';
  const d = new Date(iso);
  return ` · gelöscht am ${d.toLocaleDateString('de-DE')} ${d.toLocaleTimeString('de-DE', {
    hour: '2-digit',
    minute: '2-digit'
  })}`;
}

export default function TrashPage() {
  const {data: folders, isLoading, isError} = useTrash();
  const restore = useRestoreFolder();
  const purge = usePurgeFolder();

  const [pendingPurge, setPendingPurge] = useState<FolderDto | null>(null);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  function handleRestore(id: number) {
    restore.mutate(id, {onError: () => setErrorMessage('Wiederherstellen fehlgeschlagen.')});
  }

  function handlePurgeConfirm() {
    if (!pendingPurge) return;
    purge.mutate(pendingPurge.id, {
      onSuccess: () => setPendingPurge(null),
      onError: () => {
        setPendingPurge(null);
        setErrorMessage('Löschen fehlgeschlagen.');
      },
    });
  }

  return (
      <AppShell>
        <Typography variant="h5" gutterBottom>Papierkorb</Typography>

        {isLoading && <CircularProgress/>}

        {isError && <Alert severity="error">Papierkorb konnte nicht geladen werden.</Alert>}

        {!isLoading && !isError && folders !== undefined && folders.length === 0 && (
            <Typography color="text.secondary">Papierkorb ist leer.</Typography>
        )}

        {folders && folders.length > 0 && (
            <List>
              {folders.map(folder => (
                  <ListItem
                      key={folder.id}
                      divider
                      secondaryAction={
                        <>
                          <Tooltip title="Wiederherstellen">
                    <span>
                      <IconButton
                          aria-label="wiederherstellen"
                          onClick={() => handleRestore(folder.id)}
                          disabled={restore.isPending}
                      >
                        <RestoreFromTrashOutlinedIcon/>
                      </IconButton>
                    </span>
                          </Tooltip>
                          <Tooltip title="Endgültig löschen">
                            <IconButton
                                aria-label="endgültig löschen"
                                color="error"
                                onClick={() => setPendingPurge(folder)}
                            >
                              <DeleteForeverOutlinedIcon/>
                            </IconButton>
                          </Tooltip>
                        </>
                      }
                  >
                    <ListItemText
                        primary={<Box component="span"
                                      sx={{fontWeight: 'bold'}}>{folder.name}</Box>}
                        secondary={`${folder.path}${formatDate(folder.trashedAt)}`}
                    />
                  </ListItem>
              ))}
            </List>
        )}

        <Dialog open={pendingPurge !== null} onClose={() => setPendingPurge(null)}>
          <DialogTitle>Ordner endgültig löschen?</DialogTitle>
          <DialogContent>
            Ordner „{pendingPurge?.name}" und alle enthaltenen Dateien werden unwiderruflich
            gelöscht.
          </DialogContent>
          <DialogActions>
            <Button onClick={() => setPendingPurge(null)}>Abbrechen</Button>
            <Button
                color="error"
                disabled={purge.isPending}
                onClick={handlePurgeConfirm}
            >
              Löschen
            </Button>
          </DialogActions>
        </Dialog>

        <Snackbar
            open={errorMessage !== null}
            autoHideDuration={4000}
            onClose={() => setErrorMessage(null)}
        >
          <Alert severity="error" onClose={() => setErrorMessage(null)}>
            {errorMessage}
          </Alert>
        </Snackbar>
      </AppShell>
  );
}
