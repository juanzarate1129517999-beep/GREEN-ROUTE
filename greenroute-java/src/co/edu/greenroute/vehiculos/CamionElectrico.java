package co.edu.greenroute.vehiculos;

/** Camión eléctrico: emite según la red eléctrica y tiene autonomía limitada. */
public class CamionElectrico extends VehiculoTerrestre implements Recargable {

    private final double bateriaKWh;

    public CamionElectrico(String id, String nombre) {
        this(id, nombre, 9_000, 8_200, 300);
    }

    public CamionElectrico(String id, String nombre, double capacidadKg, double pesoVacioKg,
                           double bateriaKWh) {
        super(id, nombre, capacidadKg, pesoVacioKg, 80, 520);
        if (bateriaKWh <= 0) {
            throw new IllegalArgumentException("La batería debe tener capacidad positiva");
        }
        this.bateriaKWh = bateriaKWh;
    }

    @Override
    public TipoEnergia getTipoEnergia() {
        return TipoEnergia.ELECTRICIDAD;
    }

    @Override
    public String getTecnologia() {
        return String.format("Eléctrico de batería (%.0f kWh)", bateriaKWh);
    }

    @Override
    protected double consumoPorKm(double velocidadKmh, double cargaKg) {
        return consumoReferenciaKWhKm(cargaKg) * curvaVelocidad(velocidadKmh, 0.85, 5.0, 0.00009);
    }

    @Override
    public double getCapacidadBateriaKWh() {
        return bateriaKWh;
    }

    @Override
    public double consumoReferenciaKWhKm(double cargaKg) {
        return 0.95 + 0.045 * cargaKg / 1000.0;
    }
}
