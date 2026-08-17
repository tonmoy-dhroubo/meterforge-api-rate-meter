import { apiClient } from "./client";
import { Consumer, ConsumerApplication } from "./types";

export async function fetchConsumers(workspaceId: string): Promise<Consumer[]> {
  return apiClient<Consumer[]>(`/api/v1/workspaces/${workspaceId}/consumers`);
}

export async function fetchConsumer(workspaceId: string, consumerId: string): Promise<Consumer> {
  return apiClient<Consumer>(`/api/v1/workspaces/${workspaceId}/consumers/${consumerId}`);
}

export async function createConsumer(
  workspaceId: string,
  data: { name: string; externalReference?: string }
): Promise<Consumer> {
  return apiClient<Consumer>(`/api/v1/workspaces/${workspaceId}/consumers`, {
    method: "POST",
    body: JSON.stringify(data),
  });
}

export async function updateConsumer(
  workspaceId: string,
  consumerId: string,
  data: { name?: string; externalReference?: string }
): Promise<Consumer> {
  return apiClient<Consumer>(`/api/v1/workspaces/${workspaceId}/consumers/${consumerId}`, {
    method: "PATCH",
    body: JSON.stringify(data),
  });
}

export async function activateConsumer(workspaceId: string, consumerId: string): Promise<Consumer> {
  return apiClient<Consumer>(`/api/v1/workspaces/${workspaceId}/consumers/${consumerId}/activate`, {
    method: "POST",
  });
}

export async function disableConsumer(workspaceId: string, consumerId: string): Promise<Consumer> {
  return apiClient<Consumer>(`/api/v1/workspaces/${workspaceId}/consumers/${consumerId}/disable`, {
    method: "POST",
  });
}

export async function fetchApplicationsByConsumer(
  workspaceId: string,
  consumerId: string
): Promise<ConsumerApplication[]> {
  return apiClient<ConsumerApplication[]>(
    `/api/v1/workspaces/${workspaceId}/consumers/${consumerId}/applications`
  );
}

export async function fetchAllWorkspaceApplications(
  workspaceId: string
): Promise<ConsumerApplication[]> {
  return apiClient<ConsumerApplication[]>(`/api/v1/workspaces/${workspaceId}/applications`);
}

export async function createApplication(
  workspaceId: string,
  consumerId: string,
  data: { name: string }
): Promise<ConsumerApplication> {
  return apiClient<ConsumerApplication>(
    `/api/v1/workspaces/${workspaceId}/consumers/${consumerId}/applications`,
    {
      method: "POST",
      body: JSON.stringify(data),
    }
  );
}
