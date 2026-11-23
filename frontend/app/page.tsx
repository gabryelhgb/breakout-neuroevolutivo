"use client";
import { useEffect, useRef, useState } from "react";
import { Arena, Neuronios, type Cena, type Partida, type Quadro } from "./arena";

type Modelo = { id: string; nome: string };
type Chave = { nomes: string[]; pontos: number[]; vencedor: string; sorteio: boolean; partidas: Partida[] };
type Torneio = { fases: Chave[][]; campea: string; nome_campea: string; ranking: string[] };
type Jogo = Cena & { quadro: Quadro };
type ConfiguracaoJogo = { modelo: string; semente: number; regras: number[] };
type AoVivo = { geracao: number; geracoes?: number; fase: string; partidas: Jogo[] };
type Treino = { rodando: boolean; codigo?: number | null; linhas: string[]; ritmo?: number | null; pausado?: boolean; origem?: "terminal" };
const camposPartida = [
  ["duracao", "Duração (s)", 90, 5, 180, 1],
  ["pontos", "Pontos para vencer", 9, 1, 21, 1],
  ["velocidade", "Velocidade inicial", 4, 3, 12, 0.25],
  ["incremento", "Aumento por ponto", 0.45, 0, 3, 0.05],
] as const;
const camposTreino = [
  ["geracoes", "Gerações", 100, 1, 20000, 1],
  ["rodadas", "Rodadas por geração", 12, 1, 40, 1],
  ["taxa", "Taxa de aprendizagem", 0.04, 0.001, 1, 0.001],
  ["mutacao", "Probabilidade de mutação", 0.03, 0, 1, 0.001],
] as const;

async function requisitar<T>(url: string, dados?: object): Promise<T> {
  const res = await fetch(url, dados ? { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify(dados) } : { cache: "no-store" });
  const corpo = await res.json().catch(() => ({}));
  if (!res.ok) throw new Error(corpo.erro || "Confira se a API Python está ativa.");
  return corpo as T;
}

