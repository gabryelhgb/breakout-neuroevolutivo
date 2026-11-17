from cliente_java import ClienteJava


def verificar(condicao, mensagem):
    if not condicao:
        raise RuntimeError(mensagem)


def main():
    with ClienteJava() as cliente:
        primeira = cliente.criar_partida(0, semente=42)
        segunda = cliente.criar_partida(1, semente=42)

        verificar(primeira["bolas"] == segunda["bolas"], "Sementes iguais deveriam repetir o lançamento.")
        verificar(len(primeira["blocos"]) == 28, "A fase inicial deveria ter 28 blocos.")
        print("Conexão com Java: OK")

        movida = cliente.avancar(0, acao=-1, passos=10)
        verificar(movida["raquete"]["x"] == 340.0, "A raquete deveria andar 80 pixels para a esquerda.")
        verificar(movida["passos"] == 10, "Quantidade de passos incorreta.")
        verificar(cliente.obter_estado(1)["passos"] == 0, "Uma partida alterou a outra.")
        print("Controle pelo Python e partidas independentes: OK")

        repetida = cliente.avancar(1, acao=-1, passos=10)
        verificar(movida == {**repetida, "id": 0}, "Mesmas ações e semente deveriam repetir o estado.")
        print("Reprodução por semente e ações: OK")

        try:
            cliente._enviar("AVANCAR 0 9 1")
        except ValueError:
            pass
        else:
            raise RuntimeError("O Java aceitou uma ação inválida.")
        verificar(cliente.obter_estado(0) == movida, "O comando inválido alterou a partida.")
        print("Validação de comandos no Java: OK")

        cliente.remover_partida(1)
        try:
            cliente.obter_estado(1)
        except ValueError:
            pass
        else:
            raise RuntimeError("A partida removida ainda existe.")

        for _ in range(100):
            movida = cliente.avancar(0, acao=0, passos=100)
            if movida["encerrada"]:
                break
        verificar(movida["encerrada"], "A partida não encerrou dentro do limite do teste.")
        verificar(cliente.avancar(0, acao=1)["eventos"] == [], "Eventos foram repetidos após o fim.")
        print("Partida completa sem janela: OK")
        print("Pontuação:", movida["pontuacao"])
        print("Vidas:", movida["vidas"])
        print("Fase:", movida["fase"])
    verificar(cliente._processo.poll() is not None, "O processo Java não foi encerrado.")
    print("Encerramento do processo Java: OK")


if __name__ == "__main__":
    main()