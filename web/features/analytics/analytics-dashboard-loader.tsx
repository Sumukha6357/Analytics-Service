"use client";

import dynamic from "next/dynamic";
import { ExecutiveKpiSkeleton } from "@/features/analytics/loading-skeletons";

const AnalyticsDashboard = dynamic(() => import("@/features/analytics/analytics-dashboard"), {
  ssr: false,
  loading: () => <ExecutiveKpiSkeleton />
});

export default function AnalyticsDashboardLoader() {
  return <AnalyticsDashboard />;
}
