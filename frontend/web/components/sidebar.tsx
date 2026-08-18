"use client";

import React from "react";
import Link from "next/link";
import { usePathname, useParams } from "next/navigation";
import { cn } from "@/lib/utils";
import {
  Boxes,
  Users,
  KeyRound,
  FileSpreadsheet,
  Zap,
  BarChart3,
  History,
  Sparkles,
} from "lucide-react";

export function Sidebar() {
  const pathname = usePathname();
  const params = useParams();
  const workspaceSlug = (params?.workspaceSlug as string) || "acme-apis";

  const navItems = [
    {
      title: "API Products",
      href: `/${workspaceSlug}/products`,
      icon: Boxes,
      active: pathname.startsWith(`/${workspaceSlug}/products`),
      badge: "M1",
    },
    {
      title: "Consumers & Apps",
      href: `/${workspaceSlug}/consumers`,
      icon: Users,
      active: pathname.startsWith(`/${workspaceSlug}/consumers`),
      badge: "M2",
    },
    {
      title: "Plans & Policies",
      href: `/${workspaceSlug}/plans`,
      icon: FileSpreadsheet,
      active: pathname.startsWith(`/${workspaceSlug}/plans`),
      badge: "M2",
    },
    {
      title: "Subscriptions",
      href: `/${workspaceSlug}/subscriptions`,
      icon: KeyRound,
      active: pathname.startsWith(`/${workspaceSlug}/subscriptions`),
      badge: "M2",
    },
    {
      title: "Request Lab",
      href: `/${workspaceSlug}/lab`,
      icon: Zap,
      active: pathname.startsWith(`/${workspaceSlug}/lab`),
      badge: "M3",
    },
    {
      title: "Usage & Analytics",
      href: `/${workspaceSlug}/usage`,
      icon: BarChart3,
      active: pathname.startsWith(`/${workspaceSlug}/usage`),
      badge: "M4",
    },
    {
      title: "Audit Logs",
      href: `/${workspaceSlug}/audit-logs`,
      icon: History,
      active: pathname.startsWith(`/${workspaceSlug}/audit-logs`),
      badge: "M1",
    },
  ];

  return (
    <aside className="w-64 shrink-0 border-r border-border bg-card/40 flex flex-col justify-between p-4 min-h-[calc(100vh-3.5rem)]">
      <div className="space-y-6">
        <div>
          <p className="px-3 text-xs font-semibold uppercase tracking-wider text-muted-foreground mb-2">
            Workspace Navigation
          </p>
          <nav className="space-y-1">
            {navItems.map((item) => {
              const Icon = item.icon;
              return (
                <Link
                  key={item.title}
                  href={item.href}
                  className={cn(
                    "flex items-center justify-between rounded-md px-3 py-2 text-sm font-medium transition-colors",
                    item.active
                      ? "bg-primary text-primary-foreground shadow-xs font-semibold"
                      : "text-foreground/80 hover:bg-muted hover:text-foreground"
                  )}
                >
                  <div className="flex items-center gap-2.5">
                    <Icon className="h-4 w-4" />
                    <span>{item.title}</span>
                  </div>
                  {item.badge && (
                    <span
                      className={cn(
                        "text-[10px] uppercase font-mono px-1.5 py-0.5 rounded",
                        item.active
                          ? "bg-primary-foreground/20 text-primary-foreground"
                          : "bg-muted text-muted-foreground"
                      )}
                    >
                      {item.badge}
                    </span>
                  )}
                </Link>
              );
            })}
          </nav>
        </div>
      </div>

      <div className="pt-4 border-t border-border/60">
        <div className="flex items-center gap-2 rounded-lg bg-muted/50 p-3 text-xs text-muted-foreground">
          <Sparkles className="h-4 w-4 text-primary shrink-0" />
          <span>
            MeterForge Portfolio Edition &middot; Local Docker KRaft Stack
          </span>
        </div>
      </div>
    </aside>
  );
}
