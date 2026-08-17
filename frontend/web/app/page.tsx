"use client";

import { useEffect } from "react";
import { useRouter } from "next/navigation";
import { useAuth } from "@/lib/auth-context";

export default function RootPage() {
  const router = useRouter();
  const { user, memberships, isLoading } = useAuth();

  useEffect(() => {
    if (!isLoading) {
      if (user && memberships.length > 0) {
        router.push(`/${memberships[0].workspaceSlug}`);
      } else {
        router.push("/login");
      }
    }
  }, [user, memberships, isLoading, router]);

  return (
    <div className="min-h-screen flex items-center justify-center bg-background">
      <div className="flex flex-col items-center gap-3">
        <div className="h-8 w-8 animate-spin rounded-full border-4 border-primary border-t-transparent" />
        <p className="text-sm text-muted-foreground font-medium">Loading workspace...</p>
      </div>
    </div>
  );
}
