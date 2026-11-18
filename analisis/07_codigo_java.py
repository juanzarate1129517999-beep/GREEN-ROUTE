"""Paso 7: exporta el código Java a codigo_java.json para la página codigo.html.

Para cada archivo guarda su código y en qué líneas aparece cada concepto de POO (herencia, interfaces, etc.).
"""
import json
import re

from config import RAIZ, WEB_DATA

FUENTES = RAIZ / "greenroute-java" / "src"

# Concepto -> expresión regular que lo detecta en una línea
CONCEPTOS = {
    "Herencia": r"\bextends\s+\w+",
    "Interfaces": r"\bimplements\s+\w+|\binterface\s+\w+",
    "Abstracción": r"\babstract\s+(class|\w+\s+\w+\()",
    "Polimorfismo": r"@Override",
    "Encapsulamiento": r"\bprivate\s+(final\s+)?[\w<>\[\], ]+\s+\w+\s*[;=]",
    "Excepciones": r"\bthrows?\b|\bcatch\s*\(|extends\s+\w*Exception",
    "JOptionPane": r"\bJOptionPane\.\w+",
    "Swing": r"\bextends\s+(J\w+|AbstractTableModel|DefaultTableCellRenderer|DefaultMetalTheme)\b|paintComponent",
}

DESCRIPCION_PAQUETE = {
    "co.edu.greenroute": "Punto de entrada",
    "co.edu.greenroute.vehiculos": "Jerarquía de vehículos",
    "co.edu.greenroute.red": "Red de transporte (grafo)",
    "co.edu.greenroute.ruteo": "Algoritmos y criterios",
    "co.edu.greenroute.servicio": "Fachada del motor",
    "co.edu.greenroute.excepciones": "Excepciones propias",
    "co.edu.greenroute.modelo": "Modelo de dominio",
    "co.edu.greenroute.analitica": "Perfil de tráfico (ML)",
    "co.edu.greenroute.api": "Servidor web / API REST",
    "co.edu.greenroute.util": "Utilidades",
    "co.edu.greenroute.escritorio": "App de escritorio (Swing + JOptionPane)",
}

DECLARACION = re.compile(r"^public\s+(?:final\s+|abstract\s+)*(class|interface|enum|record)\s+(\w+)([^{]*)", re.M)


def es_comentario(linea):
    return linea.strip().startswith(("*", "//", "/*"))


def describir(ruta):
    texto = ruta.read_text(encoding="utf-8").replace("\r\n", "\n")
    lineas = texto.split("\n")
    conceptos = {}
    for nombre, patron in CONCEPTOS.items():
        numeros = [i + 1 for i, l in enumerate(lineas) if re.search(patron, l) and not es_comentario(l)]
        if numeros:
            conceptos[nombre] = numeros
    decl = DECLARACION.search(texto)
    return {
        "ruta": ruta.relative_to(FUENTES).as_posix(),
        "paquete": re.search(r"^package\s+([\w.]+);", texto, re.M).group(1),
        "clase": ruta.stem,
        "tipo": decl.group(1) if decl else "class",
        "abstracta": bool(decl and "abstract" in decl.group(0)),
        "declaracion": " ".join(decl.group(0).split()) if decl else "",
        "lineas": len(lineas),
        "conceptos": conceptos,
        "codigo": texto,
    }


def main():
    archivos = [describir(ruta) for ruta in sorted(FUENTES.rglob("*.java"))]
    paquetes = []
    for paquete, descripcion in DESCRIPCION_PAQUETE.items():
        n = sum(a["paquete"] == paquete for a in archivos)
        if n:
            paquetes.append({"paquete": paquete, "descripcion": descripcion, "archivos": n})
    salida = {"total_archivos": len(archivos), "total_lineas": sum(a["lineas"] for a in archivos),
              "paquetes": paquetes, "archivos": archivos}
    (WEB_DATA / "codigo_java.json").write_text(json.dumps(salida, ensure_ascii=False), encoding="utf-8")
    print(f"✓ {len(archivos)} archivos Java · {salida['total_lineas']:,} líneas → web/data/codigo_java.json")


if __name__ == "__main__":
    main()
