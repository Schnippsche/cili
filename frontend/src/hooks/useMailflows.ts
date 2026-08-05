import {useMutation, useQuery, useQueryClient} from '@tanstack/react-query';
import * as api from '../api/mailflows';
import type {StartMailflowRequest} from '../types/api';

export function useAvailableMailflows() {
  return useQuery({
    queryKey: ['mailflows', 'available'],
    queryFn: api.getAvailableMailflows,
  });
}

export function useCustomerMailflows(customerId: number) {
  return useQuery({
    queryKey: ['customers', customerId, 'mailflows'],
    queryFn: () => api.getCustomerMailflows(customerId),
    enabled: customerId > 0,
  });
}

export function useStartMailflow() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({customerId, req}: { customerId: number; req: StartMailflowRequest }) =>
        api.startMailflow(customerId, req),
    onSuccess: (_, {customerId}) =>
        qc.invalidateQueries({queryKey: ['customers', customerId, 'mailflows']}),
  });
}
