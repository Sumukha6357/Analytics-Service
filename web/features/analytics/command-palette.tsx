"use client";

import { Command as CommandPrimitive } from "cmdk";
import { BarChart3, FileText, Gauge, Search } from "lucide-react";
import { useEffect, useState } from "react";
import { Dialog, DialogContent } from "@/components/ui/dialog";

type CommandPaletteProps = {
  onOpenSavedReports: () => void;
  onFocusKpi: (kpiId: string) => void;
};

export function CommandPalette({ onOpenSavedReports, onFocusKpi }: CommandPaletteProps) {
  const [open, setOpen] = useState(false);

  useEffect(() => {
    function onKey(e: KeyboardEvent) {
      if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === "k") {
        e.preventDefault();
        setOpen((v) => !v);
      }
    }
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, []);

  return (
    <Dialog open={open} onOpenChange={setOpen}>
      <DialogContent className="max-w-xl p-0">
        <CommandPrimitive className="rounded-2xl" label="Analytics command menu">
          <div className="flex items-center border-b px-3">
            <Search className="h-4 w-4 text-muted-foreground" />
            <CommandPrimitive.Input
              className="h-11 w-full bg-transparent px-3 text-sm outline-none"
              placeholder="Search commands..."
              aria-label="Search commands"
            />
          </div>
          <CommandPrimitive.List className="max-h-80 overflow-y-auto p-2">
            <CommandPrimitive.Empty className="p-3 text-sm text-muted-foreground">No command found.</CommandPrimitive.Empty>
            <CommandPrimitive.Group heading="Navigation" className="px-1 pb-2 text-xs text-muted-foreground">
              <Item
                icon={<FileText className="h-4 w-4" />}
                label="Jump to Saved Reports"
                onSelect={() => {
                  onOpenSavedReports();
                  setOpen(false);
                }}
              />
            </CommandPrimitive.Group>
            <CommandPrimitive.Group heading="KPI Focus" className="px-1 pb-2 text-xs text-muted-foreground">
              <Item icon={<Gauge className="h-4 w-4" />} label="Focus Revenue KPI" onSelect={() => selectKpi("total-revenue")} />
              <Item icon={<Gauge className="h-4 w-4" />} label="Focus Conversion KPI" onSelect={() => selectKpi("conversion-rate")} />
              <Item icon={<BarChart3 className="h-4 w-4" />} label="Focus Sales Velocity KPI" onSelect={() => selectKpi("sales-velocity")} />
            </CommandPrimitive.Group>
          </CommandPrimitive.List>
        </CommandPrimitive>
      </DialogContent>
    </Dialog>
  );

  function selectKpi(kpiId: string) {
    onFocusKpi(kpiId);
    setOpen(false);
  }
}

function Item({ icon, label, onSelect }: { icon: React.ReactNode; label: string; onSelect: () => void }) {
  return (
    <CommandPrimitive.Item
      className="flex cursor-pointer items-center gap-2 rounded-xl px-3 py-2 text-sm data-[selected=true]:bg-muted"
      onSelect={onSelect}
    >
      {icon}
      {label}
    </CommandPrimitive.Item>
  );
}
