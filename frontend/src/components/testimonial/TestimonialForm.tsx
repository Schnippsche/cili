import {
  Alert,
  Box,
  Button,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  IconButton,
  LinearProgress,
  Stack,
  TextField,
  ToggleButton,
  ToggleButtonGroup,
  Tooltip,
  Typography,
} from '@mui/material';
import AddPhotoAlternateIcon from '@mui/icons-material/AddPhotoAlternate';
import AddIcon from '@mui/icons-material/Add';
import CloseIcon from '@mui/icons-material/Close';
import PlayArrowIcon from '@mui/icons-material/PlayArrow';
import MusicNoteIcon from '@mui/icons-material/MusicNote';
import {type ChangeEvent, useEffect, useRef, useState} from 'react';
import { useQueryClient } from '@tanstack/react-query';
import type {TestimonialDto, TestimonialAttachmentDto} from '../../types/api';
import type {TestimonialFormData} from '../../api/testimonials';
import {getThumbnailUrl} from '../../api/resources';
import {useAuthenticatedUrl} from '../../hooks/useAuthenticatedUrl';
import {completeUpload, initUpload, uploadChunk} from '../../api/upload';

interface ExistingAttachmentThumbProps {
  attachment: TestimonialAttachmentDto;
  onRemove: () => void;
}

