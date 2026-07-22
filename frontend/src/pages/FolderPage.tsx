import { useEffect, useMemo, useRef, useState } from 'react';
import { useParams, useSearchParams } from 'react-router-dom';
import { Alert, Box, Button, CircularProgress, Dialog, DialogContent, DialogTitle, Divider, IconButton, Link, Snackbar, Tooltip, Typography } from '@mui/material';
import GridViewIcon from '@mui/icons-material/GridView';
import ViewListIcon from '@mui/icons-material/ViewList';
import ContentCutIcon from '@mui/icons-material/ContentCut';
import AppShell from '../components/layout/AppShell';
import Breadcrumb from '../components/layout/Breadcrumb';
import FolderGrid from '../components/folder/FolderGrid';
import FolderList from '../components/folder/FolderList';
import FolderActions from '../components/folder/FolderActions';
import ResourceGrid from '../components/resource/ResourceGrid';
import ResourceList from '../components/resource/ResourceList';
import DropZone from '../components/upload/DropZone';
import VideoPlayer from '../components/viewer/VideoPlayer';
import PdfViewer from '../components/viewer/PdfViewer';
import ImageLightbox from '../components/viewer/ImageLightbox';
import MediaTrimBar from '../components/viewer/MediaTrimBar';
import { useFolderBreadcrumb, useFolderChildren, useFolderPermissions } from '../hooks/useFolders';
import { useResourcesByFolder, useSubtitleTracks, useCreateVideoClip, useActiveClipJobs } from '../hooks/useResources';
import { getStreamUrl, getPreviewUrl, getSubtitleUrl, reorderResources } from '../api/resources';
import type { FolderDto, ResourceDto } from '../types/api';
import { useSelector } from 'react-redux';
import { useQueryClient } from '@tanstack/react-query';
import type { RootState } from '../store/store';
import type Player from 'video.js/dist/types/player';

