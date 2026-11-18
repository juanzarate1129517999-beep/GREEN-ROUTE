"""Paso 4: limpieza de datos y estudio exploratorio (muestreo, accidentalidad, calidad del aire, red vial).

Produce los CSV limpios, las gráficas del estudio y resumen_estudio.json para la web.
"""
import json
import math

import numpy as np
import pandas as pd
from scipy import stats

import graficas as gf
from config import DATA_PROC, DATA_RAW, DIAS, JAVA_DATA, SEMILLA, VEH_CARGA, WEB_DATA

TIPOS_VIA_ES = {
    "residential": "Residencial", "tertiary": "Terciaria", "secondary": "Secundaria",
    "unclassified": "Sin clasificar", "trunk": "Troncal", "primary": "Primaria",
    "busway": "Carril Transmetro", "trunk_link": "Enlace troncal",
}
MESES = ["ene", "feb", "mar", "abr", "may", "jun", "jul", "ago", "sep", "oct", "nov", "dic"]
GUIA_OMS_PM25_DIARIA = 15  # µg/m³


def leer_json(nombre):
    return json.loads((WEB_DATA / nombre).read_text(encoding="utf-8"))


def escribir_json(nombre, datos):
    (WEB_DATA / nombre).write_text(json.dumps(datos, ensure_ascii=False, indent=1), encoding="utf-8")


def miles(n):
    """1234567 -> '1.234.567'"""
    return f"{n:,}".replace(",", ".")

# ---------------------------------------------------------------- Limpieza


def cargar_accidentes():
    """Une accidentes con el resumen de sus vehículos y con las coordenadas del paso 3."""
    a = pd.read_csv(DATA_RAW / "accidentes.csv")
    a["fecha"] = pd.to_datetime(a["fecha_accidente"])
    a["hora"] = pd.to_datetime(a["hora_accidente"].str.upper(), format="%I:%M:%S:%p", errors="coerce").dt.hour
    a["anio"] = a["fecha"].dt.year
    a["mes"] = a["fecha"].dt.month
    a["dia_semana"] = a["fecha"].dt.dayofweek  # 0 = lunes
    a["con_victimas"] = (a["gravedad_accidente"] != "Solo daños").astype(int)

    # Qué tipos de vehículo participaron en cada accidente
    v = pd.read_csv(DATA_RAW / "vehiculos.csv")
    clase = v["clase_vehiculo_accidentado"]
    v["carga"] = clase.isin(VEH_CARGA)
    v["moto"] = clase.isin(["MOTOCICLETA", "MOTOCARRO", "MOTOTRICICLO"])
    v["bus"] = clase.isin(["BUS", "BUSETA", "MICROBUS"])
    v["bici"] = clase.isin(["BICICLETA", "CICLO TAXI"])
    v["publico"] = v["servicio_vehiculo_accidentado"] == "PUBLICO"
    por_accidente = v.groupby(["fecha_de_accidente", "direccion_accidente"]).agg(
        n_vehiculos=("clase_vehiculo_accidentado", "size"), hay_carga=("carga", "max"),
        hay_moto=("moto", "max"), hay_bus=("bus", "max"), hay_bici=("bici", "max"),
        hay_publico=("publico", "max")).reset_index()
    a = a.merge(por_accidente, left_on=["fecha_accidente", "sitio_exacto_accidente"],
                right_on=["fecha_de_accidente", "direccion_accidente"], how="left")
    a = a.drop(columns=["fecha_de_accidente", "direccion_accidente"])

    geo = pd.read_csv(DATA_PROC / "accidentes_geo.csv", usecols=["lat", "lon", "geocodificacion"])
    a[["lat", "lon", "geocodificacion"]] = geo.values
    a.to_csv(DATA_PROC / "accidentes_limpio.csv", index=False)
    return a, v


def cargar_aire():
    df = pd.read_csv(DATA_RAW / "aire_epa.csv")
    df = df[df["msfl_code"].isin(["PM2.5", "PM10", "CO", "O3"])].copy()
    df["fecha"] = pd.to_datetime(df["med_fecha_inicio"])
    df["valor_original"] = df["med_concentracion_estandar"]
    # Hasta 2023 la fuente publica los valores multiplicados por 100
    df["valor"] = np.where(df["fecha"].dt.year <= 2023, df["valor_original"] / 100, df["valor_original"])
    df = df[df["valor"] > 0]
    limite = df.groupby("msfl_code")["valor"].transform(lambda s: s.quantile(0.999))
    df = df[df["valor"] <= limite]  # quita el 0,1 % de valores más extremos
    df["estacion"] = df["nombre_est"].str.title().str.replace("Movil", "Móvil")
    df.to_csv(DATA_PROC / "aire_limpio.csv", index=False)
    return df

