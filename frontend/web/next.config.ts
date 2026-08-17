import type { NextConfig } from "next";

const controlPlaneUrl = process.env.CONTROL_PLANE_URL || "http://localhost:8080";

const nextConfig: NextConfig = {
  output: "standalone",
  async rewrites() {
    return [
      {
        source: "/api/:path*",
        destination: `${controlPlaneUrl}/api/:path*`,
      },
    ];
  },
};

export default nextConfig;
