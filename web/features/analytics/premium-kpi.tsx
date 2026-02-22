"use client";

import { memo } from "react";
import { TrendingDown, TrendingUp } from "lucide-react";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { cn } from "@/lib/utils";

type Props = {
  title: string;
  value: string;
  delta: number;
  subLabel: string;
  sparkline: number[];
};

function Sparkline({ values, positive }: { values: number[]; positive: boolean }) {
  const min = Math.min(...values);
  const max = Math.max(...values);
  const span = max - min || 1;
  const points = values
    .map((v, i) => `${(i / Math.max(values.length - 1, 1)) * 100},${100 - ((v - min) / span) * 100}`)
    .join(" ");

  return (
    <svg viewBox="0 0 100 36" className="h-9 w-24" aria-hidden>
      <polyline
        fill="none"
        stroke={positive ? "hsl(var(--success))" : "hsl(var(--danger))"}
        strokeWidth="2"
        strokeLinecap="round"
        points={points.replaceAll(",", " ")}
      />
    </svg>
  );
}

export const PremiumKPI = memo(function PremiumKPI({ title, value, delta, subLabel, sparkline }: Props) {
  const positive = delta >= 0;
  return (
    <Card
      className={cn(
        "overflow-hidden border-border/90 bg-card transition duration-175",
        positive ? "shadow-[inset_0_0_0_1px_rgba(34,197,94,0.24)]" : "shadow-card"
      )}
    >
      <CardHeader className="pb-2">
        <div className="space-y-1">
          <CardTitle className="text-xs uppercase tracking-[0.08em] text-muted-foreground">{title}</CardTitle>
          <div className="text-3xl font-semibold tracking-tight text-card-foreground">{value}</div>
          <p className="text-xs text-muted-foreground">{subLabel}</p>
        </div>
        <Sparkline values={sparkline} positive={positive} />
      </CardHeader>
      <CardContent className="pt-0">
        <div
          className={cn(
            "inline-flex items-center gap-1 rounded-full px-2 py-1 text-xs font-semibold",
            positive ? "bg-success/15 text-success" : "bg-danger/15 text-danger"
          )}
          aria-label={`${title} changed ${Math.abs(delta)} percent`}
        >
          {positive ? <TrendingUp className="h-3.5 w-3.5" /> : <TrendingDown className="h-3.5 w-3.5" />}
          {Math.abs(delta)}%
        </div>
      </CardContent>
    </Card>
  );
});
