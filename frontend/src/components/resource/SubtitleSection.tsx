import {useState} from 'react';
import {
  Alert,
  Box,
  Button,
  Chip,
  CircularProgress,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Divider,
  Menu,
  MenuItem,
  Typography,
} from '@mui/material';
import DeleteIcon from '@mui/icons-material/Delete';
import DownloadIcon from '@mui/icons-material/Download';
import ArrowDropDownIcon from '@mui/icons-material/ArrowDropDown';
import RefreshIcon from '@mui/icons-material/Refresh';
import {useQueryClient} from '@tanstack/react-query';
import type {SubtitleTrackDto} from '../../types/api';
import {deleteSubtitle, getSubtitleDownloadUrl} from '../../api/resources';
import {
  useActiveTranscriptionJob,
  useRetranscribeResource,
  useSubtitleTracks
} from '../../hooks/useResources';
import {useLanguageOptions} from '../../hooks/useLanguageOptions';

interface Props {
  resourceId: number;
  readonly?: boolean;
  canDownload?: boolean;
}

export default function SubtitleSection({
                                          resourceId,
                                          readonly = false,
                                          canDownload = false
                                        }: Readonly<Props>) {
  const qc = useQueryClient();
  const {data: tracks, isLoading: loading, isError} = useSubtitleTracks(resourceId);
  const {data: languages = []} = useLanguageOptions();
  const langLabel = (code: string) =>
      languages.find(l => l.code.toLowerCase() === code.toLowerCase())?.label ?? code.toUpperCase();

  const [deletingId, setDeletingId] = useState<number | null>(null);
  const [deleteError, setDeleteError] = useState<string | null>(null);

  const [menuAnchor, setMenuAnchor] = useState<{ el: HTMLElement; trackId: number } | null>(null);
  const [retranscribeOpen, setRetranscribeOpen] = useState(false);
  const retranscribe = useRetranscribeResource();
  const {data: transcriptionRunning = false} = useActiveTranscriptionJob(resourceId, !readonly);

  const handleDelete = async () => {
    if (deletingId === null) return;
    try {
      await deleteSubtitle(resourceId, deletingId);
      setDeletingId(null);
      void qc.invalidateQueries({queryKey: ['subtitles', resourceId]});
    } catch {
      setDeleteError('Löschen fehlgeschlagen.');
      setDeletingId(null);
    }
  };

  const openMenu = (e: React.MouseEvent<HTMLElement>, trackId: number) => {
    setMenuAnchor({el: e.currentTarget, trackId});
  };
  const closeMenu = () => setMenuAnchor(null);

  const downloadTrack = (track: SubtitleTrackDto, format: 'vtt' | 'srt' | 'txt') => {
    const a = document.createElement('a');
    a.href = getSubtitleDownloadUrl(resourceId, track.id, format);
    a.download = '';
    a.click();
    closeMenu();
  };

  return (
      <Box>
        <Divider sx={{my: 2}}/>
        <Typography variant="subtitle2" gutterBottom>Untertitel</Typography>

        {loading && <CircularProgress size={20}/>}
        {isError &&
            <Alert severity="error" sx={{mb: 1}}>Untertitel konnten nicht geladen werden.</Alert>}

        {tracks?.length === 0 && (
            <Typography variant="body2" color="text.secondary" sx={{mb: 1}}>
              Noch keine Untertitel.
            </Typography>
        )}

        {tracks?.map(track => (
            <Box key={track.id} sx={{display: 'flex', alignItems: 'center', gap: 1, mb: 1}}>
              <Chip label={langLabel(track.languageCode)} size="small" sx={{flexShrink: 0}}/>
              <Box sx={{flex: 1}}/>

              {canDownload && (
                  <>
                    <Button
                        size="small"
                        startIcon={<DownloadIcon/>}
                        endIcon={<ArrowDropDownIcon/>}
                        onClick={e => openMenu(e, track.id)}
                    >
                      Download
                    </Button>
                    <Menu
                        anchorEl={menuAnchor?.trackId === track.id ? menuAnchor.el : null}
                        open={menuAnchor?.trackId === track.id}
                        onClose={closeMenu}
                    >
                      <MenuItem onClick={() => downloadTrack(track, 'vtt')}>Untertitel als
                        VTT</MenuItem>
                      <MenuItem onClick={() => downloadTrack(track, 'srt')}>Untertitel als
                        SRT</MenuItem>
                      <MenuItem onClick={() => downloadTrack(track, 'txt')}>Untertitel als
                        Text</MenuItem>
                    </Menu>
                  </>
              )}

              {!readonly && (
                  <Button
                      size="small"
                      color="error"
                      startIcon={<DeleteIcon/>}
                      onClick={() => {
                        setDeleteError(null);
                        setDeletingId(track.id);
                      }}
                  >
                    Löschen
                  </Button>
              )}
            </Box>
        ))}

        {deleteError && <Alert severity="error" sx={{mb: 1}}>{deleteError}</Alert>}

        {!readonly && (
            <Box sx={{mt: 1, mb: 1}}>
              <Button
                  size="small"
                  startIcon={transcriptionRunning || retranscribe.isPending ?
                      <CircularProgress size={14}/> : <RefreshIcon/>}
                  disabled={transcriptionRunning || retranscribe.isPending}
                  onClick={() => setRetranscribeOpen(true)}
              >
                {transcriptionRunning ? 'Transkription läuft…' : 'Neu transkribieren'}
              </Button>
              {retranscribe.isSuccess && !transcriptionRunning && (
                  <Alert severity="success" sx={{mt: 1, py: 0}}>Transkription gestartet.</Alert>
              )}
              {retranscribe.isError && (
                  <Alert severity="error" sx={{mt: 1, py: 0}}>Fehler beim Starten der
                    Transkription.</Alert>
              )}
            </Box>
        )}

        <Dialog open={deletingId !== null} onClose={() => setDeletingId(null)} maxWidth="xs"
                fullWidth>
          <DialogTitle>Untertitel löschen</DialogTitle>
          <DialogContent>Untertitel wirklich löschen?</DialogContent>
          <DialogActions>
            <Button onClick={() => setDeletingId(null)}>Abbrechen</Button>
            <Button variant="contained" color="error" onClick={handleDelete}>Löschen</Button>
          </DialogActions>
        </Dialog>

        <Dialog open={retranscribeOpen} onClose={() => setRetranscribeOpen(false)} maxWidth="xs"
                fullWidth>
          <DialogTitle>Neu transkribieren?</DialogTitle>
          <DialogContent>
            {(tracks?.length ?? 0) > 0
                ? 'Der bestehende automatische Untertitel wird überschrieben. Manuell hochgeladene Untertitel bleiben erhalten.'
                : 'Automatische Transkription für dieses Video starten?'}
          </DialogContent>
          <DialogActions>
            <Button onClick={() => setRetranscribeOpen(false)}>Abbrechen</Button>
            <Button variant="contained" onClick={() => {
              setRetranscribeOpen(false);
              retranscribe.mutate(resourceId);
            }}>
              Transkription starten
            </Button>
          </DialogActions>
        </Dialog>
      </Box>
  );
}
