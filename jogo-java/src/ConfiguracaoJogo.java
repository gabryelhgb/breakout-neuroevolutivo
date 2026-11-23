public class ConfiguracaoJogo {

    public final int largura;
    public final int altura;
    public final double velocidadeRaquete;

    public ConfiguracaoJogo() {
        this(960, 720, 8.0);
    }

    protected ConfiguracaoJogo(int largura, int altura, double velocidadeRaquete) {
        this.largura = largura;
        this.altura = altura;
        this.velocidadeRaquete = velocidadeRaquete;
    }

    public final int larguraRaquete = 120;
    public final int alturaRaquete = 14;

    public final int tamanhoBola = 12;
    public final double velocidadeBola = 6.0;

    public final int vidasIniciais = 3;

    public final int linhasBlocos = 4;
    public final int colunasBlocos = 7;
    public final int pontosPorBloco = 10;

    public final int totalFases = 3;
    public final int alturaBloco = 22;
    public final int espacamentoBlocos = 8;
    public final int margemLateralBlocos = 40;
    public final int margemSuperiorBlocos = 60;
}
