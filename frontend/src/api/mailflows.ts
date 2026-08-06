import axiosClient from './axiosClient';
import type {AvailableMailflowDto, MailflowInstanceDto, StartMailflowRequest} from '../types/api';

export async function getAvailableMailflows(): Promise<AvailableMailflowDto[]> {
  const {data} = await axiosClient.get<AvailableMailflowDto[]>('/mailflows');
  return data;
}

export async function getCustomerMailflows(customerId: number): Promise<MailflowInstanceDto[]> {
  const {data} = await axiosClient.get<MailflowInstanceDto[]>(`/customers/${customerId}/mailflows`);
  return data;
}

export async function startMailflow(customerId: number, req: StartMailflowRequest): Promise<MailflowInstanceDto> {
  const {data} = await axiosClient.post<MailflowInstanceDto>(`/customers/${customerId}/mailflows`, req);
  return data;
}

export async function deleteMailflowInstance(customerId: number, instanceId: number): Promise<void> {
  await axiosClient.delete(`/customers/${customerId}/mailflows/${instanceId}`);
}

export async function sendMailflowStepNow(
    customerId: number, instanceId: number, stepId: string
): Promise<MailflowInstanceDto> {
  const {data} = await axiosClient.post<MailflowInstanceDto>(
      `/customers/${customerId}/mailflows/${instanceId}/steps/${stepId}/send-now`);
  return data;
}
