// Página mapa.html: mapa Leaflet conectado al API Java para calcular rutas, alternativas y comparar la flota.
(function () {
  "use strict";
  const { fmt, json, aviso } = window.GR;
  const $ = (s) => document.querySelector(s);
  const CENTRO = [10.965, -74.81];

  const ICONOS = {
    van: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round"><path d="M3 7h11v9H3zM14 10h4l3 3v3h-7"/><circle cx="7" cy="17.5" r="1.8"/><circle cx="17" cy="17.5" r="1.8"/></svg>',
    camion: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round"><path d="M2 5h12v11H2zM14 9h4.5L22 12.5V16h-8"/><circle cx="6" cy="17.5" r="1.8"/><circle cx="18" cy="17.5" r="1.8"/></svg>',
    tracto: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round"><path d="M1 6h13v9H1zM14 9h4l3 3v3h-7"/><circle cx="4" cy="17.5" r="1.6"/><circle cx="9" cy="17.5" r="1.6"/><circle cx="18" cy="17.5" r="1.6"/></svg>',
    barco: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round"><path d="M3 14h18l-2.5 4.5h-13zM6 14V9h8v5M14 11h3l2 3"/><path d="M2 21c2 0 2-1 4-1s2 1 4 1 2-1 4-1 2 1 4 1 2-1 4-1"/></svg>',
  };
  const icoVehiculo = (v) => v.medio === "FLUVIAL" ? ICONOS.barco
    : v.clase === "Tractocamion" ? ICONOS.tracto : v.clase.startsWith("Furgoneta") ? ICONOS.van : ICONOS.camion;
  const CRITERIOS = [
    ["EMISIONES", "Menor CO₂", "#12ad7a"], ["TIEMPO", "Más rápida", "#1e9ad6"], ["DISTANCIA", "Más corta", "#8f78f0"],
    ["RIESGO", "Más segura", "#e8662f"], ["ECO", "Eco-balanceada", "#c48200"],
  ];

  const estado = {
    origen: null, destino: null, modo: "origen", vehiculo: "VAN-E", criterio: "EMISIONES", hora: 7, carga: 900,
    vehiculos: [], factores: [], rutas: [], rutaActiva: 0, ultimaConsulta: null,
  };

  const mapa = L.map("mapa", { zoomControl: false, maxBounds: [[10.55, -75.25], [11.35, -74.4]], minZoom: 11, preferCanvas: false })
    .setView(CENTRO, 13);
  L.control.zoom({ position: "bottomright" }).addTo(mapa);
  L.control.scale({ imperial: false, position: "bottomright" }).addTo(mapa);
  const atrib = '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>';
  const esri = (s) => `https://server.arcgisonline.com/ArcGIS/rest/services/${s}/MapServer/tile/{z}/{y}/{x}`;
  const capaEsri = (base, ref) => L.layerGroup([
    L.tileLayer(esri(base), { attribution: "Mapa base &copy; Esri, HERE, Garmin · " + atrib, maxZoom: 19, maxNativeZoom: 16 }),
    L.tileLayer(esri(ref), { maxZoom: 19, maxNativeZoom: 16, pane: "overlayPane", opacity: 0.85 }),
  ]);
  const bases = {
    "Monocromo (sigue el tema)": null,
    "Calles (OpenStreetMap)": L.tileLayer("https://tile.openstreetmap.org/{z}/{x}/{y}.png", { attribution: atrib, maxZoom: 19 }),
    "Satélite": L.tileLayer(esri("World_Imagery"), { attribution: "Imágenes &copy; Esri", maxZoom: 19 }),
  };
  const mono = { dark: capaEsri("Canvas/World_Dark_Gray_Base", "Canvas/World_Dark_Gray_Reference"),
                 light: capaEsri("Canvas/World_Light_Gray_Base", "Canvas/World_Light_Gray_Reference") };
  const capaMono = L.layerGroup().addTo(mapa);
  bases["Monocromo (sigue el tema)"] = capaMono;
  function pintarMono() { capaMono.clearLayers(); capaMono.addLayer(mono[window.GR.temaActual()]); }
  pintarMono();
  document.addEventListener("gr:tema", () => { pintarMono(); dibujarRutas(false); });

  const capaRutas = L.layerGroup().addTo(mapa);
  const capas = {};
  const control = L.control.layers(bases, {}, { position: "topright", collapsed: true }).addTo(mapa);
  const leyenda = L.control({ position: "bottomright" });
  leyenda.onAdd = () => {
    const d = L.DomUtil.create("div", "leyenda");
    d.innerHTML = "<b>Criterio</b>" + CRITERIOS.map(([, n, c]) => `<div><i style="background:${c}"></i>${n}</div>`).join("");
    return d;
  };
  leyenda.addTo(mapa);

  const pin = (letra, clase) => L.divIcon({ className: "", iconSize: [30, 30], iconAnchor: [15, 15],
    html: `<div class="marcador ${clase}"><span class="marcador-pulso"></span>${letra}</div>` });
  const marcadores = {
    origen: L.marker(CENTRO, { icon: pin("A", "marcador--a"), draggable: true, autoPan: true }),
    destino: L.marker(CENTRO, { icon: pin("B", "marcador--b"), draggable: true, autoPan: true }),
  };
  ["origen", "destino"].forEach((k) => marcadores[k].on("dragend", (e) => fijarPunto(k, e.target.getLatLng(), null, true)));

  const relleno = () => {
    const plegado = $("#panel").classList.contains("plegado") || innerWidth < 760;
    return { paddingTopLeft: [plegado ? 40 : 440, 90], paddingBottomRight: [60, 40] };
  };

  async function cargarCapas() {
    const cargar = async (url, fn, nombre) => {
      try { const capa = fn(await json(url)); capas[nombre] = capa; control.addOverlay(capa, nombre); } catch (e) { console.warn(url, e); }
    };
    await Promise.all([
      cargar("data/accidentes_heat.json", (heat) => {
        const max = heat.reduce((m, p) => Math.max(m, p[2]), 0);
        return L.heatLayer(heat, { radius: 13, blur: 16, maxZoom: 16, max: max * 0.25, minOpacity: 0.25,
          gradient: { 0.2: "#1e9ad6", 0.45: "#12ad7a", 0.7: "#c48200", 1: "#e64566" } });
      }, "Mapa de calor de accidentes"),
      cargar("data/puntos_criticos.json", (pc) => L.layerGroup(pc.puntos.map((p) => L.circleMarker([p.lat, p.lon], {
        radius: 6 + Math.sqrt(p.indice_severidad) / 3, color: "#fff", weight: 1.5, fillColor: "#e64566", fillOpacity: 0.85,
      }).bindPopup(`<h4>Punto crítico #${p.ranking}</h4>${p.sitio_frecuente}<br>${fmt(p.accidentes)} accidentes · ${fmt(p.heridos)} con heridos · ${fmt(p.muertos)} con muertos`))), "Puntos críticos (DBSCAN)"),
      cargar("data/estaciones_aire.json", (est) => L.layerGroup(est.map((s) => L.circleMarker([s.lat, s.lon], {
        radius: 8, color: "#fff", weight: 1.5, fillColor: "#8f78f0", fillOpacity: 0.9,
      }).bindPopup(`<h4>Estación ${s.estacion}</h4>${s.entidad}<br>${s.pm25 != null ? `PM2.5 medio: <b>${fmt(s.pm25, 1)} µg/m³</b><br>` : ""}${s.pm10 != null ? `PM10 medio: <b>${fmt(s.pm10, 1)} µg/m³</b>` : ""}`))), "Estaciones de calidad del aire"),
      cargar("data/rio.json", (rio) => L.polyline(rio, { color: "#1e9ad6", weight: 4, opacity: 0.6, dashArray: "2 8", lineCap: "round", interactive: false }), "Río Magdalena (red fluvial)"),
    ]);
  }

  let puntosLogisticos = [];
  async function cargarPuntos() {
    puntosLogisticos = await json("api/puntos");
    capas.logisticos = L.layerGroup(puntosLogisticos.map((p) => L.circleMarker([p.lat, p.lon], {
      radius: 4.5, color: "#fff", weight: 1.5, fillColor: "#111", fillOpacity: 0.9,
    }).bindTooltip(`${p.nombre} · ${p.categoria}`, { direction: "top" }).on("click", (ev) => {
      L.DomEvent.stopPropagation(ev); fijarPunto(estado.modo, L.latLng(p.lat, p.lon), p.nombre);
    })));
    capas.logisticos.addTo(mapa);
    control.addOverlay(capas.logisticos, "Puntos logísticos");
    $("#atajos").innerHTML = puntosLogisticos.map((p, i) => `<button class="atajo" data-i="${i}">${p.nombre}</button>`).join("");
    $("#atajos").addEventListener("click", (e) => {
      const b = e.target.closest(".atajo"); if (!b) return;
      const p = puntosLogisticos[+b.dataset.i];
      fijarPunto(estado.modo, L.latLng(p.lat, p.lon), p.nombre);
    });
  }

  function ponerModo(modo) {
    estado.modo = modo;
    document.querySelectorAll(".od__pin[data-modo]").forEach((b) => b.classList.toggle("activo", b.dataset.modo === modo));
    $("#modo-txt").textContent = modo === "origen" ? "el origen (A)" : "el destino (B)";
  }
  document.querySelectorAll(".od__pin[data-modo]").forEach((b) => b.addEventListener("click", () => ponerModo(b.dataset.modo)));
  ponerModo("origen");

  function pintarVehiculos() {
    $("#vehiculos").innerHTML = estado.vehiculos.map((v) => `
      <button class="vehiculo" role="radio" aria-checked="${v.id === estado.vehiculo}" data-id="${v.id}" title="${v.tecnologia}">
        <span class="vehiculo__ico">${icoVehiculo(v)}</span>
        <span><b>${v.nombre}</b><small>${v.recargable ? "⚡ " : ""}${fmt(v.capacidadKg / 1000, v.capacidadKg < 10000 ? 1 : 0)} t · ${v.energia.split(" ")[0]}</small></span>
      </button>`).join("");
    ajustarCarga(false);
  }
  $("#vehiculos").addEventListener("click", (e) => {
    const b = e.target.closest(".vehiculo"); if (!b) return;
    estado.vehiculo = b.dataset.id; pintarVehiculos();
    if (listos()) calcular("ruta");
  });

  function ajustarCarga(avisar = true) {
    const v = estado.vehiculos.find((x) => x.id === estado.vehiculo);
    if (!v) return;
    const r = $("#in-carga");
    const paso = v.capacidadKg > 100000 ? 5000 : v.capacidadKg > 20000 ? 500 : v.capacidadKg > 5000 ? 100 : 50;
    r.max = v.capacidadKg; r.step = paso; r.min = paso;
    if (estado.carga > v.capacidadKg) {
      if (avisar) aviso(`Carga ajustada a ${fmt(v.capacidadKg)} kg: es la capacidad de ${v.nombre.toLowerCase()}`);
      estado.carga = v.capacidadKg;
    }
    r.value = estado.carga;
    $("#lbl-carga").textContent = fmt(estado.carga) + " kg";
    $("#ayuda-carga").textContent = `${fmt(estado.carga / v.capacidadKg * 100)} % de ${fmt(v.capacidadKg)} kg`;
  }
  $("#in-carga").addEventListener("input", (e) => { estado.carga = +e.target.value; ajustarCarga(false); });
  $("#in-carga").addEventListener("change", () => { if (listos()) calcular("ruta"); });

  function pintarCriterios() {
    $("#criterios").innerHTML = CRITERIOS.map(([id, n, c]) =>
      `<button class="chip" role="radio" aria-checked="${id === estado.criterio}" data-id="${id}"><i style="background:${c}"></i>${n}</button>`).join("");
  }
  $("#criterios").addEventListener("click", (e) => {
    const b = e.target.closest(".chip"); if (!b) return;
    estado.criterio = b.dataset.id; pintarCriterios();
    if (listos()) calcular("ruta");
  });

  function pintarHora() {
    $("#lbl-hora").textContent = String(estado.hora).padStart(2, "0") + ":00";
    const f = estado.factores[estado.hora];
    if (f != null) {
      const nivel = f > 0.9 ? "fluido" : f > 0.72 ? "moderado" : f > 0.6 ? "denso" : "hora pico";
      $("#ayuda-hora").textContent = `Tráfico ${nivel} · ${fmt(f * 100)} % del límite`;
    }
    $("#trafico").querySelectorAll("i").forEach((el, h) => el.classList.toggle("sel", h === estado.hora));
  }
  $("#in-hora").addEventListener("input", (e) => { estado.hora = +e.target.value; pintarHora(); });
  $("#in-hora").addEventListener("change", () => { if (listos()) calcular("ruta"); });

  $("#btn-swap").addEventListener("click", () => {
    const o = estado.origen, d = estado.destino, to = $("#in-origen").value, td = $("#in-destino").value;
    if (d) fijarPunto("origen", d, td, false); else limpiarPunto("origen");
    if (o) fijarPunto("destino", o, to, false); else limpiarPunto("destino");
    if (listos()) calcular("ruta");
  });
  $("#btn-plegar").addEventListener("click", () => { $("#panel").classList.add("plegado"); $("#btn-desplegar").classList.remove("oculto"); });
  $("#btn-desplegar").addEventListener("click", () => { $("#panel").classList.remove("plegado"); $("#btn-desplegar").classList.add("oculto"); });

  const listos = () => estado.origen && estado.destino;
  const avisarPuntos = () => document.dispatchEvent(new CustomEvent("gr:puntos"));
  function limpiarPunto(campo) { estado[campo] = null; mapa.removeLayer(marcadores[campo]); $("#in-" + campo).value = ""; avisarPuntos(); }

  let ctrlGeo = {};
  async function fijarPunto(campo, latlng, nombre, recalcular = true) {
    estado[campo] = latlng;
    marcadores[campo].setLatLng(latlng).addTo(mapa);
    avisarPuntos();
    const input = $("#in-" + campo);
    input.value = nombre || `${latlng.lat.toFixed(5)}, ${latlng.lng.toFixed(5)}`;
    if (campo === "origen" && !estado.destino) ponerModo("destino");
    else if (campo === "origen") ponerModo("destino");
    if (recalcular && listos()) calcular("ruta");
    else if (recalcular && campo === "origen") aviso("Origen fijado. Ahora haz clic en el destino (B).");
    if (!nombre) {
      if (ctrlGeo[campo]) ctrlGeo[campo].abort();
      const c = ctrlGeo[campo] = new AbortController();
      setTimeout(() => c.abort(), 3000);
      try {
        const r = await fetch(`https://nominatim.openstreetmap.org/reverse?format=jsonv2&zoom=17&accept-language=es&lat=${latlng.lat}&lon=${latlng.lng}`, { signal: c.signal });
        const d = await r.json();
        if (d && d.address && estado[campo] === latlng) {
          const a = d.address;
          input.value = [a.road, a.suburb || a.neighbourhood || a.city_district || a.town].filter(Boolean).join(", ") || input.value;
        }
      } catch (e) {  }
    }
  }

  mapa.on("click", (e) => { cerrarMenu(); fijarPunto(estado.modo, e.latlng, null); });

  let puntoCtx = null;
  const menu = $("#menu-ctx");
  function cerrarMenu() { menu.classList.add("oculto"); }
  mapa.on("contextmenu", (e) => {
    puntoCtx = e.latlng;
    const p = e.containerPoint;
    menu.style.left = Math.min(p.x, innerWidth - 220) + "px";
    menu.style.top = Math.min(p.y, innerHeight - 150) + "px";
    menu.classList.remove("oculto");
  });
  mapa.on("movestart zoomstart", cerrarMenu);
  menu.addEventListener("click", (e) => {
    const b = e.target.closest("button[data-accion]"); if (!b || !puntoCtx) return;
    cerrarMenu();
    if (b.dataset.accion === "centrar") mapa.panTo(puntoCtx);
    else fijarPunto(b.dataset.accion, puntoCtx, null);
  });

  ["origen", "destino"].forEach((campo) => {
    const input = $("#in-" + campo), lista = $("#sug-" + campo);
    let temporizador, resultados = [], ctrl, sel = -1;
    input.addEventListener("focus", () => ponerModo(campo));
    input.addEventListener("input", () => {
      clearTimeout(temporizador);
      const q = input.value.trim();
      if (q.length < 3) { lista.classList.remove("visible"); return; }
      temporizador = setTimeout(async () => {
        const locales = puntosLogisticos.filter((p) => p.nombre.toLowerCase().includes(q.toLowerCase()))
          .map((p) => ({ nombre: p.nombre, detalle: "Punto logístico · " + p.categoria, lat: p.lat, lon: p.lon }));
        let remotos = [];
        if (ctrl) ctrl.abort();
        ctrl = new AbortController();
        try {
          const url = `https://nominatim.openstreetmap.org/search?format=jsonv2&limit=6&countrycodes=co&accept-language=es&bounded=1&viewbox=-74.98,11.10,-74.68,10.80&q=${encodeURIComponent(q)}`;
          remotos = (await (await fetch(url, { signal: ctrl.signal })).json()).map((r) => ({
            nombre: r.display_name.split(",").slice(0, 2).join(","), detalle: r.display_name.split(",").slice(2, 4).join(","), lat: +r.lat, lon: +r.lon }));
        } catch (e) { if (e.name === "AbortError") return; }
        resultados = [...locales, ...remotos].slice(0, 8);
        sel = -1;
        lista.innerHTML = resultados.length ? resultados.map((r, i) => `<li role="option" data-i="${i}">${r.nombre}<small>${r.detalle}</small></li>`).join("")
                                            : '<li aria-disabled="true"><small>Sin resultados en el área metropolitana</small></li>';
        lista.classList.add("visible");
      }, 350);
    });
    const elegir = (r) => { lista.classList.remove("visible"); fijarPunto(campo, L.latLng(r.lat, r.lon), r.nombre); mapa.panTo([r.lat, r.lon]); };
    input.addEventListener("keydown", (e) => {
      if (!lista.classList.contains("visible") || !resultados.length) { if (e.key === "Enter" && listos()) calcular("ruta"); return; }
      if (e.key === "ArrowDown" || e.key === "ArrowUp") {
        sel = (sel + (e.key === "ArrowDown" ? 1 : -1) + resultados.length) % resultados.length;
        lista.querySelectorAll("li").forEach((li, i) => li.setAttribute("aria-selected", i === sel)); e.preventDefault();
      } else if (e.key === "Enter") { elegir(resultados[Math.max(sel, 0)]); e.preventDefault(); }
      else if (e.key === "Escape") lista.classList.remove("visible");
    });
    lista.addEventListener("mousedown", (e) => { const li = e.target.closest("li[data-i]"); if (li) elegir(resultados[+li.dataset.i]); });
    input.addEventListener("blur", () => setTimeout(() => lista.classList.remove("visible"), 150));
  });

  let ctrlApi = null;
  function parametros(extra = {}) {
    return new URLSearchParams({
      olat: estado.origen.lat, olon: estado.origen.lng, dlat: estado.destino.lat, dlon: estado.destino.lng,
      vehiculo: estado.vehiculo, criterio: estado.criterio, hora: estado.hora, carga: estado.carga, ...extra }).toString();
  }
  async function api(ruta, extra, senal) {
    const r = await fetch(`api/${ruta}?${parametros(extra)}`, { signal: senal });
    const d = await r.json();
    if (!r.ok) throw new Error(d.mensaje || "Error del servidor");
    return d;
  }
  function ocupado(si) {
    ["#btn-ruta", "#btn-alternativas", "#btn-flota"].forEach((s) => { $(s).disabled = si; });
    const b = $("#carga-barra");
    if (si) { b.className = "carga-barra"; void b.offsetWidth; b.classList.add("activa"); }
    else { b.classList.remove("activa"); b.classList.add("fin"); }
  }

  async function calcular(tipo) {
    if (!listos()) { aviso("Fija el origen (A) y el destino (B): clic en el mapa, buscador o puntos logísticos."); return; }
    if (ctrlApi) ctrlApi.abort();
    const ctrl = ctrlApi = new AbortController();
    const t0 = performance.now();
    ocupado(true);
    try {
      if (tipo === "flota") {
        const d = await api("comparar", {}, ctrl.signal);
        pintarFlota(d); activarPestana("flota");
      } else {
        const rutas = tipo === "alternativas" ? await api("alternativas", {}, ctrl.signal) : [await api("ruta", {}, ctrl.signal)];
        estado.rutas = rutas;
        estado.rutaActiva = tipo === "alternativas" ? rutas.reduce((m, r, i) => (r.emisionKgCO2 < rutas[m].emisionKgCO2 ? i : m), 0) : 0;
        dibujarRutas(true); pintarResumen(); pintarPasos();
        if (tipo === "alternativas" || $("#p-flota").classList.contains("oculto")) activarPestana("resumen");
      }
      $("#resultado").classList.remove("oculto");
      estado.ultimaConsulta = Math.round(performance.now() - t0);
    } catch (e) {
      if (e.name === "AbortError") return;
      aviso(e.message, "error");
    } finally { if (ctrl === ctrlApi) ocupado(false); }
  }
  $("#btn-ruta").addEventListener("click", () => calcular("ruta"));
  $("#btn-alternativas").addEventListener("click", () => calcular("alternativas"));
  $("#btn-flota").addEventListener("click", () => calcular("flota"));
  document.addEventListener("keydown", (e) => {
    if (e.key === "Enter" && !/input|textarea/i.test(document.activeElement.tagName) && listos()) calcular("ruta");
  });

  function dibujarRutas(encuadrar) {
    capaRutas.clearLayers();
    document.dispatchEvent(new CustomEvent("gr:rutas", { detail: { encuadrar } }));
    if (!estado.rutas.length) return;
    const casing = window.GR.temaActual() === "dark" ? "#000" : "#fff";
    const orden = estado.rutas.map((_, i) => i).sort((a, b) => (a === estado.rutaActiva) - (b === estado.rutaActiva));
    orden.forEach((i) => {
      const r = estado.rutas[i], activa = i === estado.rutaActiva;
      L.polyline(r.geometria, { color: casing, weight: activa ? 10 : 7, opacity: activa ? 0.9 : 0.5, interactive: false, lineJoin: "round" }).addTo(capaRutas);
      const linea = L.polyline(r.geometria, { color: r.color, weight: activa ? 5.5 : 3.5, opacity: activa ? 1 : 0.5, interactive: false, lineJoin: "round" }).addTo(capaRutas);
      if (activa) {
        animarTrazo(linea);
        const g = r.geometria;
        [[estado.origen, g[0]], [estado.destino, g[g.length - 1]]].forEach(([p, q]) => {
          if (p && mapa.distance(p, q) > 15) L.polyline([p, q], { color: window.GR.temaActual() === "dark" ? "#fff" : "#000", weight: 1.5, dashArray: "3 6", opacity: .8, interactive: false }).addTo(capaRutas);
        });
        if (encuadrar) mapa.fitBounds(linea.getBounds(), relleno());
      }
    });
  }
  function animarTrazo(linea) {
    const el = linea.getElement && linea.getElement();
    if (!el || !el.getTotalLength || matchMedia("(prefers-reduced-motion: reduce)").matches) return;
    const largo = Math.ceil(el.getTotalLength());
    el.style.setProperty("--largo", largo);
    el.classList.remove("ruta-animada"); void el.getBoundingClientRect(); el.classList.add("ruta-animada");
  }

  function seleccionar(i) { estado.rutaActiva = i; dibujarRutas(false); pintarResumen(); pintarPasos(); }

  function pintarResumen() {
    const r = estado.rutas[estado.rutaActiva];
    const alt = estado.rutas.length > 1;
    const iMin = estado.rutas.reduce((m, x, i) => (x.emisionKgCO2 < estado.rutas[m].emisionKgCO2 - 1e-9 ? i : m), 0);
    $("#p-resumen").innerHTML = `
      <div class="ruta-cab"><i style="background:${r.color}"></i><strong>${r.criterioEtiqueta}</strong><span>${r.vehiculoNombre}</span></div>
      <div class="metricas">
        <div class="metrica"><b>${fmt(r.distanciaKm, 1)} km</b><small>Distancia</small></div>
        <div class="metrica"><b>${fmt(r.tiempoMin)} min</b><small>Salida ${String(r.horaSalida).padStart(2, "0")}:00</small></div>
        <div class="metrica metrica--co2"><b>${fmt(r.emisionKgCO2, 2)} kg</b><small>CO₂e emitido</small></div>
        <div class="metrica"><b>$${fmt(r.costoCOP)}</b><small>Costo COP</small></div>
      </div>
      <dl class="detalles">
        <dt>Energía</dt><dd>${fmt(r.consumo, 2)} ${r.unidadConsumo}</dd>
        <dt>Intensidad</dt><dd>${fmt(r.intensidadGTkm, 0)} g CO₂/t·km</dd>
        <dt>Riesgo vial medio</dt><dd>${fmt(r.riesgo * 100, 0)} / 100</dd>
        <dt>Tráfico a esa hora</dt><dd>${fmt(r.factorTrafico * 100, 0)} % del límite</dd>
        ${r.autonomiaKm ? `<dt>Autonomía con esta carga</dt><dd>${fmt(r.autonomiaKm)} km</dd>` : ""}
      </dl>
      ${alt ? `<ul class="alternativas">${estado.rutas.map((x, i) => `
        <li class="alternativa ${i === estado.rutaActiva ? "activa" : ""}" data-i="${i}" tabindex="0">
          <i style="background:${x.color}"></i>
          <span><b>${x.criterioEtiqueta}${i === iMin ? '<span class="mejor">menor CO₂</span>' : ""}</b>
          <small>${fmt(x.distanciaKm, 1)} km · ${fmt(x.tiempoMin)} min · riesgo ${fmt(x.riesgo * 100)}</small></span>
          <span class="co2">${fmt(x.emisionKgCO2, 2)}<small>kg CO₂</small></span></li>`).join("")}</ul>` : ""}
      <div class="algoritmo"><span>${r.algoritmo} · ${fmt(r.nodosExplorados)} nodos · ${fmt(r.calculoMs, 0)} ms en el servidor</span>
        <button id="btn-alg">Dijkstra vs A*</button></div>
      <div class="comparativa-alg" id="cmp-alg"></div>`;
    $("#p-resumen").querySelectorAll(".alternativa").forEach((li) => {
      li.addEventListener("click", () => seleccionar(+li.dataset.i));
      li.addEventListener("keydown", (e) => { if (e.key === "Enter") seleccionar(+li.dataset.i); });
    });
    $("#btn-alg").addEventListener("click", async () => {
      $("#cmp-alg").innerHTML = '<span class="cargando"></span>';
      try {
        const d = await api("algoritmos", { criterio: r.criterio, vehiculo: r.vehiculo });
        const [dj, as] = d;
        $("#cmp-alg").innerHTML = `<table class="tabla-flota"><thead><tr><th>Algoritmo</th><th class="num">Nodos</th><th class="num">ms</th><th class="num">kg CO₂</th></tr></thead>
          <tbody>${d.map((a) => `<tr><td>${a.algoritmo}</td><td class="num">${fmt(a.nodosExplorados)}</td><td class="num">${fmt(a.calculoMs, 1)}</td><td class="num">${fmt(a.emisionKgCO2, 3)}</td></tr>`).join("")}</tbody></table>
          <p class="ayuda">A* explora ${fmt((1 - as.nodosExplorados / dj.nodosExplorados) * 100)} % menos nodos y obtiene la misma ruta óptima.</p>`;
      } catch (e) { $("#cmp-alg").textContent = e.message; }
    });
  }

  function pintarPasos() {
    const r = estado.rutas[estado.rutaActiva];
    $("#p-pasos").innerHTML = `<ol class="pasos">${r.indicaciones.map((p, i) =>
      `<li><b style="font-weight:500">${i === 0 ? "Sal por" : "Continúa por"} ${p.via}</b><span>${p.metros >= 1000 ? fmt(p.metros / 1000, 1) + " km" : fmt(p.metros) + " m"}</span></li>`).join("")}
      <li><b style="font-weight:500">Llegada al destino</b><span>${fmt(r.distanciaKm, 1)} km</span></li></ol>`;
  }

  function pintarFlota(d) {
    const ok = d.opciones.filter((o) => o.factible);
    const max = Math.max(...ok.map((o) => o.ruta.emisionKgCO2), 0.001);
    const rec = d.opciones.find((o) => o.vehiculo === d.recomendado);
    const peor = ok.reduce((m, o) => (!m || o.ruta.emisionKgCO2 > m.ruta.emisionKgCO2 ? o : m), null);
    $("#p-flota").innerHTML = `
      ${rec ? `<p class="nota-flota">Recomendado: <b>${rec.nombre}</b> · ${fmt(rec.ruta.emisionKgCO2, 2)} kg CO₂${peor && peor !== rec ? `, ${fmt((1 - rec.ruta.emisionKgCO2 / peor.ruta.emisionKgCO2) * 100)} % menos que ${peor.nombre.toLowerCase()}` : ""}.</p>` : ""}
      <table class="tabla-flota"><thead><tr><th>Vehículo</th><th class="num">km</th><th class="num">min</th><th class="num">kg CO₂</th></tr></thead>
      <tbody>${d.opciones.map((o) => o.factible ? `
        <tr data-id="${o.vehiculo}" class="${o.vehiculo === d.recomendado ? "destacada" : ""}">
          <td>${o.nombre}<div class="barra-co2"><i style="width:${o.ruta.emisionKgCO2 / max * 100}%"></i></div></td>
          <td class="num">${fmt(o.ruta.distanciaKm, 1)}</td><td class="num">${fmt(o.ruta.tiempoMin)}</td><td class="num">${fmt(o.ruta.emisionKgCO2, 2)}</td></tr>` : `
        <tr><td>${o.nombre}<div class="motivo">${o.motivo}</div></td><td colspan="3" class="num"><span class="pastilla pastilla--no">no factible</span></td></tr>`).join("")}
      </tbody></table><p class="ayuda">Haz clic en un vehículo para trazar su ruta.</p>`;
    $("#p-flota").querySelectorAll("tr[data-id]").forEach((tr) => tr.addEventListener("click", () => {
      estado.vehiculo = tr.dataset.id; pintarVehiculos(); calcular("ruta");
    }));
  }

  function activarPestana(nombre) {
    document.querySelectorAll(".pestanas button").forEach((b) => b.setAttribute("aria-selected", b.dataset.pestana === nombre));
    document.querySelectorAll(".pestana").forEach((p) => p.classList.toggle("oculto", p.id !== "p-" + nombre));
  }
  document.querySelector(".pestanas").addEventListener("click", (e) => {
    const b = e.target.closest("button[data-pestana]"); if (b) activarPestana(b.dataset.pestana);
  });

  function leerHash() {
    const h = new URLSearchParams(location.hash.slice(1));
    const par = (k) => { const v = h.get(k); if (!v) return null; const [a, b] = v.split(",").map(Number); return isNaN(a) || isNaN(b) ? null : L.latLng(a, b); };
    return { o: par("o"), d: par("d"), c: +h.get("c") || null, hr: h.has("h") ? +h.get("h") : null, v: h.get("v"), flota: h.has("flota") };
  }

  // Pantalla de arranque: lista de pasos que se marcan a medida que cargan la malla, el motor, los puntos y la ruta.
  const arranque = (() => {
    const caja = $("#arranque");
    if (!caja) return { ok() {}, mal() {}, cerrar() {} };
    const pasos = [...caja.querySelectorAll("li[data-paso]")];
    let cerrado = false;
    const marcarActivo = () => { const sig = pasos.find((li) => !li.classList.contains("ok") && !li.classList.contains("mal")); pasos.forEach((li) => li.classList.toggle("activo", li === sig)); return sig; };
    const cerrar = () => {
      if (cerrado) return; cerrado = true;
      caja.classList.add("fin");
      setTimeout(() => caja.remove(), 700);
    };
    const revisar = () => { if (!marcarActivo()) setTimeout(cerrar, 420); };
    marcarActivo();
    caja.querySelector("#arranque-saltar").addEventListener("click", cerrar);
    setTimeout(cerrar, 9000);
    return {
      ok(p) { const li = caja.querySelector(`li[data-paso="${p}"]`); if (li) { li.classList.add("ok"); revisar(); } },
      mal(p) { const li = caja.querySelector(`li[data-paso="${p}"]`); if (li) { li.classList.add("mal"); } pasos.forEach((x) => { if (!x.classList.contains("ok")) x.classList.add("mal"); }); marcarActivo(); setTimeout(cerrar, 1600); },
      cerrar,
    };
  })();
  mapa.whenReady(() => {
    const capa = mono[window.GR.temaActual()].getLayers()[0];
    let hecho = false;
    const listo = () => { if (!hecho) { hecho = true; arranque.ok("malla"); } };
    capa.once("load", listo);
    setTimeout(listo, 2500);
  });
  document.addEventListener("gr:rutas", () => arranque.ok("ruta"), { once: true });

  window.GRMapa = { estado, mapa, fijarPunto, calcular, seleccionar, relleno, ponerModo };

  async function iniciar() {
    pintarCriterios();
    cargarCapas();
    const est = $("#estado-motor");
    try {
      const [info, vehiculos] = await Promise.all([json("api/estado"), json("api/vehiculos")]);
      arranque.ok("motor");
      estado.factores = info.factorVelocidadHora;
      estado.vehiculos = vehiculos;
      $("#trafico").innerHTML = info.indiceActividadHora.map((v) => `<i style="height:${15 + v * 85}%"></i>`).join("");
      est.className = "panel__sub ok";
      est.innerHTML = info.estatico
        ? `<span class="punto"></span>Demo estática · rutas precalculadas · ${fmt(info.nodos)} nodos`
        : `<span class="punto"></span>Motor en línea · ${fmt(info.nodos)} nodos · ${fmt(info.kmViales)} km`;
      if (info.estatico) aviso("Demo en GitHub Pages: rutas precalculadas para Puerto → Aeropuerto (todos los vehículos, criterios y horas) y los envíos del simulador.");
      const p = leerHash();
      if (p.v && vehiculos.some((v) => v.id === p.v)) estado.vehiculo = p.v;
      if (p.c) estado.carga = p.c;
      if (p.hr != null) { estado.hora = p.hr; $("#in-hora").value = p.hr; }
      pintarVehiculos(); pintarHora();
      await cargarPuntos();
      arranque.ok("puntos");
      if (p.o && p.d) {
        fijarPunto("origen", p.o, null, false); fijarPunto("destino", p.d, null, false);
        calcular(p.flota ? "flota" : "alternativas");
      } else {
        const puerto = puntosLogisticos.find((x) => x.categoria === "Puerto"), aero = puntosLogisticos.find((x) => x.categoria === "Aeropuerto");
        if (puerto && aero) {
          fijarPunto("origen", L.latLng(puerto.lat, puerto.lon), puerto.nombre, false);
          fijarPunto("destino", L.latLng(aero.lat, aero.lon), aero.nombre, false);
          calcular(location.hash.includes("flota") ? "flota" : "alternativas");
        }
      }
    } catch (e) {
      arranque.mal("motor");
      est.className = "panel__sub mal";
      est.innerHTML = '<span class="punto"></span>Motor fuera de línea · ejecuta greenroute-java\\ejecutar.ps1';
      aviso("No hay conexión con el motor Java. Inícialo con greenroute-java\\ejecutar.ps1 y recarga.", "error");
    }
  }
  iniciar();
})();
