package co.edu.greenroute.red;

/** Tipos de vía, traducidos desde la etiqueta "highway" de OpenStreetMap. */
public enum TipoVia {
    AUTOPISTA("Autopista"),
    TRONCAL("Vía troncal / arteria"),
    PRIMARIA("Vía primaria"),
    SECUNDARIA("Vía secundaria"),
    TERCIARIA("Vía terciaria / colectora"),
    RESIDENCIAL("Vía residencial"),
    SERVICIO("Vía de servicio"),
    PEATONAL("Vía de tráfico calmado"),
    EXCLUSIVA_BUS("Carril exclusivo Transmetro"),
    OTRA("Otra");

    private final String descripcion;

    TipoVia(String descripcion) { this.descripcion = descripcion; }

    public String getDescripcion() { return descripcion; }

    public static TipoVia desdeOSM(String highway) {
        if (highway == null) return OTRA;
        switch (highway.replace("_link", "")) {
            case "motorway":      return AUTOPISTA;
            case "trunk":         return TRONCAL;
            case "primary":       return PRIMARIA;
            case "secondary":     return SECUNDARIA;
            case "tertiary":      return TERCIARIA;
            case "residential":
            case "unclassified":
            case "road":          return RESIDENCIAL;
            case "service":       return SERVICIO;
            case "living_street": return PEATONAL;
            case "busway":        return EXCLUSIVA_BUS;
            default:              return OTRA;
        }
    }
}
