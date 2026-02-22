import { cn } from "@/lib/utils";

export function AppShell({ children, className }: { children: React.ReactNode; className?: string }) {
  return (
    <div className="min-h-screen bg-background">
      <div className={cn("mx-auto max-w-[1520px] p-4 md:p-8", className)}>{children}</div>
    </div>
  );
}
