"use client";

import { AlertTriangle, ChevronLeft, ChevronRight } from "lucide-react";
import { useState } from "react";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import type { RiskAlert } from "@/features/analytics/types";

export function RiskAlertPanel({ alerts }: { alerts: RiskAlert[] }) {
  const [collapsed, setCollapsed] = useState(false);
  return (
    <Card className="sticky top-4 border-border/80 bg-card">
      <CardHeader className="items-center">
        <CardTitle className="flex items-center gap-2 text-sm">
          <AlertTriangle className="h-4 w-4 text-warning" />
          Risk & Alerts
        </CardTitle>
        <Button variant="ghost" size="sm" aria-label="Toggle risk panel" onClick={() => setCollapsed((v) => !v)}>
          {collapsed ? <ChevronLeft className="h-4 w-4" /> : <ChevronRight className="h-4 w-4" />}
        </Button>
      </CardHeader>
      {!collapsed ? (
        <CardContent className="space-y-3">
          {alerts.map((alert) => (
            <div key={alert.id} className="rounded-xl border border-border/80 bg-muted/35 p-3">
              <div className="mb-1 flex items-center justify-between">
                <p className="text-sm font-semibold">{alert.title}</p>
                <span
                  className={
                    alert.severity === "high"
                      ? "rounded-full bg-danger/20 px-2 py-0.5 text-[11px] font-semibold text-danger"
                      : alert.severity === "medium"
                        ? "rounded-full bg-warning/20 px-2 py-0.5 text-[11px] font-semibold text-warning"
                        : "rounded-full bg-primary/20 px-2 py-0.5 text-[11px] font-semibold text-primary"
                  }
                >
                  {alert.severity}
                </span>
              </div>
              <p className="mb-2 text-xs text-muted-foreground">{alert.description}</p>
              <Button variant="outline" size="sm" className="w-full" aria-label={alert.actionLabel ?? "Review alert"}>
                {alert.actionLabel ?? "Review"}
              </Button>
            </div>
          ))}
        </CardContent>
      ) : null}
    </Card>
  );
}
