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

        estado.eventos.clear();
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
            if (verificarBlocos() && verificarFimFase()) {
                return;
            }

            if (estado.bolaY >= configuracao.altura) {
                estado.vidas--;
                estado.eventos.add("vida");

                if (estado.vidas <= 0) {
                    estado.encerrada = true;
                    estado.eventos.add("perdeu");
                } else {
                    estado.reiniciarBola();
                }

                return;
            }
        }
    }

    private boolean verificarBlocos() {
        double tamanho = estado.configuracao.tamanhoBola;

        for (BlocoPartida bloco : estado.blocos) {
            if (!bloco.estaAtivo()) {
                continue;
            }

            boolean sobreposicao = (
                estado.bolaX + tamanho > bloco.x
                && estado.bolaX < bloco.x + bloco.largura
                && estado.bolaY + tamanho > bloco.y
                && estado.bolaY < bloco.y + bloco.altura
            );

            if (!sobreposicao) {
                continue;
            }

            double esquerda = estado.bolaX + tamanho - bloco.x;
            double direita = bloco.x + bloco.largura - estado.bolaX;
            double cima = estado.bolaY + tamanho - bloco.y;
            double baixo = bloco.y + bloco.altura - estado.bolaY;

            if (Math.min(esquerda, direita) < Math.min(cima, baixo)) {
                estado.bolaX = esquerda < direita
                    ? bloco.x - tamanho : bloco.x + bloco.largura;
                estado.velocidadeBolaX *= -1;
            } else {
                estado.bolaY = cima < baixo
                    ? bloco.y - tamanho : bloco.y + bloco.altura;
                estado.velocidadeBolaY *= -1;
            }

            bloco.resistencia--;
            estado.eventos.add("bloco");

            if (!bloco.estaAtivo()) {
                estado.pontuacao += estado.configuracao.pontosPorBloco;
            }

            return true;
        }

        return false;
    }

    private boolean verificarFimFase() {
        for (BlocoPartida bloco : estado.blocos) {
            if (bloco.estaAtivo()) {
                return false;
            }
        }

        if (estado.fase == estado.configuracao.totalFases) {
            estado.venceu = true;
            estado.encerrada = true;
            estado.eventos.add("venceu");
        } else {
            estado.fase++;
            estado.eventos.add("fase");
            estado.gerarBlocos();
            estado.reiniciarBola();
        }

        return true;
    }

    private void verificarParedes() {
        ConfiguracaoJogo configuracao = estado.configuracao;

        double limiteDireito = (
            configuracao.largura - configuracao.tamanhoBola
        );

        if (estado.bolaX < 0) {
            estado.eventos.add("parede");
            estado.bolaX = 0;
            estado.velocidadeBolaX = Math.abs(
                estado.velocidadeBolaX
            );
        } else if (estado.bolaX > limiteDireito) {
            estado.eventos.add("parede");
            estado.bolaX = limiteDireito;
            estado.velocidadeBolaX = -Math.abs(
                estado.velocidadeBolaX
            );
        }

        if (estado.bolaY < 0) {
            estado.eventos.add("parede");
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
            estado.eventos.add("raquete");
        }
    }
}
