package co.edu.greenroute.escritorio;

import co.edu.greenroute.servicio.PlanificadorRutas;

import java.nio.file.Path;
import java.util.List;

/** Datos compartidos por todas las pantallas: planificador, puntos logísticos y carpeta del código. */
public final class ContextoApp {

    private final PlanificadorRutas planificador;
    private final List<PuntoLogistico> puntos;
    private final Path carpetaFuentes;

    public ContextoApp(PlanificadorRutas planificador, List<PuntoLogistico> puntos, Path carpetaFuentes) {
        this.planificador = planificador;
        this.puntos = List.copyOf(puntos);
        this.carpetaFuentes = carpetaFuentes;
    }

    public PlanificadorRutas getPlanificador() { return planificador; }
    public List<PuntoLogistico> getPuntos() { return puntos; }
    public Path getCarpetaFuentes() { return carpetaFuentes; }

    public PuntoLogistico punto(String prefijo) {
        return puntos.stream().filter(p -> p.getNombre().startsWith(prefijo)).findFirst().orElse(puntos.get(0));
    }
}
