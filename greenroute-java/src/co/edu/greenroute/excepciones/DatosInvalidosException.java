package co.edu.greenroute.excepciones;

/** Un dato de entrada (archivo o parámetro) no es válido. */
public class DatosInvalidosException extends GreenRouteException {
    public DatosInvalidosException(String mensaje) {
        super("DATOS_INVALIDOS", mensaje);
    }
}
