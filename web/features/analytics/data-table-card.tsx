"use client";

import { ChevronDown, ChevronUp, Search } from "lucide-react";
import { useMemo, useState } from "react";
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
  render?: (value: T[keyof T], row: T) => React.ReactNode;
};

type DataTableCardProps<T extends Record<string, unknown>> = {
  title: string;
  data: T[];
  columns: Column<T>[];
  statusKey?: keyof T;
};

export function DataTableCard<T extends Record<string, unknown>>({ title, data, columns, statusKey }: DataTableCardProps<T>) {
  const [query, setQuery] = useState("");
  const [sortKey, setSortKey] = useState<keyof T | null>(null);
  const [sortOrder, setSortOrder] = useState<"asc" | "desc">("asc");
  const [page, setPage] = useState(1);
  const pageSize = 4;

  const filtered = useMemo(() => {
    const searched = data.filter((row) => JSON.stringify(row).toLowerCase().includes(query.toLowerCase()));
    if (!sortKey) return searched;
    return [...searched].sort((a, b) => {
      const av = String(a[sortKey] ?? "");
      const bv = String(b[sortKey] ?? "");
      return sortOrder === "asc" ? av.localeCompare(bv, undefined, { numeric: true }) : bv.localeCompare(av, undefined, { numeric: true });
    });
  }, [data, query, sortKey, sortOrder]);

  const totalPages = Math.max(1, Math.ceil(filtered.length / pageSize));
  const paged = filtered.slice((page - 1) * pageSize, page * pageSize);

  function toggleSort(key: keyof T) {
    if (sortKey === key) {
      setSortOrder((v) => (v === "asc" ? "desc" : "asc"));
    } else {
      setSortKey(key);
      setSortOrder("asc");
    }
  }

  return (
    <Card className="animate-fade-in">
      <CardHeader className="gap-3 md:flex-row md:items-center md:justify-between">
        <CardTitle>{title}</CardTitle>
        <label className="relative w-full md:max-w-64">
          <Search className="pointer-events-none absolute left-3 top-3 h-4 w-4 text-muted-foreground" />
          <Input
            aria-label={`Filter ${title}`}
            className="pl-9"
            placeholder="Filter..."
            value={query}
            onChange={(e) => {
              setPage(1);
              setQuery(e.target.value);
            }}
          />
        </label>
      </CardHeader>
      <CardContent>
        <div className="max-h-[360px] overflow-auto rounded-xl border">
          <Table>
            <TableHeader>
              <TableRow>
                {columns.map((column) => (
                  <TableHead key={String(column.key)}>
                    <button
                      type="button"
                      aria-label={`Sort by ${column.label}`}
                      className={cn(
                        "inline-flex items-center gap-1",
                        column.sortable ? "hover:text-foreground" : "pointer-events-none"
                      )}
                      onClick={() => column.sortable && toggleSort(column.key)}
                    >
                      {column.label}
                      {sortKey === column.key ? (
                        sortOrder === "asc" ? <ChevronUp className="h-3.5 w-3.5" /> : <ChevronDown className="h-3.5 w-3.5" />
                      ) : null}
                    </button>
                  </TableHead>
                ))}
              </TableRow>
            </TableHeader>
            <TableBody>
              {paged.length === 0 ? (
                <TableRow>
                  <TableCell colSpan={columns.length} className="py-10 text-center text-muted-foreground">
                    No records found.
                  </TableCell>
                </TableRow>
              ) : (
                paged.map((row, idx) => (
                  <TableRow key={idx}>
                    {columns.map((column) => {
                      const value = row[column.key];
                      return (
                        <TableCell key={String(column.key)}>
                          {column.render
                            ? column.render(value, row)
                            : statusKey && column.key === statusKey
                              ? renderStatusBadge(String(value))
                              : String(value)}
                        </TableCell>
                      );
                    })}
                  </TableRow>
                ))
              )}
            </TableBody>
          </Table>
        </div>
        <div className="mt-3 flex items-center justify-between">
          <p className="text-xs text-muted-foreground">
            Page {page} of {totalPages}
          </p>
          <div className="flex items-center gap-2">
            <Button variant="outline" size="sm" onClick={() => setPage((p) => Math.max(1, p - 1))} disabled={page === 1}>
              Previous
            </Button>
            <Button variant="outline" size="sm" onClick={() => setPage((p) => Math.min(totalPages, p + 1))} disabled={page === totalPages}>
              Next
            </Button>
          </div>
        </div>
      </CardContent>
    </Card>
  );
}

function renderStatusBadge(status: string) {
  if (status === "rising" || status === "on-track") return <Badge variant="success">{status}</Badge>;
  if (status === "risk" || status === "stalled") return <Badge variant="danger">{status}</Badge>;
  if (status === "watch") return <Badge variant="warning">{status}</Badge>;
  return <Badge>{status}</Badge>;
}
