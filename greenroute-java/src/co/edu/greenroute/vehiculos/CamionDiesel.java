package co.edu.greenroute.vehiculos;

/** Camión de carga a diésel. */
public class CamionDiesel extends VehiculoTerrestre {

    private final double litrosBase100Km;
    private final double litrosPorTonelada100Km;

    public CamionDiesel(String id, String nombre) {
        this(id, nombre, 10_000, 7_000, 80, 24.0, 0.55, 650);
    }

    protected CamionDiesel(String id, String nombre, double capacidadKg, double pesoVacioKg,
                           double velocidadMaxKmh, double litrosBase100Km,
                           double litrosPorTonelada100Km, double costoFijo) {
        super(id, nombre, capacidadKg, pesoVacioKg, velocidadMaxKmh, costoFijo);
        this.litrosBase100Km = litrosBase100Km;
        this.litrosPorTonelada100Km = litrosPorTonelada100Km;
    }

    @Override
    public TipoEnergia getTipoEnergia() {
        return TipoEnergia.DIESEL;
    }

    @Override
    public String getTecnologia() {
        return "Motor de combustión diésel Euro IV";
    }

    @Override
    protected double consumoPorKm(double velocidadKmh, double cargaKg) {
        double base = (litrosBase100Km + litrosPorTonelada100Km * cargaKg / 1000.0) / 100.0;
        return base * curvaVelocidad(velocidadKmh, 0.60, 16.0, 0.00008);
    }
}
