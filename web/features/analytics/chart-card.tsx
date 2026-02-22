"use client";

import { ChevronDown, ChevronUp, Expand } from "lucide-react";
import { useState } from "react";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Dialog, DialogContent, DialogTrigger } from "@/components/ui/dialog";

type ChartCardProps = {
  title: string;
  children: React.ReactNode;
  defaultCollapsed?: boolean;
};

export function ChartCard({ title, children, defaultCollapsed = false }: ChartCardProps) {
  const [collapsed, setCollapsed] = useState(defaultCollapsed);
  return (
    <Card className="animate-fade-in">
      <CardHeader className="items-center">
        <CardTitle>{title}</CardTitle>
        <div className="flex items-center gap-2">
          <Button
            variant="ghost"
            size="sm"
            aria-label={collapsed ? `Expand ${title}` : `Collapse ${title}`}
            onClick={() => setCollapsed((v) => !v)}
          >
            {collapsed ? <ChevronDown className="h-4 w-4" /> : <ChevronUp className="h-4 w-4" />}
          </Button>
          <Dialog>
            <DialogTrigger asChild>
              <Button variant="ghost" size="sm" aria-label={`Fullscreen ${title}`}>
                <Expand className="h-4 w-4" />
              </Button>
            </DialogTrigger>
            <DialogContent>
              <h4 className="mb-3 text-base font-semibold">{title}</h4>
              <div className="h-[65vh]">{children}</div>
            </DialogContent>
          </Dialog>
        </div>
      </CardHeader>
      {!collapsed ? <CardContent className="h-80 md:h-96">{children}</CardContent> : null}
    </Card>
  );
}