function ExistingAttachmentThumb({attachment, onRemove}: Readonly<ExistingAttachmentThumbProps>) {
  const isImage = attachment.mimeType?.startsWith('image/');
  const isVideo = attachment.mimeType?.startsWith('video/');
  const isAudio = attachment.mimeType?.startsWith('audio/');
  // Video-Poster erst laden wenn DONE — storedName als Cache-Busting-Parameter sorgt dafür,
  // dass die URL sich beim Übergang PENDING→DONE ändert und useAuthenticatedUrl neu fetcht.
  const thumbSrc = isImage || attachment.thumbnailStatus === 'DONE'
    ? getThumbnailUrl(attachment.id, 'small', attachment.thumbnailStatus === 'DONE' ? attachment.storedName ?? undefined : undefined)
    : null;
  const url = useAuthenticatedUrl(thumbSrc, isImage ? 3 : 0);

  return (
      <Box sx={{position: 'relative', width: 72, height: 72}}>
        {isImage && (
          <Box component="img" src={url ?? undefined} alt={attachment.originalName}
               sx={{
                 width: 72,
                 height: 72,
                 objectFit: 'cover',
                 borderRadius: 1,
                 bgcolor: 'action.hover'
               }}/>
        )}
        {isVideo && (
          <>
            {url && (
              <Box component="img" src={url} alt={attachment.originalName}
                   sx={{
                     width: 72,
                     height: 72,
                     objectFit: 'cover',
                     borderRadius: 1,
                     bgcolor: 'action.hover'
                   }}/>
            )}
            <Box sx={{
              position: 'absolute',
              inset: 0,
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              bgcolor: 'rgba(0,0,0,0.3)',
              borderRadius: 1,
            }}>
              <PlayArrowIcon sx={{ fontSize: 32, color: 'white' }} />
            </Box>
          </>
        )}
        {isAudio && (
          <Box sx={{
            width: 72,
            height: 72,
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            bgcolor: 'action.hover',
            borderRadius: 1,
          }}>
            <MusicNoteIcon sx={{ fontSize: 40, color: 'text.secondary' }} />
          </Box>
        )}
        <Tooltip title="Entfernen">
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
  onSave: (data: TestimonialFormData) => Promise<TestimonialDto>;
  onClose: () => void;
}

const CHUNK_SIZE = 5 * 1024 * 1024;

export interface MediaUploadState {
  progress: number;
  status: 'uploading' | 'done' | 'error';
  error?: string;
}

export default function TestimonialForm({open, initial, onSave, onClose}: Readonly<Props>) {
  const qc = useQueryClient();
  const [authorName, setAuthorName] = useState('');
  const [tags, setTags] = useState('');
  const [text, setText] = useState('');
  const [source, setSource] = useState<'Mensch' | 'Tier' | ''>('');
  const [newFiles, setNewFiles] = useState<File[]>([]);
  const [newPreviews, setNewPreviews] = useState<string[]>([]);
  const [newMediaFiles, setNewMediaFiles] = useState<File[]>([]);
  const [deleteAttachmentIds, setDeleteAttachmentIds] = useState<number[]>([]);
  const [saving, setSaving] = useState(false);
  const [errors, setErrors] = useState<{ authorName?: string; tags?: string; text?: string; source?: string }>({});
  const [saveError, setSaveError] = useState<string | null>(null);
  const [mediaUploads, setMediaUploads] = useState<Map<string, MediaUploadState>>(new Map());
  const fileInputRef = useRef<HTMLInputElement>(null);
  const mediaInputRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    if (open) {
      setAuthorName(initial?.authorName ?? '');
      setTags(initial?.tags ?? '');
      setText(initial?.text ?? '');
      setSource((initial?.source as 'Mensch' | 'Tier' | undefined) ?? '');
      setNewFiles([]);
      setNewPreviews([]);
      setNewMediaFiles([]);
      setDeleteAttachmentIds([]);
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

  const updateMediaUpload = (fileName: string, patch: Partial<MediaUploadState>) =>
    setMediaUploads(prev => {
      const copy = new Map(prev);
      const state = copy.get(fileName) ?? { progress: 0, status: 'uploading' as const };
      copy.set(fileName, { ...state, ...patch });
      return copy;
    });

  const uploadMediaFile = async (testimonialId: number, file: File) => {
    updateMediaUpload(file.name, { progress: 0, status: 'uploading' });
    try {
      const job = await initUpload({
        fileName: file.name,
        mimeType: file.type || 'application/octet-stream',
        totalSize: file.size,
        chunkSize: CHUNK_SIZE,
        testimonialId,
        fileLastModified: file.lastModified,
      });
      for (let i = 0; i < job.chunksTotal; i++) {
        await uploadChunk(job.jobId, i, file.slice(i * CHUNK_SIZE, (i + 1) * CHUNK_SIZE));
        updateMediaUpload(file.name, { progress: Math.round(((i + 1) / job.chunksTotal) * 100) });
      }
      await completeUpload(job.jobId);
      updateMediaUpload(file.name, { status: 'done', progress: 100 });
      qc.invalidateQueries({ queryKey: ['testimonial', testimonialId] });
      qc.invalidateQueries({ queryKey: ['testimonials'] });
    } catch (err) {
      updateMediaUpload(file.name, {
        status: 'error',
        error: (err as { message?: string })?.message ?? 'Upload fehlgeschlagen',
      });
    }
  };

  async function handleSave() {
    if (!validate()) return;
    setSaving(true);
    setSaveError(null);
    try {
      // Step 1: Save testimonial with images and deletions
      const testimonial = await onSave({
        authorName: authorName.trim(),
        tags: tags.trim() || null,
        text: text.trim(),
        source: source as 'Mensch' | 'Tier',
        images: newFiles,
        deleteAttachmentIds,
      });

      // Step 2: If we have media files to upload, upload them in parallel (max 3)
      if (newMediaFiles.length > 0) {
        const CONCURRENCY = 3;
        const queue = newMediaFiles.map(f => () => uploadMediaFile(testimonial.id, f));
        let idx = 0;
        const runNext = () => {
          if (idx >= queue.length) return;
          const task = queue[idx++];
          task().finally(runNext);
        };
        for (let i = 0; i < Math.min(CONCURRENCY, queue.length); i++) {
          runNext();
        }
        // Wait a bit to let uploads start before closing dialog
        await new Promise(resolve => setTimeout(resolve, 500));
      }

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

  function handleMediaFileChange(e: ChangeEvent<HTMLInputElement>) {
    const files = Array.from(e.target.files ?? []).filter(
      f => f.type.startsWith('video/') || f.type.startsWith('audio/')
    );
    setNewMediaFiles(prev => [...prev, ...files]);
    if (mediaInputRef.current) mediaInputRef.current.value = '';
  }

  function removeNewFile(idx: number) {
    setNewFiles(prev => prev.filter((_, i) => i !== idx));
  }

  function removeNewMediaFile(idx: number) {
    setNewMediaFiles(prev => prev.filter((_, i) => i !== idx));
  }

  function markAttachmentForDelete(attachmentId: number) {
    setDeleteAttachmentIds(prev => [...prev, attachmentId]);
  }

  const existingAttachments = (initial?.attachments ?? []).filter(
    att => !deleteAttachmentIds.includes(att.id)
  );

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
            {(existingAttachments.filter(a => a.mimeType?.startsWith('image/')).length > 0 || newFiles.length > 0) && (
                <Stack direction="row" flexWrap="wrap" gap={1}>
                  {existingAttachments.filter(a => a.mimeType?.startsWith('image/')).map(img => (
                      <ExistingAttachmentThumb
                          key={img.id} attachment={img}
                          onRemove={() => markAttachmentForDelete(img.id)}
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

          {/* Video/Audio section */}
          <Box sx={{mb: 2}}>
            <Stack direction="row" alignItems="center" gap={1} sx={{mb: 1}}>
              <Typography variant="caption" color="text.secondary">Video/Audio</Typography>
              <Tooltip title="Video/Audio hinzufügen (MP4, WebM, MP3, WAV, OGG, etc.)">
                <IconButton size="small" onClick={() => mediaInputRef.current?.click()}>
                  <AddIcon fontSize="small"/>
                </IconButton>
              </Tooltip>
            </Stack>
            <input ref={mediaInputRef} type="file" accept="video/*,audio/*" multiple hidden
                   onChange={handleMediaFileChange}/>
            {(existingAttachments.filter(a => a.mimeType?.startsWith('video/') || a.mimeType?.startsWith('audio/')).length > 0 || newMediaFiles.length > 0) && (
                <Stack gap={1.5}>
                  {existingAttachments.filter(a => a.mimeType?.startsWith('video/') || a.mimeType?.startsWith('audio/')).map(attachment => (
                      <Box key={attachment.id} sx={{display: 'flex', alignItems: 'center', gap: 1}}>
                        <ExistingAttachmentThumb
                            attachment={attachment}
                            onRemove={() => markAttachmentForDelete(attachment.id)}
                        />
                        <Box sx={{flex: 1}}>
                          <Typography variant="caption">{attachment.originalName}</Typography>
                        </Box>
                      </Box>
                  ))}
                  {newMediaFiles.map((file) => {
                    const state = mediaUploads.get(file.name);
                    return (
                        <Box key={`${file.name}-${file.size}-${file.lastModified}`}>
                          <Box sx={{display: 'flex', alignItems: 'center', gap: 1, mb: 0.5}}>
                            <Typography variant="caption" sx={{flex: 1}}>{file.name}</Typography>
                            {state?.status === 'error' && (
                                <Typography variant="caption" color="error">{state.error}</Typography>
                            )}
                          </Box>
                          {state?.status !== 'error' && (
                              <LinearProgress
                                  variant="determinate"
                                  value={state?.progress ?? 0}
                                  sx={{height: 6, borderRadius: 1}}
                              />
                          )}
                          {state?.status === 'done' && (
                              <Typography variant="caption" color="success.main">Fertig</Typography>
                          )}
                        </Box>
                    );
                  })}
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
