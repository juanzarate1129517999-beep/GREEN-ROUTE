package co.edu.greenroute.api;

import co.edu.greenroute.excepciones.DatosInvalidosException;
import co.edu.greenroute.excepciones.GreenRouteException;
import co.edu.greenroute.modelo.Coordenada;
import co.edu.greenroute.ruteo.AEstrella;
import co.edu.greenroute.ruteo.AlgoritmoRuta;
import co.edu.greenroute.ruteo.Criterio;
import co.edu.greenroute.ruteo.Dijkstra;
import co.edu.greenroute.ruteo.Ruta;
import co.edu.greenroute.servicio.OpcionTransporte;
import co.edu.greenroute.servicio.PlanificadorRutas;
import co.edu.greenroute.util.Json;
import co.edu.greenroute.util.LectorCSV;
import co.edu.greenroute.vehiculos.Recargable;
import co.edu.greenroute.vehiculos.Vehiculo;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executors;

/** Servidor HTTP: sirve la carpeta web y expone el API REST (/api/ruta, /api/comparar, ...) en JSON. */
public class ServidorWeb {
    private static final Map<String, String> TIPOS_MIME = new HashMap<>();

    static {
        TIPOS_MIME.put("html", "text/html; charset=utf-8");
        TIPOS_MIME.put("css", "text/css; charset=utf-8");
        TIPOS_MIME.put("js", "application/javascript; charset=utf-8");
        TIPOS_MIME.put("json", "application/json; charset=utf-8");
        TIPOS_MIME.put("geojson", "application/json; charset=utf-8");
        TIPOS_MIME.put("png", "image/png");
        TIPOS_MIME.put("jpg", "image/jpeg");
        TIPOS_MIME.put("svg", "image/svg+xml");
        TIPOS_MIME.put("ico", "image/x-icon");
        TIPOS_MIME.put("csv", "text/csv; charset=utf-8");
        TIPOS_MIME.put("mp4", "video/mp4");
    }

    private final PlanificadorRutas planificador;
    private final Path carpetaWeb;
    private final Path carpetaDatos;
    private HttpServer servidor;

    public ServidorWeb(PlanificadorRutas planificador, Path carpetaWeb, Path carpetaDatos) {
        this.planificador = planificador;
        this.carpetaWeb = carpetaWeb.toAbsolutePath().normalize();
        this.carpetaDatos = carpetaDatos;
    }

    public void iniciar(int puerto) throws IOException {
        servidor = HttpServer.create(new InetSocketAddress(puerto), 0);
        servidor.createContext("/api/estado", api(this::estado));
        servidor.createContext("/api/vehiculos", api(q -> vehiculos()));
        servidor.createContext("/api/puntos", api(q -> puntos()));
        servidor.createContext("/api/ruta", api(this::ruta));
        servidor.createContext("/api/alternativas", api(this::alternativas));
        servidor.createContext("/api/comparar", api(this::comparar));
        servidor.createContext("/api/algoritmos", api(this::algoritmos));
        servidor.createContext("/", this::archivoEstatico);
        servidor.setExecutor(Executors.newFixedThreadPool(8));
        servidor.start();
    }

    private Object estado(Map<String, String> q) {
        return Json.obj(
                "nodos", planificador.getRed().totalNodos(),
                "tramos", planificador.getRed().totalTramos(),
                "kmViales", planificador.getRed().getKmViales(),
                "kmFluviales", planificador.getRed().getKmFluviales(),
                "vehiculos", planificador.getFlota().tamano(),
                "algoritmo", planificador.getAlgoritmo().getNombre(),
                "perfilFuente", planificador.getPerfil().getFuente(),
                "factorVelocidadHora", planificador.getPerfil().getFactores(),
                "indiceActividadHora", planificador.getPerfil().getIndices());
    }

