"use client";

import React, { useState } from "react";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { useParams, useRouter } from "next/navigation";
import { useAuth } from "@/lib/auth-context";
import {
  getProduct,
  getRoutes,
  createRoute,
  activateRoute,
  disableRoute,
  activateProduct,
  disableProduct,
  CreateRoutePayload,
} from "@/lib/api/products";
import { ApiProduct, ApiRoute } from "@/lib/api/types";
import { ApiError } from "@/lib/api/client";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Select } from "@/components/ui/select";
import { Badge } from "@/components/ui/badge";
import { Card, CardHeader, CardTitle, CardDescription, CardContent } from "@/components/ui/card";
import { Dialog } from "@/components/ui/dialog";
import { Alert } from "@/components/ui/alert";
import {
  ArrowLeft,
  Plus,
  Power,
  Route as RouteIcon,
} from "lucide-react";

export default function ProductDetailPage() {
  const params = useParams();
  const router = useRouter();
  const queryClient = useQueryClient();
  const { currentMembership, currentRole } = useAuth();
  const workspaceId = currentMembership?.workspaceId;
  const workspaceSlug = (params?.workspaceSlug as string) || "acme-apis";
  const productId = params?.productId as string;

  const isViewer = currentRole === "VIEWER";

  const [activeTab, setActiveTab] = useState<"overview" | "routes">("routes");
  const [isCreateRouteOpen, setIsCreateRouteOpen] = useState(false);

  // Route form state
  const [method, setMethod] = useState("GET");
  const [pathPattern, setPathPattern] = useState("");
  const [upstreamPath, setUpstreamPath] = useState("");
  const [costUnits, setCostUnits] = useState(1);
  const [priority, setPriority] = useState(10);
  const [routeError, setRouteError] = useState<string | null>(null);

  const {
    data: product,
    isLoading: isProductLoading,
  } = useQuery<ApiProduct>({
    queryKey: ["product", workspaceId, productId],
    queryFn: () => getProduct(workspaceId!, productId),
    enabled: !!workspaceId && !!productId,
  });

  const {
    data: routes = [],
    isLoading: isRoutesLoading,
  } = useQuery<ApiRoute[]>({
    queryKey: ["routes", workspaceId, productId],
    queryFn: () => getRoutes(workspaceId!, productId),
    enabled: !!workspaceId && !!productId,
  });

  const toggleProductStatus = useMutation({
    mutationFn: () => {
      if (product?.status === "ACTIVE") {
        return disableProduct(workspaceId!, productId);
      } else {
        return activateProduct(workspaceId!, productId);
      }
    },
    onSuccess: () => {
      queryClient.invalidateQueries({
        queryKey: ["product", workspaceId, productId],
      });
      queryClient.invalidateQueries({ queryKey: ["products", workspaceId] });
    },
  });

  const createRouteMutation = useMutation({
    mutationFn: (payload: CreateRoutePayload) =>
      createRoute(workspaceId!, productId, payload),
    onSuccess: () => {
      queryClient.invalidateQueries({
        queryKey: ["routes", workspaceId, productId],
      });
      setIsCreateRouteOpen(false);
      resetRouteForm();
    },
    onError: (err: unknown) => {
      if (err instanceof ApiError) {
        setRouteError(err.problem?.detail || err.message);
      } else if (err instanceof Error) {
        setRouteError(err.message);
      } else {
        setRouteError("Failed to create route");
      }
    },
  });

  const toggleRouteStatus = useMutation({
    mutationFn: ({
      routeId,
      currentStatus,
    }: {
      routeId: string;
      currentStatus: string;
    }) => {
      if (currentStatus === "ACTIVE") {
        return disableRoute(workspaceId!, productId, routeId);
      } else {
        return activateRoute(workspaceId!, productId, routeId);
      }
    },
    onSuccess: () => {
      queryClient.invalidateQueries({
        queryKey: ["routes", workspaceId, productId],
      });
    },
  });

  const resetRouteForm = () => {
    setMethod("GET");
    setPathPattern(product ? `${product.gatewayBasePath}/` : "/v1/");
    setUpstreamPath("");
    setCostUnits(1);
    setPriority(10);
    setRouteError(null);
  };

  const handleOpenCreateRoute = () => {
    resetRouteForm();
    setIsCreateRouteOpen(true);
  };

  const handleCreateRouteSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    setRouteError(null);
    createRouteMutation.mutate({
      httpMethod: method,
      pathPattern,
      upstreamPath: upstreamPath ? upstreamPath : null,
      costUnits,
      priority,
    });
  };

  const getMethodBadgeVariant = (m: string) => {
    switch (m.toUpperCase()) {
      case "GET":
        return "method-get";
      case "POST":
        return "method-post";
      case "PUT":
        return "method-put";
      case "DELETE":
        return "method-delete";
      case "PATCH":
        return "method-patch";
      default:
        return "default";
    }
  };

  if (isProductLoading) {
    return (
      <div className="flex items-center justify-center p-12">
        <div className="h-6 w-6 animate-spin rounded-full border-2 border-primary border-t-transparent" />
      </div>
    );
  }

  if (!product) {
    return (
      <Alert variant="destructive" title="Product Not Found">
        The requested API product could not be found in this workspace.
      </Alert>
    );
  }

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex items-center gap-2 text-sm text-muted-foreground">
        <button
          onClick={() => router.push(`/${workspaceSlug}/products`)}
          className="flex items-center gap-1 hover:text-foreground transition-colors cursor-pointer"
        >
          <ArrowLeft className="h-4 w-4" />
          <span>API Products</span>
        </button>
        <span>/</span>
        <span className="text-foreground font-medium">{product.name}</span>
      </div>

      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 border-b border-border/80 pb-6">
        <div>
          <div className="flex items-center gap-3">
            <h1 className="text-2xl font-bold tracking-tight text-foreground">
              {product.name}
            </h1>
            <Badge
              variant={product.status === "ACTIVE" ? "success" : "warning"}
            >
              {product.status}
            </Badge>
          </div>
          <p className="text-xs font-mono text-muted-foreground mt-1">
            ID: {product.id} &middot; Base: {product.gatewayBasePath}
          </p>
        </div>

        <div className="flex items-center gap-2">
          {!isViewer && (
            <Button
              variant="outline"
              size="sm"
              onClick={() => toggleProductStatus.mutate()}
              isLoading={toggleProductStatus.isPending}
            >
              <Power className="h-4 w-4 mr-1.5" />
              {product.status === "ACTIVE" ? "Disable Product" : "Activate Product"}
            </Button>
          )}

          <Button size="sm" onClick={handleOpenCreateRoute} disabled={isViewer}>
            <Plus className="h-4 w-4 mr-1.5" />
            Add Route
          </Button>
        </div>
      </div>

      {isViewer && (
        <Alert variant="info" title="Read-Only Access">
          You are signed in with the <strong>VIEWER</strong> role. Modifying routes and product status is disabled.
        </Alert>
      )}

      {/* Tabs */}
      <div className="flex items-center gap-4 border-b border-border">
        <button
          onClick={() => setActiveTab("routes")}
          className={`pb-2.5 text-sm font-medium transition-colors border-b-2 -mb-px cursor-pointer ${
            activeTab === "routes"
              ? "border-primary text-primary font-semibold"
              : "border-transparent text-muted-foreground hover:text-foreground"
          }`}
        >
          Routes ({routes.length})
        </button>
        <button
          onClick={() => setActiveTab("overview")}
          className={`pb-2.5 text-sm font-medium transition-colors border-b-2 -mb-px cursor-pointer ${
            activeTab === "overview"
              ? "border-primary text-primary font-semibold"
              : "border-transparent text-muted-foreground hover:text-foreground"
          }`}
        >
          Overview & Upstream
        </button>
      </div>

      {/* Routes Tab */}
      {activeTab === "routes" && (
        <div className="space-y-4">
          {isRoutesLoading ? (
            <div className="p-8 text-center text-muted-foreground">
              Loading routes...
            </div>
          ) : routes.length === 0 ? (
            <Card className="p-8 text-center">
              <RouteIcon className="h-8 w-8 text-muted-foreground mx-auto mb-2" />
              <h4 className="text-sm font-semibold">No routes registered</h4>
              <p className="text-xs text-muted-foreground mt-1 mb-4">
                Define the endpoints and methods this product exposes.
              </p>
              {!isViewer && (
                <Button size="sm" variant="outline" onClick={handleOpenCreateRoute}>
                  <Plus className="h-4 w-4 mr-1" />
                  Add First Route
                </Button>
              )}
            </Card>
          ) : (
            <div className="rounded-lg border border-border overflow-hidden bg-card">
              <table className="w-full text-left text-sm">
                <thead className="bg-muted/50 text-xs font-semibold uppercase tracking-wider text-muted-foreground border-b border-border">
                  <tr>
                    <th className="py-3 px-4">Method</th>
                    <th className="py-3 px-4">Path Pattern</th>
                    <th className="py-3 px-4">Upstream Target</th>
                    <th className="py-3 px-4 text-center">Cost</th>
                    <th className="py-3 px-4 text-center">Priority</th>
                    <th className="py-3 px-4 text-center">Status</th>
                    {!isViewer && <th className="py-3 px-4 text-right">Actions</th>}
                  </tr>
                </thead>
                <tbody className="divide-y divide-border/60 font-mono text-xs">
                  {routes.map((route) => (
                    <tr
                      key={route.id}
                      className="hover:bg-muted/30 transition-colors"
                    >
                      <td className="py-3 px-4">
                        <Badge variant={getMethodBadgeVariant(route.httpMethod)}>
                          {route.httpMethod}
                        </Badge>
                      </td>
                      <td className="py-3 px-4 font-semibold text-foreground">
                        {route.pathPattern}
                      </td>
                      <td className="py-3 px-4 text-muted-foreground">
                        {route.upstreamPath ? route.upstreamPath : "(matches path)"}
                      </td>
                      <td className="py-3 px-4 text-center font-bold">
                        {route.costUnits}
                      </td>
                      <td className="py-3 px-4 text-center text-muted-foreground">
                        {route.priority}
                      </td>
                      <td className="py-3 px-4 text-center">
                        <Badge
                          variant={route.status === "ACTIVE" ? "success" : "warning"}
                        >
                          {route.status}
                        </Badge>
                      </td>
                      {!isViewer && (
                        <td className="py-3 px-4 text-right">
                          <Button
                            variant="ghost"
                            size="sm"
                            className="h-7 text-xs"
                            onClick={() =>
                              toggleRouteStatus.mutate({
                                routeId: route.id,
                                currentStatus: route.status,
                              })
                            }
                            isLoading={toggleRouteStatus.isPending}
                          >
                            <Power className="h-3.5 w-3.5 mr-1" />
                            {route.status === "ACTIVE" ? "Disable" : "Activate"}
                          </Button>
                        </td>
                      )}
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>
      )}

      {/* Overview Tab */}
      {activeTab === "overview" && (
        <div className="grid gap-6 md:grid-cols-2">
          <Card>
            <CardHeader>
              <CardTitle className="text-base font-semibold">
                Product Details
              </CardTitle>
              <CardDescription>
                Core configuration and routing identifiers
              </CardDescription>
            </CardHeader>
            <CardContent className="space-y-3 text-sm">
              <div className="flex justify-between py-1.5 border-b border-border/40">
                <span className="text-muted-foreground">Slug</span>
                <span className="font-mono font-medium">{product.slug}</span>
              </div>
              <div className="flex justify-between py-1.5 border-b border-border/40">
                <span className="text-muted-foreground">Gateway Prefix</span>
                <span className="font-mono font-semibold text-primary">
                  {product.gatewayBasePath}
                </span>
              </div>
              <div className="flex justify-between py-1.5 border-b border-border/40">
                <span className="text-muted-foreground">Upstream Service</span>
                <span className="font-mono font-medium">
                  {product.upstreamBaseUrl}
                </span>
              </div>
              <div className="flex justify-between py-1.5 border-b border-border/40">
                <span className="text-muted-foreground">Version</span>
                <span className="font-mono">{product.version}</span>
              </div>
              <div className="flex justify-between py-1.5">
                <span className="text-muted-foreground">Created</span>
                <span className="text-xs text-muted-foreground font-mono">
                  {new Date(product.createdAt).toLocaleString()}
                </span>
              </div>
            </CardContent>
          </Card>

          <Card>
            <CardHeader>
              <CardTitle className="text-base font-semibold">
                Routing Architecture Rules
              </CardTitle>
              <CardDescription>
                Enforced by Spring Cloud Gateway WebFlux
              </CardDescription>
            </CardHeader>
            <CardContent className="space-y-2 text-xs text-muted-foreground">
              <p>
                &bull; <strong>Static &gt; Variable &gt; Wildcard</strong>: Exact static segments take precedence over <code>&#123;var&#125;</code> parameters and terminal <code>**</code> wildcards.
              </p>
              <p>
                &bull; <strong>Ambiguity Prevention</strong>: Structurally equivalent path templates (e.g. <code>/v1/forecast/&#123;city&#125;</code> and <code>/v1/forecast/&#123;code&#125;</code>) are rejected immediately at configuration time.
              </p>
              <p>
                &bull; <strong>Atomic Invalidation</strong>: Config changes emit versioned outbox events that project to Redis atomically.
              </p>
            </CardContent>
          </Card>
        </div>
      )}

      {/* Create Route Dialog */}
      <Dialog
        isOpen={isCreateRouteOpen}
        onClose={() => setIsCreateRouteOpen(false)}
        title="Add Route"
        description={`Define a method and path pattern under ${product.name}`}
      >
        <form onSubmit={handleCreateRouteSubmit} className="space-y-4">
          {routeError && (
            <Alert variant="destructive" title="Cannot Register Route">
              {routeError}
            </Alert>
          )}

          <div className="grid grid-cols-3 gap-3">
            <div className="space-y-1.5 col-span-1">
              <label className="text-xs font-semibold uppercase tracking-wider text-muted-foreground">
                HTTP Method
              </label>
              <Select
                value={method}
                onChange={(e) => setMethod(e.target.value)}
              >
                <option value="GET">GET</option>
                <option value="POST">POST</option>
                <option value="PUT">PUT</option>
                <option value="DELETE">DELETE</option>
                <option value="PATCH">PATCH</option>
              </Select>
            </div>

            <div className="space-y-1.5 col-span-2">
              <label className="text-xs font-semibold uppercase tracking-wider text-muted-foreground">
                Path Pattern
              </label>
              <Input
                value={pathPattern}
                onChange={(e) => setPathPattern(e.target.value)}
                placeholder="/v1/forecast/{city}"
                required
              />
            </div>
          </div>

          <div className="space-y-1.5">
            <label className="text-xs font-semibold uppercase tracking-wider text-muted-foreground">
              Upstream Path Override (Optional)
            </label>
            <Input
              value={upstreamPath}
              onChange={(e) => setUpstreamPath(e.target.value)}
              placeholder="e.g. /internal/v1/forecast/{city} (Leave blank to match path)"
            />
          </div>

          <div className="grid grid-cols-2 gap-3">
            <div className="space-y-1.5">
              <label className="text-xs font-semibold uppercase tracking-wider text-muted-foreground">
                Cost Units (Default 1)
              </label>
              <Input
                type="number"
                min={1}
                max={1000}
                value={costUnits}
                onChange={(e) => setCostUnits(parseInt(e.target.value) || 1)}
                required
              />
            </div>

            <div className="space-y-1.5">
              <label className="text-xs font-semibold uppercase tracking-wider text-muted-foreground">
                Priority (Default 10)
              </label>
              <Input
                type="number"
                min={1}
                max={100}
                value={priority}
                onChange={(e) => setPriority(parseInt(e.target.value) || 10)}
                required
              />
            </div>
          </div>

          <div className="flex justify-end gap-2 pt-3 border-t border-border/60">
            <Button
              type="button"
              variant="outline"
              onClick={() => setIsCreateRouteOpen(false)}
            >
              Cancel
            </Button>
            <Button type="submit" isLoading={createRouteMutation.isPending}>
              Add Route
            </Button>
          </div>
        </form>
      </Dialog>
    </div>
  );
}
