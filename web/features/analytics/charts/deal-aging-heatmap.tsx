"use client";

import { memo, Fragment } from "react";
import type { AgingMatrixCell } from "@/features/analytics/types";

const RISK_CLASSES: Record<AgingMatrixCell["risk"], string> = {
  low: "bg-emerald-500/35",
  medium: "bg-amber-500/35",
  high: "bg-rose-500/35"
};

export const DealAgingHeatmap = memo(function DealAgingHeatmap({ data }: { data: AgingMatrixCell[] }) {
  const stages = Array.from(new Set(data.map((d) => d.stage)));
  const buckets = Array.from(new Set(data.map((d) => d.bucket)));
  return (
    <div className="h-full overflow-auto scroll-thin">
      <div className="grid min-w-[540px] grid-cols-[140px_repeat(3,minmax(0,1fr))] gap-2 text-xs">
        <div />
        {buckets.map((bucket) => (
          <div key={bucket} className="px-2 py-1 text-center font-semibold text-muted-foreground">
            {bucket}
          </div>
        ))}
        {stages.map((stage) => (
          <Fragment key={stage}>
            <div key={`${stage}-label`} className="px-2 py-3 text-muted-foreground">
              {stage}
            </div>
            {buckets.map((bucket) => {
              const cell = data.find((item) => item.stage === stage && item.bucket === bucket);
              return (
                <div
                  key={`${stage}-${bucket}`}
                  className={`rounded-xl border border-border/70 px-2 py-3 text-center font-semibold ${cell ? RISK_CLASSES[cell.risk] : "bg-muted/30"}`}
                >
                  {cell ? cell.count : 0}
                </div>
              );
            })}
          </Fragment>
        ))}
      </div>
    </div>
  );
});