    private Object vehiculos() {
        List<Object> lista = new ArrayList<>();
        for (Vehiculo v : planificador.getFlota().getVehiculos()) {
            Map<String, Object> m = Json.obj(
                    "id", v.getId(),
                    "nombre", v.getNombre(),
                    "clase", v.getClass().getSimpleName(),
                    "superclase", v.getClass().getSuperclass().getSimpleName(),
                    "medio", v.getMedio().name(),
                    "energia", v.getTipoEnergia().getDescripcion(),
                    "unidad", v.getTipoEnergia().getUnidad(),
                    "factorEmision", v.getTipoEnergia().getFactorEmision(),
                    "tecnologia", v.getTecnologia(),
                    "capacidadKg", v.getCapacidadKg(),
                    "pesoVacioKg", v.getPesoVacioKg(),
                    "velocidadMaxKmh", v.getVelocidadMaxKmh(),
                    "recargable", v instanceof Recargable);
            if (v instanceof Recargable) {
                m.put("bateriaKWh", ((Recargable) v).getCapacidadBateriaKWh());
            }
            lista.add(m);
        }
        return lista;
    }

    private Object puntos() throws DatosInvalidosException {
        List<Object> lista = new ArrayList<>();
        LectorCSV.leer(carpetaDatos.resolve("puntos.csv"), f -> lista.add(Json.obj(
                "nombre", f.get("nombre"), "categoria", f.get("categoria"),
                "lat", Double.parseDouble(f.get("lat")), "lon", Double.parseDouble(f.get("lon")))));
        return lista;
    }

    private Object ruta(Map<String, String> q) throws GreenRouteException {
        Vehiculo v = vehiculo(q);
        Ruta r = planificador.planificar(coord(q, "o"), coord(q, "d"), v,
                Criterio.desdeTexto(q.get("criterio")), hora(q), carga(q));
        return r.aMapa(true);
    }

    private Object alternativas(Map<String, String> q) throws GreenRouteException {
        Map<Criterio, Ruta> alt = planificador.alternativas(coord(q, "o"), coord(q, "d"), vehiculo(q),
                hora(q), carga(q));
        List<Object> lista = new ArrayList<>();
        for (Ruta r : alt.values()) {
            lista.add(r.aMapa(true));
        }
        return lista;
    }

    private Object comparar(Map<String, String> q) throws GreenRouteException {
        List<OpcionTransporte> opciones = planificador.compararFlota(coord(q, "o"), coord(q, "d"),
                hora(q), carga(q), Criterio.desdeTexto(q.get("criterio")));
        List<Object> lista = new ArrayList<>();
        for (OpcionTransporte op : opciones) {
            lista.add(op.aMapa());
        }
        Optional<OpcionTransporte> mejor = planificador.recomendar(opciones);
        return Json.obj("opciones", lista,
                "recomendado", mejor.map(op -> op.getVehiculo().getId()).orElse(null));
    }

    private Object algoritmos(Map<String, String> q) throws GreenRouteException {
        Vehiculo v = vehiculo(q);
        List<Object> lista = new ArrayList<>();
        for (AlgoritmoRuta alg : new AlgoritmoRuta[]{new Dijkstra(), new AEstrella()}) {
            PlanificadorRutas p = new PlanificadorRutas(planificador.getRed(), planificador.getFlota(),
                    planificador.getPerfil(), alg);
            Ruta r = p.planificar(coord(q, "o"), coord(q, "d"), v, Criterio.desdeTexto(q.get("criterio")),
                    hora(q), carga(q));
            lista.add(Json.obj("algoritmo", alg.getNombre(), "nodosExplorados", r.getNodosExplorados(),
                    "calculoMs", r.getTiempoCalculoMs(), "distanciaKm", r.getDistanciaKm(),
                    "emisionKgCO2", r.getEmisionKgCO2()));
        }
        return lista;
    }

    private Vehiculo vehiculo(Map<String, String> q) throws DatosInvalidosException {
        String id = q.getOrDefault("vehiculo", "CAM-D");
        return planificador.getFlota().buscar(id)
                .orElseThrow(() -> new DatosInvalidosException("Vehículo desconocido: " + id));
    }

