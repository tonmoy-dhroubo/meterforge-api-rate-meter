import * as React from "react";
import { cn } from "@/lib/utils";

export interface BadgeProps extends React.HTMLAttributes<HTMLDivElement> {
  variant?:
    | "default"
    | "secondary"
    | "outline"
    | "success"
    | "warning"
    | "destructive"
    | "method-get"
    | "method-post"
    | "method-put"
    | "method-delete"
    | "method-patch";
}

export function Badge({
  className,
  variant = "default",
  children,
  ...props
}: BadgeProps) {
  const variantStyles = {
    default: "bg-primary/10 text-primary border-primary/20",
    secondary: "bg-secondary text-secondary-foreground border-transparent",
    outline: "border-border text-foreground",
    success:
      "bg-emerald-500/10 text-emerald-600 dark:text-emerald-400 border-emerald-500/20",
    warning:
      "bg-amber-500/10 text-amber-600 dark:text-amber-400 border-amber-500/20",
    destructive:
      "bg-rose-500/10 text-rose-600 dark:text-rose-400 border-rose-500/20",
    "method-get":
      "bg-blue-500/10 text-blue-600 dark:text-blue-400 border-blue-500/20 font-mono font-bold",
    "method-post":
      "bg-emerald-500/10 text-emerald-600 dark:text-emerald-400 border-emerald-500/20 font-mono font-bold",
    "method-put":
      "bg-amber-500/10 text-amber-600 dark:text-amber-400 border-amber-500/20 font-mono font-bold",
    "method-delete":
      "bg-rose-500/10 text-rose-600 dark:text-rose-400 border-rose-500/20 font-mono font-bold",
    "method-patch":
      "bg-purple-500/10 text-purple-600 dark:text-purple-400 border-purple-500/20 font-mono font-bold",
  };

  return (
    <div
      className={cn(
        "inline-flex items-center rounded-md border px-2 py-0.5 text-xs font-semibold transition-colors focus:outline-none focus:ring-2 focus:ring-ring focus:ring-offset-2",
        variantStyles[variant],
        className
      )}
      {...props}
    >
      {children}
    </div>
  );
}
