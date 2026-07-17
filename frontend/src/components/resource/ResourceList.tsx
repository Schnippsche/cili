import {
  Box, Button, Dialog, DialogActions, DialogContent, DialogTitle,
  IconButton, Menu, MenuItem, Table, TableBody, TableCell, TableHead,
  TableRow, Tooltip, Typography,
} from '@mui/material';
import InsertDriveFileOutlinedIcon from '@mui/icons-material/InsertDriveFileOutlined';
import VideoFileOutlinedIcon from '@mui/icons-material/VideoFileOutlined';
import ImageOutlinedIcon from '@mui/icons-material/ImageOutlined';
import PictureAsPdfOutlinedIcon from '@mui/icons-material/PictureAsPdfOutlined';
import DescriptionOutlinedIcon from '@mui/icons-material/DescriptionOutlined';
import CodeOutlinedIcon from '@mui/icons-material/CodeOutlined';
import AudioFileOutlinedIcon from '@mui/icons-material/AudioFileOutlined';
import MoreVertIcon from '@mui/icons-material/MoreVert';
import DragIndicatorIcon from '@mui/icons-material/DragIndicator';
import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  DndContext, closestCenter, PointerSensor, TouchSensor,
  useSensor, useSensors, type DragEndEvent,
} from '@dnd-kit/core';
import { SortableContext, verticalListSortingStrategy, useSortable, arrayMove } from '@dnd-kit/sortable';
import { CSS } from '@dnd-kit/utilities';
import type { ResourceDto } from '../../types/api';
import { getDownloadUrl } from '../../api/resources';
import { useDeleteResource, useMoveResource } from '../../hooks/useResources';
import MovePicker from '../common/MovePicker';
import MetadataPanel from './MetadataPanel';
import SubtitlePanel from './SubtitlePanel';
import AnalysisPanel from './AnalysisPanel';
import SharePanel from './SharePanel';
import { formatBytes } from '../../utils/formatBytes';

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
  const sx = { fontSize: 20, color: 'text.disabled', flexShrink: 0 };
  if (isVideo(mimeType))    return <VideoFileOutlinedIcon sx={sx} />;
  if (isAudio(mimeType))    return <AudioFileOutlinedIcon sx={sx} />;
  if (isImage(mimeType))    return <ImageOutlinedIcon sx={sx} />;
  if (isPdf(mimeType))      return <PictureAsPdfOutlinedIcon sx={sx} />;
  if (isDocument(mimeType)) return <DescriptionOutlinedIcon sx={sx} />;
  if (isText(mimeType))     return <CodeOutlinedIcon sx={sx} />;
  return <InsertDriveFileOutlinedIcon sx={sx} />;
}

interface ListProps {
  resources: ResourceDto[];
  folderId: number;
  canDownload?: boolean;
  canEdit?: boolean;
  canDelete?: boolean;
  canReorder?: boolean;
  onReorder?: (ids: number[]) => void;
  canMove?: boolean;
  canShare?: boolean;
}

interface RowProps {
  resource: ResourceDto;
  folderId: number;
  canDownload?: boolean;
  canEdit?: boolean;
  canDelete?: boolean;
  canReorder?: boolean;
  canMove?: boolean;
  canShare?: boolean;
}