# ---------------------------------------------------------------- Muestreo


def tamano_muestra(N, z=1.96, p=0.5, e=0.03):
    """Tamaño de muestra para una proporción (95 % de confianza, error 3 %), con corrección por población finita."""
    n0 = z ** 2 * p * (1 - p) / e ** 2
    return n0, math.ceil(n0 / (1 + (n0 - 1) / N))


def prueba_chi2(a, m, variable):
    """¿La muestra m tiene la misma distribución que la población a en esta variable?"""
    pob = a[variable].value_counts(normalize=True)
    obs = m[variable].value_counts().reindex(pob.index, fill_value=0)
    esp = pob * len(m)
    chicas = esp < 5  # las categorías con esperado < 5 se juntan en una sola
    if chicas.any():
        obs = np.append(obs[~chicas].values, obs[chicas].sum())
        esp = np.append(esp[~chicas].values, esp[chicas].sum())
    else:
        obs, esp = obs.values, esp.values
    chi2, p = stats.chisquare(obs, esp * obs.sum() / esp.sum())
    return chi2, p, len(obs) - 1


def muestreo(a):
    """Muestreo estratificado por año con asignación proporcional y validación chi-cuadrado."""
    N = len(a)
    n0, n = tamano_muestra(N)
    estratos = a["anio"].value_counts().sort_index()
    cuota = estratos / N * n
    asignacion = np.floor(cuota).astype(int)
    faltan = n - asignacion.sum()  # lo que se pierde al redondear va a los estratos con mayor decimal
    asignacion[(cuota - asignacion).sort_values(ascending=False).index[:faltan]] += 1

    rng = np.random.default_rng(SEMILLA)
    m = pd.concat([a[a.anio == anio].sample(k, random_state=int(rng.integers(1e9)))
                   for anio, k in asignacion.items()])
    m.to_csv(DATA_PROC / "muestra_estratificada.csv", index=False)

    pruebas = []
    for variable, etiqueta in (("gravedad_accidente", "Gravedad"), ("clase_accidente", "Clase de accidente"),
                               ("dia_semana", "Día de la semana")):
        chi2, p, gl = prueba_chi2(a, m, variable)
        pruebas.append({"variable": etiqueta, "chi2": round(float(chi2), 3), "gl": int(gl),
                        "p_valor": round(float(p), 4), "representativa": bool(p > 0.05)})

    # Intervalo de confianza del 95 % para la proporción de accidentes con víctimas
    p_hat = m["con_victimas"].mean()
    ee = math.sqrt(p_hat * (1 - p_hat) / len(m) * (1 - len(m) / N))
    resultado = {
        "N": N, "z": 1.96, "p": 0.5, "error": 0.03, "n0": round(n0, 1), "n": n,
        "estratos": [{"anio": int(k), "N_h": int(estratos[k]), "n_h": int(asignacion[k])} for k in estratos.index],
        "pruebas": pruebas,
        "estimacion": {"parametro": "Proporción de accidentes con víctimas",
                       "muestra": round(p_hat, 4), "ic95": [round(p_hat - 1.96 * ee, 4), round(p_hat + 1.96 * ee, 4)],
                       "poblacion": round(float(a["con_victimas"].mean()), 4)},
    }
    escribir_json("muestreo.json", resultado)

    pob = a["gravedad_accidente"].value_counts(normalize=True) * 100
    mue = m["gravedad_accidente"].value_counts(normalize=True).reindex(pob.index) * 100
    gf.barras("muestreo_gravedad", pob.index, {f"Población (N = {miles(N)})": pob.values, f"Muestra (n = {n})": mue.values},
              "Población vs. muestra: gravedad del accidente",
              "Porcentaje de accidentes por categoría · muestreo estratificado por año", eje_y="%", formato=".1f")
    gf.barras("muestreo_estratos", [str(x) for x in estratos.index],
              {"Población": (estratos / N * 100).values,
               "Muestra": (m["anio"].value_counts(normalize=True).reindex(estratos.index) * 100).values},
              "Asignación proporcional por estrato (año)",
              "Participación de cada año en la población y en la muestra", eje_y="%", formato=".1f")
    print(f"Muestreo: N={N:,}  n0={n0:.1f}  n={n}  ·  " + "  ".join(f"{p['variable']}: p={p['p_valor']}" for p in pruebas))
    return resultado

