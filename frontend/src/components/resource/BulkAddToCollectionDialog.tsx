import { useState } from 'react';
import { useSelector } from 'react-redux';
import {
  Button, Checkbox, CircularProgress, Dialog, DialogActions, DialogContent, DialogTitle,
  Divider, FormControlLabel, List, ListItemButton, ListItemText, TextField, Typography,
} from '@mui/material';
import AddIcon from '@mui/icons-material/Add';
import { useQueryClient } from '@tanstack/react-query';
import { useCollections, useCreateCollection } from '../../hooks/useCollections';
import { useCollectionsPermissions } from '../../hooks/useAcl';
import { addToCollection, addTestimonialToCollection } from '../../api/collections';
import type { RootState } from '../../store/store';

interface Props {
  open: boolean;
  resourceIds: number[];
  testimonialIds: number[];
  onClose: () => void;
  defaultName?: string;
}

export default function BulkAddToCollectionDialog({ open, resourceIds, testimonialIds, onClose, defaultName }: Readonly<Props>) {
  const isAdmin = useSelector((s: RootState) => s.auth.user?.role === 'ADMIN');
  const { data: collectionsPerms } = useCollectionsPermissions();
  const canMarkTemplate = isAdmin || (collectionsPerms?.permissions.includes('MANAGE_TEMPLATES') ?? false);
  const qc = useQueryClient();
  const { data: collections = [] } = useCollections();
  const createMutation = useCreateCollection();
  const [newName, setNewName]           = useState(defaultName ?? '');
  const [showCreate, setShowCreate]     = useState(false);
  const [loading, setLoading]           = useState(false);
  const [markAsTemplate, setMarkAsTemplate] = useState(false);

  const addAll = async (collectionId: number) => {
    setLoading(true);
    await Promise.all([
      ...resourceIds.map(id => addToCollection(collectionId, { resourceId: id })),
      ...testimonialIds.map(id => addTestimonialToCollection(collectionId, { testimonialId: id })),
    ]);
    await qc.invalidateQueries({ queryKey: ['collections'] });
    setLoading(false);
    handleClose();
  };

  const handleCreate = () => {
    if (!newName.trim()) return;
    createMutation.mutate({ name: newName.trim(), isTemplate: markAsTemplate }, {
      onSuccess: (created) => addAll(created.id),
    });
  };

  const handleClose = () => {
    setShowCreate(false);
    setNewName('');
    setMarkAsTemplate(false);
    onClose();
  };

  const total = resourceIds.length + testimonialIds.length;

  return (
    <Dialog open={open} onClose={loading ? undefined : handleClose} maxWidth="xs" fullWidth>
      <DialogTitle>Alle Treffer zur Sammlung hinzufügen</DialogTitle>
      <DialogContent sx={{ p: 0 }}>
        <Typography variant="body2" color="text.secondary" sx={{ px: 2, pt: 1, pb: 0.5 }}>
          {total} {total === 1 ? 'Eintrag' : 'Einträge'} werden der gewählten Sammlung hinzugefügt.
        </Typography>
        {loading ? (
          <CircularProgress size={24} sx={{ display: 'block', mx: 'auto', my: 2 }} />
        ) : (
          <>
            {collections.length === 0 && !showCreate && (
              <Typography sx={{ px: 2, pb: 1 }} color="text.secondary">
                Noch keine Sammlungen vorhanden.
              </Typography>
            )}
            <List dense>
              {collections.map(c => (
                <ListItemButton key={c.id} onClick={() => addAll(c.id)}>
                  <ListItemText
                    primary={c.name}
                    secondary={
                      `${c.itemCount} ${c.itemCount === 1 ? 'Ressource' : 'Ressourcen'}` +
                      (c.testimonialCount > 0
                        ? ` · ${c.testimonialCount} ${c.testimonialCount === 1 ? 'Erfahrungsbericht' : 'Erfahrungsberichte'}`
                        : '')
                    }
                  />
                </ListItemButton>
              ))}
            </List>
            {showCreate && (
              <>
                <Divider />
                <TextField
                  autoFocus fullWidth size="small" label="Name der neuen Sammlung"
                  value={newName} onChange={e => setNewName(e.target.value)}
                  sx={{ mx: 2, my: 1, width: 'calc(100% - 32px)' }}
                  onKeyDown={e => e.key === 'Enter' && handleCreate()}
                />
                {canMarkTemplate && (
                  <FormControlLabel
                    sx={{ mx: 2, mb: 1 }}
                    control={
                      <Checkbox checked={markAsTemplate} onChange={e => setMarkAsTemplate(e.target.checked)} />
                    }
                    label="Als Vorlage markieren"
                  />
                )}
              </>
            )}
          </>
        )}
      </DialogContent>
      <DialogActions>
        <Button onClick={handleClose} disabled={loading}>Abbrechen</Button>
        {!loading && (showCreate
          ? <Button variant="contained" onClick={handleCreate} disabled={!newName.trim()}>
              Anlegen & hinzufügen
            </Button>
          : <Button startIcon={<AddIcon />} onClick={() => setShowCreate(true)}>
              Neue Sammlung
            </Button>
        )}
      </DialogActions>
    </Dialog>
  );
}
