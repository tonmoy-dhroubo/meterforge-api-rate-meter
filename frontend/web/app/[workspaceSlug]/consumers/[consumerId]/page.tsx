"use client";

import React, { useState } from "react";
import Link from "next/link";
import { useParams } from "next/navigation";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { useAuth } from "@/lib/auth-context";
import {
  fetchConsumer,
  fetchApplicationsByConsumer,
  createApplication,
  activateConsumer,
  disableConsumer,
} from "@/lib/api/consumers";
import {
  fetchCredentials,
  issueCredential,
  revokeCredential,
  rotateCredential,
} from "@/lib/api/credentials";
import { Consumer, ConsumerApplication, ApiCredential } from "@/lib/api/types";
import { ApiError } from "@/lib/api/client";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Badge } from "@/components/ui/badge";
import { Card, CardHeader, CardTitle, CardDescription, CardContent } from "@/components/ui/card";
import { Dialog } from "@/components/ui/dialog";
import { Alert } from "@/components/ui/alert";
import {
  Building2,
  AppWindow,
  KeyRound,
  Plus,
  ArrowLeft,
  Copy,
  Check,
  RotateCw,
  Trash2,
  AlertTriangle,
  Clock,
} from "lucide-react";

export default function ConsumerDetailPage() {
  const params = useParams();
  const workspaceSlug = (params?.workspaceSlug as string) || "acme-apis";
  const consumerId = (params?.consumerId as string) || "";
  const { currentMembership, currentRole } = useAuth();
  const workspaceId = currentMembership?.workspaceId;
  const queryClient = useQueryClient();

  const isViewer = currentRole === "VIEWER";

  // App Creation State
  const [isCreateAppOpen, setIsCreateAppOpen] = useState(false);
  const [appName, setAppName] = useState("");

  // Key Issuance State
  const [selectedAppId, setSelectedAppId] = useState<string | null>(null);
  const [isIssueKeyOpen, setIsIssueKeyOpen] = useState(false);
  const [keyEnv, setKeyEnv] = useState("dev");

  // One-time Key Reveal Modal State
  const [revealedKey, setRevealedKey] = useState<string | null>(null);
  const [copied, setCopied] = useState(false);

  const [formError, setFormError] = useState<string | null>(null);

  // Queries
  const { data: consumer, isLoading: isConsumerLoading } = useQuery<Consumer>({
    queryKey: ["consumer", workspaceId, consumerId],
    queryFn: () => (workspaceId ? fetchConsumer(workspaceId, consumerId) : Promise.reject("No workspace")),
    enabled: !!workspaceId && !!consumerId,
  });

  const { data: applications = [], isLoading: isAppsLoading } = useQuery<ConsumerApplication[]>({
    queryKey: ["applications", workspaceId, consumerId],
    queryFn: () => (workspaceId ? fetchApplicationsByConsumer(workspaceId, consumerId) : Promise.resolve([])),
    enabled: !!workspaceId && !!consumerId,
  });

  // App Creation Mutation
  const createAppMutation = useMutation({
    mutationFn: (name: string) =>
      createApplication(workspaceId!, consumerId, { name }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["applications", workspaceId, consumerId] });
      setIsCreateAppOpen(false);
      setAppName("");
      setFormError(null);
    },
    onError: (err: unknown) => {
      if (err instanceof ApiError) {
        setFormError(err.problem?.detail || err.message);
      } else if (err instanceof Error) {
        setFormError(err.message);
      } else {
        setFormError("Failed to create application");
      }
    },
  });

  // Key Issuance Mutation
  const issueKeyMutation = useMutation({
    mutationFn: ({ appId, env }: { appId: string; env: string }) =>
      issueCredential(workspaceId!, appId, { environment: env }),
    onSuccess: (data) => {
      queryClient.invalidateQueries({ queryKey: ["credentials", workspaceId] });
      setIsIssueKeyOpen(false);
      setRevealedKey(data.rawKey);
      setCopied(false);
      setFormError(null);
    },
    onError: (err: unknown) => {
      if (err instanceof ApiError) {
        setFormError(err.problem?.detail || err.message);
      } else if (err instanceof Error) {
        setFormError(err.message);
      } else {
        setFormError("Failed to issue API key");
      }
    },
  });

  // Key Rotation Mutation
  const rotateKeyMutation = useMutation({
    mutationFn: (credentialId: string) =>
      rotateCredential(workspaceId!, credentialId),
    onSuccess: (data) => {
      queryClient.invalidateQueries({ queryKey: ["credentials", workspaceId] });
      setRevealedKey(data.rawKey);
      setCopied(false);
    },
    onError: (err: unknown) => {
      if (err instanceof ApiError) {
        setFormError(err.problem?.detail || err.message);
      } else if (err instanceof Error) {
        setFormError(err.message);
      }
    },
  });

  // Key Revocation Mutation
  const revokeKeyMutation = useMutation({
    mutationFn: (credentialId: string) =>
      revokeCredential(workspaceId!, credentialId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["credentials", workspaceId] });
    },
    onError: (err: unknown) => {
      if (err instanceof ApiError) {
        setFormError(err.problem?.detail || err.message);
      } else if (err instanceof Error) {
        setFormError(err.message);
      }
    },
  });

  // Consumer Status Toggle Mutation
  const toggleStatusMutation = useMutation({
    mutationFn: (newStatus: "ACTIVE" | "DISABLED") =>
      newStatus === "ACTIVE"
        ? activateConsumer(workspaceId!, consumerId)
        : disableConsumer(workspaceId!, consumerId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["consumer", workspaceId, consumerId] });
      queryClient.invalidateQueries({ queryKey: ["consumers", workspaceId] });
    },
  });

  const copyToClipboard = (text: string) => {
    navigator.clipboard.writeText(text);
    setCopied(true);
    setTimeout(() => setCopied(false), 2500);
  };

  if (isConsumerLoading) {
    return <div className="p-8 text-center animate-pulse text-muted-foreground">Loading consumer profile...</div>;
  }

  if (!consumer) {
    return (
      <div className="p-8 text-center space-y-4">
        <p className="text-destructive font-semibold">Consumer organization not found.</p>
        <Link href={`/${workspaceSlug}/consumers`}>
          <Button variant="outline" className="gap-2">
            <ArrowLeft className="h-4 w-4" /> Back to Consumers
          </Button>
        </Link>
      </div>
    );
  }

  return (
    <div className="space-y-6">
      {/* Header breadcrumb */}
      <div className="flex items-center gap-2 text-xs text-muted-foreground">
        <Link href={`/${workspaceSlug}/consumers`} className="hover:underline flex items-center gap-1">
          <ArrowLeft className="h-3.5 w-3.5" /> Consumers
        </Link>
        <span>/</span>
        <span className="text-foreground font-medium">{consumer.name}</span>
      </div>

      {/* Consumer Profile Card */}
      <Card>
        <CardHeader className="pb-4">
          <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
            <div className="space-y-1">
              <div className="flex items-center gap-2.5">
                <Building2 className="h-6 w-6 text-primary" />
                <CardTitle className="text-2xl font-bold">{consumer.name}</CardTitle>
                <Badge variant={consumer.status === "ACTIVE" ? "default" : "secondary"}>
                  {consumer.status}
                </Badge>
              </div>
              <CardDescription className="flex items-center gap-3 text-xs">
                {consumer.externalReference && <span>Reference: {consumer.externalReference}</span>}
                <span>Registered: {new Date(consumer.createdAt).toLocaleString()}</span>
              </CardDescription>
            </div>
            {!isViewer && (
              <div className="flex items-center gap-2">
                <Button
                  variant="outline"
                  size="sm"
                  onClick={() => toggleStatusMutation.mutate(consumer.status === "ACTIVE" ? "DISABLED" : "ACTIVE")}
                  disabled={toggleStatusMutation.isPending}
                >
                  {consumer.status === "ACTIVE" ? "Disable Consumer" : "Activate Consumer"}
                </Button>
                <Button size="sm" onClick={() => setIsCreateAppOpen(true)} className="gap-1.5">
                  <Plus className="h-4 w-4" /> New Application
                </Button>
              </div>
            )}
          </div>
        </CardHeader>
      </Card>

      {/* Applications & Credentials List */}
      <div className="space-y-4">
        <div className="flex items-center justify-between">
          <h2 className="text-lg font-semibold flex items-center gap-2">
            <AppWindow className="h-5 w-5 text-primary" />
            Applications & Credentials
          </h2>
        </div>

        {isAppsLoading ? (
          <div className="space-y-3">
            {[1, 2].map((i) => (
              <Card key={i} className="animate-pulse h-32 bg-muted/40" />
            ))}
          </div>
        ) : applications.length === 0 ? (
          <Card className="text-center py-10">
            <CardContent className="space-y-3">
              <AppWindow className="mx-auto h-10 w-10 text-muted-foreground/60" />
              <p className="text-sm font-medium">No applications created for this consumer yet.</p>
              {!isViewer && (
                <Button onClick={() => setIsCreateAppOpen(true)} size="sm" variant="outline" className="gap-2">
                  <Plus className="h-4 w-4" /> Create First Application
                </Button>
              )}
            </CardContent>
          </Card>
        ) : (
          <div className="space-y-4">
            {applications.map((app) => (
              <ApplicationCard
                key={app.id}
                app={app}
                workspaceId={workspaceId!}
                isViewer={isViewer}
                onIssueKey={() => {
                  setSelectedAppId(app.id);
                  setIsIssueKeyOpen(true);
                }}
                onRotateKey={(credId) => rotateKeyMutation.mutate(credId)}
                onRevokeKey={(credId) => revokeKeyMutation.mutate(credId)}
              />
            ))}
          </div>
        )}
      </div>

      {/* Create Application Dialog */}
      <Dialog
        isOpen={isCreateAppOpen}
        onClose={() => {
          setIsCreateAppOpen(false);
          setFormError(null);
        }}
        title="Create Application"
        description={`Create a developer application for ${consumer.name} to hold API keys and subscriptions.`}
      >
        <div className="space-y-4">
          {formError && <Alert variant="destructive">{formError}</Alert>}

          <div className="space-y-1.5">
            <label className="text-xs font-semibold uppercase text-muted-foreground">
              Application Name
            </label>
            <Input
              placeholder="e.g. Production iOS Client"
              value={appName}
              onChange={(e) => setAppName(e.target.value)}
            />
          </div>

          <div className="flex items-center justify-end gap-2 pt-2 border-t border-border">
            <Button
              variant="outline"
              onClick={() => {
                setIsCreateAppOpen(false);
                setFormError(null);
              }}
            >
              Cancel
            </Button>
            <Button
              onClick={() => createAppMutation.mutate(appName)}
              disabled={!appName.trim() || createAppMutation.isPending}
            >
              {createAppMutation.isPending ? "Creating..." : "Create Application"}
            </Button>
          </div>
        </div>
      </Dialog>

      {/* Issue Key Dialog */}
      <Dialog
        isOpen={isIssueKeyOpen}
        onClose={() => {
          setIsIssueKeyOpen(false);
          setFormError(null);
        }}
        title="Issue API Key"
        description="Generate a high-entropy HMAC-SHA256 API key for this application."
      >
        <div className="space-y-4">
          {formError && <Alert variant="destructive">{formError}</Alert>}

          <div className="space-y-2">
            <label className="text-xs font-semibold uppercase text-muted-foreground">
              Environment
            </label>
            <div className="grid grid-cols-3 gap-2">
              {["dev", "staging", "prod"].map((env) => (
                <Button
                  key={env}
                  type="button"
                  variant={keyEnv === env ? "primary" : "outline"}
                  className="capitalize text-xs font-semibold"
                  onClick={() => setKeyEnv(env)}
                >
                  {env}
                </Button>
              ))}
            </div>
          </div>

          <div className="flex items-center justify-end gap-2 pt-2 border-t border-border">
            <Button
              variant="outline"
              onClick={() => {
                setIsIssueKeyOpen(false);
                setFormError(null);
              }}
            >
              Cancel
            </Button>
            <Button
              onClick={() => selectedAppId && issueKeyMutation.mutate({ appId: selectedAppId, env: keyEnv })}
              disabled={issueKeyMutation.isPending}
            >
              {issueKeyMutation.isPending ? "Generating..." : "Generate API Key"}
            </Button>
          </div>
        </div>
      </Dialog>

      {/* ONE-TIME API KEY REVEAL DIALOG */}
      <Dialog
        isOpen={!!revealedKey}
        onClose={() => setRevealedKey(null)}
        title="API Key Generated Successfully"
        description="Please copy your raw API key now. For security, MeterForge hashes this secret with HMAC-SHA256 and will never display it again."
      >
        <div className="space-y-4">
          <div className="p-3 bg-amber-500/10 border border-amber-500/30 rounded-md flex items-start gap-2 text-xs text-amber-600 dark:text-amber-400">
            <AlertTriangle className="h-4 w-4 shrink-0 mt-0.5" />
            <span>You will not be able to retrieve this secret after closing this modal. Store it in a secure environment vault.</span>
          </div>

          <div className="relative">
            <div className="p-3 bg-muted font-mono text-xs break-all rounded-md border border-border select-all pr-16">
              {revealedKey}
            </div>
            <Button
              size="sm"
              variant="secondary"
              className="absolute right-1.5 top-1.5 h-8 gap-1 text-xs"
              onClick={() => revealedKey && copyToClipboard(revealedKey)}
            >
              {copied ? <Check className="h-3.5 w-3.5 text-emerald-500" /> : <Copy className="h-3.5 w-3.5" />}
              {copied ? "Copied!" : "Copy"}
            </Button>
          </div>

          <Button onClick={() => setRevealedKey(null)} className="w-full">
            I have safely copied my API key
          </Button>
        </div>
      </Dialog>
    </div>
  );
}

