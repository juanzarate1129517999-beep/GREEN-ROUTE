package co.edu.greenroute.excepciones;

/** La carga pesa más de lo que el vehículo puede llevar. */
public class CapacidadExcedidaException extends GreenRouteException {
    private final double cargaKg;
    private final double capacidadKg;

    public CapacidadExcedidaException(String vehiculo, double cargaKg, double capacidadKg) {
        super("CAPACIDAD_EXCEDIDA", String.format(
                "%s admite %.0f kg y la carga es de %.0f kg", vehiculo, capacidadKg, cargaKg));
        this.cargaKg = cargaKg;
        this.capacidadKg = capacidadKg;
    }

    public double getCargaKg() { return cargaKg; }
    public double getCapacidadKg() { return capacidadKg; }
}
