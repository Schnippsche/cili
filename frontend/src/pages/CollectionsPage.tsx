import {useState} from 'react';
import {useSelector} from 'react-redux';
import {
  Alert,
  Box,
  Button,
  Card,
  CardActionArea,
  CardContent,
  Checkbox,
  Chip,
  CircularProgress,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Divider,
  FormControlLabel,
  Grid2,
  IconButton,
  List,
  ListItemButton,
  ListItemText,
  TextField,
  Tooltip,
  Typography
} from '@mui/material';
import AddIcon from '@mui/icons-material/Add';
import ContentCopyIcon from '@mui/icons-material/ContentCopy';
import DeleteIcon from '@mui/icons-material/Delete';
import EditIcon from '@mui/icons-material/Edit';
import ShareIcon from '@mui/icons-material/Share';
import CollectionsBookmarkIcon from '@mui/icons-material/CollectionsBookmark';
import CollectionShareDialog from '../components/collection/CollectionShareDialog';
import {useNavigate} from 'react-router-dom';
import AppShell from '../components/layout/AppShell';
import {
  useCollections,
  useCollectionTemplates,
  useCopyCollection,
  useCreateCollection,
  useCreateCollectionFromTemplate,
  useDeleteCollection,
  useRenameCollection
} from '../hooks/useCollections';
import {useCollectionsPermissions} from '../hooks/useAcl';
import type {RootState} from '../store/store';
import type {CollectionDto} from '../types/api';

