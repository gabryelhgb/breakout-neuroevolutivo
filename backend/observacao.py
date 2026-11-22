import math


VERSAO_OBSERVACAO = 1
ESCALA_VELOCIDADE = 6.0
ROTULOS_ENTRADAS = [
    f"bola_{bola}_{campo}"
    for bola in range(2)
    for campo in ("ativa", "x", "y", "vx", "vy")
] + ["raquete_propria_x", "raquete_adversaria_x"] + [
    f"defesa_{lado}_{coluna}"
    for lado in ("propria", "adversaria")
    for coluna in range(6)
]
QUANTIDADE_ENTRADAS = len(ROTULOS_ENTRADAS)


def preparar_entradas(estado, jogador, escala_velocidade=ESCALA_VELOCIDADE):
    if type(jogador) is not int or jogador not in (0, 1):
        raise ValueError("O jogador deve ser 0 (A) ou 1 (B).")
    if not math.isfinite(escala_velocidade) or escala_velocidade <= 0:
        raise ValueError("A escala de velocidade deve ser positiva e finita.")
    arena = estado["arena"]
    largura = arena["largura"]
    topo, fundo = arena["topo"], arena["fundo"]
    if not all(math.isfinite(v) for v in (largura, topo, fundo)) or largura <= 0 or fundo <= topo:
        raise ValueError("As dimensões da arena são inválidas.")
    bolas = {bola["id"]: bola for bola in estado["bolas"]}
    raquetes = {raquete["id"]: raquete for raquete in estado["raquetes"]}
    if (len(estado["bolas"]) != 2 or len(estado["raquetes"]) != 2
            or set(bolas) != {0, 1} or set(raquetes) != {0, 1}):
        raise ValueError("O estado precisa conter duas bolas e duas raquetes identificadas.")

    entradas = []
    for identificador in range(2):
        bola = bolas[identificador]
        if bola["ativa"] is not True:
            entradas.extend([0.0] * 5)
            continue
        centro_x = bola["x"] + bola["tamanho"] / 2
        centro_y = bola["y"] + bola["tamanho"] / 2
        vy = bola["vy"]
        if jogador == 1:
            centro_y = topo + fundo - centro_y
            vy = -vy
        entradas.extend([
            1.0,
            2 * centro_x / largura - 1,
            2 * (centro_y - topo) / (fundo - topo) - 1,
            bola["vx"] / escala_velocidade,
            vy / escala_velocidade,
        ])

    for identificador in (jogador, 1 - jogador):
        raquete = raquetes[identificador]
        centro = raquete["x"] + raquete["largura"] / 2
        entradas.append(2 * centro / largura - 1)

    for identificador in (jogador, 1 - jogador):
        blocos = sorted(
            (bloco for bloco in estado["blocos"] if bloco["jogador"] == identificador),
            key=lambda bloco: bloco["x"],
        )
        if len(blocos) != 6:
            raise ValueError("Cada fortaleza precisa conter seis posições de blocos.")
        entradas.extend(1.0 if bloco["resistencia"] > 0 else 0.0 for bloco in blocos)

    if len(entradas) != QUANTIDADE_ENTRADAS or not all(math.isfinite(v) for v in entradas):
        raise ValueError("O estado gerou entradas inválidas para a rede.")
    return entradas
