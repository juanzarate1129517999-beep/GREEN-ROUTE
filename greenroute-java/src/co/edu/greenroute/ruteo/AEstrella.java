package co.edu.greenroute.ruteo;

import co.edu.greenroute.red.Nodo;

/** A*: como Dijkstra pero guiado hacia el destino con una estimación del costo restante. */
public class AEstrella extends BusquedaCaminoMinimo {

    @Override
    protected double heuristica(Nodo n, Nodo destino, double cotaPorMetro) {
        return n.getCoordenada().distanciaMetros(destino.getCoordenada()) * cotaPorMetro;
    }

    @Override
    public String getNombre() {
        return "A*";
    }
}
