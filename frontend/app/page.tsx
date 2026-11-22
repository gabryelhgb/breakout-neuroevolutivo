"use client";

import { useEffect, useRef, useState } from "react";

type Quadro = { bola: [number, number]; raquetes: [number, number]; pontos: [number, number]; passos: number };
type Partida = {
  quadros: Quadro[]; nomes: [string, string]; resultado: string;
  arena: { largura: number; altura: number; topo: number; fundo: number };
  bola_tamanho: number; raquetes: { y: number; largura: number; altura: number }[];
};

export default function Home() {
  const [partida, setPartida] = useState<Partida>();
  const [indice, setIndice] = useState(0);
  const [reproduzindo, setReproduzindo] = useState(false);
  const [carregando, setCarregando] = useState(false);
  const [erro, setErro] = useState("");
  const [semente, setSemente] = useState("42");
  const canvas = useRef<HTMLCanvasElement>(null);
  const quadro = partida?.quadros[indice];
  const fim = Boolean(partida && indice === partida.quadros.length - 1);

  async function carregar(evento: React.FormEvent<HTMLFormElement>) {
    evento.preventDefault();
    setReproduzindo(false);
    setCarregando(true);
    setErro("");
    try {
      const resposta = await fetch("/partida?" + new URLSearchParams({ semente }), { cache: "no-store" });
      const dados = await resposta.json();
      if (!resposta.ok) throw new Error(dados.erro || "Inicie a API Python na porta 8006.");
      setPartida(dados);
      setIndice(0);
      setReproduzindo(true);
    } catch (causa) {
      setErro(causa instanceof SyntaxError || !(causa instanceof Error)
        ? "Não foi possível ler a partida. Confira a API Python." : causa.message);
    } finally {
      setCarregando(false);
    }
  }

  useEffect(() => {
    if (!partida || !reproduzindo || fim) return;
    const timer = window.setInterval(() => {
      setIndice(i => Math.min(i + 1, partida.quadros.length - 1));
    }, 1000 / 15);
    return () => window.clearInterval(timer);
  }, [partida, reproduzindo, fim]);

  useEffect(() => {
    const ctx = canvas.current?.getContext("2d");
    if (!ctx) return;
    const { largura, altura, topo, fundo } = partida?.arena ?? { largura: 960, altura: 720, topo: 50, fundo: 670 };
    ctx.clearRect(0, 0, largura, altura);
    ctx.fillStyle = "#101e27";
    ctx.fillRect(0, topo, largura, fundo - topo);
    ctx.strokeStyle = "#324953";
    ctx.strokeRect(1, topo, largura - 2, fundo - topo);
    ctx.setLineDash([9, 10]);
    ctx.beginPath();
    ctx.moveTo(0, altura / 2);
    ctx.lineTo(largura, altura / 2);
    ctx.stroke();
    ctx.setLineDash([]);
    if (partida && quadro) {
      for (const [i, r] of partida.raquetes.entries()) {
        ctx.fillStyle = i === 0 ? "#65ead1" : "#ffad79";
        ctx.fillRect(quadro.raquetes[i], r.y, r.largura, r.altura);
      }
      const raio = partida.bola_tamanho / 2;
      ctx.fillStyle = "#fff6dd";
      ctx.beginPath();
      ctx.arc(quadro.bola[0] + raio, quadro.bola[1] + raio, raio, 0, Math.PI * 2);
      ctx.fill();
    }
  }, [partida, quadro]);

  return <main>
    <h1>Duelo de Raquetes</h1>
    <form onSubmit={carregar}>
      <label>Semente <input type="number" min="0" max="4294967295" step="1" required value={semente}
        onChange={e => setSemente(e.target.value)} /></label>
      <button disabled={carregando}>{carregando ? "Simulando…" : "Simular duelo"}</button>
    </form>
    {erro && <p role="alert">{erro}</p>}
    <section className="placar" aria-label="Placar">
      <div className="rede-a">{partida?.nomes[0] ?? "Rede A"} <strong>{quadro?.pontos[0] ?? 0}</strong></div>
      <div className="rede-b"><strong>{quadro?.pontos[1] ?? 0}</strong> {partida?.nomes[1] ?? "Rede B"}</div>
    </section>
    <canvas ref={canvas} width={partida?.arena.largura ?? 960} height={partida?.arena.altura ?? 720}
      aria-label="Arena com a rede A embaixo e a rede B em cima" />
    <div className="controles">
      <button disabled={!partida || carregando} onClick={() => {
        if (fim) setIndice(0);
        setReproduzindo(fim || !reproduzindo);
      }}>{fim ? "Rever" : reproduzindo ? "Pausar" : "Reproduzir"}</button>
      <input aria-label="Posição na reprodução" type="range" min={0} max={(partida?.quadros.length ?? 1) - 1}
        value={indice} disabled={!partida || carregando}
        onChange={e => { setReproduzindo(false); setIndice(Number(e.target.value)); }} />
      <output>{((quadro?.passos ?? 0) / 60).toFixed(1)} s</output>
    </div>
    {fim && <p role="status">{partida?.resultado === "empate" ? "Empate" :
      (partida?.nomes[partida.resultado === "A" ? 0 : 1] ?? "Rede") + " venceu"}</p>}
  </main>;
}
