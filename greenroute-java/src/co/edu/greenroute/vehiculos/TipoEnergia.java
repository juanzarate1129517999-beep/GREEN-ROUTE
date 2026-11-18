package co.edu.greenroute.vehiculos;

/** Fuente de energía con su unidad y factor de emisión (kg CO₂ por unidad). */
public enum TipoEnergia {
    DIESEL("ACPM / Diésel", "L", 2.68, 2_800),
    GASOLINA("Gasolina corriente", "L", 2.31, 4_200),
    GNV("Gas natural vehicular", "m3", 1.93, 2_400),
    ELECTRICIDAD("Electricidad (red SIN)", "kWh", 0.220, 900);

    private final String descripcion;
    private final String unidad;
    private final double factorEmisionKgPorUnidad;
    private final double precioCOPPorUnidad;

    TipoEnergia(String descripcion, String unidad, double factor, double precio) {
        this.descripcion = descripcion;
        this.unidad = unidad;
        this.factorEmisionKgPorUnidad = factor;
        this.precioCOPPorUnidad = precio;
    }

    public String getDescripcion() { return descripcion; }
    public String getUnidad() { return unidad; }
    public double getFactorEmision() { return factorEmisionKgPorUnidad; }
    public double getPrecioCOP() { return precioCOPPorUnidad; }
}
