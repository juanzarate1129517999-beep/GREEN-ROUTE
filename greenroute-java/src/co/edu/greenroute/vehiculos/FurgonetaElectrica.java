package co.edu.greenroute.vehiculos;

/** Furgoneta eléctrica de reparto. */
public class FurgonetaElectrica extends VehiculoTerrestre implements Recargable {

    private final double bateriaKWh;

    public FurgonetaElectrica(String id, String nombre) {
        super(id, nombre, 1_100, 1_900, 90, 180);
        this.bateriaKWh = 75;
    }

    @Override
    public TipoEnergia getTipoEnergia() {
        return TipoEnergia.ELECTRICIDAD;
    }

    @Override
    public String getTecnologia() {
        return String.format("Eléctrica de batería (%.0f kWh)", bateriaKWh);
    }

    @Override
    protected double consumoPorKm(double velocidadKmh, double cargaKg) {
        return consumoReferenciaKWhKm(cargaKg) * curvaVelocidad(velocidadKmh, 0.85, 5.0, 0.0001);
    }

    @Override
    public double getCapacidadBateriaKWh() {
        return bateriaKWh;
    }

    @Override
    public double consumoReferenciaKWhKm(double cargaKg) {
        return 0.22 + 0.03 * cargaKg / 1000.0;
    }
}
