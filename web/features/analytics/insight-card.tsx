"use client";

import { AlertTriangle, Info, TrendingDown } from "lucide-react";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import type { Insight } from "@/features/analytics/types";

export function InsightCards({ insights }: { insights: Insight[] }) {
  return (
    <div className="grid grid-cols-1 gap-4 lg:grid-cols-3">
      {insights.map((insight, idx) => (
        <Card key={`${insight.title}-${idx}`} className="border-border/80 bg-card">
          <CardHeader>
            <CardTitle className="flex items-center gap-2 text-sm">
              {iconFor(insight.severity)}
              {insight.title}
            </CardTitle>
          </CardHeader>
          <CardContent>
            <p className="text-sm text-muted-foreground">{insight.description}</p>
          </CardContent>
        </Card>
      ))}
    </div>
  );
}

function iconFor(severity: Insight["severity"]) {
  if (severity === "high") {
    return <AlertTriangle className="h-4 w-4 text-danger" />;
  }
  if (severity === "medium") {
    return <TrendingDown className="h-4 w-4 text-warning" />;
  }
  return <Info className="h-4 w-4 text-primary" />;
}
