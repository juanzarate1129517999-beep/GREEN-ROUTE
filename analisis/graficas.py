"""Funciones para crear gráficas.

Cada función hace dos cosas con los mismos datos:
  1. guarda un PNG en web/img/graficas (para el informe), y
  2. registra una "especificación" (los datos en JSON) para que la web dibuje la gráfica interactiva.
Al final del script se llama a exportar() para escribir todas las especificaciones en graficas.json.
"""
import json

import matplotlib

matplotlib.use("Agg")  # dibuja sin abrir ventanas
import matplotlib.pyplot as plt
import numpy as np
from matplotlib.colors import LinearSegmentedColormap
from matplotlib.patches import Patch, Rectangle

from config import WEB_DATA, WEB_IMG

PALETA = ["#2a78d6", "#eb6834", "#1baf7a", "#eda100", "#e87ba4", "#008300", "#6250d6", "#e34948"]
SECUENCIAL = ["#cde2fb", "#9ec5f4", "#6da7ec", "#3987e5", "#256abf", "#184f95", "#0d366b"]
TINTA, TINTA_2, TENUE = "#0b0b0b", "#52514e", "#898781"
REJILLA, EJE, SUPERFICIE, RAYADO = "#e1e0d9", "#c3c2b7", "#fcfcfb", "#efeee9"

plt.rcParams.update({
    "font.family": ["Segoe UI", "DejaVu Sans", "sans-serif"],
    "font.size": 10, "axes.titlesize": 12, "axes.titleweight": "bold",
    "axes.edgecolor": EJE, "axes.labelcolor": TINTA_2, "axes.titlecolor": TINTA,
    "axes.grid": True, "grid.color": REJILLA, "grid.linewidth": 0.6,
    "axes.spines.top": False, "axes.spines.right": False,
    "xtick.color": TENUE, "ytick.color": TENUE,
    "figure.facecolor": SUPERFICIE, "axes.facecolor": SUPERFICIE,
    "legend.frameon": False, "savefig.dpi": 200, "savefig.bbox": "tight",
})

GRAFICAS = {}  # id -> especificación de cada gráfica creada en esta ejecución


def numero(v):
    """Convierte números de numpy a números normales de Python (NaN -> None) para el JSON."""
    if isinstance(v, (np.floating, float)):
        return None if np.isnan(v) else round(float(v), 4)
    if isinstance(v, np.integer):
        return int(v)
    return v


def numeros(valores):
    return [numero(v) for v in valores]


def terminar(ax, fig, spec):
    """Pone título, subtítulo y ejes; guarda el PNG y registra la especificación."""
    ax.set_title(spec["titulo"], loc="left", pad=18)
    if spec["subtitulo"]:
        ax.text(0, 1.02, spec["subtitulo"], transform=ax.transAxes, color=TINTA_2, fontsize=9)
    ax.set_xlabel(spec["eje_x"])
    ax.set_ylabel(spec["eje_y"])
    fig.savefig(WEB_IMG / f"{spec['id']}.png")
    plt.close(fig)
    GRAFICAS[spec["id"]] = spec