export default function FolderPage() {
  const { folderId } = useParams<{ folderId: string }>();
  const id = folderId ? Number(folderId) : undefined;
  const [searchParams, setSearchParams] = useSearchParams();

  const user = useSelector((s: RootState) => s.auth.user);
  const accessToken = useSelector((s: RootState) => s.auth.accessToken);
  const isAdmin = user?.role === 'ADMIN';
  const qc = useQueryClient();

  const { data: subFolders = [], isLoading: foldersLoading } = useFolderChildren(id);
  const { data: resources = [], isLoading: resourcesLoading } = useResourcesByFolder(id);
  const { data: crumbs     = [] } = useFolderBreadcrumb(id ?? 0);
  const { data: perms } = useFolderPermissions(id);

  const canReorder = isAdmin && id != null;

  const handleReorder = async (orderedIds: number[]) => {
    if (!id) return;
    const reordered = orderedIds
      .map(rid => resources.find(r => r.id === rid))
      .filter((r): r is ResourceDto => r !== undefined);
    qc.setQueryData(['resources', 'folder', id], reordered);
    try {
      await reorderResources(id, orderedIds);
    } catch {
      qc.setQueryData(['resources', 'folder', id], resources);
    }
  };

  const has = (p: string) => isAdmin || (perms?.permissions.includes(p as never) ?? false);
  const canWrite    = id == null ? isAdmin : has('WRITE');
  const canDelete   = id == null ? isAdmin : has('DELETE');
  const canUpload   = id == null ? isAdmin : has('UPLOAD');
  const canDownload = id == null ? isAdmin : has('DOWNLOAD');
  const canEdit     = id == null ? isAdmin : (has('MANAGE_METADATA') || has('WRITE'));
  const canShare    = id == null ? isAdmin : has('SHARE');
  const canMove = canDelete;
  const canFolderMenu = canWrite || canDelete;

  const [menuAnchor, setMenuAnchor]       = useState<HTMLElement | null>(null);
  const [contextFolder, setContextFolder] = useState<FolderDto | null>(null);
  const [viewMode, setViewMode] = useState<'grid' | 'list'>(
    () => (localStorage.getItem('cili.viewMode') as 'grid' | 'list') ?? 'grid'
  );
  const [trimMode, setTrimMode] = useState(false);
  const [player, setPlayer] = useState<Player | null>(null);
  const createClip = useCreateVideoClip();
  const [clipSnack, setClipSnack] = useState<{ open: boolean; severity: 'success' | 'error'; msg: string }>(
    { open: false, severity: 'success', msg: '' },
  );

  const toggleView = (mode: 'grid' | 'list') => {
    setViewMode(mode);
    localStorage.setItem('cili.viewMode', mode);
  };

  const viewId        = searchParams.get('view') ? Number(searchParams.get('view')) : null;
  const viewType      = searchParams.get('type');
  const seekToTime    = searchParams.get('t') ? Number(searchParams.get('t')) : undefined;

  const viewResource = resources.find(r => r.id === viewId);
  const viewResourceTitle = viewResource?.metadata?.title ?? viewResource?.originalName;

  const { data: activeClipJobs = [] } = useActiveClipJobs(viewType === 'video' && viewId ? viewId : 0);
  const clipJobsRunning = activeClipJobs.some(j => j.status === 'PENDING' || j.status === 'RUNNING');

  const images = resources.filter(r => r.mimeType.startsWith('image/'));

  const { data: subtitleTracks = [] } = useSubtitleTracks((viewType === 'video' || viewType === 'audio') && viewId ? viewId : null);
  const subtitles = useMemo(() => subtitleTracks.map(t => ({
    src: getSubtitleUrl(t.resourceId, t.id),
    label: t.label ?? t.languageCode,
    language: t.languageCode,
  })), [subtitleTracks]);

  // Neu berechnen wenn das Token nach einem Refresh wechselt (verhindert den "media could not be loaded"-Fehler)
  const streamUrl = useMemo(
    () => viewId ? getStreamUrl(viewId) : null,
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [viewId, accessToken],
  );

  useEffect(() => {
    setTrimMode(false);
    setPlayer(null);
  }, [viewId]);

  const prevClipJobsRunning = useRef(false);
  useEffect(() => {
    if (prevClipJobsRunning.current && !clipJobsRunning && id != null) {
      void qc.invalidateQueries({ queryKey: ['resources', 'folder', id] });
    }
    prevClipJobsRunning.current = clipJobsRunning;
  }, [clipJobsRunning, qc, id]);

  if (foldersLoading || resourcesLoading) {
    return <AppShell><Box sx={{ display: 'flex', justifyContent: 'center', mt: 4 }}><CircularProgress /></Box></AppShell>;
  }

  return (
    <AppShell>
      <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', mb: 1 }}>
        <Box>
          <Breadcrumb crumbs={crumbs} />
          <Typography variant="caption" color="text.secondary">
            {[
              `${subFolders.length} Ordner`,
              ...(id != null ? [`${resources.length} ${resources.length === 1 ? 'Datei' : 'Dateien'}`] : []),
            ].join(' · ')}
          </Typography>
        </Box>
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.5 }}>
          <Tooltip title="Kachelansicht">
            <IconButton size="small" onClick={() => toggleView('grid')} color={viewMode === 'grid' ? 'primary' : 'default'}>
              <GridViewIcon fontSize="small" />
            </IconButton>
          </Tooltip>
          <Tooltip title="Listenansicht">
            <IconButton size="small" onClick={() => toggleView('list')} color={viewMode === 'list' ? 'primary' : 'default'}>
              <ViewListIcon fontSize="small" />
            </IconButton>
          </Tooltip>
          <FolderActions parentId={id} folderId={id} canWrite={canWrite} canUpload={canUpload}
            contextFolder={contextFolder} menuAnchor={menuAnchor}
            onMenuClose={() => { setMenuAnchor(null); setContextFolder(null); }} />
        </Box>
      </Box>


      {subFolders.length > 0 && (
        <>
          <Typography variant="subtitle2" color="text.secondary" gutterBottom>Ordner</Typography>
          {viewMode === 'grid'
            ? <FolderGrid folders={subFolders} showMenu={canFolderMenu} onMenuOpen={(e, f) => { setMenuAnchor(e.currentTarget as HTMLElement); setContextFolder(f); }} />
            : <FolderList folders={subFolders} showMenu={canFolderMenu} onMenuOpen={(e, f) => { setMenuAnchor(e.currentTarget as HTMLElement); setContextFolder(f); }} />
          }
          <Divider sx={{ my: 2 }} />
        </>
      )}

      {id != null && (
        <>
          <Typography variant="subtitle2" color="text.secondary" gutterBottom>Dateien</Typography>
          {viewMode === 'grid'
            ? <ResourceGrid resources={resources} folderId={id} canDownload={canDownload} canEdit={canEdit} canDelete={canDelete} canReorder={canReorder} canMove={canMove} canShare={canShare} onReorder={handleReorder} />
            : <ResourceList resources={resources} folderId={id} canDownload={canDownload} canEdit={canEdit} canDelete={canDelete} canReorder={canReorder} canMove={canMove} canShare={canShare} onReorder={handleReorder} />
          }
          {canUpload && <Box sx={{ mt: 2 }}><DropZone folderId={id} /></Box>}
        </>
      )}

      {viewId != null && viewType !== 'image' && (
        <Dialog open fullWidth maxWidth="lg" onClose={() => setSearchParams({})}>
          <DialogTitle sx={{ display: 'flex', alignItems: 'center', pb: 0 }}>
            <Box sx={{ flexGrow: 1 }}>
              {viewResourceTitle}
            </Box>
            <Link component="button" variant="body2" onClick={() => setSearchParams({})}>Ansicht schließen</Link>
          </DialogTitle>
          <DialogContent>
            {viewType === 'video' && streamUrl && (
              <>
                <VideoPlayer
                  src={streamUrl}
                  subtitles={subtitles}
                  initialTime={seekToTime}
                  onPlayerReady={setPlayer}
                />
                {canUpload && (
                  <Box sx={{ mt: 1, display: 'flex', justifyContent: 'flex-end' }}>
                    <Button size="small" startIcon={<ContentCutIcon />} onClick={() => setTrimMode(v => !v)}>
                      {trimMode ? 'Ausschnitt verwerfen' : 'Ausschnitt erstellen'}
                    </Button>
                  </Box>
                )}
                {trimMode && (
                  <MediaTrimBar
                    player={player}
                    creating={clipJobsRunning}
                    defaultTitle={viewResourceTitle ?? ''}
                    onCreateClip={(startMs, endMs, title) => {
                      if (!viewId) return;
                      createClip.mutate(
                        { id: viewId, startMs, endMs, title },
                        {
                          onSuccess: () => {
                            setTrimMode(false);
                            void qc.invalidateQueries({ queryKey: ['clipJobs', viewId] });
                            setClipSnack({ open: true, severity: 'success', msg: 'Clip wird erstellt…' });
                          },
                          onError: () => {
                            setClipSnack({ open: true, severity: 'error', msg: 'Clip konnte nicht erstellt werden.' });
                          },
                        }
                      );
                    }}
                  />
                )}
              </>
            )}
            {viewType === 'audio' && streamUrl && (
              <VideoPlayer
                src={streamUrl}
                mimeType={resources.find(r => r.id === viewId)?.mimeType ?? 'audio/mpeg'}
                audioOnly
                subtitles={subtitles}
                initialTime={seekToTime}
              />
            )}
            {viewType === 'pdf' && <PdfViewer src={getPreviewUrl(viewId)} />}
          </DialogContent>
        </Dialog>
      )}
      {viewType === 'image' && viewId != null && (
        <ImageLightbox
          images={images}
          currentId={viewId}
          onClose={() => setSearchParams({})}
          onNavigate={(id) => setSearchParams({ view: String(id), type: 'image' })}
        />
      )}

      <Snackbar
        open={clipSnack.open}
        autoHideDuration={6000}
        onClose={() => setClipSnack(s => ({ ...s, open: false }))}
        anchorOrigin={{ vertical: 'bottom', horizontal: 'center' }}
      >
        <Alert severity={clipSnack.severity} onClose={() => setClipSnack(s => ({ ...s, open: false }))}>
          {clipSnack.msg}
        </Alert>
      </Snackbar>
    </AppShell>
  );
}
