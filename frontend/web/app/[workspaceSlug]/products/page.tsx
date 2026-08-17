"use client";

import React, { useState } from "react";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { useParams, useRouter } from "next/navigation";
import { useAuth } from "@/lib/auth-context";
import {
  getProducts,
  createProduct,
  activateProduct,
  disableProduct,
  CreateProductPayload,
} from "@/lib/api/products";
import { ApiProduct } from "@/lib/api/types";
import { ApiError } from "@/lib/api/client";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Badge } from "@/components/ui/badge";
import { Card, CardHeader, CardTitle, CardContent } from "@/components/ui/card";
import { Dialog } from "@/components/ui/dialog";
import { Alert } from "@/components/ui/alert";
import { Plus, Power, ArrowRight, Layers } from "lucide-react";

export default function ProductsPage() {
  const params = useParams();
  const router = useRouter();
  const queryClient = useQueryClient();
  const { currentMembership, currentRole } = useAuth();
  const workspaceId = currentMembership?.workspaceId;
  const workspaceSlug = (params?.workspaceSlug as string) || "acme-apis";

  const isViewer = currentRole === "VIEWER";

  const [isCreateOpen, setIsCreateOpen] = useState(false);
  const [formName, setFormName] = useState("");
  const [formSlug, setFormSlug] = useState("");
  const [formUpstream, setFormUpstream] = useState("http://wiremock:8080");
  const [formBasePath, setFormBasePath] = useState("/v1/");
  const [formError, setFormError] = useState<string | null>(null);

  const {
    data: products = [],
    isLoading,
  } = useQuery<ApiProduct[]>({
    queryKey: ["products", workspaceId],
    queryFn: () => (workspaceId ? getProducts(workspaceId) : Promise.resolve([])),
    enabled: !!workspaceId,
  });

  const createMutation = useMutation({
    mutationFn: (payload: CreateProductPayload) =>
      createProduct(workspaceId!, payload),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["products", workspaceId] });
      setIsCreateOpen(false);
      resetForm();
    },
    onError: (err: unknown) => {
      if (err instanceof ApiError) {
        setFormError(err.problem?.detail || err.message);
      } else if (err instanceof Error) {
        setFormError(err.message);
      } else {
        setFormError("Failed to create API product");
      }
    },
  });

  const toggleStatusMutation = useMutation({
    mutationFn: ({ productId, currentStatus }: { productId: string; currentStatus: string }) => {
      if (currentStatus === "ACTIVE") {
        return disableProduct(workspaceId!, productId);
      } else {
        return activateProduct(workspaceId!, productId);
      }
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["products", workspaceId] });
    },
  });

  const resetForm = () => {
    setFormName("");
    setFormSlug("");
    setFormUpstream("http://wiremock:8080");
    setFormBasePath("/v1/");
    setFormError(null);
  };

  const handleNameChange = (val: string) => {
    setFormName(val);
    if (!formSlug || formSlug === formName.toLowerCase().replace(/[^a-z0-9]/g, "-")) {
      const generatedSlug = val
        .toLowerCase()
        .replace(/[^a-z0-9]+/g, "-")
        .replace(/(^-|-$)/g, "");
      setFormSlug(generatedSlug);
      if (formBasePath === "/v1/" || formBasePath.startsWith("/v1/")) {
        setFormBasePath(`/v1/${generatedSlug}`);
      }
    }
  };

  const handleCreateSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    setFormError(null);
    createMutation.mutate({
      name: formName,
      slug: formSlug,
      upstreamBaseUrl: formUpstream,
      gatewayBasePath: formBasePath,
    });
  };

  return (
    <div className="space-y-6">
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold tracking-tight text-foreground">
            API Products
          </h1>
          <p className="text-sm text-muted-foreground">
            Register and configure upstream APIs and route prefixes protected by the gateway
          </p>
        </div>

        <Button
          onClick={() => {
            resetForm();
            setIsCreateOpen(true);
          }}
          disabled={isViewer}
          className="gap-2 shrink-0"
        >
          <Plus className="h-4 w-4" />
          Create Product
        </Button>
      </div>

      {isViewer && (
        <Alert variant="info" title="Read-Only Access">
          You are currently signed in with the <strong>VIEWER</strong> role. You can inspect API products and routes, but cannot create or modify configuration.
        </Alert>
      )}

      {isLoading ? (
        <Card className="p-12 text-center text-muted-foreground">
          <div className="inline-block h-6 w-6 animate-spin rounded-full border-2 border-primary border-t-transparent mb-2" />
          <p>Loading API products...</p>
        </Card>
      ) : products.length === 0 ? (
        <Card className="p-12 text-center">
          <Layers className="h-10 w-10 text-muted-foreground mx-auto mb-3" />
          <h3 className="text-base font-semibold text-foreground">No API Products</h3>
          <p className="text-sm text-muted-foreground mt-1 mb-4">
            Get started by registering your first API product to protect with rate limits.
          </p>
          {!isViewer && (
            <Button
              onClick={() => {
                resetForm();
                setIsCreateOpen(true);
              }}
              variant="outline"
              size="sm"
            >
              <Plus className="h-4 w-4 mr-1.5" />
              Register API Product
            </Button>
          )}
        </Card>
      ) : (
        <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-3">
          {products.map((product) => (
            <Card
              key={product.id}
              className="flex flex-col justify-between hover:border-border/90 transition-all shadow-xs"
            >
              <CardHeader className="space-y-2 pb-3">
                <div className="flex items-start justify-between gap-2">
                  <div>
                    <CardTitle className="text-lg font-bold">
                      {product.name}
                    </CardTitle>
                    <span className="text-xs font-mono text-muted-foreground">
                      {product.slug}
                    </span>
                  </div>
                  <Badge
                    variant={product.status === "ACTIVE" ? "success" : "warning"}
                  >
                    {product.status}
                  </Badge>
                </div>
              </CardHeader>

              <CardContent className="space-y-2.5 text-xs">
                <div className="rounded-md bg-muted/60 p-2.5 space-y-1.5 font-mono">
                  <div className="flex items-center justify-between text-muted-foreground">
                    <span>Gateway Base:</span>
                    <span className="font-semibold text-foreground">
                      {product.gatewayBasePath}
                    </span>
                  </div>
                  <div className="flex items-center justify-between text-muted-foreground">
                    <span>Upstream:</span>
                    <span className="font-semibold text-foreground truncate max-w-[180px]" title={product.upstreamBaseUrl}>
                      {product.upstreamBaseUrl}
                    </span>
                  </div>
                </div>
              </CardContent>

              <div className="flex items-center justify-between p-6 pt-0 border-t border-border/40 mt-3 pt-3">
                {!isViewer ? (
                  <Button
                    variant="ghost"
                    size="sm"
                    className="h-8 text-xs text-muted-foreground hover:text-foreground"
                    onClick={() =>
                      toggleStatusMutation.mutate({
                        productId: product.id,
                        currentStatus: product.status,
                      })
                    }
                    isLoading={toggleStatusMutation.isPending}
                  >
                    <Power className="h-3.5 w-3.5 mr-1" />
                    {product.status === "ACTIVE" ? "Disable" : "Activate"}
                  </Button>
                ) : (
                  <div />
                )}

                <Button
                  size="sm"
                  variant="outline"
                  className="h-8 text-xs gap-1.5"
                  onClick={() =>
                    router.push(`/${workspaceSlug}/products/${product.id}`)
                  }
                >
                  <span>Manage Routes</span>
                  <ArrowRight className="h-3.5 w-3.5" />
                </Button>
              </div>
            </Card>
          ))}
        </div>
      )}

      {/* Create Product Dialog */}
      <Dialog
        isOpen={isCreateOpen}
        onClose={() => setIsCreateOpen(false)}
        title="Create API Product"
        description="Register a new logical API product to be exposed through the gateway"
      >
        <form onSubmit={handleCreateSubmit} className="space-y-4">
          {formError && (
            <Alert variant="destructive" title="Cannot Create Product">
              {formError}
            </Alert>
          )}

          <div className="space-y-1.5">
            <label className="text-xs font-semibold uppercase tracking-wider text-muted-foreground">
              Product Name
            </label>
            <Input
              value={formName}
              onChange={(e) => handleNameChange(e.target.value)}
              placeholder="e.g. Geocoding API"
              required
            />
          </div>

          <div className="space-y-1.5">
            <label className="text-xs font-semibold uppercase tracking-wider text-muted-foreground">
              Slug Identifier
            </label>
            <Input
              value={formSlug}
              onChange={(e) => setFormSlug(e.target.value)}
              placeholder="e.g. geocoding-api"
              required
            />
          </div>

          <div className="space-y-1.5">
            <label className="text-xs font-semibold uppercase tracking-wider text-muted-foreground">
              Gateway Base Path
            </label>
            <Input
              value={formBasePath}
              onChange={(e) => setFormBasePath(e.target.value)}
              placeholder="e.g. /v1/geocoding"
              required
            />
            <p className="text-[11px] text-muted-foreground">
              Must start with <code>/</code> and be unique within this workspace.
            </p>
          </div>

          <div className="space-y-1.5">
            <label className="text-xs font-semibold uppercase tracking-wider text-muted-foreground">
              Upstream Base URL
            </label>
            <Input
              value={formUpstream}
              onChange={(e) => setFormUpstream(e.target.value)}
              placeholder="e.g. http://wiremock:8080"
              required
            />
            <p className="text-[11px] text-muted-foreground">
              Target upstream service host URL (e.g. <code>http://wiremock:8080</code>)
            </p>
          </div>

          <div className="flex justify-end gap-2 pt-3 border-t border-border/60">
            <Button
              type="button"
              variant="outline"
              onClick={() => setIsCreateOpen(false)}
            >
              Cancel
            </Button>
            <Button type="submit" isLoading={createMutation.isPending}>
              Create Product
            </Button>
          </div>
        </form>
      </Dialog>
    </div>
  );
}
