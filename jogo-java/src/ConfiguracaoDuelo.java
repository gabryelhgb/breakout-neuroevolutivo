public class ConfiguracaoDuelo extends ConfiguracaoJogo {
    public final int blocosPorLado = 6;
    public final int passosPorSegundo = 60;
    public final int duracaoSegundos = 45;
    public final int topoArena = 50;
    public final int fundoArena = altura - 50;

    public int limitePassos() {
        return duracaoSegundos * passosPorSegundo;
    }
}
