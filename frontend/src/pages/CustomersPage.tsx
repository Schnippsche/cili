import {useState} from 'react';
import {useNavigate} from 'react-router-dom';
import {
  Alert, Box, Button, Chip, Dialog, DialogActions, DialogContent, DialogTitle,
  FormControlLabel, IconButton, MenuItem, Paper, Switch, Table, TableBody, TableCell,
  TableContainer, TableHead, TableRow, TextField, Tooltip, Typography
} from '@mui/material';
import AddIcon from '@mui/icons-material/Add';
import EditOutlinedIcon from '@mui/icons-material/EditOutlined';
import OpenInNewIcon from '@mui/icons-material/OpenInNew';
import PeopleOutlinedIcon from '@mui/icons-material/PeopleOutlined';
import AppShell from '../components/layout/AppShell';
import {useCreateCustomer, useCustomers, useUpdateCustomer} from '../hooks/useCustomers';
import type {CreateCustomerRequest, CustomerDto, UpdateCustomerRequest} from '../types/api';

const GENDER_OPTIONS: {value: '' | 'MAENNLICH' | 'WEIBLICH'; label: string}[] = [
  {value: '', label: 'Keine Angabe'},
  {value: 'MAENNLICH', label: 'Männlich'},
  {value: 'WEIBLICH', label: 'Weiblich'},
];

function genderLabel(gender: CustomerDto['gender']): string {
  return GENDER_OPTIONS.find(o => o.value === gender)?.label ?? '–';
}

// ── Edit Customer Dialog ─────────────────────────────────────────────────────

function EditCustomerDialog({customer, onClose}: Readonly<{ customer: CustomerDto; onClose: () => void }>) {
  const updateMutation = useUpdateCustomer();
  const [form, setForm] = useState({
    name: customer.name,
    firstName: customer.firstName ?? '',
    email: customer.email,
    mobilePhone: customer.mobilePhone ?? '',
    birthDate: customer.birthDate ?? '',
    memberId: customer.memberId != null ? String(customer.memberId) : '',
    gender: (customer.gender ?? '') as '' | 'MAENNLICH' | 'WEIBLICH',
    informalAddress: customer.informalAddress ?? false,
  });

  const memberIdError = form.memberId.trim() !== '' && !/^\d{6}$/.test(form.memberId.trim());
  const canSubmit = !!form.name.trim() && !!form.email.trim() && !memberIdError && !updateMutation.isPending;

  const handleSave = () => {
    if (!canSubmit) return;
    const request: UpdateCustomerRequest = {
      name: form.name.trim(),
      email: form.email.trim(),
      firstName: form.firstName.trim() || undefined,
      mobilePhone: form.mobilePhone.trim() || undefined,
      birthDate: form.birthDate || undefined,
      memberId: form.memberId.trim() ? Number(form.memberId.trim()) : undefined,
      gender: form.gender || undefined,
      informalAddress: form.informalAddress,
    };
    updateMutation.mutate({id: customer.id, req: request}, {onSuccess: onClose});
  };

  return (
      <Dialog open onClose={onClose} maxWidth="sm" fullWidth>
        <DialogTitle>Kunde bearbeiten – <strong>{customer.name}</strong></DialogTitle>
        <DialogContent>
          {updateMutation.isError && (
              <Alert severity="error" sx={{mb: 2}}>
                Kunde konnte nicht gespeichert werden — existiert die E-Mail bereits?
              </Alert>
          )}
          <Box sx={{display: 'flex', flexWrap: 'wrap', gap: 2, mt: 1}}>
            <TextField autoFocus label="Vorname" value={form.firstName}
                       onChange={e => setForm({...form, firstName: e.target.value})}
                       sx={{flex: '1 1 45%'}}/>
            <TextField required label="Name" value={form.name}
                       onChange={e => setForm({...form, name: e.target.value})}
                       sx={{flex: '1 1 45%'}}/>
            <TextField required label="E-Mail" value={form.email}
                       onChange={e => setForm({...form, email: e.target.value})}
                       sx={{flex: '1 1 45%'}}/>
            <TextField label="Handynummer" value={form.mobilePhone}
                       onChange={e => setForm({...form, mobilePhone: e.target.value})}
                       sx={{flex: '1 1 45%'}}/>
            <TextField type="date" label="Geburtsdatum" value={form.birthDate}
                       onChange={e => setForm({...form, birthDate: e.target.value})}
                       slotProps={{inputLabel: {shrink: true}}}
                       sx={{flex: '1 1 45%'}}/>
            <TextField type="number" label="Mitgliedsnummer" value={form.memberId}
                       onChange={e => setForm({...form, memberId: e.target.value})}
                       error={memberIdError}
                       helperText={memberIdError ? 'Muss 6-stellig sein' : ' '}
                       sx={{flex: '1 1 45%'}}/>
            <TextField select label="Geschlecht" value={form.gender}
                       onChange={e => setForm({
                         ...form,
                         gender: e.target.value as '' | 'MAENNLICH' | 'WEIBLICH'
                       })}
                       sx={{flex: '1 1 45%'}}>
              {GENDER_OPTIONS.map(opt => (
                  <MenuItem key={opt.value} value={opt.value}>{opt.label}</MenuItem>
              ))}
            </TextField>
            <FormControlLabel
                sx={{flex: '1 1 45%'}}
                control={<Switch checked={form.informalAddress}
                                 onChange={e => setForm({...form, informalAddress: e.target.checked})}/>}
                label="Du-Ansprache (informell)"/>
          </Box>
        </DialogContent>
        <DialogActions>
          <Button onClick={onClose}>Abbrechen</Button>
          <Button variant="contained" disabled={!canSubmit} onClick={handleSave}>
            Speichern
          </Button>
        </DialogActions>
      </Dialog>
  );
}

