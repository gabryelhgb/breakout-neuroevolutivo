import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PontePython {
    private static final Map<Integer, MotorJogo> partidas = new HashMap<>();

    public static void main(String[] args) throws Exception {
        BufferedReader entrada = new BufferedReader(
            new InputStreamReader(System.in, StandardCharsets.UTF_8));
        PrintWriter saida = new PrintWriter(
            new OutputStreamWriter(System.out, StandardCharsets.UTF_8), true);
        saida.println("{\"ok\":true,\"mensagem\":\"Motor Java pronto\"}");

        String linha;
        while ((linha = entrada.readLine()) != null) {
            try {
                String[] partes = linha.trim().split("\\s+");
                if (partes[0].equals("SAIR")) {
                    exigirQuantidade(partes, 1);
                    saida.println("{\"ok\":true}");
                    break;
                }
                switch (partes[0]) {
                    case "CRIAR": {
                        exigirQuantidade(partes, 3);
                        int id = lerId(partes[1]);
                        long semente = Long.parseLong(partes[2]);
                        MotorJogo motor = new MotorJogo(new ConfiguracaoJogo(), semente);
                        partidas.put(id, motor);
                        saida.println(estadoJson(id, motor.estado));
                        break;
                    }
                    case "AVANCAR": {
                        exigirQuantidade(partes, 4);
                        int id = lerId(partes[1]);
                        MotorJogo motor = obterPartida(id);
                        int acao = Integer.parseInt(partes[2]);
                        int passos = Integer.parseInt(partes[3]);
                        if (acao < -1 || acao > 1 || passos < 1 || passos > 1000) {
                            throw new IllegalArgumentException("Use ação -1, 0 ou 1 e de 1 a 1000 passos.");
                        }
                        List<String> eventos = new ArrayList<>();
                        motor.estado.eventos.clear();
                        for (int i = 0; i < passos && !motor.estado.encerrada; i++) {
                            motor.avancar(acao);
                            eventos.addAll(motor.estado.eventos);
                        }
                        motor.estado.eventos.clear();
                        motor.estado.eventos.addAll(eventos);
                        saida.println(estadoJson(id, motor.estado));
                        break;
                    }
                    case "ESTADO": {
                        exigirQuantidade(partes, 2);
                        int id = lerId(partes[1]);
                        saida.println(estadoJson(id, obterPartida(id).estado));
                        break;
                    }
                    case "REMOVER": {
                        exigirQuantidade(partes, 2);
                        int id = lerId(partes[1]);
                        obterPartida(id);
                        partidas.remove(id);
                        saida.println("{\"ok\":true}");
                        break;
                    }
                    default:
                        throw new IllegalArgumentException("Comando desconhecido.");
                }
            } catch (IllegalArgumentException erro) {
                saida.println("{\"ok\":false,\"erro\":\"" + escapar(erro.getMessage()) + "\"}");
            }
        }
    }

    private static void exigirQuantidade(String[] partes, int quantidade) {
        if (partes.length != quantidade) {
            throw new IllegalArgumentException("Quantidade de argumentos inválida.");
        }
    }

    private static int lerId(String valor) {
        int id = Integer.parseInt(valor);
        if (id < 0 || id >= 2000) {
            throw new IllegalArgumentException("O identificador deve estar entre 0 e 1999.");
        }
        return id;
    }

    private static MotorJogo obterPartida(int id) {
        MotorJogo motor = partidas.get(id);
        if (motor == null) {
            throw new IllegalArgumentException("Partida não encontrada.");
        }
        return motor;
    }

    private static String escapar(String texto) {
        return String.valueOf(texto).replace("\\", "\\\\")
            .replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
    }

    private static String estadoJson(int id, EstadoPartida e) {
        ConfiguracaoJogo c = e.configuracao;
        StringBuilder j = new StringBuilder();
        j.append("{\"ok\":true,\"estado\":{")
            .append("\"id\":").append(id)
            .append(",\"fase\":").append(e.fase)
            .append(",\"vidas\":").append(e.vidas)
            .append(",\"pontuacao\":").append(e.pontuacao)
            .append(",\"rebatidas\":").append(e.rebatidas)
            .append(",\"passos\":").append(e.passos)
            .append(",\"encerrada\":").append(e.encerrada)
            .append(",\"venceu\":").append(e.venceu)
            .append(",\"arena\":{\"largura\":").append(c.largura)
            .append(",\"altura\":").append(c.altura).append('}')
            .append(",\"raquete\":{\"x\":").append(e.raqueteX)
            .append(",\"y\":").append(e.raqueteY)
            .append(",\"largura\":").append(c.larguraRaquete)
            .append(",\"altura\":").append(c.alturaRaquete).append('}')
            .append(",\"bolas\":[{\"x\":").append(e.bolaX)
            .append(",\"y\":").append(e.bolaY)
            .append(",\"vx\":").append(e.velocidadeBolaX)
            .append(",\"vy\":").append(e.velocidadeBolaY)
            .append(",\"tamanho\":").append(c.tamanhoBola).append("}]")
            .append(",\"blocos\":[");
        for (int i = 0; i < e.blocos.size(); i++) {
            if (i > 0) j.append(',');
            BlocoPartida b = e.blocos.get(i);
            j.append("{\"id\":").append(i)
                .append(",\"x\":").append(b.x)
                .append(",\"y\":").append(b.y)
                .append(",\"largura\":").append(b.largura)
                .append(",\"altura\":").append(b.altura)
                .append(",\"resistencia\":").append(b.resistencia)
                .append(",\"resistencia_inicial\":").append(b.resistenciaInicial).append('}');
        }
        j.append("],\"eventos\":[");
        for (int i = 0; i < e.eventos.size(); i++) {
            if (i > 0) j.append(',');
            j.append('"').append(escapar(e.eventos.get(i))).append('"');
        }
        return j.append("]}}").toString();
    }
}