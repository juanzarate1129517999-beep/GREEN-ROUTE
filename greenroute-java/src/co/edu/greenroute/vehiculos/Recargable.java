package co.edu.greenroute.vehiculos;

import co.edu.greenroute.excepciones.AutonomiaInsuficienteException;

/** Vehículo con batería: comprueba si la autonomía alcanza. */
public interface Recargable {

    double getCapacidadBateriaKWh();

    double consumoReferenciaKWhKm(double cargaKg);

    default double autonomiaKm(double cargaKg) {
        return getCapacidadBateriaKWh() * 0.90 / consumoReferenciaKWhKm(cargaKg);
    }

    default void verificarAutonomia(String nombre, double distanciaKm, double cargaKg)
            throws AutonomiaInsuficienteException {
        double autonomia = autonomiaKm(cargaKg);
        if (distanciaKm > autonomia) {
            throw new AutonomiaInsuficienteException(nombre, distanciaKm, autonomia);
        }
    }
}