export default function CustomersPage() {
  const navigate = useNavigate();
  const {data: customers = [], isLoading} = useCustomers();
  const createMutation = useCreateCustomer();

  const [createOpen, setCreateOpen] = useState(false);
  const [name, setName] = useState('');
  const [firstName, setFirstName] = useState('');
  const [email, setEmail] = useState('');
  const [mobilePhone, setMobilePhone] = useState('');
  const [birthDate, setBirthDate] = useState('');
  const [memberId, setMemberId] = useState('');
  const [gender, setGender] = useState<'' | 'MAENNLICH' | 'WEIBLICH'>('');
  const [informalAddress, setInformalAddress] = useState(false);
  const [editCustomer, setEditCustomer] = useState<CustomerDto | null>(null);

  const memberIdError = memberId.trim() !== '' && !/^\d{6}$/.test(memberId.trim());

  const resetDialog = () => {
    setCreateOpen(false);
    setName('');
    setFirstName('');
    setEmail('');
    setMobilePhone('');
    setBirthDate('');
    setMemberId('');
    setGender('');
    setInformalAddress(false);
  };

  const handleCreate = () => {
    if (!name.trim() || !email.trim() || memberIdError) return;

    const request: CreateCustomerRequest = {
      name: name.trim(),
      email: email.trim(),
      firstName: firstName.trim() || undefined,
      mobilePhone: mobilePhone.trim() || undefined,
      birthDate: birthDate || undefined,
      memberId: memberId.trim() ? Number(memberId.trim()) : undefined,
      gender: gender || undefined,
      informalAddress: informalAddress || undefined,
    };
    createMutation.mutate(request, {onSuccess: resetDialog});
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

          {!isLoading && customers.length > 0 && (
              <TableContainer component={Paper} variant="outlined" sx={{overflowX: 'auto'}}>
                <Table size="small">
                  <TableHead>
                    <TableRow>
                      <TableCell>Name</TableCell>
                      <TableCell>Vorname</TableCell>
                      <TableCell>E-Mail</TableCell>
                      <TableCell>Handynummer</TableCell>
                      <TableCell>Geburtsdatum</TableCell>
                      <TableCell>Mitgliedsnummer</TableCell>
                      <TableCell>Geschlecht</TableCell>
                      <TableCell>Ansprache</TableCell>
                      <TableCell>Einwilligung</TableCell>
                      <TableCell align="right">Aktionen</TableCell>
                    </TableRow>
                  </TableHead>
                  <TableBody>
                    {customers.map(c => (
                        <TableRow key={c.id} hover>
                          <TableCell>{c.name}</TableCell>
                          <TableCell>{c.firstName ?? '–'}</TableCell>
                          <TableCell>{c.email}</TableCell>
                          <TableCell>{c.mobilePhone ?? '–'}</TableCell>
                          <TableCell>{c.birthDate ?? '–'}</TableCell>
                          <TableCell>{c.memberId ?? '–'}</TableCell>
                          <TableCell>{genderLabel(c.gender)}</TableCell>
                          <TableCell>{c.informalAddress ? 'Du' : 'Sie'}</TableCell>
                          <TableCell>
                            <Chip label={c.consentGranted ? 'Erteilt' : 'Widerrufen'}
                                  color={c.consentGranted ? 'success' : 'default'}
                                  size="small"/>
                          </TableCell>
                          <TableCell align="right">
                            <Tooltip title="Details / Mailflows">
                              <IconButton size="small" onClick={() => navigate(`/customers/${c.id}`)}>
                                <OpenInNewIcon fontSize="small"/>
                              </IconButton>
                            </Tooltip>
                            <Tooltip title="Bearbeiten">
                              <IconButton size="small" onClick={() => setEditCustomer(c)}>
                                <EditOutlinedIcon fontSize="small"/>
                              </IconButton>
                            </Tooltip>
                          </TableCell>
                        </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </TableContainer>
          )}

          {editCustomer && (
              <EditCustomerDialog customer={editCustomer} onClose={() => setEditCustomer(null)}/>
          )}

          <Dialog open={createOpen} onClose={resetDialog} maxWidth="sm" fullWidth>
            <DialogTitle>Neuer Kunde</DialogTitle>
            <DialogContent>
              {createMutation.isError && (
                  <Alert severity="error" sx={{mb: 2}}>
                    Kunde konnte nicht angelegt werden — existiert die E-Mail bereits?
                  </Alert>
              )}
              <Box sx={{display: 'flex', flexWrap: 'wrap', gap: 2, mt: 1}}>
                <TextField autoFocus label="Vorname" value={firstName}
                           onChange={e => setFirstName(e.target.value)}
                           sx={{flex: '1 1 45%'}}/>
                <TextField required label="Name" value={name}
                           onChange={e => setName(e.target.value)}
                           sx={{flex: '1 1 45%'}}/>
                <TextField required label="E-Mail" value={email}
                           onChange={e => setEmail(e.target.value)}
                           sx={{flex: '1 1 45%'}}/>
                <TextField label="Handynummer" value={mobilePhone}
                           onChange={e => setMobilePhone(e.target.value)}
                           sx={{flex: '1 1 45%'}}/>
                <TextField type="date" label="Geburtsdatum" value={birthDate}
                           onChange={e => setBirthDate(e.target.value)}
                           slotProps={{inputLabel: {shrink: true}}}
                           sx={{flex: '1 1 45%'}}/>
                <TextField type="number" label="Mitgliedsnummer" value={memberId}
                           onChange={e => setMemberId(e.target.value)}
                           error={memberIdError}
                           helperText={memberIdError ? 'Muss 6-stellig sein' : ' '}
                           sx={{flex: '1 1 45%'}}/>
                <TextField select label="Geschlecht" value={gender}
                           onChange={e => setGender(e.target.value as '' | 'MAENNLICH' | 'WEIBLICH')}
                           sx={{flex: '1 1 45%'}}>
                  {GENDER_OPTIONS.map(opt => (
                      <MenuItem key={opt.value} value={opt.value}>{opt.label}</MenuItem>
                  ))}
                </TextField>
                <FormControlLabel
                    sx={{flex: '1 1 45%'}}
                    control={<Switch checked={informalAddress}
                                     onChange={e => setInformalAddress(e.target.checked)}/>}
                    label="Du-Ansprache (informell)"/>
              </Box>
            </DialogContent>
            <DialogActions>
              <Button onClick={resetDialog}>Abbrechen</Button>
              <Button variant="contained" onClick={handleCreate}
                      disabled={!name.trim() || !email.trim() || memberIdError}>
                Anlegen
              </Button>
            </DialogActions>
          </Dialog>
        </Box>
      </AppShell>
  );
}
