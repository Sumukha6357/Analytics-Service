import { Skeleton } from "@/components/ui/skeleton";

export function ExecutiveKpiSkeleton() {
  return (
    <div className="grid grid-cols-1 gap-4 md:grid-cols-2 xl:grid-cols-4">
      {Array.from({ length: 6 }).map((_, i) => (
        <div key={i} className="rounded-2xl border border-border/80 bg-card p-4">
          <Skeleton className="mb-2 h-3 w-24" />
          <Skeleton className="mb-2 h-8 w-36" />
          <Skeleton className="mb-3 h-3 w-28" />
          <Skeleton className="h-8 w-24" />
        </div>
      ))}
    </div>
  );
}

export function IntelligenceChartSkeleton() {
  return <Skeleton className="h-[300px] w-full" />;
}
