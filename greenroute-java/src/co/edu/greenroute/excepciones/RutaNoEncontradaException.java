package co.edu.greenroute.excepciones;

/** No existe camino entre el origen y el destino. */
public class RutaNoEncontradaException extends GreenRouteException {
    public RutaNoEncontradaException(String mensaje) {
        super("RUTA_NO_ENCONTRADA", mensaje);
    }
}
