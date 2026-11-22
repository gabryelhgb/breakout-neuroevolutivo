from cliente_java import ClienteJava
from observacao import preparar_entradas
from rede_neural import (
    ARQUITETURA, QUANTIDADE_PESOS, inicializar_rede,
    propagar_entrada, decidir_acao,
)


def main():
    redes = [inicializar_rede(42), inicializar_rede(43)]
    with ClienteJava() as cliente:
        estado = cliente.criar_partida(0, semente=42)
        for _ in range(120):
            acoes = []
            for jogador, rede in enumerate(redes):
                entradas = preparar_entradas(estado, jogador)
                ocultas, saidas = propagar_entrada(entradas, rede)
                if len(entradas) != 24 or [len(c) for c in ocultas] != [16, 12]:
                    raise RuntimeError("Dimensões incorretas na integração.")
                acoes.append(decidir_acao(saidas))
            estado = cliente.avancar(0, acao=acoes[0], acao_adversario=acoes[1])
            if estado["encerrada"]:
                break
        print("Arquitetura:", " -> ".join(map(str, ARQUITETURA)))
        print("Pesos por rede, incluindo bias:", QUANTIDADE_PESOS)
        print("Passos controlados pelas duas redes:", estado["passos"])
        print("Ações recebidas pelo Java:", acoes)
        print("Integração das redes com o jogo: OK")
        print("Estas redes são aleatórias e ainda não foram treinadas no jogo.")


if __name__ == "__main__":
    main()
