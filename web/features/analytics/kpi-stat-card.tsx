import { TrendingDown, TrendingUp } from "lucide-react";
import { memo } from "react";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { cn } from "@/lib/utils";

type KPIStatCardProps = {
  title: string;
  value: string;
  change: number;
  subtitle?: string;
  sparkline: number[];
};

function Sparkline({ values, positive }: { values: number[]; positive: boolean }) {
  const min = Math.min(...values);
  const max = Math.max(...values);
  const span = max - min || 1;
  const points = values
    .map((v, i) => `${(i / (values.length - 1)) * 100},${100 - ((v - min) / span) * 100}`)
    .join(" ");

  return (
    <svg viewBox="0 0 100 40" className="h-10 w-24" aria-hidden>
      <polyline
        fill="none"
        points={points.replaceAll(",", " ")}
        stroke={positive ? "hsl(var(--success))" : "hsl(var(--danger))"}
        strokeWidth="2"
        strokeLinecap="round"
      />
    </svg>
  );
}

export const KPIStatCard = memo(function KPIStatCard({ title, value, change, subtitle, sparkline }: KPIStatCardProps) {
  const isPositive = change >= 0;
  return (
    <Card className="animate-fade-in">
      <CardHeader className="pb-2">
        <div>
          <CardTitle>{title}</CardTitle>
          {subtitle ? <CardDescription>{subtitle}</CardDescription> : null}
        </div>
        <Sparkline values={sparkline} positive={isPositive} />
      </CardHeader>
      <CardContent className="flex items-end justify-between gap-4">
        <div className="text-3xl font-bold tracking-tight">{value}</div>
        <div
          className={cn(
            "inline-flex items-center gap-1 rounded-full px-2 py-1 text-xs font-semibold",
            isPositive ? "bg-success/10 text-success" : "bg-danger/10 text-danger"
          )}
          aria-label={`${title} ${isPositive ? "up" : "down"} ${Math.abs(change)} percent`}
        >
          {isPositive ? <TrendingUp className="h-3.5 w-3.5" /> : <TrendingDown className="h-3.5 w-3.5" />}
          {Math.abs(change)}%
        </div>
      </CardContent>
    </Card>
  );
});