    private static Coordenada coord(Map<String, String> q, String prefijo) throws DatosInvalidosException {
        try {
            return new Coordenada(Double.parseDouble(q.get(prefijo + "lat")), Double.parseDouble(q.get(prefijo + "lon")));
        } catch (NullPointerException | IllegalArgumentException e) {
            throw new DatosInvalidosException("Coordenada de " + (prefijo.equals("o") ? "origen" : "destino") + " inválida");
        }
    }

    /** Hora de salida pedida (8 si falta o no es un número). */
    private static int hora(Map<String, String> q) {
        try {
            return Integer.parseInt(q.getOrDefault("hora", "8"));
        } catch (NumberFormatException e) {
            return 8;
        }
    }

    /** Carga en kg pedida (2000 si falta o no es un número). */
    private static double carga(Map<String, String> q) {
        try {
            return Double.parseDouble(q.getOrDefault("carga", "2000"));
        } catch (NumberFormatException e) {
            return 2000;
        }
    }

    @FunctionalInterface
    private interface Endpoint {
        Object atender(Map<String, String> parametros) throws GreenRouteException;
    }

    /** Envuelve un endpoint: lee los parámetros, responde en JSON y convierte errores en códigos HTTP. */
    private HttpHandler api(Endpoint endpoint) {
        return ex -> {
            int codigo = 200;
            Object cuerpo;
            try {
                cuerpo = endpoint.atender(parametros(ex));
            } catch (GreenRouteException e) {
                codigo = 422;
                cuerpo = Json.obj("error", e.getCodigo(), "mensaje", e.getMessage());
            } catch (RuntimeException e) {
                codigo = 400;
                cuerpo = Json.obj("error", "SOLICITUD_INVALIDA", "mensaje", String.valueOf(e.getMessage()));
            }
            // Permite que el sitio publicado en GitHub Pages (otro dominio) consulte este API.
            ex.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            enviar(ex, codigo, "application/json; charset=utf-8", Json.escribir(cuerpo).getBytes(StandardCharsets.UTF_8));
        };
    }

    private void archivoEstatico(HttpExchange ex) throws IOException {
        String ruta = URLDecoder.decode(ex.getRequestURI().getPath(), StandardCharsets.UTF_8);
        if (ruta.endsWith("/")) ruta += "index.html";
        Path archivo = carpetaWeb.resolve(ruta.substring(1)).normalize();
        if (!archivo.startsWith(carpetaWeb) || !Files.isRegularFile(archivo)) {
            enviar(ex, 404, "text/plain; charset=utf-8", "404 — recurso no encontrado".getBytes(StandardCharsets.UTF_8));
            return;
        }
        String nombre = archivo.getFileName().toString();
        String ext = nombre.substring(nombre.lastIndexOf('.') + 1).toLowerCase();
        enviar(ex, 200, TIPOS_MIME.getOrDefault(ext, "application/octet-stream"), Files.readAllBytes(archivo));
    }

    private static Map<String, String> parametros(HttpExchange ex) {
        Map<String, String> mapa = new HashMap<>();
        String consulta = ex.getRequestURI().getRawQuery();
        if (consulta == null) return mapa;
        for (String par : consulta.split("&")) {
            int i = par.indexOf('=');
            if (i > 0) {
                mapa.put(URLDecoder.decode(par.substring(0, i), StandardCharsets.UTF_8),
                        URLDecoder.decode(par.substring(i + 1), StandardCharsets.UTF_8));
            }
        }
        return mapa;
    }

    private static void enviar(HttpExchange ex, int codigo, String tipo, byte[] datos) throws IOException {
        ex.getResponseHeaders().set("Content-Type", tipo);
        ex.getResponseHeaders().set("Cache-Control", "no-cache");
        ex.sendResponseHeaders(codigo, datos.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(datos);
        }
    }
}
