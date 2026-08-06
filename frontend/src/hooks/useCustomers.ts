import {useMutation, useQuery, useQueryClient} from '@tanstack/react-query';
import * as api from '../api/customers';
import type {CreateCustomerRequest, UpdateCustomerRequest} from '../types/api';

export function useCustomers() {
  return useQuery({
    queryKey: ['customers'],
    queryFn: api.getCustomers,
  });
}

export function useCustomer(id: number) {
  return useQuery({
    queryKey: ['customers', id],
    queryFn: () => api.getCustomer(id),
    enabled: id > 0,
  });
}

export function useCreateCustomer() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (req: CreateCustomerRequest) => api.createCustomer(req),
    onSuccess: () => qc.invalidateQueries({queryKey: ['customers']}),
  });
}

export function useUpdateCustomer() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({id, req}: { id: number; req: UpdateCustomerRequest }) =>
        api.updateCustomer(id, req),
    onSuccess: () => qc.invalidateQueries({queryKey: ['customers']}),
  });
}
