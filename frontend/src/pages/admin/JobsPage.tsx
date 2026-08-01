import {Typography} from '@mui/material';
import AppShell from '../../components/layout/AppShell';
import JobTable from '../../components/admin/JobTable';

export default function JobsPage() {
  return (
      <AppShell>
        <Typography variant="h5" gutterBottom>Verarbeitungs-Jobs</Typography>
        <JobTable/>
      </AppShell>
  );
}
