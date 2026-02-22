"use client";

import { memo } from "react";
import type { FunnelPoint } from "@/features/analytics/types";

const COLORS = ["bg-sky-600", "bg-sky-500", "bg-cyan-500", "bg-teal-500"];
const WIDTH_CLASSES = ["w-1/5", "w-1/4", "w-1/3", "w-2/5", "w-1/2", "w-3/5", "w-2/3", "w-3/4", "w-5/6", "w-full"];

type Props = { data: FunnelPoint[] };

export const ConversionFunnelChart = memo(function ConversionFunnelChart({ data }: Props) {
  const max = Math.max(...data.map((d) => d.value), 1);
  return (
    <div className="flex h-full flex-col justify-center gap-3">
      {data.map((row, idx) => {
        const bucket = Math.max(0, Math.min(9, Math.floor((row.value / max) * 9)));
        return (
          <div key={row.step} className="space-y-1">
            <div className="flex items-center justify-between text-xs text-muted-foreground">
              <span>{row.step}</span>
              <span>{row.value.toLocaleString()}</span>
            </div>
            <div className="h-9 rounded-xl bg-muted p-1">
              <div className={`h-full rounded-lg ${COLORS[idx % COLORS.length]} ${WIDTH_CLASSES[bucket]} transition-all duration-200`} />
            </div>
          </div>
        );
      })}
    </div>
  );
});
