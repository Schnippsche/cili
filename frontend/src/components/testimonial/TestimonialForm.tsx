import {
  Alert,
  Box,
  Button,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  IconButton,
  Stack,
  TextField,
  ToggleButton,
  ToggleButtonGroup,
  Tooltip,
  Typography,
} from '@mui/material';
import AddPhotoAlternateIcon from '@mui/icons-material/AddPhotoAlternate';
import CloseIcon from '@mui/icons-material/Close';
import {type ChangeEvent, useEffect, useRef, useState} from 'react';
import type {TestimonialDto, TestimonialImageDto} from '../../types/api';
import type {TestimonialFormData} from '../../api/testimonials';
import {getThumbnailUrl} from '../../api/resources';
import {useAuthenticatedUrl} from '../../hooks/useAuthenticatedUrl';

interface ExistingImageThumbProps {
  image: TestimonialImageDto;
  onRemove: () => void;
}

function ExistingImageThumb({image, onRemove}: Readonly<ExistingImageThumbProps>) {
  const url = useAuthenticatedUrl(getThumbnailUrl(image.id, 'small'));
  return (
      <Box sx={{position: 'relative', width: 72, height: 72}}>
        <Box component="img" src={url ?? undefined} alt={image.originalName}
             sx={{
               width: 72,
               height: 72,
               objectFit: 'cover',
               borderRadius: 1,
               bgcolor: 'action.hover'
             }}/>
        <Tooltip title="Bild entfernen">
          <IconButton size="small" onClick={onRemove}
                      sx={{
                        position: 'absolute',
                        top: -8,
                        right: -8,
                        bgcolor: 'background.paper',
                        p: 0.25,
                        border: '1px solid',
                        borderColor: 'divider',
                        '&:hover': {bgcolor: 'error.light', color: 'white'}
                      }}>
            <CloseIcon sx={{fontSize: 14}}/>
          </IconButton>
        </Tooltip>
      </Box>
  );
}

interface Props {
  open: boolean;
  initial?: TestimonialDto | null;
  onSave: (data: TestimonialFormData) => Promise<void>;
  onClose: () => void;
}

