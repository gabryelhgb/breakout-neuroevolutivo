public class MotorJogo {

    public final EstadoPartida estado;

    public MotorJogo(
        ConfiguracaoJogo configuracao,
        long semente
    ) {
        estado = new EstadoPartida(configuracao, semente);
    }

    public void avancar(int acao) {
        if (acao < -1 || acao > 1) {
            throw new IllegalArgumentException(
                "A ação deve ser -1, 0 ou 1."
            );
        }

        if (estado.encerrada) {
            return;
        }

        ConfiguracaoJogo configuracao = estado.configuracao;

        estado.raqueteX += acao * configuracao.velocidadeRaquete;

        estado.raqueteX = Math.max(
            0,
            Math.min(
                estado.raqueteX,
                configuracao.largura - configuracao.larguraRaquete
            )
        );

        estado.passos++;

        double velocidade = Math.hypot(
            estado.velocidadeBolaX,
            estado.velocidadeBolaY
        );

        int subpassos = Math.max(
            1,
            (int) Math.ceil(velocidade / 2.0)
        );

        for (int i = 0; i < subpassos; i++) {
            double yAnterior = estado.bolaY;

            estado.bolaX += estado.velocidadeBolaX / subpassos;
            estado.bolaY += estado.velocidadeBolaY / subpassos;

            verificarParedes();
            verificarRaquete(yAnterior);

            if (estado.bolaY >= configuracao.altura) {
                estado.vidas--;

                if (estado.vidas <= 0) {
                    estado.encerrada = true;
                } else {
                    estado.reiniciarBola();
                }

                return;
            }
        }
    }

    private void verificarParedes() {
        ConfiguracaoJogo configuracao = estado.configuracao;

        double limiteDireito = (
            configuracao.largura - configuracao.tamanhoBola
        );

        if (estado.bolaX < 0) {
            estado.bolaX = 0;
            estado.velocidadeBolaX = Math.abs(
                estado.velocidadeBolaX
            );
        } else if (estado.bolaX > limiteDireito) {
            estado.bolaX = limiteDireito;
            estado.velocidadeBolaX = -Math.abs(
                estado.velocidadeBolaX
            );
        }

        if (estado.bolaY < 0) {
            estado.bolaY = 0;
            estado.velocidadeBolaY = Math.abs(
                estado.velocidadeBolaY
            );
        }
    }

    private void verificarRaquete(double yAnterior) {
        ConfiguracaoJogo configuracao = estado.configuracao;

        double tamanho = configuracao.tamanhoBola;

        boolean descendo = estado.velocidadeBolaY > 0;

        boolean cruzouTopo = (
            yAnterior + tamanho <= estado.raqueteY
            && estado.bolaY + tamanho >= estado.raqueteY
        );

        boolean sobreposicaoHorizontal = (
            estado.bolaX + tamanho > estado.raqueteX
            && estado.bolaX
                < estado.raqueteX + configuracao.larguraRaquete
        );

        if (descendo && cruzouTopo && sobreposicaoHorizontal) {
            estado.bolaY = estado.raqueteY - tamanho;

            double centroBola = estado.bolaX + tamanho / 2.0;

            double centroRaquete = (
                estado.raqueteX
                    + configuracao.larguraRaquete / 2.0
            );

            double impacto = (
                (centroBola - centroRaquete)
                    / (configuracao.larguraRaquete / 2.0)
            );

            impacto = Math.max(-1.0, Math.min(1.0, impacto));

            double angulo = Math.toRadians(impacto * 60);
            double velocidade = estado.obterVelocidadeBola();

            estado.velocidadeBolaX = (
                Math.sin(angulo) * velocidade
            );

            estado.velocidadeBolaY = (
                -Math.cos(angulo) * velocidade
            );

            estado.rebatidas++;
        }
    }
}