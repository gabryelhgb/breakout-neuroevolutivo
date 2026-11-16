import java.util.Random;
import java.util.ArrayList;
import java.util.List;

public class EstadoPartida {

    public final ConfiguracaoJogo configuracao;
    private final Random sorteador;
    public final List<BlocoPartida> blocos = new ArrayList<>();
    public final List<String> eventos = new ArrayList<>();

    public double raqueteX;
    public double raqueteY;

    public double bolaX;
    public double bolaY;
    public double velocidadeBolaX;
    public double velocidadeBolaY;

    public int vidas;
    public int pontuacao;
    public int fase;
    public int rebatidas;

    public long passos;

    public boolean encerrada;
    public boolean venceu;

    public EstadoPartida(
        ConfiguracaoJogo configuracao,
        long semente
    ) {
        this.configuracao = configuracao;
        this.sorteador = new Random(semente);

        raqueteX = (
            configuracao.largura - configuracao.larguraRaquete
        ) / 2.0;

        raqueteY = (
            configuracao.altura
                - configuracao.alturaRaquete
                - 50
        );

        vidas = configuracao.vidasIniciais;
        pontuacao = 0;
        fase = 1;
        rebatidas = 0;
        passos = 0;

        encerrada = false;
        venceu = false;

        reiniciarBola();
        gerarBlocos();
    }

    public void gerarBlocos() {
        blocos.clear();

        int linhas = configuracao.linhasBlocos + fase - 1;
        int colunas = configuracao.colunasBlocos;
        double largura = (
            configuracao.largura
                - 2.0 * configuracao.margemLateralBlocos
                - (colunas - 1) * configuracao.espacamentoBlocos
        ) / colunas;

        for (int linha = 0; linha < linhas; linha++) {
            for (int coluna = 0; coluna < colunas; coluna++) {
                double x = configuracao.margemLateralBlocos
                    + coluna * (largura + configuracao.espacamentoBlocos);
                double y = configuracao.margemSuperiorBlocos
                    + linha * (
                        configuracao.alturaBloco
                            + configuracao.espacamentoBlocos
                    );
                int resistencia = 1 + linha % fase;

                blocos.add(new BlocoPartida(
                    x, y, largura, configuracao.alturaBloco,
                    resistencia
                ));
            }
        }
    }

    public double obterVelocidadeBola() {
        return configuracao.velocidadeBola
            * (1.0 + 0.2 * (fase - 1));
    }

    public void reiniciarBola() {
        bolaX = raqueteX
            + configuracao.larguraRaquete / 2.0
            - configuracao.tamanhoBola / 2.0;

        bolaY = raqueteY - configuracao.tamanhoBola - 10;

        double anguloGraus = 45 + sorteador.nextDouble() * 90;
        double anguloRadianos = Math.toRadians(anguloGraus);
        double velocidade = obterVelocidadeBola();

        velocidadeBolaX = Math.cos(anguloRadianos) * velocidade;
        velocidadeBolaY = -Math.sin(anguloRadianos) * velocidade;
    }
}
