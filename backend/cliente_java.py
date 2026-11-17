import json
import os
from pathlib import Path
import shutil
import subprocess
import threading


class ClienteJava:
    def __init__(self):
        raiz = Path(__file__).resolve().parents[1]
        pasta_java = raiz / "jogo-java"
        if not (pasta_java / "build" / "PontePython.class").is_file():
            raise RuntimeError("Compile os arquivos Java antes de iniciar o cliente.")
        executavel = shutil.which("java")
        if executavel is None:
            raise RuntimeError("Java não encontrado no PATH.")

        classpath = os.pathsep.join([
            str(pasta_java / "build"), str(pasta_java / "src")
        ])
        self._trava = threading.Lock()
        self._processo = subprocess.Popen(
            [executavel, "-Djava.awt.headless=true", "-cp", classpath, "PontePython"],
            cwd=pasta_java,
            stdin=subprocess.PIPE,
            stdout=subprocess.PIPE,
            text=True,
            encoding="utf-8",
            bufsize=1,
            creationflags=subprocess.CREATE_NO_WINDOW if os.name == "nt" else 0,
        )
        try:
            self._ler_resposta()
        except Exception:
            self.fechar()
            raise

    def _ler_resposta(self):
        linha = self._processo.stdout.readline()
        if not linha:
            raise RuntimeError("A conexão com o motor Java foi encerrada.")
        resposta = json.loads(linha)
        if not isinstance(resposta, dict):
            raise RuntimeError("Resposta inválida do motor Java.")
        if resposta.get("ok") is not True:
            raise ValueError(resposta.get("erro", "Falha no motor Java."))
        return resposta

    def _enviar(self, comando):
        with self._trava:
            if self._processo.poll() is not None:
                raise RuntimeError("O processo Java não está em execução.")
            self._processo.stdin.write(comando + "\n")
            self._processo.stdin.flush()
            return self._ler_resposta()

    @staticmethod
    def _validar_id(identificador):
        if type(identificador) is not int or not 0 <= identificador < 2000:
            raise ValueError("O identificador deve ser um inteiro de 0 a 1999.")

    def criar_partida(self, identificador, semente=42):
        self._validar_id(identificador)
        if type(semente) is not int or not -(2**63) <= semente < 2**63:
            raise ValueError("A semente deve ser um inteiro de 64 bits.")
        return self._enviar(f"CRIAR {identificador} {semente}")["estado"]

    def avancar(self, identificador, acao, passos=1):
        self._validar_id(identificador)
        if type(acao) is not int or acao not in (-1, 0, 1):
            raise ValueError("A ação deve ser -1, 0 ou 1.")
        if type(passos) is not int or not 1 <= passos <= 1000:
            raise ValueError("Use de 1 a 1000 passos inteiros.")
        return self._enviar(f"AVANCAR {identificador} {acao} {passos}")["estado"]

    def obter_estado(self, identificador):
        self._validar_id(identificador)
        return self._enviar(f"ESTADO {identificador}")["estado"]

    def remover_partida(self, identificador):
        self._validar_id(identificador)
        self._enviar(f"REMOVER {identificador}")

    def fechar(self):
        processo = self._processo
        if processo.poll() is None:
            try:
                self._enviar("SAIR")
            except (OSError, RuntimeError, ValueError):
                if processo.poll() is None:
                    processo.terminate()
            try:
                processo.wait(timeout=3)
            except subprocess.TimeoutExpired:
                processo.kill()
                processo.wait()
        for fluxo in (processo.stdin, processo.stdout):
            if fluxo is not None:
                fluxo.close()

    def __enter__(self):
        return self

    def __exit__(self, tipo, valor, traceback):
        self.fechar()