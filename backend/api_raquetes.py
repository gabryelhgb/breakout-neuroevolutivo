import argparse
import json
import random
import subprocess
import sys
import time
from http.server import BaseHTTPRequestHandler, HTTPServer
from pathlib import Path
from urllib.parse import parse_qs, urlsplit
from uuid import uuid4

from avaliar_raquetes import carregar_modelo
from cliente_java import ClienteJava
from rede_neural import decidir_acao, inicializar_rede, propagar_entrada
from treinar_raquetes import ARQUITETURA, RITMOS, ler_ritmo, observar


PASTA = Path(__file__).parent
LIMITE_ARENAS = 8
JOGO = None
TREINO = None


def treino_terminal():
    quadros = (PASTA / "treinos_locais").glob("terminal-*/ao-vivo.json")
    arquivo = max(quadros, key=lambda item: item.stat().st_mtime, default=None)
    if arquivo is None:
        return None
    if TREINO:
        processo, pasta = TREINO
        if processo.poll() is None or pasta.stat().st_mtime >= arquivo.stat().st_mtime:
            return None
    return arquivo


def status_treino():
    terminal = treino_terminal()
    if terminal:
        terminou = (terminal.parent / "concluido").exists()
        interrompido = (terminal.parent / "interrompido").exists()
        ativo = not (terminou or interrompido) and time.time() - terminal.stat().st_mtime < 15
        return {"rodando": ativo, "codigo": 0 if terminou else None if ativo else 1,
                "linhas": [], "origem": "terminal", "ritmo": ler_ritmo(terminal.with_suffix(".ritmo")),
                "pausado": ativo and terminal.with_suffix(".pausa").exists()}
    if TREINO is None:
        return {"rodando": False, "linhas": []}
    processo, pasta = TREINO
    codigo = processo.poll()
    linhas = (pasta / "treino.log").read_text(encoding="utf-8", errors="replace").splitlines()
    marcador = pasta / "concluido"
    if codigo == 0 and not marcador.exists():
        marcador.touch()
    return {"rodando": codigo is None, "codigo": codigo, "linhas": linhas[-10:],
            "ritmo": ler_ritmo(pasta / "ao-vivo.ritmo"),
            "pausado": codigo is None and (pasta / "ao-vivo.pausa").exists()}


def catalogo():
    nomes_modelos = {"modelo_validado": "Rede validada", "modelo_raquetes": "Modelo treinado",
                     "modelo_adversario": "Adversária treinada"}
    arquivos = [PASTA / f"{id}.json" for id in nomes_modelos]
    concluidos = sorted((PASTA / "treinos_locais").glob("*/concluido"),
                        key=lambda arquivo: arquivo.stat().st_mtime, reverse=True)
    pasta = concluidos[0].parent / "modelos" if concluidos else PASTA / "modelos_raquetes"
    geracoes = sorted(pasta.glob("geracao-*.json"))
    if len(geracoes) > 6:
        geracoes = [geracoes[round(i * (len(geracoes) - 1) / 5)] for i in range(6)]
    arquivos += geracoes
    if concluidos and len(geracoes) < 6:
        arquivos += sorted((PASTA / "modelos_raquetes").glob("geracao-*.json"))[:6 - len(geracoes)]
    modelos = {}
    for arquivo in arquivos:
        if not arquivo.is_file():
            continue
        if arquivo.parent == PASTA:
            nome = nomes_modelos[arquivo.stem]
        else:
            nome = f"Geração {int(arquivo.stem.split('-')[-1])}"
        identificador = arquivo.stem
        if concluidos and arquivo.parent == pasta:
            identificador += "-" + pasta.parent.name
        if concluidos and arquivo.parent != PASTA:
            nome += " · recente" if arquivo.parent == pasta else " · inicial"
        modelos[identificador] = (nome, carregar_modelo(arquivo))
        if len(modelos) == LIMITE_ARENAS - 1:
            break
    for i in range(LIMITE_ARENAS - len(modelos)):
        modelos[f"aleatoria-{i + 1}"] = (f"Aleatória {i + 1}", inicializar_rede(81000 + i, ARQUITETURA))
    return modelos


