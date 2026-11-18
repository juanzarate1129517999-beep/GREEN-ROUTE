package co.edu.greenroute.ruteo;

import java.util.function.Supplier;

/** Qué se quiere minimizar (distancia, tiempo, emisiones, riesgo o un balance). */
public enum Criterio {
    EMISIONES("Menor huella de CO2", "#1b7f4c", CriterioEmisiones::new),
    TIEMPO("Más rápida", "#2a78d6", CriterioTiempo::new),
    DISTANCIA("Más corta", "#6250d6", CriterioDistancia::new),
    RIESGO("Más segura", "#eb6834", CriterioRiesgo::new),
    ECO("Eco-balanceada", "#0e9aa7", CriterioEcoBalanceado::new);

    private final String etiqueta;
    private final String color;
    private final Supplier<FuncionCosto> fabrica;

    Criterio(String etiqueta, String color, Supplier<FuncionCosto> fabrica) {
        this.etiqueta = etiqueta;
        this.color = color;
        this.fabrica = fabrica;
    }

    public FuncionCosto crearFuncion() {
        return fabrica.get();
    }

    public String getEtiqueta() { return etiqueta; }
    public String getColor() { return color; }

    public static Criterio desdeTexto(String texto) {
        if (texto == null) return EMISIONES;
        try {
            return valueOf(texto.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return EMISIONES;
        }
    }
}
