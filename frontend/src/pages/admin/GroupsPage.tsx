import { Typography } from '@mui/material';
import AppShell from '../../components/layout/AppShell';
import GroupTable from '../../components/admin/GroupTable';

export default function GroupsPage() {
  return (
    <AppShell>
      <Typography variant="h5" gutterBottom>Gruppenverwaltung</Typography>
      <GroupTable />
    </AppShell>
  );
}
