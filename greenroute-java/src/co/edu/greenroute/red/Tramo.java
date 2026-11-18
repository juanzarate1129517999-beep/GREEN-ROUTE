package co.edu.greenroute.red;

import co.edu.greenroute.modelo.Coordenada;
import co.edu.greenroute.ruteo.ContextoViaje;

import java.util.Collections;
import java.util.List;

/** Arista del grafo entre dos nodos. Cada tipo de tramo calcula su propia velocidad. */
public abstract class Tramo {
    private final Nodo origen;
    private final Nodo destino;
    private final double longitudM;
    private final List<Coordenada> geometria;

    protected Tramo(Nodo origen, Nodo destino, double longitudM, List<Coordenada> geometria) {
        if (longitudM <= 0) {
            throw new IllegalArgumentException("La longitud del tramo debe ser positiva");
        }
        this.origen = origen;
        this.destino = destino;
        this.longitudM = longitudM;
        this.geometria = geometria;
    }

    public abstract double velocidadEfectivaKmh(ContextoViaje contexto);

    public abstract boolean esFluvial();

    public abstract String getNombre();

    public double getRiesgo() {
        return 0.0;
    }

    public Nodo getOrigen() { return origen; }
    public Nodo getDestino() { return destino; }
    public double getLongitudM() { return longitudM; }
    public double getLongitudKm() { return longitudM / 1000.0; }

    public List<Coordenada> getGeometria() {
        return Collections.unmodifiableList(geometria);
    }
}
