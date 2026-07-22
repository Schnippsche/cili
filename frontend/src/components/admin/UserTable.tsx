import {
  Alert, Autocomplete, Box, Button, Chip, Dialog, DialogActions, DialogContent, DialogTitle,
  FormControl, FormControlLabel, IconButton, InputLabel, MenuItem, Select, Snackbar, Switch,
  Table, TableBody, TableCell, TableContainer, TableHead, TablePagination, TableRow,
  TextField, Paper, Tooltip, Typography,
} from '@mui/material';
import DeleteOutlineIcon from '@mui/icons-material/DeleteOutline';
import EditOutlinedIcon from '@mui/icons-material/EditOutlined';
import GroupOutlinedIcon from '@mui/icons-material/GroupOutlined';
import LockResetIcon from '@mui/icons-material/LockReset';
import PrintOutlinedIcon from '@mui/icons-material/PrintOutlined';
import { useState } from 'react';
import { useSelector } from 'react-redux';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import * as adminApi from '../../api/admin';
import type { RootState } from '../../store/store';
import type { CreateUserRequest, GroupDto, UpdateUserRequest, UserDto } from '../../types/api';
import { useIsMobile } from '../../hooks/useIsMobile';
import { extractBlobErrorMessage } from '../../utils/blobError';

function extractErrorMessage(err: unknown): string {
  if (err && typeof err === 'object' && 'response' in err) {
    const resp = (err as { response?: { data?: { message?: string } } }).response;
    if (resp?.data?.message) return resp.data.message;
  }
  return 'Ein unbekannter Fehler ist aufgetreten.';
}

// ── User Groups Dialog ───────────────────────────────────────────────────────

function UserGroupsDialog({ user, onClose }: Readonly<{ user: UserDto; onClose: () => void }>) {
  const isMobile = useIsMobile();
  const qc = useQueryClient();
  const [selectedGroup, setSelectedGroup] = useState<GroupDto | null>(null);

  const { data: groups = [], isLoading: loadingGroups } = useQuery({
    queryKey: ['admin', 'users', user.id, 'groups'],
    queryFn: () => adminApi.listUserGroups(user.id),
  });
  const { data: allGroups } = useQuery({
    queryKey: ['admin', 'groups', 'all'],
    queryFn: () => adminApi.listGroups(0, 500),
  });

  const groupIds = new Set(groups.map(g => g.id));
  const availableGroups = allGroups?.content.filter(g => !groupIds.has(g.id)) ?? [];

  function invalidate() {
    void qc.invalidateQueries({ queryKey: ['admin', 'users', user.id, 'groups'] });
    void qc.invalidateQueries({ queryKey: ['admin', 'groups'] });
  }

  const addGroup = useMutation({
    mutationFn: () => {
      if (!selectedGroup) return Promise.reject(new Error('incomplete'));
      return adminApi.addGroupMember(selectedGroup.id, user.id);
    },
    onSuccess: () => { invalidate(); setSelectedGroup(null); },
  });
  const removeGroup = useMutation({
    mutationFn: (groupId: number) => adminApi.removeGroupMember(groupId, user.id),
    onSuccess: invalidate,
  });

  return (
    <Dialog open onClose={onClose} maxWidth="xs" fullWidth fullScreen={isMobile}>
      <DialogTitle>
        Gruppen: <strong>{user.username}</strong>
      </DialogTitle>
      <DialogContent>
        <Box sx={{ display: 'flex', gap: 1, mb: 2 }}>
          <Autocomplete
            sx={{ flex: 1 }}
            options={availableGroups}
            getOptionLabel={g => g.name}
            value={selectedGroup}
            onChange={(_, v) => setSelectedGroup(v)}
            renderInput={params => <TextField {...params} label="Gruppe hinzufügen" size="small" />}
          />
          <Button variant="contained" disabled={!selectedGroup || addGroup.isPending}
            onClick={() => addGroup.mutate()}>
            Hinzufügen
          </Button>
        </Box>
        {loadingGroups && <Typography variant="body2">Lade…</Typography>}
        {!loadingGroups && groups.length === 0 && (
          <Typography variant="body2" color="text.secondary">Keine Gruppen</Typography>
        )}
        <Box sx={{ display: 'flex', gap: 0.5, flexWrap: 'wrap' }}>
          {groups.map(g => (
            <Chip key={g.id} label={g.name} onDelete={() => removeGroup.mutate(g.id)} />
          ))}
        </Box>
      </DialogContent>
      <DialogActions><Button onClick={onClose}>Schließen</Button></DialogActions>
    </Dialog>
  );
}

// ── Delete Confirm Dialog ────────────────────────────────────────────────────

