import type { NextConfig } from "next";

const config: NextConfig = {
  devIndicators: false,
  agentRules: false,
  async rewrites() {
    return [{ source: "/partida", destination: "http://127.0.0.1:8006/partida" }];
  },
};

export default config;
