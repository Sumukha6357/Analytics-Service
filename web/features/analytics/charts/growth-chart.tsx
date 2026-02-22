"use client";

import { memo } from "react";
import { Area, AreaChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from "recharts";
import type { RevenueTrendPoint } from "@/features/analytics/types";

export const GrowthChart = memo(function GrowthChart({ data }: { data: RevenueTrendPoint[] }) {
  return (
    <ResponsiveContainer width="100%" height="100%">
      <AreaChart data={data} margin={{ top: 8, right: 8, left: -20, bottom: 0 }}>
        <XAxis dataKey="date" tick={{ fontSize: 11, fill: "#94a3b8" }} tickLine={false} axisLine={false} />
        <YAxis tick={{ fontSize: 11, fill: "#94a3b8" }} tickLine={false} axisLine={false} />
        <Tooltip />
        <Area type="monotone" dataKey="mrr" stroke="#22c55e" fill="#22c55e33" strokeWidth={2} />
        <Area type="monotone" dataKey="arr" stroke="#0ea5e9" fill="#0ea5e926" strokeWidth={2} />
      </AreaChart>
    </ResponsiveContainer>
  );
});