# ---------------------------------------------------------------- Accidentalidad


def accidentalidad(a, v):
    por_tipo = a.pivot_table(index="anio", columns="con_victimas", values="fecha", aggfunc="size", fill_value=0)
    gf.barras("acc_anual", [f"{x}*" if x == 2026 else str(x) for x in por_tipo.index],
              {"Con víctimas": por_tipo[1].values, "Solo daños": por_tipo[0].values},
              "Accidentes registrados por año y gravedad",
              "Desde 2023 casi no se registran choques de solo daños · *2026: enero a septiembre",
              eje_y="Accidentes", apilado=True)

    mensual = a.set_index("fecha").resample("MS").size()
    victimas = a[a.con_victimas == 1].set_index("fecha").resample("MS").size().reindex(mensual.index, fill_value=0)
    gf.lineas("acc_mensual", [d.strftime("%Y-%m") for d in mensual.index],
              {"Total registrado": mensual.values, "Con víctimas (serie consistente)": victimas.values},
              "Evolución mensual de la accidentalidad", "Enero 2018 – septiembre 2026",
              eje_y="Accidentes por mes", formato=",.0f")

    con_hora = a.dropna(subset=["hora"])
    tabla = con_hora.pivot_table(index="dia_semana", columns="hora", values="fecha", aggfunc="size", fill_value=0)
    gf.mapa_calor("acc_hora_dia", [str(int(h)) for h in tabla.columns], DIAS, tabla.values,
                  "¿Cuándo ocurren los accidentes?", "Número de accidentes por hora del día y día de la semana",
                  eje_x="Hora del día")

    clase = a["clase_accidente"].value_counts()
    gf.barras("acc_clase", clase.index, {"Accidentes": clase.values}, "Accidentes por clase",
              "Total 2018-2026", horizontal=True, eje_x="Accidentes")

    vc = v["clase_vehiculo_accidentado"].value_counts().head(12)
    gf.barras("veh_clases", vc.index, {"Vehículos involucrados": vc.values},
              "Vehículos involucrados en accidentes, por clase",
              "En naranja, los vehículos de carga (camión, tractocamión, volqueta)", horizontal=True,
              resaltar=VEH_CARGA, eje_x="Vehículos")

    def pct_por_hora(grupo):
        return (grupo["hora"].value_counts(normalize=True) * 100).reindex(range(24), fill_value=0).values

    con_carga = con_hora["hay_carga"] == True  # noqa: E712 (la columna tiene vacíos)
    gf.lineas("carga_hora", list(range(24)),
              {"Con vehículo de carga": pct_por_hora(con_hora[con_carga]),
               "Sin vehículo de carga": pct_por_hora(con_hora[~con_carga])},
              "Distribución horaria: accidentes con y sin vehículos de carga",
              "Porcentaje de los accidentes de cada grupo que ocurre en cada hora", eje_x="Hora", eje_y="%")

    top = leer_json("puntos_criticos.json")["puntos"][:10]
    gf.barras("puntos_criticos", [f"#{p['ranking']} {p['sitio_frecuente']}" for p in top],
              {"Índice de severidad": [p["indice_severidad"] for p in top]},
              "Puntos críticos de accidentalidad (DBSCAN)",
              "Índice = solo daños×1 + heridos×3 + muertos×10 · radio 90 m", horizontal=True,
              eje_x="Índice de severidad")

    victimas_entre = lambda desde, hasta: len(a[a.anio.between(desde, hasta) & (a.con_victimas == 1)])
    return {
        "accidentes": len(a), "victimas_heridos": int((a.gravedad_accidente == "Con heridos").sum()),
        "con_muertos": int((a.gravedad_accidente == "Con muertos").sum()),
        "vehiculos": len(v), "vehiculos_carga": int(v["carga"].sum()),
        "pct_con_carga": round(float(a["hay_carga"].fillna(False).mean() * 100), 1),
        "hora_pico": int(a["hora"].mode()[0]), "anio_max": int(a.groupby("anio").size().idxmax()),
        "pct_victimas_2018_2021": round(float(a[a.anio <= 2021].con_victimas.mean() * 100), 1),
        "pct_victimas_2023_2026": round(float(a[a.anio >= 2023].con_victimas.mean() * 100), 1),
        "victimas_mes_2018_2022": round(victimas_entre(2018, 2022) / 60, 1),
        "victimas_mes_2023_2025": round(victimas_entre(2023, 2025) / 36, 1),
    }

