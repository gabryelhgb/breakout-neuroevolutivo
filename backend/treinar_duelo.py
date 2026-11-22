import argparse
import json
import random
from pathlib import Path

from cliente_java import ClienteJava
from observacao import VERSAO_OBSERVACAO, preparar_entradas
from rede_neural import (
    ARQUITETURA, calcular_eqm, criar_variacoes_zeradas, decidir_acao,
    dna_para_rede, inicializar_rede, propagar_entrada, rede_para_dna, saida_desejada,
    treinar_amostra,
)


def referencia(estado, jogador):
    raquete = estado["raquetes"][jogador]
    largura = estado["arena"]["largura"]
    alvo, menor_tempo = largura / 2, float("inf")
    for bola in estado["bolas"]:
        if not bola["ativa"] or bola["vy"] * (1 if jogador == 0 else -1) <= 0:
            continue
        plano = (raquete["y"] - bola["tamanho"] if jogador == 0
                 else raquete["y"] + raquete["altura"])
        tempo = (plano - bola["y"]) / bola["vy"]
        if 0 <= tempo < menor_tempo:
            menor_tempo = tempo
            limite = largura - bola["tamanho"]
            x = (bola["x"] + bola["vx"] * tempo) % (2 * limite)
            alvo = min(x, 2 * limite - x) + bola["tamanho"] / 2
    diferenca = alvo - (raquete["x"] + raquete["largura"] / 2)
    return 0 if abs(diferenca) <= 4 else (1 if diferenca > 0 else -1)


def coletar(cliente, sementes, explorar=False):
    amostras = []
    for semente in sementes:
        sorteador = random.Random(semente)
        estado = cliente.criar_partida(0, semente)
        while not estado["encerrada"]:
            acoes = [referencia(estado, lado) for lado in (0, 1)]
            for lado, acao in enumerate(acoes):
                amostras.append((preparar_entradas(estado, lado), saida_desejada(acao)))
            if explorar:
                # Visita posições diferentes das alcançadas pelo controlador sozinho.
                acoes = [sorteador.choice((-1, 0, 1)) if sorteador.random() < 0.25
                         else acao for acao in acoes]
            estado = cliente.avancar(0, acoes[0], passos=4, acao_adversario=acoes[1])
    return amostras


def avaliar(cliente, rede):
    resultados = {"vitorias": 0, "empates": 0, "derrotas": 0, "blocos_preservados": 0}
    for semente in (42, 1500, 7901):
        for lado in (0, 1):
            estado = cliente.criar_partida(0, semente)
            while not estado["encerrada"]:
                acoes = [0, 0]
                acoes[1 - lado] = referencia(estado, 1 - lado)
                _, saidas = propagar_entrada(preparar_entradas(estado, lado), rede)
                acoes[lado] = decidir_acao(saidas)
                estado = cliente.avancar(0, acoes[0], passos=4, acao_adversario=acoes[1])
            vencedor = estado["vencedor"]
            chave = "empates" if vencedor < 0 else ("vitorias" if vencedor == lado else "derrotas")
            resultados[chave] += 1
            resultados["blocos_preservados"] += estado["fortalezas"][lado]["blocos_restantes"]
    return resultados


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--epocas", type=int, default=12)
    parser.add_argument("--saida", type=Path)
    args = parser.parse_args()
    if args.epocas < 1:
        parser.error("--epocas deve ser positivo")
    rede = inicializar_rede()
    with ClienteJava() as cliente:
        treino = coletar(cliente, (100, 917, 3333, 5210, 8100, 25401), explorar=True)
        validacao = coletar(cliente, (78, 6604))
        print(f"Amostras: {len(treino)} treino, {len(validacao)} validacao", flush=True)
        antes = avaliar(cliente, rede)
        print("Duelo antes:", antes, flush=True)
        anteriores = criar_variacoes_zeradas(rede)
        sorteador = random.Random(42)
        melhor_eqm = calcular_eqm(validacao, rede)
        print(f"EQM inicial de validacao: {melhor_eqm:.5f}", flush=True)
        melhor_dna = rede_para_dna(rede)
        for epoca in range(1, args.epocas + 1):
            sorteador.shuffle(treino)
            for entradas, alvo in treino:
                treinar_amostra(entradas, alvo, rede, anteriores)
            eqm = calcular_eqm(validacao, rede)
            if eqm < melhor_eqm:
                melhor_eqm = eqm
                melhor_dna = rede_para_dna(rede)
            print(f"Epoca {epoca}: EQM de validacao {eqm:.5f}", flush=True)
        depois = avaliar(cliente, dna_para_rede(melhor_dna))
        print("Duelo depois:", depois)
        if args.saida:
            dados = {"arquitetura": ARQUITETURA, "versao_observacao": VERSAO_OBSERVACAO,
                     "dna": melhor_dna, "eqm_validacao": melhor_eqm}
            with args.saida.open("x", encoding="utf-8") as arquivo:
                json.dump(dados, arquivo, indent=2, allow_nan=False)


if __name__ == "__main__":
    main()
