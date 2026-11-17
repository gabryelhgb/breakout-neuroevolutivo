import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class EstadoDuelo {
    public static class Raquete {
        public final int id;
        public double x;
        public final double y;

        public Raquete(int id, double x, double y) {
            this.id = id;
            this.x = x;
            this.y = y;
        }
    }

    public static class Bola {
        public final int id;
        public double x, y, vx, vy;
        public boolean ativa = true;

        public Bola(int id, double x, double y, double vx, double vy) {
            this.id = id;
            this.x = x;
            this.y = y;
            this.vx = vx;
            this.vy = vy;
        }
    }

    public static class Defesa extends BlocoPartida {
        public final int id;
        public final int jogador;

        public Defesa(int id, int jogador, double x, double y, double largura, double altura) {
            super(x, y, largura, altura, 1);
            this.id = id;
            this.jogador = jogador;
        }
    }

    public final ConfiguracaoDuelo configuracao;
    public final List<Raquete> raquetes = new ArrayList<>();
    public final List<Bola> bolas = new ArrayList<>();
    public final List<Defesa> blocos = new ArrayList<>();
    public final List<String> eventos = new ArrayList<>();
    public final int[] rebatidas = new int[2];
    public final int[] bolasPerdidas = new int[2];
    public long passos;
    public boolean encerrada;
    public int vencedor = -1;
    public String motivoFim = "";

    public EstadoDuelo(ConfiguracaoDuelo configuracao, long semente) {
        this.configuracao = configuracao;
        double centroX = (configuracao.largura - configuracao.larguraRaquete) / 2.0;
        raquetes.add(new Raquete(0, centroX,
            configuracao.fundoArena - 92 - configuracao.alturaRaquete));
        raquetes.add(new Raquete(1, centroX, configuracao.topoArena + 92));

        double largura = configuracao.largura / (double) configuracao.blocosPorLado;
        for (int jogador = 0; jogador < 2; jogador++) {
            double y = jogador == 0
                ? configuracao.fundoArena - 16 - configuracao.alturaBloco
                : configuracao.topoArena + 16;
            for (int coluna = 0; coluna < configuracao.blocosPorLado; coluna++) {
                blocos.add(new Defesa(blocos.size(), jogador, coluna * largura,
                    y, largura, configuracao.alturaBloco));
            }
        }

        Random sorteador = new Random(semente);
        double angulo = Math.toRadians(25 + sorteador.nextDouble() * 30);
        double vx = Math.sin(angulo) * configuracao.velocidadeBola
            * (sorteador.nextBoolean() ? 1 : -1);
        double vy = Math.cos(angulo) * configuracao.velocidadeBola;
        double x = configuracao.largura / 2.0 - 90 - configuracao.tamanhoBola / 2.0;
        double y = (configuracao.topoArena + configuracao.fundoArena
            - configuracao.tamanhoBola) / 2.0;
        bolas.add(new Bola(0, x, y, vx, vy));
        bolas.add(new Bola(1, configuracao.largura - x - configuracao.tamanhoBola,
            y, -vx, -vy));
    }

    public int blocosRestantes(int jogador) {
        int total = 0;
        for (Defesa bloco : blocos) {
            if (bloco.jogador == jogador && bloco.estaAtivo()) total++;
        }
        return total;
    }

    public int bolasAtivas() {
        int total = 0;
        for (Bola bola : bolas) if (bola.ativa) total++;
        return total;
    }

    public String resultado() {
        return !encerrada ? "em_andamento" : vencedor < 0 ? "empate" : vencedor == 0 ? "A" : "B";
    }
}