export default function Home() {
  const [semente, setSemente] = useState("42");
  const [parametros, setParametros] = useState(() => Object.fromEntries(
    [...camposPartida, ...camposTreino].map(([id, , inicial]) => [id, String(inicial)])));
  const [treino, setTreino] = useState<Treino>();
  const [aoVivo, setAoVivo] = useState<AoVivo | null>(null);
  const [ritmoTreino, setRitmoTreino] = useState(1);
  const [ajustandoRitmo, setAjustandoRitmo] = useState(false);
  const [ritmo, setRitmo] = useState(1);
  const [arenas, setArenas] = useState<Partida[]>([]);
  const [destaque, setDestaque] = useState(0);
  const [lado, setLado] = useState(0);
  const [indice, setIndice] = useState(0);
  const [rodando, setRodando] = useState(false);
  const [erro, setErro] = useState("");
  const [ocupado, setOcupado] = useState(false);
  const [torneio, setTorneio] = useState<Torneio>();
  const [modelos, setModelos] = useState<Modelo[]>([]);
  const [dificuldade, setDificuldade] = useState("facil");
  const [jogo, setJogo] = useState<Jogo>();
  const [jogando, setJogando] = useState(false);
  const teclas = useRef(new Set<string>());
  const toque = useRef(0);
  const ultimaPartida = useRef<ConfiguracaoJogo | null>(null);

  useEffect(() => {
    let ativo = true;
    requisitar<Modelo[]>("/modelos").then(m => {
      if (ativo) setModelos(m);
    }).catch(e => { if (ativo) setErro(e.message); });
    return () => { ativo = false; };
  }, []);

  useEffect(() => {
    if (!rodando || !arenas.length) return;
    const limite = Math.max(...arenas.map(a => a.quadros.length)) - 1;
    if (indice >= limite) return;
    const timer = setTimeout(() => setIndice(i => i + 1), 1000 / (15 * ritmo));
    return () => clearTimeout(timer);
  }, [indice, rodando, arenas, ritmo]);

  useEffect(() => {
    let ativo = true;
    let estavaRodando = false;
    let timer: ReturnType<typeof setTimeout>;
    async function consultar() {
      try {
        const atual = await requisitar<Treino>("/treino/status");
        if (!ativo) return;
        setTreino(atual);
        if (atual.rodando && !estavaRodando) {
          setAoVivo(null);
          setRodando(false);
        }
        const concluiu = estavaRodando && atual.codigo === 0;
        estavaRodando = atual.rodando;
        if (concluiu) {
          const lista = await requisitar<Modelo[]>("/modelos");
          if (!ativo) return;
          setModelos(lista);
          setTorneio(undefined);
        }
      } catch { /* A próxima consulta pode recuperar uma falha temporária. */ }
      if (ativo) timer = setTimeout(consultar, 1000);
    }
    void consultar();
    return () => { ativo = false; clearTimeout(timer); };
  }, []);

  useEffect(() => {
    if (!treino?.rodando) return;
    let ativo = true, timer: ReturnType<typeof setTimeout>;
    async function atualizar() {
      const inicio = performance.now();
      try {
        const quadro = await requisitar<AoVivo | null>("/treino/ao-vivo");
        if (ativo && quadro) setAoVivo(quadro);
      } catch { /* Mantém o último quadro se ocorrer uma falha temporária. */ }
      if (ativo) timer = setTimeout(atualizar, Math.max(0, 40 - (performance.now() - inicio)));
    }
    void atualizar();
    return () => { ativo = false; clearTimeout(timer); };
  }, [treino?.rodando]);

  useEffect(() => {
    if (!jogando) return;
    const pressionar = (e: KeyboardEvent) => {
      if (e.target instanceof HTMLElement && e.target.closest("input, select, textarea")) return;
      const tecla = e.key.length === 1 ? e.key.toLowerCase() : e.key;
      if (["ArrowLeft", "ArrowRight", "a", "d"].includes(tecla)) { e.preventDefault(); teclas.current.add(tecla); }
    };
    const soltar = (e: KeyboardEvent) => teclas.current.delete(e.key.length === 1 ? e.key.toLowerCase() : e.key);
    const limpar = () => { teclas.current.clear(); toque.current = 0; };
    window.addEventListener("keydown", pressionar);
    window.addEventListener("keyup", soltar);
    window.addEventListener("blur", limpar);
    let ativo = true, timer: ReturnType<typeof setTimeout>;
    async function passo() {
      const inicio = performance.now();
      try {
        const direita = teclas.current.has("ArrowRight") || teclas.current.has("d");
        const esquerda = teclas.current.has("ArrowLeft") || teclas.current.has("a");
        const acao = toque.current || Number(direita) - Number(esquerda);
        const novo = await requisitar<Jogo>("/jogar/passo", { acao });
        if (!ativo) return;
        setJogo(novo);
        if (novo.resultado !== "em_andamento") { setJogando(false); return; }
        timer = setTimeout(passo, Math.max(0, 1000 / 15 - (performance.now() - inicio)));
      } catch (e) {
        if (ativo) { setErro((e as Error).message); setJogando(false); }
      }
    }
    timer = setTimeout(passo, 1000 / 15);
    return () => {
      ativo = false; clearTimeout(timer); limpar();
      window.removeEventListener("keydown", pressionar);
      window.removeEventListener("keyup", soltar);
      window.removeEventListener("blur", limpar);
    };
  }, [jogando]);

  function exibir(partidas: Partida[]) {
    setAoVivo(null);
    setArenas(partidas); setDestaque(0); setIndice(0); setLado(0); setRodando(true);
  }

  const regras = camposPartida.map(([id]) => Number(parametros[id]));

  async function mudarRitmo(valor: number) {
    if (!treino?.rodando) {
      setRitmoTreino(valor);
      return;
    }
    setAjustandoRitmo(true);
    setErro("");
    try {
      setTreino(await requisitar<Treino>("/treino/ritmo", { ritmo: valor }));
      setRitmoTreino(valor);
    } catch (e) { setErro((e as Error).message); }
    finally { setAjustandoRitmo(false); }
  }

  async function alternarPausa() {
    setAjustandoRitmo(true);
    setErro("");
    try {
      setTreino(await requisitar<Treino>("/treino/pausa", { pausado: !treino?.pausado }));
    } catch (e) { setErro((e as Error).message); }
    finally { setAjustandoRitmo(false); }
  }

  async function treinar(reiniciar = false) {
    setOcupado(true); setErro(""); setAoVivo(null); setRodando(false); setDestaque(0); setLado(0);
    try {
      const dados = Object.fromEntries(Object.entries(parametros).map(([id, valor]) => [id, Number(valor)]));
      setTreino(await requisitar<Treino>("/treino/iniciar", { ...dados, semente: Number(semente), ritmo: ritmoTreino, reiniciar }));
    } catch (e) { setErro((e as Error).message); }
    finally { setOcupado(false); }
  }

  async function realizarTorneio() {
    setRodando(false); setOcupado(true); setErro("");
    const query = new URLSearchParams({ semente, ...Object.fromEntries(
      camposPartida.map(([id]) => [id, parametros[id]])) });
    try {
      const novo = await requisitar<Torneio>("/torneio?" + query);
      setTorneio(novo);
      exibir(novo.fases.at(-1)?.[0].partidas ?? []);
    } catch (e) { setErro((e as Error).message); }
    finally { setOcupado(false); }
  }

  async function iniciar(configuracao: ConfiguracaoJogo) {
    setOcupado(true); setRodando(false); setErro("");
    try {
      const novo = await requisitar<Jogo>("/jogar/iniciar", configuracao);
      ultimaPartida.current = configuracao;
      setLado(1); setJogo(novo); setJogando(true);
    } catch (e) { setErro((e as Error).message); }
    finally { setOcupado(false); }
  }

  async function sair() {
    setJogando(false); setOcupado(true);
    try { await requisitar("/jogar/fechar", {}); setJogo(undefined); }
    catch (e) { setErro((e as Error).message); }
    finally { setOcupado(false); }
  }

  const monitorando = Boolean(treino?.rodando || aoVivo) && !jogo;
  const partidaViva = monitorando ? aoVivo?.partidas[destaque] : undefined;
  const partida = monitorando ? undefined : arenas[destaque];
  const quadro = partida?.quadros[Math.min(indice, partida.quadros.length - 1)];
  const ultimo = Math.max(0, ...arenas.map(a => a.quadros.length - 1));
  const selecionada = jogo ? 1 : lado;
  const cenaAtual = jogo ?? partidaViva ?? partida;
  const quadroAtual = jogo?.quadro ?? partidaViva?.quadro ?? quadro;
  const sinal = quadroAtual?.sinais[selecionada];
  const ritmoDisponivel = !treino?.rodando || typeof treino.ritmo === "number";
  const bloqueado = ocupado || Boolean(jogo) || Boolean(treino?.rodando);
  const sementeValida = semente !== "" && Number.isInteger(Number(semente)) && Number(semente) >= 0 && Number(semente) < 2 ** 32;
  const validar = (campos: readonly (typeof camposPartida[number] | typeof camposTreino[number])[]) =>
    campos.every(([id, , , min, max, passo]) => {
      const n = Number(parametros[id]);
      return parametros[id] !== "" && n >= min && n <= max && (passo !== 1 || Number.isInteger(n));
    });
  const partidaValida = sementeValida && validar(camposPartida);
  const editar = (campos: readonly (typeof camposPartida[number] | typeof camposTreino[number])[]) =>
    campos.map(([id, nome, , min, max, passo]) =>
      <label key={id}>{nome}<input type="number" min={min} max={max} step={passo}
        value={parametros[id]} onChange={e => setParametros(p => ({ ...p, [id]: e.target.value }))} /></label>);
  const treinadas = modelos.filter(m => !m.id.startsWith("aleatoria-"));
  const geracoes = treinadas.filter(m => m.id.startsWith("geracao-"));
  const referencia = geracoes.length ? geracoes : treinadas;
  const ranking = torneio?.ranking;
  const niveis = [
    { id: "facil", nome: "Fácil", modelo: ranking?.at(-1) ?? modelos.find(m => m.id.startsWith("aleatoria-"))?.id },
    { id: "medio", nome: "Média", modelo: ranking?.[Math.floor(ranking.length / 2)] ?? referencia[Math.floor((referencia.length - 1) / 2)]?.id },
    { id: "dificil", nome: "Difícil", modelo: ranking?.[0] ?? modelos.find(m => m.id === "modelo_validado")?.id ?? referencia.at(-1)?.id },
  ];
  const adversaria = niveis.find(nivel => nivel.id === dificuldade)?.modelo;
  const nomeAdversaria = modelos.find(modelo => modelo.id === adversaria)?.nome;
  const resultado = jogo?.resultado ?? partidaViva?.resultado ?? (partida && quadro === partida.quadros.at(-1) ? partida.resultado : "em_andamento");

  return <main>
    <header><div><h1>Duelo de Raquetes</h1></div>
      {torneio && <div className="campea"><span>Campeã</span><strong>{torneio.nome_campea}</strong></div>}
    </header>
    <section className="painel secao parametros">
      <details open><summary>Regras da partida</summary><div className="campos">{editar(camposPartida)}</div></details>
      <details open><summary>Parâmetros de treinamento</summary><div className="campos">
        <label>Semente <input type="number" min="0" max="4294967295" value={semente} onChange={e => setSemente(e.target.value)} /></label>
        {editar(camposTreino)}</div>
        <p className="faixa">As mudanças entram em vigor ao iniciar um novo treino. O ritmo pode ser alterado ao vivo.</p>
        {!!treino?.linhas.length && <pre>{treino.linhas.join("\n")}</pre>}
      </details>
      {treino?.rodando && <div className="barra faixa">
        <button disabled={ocupado || Boolean(jogo) || !partidaValida || !validar(camposTreino) || treino?.rodando && treino.origem === "terminal"}
          onClick={() => void treinar(true)}>Reiniciar e aplicar</button>
        {treino?.rodando && treino.origem === "terminal" && <span>Encerre com Ctrl+C no terminal para reiniciar.</span>}
      </div>}
    </section>
    <div className="transmissao">
      <button className="primario" disabled={bloqueado || !partidaValida || !validar(camposTreino)}
        onClick={() => void treinar()}>Treinar 8 redes</button>
      <span role="status">{treino?.rodando
        ? treino.pausado ? "Treino pausado" : aoVivo ? `Ao vivo · Geração ${aoVivo.geracao}${aoVivo.geracoes ? "/" + aoVivo.geracoes : ""} · ${aoVivo.fase}${treino.origem === "terminal" ? " · Terminal" : ""}` : "Preparando o treino…"
        : treino?.codigo === 0 ? "Treino concluído" : treino?.codigo ? "O treino falhou" : "Treino pronto para iniciar"}</span>
      {ocupado && <span role="status">Processando…</span>}
    </div>
    {erro && <p className="erro" role="alert">{erro}</p>}
    <div className="grade">
      <section className="painel">
        <div className="titulo"><h2>{jogo ? "Você contra a rede" : `Arena ${destaque + 1} em destaque`}</h2>
          <span>{((quadroAtual?.passos ?? 0) / 60).toFixed(1)} s</span></div>
        <Arena key={destaque + ":" + String(monitorando)} cena={cenaAtual} quadro={quadroAtual} intervalo={monitorando ? 40 : jogo ? 66 : 66 / ritmo} />
        {monitorando && !quadroAtual && <p className="resultado">{aoVivo?.fase ?? "Aguardando o primeiro duelo"}</p>}
        {resultado && resultado !== "em_andamento" && <p className="resultado" role="status">{resultado === "empate" ? "Empate" : cenaAtual?.nomes[resultado === "A" ? 0 : 1] + " venceu"}</p>}
        {jogo ? <div className="barra faixa">
          {jogo.resultado !== "em_andamento" ? <button className="primario" disabled={ocupado}
            onClick={() => ultimaPartida.current && void iniciar(ultimaPartida.current)}>Jogar novamente</button>
            : <><span>Use A / D ou ← / →</span>
          {[-1, 1].map(v => <button key={v} onPointerDown={e => { e.currentTarget.setPointerCapture(e.pointerId); toque.current = v; }}
            onPointerUp={() => { toque.current = 0; }} onPointerCancel={() => { toque.current = 0; }}>{v < 0 ? "Esquerda" : "Direita"}</button>)}</>}
          <button disabled={ocupado} onClick={() => void sair()}>Voltar às arenas</button></div>
          : !monitorando && <div className="barra faixa">
            <button disabled={!arenas.length} onClick={() => { if (indice >= ultimo) setIndice(0); setRodando(!rodando || indice >= ultimo); }}>
              {indice >= ultimo && arenas.length ? "Rever" : rodando ? "Pausar" : "Reproduzir"}</button>
            <input type="range" aria-label="Posição do replay" min={0} max={ultimo} value={indice} disabled={!arenas.length}
              onChange={e => { setRodando(false); setIndice(Number(e.target.value)); }} />
            <select aria-label="Velocidade do replay" value={ritmo} onChange={e => setRitmo(Number(e.target.value))}>
              <option value={0.5}>0,5×</option><option value={1}>1×</option><option value={2}>2×</option>
            </select>
          </div>}
      </section>
      <aside className="painel">
        <div className="titulo"><h2>Ativações da rede</h2><span>{sinal?.camadas.map(camada => camada.length).join(" / ") ?? "9 / 20 / 12 / 3"}</span></div>
        <div className="controle-ritmo">
          <label htmlFor="ritmo-treino">Ritmo do treino</label>
          <select id="ritmo-treino" value={!ritmoDisponivel ? -1 : treino?.rodando ? treino.ritmo ?? ritmoTreino : ritmoTreino}
            disabled={ajustandoRitmo || !ritmoDisponivel} onChange={e => void mudarRitmo(Number(e.target.value))}>
            {!ritmoDisponivel && <option value={-1}>Fixo</option>}
            <option value={1}>1×</option><option value={2}>2×</option><option value={4}>4×</option>
            <option value={8}>8×</option><option value={0}>Máximo</option>
          </select>
          {treino?.rodando && <button disabled={ajustandoRitmo || !ritmoDisponivel}
            onClick={() => void alternarPausa()}>{treino.pausado ? "Retomar treino" : "Pausar treino"}</button>}
        </div>
        {!ritmoDisponivel && <p className="faixa" role="status">Este treino usa a versão anterior. Deixe-o terminar e reinicie a API antes do próximo treino.</p>}
        <div className="selecionar-rede" role="group" aria-label="Escolher rede da arena em destaque">
          {(cenaAtual?.nomes ?? ["Rede A", "Rede B"]).map((nome, i) => <button
            key={i} aria-pressed={selecionada === i} disabled={!cenaAtual || Boolean(jogo) && i === 0}
            onClick={() => setLado(i)}>{nome}<small>{i === 0 ? "Embaixo" : "Em cima"}</small></button>)}
        </div>
        <Neuronios sinal={sinal} />
        <p className="legenda"><span className="rede-a">Positiva</span><span className="rede-b">Negativa</span><span className="acao">Ação escolhida</span></p>
        <div className="faixa jogar">
          <h2>Enfrentar uma IA</h2>
          <label>Dificuldade
            <select value={dificuldade} disabled={bloqueado} onChange={e => setDificuldade(e.target.value)}>
              {niveis.map(nivel => <option key={nivel.id} value={nivel.id} disabled={!nivel.modelo}>{nivel.nome}</option>)}
            </select>
          </label>
          {nomeAdversaria && <span className="adversaria">{nomeAdversaria}</span>}
          <button disabled={bloqueado || !partidaValida || !adversaria} onClick={() => adversaria && void iniciar({ modelo: adversaria, semente: Number(semente), regras })}>Jogar</button>
        </div>
      </aside>
    </div>
    {(monitorando || arenas.length > 0) && <section className="painel secao">
      <div className="titulo"><h2>Arenas simultâneas</h2><span>{monitorando ? "Treinamento" : "Replays sincronizados"}</span></div>
      <div className="miniaturas">
        {monitorando ? (aoVivo?.partidas.length ? aoVivo.partidas.map((a, i) => <button key={i}
          aria-label={`Selecionar arena ${i + 1}`} aria-pressed={destaque === i} onClick={() => setDestaque(i)}>
          <Arena cena={a} quadro={a.quadro} mini /></button>)
          : <p>{aoVivo?.fase ?? "Aguardando o início do treino…"}</p>)
          : arenas.length ? arenas.map((a, i) => <button key={i} disabled={Boolean(jogo)} aria-pressed={destaque === i}
          onClick={() => setDestaque(i)}><Arena cena={a} quadro={a.quadros[Math.min(indice, a.quadros.length - 1)]} mini /></button>)
          : null}
      </div>
    </section>}
    <section className="painel secao">
      <div className="titulo"><h2>Torneio</h2>
        <button disabled={bloqueado || !partidaValida || treinadas.length < 2} onClick={() => void realizarTorneio()}>Realizar torneio</button></div>
      {treinadas.length < 2 && <p className="faixa">Treine pelo menos duas gerações para liberar o torneio.</p>}
      {torneio && <div className="chave">{torneio.fases.map((fase, i) => <div key={i}><h3>{["Final", "Semifinais", "Quartas"][torneio.fases.length - i - 1]}</h3>
        {fase.map((c, j) => <article key={j}>
          <div>{c.nomes[0]} <strong>{c.pontos[0]}</strong></div><div>{c.nomes[1]} <strong>{c.pontos[1]}</strong></div>
          <p>{c.vencedor} avançou{c.sorteio ? " por sorteio após empate" : ""}.</p>
          <button disabled={Boolean(jogo)} onClick={() => exibir(c.partidas)}>Ver partidas</button>
        </article>)}</div>)}</div>}
    </section>
  </main>;
}
