import copy
import unittest

from observacao import preparar_entradas, ROTULOS_ENTRADAS


def estado_exemplo():
    return {
        "arena": {"largura": 960, "altura": 720, "topo": 50, "fundo": 670},
        "raquetes": [
            {"id": 0, "x": 300, "y": 564, "largura": 120, "altura": 14},
            {"id": 1, "x": 500, "y": 142, "largura": 120, "altura": 14},
        ],
        "bolas": [
            {"id": 0, "x": 200, "y": 300, "tamanho": 12, "vx": 3.6, "vy": 4.8, "ativa": True},
            {"id": 1, "x": 700, "y": 250, "tamanho": 12, "vx": -3.6, "vy": -4.8, "ativa": True},
        ],
        "blocos": [
            {"id": jogador * 6 + coluna, "jogador": jogador, "x": coluna * 160,
             "y": 632 if jogador == 0 else 66, "largura": 160, "altura": 22, "resistencia": 1}
            for jogador in range(2) for coluna in range(6)
        ],
    }


class TestObservacao(unittest.TestCase):
    def test_24_entradas_sem_adicionar_bias_ao_vetor(self):
        entradas = preparar_entradas(estado_exemplo(), 0)
        self.assertEqual(len(entradas), 24)
        self.assertEqual(len(ROTULOS_ENTRADAS), 24)
        self.assertAlmostEqual(entradas[3], 0.6)
        self.assertAlmostEqual(entradas[4], 0.8)
        self.assertAlmostEqual(entradas[10], -0.25)
        self.assertEqual(entradas[12:], [1.0] * 12)

    def test_bola_perdida_mantem_cinco_posicoes_zeradas(self):
        estado = estado_exemplo()
        estado["bolas"][0]["ativa"] = False
        self.assertEqual(preparar_entradas(estado, 0)[:5], [0.0] * 5)

    def test_blocos_destruidos_e_propria_perspectiva(self):
        estado = estado_exemplo()
        estado["blocos"][0]["resistencia"] = 0
        self.assertEqual(preparar_entradas(estado, 0)[12], 0)
        self.assertEqual(preparar_entradas(estado, 1)[18], 0)

    def test_espelhamento_vertical_preserva_o_significado(self):
        estado = estado_exemplo()
        espelhado = copy.deepcopy(estado)
        soma = estado["arena"]["topo"] + estado["arena"]["fundo"]
        for r in espelhado["raquetes"]:
            r["id"] = 1 - r["id"]
            r["y"] = soma - r["y"] - r["altura"]
        for bola in espelhado["bolas"]:
            bola["y"] = soma - bola["y"] - bola["tamanho"]
            bola["vy"] *= -1
        for bloco in espelhado["blocos"]:
            bloco["jogador"] = 1 - bloco["jogador"]
            bloco["y"] = soma - bloco["y"] - bloco["altura"]
        for a, b in zip(preparar_entradas(estado, 0), preparar_entradas(espelhado, 1)):
            self.assertAlmostEqual(a, b)

    def test_ordem_das_listas_nao_altera_o_vetor(self):
        estado = estado_exemplo()
        antes = preparar_entradas(estado, 0)
        estado["bolas"].reverse()
        estado["blocos"].reverse()
        estado["raquetes"].reverse()
        self.assertEqual(antes, preparar_entradas(estado, 0))

    def test_estado_invalido_nao_produz_entradas(self):
        with self.assertRaises(ValueError):
            preparar_entradas(estado_exemplo(), 2)
        estado = estado_exemplo()
        estado["bolas"][0]["vx"] = float("nan")
        with self.assertRaises(ValueError):
            preparar_entradas(estado, 0)


if __name__ == "__main__":
    unittest.main()
