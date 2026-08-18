"use client";

import React, { useState } from "react";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { useAuth } from "@/lib/auth-context";
import { getProducts } from "@/lib/api/products";
import { fetchPlans } from "@/lib/api/plans";
import { fetchAllWorkspaceApplications, fetchConsumers } from "@/lib/api/consumers";
import { fetchSubscriptions, createSubscription, cancelSubscription } from "@/lib/api/subscriptions";
import { ApiProduct, Plan, Consumer, ConsumerApplication, Subscription } from "@/lib/api/types";
import { ApiError } from "@/lib/api/client";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { Card, CardContent } from "@/components/ui/card";
import { Dialog } from "@/components/ui/dialog";
import { Alert } from "@/components/ui/alert";
import {
  KeyRound,
  Plus,
  Building2,
  Boxes,
  FileSpreadsheet,
  Calendar,
  Zap,
  Ban,
} from "lucide-react";

export default function SubscriptionsPage() {
  const { currentMembership, currentRole } = useAuth();
  const workspaceId = currentMembership?.workspaceId;
  const queryClient = useQueryClient();

  const isViewer = currentRole === "VIEWER";

  // Create Subscription Modal State
  const [isCreateOpen, setIsCreateOpen] = useState(false);
  const [selectedAppId, setSelectedAppId] = useState("");
  const [selectedProductId, setSelectedProductId] = useState("");
  const [selectedPlanId, setSelectedPlanId] = useState("");
  const [formError, setFormError] = useState<string | null>(null);

  // Queries
  const { data: consumers = [] } = useQuery<Consumer[]>({
    queryKey: ["consumers", workspaceId],
    queryFn: () => (workspaceId ? fetchConsumers(workspaceId) : Promise.resolve([])),
    enabled: !!workspaceId,
  });

  const { data: applications = [] } = useQuery<ConsumerApplication[]>({
    queryKey: ["all-applications", workspaceId],
    queryFn: () => (workspaceId ? fetchAllWorkspaceApplications(workspaceId) : Promise.resolve([])),
    enabled: !!workspaceId,
  });

  const { data: products = [] } = useQuery<ApiProduct[]>({
    queryKey: ["products", workspaceId],
    queryFn: () => (workspaceId ? getProducts(workspaceId) : Promise.resolve([])),
    enabled: !!workspaceId,
  });

  const { data: plans = [] } = useQuery<Plan[]>({
    queryKey: ["plans", workspaceId],
    queryFn: () => (workspaceId ? fetchPlans(workspaceId) : Promise.resolve([])),
    enabled: !!workspaceId,
  });

  const { data: subscriptions = [], isLoading } = useQuery<Subscription[]>({
    queryKey: ["subscriptions", workspaceId],
    queryFn: () => (workspaceId ? fetchSubscriptions(workspaceId) : Promise.resolve([])),
    enabled: !!workspaceId,
  });

  // Mutations
  const createSubMutation = useMutation({
    mutationFn: (data: { applicationId: string; productId: string; planId: string }) =>
      createSubscription(workspaceId!, data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["subscriptions", workspaceId] });
      setIsCreateOpen(false);
      setSelectedAppId("");
      setSelectedProductId("");
      setSelectedPlanId("");
      setFormError(null);
    },
    onError: (err: unknown) => {
      if (err instanceof ApiError) {
        setFormError(err.problem?.detail || err.message);
      } else if (err instanceof Error) {
        setFormError(err.message);
      } else {
        setFormError("Failed to create subscription");
      }
    },
  });

  const cancelSubMutation = useMutation({
    mutationFn: (subscriptionId: string) =>
      cancelSubscription(workspaceId!, subscriptionId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["subscriptions", workspaceId] });
    },
    onError: (err: unknown) => {
      if (err instanceof ApiError) {
        setFormError(err.problem?.detail || err.message);
      } else if (err instanceof Error) {
        setFormError(err.message);
      }
    },
  });

  const availablePlansForProduct = (plans || []).filter(
    (p) => !selectedProductId || p.productId === selectedProductId
  );

  const handleOpenCreate = () => {
    if (applications.length > 0) setSelectedAppId(applications[0].id);
    if (products.length > 0) {
      setSelectedProductId(products[0].id);
      const matchingPlans = plans.filter((p) => p.productId === products[0].id);
      if (matchingPlans.length > 0) setSelectedPlanId(matchingPlans[0].id);
    }
    setFormError(null);
    setIsCreateOpen(true);
  };

  const handleProductChange = (prodId: string) => {
    setSelectedProductId(prodId);
    const matchingPlans = plans.filter((p) => p.productId === prodId);
    if (matchingPlans.length > 0) {
      setSelectedPlanId(matchingPlans[0].id);
    } else {
      setSelectedPlanId("");
    }
  };

  return (
    <div className="space-y-6">
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold tracking-tight">Application Subscriptions</h1>
          <p className="text-sm text-muted-foreground">
            Assign API Product Plans to Consumer Applications to control usage limits and rate buckets.
          </p>
        </div>
        {!isViewer && (
          <Button onClick={handleOpenCreate} className="gap-2">
            <Plus className="h-4 w-4" />
            Assign Subscription
          </Button>
        )}
      </div>

      {isLoading ? (
        <Card className="animate-pulse h-48 bg-muted/40" />
      ) : subscriptions.length === 0 ? (
        <Card className="text-center py-12">
          <CardContent className="space-y-4">
            <KeyRound className="mx-auto h-12 w-12 text-muted-foreground/60" />
            <div>
              <h3 className="text-lg font-semibold">No active subscriptions</h3>
              <p className="text-sm text-muted-foreground">
                Subscribe an application to an API product plan to start routing and limiting traffic.
              </p>
            </div>
            {!isViewer && (
              <Button onClick={handleOpenCreate} variant="outline" className="gap-2">
                <Plus className="h-4 w-4" />
                Assign First Subscription
              </Button>
            )}
          </CardContent>
        </Card>
      ) : (
        <Card>
          <div className="overflow-x-auto">
            <table className="w-full text-xs text-left">
              <thead className="bg-muted/40 uppercase text-muted-foreground border-b font-semibold tracking-wider">
                <tr>
                  <th className="py-3 px-4">Consumer Application</th>
                  <th className="py-3 px-4">API Product</th>
                  <th className="py-3 px-4">Plan & Limits</th>
                  <th className="py-3 px-4">Effective Date</th>
                  <th className="py-3 px-4">Status</th>
                  {!isViewer && <th className="py-3 px-4 text-right">Actions</th>}
                </tr>
              </thead>
              <tbody className="divide-y divide-border">
                {subscriptions.map((sub) => {
                  const app = applications.find((a) => a.id === sub.applicationId);
                  const consumer = consumers.find((c) => c.id === app?.consumerId);
                  const product = products.find((p) => p.id === sub.productId);
                  const plan = plans.find((p) => p.id === sub.planId);

                  return (
                    <tr key={sub.id} className="hover:bg-muted/30 transition-colors">
                      <td className="py-3.5 px-4">
                        <div className="space-y-0.5">
                          <div className="font-semibold text-sm flex items-center gap-1.5 text-foreground">
                            {app?.name || sub.applicationId}
                          </div>
                          {consumer && (
                            <div className="text-[11px] text-muted-foreground flex items-center gap-1">
                              <Building2 className="h-3 w-3" />
                              {consumer.name}
                            </div>
                          )}
                        </div>
                      </td>

                      <td className="py-3.5 px-4">
                        <div className="flex items-center gap-1.5 font-medium">
                          <Boxes className="h-3.5 w-3.5 text-primary" />
                          <span>{product?.name || sub.productId}</span>
                        </div>
                      </td>

                      <td className="py-3.5 px-4">
                        <div className="space-y-1">
                          <div className="font-medium flex items-center gap-1">
                            <FileSpreadsheet className="h-3.5 w-3.5 text-muted-foreground" />
                            <span>{plan?.name || sub.planId}</span>
                          </div>
                          <div className="flex flex-wrap gap-1">
                            {plan?.policies?.map((pol) => (
                              <span
                                key={pol.id}
                                className="text-[10px] px-1.5 py-0.5 rounded bg-muted font-mono flex items-center gap-1"
                              >
                                {pol.kind === "RATE" ? (
                                  <Zap className="h-2.5 w-2.5 text-amber-500" />
                                ) : (
                                  <Calendar className="h-2.5 w-2.5 text-sky-500" />
                                )}
                                {pol.kind === "RATE"
                                  ? `${pol.capacity} / ${pol.refillPeriodSeconds}s`
                                  : `${pol.quotaLimit} / ${pol.quotaPeriod?.toLowerCase()}`}
                              </span>
                            ))}
                          </div>
                        </div>
                      </td>

                      <td className="py-3.5 px-4 text-muted-foreground">
                        <div className="space-y-0.5">
                          <div>From: {new Date(sub.effectiveFrom).toLocaleDateString()}</div>
                          {sub.effectiveTo && (
                            <div>To: {new Date(sub.effectiveTo).toLocaleDateString()}</div>
                          )}
                        </div>
                      </td>

                      <td className="py-3.5 px-4">
                        <Badge variant={sub.status === "ACTIVE" ? "default" : "secondary"}>
                          {sub.status}
                        </Badge>
                      </td>

                      {!isViewer && (
                        <td className="py-3.5 px-4 text-right">
                          {sub.status === "ACTIVE" && (
                            <Button
                              size="sm"
                              variant="ghost"
                              className="h-7 text-xs text-destructive hover:bg-destructive/10 gap-1"
                              onClick={() => cancelSubMutation.mutate(sub.id)}
                              disabled={cancelSubMutation.isPending}
                            >
                              <Ban className="h-3 w-3" /> Cancel
                            </Button>
                          )}
                        </td>
                      )}
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        </Card>
      )}

      {/* Assign Subscription Dialog */}
      <Dialog
        isOpen={isCreateOpen}
        onClose={() => {
          setIsCreateOpen(false);
          setFormError(null);
        }}
        title="Assign Plan Subscription"
        description="Grant an application access to an API Product with defined rate limits."
      >
        <div className="space-y-4">
          {formError && <Alert variant="destructive">{formError}</Alert>}

          <div className="space-y-3">
            <div className="space-y-1.5">
              <label className="text-xs font-semibold uppercase text-muted-foreground">
                Target Application
              </label>
              <select
                className="w-full h-9 rounded-md border border-input bg-background px-3 py-1 text-sm shadow-xs"
                value={selectedAppId}
                onChange={(e) => setSelectedAppId(e.target.value)}
              >
                {applications.map((app) => {
                  const consumer = consumers.find((c) => c.id === app.consumerId);
                  return (
                    <option key={app.id} value={app.id}>
                      {app.name} {consumer ? `(${consumer.name})` : ""}
                    </option>
                  );
                })}
              </select>
            </div>

            <div className="space-y-1.5">
              <label className="text-xs font-semibold uppercase text-muted-foreground">
                API Product
              </label>
              <select
                className="w-full h-9 rounded-md border border-input bg-background px-3 py-1 text-sm shadow-xs"
                value={selectedProductId}
                onChange={(e) => handleProductChange(e.target.value)}
              >
                {products.map((prod) => (
                  <option key={prod.id} value={prod.id}>
                    {prod.name} ({prod.gatewayBasePath})
                  </option>
                ))}
              </select>
            </div>

            <div className="space-y-1.5">
              <label className="text-xs font-semibold uppercase text-muted-foreground">
                Plan Tier
              </label>
              <select
                className="w-full h-9 rounded-md border border-input bg-background px-3 py-1 text-sm shadow-xs"
                value={selectedPlanId}
                onChange={(e) => setSelectedPlanId(e.target.value)}
              >
                {availablePlansForProduct.length === 0 ? (
                  <option value="">No plans available for this product</option>
                ) : (
                  availablePlansForProduct.map((p) => (
                    <option key={p.id} value={p.id}>
                      {p.name} ({p.slug})
                    </option>
                  ))
                )}
              </select>
            </div>
          </div>

          <div className="flex items-center justify-end gap-2 pt-2 border-t border-border">
            <Button
              variant="outline"
              onClick={() => {
                setIsCreateOpen(false);
                setFormError(null);
              }}
            >
              Cancel
            </Button>
            <Button
              onClick={() =>
                createSubMutation.mutate({
                  applicationId: selectedAppId,
                  productId: selectedProductId,
                  planId: selectedPlanId,
                })
              }
              disabled={
                !selectedAppId ||
                !selectedProductId ||
                !selectedPlanId ||
                createSubMutation.isPending
              }
            >
              {createSubMutation.isPending ? "Assigning..." : "Assign Subscription"}
            </Button>
          </div>
        </div>
      </Dialog>
    </div>
  );
}
