package co.edu.greenroute.vehiculos;

import co.edu.greenroute.red.Tramo;

/** Barcaza fluvial: solo navega por el río. */
public class Barcaza extends Vehiculo {

    public Barcaza(String id, String nombre) {
        super(id, nombre, 600_000, 250_000, 16, 2_500);
    }

    @Override
    public TipoEnergia getTipoEnergia() {
        return TipoEnergia.DIESEL;
    }

    @Override
    public MedioTransporte getMedio() {
        return MedioTransporte.FLUVIAL;
    }

    @Override
    public String getTecnologia() {
        return "Remolcador diésel con barcaza de 600 t";
    }

    @Override
    public boolean puedeCircularPor(Tramo tramo) {
        return tramo.esFluvial();
    }

    @Override
    protected double consumoPorKm(double velocidadKmh, double cargaKg) {
        return (9.0 + 0.006 * cargaKg / 1000.0) * curvaVelocidad(velocidadKmh, 0.4, 2.0, 0.002);
    }
}
