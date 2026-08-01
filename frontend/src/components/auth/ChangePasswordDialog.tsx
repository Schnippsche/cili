import {Button, Dialog, DialogActions, DialogContent, DialogTitle, TextField,} from '@mui/material';
import {useState} from 'react';
import {useMutation} from '@tanstack/react-query';
import {changePassword} from '../../api/auth';

interface Props {
  open: boolean;
  onClose: () => void;
}

export default function ChangePasswordDialog({open, onClose}: Props) {
  const [currentPassword, setCurrentPassword] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');

  const mutation = useMutation({
    mutationFn: () => changePassword(currentPassword, newPassword),
    onSuccess: () => {
      handleClose();
    },
  });

  function handleClose() {
    setCurrentPassword('');
    setNewPassword('');
    setConfirmPassword('');
    mutation.reset();
    onClose();
  }

  const mismatch = newPassword.length > 0 && confirmPassword.length > 0 && newPassword !== confirmPassword;
  const tooShort = newPassword.length > 0 && newPassword.length < 8;
  const canSubmit = currentPassword.length > 0 && newPassword.length >= 8 && newPassword === confirmPassword && !mutation.isPending;

  return (
      <Dialog open={open} onClose={handleClose} maxWidth="xs" fullWidth>
        <DialogTitle>Passwort ändern</DialogTitle>
        <DialogContent sx={{display: 'flex', flexDirection: 'column', gap: 2, mt: 1}}>
          <TextField
              label="Aktuelles Passwort"
              type="password"
              value={currentPassword}
              onChange={(e) => setCurrentPassword(e.target.value)}
              autoComplete="current-password"
              fullWidth
              error={mutation.isError}
              helperText={mutation.isError ? 'Aktuelles Passwort ist falsch.' : undefined}
          />
          <TextField
              label="Neues Passwort"
              type="password"
              value={newPassword}
              onChange={(e) => setNewPassword(e.target.value)}
              autoComplete="new-password"
              fullWidth
              error={tooShort}
              helperText={tooShort ? 'Mindestens 8 Zeichen erforderlich.' : undefined}
          />
          <TextField
              label="Neues Passwort bestätigen"
              type="password"
              value={confirmPassword}
              onChange={(e) => setConfirmPassword(e.target.value)}
              autoComplete="new-password"
              fullWidth
              error={mismatch}
              helperText={mismatch ? 'Passwörter stimmen nicht überein.' : undefined}
          />
        </DialogContent>
        <DialogActions>
          <Button onClick={handleClose} disabled={mutation.isPending}>Abbrechen</Button>
          <Button
              variant="contained"
              onClick={() => mutation.mutate()}
              disabled={!canSubmit}
              loading={mutation.isPending}
          >
            Speichern
          </Button>
        </DialogActions>
      </Dialog>
  );
}
