import * as React from "react";
import { X } from "lucide-react";

interface DialogProps {
  isOpen: boolean;
  onClose: () => void;
  title: string;
  description?: string;
  children: React.ReactNode;
}

export function Dialog({
  isOpen,
  onClose,
  title,
  description,
  children,
}: DialogProps) {
  React.useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key === "Escape") onClose();
    };
    if (isOpen) {
      document.addEventListener("keydown", handleKeyDown);
      document.body.style.overflow = "hidden";
    }
    return () => {
      document.removeEventListener("keydown", handleKeyDown);
      document.body.style.overflow = "unset";
    };
  }, [isOpen, onClose]);

  if (!isOpen) return null;

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/50 backdrop-blur-xs animate-in fade-in-0 duration-150">
      <div
        className="relative w-full max-w-lg rounded-xl border border-border bg-card p-6 shadow-lg sm:rounded-lg animate-in zoom-in-95 duration-150"
        role="dialog"
        aria-modal="true"
      >
        <div className="flex items-center justify-between pb-4 border-b border-border/60">
          <div>
            <h2 className="text-lg font-semibold leading-none tracking-tight">
              {title}
            </h2>
            {description && (
              <p className="mt-1 text-sm text-muted-foreground">
                {description}
              </p>
            )}
          </div>
          <button
            onClick={onClose}
            className="rounded-sm opacity-70 ring-offset-background transition-opacity hover:opacity-100 focus:outline-none focus:ring-2 focus:ring-ring focus:ring-offset-2 cursor-pointer p-1"
          >
            <X className="h-4 w-4" />
            <span className="sr-only">Close</span>
          </button>
        </div>
        <div className="py-4">{children}</div>
      </div>
    </div>
  );
}
