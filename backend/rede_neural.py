import math
import random

from observacao import QUANTIDADE_ENTRADAS


ARQUITETURA = (QUANTIDADE_ENTRADAS, 16, 12, 3)
ACOES = (-1, 0, 1)
QUANTIDADE_PESOS = sum(
    (entradas + 1) * saidas
    for entradas, saidas in zip(ARQUITETURA, ARQUITETURA[1:])
)


def funcao_ativacao(valor):
    if valor >= 0:
        return 2 / (1 + math.exp(-valor)) - 1
    exponencial = math.exp(valor)
    return (exponencial - 1) / (exponencial + 1)


def derivada_pela_saida(saida):
    return 0.5 * (1 + saida) * (1 - saida)


def inicializar_rede(semente=42):
    sorteador = random.Random(semente)
    camadas = []
    for entradas, saidas in zip(ARQUITETURA, ARQUITETURA[1:]):
        limite = math.sqrt(6 / (entradas + saidas))
        camada = []
        for _ in range(saidas):
            pesos = [sorteador.uniform(-limite, limite) for _ in range(entradas)]
            pesos.append(0.0)
            camada.append(pesos)
        camadas.append(camada)
    return {"arquitetura": list(ARQUITETURA), "camadas": camadas}


def _validar_vetor(valores, tamanho, nome):
    if len(valores) != tamanho or any(
        isinstance(v, bool) or not isinstance(v, (int, float)) or not math.isfinite(v)
        for v in valores
    ):
        raise ValueError(f"{nome} precisa conter {tamanho} números finitos.")


def validar_rede(rede):
    if rede.get("arquitetura") != list(ARQUITETURA):
        raise ValueError("A rede precisa ter arquitetura 24 → 16 → 12 → 3.")
    camadas = rede.get("camadas", [])
    if len(camadas) != len(ARQUITETURA) - 1:
        raise ValueError("Quantidade de camadas inválida.")
    for indice, camada in enumerate(camadas):
        if len(camada) != ARQUITETURA[indice + 1]:
            raise ValueError("Quantidade de neurônios inválida.")
        for neuronio in camada:
            _validar_vetor(neuronio, ARQUITETURA[indice] + 1, "Neurônio")


def propagar_entrada(entradas, rede):
    _validar_vetor(entradas, ARQUITETURA[0], "Entrada")
    valores = entradas
    ativacoes = []
    for camada in rede["camadas"]:
        saidas = []
        for pesos in camada:
            soma = pesos[-1]
            for indice, entrada in enumerate(valores):
                soma += entrada * pesos[indice]
            saidas.append(funcao_ativacao(soma))
        ativacoes.append(saidas)
        valores = saidas
    return ativacoes[:-1], ativacoes[-1]


def decidir_acao(saidas):
    _validar_vetor(saidas, 3, "Saída")
    maior = max(saidas)
    if saidas[1] == maior:
        return 0
    return -1 if saidas[0] == maior else 1


def saida_desejada(acao):
    if type(acao) is not int or acao not in ACOES:
        raise ValueError("A ação desejada deve ser -1, 0 ou 1.")
    return [1.0 if valor == acao else -1.0 for valor in ACOES]


def erro_quadratico_medio(previsto, desejado):
    _validar_vetor(previsto, 3, "Saída prevista")
    _validar_vetor(desejado, 3, "Saída desejada")
    return sum((p - d) ** 2 for p, d in zip(previsto, desejado)) / 3


def criar_variacoes_zeradas(rede):
    return [[[0.0] * len(neuronio) for neuronio in camada] for camada in rede["camadas"]]


def calcular_gradientes(entradas, desejado, rede):
    _validar_vetor(desejado, 3, "Saída desejada")
    if any(valor < -1 or valor > 1 for valor in desejado):
        raise ValueError("Os alvos devem estar entre -1 e 1.")
    ocultas, previsto = propagar_entrada(entradas, rede)
    ativacoes = [entradas, *ocultas, previsto]
    camadas = rede["camadas"]
    deltas = [[] for _ in camadas]

    deltas[-1] = [
        (2 / len(previsto)) * (p - d) * derivada_pela_saida(p)
        for p, d in zip(previsto, desejado)
    ]
    for camada in range(len(camadas) - 2, -1, -1):
        for neuronio, saida in enumerate(ativacoes[camada + 1]):
            soma = 0.0
            for proximo, delta in enumerate(deltas[camada + 1]):
                soma += camadas[camada + 1][proximo][neuronio] * delta
            deltas[camada].append(soma * derivada_pela_saida(saida))

    gradientes = []
    for camada, deltas_camada in enumerate(deltas):
        gradientes.append([
            [delta * valor for valor in ativacoes[camada]] + [delta]
            for delta in deltas_camada
        ])
    return gradientes, previsto


def treinar_amostra(entradas, desejado, rede, anteriores, taxa=0.05, momento=0.05):
    if not math.isfinite(taxa) or not 0 < taxa <= 1:
        raise ValueError("A taxa deve estar entre 0 (exclusivo) e 1.")
    if not math.isfinite(momento) or not 0 <= momento < 1:
        raise ValueError("O momento deve estar entre 0 e 1 (exclusivo).")
    gradientes, previsto = calcular_gradientes(entradas, desejado, rede)
    # Calcula todos os deltas com os pesos antigos antes de atualizar as camadas.
    for camada, gradiente_camada in enumerate(gradientes):
        for neuronio, gradiente_neuronio in enumerate(gradiente_camada):
            for peso, gradiente in enumerate(gradiente_neuronio):
                variacao = -taxa * gradiente + momento * anteriores[camada][neuronio][peso]
                rede["camadas"][camada][neuronio][peso] += variacao
                anteriores[camada][neuronio][peso] = variacao
    return erro_quadratico_medio(previsto, desejado)


def calcular_eqm(amostras, rede):
    if not amostras:
        raise ValueError("Forneça pelo menos uma amostra.")
    soma = 0.0
    for entradas, desejado in amostras:
        _, previsto = propagar_entrada(entradas, rede)
        soma += erro_quadratico_medio(previsto, desejado)
    return soma / len(amostras)


def rede_para_dna(rede):
    validar_rede(rede)
    return [peso for camada in rede["camadas"] for neuronio in camada for peso in neuronio]


def dna_para_rede(dna):
    _validar_vetor(dna, QUANTIDADE_PESOS, "DNA")
    camadas = []
    inicio = 0
    for entradas, saidas in zip(ARQUITETURA, ARQUITETURA[1:]):
        camada = []
        for _ in range(saidas):
            fim = inicio + entradas + 1
            camada.append([float(peso) for peso in dna[inicio:fim]])
            inicio = fim
        camadas.append(camada)
    return {"arquitetura": list(ARQUITETURA), "camadas": camadas}
