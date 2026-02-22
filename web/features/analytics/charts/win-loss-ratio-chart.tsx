"use client";

import { memo } from "react";
import { Cell, Pie, PieChart, ResponsiveContainer, Tooltip } from "recharts";
import type { WinLossPoint } from "@/features/analytics/types";

const COLORS = ["#22c55e", "#ef4444", "#f59e0b"];
const TEXT_COLORS = ["text-emerald-400", "text-rose-400", "text-amber-400"];

export const WinLossRatioChart = memo(function WinLossRatioChart({ data }: { data: WinLossPoint[] }) {
  return (
    <div className="grid h-full grid-cols-1 items-center gap-3 sm:grid-cols-2">
      <ResponsiveContainer width="100%" height="100%">
        <PieChart>
          <Pie data={data} dataKey="value" nameKey="name" innerRadius={52} outerRadius={88}>
            {data.map((_, idx) => (
              <Cell key={idx} fill={COLORS[idx % COLORS.length]} />
            ))}
          </Pie>
          <Tooltip />
        </PieChart>
      </ResponsiveContainer>
      <div className="space-y-2">
        {data.map((row, idx) => (
          <div key={row.name} className="flex items-center justify-between rounded-lg border border-border/70 bg-muted/25 px-3 py-2 text-sm">
            <span>{row.name}</span>
            <span className={`font-semibold ${TEXT_COLORS[idx % TEXT_COLORS.length]}`}>{row.value}%</span>
          </div>
        ))}
      </div>
    </div>
  );
});
