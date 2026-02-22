"use client";

import { Maximize2, Minimize2, MoreHorizontal } from "lucide-react";
import { useState } from "react";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Dialog, DialogContent, DialogTrigger } from "@/components/ui/dialog";

type Props = {
  title: string;
  subtitle?: string;
  children: React.ReactNode;
};

export function IntelligenceChartCard({ title, subtitle, children }: Props) {
  const [collapsed, setCollapsed] = useState(false);
  return (
    <Card className="h-full border-border/90 bg-card/95">
      <CardHeader className="items-center">
        <div>
          <CardTitle>{title}</CardTitle>
          {subtitle ? <p className="mt-1 text-xs text-muted-foreground">{subtitle}</p> : null}
        </div>
        <div className="flex items-center gap-1">
          <Button size="sm" variant="ghost" aria-label={`Collapse ${title}`} onClick={() => setCollapsed((v) => !v)}>
            {collapsed ? <Maximize2 className="h-4 w-4" /> : <Minimize2 className="h-4 w-4" />}
          </Button>
          <Dialog>
            <DialogTrigger asChild>
              <Button size="sm" variant="ghost" aria-label={`Expand ${title} full screen`}>
                <MoreHorizontal className="h-4 w-4" />
              </Button>
            </DialogTrigger>
            <DialogContent className="max-w-6xl">
              <h3 className="mb-2 text-sm font-semibold">{title}</h3>
              <div className="h-[72vh]">{children}</div>
            </DialogContent>
          </Dialog>
        </div>
      </CardHeader>
      {!collapsed ? <CardContent className="h-[320px]">{children}</CardContent> : null}
    </Card>
  );
}
