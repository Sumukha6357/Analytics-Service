"use client";

import { memo } from "react";
import type { FunnelPoint } from "@/features/analytics/types";

const WIDTHS = ["w-full", "w-5/6", "w-2/3", "w-1/2", "w-2/5", "w-1/3", "w-1/4", "w-1/5"];

export const PipelineFunnelChart = memo(function PipelineFunnelChart({ data }: { data: FunnelPoint[] }) {
  const max = Math.max(...data.map((item) => item.value), 1);
  return (
    <div className="flex h-full flex-col justify-center gap-3">
      {data.map((item) => {
        const ratio = item.value / max;
        const idx = Math.max(0, Math.min(WIDTHS.length - 1, Math.round((1 - ratio) * (WIDTHS.length - 1))));
        return (
          <div key={item.step} className="space-y-1">
            <div className="flex justify-between text-xs text-muted-foreground">
              <span>{item.step}</span>
              <span>{item.value.toLocaleString()}</span>
            </div>
            <div className="h-9 rounded-xl bg-muted/70 p-1">
              <div className={`h-full rounded-lg bg-cyan-500 ${WIDTHS[idx]} transition-all duration-200`} />
            </div>
          </div>
        );
      })}
    </div>
  );
});