# ---------------------------------------------------------------- Calidad del aire


def nombre_mes(periodo):
    return f"{MESES[periodo.month - 1]} {periodo.year}"


def agrupar_seguidos(meses):
    """[ene, feb, mar, jun] -> [(ene, mar), (jun, jun)]"""
    tramos = []
    for m in meses:
        if tramos and (m - tramos[-1][1]).n == 1:
            tramos[-1][1] = m
        else:
            tramos.append([m, m])
    return tramos


def cobertura_aire(df):
    """Qué porcentaje de las horas de cada mes tiene datos, por contaminante, y qué meses faltan."""
    meses = pd.period_range("2020-01", "2025-12", freq="M")
    horas_mes = np.array([m.days_in_month * 24 for m in meses])
    orden = ["PM2.5", "PM10", "CO", "O3"]
    d = df.assign(m=df.fecha.dt.to_period("M"), h=df.fecha.dt.floor("h"))
    horas_con_dato = d.groupby(["msfl_code", "m"])["h"].nunique().unstack(0).reindex(meses).fillna(0)[orden]
    cob = (horas_con_dato.T / horas_mes * 100).clip(upper=100)  # filas = contaminante, columnas = mes
    gf.mapa_calor("aire_cobertura", [m.strftime("%Y-%m") for m in meses], ["PM2.5", "PM10", "CO", "O₃"], cob.values,
                  "Cobertura de las mediciones de calidad del aire",
                  "% de horas del mes con al menos un dato publicado · celdas marcadas = la fuente no publicó mediciones",
                  eje_x="Mes", formato=".0f", paso_x=6, etiqueta_barra="% de horas con dato", cero_vacio=True)

    sin_nada = [m for m in meses if (cob[m] == 0).all()]
    tramos = []
    for desde, hasta in agrupar_seguidos([m for m in meses if cob.loc["PM2.5", m] == 0]):
        rango = [m for m in meses if desde <= m <= hasta]
        tramos.append({"desde": str(desde), "hasta": str(hasta), "meses": len(rango),
                       "texto": f"{nombre_mes(desde)} – {nombre_mes(hasta)}",
                       "otros_con_dato": [c for c in orden[1:] if (cob.loc[c, rango] > 0).any()],
                       "meses_sin_ningun_dato": [str(m) for m in rango if m in sin_nada]})
    return {
        "cobertura_media": {c: round(float(cob.loc[c].mean()), 1) for c in orden},
        "tramos_sin_pm25": tramos,
        "meses_sin_ningun_contaminante": [str(m) for m in sin_nada],
        "texto_meses_sin_ningun_contaminante": ", ".join(nombre_mes(m) for m in sin_nada),
    }


