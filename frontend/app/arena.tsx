"use client";
import { useEffect, useRef } from "react";

export type Sinal = { camadas: number[][]; acao: number };
export type Quadro = { bola: [number, number]; raquetes: [number, number]; pontos: [number, number]; passos: number; sinais: [Sinal | null, Sinal | null] };
export type Cena = {
  nomes: [string, string]; resultado: string;
  arena: { largura: number; altura: number; topo: number; fundo: number };
  bola_tamanho: number; raquetes: { y: number; largura: number; altura: number }[];
};
export type Partida = Cena & { quadros: Quadro[]; ids: string[] };

export function Arena({ cena, quadro, mini = false, intervalo = 66 }: {
  cena?: Cena; quadro?: Quadro; mini?: boolean; intervalo?: number;
}) {
  const ref = useRef<HTMLCanvasElement>(null);
  const anterior = useRef<Quadro | undefined>(undefined);
  const { largura, altura, topo, fundo } = cena?.arena ?? { largura: 1120, altura: 1080, topo: 50, fundo: 1030 };
  useEffect(() => {
    const g = ref.current?.getContext("2d");
    if (!g) return;
    if (!cena || !quadro) {
      anterior.current = undefined;
      g.clearRect(0, 0, largura, altura);
      return;
    }
    const contexto = g, atual = quadro, geometria = cena;
    const ultimo = anterior.current;
    anterior.current = quadro;
    const continuo = ultimo && atual.passos > ultimo.passos && atual.passos - ultimo.passos <= 48 &&
      atual.pontos.every((p, i) => p === ultimo.pontos[i]);
    const origem = continuo ? ultimo : atual;
    const inicio = performance.now();
    let frame = 0;
    function desenhar(agora: number) {
      const t = mini || !continuo ? 1 : Math.min(1, (agora - inicio) / intervalo);
      const mover = (de: number, para: number) => de + (para - de) * t;
      contexto.clearRect(0, 0, largura, altura);
      geometria.raquetes.forEach((r, i) => {
        contexto.fillStyle = i === 0 ? "#76e3f4" : "#ffc07d";
        contexto.beginPath();
        contexto.roundRect(mover(origem.raquetes[i], atual.raquetes[i]), r.y, r.largura, r.altura, 5);
        contexto.fill();
      });
      const raio = geometria.bola_tamanho / 2;
      contexto.fillStyle = "#fff7df";
      contexto.beginPath();
      contexto.arc(mover(origem.bola[0], atual.bola[0]) + raio,
        mover(origem.bola[1], atual.bola[1]) + raio, raio, 0, Math.PI * 2);
      contexto.fill();
      if (t < 1) frame = requestAnimationFrame(desenhar);
    }
    desenhar(inicio);
    return () => cancelAnimationFrame(frame);
  }, [cena, quadro, largura, altura, mini, intervalo]);

  return <div className={mini ? "arena mini" : "arena"}>
    <div className="placar"><span>{cena?.nomes[0] ?? "Rede A (embaixo)"}</span><strong>{quadro?.pontos[0] ?? 0} <i>:</i> {quadro?.pontos[1] ?? 0}</strong><span>{cena?.nomes[1] ?? "Rede B (em cima)"}</span></div>
    <div className="quadra" style={{ "--linha-topo": (topo / altura * 100) + "%", "--linha-fundo": ((altura - fundo) / altura * 100) + "%" } as React.CSSProperties}><canvas ref={ref} width={largura} height={altura} role="img" aria-label="Arena. A joga embaixo e B joga em cima." /></div>
  </div>;
}

const titulos = ["Sensores", "Oculta 1", "Oculta 2", "Ações"];
const rotulos = ["Bola X", "Bola Y", "Vel. X", "Vel. Y", "Raquete", "Adversária", "Bola relativa", "Chegada X", "Tempo"];
const acoes = ["Esquerda", "Parar", "Direita"];

export function Neuronios({ sinal }: { sinal?: Sinal | null }) {
  const ref = useRef<HTMLCanvasElement>(null);
  useEffect(() => {
    const g = ref.current?.getContext("2d");
    if (!g) return;
    g.clearRect(0, 0, 470, 540);
    const xs = [90, 184, 278, 369];
    const camadas = sinal?.camadas ?? [Array(9).fill(0), Array(20).fill(0), Array(12).fill(0), Array(3).fill(0)];
    const pos = camadas.map((lista, i) => lista.map((_, j) => ({
      x: xs[i], y: 44 + (j + 0.5) * 470 / lista.length,
    })));
    g.lineWidth = 1;
    for (let i = 0; i < pos.length - 1; i++) {
      for (const [j, de] of pos[i].entries()) {
        g.strokeStyle = sinal && Math.abs(camadas[i][j]) > 0.35 ? "#36566a" : "#243044";
        g.beginPath();
        for (const para of pos[i + 1]) {
          g.moveTo(de.x, de.y);
          g.lineTo(para.x, para.y);
        }
        g.stroke();
      }
    }
    g.font = "13px system-ui";
    g.textBaseline = "middle";
    for (const [i, lista] of pos.entries()) {
      g.fillStyle = "#b8c8da"; g.textAlign = "center"; g.fillText(titulos[i], xs[i], 18);
      for (const [j, ponto] of lista.entries()) {
        const valor = camadas[i][j];
        const escolhida = i === 3 && sinal?.acao === j - 1;
        const intensidade = Math.min(1, Math.abs(valor));
        const cor = escolhida ? "#fff2ae" : valor < 0 ? "#ffad79" : "#4dfbd4";
        g.fillStyle = "#27384d";
        g.beginPath(); g.arc(ponto.x, ponto.y, 10, 0, Math.PI * 2); g.fill();
        g.globalAlpha = escolhida ? 1 : sinal ? 0.15 + intensidade * 0.85 : 0;
        g.fillStyle = cor;
        g.beginPath(); g.arc(ponto.x, ponto.y, escolhida ? 12 : 10, 0, Math.PI * 2); g.fill();
        g.globalAlpha = 1;
        g.fillStyle = "#b8c8da";
        if (i === 0) { g.textAlign = "right"; g.fillText(rotulos[j], ponto.x - 12, ponto.y); }
        if (i === 3) { g.textAlign = "left"; g.fillText(acoes[j], ponto.x + 14, ponto.y); }
      }
    }
  }, [sinal]);
  return <canvas ref={ref} width={470} height={540} role="img" aria-label="Ativações dos sensores, das duas camadas ocultas e das três ações. Ciano positivo, laranja negativo e amarelo para ação escolhida." />;
}
