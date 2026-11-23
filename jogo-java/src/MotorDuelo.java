public class MotorDuelo {
    public final EstadoDuelo estado;
    private final boolean raquetes;
    private final boolean acelerarComTempo;
    private final java.util.Random sorteador;

    public MotorDuelo(ConfiguracaoDuelo configuracao, long semente) {
        this(configuracao, semente, false);
    }

    public MotorDuelo(ConfiguracaoDuelo configuracao, long semente, boolean raquetes) {
        this(configuracao, semente, raquetes, false);
    }

    public MotorDuelo(ConfiguracaoDuelo configuracao, long semente, boolean raquetes, boolean acelerarComTempo) {
        this.acelerarComTempo = acelerarComTempo;
        estado = new EstadoDuelo(configuracao, semente, raquetes);
        this.raquetes = raquetes;
        sorteador = new java.util.Random(semente);
        if (raquetes) {
            estado.modoRaquetes = true;
            estado.blocos.clear();
            estado.bolas.remove(1);
            sacar(sorteador.nextBoolean() ? 1 : -1);
        }
    }

    private double velocidadeRaquetes() {
        ConfiguracaoDuelo c = estado.configuracao;
        double bonusTempo = acelerarComTempo ? 0.05 * estado.passos / c.passosPorSegundo : 0;
        return Math.min(12.0, c.velocidadeInicial + bonusTempo + c.incrementoVelocidade
            * (estado.pontos[0] + estado.pontos[1]));
    }

    private void sacar(int direcao) {
        EstadoDuelo.Bola bola = estado.bolas.get(0);
        ConfiguracaoDuelo c = estado.configuracao;
        bola.x = (c.largura - c.tamanhoBola) / 2.0;
        bola.y = (c.topoArena + c.fundoArena - c.tamanhoBola) / 2.0;
        double angulo = Math.toRadians(12 + sorteador.nextDouble() * 16);
        bola.vx = Math.sin(angulo) * velocidadeRaquetes() * (sorteador.nextBoolean() ? 1 : -1);
        bola.vy = Math.cos(angulo) * velocidadeRaquetes() * direcao;
    }

    public void avancar(int acaoA, int acaoB) {
        if (acaoA < -1 || acaoA > 1 || acaoB < -1 || acaoB > 1) {
            throw new IllegalArgumentException("As duas ações devem ser -1, 0 ou 1.");
        }
        estado.eventos.clear();
        if (estado.encerrada) return;
        moverRaquete(0, acaoA);
        moverRaquete(1, acaoB);
        estado.passos++;

        if (acelerarComTempo) {
            EstadoDuelo.Bola bola = estado.bolas.get(0);
            double fator = velocidadeRaquetes() / Math.hypot(bola.vx, bola.vy);
            bola.vx *= fator;
            bola.vy *= fator;
        }

        double velocidadeMaxima = 0;
        for (EstadoDuelo.Bola bola : estado.bolas) {
            if (bola.ativa) velocidadeMaxima = Math.max(velocidadeMaxima, Math.hypot(bola.vx, bola.vy));
        }
        int subpassos = Math.max(1, (int) Math.ceil(velocidadeMaxima / 2.0));
        movimento:
        for (int i = 0; i < subpassos; i++) {
            for (EstadoDuelo.Bola bola : estado.bolas) {
                if (!bola.ativa) continue;
                double anteriorY = bola.y;
                bola.x += bola.vx / subpassos;
                bola.y += bola.vy / subpassos;
                verificarLaterais(bola);
                verificarRaquete(bola, anteriorY);
                if (raquetes) {
                    int ponto = bola.y >= estado.configuracao.fundoArena ? 1
                        : bola.y + estado.configuracao.tamanhoBola <= estado.configuracao.topoArena ? 0 : -1;
                    if (ponto >= 0) {
                        estado.pontos[ponto]++;
                        estado.eventos.add("vida");
                        if (estado.pontos[ponto] >= estado.configuracao.pontosParaVencer) encerrar(ponto, "placar");
                        else sacar(bola.vy > 0 ? -1 : 1);
                        break movimento;
                    }
                    continue;
                }
                verificarBlocos(bola);
                verificarSaida(bola);
            }
        }
        // Só decide depois de atualizar ambas as bolas: perdas simultâneas são justas.
        if (!raquetes) verificarResultado();
        else if (!estado.encerrada && estado.passos >= estado.configuracao.limitePassos()) {
            encerrar(estado.pontos[0] == estado.pontos[1] ? -1
                : estado.pontos[0] > estado.pontos[1] ? 0 : 1, "tempo");
        }
    }

    private void moverRaquete(int jogador, int acao) {
        ConfiguracaoDuelo c = estado.configuracao;
        EstadoDuelo.Raquete r = estado.raquetes.get(jogador);
        r.x = Math.max(0, Math.min(c.largura - c.larguraRaquete,
            r.x + acao * c.velocidadeRaquete));
    }

    private void verificarLaterais(EstadoDuelo.Bola bola) {
        double limite = estado.configuracao.largura - estado.configuracao.tamanhoBola;
        if (bola.x < 0) {
            bola.x = 0;
            bola.vx = Math.abs(bola.vx);
            estado.eventos.add("parede");
        } else if (bola.x > limite) {
            bola.x = limite;
            bola.vx = -Math.abs(bola.vx);
            estado.eventos.add("parede");
        }
    }

    private void verificarRaquete(EstadoDuelo.Bola bola, double anteriorY) {
        ConfiguracaoDuelo c = estado.configuracao;
        double tamanho = c.tamanhoBola;
        for (EstadoDuelo.Raquete r : estado.raquetes) {
            boolean cruzou = r.id == 0
                ? bola.vy > 0 && anteriorY + tamanho <= r.y && bola.y + tamanho >= r.y
                : bola.vy < 0 && anteriorY >= r.y + c.alturaRaquete && bola.y <= r.y + c.alturaRaquete;
            boolean horizontal = bola.x + tamanho > r.x && bola.x < r.x + c.larguraRaquete;
            if (!cruzou || !horizontal) continue;

            bola.y = r.id == 0 ? r.y - tamanho : r.y + c.alturaRaquete;
            double impacto = (bola.x + tamanho / 2.0 - r.x - c.larguraRaquete / 2.0)
                / (c.larguraRaquete / 2.0);
            double angulo = Math.toRadians(Math.max(-1, Math.min(1, impacto)) * 60);
            double velocidade = raquetes ? velocidadeRaquetes() : c.velocidadeBola;
            bola.vx = Math.sin(angulo) * velocidade;
            bola.vy = Math.cos(angulo) * velocidade * (r.id == 0 ? -1 : 1);
            estado.rebatidas[r.id]++;
            estado.eventos.add("raquete");
            return;
        }
    }

    private void verificarBlocos(EstadoDuelo.Bola bola) {
        double tamanho = estado.configuracao.tamanhoBola;
        for (EstadoDuelo.Defesa bloco : estado.blocos) {
            if (!bloco.estaAtivo()) continue;
            boolean colidiu = bola.x + tamanho > bloco.x && bola.x < bloco.x + bloco.largura
                && bola.y + tamanho > bloco.y && bola.y < bloco.y + bloco.altura;
            if (!colidiu) continue;
            double esquerda = bola.x + tamanho - bloco.x;
            double direita = bloco.x + bloco.largura - bola.x;
            double cima = bola.y + tamanho - bloco.y;
            double baixo = bloco.y + bloco.altura - bola.y;
            if (Math.min(esquerda, direita) < Math.min(cima, baixo)) {
                bola.x = esquerda < direita ? bloco.x - tamanho : bloco.x + bloco.largura;
                bola.vx *= -1;
            } else {
                bola.y = cima < baixo ? bloco.y - tamanho : bloco.y + bloco.altura;
                bola.vy *= -1;
            }
            bloco.resistencia = 0;
            estado.eventos.add("bloco");
            return;
        }
    }

    private void verificarSaida(EstadoDuelo.Bola bola) {
        ConfiguracaoDuelo c = estado.configuracao;
        int jogador = bola.y >= c.fundoArena ? 0
            : bola.y + c.tamanhoBola <= c.topoArena ? 1 : -1;
        if (jogador >= 0) {
            bola.ativa = false;
            estado.bolasPerdidas[jogador]++;
            estado.eventos.add("vida");
        }
    }

    private void verificarResultado() {
        int a = estado.blocosRestantes(0), b = estado.blocosRestantes(1);
        if (a == 0 || b == 0) {
            encerrar(a == b ? -1 : a == 0 ? 1 : 0, "fortaleza");
        } else if (estado.bolasAtivas() == 0) {
            encerrar(a == b ? -1 : a > b ? 0 : 1, "sem_bolas");
        } else if (estado.passos >= estado.configuracao.limitePassos()) {
            encerrar(a == b ? -1 : a > b ? 0 : 1, "tempo");
        }
    }

    private void encerrar(int vencedor, String motivo) {
        estado.encerrada = true;
        estado.vencedor = vencedor;
        estado.motivoFim = motivo;
        estado.eventos.add(vencedor < 0 ? "empate" : "venceu");
    }
}
