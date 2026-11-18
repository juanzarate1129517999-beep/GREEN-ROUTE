package co.edu.greenroute.excepciones;

/** Excepción base del proyecto: todas las demás heredan de esta y llevan un código. */
public class GreenRouteException extends Exception {
    private final String codigo;

    public GreenRouteException(String codigo, String mensaje) {
        super(mensaje);
        this.codigo = codigo;
    }

    public String getCodigo() {
        return codigo;
    }
}
