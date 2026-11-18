// Vista 3D del mapa de rutas (mapa.html), construida con MapLibre GL y teselas vectoriales de OpenFreeMap (datos OSM).
// Edificios en relieve, rutas con trazo animado, "muros" cuya altura es el CO₂ de cada ruta, columnas de accidentalidad,
// puntos críticos, órbita y un vuelo de cámara que recorre la ruta activa. Se carga solo cuando el usuario pasa a 3D.
(function () {
  "use strict";
  const { fmt, json, aviso } = window.GR;
  const $ = (s) => document.querySelector(s);
  const G = () => window.GRMapa;
  if (!G()) return;

  const ML = "https://unpkg.com/maplibre-gl@4.7.1/dist/";
  const ESTILOS = { dark: "https://tiles.openfreemap.org/styles/dark", light: "https://tiles.openfreemap.org/styles/positron" };
  const reducir = matchMedia("(prefers-reduced-motion: reduce)").matches;
  const raiz = document.body;
  const dock = $("#dock3d");

  let m3 = null, activo = false, promesaML = null;
  const opc = { edificios: true, muros: true, accidentes: false };
  const marcas = {};
  let vuelo = null, orbita = null, animMuros = null, animTrazo = null, kMuros = 1, kAcc = 0, animAcc = null;
  let datosAcc = null, datosPC = null, popup = null;

  // ---------- utilidades geográficas ----------
  const RAD = Math.PI / 180;
  function distM(a, b) {
    const dLa = (b[1] - a[1]) * RAD, dLo = (b[0] - a[0]) * RAD;
    const h = Math.sin(dLa / 2) ** 2 + Math.cos(a[1] * RAD) * Math.cos(b[1] * RAD) * Math.sin(dLo / 2) ** 2;
    return 2 * 6371000 * Math.asin(Math.sqrt(h));
  }
  function rumbo(a, b) {
    const y = Math.sin((b[0] - a[0]) * RAD) * Math.cos(b[1] * RAD);
    const x = Math.cos(a[1] * RAD) * Math.sin(b[1] * RAD) - Math.sin(a[1] * RAD) * Math.cos(b[1] * RAD) * Math.cos((b[0] - a[0]) * RAD);
    return Math.atan2(y, x) / RAD;
  }
  const difAngulo = (a, b) => ((b - a + 540) % 360) - 180;
  const lngLat = ([la, lo]) => [lo, la];
  function rgba(hex, a) {
    const h = hex.replace("#", ""), n = parseInt(h.length === 3 ? h.split("").map((c) => c + c).join("") : h, 16);
    return `rgba(${(n >> 16) & 255},${(n >> 8) & 255},${n & 255},${a})`;
  }
  const css = (v) => getComputedStyle(document.documentElement).getPropertyValue(v).trim();
  const oscuro = () => window.GR.temaActual() === "dark";

  // Polígono rectangular alrededor de un segmento (para extruir muros).
  function cuadSegmento(a, b, mitad) {
    const lat = (a[1] + b[1]) / 2, mx = 1 / (111320 * Math.cos(lat * RAD)), my = 1 / 110540;
    const dx = (b[0] - a[0]) / mx, dy = (b[1] - a[1]) / my, L = Math.hypot(dx, dy) || 1;
    const ox = (-dy / L) * mitad * mx, oy = (dx / L) * mitad * my;
    return [[a[0] + ox, a[1] + oy], [b[0] + ox, b[1] + oy], [b[0] - ox, b[1] - oy], [a[0] - ox, a[1] - oy], [a[0] + ox, a[1] + oy]];
  }
  function circulo(c, radioM, lados = 24) {
    const mx = 1 / (111320 * Math.cos(c[1] * RAD)), my = 1 / 110540, anillo = [];
    for (let i = 0; i <= lados; i++) { const t = (i / lados) * 2 * Math.PI; anillo.push([c[0] + Math.cos(t) * radioM * mx, c[1] + Math.sin(t) * radioM * my]); }
    return [anillo];
  }

  // ---------- carga diferida de MapLibre ----------
  function cargarML() {
    if (window.maplibregl) return Promise.resolve();
    if (promesaML) return promesaML;
    promesaML = new Promise((ok, mal) => {
      const l = document.createElement("link"); l.rel = "stylesheet"; l.href = ML + "maplibre-gl.css"; document.head.append(l);
      const s = document.createElement("script"); s.src = ML + "maplibre-gl.js"; s.onload = ok; s.onerror = () => { promesaML = null; mal(new Error("No se pudo cargar MapLibre")); };
      document.head.append(s);
    });
    return promesaML;
  }

  function crearMapa() {
    const c = G().mapa.getCenter();
    m3 = new maplibregl.Map({
      container: "mapa3d", style: ESTILOS[window.GR.temaActual()], center: [c.lng, c.lat], zoom: G().mapa.getZoom() - 1,
      pitch: 0, bearing: 0, maxPitch: 78, attributionControl: false, antialias: true,
      maxBounds: [[-75.4, 10.45], [-74.25, 11.45]],
    });
    m3.addControl(new maplibregl.NavigationControl({ visualizePitch: true }), "bottom-right");
    m3.addControl(new maplibregl.AttributionControl({ compact: true, customAttribution: "OpenFreeMap · © OpenStreetMap" }), "bottom-right");
    m3.on("style.load", agregarCapas);
    m3.on("click", clic);
    m3.on("mousemove", (e) => {
      if (!m3.getLayer("gr-pc")) return;
      m3.getCanvas().style.cursor = m3.queryRenderedFeatures(e.point, { layers: ["gr-pc"] }).length ? "pointer" : "";
    });
    ["mousedown", "touchstart", "wheel"].forEach((ev) => m3.on(ev, () => { detenerVuelo(); detenerOrbita(); }));
    ["origen", "destino"].forEach((k) => {
      const el = document.createElement("div");
      el.innerHTML = `<div class="marcador marcador--${k === "origen" ? "a" : "b"}"><span class="marcador-pulso"></span>${k === "origen" ? "A" : "B"}</div>`;
      marcas[k] = new maplibregl.Marker({ element: el, draggable: true });
      marcas[k].on("dragend", () => { const p = marcas[k].getLngLat(); G().fijarPunto(k, L.latLng(p.lat, p.lng), null, true); });
    });
    const ev = document.createElement("div"); ev.className = "vehiculo3d";
    marcas.vehiculo = new maplibregl.Marker({ element: ev, pitchAlignment: "map" });
    return new Promise((ok) => m3.once("load", ok));
  }

  // ---------- capas ----------
  function primerSimbolo() {
    const capa = m3.getStyle().layers.find((l) => l.type === "symbol");
    return capa && capa.id;
  }
  function agregarCapas() {
    const antes = primerSimbolo();
    const vacio = { type: "FeatureCollection", features: [] };
    try { if (m3.setSky) m3.setSky({ "sky-color": oscuro() ? "#05070a" : "#eef2f5", "horizon-color": oscuro() ? "#0d1a14" : "#dfe9e4", "fog-color": oscuro() ? "#000" : "#fff", "sky-horizon-blend": 0.6, "horizon-fog-blend": 0.7, "fog-ground-blend": 0.4 }); } catch (e) { /* cielo no soportado */ }

    if (m3.getSource("openmaptiles") && !m3.getLayer("gr-edificios")) {
      m3.addLayer({
        id: "gr-edificios", type: "fill-extrusion", source: "openmaptiles", "source-layer": "building", minzoom: 12.5,
        layout: { visibility: opc.edificios ? "visible" : "none" },
        paint: {
          "fill-extrusion-color": ["interpolate", ["linear"], ["coalesce", ["get", "render_height"], 6], 0, oscuro() ? "#15181c" : "#e8e8e6", 40, oscuro() ? "#21262c" : "#d9dad7", 140, oscuro() ? "#323a42" : "#c8cac6"],
          "fill-extrusion-height": ["interpolate", ["linear"], ["zoom"], 12.5, 0, 14.2, ["coalesce", ["get", "render_height"], 6]],
          "fill-extrusion-base": ["coalesce", ["get", "render_min_height"], 0],
          "fill-extrusion-opacity": 0.9, "fill-extrusion-vertical-gradient": true,
        },
      }, antes);
    }

    m3.addSource("gr-acc", { type: "geojson", data: vacio });
    m3.addLayer({ id: "gr-acc", type: "fill-extrusion", source: "gr-acc", filter: [">=", ["get", "k"], 0.14], layout: { visibility: opc.accidentes ? "visible" : "none" }, paint: {
      "fill-extrusion-color": ["interpolate", ["linear"], ["get", "k"], 0, "#2f9bff", 0.35, "#12ad7a", 0.6, "#ffb547", 1, "#ff4d6a"],
      "fill-extrusion-height": ["*", ["get", "h"], kAcc], "fill-extrusion-opacity": 0.82, "fill-extrusion-vertical-gradient": true } }, antes);
    m3.addSource("gr-pc", { type: "geojson", data: vacio });
    m3.addLayer({ id: "gr-pc", type: "fill-extrusion", source: "gr-pc", layout: { visibility: opc.accidentes ? "visible" : "none" }, paint: {
      "fill-extrusion-color": css("--bad") || "#ff6b7d", "fill-extrusion-height": ["*", ["get", "h"], kAcc], "fill-extrusion-opacity": 0.9 } }, antes);

    m3.addSource("gr-rutas", { type: "geojson", data: vacio });
    m3.addLayer({ id: "gr-rutas-borde", type: "line", source: "gr-rutas", layout: { "line-join": "round", "line-cap": "round" }, paint: {
      "line-color": oscuro() ? "#000" : "#fff", "line-width": ["interpolate", ["linear"], ["zoom"], 11, 4, 16, 12], "line-opacity": ["case", ["get", "activa"], 0.9, 0.5] } }, antes);
    m3.addLayer({ id: "gr-rutas-linea", type: "line", source: "gr-rutas", filter: ["!", ["get", "activa"]], layout: { "line-join": "round", "line-cap": "round" }, paint: {
      "line-color": ["get", "color"], "line-width": ["interpolate", ["linear"], ["zoom"], 11, 2, 16, 6], "line-opacity": 0.6 } }, antes);

    m3.addSource("gr-activa", { type: "geojson", data: vacio, lineMetrics: true });
    m3.addLayer({ id: "gr-activa-halo", type: "line", source: "gr-activa", layout: { "line-join": "round", "line-cap": "round" }, paint: {
      "line-color": css("--accent") || "#3ee6a0", "line-width": ["interpolate", ["linear"], ["zoom"], 11, 10, 16, 26], "line-blur": 12, "line-opacity": 0.35 } }, antes);
    m3.addLayer({ id: "gr-activa", type: "line", source: "gr-activa", layout: { "line-join": "round", "line-cap": "round" }, paint: {
      "line-width": ["interpolate", ["linear"], ["zoom"], 11, 3.5, 16, 9], "line-gradient": ["step", ["line-progress"], "#3ee6a0", 1, "rgba(0,0,0,0)"] } }, antes);

    m3.addSource("gr-muros", { type: "geojson", data: vacio });
    m3.addLayer({ id: "gr-muros-otros", type: "fill-extrusion", source: "gr-muros", filter: ["!", ["get", "activa"]], layout: { visibility: opc.muros ? "visible" : "none" }, paint: {
      "fill-extrusion-color": ["get", "color"], "fill-extrusion-height": ["*", ["get", "h"], kMuros], "fill-extrusion-opacity": 0.32 } });
    m3.addLayer({ id: "gr-muros-activa", type: "fill-extrusion", source: "gr-muros", filter: ["get", "activa"], layout: { visibility: opc.muros ? "visible" : "none" }, paint: {
      "fill-extrusion-color": ["get", "color"], "fill-extrusion-height": ["*", ["get", "h"], kMuros], "fill-extrusion-opacity": 0.72 } });

    if (datosAcc) m3.getSource("gr-acc").setData(datosAcc);
    if (datosPC) m3.getSource("gr-pc").setData(datosPC);
    actualizarRutas(false, false);
  }

  // ---------- rutas ----------
  const rutaActiva = () => { const e = G().estado; return e.rutas[e.rutaActiva]; };
  function actualizarRutas(encuadrar, animar = true) {
    if (!m3 || !m3.getSource("gr-rutas")) return;
    const e = G().estado;
    const lineas = e.rutas.map((r, i) => ({ type: "Feature", properties: { color: r.color, activa: i === e.rutaActiva }, geometry: { type: "LineString", coordinates: r.geometria.map(lngLat) } }));
    m3.getSource("gr-rutas").setData({ type: "FeatureCollection", features: lineas });
    const act = rutaActiva();
    m3.getSource("gr-activa").setData({ type: "FeatureCollection", features: act ? [{ type: "Feature", properties: {}, geometry: { type: "LineString", coordinates: act.geometria.map(lngLat) } }] : [] });

    // Muros: con varias rutas la altura es proporcional al CO₂; con una sola, un muro fijo de referencia.
    const max = Math.max(...e.rutas.map((r) => r.emisionKgCO2), 1e-6);
    const varias = e.rutas.length > 1;
    const muros = [];
    e.rutas.forEach((r, i) => {
      const activa = i === e.rutaActiva;
      const h = varias ? 40 + 300 * (r.emisionKgCO2 / max) : 90;
      const pts = r.geometria.map(lngLat);
      for (let k = 0; k < pts.length - 1; k++) {
        if (distM(pts[k], pts[k + 1]) < 0.5) continue;
        muros.push({ type: "Feature", properties: { color: r.color, activa, h }, geometry: { type: "Polygon", coordinates: [cuadSegmento(pts[k], pts[k + 1], activa ? 5 : 3.5)] } });
      }
    });
    m3.getSource("gr-muros").setData({ type: "FeatureCollection", features: muros });
    if (act) animarTrazo(act.color, animar);
    if (animar && !vuelo) crecerMuros(); else aplicarMuros();
    if (encuadrar && act && activo) encuadrarRuta(act);
  }

  function pintarTrazo(color, p, recorrido) {
    if (!m3.getLayer("gr-activa")) return;
    const pend = recorrido == null ? "rgba(0,0,0,0)" : rgba(color, 0.28);
    const hecho = recorrido == null ? color : (oscuro() ? "#ffffff" : "#0a0a0a");
    const grad = p <= 0 ? ["step", ["line-progress"], pend, 0, pend] : p >= 1 ? ["step", ["line-progress"], hecho, 1, hecho]
      : ["step", ["line-progress"], hecho, p, recorrido == null ? pend : color, Math.min(1, p + 0.004), pend];
    m3.setPaintProperty("gr-activa", "line-gradient", grad);
    m3.setPaintProperty("gr-activa-halo", "line-color", color);
  }
  function animarTrazo(color, animar) {
    cancelAnimationFrame(animTrazo);
    if (!animar || reducir) { pintarTrazo(color, 1); return; }
    const t0 = performance.now(), dur = 1500;
    const paso = (t) => {
      const k = Math.min(1, (t - t0) / dur), e = 1 - Math.pow(1 - k, 3);
      pintarTrazo(color, e);
      if (k < 1) animTrazo = requestAnimationFrame(paso);
    };
    animTrazo = requestAnimationFrame(paso);
  }
  function crecerMuros() { kMuros = 0; murosA(1); }
  // Lleva la altura de los muros hacia "destino" (1 = altura completa); durante el vuelo bajan para no tapar la calle.
  function murosA(destino, dur = 1300) {
    cancelAnimationFrame(animMuros);
    if (reducir) { kMuros = destino; aplicarMuros(); return; }
    const desde = kMuros, t0 = performance.now();
    const paso = (t) => {
      const k = Math.min(1, (t - t0) / dur);
      kMuros = desde + (destino - desde) * (1 - Math.pow(1 - k, 3)); aplicarMuros();
      if (k < 1) animMuros = requestAnimationFrame(paso);
    };
    animMuros = requestAnimationFrame(paso);
  }
  function aplicarMuros() {
    const k = vuelo ? Math.min(kMuros, 0.05) : kMuros;
    ["gr-muros-otros", "gr-muros-activa"].forEach((id) => m3.getLayer(id) && m3.setPaintProperty(id, "fill-extrusion-height", ["*", ["get", "h"], k]));
  }

  function paddingPanel() {
    const plegado = $("#panel").classList.contains("plegado") || innerWidth < 760;
    return { top: 110, bottom: 90, left: plegado ? 50 : 440, right: 60 };
  }
  function encuadrarRuta(r) {
    const b = new maplibregl.LngLatBounds();
    r.geometria.forEach((p) => b.extend(lngLat(p)));
    const cam = m3.cameraForBounds(b, { padding: paddingPanel(), bearing: m3.getBearing() });
    if (!cam) return;
    m3.easeTo({ center: cam.center, zoom: cam.zoom + (m3.getPitch() > 30 ? 0.6 : 0), bearing: m3.getBearing(), pitch: m3.getPitch(), duration: reducir ? 0 : 1400, essential: true });
  }

  function actualizarMarcas() {
    if (!m3) return;
    ["origen", "destino"].forEach((k) => {
      const p = G().estado[k];
      if (p) marcas[k].setLngLat([p.lng, p.lat]).addTo(m3); else marcas[k].remove();
    });
  }

  // ---------- accidentalidad 3D ----------
  async function prepararAccidentes() {
    if (datosAcc) return;
    const [heat, pc] = await Promise.all([json("data/accidentes_heat.json"), json("data/puntos_criticos.json")]);
    const C = 0.0018, celdas = new Map();
    heat.forEach(([la, lo, w]) => {
      const k = Math.floor(lo / C) + ":" + Math.floor(la / C);
      celdas.set(k, (celdas.get(k) || 0) + w);
    });
    const max = Math.max(...celdas.values());
    const m = C * 0.16;
    datosAcc = { type: "FeatureCollection", features: [...celdas].map(([k, v]) => {
      const [i, j] = k.split(":").map(Number), x = i * C, y = j * C, t = Math.pow(v / max, 0.7);
      return { type: "Feature", properties: { k: t, h: 25 + 520 * t, n: v },
        geometry: { type: "Polygon", coordinates: [[[x + m, y + m], [x + C - m, y + m], [x + C - m, y + C - m], [x + m, y + C - m], [x + m, y + m]]] } };
    }) };
    const maxS = Math.max(...pc.puntos.map((p) => p.indice_severidad));
    datosPC = { type: "FeatureCollection", features: pc.puntos.map((p) => ({ type: "Feature",
      properties: { h: 120 + 680 * (p.indice_severidad / maxS), ranking: p.ranking, sitio: p.sitio_frecuente, acc: p.accidentes, her: p.heridos, mue: p.muertos },
      geometry: { type: "Polygon", coordinates: circulo([p.lon, p.lat], 75) } })) };
    if (m3 && m3.getSource("gr-acc")) { m3.getSource("gr-acc").setData(datosAcc); m3.getSource("gr-pc").setData(datosPC); }
  }
  function animarAccidentes(subir) {
    cancelAnimationFrame(animAcc);
    const desde = kAcc, hasta = subir ? 1 : 0, t0 = performance.now(), dur = reducir ? 1 : 1100;
    const paso = (t) => {
      const k = Math.min(1, (t - t0) / dur), e = 1 - Math.pow(1 - k, 3);
      kAcc = desde + (hasta - desde) * e;
      ["gr-acc", "gr-pc"].forEach((id) => m3.getLayer(id) && m3.setPaintProperty(id, "fill-extrusion-height", ["*", ["get", "h"], kAcc]));
      if (k < 1) animAcc = requestAnimationFrame(paso);
      else if (!subir) ["gr-acc", "gr-pc"].forEach((id) => m3.getLayer(id) && m3.setLayoutProperty(id, "visibility", "none"));
    };
    animAcc = requestAnimationFrame(paso);
  }

  // ---------- clic en el mapa 3D ----------
  function clic(e) {
    if (vuelo) return;
    const pc = m3.getLayer("gr-pc") ? m3.queryRenderedFeatures(e.point, { layers: ["gr-pc"] }) : [];
    if (pc.length) {
      const p = pc[0].properties;
      if (popup) popup.remove();
      popup = new maplibregl.Popup({ offset: 12 }).setLngLat(e.lngLat)
        .setHTML(`<h4>Punto crítico #${p.ranking}</h4>${p.sitio}<br>${fmt(p.acc)} accidentes · ${fmt(p.her)} con heridos · ${fmt(p.mue)} con muertos`).addTo(m3);
      return;
    }
    G().fijarPunto(G().estado.modo, L.latLng(e.lngLat.lat, e.lngLat.lng), null);
  }

  // ---------- entrar / salir de 3D ----------
  async function entrar3D() {
    if (activo) return;
    dock.classList.add("cargando");
    try {
      await cargarML();
      if (!m3) { await crearMapa(); await prepararAccidentes().catch((e) => console.warn(e)); }
    } catch (e) {
      dock.classList.remove("cargando");
      aviso("No se pudo cargar la vista 3D (sin conexión con unpkg.com u OpenFreeMap).", "error");
      return;
    }
    dock.classList.remove("cargando");
    activo = true;
    const c = G().mapa.getCenter();
    m3.resize();
    m3.jumpTo({ center: [c.lng, c.lat], zoom: G().mapa.getZoom() - 1, pitch: 0, bearing: 0 });
    actualizarMarcas();
    actualizarRutas(false);
    raiz.classList.add("modo-3d", "entrando-3d");
    $("#mapa3d").setAttribute("aria-hidden", "false");
    pintarSeg();
    setTimeout(() => raiz.classList.remove("entrando-3d"), 950);
    const act = rutaActiva();
    let destino = { center: m3.getCenter(), zoom: m3.getZoom() + 0.35 };
    if (act) {
      const b = new maplibregl.LngLatBounds();
      act.geometria.forEach((p) => b.extend(lngLat(p)));
      const cam = m3.cameraForBounds(b, { padding: paddingPanel(), bearing: -22 });
      if (cam) destino = { center: cam.center, zoom: cam.zoom + 0.6 };
    }
    m3.easeTo({ ...destino, pitch: 58, bearing: -22, duration: reducir ? 0 : 2200, easing: (t) => 1 - Math.pow(1 - t, 3), essential: true });
    try { history.replaceState(null, "", location.pathname + location.search + "#3d"); } catch (e) { /* sin historial */ }
  }
  function salir3D() {
    if (!activo) return;
    detenerVuelo(); detenerOrbita();
    activo = false;
    pintarSeg();
    m3.easeTo({ pitch: 0, bearing: 0, duration: reducir ? 0 : 900, essential: true });
    setTimeout(() => {
      const c = m3.getCenter();
      G().mapa.setView([c.lat, c.lng], Math.round(m3.getZoom() + 1), { animate: false });
      raiz.classList.remove("modo-3d");
      $("#mapa3d").setAttribute("aria-hidden", "true");
    }, reducir ? 0 : 820);
    try { history.replaceState(null, "", location.pathname + location.search); } catch (e) { /* sin historial */ }
  }
  function pintarSeg() {
    dock.querySelectorAll("[data-vista]").forEach((b) => b.setAttribute("aria-pressed", String((b.dataset.vista === "3d") === activo)));
  }

  // ---------- órbita ----------
  function iniciarOrbita() {
    detenerVuelo();
    let ult = performance.now();
    const paso = (t) => {
      if (!orbita) return;
      const dt = (t - ult) / 1000; ult = t;
      m3.setBearing(m3.getBearing() + dt * 9);
      orbita = requestAnimationFrame(paso);
    };
    if (m3.getPitch() < 40) m3.easeTo({ pitch: 60, duration: 800 });
    orbita = requestAnimationFrame(paso);
    dock.querySelector('[data-acc="orbitar"]').setAttribute("aria-pressed", "true");
  }
  function detenerOrbita() {
    if (!orbita) return;
    cancelAnimationFrame(orbita); orbita = null;
    dock.querySelector('[data-acc="orbitar"]').setAttribute("aria-pressed", "false");
  }

  // ---------- vuelo por la ruta ----------
  function iniciarVuelo() {
    const r = rutaActiva();
    if (!r) { aviso("Calcula una ruta primero para poder recorrerla."); return; }
    detenerOrbita(); detenerVuelo();
    const pts = r.geometria.map(lngLat), acum = [0];
    for (let i = 1; i < pts.length; i++) acum.push(acum[i - 1] + distM(pts[i - 1], pts[i]));
    const total = acum[acum.length - 1] || 1;
    const dur = Math.max(9000, Math.min(30000, total * 1.5));
    // Indicaciones: tramo de vía según la distancia recorrida.
    const vias = []; let a = 0;
    (r.indicaciones || []).forEach((p) => { vias.push([a, a + p.metros, p.via]); a += p.metros; });
    const escalaVia = a > 0 ? total / a : 1;
    const punto = (d) => {
      let i = 1; while (i < acum.length - 1 && acum[i] < d) i++;
      const f = (d - acum[i - 1]) / ((acum[i] - acum[i - 1]) || 1);
      return [pts[i - 1][0] + (pts[i][0] - pts[i - 1][0]) * f, pts[i - 1][1] + (pts[i][1] - pts[i - 1][1]) * f];
    };
    const hud = $("#vuelo3d");
    $("#vuelo-titulo").textContent = `${r.vehiculoNombre} · ${r.criterioEtiqueta.replace("Menor huella de CO2", "Menor CO₂")}`;
    hud.classList.remove("oculto");
    if (m3.getLayer("gr-muros-otros")) m3.setLayoutProperty("gr-muros-otros", "visibility", "none");
    marcas.vehiculo.setLngLat(pts[0]).addTo(m3);
    let rumboSuave = rumbo(pts[0], punto(Math.min(total, 150)));
    const zoomVuelo = 16.2;
    m3.easeTo({ center: pts[0], zoom: zoomVuelo, pitch: 66, bearing: rumboSuave, duration: reducir ? 0 : 1800, essential: true });
    let t0 = null;
    vuelo = { r };
    murosA(0.05, 900);
    const paso = (t) => {
      if (!vuelo) return;
      if (t0 == null) t0 = t + (reducir ? 0 : 1800);
      const k = Math.max(0, Math.min(1, (t - t0) / dur));
      if (t >= t0) {
        const d = (k < 0.5 ? 2 * k * k : 1 - Math.pow(-2 * k + 2, 2) / 2) * total;
        const pos = punto(d), adelante = punto(Math.min(total, d + 180));
        if (distM(pos, adelante) > 5) rumboSuave += difAngulo(rumboSuave, rumbo(pos, adelante)) * 0.045;
        m3.jumpTo({ center: pos, bearing: rumboSuave, pitch: 66, zoom: zoomVuelo });
        marcas.vehiculo.setLngLat(pos);
        pintarTrazo(r.color, d / total, true);
        $("#vuelo-km").textContent = fmt(d / 1000, 1);
        $("#vuelo-co2").textContent = fmt(r.emisionKgCO2 * d / total, 2);
        $("#vuelo-min").textContent = fmt(r.tiempoMin * d / total);
        $("#vuelo-barra").style.width = (k * 100).toFixed(1) + "%";
        const v = vias.find(([x, y]) => d / escalaVia >= x && d / escalaVia < y);
        $("#vuelo-via").textContent = v ? "Por " + v[2] : "Llegada al destino";
      }
      if (k < 1) vuelo.raf = requestAnimationFrame(paso);
      else { vuelo = null; setTimeout(() => terminarVuelo(r), 600); }
    };
    vuelo.raf = requestAnimationFrame(paso);
    dock.querySelector('[data-acc="vuelo"]').lastChild.textContent = "Recorriendo…";
  }
  function terminarVuelo(r) {
    $("#vuelo3d").classList.add("oculto");
    marcas.vehiculo.remove();
    dock.querySelector('[data-acc="vuelo"]').lastChild.textContent = "Recorrer ruta";
    if (m3 && opc.muros && m3.getLayer("gr-muros-otros")) m3.setLayoutProperty("gr-muros-otros", "visibility", "visible");
    if (m3) murosA(1, 1400);
    if (r && m3) { pintarTrazo(r.color, 1); m3.easeTo({ pitch: 55, duration: 600 }); setTimeout(() => encuadrarRuta(r), 300); }
  }
  function detenerVuelo() {
    if (!vuelo) return;
    cancelAnimationFrame(vuelo.raf);
    const r = vuelo.r; vuelo = null;
    terminarVuelo(r);
  }
  $("#vuelo-parar").addEventListener("click", detenerVuelo);
  document.addEventListener("keydown", (e) => { if (e.key === "Escape") { detenerVuelo(); detenerOrbita(); } });

  // ---------- dock ----------
  dock.addEventListener("click", async (e) => {
    const b = e.target.closest("button"); if (!b) return;
    if (b.dataset.vista) { if (b.dataset.vista === "3d") entrar3D(); else salir3D(); return; }
    if (!activo || !m3) return;
    const acc = b.dataset.acc, capa = b.dataset.capa;
    if (acc === "rotar-izq" || acc === "rotar-der") { detenerOrbita(); m3.easeTo({ bearing: m3.getBearing() + (acc === "rotar-izq" ? -45 : 45), duration: 700 }); }
    else if (acc === "inclinar") { detenerOrbita(); m3.easeTo({ pitch: m3.getPitch() > 25 ? 0 : 60, duration: 900 }); }
    else if (acc === "orbitar") { orbita ? detenerOrbita() : iniciarOrbita(); }
    else if (acc === "vuelo") { vuelo ? detenerVuelo() : iniciarVuelo(); }
    else if (capa) {
      opc[capa] = !opc[capa];
      b.setAttribute("aria-pressed", String(opc[capa]));
      if (capa === "edificios" && m3.getLayer("gr-edificios")) m3.setLayoutProperty("gr-edificios", "visibility", opc.edificios ? "visible" : "none");
      if (capa === "muros") {
        ["gr-muros-otros", "gr-muros-activa"].forEach((id) => m3.getLayer(id) && m3.setLayoutProperty(id, "visibility", opc.muros ? "visible" : "none"));
        if (opc.muros) crecerMuros();
      }
      if (capa === "accidentes") {
        if (opc.accidentes) {
          await prepararAccidentes().catch(() => aviso("No se pudieron cargar los datos de accidentalidad.", "error"));
          ["gr-acc", "gr-pc"].forEach((id) => m3.getLayer(id) && m3.setLayoutProperty(id, "visibility", "visible"));
          if (m3.getPitch() < 30) m3.easeTo({ pitch: 55, duration: 900 });
          aviso("Columnas: accidentes por celda de 200 m. Torres rojas: puntos críticos (clic para ver el detalle).");
        }
        animarAccidentes(opc.accidentes);
      }
    }
  });

  // ---------- eventos del planificador 2D ----------
  document.addEventListener("gr:rutas", (e) => { if (m3) actualizarRutas(e.detail && e.detail.encuadrar); });
  document.addEventListener("gr:puntos", actualizarMarcas);
  document.addEventListener("gr:tema", () => {
    if (!m3) return;
    m3.setStyle(ESTILOS[window.GR.temaActual()]);
  });
  addEventListener("resize", () => m3 && m3.resize());

  // Abrir directamente en 3D con mapa.html#3d (enlace de la portada).
  if (/(^#|&)3d\b/.test(location.hash)) {
    let hecho = false;
    const ir = () => { if (!hecho) { hecho = true; setTimeout(entrar3D, 350); } };
    document.addEventListener("gr:rutas", ir, { once: true });
    setTimeout(ir, 4000);
  }
})();
