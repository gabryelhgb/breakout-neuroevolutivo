import argparse
import json
import random
from pathlib import Path

from avaliar_raquetes import carregar_modelo
from cliente_java import ClienteJava
from rede_neural import decidir_acao, propagar_entrada
from treinar_raquetes import observar


def partida(cliente, rede, lado, semente, oposicao, regras, reflexo):
    estado = cliente.criar_partida(0, semente, raquetes=True, regras=regras)
    while not estado["encerrada"]:
        entradas = observar(estado, lado, rede["arquitetura"][0])
        acao = decidir_acao(propagar_entrada(entradas, rede)[1])
        outro = 1 - lado
        acoes = [0, 0]
        acoes[lado] = acao
        if oposicao:
            acoes[outro] = decidir_acao(propagar_entrada(
                observar(estado, outro, oposicao["arquitetura"][0]), oposicao)[1])
        if reflexo:
            distancia = observar(estado, outro)[6] * estado["arena"]["largura"]
            acoes[outro] = (distancia > 24) - (distancia < -24)
        estado = cliente.avancar(0, acoes[0], passos=4, acao_adversario=acoes[1])
    return estado["pontos"][lado], estado["pontos"][1 - lado], estado["fortalezas"][lado]["rebatidas"]


def medir(cliente, rede, sementes, oposicao=None, regras=(12, 5, 4.0, 0.45), reflexo=False):
    dados = [partida(cliente, rede, lado, semente, oposicao, regras, reflexo)
             for semente in sementes for lado in (0, 1)]
    feitos = sum(a for a, b, r in dados)
    sofridos = sum(b for a, b, r in dados)
    rebatidas = sum(r for a, b, r in dados)
    return {
        "partidas": len(dados),
        "vitorias": sum(a > b for a, b, _ in dados),
        "empates": sum(a == b for a, b, _ in dados),
        "pontos_feitos": feitos,
        "pontos_sofridos": sofridos,
        "rebatidas": rebatidas,
        "taxa_rebatidas": round(rebatidas / max(1, rebatidas + sofridos), 4),
    }


def main():
    parser = argparse.ArgumentParser(description="Mede placar e devolucoes com troca de lados.")
    parser.add_argument("modelos", type=Path, nargs="+")
    oposicao = parser.add_mutually_exclusive_group()
    oposicao.add_argument("--contra", type=Path)
    oposicao.add_argument("--contra-reflexo", action="store_true")
    parser.add_argument("--velocidade", type=float, default=4.0)
    parser.add_argument("--duracao", type=int, default=12)
    parser.add_argument("--pares", type=int, default=12)
    parser.add_argument("--semente", type=int, default=20261122)
    args = parser.parse_args()
    if args.pares < 1:
        parser.error("--pares deve ser positivo")
    sementes = random.Random(args.semente).sample(range(2**32), args.pares)
    referencia = carregar_modelo(args.contra) if args.contra else None
    with ClienteJava() as cliente:
        for arquivo in args.modelos:
            regras = (args.duracao, 5, args.velocidade, 0.45)
            resultado = medir(cliente, carregar_modelo(arquivo), sementes, referencia,
                              regras, args.contra_reflexo)
            print(json.dumps({"modelo": str(arquivo), **resultado}), flush=True)


if __name__ == "__main__":
    main()
