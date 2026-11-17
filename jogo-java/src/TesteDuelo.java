import java.util.Collections;

public class TesteDuelo {
    private static int testes;

    private static MotorDuelo novo() {
        return new MotorDuelo(new ConfiguracaoDuelo(), 42);
    }

    private static void verificar(boolean condicao, String mensagem) {
        if (!condicao) throw new IllegalStateException(mensagem);
    }

    private static void teste(String nome, Runnable executar) {
        executar.run();
        testes++;
        System.out.println(nome + ": OK");
    }

    private static void posicionarNaDefesa(MotorDuelo motor, int idBola, int jogador) {
        EstadoDuelo e = motor.estado;
        EstadoDuelo.Defesa bloco = e.blocos.get(jogador * 6);
        EstadoDuelo.Bola bola = e.bolas.get(idBola);
        bola.x = bloco.x + bloco.largura / 2 - e.configuracao.tamanhoBola / 2.0;
        bola.y = jogador == 0 ? bloco.y - e.configuracao.tamanhoBola - 1
            : bloco.y + bloco.altura + 1;
        bola.vx = 0;
        bola.vy = jogador == 0 ? 6 : -6;
    }

    public static void main(String[] args) {
        teste("Duas raquetes, duas bolas e seis blocos por lado", () -> {
            EstadoDuelo e = novo().estado;
            verificar(e.raquetes.size() == 2 && e.bolasAtivas() == 2, "Estado inicial incorreto.");
            verificar(e.blocosRestantes(0) == 6 && e.blocosRestantes(1) == 6, "Defesas incorretas.");
            verificar(e.bolas.get(0).vy == -e.bolas.get(1).vy, "Lançamentos desequilibrados.");
            verificar(e.bolas.get(0).vx == -e.bolas.get(1).vx, "Lançamentos não espelhados.");
        });

        teste("Movimento independente e limites das raquetes", () -> {
            MotorDuelo m = novo();
            m.avancar(-1, 1);
            verificar(m.estado.raquetes.get(0).x == 412 && m.estado.raquetes.get(1).x == 428,
                "Ações não foram aplicadas separadamente.");
            m.estado.raquetes.get(0).x = 1;
            m.estado.raquetes.get(1).x = 839;
            m.avancar(-1, 1);
            verificar(m.estado.raquetes.get(0).x == 0 && m.estado.raquetes.get(1).x == 840,
                "Uma raquete atravessou a lateral.");
        });

        teste("Rebotes nas duas raquetes", () -> {
            for (int jogador = 0; jogador < 2; jogador++) {
                MotorDuelo m = novo();
                EstadoDuelo e = m.estado;
                e.bolas.get(1).ativa = false;
                EstadoDuelo.Bola b = e.bolas.get(0);
                EstadoDuelo.Raquete r = e.raquetes.get(jogador);
                b.x = r.x + 60 - 6;
                b.y = jogador == 0 ? r.y - 13 : r.y + 15;
                b.vx = 0;
                b.vy = jogador == 0 ? 6 : -6;
                m.avancar(0, 0);
                verificar(jogador == 0 ? b.vy < 0 : b.vy > 0, "Rebote no sentido errado.");
                verificar(e.rebatidas[jogador] == 1, "Rebatida não contabilizada.");
            }
        });

        teste("Bloco quebra e a bola retorna para a arena", () -> {
            MotorDuelo m = novo();
            posicionarNaDefesa(m, 0, 0);
            m.avancar(0, 0);
            verificar(m.estado.blocosRestantes(0) == 5, "O bloco não quebrou.");
            verificar(m.estado.bolas.get(0).vy < 0, "A bola não retornou.");
            verificar(m.estado.eventos.contains("bloco"), "Evento de áudio ausente.");
        });

        teste("Buraco permite saída e bola perdida nunca retorna", () -> {
            MotorDuelo m = novo();
            m.estado.blocos.get(0).resistencia = 0;
            posicionarNaDefesa(m, 0, 0);
            for (int i = 0; i < 30 && m.estado.bolas.get(0).ativa; i++) m.avancar(0, 0);
            verificar(!m.estado.bolas.get(0).ativa && m.estado.bolasAtivas() == 1,
                "A primeira bola não foi removida.");
            verificar(m.estado.bolasPerdidas[0] == 1, "Saída atribuída ao lado errado.");
            for (int i = 0; i < 300; i++) {
                m.avancar(0, 0);
                verificar(!m.estado.bolas.get(0).ativa && m.estado.bolasAtivas() <= 1,
                    "Uma bola perdida reapareceu.");
            }
            verificar(m.estado.bolas.size() == 2, "Os identificadores das bolas devem ser preservados.");
        });

        teste("Destruição da fortaleza encerra imediatamente", () -> {
            MotorDuelo m = novo();
            for (EstadoDuelo.Defesa b : m.estado.blocos) if (b.jogador == 0 && b.id != 0) b.resistencia = 0;
            posicionarNaDefesa(m, 0, 0);
            m.avancar(0, 0);
            verificar(m.estado.encerrada && m.estado.vencedor == 1
                && m.estado.motivoFim.equals("fortaleza"), "Vitória incorreta.");
        });

        teste("Destruições simultâneas empatam nas duas ordens de bolas", () -> {
            for (boolean inverter : new boolean[] {false, true}) {
                MotorDuelo m = novo();
                for (EstadoDuelo.Defesa b : m.estado.blocos) if (b.id != 0 && b.id != 6) b.resistencia = 0;
                posicionarNaDefesa(m, 0, 0);
                posicionarNaDefesa(m, 1, 1);
                if (inverter) Collections.reverse(m.estado.bolas);
                m.avancar(0, 0);
                verificar(m.estado.resultado().equals("empate"), "A ordem das bolas favoreceu um lado.");
            }
        });

        teste("Última saída decide pelos blocos, não pelas bolas perdidas", () -> {
            MotorDuelo m = novo();
            m.estado.blocos.get(6).resistencia = 0;
            m.estado.bolas.get(0).y = m.estado.configuracao.fundoArena + 1;
            m.estado.bolas.get(0).vy = 6;
            m.estado.bolas.get(1).y = m.estado.configuracao.topoArena - 13;
            m.estado.bolas.get(1).vy = -6;
            m.avancar(0, 0);
            verificar(m.estado.vencedor == 0 && m.estado.motivoFim.equals("sem_bolas"),
                "O resultado deve comparar os blocos restantes.");
        });

        teste("Tempo limite e empate", () -> {
            MotorDuelo m = novo();
            m.estado.passos = m.estado.configuracao.limitePassos() - 1;
            m.avancar(0, 0);
            verificar(m.estado.resultado().equals("empate") && m.estado.motivoFim.equals("tempo"),
                "Tempo limite incorreto.");
            MotorDuelo outro = novo();
            outro.estado.blocos.get(6).resistencia = 0;
            outro.estado.passos = outro.estado.configuracao.limitePassos() - 1;
            outro.avancar(0, 0);
            verificar(outro.estado.vencedor == 0, "O tempo deve favorecer a fortaleza mais preservada.");
        });

        teste("Semente, partidas independentes e comandos inválidos", () -> {
            MotorDuelo a = novo(), b = novo();
            a.avancar(-1, 1);
            verificar(b.estado.passos == 0, "Partidas compartilharam estado.");
            b.avancar(-1, 1);
            verificar(SerializadorDuelo.json(0, a.estado).equals(SerializadorDuelo.json(0, b.estado)),
                "A semente não reproduziu o estado.");
            try {
                a.avancar(0, 2);
                throw new IllegalStateException("Ação inválida aceita.");
            } catch (IllegalArgumentException esperado) {
                verificar(a.estado.passos == 1, "O comando inválido alterou a partida.");
            }
        });

        teste("Cem partidas completas sem valores inválidos ou bolas adicionais", () -> {
            for (int semente = 0; semente < 100; semente++) {
                MotorDuelo m = new MotorDuelo(new ConfiguracaoDuelo(), semente);
                int limite = m.estado.configuracao.limitePassos();
                while (!m.estado.encerrada && m.estado.passos < limite) {
                    m.avancar(ControladorReferencia.acao(m.estado, 0), ControladorReferencia.acao(m.estado, 1));
                    for (EstadoDuelo.Bola b : m.estado.bolas) {
                        verificar(Double.isFinite(b.x) && Double.isFinite(b.y), "Posição inválida.");
                        verificar(Double.isFinite(b.vx) && Double.isFinite(b.vy), "Velocidade inválida.");
                        if (b.ativa) verificar(Math.abs(b.vy) >= 2.99, "Trajetória quase horizontal.");
                    }
                    verificar(m.estado.bolas.size() == 2, "Uma bola adicional foi criada.");
                }
                verificar(m.estado.encerrada && m.estado.passos <= limite, "Partida sem encerramento.");
                long antes = m.estado.passos;
                m.avancar(1, -1);
                verificar(m.estado.passos == antes && m.estado.eventos.isEmpty(), "Partida encerrada avançou.");
            }
        });
        System.out.println("Testes do duelo concluídos: " + testes);
    }
}
