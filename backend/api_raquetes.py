import json
import random
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from urllib.parse import parse_qs, urlsplit

from avaliar_raquetes import carregar_modelo
from cliente_java import ClienteJava
from rede_neural import decidir_acao, inicializar_rede, propagar_entrada
from treinar_raquetes import ARQUITETURA, observar


def replay(cliente, redes, semente):
    estado = cliente.criar_partida(0, semente, raquetes=True)
    quadros = []
    while True:
        bola = estado["bolas"][0]
        quadro = {
            "bola": [bola["x"], bola["y"]],
            "raquetes": [r["x"] for r in estado["raquetes"]],
            "pontos": estado["pontos"],
            "passos": estado["passos"],
        }
        quadros.append(quadro)
        if estado["encerrada"]:
            return {"arena": estado["arena"], "bola_tamanho": bola["tamanho"],
                    "raquetes": estado["raquetes"], "quadros": quadros,
                    "resultado": estado["resultado"]}
        a, b = [decidir_acao(propagar_entrada(observar(estado, i), rede)[1])
                for i, rede in enumerate(redes)]
        estado = cliente.avancar(0, a, passos=4, acao_adversario=b)


class API(BaseHTTPRequestHandler):
    def responder(self, status, dados):
        corpo = json.dumps(dados, allow_nan=False).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(corpo)))
        self.end_headers()
        self.wfile.write(corpo)

    def do_GET(self):
        url = urlsplit(self.path)
        if url.path != "/partida":
            self.responder(404, {"erro": "Rota inexistente."})
            return
        query = parse_qs(url.query)
        try:
            semente = int(query.get("semente", ["42"])[0])
            if not 0 <= semente < 2**32:
                raise ValueError()
        except ValueError:
            self.responder(400, {"erro": "Use uma semente inteira entre 0 e 4294967295."})
            return
        pasta = Path(__file__).parent
        redes, nomes = [], []
        try:
            for i, arquivo in enumerate(("modelo_raquetes.json", "modelo_adversario.json")):
                caminho = pasta / arquivo
                if caminho.is_file():
                    redes.append(carregar_modelo(caminho))
                    nomes.append("Rede treinada A" if i == 0 else "Rede treinada B")
                else:
                    redes.append(inicializar_rede(random.Random(semente + i).randrange(2**32), ARQUITETURA))
                    nomes.append("Rede aleatória A" if i == 0 else "Rede aleatória B")
            with ClienteJava() as cliente:
                dados = replay(cliente, redes, semente)
        except (ValueError, OSError, RuntimeError, KeyError, TypeError, AttributeError) as erro:
            self.responder(503, {"erro": str(erro)})
            return
        self.responder(200, {**dados, "nomes": nomes})


if __name__ == "__main__":
    with ThreadingHTTPServer(("127.0.0.1", 8006), API) as servidor:
        print("API de raquetes em http://127.0.0.1:8006", flush=True)
        servidor.serve_forever()
