package co.edu.greenroute.modelo;

/** Mercancía a transportar: descripción, tipo y peso. */
public class Mercancia {
    private final String descripcion;
    private final TipoMercancia tipo;
    private final double pesoKg;

    public Mercancia(String descripcion, TipoMercancia tipo, double pesoKg) {
        if (pesoKg <= 0) {
            throw new IllegalArgumentException("El peso de la mercancía debe ser positivo");
        }
        this.descripcion = descripcion;
        this.tipo = tipo;
        this.pesoKg = pesoKg;
    }

    public String getDescripcion() { return descripcion; }
    public TipoMercancia getTipo() { return tipo; }
    public double getPesoKg() { return pesoKg; }

    @Override
    public String toString() {
        return String.format("%s (%s, %.0f kg)", descripcion, tipo.getDescripcion(), pesoKg);
    }
}
