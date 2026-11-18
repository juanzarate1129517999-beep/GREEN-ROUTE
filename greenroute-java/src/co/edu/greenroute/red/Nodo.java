package co.edu.greenroute.red;

import co.edu.greenroute.modelo.Coordenada;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Intersección de la red (vial o fluvial). */
public class Nodo {
    private final int indice;
    private final String id;
    private final Coordenada coordenada;
    private final boolean fluvial;
    private final List<Tramo> salientes = new ArrayList<>(4);

    public Nodo(int indice, String id, Coordenada coordenada, boolean fluvial) {
        this.indice = indice;
        this.id = id;
        this.coordenada = coordenada;
        this.fluvial = fluvial;
    }

    void agregarSaliente(Tramo tramo) {
        salientes.add(tramo);
    }

    public int getIndice() { return indice; }
    public String getId() { return id; }
    public Coordenada getCoordenada() { return coordenada; }
    public boolean esFluvial() { return fluvial; }

    public List<Tramo> getSalientes() {
        return Collections.unmodifiableList(salientes);
    }

    @Override
    public String toString() {
        return "Nodo " + id + " " + coordenada;
    }
}