def pensar(estado, lado, rede):
    entradas = observar(estado, lado, rede['arquitetura'][0])
    ocultas, saidas = propagar_entrada(entradas, rede)
    acao = decidir_acao(saidas)
    return acao, {"camadas": [entradas, *ocultas, saidas], "acao": acao}


def quadro(estado, sinais):
    bola = estado["bolas"][0]
    return {"bola": [bola["x"], bola["y"]], "raquetes": [r["x"] for r in estado["raquetes"]],
            "pontos": estado["pontos"], "passos": estado["passos"], "sinais": sinais}


def cena(estado):
    return {"arena": estado["arena"], "bola_tamanho": estado["bolas"][0]["tamanho"],
            "raquetes": estado["raquetes"], "resultado": estado["resultado"]}


def replay(cliente, modelos, par, semente, regras):
    estado = cliente.criar_partida(0, semente, raquetes=True, regras=regras)
    quadros = []
    redes = [modelos[id][1] for id in par]
    while True:
        acoes, sinais = zip(*(pensar(estado, i, rede) for i, rede in enumerate(redes)))
        quadros.append(quadro(estado, sinais if not estado["encerrada"] else [None, None]))
        if estado["encerrada"]:
            return {**cena(estado), "quadros": quadros, "ids": par,
                    "nomes": [modelos[id][0] for id in par]}
        estado = cliente.avancar(0, acoes[0], passos=4, acao_adversario=acoes[1])


def torneio(cliente, modelos, semente, regras):
    ids = [id for id in modelos if not id.startswith("aleatoria-")]
    if len(ids) < 2:
        raise ValueError("O torneio precisa de pelo menos duas redes treinadas.")
    desempenho = {id: [0, 0] for id in ids}
    random.Random(semente).shuffle(ids)
    fases = []
    while len(ids) > 1:
        fase, sobreviventes = [], ids[-1:] if len(ids) % 2 else []
        for a, b in zip(ids[::2], ids[1::2]):
            partidas = [replay(cliente, modelos, [a, b], semente, regras),
                        replay(cliente, modelos, [b, a], semente, regras)]
            ida, volta = (p["quadros"][-1]["pontos"] for p in partidas)
            pontos = [ida[0] + volta[1], ida[1] + volta[0]]
            desempate = pontos[0] == pontos[1]
            if desempate:
                extra = [replay(cliente, modelos, [a, b], (semente + 1) % 2**32, regras),
                         replay(cliente, modelos, [b, a], (semente + 1) % 2**32, regras)]
                partidas.extend(extra)
                ida, volta = (p["quadros"][-1]["pontos"] for p in extra)
                pontos[0] += ida[0] + volta[1]
                pontos[1] += ida[1] + volta[0]
            sorteio = pontos[0] == pontos[1]
            vencedor = random.Random(f"{semente}:{a}:{b}").choice([a, b]) if sorteio else (
                a if pontos[0] > pontos[1] else b)
            sobreviventes.append(vencedor)
            desempenho[vencedor][0] += 1
            desempenho[a][1] += pontos[0] - pontos[1]
            desempenho[b][1] += pontos[1] - pontos[0]
            fase.append({"nomes": [modelos[a][0], modelos[b][0]], "pontos": pontos,
                         "vencedor": modelos[vencedor][0], "sorteio": sorteio, "partidas": partidas})
        fases.append(fase)
        ids = sobreviventes
    ranking = [ids[0], *sorted((id for id in desempenho if id != ids[0]),
                               key=desempenho.__getitem__, reverse=True)]
    return {"fases": fases, "campea": ids[0], "nome_campea": modelos[ids[0]][0], "ranking": ranking}


