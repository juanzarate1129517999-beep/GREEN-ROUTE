"""Paso 6: JSON del panel principal de la web (indicadores por año) y dibujo SVG de la red vial."""
import json

import numpy as np
import pandas as pd

from config import DATA_PROC, DATA_RAW, DIAS, JAVA_DATA, VEH_CARGA, WEB_DATA


def pct(serie):
    """Porcentaje de verdaderos en una columna booleana (los vacíos cuentan como falso)."""
    return round(float(serie.fillna(False).astype(bool).mean() * 100), 1)


def promedio(aire, contaminante, decimales):
    valores = aire[aire.msfl_code == contaminante].valor
    return round(float(valores.mean()), decimales) if len(valores) else None


def indicadores(a, v, aire):
    """Resume accidentes (a), vehículos (v) y calidad del aire en un diccionario listo para JSON."""
    con_hora = a.dropna(subset=["hora"])
    matriz = (con_hora.pivot_table(index="dia_semana", columns="hora", values="fecha", aggfunc="size", fill_value=0)
              .reindex(index=range(7), columns=range(24), fill_value=0))
    perfil = con_hora["hora"].value_counts().reindex(range(24), fill_value=0)
    clases = a["clase_accidente"].value_counts()
    tipos_veh = v["clase_vehiculo_accidentado"].value_counts().head(8)
    sitios = a["sitio_exacto_accidente"].value_counts().head(5)

    resultado = {
        "accidentes": len(a),
        "con_victimas": int(a["con_victimas"].sum()),
        "con_muertos": int((a["gravedad_accidente"] == "Con muertos").sum()),
        "pct_victimas": round(float(a["con_victimas"].mean() * 100), 1),
        "pct_carga": pct(a["hay_carga"]),
        "pct_moto": pct(a["hay_moto"]),
        "vehiculos": len(v),
        "vehiculos_carga": int(v["clase_vehiculo_accidentado"].isin(VEH_CARGA).sum()),
        "geocodificados": int(a["lat"].notna().sum()),
        "hora_pico": int(perfil.idxmax()),
        "dia_pico": DIAS[int(con_hora["dia_semana"].value_counts().idxmax()) if len(con_hora) else 0],
        "matriz": matriz.values.tolist(),
        "perfil_hora": perfil.values.tolist(),
        "clases": {"nombres": clases.index.tolist(), "valores": clases.values.tolist()},
        "vehiculos_top": {"nombres": tipos_veh.index.tolist(), "valores": tipos_veh.values.tolist()},
        "sitios_top": [{"sitio": s, "accidentes": int(n)} for s, n in sitios.items()],
        "aire": None,
    }

    if len(aire):
        pm25 = aire[aire.msfl_code == "PM2.5"]
        diario = pm25.groupby(["estacion", pm25.fecha.dt.date])["valor"].mean()
        resultado["aire"] = {
            "pm25": promedio(aire, "PM2.5", 1),
            "pm10": promedio(aire, "PM10", 1),
            "co": promedio(aire, "CO", 0),
            "dias_sobre_oms": round(float((diario > 15).mean() * 100), 1) if len(diario) else None,  # límite OMS: 15 µg/m³
            "mediciones": len(aire),
        }
    return resultado


def panel():
    """Indicadores de todo el periodo y de cada año -> panel.json."""
    a = pd.read_csv(DATA_PROC / "accidentes_limpio.csv", parse_dates=["fecha"])
    v = pd.read_csv(DATA_RAW / "vehiculos.csv", parse_dates=["fecha_de_accidente"])
    v["anio"] = v["fecha_de_accidente"].dt.year
    aire = pd.read_csv(DATA_PROC / "aire_limpio.csv", parse_dates=["fecha"])
    aire["anio"] = aire.fecha.dt.year

    anios = sorted(int(x) for x in a.anio.unique())
    por_anio = {"todos": indicadores(a, v, aire)}
    for anio in anios:
        por_anio[str(anio)] = indicadores(a[a.anio == anio], v[v.anio == anio], aire[aire.anio == anio])

    salida = {"anios": anios, "dias": DIAS, "por_anio": por_anio}
    (WEB_DATA / "panel.json").write_text(json.dumps(salida, ensure_ascii=False), encoding="utf-8")
    print(f"✓ panel.json · {len(anios)} años")


def trazo(puntos):
    """Convierte una lista de puntos (x, y) en un trazo SVG: 'M x,y L x,y ...'."""
    return "M" + "L".join(f"{x:.1f},{y:.1f}" for x, y in puntos)


def red_svg():
    """Dibuja las vías principales y el río como trazos SVG proyectados a un lienzo de 1000 px -> red_svg.json."""
    grupos = {"troncal": ("motorway", "trunk", "motorway_link", "trunk_link"),
              "primaria": ("primary", "primary_link"),
              "secundaria": ("secondary", "secondary_link"),
              "terciaria": ("tertiary",)}
    aristas = pd.read_csv(JAVA_DATA / "aristas.csv", usecols=["origen", "destino", "tipo_via", "geometria"])
    aristas = aristas[aristas.tipo_via.isin(sum(grupos.values(), ()))].copy()
    # Cada calle aparece en ambos sentidos: se deja una sola
    aristas["clave"] = [tuple(sorted(par)) for par in zip(aristas.origen, aristas.destino)]
    aristas = aristas.drop_duplicates("clave")
    geometrias = aristas.geometria.str.split(";").apply(lambda pts: [tuple(map(float, p.split(","))) for p in pts])

    # Proyección simple: lon se corrige con cos(lat) y todo se escala a 1000 px de ancho
    lats = [lat for g in geometrias for lat, _ in g]
    lons = [lon for g in geometrias for _, lon in g]
    lat_max, lon_min = max(lats), min(lons)
    kx = np.cos(np.radians((min(lats) + lat_max) / 2))
    ancho = 1000
    escala = ancho / ((max(lons) - lon_min) * kx)

    def proyectar(lat, lon):
        return (lon - lon_min) * kx * escala, (lat_max - lat) * escala

    trazos = {nombre: "".join(trazo([proyectar(*p) for p in g]) for g in geometrias[aristas.tipo_via.isin(tipos)])
              for nombre, tipos in grupos.items()}
    rio = pd.read_csv(JAVA_DATA / "fluvial.csv")
    trazos["rio"] = trazo(proyectar(lat, lon) for lat, lon in zip(rio.lat, rio.lon))

    salida = {"ancho": ancho, "alto": round((max(lats) - min(lats)) * escala, 1), "lat_max": lat_max,
              "lon_min": lon_min, "kx": kx, "escala": escala, "trazos": trazos}
    (WEB_DATA / "red_svg.json").write_text(json.dumps(salida), encoding="utf-8")
    print(f"✓ red_svg.json · {len(aristas):,} tramos principales")


if __name__ == "__main__":
    panel()
    red_svg()
