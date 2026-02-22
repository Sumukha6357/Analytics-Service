import { useQuery } from "@tanstack/react-query";
import type { DashboardResponse, DealRow, RepPerformanceRow, RiskAlert } from "@/features/analytics/types";

export type AnalyticsRole = "admin" | "manager" | "rep";

export type AnalyticsFilters = {
  org: string;
  from: string;
  to: string;
  owner: string;
  pipeline: string;
  segment: string;
  role: AnalyticsRole;
  actor: string;
};

type Paged<T> = {
  items: T[];
  total: number;
};

async function fetchJson<T>(url: string, init?: RequestInit): Promise<T> {
  const res = await fetch(url, init);
  if (!res.ok) {
    const text = await res.text();
    throw new Error(text || `Request failed: ${res.status}`);
  }
  return res.json() as Promise<T>;
}

function params(filters: AnalyticsFilters, extra?: Record<string, string>) {
  return new URLSearchParams({
    org: filters.org,
    from: filters.from,
    to: filters.to,
    owner: filters.owner,
    pipeline: filters.pipeline,
    segment: filters.segment,
    role: filters.role,
    actor: filters.actor,
    ...(extra ?? {})
  });
}

export function useExecutiveDashboard(filters: AnalyticsFilters) {
  return useQuery({
    queryKey: ["intelligence", "dashboard", filters],
    queryFn: () => fetchJson<DashboardResponse>(`/api/intelligence/dashboard?${params(filters).toString()}`),
    refetchInterval: 60_000
  });
}

export function useReps(filters: AnalyticsFilters, limit = 20, offset = 0) {
  return useQuery({
    queryKey: ["intelligence", "reps", filters, limit, offset],
    queryFn: () =>
      fetchJson<Paged<RepPerformanceRow>>(
        `/api/intelligence/reps?${params(filters, { limit: String(limit), offset: String(offset) }).toString()}`
      )
  });
}

export function useDeals(filters: AnalyticsFilters, kind: "high" | "stalled", limit = 20, offset = 0) {
  return useQuery({
    queryKey: ["intelligence", "deals", kind, filters, limit, offset],
    queryFn: () =>
      fetchJson<Paged<DealRow>>(
        `/api/intelligence/deals?${params(filters, { kind, limit: String(limit), offset: String(offset) }).toString()}`
      )
  });
}

export function useAlerts(filters: AnalyticsFilters) {
  return useQuery({
    queryKey: ["intelligence", "alerts", filters.org, filters.from, filters.to],
    queryFn: async () => {
      const data = await fetchJson<Array<Partial<RiskAlert> & { title?: string; description?: string }>>(
        `/api/intelligence/alerts?${new URLSearchParams({ org: filters.org, from: filters.from, to: filters.to }).toString()}`
      );
      return data.map((row, idx) => ({
        id: row.id ?? `alert-${idx}`,
        title: row.title ?? "Alert",
        description: row.description ?? "Details unavailable.",
        severity: row.severity ?? "info",
        actionLabel: row.actionLabel ?? "Review"
      }));
    }
  });
}

export function useSystemStatus(org: string) {
  return useQuery({
    queryKey: ["intelligence", "system-status", org],
    queryFn: () => fetchJson<Record<string, unknown>>(`/api/intelligence/system/status?${new URLSearchParams({ org })}`),
    refetchInterval: 30_000
  });
}

export async function createShare(org: string, payload: Record<string, unknown>) {
  return fetchJson<{ token: string; expiresAt: string }>(
    `/api/intelligence/share?${new URLSearchParams({ org, expiresMinutes: "120" }).toString()}`,
    { method: "POST", body: JSON.stringify(payload) }
  );
}

export async function createExport(filters: AnalyticsFilters, format: "csv" | "pdf") {
  return fetchJson<{ token: string; expiresAt: string }>(
    `/api/intelligence/export?${params(filters, { format }).toString()}`,
    { method: "POST", body: "{}" }
  );
}

export function downloadExportUrl(org: string, token: string) {
  return `/api/intelligence/export/${token}?${new URLSearchParams({ org }).toString()}`;
}
