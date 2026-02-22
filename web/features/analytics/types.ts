export type DatePreset = "7d" | "30d" | "quarter" | "custom";

export type ExecutiveKPI = {
  id: string;
  title: string;
  value: string;
  delta: number;
  subLabel: string;
  sparkline: number[];
};

export type RevenueTrendPoint = { date: string; revenue: number; target?: number; mrr?: number; arr?: number };
export type StageDistributionPoint = { stage: string; value: number };
export type FunnelPoint = { step: string; value: number };
export type WinLossPoint = { name: string; value: number };
export type RevenuePoint = RevenueTrendPoint;
export type PipelinePoint = StageDistributionPoint;
export type LeadSourcePoint = { source: string; value: number };

export type AgingMatrixCell = {
  stage: string;
  bucket: string;
  count: number;
  risk: "low" | "medium" | "high";
};

export type RepPerformanceRow = {
  rep: string;
  deals: number;
  conversion: string;
  revenue: string;
  velocity: string;
  status: "excellent" | "stable" | "risk";
};

export type DealRow = {
  id: string;
  name: string;
  owner: string;
  segment: string;
  pipeline: string;
  value: string;
  stage: string;
  idleDays: number;
  closeDate: string;
  status: "on-track" | "warning" | "stalled";
};

export type RiskAlert = {
  id: string;
  title: string;
  description: string;
  severity: "high" | "medium" | "info";
  actionLabel?: string;
};

export type Insight = {
  title: string;
  description: string;
  severity: "high" | "medium" | "info";
};

export type DashboardResponse = {
  strategicKpis: Array<{
    id: string;
    title: string;
    value: string;
    delta: number;
  }>;
  revenueTrend: RevenueTrendPoint[];
  stageDistribution: StageDistributionPoint[];
  funnel: FunnelPoint[];
  winLoss: WinLossPoint[];
  agingMatrix: AgingMatrixCell[];
  forecastConfidence: number;
  insights: Insight[];
  meta: Record<string, unknown>;
};
