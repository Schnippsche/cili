import {Typography} from '@mui/material';
import AppShell from '../../components/layout/AppShell';
import UserTable from '../../components/admin/UserTable';

export default function UsersPage() {
  return (
      <AppShell>
        <Typography variant="h5" gutterBottom>Benutzerverwaltung</Typography>
        <UserTable/>
      </AppShell>
  );
}
