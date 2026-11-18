package co.edu.greenroute.ruteo;

import co.edu.greenroute.analitica.PerfilHorario;

/** Hora de salida y carga del viaje: afectan la velocidad y las emisiones. */
public class ContextoViaje {
    private final int horaSalida;
    private final double cargaKg;
    private final PerfilHorario perfil;

    public ContextoViaje(int horaSalida, double cargaKg, PerfilHorario perfil) {
        if (horaSalida < 0 || horaSalida > 23) {
            throw new IllegalArgumentException("Hora inválida: " + horaSalida);
        }
        if (cargaKg < 0) {
            throw new IllegalArgumentException("La carga no puede ser negativa");
        }
        this.horaSalida = horaSalida;
        this.cargaKg = cargaKg;
        this.perfil = perfil;
    }

    public double factorVelocidad() {
        return perfil.factorVelocidad(horaSalida);
    }

    public int getHoraSalida() { return horaSalida; }
    public double getCargaKg() { return cargaKg; }
    public PerfilHorario getPerfil() { return perfil; }
}
