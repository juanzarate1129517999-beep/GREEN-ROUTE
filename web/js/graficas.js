// Dibuja con Plotly las gráficas interactivas a partir de data/graficas.json (las crea analisis/graficas.py).
(function () {
  "use strict";

  const PALETA = {
    light: ["#2a78d6", "#eb6834", "#1baf7a", "#eda100", "#e87ba4", "#008300", "#6250d6", "#e34948"],
    dark: ["#1e9ad6", "#e8662f", "#12ad7a", "#c48200", "#8f78f0", "#e64566", "#008300", "#e66767"],
  };
  const SECUENCIAL = {
    light: [[0, "#f4f4f4"], [0.25, "#cfcfcf"], [0.5, "#8c8c8c"], [0.75, "#3d3d3d"], [1, "#0a0a0a"]],
    dark: [[0, "#121212"], [0.25, "#3a3a3a"], [0.5, "#7a7a7a"], [0.75, "#c4c4c4"], [1, "#fafafa"]],
  };

  let especificaciones = null;

  function tokens() {
    const css = getComputedStyle(document.documentElement);
    const v = (n) => css.getPropertyValue(n).trim();
    return { tinta: v("--ink"), tinta2: v("--ink-2"), tenue: v("--muted"), borde: v("--border"),
             superficie: v("--surface"), rejilla: v("--border") };
  }

  function d3fmt(f) { return f && f.includes("%") ? ".1%" : (f || ",.1f"); }

  function layoutBase(spec, t) {
    return {
      paper_bgcolor: "rgba(0,0,0,0)", plot_bgcolor: "rgba(0,0,0,0)",
      font: { family: "Geist, Inter, system-ui, Segoe UI, sans-serif", size: 12, color: t.tinta2 },
      margin: { l: 56, r: 18, t: 12, b: 48 }, separators: ",.",
      hoverlabel: { bgcolor: t.superficie, bordercolor: t.borde, font: { color: t.tinta, size: 12 } },
      xaxis: { title: { text: spec.eje_x || "", font: { size: 12 } }, gridcolor: t.rejilla, zeroline: false,
               linecolor: t.borde, tickcolor: t.borde, tickfont: { color: t.tenue }, automargin: true },
      yaxis: { title: { text: spec.eje_y || "", font: { size: 12 } }, gridcolor: t.rejilla, zeroline: false,
               linecolor: t.borde, tickfont: { color: t.tenue }, automargin: true },
      legend: { orientation: "h", x: 0, y: -0.2, xanchor: "left", font: { size: 12, color: t.tinta2 } },
      hovermode: "closest", bargap: 0.28, bargroupgap: 0.06,
    };
  }

  function barras(spec, t, pal) {
    const horiz = spec.horizontal;
    const cats = spec.categorias;
    const trazos = spec.series.map((s, i) => {
      let color = pal[i];
      if (spec.resaltar.length && spec.series.length === 1) {
        color = cats.map((c) => (spec.resaltar.includes(c) ? pal[1] : pal[0]));
      }
      return {
        type: "bar", name: s.nombre, orientation: horiz ? "h" : "v",
        x: horiz ? s.valores : cats, y: horiz ? cats : s.valores,
        marker: { color, line: { width: 0 }, cornerradius: 3 },
        hovertemplate: horiz ? `<b>%{y}</b><br>${s.nombre}: %{x:${d3fmt(spec.formato)}}<extra></extra>`
                             : `<b>%{x}</b><br>${s.nombre}: %{y:${d3fmt(spec.formato)}}<extra></extra>`,
      };
    });
    const lay = layoutBase(spec, t);
    lay.barmode = spec.apilado ? "stack" : "group";
    lay.hovermode = "closest";
    if (horiz) {
      lay.yaxis.autorange = "reversed"; lay.yaxis.gridcolor = "rgba(0,0,0,0)";
      lay.margin.l = 10;
    } else { lay.xaxis.gridcolor = "rgba(0,0,0,0)"; }
    lay.showlegend = spec.series.length > 1;
    return [trazos, lay];
  }

  function lineas(spec, t, pal) {
    const trazos = [];
    if (spec.banda) {
      trazos.push({ x: spec.x, y: spec.banda.superior, type: "scatter", mode: "lines", line: { width: 0 },
                    hoverinfo: "skip", showlegend: false });
      trazos.push({ x: spec.x, y: spec.banda.inferior, type: "scatter", mode: "lines", line: { width: 0 },
                    fill: "tonexty", fillcolor: pal[2] + "33", name: "Intervalo 95 %", hoverinfo: "skip" });
    }
    spec.series.forEach((s, i) => {
      trazos.push({
        x: spec.x, y: s.valores, type: "scatter", mode: spec.marcadores ? "lines+markers" : "lines", name: s.nombre,
        line: { color: pal[i], width: 2, dash: s.estilo === "punteada" ? "dot" : "solid" },
        marker: { size: 7, line: { color: t.superficie, width: 2 } }, connectgaps: false,
        hovertemplate: `${s.nombre}: <b>%{y:${d3fmt(spec.formato)}}</b><extra></extra>`,
      });
    });
    const lay = layoutBase(spec, t);
    lay.hovermode = "x unified";
    lay.showlegend = spec.series.length > 1 || !!spec.banda;
    lay.shapes = (spec.referencias || []).map((r) => ({ type: "line", xref: "paper", x0: 0, x1: 1, y0: r.valor, y1: r.valor,
      line: { color: t.tinta2, width: 1, dash: "dot" } }));
    lay.annotations = (spec.referencias || []).map((r) => ({ xref: "paper", x: 1, y: r.valor, text: r.etiqueta,
      showarrow: false, xanchor: "right", yanchor: "bottom", font: { size: 11, color: t.tinta2 } }));
    (spec.vacios || []).forEach((v) => {
      const i0 = spec.x.indexOf(v.desde), i1 = spec.x.indexOf(v.hasta);
      if (i0 < 0 || i1 < 0) return;
      lay.shapes.push({ type: "rect", xref: "x", yref: "paper", x0: i0 - 0.5, x1: i1 + 0.5, y0: 0, y1: 1, layer: "below",
        line: { width: 0 }, fillcolor: t.tinta2, opacity: 0.1 });
      lay.annotations.push({ xref: "x", yref: "paper", x: (i0 + i1) / 2, y: 0.5, showarrow: false,
        text: v.etiqueta.split(String.fromCharCode(10)).join("<br>"), align: "center", bgcolor: t.superficie, bordercolor: t.borde,
        borderpad: 6, font: { size: 12, color: t.tinta } });
    });
    if (spec.vacios && spec.vacios.length) lay.xaxis.type = "category";
    if (typeof spec.x[0] === "string" && spec.x.length > 30) lay.xaxis.nticks = 12;
    return [trazos, lay];
  }

  function calor(spec, t, pal, tema) {
    const tr = {
      type: "heatmap", x: spec.x, y: spec.y, z: spec.z, colorscale: SECUENCIAL[tema], xgap: spec.paso_x > 1 ? 0 : 2, ygap: 2,
      colorbar: { thickness: 10, outlinewidth: 0, tickfont: { color: t.tenue } },
      hovertemplate: `${spec.eje_x || "x"}: %{x}<br>%{y}<br><b>%{z:${d3fmt(spec.formato)}}</b><extra></extra>`,
    };
    const lay = layoutBase(spec, t);
    if (spec.anotar) {
      tr.showscale = false;
      const plano = spec.z.flat(), min = Math.min(...plano), max = Math.max(...plano);
      lay.annotations = [];
      spec.z.forEach((fila, i) => fila.forEach((v, j) => {
        const k = (v - min) / ((max - min) || 1);
        const claro = tema === "dark" ? k > 0.55 : k < 0.45;
        lay.annotations.push({ x: spec.x[j], y: spec.y[i], text: Number(v).toLocaleString("es-CO"), showarrow: false,
          font: { size: 18, color: claro ? "#0a0a0a" : "#fafafa", family: "Geist Mono, monospace" } });
      }));
    }
    lay.yaxis.autorange = "reversed"; lay.xaxis.gridcolor = lay.yaxis.gridcolor = "rgba(0,0,0,0)";
    lay.xaxis.type = "category";
    if (spec.etiqueta_barra) tr.colorbar.title = { text: spec.etiqueta_barra, side: "right", font: { color: t.tenue, size: 11 } };
    if (spec.paso_x > 1) lay.xaxis.nticks = Math.ceil(spec.x.length / spec.paso_x);
    const trazos = [tr];
    if (spec.cero_vacio) {
      tr.z = spec.z.map((f) => f.map((v) => (v === 0 ? null : v)));
      tr.zmin = 0; tr.zmax = 100;
      trazos.push({ type: "heatmap", x: spec.x, y: spec.y, z: spec.z.map((f) => f.map((v) => (v === 0 ? 1 : null))),
        colorscale: [[0, pal[1]], [1, pal[1]]], showscale: false, xgap: 0, ygap: 2, opacity: 0.6,
        name: "Sin datos publicados", hovertemplate: "%{x} · %{y}<br><b>Sin datos publicados</b><extra></extra>" });
      lay.annotations = (lay.annotations || []).concat([{ xref: "paper", yref: "paper", x: 0, y: -0.24, xanchor: "left",
        showarrow: false, text: `<span style="color:${pal[1]}">■</span> Sin datos publicados en la fuente`,
        font: { size: 12, color: t.tinta2 } }]);
      lay.margin.b = 80;
    }
    return [trazos, lay];
  }

  function dispersion(spec, t, pal) {
    const trazos = [{ type: "scattergl", mode: "markers", x: spec.x, y: spec.y, name: "Observaciones",
      marker: { color: pal[0], size: 6, opacity: 0.45 },
      hovertemplate: `Observado %{x:${d3fmt(spec.formato)}}<br>Predicho %{y:${d3fmt(spec.formato)}}<extra></extra>` }];
    if (spec.diagonal) {
      const lim = [Math.min(...spec.x, ...spec.y), Math.max(...spec.x, ...spec.y)];
      trazos.push({ type: "scatter", mode: "lines", x: lim, y: lim, name: "Predicción perfecta",
                    line: { color: t.tinta2, width: 1.5, dash: "dot" }, hoverinfo: "skip" });
    }
    const lay = layoutBase(spec, t);
    lay.showlegend = true;
    return [trazos, lay];
  }

  const TIPOS = { barras, lineas, calor, dispersion };

  function dibujar(div) {
    const spec = especificaciones && especificaciones[div.dataset.id];
    if (!spec) { div.innerHTML = `<p style="color:var(--muted)">Gráfica «${div.dataset.id}» no disponible.</p>`; return; }
    const tema = window.GR.temaActual();
    const t = tokens();
    const [trazos, lay] = TIPOS[spec.tipo](spec, t, PALETA[tema], tema);
    if (lay.showlegend) {
      const conEje = !!spec.eje_x;
      lay.margin.b = conEje ? 116 : 92;
      lay.legend.y = conEje ? -0.24 : -0.14;
      lay.legend.yanchor = "top";
    }
    Plotly.react(div, trazos, lay, { responsive: true, displaylogo: false,
      modeBarButtonsToRemove: ["select2d", "lasso2d", "autoScale2d", "toggleSpikelines"],
      toImageButtonOptions: { filename: "greenroute_" + spec.id, scale: 2 }, locale: "es" });
    const tarjeta = div.closest(".grafica-tarjeta");
    if (tarjeta && !tarjeta.dataset.listo) {
      tarjeta.dataset.listo = "1";
      const cab = document.createElement("div");
      cab.className = "grafica-tarjeta__cab";
      cab.innerHTML = `<h3>${spec.titulo}</h3>${spec.subtitulo ? `<p>${spec.subtitulo}</p>` : ""}`;
      tarjeta.prepend(cab);
      const pie = document.createElement("div");
      pie.className = "grafica-tarjeta__pie";
      pie.innerHTML = `<span>Figura · ${spec.id}</span><a href="img/graficas/${spec.id}.png" download>Descargar PNG (informe)</a>`;
      tarjeta.append(pie);
    }
  }

  async function iniciar() {
    const divs = [...document.querySelectorAll(".grafica[data-id]")];
    if (!divs.length) return;
    especificaciones = await window.GR.json("data/graficas.json");
    const obs = new IntersectionObserver((entradas) => {
      entradas.forEach((e) => { if (e.isIntersecting) { dibujar(e.target); obs.unobserve(e.target); e.target.dataset.dibujada = "1"; } });
    }, { rootMargin: "200px" });
    divs.forEach((d) => obs.observe(d));
    document.addEventListener("gr:tema", () => divs.filter((d) => d.dataset.dibujada).forEach(dibujar));
  }

  document.addEventListener("DOMContentLoaded", iniciar);
})();
