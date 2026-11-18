"""Paso 3: ubica cada accidente en el mapa a partir de su dirección y calcula zonas de riesgo.

1. Interpreta direcciones como "CL 72 CR 43" -> cruce de "Calle 72" con "Carrera 43".
2. Busca ese cruce en la malla vial de OpenStreetMap.
3. Con los accidentes ubicados: mapa de calor, puntos críticos (DBSCAN) y riesgo por tramo de vía.
"""
import json
import re
from collections import defaultdict

import numpy as np
import osmnx as ox
import pandas as pd
from sklearn.cluster import DBSCAN
from sklearn.neighbors import BallTree

from config import DATA_PROC, DATA_RAW, JAVA_DATA, WEB_DATA

RADIO_TIERRA_M = 6_371_000
PESO_GRAVEDAD = {"Solo daños": 1, "Con heridos": 3, "Con muertos": 10}

# ---------------------------------------------------------------- Lectura de direcciones

# Abreviaturas que aparecen en los registros -> nombre de la vía en OSM
TIPOS = {
    "CL": "Calle", "CLL": "Calle", "CLLE": "Calle", "CALLE": "Calle", "CALL": "Calle",
    "CR": "Carrera", "CRA": "Carrera", "KR": "Carrera", "KRA": "Carrera", "CARRERA": "Carrera",
    "CRR": "Carrera", "CARR": "Carrera", "CAR": "Carrera",
    "DG": "Diagonal", "DIAG": "Diagonal", "DIAGONAL": "Diagonal",
    "TV": "Transversal", "TR": "Transversal", "TRANSV": "Transversal", "TRANSVERSAL": "Transversal",
}
# Avenidas conocidas por su nombre popular
AVENIDAS = {
    "CIRCUNVALAR": "Avenida Circunvalar", "MURILLO": "Avenida Murillo",
    "CORDIALIDAD": "Avenida de La Cordialidad", "VIA 40": "Vía 40", "VIA40": "Vía 40",
    "OLAYA": "Carrera 46", "20 DE JULIO": "Carrera 43", "SANTANDER": "Carrera 45", "VIA AL MAR": "Vía al Mar",
}
# Una calle se cruza con una carrera, una diagonal con una transversal
CRUCE = {"Calle": "Carrera", "Carrera": "Calle", "Diagonal": "Transversal", "Transversal": "Diagonal"}
# Nombres de OSM que equivalen a otra vía
ALIAS_OSM = {"avenida murillo": "calle 45", "autopista aeropuerto": "calle 30", "avenida del río": "vía 40"}

# Ej.: "CL 72B BIS SUR" -> tipo=CL, número=72, letra=B, bis, sur
PATRON_VIA = re.compile(
    r"\b(" + "|".join(sorted(TIPOS, key=len, reverse=True)) + r")\.?\s*(\d{1,3})\s*([A-H])?\b(\s*BIS)?(\s*SUR)?")


def normalizar(texto):
    """Mayúsculas, sin tildes ni signos de puntuación."""
    t = str(texto).upper()
    for con_tilde, sin_tilde in zip("ÁÉÍÓÚÑ", "AEIOUN"):
        t = t.replace(con_tilde, sin_tilde)
    t = re.sub(r"[#\-–,.]|N°|NO\.", " ", t)
    return re.sub(r"\s+", " ", t).strip()


def nombre_via(tipo, numero, letra=None, bis=None, sur=None):
    return f"{tipo} {int(numero)}{letra or ''}" + (" Bis" if bis else "") + (" Sur" if sur else "")


