"use client";

import React, { useState } from "react";
import Link from "next/link";
import { useParams } from "next/navigation";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { useAuth } from "@/lib/auth-context";
import { fetchConsumers, createConsumer } from "@/lib/api/consumers";
import { Consumer } from "@/lib/api/types";
import { ApiError } from "@/lib/api/client";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Badge } from "@/components/ui/badge";
import { Card, CardHeader, CardTitle, CardDescription, CardContent } from "@/components/ui/card";
import { Dialog } from "@/components/ui/dialog";
import { Alert } from "@/components/ui/alert";
import { Plus, Building2, Search, ArrowRight, AppWindow } from "lucide-react";

export default function ConsumersPage() {
  const params = useParams();
  const workspaceSlug = (params?.workspaceSlug as string) || "acme-apis";
  const { currentMembership, currentRole } = useAuth();
  const workspaceId = currentMembership?.workspaceId;
  const queryClient = useQueryClient();

  const isViewer = currentRole === "VIEWER";

  const [search, setSearch] = useState("");
  const [isCreateOpen, setIsCreateOpen] = useState(false);
  const [name, setName] = useState("");
  const [externalReference, setExternalReference] = useState("");
  const [formError, setFormError] = useState<string | null>(null);

  const { data: consumers = [], isLoading } = useQuery<Consumer[]>({
    queryKey: ["consumers", workspaceId],
    queryFn: () => (workspaceId ? fetchConsumers(workspaceId) : Promise.resolve([])),
    enabled: !!workspaceId,
  });

  const createMutation = useMutation({
    mutationFn: (data: { name: string; externalReference?: string }) =>
      createConsumer(workspaceId!, data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["consumers", workspaceId] });
      setIsCreateOpen(false);
      setName("");
      setExternalReference("");
      setFormError(null);
    },
    onError: (err: unknown) => {
      if (err instanceof ApiError) {
        setFormError(err.problem?.detail || err.message);
      } else if (err instanceof Error) {
        setFormError(err.message);
      } else {
        setFormError("Failed to create consumer");
      }
    },
  });

  const filteredConsumers = consumers.filter(
    (c) =>
      c.name.toLowerCase().includes(search.toLowerCase()) ||
      (c.externalReference && c.externalReference.toLowerCase().includes(search.toLowerCase()))
  );

  return (
    <div className="space-y-6">
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold tracking-tight">Consumers & Organizations</h1>
          <p className="text-sm text-muted-foreground">
            Manage external partner organizations, developer accounts, and their client applications.
          </p>
        </div>
        {!isViewer && (
          <Button onClick={() => setIsCreateOpen(true)} className="gap-2">
            <Plus className="h-4 w-4" />
            Register Consumer
          </Button>
        )}
      </div>

      <div className="flex items-center gap-3">
        <div className="relative flex-1 max-w-sm">
          <Search className="absolute left-3 top-2.5 h-4 w-4 text-muted-foreground" />
          <Input
            placeholder="Search consumers..."
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            className="pl-9"
          />
        </div>
      </div>

      {isLoading ? (
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
          {[1, 2, 3].map((i) => (
            <Card key={i} className="animate-pulse">
              <CardHeader className="h-24 bg-muted/40" />
            </Card>
          ))}
        </div>
      ) : filteredConsumers.length === 0 ? (
        <Card className="text-center py-12">
          <CardContent className="space-y-4">
            <Building2 className="mx-auto h-12 w-12 text-muted-foreground/60" />
            <div>
              <h3 className="text-lg font-semibold">No consumers found</h3>
              <p className="text-sm text-muted-foreground">
                {search
                  ? "No consumer matches your filter query."
                  : "Register your first consumer organization to begin provisioning applications."}
              </p>
            </div>
            {!isViewer && !search && (
              <Button onClick={() => setIsCreateOpen(true)} variant="outline" className="gap-2">
                <Plus className="h-4 w-4" />
                Register Consumer
              </Button>
            )}
          </CardContent>
        </Card>
      ) : (
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
          {filteredConsumers.map((consumer) => (
            <Card key={consumer.id} className="hover:border-primary/50 transition-colors flex flex-col justify-between">
              <CardHeader className="pb-3">
                <div className="flex items-start justify-between gap-2">
                  <div className="space-y-1">
                    <CardTitle className="text-base font-semibold flex items-center gap-2">
                      <Building2 className="h-4 w-4 text-primary shrink-0" />
                      <span>{consumer.name}</span>
                    </CardTitle>
                    {consumer.externalReference && (
                      <CardDescription className="font-mono text-xs">
                        Ref: {consumer.externalReference}
                      </CardDescription>
                    )}
                  </div>
                  <Badge variant={consumer.status === "ACTIVE" ? "default" : "secondary"}>
                    {consumer.status}
                  </Badge>
                </div>
              </CardHeader>
              <CardContent className="pt-0 space-y-4">
                <div className="flex items-center justify-between text-xs text-muted-foreground border-t pt-3">
                  <span className="flex items-center gap-1.5 font-medium">
                    <AppWindow className="h-3.5 w-3.5 text-muted-foreground" />
                    {consumer.applicationCount} {consumer.applicationCount === 1 ? "App" : "Apps"}
                  </span>
                  <span>Registered: {new Date(consumer.createdAt).toLocaleDateString()}</span>
                </div>
                <Link
                  href={`/${workspaceSlug}/consumers/${consumer.id}`}
                  className="w-full inline-flex items-center justify-center gap-2 rounded-md bg-secondary text-secondary-foreground hover:bg-secondary/80 px-3 py-2 text-xs font-medium transition-colors"
                >
                  Manage Apps & Keys
                  <ArrowRight className="h-3.5 w-3.5" />
                </Link>
              </CardContent>
            </Card>
          ))}
        </div>
      )}

      {/* Create Consumer Dialog */}
      <Dialog
        isOpen={isCreateOpen}
        onClose={() => {
          setIsCreateOpen(false);
          setFormError(null);
        }}
        title="Register Consumer Organization"
        description="Add a partner, company, or customer account to issue API credentials for."
      >
        <div className="space-y-4">
          {formError && <Alert variant="destructive">{formError}</Alert>}

          <div className="space-y-3">
            <div className="space-y-1.5">
              <label className="text-xs font-semibold uppercase text-muted-foreground">
                Organization Name
              </label>
              <Input
                placeholder="e.g. Northstar Labs"
                value={name}
                onChange={(e) => setName(e.target.value)}
              />
            </div>
            <div className="space-y-1.5">
              <label className="text-xs font-semibold uppercase text-muted-foreground">
                External Reference (Optional)
              </label>
              <Input
                placeholder="e.g. EXT-NORTHSTAR-01"
                value={externalReference}
                onChange={(e) => setExternalReference(e.target.value)}
              />
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
                createMutation.mutate({
                  name,
                  externalReference: externalReference || undefined,
                })
              }
              disabled={!name.trim() || createMutation.isPending}
            >
              {createMutation.isPending ? "Creating..." : "Create Consumer"}
            </Button>
          </div>
        </div>
      </Dialog>
    </div>
  );
}
