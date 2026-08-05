import {useParams} from 'react-router-dom';
import {Box, Divider, Paper, Typography} from '@mui/material';
import AppShell from '../components/layout/AppShell';
import MailflowSection from '../components/customer/MailflowSection';
import {useCustomer} from '../hooks/useCustomers';

export default function CustomerDetailPage() {
  const {id} = useParams<{ id: string }>();
  const customerId = Number(id);
  const {data: customer, isLoading} = useCustomer(customerId);

  if (isLoading || !customer) {
    return (
        <AppShell>
          <Box sx={{p: 3}}>
            <Typography color="text.secondary">Lädt…</Typography>
          </Box>
        </AppShell>
    );
  }

  return (
      <AppShell>
        <Box sx={{p: 3}}>
          <Typography variant="h5" fontWeight="bold" sx={{mb: 1}}>{customer.name}</Typography>
          <Typography color="text.secondary" sx={{mb: 3}}>{customer.email}</Typography>

          <Paper variant="outlined" sx={{p: 2, mb: 3}}>
            <Typography variant="body2">
              Einwilligung: {customer.consentGranted ? 'erteilt' : 'widerrufen'}
              {customer.consentRevokedAt && ` (seit ${customer.consentRevokedAt})`}
            </Typography>
          </Paper>

          <Divider sx={{mb: 3}}/>

          <MailflowSection customerId={customerId}/>
        </Box>
      </AppShell>
  );
}
