import { ProblemDetail } from "./types";

export class ApiError extends Error {
  constructor(public problem: ProblemDetail) {
    super(problem.detail || problem.title);
    this.name = "ApiError";
  }
}

export async function apiClient<T>(
  endpoint: string,
  options: RequestInit = {}
): Promise<T> {
  const headers = new Headers(options.headers || {});
  if (!headers.has("Content-Type") && options.body && typeof options.body === "string") {
    headers.set("Content-Type", "application/json");
  }

  const response = await fetch(endpoint, {
    ...options,
    headers,
    credentials: "include",
  });

  if (!response.ok) {
    let problem: ProblemDetail;
    try {
      problem = await response.json();
    } catch {
      problem = {
        title: response.statusText || "Request failed",
        status: response.status,
        detail: `Server responded with status ${response.status}`,
        code: "HTTP_ERROR",
        requestId: response.headers.get("X-Request-Id") || "",
      };
    }
    throw new ApiError(problem);
  }

  if (response.status === 204) {
    return null as unknown as T;
  }

  return response.json();
}
