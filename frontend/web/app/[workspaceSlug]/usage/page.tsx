"use client";

import React, { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { useAuth } from "@/lib/auth-context";
import {
  getUsageSummary,
  getUsageTimeseries,
  getTopRoutes,
  getTopApplications,
  getRawUsageEvents,
  UsageSummary,
  UsageTimeseries,
  TopRoute,
  TopApplication,
  RawUsageEvent,
  RawUsageEventsPage,
} from "@/lib/api/usage";
import { getProducts } from "@/lib/api/products";
import { ApiProduct } from "@/lib/api/types";
import { Button } from "@/components/ui/button";
import {
  BarChart3,
  CheckCircle2,
  Flame,
  AlertTriangle,
  Clock,
  Zap,
  RefreshCw,
  Code2,
  TrendingUp,
  Layers,
  ChevronLeft,
  ChevronRight,
} from "lucide-react";

export default function UsagePage() {
  const { currentMembership } = useAuth();
  const workspaceId = currentMembership?.workspaceId;

  // Filter state
  const [timeRange, setTimeRange] = useState<"1h" | "24h" | "7d" | "30d">("24h");
  const [selectedProductId, setSelectedProductId] = useState<string>("");
  const [decisionFilter, setDecisionFilter] = useState<string>("");
  const [page, setPage] = useState<number>(0);
  const [selectedEvent, setSelectedEvent] = useState<RawUsageEvent | null>(null);

  // Derive time range filter params
  const now = new Date();
  let fromDate = new Date();
  let granularity: "HOUR" | "DAY" = "HOUR";

  if (timeRange === "1h") {
    fromDate = new Date(now.getTime() - 60 * 60 * 1000);
  } else if (timeRange === "24h") {
    fromDate = new Date(now.getTime() - 24 * 60 * 60 * 1000);
  } else if (timeRange === "7d") {
    fromDate = new Date(now.getTime() - 7 * 24 * 60 * 60 * 1000);
    granularity = "DAY";
  } else if (timeRange === "30d") {
    fromDate = new Date(now.getTime() - 30 * 24 * 60 * 60 * 1000);
    granularity = "DAY";
  }

  const filterParams = {
    from: fromDate.toISOString(),
    to: now.toISOString(),
    productId: selectedProductId || undefined,
    granularity,
  };

  // Queries
  const { data: products = [] } = useQuery<ApiProduct[]>({
    queryKey: ["products", workspaceId],
    queryFn: () => (workspaceId ? getProducts(workspaceId) : Promise.resolve([])),
    enabled: !!workspaceId,
  });

  const {
    data: summary = null,
    isLoading: isSummaryLoading,
    refetch: refetchSummary,
  } = useQuery<UsageSummary | null>({
    queryKey: ["usage-summary", workspaceId, timeRange, selectedProductId],
    queryFn: () => (workspaceId ? getUsageSummary(workspaceId, filterParams) : Promise.resolve(null)),
    enabled: !!workspaceId,
  });

  const { data: timeseriesData, refetch: refetchTimeseries } = useQuery<UsageTimeseries>({
    queryKey: ["usage-timeseries", workspaceId, timeRange, selectedProductId],
    queryFn: () =>
      workspaceId
        ? getUsageTimeseries(workspaceId, filterParams)
        : Promise.resolve({
            granularity,
            from: filterParams.from,
            to: filterParams.to,
            buckets: [],
          }),
    enabled: !!workspaceId,
  });

  const { data: topRoutes = [], refetch: refetchRoutes } = useQuery<TopRoute[]>({
    queryKey: ["usage-top-routes", workspaceId, timeRange, selectedProductId],
    queryFn: () => (workspaceId ? getTopRoutes(workspaceId, filterParams) : Promise.resolve([])),
    enabled: !!workspaceId,
  });

  const { data: topApps = [], refetch: refetchApps } = useQuery<TopApplication[]>({
    queryKey: ["usage-top-apps", workspaceId, timeRange, selectedProductId],
    queryFn: () => (workspaceId ? getTopApplications(workspaceId, filterParams) : Promise.resolve([])),
    enabled: !!workspaceId,
  });

  const {
    data: eventsData,
    isLoading: isEventsLoading,
    refetch: refetchEvents,
  } = useQuery<RawUsageEventsPage>({
    queryKey: ["usage-events", workspaceId, timeRange, selectedProductId, decisionFilter, page],
    queryFn: () =>
      workspaceId
        ? getRawUsageEvents(workspaceId, {
            ...filterParams,
            decision: decisionFilter || undefined,
            limit: 20,
            offset: page * 20,
          })
        : Promise.resolve({ total: 0, items: [], limit: 20, offset: 0 }),
    enabled: !!workspaceId,
  });

  const timeseries = timeseriesData?.buckets || [];
  const rawEvents = eventsData?.items || [];
  const totalEvents = eventsData?.total || 0;
  const isLoading = isSummaryLoading || isEventsLoading;

  const handleRefresh = () => {
    refetchSummary();
    refetchTimeseries();
    refetchRoutes();
    refetchApps();
    refetchEvents();
  };

  const maxBucketRequests =
    timeseries.length > 0
      ? Math.max(...timeseries.map((b) => b.totalRequests), 1)
      : 1;

  return (
    <div className="space-y-8 p-6 max-w-7xl mx-auto">
      {/* Header */}
      <div className="flex flex-col md:flex-row md:items-center md:justify-between gap-4 border-b border-border/60 pb-6">
        <div>
          <div className="flex items-center gap-2.5">
            <div className="p-2 rounded-lg bg-primary/10 text-primary">
              <BarChart3 className="h-6 w-6" />
            </div>
            <div>
              <h1 className="text-2xl font-bold tracking-tight">Usage & Analytics</h1>
              <p className="text-sm text-muted-foreground">
                Real-time API gateway metrics, token usage aggregations, and raw request event telemetry.
              </p>
            </div>
          </div>
        </div>

        <div className="flex flex-wrap items-center gap-3">
          {/* Time Range Selector */}
          <div className="flex items-center rounded-lg border border-border bg-card p-1 text-xs">
            {(["1h", "24h", "7d", "30d"] as const).map((r) => (
              <button
                key={r}
                type="button"
                onClick={() => {
                  setTimeRange(r);
                  setPage(0);
                }}
                className={`px-3 py-1 font-semibold rounded-md transition-colors ${
                  timeRange === r
                    ? "bg-primary text-primary-foreground shadow-xs"
                    : "text-muted-foreground hover:text-foreground"
                }`}
              >
                {r.toUpperCase()}
              </button>
            ))}
          </div>

          {/* Refresh Button */}
          <Button
            variant="outline"
            size="sm"
            onClick={() => handleRefresh()}
            disabled={isLoading}
            className="text-xs"
          >
            <RefreshCw className={`mr-1.5 h-3.5 w-3.5 ${isLoading ? "animate-spin" : ""}`} />
            Refresh
          </Button>
        </div>
      </div>

      {/* Filter Bar */}
      <div className="flex flex-wrap items-center gap-4 bg-card p-4 rounded-xl border border-border/80 shadow-xs">
        {/* Product Filter */}
        <div className="flex items-center gap-2">
          <label className="text-xs font-medium text-muted-foreground">Product:</label>
          <select
            value={selectedProductId}
            onChange={(e) => {
              setSelectedProductId(e.target.value);
              setPage(0);
            }}
            className="rounded-md border border-input bg-background px-3 py-1.5 text-xs focus:ring-1 focus:ring-primary focus:outline-hidden"
          >
            <option value="">All Products</option>
            {products.map((p) => (
              <option key={p.id} value={p.id}>
                {p.name}
              </option>
            ))}
          </select>
        </div>

        {/* Decision Filter */}
        <div className="flex items-center gap-2">
          <label className="text-xs font-medium text-muted-foreground">Decision:</label>
          <select
            value={decisionFilter}
            onChange={(e) => {
              setDecisionFilter(e.target.value);
              setPage(0);
            }}
            className="rounded-md border border-input bg-background px-3 py-1.5 text-xs focus:ring-1 focus:ring-primary focus:outline-hidden"
          >
            <option value="">All Decisions</option>
            <option value="ALLOWED">ALLOWED (200 OK)</option>
            <option value="RATE_LIMITED">RATE_LIMITED (429)</option>
            <option value="UNAUTHORIZED">UNAUTHORIZED (401)</option>
            <option value="BLOCKED">BLOCKED (403)</option>
            <option value="NOT_FOUND">NOT_FOUND (404)</option>
          </select>
        </div>
      </div>

      {/* KPI Cards Grid */}
      <div className="grid grid-cols-2 md:grid-cols-5 gap-4">
        {/* Total Requests */}
        <div className="rounded-xl border border-border/80 bg-card p-4">
          <div className="flex items-center justify-between">
            <p className="text-xs font-medium text-muted-foreground">Total Requests</p>
            <Layers className="h-4 w-4 text-muted-foreground" />
          </div>
          <p className="text-2xl font-bold font-mono mt-1">{summary?.totalRequests || 0}</p>
          <p className="text-[11px] text-muted-foreground mt-1">
            {summary?.totalUnitsConsumed || 0} usage units
          </p>
        </div>

        {/* Allowed (200 OK) */}
        <div className="rounded-xl border border-emerald-500/30 bg-emerald-500/5 p-4">
          <div className="flex items-center justify-between">
            <p className="text-xs font-medium text-emerald-600 dark:text-emerald-400">Allowed (200)</p>
            <CheckCircle2 className="h-4 w-4 text-emerald-500" />
          </div>
          <p className="text-2xl font-bold font-mono text-emerald-600 dark:text-emerald-400 mt-1">
            {summary?.allowedRequests || 0}
          </p>
          <p className="text-[11px] text-emerald-600/80 dark:text-emerald-400/80 mt-1">
            {summary && summary.totalRequests > 0
              ? `${Math.round((summary.allowedRequests / summary.totalRequests) * 100)}% of total`
              : "0%"}
          </p>
        </div>

        {/* Rate Limited (429) */}
        <div className="rounded-xl border border-rose-500/30 bg-rose-500/5 p-4">
          <div className="flex items-center justify-between">
            <p className="text-xs font-medium text-rose-600 dark:text-rose-400">Limited (429)</p>
            <Flame className="h-4 w-4 text-rose-500" />
          </div>
          <p className="text-2xl font-bold font-mono text-rose-600 dark:text-rose-400 mt-1">
            {summary?.rateLimitedRequests || 0}
          </p>
          <p className="text-[11px] text-rose-600/80 dark:text-rose-400/80 mt-1">
            {summary && summary.totalRequests > 0
              ? `${Math.round((summary.rateLimitedRequests / summary.totalRequests) * 100)}% blocked`
              : "0%"}
          </p>
        </div>

        {/* Errors (4xx/5xx) */}
        <div className="rounded-xl border border-amber-500/30 bg-amber-500/5 p-4">
          <div className="flex items-center justify-between">
            <p className="text-xs font-medium text-amber-600 dark:text-amber-400">Errors (4xx/5xx)</p>
            <AlertTriangle className="h-4 w-4 text-amber-500" />
          </div>
          <p className="text-2xl font-bold font-mono text-amber-600 dark:text-amber-400 mt-1">
            {(summary?.clientErrorRequests || 0) + (summary?.serverErrorRequests || 0)}
          </p>
          <p className="text-[11px] text-amber-600/80 dark:text-amber-400/80 mt-1">
            {summary?.serverErrorRequests || 0} server / {summary?.clientErrorRequests || 0} client
          </p>
        </div>

        {/* Avg Latency */}
        <div className="rounded-xl border border-border/80 bg-card p-4">
          <div className="flex items-center justify-between">
            <p className="text-xs font-medium text-muted-foreground">Avg Latency</p>
            <Clock className="h-4 w-4 text-muted-foreground" />
          </div>
          <p className="text-2xl font-bold font-mono mt-1">{summary?.avgLatencyMs || 0} ms</p>
          <p className="text-[11px] text-muted-foreground mt-1">Gateway to Upstream</p>
        </div>
      </div>

      {/* Timeseries Volume Visualizer */}
      <div className="rounded-xl border border-border/80 bg-card p-6 shadow-xs space-y-4">
        <div className="flex items-center justify-between border-b border-border/60 pb-3">
          <div className="flex items-center gap-2">
            <TrendingUp className="h-4 w-4 text-primary" />
            <h2 className="text-base font-semibold">Traffic Volume Timeseries</h2>
          </div>
          <span className="text-xs font-mono text-muted-foreground">
            {timeseries.length} data bucket{timeseries.length !== 1 ? "s" : ""}
          </span>
        </div>

        {timeseries.length === 0 ? (
          <div className="py-12 text-center text-muted-foreground">
            <BarChart3 className="mx-auto h-8 w-8 text-muted-foreground/40 mb-2" />
            <p className="text-sm font-medium">No telemetry traffic in selected window</p>
            <p className="text-xs text-muted-foreground/70 mt-1">
              Fire test traffic from the Request Lab to observe real-time chart updates.
            </p>
          </div>
        ) : (
          <div className="space-y-3 pt-2">
            <div className="grid grid-cols-6 sm:grid-cols-12 gap-2 items-end h-44">
              {timeseries.map((bucket, idx) => {
                const heightPercent = Math.max(
                  8,
                  Math.round((bucket.totalRequests / maxBucketRequests) * 100)
                );
                const allowedPercent =
                  bucket.totalRequests > 0
                    ? (bucket.allowedRequests / bucket.totalRequests) * 100
                    : 0;
                const limitedPercent =
                  bucket.totalRequests > 0
                    ? (bucket.rateLimitedRequests / bucket.totalRequests) * 100
                    : 0;

                const timeLabel = new Date(bucket.bucketStart).toLocaleTimeString([], {
                  hour: "2-digit",
                  minute: "2-digit",
                });

                return (
                  <div key={idx} className="flex flex-col items-center gap-1.5 h-full justify-end group">
                    <div className="relative w-full flex flex-col justify-end items-center h-full">
                      {/* Bar Container */}
                      <div
                        style={{ height: `${heightPercent}%` }}
                        className="w-full max-w-[28px] rounded-t-sm flex flex-col overflow-hidden bg-muted transition-all group-hover:opacity-90"
                      >
                        {/* Rate-Limited Segment */}
                        <div
                          style={{ height: `${limitedPercent}%` }}
                          className="w-full bg-rose-500 shrink-0"
                        />
                        {/* Allowed Segment */}
                        <div
                          style={{ height: `${allowedPercent}%` }}
                          className="w-full bg-emerald-500 shrink-0"
                        />
                      </div>
                    </div>
                    <span className="text-[10px] font-mono text-muted-foreground/80 truncate w-full text-center">
                      {timeLabel}
                    </span>
                  </div>
                );
              })}
            </div>

            {/* Chart Legend */}
            <div className="flex items-center justify-end gap-5 text-xs text-muted-foreground pt-3 border-t border-border/40">
              <div className="flex items-center gap-1.5">
                <div className="h-3 w-3 rounded-xs bg-emerald-500" />
                <span>Allowed (200)</span>
              </div>
              <div className="flex items-center gap-1.5">
                <div className="h-3 w-3 rounded-xs bg-rose-500" />
                <span>Rate-Limited (429)</span>
              </div>
              <div className="flex items-center gap-1.5">
                <div className="h-3 w-3 rounded-xs bg-amber-500" />
                <span>Errors</span>
              </div>
            </div>
          </div>
        )}
      </div>

      {/* Top Routes & Top Applications Grid */}
      <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
        {/* Top Routes */}
        <div className="rounded-xl border border-border/80 bg-card p-5 shadow-xs space-y-4">
          <h3 className="text-sm font-semibold">Top API Routes</h3>
          {topRoutes.length === 0 ? (
            <p className="text-xs text-muted-foreground py-4 text-center">No route activity recorded.</p>
          ) : (
            <div className="divide-y divide-border/60">
              {topRoutes.map((rt) => (
                <div key={rt.routeId} className="py-2.5 flex items-center justify-between text-xs">
                  <div className="flex items-center gap-2">
                    <span className="font-mono font-bold px-1.5 py-0.5 rounded bg-muted text-[10px]">
                      {rt.httpMethod}
                    </span>
                    <span className="font-mono text-foreground">{rt.pathPattern}</span>
                  </div>
                  <div className="flex items-center gap-3 text-muted-foreground font-mono">
                    <span>{rt.totalRequests} reqs</span>
                    <span>{rt.totalUnits} units</span>
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>

        {/* Top Applications */}
        <div className="rounded-xl border border-border/80 bg-card p-5 shadow-xs space-y-4">
          <h3 className="text-sm font-semibold">Top Consumer Applications</h3>
          {topApps.length === 0 ? (
            <p className="text-xs text-muted-foreground py-4 text-center">No application activity recorded.</p>
          ) : (
            <div className="divide-y divide-border/60">
              {topApps.map((app) => (
                <div key={app.applicationId} className="py-2.5 flex items-center justify-between text-xs">
                  <span className="font-medium text-foreground">{app.applicationName}</span>
                  <div className="flex items-center gap-3 text-muted-foreground font-mono">
                    <span>{app.totalRequests} reqs</span>
                    <span>{app.totalUnits} units</span>
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>
      </div>

      {/* Raw Event Telemetry Table */}
      <div className="rounded-xl border border-border/80 bg-card shadow-xs overflow-hidden">
        <div className="border-b border-border/60 bg-muted/30 px-5 py-3.5 flex items-center justify-between">
          <div>
            <h3 className="text-sm font-semibold">Live Telemetry Events Stream</h3>
            <p className="text-xs text-muted-foreground">
              Showing {rawEvents.length} of {totalEvents} raw request trace events
            </p>
          </div>

          <div className="flex items-center gap-2">
            <Button
              variant="outline"
              size="sm"
              disabled={page === 0}
              onClick={() => setPage((p) => Math.max(0, p - 1))}
              className="h-8 px-2 text-xs"
            >
              <ChevronLeft className="h-3.5 w-3.5 mr-1" /> Prev
            </Button>
            <span className="text-xs font-mono text-muted-foreground px-1">
              Page {page + 1}
            </span>
            <Button
              variant="outline"
              size="sm"
              disabled={(page + 1) * 20 >= totalEvents}
              onClick={() => setPage((p) => p + 1)}
              className="h-8 px-2 text-xs"
            >
              Next <ChevronRight className="h-3.5 w-3.5 ml-1" />
            </Button>
          </div>
        </div>

        {rawEvents.length === 0 ? (
          <div className="p-12 text-center text-muted-foreground">
            <Zap className="mx-auto h-8 w-8 text-muted-foreground/40 mb-2" />
            <p className="text-sm font-medium">No telemetry events found</p>
            <p className="text-xs text-muted-foreground/70 mt-1">
              Send requests through the API Gateway to generate telemetry events.
            </p>
          </div>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-left text-xs">
              <thead className="border-b border-border/60 bg-muted/20 font-medium text-muted-foreground">
                <tr>
                  <th className="px-4 py-3">Timestamp</th>
                  <th className="px-4 py-3">Method & Path</th>
                  <th className="px-4 py-3">Decision</th>
                  <th className="px-4 py-3">Status</th>
                  <th className="px-4 py-3">Units</th>
                  <th className="px-4 py-3">Latency</th>
                  <th className="px-4 py-3">Request ID</th>
                  <th className="px-4 py-3 text-right">Actions</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-border/60">
                {rawEvents.map((evt) => (
                  <tr
                    key={evt.eventId}
                    className="hover:bg-muted/40 transition-colors cursor-pointer"
                    onClick={() => setSelectedEvent(evt)}
                  >
                    <td className="px-4 py-3 font-mono text-muted-foreground whitespace-nowrap">
                      {new Date(evt.occurredAt).toLocaleTimeString([], {
                        hour: "2-digit",
                        minute: "2-digit",
                        second: "2-digit",
                      })}
                    </td>
                    <td className="px-4 py-3 font-mono font-medium text-foreground">
                      <span className="px-1.5 py-0.5 rounded bg-muted mr-1.5 text-[10px]">
                        {evt.httpMethod}
                      </span>
                      {evt.routeTemplate || "/"}
                    </td>
                    <td className="px-4 py-3">
                      <span
                        className={`inline-flex items-center gap-1 px-2 py-0.5 rounded text-[11px] font-mono font-bold ${
                          evt.decision === "ALLOWED"
                            ? "bg-emerald-500/10 text-emerald-600 dark:text-emerald-400"
                            : evt.decision === "RATE_LIMITED"
                            ? "bg-rose-500/10 text-rose-600 dark:text-rose-400"
                            : "bg-amber-500/10 text-amber-600 dark:text-amber-400"
                        }`}
                      >
                        {evt.decision}
                      </span>
                    </td>
                    <td className="px-4 py-3 font-mono font-bold text-foreground">
                      {evt.statusCode}
                    </td>
                    <td className="px-4 py-3 font-mono text-muted-foreground">
                      {evt.usageUnits}
                    </td>
                    <td className="px-4 py-3 font-mono text-muted-foreground">
                      {evt.latencyMs} ms
                    </td>
                    <td className="px-4 py-3 font-mono text-muted-foreground truncate max-w-[120px]">
                      {evt.requestId}
                    </td>
                    <td className="px-4 py-3 text-right">
                      <Button
                        variant="ghost"
                        size="sm"
                        onClick={(e) => {
                          e.stopPropagation();
                          setSelectedEvent(evt);
                        }}
                        className="h-7 px-2 text-xs"
                      >
                        Inspect
                      </Button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>

      {/* Inspect Event Modal / Drawer */}
      {selectedEvent && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 backdrop-blur-xs p-4">
          <div className="w-full max-w-2xl rounded-xl border border-border bg-card p-6 shadow-xl space-y-4">
            <div className="flex items-center justify-between border-b border-border/60 pb-3">
              <div className="flex items-center gap-2">
                <Code2 className="h-5 w-5 text-primary" />
                <h3 className="text-base font-semibold">Telemetry Event Details</h3>
              </div>
              <Button
                variant="ghost"
                size="sm"
                onClick={() => setSelectedEvent(null)}
                className="h-8 px-2 text-xs"
              >
                Close
              </Button>
            </div>

            <pre className="p-4 rounded-lg bg-muted/70 text-xs font-mono overflow-x-auto max-h-[360px] text-foreground">
              {JSON.stringify(selectedEvent, null, 2)}
            </pre>
          </div>
        </div>
      )}
    </div>
  );
}
