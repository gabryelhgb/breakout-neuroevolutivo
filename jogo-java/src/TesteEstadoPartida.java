public class TesteEstadoPartida {

    public static void main(String[] args) {

        ConfiguracaoJogo configuracao = new ConfiguracaoJogo();

        EstadoPartida primeira = new EstadoPartida(configuracao, 42);
        EstadoPartida segunda = new EstadoPartida(configuracao, 42);

        verificar(
            primeira.raqueteX == 420.0,
            "A raquete deveria começar centralizada."
        );

        verificar(
            primeira.velocidadeBolaX == segunda.velocidadeBolaX
                && primeira.velocidadeBolaY == segunda.velocidadeBolaY,
            "A mesma semente deveria produzir a mesma velocidade."
        );

        double velocidadeCalculada = Math.hypot(
            primeira.velocidadeBolaX,
            primeira.velocidadeBolaY
        );

        verificar(
            Math.abs(
                velocidadeCalculada - configuracao.velocidadeBola
            ) < 0.000001,
            "A velocidade total da bola está incorreta."
        );

        verificar(
            primeira.velocidadeBolaY < 0,
            "A bola deveria começar subindo."
        );

        primeira.pontuacao = 10;

        verificar(
            segunda.pontuacao == 0,
            "Uma partida não deveria alterar a outra."
        );

        System.out.println("Estado inicial validado!");
        System.out.println(
            "Arena: " + configuracao.largura
                + " x " + configuracao.altura
        );
        System.out.println("Raquete X: " + primeira.raqueteX);
        System.out.println("Velocidade total: " + velocidadeCalculada);
        System.out.println("Partidas independentes: OK");
    }

    private static void verificar(boolean condicao, String mensagem) {
        if (!condicao) {
            throw new IllegalStateException(mensagem);
        }
    }
}