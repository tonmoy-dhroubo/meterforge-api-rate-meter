import { render, screen } from "@testing-library/react";
import { describe, it, expect, vi } from "vitest";
import UsagePage from "./page";
import { Providers } from "@/app/providers";

// Mock next/navigation
vi.mock("next/navigation", () => ({
  useRouter: () => ({
    push: vi.fn(),
  }),
  usePathname: () => "/acme-apis/usage",
  useParams: () => ({ workspaceSlug: "acme-apis" }),
}));

// Mock Usage & Product APIs
vi.mock("@/lib/api/usage", () => ({
  getUsageSummary: vi.fn().mockResolvedValue({
    totalRequests: 42,
    allowedRequests: 30,
    rateLimitedRequests: 10,
    blockedRequests: 0,
    clientErrorRequests: 2,
    serverErrorRequests: 0,
    totalUnitsConsumed: 30,
    avgLatencyMs: 24.5,
  }),
  getUsageTimeseries: vi.fn().mockResolvedValue({
    granularity: "HOUR",
    from: "2026-08-17T00:00:00Z",
    to: "2026-08-18T00:00:00Z",
    buckets: [
      {
        bucketStart: "2026-08-17T12:00:00Z",
        totalRequests: 42,
        allowedRequests: 30,
        rateLimitedRequests: 10,
        errorRequests: 2,
        totalUnits: 30,
        avgLatencyMs: 24.5,
      },
    ],
  }),
  getTopRoutes: vi.fn().mockResolvedValue([
    {
      routeId: "cccccccc-cccc-cccc-cccc-cccccccccccc",
      httpMethod: "GET",
      pathPattern: "/v1/forecast/{city}",
      totalRequests: 42,
      totalUnits: 30,
    },
  ]),
  getTopApplications: vi.fn().mockResolvedValue([
    {
      applicationId: "eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee",
      applicationName: "Northstar Demo App",
      totalRequests: 42,
      totalUnits: 30,
    },
  ]),
  getRawUsageEvents: vi.fn().mockResolvedValue({
    items: [
      {
        eventId: "11111111-1111-1111-1111-111111111111",
        occurredAt: "2026-08-17T12:30:00Z",
        requestId: "req-test-12345",
        httpMethod: "GET",
        routeTemplate: "/v1/forecast/{city}",
        decision: "ALLOWED",
        outcome: "SUCCESS",
        statusCode: 200,
        usageUnits: 1,
        latencyMs: 22,
      },
    ],
    total: 1,
    limit: 20,
    offset: 0,
  }),
}));

vi.mock("@/lib/api/products", () => ({
  getProducts: vi.fn().mockResolvedValue([
    {
      id: "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb",
      workspaceId: "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
      name: "Weather API",
      slug: "weather-api",
      gatewayBasePath: "/v1/forecast",
      upstreamBaseUrl: "http://wiremock:8080",
      status: "ACTIVE",
      routeCount: 1,
      version: 1,
      createdAt: "2026-08-17T00:00:00Z",
      updatedAt: "2026-08-17T00:00:00Z",
    },
  ]),
}));

describe("UsagePage", () => {
  it("renders Usage & Analytics title and KPI cards", async () => {
    render(
      <Providers>
        <UsagePage />
      </Providers>
    );

    expect(screen.getByText("Usage & Analytics")).toBeInTheDocument();
    expect(screen.getByText("Total Requests")).toBeInTheDocument();
    expect(screen.getByText("Allowed (200)")).toBeInTheDocument();
    expect(screen.getByText("Limited (429)")).toBeInTheDocument();
    expect(screen.getByText("Avg Latency")).toBeInTheDocument();
    expect(screen.getByText("Live Telemetry Events Stream")).toBeInTheDocument();
  });
});