export default function CollectionsPage() {
  const navigate = useNavigate();
  const currentUser = useSelector((s: RootState) => s.auth.user);
  const isAdmin = currentUser?.role === 'ADMIN';
  const {data: collectionsPerms} = useCollectionsPermissions();
  const canMarkTemplate = isAdmin || (collectionsPerms?.permissions.includes('MANAGE_TEMPLATES') ?? false);
  const {data: collections = [], isLoading} = useCollections();
  const {data: templates = []} = useCollectionTemplates();
  const createMutation = useCreateCollection();
  const createFromTemplateMutation = useCreateCollectionFromTemplate();
  const renameMutation = useRenameCollection();
  const deleteMutation = useDeleteCollection();
  const copyMutation = useCopyCollection();

  const [createOpen, setCreateOpen] = useState(false);
  const [newName, setNewName] = useState('');
  const [markAsTemplate, setMarkAsTemplate] = useState(false);
  const [selectedTemplateId, setSelectedTemplateId] = useState<number | null>(null);
  const [renameTarget, setRenameTarget] = useState<CollectionDto | null>(null);
  const [renameName, setRenameName] = useState('');
  const [renameAsTemplate, setRenameAsTemplate] = useState(false);
  const [deleteTarget, setDeleteTarget] = useState<CollectionDto | null>(null);
  const [shareTarget, setShareTarget] = useState<CollectionDto | null>(null);
  const [copyTarget, setCopyTarget] = useState<CollectionDto | null>(null);
  const [copyName, setCopyName] = useState('');

  const isDuplicateName = (name: string, excludeId?: number) =>
      collections.some(c =>
          c.name.toLowerCase() === name.trim().toLowerCase() && c.id !== excludeId
      );

  const resetCreateDialog = () => {
    setCreateOpen(false);
    setNewName('');
    setMarkAsTemplate(false);
    setSelectedTemplateId(null);
  };

  const handleCreate = () => {
    if (!newName.trim() || isDuplicateName(newName)) return;
    if (selectedTemplateId !== null) {
      createFromTemplateMutation.mutate(
          {templateId: selectedTemplateId, name: newName.trim()},
          {onSuccess: resetCreateDialog}
      );
    } else {
      createMutation.mutate(
          {name: newName.trim(), isTemplate: markAsTemplate},
          {onSuccess: resetCreateDialog}
      );
    }
  };

  const closeRenameDialog = () => {
    setRenameTarget(null);
    setRenameName('');
    setRenameAsTemplate(false);
  };

  const handleRename = () => {
    if (!renameTarget || !renameName.trim() || isDuplicateName(renameName, renameTarget.id)) return;
    renameMutation.mutate(
        {
          id: renameTarget.id,
          req: {name: renameName.trim(), isTemplate: canMarkTemplate ? renameAsTemplate : undefined}
        },
        {onSuccess: closeRenameDialog}
    );
  };

  const handleDelete = () => {
    if (!deleteTarget) return;
    deleteMutation.mutate(deleteTarget.id, {onSuccess: () => setDeleteTarget(null)});
  };

  const handleCopy = () => {
    if (!copyTarget || !copyName.trim() || isDuplicateName(copyName)) return;
    copyMutation.mutate(
        {id: copyTarget.id, name: copyName.trim()},
        {
          onSuccess: () => {
            setCopyTarget(null);
            setCopyName('');
          }
        }
    );
  };

  return (
      <AppShell>
        <Box sx={{p: 3}}>
          <Box sx={{display: 'flex', alignItems: 'center', mb: 1, gap: 2}}>
            <CollectionsBookmarkIcon color="primary"/>
            <Typography variant="h5" fontWeight="bold">Meine Sammlungen</Typography>
            <Box sx={{flexGrow: 1}}/>
            <Button variant="contained" startIcon={<AddIcon/>} onClick={() => setCreateOpen(true)}>
              Neue Sammlung
            </Button>
          </Box>
          <Alert severity="info" sx={{mb: 3}}>
            Sammlungen ermöglichen es, Dateien aus verschiedenen Ordnern thematisch zu gruppieren —
            ohne sie zu verschieben.
            Zusätzlich können Erfahrungsberichte hinzugefügt werden, um Inhalte mit persönlichen
            Eindrücken zu ergänzen.
            Über einen Freigabe-Link lässt sich eine Sammlung mit Externen teilen, ohne dass diese
            einen Login benötigen.
            Nur du siehst deine eigenen Sammlungen. Beim Anlegen kannst du eine vom Administrator
            bereitgestellte Vorlage verwenden,
            um eine Sammlung mit vordefinierten Inhalten zu starten.
          </Alert>

          {isLoading && <CircularProgress/>}

          {!isLoading && collections.length === 0 && (
              <Typography color="text.secondary">
                Noch keine Sammlungen. Lege eine neue an!
              </Typography>
          )}

          <Grid2 container spacing={2}>
            {collections.map(c => (
                <Grid2 key={c.id} size={{xs: 12, sm: 6, md: 4, lg: 3}}>
                  <Card variant="outlined">
                    <CardActionArea onClick={() => navigate(`/collections/${c.id}`)}>
                      <CardContent sx={{pb: 0.5}}>
                        <Typography
                            variant="subtitle1"
                            fontWeight="medium"
                            sx={{
                              display: '-webkit-box',
                              WebkitLineClamp: 2,
                              WebkitBoxOrient: 'vertical',
                              overflow: 'hidden',
                            }}
                        >
                          {c.name}
                        </Typography>
                      </CardContent>
                    </CardActionArea>

                    <Box sx={{
                      display: 'flex',
                      alignItems: 'center',
                      justifyContent: 'space-between',
                      px: 2,
                      minHeight: 32
                    }}>
                      <Box>
                        {c.isTemplate &&
                            <Chip label="Vorlage" size="small" color="primary" variant="outlined"/>}
                      </Box>
                      <Box sx={{display: 'flex'}}>
                        <Tooltip title="Bearbeiten">
                          <IconButton size="small" onClick={() => {
                            setRenameTarget(c);
                            setRenameName(c.name);
                            setRenameAsTemplate(c.isTemplate);
                          }}>
                            <EditIcon fontSize="small"/>
                          </IconButton>
                        </Tooltip>
                        <Tooltip title="Kopieren">
                          <IconButton size="small" onClick={() => {
                            setCopyTarget(c);
                            setCopyName(`${c.name} (Kopie)`);
                          }}>
                            <ContentCopyIcon fontSize="small"/>
                          </IconButton>
                        </Tooltip>
                        {(c.itemCount > 0 || c.testimonialCount > 0) && (
                            <Tooltip title="Freigabe-Link erstellen">
                              <IconButton size="small" onClick={() => setShareTarget(c)}>
                                <ShareIcon fontSize="small"/>
                              </IconButton>
                            </Tooltip>
                        )}
                        <Tooltip title="Löschen">
                          <IconButton size="small" onClick={() => setDeleteTarget(c)}>
                            <DeleteIcon fontSize="small"/>
                          </IconButton>
                        </Tooltip>
                      </Box>
                    </Box>

                    <CardActionArea onClick={() => navigate(`/collections/${c.id}`)}>
                      <CardContent sx={{pt: 0.5}}>
                        <Typography variant="caption" color="text.secondary">
                          {c.itemCount === 0 && c.testimonialCount === 0
                              ? 'Leere Sammlung'
                              : <>
                                {c.itemCount} {c.itemCount === 1 ? 'Ressource' : 'Ressourcen'}
                                {c.testimonialCount > 0 && (
                                    <> · {c.testimonialCount} {c.testimonialCount === 1 ? 'Erfahrungsbericht' : 'Erfahrungsberichte'}</>
                                )}
                              </>
                          }
                        </Typography>
                      </CardContent>
                    </CardActionArea>
                  </Card>
                </Grid2>
            ))}
          </Grid2>

          {/* Dialog: Neue Sammlung */}
          <Dialog open={createOpen} onClose={resetCreateDialog} maxWidth="xs" fullWidth>
            <DialogTitle>Neue Sammlung</DialogTitle>
            <DialogContent>
              <TextField autoFocus fullWidth label="Name" value={newName}
                         onChange={e => setNewName(e.target.value)}
                         onKeyDown={e => e.key === 'Enter' && handleCreate()}
                         error={!!newName.trim() && isDuplicateName(newName)}
                         helperText={newName.trim() && isDuplicateName(newName) ? 'Dieser Name ist bereits vergeben' : ''}
                         sx={{mt: 1}}/>

              {canMarkTemplate && selectedTemplateId === null && (
                  <FormControlLabel
                      sx={{mt: 1}}
                      control={
                        <Checkbox checked={markAsTemplate}
                                  onChange={e => setMarkAsTemplate(e.target.checked)}/>
                      }
                      label="Als Vorlage markieren"
                  />
              )}

              {templates.length > 0 && (
                  <>
                    <Divider sx={{mt: 2, mb: 1}}/>
                    <Typography variant="caption" color="text.secondary">
                      Aus Vorlage erstellen (optional)
                    </Typography>
                    <List dense>
                      {templates.map(t => (
                          <ListItemButton
                              key={t.id}
                              selected={selectedTemplateId === t.id}
                              onClick={() => {
                                if (selectedTemplateId === t.id) {
                                  setSelectedTemplateId(null);
                                  setNewName('');
                                } else {
                                  setSelectedTemplateId(t.id);
                                  setNewName(t.name);
                                }
                              }}
                          >
                            <ListItemText
                                primary={t.name}
                                secondary={`${t.itemCount} Ressourcen · ${t.testimonialCount} Erfahrungsberichte`}
                            />
                          </ListItemButton>
                      ))}
                    </List>
                  </>
              )}
            </DialogContent>
            <DialogActions>
              <Button onClick={resetCreateDialog}>Abbrechen</Button>
              <Button variant="contained" onClick={handleCreate}
                      disabled={!newName.trim() || isDuplicateName(newName)}>
                Anlegen
              </Button>
            </DialogActions>
          </Dialog>

          {/* Dialog: Bearbeiten */}
          <Dialog open={!!renameTarget} onClose={closeRenameDialog} maxWidth="xs" fullWidth>
            <DialogTitle>Sammlung bearbeiten</DialogTitle>
            <DialogContent>
              <TextField autoFocus fullWidth label="Neuer Name" value={renameName}
                         onChange={e => setRenameName(e.target.value)}
                         onKeyDown={e => e.key === 'Enter' && handleRename()}
                         error={!!renameName.trim() && isDuplicateName(renameName, renameTarget?.id)}
                         helperText={renameName.trim() && isDuplicateName(renameName, renameTarget?.id) ? 'Dieser Name ist bereits vergeben' : ''}
                         sx={{mt: 1}}/>

              {canMarkTemplate && (
                  <FormControlLabel
                      sx={{mt: 1}}
                      control={
                        <Checkbox checked={renameAsTemplate}
                                  onChange={e => setRenameAsTemplate(e.target.checked)}/>
                      }
                      label="Als Vorlage markieren"
                  />
              )}
            </DialogContent>
            <DialogActions>
              <Button onClick={closeRenameDialog}>Abbrechen</Button>
              <Button variant="contained" onClick={handleRename}
                      disabled={!renameName.trim() || isDuplicateName(renameName, renameTarget?.id)}>
                Speichern
              </Button>
            </DialogActions>
          </Dialog>

          {/* Dialog: Kopieren */}
          <Dialog open={!!copyTarget} onClose={() => {
            setCopyTarget(null);
            setCopyName('');
          }} maxWidth="xs" fullWidth>
            <DialogTitle>Sammlung kopieren</DialogTitle>
            <DialogContent>
              <TextField autoFocus fullWidth label="Name der Kopie" value={copyName}
                         onChange={e => setCopyName(e.target.value)}
                         onKeyDown={e => e.key === 'Enter' && handleCopy()}
                         error={!!copyName.trim() && isDuplicateName(copyName)}
                         helperText={copyName.trim() && isDuplicateName(copyName) ? 'Dieser Name ist bereits vergeben' : ''}
                         sx={{mt: 1}}/>
            </DialogContent>
            <DialogActions>
              <Button onClick={() => {
                setCopyTarget(null);
                setCopyName('');
              }}>Abbrechen</Button>
              <Button variant="contained" onClick={handleCopy}
                      disabled={!copyName.trim() || isDuplicateName(copyName)}>
                Kopieren
              </Button>
            </DialogActions>
          </Dialog>

          {/* Dialog: Löschen */}
          <Dialog open={!!deleteTarget} onClose={() => setDeleteTarget(null)}>
            <DialogTitle>Sammlung löschen?</DialogTitle>
            <DialogContent>
              <Typography>„{deleteTarget?.name}" wird unwiderruflich gelöscht.</Typography>
            </DialogContent>
            <DialogActions>
              <Button onClick={() => setDeleteTarget(null)}>Abbrechen</Button>
              <Button color="error" variant="contained" onClick={handleDelete}>Löschen</Button>
            </DialogActions>
          </Dialog>
          {shareTarget && (
              <CollectionShareDialog
                  collection={shareTarget}
                  onClose={() => setShareTarget(null)}
              />
          )}
        </Box>
      </AppShell>
  );
}
