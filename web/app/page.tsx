import Link from "next/link";

export default function HomePage() {
  return (
    <main className="flex min-h-screen items-center justify-center bg-background p-6">
      <Link
        href="/analytics"
        className="rounded-xl border bg-card px-6 py-4 text-sm font-semibold shadow-card transition-colors hover:bg-muted"
      >
        Open Analytics & Insights
      </Link>
    </main>
  );
}
