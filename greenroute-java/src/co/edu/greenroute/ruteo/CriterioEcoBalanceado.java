package co.edu.greenroute.ruteo;

import co.edu.greenroute.red.Tramo;
import co.edu.greenroute.vehiculos.Vehiculo;

/** Costo de un tramo = mezcla de tiempo, emisiones y riesgo. */
public class CriterioEcoBalanceado implements FuncionCosto {
    private static final double PESO_EMISION = 0.5;
    private static final double PESO_TIEMPO = 0.5;
    private static final double TIEMPO_REF_H_POR_KM = 1.0 / 30.0;

    @Override
    public double costo(Tramo tramo, Vehiculo vehiculo, ContextoViaje contexto) {
        double eRef = vehiculo.emisionReferenciaPorKm(contexto.getCargaKg());
        double e = vehiculo.calcularEmisionKgCO2(tramo, contexto) / eRef;
        double t = vehiculo.tiempoHoras(tramo, contexto) / TIEMPO_REF_H_POR_KM;
        return (PESO_EMISION * e + PESO_TIEMPO * t) * (1 + tramo.getRiesgo());
    }

    @Override
    public double cotaInferiorPorMetro(Vehiculo vehiculo, ContextoViaje contexto) {
        double eRef = vehiculo.emisionReferenciaPorKm(contexto.getCargaKg());
        double e = vehiculo.emisionMinimaPorMetro(contexto.getCargaKg()) / eRef;
        double t = (1.0 / (FuncionCosto.velocidadTope(vehiculo) * 1000.0)) / TIEMPO_REF_H_POR_KM;
        return PESO_EMISION * e + PESO_TIEMPO * t;
    }
}
