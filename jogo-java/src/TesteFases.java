public class TesteFases {

    public static void main(String[] args) {
        MotorJogo motor = new MotorJogo(new ConfiguracaoJogo(), 42);
        EstadoPartida estado = motor.estado;

        verificar(estado.blocos.size() == 28, "Quantidade incorreta na fase 1.");

        atingirPorCima(motor, estado.blocos.get(0));
        verificar(!estado.blocos.get(0).estaAtivo(), "O bloco deveria quebrar.");
        verificar(estado.pontuacao == 10, "A destruição deveria somar 10 pontos.");
        verificar(estado.velocidadeBolaY < 0, "A bola deveria rebater para cima.");
        verificar(estado.eventos.contains("bloco"), "A colisão deveria gerar o evento de bloco.");

        concluirFase(motor);
        verificar(estado.fase == 2 && estado.blocos.size() == 35,
            "A fase 2 deveria ter 35 blocos.");
        verificar(Math.abs(estado.obterVelocidadeBola() - 7.2) < 0.000001,
            "A velocidade da fase 2 está incorreta.");

        BlocoPartida resistente = estado.blocos.get(7);
        estado.blocos.get(0).resistencia = 0;
        int pontosAntes = estado.pontuacao;
        atingirPorCima(motor, resistente);
        verificar(resistente.resistencia == 1, "O bloco deveria resistir ao primeiro impacto.");
        verificar(estado.pontuacao == pontosAntes, "O bloco ainda não foi destruído.");
        atingirPorCima(motor, resistente);
        verificar(!resistente.estaAtivo(), "O segundo impacto deveria destruir o bloco.");
        verificar(estado.pontuacao == pontosAntes + 10, "Pontuação incorreta no bloco resistente.");

        concluirFase(motor);
        verificar(estado.fase == 3 && estado.blocos.size() == 42,
            "A fase 3 deveria ter 42 blocos.");
        verificar(Math.abs(estado.obterVelocidadeBola() - 8.4) < 0.000001,
            "A velocidade da fase 3 está incorreta.");
        verificar(estado.blocos.get(14).resistencia == 3,
            "Deveria existir um bloco com resistência 3.");

        concluirFase(motor);
        verificar(estado.venceu && estado.encerrada && estado.fase == 3,
            "A vitória deveria encerrar a terceira fase.");
        verificar(estado.eventos.contains("venceu"), "A vitória deveria gerar seu evento.");
        verificar(estado.vidas == 3, "Trocar de fase não deveria gastar vidas.");

        System.out.println("Blocos, resistência e pontuação: OK");
        System.out.println("Fases 1, 2 e 3: OK");
        System.out.println("Velocidades 6.0, 7.2 e 8.4: OK");
        System.out.println("Vitória ao terminar a terceira fase: OK");
    }

    private static void atingirPorCima(MotorJogo motor, BlocoPartida bloco) {
        EstadoPartida estado = motor.estado;
        estado.bolaX = bloco.x + bloco.largura / 2.0
            - estado.configuracao.tamanhoBola / 2.0;
        estado.bolaY = bloco.y - estado.configuracao.tamanhoBola - 1;
        estado.velocidadeBolaX = 0;
        estado.velocidadeBolaY = estado.obterVelocidadeBola();
        motor.avancar(0);
    }

    private static void concluirFase(MotorJogo motor) {
        for (BlocoPartida bloco : motor.estado.blocos) {
            bloco.resistencia = 0;
        }
        BlocoPartida ultimo = motor.estado.blocos.get(0);
        ultimo.resistencia = 1;
        atingirPorCima(motor, ultimo);
    }

    private static void verificar(boolean condicao, String mensagem) {
        if (!condicao) {
            throw new IllegalStateException(mensagem);
        }
    }
}
