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
    private static final Map<Integer, MotorDuelo> partidas = new HashMap<>();

    public static void main(String[] args) throws Exception {
        BufferedReader entrada = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
        PrintWriter saida = new PrintWriter(new OutputStreamWriter(System.out, StandardCharsets.UTF_8), true);
        saida.println("{\"ok\":true,\"protocolo\":2,\"jogo\":\"duelo_fortalezas\"}");
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
                    case "RAQUETES_JOGADOR":
                    case "RAQUETES":
                    case "CRIAR": {
                        boolean raquetes = !partes[0].equals("CRIAR");
                        boolean jogador = partes[0].equals("RAQUETES_JOGADOR");
                        boolean personalizado = raquetes && partes.length == 7;
                        exigirQuantidade(partes, personalizado ? 7 : 3);
                        int id = lerId(partes[1]);
                        long semente = Long.parseLong(partes[2]);
                        ConfiguracaoDuelo configuracao = personalizado
                            ? new ConfiguracaoDuelo(Integer.parseInt(partes[3]), Integer.parseInt(partes[4]),
                                Double.parseDouble(partes[5]), Double.parseDouble(partes[6]), true)
                            : raquetes ? ConfiguracaoDuelo.paraRaquetes() : new ConfiguracaoDuelo();
                        MotorDuelo motor = new MotorDuelo(configuracao, semente, raquetes, jogador);
                        partidas.put(id, motor);
                        saida.println(SerializadorDuelo.json(id, motor.estado));
                        break;
                    }
                    case "AVANCAR": {
                        exigirQuantidade(partes, 5);
                        int id = lerId(partes[1]);
                        MotorDuelo motor = obterPartida(id);
                        int acaoA = Integer.parseInt(partes[2]);
                        int acaoB = Integer.parseInt(partes[3]);
                        int passos = Integer.parseInt(partes[4]);
                        if (acaoA < -1 || acaoA > 1 || acaoB < -1 || acaoB > 1
                            || passos < 1 || passos > 1000) {
                            throw new IllegalArgumentException("Use ações -1, 0 ou 1 e de 1 a 1000 passos.");
                        }
                        List<String> eventos = new ArrayList<>();
                        motor.estado.eventos.clear();
                        for (int i = 0; i < passos && !motor.estado.encerrada; i++) {
                            motor.avancar(acaoA, acaoB);
                            eventos.addAll(motor.estado.eventos);
                        }
                        motor.estado.eventos.clear();
                        motor.estado.eventos.addAll(eventos);
                        saida.println(SerializadorDuelo.json(id, motor.estado));
                        break;
                    }
                    case "ESTADO": {
                        exigirQuantidade(partes, 2);
                        int id = lerId(partes[1]);
                        saida.println(SerializadorDuelo.json(id, obterPartida(id).estado));
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
                saida.println("{\"ok\":false,\"erro\":\"" + SerializadorDuelo.escapar(erro.getMessage()) + "\"}");
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

    private static MotorDuelo obterPartida(int id) {
        MotorDuelo motor = partidas.get(id);
        if (motor == null) throw new IllegalArgumentException("Partida não encontrada.");
        return motor;
    }
}
