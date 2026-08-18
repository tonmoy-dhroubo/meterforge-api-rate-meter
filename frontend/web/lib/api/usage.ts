import { apiClient } from "./client";

export interface UsageSummary {
  totalRequests: number;
  allowedRequests: number;
  rateLimitedRequests: number;
  blockedRequests: number;
  clientErrorRequests: number;
  serverErrorRequests: number;
  totalUnitsConsumed: number;
  avgLatencyMs: number;
}

export interface TimeseriesBucket {
  bucketStart: string;
  totalRequests: number;
  allowedRequests: number;
  rateLimitedRequests: number;
  errorRequests: number;
  totalUnits: number;
  avgLatencyMs: number;
}

export interface UsageTimeseries {
  granularity: string;
  from: string;
  to: string;
  buckets: TimeseriesBucket[];
}

export interface TopRoute {
  routeId: string;
  httpMethod: string;
  pathPattern: string;
  totalRequests: number;
  totalUnits: number;
}

export interface TopApplication {
  applicationId: string;
  applicationName: string;
  totalRequests: number;
  totalUnits: number;
}

export interface RawUsageEvent {
  eventId: string;
  occurredAt: string;
  requestId: string;
  workspaceId?: string;
  productId?: string;
  routeId?: string;
  httpMethod: string;
  routeTemplate?: string;
  consumerId?: string;
  applicationId?: string;
  credentialId?: string;
  subscriptionId?: string;
  decision: string;
  outcome: string;
  statusCode: number;
  usageUnits: number;
  latencyMs: number;
  limitingPolicyId?: string;
  gatewayInstanceId?: string;
}

export interface RawUsageEventsPage {
  items: RawUsageEvent[];
  total: number;
  limit: number;
  offset: number;
}

export interface UsageFilterParams {
  from?: string;
  to?: string;
  productId?: string;
  consumerId?: string;
  granularity?: "HOUR" | "DAY";
  decision?: string;
  limit?: number;
  offset?: number;
}

export async function getUsageSummary(
  workspaceSlug: string,
  params?: UsageFilterParams
): Promise<UsageSummary> {
  const query = new URLSearchParams();
  if (params?.from) query.set("from", params.from);
  if (params?.to) query.set("to", params.to);
  if (params?.productId) query.set("productId", params.productId);
  if (params?.consumerId) query.set("consumerId", params.consumerId);

  const qs = query.toString() ? `?${query.toString()}` : "";
  return apiClient<UsageSummary>(
    `/api/v1/workspaces/${workspaceSlug}/usage/summary${qs}`
  );
}

export async function getUsageTimeseries(
  workspaceSlug: string,
  params?: UsageFilterParams
): Promise<UsageTimeseries> {
  const query = new URLSearchParams();
  if (params?.from) query.set("from", params.from);
  if (params?.to) query.set("to", params.to);
  if (params?.granularity) query.set("granularity", params.granularity);
  if (params?.productId) query.set("productId", params.productId);
  if (params?.consumerId) query.set("consumerId", params.consumerId);

  const qs = query.toString() ? `?${query.toString()}` : "";
  return apiClient<UsageTimeseries>(
    `/api/v1/workspaces/${workspaceSlug}/usage/timeseries${qs}`
  );
}

export async function getTopRoutes(
  workspaceSlug: string,
  params?: UsageFilterParams
): Promise<TopRoute[]> {
  const query = new URLSearchParams();
  if (params?.from) query.set("from", params.from);
  if (params?.to) query.set("to", params.to);
  if (params?.limit) query.set("limit", String(params.limit));

  const qs = query.toString() ? `?${query.toString()}` : "";
  return apiClient<TopRoute[]>(
    `/api/v1/workspaces/${workspaceSlug}/usage/top-routes${qs}`
  );
}

export async function getTopApplications(
  workspaceSlug: string,
  params?: UsageFilterParams
): Promise<TopApplication[]> {
  const query = new URLSearchParams();
  if (params?.from) query.set("from", params.from);
  if (params?.to) query.set("to", params.to);
  if (params?.limit) query.set("limit", String(params.limit));

  const qs = query.toString() ? `?${query.toString()}` : "";
  return apiClient<TopApplication[]>(
    `/api/v1/workspaces/${workspaceSlug}/usage/top-applications${qs}`
  );
}

export async function getRawUsageEvents(
  workspaceSlug: string,
  params?: UsageFilterParams
): Promise<RawUsageEventsPage> {
  const query = new URLSearchParams();
  if (params?.from) query.set("from", params.from);
  if (params?.to) query.set("to", params.to);
  if (params?.productId) query.set("productId", params.productId);
  if (params?.consumerId) query.set("consumerId", params.consumerId);
  if (params?.decision) query.set("decision", params.decision);
  if (params?.limit) query.set("limit", String(params.limit));
  if (params?.offset !== undefined) query.set("offset", String(params.offset));

  const qs = query.toString() ? `?${query.toString()}` : "";
  return apiClient<RawUsageEventsPage>(
    `/api/v1/workspaces/${workspaceSlug}/usage/events${qs}`
  );
}

export async function getUsageEventById(
  workspaceSlug: string,
  eventId: string
): Promise<RawUsageEvent> {
  return apiClient<RawUsageEvent>(
    `/api/v1/workspaces/${workspaceSlug}/usage/events/${eventId}`
  );
}
