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
  Snackbar,
  Stack,
  TextField,
  ToggleButton,
  ToggleButtonGroup,
  Tooltip,
  Typography,
} from '@mui/material';
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

  const thumb = (
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
                      onMouseOver={(e) => e.stopPropagation()}
                      onFocus={(e) => e.stopPropagation()}
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

  return (isVideo || isAudio) ? <Tooltip title={attachment.originalName}>{thumb}</Tooltip> : thumb;
}

interface NewMediaFileTileProps {
  file: File;
  state: MediaUploadState | undefined;
  onRemove: () => void;
}

function NewMediaFileTile({file, state, onRemove}: Readonly<NewMediaFileTileProps>) {
  const isVideo = isVideoLikeFile(file);
  const isError = state?.status === 'error';
  const tooltipTitle = isError ? `${file.name} — ${state?.error}` : file.name;

  return (
      <Tooltip title={tooltipTitle}>
        <Box sx={{
          position: 'relative',
          width: 72,
          height: 72,
          borderRadius: 1,
          bgcolor: 'action.hover',
          overflow: 'hidden',
          border: isError ? '1px solid' : 'none',
          borderColor: isError ? 'error.main' : undefined,
        }}>
          <Box sx={{
            width: '100%',
            height: '100%',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
          }}>
            {isVideo
                ? <PlayArrowIcon sx={{fontSize: 32, color: 'text.secondary'}}/>
                : <MusicNoteIcon sx={{fontSize: 32, color: 'text.secondary'}}/>}
          </Box>
          {state?.status === 'uploading' && (
              <LinearProgress
                  variant="determinate"
                  value={state.progress}
                  sx={{position: 'absolute', bottom: 0, left: 0, right: 0, height: 4}}
              />
          )}
          <Tooltip title="Entfernen">
            <IconButton size="small" onClick={onRemove}
                        onMouseOver={(e) => e.stopPropagation()}
                        onFocus={(e) => e.stopPropagation()}
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
      </Tooltip>
  );
}

function formatRejectedNames(names: string[]): string {
  if (names.length <= 3) return names.join(', ');
  return `${names.slice(0, 3).join(', ')} und ${names.length - 3} weitere`;
}

interface Props {
  open: boolean;
  initial?: TestimonialDto | null;
  onSave: (data: TestimonialFormData) => Promise<TestimonialDto>;
  onClose: () => void;
}

const CHUNK_SIZE = 5 * 1024 * 1024;

// Manche Browser/Betriebssysteme melden für .ogg/.oga/.ogv keinen oder einen generischen
// File.type ('', 'application/ogg', 'application/octet-stream') statt 'audio/'/'video/' —
// ohne diesen Fallback würde handleAttachmentFileChange solche Dateien stillschweigend verwerfen,
// obwohl der Backend-Normalizer (UploadService.normalizeMimeType) sie korrekt erkennt.
const MEDIA_EXTENSION_FALLBACK = /\.(ogg|oga|ogv|mkv|m4a|flac|opus|wma|3gp)$/i;

function isMediaFile(file: File): boolean {
  if (file.type.startsWith('video/') || file.type.startsWith('audio/')) return true;
  if (!file.type || file.type === 'application/ogg' || file.type === 'application/octet-stream') {
    return MEDIA_EXTENSION_FALLBACK.test(file.name);
  }
  return false;
}

const VIDEO_EXTENSION_FALLBACK = /\.(ogv|mkv|3gp)$/i;

export function isVideoLikeFile(file: File): boolean {
  if (file.type.startsWith('video/')) return true;
  if (file.type.startsWith('audio/')) return false;
  return VIDEO_EXTENSION_FALLBACK.test(file.name);
}

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
  const [rejectedFileNames, setRejectedFileNames] = useState<string[]>([]);
  const [saving, setSaving] = useState(false);
  const [errors, setErrors] = useState<{ authorName?: string; tags?: string; text?: string; source?: string }>({});
  const [saveError, setSaveError] = useState<string | null>(null);
  const [mediaUploads, setMediaUploads] = useState<Map<string, MediaUploadState>>(new Map());
  const attachmentInputRef = useRef<HTMLInputElement>(null);

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
      setRejectedFileNames([]);
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

  function handleAttachmentFileChange(e: ChangeEvent<HTMLInputElement>) {
    const files = Array.from(e.target.files ?? []);
    const images: File[] = [];
    const media: File[] = [];
    const rejected: string[] = [];

    for (const file of files) {
      if (file.type.startsWith('image/')) images.push(file);
      else if (isMediaFile(file)) media.push(file);
      else rejected.push(file.name);
    }

    if (images.length) setNewFiles(prev => [...prev, ...images]);
    if (media.length) setNewMediaFiles(prev => [...prev, ...media]);
    if (rejected.length) setRejectedFileNames(rejected);

    if (attachmentInputRef.current) attachmentInputRef.current.value = '';
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

          {/* Anhänge */}
          <Box sx={{mb: 2}}>
            <Stack direction="row" alignItems="center" gap={1} sx={{mb: 1}}>
              <Typography variant="caption" color="text.secondary">Anhänge</Typography>
              <Tooltip title="Bilder, Video oder Audio hinzufügen">
                <IconButton size="small" onClick={() => attachmentInputRef.current?.click()}>
                  <AddIcon fontSize="small"/>
                </IconButton>
              </Tooltip>
            </Stack>
            <input ref={attachmentInputRef} type="file" accept="image/*,video/*,audio/*" multiple hidden
                   data-testid="attachment-input"
                   onChange={handleAttachmentFileChange}/>
            {(existingAttachments.length > 0 || newFiles.length > 0 || newMediaFiles.length > 0) && (
                <Stack direction="row" flexWrap="wrap" gap={1}>
                  {existingAttachments.map(attachment => (
                      <ExistingAttachmentThumb
                          key={attachment.id} attachment={attachment}
                          onRemove={() => markAttachmentForDelete(attachment.id)}
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
                  {newMediaFiles.map((file, idx) => (
                      <NewMediaFileTile
                          key={`${file.name}-${file.size}-${file.lastModified}`}
                          file={file}
                          state={mediaUploads.get(file.name)}
                          onRemove={() => removeNewMediaFile(idx)}
                      />
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
        <Snackbar
            open={rejectedFileNames.length > 0}
            autoHideDuration={6000}
            onClose={() => setRejectedFileNames([])}
            anchorOrigin={{vertical: 'bottom', horizontal: 'center'}}
        >
          <Alert severity="warning" onClose={() => setRejectedFileNames([])}>
            Nicht unterstützt und übersprungen: {formatRejectedNames(rejectedFileNames)}
          </Alert>
        </Snackbar>
      </Dialog>
  );
}
