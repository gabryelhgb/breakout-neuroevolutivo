import type { NextConfig } from "next";

const config: NextConfig = {
  devIndicators: false,
  agentRules: false,
  async rewrites() {
    const backend = process.env.RAQUETES_API ?? "http://127.0.0.1:8006";
    return ["/partida", "/arenas", "/modelos", "/torneio", "/jogar/:acao", "/treino/:acao"].map(source => ({
      source, destination: backend + source,
    }));
  },
};

export default config;
