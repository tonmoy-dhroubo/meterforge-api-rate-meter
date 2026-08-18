"use client";

import React, { useState, useEffect } from "react";
import { useParams } from "next/navigation";
import Link from "next/link";
import { getUsageSummary, UsageSummary } from "@/lib/api/usage";
import { getProducts } from "@/lib/api/products";
import { fetchConsumers } from "@/lib/api/consumers";
import { ApiProduct, Consumer } from "@/lib/api/types";
import { Button } from "@/components/ui/button";
import {
  Zap,
  BarChart3,
  Boxes,
  Users,
  CheckCircle2,
  Flame,
  ArrowRight,
  Sparkles,
  ShieldCheck,
  Play,
} from "lucide-react";

export default function WorkspaceOverviewPage() {
  const params = useParams();
  const workspaceSlug = (params?.workspaceSlug as string) || "acme-apis";

  const [products, setProducts] = useState<ApiProduct[]>([]);
  const [consumers, setConsumers] = useState<Consumer[]>([]);
  const [summary, setSummary] = useState<UsageSummary | null>(null);
  const [isLoading, setIsLoading] = useState<boolean>(true);

  useEffect(() => {
    async function loadData() {
      setIsLoading(true);
      try {
        const [prodsRes, consumersRes, summaryRes] = await Promise.all([
          getProducts(workspaceSlug).catch(() => []),
          fetchConsumers(workspaceSlug).catch(() => []),
          getUsageSummary(workspaceSlug).catch(() => null),
        ]);
        setProducts(prodsRes);
        setConsumers(consumersRes);
        setSummary(summaryRes);
      } catch (err) {
        console.error("Failed to load workspace overview data", err);
      } finally {
        setIsLoading(false);
      }
    }
    loadData();
  }, [workspaceSlug]);

  return (
    <div className="space-y-8 p-6 max-w-7xl mx-auto">
      {/* Welcome Banner */}
      <div className="rounded-2xl border border-primary/20 bg-linear-to-r from-primary/5 via-card to-card p-6 md:p-8 shadow-xs">
        <div className="flex flex-col md:flex-row md:items-center md:justify-between gap-6">
          <div className="space-y-2">
            <div className="inline-flex items-center gap-1.5 px-2.5 py-0.5 rounded-full bg-primary/10 text-primary text-xs font-semibold">
              <Sparkles className="h-3.5 w-3.5" />
              <span>MeterForge Portfolio Edition</span>
            </div>
            <h1 className="text-2xl md:text-3xl font-bold tracking-tight text-foreground">
              Workspace Overview: <span className="text-primary capitalize">{workspaceSlug.replace(/-/g, " ")}</span>
            </h1>
            <p className="text-sm text-muted-foreground max-w-2xl">
              High-performance API rate limiting and metering platform powered by Spring Cloud Gateway, Redis Lua atomic multi-policy limiting, and Kafka durable usage ingestion.
            </p>
          </div>

          <div className="flex items-center gap-3 shrink-0">
            <Link href={`/${workspaceSlug}/lab`}>
              <Button className="font-semibold shadow-xs">
                <Play className="mr-2 h-4 w-4 fill-current" />
                Launch Request Lab
              </Button>
            </Link>
          </div>
        </div>
      </div>

      {/* KPI Overview Grid */}
      <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
        {/* Products */}
        <div className="rounded-xl border border-border/80 bg-card p-5 shadow-xs">
          <div className="flex items-center justify-between">
            <p className="text-xs font-medium text-muted-foreground">API Products</p>
            <Boxes className="h-4 w-4 text-muted-foreground" />
          </div>
          <p className="text-2xl font-bold font-mono mt-2">{products.length}</p>
          <Link
            href={`/${workspaceSlug}/products`}
            className="text-xs text-primary hover:underline mt-2 inline-flex items-center gap-1"
          >
            Manage Products <ArrowRight className="h-3 w-3" />
          </Link>
        </div>

        {/* Consumers */}
        <div className="rounded-xl border border-border/80 bg-card p-5 shadow-xs">
          <div className="flex items-center justify-between">
            <p className="text-xs font-medium text-muted-foreground">Consumers & Apps</p>
            <Users className="h-4 w-4 text-muted-foreground" />
          </div>
          <p className="text-2xl font-bold font-mono mt-2">{consumers.length}</p>
          <Link
            href={`/${workspaceSlug}/consumers`}
            className="text-xs text-primary hover:underline mt-2 inline-flex items-center gap-1"
          >
            View Consumers <ArrowRight className="h-3 w-3" />
          </Link>
        </div>

        {/* Allowed Requests */}
        <div className="rounded-xl border border-emerald-500/30 bg-emerald-500/5 p-5 shadow-xs">
          <div className="flex items-center justify-between">
            <p className="text-xs font-medium text-emerald-600 dark:text-emerald-400">Total Allowed</p>
            <CheckCircle2 className="h-4 w-4 text-emerald-500" />
          </div>
          <p className="text-2xl font-bold font-mono text-emerald-600 dark:text-emerald-400 mt-2">
            {summary?.allowedRequests || 0}
          </p>
          <p className="text-xs text-muted-foreground mt-2">200 OK Upstream Admitted</p>
        </div>

        {/* Rate Limited Requests */}
        <div className="rounded-xl border border-rose-500/30 bg-rose-500/5 p-5 shadow-xs">
          <div className="flex items-center justify-between">
            <p className="text-xs font-medium text-rose-600 dark:text-rose-400">Rate Limited</p>
            <Flame className="h-4 w-4 text-rose-500" />
          </div>
          <p className="text-2xl font-bold font-mono text-rose-600 dark:text-rose-400 mt-2">
            {summary?.rateLimitedRequests || 0}
          </p>
          <p className="text-xs text-muted-foreground mt-2">429 Blocked Decisions</p>
        </div>
      </div>

      {/* Feature Cards Grid */}
      <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
        {/* Request Lab Card */}
        <div className="rounded-xl border border-border/80 bg-card p-6 shadow-xs flex flex-col justify-between space-y-4">
          <div className="space-y-2">
            <div className="p-2.5 rounded-lg bg-primary/10 text-primary w-fit">
              <Zap className="h-5 w-5" />
            </div>
            <h3 className="text-base font-semibold">Interactive Request Lab</h3>
            <p className="text-xs text-muted-foreground">
              Simulate burst concurrency (up to 20 concurrent requests), test token bucket refill periods, and observe immediate 429 rate limit enforcement.
            </p>
          </div>
          <Link href={`/${workspaceSlug}/lab`}>
            <Button variant="outline" className="w-full text-xs font-semibold">
              Open Request Lab <ArrowRight className="ml-1.5 h-3.5 w-3.5" />
            </Button>
          </Link>
        </div>

        {/* Usage Analytics Card */}
        <div className="rounded-xl border border-border/80 bg-card p-6 shadow-xs flex flex-col justify-between space-y-4">
          <div className="space-y-2">
            <div className="p-2.5 rounded-lg bg-primary/10 text-primary w-fit">
              <BarChart3 className="h-5 w-5" />
            </div>
            <h3 className="text-base font-semibold">Usage & Telemetry</h3>
            <p className="text-xs text-muted-foreground">
              Inspect aggregated timeseries rollups, top consumer routes, error rates, and paginated raw request event telemetry streams.
            </p>
          </div>
          <Link href={`/${workspaceSlug}/usage`}>
            <Button variant="outline" className="w-full text-xs font-semibold">
              View Analytics <ArrowRight className="ml-1.5 h-3.5 w-3.5" />
            </Button>
          </Link>
        </div>

        {/* Plan & Policies Card */}
        <div className="rounded-xl border border-border/80 bg-card p-6 shadow-xs flex flex-col justify-between space-y-4">
          <div className="space-y-2">
            <div className="p-2.5 rounded-lg bg-primary/10 text-primary w-fit">
              <ShieldCheck className="h-5 w-5" />
            </div>
            <h3 className="text-base font-semibold">Plans & Limit Policies</h3>
            <p className="text-xs text-muted-foreground">
              Configure token-bucket rate limits and fixed-window daily/monthly quotas evaluated atomically in a single Redis Lua script.
            </p>
          </div>
          <Link href={`/${workspaceSlug}/plans`}>
            <Button variant="outline" className="w-full text-xs font-semibold">
              Manage Plans <ArrowRight className="ml-1.5 h-3.5 w-3.5" />
            </Button>
          </Link>
        </div>
      </div>
    </div>
  );
}
