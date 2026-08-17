"use client";

import React, { useState } from "react";
import { useParams } from "next/navigation";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { useAuth } from "@/lib/auth-context";
import { getProducts } from "@/lib/api/products";
import {
  fetchPlans,
  createPlan,
  addPolicyToPlan,
  togglePolicy,
  activatePlan,
  disablePlan,
} from "@/lib/api/plans";
import { ApiProduct, Plan, LimitPolicyKind, QuotaPeriod } from "@/lib/api/types";
import { ApiError } from "@/lib/api/client";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Badge } from "@/components/ui/badge";
import { Card, CardHeader, CardTitle, CardDescription, CardContent } from "@/components/ui/card";
import { Dialog } from "@/components/ui/dialog";
import { Alert } from "@/components/ui/alert";
import {
  FileSpreadsheet,
  Plus,
  Zap,
  Calendar,
  Layers,
  CheckCircle2,
  XCircle,
  ToggleLeft,
  ToggleRight,
} from "lucide-react";

export default function PlansPage() {
  const params = useParams();
  const workspaceSlug = (params?.workspaceSlug as string) || "acme-apis";
  const { currentMembership, currentRole } = useAuth();
  const workspaceId = currentMembership?.workspaceId;
  const queryClient = useQueryClient();

  const isViewer = currentRole === "VIEWER";

  // Create Plan Form State
  const [isCreatePlanOpen, setIsCreatePlanOpen] = useState(false);
  const [selectedProductId, setSelectedProductId] = useState("");
  const [planName, setPlanName] = useState("");
  const [planSlug, setPlanSlug] = useState("");

  // Initial Policy options in create plan
  const [includeRatePolicy, setIncludeRatePolicy] = useState(true);
  const [rateCapacity, setRateCapacity] = useState("5");
  const [rateRefillTokens, setRateRefillTokens] = useState("5");
  const [ratePeriodSeconds, setRatePeriodSeconds] = useState("10");

  const [includeQuotaPolicy, setIncludeQuotaPolicy] = useState(true);
  const [quotaLimit, setQuotaLimit] = useState("100");
  const [quotaPeriod, setQuotaPeriod] = useState<QuotaPeriod>("DAY");

  // Add Policy to Existing Plan State
  const [activePlanIdForPolicy, setActivePlanIdForPolicy] = useState<string | null>(null);
  const [newPolicyKind, setNewPolicyKind] = useState<LimitPolicyKind>("RATE");
  const [newRateCapacity, setNewRateCapacity] = useState("10");
  const [newRateRefillTokens, setNewRateRefillTokens] = useState("10");
  const [newRatePeriodSeconds, setNewRatePeriodSeconds] = useState("1");
  const [newQuotaLimit, setNewQuotaLimit] = useState("1000");
  const [newQuotaPeriod, setNewQuotaPeriod] = useState<QuotaPeriod>("DAY");

  const [formError, setFormError] = useState<string | null>(null);

  // Queries
  const { data: products = [] } = useQuery<ApiProduct[]>({
    queryKey: ["products", workspaceId],
    queryFn: () => (workspaceId ? getProducts(workspaceId) : Promise.resolve([])),
    enabled: !!workspaceId,
  });

  const { data: plans = [], isLoading: isPlansLoading } = useQuery<Plan[]>({
    queryKey: ["plans", workspaceId],
    queryFn: () => (workspaceId ? fetchPlans(workspaceId) : Promise.resolve([])),
    enabled: !!workspaceId,
  });

  // Mutations
  const createPlanMutation = useMutation({
    mutationFn: (data: any) => createPlan(workspaceId!, data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["plans", workspaceId] });
      setIsCreatePlanOpen(false);
      resetCreateForm();
    },
    onError: (err: unknown) => {
      if (err instanceof ApiError) {
        setFormError(err.problem?.detail || err.message);
      } else if (err instanceof Error) {
        setFormError(err.message);
      } else {
        setFormError("Failed to create plan");
      }
    },
  });

  const addPolicyMutation = useMutation({
    mutationFn: ({ planId, policy }: { planId: string; policy: any }) =>
      addPolicyToPlan(workspaceId!, planId, policy),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["plans", workspaceId] });
      setActivePlanIdForPolicy(null);
      setFormError(null);
    },
    onError: (err: unknown) => {
      if (err instanceof ApiError) {
        setFormError(err.problem?.detail || err.message);
      } else if (err instanceof Error) {
        setFormError(err.message);
      } else {
        setFormError("Failed to add policy");
      }
    },
  });

  const togglePolicyMutation = useMutation({
    mutationFn: ({ planId, policyId, enabled }: { planId: string; policyId: string; enabled: boolean }) =>
      togglePolicy(workspaceId!, planId, policyId, enabled),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["plans", workspaceId] });
    },
  });

  const togglePlanStatusMutation = useMutation({
    mutationFn: ({ planId, status }: { planId: string; status: "ACTIVE" | "DISABLED" }) =>
      status === "ACTIVE"
        ? activatePlan(workspaceId!, planId)
        : disablePlan(workspaceId!, planId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["plans", workspaceId] });
    },
  });

  const resetCreateForm = () => {
    setPlanName("");
    setPlanSlug("");
    setSelectedProductId(products?.[0]?.id || "");
    setFormError(null);
  };

  const handleNameChange = (val: string) => {
    setPlanName(val);
    setPlanSlug(val.toLowerCase().replace(/[^a-z0-9]+/g, "-").replace(/^-|-$/g, ""));
  };

  const handleCreatePlan = () => {
    const policies: any[] = [];
    if (includeRatePolicy) {
      policies.push({
        kind: "RATE",
        capacity: parseInt(rateCapacity, 10),
        refillTokens: parseInt(rateRefillTokens, 10),
        refillPeriodSeconds: parseInt(ratePeriodSeconds, 10),
      });
    }
    if (includeQuotaPolicy) {
      policies.push({
        kind: "QUOTA",
        quotaLimit: parseInt(quotaLimit, 10),
        quotaPeriod,
      });
    }

    createPlanMutation.mutate({
      productId: selectedProductId || products?.[0]?.id,
      name: planName,
      slug: planSlug,
      policies,
    });
  };

  const handleAddPolicy = () => {
    if (!activePlanIdForPolicy) return;
    const policy: any = { kind: newPolicyKind };
    if (newPolicyKind === "RATE") {
      policy.capacity = parseInt(newRateCapacity, 10);
      policy.refillTokens = parseInt(newRateRefillTokens, 10);
      policy.refillPeriodSeconds = parseInt(newRatePeriodSeconds, 10);
    } else {
      policy.quotaLimit = parseInt(newQuotaLimit, 10);
      policy.quotaPeriod = newQuotaPeriod;
    }
    addPolicyMutation.mutate({ planId: activePlanIdForPolicy, policy });
  };

  return (
    <div className="space-y-6">
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold tracking-tight">Plans & Limit Policies</h1>
          <p className="text-sm text-muted-foreground">
            Define reusable rate limit token buckets and fixed-window calendar quotas for your API products.
          </p>
        </div>
        {!isViewer && (
          <Button
            onClick={() => {
              if (products && products.length > 0) {
                setSelectedProductId(products[0].id);
              }
              setIsCreatePlanOpen(true);
            }}
            className="gap-2"
          >
            <Plus className="h-4 w-4" />
            Create Plan
          </Button>
        )}
      </div>

      {isPlansLoading ? (
        <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
          {[1, 2].map((i) => (
            <Card key={i} className="animate-pulse h-48 bg-muted/40" />
          ))}
        </div>
      ) : plans.length === 0 ? (
        <Card className="text-center py-12">
          <CardContent className="space-y-4">
            <FileSpreadsheet className="mx-auto h-12 w-12 text-muted-foreground/60" />
            <div>
              <h3 className="text-lg font-semibold">No plans defined</h3>
              <p className="text-sm text-muted-foreground">
                Create a tier or package (e.g. Free Tier, Pro Tier) with rate limits and quota allowances.
              </p>
            </div>
            {!isViewer && (
              <Button
                onClick={() => {
                  if (products && products.length > 0) setSelectedProductId(products[0].id);
                  setIsCreatePlanOpen(true);
                }}
                variant="outline"
                className="gap-2"
              >
                <Plus className="h-4 w-4" />
                Create First Plan
              </Button>
            )}
          </CardContent>
        </Card>
      ) : (
        <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
          {plans.map((plan) => {
            const product = products.find((p) => p.id === plan.productId);
            return (
              <Card key={plan.id} className="flex flex-col justify-between border-border">
                <CardHeader className="pb-3 border-b bg-muted/20">
                  <div className="flex items-start justify-between gap-2">
                    <div className="space-y-1">
                      <div className="flex items-center gap-2">
                        <FileSpreadsheet className="h-4 w-4 text-primary" />
                        <CardTitle className="text-base font-semibold">{plan.name}</CardTitle>
                        <span className="font-mono text-xs text-muted-foreground">({plan.slug})</span>
                      </div>
                      {product && (
                        <CardDescription className="text-xs flex items-center gap-1.5 font-medium text-foreground/80">
                          <Layers className="h-3.5 w-3.5 text-muted-foreground" />
                          Product: {product.name}
                        </CardDescription>
                      )}
                    </div>
                    <div className="flex items-center gap-2">
                      <Badge variant={plan.status === "ACTIVE" ? "default" : "secondary"}>
                        {plan.status}
                      </Badge>
                      {!isViewer && (
                        <Button
                          size="sm"
                          variant="ghost"
                          className="h-7 text-xs px-2"
                          onClick={() =>
                            togglePlanStatusMutation.mutate({
                              planId: plan.id,
                              status: plan.status === "ACTIVE" ? "DISABLED" : "ACTIVE",
                            })
                          }
                        >
                          {plan.status === "ACTIVE" ? "Disable" : "Activate"}
                        </Button>
                      )}
                    </div>
                  </div>
                </CardHeader>
                <CardContent className="p-4 space-y-4">
                  <div className="flex items-center justify-between">
                    <span className="text-xs font-semibold uppercase text-muted-foreground tracking-wider">
                      Enforced Limit Policies ({plan.policies?.length || 0})
                    </span>
                    {!isViewer && (
                      <Button
                        size="sm"
                        variant="outline"
                        className="h-6 text-[11px] gap-1 px-2"
                        onClick={() => setActivePlanIdForPolicy(plan.id)}
                      >
                        <Plus className="h-3 w-3" /> Add Policy
                      </Button>
                    )}
                  </div>

                  <div className="space-y-2">
                    {plan.policies?.length === 0 ? (
                      <p className="text-xs text-muted-foreground italic py-1">
                        No rate or quota policies configured for this plan.
                      </p>
                    ) : (
                      plan.policies?.map((policy) => (
                        <div
                          key={policy.id}
                          className="flex items-center justify-between p-2.5 rounded-md border border-border/70 bg-card/60 text-xs"
                        >
                          <div className="flex items-center gap-2.5">
                            {policy.kind === "RATE" ? (
                              <Zap className="h-4 w-4 text-amber-500 shrink-0" />
                            ) : (
                              <Calendar className="h-4 w-4 text-sky-500 shrink-0" />
                            )}
                            <div>
                              <div className="font-medium flex items-center gap-2">
                                <span>
                                  {policy.kind === "RATE"
                                    ? `Token Bucket: ${policy.capacity} capacity (${policy.refillTokens} tokens / ${policy.refillPeriodSeconds}s)`
                                    : `Fixed Window Quota: ${policy.quotaLimit?.toLocaleString()} requests / ${policy.quotaPeriod?.toLowerCase()}`}
                                </span>
                              </div>
                              <p className="text-[11px] text-muted-foreground">
                                {policy.routeId ? "Route-specific rule" : "Product-wide allowance"}
                              </p>
                            </div>
                          </div>

                          <div className="flex items-center gap-2">
                            {policy.enabled ? (
                              <span className="flex items-center gap-1 text-[11px] text-emerald-600 dark:text-emerald-400 font-medium">
                                <CheckCircle2 className="h-3.5 w-3.5" /> Enabled
                              </span>
                            ) : (
                              <span className="flex items-center gap-1 text-[11px] text-muted-foreground font-medium">
                                <XCircle className="h-3.5 w-3.5" /> Disabled
                              </span>
                            )}
                            {!isViewer && (
                              <Button
                                size="sm"
                                variant="ghost"
                                className="h-7 w-7 p-0 text-muted-foreground"
                                onClick={() =>
                                  togglePolicyMutation.mutate({
                                    planId: plan.id,
                                    policyId: policy.id,
                                    enabled: !policy.enabled,
                                  })
                                }
                                title={policy.enabled ? "Disable policy" : "Enable policy"}
                              >
                                {policy.enabled ? (
                                  <ToggleRight className="h-4 w-4 text-primary" />
                                ) : (
                                  <ToggleLeft className="h-4 w-4" />
                                )}
                              </Button>
                            )}
                          </div>
                        </div>
                      ))
                    )}
                  </div>
                </CardContent>
              </Card>
            );
          })}
        </div>
      )}

      {/* Create Plan Dialog */}
      <Dialog
        isOpen={isCreatePlanOpen}
        onClose={() => {
          setIsCreatePlanOpen(false);
          setFormError(null);
        }}
        title="Create API Plan"
        description="Configure a subscription plan with token-bucket burst limits and daily/monthly quotas."
      >
        <div className="space-y-4">
          {formError && <Alert variant="destructive">{formError}</Alert>}

          <div className="space-y-3">
            <div className="space-y-1.5">
              <label className="text-xs font-semibold uppercase text-muted-foreground">
                Target API Product
              </label>
              <select
                className="w-full h-9 rounded-md border border-input bg-background px-3 py-1 text-sm shadow-xs focus:outline-none focus:ring-1 focus:ring-ring"
                value={selectedProductId}
                onChange={(e) => setSelectedProductId(e.target.value)}
              >
                {products.map((p) => (
                  <option key={p.id} value={p.id}>
                    {p.name} ({p.gatewayBasePath})
                  </option>
                ))}
              </select>
            </div>

            <div className="grid grid-cols-2 gap-3">
              <div className="space-y-1.5">
                <label className="text-xs font-semibold uppercase text-muted-foreground">
                  Plan Name
                </label>
                <Input
                  placeholder="e.g. Free Tier"
                  value={planName}
                  onChange={(e) => handleNameChange(e.target.value)}
                />
              </div>
              <div className="space-y-1.5">
                <label className="text-xs font-semibold uppercase text-muted-foreground">
                  Slug
                </label>
                <Input
                  placeholder="free-tier"
                  value={planSlug}
                  onChange={(e) => setPlanSlug(e.target.value)}
                />
              </div>
            </div>

            {/* Rate Limit Token Bucket Section */}
            <div className="p-3 rounded-md border bg-muted/20 space-y-3">
              <div className="flex items-center justify-between">
                <div className="flex items-center gap-2">
                  <Zap className="h-4 w-4 text-amber-500" />
                  <span className="text-xs font-bold uppercase tracking-wide">
                    Rate Limit (Token Bucket)
                  </span>
                </div>
                <input
                  type="checkbox"
                  checked={includeRatePolicy}
                  onChange={(e) => setIncludeRatePolicy(e.target.checked)}
                  className="rounded border-input text-primary focus:ring-primary h-4 w-4"
                />
              </div>

              {includeRatePolicy && (
                <div className="grid grid-cols-3 gap-2 pt-1">
                  <div className="space-y-1">
                    <label className="text-[11px] text-muted-foreground">Capacity</label>
                    <Input
                      type="number"
                      value={rateCapacity}
                      onChange={(e) => setRateCapacity(e.target.value)}
                      className="h-8 text-xs font-mono"
                    />
                  </div>
                  <div className="space-y-1">
                    <label className="text-[11px] text-muted-foreground">Refill Count</label>
                    <Input
                      type="number"
                      value={rateRefillTokens}
                      onChange={(e) => setRateRefillTokens(e.target.value)}
                      className="h-8 text-xs font-mono"
                    />
                  </div>
                  <div className="space-y-1">
                    <label className="text-[11px] text-muted-foreground">Period (Sec)</label>
                    <Input
                      type="number"
                      value={ratePeriodSeconds}
                      onChange={(e) => setRatePeriodSeconds(e.target.value)}
                      className="h-8 text-xs font-mono"
                    />
                  </div>
                </div>
              )}
            </div>

            {/* Quota Section */}
            <div className="p-3 rounded-md border bg-muted/20 space-y-3">
              <div className="flex items-center justify-between">
                <div className="flex items-center gap-2">
                  <Calendar className="h-4 w-4 text-sky-500" />
                  <span className="text-xs font-bold uppercase tracking-wide">
                    Calendar Quota (Fixed Window)
                  </span>
                </div>
                <input
                  type="checkbox"
                  checked={includeQuotaPolicy}
                  onChange={(e) => setIncludeQuotaPolicy(e.target.checked)}
                  className="rounded border-input text-primary focus:ring-primary h-4 w-4"
                />
              </div>

              {includeQuotaPolicy && (
                <div className="grid grid-cols-2 gap-3 pt-1">
                  <div className="space-y-1">
                    <label className="text-[11px] text-muted-foreground">Allowance</label>
                    <Input
                      type="number"
                      value={quotaLimit}
                      onChange={(e) => setQuotaLimit(e.target.value)}
                      className="h-8 text-xs font-mono"
                    />
                  </div>
                  <div className="space-y-1">
                    <label className="text-[11px] text-muted-foreground">Window Period</label>
                    <select
                      className="w-full h-8 rounded-md border border-input bg-background px-2 text-xs shadow-xs"
                      value={quotaPeriod}
                      onChange={(e) => setQuotaPeriod(e.target.value as QuotaPeriod)}
                    >
                      <option value="DAY">Daily (UTC Midnight)</option>
                      <option value="MONTH">Monthly (UTC 1st)</option>
                    </select>
                  </div>
                </div>
              )}
            </div>
          </div>

          <div className="flex items-center justify-end gap-2 pt-2 border-t border-border">
            <Button
              variant="outline"
              onClick={() => {
                setIsCreatePlanOpen(false);
                setFormError(null);
              }}
            >
              Cancel
            </Button>
            <Button
              onClick={handleCreatePlan}
              disabled={!planName.trim() || !planSlug.trim() || createPlanMutation.isPending}
            >
              {createPlanMutation.isPending ? "Creating..." : "Create Plan"}
            </Button>
          </div>
        </div>
      </Dialog>

      {/* Add Policy to Plan Dialog */}
      <Dialog
        isOpen={!!activePlanIdForPolicy}
        onClose={() => {
          setActivePlanIdForPolicy(null);
          setFormError(null);
        }}
        title="Add Limit Policy to Plan"
        description="Attach an additional token bucket rate limiter or quota allowance."
      >
        <div className="space-y-4">
          {formError && <Alert variant="destructive">{formError}</Alert>}

          <div className="space-y-3">
            <div className="space-y-1.5">
              <label className="text-xs font-semibold uppercase text-muted-foreground">
                Policy Type
              </label>
              <div className="grid grid-cols-2 gap-2">
                <Button
                  type="button"
                  variant={newPolicyKind === "RATE" ? "primary" : "outline"}
                  onClick={() => setNewPolicyKind("RATE")}
                  className="gap-2 text-xs"
                >
                  <Zap className="h-3.5 w-3.5" /> Rate Limit
                </Button>
                <Button
                  type="button"
                  variant={newPolicyKind === "QUOTA" ? "primary" : "outline"}
                  onClick={() => setNewPolicyKind("QUOTA")}
                  className="gap-2 text-xs"
                >
                  <Calendar className="h-3.5 w-3.5" /> Calendar Quota
                </Button>
              </div>
            </div>

            {newPolicyKind === "RATE" ? (
              <div className="grid grid-cols-3 gap-2">
                <div className="space-y-1">
                  <label className="text-[11px] text-muted-foreground">Capacity</label>
                  <Input
                    type="number"
                    value={newRateCapacity}
                    onChange={(e) => setNewRateCapacity(e.target.value)}
                    className="h-8 text-xs font-mono"
                  />
                </div>
                <div className="space-y-1">
                  <label className="text-[11px] text-muted-foreground">Refill Count</label>
                  <Input
                    type="number"
                    value={newRateRefillTokens}
                    onChange={(e) => setNewRateRefillTokens(e.target.value)}
                    className="h-8 text-xs font-mono"
                  />
                </div>
                <div className="space-y-1">
                  <label className="text-[11px] text-muted-foreground">Period (Sec)</label>
                  <Input
                    type="number"
                    value={newRatePeriodSeconds}
                    onChange={(e) => setNewRatePeriodSeconds(e.target.value)}
                    className="h-8 text-xs font-mono"
                  />
                </div>
              </div>
            ) : (
              <div className="grid grid-cols-2 gap-3">
                <div className="space-y-1">
                  <label className="text-[11px] text-muted-foreground">Quota Allowance</label>
                  <Input
                    type="number"
                    value={newQuotaLimit}
                    onChange={(e) => setNewQuotaLimit(e.target.value)}
                    className="h-8 text-xs font-mono"
                  />
                </div>
                <div className="space-y-1">
                  <label className="text-[11px] text-muted-foreground">Window Period</label>
                  <select
                    className="w-full h-8 rounded-md border border-input bg-background px-2 text-xs shadow-xs"
                    value={newQuotaPeriod}
                    onChange={(e) => setNewQuotaPeriod(e.target.value as QuotaPeriod)}
                  >
                    <option value="DAY">Daily (UTC Midnight)</option>
                    <option value="MONTH">Monthly (UTC 1st)</option>
                  </select>
                </div>
              </div>
            )}
          </div>

          <div className="flex items-center justify-end gap-2 pt-2 border-t border-border">
            <Button
              variant="outline"
              onClick={() => {
                setActivePlanIdForPolicy(null);
                setFormError(null);
              }}
            >
              Cancel
            </Button>
            <Button onClick={handleAddPolicy} disabled={addPolicyMutation.isPending}>
              {addPolicyMutation.isPending ? "Adding..." : "Add Policy"}
            </Button>
          </div>
        </div>
      </Dialog>
    </div>
  );
}
