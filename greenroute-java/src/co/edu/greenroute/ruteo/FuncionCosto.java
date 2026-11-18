package co.edu.greenroute.ruteo;

import co.edu.greenroute.red.Tramo;
import co.edu.greenroute.vehiculos.Vehiculo;

/** Cuánto cuesta recorrer un tramo según el criterio elegido. */
public interface FuncionCosto {

    double costo(Tramo tramo, Vehiculo vehiculo, ContextoViaje contexto);

    double cotaInferiorPorMetro(Vehiculo vehiculo, ContextoViaje contexto);

    static double velocidadTope(Vehiculo v) {
        return Math.min(v.getVelocidadMaxKmh(), 90.0);
    }
}
