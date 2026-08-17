import { apiClient } from "./client";

export interface AuditLogItem {
  id: string;
  workspaceId: string;
  userId: string | null;
  action: string;
  resourceType: string;
  resourceId: string | null;
  requestId: string | null;
  summary: string;
  metadata: string | null;
  createdAt: string;
}

export async function getAuditLogs(
  workspaceId: string
): Promise<AuditLogItem[]> {
  return apiClient<AuditLogItem[]>(
    `/api/v1/workspaces/${workspaceId}/audit-logs`
  );
}
