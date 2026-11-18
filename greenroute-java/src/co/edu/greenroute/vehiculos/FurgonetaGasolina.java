package co.edu.greenroute.vehiculos;

/** Furgoneta de reparto a gasolina. */
public class FurgonetaGasolina extends VehiculoTerrestre {

    public FurgonetaGasolina(String id, String nombre) {
        super(id, nombre, 1_200, 1_700, 90, 220);
    }

    @Override
    public TipoEnergia getTipoEnergia() {
        return TipoEnergia.GASOLINA;
    }

    @Override
    public String getTecnologia() {
        return "Motor a gasolina de inyección";
    }

    @Override
    protected double consumoPorKm(double velocidadKmh, double cargaKg) {
        double base = (10.5 + 1.2 * cargaKg / 1000.0) / 100.0;
        return base * curvaVelocidad(velocidadKmh, 0.55, 17.0, 0.00009);
    }
}