function DeleteConfirmDialog({ user, onConfirm, onClose }: Readonly<{ user: UserDto; onConfirm: () => void; onClose: () => void }>) {
  const isMobile = useIsMobile();
  return (
    <Dialog open onClose={onClose} maxWidth="xs" fullWidth fullScreen={isMobile}>
      <DialogTitle>Benutzer löschen</DialogTitle>
      <DialogContent>
        Soll der Benutzer <strong>{user.username}</strong> wirklich gelöscht werden? Diese Aktion kann nicht rückgängig gemacht werden.
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>Abbrechen</Button>
        <Button variant="contained" color="error" onClick={() => { onConfirm(); onClose(); }}>
          Löschen
        </Button>
      </DialogActions>
    </Dialog>
  );
}

// ── Edit User Dialog ─────────────────────────────────────────────────────────

function EditUserDialog({ user, onClose }: Readonly<{ user: UserDto; onClose: () => void }>) {
  const isMobile = useIsMobile();
  const qc = useQueryClient();
  const [form, setForm] = useState<UpdateUserRequest>({
    email: user.email,
    displayName: user.displayName ?? '',
    memberId: user.memberId ?? undefined,
    url: user.url ?? '',
    phone: user.phone ?? '',
    role: user.role,
    active: user.active,
  });
  const [error, setError] = useState<string | null>(null);

  const update = useMutation({
    mutationFn: () => adminApi.updateUser(user.id, form),
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: ['admin', 'users'], exact: false });
      onClose();
    },
    onError: (err) => setError(extractErrorMessage(err)),
  });

  const canSubmit = !!form.email && !update.isPending;

  return (
    <Dialog open onClose={onClose} maxWidth="xs" fullWidth fullScreen={isMobile}>
      <DialogTitle>Benutzer bearbeiten – <strong>{user.username}</strong></DialogTitle>
      <DialogContent sx={{ display: 'flex', flexDirection: 'column', gap: 2, mt: 1 }}>
        {error && <Alert severity="error">{error}</Alert>}
        <TextField
          label="E-Mail" type="email" value={form.email ?? ''} fullWidth
          onChange={e => { setForm({ ...form, email: e.target.value }); setError(null); }}
        />
        <TextField
          label="Anzeigename" value={form.displayName ?? ''} fullWidth
          onChange={e => setForm({ ...form, displayName: e.target.value })}
        />
        <TextField
          label="Mitglieds-ID" type="number" value={form.memberId ?? ''} fullWidth
          onChange={e => setForm({ ...form, memberId: e.target.value === '' ? undefined : Number(e.target.value) })}
        />
        <TextField
          label="URL" value={form.url ?? ''} fullWidth
          onChange={e => setForm({ ...form, url: e.target.value })}
        />
        <TextField
          label="Telefon" value={form.phone ?? ''} fullWidth
          onChange={e => setForm({ ...form, phone: e.target.value })}
        />
        <FormControl fullWidth>
          <InputLabel>Rolle</InputLabel>
          <Select value={form.role ?? 'USER'} label="Rolle"
            onChange={e => setForm({ ...form, role: e.target.value })}>
            <MenuItem value="USER">USER</MenuItem>
            <MenuItem value="ADMIN">ADMIN</MenuItem>
          </Select>
        </FormControl>
        <FormControlLabel
          control={
            <Switch checked={form.active ?? true}
              onChange={e => setForm({ ...form, active: e.target.checked })} />
          }
          label="Aktiv"
        />
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>Abbrechen</Button>
        <Button variant="contained" disabled={!canSubmit} onClick={() => update.mutate()}>
          Speichern
        </Button>
      </DialogActions>
    </Dialog>
  );
}

// ── Reset Password Dialog ────────────────────────────────────────────────────

function ResetPasswordDialog({ user, onClose }: Readonly<{ user: UserDto; onClose: () => void }>) {
  const isMobile = useIsMobile();
  const [password, setPassword] = useState('');
  const [confirm, setConfirm] = useState('');
  const [error, setError] = useState<string | null>(null);

  const reset = useMutation({
    mutationFn: () => adminApi.updateUser(user.id, { password }),
    onSuccess: onClose,
    onError: (err) => setError(extractErrorMessage(err)),
  });

  const mismatch = confirm.length > 0 && password !== confirm;
  const canSubmit = password.length >= 8 && password === confirm && !reset.isPending;

  return (
    <Dialog open onClose={onClose} maxWidth="xs" fullWidth fullScreen={isMobile}>
      <DialogTitle>Passwort zurücksetzen – <strong>{user.username}</strong></DialogTitle>
      <DialogContent sx={{ display: 'flex', flexDirection: 'column', gap: 2, mt: 1 }}>
        {error && <Alert severity="error">{error}</Alert>}
        <TextField
          label="Neues Passwort" type="password" value={password} fullWidth
          helperText="Mindestens 8 Zeichen"
          onChange={e => { setPassword(e.target.value); setError(null); }}
        />
        <TextField
          label="Passwort bestätigen" type="password" value={confirm} fullWidth
          error={mismatch} helperText={mismatch ? 'Passwörter stimmen nicht überein' : ''}
          onChange={e => setConfirm(e.target.value)}
        />
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>Abbrechen</Button>
        <Button variant="contained" disabled={!canSubmit} onClick={() => reset.mutate()}>
          Zurücksetzen
        </Button>
      </DialogActions>
    </Dialog>
  );
}

