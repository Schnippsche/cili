import { Box, Button, Card, CardActionArea, CardContent, CardMedia, Dialog, DialogActions, DialogContent, DialogTitle, IconButton, Menu, MenuItem, Tooltip, Typography } from '@mui/material';
import InsertDriveFileOutlinedIcon from '@mui/icons-material/InsertDriveFileOutlined';
import VideoFileOutlinedIcon from '@mui/icons-material/VideoFileOutlined';
import ImageOutlinedIcon from '@mui/icons-material/ImageOutlined';
import PictureAsPdfOutlinedIcon from '@mui/icons-material/PictureAsPdfOutlined';
import DescriptionOutlinedIcon from '@mui/icons-material/DescriptionOutlined';
import CodeOutlinedIcon from '@mui/icons-material/CodeOutlined';
import AudioFileOutlinedIcon from '@mui/icons-material/AudioFileOutlined';
import MoreVertIcon from '@mui/icons-material/MoreVert';
import { useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import type { ResourceDto } from '../../types/api';
import { getDownloadUrl, getThumbnailUrl } from '../../api/resources';
import { useDeleteResource, useMoveResource } from '../../hooks/useResources';
import { useAuthenticatedUrl } from '../../hooks/useAuthenticatedUrl';
import MovePicker from '../common/MovePicker';
import MetadataPanel from './MetadataPanel';
import SubtitlePanel from './SubtitlePanel';
import AnalysisPanel from './AnalysisPanel';
import SharePanel from './SharePanel';
import AddToCollectionDialog from './AddToCollectionDialog';

const isVideo    = (m: string) => m.startsWith('video/');
const isAudio    = (m: string) => m.startsWith('audio/');
const isImage    = (m: string) => m.startsWith('image/');
const isText     = (m: string) => m.startsWith('text/') || m === 'application/json' || m === 'application/x-subrip';
const isPdf      = (m: string) => m === 'application/pdf';
const isDocument = (m: string) =>
  m === 'application/pdf' ||
  m.startsWith('application/vnd.openxmlformats') ||
  m.startsWith('application/vnd.ms-') ||
  m === 'application/msword' ||
  m.startsWith('application/vnd.oasis.opendocument');

function MimeIcon({ mimeType }: Readonly<{ mimeType: string }>) {
  const sx = { fontSize: 40, color: 'text.disabled' };
  if (isVideo(mimeType))        return <VideoFileOutlinedIcon sx={sx} />;
  if (isAudio(mimeType))        return <AudioFileOutlinedIcon sx={sx} />;
  if (isImage(mimeType))        return <ImageOutlinedIcon sx={sx} />;
  if (isPdf(mimeType))          return <PictureAsPdfOutlinedIcon sx={sx} />;
  if (isDocument(mimeType))     return <DescriptionOutlinedIcon sx={sx} />;
  if (isText(mimeType))         return <CodeOutlinedIcon sx={sx} />;
  return <InsertDriveFileOutlinedIcon sx={sx} />;
}

function FileName({ title, originalName }: Readonly<{ title: string | null; originalName: string }>) {
  const extMatch = originalName.match(/(\.[^.]+)$/);
  const ext = extMatch ? extMatch[1] : '';
  const display = title ?? originalName;
  const base = !title && ext ? display.slice(0, display.length - ext.length) : display;
  const baseRef = useRef<HTMLSpanElement>(null);
  const [isTruncated, setIsTruncated] = useState(false);

  const checkTruncation = () => {
    const el = baseRef.current;
    if (el) setIsTruncated(el.scrollWidth > el.clientWidth);
  };

  return (
    <Tooltip title={display} placement="bottom" enterDelay={300} disableHoverListener={!isTruncated}>
      <Box sx={{ display: 'flex', overflow: 'hidden', alignItems: 'baseline' }} onMouseEnter={checkTruncation}>
        <Typography ref={baseRef} variant="caption" noWrap sx={{ minWidth: 0 }}>{base}</Typography>
        {ext && <Typography variant="caption" sx={{ flexShrink: 0, color: 'text.secondary' }}>{ext}</Typography>}
      </Box>
    </Tooltip>
  );
}

interface Props { resource: ResourceDto; canDownload?: boolean; canEdit?: boolean; canDelete?: boolean; canMove?: boolean; canShare?: boolean; onRemoveFromCollection?: (resourceId: number) => void; }

export default function ResourceCard({ resource, canDownload = false, canEdit = false, canDelete = false, canMove = false, canShare = false, onRemoveFromCollection }: Readonly<Props>) {
  const navigate = useNavigate();
  const deleteRes = useDeleteResource();
  const moveResource = useMoveResource();
  const [anchor, setAnchor] = useState<null | HTMLElement>(null);
  const [moveOpen, setMoveOpen] = useState(false);
  const [metaOpen, setMetaOpen] = useState(false);
  const [metaReadonly, setMetaReadonly] = useState(false);
  const [subtitleOpen, setSubtitleOpen] = useState(false);
  const [analysisOpen, setAnalysisOpen] = useState(false);
  const [shareOpen, setShareOpen] = useState(false);
  const [deleteOpen, setDeleteOpen] = useState(false);
  const [collectionOpen, setCollectionOpen] = useState(false);

  const { id, folderId, mimeType, thumbnailStatus } = resource;
  // Bilder: immer laden — Backend liefert Originalbild als Fallback solange Thumbnail noch generiert wird.
  // v-Parameter nur bei DONE gesetzt: URL-Änderung beim Übergang PROCESSING→DONE triggert Re-fetch.
  // Videos/Dokumente/Audio: erst laden wenn DONE (kein sinnvoller Fallback verfügbar).
  const thumbSrc = thumbnailStatus === 'DONE' || isImage(mimeType)
    ? getThumbnailUrl(resource.id, 'small', thumbnailStatus === 'DONE' ? resource.storedName : undefined)
    : null;
  const thumbUrl = useAuthenticatedUrl(thumbSrc, isImage(mimeType) ? 3 : 0);
  const hasViewer = isVideo(mimeType) || isAudio(mimeType) || isImage(mimeType) || isDocument(mimeType) || isText(mimeType);
  const canOpen   = hasViewer || canDownload;
  const isMediaType = isVideo(mimeType) || isAudio(mimeType);

  const handleOpen = () => {
    if (isVideo(mimeType))         navigate(`/folders/${folderId}?view=${id}&type=video`);
    else if (isAudio(mimeType))    navigate(`/folders/${folderId}?view=${id}&type=audio`);
    else if (isImage(mimeType))    navigate(`/folders/${folderId}?view=${id}&type=image`);
    else if (isDocument(mimeType)) navigate(`/folders/${folderId}?view=${id}&type=pdf`);
    else if (isText(mimeType))     navigate(`/resources/${id}/edit`);
    else globalThis.open(getDownloadUrl(id), '_blank');
  };

  return (
    <>
      <Card variant="outlined" sx={{ height: '100%', display: 'flex', flexDirection: 'column', '&:hover': { boxShadow: 2 } }}>
        <CardActionArea onClick={handleOpen} disabled={!canOpen} sx={{ flex: 1, display: 'flex', flexDirection: 'column', alignItems: 'stretch' }}>
          {thumbUrl ? (
            <CardMedia component="img" height="100" image={thumbUrl} alt={resource.originalName}
              sx={{ objectFit: 'contain', bgcolor: 'action.hover' }} />
          ) : (
            <Box sx={{
              height: 100, display: 'flex', alignItems: 'center', justifyContent: 'center',
              bgcolor: 'action.hover',
            }}>
              <MimeIcon mimeType={resource.mimeType} />
            </Box>
          )}
          <CardContent sx={{ pt: 1, pb: '8px !important', mt: 'auto' }}>
            <Box sx={{ display: 'flex', alignItems: 'flex-start', gap: 0.5 }}>
              <Box sx={{ flex: 1, overflow: 'hidden' }}>
                <FileName title={resource.metadata?.title ?? null} originalName={resource.originalName} />

                <Typography variant="caption" color="text.secondary">
                  {new Date(resource.fileDate ?? resource.createdAt).toLocaleDateString('de-DE')}
                </Typography>
              </Box>
              <Tooltip title="Optionen">
                <IconButton size="small" aria-label="resource options"
                  onClick={e => { e.stopPropagation(); e.preventDefault(); setAnchor(e.currentTarget); }}
                  sx={{ mt: -0.5, mr: -0.5, flexShrink: 0 }}>
                  <MoreVertIcon fontSize="small" />
                </IconButton>
              </Tooltip>
            </Box>
          </CardContent>
        </CardActionArea>
        <Menu anchorEl={anchor} open={Boolean(anchor)} onClose={() => setAnchor(null)}>
          {canDownload && <MenuItem onClick={() => { setAnchor(null); globalThis.open(getDownloadUrl(resource.id), '_blank'); }}>Herunterladen</MenuItem>}
          {canEdit   && <MenuItem onClick={() => { setAnchor(null); setMetaReadonly(false); setMetaOpen(true); }}>Metadaten bearbeiten</MenuItem>}
          {!canEdit  && <MenuItem onClick={() => { setAnchor(null); setMetaReadonly(true); setMetaOpen(true); }}>Metadaten ansehen</MenuItem>}
          {isMediaType && <MenuItem onClick={() => { setAnchor(null); setSubtitleOpen(true); }}>Untertitel</MenuItem>}
          {isMediaType && resource.hasAnalyzableSubtitles && <MenuItem onClick={() => { setAnchor(null); setAnalysisOpen(true); }}>KI-Zusammenfassung</MenuItem>}
          {canShare  && <MenuItem onClick={() => { setAnchor(null); setShareOpen(true); }}>Freigabelink</MenuItem>}
          <MenuItem onClick={() => { setAnchor(null); setCollectionOpen(true); }}>
            Zu Sammlung hinzufügen
          </MenuItem>
          {canMove   && <MenuItem onClick={() => { setAnchor(null); setMoveOpen(true); }}>Verschieben</MenuItem>}
          {canDelete && <MenuItem sx={{ color: 'error.main' }} onClick={() => { setAnchor(null); setDeleteOpen(true); }}>Löschen</MenuItem>}
          {onRemoveFromCollection && (
            <MenuItem onClick={() => { setAnchor(null); onRemoveFromCollection(resource.id); }}>
              Aus Sammlung entfernen
            </MenuItem>
          )}
        </Menu>
      </Card>
      <MetadataPanel resource={resource} open={metaOpen} onClose={() => setMetaOpen(false)} readonly={metaReadonly} />
      <SubtitlePanel resource={resource} open={subtitleOpen} onClose={() => setSubtitleOpen(false)} />
      <AnalysisPanel resource={resource} open={analysisOpen} onClose={() => setAnalysisOpen(false)} />
      <SharePanel resource={resource} open={shareOpen} onClose={() => setShareOpen(false)} />
      <AddToCollectionDialog open={collectionOpen} itemId={resource.id} itemType="resource" onClose={() => setCollectionOpen(false)} />

      <MovePicker
        open={moveOpen}
        title={`"${resource.metadata?.title || resource.originalName}" verschieben`}
        excludeId={resource.folderId ?? undefined}
        onClose={() => setMoveOpen(false)}
        onConfirm={async (targetFolderId) => {
          await moveResource.mutateAsync({ id: resource.id, newFolderId: targetFolderId });
          setMoveOpen(false);
        }}
      />
      <Dialog open={deleteOpen} onClose={() => setDeleteOpen(false)} maxWidth="xs" fullWidth>
        <DialogTitle>Datei löschen?</DialogTitle>
        <DialogContent>
          <strong>{resource.metadata?.title || resource.originalName}</strong> wird unwiderruflich gelöscht.
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setDeleteOpen(false)}>Abbrechen</Button>
          <Button variant="contained" color="error" onClick={() => { deleteRes.mutate(resource.id); setDeleteOpen(false); }}>Löschen</Button>
        </DialogActions>
      </Dialog>
    </>
  );
}
