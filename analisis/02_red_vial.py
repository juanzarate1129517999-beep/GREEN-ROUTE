"""Paso 2: descarga de OpenStreetMap la malla vial, el río Magdalena y los puntos logísticos.

Genera los CSV que usa el motor Java (nodos, aristas, fluvial, puntos) y un resumen para la web.
Necesita internet: no guarda caché de las descargas.
"""
import json
import math

import osmnx as ox
import pandas as pd
from shapely.geometry import LineString, MultiLineString, box
from shapely.ops import linemerge

from config import DATA_PROC, JAVA_DATA, LUGAR_OSM, WEB_DATA

ox.settings.use_cache = False  # sin carpeta cache/: cada ejecución descarga de nuevo
ox.settings.log_console = False
ox.settings.requests_timeout = 240

# Velocidad típica (km/h) por tipo de vía de OSM
VEL_POR_TIPO = {
    "motorway": 80, "motorway_link": 50, "trunk": 60, "trunk_link": 40,
    "primary": 50, "primary_link": 35, "secondary": 45, "secondary_link": 30,
    "tertiary": 40, "tertiary_link": 30, "residential": 30, "unclassified": 30,
    "living_street": 10, "service": 20, "road": 30,
}

# Servidores Overpass alternativos por si uno falla
SERVIDORES_OVERPASS = ["https://overpass-api.de/api", "https://overpass.kumi.systems/api",
                       "https://maps.mail.ru/osm/tools/overpass/api", "https://overpass.private.coffee/api"]

# (nombre que se muestra, categoría, texto para buscarlo en el mapa)
PUNTOS = [
    ("Puerto de Barranquilla", "Puerto", "Puerto de Barranquilla"),
    ("Zona Franca de Barranquilla", "Zona franca", "Zona Franca, Barranquilla"),
    ("Aeropuerto Ernesto Cortissoz", "Aeropuerto", "Aeropuerto Internacional Ernesto Cortissoz"),
    ("Mercado de Barranquillita", "Abastecimiento", "Barranquillita, Barranquilla"),
    ("Terminal de Transportes", "Terminal", "Terminal de Transportes, Soledad"),
    ("Centro Histórico (Paseo Bolívar)", "Centro", "Paseo Bolívar, Barranquilla"),
    ("Estadio Metropolitano", "Referencia", "Estadio Metropolitano Roberto Meléndez"),
    ("Universidad del Norte", "Referencia", "Universidad del Norte, Puerto Colombia"),
    ("Vía 40 — Zona Industrial", "Industrial", "Vía 40, Barranquilla"),
    ("Centro Comercial Buenavista", "Comercial", "Centro Comercial Buenavista, Barranquilla"),
    ("Malecón del Río", "Referencia", "Gran Malecón del Río, Barranquilla"),
    ("Soledad 2000", "Residencial", "Soledad 2000, Soledad, Atlántico"),
]


def vacio(valor):
    return valor is None or (isinstance(valor, float) and math.isnan(valor))


def primero(valor):
    """OSM a veces guarda listas (p. ej. dos nombres para una vía): se toma el primero."""
    if isinstance(valor, list):
        return valor[0]
    return "" if vacio(valor) else str(valor)


def probar_servidores(funcion, *args, **kwargs):
    """Llama a una función de OSMnx probando cada servidor Overpass hasta que uno responda."""
    for url in SERVIDORES_OVERPASS:
        ox.settings.overpass_url = url
        try:
            return funcion(*args, **kwargs)
        except Exception as e:
            print(f"   ⚠ {url} falló ({type(e).__name__}); probando otro servidor…")
            error = e
    raise error


