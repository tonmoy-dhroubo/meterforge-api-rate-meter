import { apiClient } from "./client";
import { LimitPolicy, LimitPolicyKind, Plan, QuotaPeriod } from "./types";

export async function fetchPlans(workspaceId: string, productId?: string): Promise<Plan[]> {
  const query = productId ? `?productId=${productId}` : "";
  return apiClient<Plan[]>(`/api/v1/workspaces/${workspaceId}/plans${query}`);
}

export async function fetchPlan(workspaceId: string, planId: string): Promise<Plan> {
  return apiClient<Plan>(`/api/v1/workspaces/${workspaceId}/plans/${planId}`);
}

export interface CreatePlanPayload {
  productId: string;
  name: string;
  slug: string;
  policies?: {
    routeId?: string;
    kind: LimitPolicyKind;
    capacity?: number;
    refillTokens?: number;
    refillPeriodSeconds?: number;
    quotaLimit?: number;
    quotaPeriod?: QuotaPeriod;
  }[];
}

export async function createPlan(workspaceId: string, data: CreatePlanPayload): Promise<Plan> {
  return apiClient<Plan>(`/api/v1/workspaces/${workspaceId}/plans`, {
    method: "POST",
    body: JSON.stringify(data),
  });
}

export interface AddPolicyPayload {
  routeId?: string;
  kind: LimitPolicyKind;
  capacity?: number;
  refillTokens?: number;
  refillPeriodSeconds?: number;
  quotaLimit?: number;
  quotaPeriod?: QuotaPeriod;
}

export async function addPolicyToPlan(
  workspaceId: string,
  planId: string,
  data: AddPolicyPayload
): Promise<LimitPolicy> {
  return apiClient<LimitPolicy>(`/api/v1/workspaces/${workspaceId}/plans/${planId}/policies`, {
    method: "POST",
    body: JSON.stringify(data),
  });
}

export async function togglePolicy(
  workspaceId: string,
  planId: string,
  policyId: string,
  enabled: boolean
): Promise<LimitPolicy> {
  return apiClient<LimitPolicy>(
    `/api/v1/workspaces/${workspaceId}/plans/${planId}/policies/${policyId}?enabled=${enabled}`,
    {
      method: "PATCH",
    }
  );
}

export async function activatePlan(workspaceId: string, planId: string): Promise<Plan> {
  return apiClient<Plan>(`/api/v1/workspaces/${workspaceId}/plans/${planId}/activate`, {
    method: "POST",
  });
}

export async function disablePlan(workspaceId: string, planId: string): Promise<Plan> {
  return apiClient<Plan>(`/api/v1/workspaces/${workspaceId}/plans/${planId}/disable`, {
    method: "POST",
  });
}
