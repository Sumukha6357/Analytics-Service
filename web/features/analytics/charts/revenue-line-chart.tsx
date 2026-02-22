"use client";

import { memo } from "react";
import { Line, LineChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from "recharts";
import type { RevenuePoint } from "@/features/analytics/types";

type Props = { data: RevenuePoint[] };

export const RevenueLineChart = memo(function RevenueLineChart({ data }: Props) {
  return (
    <ResponsiveContainer width="100%" height="100%">
      <LineChart data={data} margin={{ top: 8, right: 8, left: -12, bottom: 0 }}>
        <XAxis dataKey="date" tick={{ fontSize: 12, fill: "#64748b" }} tickLine={false} axisLine={false} />
        <YAxis tick={{ fontSize: 12, fill: "#64748b" }} tickLine={false} axisLine={false} />
        <Tooltip />
        <Line type="monotone" dataKey="revenue" stroke="#0369a1" strokeWidth={2.5} dot={false} />
      </LineChart>
    </ResponsiveContainer>
  );
});
