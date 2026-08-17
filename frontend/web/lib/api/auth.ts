import { apiClient } from "./client";
import { MeResponse } from "./types";

export interface LoginPayload {
  email: string;
  password: string;
}

export async function login(payload: LoginPayload): Promise<MeResponse> {
  return apiClient<MeResponse>("/api/v1/auth/login", {
    method: "POST",
    body: JSON.stringify(payload),
  });
}

export async function logout(): Promise<void> {
  return apiClient<void>("/api/v1/auth/logout", {
    method: "POST",
  });
}

export async function getMe(): Promise<MeResponse> {
  return apiClient<MeResponse>("/api/v1/me");
}