def barras(id, categorias, series, titulo, subtitulo="", eje_x="", eje_y="", horizontal=False,
           resaltar=None, apilado=False, formato=",.0f"):
    """series = {"nombre de la serie": [valores, uno por categoría]}."""
    spec = {"id": id, "tipo": "barras", "titulo": titulo, "subtitulo": subtitulo, "eje_x": eje_x,
            "eje_y": eje_y, "horizontal": horizontal, "apilado": apilado, "formato": formato,
            "categorias": [str(c) for c in categorias], "resaltar": resaltar or [],
            "series": [{"nombre": k, "valores": numeros(v)} for k, v in series.items()]}

    alto = max(3.5, 0.32 * len(categorias) + 1) if horizontal else 4.2
    fig, ax = plt.subplots(figsize=(8, alto))
    n = len(series)
    pos = np.arange(len(categorias))
    ancho = 0.8 if apilado else 0.8 / n
    acumulado = np.zeros(len(categorias))
    for i, (nombre, vals) in enumerate(series.items()):
        vals = np.asarray(vals, dtype=float)
        colores = [PALETA[i]] * len(vals)
        if resaltar and n == 1:
            colores = [PALETA[1] if str(c) in resaltar else PALETA[0] for c in categorias]
        desplazamiento = 0 if apilado else (i - (n - 1) / 2) * ancho
        base = acumulado if apilado else None
        if horizontal:
            ax.barh(pos + desplazamiento, vals, height=ancho * 0.92, left=base, color=colores, label=nombre)
        else:
            ax.bar(pos + desplazamiento, vals, width=ancho * 0.92, bottom=base, color=colores, label=nombre)
        acumulado = acumulado + vals

    if horizontal:
        ax.set_yticks(pos, categorias)
        ax.invert_yaxis()
        ax.grid(axis="y", visible=False)
    else:
        muchas = len(categorias) >= 13
        ax.set_xticks(pos, categorias, rotation=45 if muchas else 0, ha="right" if muchas else "center")
        ax.grid(axis="x", visible=False)
    if n > 1:
        ax.legend(loc="upper right", ncols=min(n, 4))
    terminar(ax, fig, spec)


