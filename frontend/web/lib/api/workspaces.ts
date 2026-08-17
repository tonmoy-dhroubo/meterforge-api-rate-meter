import { apiClient } from "./client";
import { Workspace } from "./types";

export async function getWorkspaces(): Promise<Workspace[]> {
  return apiClient<Workspace[]>("/api/v1/workspaces");
}

export async function getWorkspace(workspaceId: string): Promise<Workspace> {
  return apiClient<Workspace>(`/api/v1/workspaces/${workspaceId}`);
}
