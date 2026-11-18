package co.edu.greenroute.ruteo;

import co.edu.greenroute.excepciones.RutaNoEncontradaException;
import co.edu.greenroute.red.Nodo;
import co.edu.greenroute.red.RedTransporte;
import co.edu.greenroute.red.Tramo;
import co.edu.greenroute.vehiculos.Vehiculo;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.PriorityQueue;

/** Búsqueda de camino mínimo con cola de prioridad. Las hijas solo cambian la heurística. */
public abstract class BusquedaCaminoMinimo implements AlgoritmoRuta {

    protected abstract double heuristica(Nodo n, Nodo destino, double cotaPorMetro);

    @Override
    public final Ruta buscar(RedTransporte red, Nodo origen, Nodo destino, Vehiculo vehiculo,
                             Criterio criterio, ContextoViaje contexto) throws RutaNoEncontradaException {
        long inicio = System.nanoTime();
        FuncionCosto funcion = criterio.crearFuncion();
        double cota = funcion.cotaInferiorPorMetro(vehiculo, contexto);

        int n = red.totalNodos();
        double[] g = new double[n];
        Arrays.fill(g, Double.POSITIVE_INFINITY);
        Tramo[] previo = new Tramo[n];
        boolean[] cerrado = new boolean[n];
        PriorityQueue<double[]> abiertos = new PriorityQueue<>((a, b) -> Double.compare(a[0], b[0]));

        g[origen.getIndice()] = 0;
        abiertos.add(new double[]{heuristica(origen, destino, cota), origen.getIndice()});
        int explorados = 0;

        while (!abiertos.isEmpty()) {
            int actual = (int) abiertos.poll()[1];
            if (cerrado[actual]) continue;
            cerrado[actual] = true;
            explorados++;
            if (actual == destino.getIndice()) break;

            for (Tramo t : red.getNodo(actual).getSalientes()) {
                if (!vehiculo.puedeCircularPor(t)) continue;
                int vecino = t.getDestino().getIndice();
                if (cerrado[vecino]) continue;
                double nuevo = g[actual] + funcion.costo(t, vehiculo, contexto) * vehiculo.penalizacionVia(t);
                if (nuevo < g[vecino]) {
                    g[vecino] = nuevo;
                    previo[vecino] = t;
                    abiertos.add(new double[]{nuevo + heuristica(t.getDestino(), destino, cota), vecino});
                }
            }
        }

        if (previo[destino.getIndice()] == null) {
            throw new RutaNoEncontradaException("No existe un camino transitable para "
                    + vehiculo.getNombre() + " entre los puntos seleccionados");
        }
        List<Tramo> camino = new ArrayList<>();
        for (Tramo t = previo[destino.getIndice()]; t != null; t = previo[t.getOrigen().getIndice()]) {
            camino.add(t);
            if (t.getOrigen() == origen) break;
        }
        Collections.reverse(camino);
        double ms = (System.nanoTime() - inicio) / 1e6;
        return new Ruta(camino, vehiculo, contexto, criterio, getNombre(), explorados, ms);
    }
}
