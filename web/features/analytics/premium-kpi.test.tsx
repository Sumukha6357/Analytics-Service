import { render, screen } from "@testing-library/react";
import { PremiumKPI } from "@/features/analytics/premium-kpi";

describe("PremiumKPI", () => {
  it("renders KPI content", () => {
    render(<PremiumKPI title="Total Revenue" value="$420,000" delta={8.2} subLabel="Quarter to date" sparkline={[1, 2, 3, 4]} />);
    expect(screen.getByText("Total Revenue")).toBeInTheDocument();
    expect(screen.getByText("$420,000")).toBeInTheDocument();
    expect(screen.getByLabelText("Total Revenue changed 8.2 percent")).toBeInTheDocument();
  });
});
