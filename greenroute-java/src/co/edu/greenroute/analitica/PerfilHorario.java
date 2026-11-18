package co.edu.greenroute.analitica;

import co.edu.greenroute.excepciones.DatosInvalidosException;
import co.edu.greenroute.util.LectorCSV;

import java.nio.file.Path;

/** Factor de velocidad por hora del día, aprendido en Python (perfil_horario.csv): simula el tráfico. */
public class PerfilHorario {
    private final double[] indiceActividad = new double[24];
    private final double[] factorVelocidad = new double[24];
    private final String fuente;

    private PerfilHorario(String fuente) {
        this.fuente = fuente;
    }

    public static PerfilHorario cargar(Path archivo) {
        PerfilHorario p = new PerfilHorario("Modelo ML (perfil_horario.csv)");
        try {
            LectorCSV.leer(archivo, fila -> {
                int h = Integer.parseInt(fila.get("hora"));
                p.indiceActividad[h] = Double.parseDouble(fila.get("indice_actividad"));
                p.factorVelocidad[h] = Double.parseDouble(fila.get("factor_velocidad"));
            });
            return p;
        } catch (DatosInvalidosException | RuntimeException e) {
            System.err.println("[PerfilHorario] " + e.getMessage() + " — se usa un perfil por defecto");
            return porDefecto();
        }
    }

    public static PerfilHorario porDefecto() {
        PerfilHorario p = new PerfilHorario("Perfil por defecto");
        for (int h = 0; h < 24; h++) {
            double pico = Math.exp(-Math.pow(h - 7, 2) / 4.0) + Math.exp(-Math.pow(h - 18, 2) / 5.0);
            p.indiceActividad[h] = Math.min(1, 0.15 + pico);
            p.factorVelocidad[h] = 1 - 0.45 * p.indiceActividad[h];
        }
        return p;
    }

    public double factorVelocidad(int hora) {
        return factorVelocidad[Math.floorMod(hora, 24)];
    }

    public double indiceActividad(int hora) {
        return indiceActividad[Math.floorMod(hora, 24)];
    }

    public double[] getFactores() {
        return factorVelocidad.clone();
    }

    public double[] getIndices() {
        return indiceActividad.clone();
    }

    public String getFuente() {
        return fuente;
    }
}
