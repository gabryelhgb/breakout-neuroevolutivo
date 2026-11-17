from cliente_java import ClienteJava


def verificar(condicao, mensagem):
    if not condicao:
        raise RuntimeError(mensagem)


def main():
    with ClienteJava() as cliente:
        primeira = cliente.criar_partida(0, semente=42)
        segunda = cliente.criar_partida(1, semente=42)

        verificar(primeira["protocolo"] == 2, "Protocolo incorreto.")
        verificar(len(primeira["bolas"]) == 2 and primeira["bolas_ativas"] == 2, "O duelo precisa de duas bolas.")
        verificar(len(primeira["raquetes"]) == 2, "O duelo precisa de duas raquetes.")
        verificar(len(primeira["blocos"]) == 12, "São seis blocos por jogador.")
        verificar(primeira["bolas"] == segunda["bolas"], "A semente não reproduziu o lançamento.")
        print("Conexão com o Duelo de Fortalezas: OK")

        movida = cliente.avancar(0, acao=-1, passos=10, acao_adversario=1)
        verificar(movida["raquetes"][0]["x"] == 340.0, "Movimento incorreto da raquete A.")
        verificar(movida["raquetes"][1]["x"] == 500.0, "Movimento incorreto da raquete B.")
        verificar(movida["passos"] == 10, "Quantidade de passos incorreta.")
        verificar(cliente.obter_estado(1)["passos"] == 0, "Uma partida alterou a outra.")
        print("Duas raquetes controladas pelo Python: OK")

        repetida = cliente.avancar(1, acao=-1, passos=10, acao_adversario=1)
        verificar(movida == {**repetida, "id": 0}, "Mesmas ações e semente produziram estados diferentes.")
        print("Partidas independentes e reprodução por semente: OK")

        for comando in ("AVANCAR 0 0 9 1", "AVANCAR 0 0 0 1001"):
            try:
                cliente._enviar(comando)
            except ValueError:
                pass
            else:
                raise RuntimeError("O Java aceitou um comando inválido.")
        verificar(cliente.obter_estado(0) == movida, "O comando inválido alterou o estado.")
        print("Validação de comandos no Java: OK")

        cliente.remover_partida(1)
        try:
            cliente.obter_estado(1)
        except ValueError:
            pass
        else:
            raise RuntimeError("A partida removida ainda existe.")

        ativas = 2
        for _ in range(30):
            movida = cliente.avancar(0, acao=0, passos=100, acao_adversario=0)
            verificar(movida["bolas_ativas"] <= ativas, "Uma bola perdida retornou.")
            ativas = movida["bolas_ativas"]
            if movida["encerrada"]:
                break
        verificar(movida["encerrada"], "A partida não encerrou dentro de 45 segundos de simulação.")
        verificar(cliente.avancar(0, acao=1)["eventos"] == [], "Eventos foram repetidos após o fim.")
        print("Partida completa e bolas sem reposição: OK")
        print("Resultado:", movida["resultado"])
        print("Motivo:", movida["motivo_fim"])
        print("Blocos A/B:", [f["blocos_restantes"] for f in movida["fortalezas"]])
    verificar(cliente._processo.poll() is not None, "O processo Java não encerrou.")
    print("Encerramento do processo Java: OK")


if __name__ == "__main__":
    main()
