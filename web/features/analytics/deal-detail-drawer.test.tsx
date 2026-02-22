import { render, screen } from "@testing-library/react";
import { DealDetailDrawer } from "@/features/analytics/deal-detail-drawer";

describe("DealDetailDrawer", () => {
  it("shows selected deal details", () => {
    render(
      <DealDetailDrawer
        open
        onOpenChange={() => {}}
        deal={{
          id: "d-1",
          name: "Northwind Expansion",
          owner: "Ariana",
          segment: "Fintech",
          pipeline: "Enterprise",
          value: "$440K",
          stage: "Negotiation",
          idleDays: 5,
          closeDate: "2026-03-01",
          status: "on-track"
        }}
      />
    );

    expect(screen.getByText("Northwind Expansion")).toBeInTheDocument();
    expect(screen.getByText("Owner: Ariana")).toBeInTheDocument();
    expect(screen.getByText("$440K")).toBeInTheDocument();
  });
});
