import {Box, CircularProgress, Divider, Typography} from '@mui/material';
import {useState} from 'react';
import {useQuery} from '@tanstack/react-query';
import {useSelector} from 'react-redux';
import AppShell from '../components/layout/AppShell';
import FolderGrid from '../components/folder/FolderGrid';
import FolderActions from '../components/folder/FolderActions';
import ResourceGrid from '../components/resource/ResourceGrid';
import RecentResourceList from '../components/resource/RecentResourceList';
import {useFolderChildren} from '../hooks/useFolders';
import {getRecentResources, getResourceFavorites} from '../api/resources';
import type {RootState} from '../store/store';
import type {FolderDto} from '../types/api';

export default function DashboardPage() {
  const isAdmin = useSelector((s: RootState) => s.auth.user?.role === 'ADMIN');
  const {data: rootFolders = [], isLoading: foldersLoading} = useFolderChildren();
  const {data: favorites = [], isLoading: favLoading} = useQuery({
    queryKey: ['resources', 'favorites'],
    queryFn: getResourceFavorites,
  });
  const {data: recent = [], isLoading: recentLoading} = useQuery({
    queryKey: ['resources', 'recent'],
    queryFn: () => getRecentResources(),
  });

  const [menuAnchor, setMenuAnchor] = useState<HTMLElement | null>(null);
  const [contextFolder, setContextFolder] = useState<FolderDto | null>(null);

  if (foldersLoading || favLoading || recentLoading) {
    return <AppShell><Box sx={{
      display: 'flex',
      justifyContent: 'center',
      mt: 4
    }}><CircularProgress/></Box></AppShell>;
  }

  return (
      <AppShell>
        <Box sx={{display: 'flex', justifyContent: 'space-between', mb: 2}}>
          <Typography variant="h5">Startseite</Typography>
          <FolderActions parentId={undefined} canWrite={isAdmin} contextFolder={contextFolder}
                         menuAnchor={menuAnchor}
                         onMenuClose={() => {
                           setMenuAnchor(null);
                           setContextFolder(null);
                         }}/>
        </Box>

        {rootFolders.length > 0 && (
            <>
              <Typography variant="subtitle2" color="text.secondary"
                          gutterBottom>Ordner</Typography>
              <FolderGrid folders={rootFolders} showMenu={isAdmin} onMenuOpen={(e, f) => {
                setMenuAnchor(e.currentTarget as HTMLElement);
                setContextFolder(f);
              }}/>
              <Divider sx={{my: 3}}/>
            </>
        )}

        {favorites.length > 0 && (
            <>
              <Typography variant="subtitle2" color="text.secondary" gutterBottom>Favorisierte
                Dateien</Typography>
              <ResourceGrid resources={favorites} folderId={0}/>
            </>
        )}

        {recent.length > 0 && (
            <>
              {(rootFolders.length > 0 || favorites.length > 0) && <Divider sx={{my: 3}}/>}
              <Typography variant="subtitle2" color="text.secondary" gutterBottom>Aktuellste
                Dateien</Typography>
              <RecentResourceList items={recent}/>
            </>
        )}

        {rootFolders.length === 0 && favorites.length === 0 && recent.length === 0 && (
            <Typography color="text.secondary">Noch keine Ordner. Klicke auf „Neuer Ordner" um zu
              beginnen.</Typography>
        )}

      </AppShell>
  );
}
