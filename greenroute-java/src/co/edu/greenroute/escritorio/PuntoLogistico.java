package co.edu.greenroute.escritorio;

import co.edu.greenroute.excepciones.DatosInvalidosException;
import co.edu.greenroute.modelo.Coordenada;
import co.edu.greenroute.util.LectorCSV;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Lugar con nombre (puerto, aeropuerto, ...) leído de puntos.csv. */
public final class PuntoLogistico {

    private final String nombre;
    private final String categoria;
    private final Coordenada coordenada;

    public PuntoLogistico(String nombre, String categoria, Coordenada coordenada) {
        this.nombre = nombre;
        this.categoria = categoria;
        this.coordenada = coordenada;
    }

    public static List<PuntoLogistico> cargar(Path archivo) throws DatosInvalidosException {
        List<PuntoLogistico> puntos = new ArrayList<>();
        LectorCSV.leer(archivo, fila -> puntos.add(new PuntoLogistico(
                fila.get("nombre"), fila.get("categoria"),
                new Coordenada(Double.parseDouble(fila.get("lat")), Double.parseDouble(fila.get("lon"))))));
        return puntos;
    }

    public String getNombre() { return nombre; }
    public String getCategoria() { return categoria; }
    public Coordenada getCoordenada() { return coordenada; }

    @Override
    public String toString() {
        return nombre + "  ·  " + categoria;
    }
}
