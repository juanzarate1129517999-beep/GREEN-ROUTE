"""Paso 1: descarga los datos crudos (accidentes, vehículos, víctimas, aire y clima) a data/raw.

Si un archivo ya existe no se vuelve a descargar.
"""
import io
import time

import pandas as pd
import requests

from config import CENTRO, DATA_RAW, FUENTES, SOCRATA

FILAS_POR_PAGINA = 50_000


def descargar_socrata(id_dataset, consulta=None):
    """Descarga un dataset de datos.gov.co página por página y une todas las páginas."""
    paginas, desde = [], 0
    while True:
        params = {"$limit": FILAS_POR_PAGINA, "$offset": desde, "$order": ":id", **(consulta or {})}
        r = requests.get(SOCRATA.format(id=id_dataset), params=params, timeout=180)
        r.raise_for_status()
        pagina = pd.read_csv(io.StringIO(r.text), low_memory=False)
        paginas.append(pagina)
        print(f"   {id_dataset}: {desde + len(pagina):>8,} filas")
        if len(pagina) < FILAS_POR_PAGINA:  # última página
            return pd.concat(paginas, ignore_index=True)
        desde += FILAS_POR_PAGINA
        time.sleep(0.5)  # pausa corta para no saturar el servidor


def descargar_clima():
    """Clima horario 2020-2025 en el centro de Barranquilla (Open-Meteo / ERA5)."""
    params = {
        "latitude": CENTRO[0], "longitude": CENTRO[1],
        "start_date": "2020-01-01", "end_date": "2025-12-31",
        "hourly": "temperature_2m,relative_humidity_2m,precipitation,wind_speed_10m,wind_direction_10m",
        "timezone": "America/Bogota",
    }
    r = requests.get("https://archive-api.open-meteo.com/v1/archive", params=params, timeout=180)
    r.raise_for_status()
    return pd.DataFrame(r.json()["hourly"]).rename(columns={"time": "fecha_hora"})


def guardar_si_falta(nombre_archivo, descripcion, descargar):
    destino = DATA_RAW / nombre_archivo
    if destino.exists():
        print(f"✓ {nombre_archivo} ya existe")
        return
    print(f"↓ {descripcion}")
    descargar().to_csv(destino, index=False)


def main():
    for clave in ("accidentes", "vehiculos", "victimas"):
        guardar_si_falta(f"{clave}.csv", FUENTES[clave]["nombre"],
                         lambda clave=clave: descargar_socrata(FUENTES[clave]["id"]))

    consulta_aire = {
        "$select": "nombre_fgda,nombre_est,msfl_code,med_concentracion_estandar,"
                   "med_fecha_inicio,latitud,longitud,sigla_unidad,municipio",
        "$where": "municipio in('Barranquilla','Soledad','Malambo')",
    }
    guardar_si_falta("aire_epa.csv", "Calidad del aire — estaciones del Área Metropolitana",
                     lambda: descargar_socrata(FUENTES["aire"]["id"], consulta_aire))
    guardar_si_falta("clima_openmeteo.csv", "Clima horario (Open-Meteo / ERA5)", descargar_clima)


if __name__ == "__main__":
    main()
