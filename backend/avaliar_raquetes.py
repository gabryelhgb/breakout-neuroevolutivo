import argparse
import json
import random
from pathlib import Path

from cliente_java import ClienteJava
from rede_neural import dna_para_rede
from treinar_raquetes import ARQUITETURA, ARQUITETURA_ANTERIOR, jogar


def carregar_modelo(caminho):
    dados = json.loads(caminho.read_text(encoding="utf-8"))
    arquitetura = tuple(dados.get("arquitetura", ()))
    if dados.get("modo") != "raquetes" or arquitetura not in (ARQUITETURA, ARQUITETURA_ANTERIOR):
        raise ValueError(f"Modelo incompatível com o jogo de raquetes: {caminho}")
    return dna_para_rede(dados["dna"], arquitetura)


def comparar(cliente, candidata, referencia, sementes):
    rng = random.Random(0)
    resultados = []
    for semente in sementes:
        ida = jogar(cliente, (candidata, referencia), semente, rng)
        volta = jogar(cliente, (referencia, candidata), semente, rng)
        resultados.extend([(ida[0], ida[1]), (volta[1], volta[0])])
    return resultados


def main():
    parser = argparse.ArgumentParser(description="Compara duas redes sem alterar seus pesos.")
    parser.add_argument("candidata", type=Path)
    parser.add_argument("referencia", type=Path)
    parser.add_argument("--pares", type=int, default=12)
    parser.add_argument("--semente", type=int, default=20261122)
    args = parser.parse_args()
    if args.pares < 1:
        parser.error("--pares deve ser positivo")
    try:
        candidata = carregar_modelo(args.candidata)
        referencia = carregar_modelo(args.referencia)
    except (OSError, ValueError, AttributeError, KeyError, TypeError) as erro:
        parser.error(str(erro))
    sementes = random.Random(args.semente).sample(range(2**32), args.pares)
    with ClienteJava() as cliente:
        resultados = comparar(cliente, candidata, referencia, sementes)
    vitorias = sum(a > b for a, b in resultados)
    empates = sum(a == b for a, b in resultados)
    derrotas = len(resultados) - vitorias - empates
    saldo = sum(a - b for a, b in resultados)
    print(f"{len(resultados)} partidas | {vitorias} vitórias | {empates} empates | {derrotas} derrotas")
    print(f"Saldo da candidata: {saldo:+d} pontos")


if __name__ == "__main__":
    main()
