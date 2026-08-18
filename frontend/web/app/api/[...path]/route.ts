import { NextRequest, NextResponse } from "next/server";

const getTargetUrl = () => {
  const base =
    process.env.CONTROL_PLANE_URL ||
    (process.env.NODE_ENV === "production"
      ? "http://control-plane:8080"
      : "http://localhost:8080");
  return base.replace(/\/$/, "");
};

async function handleProxy(
  req: NextRequest,
  { params }: { params: Promise<{ path: string[] }> }
) {
  const { path } = await params;
  const targetBase = getTargetUrl();
  const searchParams = req.nextUrl.search;
  const targetUrl = `${targetBase}/api/${path.join("/")}${searchParams}`;

  const headers = new Headers(req.headers);
  headers.delete("host");

  const body = ["GET", "HEAD"].includes(req.method)
    ? undefined
    : await req.arrayBuffer();

  try {
    const res = await fetch(targetUrl, {
      method: req.method,
      headers,
      body,
      redirect: "manual",
      cache: "no-store",
    });

    const resHeaders = new Headers(res.headers);
    return new NextResponse(res.body, {
      status: res.status,
      statusText: res.statusText,
      headers: resHeaders,
    });
  } catch (err: unknown) {
    const errorMessage =
      err instanceof Error ? err.message : "Proxy connection failure";
    return NextResponse.json(
      {
        title: "Proxy Error",
        status: 502,
        detail: `Failed to communicate with control plane at ${targetUrl}: ${errorMessage}`,
        code: "CONTROL_PLANE_UNAVAILABLE",
      },
      { status: 502 }
    );
  }
}

export const GET = handleProxy;
export const POST = handleProxy;
export const PUT = handleProxy;
export const PATCH = handleProxy;
export const DELETE = handleProxy;