def calidad_aire(df):
    cobertura = cobertura_aire(df)

    pm10 = df[df.msfl_code == "PM10"]
    anual = pm10.groupby([pm10.fecha.dt.year, "estacion"])["valor"].median().unstack()
    anual = anual[[c for c in anual.columns if anual[c].notna().sum() >= 2]]
    gf.lineas("aire_correccion", [str(x) for x in anual.index], {c: anual[c].values for c in anual.columns},
              "Continuidad de PM10 tras la corrección de escala",
              "Mediana anual por estación (µg/m³) · valores 2020-2023 divididos entre 100",
              eje_y="PM10 (µg/m³)", marcadores=True)

    pm25 = df[df.msfl_code == "PM2.5"]
    mensual = pm25.groupby([pd.Grouper(key="fecha", freq="MS"), "estacion"])["valor"].mean().unstack()
    mensual = mensual[[c for c in mensual.columns if mensual[c].notna().sum() >= 12]]
    mensual = mensual.reindex(pd.date_range(mensual.index.min(), mensual.index.max(), freq="MS"))
    x = [d.strftime("%Y-%m") for d in mensual.index]
    vacios = [{"desde": t["desde"], "hasta": t["hasta"], "etiqueta": f"Sin datos en la fuente\n{t['texto']} ({t['meses']} meses)"}
              for t in cobertura["tramos_sin_pm25"] if t["desde"] in x and t["hasta"] in x]
    gf.lineas("aire_pm25_mensual", x, {c: mensual[c].values for c in mensual.columns},
              "PM2.5: promedio mensual por estación",
              "µg/m³ · EPA Barranquilla Verde, 2020-2025 · franja rayada = meses sin mediciones publicadas",
              eje_y="PM2.5 (µg/m³)", vacios=vacios,
              referencias=[{"valor": 25, "etiqueta": "Norma anual Res. 2254/2017"},
                           {"valor": 5, "etiqueta": "Guía anual OMS 2021"}])

    for contaminante, id_grafica, unidad in (("CO", "aire_co_diurno", "CO (µg/m³)"),
                                             ("PM2.5", "aire_pm25_diurno", "PM2.5 (µg/m³)")):
        d = df[(df.msfl_code == contaminante) & (df.fecha.dt.year >= 2024)]
        laboral = (d.fecha.dt.dayofweek < 5).rename("laboral")
        perfil = d.groupby([laboral, d.fecha.dt.hour.rename("hora")])["valor"].mean().unstack(0)
        gf.lineas(id_grafica, list(range(24)), {"Lunes a viernes": perfil[True].values, "Fin de semana": perfil[False].values},
                  f"Perfil horario de {contaminante}: días laborales vs. fin de semana",
                  "Promedio por hora, todas las estaciones · 2024-2025", eje_x="Hora", eje_y=unidad)

    diario = pm25.groupby(["estacion", pm25.fecha.dt.date])["valor"].mean().reset_index()
    excede = diario.groupby("estacion")["valor"].agg(lambda s: (s > GUIA_OMS_PM25_DIARIA).mean() * 100)
    excede = excede.sort_values(ascending=False)[diario.groupby("estacion").size() >= 200]
    gf.barras("aire_excedencias", excede.index, {"% de días": excede.values},
              "Días que superan la guía diaria de la OMS para PM2.5",
              "Porcentaje de días con promedio > 15 µg/m³ (OMS 2021)", eje_y="% de días", formato=".1f")
    return {
        "mediciones": len(df), "estaciones": int(df.estacion.nunique()),
        "pm25_media": round(float(pm25.valor.mean()), 1), "pm10_media": round(float(pm10.valor.mean()), 1),
        "pm25_dias_sobre_oms_pct": round(float((diario.valor > GUIA_OMS_PM25_DIARIA).mean() * 100), 1),
        "cobertura": cobertura,
    }

# ---------------------------------------------------------------- Red vial y capas del mapa


def red_vial():
    res = leer_json("red_vial_resumen.json")
    km = {TIPOS_VIA_ES.get(k, k): v for k, v in res["km_por_tipo"].items() if v >= 1}
    gf.barras("red_tipos", list(km.keys()), {"km": list(km.values())}, "Kilómetros de malla vial por tipo de vía",
              f"Barranquilla y Soledad · {miles(round(res['km_totales']))} km dirigidos · OpenStreetMap",
              horizontal=True, eje_x="km", formato=",.1f")
    return res


def capas_mapa(df):
    """Estaciones de aire y cauce del río para el mapa web."""
    def media(g, contaminante):
        valores = g[g.msfl_code == contaminante].valor
        return round(float(valores.mean()), 1) if len(valores) else None

    estaciones = [{"estacion": est, "entidad": ent, "lat": float(g.latitud.iloc[0]), "lon": float(g.longitud.iloc[0]),
                   "pm25": media(g, "PM2.5"), "pm10": media(g, "PM10"), "mediciones": len(g),
                   "periodo": f"{g.fecha.dt.year.min()}-{g.fecha.dt.year.max()}"}
                  for (est, ent), g in df.groupby(["estacion", "nombre_fgda"])]
    (WEB_DATA / "estaciones_aire.json").write_text(json.dumps(estaciones, ensure_ascii=False), encoding="utf-8")
    rio = pd.read_csv(JAVA_DATA / "fluvial.csv")
    (WEB_DATA / "rio.json").write_text(json.dumps(rio[["lat", "lon"]].values.round(5).tolist()), encoding="utf-8")


def main():
    a, v = cargar_accidentes()
    aire = cargar_aire()
    capas_mapa(aire)
    resumen = {
        "muestreo": muestreo(a),
        "accidentalidad": accidentalidad(a, v),
        "aire": calidad_aire(aire),
        "red": red_vial(),
        "geocodificacion": leer_json("geocodificacion.json"),
    }
    escribir_json("resumen_estudio.json", resumen)
    gf.exportar()
    print(json.dumps({k: v for k, v in resumen.items() if k != "muestreo"}, ensure_ascii=False, indent=1))


if __name__ == "__main__":
    main()