export default function TestimonialForm({open, initial, onSave, onClose}: Readonly<Props>) {
  const [authorName, setAuthorName] = useState('');
  const [tags, setTags] = useState('');
  const [text, setText] = useState('');
  const [source, setSource] = useState<'Mensch' | 'Tier' | ''>('');
  const [newFiles, setNewFiles] = useState<File[]>([]);
  const [newPreviews, setNewPreviews] = useState<string[]>([]);
  const [deleteImageIds, setDeleteImageIds] = useState<number[]>([]);
  const [saving, setSaving] = useState(false);
  const [errors, setErrors] = useState<{ authorName?: string; tags?: string; text?: string; source?: string }>({});
  const [saveError, setSaveError] = useState<string | null>(null);
  const fileInputRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    if (open) {
      setAuthorName(initial?.authorName ?? '');
      setTags(initial?.tags ?? '');
      setText(initial?.text ?? '');
      setSource((initial?.source as 'Mensch' | 'Tier' | undefined) ?? '');
      setNewFiles([]);
      setNewPreviews([]);
      setDeleteImageIds([]);
      setErrors({});
      setSaveError(null);
    }
  }, [open, initial]);

  useEffect(() => {
    newPreviews.forEach(URL.revokeObjectURL);
    const urls = newFiles.map(f => URL.createObjectURL(f));
    setNewPreviews(urls);
    return () => urls.forEach(URL.revokeObjectURL);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [newFiles]);

  function validate(): boolean {
    const e: typeof errors = {};
    if (!authorName.trim()) e.authorName = 'Name ist erforderlich';
    else if (authorName.trim().length > 200) e.authorName = 'Maximal 200 Zeichen';
    if (tags.trim().length > 500) e.tags = 'Maximal 500 Zeichen';
    if (!text.trim() || text.trim().length < 10) e.text = 'Mindestens 10 Zeichen';
    else if (text.trim().length > 5000) e.text = 'Maximal 5000 Zeichen';
    if (!source) e.source = 'Bitte Mensch oder Tier auswählen';
    setErrors(e);
    return Object.keys(e).length === 0;
  }

  async function handleSave() {
    if (!validate()) return;
    setSaving(true);
    setSaveError(null);
    try {
      await onSave({
        authorName: authorName.trim(),
        tags: tags.trim() || null,
        text: text.trim(),
        source: source as 'Mensch' | 'Tier',
        images: newFiles,
        deleteImageIds,
      });
      onClose();
    } catch (err: unknown) {
      const axiosMsg = (err as { response?: { data?: { message?: string } } })?.response?.data?.message;
      setSaveError(axiosMsg ?? 'Beim Speichern ist ein Fehler aufgetreten.');
    } finally {
      setSaving(false);
    }
  }

  function handleFileChange(e: ChangeEvent<HTMLInputElement>) {
    const files = Array.from(e.target.files ?? []).filter(f => f.type.startsWith('image/'));
    setNewFiles(prev => [...prev, ...files]);
    if (fileInputRef.current) fileInputRef.current.value = '';
  }

  function removeNewFile(idx: number) {
    setNewFiles(prev => prev.filter((_, i) => i !== idx));
  }

  function markImageForDelete(imageId: number) {
    setDeleteImageIds(prev => [...prev, imageId]);
  }

  const existingImages = (initial?.images ?? []).filter(img => !deleteImageIds.includes(img.id));

  return (
      <Dialog open={open} onClose={onClose} fullWidth maxWidth="sm">
        <DialogTitle>{initial ? 'Testimonial bearbeiten' : 'Neues Testimonial'}</DialogTitle>
        <DialogContent>
          <TextField
              label="Name" value={authorName} onChange={e => setAuthorName(e.target.value)}
              error={!!errors.authorName} helperText={errors.authorName}
              fullWidth sx={{mt: 1, mb: 2}}
          />
          <ToggleButtonGroup
              value={source}
              exclusive
              onChange={(_, val) => val && setSource(val)}
              size="small"
              sx={{mb: 2}}
          >
            <ToggleButton value="Mensch">Mensch</ToggleButton>
            <ToggleButton value="Tier">Tier</ToggleButton>
          </ToggleButtonGroup>
          {errors.source && (
              <Typography color="error" variant="caption" sx={{display: 'block', mt: -1.5, mb: 1.5}}>
                {errors.source}
              </Typography>
          )}

          <TextField
              label="Tags (kommagetrennt)" value={tags} onChange={e => setTags(e.target.value)}
              error={!!errors.tags}
              helperText={errors.tags ?? 'z. B. Produkt, Ergebnis, Empfehlung'}
              fullWidth sx={{mb: 2}}
          />

          {/* Image section */}
          <Box sx={{mb: 2}}>
            <Stack direction="row" alignItems="center" gap={1} sx={{mb: 1}}>
              <Typography variant="caption" color="text.secondary">Bilder</Typography>
              <Tooltip title="Bilder hinzufügen (JPG, PNG, GIF, WebP, BMP)">
                <IconButton size="small" onClick={() => fileInputRef.current?.click()}>
                  <AddPhotoAlternateIcon fontSize="small"/>
                </IconButton>
              </Tooltip>
            </Stack>
            <input ref={fileInputRef} type="file" accept="image/*" multiple hidden
                   onChange={handleFileChange}/>
            {(existingImages.length > 0 || newFiles.length > 0) && (
                <Stack direction="row" flexWrap="wrap" gap={1}>
                  {existingImages.map(img => (
                      <ExistingImageThumb
                          key={img.id} image={img}
                          onRemove={() => markImageForDelete(img.id)}
                      />
                  ))}
                  {newFiles.map((file, idx) => (
                      <Box key={`${file.name}-${file.size}-${file.lastModified}`}
                           sx={{position: 'relative', width: 72, height: 72}}>
                        <Box component="img" src={newPreviews[idx]} alt={`Neu ${idx + 1}`}
                             sx={{width: 72, height: 72, objectFit: 'cover', borderRadius: 1}}/>
                        <Tooltip title="Bild entfernen">
                          <IconButton size="small" onClick={() => removeNewFile(idx)}
                                      sx={{
                                        position: 'absolute',
                                        top: -8,
                                        right: -8,
                                        bgcolor: 'background.paper',
                                        p: 0.25,
                                        border: '1px solid',
                                        borderColor: 'divider',
                                        '&:hover': {bgcolor: 'error.light', color: 'white'}
                                      }}>
                            <CloseIcon sx={{fontSize: 14}}/>
                          </IconButton>
                        </Tooltip>
                      </Box>
                  ))}
                </Stack>
            )}
          </Box>

          <TextField
              label="Testimonial" value={text} onChange={e => setText(e.target.value)}
              error={!!errors.text} helperText={errors.text}
              multiline minRows={5} maxRows={15} fullWidth
          />

          {saveError && (
              <Alert severity="error" sx={{mt: 2}}>{saveError}</Alert>
          )}
        </DialogContent>
        <DialogActions>
          <Button onClick={onClose} disabled={saving}>Abbrechen</Button>
          <Button onClick={handleSave} variant="contained" disabled={saving}>
            {saving ? 'Wird gespeichert…' : 'Speichern'}
          </Button>
        </DialogActions>
      </Dialog>
  );
}
