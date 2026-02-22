"use client";

import { memo } from "react";
import { Cell, Pie, PieChart, ResponsiveContainer, Tooltip } from "recharts";
import type { LeadSourcePoint } from "@/features/analytics/types";

const COLORS = ["#075985", "#0ea5e9", "#22d3ee", "#2dd4bf", "#7dd3fc"];
const DOT_CLASSES = ["bg-sky-800", "bg-sky-500", "bg-cyan-400", "bg-teal-400", "bg-sky-300"];

type Props = { data: LeadSourcePoint[] };

export const LeadSourceDonutChart = memo(function LeadSourceDonutChart({ data }: Props) {
  return (
    <div className="grid h-full grid-cols-1 gap-3 lg:grid-cols-2">
      <ResponsiveContainer width="100%" height="100%">
        <PieChart>
          <Pie data={data} dataKey="value" nameKey="source" innerRadius={58} outerRadius={92} paddingAngle={2}>
            {data.map((_, index) => (
              <Cell key={index} fill={COLORS[index % COLORS.length]} />
            ))}
          </Pie>
          <Tooltip />
        </PieChart>
      </ResponsiveContainer>
      <div className="flex flex-col justify-center gap-2 px-2">
        {data.map((item, index) => (
          <div key={item.source} className="flex items-center justify-between text-sm">
            <span className="inline-flex items-center gap-2">
              <span className={`h-2.5 w-2.5 rounded-full ${DOT_CLASSES[index % DOT_CLASSES.length]}`} />
              {item.source}
            </span>
            <span className="font-semibold">{item.value}%</span>
          </div>
        ))}
      </div>
    </div>
  );
});
