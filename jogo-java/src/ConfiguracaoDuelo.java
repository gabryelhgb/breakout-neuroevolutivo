public class ConfiguracaoDuelo extends ConfiguracaoJogo {
    public final int blocosPorLado = 6;
    public final int passosPorSegundo = 60;
    public final int duracaoSegundos;
    public final int pontosParaVencer;
    public final double velocidadeInicial;
    public final double incrementoVelocidade;
    public final int topoArena = 50;
    public final int fundoArena = altura - 50;

    public ConfiguracaoDuelo() {
        this(45, 5, 6.0, 0.75);
    }

    public ConfiguracaoDuelo(int duracao, int pontos, double velocidade, double incremento) {
        this(duracao, pontos, velocidade, incremento, false);
    }

    public static ConfiguracaoDuelo paraRaquetes() {
        return new ConfiguracaoDuelo(90, 9, 4.0, 0.45, true);
    }

    public ConfiguracaoDuelo(int duracao, int pontos, double velocidade, double incremento, boolean raquetes) {
        super(raquetes ? 1120 : 960, raquetes ? 1080 : 720, raquetes ? 11.0 : 8.0);
        if (duracao < 5 || duracao > 180 || pontos < 1 || pontos > 21
            || !(velocidade >= 3 && velocidade <= 12)
            || !(incremento >= 0 && incremento <= 3)) {
            throw new IllegalArgumentException("Configuração de partida fora dos limites.");
        }
        duracaoSegundos = duracao;
        pontosParaVencer = pontos;
        velocidadeInicial = velocidade;
        incrementoVelocidade = incremento;
    }

    public int limitePassos() {
        return duracaoSegundos * passosPorSegundo;
    }
}
