import { render } from "@testing-library/react";
import type { ReactNode } from "react";
import { RevenueTrendChart } from "@/features/analytics/charts/revenue-trend-chart";

vi.mock("recharts", () => ({
  ResponsiveContainer: ({ children }: { children: ReactNode }) => <div data-testid="rc">{children}</div>,
  LineChart: ({ children }: { children: ReactNode }) => <svg>{children}</svg>,
  Line: () => <path />,
  XAxis: () => null,
  YAxis: () => null,
  Tooltip: () => null
}));

describe("RevenueTrendChart", () => {
  it("renders chart container", () => {
    const { container } = render(<RevenueTrendChart data={[{ date: "2026-02-20", revenue: 1200 }]} />);
    expect(container.querySelector("svg")).toBeTruthy();
  });
});
