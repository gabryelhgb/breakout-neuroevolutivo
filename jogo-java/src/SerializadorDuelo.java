public class SerializadorDuelo {
    public static String escapar(String texto) {
        return String.valueOf(texto).replace("\\", "\\\\").replace("\"", "\\\"")
            .replace("\n", "\\n").replace("\r", "\\r");
    }

    public static String json(int id, EstadoDuelo e) {
        ConfiguracaoDuelo c = e.configuracao;
        StringBuilder j = new StringBuilder("{\"ok\":true,\"estado\":{");
        j.append("\"id\":").append(id)
            .append(",\"tipo\":\"duelo_fortalezas\",\"protocolo\":2")
            .append(",\"passos\":").append(e.passos)
            .append(",\"encerrada\":").append(e.encerrada)
            .append(",\"vencedor\":").append(e.vencedor)
            .append(",\"resultado\":\"").append(e.resultado()).append('"')
            .append(",\"motivo_fim\":\"").append(e.motivoFim).append('"')
            .append(",\"bolas_ativas\":").append(e.bolasAtivas())
            .append(",\"tempo_restante\":").append(Math.max(0,
                (c.limitePassos() - e.passos) / (double) c.passosPorSegundo))
            .append(",\"arena\":{\"largura\":").append(c.largura)
            .append(",\"altura\":").append(c.altura)
            .append(",\"topo\":").append(c.topoArena)
            .append(",\"fundo\":").append(c.fundoArena).append('}')
            .append(",\"raquetes\":[");
        for (int i = 0; i < e.raquetes.size(); i++) {
            if (i > 0) j.append(',');
            EstadoDuelo.Raquete r = e.raquetes.get(i);
            j.append("{\"id\":").append(r.id)
                .append(",\"x\":").append(r.x).append(",\"y\":").append(r.y)
                .append(",\"largura\":").append(c.larguraRaquete)
                .append(",\"altura\":").append(c.alturaRaquete).append('}');
        }
        j.append("],\"bolas\":[");
        for (int i = 0; i < e.bolas.size(); i++) {
            if (i > 0) j.append(',');
            EstadoDuelo.Bola b = e.bolas.get(i);
            j.append("{\"id\":").append(b.id).append(",\"ativa\":").append(b.ativa)
                .append(",\"x\":").append(b.x).append(",\"y\":").append(b.y)
                .append(",\"vx\":").append(b.vx).append(",\"vy\":").append(b.vy)
                .append(",\"tamanho\":").append(c.tamanhoBola).append('}');
        }
        j.append("],\"blocos\":[");
        for (int i = 0; i < e.blocos.size(); i++) {
            if (i > 0) j.append(',');
            EstadoDuelo.Defesa b = e.blocos.get(i);
            j.append("{\"id\":").append(b.id).append(",\"jogador\":").append(b.jogador)
                .append(",\"x\":").append(b.x).append(",\"y\":").append(b.y)
                .append(",\"largura\":").append(b.largura).append(",\"altura\":").append(b.altura)
                .append(",\"resistencia\":").append(b.resistencia)
                .append(",\"resistencia_inicial\":").append(b.resistenciaInicial).append('}');
        }
        j.append("],\"fortalezas\":[");
        for (int jogador = 0; jogador < 2; jogador++) {
            if (jogador > 0) j.append(',');
            j.append("{\"jogador\":").append(jogador)
                .append(",\"blocos_restantes\":").append(e.blocosRestantes(jogador))
                .append(",\"danos_causados\":").append(c.blocosPorLado - e.blocosRestantes(1 - jogador))
                .append(",\"rebatidas\":").append(e.rebatidas[jogador])
                .append(",\"bolas_perdidas\":").append(e.bolasPerdidas[jogador]).append('}');
        }
        j.append("],\"eventos\":[");
        for (int i = 0; i < e.eventos.size(); i++) {
            if (i > 0) j.append(',');
            j.append('"').append(escapar(e.eventos.get(i))).append('"');
        }
        return j.append("]}}").toString();
    }
}
