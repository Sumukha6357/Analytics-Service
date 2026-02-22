import { Card, CardContent } from "@/components/ui/card";

export function SectionLabel({ title, subtitle }: { title: string; subtitle: string }) {
  return (
    <Card className="border-border/60 bg-transparent shadow-none">
      <CardContent className="px-0 py-0">
        <h2 className="text-sm font-semibold uppercase tracking-[0.12em] text-muted-foreground">{title}</h2>
        <p className="mt-1 text-lg font-semibold text-foreground">{subtitle}</p>
      </CardContent>
    </Card>
  );
}
