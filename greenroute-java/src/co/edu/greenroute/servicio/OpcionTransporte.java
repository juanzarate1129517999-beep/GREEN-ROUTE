package co.edu.greenroute.servicio;

import co.edu.greenroute.excepciones.GreenRouteException;
import co.edu.greenroute.ruteo.Ruta;
import co.edu.greenroute.util.Json;
import co.edu.greenroute.vehiculos.Vehiculo;

import java.util.Map;

/** Resultado de un vehículo al comparar la flota: su ruta o el motivo por el que no puede. */
public class OpcionTransporte {
    private final Vehiculo vehiculo;
    private final Ruta ruta;
    private final GreenRouteException error;

    private OpcionTransporte(Vehiculo vehiculo, Ruta ruta, GreenRouteException error) {
        this.vehiculo = vehiculo;
        this.ruta = ruta;
        this.error = error;
    }

    public static OpcionTransporte factible(Vehiculo v, Ruta r) {
        return new OpcionTransporte(v, r, null);
    }

    public static OpcionTransporte noFactible(Vehiculo v, GreenRouteException e) {
        return new OpcionTransporte(v, null, e);
    }

    public boolean esFactible() { return ruta != null; }
    public Vehiculo getVehiculo() { return vehiculo; }
    public Ruta getRuta() { return ruta; }

    public Map<String, Object> aMapa() {
        Map<String, Object> m = Json.obj(
                "vehiculo", vehiculo.getId(),
                "nombre", vehiculo.getNombre(),
                "clase", vehiculo.getClass().getSimpleName(),
                "energia", vehiculo.getTipoEnergia().getDescripcion(),
                "factible", esFactible());
        if (esFactible()) {
            m.put("ruta", ruta.aMapa(false));
        } else {
            m.put("codigoError", error.getCodigo());
            m.put("motivo", error.getMessage());
        }
        return m;
    }
}
