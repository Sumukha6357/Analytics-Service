"use client";

import { memo } from "react";
import { Line, LineChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from "recharts";
import type { RevenueTrendPoint } from "@/features/analytics/types";

export const RevenueVsTargetChart = memo(function RevenueVsTargetChart({ data }: { data: RevenueTrendPoint[] }) {
  return (
    <ResponsiveContainer width="100%" height="100%">
      <LineChart data={data} margin={{ top: 8, right: 8, left: -20, bottom: 0 }}>
        <XAxis dataKey="date" tick={{ fontSize: 11, fill: "#94a3b8" }} tickLine={false} axisLine={false} />
        <YAxis tick={{ fontSize: 11, fill: "#94a3b8" }} tickLine={false} axisLine={false} />
        <Tooltip />
        <Line type="monotone" dataKey="revenue" stroke="#22d3ee" strokeWidth={2.4} dot={false} />
        <Line type="monotone" dataKey="target" stroke="#f59e0b" strokeWidth={2.2} strokeDasharray="4 4" dot={false} />
      </LineChart>
    </ResponsiveContainer>
  );
});
