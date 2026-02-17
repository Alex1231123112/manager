import { NextRequest, NextResponse } from "next/server";

const getBackendUrl = () =>
  process.env.API_BACKEND_URL || process.env.NEXT_PUBLIC_API_URL || "http://localhost:8095";

export async function GET(
  request: NextRequest,
  { params }: { params: Promise<{ path?: string[] }> }
) {
  return proxy(request, await params, "GET");
}

export async function POST(
  request: NextRequest,
  { params }: { params: Promise<{ path?: string[] }> }
) {
  return proxy(request, await params, "POST");
}

export async function PUT(
  request: NextRequest,
  { params }: { params: Promise<{ path?: string[] }> }
) {
  return proxy(request, await params, "PUT");
}

export async function PATCH(
  request: NextRequest,
  { params }: { params: Promise<{ path?: string[] }> }
) {
  return proxy(request, await params, "PATCH");
}

export async function DELETE(
  request: NextRequest,
  { params }: { params: Promise<{ path?: string[] }> }
) {
  return proxy(request, await params, "DELETE");
}

async function proxy(
  request: NextRequest,
  params: { path?: string[] },
  method: string
) {
  const path = params.path ?? [];
  const backendBase = getBackendUrl().replace(/\/$/, "");
  const pathStr = path.length ? path.join("/") : "";
  const url = `${backendBase}/api/${pathStr}${request.nextUrl.search}`;

  const headers = new Headers();
  request.headers.forEach((value, key) => {
    const lower = key.toLowerCase();
    if (lower === "host" || lower === "connection") return;
    headers.set(key, value);
  });

  let body: string | undefined;
  if (method !== "GET" && method !== "HEAD") {
    try {
      body = await request.text();
    } catch {
      // ignore
    }
  }

  const res = await fetch(url, {
    method,
    headers,
    body: body ?? undefined,
    cache: "no-store",
  });

  const resHeaders = new NextResponse(null, { status: res.status }).headers;
  res.headers.forEach((value, key) => {
    const lower = key.toLowerCase();
    if (lower === "transfer-encoding") return;
    resHeaders.set(key, value);
  });

  const responseBody = await res.arrayBuffer();
  return new NextResponse(responseBody, {
    status: res.status,
    statusText: res.statusText,
    headers: resHeaders,
  });
}
