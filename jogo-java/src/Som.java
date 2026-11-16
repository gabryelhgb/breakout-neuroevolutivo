import java.net.URL;
import javax.sound.sampled.*;

public class Som {
    private static Clip musicaFundo;

    public static void tocar(String nomeArquivo) {
        try {
            URL url = Som.class.getResource("/audio/" + nomeArquivo);
            if (url == null) return;
            Clip clip = AudioSystem.getClip();
            try (AudioInputStream audioIn = AudioSystem.getAudioInputStream(url)) {
                clip.open(audioIn);
            }
            clip.addLineListener(evento -> {
                if (evento.getType() == LineEvent.Type.STOP && clip.isOpen()) {
                    clip.close();
                }
            });

            // Se for o som de bloco, aumenta o volume
            if (nomeArquivo.equals("bloco.wav")) {
                ajustarVolume(clip, +2.0f);
            }
            // Se for o som de parede, aumenta o volume
            if (nomeArquivo.equals("parede.wav")) {
                ajustarVolume(clip, -8.0f);
            }

            clip.start();
        } catch (Exception e) {
            System.err.println("Erro ao tocar som: " + nomeArquivo);
        }
    }

    public static void tocarMusicaFundo(String nomeArquivo) {
        pararMusicaFundo();
        try {
            URL url = Som.class.getResource("/audio/" + nomeArquivo);
            if (url == null) return;
            musicaFundo = AudioSystem.getClip();
            try (AudioInputStream audioIn = AudioSystem.getAudioInputStream(url)) {
                musicaFundo.open(audioIn);
            }

            // Diminui o volume da música de fundo (-10.0f é mais baixo, ajuste como quiser)
            ajustarVolume(musicaFundo, -20.0f);

            musicaFundo.loop(Clip.LOOP_CONTINUOUSLY);
            musicaFundo.start();
        } catch (Exception e) {
            System.err.println("Erro ao tocar música de fundo: " + nomeArquivo);
        }
    }

    public static void pararMusicaFundo() {
        if (musicaFundo != null) {
            musicaFundo.stop();
            musicaFundo.close();
            musicaFundo = null;
        }
    }

    private static void ajustarVolume(Clip clip, float ganho) {
        if (clip.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
            FloatControl volume = (FloatControl) clip.getControl(FloatControl.Type.MASTER_GAIN);
            volume.setValue(Math.max(volume.getMinimum(), Math.min(volume.getMaximum(), ganho)));
        }
    }
}
