import argparse
import json
import random
from collections import deque
from pathlib import Path

from cliente_java import ClienteJava
from rede_neural import (
    ACOES, criar_variacoes_zeradas, decidir_acao, dna_para_rede,
    inicializar_rede, propagar_entrada, rede_para_dna, treinar_amostra,
)


ARQUITETURA = (6, 12, 8, 3)


def observar(estado, lado):
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
    return entradas


def jogar(cliente, redes, semente, sorteador, explorar=0, memorias=None):
    estado = cliente.criar_partida(0, semente, raquetes=True)
    while not estado["encerrada"]:
        entradas = [observar(estado, lado) for lado in (0, 1)]
        acoes = []
        for lado, rede in enumerate(redes):
            _, valores = propagar_entrada(entradas[lado], rede)
            acao = decidir_acao(valores)
            if explorar and sorteador.random() < explorar:
                acao = sorteador.choice(ACOES)
            acoes.append(acao)
        novo = cliente.avancar(0, acoes[0], passos=4, acao_adversario=acoes[1])
        if memorias is not None:
            pontos_a, pontos_b = novo["pontos"]
            recompensa_a = (pontos_a - estado["pontos"][0]) - (pontos_b - estado["pontos"][1])
            for lado, recompensa in enumerate((recompensa_a, -recompensa_a)):
                fim = novo["encerrada"] or novo["pontos"] != estado["pontos"]
                memorias[lado].append((entradas[lado], ACOES.index(acoes[lado]),
                                       recompensa, observar(novo, lado), fim))
        estado = novo
    return estado["pontos"]


def aprender(rede, memoria, sorteador):
    alvo_fixo = dna_para_rede(rede_para_dna(rede), ARQUITETURA)
    anteriores = criar_variacoes_zeradas(rede)
    exemplos = sorteador.sample(list(memoria), min(256, len(memoria)))
    erros = []
    for entrada, acao, recompensa, proxima, fim in exemplos:
        _, valores = propagar_entrada(entrada, rede)
        if not fim:
            _, futuros = propagar_entrada(proxima, alvo_fixo)
            recompensa += 0.98 * max(futuros)
        # As saídas bipolares representam retornos entre -1 e 1.
        valores[acao] = max(-1.0, min(1.0, recompensa))
        erros.append(treinar_amostra(entrada, valores, rede, anteriores,
                                     taxa=0.04, momento=0))
    return sum(erros) / len(erros)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--geracoes", type=int, default=20)
    parser.add_argument("--rodadas", type=int, default=4,
                        help="Rodadas de coleta por geração, sempre com troca de lados.")
    parser.add_argument("--saida", type=Path, help="Arquivo JSON novo para guardar a melhor rede.")
    args = parser.parse_args()
    if min(args.geracoes, args.rodadas) < 1:
        parser.error("Use pelo menos uma geração e uma rodada.")
    rng = random.Random(42)
    populacao = [inicializar_rede(rng.randrange(2**32), ARQUITETURA) for _ in range(8)]
    with ClienteJava() as cliente:
        for geracao in range(1, args.geracoes + 1):
            memorias = [deque(maxlen=4000) for _ in populacao]
            ordem = list(range(len(populacao)))
            for _ in range(args.rodadas):
                rng.shuffle(ordem)
                for a, b in zip(ordem[::2], ordem[1::2]):
                    semente = rng.randrange(2**32)
                    for i, j in ((a, b), (b, a)):
                        jogar(cliente, (populacao[i], populacao[j]), semente, rng,
                              explorar=max(0.08, 0.35 * 0.94 ** (geracao - 1)),
                              memorias=(memorias[i], memorias[j]))
            erros = [aprender(rede, memoria, rng) for rede, memoria in zip(populacao, memorias)]

            # Dois adversários distintos por rede, com ida e volta.
            aptidao = [0.0] * len(populacao)
            rng.shuffle(ordem)
            for k, a in enumerate(ordem):
                b = ordem[(k + 1) % len(ordem)]
                semente = rng.randrange(2**32)
                for i, j in ((a, b), (b, a)):
                    pa, pb = jogar(cliente, (populacao[i], populacao[j]), semente, rng)
                    for jogador, feitos, sofridos in ((i, pa, pb), (j, pb, pa)):
                        resultado = 3 if feitos > sofridos else 1 if feitos == sofridos else 0
                        aptidao[jogador] += (resultado + 0.05 * (feitos - sofridos)) / 4
            ranking = sorted(ordem, key=aptidao.__getitem__, reverse=True)
            melhor_dna = rede_para_dna(populacao[ranking[0]])
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
                    if rng.random() < 0.03:
                        dna[i] = peso + rng.gauss(0, 0.08)
                nova.append(dna_para_rede(dna, ARQUITETURA))
            populacao = nova
    if args.saida:
        with args.saida.open("x", encoding="utf-8") as arquivo:
            json.dump({"modo": "raquetes", "arquitetura": ARQUITETURA,
                       "dna": melhor_dna}, arquivo, allow_nan=False)


if __name__ == "__main__":
    main()
