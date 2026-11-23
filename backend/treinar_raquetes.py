import argparse
import json
import random
import sys
import time
from collections import deque
from pathlib import Path
from uuid import uuid4

from cliente_java import ClienteJava
from rede_neural import (
    ACOES, criar_variacoes_zeradas, decidir_acao, dna_para_rede,
    inicializar_rede, propagar_entrada, rede_para_dna, treinar_amostra,
)


ARQUITETURA = (9, 20, 12, 3)
ARQUITETURA_ANTERIOR = (6, 12, 8, 3)
RITMOS = (0, 1, 2, 4, 8)


def ler_ritmo(caminho):
    try:
        valor = int(caminho.read_text(encoding='ascii'))
    except (ValueError, OSError):
        return None
    return valor if valor in RITMOS else None


def observar(estado, lado, quantidade=ARQUITETURA[0]):
    arena = estado["arena"]
    largura = arena["largura"]
    bola = estado["bolas"][0]
    y = 2 * (bola["y"] + bola["tamanho"] / 2 - arena["topo"]) / (
        arena["fundo"] - arena["topo"]
    ) - 1
    sentido = 1 if lado == 0 else -1
    entradas = [
        2 * (bola["x"] + bola["tamanho"] / 2) / largura - 1,
        y * sentido, bola["vx"] / 12, bola["vy"] * sentido / 12,
    ]
    for jogador in (lado, 1 - lado):
        raquete = estado["raquetes"][jogador]
        entradas.append(2 * (raquete["x"] + raquete["largura"] / 2) / largura - 1)
    if quantidade == ARQUITETURA_ANTERIOR[0]:
        return entradas

    raquete = estado["raquetes"][lado]
    centro = raquete["x"] + raquete["largura"] / 2
    alvo_y = (raquete["y"] - bola["tamanho"] if lado == 0
              else raquete["y"] + raquete["altura"])
    x = bola["x"]
    margem = largura - bola["tamanho"]
    passos = (alvo_y - bola["y"]) / bola["vy"] if bola["vy"] else -1
    if passos > 0 and bola["vy"] * sentido > 0:
        x = (x + bola["vx"] * passos) % (2 * margem)
        x = min(x, 2 * margem - x)
    entradas.extend([
        (bola["x"] + bola["tamanho"] / 2 - centro) / largura,
        (x + bola["tamanho"] / 2 - centro) / largura,
        min(1.0, passos / 180) if passos > 0 and bola["vy"] * sentido > 0 else -1.0,
    ])
    return entradas


def avaliar_acoes(estado, lado, entrada):
    largura = estado["arena"]["largura"]
    raquete = estado["raquetes"][lado]
    meia = raquete["largura"] / 2
    centro = raquete["x"] + meia
    alvo = centro + entrada[7] * largura if entrada[8] >= 0 else largura / 2
    alvo = max(meia, min(largura - meia, alvo))
    erros = [abs(alvo - max(meia, min(largura - meia, centro + acao * 44)))
             for acao in ACOES]
    # Se a raquete estiver perto, ficar parada evita passar do alvo.
    if erros[1] <= 24:
        erros[1] = 0
    melhor = min(erros)
    return [max(-1, 0.9 - (erro - melhor) / 28) for erro in erros]


def acompanhar_partida(cliente, redes, semente, sorteador, explorar=0, memorias=None,
                       regras=None, identificador=0):
    estado = cliente.criar_partida(identificador, semente, raquetes=True, regras=regras)
    while not estado["encerrada"]:
        entradas = [observar(estado, lado, rede['arquitetura'][0]) for lado, rede in enumerate(redes)]
        acoes = []
        sinais = []
        for lado, rede in enumerate(redes):
            ocultas, valores = propagar_entrada(entradas[lado], rede)
            acao = decidir_acao(valores)
            if explorar and sorteador.random() < explorar:
                acao = sorteador.choice(ACOES)
            acoes.append(acao)
            sinais.append({"camadas": [entradas[lado], *ocultas, valores], "acao": acao})
        yield estado, sinais
        novo = cliente.avancar(identificador, acoes[0], passos=4, acao_adversario=acoes[1])
        if memorias is not None:
            for lado, entrada in enumerate(entradas):
                alvos = avaliar_acoes(estado, lado, entrada)
                devolveu = novo["fortalezas"][lado]["rebatidas"] > estado["fortalezas"][lado]["rebatidas"]
                sofreu = novo["pontos"][1 - lado] > estado["pontos"][1 - lado]
                indice = ACOES.index(acoes[lado])
                alvos[indice] = max(-1, min(1, alvos[indice] + 0.2 * devolveu - 0.2 * sofreu))
                memorias[lado].append((entrada, alvos))
        estado = novo
    yield estado, [None, None]


def jogar(cliente, redes, semente, sorteador, explorar=0, memorias=None, regras=None):
    for estado, _ in acompanhar_partida(cliente, redes, semente, sorteador,
                                        explorar, memorias, regras):
        pass
    return estado["pontos"]


