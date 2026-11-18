package co.edu.greenroute.vehiculos;

import co.edu.greenroute.excepciones.CapacidadExcedidaException;
import co.edu.greenroute.red.Tramo;
import co.edu.greenroute.ruteo.ContextoViaje;

import java.util.Objects;

/** Clase base de todos los vehículos. Cada hija define cuánto consume por km. */
public abstract class Vehiculo implements Emisor {

    private final String id;
    private final String nombre;
    private final double capacidadKg;
    private final double pesoVacioKg;
    private final double velocidadMaxKmh;
    private final double costoFijoCOPPorKm;

    protected Vehiculo(String id, String nombre, double capacidadKg, double pesoVacioKg,
                       double velocidadMaxKmh, double costoFijoCOPPorKm) {
        if (capacidadKg <= 0 || pesoVacioKg <= 0 || velocidadMaxKmh <= 0) {
            throw new IllegalArgumentException("Parámetros físicos inválidos para " + nombre);
        }
        this.id = Objects.requireNonNull(id);
        this.nombre = Objects.requireNonNull(nombre);
        this.capacidadKg = capacidadKg;
        this.pesoVacioKg = pesoVacioKg;
        this.velocidadMaxKmh = velocidadMaxKmh;
        this.costoFijoCOPPorKm = costoFijoCOPPorKm;
    }

    public abstract TipoEnergia getTipoEnergia();

    public abstract MedioTransporte getMedio();

    public abstract String getTecnologia();

    protected abstract double consumoPorKm(double velocidadKmh, double cargaKg);

    public abstract boolean puedeCircularPor(Tramo tramo);

    public double penalizacionVia(Tramo tramo) {
        return 1.0;
    }

    public double velocidadEn(Tramo tramo, ContextoViaje contexto) {
        return Math.min(velocidadMaxKmh, tramo.velocidadEfectivaKmh(contexto));
    }

    public double tiempoHoras(Tramo tramo, ContextoViaje contexto) {
        return tramo.getLongitudKm() / velocidadEn(tramo, contexto);
    }

    public final double consumoEnTramo(Tramo tramo, ContextoViaje contexto) {
        return consumoPorKm(velocidadEn(tramo, contexto), contexto.getCargaKg()) * tramo.getLongitudKm();
    }

    @Override
    public double calcularEmisionKgCO2(Tramo tramo, ContextoViaje contexto) {
        return consumoEnTramo(tramo, contexto) * getTipoEnergia().getFactorEmision();
    }

    public double calcularCostoCOP(Tramo tramo, ContextoViaje contexto) {
        return consumoEnTramo(tramo, contexto) * getTipoEnergia().getPrecioCOP()
                + costoFijoCOPPorKm * tramo.getLongitudKm();
    }

    public double emisionMinimaPorMetro(double cargaKg) {
        double minimo = Double.MAX_VALUE;
        for (double v = 5; v <= velocidadMaxKmh; v += 1) {
            minimo = Math.min(minimo, consumoPorKm(v, cargaKg));
        }
        return minimo * getTipoEnergia().getFactorEmision() / 1000.0;
    }

    public double emisionReferenciaPorKm(double cargaKg) {
        return consumoPorKm(40, cargaKg) * getTipoEnergia().getFactorEmision();
    }

    public void validarCarga(double cargaKg) throws CapacidadExcedidaException {
        if (cargaKg > capacidadKg) {
            throw new CapacidadExcedidaException(nombre, cargaKg, capacidadKg);
        }
    }

    protected static double curvaVelocidad(double v, double a, double b, double c) {
        double vel = Math.max(v, 5);
        double ref = a + b / 50.0 + c * 50.0 * 50.0;
        return (a + b / vel + c * vel * vel) / ref;
    }

    public String getId() { return id; }
    public String getNombre() { return nombre; }
    public double getCapacidadKg() { return capacidadKg; }
    public double getPesoVacioKg() { return pesoVacioKg; }
    public double getPesoBrutoMaxKg() { return pesoVacioKg + capacidadKg; }
    public double getVelocidadMaxKmh() { return velocidadMaxKmh; }
    public double getCostoFijoCOPPorKm() { return costoFijoCOPPorKm; }

    @Override
    public boolean equals(Object o) {
        return o instanceof Vehiculo && ((Vehiculo) o).id.equals(id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return String.format("%s [%s, %s, %.0f kg]", nombre, getClass().getSimpleName(),
                getTipoEnergia().getDescripcion(), capacidadKg);
    }
}
