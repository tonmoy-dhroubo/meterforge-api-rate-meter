"use client";

import * as React from "react";
import { ThemeProvider as NextThemesProvider } from "next-themes";
import { type ThemeProviderProps } from "next-themes";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { AuthProvider } from "@/lib/auth-context";

export function Providers({ children, ...props }: ThemeProviderProps) {
  const [queryClient] = React.useState(
    () =>
      new QueryClient({
        defaultOptions: {
          queries: {
            refetchOnWindowFocus: false,
            retry: 1,
          },
        },
      })
  );

  return (
    <NextThemesProvider {...props}>
      <QueryClientProvider client={queryClient}>
        <AuthProvider>{children}</AuthProvider>
      </QueryClientProvider>
    </NextThemesProvider>
  );
}
