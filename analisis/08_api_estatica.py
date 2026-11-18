"""Precalcula respuestas del API Java para publicar el sitio en GitHub Pages (que solo sirve archivos estáticos).

Requiere el motor en marcha:  .\\greenroute-java\\ejecutar.ps1   (http://localhost:8080)
Uso:                          python analisis/08_api_estatica.py

Genera web/data/api/<endpoint>.json con un diccionario clave → {"s": estado HTTP, "d": respuesta}.
La clave se construye igual que en web/js/comun.js (función claveApi): endpoint + parámetros relevantes
ordenados, con los números normalizados a 5 decimales sin ceros sobrantes.
"""
import json
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path

SERVIDOR = "http://localhost:8080/api/"
SALIDA = Path(__file__).resolve().parent.parent / "web" / "data" / "api"
IGNORAR = {"alternativas": {"criterio"}, "comparar": {"vehiculo"}}
CRITERIOS = ["EMISIONES", "TIEMPO", "DISTANCIA", "RIESGO", "ECO"]
HORA, CARGA = 7, 900


def numero(v):
    try:
        float(v)
    except ValueError:
        return v
    return f"{float(v):.5f}".rstrip("0").rstrip(".")


def clave(endpoint, params):
    ign = IGNORAR.get(endpoint, set())
    pares = sorted((k, numero(str(v))) for k, v in params.items() if k not in ign)
    return endpoint + "?" + "&".join(f"{k}={v}" for k, v in pares)


respuestas = {}


def pedir(endpoint, **params):
    k = clave(endpoint, params)
    if k in respuestas.setdefault(endpoint, {}):
        return respuestas[endpoint][k]["d"]
    url = SERVIDOR + endpoint + ("?" + urllib.parse.urlencode(params) if params else "")
    try:
        with urllib.request.urlopen(url, timeout=60) as r:
            estado, cuerpo = r.status, json.loads(r.read().decode("utf-8"))
    except urllib.error.HTTPError as e:
        estado, cuerpo = e.code, json.loads(e.read().decode("utf-8"))
    respuestas[endpoint][k] = {"s": estado, "d": cuerpo}
    return cuerpo


def od(o, d):
    return {"olat": o["lat"], "olon": o["lon"], "dlat": d["lat"], "dlon": d["lon"]}


def main():
    estado = pedir("estado")
    estado["estatico"] = True
    vehiculos = pedir("vehiculos")
    puntos = pedir("puntos")
    ids = [v["id"] for v in vehiculos]
    cat = lambda c: next(p for p in puntos if p["categoria"] == c)
    nom = lambda n: next(p for p in puntos if p["nombre"] == n)

    # Envío por defecto de la portada y del mapa: Puerto → Aeropuerto.
    puerto, aero = cat("Puerto"), cat("Aeropuerto")
    base = od(puerto, aero)
    for v in ids:
        pedir("alternativas", **base, vehiculo=v, criterio="EMISIONES", hora=HORA, carga=CARGA)
        for c in CRITERIOS:
            pedir("ruta", **base, vehiculo=v, criterio=c, hora=HORA, carga=CARGA)
    for c in CRITERIOS:
        pedir("comparar", **base, criterio=c, hora=HORA, carga=CARGA)
        pedir("algoritmos", **base, vehiculo="VAN-E", criterio=c, hora=HORA, carga=CARGA)
    pedir("alternativas", **base, vehiculo="CAM-D", criterio="EMISIONES", hora=HORA, carga=6000)  # capítulo 4 de la portada

    # Envíos del simulador de la portada (mismo orden que ENVIOS en panel.js).
    envios = [(cat("Puerto"), cat("Aeropuerto")), (cat("Zona franca"), nom("Universidad del Norte")),
              (cat("Abastecimiento"), cat("Terminal")), (cat("Industrial"), cat("Residencial")), (cat("Centro"), cat("Comercial"))]
    for o, d in envios:
        par = od(o, d)
        pedir("comparar", **par, criterio="EMISIONES", hora=HORA, carga=CARGA)
        pedir("alternativas", **par, vehiculo="VAN-E", criterio="EMISIONES", hora=HORA, carga=CARGA)
        for v in ids:
            pedir("ruta", **par, vehiculo=v, criterio="EMISIONES", hora=HORA, carga=CARGA)

    # Las 24 horas de salida para el envío por defecto y el simulador (los deslizadores de hora).
    for h in range(24):
        pedir("alternativas", **base, vehiculo="VAN-E", criterio="EMISIONES", hora=h, carga=CARGA)
        for c in CRITERIOS:
            pedir("ruta", **base, vehiculo="VAN-E", criterio=c, hora=h, carga=CARGA)
        for o, d in envios:
            pedir("comparar", **od(o, d), criterio="EMISIONES", hora=h, carga=CARGA)

    respuestas["estado"][clave("estado", {})]["d"] = estado
    SALIDA.mkdir(parents=True, exist_ok=True)
    total = 0
    for endpoint, datos in respuestas.items():
        ruta = SALIDA / f"{endpoint}.json"
        ruta.write_text(json.dumps(datos, ensure_ascii=False, separators=(",", ":")), encoding="utf-8")
        total += ruta.stat().st_size
        print(f"{endpoint:13s} {len(datos):3d} respuestas · {ruta.stat().st_size / 1024:7.1f} KB")
    print(f"Total: {total / 1024 / 1024:.2f} MB en {SALIDA}")


if __name__ == "__main__":
    main()
