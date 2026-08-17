"use client";

import React, { useState, useEffect, useTransition } from "react";
import { useParams } from "next/navigation";
import { getProducts, getRoutes } from "@/lib/api/products";
import { executeBurstRequests, GatewayResponseResult } from "@/lib/api/gateway";
import { ApiProduct, ApiRoute } from "@/lib/api/types";
import { Button } from "@/components/ui/button";
import {
  Zap,
  Play,
  Flame,
  CheckCircle2,
  AlertTriangle,
  Clock,
  KeyRound,
  RefreshCw,
  Code2,
} from "lucide-react";

export default function RequestLabPage() {
  const params = useParams();
  const workspaceSlug = (params?.workspaceSlug as string) || "acme-apis";

  const [products, setProducts] = useState<ApiProduct[]>([]);
  const [selectedProductId, setSelectedProductId] = useState<string>("");
  const [routes, setRoutes] = useState<ApiRoute[]>([]);
  const [selectedRouteId, setSelectedRouteId] = useState<string>("");

  const [apiKey, setApiKey] = useState<string>(
    "mf_dev_nsdemo123456_seedednorthstardemosecretkey9999"
  );
  const [requestPath, setRequestPath] = useState<string>("/v1/forecast/tokyo");
  const [httpMethod, setHttpMethod] = useState<string>("GET");
  const [burstCount, setBurstCount] = useState<number>(10);

  const [isRunning, startTransition] = useTransition();
  const [results, setResults] = useState<GatewayResponseResult[]>([]);
  const [countdown, setCountdown] = useState<number | null>(null);
  const [selectedResult, setSelectedResult] = useState<GatewayResponseResult | null>(null);

  // Load products on mount
  useEffect(() => {
    async function loadCatalog() {
      try {
        const prods = await getProducts(workspaceSlug);
        setProducts(prods);
        if (prods.length > 0) {
          const firstProd = prods[0];
          setSelectedProductId(firstProd.id);
          const rts = await getRoutes(workspaceSlug, firstProd.id);
          setRoutes(rts);
          if (rts.length > 0) {
            setSelectedRouteId(rts[0].id);
            setRequestPath(rts[0].pathPattern.replace("{city}", "tokyo"));
            setHttpMethod(rts[0].httpMethod);
          }
        }
      } catch (err) {
        console.error("Failed to load catalog for Request Lab", err);
      }
    }
    loadCatalog();
  }, [workspaceSlug]);

  // When product changes, load routes
  const handleProductChange = async (prodId: string) => {
    setSelectedProductId(prodId);
    try {
      const rts = await getRoutes(workspaceSlug, prodId);
      setRoutes(rts);
      if (rts.length > 0) {
        setSelectedRouteId(rts[0].id);
        setRequestPath(rts[0].pathPattern.replace("{city}", "tokyo"));
        setHttpMethod(rts[0].httpMethod);
      }
    } catch (err) {
      console.error("Failed to load routes", err);
    }
  };

  // When route changes, update path
  const handleRouteChange = (routeId: string) => {
    setSelectedRouteId(routeId);
    const rt = routes.find((r) => r.id === routeId);
    if (rt) {
      setRequestPath(rt.pathPattern.replace("{city}", "tokyo"));
      setHttpMethod(rt.httpMethod);
    }
  };

  // Run traffic execution
  const handleExecute = () => {
    startTransition(async () => {
      const responses = await executeBurstRequests(
        {
          method: httpMethod,
          path: requestPath,
          apiKey: apiKey.trim(),
        },
        burstCount
      );

      setResults(responses);
      if (responses.length > 0) {
        setSelectedResult(responses[0]);
      }

      // Check if any response returned Retry-After
      const limited = responses.find((r) => r.retryAfter);
      if (limited && limited.retryAfter) {
        const sec = parseInt(limited.retryAfter, 10);
        if (!isNaN(sec) && sec > 0) {
          setCountdown(sec);
        }
      }
    });
  };

  // Countdown timer effect
  useEffect(() => {
    if (countdown === null || countdown <= 0) return;
    const timer = setInterval(() => {
      setCountdown((prev) => {
        if (prev === null || prev <= 1) return null;
        return prev - 1;
      });
    }, 1000);
    return () => clearInterval(timer);
  }, [countdown]);

  // Summary counts
  const allowedCount = results.filter((r) => r.isAllowed).length;
  const limitedCount = results.filter((r) => r.isRateLimited).length;
  const avgLatency = results.length > 0
    ? Math.round(results.reduce((acc, r) => acc + r.latencyMs, 0) / results.length)
    : 0;

  return (
    <div className="space-y-8 p-6 max-w-7xl mx-auto">
      {/* Header */}
      <div className="flex flex-col md:flex-row md:items-center md:justify-between gap-4 border-b border-border/60 pb-6">
        <div>
          <div className="flex items-center gap-2.5">
            <div className="p-2 rounded-lg bg-primary/10 text-primary">
              <Zap className="h-6 w-6" />
            </div>
            <div>
              <h1 className="text-2xl font-bold tracking-tight">Request Lab</h1>
              <p className="text-sm text-muted-foreground">
                Simulate concurrent API traffic, test token-bucket rate limits, and inspect live gateway responses.
              </p>
            </div>
          </div>
        </div>

        <div className="flex items-center gap-3">
          <Button
            variant="outline"
            size="sm"
            onClick={() => {
              setApiKey("mf_dev_nsdemo123456_seedednorthstardemosecretkey9999");
              setRequestPath("/v1/forecast/tokyo");
              setBurstCount(10);
            }}
            className="text-xs"
          >
            <RefreshCw className="mr-1.5 h-3.5 w-3.5" />
            Load Seed Demo Scenario
          </Button>
        </div>
      </div>

      {/* Main Workbench Grid */}
      <div className="grid grid-cols-1 lg:grid-cols-12 gap-8">
        {/* Left Column: Request Configuration */}
        <div className="lg:col-span-5 space-y-6">
          <div className="rounded-xl border border-border/80 bg-card p-6 shadow-xs space-y-5">
            <div className="flex items-center justify-between border-b border-border/60 pb-3">
              <h2 className="text-base font-semibold">Traffic Configuration</h2>
              <span className="text-xs font-mono px-2 py-0.5 rounded bg-primary/10 text-primary font-medium">
                Gateway: :8890
              </span>
            </div>

            {/* Target Product */}
            <div className="space-y-1.5">
              <label className="text-xs font-medium text-muted-foreground uppercase tracking-wider">
                API Product
              </label>
              <select
                value={selectedProductId}
                onChange={(e) => handleProductChange(e.target.value)}
                className="w-full rounded-md border border-input bg-background px-3 py-2 text-sm focus:ring-1 focus:ring-primary focus:outline-hidden"
              >
                {products.map((p) => (
                  <option key={p.id} value={p.id}>
                    {p.name} ({p.gatewayBasePath})
                  </option>
                ))}
              </select>
            </div>

            {/* Target Route */}
            <div className="space-y-1.5">
              <label className="text-xs font-medium text-muted-foreground uppercase tracking-wider">
                Route Template
              </label>
              <select
                value={selectedRouteId}
                onChange={(e) => handleRouteChange(e.target.value)}
                className="w-full rounded-md border border-input bg-background px-3 py-2 text-sm focus:ring-1 focus:ring-primary focus:outline-hidden"
              >
                {routes.map((r) => (
                  <option key={r.id} value={r.id}>
                    {r.httpMethod} {r.pathPattern} ({r.costUnits} unit{r.costUnits > 1 ? "s" : ""})
                  </option>
                ))}
              </select>
            </div>

            {/* Request Path */}
            <div className="space-y-1.5">
              <label className="text-xs font-medium text-muted-foreground uppercase tracking-wider">
                Actual Request URI
              </label>
              <div className="flex items-center gap-2">
                <span className="px-2.5 py-1.5 text-xs font-bold font-mono rounded bg-muted border border-border">
                  {httpMethod}
                </span>
                <input
                  type="text"
                  value={requestPath}
                  onChange={(e) => setRequestPath(e.target.value)}
                  placeholder="/v1/forecast/tokyo"
                  className="flex-1 rounded-md border border-input bg-background px-3 py-2 text-sm font-mono focus:ring-1 focus:ring-primary focus:outline-hidden"
                />
              </div>
            </div>

            {/* API Key */}
            <div className="space-y-1.5">
              <div className="flex items-center justify-between">
                <label className="text-xs font-medium text-muted-foreground uppercase tracking-wider flex items-center gap-1.5">
                  <KeyRound className="h-3.5 w-3.5" />
                  API Key Header (X-API-Key)
                </label>
              </div>
              <input
                type="text"
                value={apiKey}
                onChange={(e) => setApiKey(e.target.value)}
                placeholder="mf_dev_..."
                className="w-full rounded-md border border-input bg-background px-3 py-2 text-xs font-mono focus:ring-1 focus:ring-primary focus:outline-hidden"
              />
              <p className="text-[11px] text-muted-foreground">
                Seed key: <code className="bg-muted px-1 py-0.5 rounded">nsdemo123456</code> (Free Tier: 5 capacity / 10s).
              </p>
            </div>

            {/* Concurrency Selector */}
            <div className="space-y-2 pt-2 border-t border-border/60">
              <label className="text-xs font-medium text-muted-foreground uppercase tracking-wider">
                Burst Concurrency
              </label>
              <div className="grid grid-cols-4 gap-2">
                {[1, 5, 10, 20].map((count) => (
                  <button
                    key={count}
                    type="button"
                    onClick={() => setBurstCount(count)}
                    className={`py-2 text-xs font-semibold rounded-md border transition-all ${
                      burstCount === count
                        ? "bg-primary text-primary-foreground border-primary shadow-xs"
                        : "bg-muted/40 hover:bg-muted border-border text-foreground"
                    }`}
                  >
                    {count === 1 ? "1 (Single)" : `${count} Burst`}
                  </button>
                ))}
              </div>
            </div>

            {/* Execute Button */}
            <Button
              onClick={handleExecute}
              disabled={isRunning || !apiKey.trim() || !requestPath.trim()}
              className="w-full font-semibold py-2.5"
            >
              {isRunning ? (
                <>
                  <RefreshCw className="mr-2 h-4 w-4 animate-spin" />
                  Dispatching {burstCount} Requests...
                </>
              ) : (
                <>
                  <Play className="mr-2 h-4 w-4 fill-current" />
                  Fire {burstCount} Concurrent Request{burstCount > 1 ? "s" : ""}
                </>
              )}
            </Button>
          </div>

          {/* Refill Countdown Alert */}
          {countdown !== null && (
            <div className="rounded-xl border border-destructive/30 bg-destructive/5 p-4 flex items-center gap-3">
              <div className="p-2 rounded-lg bg-destructive/10 text-destructive shrink-0">
                <Clock className="h-5 w-5 animate-pulse" />
              </div>
              <div>
                <p className="text-xs font-bold text-destructive">Rate Limit Bucket Empty</p>
                <p className="text-xs text-muted-foreground">
                  Refilling in <span className="font-bold font-mono text-destructive">{countdown}s</span>. Next request will succeed when tokens replenish.
                </p>
              </div>
            </div>
          )}
        </div>

        {/* Right Column: Execution Metrics & Results */}
        <div className="lg:col-span-7 space-y-6">
          {/* Top Metric Cards */}
          <div className="grid grid-cols-2 sm:grid-cols-4 gap-4">
            <div className="rounded-xl border border-border/80 bg-card p-4">
              <p className="text-xs font-medium text-muted-foreground">Total Sent</p>
              <p className="text-2xl font-bold font-mono mt-1">{results.length}</p>
            </div>
            <div className="rounded-xl border border-emerald-500/30 bg-emerald-500/5 p-4">
              <div className="flex items-center justify-between">
                <p className="text-xs font-medium text-emerald-600 dark:text-emerald-400">Allowed (200)</p>
                <CheckCircle2 className="h-4 w-4 text-emerald-500" />
              </div>
              <p className="text-2xl font-bold font-mono text-emerald-600 dark:text-emerald-400 mt-1">
                {allowedCount}
              </p>
            </div>
            <div className="rounded-xl border border-rose-500/30 bg-rose-500/5 p-4">
              <div className="flex items-center justify-between">
                <p className="text-xs font-medium text-rose-600 dark:text-rose-400">Limited (429)</p>
                <Flame className="h-4 w-4 text-rose-500" />
              </div>
              <p className="text-2xl font-bold font-mono text-rose-600 dark:text-rose-400 mt-1">
                {limitedCount}
              </p>
            </div>
            <div className="rounded-xl border border-border/80 bg-card p-4">
              <p className="text-xs font-medium text-muted-foreground">Avg Latency</p>
              <p className="text-2xl font-bold font-mono mt-1">{avgLatency} ms</p>
            </div>
          </div>

          {/* Results Stream */}
          <div className="rounded-xl border border-border/80 bg-card overflow-hidden shadow-xs">
            <div className="border-b border-border/60 bg-muted/30 px-4 py-3 flex items-center justify-between">
              <h3 className="text-sm font-semibold">Live Request Stream</h3>
              <span className="text-xs text-muted-foreground">
                {results.length > 0 ? `${results.length} responses recorded` : "No traffic fired yet"}
              </span>
            </div>

            {results.length === 0 ? (
              <div className="p-12 text-center text-muted-foreground">
                <Zap className="mx-auto h-8 w-8 text-muted-foreground/40 mb-3" />
                <p className="text-sm font-medium">Ready to execute traffic</p>
                <p className="text-xs text-muted-foreground/70 mt-1">
                  Click &ldquo;Fire Concurrent Requests&rdquo; to test the live token-bucket limiter.
                </p>
              </div>
            ) : (
              <div className="divide-y divide-border/60 max-h-[340px] overflow-y-auto">
                {results.map((res) => (
                  <div
                    key={res.id}
                    onClick={() => setSelectedResult(res)}
                    className={`px-4 py-3 flex items-center justify-between hover:bg-muted/40 transition-colors cursor-pointer text-sm ${
                      selectedResult?.id === res.id ? "bg-muted/60" : ""
                    }`}
                  >
                    <div className="flex items-center gap-3">
                      <span className="text-xs font-mono text-muted-foreground w-6">
                        #{res.index}
                      </span>
                      <span
                        className={`inline-flex items-center gap-1 px-2.5 py-0.5 rounded text-xs font-mono font-bold ${
                          res.isAllowed
                            ? "bg-emerald-500/10 text-emerald-600 dark:text-emerald-400 border border-emerald-500/20"
                            : res.isRateLimited
                            ? "bg-rose-500/10 text-rose-600 dark:text-rose-400 border border-rose-500/20"
                            : "bg-amber-500/10 text-amber-600 dark:text-amber-400 border border-amber-500/20"
                        }`}
                      >
                        {res.isAllowed ? (
                          <CheckCircle2 className="h-3 w-3" />
                        ) : res.isRateLimited ? (
                          <Flame className="h-3 w-3" />
                        ) : (
                          <AlertTriangle className="h-3 w-3" />
                        )}
                        {res.statusCode || "ERR"} {res.statusText}
                      </span>
                    </div>

                    <div className="flex items-center gap-4 text-xs font-mono text-muted-foreground">
                      {res.remaining !== null && (
                        <span>
                          rem: <strong className="text-foreground">{res.remaining}</strong>
                        </span>
                      )}
                      {res.retryAfter !== null && (
                        <span className="text-rose-500 font-semibold">
                          retry: {res.retryAfter}s
                        </span>
                      )}
                      <span>{res.latencyMs}ms</span>
                    </div>
                  </div>
                ))}
              </div>
            )}
          </div>

          {/* Selected Request Inspection Box */}
          {selectedResult && (
            <div className="rounded-xl border border-border/80 bg-card p-4 space-y-3">
              <div className="flex items-center justify-between border-b border-border/60 pb-2">
                <div className="flex items-center gap-2">
                  <Code2 className="h-4 w-4 text-primary" />
                  <h4 className="text-xs font-semibold uppercase tracking-wider">
                    Response Payload & Headers (Request #{selectedResult.index})
                  </h4>
                </div>
                {selectedResult.requestId && (
                  <span className="text-[11px] font-mono text-muted-foreground">
                    Req ID: {selectedResult.requestId.substring(0, 8)}...
                  </span>
                )}
              </div>

              <pre className="p-3 rounded-lg bg-muted/60 text-xs font-mono overflow-x-auto max-h-[160px] text-foreground/90">
                {JSON.stringify(selectedResult.body, null, 2)}
              </pre>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
