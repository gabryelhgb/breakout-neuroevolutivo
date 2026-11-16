import java.util.Random;

public class EstadoPartida {
    public final ConfiguracaoJogo configuracao;

    public double raqueteX;
    public double raqueteY;

    public double bolaX;
    public double bolaY;
    public double velocidadeBolaX;
    public double velocidadeBolaY;

    public int vidas;
    public int pontuacao;
    public long passos;

    public boolean encerrada;

    public EstadoPartida(ConfiguracaoJogo configuracao, long semente) {
        this.configuracao = configuracao;

        Random sorteador = new Random(semente);

        raqueteX = (configuracao.largura - configuracao.larguraRaquete) / 2.0;
        raqueteY = configuracao.altura - configuracao.alturaRaquete - 50;

        bolaX = (configuracao.largura - configuracao.tamanhoBola) / 2.0;
        bolaY = raqueteY - configuracao.tamanhoBola - 10;

        double anguloGraus = 45 + sorteador.nextDouble() * 90;
        double anguloRadianos = Math.toRadians(anguloGraus);

        velocidadeBolaX = (Math.cos(anguloRadianos) * configuracao.velocidadeBola);
        velocidadeBolaY = -Math.sin(anguloRadianos) * configuracao.velocidadeBola;

        vidas = configuracao.vidasIniciais;
        pontuacao = 0;
        passos = 0;
        encerrada = false;
    }
}