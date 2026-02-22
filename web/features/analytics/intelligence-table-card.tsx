"use client";

import { Eye, Search } from "lucide-react";
import { useMemo, useRef, useState } from "react";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { cn } from "@/lib/utils";

type Column<T> = {
  key: keyof T;
  label: string;
  sortable?: boolean;
  render?: (row: T) => React.ReactNode;
};

type Props<T extends Record<string, unknown>> = {
  title: string;
  rows: T[];
  columns: Column<T>[];
  rowHeight?: number;
  statusKey?: keyof T;
  onViewRow?: (row: T) => void;
};

export function IntelligenceTableCard<T extends Record<string, unknown>>({
  title,
  rows,
  columns,
  rowHeight = 48,
  statusKey,
  onViewRow
}: Props<T>) {
  const [query, setQuery] = useState("");
  const [sortKey, setSortKey] = useState<keyof T | null>(null);
  const [sortDir, setSortDir] = useState<"asc" | "desc">("asc");
  const [scrollTop, setScrollTop] = useState(0);
  const viewportRef = useRef<HTMLDivElement | null>(null);

  const filtered = useMemo(() => {
    const q = query.toLowerCase();
    const searched = rows.filter((row) => JSON.stringify(row).toLowerCase().includes(q));
    if (!sortKey) return searched;
    return [...searched].sort((a, b) => {
      const av = String(a[sortKey] ?? "");
      const bv = String(b[sortKey] ?? "");
      const cmp = av.localeCompare(bv, undefined, { numeric: true });
      return sortDir === "asc" ? cmp : -cmp;
    });
  }, [query, rows, sortDir, sortKey]);

  const viewportHeight = 288;
  const start = Math.max(0, Math.floor(scrollTop / rowHeight) - 4);
  const visibleCount = Math.ceil(viewportHeight / rowHeight) + 8;
  const end = Math.min(filtered.length, start + visibleCount);
  const visible = filtered.slice(start, end);
  const offset = start * rowHeight;
  const totalHeight = filtered.length * rowHeight;

  return (
    <Card className="h-full border-border/80 bg-card">
      <CardHeader className="items-center gap-2">
        <CardTitle>{title}</CardTitle>
        <label className="relative w-full max-w-56">
          <Search className="pointer-events-none absolute left-3 top-3 h-4 w-4 text-muted-foreground" />
          <Input
            aria-label={`Filter ${title}`}
            className="pl-9"
            placeholder="Filter rows"
            value={query}
            onChange={(e) => {
              setQuery(e.target.value);
              if (viewportRef.current) viewportRef.current.scrollTo({ top: 0 });
            }}
          />
        </label>
      </CardHeader>
      <CardContent>
        <div className="rounded-2xl border border-border/80">
          <Table>
            <TableHeader>
              <TableRow>
                {columns.map((col) => (
                  <TableHead key={String(col.key)}>
                    <button
                      type="button"
                      className={cn("text-left", col.sortable ? "hover:text-foreground" : "pointer-events-none")}
                      onClick={() => {
                        if (!col.sortable) return;
                        if (sortKey === col.key) setSortDir((v) => (v === "asc" ? "desc" : "asc"));
                        else {
                          setSortKey(col.key);
                          setSortDir("asc");
                        }
                      }}
                      aria-label={`Sort by ${col.label}`}
                    >
                      {col.label}
                    </button>
                  </TableHead>
                ))}
                {onViewRow ? <TableHead className="text-right">Actions</TableHead> : null}
              </TableRow>
            </TableHeader>
          </Table>
          <div
            ref={viewportRef}
            className="scroll-thin max-h-[288px] overflow-y-auto"
            onScroll={(e) => setScrollTop((e.target as HTMLDivElement).scrollTop)}
          >
            <div style={{ height: totalHeight }}>
              <div style={{ transform: `translateY(${offset}px)` }}>
                <Table>
                  <TableBody>
                    {visible.length === 0 ? (
                      <TableRow>
                        <TableCell colSpan={columns.length + (onViewRow ? 1 : 0)} className="py-8 text-center text-muted-foreground">
                          No records found.
                        </TableCell>
                      </TableRow>
                    ) : (
                      visible.map((row, idx) => (
                        <TableRow key={idx} style={{ height: rowHeight }}>
                          {columns.map((col) => (
                            <TableCell key={String(col.key)}>
                              {col.render
                                ? col.render(row)
                                : statusKey && col.key === statusKey
                                  ? statusBadge(String(row[col.key]))
                                  : String(row[col.key] ?? "-")}
                            </TableCell>
                          ))}
                          {onViewRow ? (
                            <TableCell className="text-right">
                              <Button variant="ghost" size="sm" aria-label="Open row details" onClick={() => onViewRow(row)}>
                                <Eye className="h-4 w-4" />
                              </Button>
                            </TableCell>
                          ) : null}
                        </TableRow>
                      ))
                    )}
                  </TableBody>
                </Table>
              </div>
            </div>
          </div>
        </div>
      </CardContent>
    </Card>
  );
}

function statusBadge(value: string) {
  if (value === "excellent" || value === "on-track") return <Badge variant="success">{value}</Badge>;
  if (value === "warning") return <Badge variant="warning">{value}</Badge>;
  if (value === "risk" || value === "stalled") return <Badge variant="danger">{value}</Badge>;
  return <Badge>{value}</Badge>;
}
