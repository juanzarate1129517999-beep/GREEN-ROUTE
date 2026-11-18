package co.edu.greenroute.ruteo;

import co.edu.greenroute.red.Tramo;
import co.edu.greenroute.vehiculos.Vehiculo;

/** Costo de un tramo = kg de CO₂ que emite el vehículo. */
public class CriterioEmisiones implements FuncionCosto {

    @Override
    public double costo(Tramo tramo, Vehiculo vehiculo, ContextoViaje contexto) {
        return vehiculo.calcularEmisionKgCO2(tramo, contexto);
    }

    @Override
    public double cotaInferiorPorMetro(Vehiculo vehiculo, ContextoViaje contexto) {
        return vehiculo.emisionMinimaPorMetro(contexto.getCargaKg());
    }
}
