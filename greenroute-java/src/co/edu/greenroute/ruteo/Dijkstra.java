package co.edu.greenroute.ruteo;

import co.edu.greenroute.red.Nodo;

/** Dijkstra: A* sin heurística (explora en todas las direcciones). */
public class Dijkstra extends BusquedaCaminoMinimo {

    @Override
    protected double heuristica(Nodo n, Nodo destino, double cotaPorMetro) {
        return 0.0;
    }

    @Override
    public String getNombre() {
        return "Dijkstra";
    }
}
