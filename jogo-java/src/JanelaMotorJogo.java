import java.awt.*;
import java.awt.event.*;
import java.util.HashSet;
import java.util.Set;
import javax.swing.*;

public class JanelaMotorJogo extends JPanel {
    private final ConfiguracaoDuelo configuracao = new ConfiguracaoDuelo();
    private final Set<Integer> teclas = new HashSet<>();
    private MotorDuelo motor = new MotorDuelo(configuracao, 42);
    private boolean iniciado, pausado;
    private boolean referenciaB = true;
    private boolean somAtivo;
    private long anterior = System.nanoTime();
    private double acumulado;
    private final Timer timer;
    private final double passo;
    private static final Color FUNDO = new Color(10, 17, 30);
    private static final Color TEXTO = new Color(223, 233, 245);
    private static final Color CIANO = new Color(79, 210, 235);
    private static final Color LARANJA = new Color(238, 160, 98);

    public JanelaMotorJogo() { this(true); }

    public JanelaMotorJogo(boolean somInicial) {
        somAtivo = somInicial;
        passo = 1.0 / configuracao.passosPorSegundo;
        setPreferredSize(new Dimension(configuracao.largura, configuracao.altura));
        setBackground(FUNDO);
        for (int tecla : new int[] {KeyEvent.VK_LEFT, KeyEvent.VK_RIGHT, KeyEvent.VK_A, KeyEvent.VK_D}) {
            for (boolean soltar : new boolean[] {false, true}) {
                vincular(tecla, soltar, () -> {
                    if (soltar) teclas.remove(tecla); else teclas.add(tecla);
                });
            }
        }
        vincular(KeyEvent.VK_SPACE, true, () -> {
            if (motor.estado.encerrada) reiniciar();
            else if (!iniciado) iniciado = true;
            else pausado = !pausado;
            acumulado = 0;
        });
        vincular(KeyEvent.VK_R, true, this::reiniciar);
        vincular(KeyEvent.VK_T, true, () -> { referenciaB = !referenciaB; teclas.clear(); });
        vincular(KeyEvent.VK_M, true, () -> {
            somAtivo = !somAtivo;
            if (somAtivo && !motor.estado.encerrada) Som.tocarMusicaFundo("musica.wav");
            else Som.pararMusicaFundo();
        });
        addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent evento) {
                requestFocusInWindow();
                if (!iniciado) iniciado = true;
            }
        });
        timer = new Timer(16, evento -> atualizar());
        timer.start();
        if (somAtivo) Som.tocarMusicaFundo("musica.wav");
    }

    private void vincular(int tecla, boolean soltar, Runnable comando) {
        String nome = tecla + "-" + soltar;
        getInputMap(WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(tecla, 0, soltar), nome);
        getActionMap().put(nome, new AbstractAction() {
            @Override public void actionPerformed(ActionEvent evento) { comando.run(); }
        });
    }

    private void reiniciar() {
        motor = new MotorDuelo(configuracao, 42);
        iniciado = true;
        pausado = false;
        teclas.clear();
        acumulado = 0;
        anterior = System.nanoTime();
        if (somAtivo) Som.tocarMusicaFundo("musica.wav");
    }

    private void atualizar() {
        long agora = System.nanoTime();
        double decorrido = Math.min(0.1, (agora - anterior) / 1_000_000_000.0);
        anterior = agora;
        if (iniciado && !pausado && !motor.estado.encerrada) {
            acumulado += decorrido;
            while (acumulado >= passo && !motor.estado.encerrada) {
                int acaoA = (teclas.contains(KeyEvent.VK_RIGHT) ? 1 : 0)
                    - (teclas.contains(KeyEvent.VK_LEFT) ? 1 : 0);
                int acaoB = referenciaB ? ControladorReferencia.acao(motor.estado, 1)
                    : (teclas.contains(KeyEvent.VK_D) ? 1 : 0) - (teclas.contains(KeyEvent.VK_A) ? 1 : 0);
                motor.avancar(acaoA, acaoB);
                if (motor.estado.encerrada) Som.pararMusicaFundo();
                if (somAtivo) {
                    for (String evento : new HashSet<>(motor.estado.eventos)) {
                        if (evento.equals("venceu")) Som.tocar(motor.estado.vencedor == 0 ? "venceu.wav" : "perdeu.wav");
                        else if (!evento.equals("empate")) Som.tocar(evento + ".wav");
                    }
                }
                acumulado -= passo;
            }
        } else acumulado = 0;
        repaint();
    }

    public void fechar() {
        timer.stop();
        if (somAtivo) Som.pararMusicaFundo();
    }

    @Override protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            double escala = Math.min(getWidth() / (double) configuracao.largura,
                getHeight() / (double) configuracao.altura);
            g.translate((getWidth() - configuracao.largura * escala) / 2,
                (getHeight() - configuracao.altura * escala) / 2);
            g.scale(escala, escala);
            g.clipRect(0, 0, configuracao.largura, configuracao.altura);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            desenhar(g);
        } finally { g.dispose(); }
    }

    private void desenhar(Graphics2D g) {
        EstadoDuelo e = motor.estado;
        g.setColor(FUNDO); g.fillRect(0, 0, configuracao.largura, configuracao.altura);
        g.setColor(new Color(20, 31, 47));
        for (int x = 0; x < configuracao.largura; x += 40) g.drawLine(x, configuracao.topoArena, x, configuracao.fundoArena);
        for (int y = configuracao.topoArena; y < configuracao.fundoArena; y += 40) g.drawLine(0, y, configuracao.largura, y);
        g.setColor(new Color(60, 75, 94));
        g.drawLine(0, (configuracao.topoArena + configuracao.fundoArena) / 2,
            configuracao.largura, (configuracao.topoArena + configuracao.fundoArena) / 2);
        g.setFont(new Font("SansSerif", Font.BOLD, 16));
        g.setColor(CIANO); g.drawString("A / SUA FORTALEZA", 24, 23);
        g.setColor(LARANJA); g.drawString(referenciaB ? "B / REFERÊNCIA" : "B / MANUAL", 740, 23);
        g.setFont(new Font("Monospaced", Font.PLAIN, 13));
        g.setColor(TEXTO); g.drawString(e.blocosRestantes(0) + " de 6 blocos", 24, 42);
        g.drawString(e.blocosRestantes(1) + " de 6 blocos", 740, 42);
        g.setFont(new Font("SansSerif", Font.BOLD, 17)); centralizar(g, "DUELO DE FORTALEZAS", 22);
        g.setFont(new Font("Monospaced", Font.PLAIN, 13));
        int segundos = (int) Math.ceil(Math.max(0, configuracao.limitePassos() - e.passos)
            / (double) configuracao.passosPorSegundo);
        centralizar(g, segundos + "s   |   " + e.bolasAtivas() + " bola(s)", 42);

        for (EstadoDuelo.Defesa b : e.blocos) {
            if (!b.estaAtivo()) continue;
            int x = (int) Math.round(b.x), y = (int) Math.round(b.y);
            int w = (int) Math.round(b.largura), h = (int) Math.round(b.altura);
            Color cor = b.jogador == 0 ? CIANO : LARANJA;
            g.setPaint(new GradientPaint(x, y, cor.brighter(), x, y + h, cor.darker()));
            g.fillRect(x, y, w, h);
            g.setColor(FUNDO); g.drawRect(x, y, w, h);
            g.setColor(new Color(255, 255, 255, 120));
            g.drawLine(x + 4, y + 2, x + w - 4, y + 2);
        }
        for (EstadoDuelo.Raquete r : e.raquetes) {
            int x = (int) Math.round(r.x), y = (int) Math.round(r.y);
            Color cor = r.id == 0 ? CIANO : LARANJA;
            g.setColor(new Color(cor.getRed(), cor.getGreen(), cor.getBlue(), 25));
            g.fillRoundRect(x - 5, y - 5, configuracao.larguraRaquete + 10,
                configuracao.alturaRaquete + 10, 14, 14);
            g.setPaint(new GradientPaint(x, y, cor.brighter(), x,
                y + configuracao.alturaRaquete, cor.darker()));
            g.fillRoundRect(x, y, configuracao.larguraRaquete, configuracao.alturaRaquete, 7, 7);
        }
        for (EstadoDuelo.Bola b : e.bolas) {
            if (!b.ativa) continue;
            int x = (int) Math.round(b.x), y = (int) Math.round(b.y), t = configuracao.tamanhoBola;
            g.setColor(new Color(180, 222, 255, 30)); g.fillOval(x - 6, y - 6, t + 12, t + 12);
            g.setColor(new Color(235, 250, 255)); g.fillOval(x, y, t, t);
        }
        g.setColor(new Color(139, 160, 181)); g.setFont(new Font("SansSerif", Font.PLAIN, 12));
        g.drawString("A: ←/→   |   B manual: A/D   |   ESPAÇO: iniciar/pausar   |   R: reiniciar", 24, 690);
        g.drawString("T: B referência/manual   |   M: som " + (somAtivo ? "ligado" : "desligado")
            + "   |   Bolas perdidas não retornam", 24, 708);
        if (!iniciado || pausado || e.encerrada) {
            g.setColor(new Color(10, 17, 30, 230)); g.fillRoundRect(220, 295, 520, 145, 12, 12);
            String titulo = e.encerrada ? (e.vencedor < 0 ? "EMPATE" : "VITÓRIA DE " + e.resultado())
                : pausado ? "PAUSADO" : "PROTEJA SUA FORTALEZA";
            g.setFont(new Font("SansSerif", Font.BOLD, 26)); g.setColor(TEXTO); centralizar(g, titulo, 338);
            g.setFont(new Font("SansSerif", Font.PLAIN, 15)); g.setColor(CIANO);
            centralizar(g, e.encerrada ? "Pressione R para uma nova partida"
                : pausado ? "ESPAÇO para continuar" : "ESPAÇO para iniciar · você controla a raquete de baixo", 373);
            g.setFont(new Font("SansSerif", Font.PLAIN, 12)); g.setColor(new Color(139, 160, 181));
            String detalhe = e.encerrada
                ? (e.motivoFim.equals("fortaleza") ? "Fortaleza destruída"
                    : e.motivoFim.equals("tempo") ? "Tempo esgotado" : "As duas bolas saíram")
                    + " · blocos A: " + e.blocosRestantes(0) + " / B: " + e.blocosRestantes(1)
                : "Duas bolas · seis blocos por lado · duelo de até 45 segundos";
            centralizar(g, detalhe, 405);
        }
    }

    private void centralizar(Graphics2D g, String texto, int y) {
        g.drawString(texto, (configuracao.largura - g.getFontMetrics().stringWidth(texto)) / 2, y);
    }

    public static void abrir() {
        JanelaMotorJogo painel = new JanelaMotorJogo();
        JFrame janela = new JFrame("Breakout Neuroevolutivo - Duelo de Fortalezas");
        janela.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        janela.setContentPane(painel); janela.pack();
        Rectangle area = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
        janela.setSize(Math.min(janela.getWidth(), area.width - 40), Math.min(janela.getHeight(), area.height - 40));
        janela.setMinimumSize(new Dimension(600, 480)); janela.setLocationRelativeTo(null);
        janela.addWindowFocusListener(new WindowAdapter() {
            @Override public void windowLostFocus(WindowEvent evento) {
                painel.teclas.clear();
                if (painel.iniciado && !painel.motor.estado.encerrada) painel.pausado = true;
            }
        });
        janela.addWindowListener(new WindowAdapter() {
            @Override public void windowClosed(WindowEvent evento) { painel.fechar(); }
        });
        janela.setVisible(true); painel.requestFocusInWindow();
    }
}
