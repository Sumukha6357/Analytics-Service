"use client";

import { memo } from "react";
import { Bar, BarChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from "recharts";
import type { StageDistributionPoint } from "@/features/analytics/types";

export const StageDistributionChart = memo(function StageDistributionChart({ data }: { data: StageDistributionPoint[] }) {
  return (
    <ResponsiveContainer width="100%" height="100%">
      <BarChart data={data} margin={{ top: 10, right: 8, left: -20, bottom: 0 }}>
        <XAxis dataKey="stage" tick={{ fontSize: 11, fill: "#94a3b8" }} tickLine={false} axisLine={false} />
        <YAxis tick={{ fontSize: 11, fill: "#94a3b8" }} tickLine={false} axisLine={false} />
        <Tooltip />
        <Bar dataKey="value" fill="#38bdf8" radius={[6, 6, 0, 0]} />
      </BarChart>
    </ResponsiveContainer>
  );
});
