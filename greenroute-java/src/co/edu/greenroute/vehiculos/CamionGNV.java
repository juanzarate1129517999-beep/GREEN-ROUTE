package co.edu.greenroute.vehiculos;

/** Camión a gas natural vehicular. */
public class CamionGNV extends VehiculoTerrestre {

    public CamionGNV(String id, String nombre) {
        super(id, nombre, 9_000, 7_300, 80, 620);
    }

    @Override
    public TipoEnergia getTipoEnergia() {
        return TipoEnergia.GNV;
    }

    @Override
    public String getTecnologia() {
        return "Motor dedicado a gas natural (ciclo Otto)";
    }

    @Override
    protected double consumoPorKm(double velocidadKmh, double cargaKg) {
        double base = (29.5 + 0.60 * cargaKg / 1000.0) / 100.0;
        return base * curvaVelocidad(velocidadKmh, 0.62, 15.0, 0.00008);
    }
}
