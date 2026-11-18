package co.edu.greenroute.excepciones;

/** La batería del vehículo eléctrico no alcanza para la ruta. */
public class AutonomiaInsuficienteException extends GreenRouteException {
    public AutonomiaInsuficienteException(String vehiculo, double distanciaKm, double autonomiaKm) {
        super("AUTONOMIA_INSUFICIENTE", String.format(
                "%s tiene %.0f km de autonomía y la ruta mide %.1f km", vehiculo, autonomiaKm, distanciaKm));
    }
}
