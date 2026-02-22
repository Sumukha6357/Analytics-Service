"use client";

import { Badge } from "@/components/ui/badge";
import { Drawer, DrawerContent, DrawerDescription, DrawerTitle } from "@/components/ui/drawer";
import type { DealRow } from "@/features/analytics/types";

type Props = {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  deal: DealRow | null;
};

export function DealDetailDrawer({ open, onOpenChange, deal }: Props) {
  return (
    <Drawer open={open} onOpenChange={onOpenChange}>
      <DrawerContent aria-label="Deal details panel">
        <DrawerTitle className="sr-only">Deal details</DrawerTitle>
        <DrawerDescription className="sr-only">Detailed deal attributes and status information.</DrawerDescription>
        {deal ? (
          <div className="space-y-4 pr-8">
            <div>
              <h3 className="text-lg font-semibold">{deal.name}</h3>
              <p className="text-sm text-muted-foreground">Owner: {deal.owner}</p>
            </div>
            <div className="grid grid-cols-2 gap-3 text-sm">
              <Detail label="Value" value={deal.value} />
              <Detail label="Stage" value={deal.stage} />
              <Detail label="Pipeline" value={deal.pipeline} />
              <Detail label="Segment" value={deal.segment} />
              <Detail label="Idle Days" value={`${deal.idleDays}`} />
              <Detail label="Close Date" value={deal.closeDate} />
            </div>
            <div className="rounded-xl border border-border/80 bg-muted/40 p-3">
              <p className="mb-2 text-xs font-semibold uppercase tracking-wide text-muted-foreground">Status</p>
              <Badge
                variant={deal.status === "on-track" ? "success" : deal.status === "warning" ? "warning" : "danger"}
                className="capitalize"
              >
                {deal.status}
              </Badge>
            </div>
          </div>
        ) : null}
      </DrawerContent>
    </Drawer>
  );
}

function Detail({ label, value }: { label: string; value: string }) {
  return (
    <div className="rounded-xl border border-border/80 bg-muted/35 p-3">
      <p className="text-xs uppercase tracking-wide text-muted-foreground">{label}</p>
      <p className="mt-1 font-semibold">{value}</p>
    </div>
  );
}
