package co.edu.greenroute.red;

import co.edu.greenroute.modelo.Coordenada;
import co.edu.greenroute.ruteo.ContextoViaje;

import java.util.List;

/** Tramo de calle: su velocidad es el límite de la vía por el factor de tráfico de la hora. */
public class TramoVial extends Tramo {
    private final TipoVia tipoVia;
    private final double velocidadLimiteKmh;
    private final String nombre;
    private double riesgo;

    public TramoVial(Nodo origen, Nodo destino, double longitudM, List<Coordenada> geometria,
                     TipoVia tipoVia, double velocidadLimiteKmh, String nombre) {
        super(origen, destino, longitudM, geometria);
        this.tipoVia = tipoVia;
        this.velocidadLimiteKmh = velocidadLimiteKmh;
        this.nombre = nombre == null || nombre.isEmpty() ? "Vía sin nombre" : nombre;
    }

    @Override
    public double velocidadEfectivaKmh(ContextoViaje contexto) {
        double factor = contexto.factorVelocidad();
        boolean arteria = tipoVia == TipoVia.TRONCAL || tipoVia == TipoVia.PRIMARIA
                || tipoVia == TipoVia.SECUNDARIA || tipoVia == TipoVia.AUTOPISTA;
        double efecto = arteria ? factor : 1 - (1 - factor) * 0.6;
        return Math.max(5.0, velocidadLimiteKmh * efecto);
    }

    @Override
    public boolean esFluvial() {
        return false;
    }

    @Override
    public String getNombre() {
        return nombre;
    }

    @Override
    public double getRiesgo() {
        return riesgo;
    }

    public void setRiesgo(double riesgo) {
        this.riesgo = Math.max(0, Math.min(1, riesgo));
    }

    public TipoVia getTipoVia() { return tipoVia; }
    public double getVelocidadLimiteKmh() { return velocidadLimiteKmh; }
}
