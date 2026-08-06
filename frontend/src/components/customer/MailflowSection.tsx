import {useState} from 'react';
import {useSelector} from 'react-redux';
import {
  Alert, Box, Button, Chip, Dialog, DialogActions, DialogContent, DialogTitle, IconButton,
  MenuItem, Paper, Table, TableBody, TableCell, TableContainer, TableHead, TableRow, TextField,
  Tooltip, Typography
} from '@mui/material';
import DeleteOutlineIcon from '@mui/icons-material/DeleteOutline';
import SendIcon from '@mui/icons-material/Send';
import {
  useAvailableMailflows, useCustomerMailflows, useDeleteMailflowInstance,
  useSendMailflowStepNow, useStartMailflow
} from '../../hooks/useMailflows';
import type {RootState} from '../../store/store';
import type {MailflowInstanceDto, MailflowStepDto} from '../../types/api';

const STATUS_COLOR: Record<MailflowStepDto['status'], 'default' | 'success' | 'warning' | 'error'> = {
  PENDING: 'default',
  SENT: 'success',
  SKIPPED: 'default',
  ERROR: 'warning',
  FAILED: 'error',
};

interface Props {
  customerId: number;
}

export default function MailflowSection({customerId}: Readonly<Props>) {
  const isAdmin = useSelector((s: RootState) => s.auth.user?.role === 'ADMIN');
  const {data: availableFlows = []} = useAvailableMailflows();
  const {data: instances = [], isLoading} = useCustomerMailflows(customerId);
  const startMutation = useStartMailflow();
  const sendNowMutation = useSendMailflowStepNow();
  const deleteMutation = useDeleteMailflowInstance();
  const [selectedFlow, setSelectedFlow] = useState('');
  const [deleteTarget, setDeleteTarget] = useState<MailflowInstanceDto | null>(null);

  const handleStart = () => {
    if (!selectedFlow) return;
    startMutation.mutate(
        {customerId, req: {flowName: selectedFlow}},
        {onSuccess: () => setSelectedFlow('')}
    );
  };

  return (
      <Box>
        <Typography variant="h6" sx={{mb: 2}}>Mailflows</Typography>

        {availableFlows.length === 0 && (
            <Alert severity="info" sx={{mb: 2}}>
              Aktuell sind keine Mailflows konfiguriert.
            </Alert>
        )}

        {availableFlows.length > 0 && (
            <Box sx={{display: 'flex', gap: 2, alignItems: 'center', mb: 3}}>
              <TextField
                  select
                  label="Flow"
                  value={selectedFlow}
                  onChange={e => setSelectedFlow(e.target.value)}
                  sx={{minWidth: 260}}
                  size="small"
              >
                {availableFlows.map(f => (
                    <MenuItem key={f.flowName} value={f.flowName}>{f.description}</MenuItem>
                ))}
              </TextField>
              <Button variant="contained" disabled={!selectedFlow} onClick={handleStart}>
                Starten
              </Button>
            </Box>
        )}

        {startMutation.isError && (
            <Alert severity="error" sx={{mb: 2}}>
              Flow konnte nicht gestartet werden — läuft eventuell bereits eine Instanz?
            </Alert>
        )}

        {sendNowMutation.isError && (
            <Alert severity="error" sx={{mb: 2}}>
              Step konnte nicht sofort gesendet werden.
            </Alert>
        )}

        {deleteMutation.isError && (
            <Alert severity="error" sx={{mb: 2}}>
              Instanz konnte nicht gelöscht werden.
            </Alert>
        )}

        {isLoading && <Typography color="text.secondary">Lädt…</Typography>}
        {!isLoading && instances.length === 0 && (
            <Typography color="text.secondary">Noch keine Mailflow-Instanzen für diesen Kunden.</Typography>
        )}

        {instances.map(instance => (
            <Paper key={instance.id} variant="outlined" sx={{p: 2, mb: 2}}>
              <Box sx={{display: 'flex', alignItems: 'center', gap: 2, mb: 1}}>
                <Typography variant="subtitle1" fontWeight="medium">{instance.description}</Typography>
                <Chip
                    size="small"
                    label={instance.status === 'RUNNING' ? 'Läuft' : 'Abgeschlossen'}
                    color={instance.status === 'RUNNING' ? 'primary' : 'default'}
                />
                <Box sx={{flexGrow: 1}}/>
                <Tooltip title="Instanz löschen">
                  <IconButton size="small" onClick={() => setDeleteTarget(instance)}>
                    <DeleteOutlineIcon fontSize="small"/>
                  </IconButton>
                </Tooltip>
              </Box>
              <TableContainer>
                <Table size="small">
                  <TableHead>
                    <TableRow>
                      <TableCell>Step</TableCell>
                      <TableCell>Geplant für</TableCell>
                      <TableCell>Versendet</TableCell>
                      <TableCell>Status</TableCell>
                      {isAdmin && <TableCell align="right">Aktionen</TableCell>}
                    </TableRow>
                  </TableHead>
                  <TableBody>
                    {instance.steps.map(step => (
                        <TableRow key={step.stepId}>
                          <TableCell>{step.stepId}</TableCell>
                          <TableCell>{step.scheduledFor}</TableCell>
                          <TableCell>{step.sentAt ?? '—'}</TableCell>
                          <TableCell>
                            <Tooltip title={step.lastError ?? ''} disableHoverListener={!step.lastError}>
                              <Chip size="small" label={step.status} color={STATUS_COLOR[step.status]}/>
                            </Tooltip>
                          </TableCell>
                          {isAdmin && (
                              <TableCell align="right">
                                <Tooltip title="Jetzt senden (Test)">
                                  <span>
                                    <IconButton
                                        size="small"
                                        disabled={sendNowMutation.isPending
                                            && sendNowMutation.variables?.instanceId === instance.id
                                            && sendNowMutation.variables?.stepId === step.stepId}
                                        onClick={() => sendNowMutation.mutate(
                                            {customerId, instanceId: instance.id, stepId: step.stepId})}
                                    >
                                      <SendIcon fontSize="small"/>
                                    </IconButton>
                                  </span>
                                </Tooltip>
                              </TableCell>
                          )}
                        </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </TableContainer>
            </Paper>
        ))}

        {deleteTarget && (
            <Dialog open onClose={() => setDeleteTarget(null)} maxWidth="xs" fullWidth>
              <DialogTitle>Mailflow-Instanz löschen</DialogTitle>
              <DialogContent>
                Soll die Instanz <strong>{deleteTarget.description}</strong> wirklich gelöscht werden?
                {deleteTarget.status === 'RUNNING' && (
                    <>
                      {' '}Sie läuft noch — noch ausstehende Mails werden dadurch{' '}
                      <strong>nicht mehr versendet</strong>.
                    </>
                )}
                {' '}Diese Aktion kann nicht rückgängig gemacht werden.
              </DialogContent>
              <DialogActions>
                <Button onClick={() => setDeleteTarget(null)}>Abbrechen</Button>
                <Button
                    variant="contained"
                    color="error"
                    onClick={() => deleteMutation.mutate(
                        {customerId, instanceId: deleteTarget.id},
                        {onSuccess: () => setDeleteTarget(null)}
                    )}
                >
                  Löschen
                </Button>
              </DialogActions>
            </Dialog>
        )}
      </Box>
  );
}
