package co.edu.greenroute.ruteo;

import co.edu.greenroute.red.Tramo;
import co.edu.greenroute.vehiculos.Vehiculo;

/** Costo de un tramo = metros. */
public class CriterioDistancia implements FuncionCosto {

    @Override
    public double costo(Tramo tramo, Vehiculo vehiculo, ContextoViaje contexto) {
        return tramo.getLongitudM();
    }

    @Override
    public double cotaInferiorPorMetro(Vehiculo vehiculo, ContextoViaje contexto) {
        return 1.0;
    }
}
