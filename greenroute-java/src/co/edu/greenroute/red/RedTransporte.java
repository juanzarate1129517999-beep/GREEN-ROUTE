package co.edu.greenroute.red;

import co.edu.greenroute.modelo.Coordenada;
import co.edu.greenroute.vehiculos.MedioTransporte;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Grafo de la red: nodos y tramos viales y fluviales. */
public class RedTransporte {
    private final List<Nodo> nodos = new ArrayList<>();
    private final Map<String, Nodo> porId = new HashMap<>();
    private final IndiceEspacial indiceVial = new IndiceEspacial();
    private final IndiceEspacial indiceFluvial = new IndiceEspacial();
    private int totalTramos;
    private double kmViales;
    private double kmFluviales;

    public Nodo agregarNodo(String id, Coordenada coordenada, boolean fluvial) {
        Nodo existente = porId.get(id);
        if (existente != null) {
            return existente;
        }
        Nodo nodo = new Nodo(nodos.size(), id, coordenada, fluvial);
        nodos.add(nodo);
        porId.put(id, nodo);
        (fluvial ? indiceFluvial : indiceVial).insertar(nodo);
        return nodo;
    }

    public void agregarTramo(Tramo tramo) {
        tramo.getOrigen().agregarSaliente(tramo);
        totalTramos++;
        if (tramo.esFluvial()) {
            kmFluviales += tramo.getLongitudKm();
        } else {
            kmViales += tramo.getLongitudKm();
        }
    }

    public Nodo nodoMasCercano(Coordenada punto, MedioTransporte medio) {
        return medio == MedioTransporte.FLUVIAL
                ? indiceFluvial.masCercano(punto)
                : indiceVial.masCercano(punto);
    }

    public Nodo getNodo(String id) { return porId.get(id); }
    public Nodo getNodo(int indice) { return nodos.get(indice); }
    public List<Nodo> getNodos() { return Collections.unmodifiableList(nodos); }
    public int totalNodos() { return nodos.size(); }
    public int totalTramos() { return totalTramos; }
    public double getKmViales() { return kmViales; }
    public double getKmFluviales() { return kmFluviales; }
}
