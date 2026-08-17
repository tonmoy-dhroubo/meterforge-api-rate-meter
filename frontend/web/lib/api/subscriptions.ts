import { apiClient } from "./client";
import { Subscription } from "./types";

export async function fetchSubscriptions(
  workspaceId: string,
  applicationId?: string
): Promise<Subscription[]> {
  const query = applicationId ? `?applicationId=${applicationId}` : "";
  return apiClient<Subscription[]>(`/api/v1/workspaces/${workspaceId}/subscriptions${query}`);
}

export async function createSubscription(
  workspaceId: string,
  data: {
    applicationId: string;
    productId: string;
    planId: string;
  }
): Promise<Subscription> {
  return apiClient<Subscription>(`/api/v1/workspaces/${workspaceId}/subscriptions`, {
    method: "POST",
    body: JSON.stringify(data),
  });
}

export async function cancelSubscription(
  workspaceId: string,
  subscriptionId: string
): Promise<Subscription> {
  return apiClient<Subscription>(
    `/api/v1/workspaces/${workspaceId}/subscriptions/${subscriptionId}/cancel`,
    {
      method: "POST",
    }
  );
}