def lineas(id, x, series, titulo, subtitulo="", eje_x="", eje_y="", banda=None, marcadores=False,
           referencias=None, formato=",.1f", estilos=None, vacios=None):
    """Gráfica de líneas.

    banda: (inferiores, superiores) para sombrear un intervalo.
    referencias: líneas horizontales [{"valor", "etiqueta"}].
    estilos: {"serie": "punteada"}.  vacios: franjas rayadas [{"desde", "hasta", "etiqueta"}].
    """
    estilos, referencias, vacios = estilos or {}, referencias or [], vacios or []
    x_texto = isinstance(x[0], str)
    spec = {"id": id, "tipo": "lineas", "titulo": titulo, "subtitulo": subtitulo, "eje_x": eje_x,
            "eje_y": eje_y, "x": list(x) if x_texto else numeros(x), "formato": formato,
            "series": [{"nombre": k, "valores": numeros(v), "estilo": estilos.get(k, "solida")}
                       for k, v in series.items()],
            "referencias": referencias, "marcadores": marcadores, "vacios": vacios}
    if banda is not None:
        spec["banda"] = {"inferior": numeros(banda[0]), "superior": numeros(banda[1])}

    fig, ax = plt.subplots(figsize=(8, 4.2))
    xs = np.arange(len(x)) if x_texto else x
    if banda is not None:
        ax.fill_between(xs, banda[0], banda[1], color=PALETA[0], alpha=0.15, linewidth=0)
    for i, (nombre, vals) in enumerate(series.items()):
        ax.plot(xs, vals, color=PALETA[i], lw=2, label=nombre, ms=4,
                ls="--" if estilos.get(nombre) == "punteada" else "-", marker="o" if marcadores else None)
    for v in vacios:
        i0, i1 = list(x).index(v["desde"]), list(x).index(v["hasta"])
        ax.axvspan(i0 - 0.5, i1 + 0.5, facecolor=RAYADO, edgecolor=EJE, hatch="////", lw=0, zorder=0)
        ax.text((i0 + i1) / 2, 0.5, v["etiqueta"], transform=ax.get_xaxis_transform(), ha="center", va="center",
                fontsize=8.5, color=TINTA_2, linespacing=1.4,
                bbox={"boxstyle": "round,pad=0.4", "fc": SUPERFICIE, "ec": EJE, "lw": 0.6})
    for r in referencias:
        ax.axhline(r["valor"], color=TINTA_2, lw=1, ls=":")
        ax.text(xs[-1], r["valor"], " " + r["etiqueta"], color=TINTA_2, fontsize=8, va="bottom", ha="right")
    if x_texto:
        paso = max(1, len(x) // 12)  # máximo ~12 etiquetas en el eje x
        ax.set_xticks(xs[::paso], list(x)[::paso], rotation=45, ha="right")
    if len(series) > 1:
        ax.legend(loc="upper center", bbox_to_anchor=(0.5, -0.2 if x_texto else -0.14), ncols=min(len(series), 4))
    terminar(ax, fig, spec)


def mapa_calor(id, x, y, z, titulo, subtitulo="", eje_x="", eje_y="", formato=",.0f", anotar=False,
               paso_x=1, etiqueta_barra="", cero_vacio=False):
    """Matriz de colores z[fila][columna]. cero_vacio=True muestra los ceros como celdas rayadas (sin datos)."""
    z = np.asarray(z, dtype=float)
    spec = {"id": id, "tipo": "calor", "titulo": titulo, "subtitulo": subtitulo, "eje_x": eje_x,
            "eje_y": eje_y, "x": [str(v) for v in x], "y": [str(v) for v in y],
            "z": [numeros(fila) for fila in z], "formato": formato, "anotar": anotar,
            "paso_x": paso_x, "etiqueta_barra": etiqueta_barra, "cero_vacio": cero_vacio}

    fig, ax = plt.subplots(figsize=(8, max(3.2, 0.42 * len(y) + 1.2)))
    colores = LinearSegmentedColormap.from_list("gr", SECUENCIAL)
    if cero_vacio:
        colores.set_bad((0, 0, 0, 0))  # transparente: deja ver el rayado de fondo
        ax.add_patch(Rectangle((-0.5, -0.5), z.shape[1], z.shape[0], facecolor=RAYADO, edgecolor="#9b9a94",
                               hatch="////", lw=0, zorder=0))
        imagen = ax.imshow(np.ma.masked_equal(z, 0), aspect="auto", cmap=colores, zorder=1, vmin=0)
        ax.legend(handles=[Patch(facecolor=RAYADO, edgecolor="#9b9a94", hatch="////", label="Sin datos publicados")],
                  loc="upper center", bbox_to_anchor=(0.5, -0.32), frameon=False)
    else:
        imagen = ax.imshow(z, aspect="auto", cmap=colores)
    ax.set_xticks(range(0, len(x), paso_x), [x[i] for i in range(0, len(x), paso_x)],
                  rotation=0 if paso_x == 1 else 45, ha="center" if paso_x == 1 else "right")
    ax.set_yticks(range(len(y)), y)
    ax.grid(False)
    if anotar:  # escribe el valor dentro de cada celda
        oscuro = np.nanmax(z) * 0.55
        for i in range(z.shape[0]):
            for j in range(z.shape[1]):
                ax.text(j, i, format(z[i, j], formato), ha="center", va="center", fontsize=9,
                        color="white" if z[i, j] > oscuro else TINTA)
    barra = fig.colorbar(imagen, ax=ax, shrink=0.8)
    if etiqueta_barra:
        barra.set_label(etiqueta_barra, color=TINTA_2)
    terminar(ax, fig, spec)


def dispersion(id, x, y, titulo, subtitulo="", eje_x="", eje_y="", diagonal=False, formato=",.1f"):
    """Nube de puntos. diagonal=True dibuja la línea y = x (útil para real vs. predicho)."""
    spec = {"id": id, "tipo": "dispersion", "titulo": titulo, "subtitulo": subtitulo, "eje_x": eje_x,
            "eje_y": eje_y, "x": numeros(x), "y": numeros(y), "diagonal": diagonal, "formato": formato}
    fig, ax = plt.subplots(figsize=(5.5, 5))
    ax.scatter(x, y, s=8, alpha=0.35, color=PALETA[0], linewidths=0)
    if diagonal:
        limites = [min(np.min(x), np.min(y)), max(np.max(x), np.max(y))]
        ax.plot(limites, limites, color=TINTA_2, lw=1, ls="--")
    terminar(ax, fig, spec)


def exportar():
    """Agrega las gráficas de esta ejecución a graficas.json (conserva las de otros scripts)."""
    destino = WEB_DATA / "graficas.json"
    todas = json.loads(destino.read_text(encoding="utf-8")) if destino.exists() else {}
    todas.update(GRAFICAS)
    destino.write_text(json.dumps(todas, ensure_ascii=False), encoding="utf-8")
    print(f"✓ {len(GRAFICAS)} gráficas exportadas (PNG + JSON)")
