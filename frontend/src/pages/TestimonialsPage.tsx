import { Typography } from '@mui/material';
import AppShell from '../components/layout/AppShell';
import TestimonialList from '../components/testimonial/TestimonialList';
import { useTestimonialsPermissions } from '../hooks/useAcl';
import { useSelector } from 'react-redux';
import type { RootState } from '../store/store';
import { useSearchParams } from 'react-router-dom';

export default function TestimonialsPage() {
  const user = useSelector((s: RootState) => s.auth.user);
  const isAdmin = user?.role === 'ADMIN';
  const { data: perms } = useTestimonialsPermissions();
  const [searchParams] = useSearchParams();
  const highlightId = searchParams.get('id') ? Number(searchParams.get('id')) : undefined;

  const canWrite  = isAdmin || (perms?.permissions.includes('WRITE')  ?? false);
  const canDelete = isAdmin || (perms?.permissions.includes('DELETE') ?? false);

  return (
    <AppShell>
      <Typography variant="h5" gutterBottom>Erfahrungsberichte</Typography>
      <TestimonialList canWrite={canWrite} canDelete={canDelete} highlightId={highlightId} />
    </AppShell>
  );
}
