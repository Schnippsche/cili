import { Box, Grid2, Typography } from '@mui/material';
import DragIndicatorIcon from '@mui/icons-material/DragIndicator';
import ResourceCard from './ResourceCard';
import type { ResourceDto } from '../../types/api';

function typeInfo(mimeType: string): { label: string; color: string } {
  if (mimeType.startsWith('video/'))   return { label: 'VIDEO', color: '#e53935' };
  if (mimeType.startsWith('audio/'))   return { label: 'AUDIO', color: '#8e24aa' };
  if (mimeType.startsWith('image/'))   return { label: 'BILD',  color: '#43a047' };
  if (mimeType === 'application/pdf')  return { label: 'PDF',   color: '#e65100' };
  if (/word|opendocument\.text/.test(mimeType))          return { label: 'DOC', color: '#1565c0' };
  if (/sheet|excel|opendocument\.spreadsheet/.test(mimeType)) return { label: 'XLS', color: '#2e7d32' };
  if (/presentation|powerpoint|opendocument\.presentation/.test(mimeType)) return { label: 'PPT', color: '#bf360c' };
  if (mimeType.startsWith('text/'))    return { label: 'TEXT',  color: '#546e7a' };
  return { label: 'DATEI', color: '#9e9e9e' };
}
import {
  DndContext, closestCenter, PointerSensor, TouchSensor,
  useSensor, useSensors, type DragEndEvent,
} from '@dnd-kit/core';
import { SortableContext, rectSortingStrategy, useSortable, arrayMove } from '@dnd-kit/sortable';
import { CSS } from '@dnd-kit/utilities';

interface Props { resources: ResourceDto[]; folderId?: number; canDownload?: boolean; canEdit?: boolean; canDelete?: boolean; canReorder?: boolean; canMove?: boolean; canShare?: boolean; onReorder?: (ids: number[]) => void; onRemoveFromCollection?: (resourceId: number) => void; }

function SortableCard({ resource, canDownload, canEdit, canDelete, canReorder, canMove, canShare, onRemoveFromCollection }: Readonly<{ resource: ResourceDto; canDownload: boolean; canEdit: boolean; canDelete: boolean; canReorder: boolean; canMove: boolean; canShare: boolean; onRemoveFromCollection?: (resourceId: number) => void }>) {
  const { attributes, listeners, setNodeRef, transform, transition, isDragging } =
    useSortable({ id: resource.id, disabled: !canReorder });

  const { label, color } = typeInfo(resource.mimeType);

  return (
    <Box
      ref={setNodeRef}
      sx={{ display: 'flex', flexDirection: 'column', width: '100%', transform: CSS.Transform.toString(transform), transition, opacity: isDragging ? 0.5 : 1 }}
    >
      <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', pb: 0.5, px: 0.5, minHeight: 22 }}>
        {canReorder ? (
          <Box sx={{ display: 'flex', cursor: 'grab', color: 'text.disabled' }} {...attributes} {...listeners}>
            <DragIndicatorIcon fontSize="small" sx={{ transform: 'rotate(90deg)' }} />
          </Box>
        ) : <Box />}
        <Typography sx={{ fontSize: '0.6rem', fontWeight: 700, letterSpacing: 0.8, color, lineHeight: 1 }}>
          {label}
        </Typography>
      </Box>
      <ResourceCard resource={resource} canDownload={canDownload} canEdit={canEdit} canDelete={canDelete} canMove={canMove} canShare={canShare} onRemoveFromCollection={onRemoveFromCollection} />
    </Box>
  );
}

export default function ResourceGrid({ resources, canDownload = false, canEdit = false, canDelete = false, canReorder = false, canMove = false, canShare = false, onReorder, onRemoveFromCollection }: Readonly<Props>) {
  const sensors = useSensors(
    useSensor(PointerSensor, { activationConstraint: { distance: 5 } }),
    useSensor(TouchSensor,   { activationConstraint: { delay: 200, tolerance: 5 } }),
  );

  const handleDragEnd = (event: DragEndEvent) => {
    const { active, over } = event;
    if (!over || active.id === over.id) return;
    const oldIndex = resources.findIndex(r => r.id === active.id);
    const newIndex = resources.findIndex(r => r.id === over.id);
    onReorder?.(arrayMove(resources, oldIndex, newIndex).map(r => r.id));
  };

  return (
    <DndContext sensors={sensors} collisionDetection={closestCenter} onDragEnd={handleDragEnd}>
      <SortableContext items={resources.map(r => r.id)} strategy={rectSortingStrategy}>
        <Grid2 container spacing={2} alignItems="stretch">
          {resources.map(r => (
            <Grid2 key={r.id} size={{ xs: 6, sm: 4, md: 3, lg: 2 }} sx={{ display: 'flex' }}>
              <SortableCard resource={r} canDownload={canDownload} canEdit={canEdit} canDelete={canDelete} canReorder={canReorder} canMove={canMove} canShare={canShare} onRemoveFromCollection={onRemoveFromCollection} />
            </Grid2>
          ))}
        </Grid2>
      </SortableContext>
    </DndContext>
  );
}