def interpretar(direccion):
    """Devuelve las dos vías que se cruzan en la dirección, o None si no se entiende."""
    t = normalizar(direccion)
    vias = []
    for clave, nombre in AVENIDAS.items():
        if clave in t:
            vias.append(nombre)
            t = t.replace(clave, " ")
    for m in PATRON_VIA.finditer(t):
        vias.append(nombre_via(TIPOS[m.group(1)], *m.groups()[1:]))
    vias = list(dict.fromkeys(vias))  # sin repetidos, en orden

    if len(vias) >= 2:
        return vias[0], vias[1]
    # Caso "CL 72 43": la segunda vía solo trae el número; se deduce su tipo
    m = PATRON_VIA.search(t)
    if len(vias) == 1 and m:
        numero = re.match(r"(\d{1,3})\s*([A-H])?\b", t[m.end():].strip())
        tipo = TIPOS[m.group(1)]
        if numero and tipo in CRUCE:
            return vias[0], nombre_via(CRUCE[tipo], numero.group(1), numero.group(2))
    return None

# ---------------------------------------------------------------- Búsqueda en la malla vial


def indice_de_nombres(G):
    """Para cada nombre de vía (en minúsculas), el conjunto de nodos que la forman."""
    indice = defaultdict(set)
    for u, v, d in G.edges(data=True):
        nombres = d.get("name")
        for n in nombres if isinstance(nombres, list) else [nombres]:
            if not isinstance(n, str):
                continue
            clave = n.strip().lower()
            indice[clave].update((u, v))
            m = re.match(r"(avenida|autopista)\s+((calle|carrera)\s+\S+)", clave)
            if m:  # "Avenida Calle 30" también es "calle 30"
                indice[m.group(2)].update((u, v))
            if clave in ALIAS_OSM:
                indice[ALIAS_OSM[clave]].update((u, v))
    return indice


def buscar_cruce(via1, via2, indice, coords):
    """Coordenadas del cruce de dos vías y el método ("exacta" o "aproximada"), o None."""
    a, b = indice.get(via1.lower(), set()), indice.get(via2.lower(), set())
    if not a or not b:
        return None
    comunes = a & b
    if comunes:  # comparten un nodo: el cruce es exacto
        return np.array([coords[n] for n in comunes]).mean(axis=0), "exacta"
    # Si no se tocan, se toma el par de nodos más cercano (máximo 150 m)
    pa = np.radians([coords[n] for n in a])
    pb = np.radians([coords[n] for n in b])
    dist, cercano = BallTree(pa, metric="haversine").query(pb, k=1)
    j = int(np.argmin(dist))
    if dist[j][0] * RADIO_TIERRA_M <= 150:
        return (np.degrees(pa[cercano[j][0]]) + np.degrees(pb[j])) / 2, "aproximada"
    return None


def geocodificar(acc, G):
    """Agrega a cada accidente las columnas lat, lon y geocodificacion."""
    coords = {n: (d["y"], d["x"]) for n, d in G.nodes(data=True)}
    indice = indice_de_nombres(G)
    ubicadas = {}  # dirección -> resultado, para no buscar dos veces la misma
    for direccion in acc["sitio_exacto_accidente"].fillna("").unique():
        vias = interpretar(direccion)
        res = buscar_cruce(*vias, indice, coords) if vias else None
        ubicadas[direccion] = (res[0][0], res[0][1], res[1]) if res else (np.nan, np.nan, "no ubicada")
    resultado = acc["sitio_exacto_accidente"].fillna("").map(ubicadas)
    acc["lat"] = resultado.str[0]
    acc["lon"] = resultado.str[1]
    acc["geocodificacion"] = resultado.str[2]
    return len(ubicadas)

# ---------------------------------------------------------------- Análisis espacial


