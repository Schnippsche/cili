import { Box, Button, Drawer, TextField, Toolbar, Typography } from '@mui/material';
import { useEffect, useState } from 'react';
import type { ResourceDto } from '../../types/api';
import { useUpdateMetadata } from '../../hooks/useResources';

interface Props { resource: ResourceDto; open: boolean; onClose: () => void; readonly?: boolean; }

export default function MetadataPanel({ resource, open, onClose, readonly = false }: Readonly<Props>) {
  const update = useUpdateMetadata();
  const [form, setForm] = useState({ title: '', description: '', tags: '', language: '' });

  useEffect(() => {
    if (open) setForm({
      title:       resource.metadata?.title       ?? '',
      description: resource.metadata?.description ?? '',
      tags:        resource.metadata?.tags        ?? '',
      language:    resource.metadata?.language    ?? 'Deutsch',
    });
  }, [open, resource]);

  const handleSave = async () => {
    await update.mutateAsync({ id: resource.id, req: form });
    onClose();
  };

  return (
    <Drawer anchor="right" open={open} onClose={onClose} slotProps={{ paper: { sx: { width: 400, p: 3, overflowY: 'auto' } } }}>
      <Toolbar />
      <Typography variant="h6" gutterBottom>{readonly ? 'Metadaten ansehen' : 'Metadaten bearbeiten'}</Typography>
      <Typography variant="caption" color="text.secondary" sx={{ mb: 2, display: 'block' }}>{resource.originalName}</Typography>
      <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
        <TextField label="Titel" fullWidth value={form.title} onChange={e => setForm({ ...form, title: e.target.value })} disabled={readonly} />
        <TextField label="Beschreibung" fullWidth multiline rows={3} value={form.description} onChange={e => setForm({ ...form, description: e.target.value })} disabled={readonly} />
        <TextField label="Tags (kommagetrennt)" fullWidth value={form.tags} onChange={e => setForm({ ...form, tags: e.target.value })} disabled={readonly} />
        <TextField label="Sprache" fullWidth value={form.language} onChange={e => setForm({ ...form, language: e.target.value })} disabled={readonly} />
        <Box sx={{ display: 'flex', gap: 1, justifyContent: 'flex-end' }}>
          <Button onClick={onClose}>{readonly ? 'Schließen' : 'Abbrechen'}</Button>
          {!readonly && <Button variant="contained" onClick={handleSave} disabled={update.isPending}>Speichern</Button>}
        </Box>
      </Box>
    </Drawer>
  );
}
