// Página principal (index.html): red animada de la portada, panel por año y simulador de flota en vivo.
(function () {
  "use strict";
  const { fmt, json, contar } = window.GR;
  const $ = (s) => document.querySelector(s);
  const NS = "http://www.w3.org/2000/svg";

  const palabras = ["menos CO₂.", "menos tiempo.", "menos riesgo.", "más datos."];
  let ip = 0;
  setInterval(() => {
    const el = $("#rotativo");
    el.classList.add("saliendo");
    setTimeout(() => { ip = (ip + 1) % palabras.length; el.textContent = palabras[ip]; el.classList.remove("saliendo"); }, 450);
  }, 2800);

  let proyeccion = null, svgRed = null;
  async function dibujarRed() {
    try {
      const r = await json("data/red_svg.json");
      proyeccion = r;
      const svg = document.createElementNS(NS, "svg");
      svg.setAttribute("viewBox", `0 0 ${r.ancho} ${r.alto}`);
      ["rio", "terciaria", "secundaria", "primaria", "troncal"].forEach((k) => {
        if (!r.trazos[k]) return;
        const p = document.createElementNS(NS, "path");
        p.setAttribute("d", r.trazos[k]);
        p.setAttribute("class", k + (k === "rio" ? "" : " trazar"));
        p.setAttribute("pathLength", "1");
        svg.append(p);
      });
      $("#heroe-red").append(svg);
      svgRed = svg;
    } catch (e) { console.warn("Red SVG no disponible", e); }
  }
  const proyectar = ([la, lo]) => [(lo - proyeccion.lon_min) * proyeccion.kx * proyeccion.escala, (proyeccion.lat_max - la) * proyeccion.escala];

  async function rutaEnVivo(puntos) {
    const caja = $("#heroe-ruta"), txt = $("#heroe-ruta-txt");
    const o = puntos.find((p) => p.categoria === "Puerto"), d = puntos.find((p) => p.categoria === "Aeropuerto");
    if (!o || !d) return;
    try {
      const r = await json(`api/ruta?olat=${o.lat}&olon=${o.lon}&dlat=${d.lat}&dlon=${d.lon}&vehiculo=VAN-E&criterio=EMISIONES&hora=7&carga=900`);
      txt.innerHTML = `Ruta óptima ${window.GR.modoEstatico() ? "precalculada" : "en vivo"} · Puerto → Aeropuerto · <b>${fmt(r.distanciaKm, 1)} km</b> · <b>${fmt(r.emisionKgCO2, 2)} kg CO₂</b> · ${fmt(r.nodosExplorados)} nodos en ${fmt(r.calculoMs, 0)} ms`;
      if (!svgRed || !proyeccion) return;
      const pts = r.geometria.map(proyectar);
      const ruta = document.createElementNS(NS, "path");
      ruta.setAttribute("d", "M" + pts.map((p) => p.map((v) => v.toFixed(1)).join(",")).join("L"));
      ruta.setAttribute("class", "ruta"); ruta.setAttribute("pathLength", "1"); ruta.id = "ruta-heroe";
      svgRed.append(ruta);
      [[pts[0], "PUERTO"], [pts[pts.length - 1], "AEROPUERTO"]].forEach(([p, t]) => {
        const c = document.createElementNS(NS, "circle");
        c.setAttribute("cx", p[0]); c.setAttribute("cy", p[1]); c.setAttribute("r", 7); c.setAttribute("class", "marca-od");
        const tx = document.createElementNS(NS, "text");
        tx.setAttribute("x", p[0] + 14); tx.setAttribute("y", p[1] + 5); tx.textContent = t;
        svgRed.append(c, tx);
      });
      const punto = document.createElementNS(NS, "circle");
      punto.setAttribute("r", 7); punto.setAttribute("class", "vehiculo-punto");
      punto.innerHTML = `<animateMotion dur="7s" begin="2.8s" repeatCount="indefinite" rotate="auto"><mpath href="#ruta-heroe"/></animateMotion>`;
      svgRed.append(punto);
    } catch (e) {
      caja.classList.add("caido");
      txt.textContent = "Motor Java fuera de línea · ejecuta greenroute-java\\ejecutar.ps1 para ver la ruta en vivo";
    }
  }

  function cinta(res, mod) {
    const items = [
      [fmt(res.accidentalidad.accidentes), "accidentes analizados"], [fmt(res.aire.mediciones), "mediciones de aire"],
      [fmt(res.red.km_totales) + " km", "de malla vial"], [fmt(res.red.nodos), "intersecciones"],
      [fmt(mod.m2.mejor.auc, 3), "AUC severidad"], [fmt(mod.m1.mejor.R2, 3), "R² tráfico (CO)"],
      [fmt(mod.m3.comparacion[0].MAPE_2025, 1) + " %", "error pronóstico"], [fmt(mod.m4.total_clusters), "puntos críticos"],
      ["52", "clases Java"], ["7", "vehículos"], ["5", "criterios de ruta"], [fmt(res.muestreo.n), "accidentes en la muestra"],
    ];
    const html = items.map(([v, t]) => `<span class="cinta__item"><b>${v}</b>${t}</span>`).join("");
    $("#cinta").innerHTML = html + html;
  }

  let panel;
  function construirFiltro() {
    const cont = $("#filtro-anio");
    const claves = ["todos", ...panel.anios.map(String)];
    cont.innerHTML = claves.map((k) => `<button aria-pressed="${k === "todos"}" data-k="${k}">${k === "todos" ? "Todos" : k}</button>`).join("");
    cont.addEventListener("click", (e) => {
      const b = e.target.closest("button"); if (!b) return;
      cont.querySelectorAll("button").forEach((x) => x.setAttribute("aria-pressed", x === b));
      actualizar(b.dataset.k);
    });
    const calor = $("#calor");
    let html = "";
    panel.dias.forEach((d, i) => {
      html += `<span>${d}</span>`;
      for (let h = 0; h < 24; h++) html += `<i data-d="${i}" data-h="${h}"></i>`;
    });
    calor.innerHTML = html;
    $("#barras-hora").innerHTML = Array.from({ length: 24 }, (_, h) => `<i data-h="${h}"></i>`).join("");
  }

  let anioActual = "todos";
  function actualizar(clave) {
    anioActual = clave;
    const d = panel.por_anio[clave];
    contar($("#b-acc"), d.accidentes);
    contar($("#b-vic"), d.con_victimas);
    contar($("#b-mue"), d.con_muertos);
    contar($("#b-geo"), d.geocodificados);
    contar($("#b-carga"), d.pct_carga, 1);
    contar($("#b-hora"), d.hora_pico);
    $("#b-dia").textContent = { Lun: "lunes", Mar: "martes", "Mié": "miércoles", Jue: "jueves", Vie: "viernes", "Sáb": "sábado", Dom: "domingo" }[d.dia_pico];
    $("#b-moto").textContent = fmt(d.pct_moto, 1);
    $("#anillo-valor").style.strokeDasharray = `${d.pct_carga} 100`;
    const maxH = Math.max(...d.perfil_hora, 1);
    $("#barras-hora").querySelectorAll("i").forEach((el, h) => {
      el.style.height = Math.max(2, d.perfil_hora[h] / maxH * 100) + "%";
      el.classList.toggle("pico", h === d.hora_pico);
      el.dataset.t = `${h}:00 · ${fmt(d.perfil_hora[h])}`;
    });
    const maxC = Math.max(...d.matriz.flat(), 1);
    $("#calor").querySelectorAll("i").forEach((el) => {
      const v = d.matriz[+el.dataset.d][+el.dataset.h];
      el.style.opacity = (0.05 + 0.95 * Math.pow(v / maxC, 0.85)).toFixed(3);
      el.dataset.v = v;
    });
    $("#calor-info").textContent = `${clave === "todos" ? "2018-2026" : clave} · máximo ${fmt(maxC)} accidentes en una celda`;
    const maxS = Math.max(...d.sitios_top.map((s) => s.accidentes), 1);
    $("#sitios").innerHTML = d.sitios_top.map((s) => `<li><span title="${s.sitio}">${s.sitio}</span><b>${fmt(s.accidentes)}</b><i style="transform:scaleX(0)" data-k="${s.accidentes / maxS}"></i></li>`).join("");
    requestAnimationFrame(() => $("#sitios").querySelectorAll("i").forEach((i) => { i.style.transform = `scaleX(${i.dataset.k})`; }));
    if (d.aire && d.aire.pm25 != null) {
      contar($("#b-pm25"), d.aire.pm25, 1);
      const parcial = clave === "2023" ? " Año incompleto: solo enero a mayo." : clave === "2025" ? " Año incompleto: desde marzo." : "";
      $("#b-aire-txt").innerHTML = `PM2.5 promedio${clave === "todos" ? " 2020-2025" : ` en ${clave}`}. El <b>${fmt(d.aire.dias_sobre_oms, 1)} %</b> de los días supera la guía diaria de la OMS (15 µg/m³).${parcial}`;
    } else {
      $("#b-pm25").textContent = "—";
      $("#b-aire-txt").innerHTML = `Sin mediciones de PM2.5 en ${clave}: la fuente oficial no publicó datos entre junio de 2023 y febrero de 2025. <a href="estudio.html#huecos">¿Por qué?</a>`;
    }
  }
  $("#calor").addEventListener("mouseover", (e) => {
    const c = e.target.closest("i[data-v]"); if (!c) return;
    $("#calor-info").innerHTML = `<b style="color:var(--ink)">${panel.dias[c.dataset.d]} · ${c.dataset.h}:00 h</b> → ${fmt(+c.dataset.v)} accidentes${anioActual === "todos" ? "" : ` en ${anioActual}`}`;
  });

  const ENVIOS = [
    ["Puerto", "Aeropuerto"], ["Zona franca", "Referencia:Universidad del Norte"], ["Abastecimiento", "Terminal"],
    ["Industrial", "Residencial"], ["Centro", "Comercial"],
  ];
  let envios = [], envioSel = 0, ctrl = null, tSim;
  function construirSimulador(puntos) {
    const buscar = (k) => k.includes(":") ? puntos.find((p) => p.nombre === k.split(":")[1]) : puntos.find((p) => p.categoria === k);
    envios = ENVIOS.map(([a, b]) => [buscar(a), buscar(b)]).filter(([a, b]) => a && b);
    $("#envios").innerHTML = envios.map(([a, b], i) => `<button class="envio" aria-pressed="${i === 0}" data-i="${i}">${a.nombre} → ${b.nombre}
      <small>${fmt(dist(a, b), 1)} km en línea recta</small></button>`).join("");
    $("#envios").addEventListener("click", (e) => {
      const b = e.target.closest(".envio"); if (!b) return;
      envioSel = +b.dataset.i;
      $("#envios").querySelectorAll(".envio").forEach((x) => x.setAttribute("aria-pressed", x === b));
      simular();
    });
    ["#sim-carga", "#sim-hora"].forEach((s) => $(s).addEventListener("input", () => {
      $("#lbl-carga").textContent = fmt(+$("#sim-carga").value) + " kg";
      $("#lbl-hora").textContent = String($("#sim-hora").value).padStart(2, "0") + ":00";
      clearTimeout(tSim); tSim = setTimeout(simular, 220);
    }));
    simular();
  }
  const dist = (a, b) => {
    const R = 6371, dLa = (b.lat - a.lat) * Math.PI / 180, dLo = (b.lon - a.lon) * Math.PI / 180;
    const h = Math.sin(dLa / 2) ** 2 + Math.cos(a.lat * Math.PI / 180) * Math.cos(b.lat * Math.PI / 180) * Math.sin(dLo / 2) ** 2;
    return 2 * R * Math.asin(Math.sqrt(h));
  };

  async function simular() {
    const [o, d] = envios[envioSel];
    const carga = +$("#sim-carga").value, hora = +$("#sim-hora").value;
    $("#sim-abrir").href = `mapa.html#o=${o.lat},${o.lon}&d=${d.lat},${d.lon}&c=${carga}&h=${hora}&flota=1`;
    if (ctrl) ctrl.abort();
    ctrl = new AbortController();
    $("#ranking").style.opacity = .5;
    try {
      const r = await fetch(`api/comparar?olat=${o.lat}&olon=${o.lon}&dlat=${d.lat}&dlon=${d.lon}&hora=${hora}&carga=${carga}&criterio=EMISIONES`, { signal: ctrl.signal });
      const datos = await r.json();
      if (!r.ok) throw new Error(datos.mensaje);
      pintarSimulacion(datos);
    } catch (e) {
      if (e.name === "AbortError") return;
      $("#ranking").innerHTML = window.GR.modoEstatico()
        ? '<p class="vacio">Demo estática (GitHub Pages): el simulador tiene precalculadas las 24 horas de salida con 900 kg de carga. Para otras cargas ejecuta el motor Java (<code>greenroute-java\\ejecutar.ps1</code>).</p>'
        : '<p class="vacio">El motor Java no está en línea. Ejecuta <code>greenroute-java\\ejecutar.ps1</code> y recarga.</p>';
      $("#podio").innerHTML = "";
    } finally { $("#ranking").style.opacity = 1; }
  }

  function pintarSimulacion(d) {
    const ok = d.opciones.filter((x) => x.factible).sort((a, b) => a.ruta.emisionKgCO2 - b.ruta.emisionKgCO2);
    const no = d.opciones.filter((x) => !x.factible);
    const max = Math.max(...ok.map((x) => x.ruta.emisionKgCO2), 0.001);
    const top = [ok[1], ok[0], ok[2]];
    $("#podio").innerHTML = top.map((x, i) => x ? `<div class="podio__col p${[2, 1, 3][i]}">
        <div class="nombre">${x.nombre}</div><div class="co2">${fmt(x.ruta.emisionKgCO2, 2)} kg CO₂ · ${fmt(x.ruta.tiempoMin)} min</div>
        <div class="podio__bloque">${[2, 1, 3][i]}</div></div>` : "<div></div>").join("");
    $("#ranking").innerHTML = ok.map((x, i) => `<div class="fila-rank ${i === 0 ? "mejor" : ""}"><span>${x.nombre}</span>
        <div class="barra"><i data-w="${x.ruta.emisionKgCO2 / max * 100}"></i></div><span class="valor">${fmt(x.ruta.emisionKgCO2, 2)} kg</span></div>`).join("") +
      no.map((x) => `<div class="fila-rank no"><span>${x.nombre}</span><div><div class="barra"></div><div class="motivo">${x.motivo}</div></div><span class="valor">—</span></div>`).join("");
    requestAnimationFrame(() => $("#ranking").querySelectorAll("i[data-w]").forEach((i) => { i.style.width = i.dataset.w + "%"; }));
  }

  (async () => {
    dibujarRed();
    try {
      const [p, res, mod] = await Promise.all([json("data/panel.json"), json("data/resumen_estudio.json"), json("data/modelos.json")]);
      panel = p;
      cinta(res, mod);
      construirFiltro();
      const obs = new IntersectionObserver((e) => { if (e[0].isIntersecting) { actualizar("todos"); obs.disconnect(); } }, { rootMargin: "0px 0px -20% 0px" });
      obs.observe($("#panel"));
    } catch (e) { console.warn(e); }
    try {
      const puntos = await json("api/puntos");
      await new Promise((r) => setTimeout(r, 500));
      rutaEnVivo(puntos);
      construirSimulador(puntos);
    } catch (e) {
      $("#heroe-ruta").classList.add("caido");
      $("#heroe-ruta-txt").textContent = "Motor Java fuera de línea · ejecuta greenroute-java\\ejecutar.ps1";
      $("#ranking").innerHTML = '<p class="vacio">El motor Java no está en línea. Ejecuta <code>greenroute-java\\ejecutar.ps1</code> y recarga.</p>';
    }
  })();
})();