function ApplicationCard({
  app,
  workspaceId,
  isViewer,
  onIssueKey,
  onRotateKey,
  onRevokeKey,
}: {
  app: ConsumerApplication;
  workspaceId: string;
  isViewer: boolean;
  onIssueKey: () => void;
  onRotateKey: (credId: string) => void;
  onRevokeKey: (credId: string) => void;
}) {
  const { data: credentials = [], isLoading } = useQuery<ApiCredential[]>({
    queryKey: ["credentials", workspaceId, app.id],
    queryFn: () => fetchCredentials(workspaceId, app.id),
  });

  return (
    <Card className="border-border/80">
      <CardHeader className="py-3.5 px-4 bg-muted/20 border-b">
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-2">
            <AppWindow className="h-4 w-4 text-primary" />
            <h3 className="font-semibold text-sm">{app.name}</h3>
            <Badge variant={app.status === "ACTIVE" ? "default" : "secondary"} className="text-[10px]">
              {app.status}
            </Badge>
          </div>
          {!isViewer && (
            <Button size="sm" variant="outline" onClick={onIssueKey} className="h-7 text-xs gap-1">
              <KeyRound className="h-3.5 w-3.5" /> Issue Key
            </Button>
          )}
        </div>
      </CardHeader>
      <CardContent className="p-4 space-y-3">
        <p className="text-xs font-semibold uppercase text-muted-foreground tracking-wider">
          Issued API Credentials
        </p>

        {isLoading ? (
          <div className="text-xs text-muted-foreground animate-pulse">Loading credentials...</div>
        ) : credentials.length === 0 ? (
          <div className="text-xs text-muted-foreground italic py-2">
            No API keys issued yet. Click &quot;Issue Key&quot; to generate an authentication credential.
          </div>
        ) : (
          <div className="space-y-2">
            {credentials.map((cred) => (
              <div
                key={cred.id}
                className="flex items-center justify-between p-2.5 rounded-md border border-border/70 bg-card/60 text-xs"
              >
                <div className="flex items-center gap-3">
                  <KeyRound className="h-4 w-4 text-muted-foreground shrink-0" />
                  <div className="space-y-0.5">
                    <div className="flex items-center gap-2">
                      <span className="font-mono font-medium">
                        {cred.displayPrefix}...{cred.displayLastFour}
                      </span>
                      <span className="text-[10px] uppercase font-mono px-1.5 py-0.5 rounded bg-muted">
                        {cred.environment}
                      </span>
                      <Badge
                        variant={cred.status === "ACTIVE" ? "default" : "secondary"}
                        className="text-[10px] py-0"
                      >
                        {cred.status}
                      </Badge>
                    </div>
                    <p className="text-[11px] text-muted-foreground flex items-center gap-1.5">
                      <Clock className="h-3 w-3" />
                      Created: {new Date(cred.createdAt).toLocaleDateString()}
                      {cred.revokedAt && <span> &middot; Revoked: {new Date(cred.revokedAt).toLocaleDateString()}</span>}
                    </p>
                  </div>
                </div>

                {!isViewer && cred.status === "ACTIVE" && (
                  <div className="flex items-center gap-1.5">
                    <Button
                      size="sm"
                      variant="ghost"
                      className="h-7 text-xs gap-1 text-muted-foreground hover:text-foreground"
                      onClick={() => onRotateKey(cred.id)}
                      title="Rotate key (revokes old key and returns new key)"
                    >
                      <RotateCw className="h-3 w-3" /> Rotate
                    </Button>
                    <Button
                      size="sm"
                      variant="ghost"
                      className="h-7 text-xs gap-1 text-destructive hover:bg-destructive/10"
                      onClick={() => onRevokeKey(cred.id)}
                      title="Revoke key"
                    >
                      <Trash2 className="h-3 w-3" /> Revoke
                    </Button>
                  </div>
                )}
              </div>
            ))}
          </div>
        )}
      </CardContent>
    </Card>
  );
}
