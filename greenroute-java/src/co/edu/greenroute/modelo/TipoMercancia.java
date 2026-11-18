package co.edu.greenroute.modelo;

/** Tipos de mercancía. */
public enum TipoMercancia {
    GENERAL("Carga general"),
    PERECEDERA("Perecedera / refrigerada"),
    GRANEL("Granel"),
    CONTENEDOR("Contenedor"),
    PELIGROSA("Mercancía peligrosa");

    private final String descripcion;

    TipoMercancia(String descripcion) { this.descripcion = descripcion; }

    public String getDescripcion() { return descripcion; }
}
