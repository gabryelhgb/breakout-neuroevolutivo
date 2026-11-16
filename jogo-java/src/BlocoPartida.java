public class BlocoPartida {

    public final double x, y, largura, altura;
    public final int resistenciaInicial;
    public int resistencia;

    public BlocoPartida(
        double x, double y, double largura, double altura,
        int resistencia
    ) {
        this.x = x;
        this.y = y;
        this.largura = largura;
        this.altura = altura;
        this.resistenciaInicial = resistencia;
        this.resistencia = resistencia;
    }

    public boolean estaAtivo() {
        return resistencia > 0;
    }
}
