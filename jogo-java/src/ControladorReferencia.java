public class ControladorReferencia {
    public static int acao(EstadoDuelo estado, int jogador) {
        if (jogador < 0 || jogador > 1) throw new IllegalArgumentException("Jogador inválido.");
        ConfiguracaoDuelo c = estado.configuracao;
        EstadoDuelo.Raquete r = estado.raquetes.get(jogador);
        double alvo = c.largura / 2.0, menorTempo = Double.POSITIVE_INFINITY;
        for (EstadoDuelo.Bola bola : estado.bolas) {
            if (!bola.ativa || (jogador == 0 ? bola.vy <= 0 : bola.vy >= 0)) continue;
            double plano = jogador == 0 ? r.y - c.tamanhoBola : r.y + c.alturaRaquete;
            double tempo = (plano - bola.y) / bola.vy;
            if (tempo < 0 || tempo >= menorTempo) continue;
            menorTempo = tempo;
            double limite = c.largura - c.tamanhoBola;
            double x = ((bola.x + bola.vx * tempo) % (2 * limite) + 2 * limite) % (2 * limite);
            alvo = (x > limite ? 2 * limite - x : x) + c.tamanhoBola / 2.0;
        }
        double diferenca = alvo - (r.x + c.larguraRaquete / 2.0);
        return Math.abs(diferenca) <= c.velocidadeRaquete / 2.0 ? 0 : diferenca > 0 ? 1 : -1;
    }
}
