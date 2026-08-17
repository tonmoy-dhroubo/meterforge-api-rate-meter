"use client";

import React from "react";
import { useQuery } from "@tanstack/react-query";
import { useAuth } from "@/lib/auth-context";
import { getAuditLogs, AuditLogItem } from "@/lib/api/audit";
import { Badge } from "@/components/ui/badge";
import { Card } from "@/components/ui/card";
import { History } from "lucide-react";

export default function AuditLogsPage() {
  const { currentMembership } = useAuth();
  const workspaceId = currentMembership?.workspaceId;

  const {
    data: logs = [],
    isLoading,
  } = useQuery<AuditLogItem[]>({
    queryKey: ["auditLogs", workspaceId],
    queryFn: () => (workspaceId ? getAuditLogs(workspaceId) : Promise.resolve([])),
    enabled: !!workspaceId,
  });

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-bold tracking-tight text-foreground">
          Audit Logs
        </h1>
        <p className="text-sm text-muted-foreground">
          Immutable audit trail of all workspace and API configuration mutations
        </p>
      </div>

      {isLoading ? (
        <Card className="p-8 text-center text-muted-foreground">
          Loading audit logs...
        </Card>
      ) : logs.length === 0 ? (
        <Card className="p-8 text-center">
          <History className="h-8 w-8 text-muted-foreground mx-auto mb-2" />
          <h4 className="text-sm font-semibold">No audit logs yet</h4>
          <p className="text-xs text-muted-foreground mt-1">
            Mutations to products, routes, and credentials will appear here automatically.
          </p>
        </Card>
      ) : (
        <div className="rounded-lg border border-border overflow-hidden bg-card">
          <table className="w-full text-left text-sm">
            <thead className="bg-muted/50 text-xs font-semibold uppercase tracking-wider text-muted-foreground border-b border-border">
              <tr>
                <th className="py-3 px-4">Timestamp</th>
                <th className="py-3 px-4">Action</th>
                <th className="py-3 px-4">Resource</th>
                <th className="py-3 px-4">Summary</th>
                <th className="py-3 px-4 font-mono text-xs">Request ID</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-border/60 text-xs">
              {logs.map((log) => (
                <tr key={log.id} className="hover:bg-muted/30 transition-colors">
                  <td className="py-3 px-4 font-mono text-muted-foreground whitespace-nowrap">
                    {new Date(log.createdAt).toLocaleString()}
                  </td>
                  <td className="py-3 px-4">
                    <Badge variant="outline" className="font-mono uppercase font-semibold">
                      {log.action}
                    </Badge>
                  </td>
                  <td className="py-3 px-4 font-medium">
                    <span className="font-mono text-muted-foreground mr-1.5">
                      {log.resourceType}
                    </span>
                    {log.resourceId && (
                      <span className="font-mono text-[11px] text-muted-foreground truncate max-w-[120px] inline-block align-bottom">
                        {log.resourceId}
                      </span>
                    )}
                  </td>
                  <td className="py-3 px-4 text-foreground">{log.summary}</td>
                  <td className="py-3 px-4 font-mono text-[11px] text-muted-foreground">
                    {log.requestId || "—"}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}
