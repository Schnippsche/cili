import {
  Alert, Autocomplete, Box, Button, Checkbox, Chip, Dialog, DialogActions, DialogContent,
  DialogTitle, Divider, FormControl, FormControlLabel, IconButton, InputLabel, List,
  ListItem, ListItemText, MenuItem, Paper, Select, Table, TableBody, TableCell,
  TableContainer, TableHead, TableRow, TextField, Tooltip, Typography,
} from '@mui/material';
import DeleteOutlineIcon from '@mui/icons-material/DeleteOutline';
import EditOutlinedIcon from '@mui/icons-material/EditOutlined';
import PeopleOutlineIcon from '@mui/icons-material/PeopleOutline';
import PersonRemoveIcon from '@mui/icons-material/PersonRemove';
import SecurityOutlinedIcon from '@mui/icons-material/SecurityOutlined';
import { useMemo, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import * as adminApi from '../../api/admin';
import type { AclEntryDto, CreateGroupRequest, GroupDto, UserDto } from '../../types/api';
import { useIsMobile } from '../../hooks/useIsMobile';

const ALL_PERMISSIONS = [
  'READ', 'WRITE', 'DELETE', 'DOWNLOAD', 'UPLOAD',
  'SHARE', 'MANAGE_METADATA', 'MANAGE_SUBTITLES', 'TRANSLATE_SUBTITLES',
] as const;

const EB_LABEL: Record<string, string> = {
  READ: 'Lesen',
  WRITE: 'Bearbeiten',
  DELETE: 'Löschen',
};

function extractErrorMessage(err: unknown): string {
  if (err && typeof err === 'object' && 'response' in err) {
    const resp = (err as { response?: { data?: { message?: string } } }).response;
    if (resp?.data?.message) return resp.data.message;
  }
  return 'Ein unbekannter Fehler ist aufgetreten.';
}

// ── Edit Group Dialog ────────────────────────────────────────────────────────

function EditGroupDialog({ group, onClose }: Readonly<{ group: GroupDto; onClose: () => void }>) {
  const isMobile = useIsMobile();
  const qc = useQueryClient();
  const [name, setName] = useState(group.name);
  const [desc, setDesc] = useState(group.description ?? '');
  const [error, setError] = useState<string | null>(null);

  const update = useMutation({
    mutationFn: () => adminApi.updateGroup(group.id, { name, description: desc }),
    onSuccess: () => { void qc.invalidateQueries({ queryKey: ['admin', 'groups'] }); onClose(); },
    onError: (err) => setError(extractErrorMessage(err)),
  });

  return (
    <Dialog open onClose={onClose} maxWidth="xs" fullWidth fullScreen={isMobile}>
      <DialogTitle>Gruppe bearbeiten</DialogTitle>
      <DialogContent sx={{ display: 'flex', flexDirection: 'column', gap: 2, mt: 1 }}>
        {error && <Alert severity="error">{error}</Alert>}
        <TextField label="Name" value={name} onChange={e => setName(e.target.value)} fullWidth />
        <TextField label="Beschreibung" value={desc} onChange={e => setDesc(e.target.value)} fullWidth multiline rows={2} />
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>Abbrechen</Button>
        <Button variant="contained" disabled={!name || update.isPending} onClick={() => update.mutate()}>
          Speichern
        </Button>
      </DialogActions>
    </Dialog>
  );
}

// ── Members Dialog ───────────────────────────────────────────────────────────

function MembersDialog({ group, onClose }: Readonly<{ group: GroupDto; onClose: () => void }>) {
  const isMobile = useIsMobile();
  const qc = useQueryClient();
  const [selectedUsers, setSelectedUsers] = useState<UserDto[]>([]);

  const { data: members = [], isLoading: loadingMembers } = useQuery({
    queryKey: ['admin', 'groups', group.id, 'members'],
    queryFn: () => adminApi.listGroupMembers(group.id),
  });
  const { data: allUsers } = useQuery({
    queryKey: ['admin', 'users'],
    queryFn: () => adminApi.listUsers(0, 500),
  });

  const memberIds = new Set(members.map(m => m.id));
  const availableUsers = allUsers?.content.filter(u => !memberIds.has(u.id)) ?? [];

  const addMember = useMutation({
    mutationFn: () => Promise.all(selectedUsers.map(u => adminApi.addGroupMember(group.id, u.id))),
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: ['admin', 'groups', group.id, 'members'] });
      void qc.invalidateQueries({ queryKey: ['admin', 'groups'] });
      setSelectedUsers([]);
    },
  });
  const removeMember = useMutation({
    mutationFn: (userId: number) => adminApi.removeGroupMember(group.id, userId),
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: ['admin', 'groups', group.id, 'members'] });
      void qc.invalidateQueries({ queryKey: ['admin', 'groups'] });
    },
  });

  return (
    <Dialog open onClose={onClose} maxWidth="sm" fullWidth fullScreen={isMobile}>
      <DialogTitle>
        Mitglieder: <strong>{group.name}</strong>
        <Typography variant="body2" color="text.secondary">{members.length} Mitglied(er)</Typography>
      </DialogTitle>
      <DialogContent>
        <Box sx={{ display: 'flex', gap: 1, mb: 2 }}>
          <Autocomplete
            sx={{ flex: 1 }}
            multiple
            options={availableUsers}
            getOptionLabel={u => `${u.username} (${u.email})`}
            value={selectedUsers}
            onChange={(_, v) => setSelectedUsers(v)}
            renderInput={params => <TextField {...params} label="User hinzufügen" size="small" />}
            renderTags={(value, getTagProps) =>
              value.map((u, index) => (
                <Chip {...getTagProps({ index })} label={u.username} size="small" />
              ))
            }
          />
          <Button variant="contained" disabled={selectedUsers.length === 0 || addMember.isPending}
            onClick={() => addMember.mutate()}>
            Hinzufügen
          </Button>
        </Box>
        <Divider sx={{ mb: 1 }} />
        {loadingMembers && <Typography variant="body2">Lade…</Typography>}
        {!loadingMembers && members.length === 0 && (
          <Typography variant="body2" color="text.secondary">Keine Mitglieder</Typography>
        )}
        <List dense disablePadding>
          {members.map(m => (
            <ListItem key={m.id} disableGutters
              secondaryAction={
                <Tooltip title="Mitglied entfernen">
                  <IconButton size="small" color="error" onClick={() => removeMember.mutate(m.id)}>
                    <PersonRemoveIcon fontSize="small" />
                  </IconButton>
                </Tooltip>
              }>
              <ListItemText primary={m.username} secondary={m.email} />
              <Chip label={m.role} size="small" sx={{ mr: 4 }} />
            </ListItem>
          ))}
        </List>
      </DialogContent>
      <DialogActions><Button onClick={onClose}>Schließen</Button></DialogActions>
    </Dialog>
  );
}

