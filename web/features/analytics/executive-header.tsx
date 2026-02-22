"use client";

import { Building2, CalendarDays, Download, Moon, Share2, Sun } from "lucide-react";
import { useEffect, useState } from "react";
import { Button } from "@/components/ui/button";
import { DropdownMenu, DropdownMenuContent, DropdownMenuItem, DropdownMenuTrigger } from "@/components/ui/dropdown-menu";
import { Select } from "@/components/ui/select";
import { SAVED_REPORTS } from "@/features/analytics/data";
import type { DatePreset } from "@/features/analytics/types";

type Props = {
  org: string;
  orgOptions: string[];
  onOrgChange: (value: string) => void;
  datePreset: DatePreset;
  onDatePresetChange: (value: DatePreset) => void;
  owner: string;
  ownerOptions: string[];
  onOwnerChange: (value: string) => void;
  pipeline: string;
  pipelineOptions: string[];
  onPipelineChange: (value: string) => void;
  segment: string;
  segmentOptions: string[];
  onSegmentChange: (value: string) => void;
  onExport: (format: "csv" | "pdf") => void;
  onShare: () => void;
  onCreateReport: () => void;
  onApplySavedReport: (name: string) => void;
};

export function ExecutiveHeader({
  org,
  orgOptions,
  onOrgChange,
  datePreset,
  onDatePresetChange,
  owner,
  ownerOptions,
  onOwnerChange,
  pipeline,
  pipelineOptions,
  onPipelineChange,
  segment,
  segmentOptions,
  onSegmentChange,
  onExport,
  onShare,
  onCreateReport,
  onApplySavedReport
}: Props) {
  const [dark, setDark] = useState(true);

  useEffect(() => {
    const root = document.documentElement;
    root.classList.toggle("light", !dark);
    root.classList.toggle("dark", dark);
  }, [dark]);

  return (
    <header className="space-y-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h1 className="text-2xl font-semibold tracking-tight md:text-3xl">Executive Intelligence</h1>
          <p className="mt-1 text-sm text-muted-foreground">Current organization: <span className="font-semibold text-foreground">{org}</span></p>
        </div>
        <div className="flex items-center gap-2">
          <DropdownMenu>
            <DropdownMenuTrigger asChild>
              <Button variant="outline" size="sm" aria-label="Saved reports">
                Saved Reports
              </Button>
            </DropdownMenuTrigger>
            <DropdownMenuContent align="end">
              {SAVED_REPORTS.map((report) => (
                <DropdownMenuItem key={report} onClick={() => onApplySavedReport(report)}>
                  {report}
                </DropdownMenuItem>
              ))}
            </DropdownMenuContent>
          </DropdownMenu>
          <Button variant="outline" size="sm" aria-label="Create report" onClick={onCreateReport}>
            Create Report
          </Button>
          <Button variant="outline" size="sm" aria-label="Share report" onClick={onShare}>
            <Share2 className="h-4 w-4" />
            Share Report
          </Button>
          <DropdownMenu>
            <DropdownMenuTrigger asChild>
              <Button size="sm" aria-label="Export report">
                <Download className="h-4 w-4" />
                Export
              </Button>
            </DropdownMenuTrigger>
            <DropdownMenuContent align="end">
              <DropdownMenuItem onClick={() => onExport("csv")}>Export CSV</DropdownMenuItem>
              <DropdownMenuItem onClick={() => onExport("pdf")}>Export PDF</DropdownMenuItem>
            </DropdownMenuContent>
          </DropdownMenu>
          <Button variant="ghost" size="sm" aria-label="Toggle dark mode" onClick={() => setDark((v) => !v)}>
            {dark ? <Sun className="h-4 w-4" /> : <Moon className="h-4 w-4" />}
          </Button>
        </div>
      </div>

      <div className="grid grid-cols-1 gap-2 md:grid-cols-6">
        <label className="space-y-1">
          <span className="text-xs font-semibold text-muted-foreground">Organization</span>
          <div className="relative">
            <Building2 className="pointer-events-none absolute left-3 top-3 h-4 w-4 text-muted-foreground" />
            <Select className="pl-9" value={org} onChange={(e) => onOrgChange(e.target.value)} aria-label="Organization selector">
              {orgOptions.map((option) => (
                <option key={option}>{option}</option>
              ))}
            </Select>
          </div>
        </label>
        <label className="space-y-1">
          <span className="text-xs font-semibold text-muted-foreground">Date Range</span>
          <div className="relative">
            <CalendarDays className="pointer-events-none absolute left-3 top-3 h-4 w-4 text-muted-foreground" />
            <Select className="pl-9" value={datePreset} onChange={(e) => onDatePresetChange(e.target.value as DatePreset)} aria-label="Date range selector">
              <option value="7d">Last 7 days</option>
              <option value="30d">Last 30 days</option>
              <option value="quarter">Quarter</option>
              <option value="custom">Custom</option>
            </Select>
          </div>
        </label>
        <label className="space-y-1">
          <span className="text-xs font-semibold text-muted-foreground">Owner</span>
          <Select value={owner} onChange={(e) => onOwnerChange(e.target.value)} aria-label="Owner filter">
            <option value="All">All</option>
            {ownerOptions.map((option) => (
              <option key={option}>{option}</option>
            ))}
          </Select>
        </label>
        <label className="space-y-1">
          <span className="text-xs font-semibold text-muted-foreground">Pipeline</span>
          <Select value={pipeline} onChange={(e) => onPipelineChange(e.target.value)} aria-label="Pipeline filter">
            <option value="All">All</option>
            {pipelineOptions.map((option) => (
              <option key={option}>{option}</option>
            ))}
          </Select>
        </label>
        <label className="space-y-1 md:col-span-2">
          <span className="text-xs font-semibold text-muted-foreground">Segment</span>
          <Select value={segment} onChange={(e) => onSegmentChange(e.target.value)} aria-label="Segment filter">
            <option value="All">All</option>
            {segmentOptions.map((option) => (
              <option key={option}>{option}</option>
            ))}
          </Select>
        </label>
      </div>
    </header>
  );
}