class API(BaseHTTPRequestHandler):
    def responder(self, status, dados):
        corpo = json.dumps(dados, allow_nan=False, separators=(",", ":")).encode()
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Cache-Control", "no-store")
        self.send_header("Content-Length", str(len(corpo)))
        self.end_headers()
        self.wfile.write(corpo)

    def do_GET(self):
        try:
            url = urlsplit(self.path)
            query = parse_qs(url.query)
            if url.path == "/treino/status":
                self.responder(200, status_treino())
                return
            if url.path == "/treino/ao-vivo":
                arquivo = treino_terminal() or (TREINO[1] / "ao-vivo.json" if TREINO else None)
                try:
                    dados = json.loads(arquivo.read_text(encoding="utf-8")) if arquivo else None
                except (FileNotFoundError, PermissionError):
                    dados = None
                self.responder(200, dados)
                return
            modelos = catalogo()
            if url.path == "/modelos":
                self.responder(200, [{"id": id, "nome": nome} for id, (nome, _) in modelos.items()])
                return
            if url.path not in ("/partida", "/arenas", "/torneio"):
                self.responder(404, {"erro": "Rota inexistente."})
                return
            semente = int(query.get("semente", ["42"])[0])
            if not 0 <= semente < 2**32:
                raise ValueError("Use uma semente de 0 a 4294967295.")
            regras = (int(query.get("duracao", ["90"])[0]), int(query.get("pontos", ["9"])[0]),
                      float(query.get("velocidade", ["4"])[0]), float(query.get("incremento", ["0.45"])[0]))
            ids = list(modelos)
            with ClienteJava() as cliente:
                if url.path == "/torneio":
                    dados = torneio(cliente, modelos, semente, regras)
                elif url.path == "/arenas":
                    dados = [replay(cliente, modelos, ids[i:i + 2], (semente + i // 2) % 2**32, regras)
                             for i in range(0, len(ids), 2)]
                else:
                    dados = replay(cliente, modelos, ids[:2], semente, regras)
            self.responder(200, dados)
        except (ValueError, OSError, RuntimeError, KeyError, TypeError, AttributeError) as erro:
            self.responder(400, {"erro": str(erro)})

    def do_POST(self):
        global JOGO, TREINO
        try:
            url = urlsplit(self.path)
            if url.path not in ("/jogar/iniciar", "/jogar/passo", "/jogar/fechar", "/treino/iniciar", "/treino/ritmo", "/treino/pausa"):
                self.responder(404, {"erro": "Rota inexistente."})
                return
            origem = self.headers.get("Origin")
            if origem and origem not in ("http://127.0.0.1:3006", "http://localhost:3006"):
                self.responder(403, {"erro": "Origem não permitida."})
                return
            tamanho = int(self.headers.get("Content-Length", "0"))
            if not 0 < tamanho <= 1024:
                raise ValueError("Corpo inválido.")
            dados = json.loads(self.rfile.read(tamanho))
            if not isinstance(dados, dict):
                raise ValueError("Corpo inválido.")
            if url.path == "/treino/pausa":
                pausa = dados.get("pausado")
                if type(pausa) is not bool or not status_treino()["rodando"]:
                    raise ValueError("O controle de pausa exige um treino ativo.")
                terminal = treino_terminal()
                arquivo = terminal.with_suffix(".pausa") if terminal else TREINO[1] / "ao-vivo.pausa"
                if pausa:
                    arquivo.touch()
                else:
                    arquivo.unlink(missing_ok=True)
                self.responder(200, status_treino())
                return

            if url.path == "/treino/ritmo":
                ritmo = dados.get("ritmo")
                if type(ritmo) is not int or ritmo not in RITMOS:
                    raise ValueError("Ritmo de treino inválido.")
                if not status_treino()["rodando"]:
                    raise ValueError("O treino atual já terminou.")
                terminal = treino_terminal()
                controle = terminal.with_suffix(".ritmo") if terminal else TREINO[1] / "ao-vivo.ritmo"
                temporario = controle.with_suffix(".ritmo.tmp")
                temporario.write_text(str(ritmo), encoding="ascii")
                temporario.replace(controle)
                self.responder(200, {**status_treino(), "ritmo": ritmo})
                return

            if url.path == "/treino/iniciar":
                status = status_treino()
                reiniciar = dados.get("reiniciar") is True
                if status["rodando"] and (not reiniciar or status.get("origem") == "terminal"):
                    raise ValueError("Encerre o treino atual antes de iniciar outro.")
                campos = {
                    "semente": (int, 0, 2**32 - 1), "geracoes": (int, 1, 20_000),
                    "rodadas": (int, 1, 40), "taxa": (float, 0.001, 1),
                    "mutacao": (float, 0, 1), "duracao": (int, 5, 180),
                    "pontos": (int, 1, 21), "velocidade": (float, 3, 12),
                    "incremento": (float, 0, 3),
                }
                comando = [sys.executable, "-B", "-X", "utf8", str(PASTA / "treinar_raquetes.py")]
                for chave, (tipo, minimo, maximo) in campos.items():
                    valor = dados[chave]
                    tipos = (int,) if tipo is int else (int, float)
                    if type(valor) not in tipos or not minimo <= valor <= maximo:
                        raise ValueError(f"Parâmetro inválido: {chave}.")
                    comando.extend([f"--{chave}", str(valor)])
                pasta = PASTA / "treinos_locais" / uuid4().hex
                ritmo = dados.get("ritmo", 4)
                if type(ritmo) is not int or ritmo not in RITMOS:
                    raise ValueError("Ritmo de treino inválido.")
                if status["rodando"]:
                    TREINO[0].terminate()
                    TREINO[0].wait(timeout=5)
                pasta.mkdir(parents=True)
                (pasta / "ao-vivo.ritmo").write_text(str(ritmo), encoding="ascii")
                with (pasta / "treino.log").open("wb") as log:
                    processo = subprocess.Popen(
                        [*comando, "--modelos", str(pasta / "modelos"),
                         "--ao-vivo", str(pasta / "ao-vivo.json"), "--ritmo", str(ritmo)],
                        cwd=PASTA, stdout=log, stderr=subprocess.STDOUT,
                        creationflags=subprocess.CREATE_NO_WINDOW if sys.platform == "win32" else 0,
                    )
                TREINO = (processo, pasta)
                self.responder(202, {"rodando": True, "linhas": [], "ritmo": ritmo})
                return
            if url.path == "/jogar/iniciar":
                nome, rede = catalogo()[dados["modelo"]]
                semente = dados["semente"]
                if type(semente) is not int or not 0 <= semente < 2**32:
                    raise ValueError("Semente inválida.")
                cliente = ClienteJava()
                try:
                    estado = cliente.criar_partida(0, semente, raquetes=True, regras=dados.get("regras"), jogador=True)
                except Exception:
                    cliente.fechar()
                    raise
                if JOGO:
                    JOGO[0].fechar()
                JOGO = (cliente, estado, nome, rede)
                self.responder(200, {**cena(estado), "nomes": ["Você", nome],
                                     "quadro": quadro(estado, [None, None])})
                return
            if url.path == "/jogar/fechar":
                if JOGO:
                    JOGO[0].fechar()
                    JOGO = None
                self.responder(200, {"ok": True})
                return
            if not JOGO:
                raise ValueError("Inicie a partida primeiro.")
            acao = dados["acao"]
            if type(acao) is not int or acao not in (-1, 0, 1):
                raise ValueError("Ação inválida.")
            cliente, estado, nome, rede = JOGO
            resposta, sinal = pensar(estado, 1, rede)
            if not estado["encerrada"]:
                estado = cliente.avancar(0, acao, passos=4, acao_adversario=resposta)
            JOGO = (cliente, estado, nome, rede)
            self.responder(200, {**cena(estado), "nomes": ["Você", nome],
                                 "quadro": quadro(estado, [None, sinal])})
        except (ValueError, OSError, RuntimeError, KeyError, TypeError) as erro:
            self.responder(400, {"erro": str(erro)})

    def log_message(self, formato, *args):
        if self.path not in ("/jogar/passo", "/treino/ao-vivo"):
            super().log_message(formato, *args)


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--porta", type=int, default=8006)
    args = parser.parse_args()
    with HTTPServer(("127.0.0.1", args.porta), API) as servidor:
        print(f"API de raquetes em http://127.0.0.1:{args.porta}", flush=True)
        try:
            servidor.serve_forever()
        finally:
            if JOGO:
                JOGO[0].fechar()
