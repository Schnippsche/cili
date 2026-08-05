import {useState} from 'react';
import {useNavigate} from 'react-router-dom';
import {
  Alert, Box, Button, Dialog, DialogActions, DialogContent, DialogTitle,
  List, ListItemButton, ListItemText, TextField, Typography
} from '@mui/material';
import AddIcon from '@mui/icons-material/Add';
import PeopleOutlinedIcon from '@mui/icons-material/PeopleOutlined';
import AppShell from '../components/layout/AppShell';
import {useCreateCustomer, useCustomers} from '../hooks/useCustomers';

export default function CustomersPage() {
  const navigate = useNavigate();
  const {data: customers = [], isLoading} = useCustomers();
  const createMutation = useCreateCustomer();

  const [createOpen, setCreateOpen] = useState(false);
  const [name, setName] = useState('');
  const [email, setEmail] = useState('');

  const resetDialog = () => {
    setCreateOpen(false);
    setName('');
    setEmail('');
  };

  const handleCreate = () => {
    if (!name.trim() || !email.trim()) return;
    createMutation.mutate({name: name.trim(), email: email.trim()}, {onSuccess: resetDialog});
  };

  return (
      <AppShell>
        <Box sx={{p: 3}}>
          <Box sx={{display: 'flex', alignItems: 'center', mb: 3, gap: 2}}>
            <PeopleOutlinedIcon color="primary"/>
            <Typography variant="h5" fontWeight="bold">Kunden</Typography>
            <Box sx={{flexGrow: 1}}/>
            <Button variant="contained" startIcon={<AddIcon/>} onClick={() => setCreateOpen(true)}>
              Neuer Kunde
            </Button>
          </Box>

          {isLoading && <Typography color="text.secondary">Lädt…</Typography>}
          {!isLoading && customers.length === 0 && (
              <Typography color="text.secondary">Noch keine Kunden angelegt.</Typography>
          )}

          <List>
            {customers.map(c => (
                <ListItemButton key={c.id} onClick={() => navigate(`/customers/${c.id}`)}>
                  <ListItemText primary={c.name} secondary={c.email}/>
                </ListItemButton>
            ))}
          </List>

          <Dialog open={createOpen} onClose={resetDialog} maxWidth="xs" fullWidth>
            <DialogTitle>Neuer Kunde</DialogTitle>
            <DialogContent>
              {createMutation.isError && (
                  <Alert severity="error" sx={{mb: 2}}>
                    Kunde konnte nicht angelegt werden — existiert die E-Mail bereits?
                  </Alert>
              )}
              <TextField autoFocus fullWidth label="Name" value={name}
                         onChange={e => setName(e.target.value)} sx={{mt: 1, mb: 2}}/>
              <TextField fullWidth label="E-Mail" value={email}
                         onChange={e => setEmail(e.target.value)}/>
            </DialogContent>
            <DialogActions>
              <Button onClick={resetDialog}>Abbrechen</Button>
              <Button variant="contained" onClick={handleCreate}
                      disabled={!name.trim() || !email.trim()}>
                Anlegen
              </Button>
            </DialogActions>
          </Dialog>
        </Box>
      </AppShell>
  );
}
