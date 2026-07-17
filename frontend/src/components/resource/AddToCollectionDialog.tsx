import { useState } from 'react';
import { useSelector } from 'react-redux';
import {
  Button, Checkbox, Dialog, DialogActions, DialogContent, DialogTitle,
  Divider, FormControlLabel, List, ListItemButton, ListItemText, TextField, Typography
} from '@mui/material';
import AddIcon from '@mui/icons-material/Add';
import { useCollections, useCreateCollection, useAddToCollection, useAddTestimonialToCollection } from '../../hooks/useCollections';
import { useCollectionsPermissions } from '../../hooks/useAcl';
import type { RootState } from '../../store/store';

interface Props {
  open: boolean;
  itemId: number;
  itemType: 'resource' | 'testimonial';
  onClose: () => void;
}

export default function AddToCollectionDialog({ open, itemId, itemType, onClose }: Readonly<Props>) {
  const isAdmin = useSelector((s: RootState) => s.auth.user?.role === 'ADMIN');
  const { data: collectionsPerms } = useCollectionsPermissions();
  const canMarkTemplate = isAdmin || (collectionsPerms?.permissions.includes('MANAGE_TEMPLATES') ?? false);
  const { data: collections = [] } = useCollections();
  const addResourceMutation    = useAddToCollection();
  const addTestimonialMutation = useAddTestimonialToCollection();
  const createMutation = useCreateCollection();
  const [newName, setNewName]           = useState('');
  const [showCreate, setShowCreate]     = useState(false);
  const [markAsTemplate, setMarkAsTemplate] = useState(false);

  const addToCollection = (collectionId: number, onSuccess: () => void) => {
    if (itemType === 'resource') {
      addResourceMutation.mutate({ collectionId, req: { resourceId: itemId } }, { onSuccess });
    } else {
      addTestimonialMutation.mutate({ collectionId, req: { testimonialId: itemId } }, { onSuccess });
    }
  };

  const handleAdd = (collectionId: number) => {
    addToCollection(collectionId, onClose);
  };

  const handleCreate = () => {
    if (!newName.trim()) return;
    createMutation.mutate({ name: newName.trim(), isTemplate: markAsTemplate }, {
      onSuccess: (created) => {
        addToCollection(created.id, onClose);
      }
    });
  };

  const handleClose = () => {
    setShowCreate(false);
    setNewName('');
    setMarkAsTemplate(false);
    onClose();
  };

  return (
    <Dialog open={open} onClose={handleClose} maxWidth="xs" fullWidth>
      <DialogTitle>Zu Sammlung hinzufügen</DialogTitle>
      <DialogContent sx={{ p: 0 }}>
        {collections.length === 0 && !showCreate && (
          <Typography sx={{ p: 2 }} color="text.secondary">
            Noch keine Sammlungen vorhanden.
          </Typography>
        )}
        <List dense>
          {collections.map(c => (
            <ListItemButton key={c.id} onClick={() => handleAdd(c.id)}>
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
      </DialogContent>
      <DialogActions>
        <Button onClick={handleClose}>Abbrechen</Button>
        {showCreate
          ? <Button variant="contained" onClick={handleCreate} disabled={!newName.trim()}>
              Anlegen & hinzufügen
            </Button>
          : <Button startIcon={<AddIcon />} onClick={() => setShowCreate(true)}>
              Neue Sammlung
            </Button>
        }
      </DialogActions>
    </Dialog>
  );
}
