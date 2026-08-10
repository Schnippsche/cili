import {Typography} from '@mui/material';
import AppShell from '../../components/layout/AppShell';
import GpuMetricsPanel from '../../components/admin/GpuMetricsPanel';

export default function GpuPage() {
  return (
      <AppShell>
        <Typography variant="h5" gutterBottom>GPU-Monitoring</Typography>
        <GpuMetricsPanel/>
      </AppShell>
  );
}
