import axiosClient from './axiosClient';
import type {CreateCustomerRequest, CustomerDto, UpdateCustomerRequest} from '../types/api';

export async function getCustomers(): Promise<CustomerDto[]> {
  const {data} = await axiosClient.get<CustomerDto[]>('/customers');
  return data;
}

export async function getCustomer(id: number): Promise<CustomerDto> {
  const {data} = await axiosClient.get<CustomerDto>(`/customers/${id}`);
  return data;
}

export async function createCustomer(req: CreateCustomerRequest): Promise<CustomerDto> {
  const {data} = await axiosClient.post<CustomerDto>('/customers', req);
  return data;
}

export async function updateCustomer(id: number, req: UpdateCustomerRequest): Promise<CustomerDto> {
  const {data} = await axiosClient.put<CustomerDto>(`/customers/${id}`, req);
  return data;
}
