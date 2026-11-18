package co.edu.greenroute.red;

import co.edu.greenroute.modelo.Coordenada;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Cuadrícula para encontrar rápido el nodo más cercano a una coordenada. */
public class IndiceEspacial {
    private static final double TAMANO_CELDA = 0.004;

    private final Map<Long, List<Nodo>> celdas = new HashMap<>();

    public void insertar(Nodo nodo) {
        celdas.computeIfAbsent(clave(nodo.getCoordenada()), k -> new ArrayList<>()).add(nodo);
    }

    public Nodo masCercano(Coordenada punto) {
        int cx = celda(punto.getLongitud());
        int cy = celda(punto.getLatitud());
        Nodo mejor = null;
        double mejorDist = Double.MAX_VALUE;
        for (int radio = 1; radio <= 40 && mejor == null; radio *= 2) {
            for (int dx = -radio; dx <= radio; dx++) {
                for (int dy = -radio; dy <= radio; dy++) {
                    List<Nodo> lista = celdas.get(combinar(cx + dx, cy + dy));
                    if (lista == null) continue;
                    for (Nodo n : lista) {
                        double d = n.getCoordenada().distanciaMetros(punto);
                        if (d < mejorDist) {
                            mejorDist = d;
                            mejor = n;
                        }
                    }
                }
            }
        }
        return mejor;
    }

    private static int celda(double grados) {
        return (int) Math.floor(grados / TAMANO_CELDA);
    }

    private static long combinar(int x, int y) {
        return ((long) x << 32) ^ (y & 0xffffffffL);
    }

    private static long clave(Coordenada c) {
        return combinar(celda(c.getLongitud()), celda(c.getLatitud()));
    }
}
