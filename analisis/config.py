"""Rutas de carpetas y constantes compartidas por todos los scripts de análisis."""
from pathlib import Path

# Carpetas del proyecto
RAIZ = Path(__file__).resolve().parent.parent
DATA_RAW = RAIZ / "data" / "raw"            # datos tal como se descargan
DATA_PROC = RAIZ / "data" / "processed"     # datos limpios
WEB_DATA = RAIZ / "web" / "data"            # JSON que lee la página web
WEB_IMG = RAIZ / "web" / "img" / "graficas"  # gráficas en PNG
JAVA_DATA = RAIZ / "greenroute-java" / "datos"  # CSV que lee el motor de rutas en Java

for carpeta in (DATA_RAW, DATA_PROC, WEB_IMG, WEB_DATA, JAVA_DATA):
    carpeta.mkdir(parents=True, exist_ok=True)

# Datos abiertos de Colombia (datos.gov.co)
SOCRATA = "https://www.datos.gov.co/resource/{id}.csv"
FUENTES = {
    "accidentes": {"id": "yb9r-2dsi", "nombre": "Accidentalidad en Barranquilla"},
    "vehiculos": {"id": "nfa3-wgxy", "nombre": "Accidentalidad Barranquilla — detalle de vehículos"},
    "victimas": {"id": "y628-5q9a", "nombre": "Accidentalidad en Barranquilla — víctimas"},
    "aire": {"id": "g4t8-zkc3", "nombre": "Calidad del Aire en Colombia (SISAIRE / IDEAM)"},
}

# Zona de estudio
CENTRO = (10.9685, -74.7813)  # centro de Barranquilla (lat, lon)
LUGAR_OSM = ["Barranquilla, Atlántico, Colombia", "Soledad, Atlántico, Colombia",
             "Puerto Colombia, Atlántico, Colombia", "Malambo, Atlántico, Colombia",
             "Galapa, Atlántico, Colombia"]

# Tipos de vehículo que se consideran de carga
VEH_CARGA = ["CAMION", "TRACTO/CAMION", "VOLQUETA"]
DIAS = ["Lun", "Mar", "Mié", "Jue", "Vie", "Sáb", "Dom"]

SEMILLA = 42  # para que los resultados aleatorios sean reproducibles
