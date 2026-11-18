package co.edu.greenroute.excepciones;

/** El vehículo no puede llegar a ese punto (p. ej. barcaza lejos del río). */
public class VehiculoIncompatibleException extends GreenRouteException {
    public VehiculoIncompatibleException(String mensaje) {
        super("VEHICULO_INCOMPATIBLE", mensaje);
    }
}
