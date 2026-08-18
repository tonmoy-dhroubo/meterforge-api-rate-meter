export interface GatewayRequestOptions {
  gatewayUrl?: string;
  method: string;
  path: string;
  apiKey: string;
  queryParams?: Record<string, string>;
  headers?: Record<string, string>;
  body?: string;
}

export interface GatewayResponseResult {
  id: string;
  index: number;
  statusCode: number;
  statusText: string;
  latencyMs: number;
  remaining: string | null;
  resetSeconds: string | null;
  retryAfter: string | null;
  requestId: string | null;
  body: unknown;
  timestamp: string;
  isAllowed: boolean;
  isRateLimited: boolean;
  isUnauthorized: boolean;
}

export async function sendGatewayRequest(
  options: GatewayRequestOptions,
  index: number = 1
): Promise<GatewayResponseResult> {
  const gatewayUrl = options.gatewayUrl || process.env.NEXT_PUBLIC_GATEWAY_URL || "http://localhost:8890";
  
  let url = `${gatewayUrl}${options.path.startsWith("/") ? "" : "/"}${options.path}`;
  if (options.queryParams && Object.keys(options.queryParams).length > 0) {
    const params = new URLSearchParams(options.queryParams);
    url += `?${params.toString()}`;
  }

  const reqHeaders: Record<string, string> = {
    ...(options.headers || {}),
  };

  if (options.apiKey) {
    reqHeaders["X-API-Key"] = options.apiKey;
  }

  const startNs = performance.now();
  const timestamp = new Date().toISOString();

  try {
    const res = await fetch(url, {
      method: options.method || "GET",
      headers: reqHeaders,
      body: options.body && ["POST", "PUT", "PATCH"].includes(options.method.toUpperCase())
        ? options.body
        : undefined,
    });

    const latencyMs = Math.round(performance.now() - startNs);
    const statusCode = res.status;
    const statusText = res.statusText;

    const remaining = res.headers.get("X-RateLimit-Remaining");
    const resetSeconds = res.headers.get("X-RateLimit-Reset");
    const retryAfter = res.headers.get("Retry-After");
    const requestId = res.headers.get("X-Request-ID");

    let responseBody: unknown = null;
    const contentType = res.headers.get("Content-Type") || "";
    if (contentType.includes("application/json") || contentType.includes("application/problem+json")) {
      try {
        responseBody = await res.json();
      } catch {
        responseBody = await res.text();
      }
    } else {
      responseBody = await res.text();
    }

    return {
      id: crypto.randomUUID(),
      index,
      statusCode,
      statusText,
      latencyMs,
      remaining,
      resetSeconds,
      retryAfter,
      requestId,
      body: responseBody,
      timestamp,
      isAllowed: statusCode >= 200 && statusCode < 300,
      isRateLimited: statusCode === 429,
      isUnauthorized: statusCode === 401,
    };
  } catch (error: unknown) {
    const latencyMs = Math.round(performance.now() - startNs);
    const message = error instanceof Error ? error.message : "Failed to reach gateway";
    return {
      id: crypto.randomUUID(),
      index,
      statusCode: 0,
      statusText: "Network Error / Gateway Unavailable",
      latencyMs,
      remaining: null,
      resetSeconds: null,
      retryAfter: null,
      requestId: null,
      body: { error: message },
      timestamp,
      isAllowed: false,
      isRateLimited: false,
      isUnauthorized: false,
    };
  }
}

export async function executeBurstRequests(
  options: GatewayRequestOptions,
  count: number
): Promise<GatewayResponseResult[]> {
  const promises: Promise<GatewayResponseResult>[] = [];
  for (let i = 1; i <= count; i++) {
    promises.push(sendGatewayRequest(options, i));
  }
  return Promise.all(promises);
}
