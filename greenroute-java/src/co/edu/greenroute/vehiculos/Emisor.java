package co.edu.greenroute.vehiculos;

import co.edu.greenroute.red.Tramo;
import co.edu.greenroute.ruteo.ContextoViaje;

/** Todo lo que emite CO₂ al moverse. */
public interface Emisor {

    double calcularEmisionKgCO2(Tramo tramo, ContextoViaje contexto);

    default String getUnidadEmision() {
        return "kg CO2e";
    }
}
