"use client";

import dynamic from "next/dynamic";
import { useEffect, useMemo, useRef, useState } from "react";
import { AppShell } from "@/components/layout/app-shell";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { ANALYTICS_FLAGS, DEFAULT_FILTER_OPTIONS } from "@/features/analytics/data";
import {
  createExport,
  createShare,
  downloadExportUrl,
  useAlerts,
  useDeals,
  useExecutiveDashboard,
  useReps,
  useSystemStatus
} from "@/features/analytics/api-client";
import { CommandPalette } from "@/features/analytics/command-palette";
import { DealDetailDrawer } from "@/features/analytics/deal-detail-drawer";
import { EmptyState } from "@/features/analytics/empty-state";
import { ExecutiveHeader } from "@/features/analytics/executive-header";
import { InsightCards } from "@/features/analytics/insight-card";
import { IntelligenceChartCard } from "@/features/analytics/intelligence-chart-card";
import { IntelligenceTableCard } from "@/features/analytics/intelligence-table-card";
import { ExecutiveKpiSkeleton, IntelligenceChartSkeleton } from "@/features/analytics/loading-skeletons";
import { PremiumKPI } from "@/features/analytics/premium-kpi";
import { RiskAlertPanel } from "@/features/analytics/risk-alert-panel";
import { SectionLabel } from "@/features/analytics/section-label";
import type { DatePreset, DealRow } from "@/features/analytics/types";

const RevenueTrendChart = dynamic(() => import("@/features/analytics/charts/revenue-trend-chart").then((m) => m.RevenueTrendChart), {
  ssr: false,
  loading: () => <IntelligenceChartSkeleton />
});
const RevenueVsTargetChart = dynamic(() => import("@/features/analytics/charts/revenue-vs-target-chart").then((m) => m.RevenueVsTargetChart), {
  ssr: false,
  loading: () => <IntelligenceChartSkeleton />
});
const GrowthChart = dynamic(() => import("@/features/analytics/charts/growth-chart").then((m) => m.GrowthChart), {
  ssr: false,
  loading: () => <IntelligenceChartSkeleton />
});
const ForecastConfidenceGauge = dynamic(
  () => import("@/features/analytics/charts/forecast-confidence-gauge").then((m) => m.ForecastConfidenceGauge),
  { ssr: false, loading: () => <IntelligenceChartSkeleton /> }
);
const PipelineFunnelChart = dynamic(() => import("@/features/analytics/charts/pipeline-funnel-chart").then((m) => m.PipelineFunnelChart), {
  ssr: false,
  loading: () => <IntelligenceChartSkeleton />
});
const StageDistributionChart = dynamic(
  () => import("@/features/analytics/charts/stage-distribution-chart").then((m) => m.StageDistributionChart),
  { ssr: false, loading: () => <IntelligenceChartSkeleton /> }
);
const DealAgingHeatmap = dynamic(() => import("@/features/analytics/charts/deal-aging-heatmap").then((m) => m.DealAgingHeatmap), {
  ssr: false,
  loading: () => <IntelligenceChartSkeleton />
});
const WinLossRatioChart = dynamic(() => import("@/features/analytics/charts/win-loss-ratio-chart").then((m) => m.WinLossRatioChart), {
  ssr: false,
  loading: () => <IntelligenceChartSkeleton />
});

const ORG_OPTIONS = (process.env.NEXT_PUBLIC_ANALYTICS_ORGS ?? DEFAULT_FILTER_OPTIONS.organizations.join(","))
  .split(",")
  .map((v) => v.trim())
  .filter(Boolean);

