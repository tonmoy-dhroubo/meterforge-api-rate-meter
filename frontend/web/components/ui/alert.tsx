import * as React from "react";
import { cn } from "@/lib/utils";
import { AlertCircle, AlertTriangle, CheckCircle2, Info } from "lucide-react";

export interface AlertProps extends React.HTMLAttributes<HTMLDivElement> {
  variant?: "info" | "success" | "warning" | "destructive";
  title?: string;
}

export function Alert({
  className,
  variant = "info",
  title,
  children,
  ...props
}: AlertProps) {
  const icons = {
    info: <Info className="h-4 w-4 text-blue-500" />,
    success: <CheckCircle2 className="h-4 w-4 text-emerald-500" />,
    warning: <AlertTriangle className="h-4 w-4 text-amber-500" />,
    destructive: <AlertCircle className="h-4 w-4 text-rose-500" />,
  };

  const variants = {
    info: "bg-blue-500/10 border-blue-500/20 text-blue-900 dark:text-blue-200",
    success:
      "bg-emerald-500/10 border-emerald-500/20 text-emerald-900 dark:text-emerald-200",
    warning:
      "bg-amber-500/10 border-amber-500/20 text-amber-900 dark:text-amber-200",
    destructive:
      "bg-rose-500/10 border-rose-500/20 text-rose-900 dark:text-rose-200",
  };

  return (
    <div
      role="alert"
      className={cn(
        "flex gap-3 rounded-lg border p-4 text-sm leading-relaxed",
        variants[variant],
        className
      )}
      {...props}
    >
      <div className="shrink-0 pt-0.5">{icons[variant]}</div>
      <div className="space-y-1">
        {title && <h5 className="font-semibold tracking-tight">{title}</h5>}
        <div className="text-sm opacity-90">{children}</div>
      </div>
    </div>
  );
}
