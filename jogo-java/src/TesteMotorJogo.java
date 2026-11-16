public class TesteMotorJogo {

    public static void main(String[] args) {
        ConfiguracaoJogo configuracao = new ConfiguracaoJogo();

        MotorJogo motor = new MotorJogo(configuracao, 42);
        EstadoPartida estado = motor.estado;

        estado.raqueteX = 1;
        motor.avancar(-1);

        verificar(
            estado.raqueteX == 0,
            "A raquete ultrapassou a parede esquerda."
        );

        estado.raqueteX = (
            configuracao.largura
                - configuracao.larguraRaquete
                - 1
        );

        motor.avancar(1);

        verificar(
            estado.raqueteX
                == configuracao.largura - configuracao.larguraRaquete,
            "A raquete ultrapassou a parede direita."
        );

        estado.bolaX = estado.raqueteX
            + configuracao.larguraRaquete / 2.0
            - configuracao.tamanhoBola / 2.0;

        estado.bolaY = (
            estado.raqueteY - configuracao.tamanhoBola - 1
        );

        estado.velocidadeBolaX = 0;
        estado.velocidadeBolaY = 6;

        motor.avancar(0);

        verificar(
            estado.velocidadeBolaY < 0,
            "A bola deveria subir após atingir a raquete."
        );

        verificar(
            estado.rebatidas == 1,
            "A rebatida deveria ser contabilizada."
        );

        for (int i = 0; i < configuracao.vidasIniciais; i++) {
            estado.bolaY = configuracao.altura;
            estado.velocidadeBolaX = 0;
            estado.velocidadeBolaY = 1;

            motor.avancar(0);
        }

        verificar(
            estado.vidas == 0 && estado.encerrada,
            "A partida deveria encerrar após perder as vidas."
        );

        long passosAntes = estado.passos;
        motor.avancar(1);

        verificar(
            estado.passos == passosAntes,
            "Uma partida encerrada não deveria avançar."
        );

        System.out.println("Motor validado!");
        System.out.println("Limites da raquete: OK");
        System.out.println("Rebote e contagem de rebatidas: OK");
        System.out.println("Perda de vidas e encerramento: OK");
    }

    private static void verificar(boolean condicao, String mensagem) {
        if (!condicao) {
            throw new IllegalStateException(mensagem);
        }
    }
}