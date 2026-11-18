package co.edu.greenroute.ruteo;

import co.edu.greenroute.modelo.Coordenada;
import co.edu.greenroute.red.Tramo;
import co.edu.greenroute.util.Json;
import co.edu.greenroute.vehiculos.Recargable;
import co.edu.greenroute.vehiculos.Vehiculo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/** Resultado de una búsqueda: tramos recorridos y sus totales (km, minutos, CO₂, costo). */
public class Ruta {
    private final List<Tramo> tramos;
    private final Vehiculo vehiculo;
    private final ContextoViaje contexto;
    private final Criterio criterio;
    private final String algoritmo;
    private final int nodosExplorados;
    private final double tiempoCalculoMs;

    private double distanciaKm;
    private double tiempoMin;
    private double emisionKgCO2;
    private double energiaConsumida;
    private double costoCOP;
    private double riesgoPromedio;

    public Ruta(List<Tramo> tramos, Vehiculo vehiculo, ContextoViaje contexto, Criterio criterio,
                String algoritmo, int nodosExplorados, double tiempoCalculoMs) {
        this.tramos = new ArrayList<>(tramos);
        this.vehiculo = vehiculo;
        this.contexto = contexto;
        this.criterio = criterio;
        this.algoritmo = algoritmo;
        this.nodosExplorados = nodosExplorados;
        this.tiempoCalculoMs = tiempoCalculoMs;
        calcularIndicadores();
    }

    private void calcularIndicadores() {
        double riesgoPonderado = 0;
        for (Tramo t : tramos) {
            distanciaKm += t.getLongitudKm();
            tiempoMin += vehiculo.tiempoHoras(t, contexto) * 60.0;
            emisionKgCO2 += vehiculo.calcularEmisionKgCO2(t, contexto);
            energiaConsumida += vehiculo.consumoEnTramo(t, contexto);
            costoCOP += vehiculo.calcularCostoCOP(t, contexto);
            riesgoPonderado += t.getRiesgo() * t.getLongitudKm();
        }
        riesgoPromedio = distanciaKm > 0 ? riesgoPonderado / distanciaKm : 0;
    }

    public List<Map<String, Object>> indicaciones() {
        List<Map<String, Object>> pasos = new ArrayList<>();
        String nombreActual = null;
        double metros = 0;
        for (Tramo t : tramos) {
            if (nombreActual != null && !nombreActual.equals(t.getNombre())) {
                pasos.add(Json.obj("via", nombreActual, "metros", Math.round(metros)));
                metros = 0;
            }
            nombreActual = t.getNombre();
            metros += t.getLongitudM();
        }
        if (nombreActual != null) {
            pasos.add(Json.obj("via", nombreActual, "metros", Math.round(metros)));
        }
        return pasos;
    }

    public List<double[]> geometria() {
        List<double[]> puntos = new ArrayList<>();
        for (Tramo t : tramos) {
            List<Coordenada> geo = t.getGeometria();
            for (int i = puntos.isEmpty() ? 0 : 1; i < geo.size(); i++) {
                puntos.add(geo.get(i).comoArreglo());
            }
        }
        return puntos;
    }

    public double intensidadGramosPorTkm() {
        double toneladas = contexto.getCargaKg() / 1000.0;
        return toneladas > 0 && distanciaKm > 0 ? emisionKgCO2 * 1000.0 / (toneladas * distanciaKm) : 0;
    }

    public Map<String, Object> aMapa(boolean incluirGeometria) {
        Map<String, Object> m = Json.obj(
                "criterio", criterio.name(),
                "criterioEtiqueta", criterio.getEtiqueta(),
                "color", criterio.getColor(),
                "vehiculo", vehiculo.getId(),
                "vehiculoNombre", vehiculo.getNombre(),
                "energia", vehiculo.getTipoEnergia().getDescripcion(),
                "distanciaKm", distanciaKm,
                "tiempoMin", tiempoMin,
                "emisionKgCO2", emisionKgCO2,
                "intensidadGTkm", intensidadGramosPorTkm(),
                "consumo", energiaConsumida,
                "unidadConsumo", vehiculo.getTipoEnergia().getUnidad(),
                "costoCOP", costoCOP,
                "riesgo", riesgoPromedio,
                "tramos", tramos.size(),
                "algoritmo", algoritmo,
                "nodosExplorados", nodosExplorados,
                "calculoMs", tiempoCalculoMs,
                "horaSalida", contexto.getHoraSalida(),
                "factorTrafico", contexto.factorVelocidad());
        if (vehiculo instanceof Recargable) {
            m.put("autonomiaKm", ((Recargable) vehiculo).autonomiaKm(contexto.getCargaKg()));
        }
        if (incluirGeometria) {
            m.put("geometria", geometria());
            m.put("indicaciones", indicaciones());
        }
        return m;
    }

    public List<Tramo> getTramos() { return Collections.unmodifiableList(tramos); }
    public Vehiculo getVehiculo() { return vehiculo; }
    public Criterio getCriterio() { return criterio; }
    public double getDistanciaKm() { return distanciaKm; }
    public double getTiempoMin() { return tiempoMin; }
    public double getEmisionKgCO2() { return emisionKgCO2; }
    public double getCostoCOP() { return costoCOP; }
    public double getRiesgoPromedio() { return riesgoPromedio; }
    public int getNodosExplorados() { return nodosExplorados; }
    public double getTiempoCalculoMs() { return tiempoCalculoMs; }
    public String getAlgoritmo() { return algoritmo; }
}
