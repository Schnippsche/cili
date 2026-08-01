import {lazy, type ReactNode, Suspense} from 'react';
import {Navigate, Route, Routes} from 'react-router-dom';
import {useSelector} from 'react-redux';
import {Box, CircularProgress} from '@mui/material';
import type {RootState} from './store/store';
import LoginPage from './pages/LoginPage';

const DashboardPage = lazy(() => import('./pages/DashboardPage'));
const FolderPage = lazy(() => import('./pages/FolderPage'));
const SearchPage = lazy(() => import('./pages/SearchPage'));
const TextEditorPage = lazy(() => import('./pages/TextEditorPage'));
const UsersPage = lazy(() => import('./pages/admin/UsersPage'));
const GroupsPage = lazy(() => import('./pages/admin/GroupsPage'));
const JobsPage = lazy(() => import('./pages/admin/JobsPage'));
const LogsPage = lazy(() => import('./pages/admin/LogsPage'));
const BulkImportPage = lazy(() => import('./pages/admin/BulkImportPage'));
const TestimonialsPage = lazy(() => import('./pages/TestimonialsPage'));
const TrashPage = lazy(() => import('./pages/TrashPage'));
const CollectionsPage = lazy(() => import('./pages/CollectionsPage'));
const CollectionDetailPage = lazy(() => import('./pages/CollectionDetailPage'));
const SharePage = lazy(() => import('./pages/SharePage'));
const SharedCollectionPage = lazy(() => import('./pages/SharedCollectionPage'));
const PublicTestimonialsPage = lazy(() => import('./pages/PublicTestimonialsPage'));
const PublicTestimonialDetailPage = lazy(() => import('./pages/PublicTestimonialDetailPage'));
const HelpPage = lazy(() => import('./pages/HelpPage'));

const Spinner = () => <Box
    sx={{display: 'flex', justifyContent: 'center', mt: 10}}><CircularProgress/></Box>;

function ProtectedRoute({children}: Readonly<{ children: ReactNode }>) {
  const isAuthenticated = useSelector((s: RootState) => s.auth.isAuthenticated);
  return isAuthenticated ? <>{children}</> : <Navigate to="/login" replace/>;
}

function AdminRoute({children}: Readonly<{ children: ReactNode }>) {
  const user = useSelector((s: RootState) => s.auth.user);
  if (!user) return <Navigate to="/login" replace/>;
  if (user.role !== 'ADMIN') return <Navigate to="/" replace/>;
  return <>{children}</>;
}

export default function App() {
  return (
      <Suspense fallback={<Spinner/>}>
        <Routes>
          <Route path="/login" element={<LoginPage/>}/>
          <Route path="/erfahrungsberichte" element={<PublicTestimonialsPage/>}/>
          <Route path="/erfahrungsberichte/:id" element={<PublicTestimonialDetailPage/>}/>
          <Route path="/" element={<ProtectedRoute><DashboardPage/></ProtectedRoute>}/>
          <Route path="/folders/:folderId"
                 element={<ProtectedRoute><FolderPage/></ProtectedRoute>}/>
          <Route path="/testimonials"
                 element={<ProtectedRoute><TestimonialsPage/></ProtectedRoute>}/>
          <Route path="/search" element={<ProtectedRoute><SearchPage/></ProtectedRoute>}/>
          <Route path="/resources/:resourceId/edit"
                 element={<ProtectedRoute><TextEditorPage/></ProtectedRoute>}/>
          <Route path="/admin/users" element={<AdminRoute><UsersPage/></AdminRoute>}/>
          <Route path="/admin/groups" element={<AdminRoute><GroupsPage/></AdminRoute>}/>
          <Route path="/admin/jobs" element={<AdminRoute><JobsPage/></AdminRoute>}/>
          <Route path="/admin/logs" element={<AdminRoute><LogsPage/></AdminRoute>}/>
          <Route path="/admin/bulk-import" element={<AdminRoute><BulkImportPage/></AdminRoute>}/>
          <Route path="/trash" element={<AdminRoute><TrashPage/></AdminRoute>}/>
          <Route path="/collections" element={<ProtectedRoute><CollectionsPage/></ProtectedRoute>}/>
          <Route path="/collections/:id"
                 element={<ProtectedRoute><CollectionDetailPage/></ProtectedRoute>}/>
          <Route path="/help" element={<ProtectedRoute><HelpPage/></ProtectedRoute>}/>
          <Route path="/share/:token" element={<SharePage/>}/>
          <Route path="/share/collection/:token" element={<SharedCollectionPage/>}/>
          <Route path="*" element={<Navigate to="/" replace/>}/>
        </Routes>
      </Suspense>
  );
}