class Transmissao:
    def __init__(self, caminho, ritmo, geracoes):
        self.caminho = caminho
        self.ritmo = ritmo
        self.controle = caminho.with_suffix(".ritmo") if caminho else None
        self.ultima_consulta = float("-inf")
        self.pausa = caminho.with_suffix(".pausa") if caminho else None
        if self.controle:
            try:
                with self.controle.open("x", encoding="ascii") as arquivo:
                    arquivo.write(str(ritmo))
            except FileExistsError:
                pass
        self.geracoes = geracoes
        self.publicado = 0.0

    def aguardar_retomada(self):
        proximo_sinal = 0.0
        while self.pausa and self.pausa.exists():
            agora = time.monotonic()
            if agora >= proximo_sinal:
                try:
                    self.caminho.touch()
                except PermissionError:
                    pass
                proximo_sinal = agora + 1
            time.sleep(0.03)

    def ritmo_atual(self):
        agora = time.monotonic()
        if self.controle and agora - self.ultima_consulta >= 0.01:
            self.ultima_consulta = agora
            ritmo = ler_ritmo(self.controle)
            if ritmo is not None:
                self.ritmo = ritmo
        return self.ritmo

    def publicar(self, geracao, fase, partidas, *, forcar=False):
        agora = time.monotonic()
        if self.caminho is None or (not forcar and agora - self.publicado < 1 / 30):
            return
        temporario = self.caminho.with_suffix(".tmp")
        temporario.write_text(json.dumps({
            "geracao": geracao, "geracoes": self.geracoes, "fase": fase, "partidas": partidas,
        }, separators=(",", ":")), encoding="utf-8")
        try:
            temporario.replace(self.caminho)
        except PermissionError:
            # No Windows, uma consulta pode manter o quadro anterior aberto por instantes.
            if forcar:
                time.sleep(0.01)
                try:
                    temporario.replace(self.caminho)
                except PermissionError:
                    return
            else:
                return
        self.publicado = agora


def jogar_grupo(cliente, populacao, confrontos, sorteador, regras, transmissao,
                geracao, fase, memorias=None, explorar=0):
    partidas = [acompanhar_partida(
        cliente, (populacao[a], populacao[b]), semente, sorteador,
        explorar=explorar, memorias=(memorias[a], memorias[b]) if memorias else None,
        regras=regras, identificador=i,
    ) for i, (a, b, semente) in enumerate(confrontos)]
    cenas = [{} for _ in partidas]
    resultados = [None for _ in partidas]
    while any(resultado is None for resultado in resultados):
        transmissao.aguardar_retomada()
        inicio = time.monotonic()
        for i, partida in enumerate(partidas):
            if resultados[i] is not None:
                continue
            estado, sinais = next(partida)
            if estado["encerrada"]:
                resultados[i] = estado
            if transmissao.caminho is None:
                continue
            bola = estado["bolas"][0]
            a, b, _ = confrontos[i]
            cenas[i] = {
                "nomes": [f"Rede {a + 1}", f"Rede {b + 1}"],
                "resultado": estado["resultado"], "arena": estado["arena"],
                "bola_tamanho": bola["tamanho"], "raquetes": estado["raquetes"],
                "quadro": {"bola": [bola["x"], bola["y"]],
                           "raquetes": [r["x"] for r in estado["raquetes"]],
                           "pontos": estado["pontos"], "passos": estado["passos"], "sinais": sinais},
            }
        transmissao.publicar(geracao, fase, cenas, forcar=all(r is not None for r in resultados))
        while transmissao.caminho:
            ritmo = transmissao.ritmo_atual()
            if not ritmo:
                break
            espera = inicio + 4 / (60 * ritmo) - time.monotonic()
            if espera <= 0:
                break
            time.sleep(min(espera, 0.01))
    return resultados


def aprender(rede, memoria, sorteador, taxa=0.04, transmissao=None):
    anteriores = criar_variacoes_zeradas(rede)
    exemplos = sorteador.sample(list(memoria), min(1024, len(memoria)))
    erros = []
    for entrada, alvos in exemplos:
        if transmissao:
            transmissao.aguardar_retomada()
        erros.append(treinar_amostra(entrada, alvos, rede, anteriores,
                                     taxa=taxa, momento=0))
    return sum(erros) / len(erros)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--geracoes", type=int, default=100)
    parser.add_argument("--rodadas", type=int, default=12,
                        help="Rodadas de coleta por geração, sempre com troca de lados.")
    parser.add_argument("--saida", type=Path, help="Arquivo JSON novo para guardar a melhor rede.")
    parser.add_argument("--modelos", type=Path, help="Pasta nova para salvar a melhor rede de cada geração.")
    parser.add_argument("--taxa", type=float, default=0.04)
    parser.add_argument("--mutacao", type=float, default=0.03)
    parser.add_argument("--semente", type=int, default=42)
    parser.add_argument("--duracao", type=int, default=90)
    parser.add_argument("--pontos", type=int, default=9)
    parser.add_argument("--velocidade", type=float, default=4.0)
    parser.add_argument("--incremento", type=float, default=0.45)
    parser.add_argument("--ao-vivo", type=Path)
    parser.add_argument("--ritmo", type=int, choices=RITMOS, default=8)
    args = parser.parse_args()
    if not (1 <= args.geracoes <= 20_000 and 1 <= args.rodadas <= 40):
        parser.error("Use 1 a 20.000 geracoes e 1 a 40 rodadas.")
    if not (0 < args.taxa <= 1 and 0 <= args.mutacao <= 1):
        parser.error("Taxa e mutação fora dos limites.")
    pasta_terminal = None
    if args.ao_vivo is None:
        pasta_terminal = Path(__file__).resolve().parent / "treinos_locais" / ("terminal-" + uuid4().hex)
        pasta_terminal.mkdir(parents=True)
        args.ao_vivo = pasta_terminal / "ao-vivo.json"
        if args.modelos is None:
            args.modelos = pasta_terminal / "modelos"

    try:
        treinar(args)
    finally:
        if pasta_terminal:
            marcador = "concluido" if sys.exc_info()[0] is None else "interrompido"
            (pasta_terminal / marcador).touch()


