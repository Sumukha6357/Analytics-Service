import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import type { ReactNode } from "react";
import AnalyticsDashboard from "@/features/analytics/analytics-dashboard";

const useExecutiveDashboardMock = vi.fn((filters: Record<string, string>) => ({
  isPending: false,
  isFetching: false,
  data: {
    strategicKpis: [{ id: "total-revenue", title: "Total Revenue", value: "$1,000", delta: 3.1 }],
    revenueTrend: [{ date: filters.from.slice(0, 10), revenue: 1000, target: 900, mrr: 50, arr: 600 }],
    stageDistribution: [{ stage: "Qualified", value: 22 }],
    funnel: [{ step: "Leads", value: 10 }],
    winLoss: [{ name: "Won", value: 60 }],
    agingMatrix: [{ stage: "Qualified", bucket: "0-14d", count: 3, risk: "low" }],
    forecastConfidence: 78,
    insights: [{ title: "No anomalies", description: "Stable", severity: "info" }],
    meta: {}
  }
}));

vi.mock("next/dynamic", () => ({
  default: () => () => <div data-testid="dynamic-chart" />
}));

vi.mock("@/components/layout/app-shell", () => ({
  AppShell: ({ children }: { children: ReactNode }) => <div>{children}</div>
}));

vi.mock("@/features/analytics/api-client", () => ({
  useExecutiveDashboard: (filters: Record<string, string>) => useExecutiveDashboardMock(filters),
  useReps: () => ({ isPending: false, data: { items: [], total: 0 } }),
  useDeals: () => ({ isPending: false, data: { items: [], total: 0 } }),
  useAlerts: () => ({ isPending: false, data: [] }),
  useSystemStatus: () => ({ isPending: false, data: {} }),
  createExport: vi.fn(async () => ({ token: "t1", expiresAt: "2026-03-01T00:00:00Z" })),
  createShare: vi.fn(async () => ({ token: "s1", expiresAt: "2026-03-01T00:00:00Z" })),
  downloadExportUrl: () => "/api/intelligence/export/t1?org=Primary%20Org"
}));

describe("AnalyticsDashboard integration", () => {
  it("refetches dashboard when date filter changes", async () => {
    render(<AnalyticsDashboard />);
    expect(screen.getByText("Executive Intelligence")).toBeInTheDocument();
    expect(useExecutiveDashboardMock).toHaveBeenCalledTimes(1);

    fireEvent.change(screen.getByLabelText("Date range selector"), { target: { value: "7d" } });

    await waitFor(() => {
      expect(useExecutiveDashboardMock.mock.calls.length).toBeGreaterThan(1);
    });
  });
});
