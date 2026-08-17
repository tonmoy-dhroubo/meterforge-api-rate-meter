"use client";

import React from "react";
import { useAuth } from "@/lib/auth-context";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { useTheme } from "next-themes";
import { Moon, Sun, ChevronDown, LogOut, Shield, Layers } from "lucide-react";
import { useRouter } from "next/navigation";

export function Navbar() {
  const { user, memberships, currentMembership, currentRole, logout } = useAuth();
  const { theme, setTheme } = useTheme();
  const router = useRouter();
  const [isWorkspaceMenuOpen, setIsWorkspaceMenuOpen] = React.useState(false);

  const handleSwitchWorkspace = (slug: string) => {
    setIsWorkspaceMenuOpen(false);
    router.push(`/${slug}/products`);
  };

  return (
    <header className="sticky top-0 z-40 flex h-14 w-full items-center justify-between border-b border-border bg-card/80 px-4 backdrop-blur-md">
      <div className="flex items-center gap-4">
        <div className="flex items-center gap-2">
          <div className="flex h-8 w-8 items-center justify-center rounded-lg bg-primary text-primary-foreground font-bold tracking-wider text-sm shadow-xs">
            MF
          </div>
          <span className="font-bold tracking-tight text-foreground hidden sm:inline-block">
            MeterForge
          </span>
        </div>

        <div className="h-4 w-px bg-border mx-1" />

        {/* Workspace Dropdown */}
        <div className="relative">
          <button
            onClick={() => setIsWorkspaceMenuOpen(!isWorkspaceMenuOpen)}
            className="flex items-center gap-2 rounded-md px-2.5 py-1.5 text-sm font-medium hover:bg-muted/80 transition-colors border border-border/60 bg-background cursor-pointer"
          >
            <Layers className="h-4 w-4 text-muted-foreground" />
            <span>{currentMembership?.workspaceName || "Select Workspace"}</span>
            <ChevronDown className="h-3.5 w-3.5 text-muted-foreground" />
          </button>

          {isWorkspaceMenuOpen && (
            <div className="absolute left-0 top-full mt-1.5 w-56 rounded-md border border-border bg-popover p-1 shadow-md z-50 animate-in fade-in-0 zoom-in-95 duration-100">
              <div className="px-2 py-1.5 text-xs font-semibold text-muted-foreground uppercase tracking-wider">
                Workspaces
              </div>
              {memberships.map((m) => (
                <button
                  key={m.workspaceId}
                  onClick={() => handleSwitchWorkspace(m.workspaceSlug)}
                  className="flex w-full items-center justify-between rounded-sm px-2 py-1.5 text-sm text-left hover:bg-accent hover:text-accent-foreground cursor-pointer"
                >
                  <span className="font-medium truncate">{m.workspaceName}</span>
                  <Badge variant="outline" className="text-[10px] uppercase font-mono">
                    {m.role}
                  </Badge>
                </button>
              ))}
            </div>
          )}
        </div>

        {currentRole && (
          <Badge
            variant={
              currentRole === "OWNER"
                ? "default"
                : currentRole === "MEMBER"
                ? "secondary"
                : "outline"
            }
            className="hidden md:inline-flex"
          >
            <Shield className="h-3 w-3 mr-1" />
            {currentRole}
          </Badge>
        )}
      </div>

      <div className="flex items-center gap-2">
        <button
          onClick={() => setTheme(theme === "dark" ? "light" : "dark")}
          className="flex h-8 w-8 items-center justify-center rounded-md border border-border/60 hover:bg-muted transition-colors cursor-pointer text-muted-foreground hover:text-foreground"
          aria-label="Toggle theme"
        >
          <Sun className="h-4 w-4 rotate-0 scale-100 transition-all dark:-rotate-90 dark:scale-0" />
          <Moon className="absolute h-4 w-4 rotate-90 scale-0 transition-all dark:rotate-0 dark:scale-100" />
        </button>

        <div className="h-4 w-px bg-border mx-1" />

        <div className="flex items-center gap-2">
          <span className="text-xs text-muted-foreground hidden sm:inline-block">
            {user?.email}
          </span>
          <Button
            variant="ghost"
            size="sm"
            onClick={logout}
            className="text-muted-foreground hover:text-destructive h-8 px-2"
            title="Sign out"
          >
            <LogOut className="h-4 w-4 mr-1" />
            <span className="hidden sm:inline">Sign out</span>
          </Button>
        </div>
      </div>
    </header>
  );
}
