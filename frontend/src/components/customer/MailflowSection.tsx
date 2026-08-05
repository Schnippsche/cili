import {useState} from 'react';
import {
  Alert, Box, Button, Chip, MenuItem, Paper, Table, TableBody, TableCell,
  TableContainer, TableHead, TableRow, TextField, Tooltip, Typography
} from '@mui/material';
import {useAvailableMailflows, useCustomerMailflows, useStartMailflow} from '../../hooks/useMailflows';
import type {MailflowStepDto} from '../../types/api';

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
  const {data: availableFlows = []} = useAvailableMailflows();
  const {data: instances = [], isLoading} = useCustomerMailflows(customerId);
  const startMutation = useStartMailflow();
  const [selectedFlow, setSelectedFlow] = useState('');

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
              </Box>
              <TableContainer>
                <Table size="small">
                  <TableHead>
                    <TableRow>
                      <TableCell>Step</TableCell>
                      <TableCell>Geplant für</TableCell>
                      <TableCell>Versendet</TableCell>
                      <TableCell>Status</TableCell>
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
                        </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </TableContainer>
            </Paper>
        ))}
      </Box>
  );
}
