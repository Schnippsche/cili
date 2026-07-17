import { useEffect, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { Box, Button, CircularProgress, TextField, Typography } from '@mui/material';
import AppShell from '../components/layout/AppShell';
import { useResource, useResourceContent, useSaveVersion } from '../hooks/useResources';

export default function TextEditorPage() {
  const { resourceId } = useParams<{ resourceId: string }>();
  const id = Number(resourceId);
  const navigate = useNavigate();
  const { data: resource }         = useResource(id);
  const { data: content, isLoading } = useResourceContent(id);
  const saveVersion                = useSaveVersion();
  const [text, setText]            = useState('');

  useEffect(() => { if (content != null) setText(content); }, [content]);

  const handleSave = async () => {
    await saveVersion.mutateAsync({ id, content: text });
    navigate(-1);
  };

  return (
    <AppShell>
      <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 2 }}>
        <Typography variant="h6">{resource?.originalName ?? 'Texteditor'}</Typography>
        <Box sx={{ display: 'flex', gap: 1 }}>
          <Button variant="outlined" onClick={() => navigate(-1)}>Abbrechen</Button>
          <Button variant="contained" onClick={handleSave} disabled={saveVersion.isPending}>Speichern</Button>
        </Box>
      </Box>
      {isLoading ? <CircularProgress /> : (
        <TextField value={text} onChange={e => setText(e.target.value)} fullWidth multiline minRows={20}
          slotProps={{ htmlInput: { style: { fontFamily: 'monospace', fontSize: 14 } } }} />
      )}
    </AppShell>
  );
}
