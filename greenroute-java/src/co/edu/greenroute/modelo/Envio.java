package co.edu.greenroute.modelo;

/** Un envío: qué mercancía, desde dónde, hasta dónde y a qué hora sale. */
public class Envio {
    private final Mercancia mercancia;
    private final Coordenada origen;
    private final Coordenada destino;
    private final int horaSalida;

    public Envio(Mercancia mercancia, Coordenada origen, Coordenada destino, int horaSalida) {
        if (horaSalida < 0 || horaSalida > 23) {
            throw new IllegalArgumentException("La hora de salida debe estar entre 0 y 23");
        }
        this.mercancia = mercancia;
        this.origen = origen;
        this.destino = destino;
        this.horaSalida = horaSalida;
    }

    public Mercancia getMercancia() { return mercancia; }
    public Coordenada getOrigen() { return origen; }
    public Coordenada getDestino() { return destino; }
    public int getHoraSalida() { return horaSalida; }

    public double distanciaLineaRectaKm() {
        return origen.distanciaMetros(destino) / 1000.0;
    }
}