def construir_red_vial():
    print("↓ Descargando malla vial de OpenStreetMap (área metropolitana)…")
    G = probar_servidores(ox.graph_from_place, LUGAR_OSM, network_type="drive", simplify=True)
    G = ox.add_edge_speeds(G, hwy_speeds=VEL_POR_TIPO, fallback=30)
    G = ox.add_edge_travel_times(G)
    G = ox.truncate.largest_component(G, strongly=True)  # solo la parte donde se puede ir de todo a todo
    ox.save_graphml(G, DATA_PROC / "red_vial.graphml")
    print(f"   nodos={G.number_of_nodes():,}  aristas={G.number_of_edges():,}")

    nodos = pd.DataFrame([(n, d["y"], d["x"]) for n, d in G.nodes(data=True)], columns=["id", "lat", "lon"])

    filas = []
    for u, v, d in G.edges(data=True):
        tipo = primero(d.get("highway", "road"))
        # Geometría como texto "lat,lon;lat,lon;..." (si no hay, línea recta entre los dos nodos)
        geom = d.get("geometry")
        puntos = [(G.nodes[u]["x"], G.nodes[u]["y"]), (G.nodes[v]["x"], G.nodes[v]["y"])] if vacio(geom) else geom.coords
        filas.append({
            "origen": u, "destino": v,
            "longitud_m": round(float(d["length"]), 1),
            "tipo_via": tipo,
            "velocidad_kmh": round(float(min(d.get("speed_kph", 30), VEL_POR_TIPO.get(tipo, 50) + 10)), 1),
            "nombre": primero(d.get("name")).replace(";", " ").replace('"', ""),
            "geometria": ";".join(f"{y:.5f},{x:.5f}" for x, y in puntos),
        })
    aristas = pd.DataFrame(filas)
    nodos.to_csv(JAVA_DATA / "nodos.csv", index=False)
    aristas.to_csv(JAVA_DATA / "aristas.csv", index=False)
    return G, aristas


def construir_red_fluvial():
    """Toma el cauce del río Magdalena y lo convierte en una línea de puntos cada ~300 m."""
    print("↓ Descargando cauce del río Magdalena…")
    zona = (-74.86, 10.85, -74.72, 11.11)  # oeste, sur, este, norte
    rios = probar_servidores(ox.features_from_bbox, zona, tags={"waterway": "river"})
    rios = rios[rios["name"].fillna("").str.contains("Magdalena", case=False)]

    # Recortar a la zona y unir los pedazos en una sola línea
    lineas = []
    for g in rios.geometry:
        if isinstance(g, (LineString, MultiLineString)):
            recorte = g.intersection(box(*zona))
            if isinstance(recorte, LineString) and not recorte.is_empty:
                lineas.append(recorte)
            elif isinstance(recorte, MultiLineString):
                lineas.extend(recorte.geoms)
    rio = linemerge(lineas)
    if isinstance(rio, MultiLineString):
        rio = max(rio.geoms, key=lambda g: g.length)  # el tramo más largo
    coords = list(rio.coords)
    if coords[0][1] > coords[-1][1]:
        coords.reverse()  # que vaya de sur a norte
    rio = LineString(coords)

    n = max(2, int(rio.length / 0.0028))  # 0.0028° ≈ 300 m
    puntos = [rio.interpolate(i / n, normalized=True) for i in range(n + 1)]
    df = pd.DataFrame([{"id": f"R{i}", "lat": round(p.y, 6), "lon": round(p.x, 6)} for i, p in enumerate(puntos)])
    df.to_csv(JAVA_DATA / "fluvial.csv", index=False)
    print(f"   {len(df)} nodos fluviales")


def geocodificar_puntos():
    """Busca las coordenadas de los puntos logísticos (puerto, aeropuerto, etc.)."""
    filas = []
    for nombre, categoria, consulta in PUNTOS:
        try:
            lat, lon = ox.geocode(consulta + ", Colombia")
            filas.append({"nombre": nombre, "categoria": categoria, "lat": round(lat, 6), "lon": round(lon, 6)})
            print(f"   ✓ {nombre}: {lat:.5f}, {lon:.5f}")
        except Exception as e:
            print(f"   ✗ {nombre}: {e}")
    pd.DataFrame(filas).to_csv(JAVA_DATA / "puntos.csv", index=False)


def resumen(G, aristas):
    km = (aristas.groupby("tipo_via")["longitud_m"].sum() / 1000).sort_values(ascending=False)
    grados = [d for _, d in G.degree()]
    res = {
        "nodos": G.number_of_nodes(),
        "aristas": G.number_of_edges(),
        "km_totales": round(float(aristas["longitud_m"].sum() / 1000), 1),
        "km_por_tipo": {k: round(float(v), 1) for k, v in km.items()},
        "grado_medio": round(sum(grados) / len(grados), 2),
        "vias_con_nombre_pct": round(float((aristas["nombre"] != "").mean() * 100), 1),
    }
    texto = json.dumps(res, indent=2, ensure_ascii=False)
    (WEB_DATA / "red_vial_resumen.json").write_text(texto, encoding="utf-8")
    print(texto)


if __name__ == "__main__":
    G, aristas = construir_red_vial()
    construir_red_fluvial()
    geocodificar_puntos()
    resumen(G, aristas)