def puntos_criticos(geo, X):
    """Agrupa accidentes cercanos (radio 90 m, mínimo 30) con DBSCAN y ordena por severidad."""
    geo["cluster"] = DBSCAN(eps=90 / RADIO_TIERRA_M, min_samples=30, metric="haversine",
                            algorithm="ball_tree").fit(X).labels_  # -1 = no pertenece a ningún grupo
    en_grupo = geo[geo.cluster >= 0]
    grupos = []
    for c, g in en_grupo.groupby("cluster"):
        grupos.append({
            "cluster": int(c),
            "lat": round(float(g.lat.mean()), 6), "lon": round(float(g.lon.mean()), 6),
            "accidentes": len(g),
            "heridos": int((g.gravedad_accidente == "Con heridos").sum()),
            "muertos": int((g.gravedad_accidente == "Con muertos").sum()),
            "indice_severidad": round(float(g.peso.sum()), 1),
            "sitio_frecuente": str(g["sitio_exacto_accidente"].value_counts().index[0]),
        })
    grupos.sort(key=lambda d: d["indice_severidad"], reverse=True)
    for i, g in enumerate(grupos, 1):
        g["ranking"] = i
    salida = {"total_clusters": len(grupos), "accidentes_en_clusters": len(en_grupo),
              "eps_m": 90, "min_samples": 30, "puntos": grupos[:20]}
    (WEB_DATA / "puntos_criticos.json").write_text(json.dumps(salida, ensure_ascii=False, indent=1), encoding="utf-8")
    print(f"DBSCAN: {len(grupos)} puntos críticos; {len(en_grupo):,} accidentes agrupados")


def riesgo_por_tramo(geo, X, G):
    """Riesgo de 0 a 1 por tramo: suma de pesos de accidentes a menos de 150 m de su punto medio."""
    tramos = list(G.edges())
    medios = [((G.nodes[u]["y"] + G.nodes[v]["y"]) / 2, (G.nodes[u]["x"] + G.nodes[v]["x"]) / 2) for u, v in tramos]
    vecinos = BallTree(X, metric="haversine").query_radius(np.radians(medios), r=150 / RADIO_TIERRA_M)
    pesos = geo["peso"].values
    densidad = np.log1p([pesos[ix].sum() for ix in vecinos])
    riesgo = np.clip(densidad / np.percentile(densidad[densidad > 0], 99), 0, 1)  # percentil 99 -> 1

    df = pd.DataFrame(tramos, columns=["origen", "destino"])
    df["riesgo"] = riesgo.round(4)
    df = df[df.riesgo > 0]
    df.to_csv(JAVA_DATA / "riesgo_aristas.csv", index=False)
    print(f"Riesgo asignado a {len(df):,} tramos (de {len(tramos):,})")


def main():
    G = ox.load_graphml(DATA_PROC / "red_vial.graphml")
    acc = pd.read_csv(DATA_RAW / "accidentes.csv")
    direcciones_unicas = geocodificar(acc, G)
    acc.to_csv(DATA_PROC / "accidentes_geo.csv", index=False)

    conteo = acc["geocodificacion"].value_counts()
    tasa = 100 * (1 - conteo.get("no ubicada", 0) / len(acc))
    print(conteo.to_string())
    print(f"Tasa de geocodificación: {tasa:.1f} %  ({direcciones_unicas:,} direcciones únicas)")
    (WEB_DATA / "geocodificacion.json").write_text(json.dumps({
        "total": len(acc), "ubicados": int(acc.lat.notna().sum()),
        "tasa_pct": round(tasa, 1), "direcciones_unicas": direcciones_unicas,
        "exacta": int(conteo.get("exacta", 0)), "aproximada": int(conteo.get("aproximada", 0)),
        "no_ubicada": int(conteo.get("no ubicada", 0)),
    }, indent=1), encoding="utf-8")

    geo = acc.dropna(subset=["lat"]).copy()
    geo["peso"] = geo["gravedad_accidente"].map(PESO_GRAVEDAD).fillna(1)

    calor = geo.groupby([geo.lat.round(5), geo.lon.round(5)])["peso"].sum().reset_index()
    (WEB_DATA / "accidentes_heat.json").write_text(json.dumps(calor.values.round(5).tolist()), encoding="utf-8")

    X = np.radians(geo[["lat", "lon"]].values)
    puntos_criticos(geo, X)
    riesgo_por_tramo(geo, X, G)


if __name__ == "__main__":
    main()
