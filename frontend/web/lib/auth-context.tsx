"use client";

import React, { createContext, useContext, useEffect } from "react";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { useRouter, useParams, usePathname } from "next/navigation";
import { getMe, logout as apiLogout } from "./api/auth";
import { MeResponse, UserSummary, WorkspaceMembershipSummary, Role } from "./api/types";

interface AuthContextType {
  user: UserSummary | null;
  memberships: WorkspaceMembershipSummary[];
  currentMembership: WorkspaceMembershipSummary | null;
  currentRole: Role | null;
  isLoading: boolean;
  isAuthenticated: boolean;
  logout: () => Promise<void>;
  refetchUser: () => void;
}

const AuthContext = createContext<AuthContextType | undefined>(undefined);

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const router = useRouter();
  const params = useParams();
  const pathname = usePathname();
  const queryClient = useQueryClient();

  const workspaceSlug = params?.workspaceSlug as string | undefined;

  const {
    data: meData,
    isLoading,
    isError,
    refetch: refetchUser,
  } = useQuery<MeResponse>({
    queryKey: ["auth", "me"],
    queryFn: getMe,
    retry: false,
    staleTime: 5 * 60 * 1000,
  });

  const user = meData?.user || null;
  const memberships = meData?.memberships || [];

  const currentMembership =
    memberships.find((m) => m.workspaceSlug === workspaceSlug) ||
    memberships[0] ||
    null;

  const currentRole = currentMembership?.role || null;

  const logoutMutation = useMutation({
    mutationFn: apiLogout,
    onSuccess: () => {
      queryClient.clear();
      router.push("/login");
    },
  });

  // If unauthenticated and on a protected dashboard route, redirect to login
  useEffect(() => {
    if (!isLoading && (isError || !user) && pathname !== "/login" && pathname !== "/") {
      router.push("/login");
    }
  }, [isLoading, isError, user, pathname, router]);

  return (
    <AuthContext.Provider
      value={{
        user,
        memberships,
        currentMembership,
        currentRole,
        isLoading,
        isAuthenticated: !!user,
        logout: async () => {
          await logoutMutation.mutateAsync();
        },
        refetchUser,
      }}
    >
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth() {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error("useAuth must be used within an AuthProvider");
  }
  return context;
}
