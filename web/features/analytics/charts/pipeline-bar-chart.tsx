"use client";

import { memo } from "react";
import { Bar, BarChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from "recharts";
import type { PipelinePoint } from "@/features/analytics/types";

type Props = { data: PipelinePoint[] };

export const PipelineBarChart = memo(function PipelineBarChart({ data }: Props) {
  return (
    <ResponsiveContainer width="100%" height="100%">
      <BarChart data={data} margin={{ top: 12, right: 12, left: -10, bottom: 0 }}>
        <XAxis dataKey="stage" tick={{ fontSize: 12, fill: "#64748b" }} tickLine={false} axisLine={false} />
        <YAxis tick={{ fontSize: 12, fill: "#64748b" }} tickLine={false} axisLine={false} />
        <Tooltip />
        <Bar dataKey="value" fill="#0ea5e9" radius={[6, 6, 0, 0]} />
      </BarChart>
    </ResponsiveContainer>
  );
});