// ── Permissions Dialog ───────────────────────────────────────────────────────

function PermissionsDialog({ group, onClose }: Readonly<{ group: GroupDto; onClose: () => void }>) {
  const isMobile = useIsMobile();
  const qc = useQueryClient();
  const [selFolder, setSelFolder] = useState<adminApi.FolderItem | null>(null);
  const [selPerms, setSelPerms] = useState<string[]>([]);
  const [inheritable, setInheritable] = useState(true);
  const [addError, setAddError] = useState<string | null>(null);

  const { data: entries = [], isLoading: loadingEntries } = useQuery({
    queryKey: ['acl', 'group', group.id],
    queryFn: () => adminApi.listGroupAclEntries(group.id),
  });
  const { data: folders = [] } = useQuery({
    queryKey: ['acl', 'folders'],
    queryFn: () => adminApi.listAllFolders(),
  });

  const folderMap = useMemo(() => new Map(folders.map(f => [f.id, f])), [folders]);

  const addEntry = useMutation({
    mutationFn: () => {
      if (!selFolder || selPerms.length === 0) return Promise.reject(new Error('incomplete'));
      return Promise.all(
        selPerms.map(p => adminApi.createFolderAclEntry(selFolder.id, group.id, p, 'ALLOW', inheritable))
      );
    },
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: ['acl', 'group', group.id] });
      setSelFolder(null); setSelPerms([]); setInheritable(true);
      setAddError(null);
    },
    onError: (err) => setAddError(extractErrorMessage(err)),
  });

  const removeEntry = useMutation({
    mutationFn: (entryId: number) => adminApi.deleteAclEntry(entryId),
    onSuccess: () => void qc.invalidateQueries({ queryKey: ['acl', 'group', group.id] }),
  });

  const addTestEntry = useMutation({
    mutationFn: (permission: 'READ' | 'WRITE' | 'DELETE') =>
      adminApi.createTestimonialsAclEntry(group.id, permission),
    onSuccess: () => void qc.invalidateQueries({ queryKey: ['acl', 'group', group.id] }),
  });

  const addCollectionsEntry = useMutation({
    mutationFn: (permission: 'MANAGE_TEMPLATES') =>
      adminApi.createCollectionsAclEntry(group.id, permission),
    onSuccess: () => void qc.invalidateQueries({ queryKey: ['acl', 'group', group.id] }),
  });

  const folderEntries = entries.filter(e => e.resourceType === 'FOLDER');

  const grouped = useMemo(() => {
    const map = new Map<number, AclEntryDto[]>();
    for (const e of folderEntries) {
      const id = e.resourceId!;
      if (!map.has(id)) map.set(id, []);
      map.get(id)!.push(e);
    }
    return [...map.entries()]
      .map(([id, es]) => ({ folder: folderMap.get(id), entries: es }))
      .filter((row): row is { folder: adminApi.FolderItem; entries: AclEntryDto[] } =>
        row.folder !== undefined)
      .sort((a, b) => a.folder.name.localeCompare(b.folder.name));
  }, [folderEntries, folderMap]);

  const testimonialEntries = entries.filter(e => e.resourceType === 'TESTIMONIALS');
  const hasTestPerm = (perm: string) => testimonialEntries.some(e => e.permission === perm && e.grantType === 'ALLOW');
  const testEntryId = (perm: string) => testimonialEntries.find(e => e.permission === perm)?.id;

  const collectionsEntries = entries.filter(e => e.resourceType === 'COLLECTIONS');
  const hasCollectionsPerm = (perm: string) => collectionsEntries.some(e => e.permission === perm && e.grantType === 'ALLOW');
  const collectionsEntryId = (perm: string) => collectionsEntries.find(e => e.permission === perm)?.id;

  return (
    <Dialog open onClose={onClose} maxWidth="md" fullWidth fullScreen={isMobile}>
      <DialogTitle>
        Rechte: <strong>{group.name}</strong>
      </DialogTitle>
      <DialogContent>
        {/* Existing entries */}
        {loadingEntries && <Typography variant="body2">Lade…</Typography>}
        {!loadingEntries && grouped.length === 0 && (
          <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
            Keine Berechtigungen vergeben.
          </Typography>
        )}
        {grouped.length > 0 && (
          <TableContainer component={Paper} variant="outlined" sx={{ mb: 3, overflowX: 'auto' }}>
            <Table size="small">
              <TableHead>
                <TableRow>
                  <TableCell>Ordner</TableCell>
                  <TableCell>Berechtigungen</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {grouped.map(({ folder, entries: rowEntries }) => (
                  <TableRow key={folder.id}>
                    <TableCell sx={{ whiteSpace: 'nowrap' }}>
                      {`${folder.name} (${folder.path})`}
                    </TableCell>
                    <TableCell>
                      <Box sx={{ display: 'flex', gap: 0.5, flexWrap: 'wrap' }}>
                        {rowEntries.map(e => (
                          <Chip
                            key={e.id}
                            label={e.inheritable ? `${e.permission} ↓` : e.permission}
                            size="small"
                            onDelete={() => removeEntry.mutate(e.id)}
                          />
                        ))}
                      </Box>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </TableContainer>
        )}

        {/* Add new entry */}
        <Typography variant="subtitle2" gutterBottom>Neue Berechtigung vergeben</Typography>
        {addError && <Alert severity="error" sx={{ mb: 1 }}>{addError}</Alert>}
        <Box sx={{ display: 'flex', gap: 1, flexWrap: 'wrap', alignItems: 'center' }}>
          <Autocomplete sx={{ minWidth: 260 }} options={folders}
            getOptionLabel={f => `${f.name} (${f.path})`}
            value={selFolder} onChange={(_, v) => setSelFolder(v)}
            renderInput={params => <TextField {...params} label="Ordner" size="small" />}
          />
          <FormControl size="small" sx={{ minWidth: 220 }}>
            <InputLabel>Berechtigungen</InputLabel>
            <Select
              label="Berechtigungen"
              multiple
              value={selPerms}
              onChange={e => setSelPerms(typeof e.target.value === 'string' ? [e.target.value] : e.target.value as string[])}
              renderValue={selected => (selected as string[]).join(', ')}
            >
              {ALL_PERMISSIONS.map(p => (
                <MenuItem key={p} value={p}>
                  <Checkbox checked={selPerms.includes(p)} size="small" />
                  {p}
                </MenuItem>
              ))}
            </Select>
          </FormControl>
          <FormControlLabel
            control={<Checkbox checked={inheritable} onChange={e => setInheritable(e.target.checked)} size="small" />}
            label="Vererbbar"
          />
          <Button variant="contained" disabled={!selFolder || selPerms.length === 0 || addEntry.isPending} onClick={() => addEntry.mutate()}>
            Hinzufügen
          </Button>
        </Box>
        <Divider sx={{ my: 3 }} />
        <Typography variant="subtitle2" gutterBottom>Erfahrungsberichte</Typography>
        <Box sx={{ display: 'flex', gap: 1, flexWrap: 'wrap' }}>
          {(['READ', 'WRITE', 'DELETE'] as const).map(perm => (
            <Chip
              key={perm}
              label={EB_LABEL[perm] ?? perm}
              color={hasTestPerm(perm) ? 'success' : 'default'}
              variant={hasTestPerm(perm) ? 'filled' : 'outlined'}
              onClick={() => {
                if (hasTestPerm(perm)) {
                  const id = testEntryId(perm);
                  if (id != null) removeEntry.mutate(id);
                } else {
                  addTestEntry.mutate(perm);
                }
              }}
              sx={{ cursor: 'pointer' }}
            />
          ))}
        </Box>
        <Typography variant="caption" color="text.secondary">
          Klick zum Aktivieren/Deaktivieren. Grün = aktiv.
        </Typography>
        <Divider sx={{ my: 3 }} />
        <Typography variant="subtitle2" gutterBottom>Sammlungen</Typography>
        <Box sx={{ display: 'flex', gap: 1, flexWrap: 'wrap' }}>
          <Chip
            label="Vorlagen erstellen"
            color={hasCollectionsPerm('MANAGE_TEMPLATES') ? 'success' : 'default'}
            variant={hasCollectionsPerm('MANAGE_TEMPLATES') ? 'filled' : 'outlined'}
            onClick={() => {
              if (hasCollectionsPerm('MANAGE_TEMPLATES')) {
                const id = collectionsEntryId('MANAGE_TEMPLATES');
                if (id != null) removeEntry.mutate(id);
              } else {
                addCollectionsEntry.mutate('MANAGE_TEMPLATES');
              }
            }}
            sx={{ cursor: 'pointer' }}
          />
        </Box>
        <Typography variant="caption" color="text.secondary">
          Erlaubt Mitgliedern dieser Gruppe, eigene Sammlungen als Vorlage zu markieren (unabhängig vom Admin-Status).
        </Typography>
      </DialogContent>
      <DialogActions><Button onClick={onClose}>Schließen</Button></DialogActions>
    </Dialog>
  );
}

// ── Delete Confirm Dialog ────────────────────────────────────────────────────

function DeleteGroupConfirmDialog({ group, onConfirm, onClose }: Readonly<{ group: GroupDto; onConfirm: () => void; onClose: () => void }>) {
  const isMobile = useIsMobile();
  return (
    <Dialog open onClose={onClose} maxWidth="xs" fullWidth fullScreen={isMobile}>
      <DialogTitle>Gruppe löschen</DialogTitle>
      <DialogContent>
        Soll die Gruppe <strong>{group.name}</strong> wirklich gelöscht werden? Alle Berechtigungen dieser Gruppe werden ebenfalls entfernt.
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

// ── Main GroupTable ──────────────────────────────────────────────────────────

export default function GroupTable() {
  const isMobile = useIsMobile();
  const qc = useQueryClient();
  const [createOpen, setCreateOpen] = useState(false);
  const [createError, setCreateError] = useState<string | null>(null);
  const [name, setName] = useState('');
  const [desc, setDesc] = useState('');
  const [editingGroup, setEditingGroup]   = useState<GroupDto | null>(null);
  const [membersGroup, setMembersGroup]   = useState<GroupDto | null>(null);
  const [permGroup, setPermGroup]         = useState<GroupDto | null>(null);
  const [deleteConfirm, setDeleteConfirm] = useState<GroupDto | null>(null);

  const { data, isLoading } = useQuery({
    queryKey: ['admin', 'groups'],
    queryFn: () => adminApi.listGroups(),
  });

  const createGroup = useMutation({
    mutationFn: (req: CreateGroupRequest) => adminApi.createGroup(req),
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: ['admin', 'groups'] });
      setCreateOpen(false); setName(''); setDesc(''); setCreateError(null);
    },
    onError: (err) => setCreateError(extractErrorMessage(err)),
  });

  const doDelete = useMutation({
    mutationFn: (id: number) => adminApi.deleteGroup(id),
    onSuccess: () => void qc.invalidateQueries({ queryKey: ['admin', 'groups'] }),
  });

  return (
    <>
      <Box sx={{ display: 'flex', justifyContent: 'flex-end', mb: 2 }}>
        <Button variant="contained" onClick={() => setCreateOpen(true)}>Gruppe anlegen</Button>
      </Box>

      <TableContainer component={Paper} variant="outlined" sx={{ overflowX: 'auto' }}>
        <Table size="small">
          <TableHead>
            <TableRow>
              <TableCell>Name</TableCell>
              <TableCell>Beschreibung</TableCell>
              <TableCell>Mitglieder</TableCell>
              <TableCell>Erstellt</TableCell>
              <TableCell align="right">Aktionen</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {isLoading && <TableRow><TableCell colSpan={5}>Lade…</TableCell></TableRow>}
            {data?.content.map(g => (
              <TableRow key={g.id} hover>
                <TableCell>{g.name}</TableCell>
                <TableCell>{g.description ?? '—'}</TableCell>
                <TableCell><Chip label={g.memberCount} size="small" /></TableCell>
                <TableCell>{new Date(g.createdAt).toLocaleDateString()}</TableCell>
                <TableCell align="right">
                  {!g.system && (
                    <Tooltip title="Bearbeiten">
                      <IconButton size="small" onClick={() => setEditingGroup(g)}>
                        <EditOutlinedIcon fontSize="small" />
                      </IconButton>
                    </Tooltip>
                  )}
                  <Tooltip title="Mitglieder">
                    <IconButton size="small" onClick={() => setMembersGroup(g)}>
                      <PeopleOutlineIcon fontSize="small" />
                    </IconButton>
                  </Tooltip>
                  <Tooltip title="Rechte">
                    <IconButton size="small" onClick={() => setPermGroup(g)}>
                      <SecurityOutlinedIcon fontSize="small" />
                    </IconButton>
                  </Tooltip>
                  {!g.system && (
                    <Tooltip title="Löschen">
                      <IconButton size="small" color="error" onClick={() => setDeleteConfirm(g)}>
                        <DeleteOutlineIcon fontSize="small" />
                      </IconButton>
                    </Tooltip>
                  )}
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </TableContainer>

      {/* Create dialog */}
      <Dialog open={createOpen} onClose={() => { setCreateOpen(false); setCreateError(null); setName(''); setDesc(''); }} maxWidth="xs" fullWidth fullScreen={isMobile}>
        <DialogTitle>Gruppe anlegen</DialogTitle>
        <DialogContent sx={{ display: 'flex', flexDirection: 'column', gap: 2, mt: 1 }}>
          {createError && <Alert severity="error">{createError}</Alert>}
          <TextField label="Name" value={name} onChange={e => setName(e.target.value)} fullWidth />
          <TextField label="Beschreibung" value={desc} onChange={e => setDesc(e.target.value)} fullWidth multiline rows={2} />
        </DialogContent>
        <DialogActions>
          <Button onClick={() => { setCreateOpen(false); setCreateError(null); setName(''); setDesc(''); }}>Abbrechen</Button>
          <Button variant="contained" disabled={!name || createGroup.isPending}
            onClick={() => createGroup.mutate({ name, description: desc })}>
            Anlegen
          </Button>
        </DialogActions>
      </Dialog>

      {editingGroup  && <EditGroupDialog group={editingGroup} onClose={() => setEditingGroup(null)} />}
      {membersGroup  && <MembersDialog group={membersGroup} onClose={() => setMembersGroup(null)} />}
      {permGroup     && <PermissionsDialog group={permGroup} onClose={() => setPermGroup(null)} />}
      {deleteConfirm && (
        <DeleteGroupConfirmDialog
          group={deleteConfirm}
          onConfirm={() => doDelete.mutate(deleteConfirm.id)}
          onClose={() => setDeleteConfirm(null)}
        />
      )}
    </>
  );
}
