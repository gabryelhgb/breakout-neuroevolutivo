import java.awt.*;
import java.awt.event.*;
import java.util.HashSet;
import java.util.Set;
import javax.swing.*;

public class JanelaMotorJogo extends JPanel {
    private final ConfiguracaoJogo configuracao = new ConfiguracaoJogo();
    private final Set<Integer> teclas = new HashSet<>();
    private MotorJogo motor = new MotorJogo(configuracao, 42);
    private boolean iniciado, pausado;
    private long anterior = System.nanoTime();
    private double acumulado;
    private final Timer timer;
    private static final double PASSO = 1.0 / 60.0;
    private static final Color FUNDO = new Color(10, 17, 30);
    private static final Color TEXTO = new Color(223, 233, 245);
    private static final Color CIANO = new Color(79, 210, 235);

    public JanelaMotorJogo() {
        setPreferredSize(new Dimension(configuracao.largura, configuracao.altura));
        setBackground(FUNDO);
        for (int tecla : new int[] {KeyEvent.VK_LEFT, KeyEvent.VK_RIGHT, KeyEvent.VK_A, KeyEvent.VK_D}) {
            for (boolean soltar : new boolean[] {false, true}) {
                vincular(tecla, soltar, () -> {
                    if (soltar) teclas.remove(tecla); else teclas.add(tecla);
                });
            }
        }
        vincular(KeyEvent.VK_SPACE, false, () -> {
            if (motor.estado.encerrada) reiniciar();
            else if (!iniciado) iniciado = true;
            else pausado = !pausado;
            acumulado = 0;
        });
        vincular(KeyEvent.VK_R, false, this::reiniciar);
        addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent evento) {
                requestFocusInWindow();
                if (!iniciado) iniciado = true;
            }
        });
        timer = new Timer(16, evento -> atualizar());
        timer.start();
        Som.tocarMusicaFundo("musica.wav");
    }

    private void vincular(int tecla, boolean soltar, Runnable comando) {
        String nome = tecla + "-" + soltar;
        getInputMap(WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(tecla, 0, soltar), nome);
        getActionMap().put(nome, new AbstractAction() {
            @Override public void actionPerformed(ActionEvent evento) { comando.run(); }
        });
    }

    private void reiniciar() {
        motor = new MotorJogo(configuracao, 42);
        iniciado = true;
        pausado = false;
        teclas.clear();
        acumulado = 0;
        anterior = System.nanoTime();
        Som.tocarMusicaFundo("musica.wav");
    }

    private void atualizar() {
        long agora = System.nanoTime();
        double decorrido = Math.min(0.1, (agora - anterior) / 1_000_000_000.0);
        anterior = agora;
        if (iniciado && !pausado && !motor.estado.encerrada) {
            acumulado += decorrido;
            boolean esquerda = teclas.contains(KeyEvent.VK_LEFT) || teclas.contains(KeyEvent.VK_A);
            boolean direita = teclas.contains(KeyEvent.VK_RIGHT) || teclas.contains(KeyEvent.VK_D);
            while (acumulado >= PASSO && !motor.estado.encerrada) {
                motor.avancar((direita ? 1 : 0) - (esquerda ? 1 : 0));
                for (String evento : motor.estado.eventos) {
                    if (evento.equals("venceu") || evento.equals("perdeu")) {
                        Som.pararMusicaFundo();
                    }
                    if (!evento.equals("fase")) {
                        Som.tocar(evento + ".wav");
                    }
                }
                acumulado -= PASSO;
            }
        } else acumulado = 0;
        repaint();
    }

    @Override protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            double escala = Math.min(getWidth() / 960.0, getHeight() / 720.0);
            g.translate((getWidth() - 960 * escala) / 2, (getHeight() - 720 * escala) / 2);
            g.scale(escala, escala);
            g.clipRect(0, 0, 960, 720);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            desenhar(g);
        } finally { g.dispose(); }
    }

    private void desenhar(Graphics2D g) {
        EstadoPartida e = motor.estado;
        g.setColor(FUNDO); g.fillRect(0, 0, 960, 720);
        g.setColor(new Color(20, 31, 47));
        for (int x = 0; x < 960; x += 40) g.drawLine(x, 52, x, 720);
        for (int y = 52; y < 720; y += 40) g.drawLine(0, y, 960, y);
        g.setFont(new Font("SansSerif", Font.BOLD, 17)); g.setColor(TEXTO);
        g.drawString("BREAKOUT / NEUROEVOLUTIVO", 24, 32);
        g.setFont(new Font("Monospaced", Font.PLAIN, 14)); g.setColor(CIANO);
        g.drawString(String.format("FASE %d/3   PONTOS %04d   VIDAS %d", e.fase, e.pontuacao, e.vidas), 530, 32);
        Color[] cores = {new Color(64, 182, 202), new Color(112, 111, 218), new Color(222, 143, 84)};
        for (BlocoPartida b : e.blocos) {
            if (!b.estaAtivo()) continue;
            int x = (int) Math.round(b.x), y = (int) Math.round(b.y);
            int w = (int) Math.round(b.largura), h = (int) Math.round(b.altura);
            Color cor = cores[Math.min(2, b.resistenciaInicial - 1)];
            g.setPaint(new GradientPaint(x, y, cor.brighter(), x, y + h, cor.darker()));
            g.fillRoundRect(x, y, w, h, 5, 5);
            g.setColor(new Color(255, 255, 255, 130));
            g.drawLine(x + 5, y + 2, x + w - 5, y + 2);
            for (int i = 0; i < b.resistencia; i++) g.fillOval(x + w / 2 - b.resistencia * 4 + i * 8, y + h / 2 - 2, 4, 4);
        }
        int rx = (int) Math.round(e.raqueteX), ry = (int) Math.round(e.raqueteY);
        int rw = configuracao.larguraRaquete, rh = configuracao.alturaRaquete;
        g.setColor(new Color(79, 210, 235, 25)); g.fillRoundRect(rx - 5, ry - 5, rw + 10, rh + 10, 14, 14);
        g.setPaint(new GradientPaint(rx, ry, new Color(184, 244, 255), rx, ry + rh, new Color(48, 142, 185)));
        g.fillRoundRect(rx, ry, rw, rh, 7, 7);
        int bx = (int) Math.round(e.bolaX), by = (int) Math.round(e.bolaY), tamanho = configuracao.tamanhoBola;
        g.setColor(new Color(148, 222, 255, 30)); g.fillOval(bx - 6, by - 6, tamanho + 12, tamanho + 12);
        g.setColor(new Color(235, 250, 255)); g.fillOval(bx, by, tamanho, tamanho);
        g.setColor(new Color(139, 160, 181)); g.setFont(new Font("SansSerif", Font.PLAIN, 12));
        g.drawString("← → ou A/D: mover     ESPAÇO: iniciar/pausar     R: reiniciar", 24, 703);
        g.drawString("Motor Java · 60 passos/s", 765, 703);
        if (!iniciado || pausado || e.encerrada) {
            g.setColor(new Color(10, 17, 30, 230)); g.fillRoundRect(230, 300, 500, 140, 12, 12);
            String titulo = e.encerrada ? (e.venceu ? "TRÊS FASES CONCLUÍDAS!" : "FIM DE JOGO") : pausado ? "PAUSADO" : "PRONTO PARA JOGAR?";
            g.setFont(new Font("SansSerif", Font.BOLD, 27)); g.setColor(TEXTO); centralizar(g, titulo, 351);
            g.setFont(new Font("SansSerif", Font.PLAIN, 15)); g.setColor(CIANO);
            centralizar(g, e.encerrada ? "Pressione R para tentar novamente" : pausado ? "Pressione ESPAÇO para continuar" : "ESPAÇO para iniciar · setas ou A/D para mover", 387);
            g.setFont(new Font("SansSerif", Font.PLAIN, 12)); g.setColor(new Color(139, 160, 181));
            centralizar(g, "Três fases · velocidade progressiva · blocos resistentes", 414);
        }
    }

    private void centralizar(Graphics2D g, String texto, int y) {
        g.drawString(texto, (960 - g.getFontMetrics().stringWidth(texto)) / 2, y);
    }

    public static void abrir() {
        JanelaMotorJogo painel = new JanelaMotorJogo();
        JFrame janela = new JFrame("Breakout Neuroevolutivo - Teste das 3 fases");
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
            @Override public void windowClosed(WindowEvent evento) {
                painel.timer.stop();
                Som.pararMusicaFundo();
            }
        });
        janela.setVisible(true); painel.requestFocusInWindow();
    }
}