export default function AnalyticsDashboard() {
  const [orgName, setOrgName] = useState(ORG_OPTIONS[0] ?? "Primary Org");
  const [datePreset, setDatePreset] = useState<DatePreset>("30d");
  const [owner, setOwner] = useState("All");
  const [pipeline, setPipeline] = useState("All");
  const [segment, setSegment] = useState("All");
  const [role, setRole] = useState<"admin" | "manager" | "rep">("admin");
  const [actor, setActor] = useState("");
  const [activeDeal, setActiveDeal] = useState<DealRow | null>(null);
  const [reportMessage, setReportMessage] = useState<string>("");

  const { from, to } = useMemo(() => rangeForPreset(datePreset), [datePreset]);
  const filters = useMemo(
    () => ({ org: orgName, from, to, owner, pipeline, segment, role, actor }),
    [orgName, from, to, owner, pipeline, segment, role, actor]
  );

  const dashboard = useExecutiveDashboard(filters);
  const reps = useReps(filters, 20, 0);
  const highDeals = useDeals(filters, "high", 20, 0);
  const stalledDeals = useDeals(filters, "stalled", 20, 0);
  const alerts = useAlerts(filters);
  const systemStatus = useSystemStatus(orgName);

  const ownerOptions = useMemo(
    () =>
      distinct([
        ...DEFAULT_FILTER_OPTIONS.owners,
        ...(reps.data?.items.map((r) => r.rep) ?? []),
        ...(highDeals.data?.items.map((d) => d.owner) ?? [])
      ]),
    [reps.data, highDeals.data]
  );
  const pipelineOptions = useMemo(
    () => distinct([...DEFAULT_FILTER_OPTIONS.pipelines, ...(highDeals.data?.items.map((d) => d.pipeline) ?? [])]),
    [highDeals.data]
  );
  const segmentOptions = useMemo(
    () => distinct([...DEFAULT_FILTER_OPTIONS.segments, ...(highDeals.data?.items.map((d) => d.segment) ?? [])]),
    [highDeals.data]
  );

  const topReps = reps.data?.items ?? [];
  const leaderboard = useMemo(() => [...topReps].sort((a, b) => String(b.revenue).localeCompare(String(a.revenue))), [topReps]);
  const highValueDeals = highDeals.data?.items ?? [];
  const stalledDealsRows = stalledDeals.data?.items ?? [];
  const stalledCount = stalledDealsRows.filter((deal) => deal.idleDays > 20).length;

  const kpis = (dashboard.data?.strategicKpis ?? []).map((kpi) => ({
    ...kpi,
    subLabel: "Period-over-period",
    sparkline: sparklineFromDelta(kpi.delta)
  }));

  const chartData = useMemo(
    () => (dashboard.data?.revenueTrend ?? []).map((row) => ({ ...row, date: String(row.date).slice(0, 10) })),
    [dashboard.data]
  );

  const loadingPrimary = dashboard.isPending || reps.isPending || highDeals.isPending || stalledDeals.isPending;
  const hasAnyData =
    kpis.length > 0 ||
    chartData.length > 0 ||
    topReps.length > 0 ||
    highValueDeals.length > 0 ||
    stalledDealsRows.length > 0;

  useAnalyticsPerf(dashboard.isFetching, filters);

  return (
    <AppShell>
      <div className="space-y-6">
        <ExecutiveHeader
          org={orgName}
          orgOptions={ORG_OPTIONS}
          onOrgChange={setOrgName}
          datePreset={datePreset}
          onDatePresetChange={setDatePreset}
          owner={owner}
          ownerOptions={ownerOptions}
          onOwnerChange={setOwner}
          pipeline={pipeline}
          pipelineOptions={pipelineOptions}
          onPipelineChange={setPipeline}
          segment={segment}
          segmentOptions={segmentOptions}
          onSegmentChange={setSegment}
          onExport={async (format) => {
            const res = await createExport(filters, format);
            setReportMessage(`Export ready until ${res.expiresAt}`);
            window.open(downloadExportUrl(filters.org, res.token), "_blank", "noopener,noreferrer");
          }}
          onShare={async () => {
            const res = await createShare(filters.org, { filters });
            const shareLink = `${window.location.origin}/analytics?share=${res.token}`;
            await navigator.clipboard.writeText(shareLink);
            setReportMessage(`Share link copied. Expires at ${res.expiresAt}`);
          }}
          onCreateReport={() => setReportMessage("Scheduled email report setup is available as a next backend phase.")}
          onApplySavedReport={(name) => setReportMessage(`Applied preset: ${name}`)}
        />

        <div className="flex flex-wrap items-center gap-2">
          <Badge>Role</Badge>
          <select
            aria-label="Role selector"
            className="rounded-md border border-border bg-card px-2 py-1 text-sm"
            value={role}
            onChange={(e) => setRole(e.target.value as "admin" | "manager" | "rep")}
          >
            <option value="admin">admin</option>
            <option value="manager">manager</option>
            <option value="rep">rep</option>
          </select>
          {role === "rep" ? (
            <input
              aria-label="Actor identifier"
              className="rounded-md border border-border bg-card px-2 py-1 text-sm"
              value={actor}
              onChange={(e) => setActor(e.target.value)}
              placeholder="rep id/name"
            />
          ) : null}
          {reportMessage ? <p className="text-xs text-muted-foreground">{reportMessage}</p> : null}
        </div>

        {ANALYTICS_FLAGS.insights && dashboard.data?.insights ? <InsightCards insights={dashboard.data.insights} /> : null}

        {!hasAnyData && !loadingPrimary ? (
          <EmptyState title="No data for current filter set" description="Try a wider date range or pipeline scope." />
        ) : null}

        <div className="enterprise-grid gap-4">
          <main className="col-span-12 space-y-6 xl:col-span-9">
            <section className="space-y-3">
              <SectionLabel title="Section 1" subtitle="Strategic KPIs" />
              {loadingPrimary ? (
                <ExecutiveKpiSkeleton />
              ) : (
                <div className="grid grid-cols-1 gap-4 md:grid-cols-2 xl:grid-cols-4">
                  {kpis.map((kpi) => (
                    <div id={`kpi-${kpi.id}`} key={kpi.id}>
                      <PremiumKPI title={kpi.title} value={kpi.value} delta={kpi.delta} subLabel={kpi.subLabel} sparkline={kpi.sparkline} />
                    </div>
                  ))}
                </div>
              )}
            </section>

            {ANALYTICS_FLAGS.revenueIntelligence ? (
              <section className="space-y-3">
                <SectionLabel title="Section 2" subtitle="Revenue Intelligence" />
                <div className="enterprise-grid gap-4">
                  <div className="col-span-12 lg:col-span-6">
                    <IntelligenceChartCard title="Revenue Trend" subtitle="Actual revenue over selected period">
                      <RevenueTrendChart data={chartData} />
                    </IntelligenceChartCard>
                  </div>
                  <div className="col-span-12 lg:col-span-6">
                    <IntelligenceChartCard title="Revenue vs Target" subtitle="Board plan comparison">
                      <RevenueVsTargetChart data={chartData} />
                    </IntelligenceChartCard>
                  </div>
                  <div className="col-span-12 lg:col-span-8">
                    <IntelligenceChartCard title="MRR / ARR Growth" subtitle="Recurring growth velocity">
                      <GrowthChart data={chartData} />
                    </IntelligenceChartCard>
                  </div>
                  <div className="col-span-12 lg:col-span-4">
                    <IntelligenceChartCard title="Forecast Confidence" subtitle="Model confidence score">
                      <ForecastConfidenceGauge value={dashboard.data?.forecastConfidence ?? 0} />
                    </IntelligenceChartCard>
                  </div>
                </div>
              </section>
            ) : null}

            {ANALYTICS_FLAGS.pipelineHealth ? (
              <section className="space-y-3">
                <SectionLabel title="Section 3" subtitle="Pipeline Health" />
                <div className="mb-2 flex flex-wrap items-center gap-2 text-xs text-muted-foreground">
                  <span>Risk Detection:</span>
                  <Badge variant="warning">Deals idle &gt; 20 days</Badge>
                  <Badge variant={stalledCount > 0 ? "danger" : "success"}>{stalledCount} stalled indicators</Badge>
                </div>
                <div className="enterprise-grid gap-4">
                  <div className="col-span-12 lg:col-span-6">
                    <IntelligenceChartCard title="Funnel Visualization" subtitle="Lead progression quality">
                      <PipelineFunnelChart data={dashboard.data?.funnel ?? []} />
                    </IntelligenceChartCard>
                  </div>
                  <div className="col-span-12 lg:col-span-6">
                    <IntelligenceChartCard title="Stage Distribution" subtitle="Deal value by stage">
                      <StageDistributionChart data={dashboard.data?.stageDistribution ?? []} />
                    </IntelligenceChartCard>
                  </div>
                  <div className="col-span-12 lg:col-span-7">
                    <IntelligenceChartCard title="Deal Aging Heatmap" subtitle="Aging pressure by stage">
                      <DealAgingHeatmap data={dashboard.data?.agingMatrix ?? []} />
                    </IntelligenceChartCard>
                  </div>
                  <div className="col-span-12 lg:col-span-5">
                    <IntelligenceChartCard title="Win/Loss Ratio" subtitle="Outcome quality">
                      <WinLossRatioChart data={dashboard.data?.winLoss ?? []} />
                    </IntelligenceChartCard>
                  </div>
                </div>
              </section>
            ) : null}

            {ANALYTICS_FLAGS.salesPerformance ? (
              <section className="space-y-3">
                <SectionLabel title="Section 4" subtitle="Sales Performance" />
                <div className="enterprise-grid gap-4">
                  <div className="col-span-12 lg:col-span-6">
                    <IntelligenceTableCard
                      title="Top Performing Reps"
                      rows={topReps}
                      statusKey="status"
                      columns={[
                        { key: "rep", label: "Rep", sortable: true },
                        { key: "deals", label: "Deals", sortable: true },
                        { key: "conversion", label: "Conversion", sortable: true },
                        { key: "revenue", label: "Revenue", sortable: true },
                        { key: "status", label: "Status", sortable: true }
                      ]}
                    />
                  </div>
                  {role !== "rep" ? (
                    <div className="col-span-12 lg:col-span-6">
                      <IntelligenceTableCard
                        title="Rep Leaderboard"
                        rows={leaderboard}
                        statusKey="status"
                        columns={[
                          { key: "rep", label: "Rep", sortable: true },
                          { key: "revenue", label: "Revenue", sortable: true },
                          { key: "conversion", label: "Conversion", sortable: true },
                          { key: "velocity", label: "Velocity", sortable: true },
                          { key: "status", label: "Status", sortable: true }
                        ]}
                      />
                    </div>
                  ) : null}
                  <div className="col-span-12 lg:col-span-6">
                    <IntelligenceTableCard
                      title="High Value Deals"
                      rows={highValueDeals}
                      statusKey="status"
                      onViewRow={(row) => setActiveDeal(row as DealRow)}
                      columns={[
                        { key: "name", label: "Deal", sortable: true },
                        { key: "owner", label: "Owner", sortable: true },
                        { key: "value", label: "Value", sortable: true },
                        { key: "pipeline", label: "Pipeline", sortable: true },
                        { key: "status", label: "Status", sortable: true }
                      ]}
                    />
                  </div>
                  <div className="col-span-12 lg:col-span-6">
                    <IntelligenceTableCard
                      title="Stalled Deals"
                      rows={stalledDealsRows}
                      statusKey="status"
                      onViewRow={(row) => setActiveDeal(row as DealRow)}
                      columns={[
                        { key: "name", label: "Deal", sortable: true },
                        { key: "owner", label: "Owner", sortable: true },
                        { key: "idleDays", label: "Idle Days", sortable: true },
                        { key: "closeDate", label: "Close", sortable: true },
                        { key: "status", label: "Status", sortable: true }
                      ]}
                    />
                  </div>
                </div>
              </section>
            ) : null}

            <section className="space-y-2 xl:hidden">
              <SectionLabel title="Section 5" subtitle="Risk & Alerts" />
              <RiskAlertPanel alerts={alerts.data ?? []} />
            </section>
          </main>

          <aside className="col-span-12 hidden xl:block xl:col-span-3">
            <SectionLabel title="Section 5" subtitle="Risk & Alerts" />
            <div className="mt-3 space-y-3">
              <RiskAlertPanel alerts={alerts.data ?? []} />
              <div className="rounded-2xl border border-border/80 bg-card p-3">
                <p className="text-xs font-semibold uppercase tracking-wide text-muted-foreground">System Status</p>
                <pre className="mt-2 overflow-x-auto text-[11px] text-muted-foreground">
                  {JSON.stringify(systemStatus.data ?? {}, null, 2)}
                </pre>
              </div>
            </div>
          </aside>
        </div>

        <div className="flex justify-end">
          <Button variant="ghost" size="sm" aria-label="Open command palette help">
            Keyboard quick jump: Ctrl/Cmd + K
          </Button>
        </div>
      </div>

      <CommandPalette
        onOpenSavedReports={() => setReportMessage("Saved reports menu available in header.")}
        onFocusKpi={(kpiId) => {
          document.getElementById(`kpi-${kpiId}`)?.scrollIntoView({ behavior: "smooth", block: "center" });
        }}
      />
      <DealDetailDrawer open={!!activeDeal} onOpenChange={(open) => !open && setActiveDeal(null)} deal={activeDeal} />
    </AppShell>
  );
}

