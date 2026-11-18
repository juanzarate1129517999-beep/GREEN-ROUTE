// Código compartido por todas las páginas: menú, tema claro/oscuro, paleta de comandos (Ctrl+K),
// pie de página, animaciones y utilidades (window.GR: fmt, json, contar, rellenar, aviso, temaActual).
(function () {
  "use strict";

  // ---------- Demo estática (GitHub Pages) ----------
  // Si no hay motor Java (la ruta api/ no devuelve JSON), las llamadas al API se responden con data/api/<endpoint>.json,
  // generados por analisis/08_api_estatica.py. La clave debe construirse igual que en ese script.
  const IGNORAR_API = { alternativas: ["criterio"], comparar: ["vehiculo"] };
  const numApi = (v) => (/^-?\d+(\.\d+)?$/.test(v) ? (+v).toFixed(5).replace(/\.?0+$/, "") : v);
  const claveApi = (endpoint, qs) => {
    const ign = IGNORAR_API[endpoint] || [];
    return endpoint + "?" + [...new URLSearchParams(qs)].filter(([k]) => !ign.includes(k)).map(([k, v]) => [k, numApi(v)])
      .sort((a, b) => (a[0] < b[0] ? -1 : a[0] > b[0] ? 1 : 0)).map(([k, v]) => `${k}=${v}`).join("&");
  };
  const MSG_ESTATICO = "Demo estática (GitHub Pages): esta combinación no está precalculada. Prueba el envío Puerto → Aeropuerto, " +
    "los envíos del simulador o cualquier hora de salida con 900 kg; para cualquier otra ruta ejecuta el motor Java.";
  const MSG_DESPERTANDO = "El motor Java en Render se está encendiendo (puede tardar hasta un minuto). Intenta de nuevo en unos segundos.";

  // ---------- Motor Java en Render ----------
  // GitHub Pages no ejecuta Java: desde github.io las consultas al API se envían al motor publicado en Render.
  // Si Render no responde, el sitio sigue funcionando con la demo estática.
  const MOTOR_REMOTO = "https://green-route-7atf.onrender.com";
  const usarRemoto = !!MOTOR_REMOTO && location.hostname.endsWith("github.io");
  const ESPERA_MOTOR_MS = 12000;   // el plan gratuito de Render se duerme y tarda en despertar
  const esperar = (ms) => new Promise((ok) => setTimeout(ok, ms));
  const fetchOriginal = window.fetch.bind(window);
  // Despierta el motor apenas se abre la página para que la primera consulta no espere.
  if (usarRemoto) fetchOriginal(MOTOR_REMOTO + "/api/estado").catch(() => {});
  const JSON_H = { "Content-Type": "application/json; charset=utf-8" };
  const tablasApi = {}, tablasListas = {};
  const memoria = new Map();   // respuestas del motor ya recibidas en esta visita
  let estatico = null;
  function cargarTabla(endpoint) {
    if (!tablasApi[endpoint]) {
      tablasApi[endpoint] = fetchOriginal(new URL(`data/api/${endpoint}.json`, document.baseURI))
        .then((r) => (r.ok ? r.json() : {})).catch(() => ({})).then((t) => (tablasListas[endpoint] = t));
    }
    return tablasApi[endpoint];
  }
  const responder = (e) => new Response(JSON.stringify(e.d), { status: e.s, headers: JSON_H });
  // Orden: 1) respuesta ya conocida (memoria o tabla precalculada ya descargada) → instantánea;
  // 2) motor Java en vivo; 3) sin motor (GitHub Pages) → tabla precalculada o aviso.
  window.fetch = async (recurso, opciones) => {
    const texto = typeof recurso === "string" ? recurso : recurso && recurso.url;
    const url = texto ? new URL(texto, location.href) : null;
    if (!url || url.origin !== location.origin || !url.pathname.includes("/api/")) return fetchOriginal(recurso, opciones);
    const senal = opciones && opciones.signal;
    const endpoint = url.pathname.split("/api/")[1], k = claveApi(endpoint, url.search);
    const conocida = memoria.get(k) || (tablasListas[endpoint] && tablasListas[endpoint][k]);
    if (conocida) return responder(conocida);
    let despertando = false;
    if (estatico !== true) {
      const destino = usarRemoto ? `${MOTOR_REMOTO}/api/${endpoint}${url.search}` : recurso;
      try {
        // null = la respuesta no es del motor (no es JSON); undefined = Render todavía no contesta.
        const peticion = fetchOriginal(destino, opciones).then((r) => {
          if (!(r.headers.get("content-type") || "").includes("json")) return null;
          if (r.ok) r.clone().json().then((d) => memoria.set(k, { s: r.status, d })).catch(() => {});
          return r;
        });
        peticion.catch(() => {});
        const r = usarRemoto ? await Promise.race([peticion, esperar(ESPERA_MOTOR_MS)]) : await peticion;
        if (r) { estatico = false; return r; }
        if (r === null) estatico = true; else despertando = true;
      } catch (e) {
        if (e.name === "AbortError") throw e;
        estatico = true;
      }
    }
    const e = (await cargarTabla(endpoint))[k];
    if (senal && senal.aborted) throw new DOMException("Consulta cancelada", "AbortError");
    if (e) return responder(e);
    return new Response(JSON.stringify({ mensaje: despertando ? MSG_DESPERTANDO : MSG_ESTATICO }), { status: 404, headers: JSON_H });
  };
  // En el mapa publicado en GitHub Pages, las rutas precalculadas se descargan en segundo plano para responder al instante.
  addEventListener("load", () => {
    if (!usarRemoto || !document.body.classList.contains("pagina-mapa")) return;
    const luego = window.requestIdleCallback || ((f) => setTimeout(f, 1200));
    luego(() => ["alternativas", "ruta", "comparar"].forEach(cargarTabla), { timeout: 4000 });
  });
  const modoEstatico = () => estatico === true;

  let tema = "dark";
  try { tema = localStorage.getItem("gr-tema") || "dark"; } catch (e) { /* sin almacenamiento local */ }
  document.documentElement.setAttribute("data-theme", tema);
  const temaActual = () => document.documentElement.getAttribute("data-theme") || "dark";

  const I = (d) => `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">${d}</svg>`;
  const IC = {
    sol: I('<circle cx="12" cy="12" r="4"/><path d="M12 2v2M12 20v2M4.9 4.9l1.4 1.4M17.7 17.7l1.4 1.4M2 12h2M20 12h2M4.9 19.1l1.4-1.4M17.7 6.3l1.4-1.4"/>'),
    luna: I('<path d="M21 12.8A9 9 0 1 1 11.2 3a7 7 0 0 0 9.8 9.8z"/>'),
    buscar: I('<circle cx="11" cy="11" r="7"/><path d="M20 20l-3.5-3.5"/>'),
    menu: I('<path d="M4 8h16M4 16h16"/>'),
    pagina: I('<path d="M6 3h9l4 4v14H6z"/><path d="M14 3v5h5"/>'),
    seccion: I('<path d="M5 12h14M13 6l6 6-6 6"/>'),
    accion: I('<path d="M13 2L4 14h7l-1 8 9-12h-7z"/>'),
  };
  const LOGO = `<svg viewBox="0 0 32 32" aria-hidden="true"><circle cx="16" cy="16" r="15" fill="currentColor"/>
    <path d="M9.5 21.5c2.6-7.2 6.5-10.2 13-11-1 6.4-4 10-11 11.5" fill="none" stroke="var(--bg)" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"/>
    <circle cx="9.6" cy="22" r="1.9" fill="var(--accent)"/></svg>`;
  const PAGINAS = [
    ["index.html", "Panel"], ["estudio.html", "Estudio de datos"], ["modelos.html", "Modelos ML"],
    ["mapa.html", "Mapa de rutas"], ["arquitectura.html", "Arquitectura POO"], ["codigo.html", "Código Java"],
  ];

  function construirNav() {
    const actual = location.pathname.split("/").pop() || "index.html";
    const nav = document.createElement("header");
    nav.className = "nav";
    nav.innerHTML = `
      <a class="marca" href="index.html" style="color:var(--ink)">${LOGO}<span>Green Route</span></a>
      <ul class="nav__links"><span class="nav__indicador"></span>${PAGINAS.map(([h, t]) =>
        `<li><a href="${h}" ${h === actual ? 'aria-current="page"' : ""}>${t}</a></li>`).join("")}</ul>
      <button class="nav__accion" id="btn-paleta" aria-label="Abrir paleta de comandos">${IC.buscar}<span class="txt">Buscar</span><kbd>Ctrl K</kbd></button>
      <button class="nav__accion" id="btn-tema" aria-label="Cambiar entre negro y blanco"></button>
      <button class="nav__accion nav__menu" id="btn-menu" aria-label="Abrir menú">${IC.menu}</button>`;
    document.body.prepend(nav);
    const progreso = document.createElement("div");
    progreso.className = "progreso";
    document.body.prepend(progreso);
    addEventListener("scroll", () => {
      const max = document.documentElement.scrollHeight - innerHeight;
      progreso.style.width = (max > 0 ? scrollY / max * 100 : 0) + "%";
    }, { passive: true });

    const lista = nav.querySelector(".nav__links"), ind = nav.querySelector(".nav__indicador");
    const mover = (a) => { if (!a) { ind.style.width = "0"; return; } ind.style.left = a.offsetLeft + "px"; ind.style.width = a.offsetWidth + "px"; };
    const activo = () => lista.querySelector('[aria-current="page"]');
    requestAnimationFrame(() => mover(activo()));
    document.fonts && document.fonts.ready.then(() => mover(activo()));
    lista.querySelectorAll("a").forEach((a) => a.addEventListener("mouseenter", () => mover(a)));
    lista.addEventListener("mouseleave", () => mover(activo()));
    nav.querySelector("#btn-menu").addEventListener("click", () => lista.classList.toggle("abierto"));

    const btnTema = nav.querySelector("#btn-tema");
    const pintar = () => { btnTema.innerHTML = temaActual() === "dark" ? IC.sol : IC.luna; };
    pintar();
    btnTema.addEventListener("click", (e) => cambiarTema(e.clientX, e.clientY, pintar));
    nav.querySelector("#btn-paleta").addEventListener("click", abrirPaleta);
  }

  function cambiarTema(x, y, alTerminar) {
    const nuevo = temaActual() === "dark" ? "light" : "dark";
    const aplicar = () => {
      document.documentElement.setAttribute("data-theme", nuevo);
      try { localStorage.setItem("gr-tema", nuevo); } catch (e) { /* sin almacenamiento local */ }
      if (alTerminar) alTerminar();
      document.dispatchEvent(new CustomEvent("gr:tema", { detail: nuevo }));
    };
    const reducir = matchMedia("(prefers-reduced-motion: reduce)").matches;
    if (!document.startViewTransition || reducir) { aplicar(); return; }
    x = x ?? innerWidth - 60; y = y ?? 40;
    const r = Math.hypot(Math.max(x, innerWidth - x), Math.max(y, innerHeight - y));
    document.documentElement.classList.add("vt-tema");
    const t = document.startViewTransition(aplicar);
    t.finished.finally(() => document.documentElement.classList.remove("vt-tema"));
    t.ready.then(() => document.documentElement.animate(
      { clipPath: [`circle(0px at ${x}px ${y}px)`, `circle(${r}px at ${x}px ${y}px)`] },
      { duration: 650, easing: "cubic-bezier(.6,0,.2,1)", pseudoElement: "::view-transition-new(root)" }));
  }

  let paleta, entradaPaleta, listaPaleta, comandos = [], filtrados = [], sel = 0;
  function construirPaleta() {
    paleta = document.createElement("div");
    paleta.className = "paleta-fondo";
    paleta.innerHTML = `<div class="paleta" role="dialog" aria-label="Paleta de comandos">
        <input placeholder="Ir a una página, sección o acción…" aria-label="Buscar comando">
        <ul role="listbox"></ul>
        <div class="paleta__pie"><span>↑↓ navegar</span><span>↵ abrir</span><span>Esc cerrar</span></div></div>`;
    document.body.append(paleta);
    entradaPaleta = paleta.querySelector("input");
    listaPaleta = paleta.querySelector("ul");
    comandos = [
      ...PAGINAS.map(([h, t]) => ({ t, tipo: "Página", ic: IC.pagina, ir: () => (location.href = h) })),
      ...[...document.querySelectorAll(".indice-pagina a")].map((a) => ({ t: a.textContent, tipo: "Sección", ic: IC.seccion, ir: () => document.querySelector(a.getAttribute("href"))?.scrollIntoView({ behavior: "smooth" }) })),
      { t: "Cambiar a modo " + (temaActual() === "dark" ? "blanco" : "negro"), tipo: "Acción", ic: IC.accion, ir: () => document.getElementById("btn-tema").click() },
      { t: "Ruta Puerto → Aeropuerto en el mapa", tipo: "Acción", ic: IC.accion, ir: () => (location.href = "mapa.html#ejemplo=puerto-aeropuerto") },
      { t: "Comparar toda la flota en el mapa", tipo: "Acción", ic: IC.accion, ir: () => (location.href = "mapa.html#flota") },
      { t: "Abrir el mapa en 3D", tipo: "Acción", ic: IC.accion, ir: () => (location.href = "mapa.html#3d") },
      { t: "Descargar figura PNG del informe (estudio)", tipo: "Acción", ic: IC.accion, ir: () => (location.href = "estudio.html#accidentalidad") },
    ];
    entradaPaleta.addEventListener("input", filtrar);
    entradaPaleta.addEventListener("keydown", (e) => {
      if (e.key === "ArrowDown") { sel = Math.min(sel + 1, filtrados.length - 1); pintarPaleta(); e.preventDefault(); }
      else if (e.key === "ArrowUp") { sel = Math.max(sel - 1, 0); pintarPaleta(); e.preventDefault(); }
      else if (e.key === "Enter" && filtrados[sel]) { cerrarPaleta(); filtrados[sel].ir(); }
    });
    paleta.addEventListener("click", (e) => {
      if (e.target === paleta) cerrarPaleta();
      const li = e.target.closest("li[data-i]");
      if (li) { cerrarPaleta(); filtrados[+li.dataset.i].ir(); }
    });
    document.addEventListener("keydown", (e) => {
      if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === "k") { e.preventDefault(); paleta.classList.contains("abierta") ? cerrarPaleta() : abrirPaleta(); }
      else if (e.key === "Escape" && paleta.classList.contains("abierta")) cerrarPaleta();
      else if (e.key === "/" && !/input|textarea|select/i.test(document.activeElement.tagName)) { e.preventDefault(); abrirPaleta(); }
    });
  }
  function filtrar() {
    const q = entradaPaleta.value.trim().toLowerCase();
    filtrados = comandos.filter((c) => !q || c.t.toLowerCase().includes(q) || c.tipo.toLowerCase().includes(q));
    sel = 0; pintarPaleta();
  }
  function pintarPaleta() {
    listaPaleta.innerHTML = filtrados.length ? filtrados.map((c, i) =>
      `<li role="option" data-i="${i}" aria-selected="${i === sel}">${c.ic}<span>${c.t}</span><small>${c.tipo}</small></li>`).join("")
      : '<li aria-disabled="true">Sin resultados</li>';
    const s = listaPaleta.querySelector('[aria-selected="true"]'); if (s) s.scrollIntoView({ block: "nearest" });
  }
  function abrirPaleta() { paleta.classList.add("abierta"); entradaPaleta.value = ""; filtrar(); setTimeout(() => entradaPaleta.focus(), 20); }
  function cerrarPaleta() { paleta.classList.remove("abierta"); }

  function focoDeLuz() {
    document.addEventListener("pointermove", (e) => {
      const el = e.target.closest(".tarjeta, .kpi, .grafica-tarjeta, .lectura, .paso, .bento > *");
      if (!el) return;
      const r = el.getBoundingClientRect();
      el.style.setProperty("--mx", (e.clientX - r.left) + "px");
      el.style.setProperty("--my", (e.clientY - r.top) + "px");
    }, { passive: true });
  }

  function revelar() {
    const els = document.querySelectorAll(".tarjeta, .kpis, .grafica-tarjeta, .tabla-env, .lectura, .proceso, .nota, .bento > *");
    const obs = new IntersectionObserver((ents) => ents.forEach((e) => {
      if (e.isIntersecting) { e.target.classList.add("visible"); obs.unobserve(e.target); }
    }), { rootMargin: "0px 0px -30px 0px" });
    els.forEach((el, i) => { if (!el.closest(".app")) { el.classList.add("revelar"); el.style.transitionDelay = (i % 4) * 60 + "ms"; obs.observe(el); } });
  }

  // Transiciones entre páginas: View Transitions nativas (Chrome/Edge) o un fundido de respaldo (Firefox/Safari).
  function transiciones() {
    const raiz = document.documentElement;
    const reducir = matchMedia("(prefers-reduced-motion: reduce)").matches;
    if (!reducir) {
      const e = document.createElement("div");
      e.className = "gr-escaneo"; e.setAttribute("aria-hidden", "true");
      document.body.append(e);
      e.addEventListener("animationend", () => e.remove());
    }
    if (HTMLScriptElement.supports && HTMLScriptElement.supports("speculationrules")) {
      const sr = document.createElement("script");
      sr.type = "speculationrules";
      sr.textContent = JSON.stringify({ prefetch: [{ where: { href_matches: location.pathname.replace(/[^/]*$/, "") + "*.html" }, eagerness: "moderate" }] });
      document.head.append(sr);
    }
    const nativa = "onpagereveal" in window && !!document.startViewTransition;
    if (nativa || reducir) return;
    raiz.classList.add("gr-entrando");
    setTimeout(() => raiz.classList.remove("gr-entrando"), 950);
    document.addEventListener("click", (e) => {
      const a = e.target.closest("a[href]");
      if (!a || e.defaultPrevented || e.button !== 0 || e.metaKey || e.ctrlKey || e.shiftKey || e.altKey || a.target === "_blank" || a.hasAttribute("download")) return;
      const url = new URL(a.href, location.href);
      if (url.origin !== location.origin || url.pathname === location.pathname || !/(\.html|\/)$/.test(url.pathname)) return;
      e.preventDefault();
      raiz.classList.add("gr-saliendo");
      setTimeout(() => { location.href = url.href; }, 360);
    });
    addEventListener("pageshow", (e) => { if (e.persisted) raiz.classList.remove("gr-saliendo"); });
  }

  // Divide los títulos en palabras para que suban una a una al entrar en pantalla.
  function dividirTitulos() {
    const titulos = [...document.querySelectorAll("main h1, main h2")].filter((h) =>
      !h.closest(".app, .heroe, [data-sin-dividir]") && [...h.childNodes].every((n) => n.nodeType === 3));
    if (!titulos.length || matchMedia("(prefers-reduced-motion: reduce)").matches) return;
    const obs = new IntersectionObserver((ents) => ents.forEach((e) => {
      if (e.isIntersecting) { e.target.classList.add("visible"); obs.unobserve(e.target); }
    }), { rootMargin: "0px 0px -8% 0px" });
    titulos.forEach((h) => {
      const palabras = h.textContent.trim().split(/\s+/);
      h.setAttribute("aria-label", h.textContent.trim());
      h.innerHTML = palabras.map((w, i) => `<span class="palabra" aria-hidden="true"><span style="--i:${i}">${w}</span></span>`).join(" ");
      h.classList.add("titulo-dividido");
      obs.observe(h);
    });
  }

  function indiceActivo() {
    const enlaces = [...document.querySelectorAll(".indice-pagina a")];
    if (!enlaces.length) return;
    const obs = new IntersectionObserver((entradas) => entradas.forEach((e) => {
      if (!e.isIntersecting) return;
      enlaces.forEach((a) => {
        const on = a.getAttribute("href") === "#" + e.target.id;
        a.classList.toggle("activo", on);
        if (on) a.parentElement.scrollTo({ left: a.offsetLeft - 40, behavior: "smooth" });
      });
    }), { rootMargin: "-30% 0px -60% 0px" });
    enlaces.forEach((a) => { const s = document.querySelector(a.getAttribute("href")); if (s) obs.observe(s); });
  }

  function construirPie() {
    if (document.body.dataset.sinPie !== undefined) return;
    const pie = document.createElement("footer");
    pie.className = "pie";
    pie.innerHTML = `<div class="contenedor"><div class="pie__grande" aria-hidden="true">Green Route</div><div class="pie__in">
        <div>Ingeniería de Datos e IA · Proyecto integrador T04 · Prof. Fabio Enrique Mendoza Palechor</div>
        <div>Datos: <a href="https://www.datos.gov.co" target="_blank" rel="noopener">datos.gov.co</a> ·
        <a href="https://www.openstreetmap.org/copyright" target="_blank" rel="noopener">© OpenStreetMap</a> ·
        <a href="https://open-meteo.com" target="_blank" rel="noopener">Open-Meteo</a></div></div></div>`;
    document.body.append(pie);
  }

  const fmt = (v, dec = 0) => (v == null || isNaN(v)) ? "—"
    : Number(v).toLocaleString("es-CO", { minimumFractionDigits: dec, maximumFractionDigits: dec });

  function contar(el, destino, dec = 0, suf = "") {
    if (destino == null || isNaN(destino)) { el.textContent = "—"; return; }
    const inicio = parseFloat(el.dataset.valorActual || "0") || 0;
    el.dataset.valorActual = destino;
    if (matchMedia("(prefers-reduced-motion: reduce)").matches) { el.textContent = fmt(destino, dec) + suf; return; }
    const t0 = performance.now(), dur = 1000;
    const paso = (t) => {
      const k = Math.min(1, (t - t0) / dur), e = 1 - Math.pow(1 - k, 4);
      el.textContent = fmt(inicio + (destino - inicio) * e, dec) + suf;
      if (k < 1) requestAnimationFrame(paso);
    };
    requestAnimationFrame(paso);
  }

  async function json(url) {
    const r = await fetch(url);
    if (!r.ok) throw new Error(`${url}: ${r.status}`);
    return r.json();
  }

  function rellenar(obj, raiz = document) {
    raiz.querySelectorAll("[data-dato]").forEach((el) => {
      if (el.dataset.src) return;
      const v = el.dataset.dato.split(".").reduce((o, k) => (o == null ? o : o[k]), obj);
      if (v === undefined) return;
      if (typeof v === "number") contar(el, v, +(el.dataset.dec || 0), el.dataset.suf || "");
      else el.textContent = v;
    });
  }

  let tAviso;
  function aviso(msg, tipo = "") {
    let el = document.querySelector(".aviso-flotante");
    if (!el) { el = document.createElement("div"); el.className = "aviso-flotante"; el.setAttribute("role", "status"); document.body.append(el); }
    el.className = "aviso-flotante " + tipo;
    el.textContent = msg;
    requestAnimationFrame(() => el.classList.add("ver"));
    clearTimeout(tAviso);
    tAviso = setTimeout(() => el.classList.remove("ver"), 4500);
  }

  window.GR = { fmt, json, rellenar, contar, aviso, temaActual, modoEstatico };
  document.addEventListener("DOMContentLoaded", () => {
    construirNav(); construirPaleta(); construirPie(); focoDeLuz(); dividirTitulos(); revelar(); indiceActivo(); transiciones();
  });
})();
