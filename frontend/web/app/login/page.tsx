"use client";

import React, { useState } from "react";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { useRouter } from "next/navigation";
import { login } from "@/lib/api/auth";
import { ApiError } from "@/lib/api/client";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Card, CardHeader, CardTitle, CardDescription, CardContent, CardFooter } from "@/components/ui/card";
import { Alert } from "@/components/ui/alert";
import { Shield, ArrowRight, UserCheck, KeyRound } from "lucide-react";
import { useTheme } from "next-themes";
import { Moon, Sun } from "lucide-react";

export default function LoginPage() {
  const router = useRouter();
  const queryClient = useQueryClient();
  const { theme, setTheme } = useTheme();

  const [email, setEmail] = useState("owner@meterforge.local");
  const [password, setPassword] = useState("password123");
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  const loginMutation = useMutation({
    mutationFn: login,
    onSuccess: (data) => {
      queryClient.setQueryData(["auth", "me"], data);
      const defaultSlug = data.memberships[0]?.workspaceSlug || "acme-apis";
      router.push(`/${defaultSlug}/products`);
    },
    onError: (err: unknown) => {
      if (err instanceof ApiError) {
        setErrorMessage(err.problem?.detail || err.message);
      } else if (err instanceof Error) {
        setErrorMessage(err.message);
      } else {
        setErrorMessage("Invalid login credentials");
      }
    },
  });

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    setErrorMessage(null);
    loginMutation.mutate({ email, password });
  };

  const handleQuickLogin = (quickEmail: string) => {
    setEmail(quickEmail);
    setPassword("password123");
    setErrorMessage(null);
    loginMutation.mutate({ email: quickEmail, password: "password123" });
  };

  return (
    <main className="min-h-screen flex flex-col justify-between bg-muted/30 p-4 sm:p-6">
      <div className="flex justify-between items-center max-w-5xl w-full mx-auto py-2">
        <div className="flex items-center gap-2">
          <div className="flex h-9 w-9 items-center justify-center rounded-lg bg-primary text-primary-foreground font-bold tracking-wider shadow-xs">
            MF
          </div>
          <div>
            <h1 className="text-lg font-bold tracking-tight leading-none text-foreground">
              MeterForge
            </h1>
            <p className="text-xs text-muted-foreground">API Limiting & Metering</p>
          </div>
        </div>

        <button
          onClick={() => setTheme(theme === "dark" ? "light" : "dark")}
          className="flex h-8 w-8 items-center justify-center rounded-md border border-border/60 hover:bg-muted transition-colors cursor-pointer text-muted-foreground hover:text-foreground"
          aria-label="Toggle theme"
        >
          <Sun className="h-4 w-4 rotate-0 scale-100 transition-all dark:-rotate-90 dark:scale-0" />
          <Moon className="absolute h-4 w-4 rotate-90 scale-0 transition-all dark:rotate-0 dark:scale-100" />
        </button>
      </div>

      <div className="w-full max-w-md mx-auto my-auto space-y-6">
        <Card className="border-border/80 shadow-md">
          <CardHeader className="space-y-1">
            <CardTitle className="text-2xl font-bold tracking-tight">
              Sign in to Console
            </CardTitle>
            <CardDescription>
              Enter staff credentials or select a seeded role to test RBAC
            </CardDescription>
          </CardHeader>

          <form onSubmit={handleSubmit}>
            <CardContent className="space-y-4">
              {errorMessage && (
                <Alert variant="destructive" title="Authentication Failed">
                  {errorMessage}
                </Alert>
              )}

              <div className="space-y-2">
                <label className="text-xs font-semibold uppercase tracking-wider text-muted-foreground">
                  Email address
                </label>
                <Input
                  type="email"
                  value={email}
                  onChange={(e) => setEmail(e.target.value)}
                  placeholder="staff@meterforge.local"
                  required
                />
              </div>

              <div className="space-y-2">
                <label className="text-xs font-semibold uppercase tracking-wider text-muted-foreground">
                  Password
                </label>
                <Input
                  type="password"
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  placeholder="••••••••••••"
                  required
                />
              </div>

              <Button
                type="submit"
                className="w-full mt-2"
                isLoading={loginMutation.isPending}
              >
                Sign in with Password <ArrowRight className="ml-2 h-4 w-4" />
              </Button>
            </CardContent>
          </form>

          <div className="relative px-6 py-2">
            <div className="absolute inset-0 flex items-center px-6">
              <span className="w-full border-t border-border" />
            </div>
            <div className="relative flex justify-center text-xs uppercase">
              <span className="bg-card px-2 text-muted-foreground font-semibold">
                Quick Demo Logins
              </span>
            </div>
          </div>

          <CardFooter className="flex flex-col gap-2 pt-2">
            <Button
              type="button"
              variant="outline"
              size="sm"
              className="w-full justify-between"
              onClick={() => handleQuickLogin("owner@meterforge.local")}
              disabled={loginMutation.isPending}
            >
              <div className="flex items-center gap-2">
                <Shield className="h-4 w-4 text-primary" />
                <span className="font-medium">Owner (Acme APIs)</span>
              </div>
              <span className="text-xs text-muted-foreground font-mono">
                owner@meterforge.local
              </span>
            </Button>

            <Button
              type="button"
              variant="outline"
              size="sm"
              className="w-full justify-between"
              onClick={() => handleQuickLogin("member@meterforge.local")}
              disabled={loginMutation.isPending}
            >
              <div className="flex items-center gap-2">
                <UserCheck className="h-4 w-4 text-emerald-500" />
                <span className="font-medium">Member (Write Access)</span>
              </div>
              <span className="text-xs text-muted-foreground font-mono">
                member@meterforge.local
              </span>
            </Button>

            <Button
              type="button"
              variant="outline"
              size="sm"
              className="w-full justify-between"
              onClick={() => handleQuickLogin("viewer@meterforge.local")}
              disabled={loginMutation.isPending}
            >
              <div className="flex items-center gap-2">
                <KeyRound className="h-4 w-4 text-amber-500" />
                <span className="font-medium">Viewer (Read-Only)</span>
              </div>
              <span className="text-xs text-muted-foreground font-mono">
                viewer@meterforge.local
              </span>
            </Button>
          </CardFooter>
        </Card>
      </div>

      <div className="text-center text-xs text-muted-foreground py-4">
        Protected by HttpOnly JWT Session &middot; Flyway Seeded Data &middot; Spring Security RBAC
      </div>
    </main>
  );
}
