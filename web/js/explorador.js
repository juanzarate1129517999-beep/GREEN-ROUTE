// Explorador por año (estudio.html): mapa de calor día × hora y clases de accidente con Plotly.
(function () {
  "use strict";
  const $ = (s) => document.querySelector(s);
  const { fmt, json, contar, temaActual } = window.GR;
  let panel, clave = "todos";

  function colores() {
    const cs = getComputedStyle(document.documentElement);
    const v = (n) => cs.getPropertyValue(n).trim();
    return { tinta: v("--ink"), tinta2: v("--ink-2"), tenue: v("--muted"), borde: v("--line"), fondo: v("--bg"),
             oscuro: temaActual() === "dark" };
  }

  function base(c) {
    return { paper_bgcolor: "rgba(0,0,0,0)", plot_bgcolor: "rgba(0,0,0,0)", separators: ",.",
      font: { family: "Geist, Inter, sans-serif", size: 11, color: c.tinta2 }, margin: { l: 40, r: 10, t: 6, b: 34 },
      hoverlabel: { bgcolor: c.fondo, bordercolor: c.borde, font: { color: c.tinta } } };
  }

  function dibujar() {
    const d = panel.por_anio[clave];
    const c = colores();
    contar($("#exp-acc"), d.accidentes);
    contar($("#exp-vic"), d.pct_victimas, 1);
    contar($("#exp-carga"), d.pct_carga, 1);
    contar($("#exp-moto"), d.pct_moto, 1);
    $("#exp-pico").textContent = d.hora_pico + ":00";
    $("#exp-titulo").textContent = `${clave === "todos" ? "Todos los años" : clave} · ${fmt(d.accidentes)} accidentes`;
    const escala = c.oscuro ? [[0, "#121212"], [0.5, "#7a7a7a"], [1, "#fafafa"]] : [[0, "#f4f4f4"], [0.5, "#8c8c8c"], [1, "#0a0a0a"]];
    const lay = base(c);
    Plotly.react("exp-calor", [{ type: "heatmap", z: d.matriz, x: [...Array(24).keys()].map(String), y: panel.dias, colorscale: escala,
      xgap: 2, ygap: 2, showscale: false, hovertemplate: "%{y} · %{x}:00 h<br><b>%{z} accidentes</b><extra></extra>" }],
      { ...lay, xaxis: { tickfont: { color: c.tenue }, dtick: 3, title: { text: "Hora" } }, yaxis: { autorange: "reversed", tickfont: { color: c.tenue } } },
      { displayModeBar: false, responsive: true });
    const n = d.clases.nombres.slice(0, 6), v = d.clases.valores.slice(0, 6);
    Plotly.react("exp-clases", [{ type: "bar", orientation: "h", y: n, x: v, marker: { color: c.tinta, cornerradius: 3 },
      hovertemplate: "<b>%{y}</b><br>%{x:,.0f} accidentes<extra></extra>" }],
      { ...lay, margin: { l: 110, r: 10, t: 6, b: 30 }, yaxis: { autorange: "reversed", tickfont: { color: c.tinta2 } },
        xaxis: { gridcolor: c.borde, tickfont: { color: c.tenue }, type: v[0] / (v[v.length - 1] || 1) > 50 ? "log" : "linear" }, bargap: .35 },
      { displayModeBar: false, responsive: true });
  }

  (async () => {
    if (!$("#explorador")) return;
    panel = await json("data/panel.json");
    const cont = $("#exp-anios");
    cont.innerHTML = ["todos", ...panel.anios.map(String)].map((k) => `<button aria-pressed="${k === "todos"}" data-k="${k}">${k === "todos" ? "Todos" : k}</button>`).join("");
    cont.addEventListener("click", (e) => {
      const b = e.target.closest("button"); if (!b) return;
      cont.querySelectorAll("button").forEach((x) => x.setAttribute("aria-pressed", x === b));
      clave = b.dataset.k; dibujar();
    });
    const obs = new IntersectionObserver((e) => { if (e[0].isIntersecting) { dibujar(); obs.disconnect(); } }, { rootMargin: "200px" });
    obs.observe($("#explorador"));
    document.addEventListener("gr:tema", () => { if ($("#exp-calor").data) dibujar(); });
  })();
})();
