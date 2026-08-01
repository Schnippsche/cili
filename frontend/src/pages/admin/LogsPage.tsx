import {Typography} from '@mui/material';
import AppShell from '../../components/layout/AppShell';
import LogViewer from '../../components/admin/LogViewer';

export default function LogsPage() {
  return (
      <AppShell>
        <Typography variant="h5" gutterBottom>Server-Log</Typography>
        <LogViewer/>
      </AppShell>
  );
}
