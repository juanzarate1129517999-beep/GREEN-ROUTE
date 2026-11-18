package co.edu.greenroute.vehiculos;

import co.edu.greenroute.red.TipoVia;
import co.edu.greenroute.red.Tramo;
import co.edu.greenroute.red.TramoVial;

/** Vehículo que circula por calles (no por el río ni por el carril exclusivo del Transmetro). */
public abstract class VehiculoTerrestre extends Vehiculo {

    public static final double UMBRAL_PESADO_KG = 3_500;

    protected VehiculoTerrestre(String id, String nombre, double capacidadKg, double pesoVacioKg,
                                double velocidadMaxKmh, double costoFijoCOPPorKm) {
        super(id, nombre, capacidadKg, pesoVacioKg, velocidadMaxKmh, costoFijoCOPPorKm);
    }

    @Override
    public MedioTransporte getMedio() {
        return MedioTransporte.TERRESTRE;
    }

    @Override
    public boolean puedeCircularPor(Tramo tramo) {
        if (tramo.esFluvial()) {
            return false;
        }
        return !(tramo instanceof TramoVial)
                || ((TramoVial) tramo).getTipoVia() != TipoVia.EXCLUSIVA_BUS;
    }

    public boolean esPesado() {
        return getPesoBrutoMaxKg() > UMBRAL_PESADO_KG;
    }

    @Override
    public double penalizacionVia(Tramo tramo) {
        if (!esPesado() || !(tramo instanceof TramoVial)) {
            return 1.0;
        }
        switch (((TramoVial) tramo).getTipoVia()) {
            case RESIDENCIAL: return 3.0;
            case SERVICIO:    return 2.0;
            case PEATONAL:    return 8.0;
            default:          return 1.0;
        }
    }
}
