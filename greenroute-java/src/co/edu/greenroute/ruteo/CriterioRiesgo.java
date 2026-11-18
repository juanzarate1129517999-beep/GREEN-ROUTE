package co.edu.greenroute.ruteo;

import co.edu.greenroute.red.Tramo;
import co.edu.greenroute.vehiculos.Vehiculo;

/** Costo de un tramo = tiempo penalizado por la accidentalidad del tramo. */
public class CriterioRiesgo implements FuncionCosto {
    private static final double PESO_RIESGO = 4.0;

    @Override
    public double costo(Tramo tramo, Vehiculo vehiculo, ContextoViaje contexto) {
        return vehiculo.tiempoHoras(tramo, contexto) * (1 + PESO_RIESGO * tramo.getRiesgo());
    }

    @Override
    public double cotaInferiorPorMetro(Vehiculo vehiculo, ContextoViaje contexto) {
        return 1.0 / (FuncionCosto.velocidadTope(vehiculo) * 1000.0);
    }
}
