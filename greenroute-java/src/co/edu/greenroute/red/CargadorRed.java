package co.edu.greenroute.red;

import co.edu.greenroute.excepciones.DatosInvalidosException;
import co.edu.greenroute.modelo.Coordenada;
import co.edu.greenroute.util.LectorCSV;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Lee los CSV (nodos, aristas, río, riesgo) y construye la red de transporte. */
public final class CargadorRed {

    private CargadorRed() {
    }

    public static RedTransporte cargar(Path carpeta) throws DatosInvalidosException {
        RedTransporte red = new RedTransporte();
        cargarNodos(red, carpeta.resolve("nodos.csv"));
        Map<String, TramoVial> porClave = cargarAristas(red, carpeta.resolve("aristas.csv"));
        cargarRio(red, carpeta.resolve("fluvial.csv"));
        Path riesgo = carpeta.resolve("riesgo_aristas.csv");
        if (Files.exists(riesgo)) {
            int[] asignados = {0};
            LectorCSV.leer(riesgo, fila -> {
                TramoVial t = porClave.get(fila.get("origen") + ">" + fila.get("destino"));
                if (t != null) {
                    t.setRiesgo(Double.parseDouble(fila.get("riesgo")));
                    asignados[0]++;
                }
            });
            System.out.printf("  · Riesgo vial asignado a %,d tramos%n", asignados[0]);
        }
        return red;
    }

    private static void cargarNodos(RedTransporte red, Path archivo) throws DatosInvalidosException {
        LectorCSV.leer(archivo, fila -> red.agregarNodo(
                fila.get("id"),
                new Coordenada(Double.parseDouble(fila.get("lat")), Double.parseDouble(fila.get("lon"))),
                false));
    }

    private static Map<String, TramoVial> cargarAristas(RedTransporte red, Path archivo)
            throws DatosInvalidosException {
        Map<String, TramoVial> porClave = new HashMap<>();
        LectorCSV.leer(archivo, fila -> {
            Nodo o = red.getNodo(fila.get("origen"));
            Nodo d = red.getNodo(fila.get("destino"));
            if (o == null || d == null) return;
            TramoVial t = new TramoVial(o, d,
                    Math.max(0.5, Double.parseDouble(fila.get("longitud_m"))),
                    parsearGeometria(fila.get("geometria")),
                    TipoVia.desdeOSM(fila.get("tipo_via")),
                    Double.parseDouble(fila.get("velocidad_kmh")),
                    fila.get("nombre"));
            red.agregarTramo(t);
            porClave.putIfAbsent(fila.get("origen") + ">" + fila.get("destino"), t);
        });
        return porClave;
    }

    private static void cargarRio(RedTransporte red, Path archivo) throws DatosInvalidosException {
        if (!Files.exists(archivo)) return;
        List<Nodo> secuencia = new ArrayList<>();
        LectorCSV.leer(archivo, fila -> secuencia.add(red.agregarNodo(
                fila.get("id"),
                new Coordenada(Double.parseDouble(fila.get("lat")), Double.parseDouble(fila.get("lon"))),
                true)));
        for (int i = 0; i + 1 < secuencia.size(); i++) {
            Nodo a = secuencia.get(i);
            Nodo b = secuencia.get(i + 1);
            double largo = a.getCoordenada().distanciaMetros(b.getCoordenada());
            List<Coordenada> geo = List.of(a.getCoordenada(), b.getCoordenada());
            red.agregarTramo(new TramoFluvial(a, b, largo, geo, true));
            red.agregarTramo(new TramoFluvial(b, a, largo, List.of(b.getCoordenada(), a.getCoordenada()), false));
        }
    }

    private static List<Coordenada> parsearGeometria(String texto) {
        String[] puntos = texto.split(";");
        List<Coordenada> lista = new ArrayList<>(puntos.length);
        for (String p : puntos) {
            int coma = p.indexOf(',');
            lista.add(new Coordenada(Double.parseDouble(p.substring(0, coma)),
                    Double.parseDouble(p.substring(coma + 1))));
        }
        return lista;
    }
}
