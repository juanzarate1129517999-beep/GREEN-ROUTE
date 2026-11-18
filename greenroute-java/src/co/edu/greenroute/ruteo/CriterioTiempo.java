package co.edu.greenroute.ruteo;

import co.edu.greenroute.red.Tramo;
import co.edu.greenroute.vehiculos.Vehiculo;

/** Costo de un tramo = minutos de recorrido. */
public class CriterioTiempo implements FuncionCosto {

    @Override
    public double costo(Tramo tramo, Vehiculo vehiculo, ContextoViaje contexto) {
        return vehiculo.tiempoHoras(tramo, contexto);
    }

    @Override
    public double cotaInferiorPorMetro(Vehiculo vehiculo, ContextoViaje contexto) {
        return 1.0 / (FuncionCosto.velocidadTope(vehiculo) * 1000.0);
    }
}
