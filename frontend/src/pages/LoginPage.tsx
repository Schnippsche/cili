import {type FormEvent, useState} from 'react';
import {Alert, Box, Button, CircularProgress, Paper, TextField, Typography} from '@mui/material';
import {useAuth} from '../hooks/useAuth';

export default function LoginPage() {
  const {login} = useAuth();
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  const handleSubmit = async (e: FormEvent<HTMLFormElement>) => {
    e.preventDefault();
    setError(null);
    setLoading(true);
    try {
      await login({username, password});
    } catch (err: unknown) {
      const msg = (err as {
        response?: { data?: { message?: string } }
      })?.response?.data?.message ?? 'Anmeldung fehlgeschlagen.';
      setError(msg);
    } finally {
      setLoading(false);
    }
  };

  return (
      <Box sx={{
        minHeight: '100vh',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        bgcolor: 'background.default'
      }}>
        <Paper elevation={4} sx={{p: 4, width: '100%', maxWidth: 400}}>
          <Typography variant="h5" align="center" gutterBottom>CILI</Typography>
          <Typography variant="body2" align="center" color="text.secondary" sx={{mb: 2}}>Bitte
            anmelden</Typography>
          {error && <Alert severity="error" sx={{mb: 2}} role="alert">{error}</Alert>}
          <Box component="form" onSubmit={handleSubmit} noValidate>
            <TextField margin="normal" required fullWidth label="Benutzername" id="username"
                       autoFocus value={username} onChange={e => setUsername(e.target.value)}/>
            <TextField margin="normal" required fullWidth label="Passwort" id="password"
                       type="password" value={password}
                       onChange={e => setPassword(e.target.value)}/>
            <Button type="submit" fullWidth variant="contained" sx={{mt: 3}} disabled={loading}>
              {loading ? <CircularProgress size={22}/> : 'Anmelden'}
            </Button>
          </Box>
        </Paper>
      </Box>
  );
}
