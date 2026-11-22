import javax.swing.SwingUtilities;

public class Main {
    public static void main(String[] args) {
        boolean raquetes = !java.util.Arrays.asList(args).contains("--fortalezas");
        SwingUtilities.invokeLater(() -> JanelaMotorJogo.abrir(raquetes));
    }
}
