import { Drawer, Toolbar, Typography } from '@mui/material';
import { useQuery } from '@tanstack/react-query';
import type { ResourceDto } from '../../types/api';
import { getEffectivePermissions } from '../../api/folders';
import SubtitleSection from './SubtitleSection';
import TranslationSection from './TranslationSection';
import SubtitleUploadSection from './SubtitleUploadSection';

interface Props {
  resource: ResourceDto;
  open: boolean;
  onClose: () => void;
}

export default function SubtitlePanel({ resource, open, onClose }: Readonly<Props>) {
  const { data: perms } = useQuery({
    queryKey: ['effectivePerms', resource.folderId],
    queryFn: () => getEffectivePermissions(resource.folderId),
    enabled: open,
  });
  const canManageSubtitles   = perms?.permissions.includes('MANAGE_SUBTITLES') ?? false;
  const canTranslateSubtitles = perms?.permissions.includes('TRANSLATE_SUBTITLES') ?? false;
  const canDownload           = perms?.permissions.includes('DOWNLOAD') ?? false;

  return (
    <Drawer anchor="right" open={open} onClose={onClose}
      slotProps={{ paper: { sx: { width: 400, p: 3, overflowY: 'auto' } } }}>
      <Toolbar />
      <Typography variant="h6" gutterBottom>Untertitel & Übersetzung</Typography>
      <Typography variant="caption" color="text.secondary" sx={{ mb: 2, display: 'block' }}>
        {resource.metadata?.title ?? resource.originalName}
      </Typography>
      <SubtitleSection resourceId={resource.id} readonly={!canManageSubtitles} canDownload={canDownload} />
      <TranslationSection resourceId={resource.id} canTranslateSubtitles={canTranslateSubtitles} readonly={!canTranslateSubtitles} />
      {canManageSubtitles && <SubtitleUploadSection resourceId={resource.id} />}
    </Drawer>
  );
}