export default function UserTable() {
  const isMobile = useIsMobile();
  const qc = useQueryClient();
  const currentUserId = useSelector((s: RootState) => s.auth.user?.id);
  const [createOpen, setCreateOpen] = useState(false);
  const [form, setForm] = useState<CreateUserRequest>({ username: '', email: '', password: '', role: 'USER' });
  const [createError, setCreateError] = useState<string | null>(null);
  const [resetUser, setResetUser] = useState<UserDto | null>(null);
  const [editUser, setEditUser] = useState<UserDto | null>(null);
  const [groupsUser, setGroupsUser] = useState<UserDto | null>(null);
  const [deleteUser, setDeleteUser] = useState<UserDto | null>(null);
  const [page, setPage] = useState(0);
  const [rowsPerPage, setRowsPerPage] = useState(20);
  const [labelError, setLabelError] = useState<string | null>(null);

  const { data, isLoading } = useQuery({
    queryKey: ['admin', 'users', page, rowsPerPage],
    queryFn: () => adminApi.listUsers(page, rowsPerPage),
  });

  const generateLabels = useMutation({
    mutationFn: (userId: number) => adminApi.generateUserLabels(userId),
    onSuccess: (blob) => {
      const url = URL.createObjectURL(blob);
      window.open(url, '_blank');
      setTimeout(() => URL.revokeObjectURL(url), 60_000);
    },
    onError: (err) => { void extractBlobErrorMessage(err).then(setLabelError); },
  });

  const createUser = useMutation({
    mutationFn: (req: CreateUserRequest) => adminApi.createUser(req),
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: ['admin', 'users'], exact: false });
      setCreateOpen(false);
      setForm({ username: '', email: '', password: '', role: 'USER' });
      setCreateError(null);
    },
    onError: (err) => setCreateError(extractErrorMessage(err)),
  });

  const doDelete = useMutation({
    mutationFn: (id: number) => adminApi.deleteUser(id),
    onSuccess: () => void qc.invalidateQueries({ queryKey: ['admin', 'users'] }),
  });

  function handleClose() {
    setCreateOpen(false);
    setCreateError(null);
    setForm({ username: '', email: '', password: '', role: 'USER' });
  }

  return (
    <>
      <Box sx={{ display: 'flex', justifyContent: 'flex-end', mb: 2 }}>
        <Button variant="contained" onClick={() => setCreateOpen(true)}>Benutzer anlegen</Button>
      </Box>
      <TableContainer component={Paper} variant="outlined" sx={{ overflowX: 'auto' }}>
        <Table size="small">
          <TableHead>
            <TableRow>
              <TableCell>Benutzername</TableCell><TableCell>E-Mail</TableCell>
              <TableCell>Mitglieds-ID</TableCell><TableCell>URL</TableCell><TableCell>Telefon</TableCell>
              <TableCell>Rolle</TableCell><TableCell>Status</TableCell><TableCell align="right">Aktionen</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {isLoading && <TableRow><TableCell colSpan={8}>Lade…</TableCell></TableRow>}
            {data?.content.map(u => (
              <TableRow key={u.id} hover>
                <TableCell>{u.username}</TableCell>
                <TableCell>{u.email}</TableCell>
                <TableCell>{u.memberId ?? '–'}</TableCell>
                <TableCell>{u.url ?? '–'}</TableCell>
                <TableCell>{u.phone ?? '–'}</TableCell>
                <TableCell>{u.role}</TableCell>
                <TableCell><Chip label={u.active ? 'Aktiv' : 'Inaktiv'} color={u.active ? 'success' : 'default'} size="small" /></TableCell>
                <TableCell align="right">
                  <Tooltip title="Bearbeiten">
                    <IconButton size="small" onClick={() => setEditUser(u)}>
                      <EditOutlinedIcon fontSize="small" />
                    </IconButton>
                  </Tooltip>
                  <Tooltip title="Passwort zurücksetzen">
                    <IconButton size="small" onClick={() => setResetUser(u)}>
                      <LockResetIcon fontSize="small" />
                    </IconButton>
                  </Tooltip>
                  <Tooltip title="Gruppen">
                    <IconButton size="small" onClick={() => setGroupsUser(u)}>
                      <GroupOutlinedIcon fontSize="small" />
                    </IconButton>
                  </Tooltip>
                  <Tooltip title="Etikettenbogen (Zweckform 6174) mit QR-Code erzeugen und anzeigen">
                    <span>
                      <IconButton
                        size="small"
                        disabled={generateLabels.isPending && generateLabels.variables === u.id}
                        onClick={() => generateLabels.mutate(u.id)}
                      >
                        <PrintOutlinedIcon fontSize="small" />
                      </IconButton>
                    </span>
                  </Tooltip>
                  {u.id !== currentUserId && (
                    <Tooltip title="Löschen">
                      <IconButton size="small" color="error" onClick={() => setDeleteUser(u)}>
                        <DeleteOutlineIcon fontSize="small" />
                      </IconButton>
                    </Tooltip>
                  )}
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
        <TablePagination
          component="div"
          count={data?.totalElements ?? 0}
          page={page}
          rowsPerPage={rowsPerPage}
          rowsPerPageOptions={[10, 20, 50]}
          onPageChange={(_, newPage) => setPage(newPage)}
          onRowsPerPageChange={e => { setRowsPerPage(parseInt(e.target.value, 10)); setPage(0); }}
          labelRowsPerPage="Zeilen:"
          labelDisplayedRows={({ from, to, count }) => `${from}–${to} von ${count}`}
        />
      </TableContainer>
      {editUser   && <EditUserDialog user={editUser} onClose={() => setEditUser(null)} />}
      {resetUser  && <ResetPasswordDialog user={resetUser} onClose={() => setResetUser(null)} />}
      {groupsUser && <UserGroupsDialog user={groupsUser} onClose={() => setGroupsUser(null)} />}
      {deleteUser && (
        <DeleteConfirmDialog
          user={deleteUser}
          onConfirm={() => doDelete.mutate(deleteUser.id)}
          onClose={() => setDeleteUser(null)}
        />
      )}

      <Dialog open={createOpen} onClose={handleClose} maxWidth="xs" fullWidth fullScreen={isMobile}>
        <DialogTitle>Benutzer anlegen</DialogTitle>
        <DialogContent sx={{ display: 'flex', flexDirection: 'column', gap: 2, mt: 1 }}>
          {createError && <Alert severity="error">{createError}</Alert>}
          <TextField label="Benutzername" value={form.username} onChange={e => setForm({ ...form, username: e.target.value })} fullWidth />
          <TextField label="E-Mail" type="email" value={form.email} onChange={e => setForm({ ...form, email: e.target.value })} fullWidth />
          <TextField
            label="Passwort"
            type="password"
            value={form.password}
            onChange={e => setForm({ ...form, password: e.target.value })}
            helperText="Mindestens 8 Zeichen"
            fullWidth
          />
          <TextField
            label="Mitglieds-ID"
            type="number"
            value={form.memberId ?? ''}
            onChange={e => setForm({ ...form, memberId: e.target.value === '' ? undefined : Number(e.target.value) })}
            fullWidth
          />
          <TextField
            label="URL"
            value={form.url ?? ''}
            onChange={e => setForm({ ...form, url: e.target.value })}
            fullWidth
          />
          <TextField
            label="Telefon"
            value={form.phone ?? ''}
            onChange={e => setForm({ ...form, phone: e.target.value })}
            fullWidth
          />
          <FormControl fullWidth>
            <InputLabel>Rolle</InputLabel>
            <Select value={form.role} label="Rolle" onChange={e => setForm({ ...form, role: e.target.value })}>
              <MenuItem value="USER">USER</MenuItem>
              <MenuItem value="ADMIN">ADMIN</MenuItem>
            </Select>
          </FormControl>
        </DialogContent>
        <DialogActions>
          <Button onClick={handleClose}>Abbrechen</Button>
          <Button
            variant="contained"
            onClick={() => createUser.mutate(form)}
            disabled={!form.username || !form.email || !form.password || createUser.isPending}
          >
            Anlegen
          </Button>
        </DialogActions>
      </Dialog>
      <Snackbar open={!!labelError} autoHideDuration={6000} onClose={() => setLabelError(null)}>
        <Alert severity="error" onClose={() => setLabelError(null)}>{labelError}</Alert>
      </Snackbar>
    </>
  );
}
