"use client";

import { memo } from "react";
import { PolarAngleAxis, RadialBar, RadialBarChart, ResponsiveContainer } from "recharts";

export const ForecastConfidenceGauge = memo(function ForecastConfidenceGauge({ value }: { value: number }) {
  const safe = Math.max(0, Math.min(100, value));
  return (
    <div className="flex h-full flex-col items-center justify-center gap-2">
      <div className="h-56 w-full">
        <ResponsiveContainer width="100%" height="100%">
          <RadialBarChart
            cx="50%"
            cy="50%"
            innerRadius="62%"
            outerRadius="86%"
            barSize={18}
            data={[{ name: "confidence", value: safe }]}
            startAngle={220}
            endAngle={-40}
          >
            <PolarAngleAxis type="number" domain={[0, 100]} tick={false} />
            <RadialBar dataKey="value" cornerRadius={10} fill="#22c55e" background={{ fill: "#334155" }} />
          </RadialBarChart>
        </ResponsiveContainer>
      </div>
      <p className="text-3xl font-semibold">{safe}%</p>
      <p className="text-xs text-muted-foreground">Forecast Confidence</p>
    </div>
  );
});
