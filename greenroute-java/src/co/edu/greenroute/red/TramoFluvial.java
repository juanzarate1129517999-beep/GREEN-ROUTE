package co.edu.greenroute.red;

import co.edu.greenroute.modelo.Coordenada;
import co.edu.greenroute.ruteo.ContextoViaje;

import java.util.List;

/** Tramo sobre el río Magdalena. */
public class TramoFluvial extends Tramo {
    private static final double VELOCIDAD_AGUAS_ABAJO = 14.0;
    private static final double VELOCIDAD_AGUAS_ARRIBA = 9.0;

    private final boolean aguasAbajo;

    public TramoFluvial(Nodo origen, Nodo destino, double longitudM, List<Coordenada> geometria,
                        boolean aguasAbajo) {
        super(origen, destino, longitudM, geometria);
        this.aguasAbajo = aguasAbajo;
    }

    @Override
    public double velocidadEfectivaKmh(ContextoViaje contexto) {
        return aguasAbajo ? VELOCIDAD_AGUAS_ABAJO : VELOCIDAD_AGUAS_ARRIBA;
    }

    @Override
    public boolean esFluvial() {
        return true;
    }

    @Override
    public String getNombre() {
        return aguasAbajo ? "Río Magdalena (aguas abajo)" : "Río Magdalena (aguas arriba)";
    }

    public boolean isAguasAbajo() {
        return aguasAbajo;
    }
}