function ResourceRow({ resource, folderId, canDownload, canEdit, canDelete, canReorder, canMove, canShare }: Readonly<RowProps>) {
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

  const { mimeType, id } = resource;
  const hasViewer = isVideo(mimeType) || isAudio(mimeType) || isImage(mimeType) || isDocument(mimeType) || isText(mimeType);
  const isMediaType = isVideo(mimeType) || isAudio(mimeType);

  const { attributes, listeners, setNodeRef, transform, transition, isDragging } =
    useSortable({ id: resource.id, disabled: !canReorder });

  const handleOpen = () => {
    if (isVideo(mimeType))         navigate(`/folders/${folderId}?view=${id}&type=video`);
    else if (isAudio(mimeType))    navigate(`/folders/${folderId}?view=${id}&type=audio`);
    else if (isImage(mimeType))    navigate(`/folders/${folderId}?view=${id}&type=image`);
    else if (isDocument(mimeType)) navigate(`/folders/${folderId}?view=${id}&type=pdf`);
    else if (isText(mimeType))     navigate(`/resources/${id}/edit`);
    else globalThis.open(getDownloadUrl(id), '_blank');
  };

  const displayName = resource.metadata?.title || resource.originalName;

  return (
    <>
      <TableRow
        ref={setNodeRef}
        hover
        sx={{
          cursor: hasViewer || canDownload ? 'pointer' : 'default',
          transform: CSS.Transform.toString(transform),
          transition,
          opacity: isDragging ? 0.5 : 1,
        }}
      >
        {canReorder && (
          <TableCell sx={{ width: 32, pr: 0, pl: 1 }}>
            <DragIndicatorIcon
              fontSize="small"
              sx={{ color: 'text.disabled', cursor: 'grab', display: 'block' }}
              {...attributes}
              {...listeners}
            />
          </TableCell>
        )}
        <TableCell onClick={handleOpen}>
          <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
            <MimeIcon mimeType={mimeType} />
            <Typography variant="body2">{displayName}</Typography>
          </Box>
        </TableCell>
        <TableCell onClick={handleOpen}>
          <Typography variant="body2" color="text.secondary">{formatBytes(resource.size)}</Typography>
        </TableCell>
        <TableCell onClick={handleOpen}>
          <Typography variant="body2" color="text.secondary">
            {new Date(resource.fileDate ?? resource.createdAt).toLocaleDateString('de-DE')}
          </Typography>
        </TableCell>
        <TableCell align="right" sx={{ width: 40, pr: 0 }}>
          <Tooltip title="Optionen">
            <IconButton size="small" onClick={e => { e.stopPropagation(); setAnchor(e.currentTarget); }}>
              <MoreVertIcon fontSize="small" />
            </IconButton>
          </Tooltip>
        </TableCell>
      </TableRow>

      <Menu anchorEl={anchor} open={Boolean(anchor)} onClose={() => setAnchor(null)} onClick={e => e.stopPropagation()}>
        {canDownload  && <MenuItem onClick={() => { setAnchor(null); globalThis.open(getDownloadUrl(id), '_blank'); }}>Herunterladen</MenuItem>}
        {canEdit      && <MenuItem onClick={() => { setAnchor(null); setMetaReadonly(false); setMetaOpen(true); }}>Metadaten bearbeiten</MenuItem>}
        {!canEdit     && <MenuItem onClick={() => { setAnchor(null); setMetaReadonly(true); setMetaOpen(true); }}>Metadaten ansehen</MenuItem>}
        {isMediaType  && <MenuItem onClick={() => { setAnchor(null); setSubtitleOpen(true); }}>Untertitel</MenuItem>}
        {isMediaType  && resource.hasAnalyzableSubtitles && <MenuItem onClick={() => { setAnchor(null); setAnalysisOpen(true); }}>KI-Zusammenfassung</MenuItem>}
        {canShare     && <MenuItem onClick={() => { setAnchor(null); setShareOpen(true); }}>Freigabe</MenuItem>}
        {canMove      && <MenuItem onClick={() => { setAnchor(null); setMoveOpen(true); }}>Verschieben</MenuItem>}
        {canDelete    && <MenuItem sx={{ color: 'error.main' }} onClick={() => { setAnchor(null); setDeleteOpen(true); }}>Löschen</MenuItem>}
      </Menu>

      <MetadataPanel resource={resource} open={metaOpen} onClose={() => setMetaOpen(false)} readonly={metaReadonly} />
      <SubtitlePanel resource={resource} open={subtitleOpen} onClose={() => setSubtitleOpen(false)} />
      <AnalysisPanel resource={resource} open={analysisOpen} onClose={() => setAnalysisOpen(false)} />
      <SharePanel resource={resource} open={shareOpen} onClose={() => setShareOpen(false)} />

      <MovePicker
        open={moveOpen}
        title={`"${displayName}" verschieben`}
        excludeId={resource.folderId ?? undefined}
        onClose={() => setMoveOpen(false)}
        onConfirm={async (targetFolderId) => {
          await moveResource.mutateAsync({ id, newFolderId: targetFolderId });
          setMoveOpen(false);
        }}
      />
      <Dialog open={deleteOpen} onClose={() => setDeleteOpen(false)} maxWidth="xs" fullWidth>
        <DialogTitle>Datei löschen?</DialogTitle>
        <DialogContent>
          <strong>{displayName}</strong> wird unwiderruflich gelöscht.
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setDeleteOpen(false)}>Abbrechen</Button>
          <Button variant="contained" color="error" onClick={() => { deleteRes.mutate(id); setDeleteOpen(false); }}>Löschen</Button>
        </DialogActions>
      </Dialog>
    </>
  );
}

export default function ResourceList({ resources, folderId, canDownload = false, canEdit = false, canDelete = false, canReorder = false, canMove = false, canShare = false, onReorder }: Readonly<ListProps>) {
  const sensors = useSensors(
    useSensor(PointerSensor, { activationConstraint: { distance: 5 } }),
    useSensor(TouchSensor,   { activationConstraint: { delay: 200, tolerance: 5 } }),
  );

  const handleDragEnd = (event: DragEndEvent) => {
    const { active, over } = event;
    if (!over || active.id === over.id) return;
    const oldIndex = resources.findIndex(r => r.id === active.id);
    const newIndex = resources.findIndex(r => r.id === over.id);
    const reordered = arrayMove(resources, oldIndex, newIndex);
    onReorder?.(reordered.map(r => r.id));
  };

  return (
    <DndContext sensors={sensors} collisionDetection={closestCenter} onDragEnd={handleDragEnd}>
      <SortableContext items={resources.map(r => r.id)} strategy={verticalListSortingStrategy}>
        <Table size="small">
          <TableHead>
            <TableRow>
              {canReorder && <TableCell sx={{ width: 32 }} />}
              <TableCell>Name</TableCell>
              <TableCell>Größe</TableCell>
              <TableCell>Datum</TableCell>
              <TableCell />
            </TableRow>
          </TableHead>
          <TableBody>
            {resources.map(r => (
              <ResourceRow key={r.id} resource={r} folderId={folderId}
                canDownload={canDownload} canEdit={canEdit} canDelete={canDelete} canReorder={canReorder} canMove={canMove} canShare={canShare} />
            ))}
          </TableBody>
        </Table>
      </SortableContext>
    </DndContext>
  );
}
