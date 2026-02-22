"use client";

import { CalendarRange, ChevronDown, Download, Plus } from "lucide-react";
import { Button } from "@/components/ui/button";
import { DropdownMenu, DropdownMenuContent, DropdownMenuItem, DropdownMenuTrigger } from "@/components/ui/dropdown-menu";
import { Select } from "@/components/ui/select";
import type { DatePreset } from "@/features/analytics/types";

type ToolbarProps = {
  datePreset: DatePreset;
  setDatePreset: (value: DatePreset) => void;
  organization: string;
  setOrganization: (value: string) => void;
  pipeline: string;
  setPipeline: (value: string) => void;
  owner: string;
  setOwner: (value: string) => void;
  savedReport: string;
  setSavedReport: (value: string) => void;
  savedReports: string[];
};

export function AnalyticsToolbar({
  datePreset,
  setDatePreset,
  organization,
  setOrganization,
  pipeline,
  setPipeline,
  owner,
  setOwner,
  savedReport,
  setSavedReport,
  savedReports
}: ToolbarProps) {
  return (
    <div className="enterprise-grid items-end gap-4">
      <div className="col-span-12 lg:col-span-4">
        <h1 className="text-2xl font-bold tracking-tight md:text-3xl">Analytics & Insights</h1>
        <p className="mt-1 text-sm text-muted-foreground">Sales and revenue intelligence for leadership and frontline teams.</p>
      </div>
      <div className="col-span-12 lg:col-span-8">
        <div className="grid grid-cols-1 gap-2 sm:grid-cols-2 lg:grid-cols-6">
          <label className="lg:col-span-2">
            <span className="mb-1 block text-xs font-semibold text-muted-foreground">Date Range</span>
            <Select
              value={datePreset}
              onChange={(e) => setDatePreset(e.target.value as DatePreset)}
              aria-label="Date range selector"
            >
              <option value="7d">Last 7 days</option>
              <option value="30d">Last 30 days</option>
              <option value="quarter">Quarter</option>
              <option value="custom">Custom</option>
            </Select>
          </label>
          <label>
            <span className="mb-1 block text-xs font-semibold text-muted-foreground">Organization</span>
            <Select value={organization} onChange={(e) => setOrganization(e.target.value)} aria-label="Organization filter">
              <option>All</option>
              <option>Gully Core</option>
              <option>Gully Enterprise</option>
            </Select>
          </label>
          <label>
            <span className="mb-1 block text-xs font-semibold text-muted-foreground">Pipeline</span>
            <Select value={pipeline} onChange={(e) => setPipeline(e.target.value)} aria-label="Pipeline filter">
              <option>All</option>
              <option>Mid-Market</option>
              <option>Enterprise</option>
            </Select>
          </label>
          <label>
            <span className="mb-1 block text-xs font-semibold text-muted-foreground">Owner</span>
            <Select value={owner} onChange={(e) => setOwner(e.target.value)} aria-label="Owner filter">
              <option>All</option>
              <option>Ariana</option>
              <option>Bryan</option>
              <option>Naomi</option>
            </Select>
          </label>
          <div className="flex items-center gap-2">
            <DropdownMenu>
              <DropdownMenuTrigger asChild>
                <Button variant="outline" className="w-full justify-between" aria-label="Saved reports dropdown">
                  {savedReport}
                  <ChevronDown className="h-4 w-4" />
                </Button>
              </DropdownMenuTrigger>
              <DropdownMenuContent align="end">
                {savedReports.map((item) => (
                  <DropdownMenuItem key={item} onClick={() => setSavedReport(item)}>
                    {item}
                  </DropdownMenuItem>
                ))}
              </DropdownMenuContent>
            </DropdownMenu>
            <Button variant="outline" aria-label="Create report">
              <Plus className="h-4 w-4" />
              Create Report
            </Button>
          </div>
        </div>
      </div>
      <div className="col-span-12 flex flex-wrap items-center justify-end gap-2">
        <Button variant="outline" size="sm" aria-label="Open date controls">
          <CalendarRange className="h-4 w-4" />
          {datePreset === "custom" ? "Custom Date" : "Preset"}
        </Button>
        <DropdownMenu>
          <DropdownMenuTrigger asChild>
            <Button size="sm" aria-label="Export report">
              <Download className="h-4 w-4" />
              Export
            </Button>
          </DropdownMenuTrigger>
          <DropdownMenuContent align="end">
            <DropdownMenuItem>Export CSV (Coming soon)</DropdownMenuItem>
            <DropdownMenuItem>Export PDF (Coming soon)</DropdownMenuItem>
          </DropdownMenuContent>
        </DropdownMenu>
      </div>
    </div>
  );
}
