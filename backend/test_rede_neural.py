import copy
import unittest

from rede_neural import (
    ARQUITETURA, QUANTIDADE_PESOS, inicializar_rede, propagar_entrada,
    funcao_ativacao, decidir_acao, saida_desejada, calcular_gradientes,
    erro_quadratico_medio, criar_variacoes_zeradas, treinar_amostra,
    calcular_eqm, rede_para_dna, dna_para_rede, validar_rede,
)


def achatar(camadas):
    return [valor for camada in camadas for neuronio in camada for valor in neuronio]


class TestRedeNeural(unittest.TestCase):
    def test_duas_camadas_ocultas_e_643_pesos(self):
        rede = inicializar_rede()
        validar_rede(rede)
        self.assertEqual(ARQUITETURA, (24, 16, 12, 3))
        self.assertEqual(QUANTIDADE_PESOS, 643)
        self.assertEqual(len(rede_para_dna(rede)), 643)
        ocultas, saidas = propagar_entrada([0.1] * 24, rede)
        self.assertEqual([len(camada) for camada in ocultas], [16, 12])
        self.assertEqual(len(saidas), 3)

    def test_mesma_ativacao_bipolar_estavel_do_exercicio_anterior(self):
        self.assertEqual(funcao_ativacao(0), 0)
        self.assertEqual(funcao_ativacao(10000), 1)
        self.assertEqual(funcao_ativacao(-10000), -1)
        self.assertAlmostEqual(funcao_ativacao(2), -funcao_ativacao(-2))

    def test_argmax_escolhe_uma_acao_e_empate_prefere_parar(self):
        self.assertEqual(decidir_acao([0.8, 0.1, -0.2]), -1)
        self.assertEqual(decidir_acao([-0.8, -0.1, -0.2]), 0)
        self.assertEqual(decidir_acao([-0.8, -0.1, 0.2]), 1)
        self.assertEqual(decidir_acao([0, 0, 0]), 0)
        self.assertEqual(saida_desejada(-1), [1, -1, -1])
        self.assertEqual(saida_desejada(0), [-1, 1, -1])
        self.assertEqual(saida_desejada(1), [-1, -1, 1])

    def test_dna_reconstroi_rede_sem_compartilhar_pesos(self):
        rede = inicializar_rede(42)
        copia = dna_para_rede(rede_para_dna(rede))
        self.assertEqual(rede, copia)
        self.assertEqual(rede, inicializar_rede(42))
        copia["camadas"][0][0][0] += 1
        self.assertNotEqual(rede, copia)
        with self.assertRaises(ValueError):
            dna_para_rede([float("nan")] * 643)

    def test_backpropagation_contra_derivadas_numericas_em_todos_os_pesos(self):
        rede = inicializar_rede(7)
        entradas = [(i - 12) / 24 for i in range(24)]
        desejado = saida_desejada(1)
        gradientes, _ = calcular_gradientes(entradas, desejado, rede)
        epsilon = 0.00001
        comparacoes = 0
        for c, camada in enumerate(rede["camadas"]):
            for n, neuronio in enumerate(camada):
                for p in range(len(neuronio)):
                    original = neuronio[p]
                    try:
                        neuronio[p] = original + epsilon
                        _, acima = propagar_entrada(entradas, rede)
                        neuronio[p] = original - epsilon
                        _, abaixo = propagar_entrada(entradas, rede)
                    finally:
                        neuronio[p] = original
                    numerico = (erro_quadratico_medio(acima, desejado)
                                - erro_quadratico_medio(abaixo, desejado)) / (2 * epsilon)
                    self.assertAlmostEqual(gradientes[c][n][p], numerico, delta=0.000001)
                    comparacoes += 1
        self.assertEqual(comparacoes, 643)

    def test_momento_utiliza_a_variacao_anterior(self):
        rede = inicializar_rede()
        anteriores = criar_variacoes_zeradas(rede)
        entradas = [0.2] * 24
        alvo = saida_desejada(-1)
        treinar_amostra(entradas, alvo, rede, anteriores, taxa=0.1, momento=0.3)
        pesos_antes = rede_para_dna(rede)
        variacoes_antes = achatar(copy.deepcopy(anteriores))
        gradientes, _ = calcular_gradientes(entradas, alvo, rede)
        esperado = [p - 0.1 * g + 0.3 * v
                    for p, g, v in zip(pesos_antes, achatar(gradientes), variacoes_antes)]
        treinar_amostra(entradas, alvo, rede, anteriores, taxa=0.1, momento=0.3)
        for obtido, correto in zip(rede_para_dna(rede), esperado):
            self.assertAlmostEqual(obtido, correto)

    def test_aprende_tres_exemplos_sinteticos(self):
        amostras = []
        for x, acao in ((-0.8, -1), (0.0, 0), (0.8, 1)):
            entradas = [0.0] * 24
            entradas[0], entradas[1] = 1.0, x
            amostras.append((entradas, saida_desejada(acao)))
        rede = inicializar_rede(42)
        anteriores = criar_variacoes_zeradas(rede)
        inicial = calcular_eqm(amostras, rede)
        for _ in range(800):
            for entradas, desejado in amostras:
                treinar_amostra(entradas, desejado, rede, anteriores, taxa=0.15, momento=0.05)
        final = calcular_eqm(amostras, rede)
        self.assertLess(final, inicial / 10)
        self.assertLess(final, 0.1)
        self.assertEqual([decidir_acao(propagar_entrada(x, rede)[1]) for x, _ in amostras], [-1, 0, 1])

    def test_rejeita_entradas_e_parametros_invalidos(self):
        rede = inicializar_rede()
        with self.assertRaises(ValueError):
            propagar_entrada([0.0] * 23, rede)
        with self.assertRaises(ValueError):
            propagar_entrada([float("nan")] * 24, rede)
        with self.assertRaises(ValueError):
            treinar_amostra([0.0] * 24, saida_desejada(0), rede,
                            criar_variacoes_zeradas(rede), taxa=0)


if __name__ == "__main__":
    unittest.main()
