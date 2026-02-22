import { NextRequest, NextResponse } from "next/server";

const BASE_URL = process.env.ANALYTICS_API_URL ?? "http://localhost:9100";
const DEFAULT_KEY = process.env.ANALYTICS_API_KEY ?? "";

function resolveKey(org: string | null) {
  if (!org) return DEFAULT_KEY;
  const mapRaw = process.env.ANALYTICS_ORG_KEY_MAP;
  if (!mapRaw) return DEFAULT_KEY;
  try {
    const parsed = JSON.parse(mapRaw) as Record<string, string>;
    return parsed[org] ?? DEFAULT_KEY;
  } catch {
    return DEFAULT_KEY;
  }
}

async function forward(req: NextRequest, method: "GET" | "POST") {
  const path = req.nextUrl.pathname.replace("/api/intelligence", "/v1/intelligence");
  const target = new URL(path, BASE_URL);
  req.nextUrl.searchParams.forEach((value, key) => {
    if (key !== "org") target.searchParams.set(key, value);
  });
  const key = resolveKey(req.nextUrl.searchParams.get("org"));
  if (!key) {
    return NextResponse.json({ message: "ANALYTICS_API_KEY not configured" }, { status: 500 });
  }

  const init: RequestInit = {
    method,
    headers: {
      "X-API-Key": key,
      "Content-Type": "application/json"
    },
    cache: "no-store"
  };

  if (method === "POST") {
    init.body = await req.text();
  }

  const res = await fetch(target.toString(), init);
  const contentType = res.headers.get("content-type") ?? "application/json";
  const bodyText = await res.text();
  return new NextResponse(bodyText, {
    status: res.status,
    headers: {
      "content-type": contentType,
      "cache-control": res.headers.get("cache-control") ?? "no-store"
    }
  });
}

export async function GET(req: NextRequest) {
  return forward(req, "GET");
}

export async function POST(req: NextRequest) {
  return forward(req, "POST");
}