function distinct(values: string[]) {
  return [...new Set(values)];
}

function sparklineFromDelta(delta: number) {
  const base = 60;
  return Array.from({ length: 7 }, (_, i) => Math.round(base + delta * (i - 3) * 1.5 + i * 1.8));
}

function rangeForPreset(preset: DatePreset) {
  const now = new Date();
  const to = now.toISOString();
  const fromDate = new Date(now);
  if (preset === "7d") fromDate.setDate(now.getDate() - 7);
  else if (preset === "30d") fromDate.setDate(now.getDate() - 30);
  else if (preset === "quarter") fromDate.setDate(now.getDate() - 90);
  else fromDate.setDate(now.getDate() - 14);
  return { from: fromDate.toISOString(), to };
}

function useAnalyticsPerf(isFetching: boolean, filters: Record<string, string>) {
  const loadStartRef = useRef<number>(0);
  const lastFilterRef = useRef<string>("");
  const loggedLoadRef = useRef(false);

  useEffect(() => {
    loadStartRef.current = performance.now();
    loggedLoadRef.current = false;
  }, []);

  useEffect(() => {
    const snapshot = JSON.stringify(filters);
    if (lastFilterRef.current && snapshot !== lastFilterRef.current) {
      const filterStart = performance.now();
      console.info("analytics.filter.change", {
        at: new Date().toISOString(),
        filters,
        latencyMs: Math.round(performance.now() - filterStart)
      });
    }
    lastFilterRef.current = snapshot;
  }, [filters]);

  useEffect(() => {
    if (!isFetching && !loggedLoadRef.current) {
      loggedLoadRef.current = true;
      console.info("analytics.dashboard.load", {
        durationMs: Math.round(performance.now() - loadStartRef.current)
      });
    }
  }, [isFetching]);
}