def treinar(args):
    regras = (args.duracao, args.pontos, args.velocidade, args.incremento)
    if args.modelos:
        args.modelos.mkdir()
    rng = random.Random(args.semente)
    populacao = [inicializar_rede(rng.randrange(2**32), ARQUITETURA) for _ in range(8)]
    transmissao = Transmissao(args.ao_vivo, args.ritmo, args.geracoes)
    transmissao.publicar(1, "Preparando o treino", [], forcar=True)
    with ClienteJava() as cliente:
        for geracao in range(1, args.geracoes + 1):
            memorias = [deque(maxlen=12000) for _ in populacao]
            ordem = list(range(len(populacao)))
            for rodada in range(1, args.rodadas + 1):
                rng.shuffle(ordem)
                pares = [(a, b, rng.randrange(2**32)) for a, b in zip(ordem[::2], ordem[1::2])]
                for confrontos in (pares, [(b, a, s) for a, b, s in pares]):
                    jogar_grupo(cliente, populacao, confrontos, rng, regras, transmissao,
                                geracao, f"Coleta {rodada}/{args.rodadas}", memorias,
                                explorar=max(0.25, 0.55 * 0.96 ** (geracao - 1)))
            transmissao.publicar(geracao, "Backpropagation", [], forcar=True)
            erros = [aprender(rede, memoria, rng, args.taxa, transmissao)
                     for rede, memoria in zip(populacao, memorias)]

            # Dois adversários distintos por rede, com ida e volta.
            aptidao = [0.0] * len(populacao)
            rng.shuffle(ordem)
            pares = [(a, ordem[(k + 1) % len(ordem)], rng.randrange(2**32))
                     for k, a in enumerate(ordem)]
            for grupo in (pares[::2], pares[1::2]):
                for confrontos in (grupo, [(b, a, s) for a, b, s in grupo]):
                    resultados = jogar_grupo(cliente, populacao, confrontos, rng, regras,
                                             transmissao, geracao, "Avaliação genética")
                    for (i, j, _), estado in zip(confrontos, resultados):
                        for lado, jogador in enumerate((i, j)):
                            feitos, sofridos = estado["pontos"][lado], estado["pontos"][1 - lado]
                            devolucoes = estado["fortalezas"][lado]["rebatidas"]
                            resultado = 3 if feitos > sofridos else 1 if feitos == sofridos else 0
                            defesa = devolucoes / max(1, devolucoes + sofridos)
                            aptidao[jogador] += (resultado + defesa + 0.05 * (feitos - sofridos)) / 4
            ranking = sorted(ordem, key=aptidao.__getitem__, reverse=True)
            melhor_dna = rede_para_dna(populacao[ranking[0]])
            if args.modelos:
                caminho = args.modelos / f"geracao-{geracao:04d}.json"
                with caminho.open("x", encoding="utf-8") as arquivo:
                    json.dump({"modo": "raquetes", "arquitetura": ARQUITETURA,
                               "dna": melhor_dna, "geracao": geracao}, arquivo, allow_nan=False)
            print(f"Geração {geracao}: melhor aptidão {max(aptidao):.2f} | "
                  f"erro médio do backpropagation {sum(erros) / len(erros):.5f}", flush=True)
            if geracao == args.geracoes:
                break

            def escolher_pai():
                indice = max(rng.sample(ordem, 3), key=aptidao.__getitem__)
                return rede_para_dna(populacao[indice])

            nova = [dna_para_rede(rede_para_dna(populacao[i]), ARQUITETURA) for i in ranking[:2]]
            while len(nova) < len(populacao):
                pai, mae = escolher_pai(), escolher_pai()
                corte = rng.randrange(1, len(pai))
                dna = pai[:corte] + mae[corte:]
                for i, peso in enumerate(dna):
                    if rng.random() < args.mutacao:
                        dna[i] = peso + rng.gauss(0, 0.08)
                nova.append(dna_para_rede(dna, ARQUITETURA))
            populacao = nova
    if args.saida:
        with args.saida.open("x", encoding="utf-8") as arquivo:
            json.dump({"modo": "raquetes", "arquitetura": ARQUITETURA,
                       "dna": melhor_dna}, arquivo, allow_nan=False)


if __name__ == "__main__":
    main()
