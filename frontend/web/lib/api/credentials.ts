import { apiClient } from "./client";
import { ApiCredential, CreateCredentialResponse } from "./types";

export async function fetchCredentials(
  workspaceId: string,
  applicationId: string
): Promise<ApiCredential[]> {
  return apiClient<ApiCredential[]>(
    `/api/v1/workspaces/${workspaceId}/applications/${applicationId}/credentials`
  );
}

export async function issueCredential(
  workspaceId: string,
  applicationId: string,
  data?: { environment?: string; expiresAt?: string }
): Promise<CreateCredentialResponse> {
  return apiClient<CreateCredentialResponse>(
    `/api/v1/workspaces/${workspaceId}/applications/${applicationId}/credentials`,
    {
      method: "POST",
      body: JSON.stringify(data || {}),
    }
  );
}

export async function revokeCredential(
  workspaceId: string,
  credentialId: string
): Promise<ApiCredential> {
  return apiClient<ApiCredential>(
    `/api/v1/workspaces/${workspaceId}/credentials/${credentialId}/revoke`,
    {
      method: "POST",
    }
  );
}

export async function rotateCredential(
  workspaceId: string,
  credentialId: string
): Promise<CreateCredentialResponse> {
  return apiClient<CreateCredentialResponse>(
    `/api/v1/workspaces/${workspaceId}/credentials/${credentialId}/rotate`,
    {
      method: "POST",
    }
  );
}
