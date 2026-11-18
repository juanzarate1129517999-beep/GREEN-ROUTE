// Portada (index.html): escena 3D fija con la malla vial real de Barranquilla que cambia de capítulo con el scroll.
// 0 inicio · 1 la malla · 2 el riesgo (accidentes en columnas) · 3 la ruta en vivo · 4 cinco criterios (muros de CO₂) · 5 explorar.
// Usa three.js (r128). Si WebGL o la librería no están disponibles, la portada conserva la red SVG de panel.js.
(function () {
  "use strict";
  const { fmt, json, contar } = window.GR;
  const $ = (s) => document.querySelector(s);
  const contenedor = $("#escena3d"), historia = $("#historia");
  if (!contenedor || !historia || !window.THREE) return;
  const T = window.THREE;

  let renderer;
  try {
    renderer = new T.WebGLRenderer({ antialias: true, alpha: true, powerPreference: "high-performance" });
  } catch (e) { console.warn("WebGL no disponible", e); return; }
  document.documentElement.classList.add("con-3d");

  const reducir = matchMedia("(prefers-reduced-motion: reduce)").matches;
  renderer.setPixelRatio(Math.min(devicePixelRatio || 1, 1.75));
  renderer.setClearColor(0x000000, 0);
  contenedor.prepend(renderer.domElement);

  const escena = new T.Scene();
  const camara = new T.PerspectiveCamera(36, 1, 0.5, 3000);
  const capaEtiquetas = $("#etiquetas3d");
  const caps = [...historia.querySelectorAll(".capitulo")];
  const botonesCap = [...document.querySelectorAll("#capitulos button")];

  // ---------- colores del tema ----------
  const css = (v) => getComputedStyle(document.documentElement).getPropertyValue(v).trim();
  const oscuro = () => window.GR.temaActual() === "dark";
  const pal = {};
  function leerPaleta() {
    pal.ink = new T.Color(css("--ink") || "#fafafa");
    pal.acento = new T.Color(css("--accent") || "#3ee6a0");
    pal.mal = new T.Color(css("--bad") || "#ff6b7d");
    pal.rio = new T.Color(oscuro() ? "#2f9bff" : "#1677d2");
    pal.frio = new T.Color(oscuro() ? "#2f9bff" : "#1677d2");
    pal.medio = new T.Color(oscuro() ? "#ffb547" : "#c27a00");
    pal.mezcla = oscuro() ? T.AdditiveBlending : T.NormalBlending;
  }
  leerPaleta();

  // ---------- proyección: coordenadas del SVG de la red → mundo 3D (1 unidad ≈ 10 px del SVG) ----------
  let P = null;
  const ESC = 0.1;
  const aMundo = (x, y) => [(x - P.ancho / 2) * ESC, (y - P.alto / 2) * ESC];
  const latLonAMundo = (la, lo) => aMundo((lo - P.lon_min) * P.kx * P.escala, (P.lat_max - la) * P.escala);

  // ---------- shaders ----------
  const VS_LINEA = `attribute float aOrden; varying float vOrden;
    void main(){ vOrden = aOrden; gl_Position = projectionMatrix * modelViewMatrix * vec4(position, 1.0); }`;
  const FS_LINEA = `uniform vec3 uColor; uniform float uOpacidad; uniform float uProgreso; varying float vOrden;
    void main(){ float a = uOpacidad * (1.0 - smoothstep(uProgreso - 0.1, uProgreso, vOrden)); if (a < 0.004) discard; gl_FragColor = vec4(uColor, a); }`;

  const VS_PART = `uniform float uAltura; uniform float uTam; uniform float uTiempo; uniform float uPx;
    attribute float aNivel; attribute float aPeso; varying float vT; varying float vBrillo;
    void main(){
      vec3 p = position; p.y = 0.08 + aNivel * 0.48 * uAltura;
      vT = clamp(aPeso / 9.0, 0.0, 1.0);
      vBrillo = 0.72 + 0.28 * sin(uTiempo * 1.6 + position.x * 2.1 + position.z * 1.3 + aNivel);
      vec4 mv = modelViewMatrix * vec4(p, 1.0);
      gl_PointSize = uTam * uPx * (0.7 + 0.5 * vT) * (90.0 / -mv.z);
      gl_Position = projectionMatrix * mv; }`;
  const FS_PART = `uniform vec3 uFrio; uniform vec3 uMedio; uniform vec3 uCalido; uniform float uOpacidad; varying float vT; varying float vBrillo;
    void main(){
      float d = length(gl_PointCoord - 0.5); if (d > 0.5) discard;
      float a = (1.0 - smoothstep(0.05, 0.5, d)) * uOpacidad * vBrillo;
      vec3 c = vT < 0.5 ? mix(uFrio, uMedio, vT * 2.0) : mix(uMedio, uCalido, vT * 2.0 - 1.0);
      gl_FragColor = vec4(c, a); }`;

  const VS_MURO = `attribute float aArriba; varying float vArriba;
    void main(){ vArriba = aArriba; gl_Position = projectionMatrix * modelViewMatrix * vec4(position, 1.0); }`;
  const FS_MURO = `uniform vec3 uColor; uniform float uOpacidad; varying float vArriba;
    void main(){ float a = uOpacidad * (0.04 + 0.96 * pow(vArriba, 1.6)); if (a < 0.004) discard; gl_FragColor = vec4(uColor, a); }`;

  // ---------- objetos ----------
  const mundo = new T.Group();
  escena.add(mundo);
  const capasRed = {};      // tipo → { mesh, mat, base }
  let particulas = null, pilares = null, ruta = null, muros = [], marcasOD = null, vehiculo = null;
  const etiquetas = [];
  const datos = { ruta: null, alternativas: [], centroRuta: new T.Vector3(), centroRiesgo: new T.Vector3() };

  function lineasDesdePath(d, centro) {
    const pos = [], orden = [];
    for (const parte of d.split("M")) {
      if (!parte) continue;
      const pts = parte.split("L").map((p) => p.split(",").map(Number));
      for (let i = 0; i < pts.length - 1; i++) {
        const [x1, z1] = aMundo(pts[i][0], pts[i][1]), [x2, z2] = aMundo(pts[i + 1][0], pts[i + 1][1]);
        pos.push(x1, 0, z1, x2, 0, z2);
        const o = Math.min(1, Math.hypot(x1 - centro[0], z1 - centro[1]) / 75) * 0.9 + Math.random() * 0.1;
        orden.push(o, o);
      }
    }
    const g = new T.BufferGeometry();
    g.setAttribute("position", new T.Float32BufferAttribute(pos, 3));
    g.setAttribute("aOrden", new T.Float32BufferAttribute(orden, 1));
    return g;
  }

  const ESTILO_RED = { troncal: 0.95, primaria: 0.7, secundaria: 0.42, terciaria: 0.2, rio: 0.75 };
  function construirRed(r) {
    const centro = latLonAMundo(10.965, -74.80);
    Object.entries(ESTILO_RED).forEach(([tipo, base]) => {
      if (!r.trazos[tipo]) return;
      const mat = new T.ShaderMaterial({
        vertexShader: VS_LINEA, fragmentShader: FS_LINEA, transparent: true, depthWrite: false, blending: pal.mezcla,
        uniforms: { uColor: { value: (tipo === "rio" ? pal.rio : pal.ink).clone() }, uOpacidad: { value: base }, uProgreso: { value: reducir ? 1.2 : 0 } },
      });
      const mesh = new T.LineSegments(lineasDesdePath(r.trazos[tipo], centro), mat);
      mesh.position.y = tipo === "rio" ? -0.05 : tipo === "troncal" ? 0.04 : 0;
      mesh.renderOrder = 1;
      mundo.add(mesh);
      capasRed[tipo] = { mesh, mat, base };
    });
    // Suelo: rejilla tenue para dar sensación de plano.
    const rejilla = new T.GridHelper(260, 52, pal.ink, pal.ink);
    rejilla.material.transparent = true; rejilla.material.opacity = oscuro() ? 0.045 : 0.06; rejilla.material.depthWrite = false;
    rejilla.position.y = -0.2;
    mundo.add(rejilla);
    capasRed.rejilla = { mesh: rejilla };
  }

  function construirParticulas(heat) {
    const pos = [], nivel = [], peso = [];
    heat.forEach(([la, lo, w]) => {
      const [x, z] = latLonAMundo(la, lo);
      const n = Math.max(1, Math.min(16, Math.round(w)));
      for (let k = 0; k < n; k++) {
        pos.push(x + (Math.random() - 0.5) * 0.25, 0, z + (Math.random() - 0.5) * 0.25);
        nivel.push(k); peso.push(w);
      }
    });
    const g = new T.BufferGeometry();
    g.setAttribute("position", new T.Float32BufferAttribute(pos, 3));
    g.setAttribute("aNivel", new T.Float32BufferAttribute(nivel, 1));
    g.setAttribute("aPeso", new T.Float32BufferAttribute(peso, 1));
    const mat = new T.ShaderMaterial({
      vertexShader: VS_PART, fragmentShader: FS_PART, transparent: true, depthWrite: false, blending: pal.mezcla,
      uniforms: { uAltura: { value: 0 }, uTam: { value: 2.4 }, uTiempo: { value: 0 }, uPx: { value: renderer.getPixelRatio() }, uOpacidad: { value: 0 },
        uFrio: { value: pal.frio.clone() }, uMedio: { value: pal.medio.clone() }, uCalido: { value: pal.mal.clone() } },
    });
    particulas = new T.Points(g, mat);
    particulas.renderOrder = 3;
    mundo.add(particulas);
  }

  function construirPilares(pc) {
    pilares = new T.Group();
    const max = Math.max(...pc.puntos.map((p) => p.indice_severidad));
    const cx = [], cz = [];
    pc.puntos.forEach((p, i) => {
      const [x, z] = latLonAMundo(p.lat, p.lon);
      cx.push(x); cz.push(z);
      const h = 3 + (p.indice_severidad / max) * 15;
      const geo = new T.CylinderGeometry(0.42, 0.42, 1, 18, 1, true); geo.translate(0, 0.5, 0);
      const mat = new T.MeshBasicMaterial({ color: pal.mal, transparent: true, opacity: 0, depthWrite: false, blending: pal.mezcla, side: T.DoubleSide });
      const m = new T.Mesh(geo, mat); m.position.set(x, 0, z); m.userData.h = h; m.scale.y = 0.001;
      const anillo = new T.Mesh(new T.RingGeometry(0.9, 1.25, 32), new T.MeshBasicMaterial({ color: pal.mal, transparent: true, opacity: 0, depthWrite: false, side: T.DoubleSide }));
      anillo.rotation.x = -Math.PI / 2; anillo.position.set(x, 0.06, z);
      pilares.add(m, anillo);
      if (i < 3) etiquetas.push(crearEtiqueta(`#${p.ranking} ${p.sitio_frecuente}<b>${fmt(p.accidentes)}</b>`, "mal", () => new T.Vector3(x, h * estado.pil + 1.5, z), () => estado.pil));
    });
    mundo.add(pilares);
    datos.centroRiesgo.set(cx.reduce((a, b) => a + b, 0) / cx.length, 0, cz.reduce((a, b) => a + b, 0) / cz.length);
    $("#top-riesgo").innerHTML = pc.puntos.slice(0, 3).map((p) => `<li><span>${p.sitio_frecuente}</span><b>${fmt(p.accidentes)} acc.</b></li>`).join("");
  }

  function curvaDesde(geom, y) {
    const pts = geom.map(([la, lo]) => { const [x, z] = latLonAMundo(la, lo); return new T.Vector3(x, y, z); });
    const limpio = pts.filter((p, i) => i === 0 || p.distanceTo(pts[i - 1]) > 0.02);
    const curva = new T.CurvePath();
    for (let i = 0; i < limpio.length - 1; i++) curva.add(new T.LineCurve3(limpio[i], limpio[i + 1]));
    return { curva, pts: limpio };
  }

  function construirRuta(r) {
    const { curva, pts } = curvaDesde(r.geometria, 0.35);
    const seg = Math.min(1400, pts.length * 4);
    const nucleo = new T.Mesh(new T.TubeGeometry(curva, seg, 0.3, 6, false), new T.MeshBasicMaterial({ color: pal.acento, transparent: true, opacity: 0 }));
    const halo = new T.Mesh(new T.TubeGeometry(curva, seg, 0.95, 8, false),
      new T.MeshBasicMaterial({ color: pal.acento, transparent: true, opacity: 0, depthWrite: false, blending: pal.mezcla }));
    ruta = { curva, nucleo, halo, total: nucleo.geometry.index.count, totalHalo: halo.geometry.index.count };
    nucleo.renderOrder = 5; halo.renderOrder = 4;
    mundo.add(halo, nucleo);
    const caja = new T.Box3().setFromPoints(pts);
    caja.getCenter(datos.centroRuta); datos.centroRuta.y = 0;

    // marcas de origen y destino: haz vertical + anillo
    marcasOD = new T.Group();
    [pts[0], pts[pts.length - 1]].forEach((p, i) => {
      const haz = new T.Mesh(new T.CylinderGeometry(0.06, 0.06, 9, 6, 1, true).translate(0, 4.5, 0),
        new T.MeshBasicMaterial({ color: pal.ink, transparent: true, opacity: 0, depthWrite: false }));
      const anillo = new T.Mesh(new T.RingGeometry(0.8, 1.15, 40), new T.MeshBasicMaterial({ color: pal.acento, transparent: true, opacity: 0, side: T.DoubleSide, depthWrite: false }));
      anillo.rotation.x = -Math.PI / 2;
      haz.position.copy(p); anillo.position.set(p.x, 0.1, p.z);
      marcasOD.add(haz, anillo);
      etiquetas.push(crearEtiqueta(i === 0 ? "Puerto" : "Aeropuerto", "od", () => new T.Vector3(p.x, 9.6, p.z), () => estado.ruta));
    });
    mundo.add(marcasOD);

    // vehículo: núcleo + destello
    const lienzo = document.createElement("canvas"); lienzo.width = lienzo.height = 64;
    const cx = lienzo.getContext("2d"), gr = cx.createRadialGradient(32, 32, 0, 32, 32, 32);
    gr.addColorStop(0, "rgba(255,255,255,1)"); gr.addColorStop(0.25, "rgba(255,255,255,.55)"); gr.addColorStop(1, "rgba(255,255,255,0)");
    cx.fillStyle = gr; cx.fillRect(0, 0, 64, 64);
    const destello = new T.Sprite(new T.SpriteMaterial({ map: new T.CanvasTexture(lienzo), color: pal.acento, transparent: true, depthWrite: false, blending: T.AdditiveBlending, opacity: 0 }));
    destello.scale.set(5, 5, 5);
    const nucleoV = new T.Mesh(new T.SphereGeometry(0.55, 16, 12), new T.MeshBasicMaterial({ color: pal.ink, transparent: true, opacity: 0 }));
    vehiculo = new T.Group(); vehiculo.add(destello, nucleoV);
    vehiculo.userData = { destello, nucleoV };
    mundo.add(vehiculo);

    $("#h-ms").textContent = fmt(r.calculoMs, 1);
    datos.cifras = r;
  }

  function construirMuros(alts) {
    const max = Math.max(...alts.map((a) => a.emisionKgCO2), 0.001);
    alts.forEach((a, i) => {
      const { pts } = curvaDesde(a.geometria, 0);
      const H = 1.5 + (a.emisionKgCO2 / max) * 9;
      const pos = [], arriba = [], idx = [];
      pts.forEach((p) => { pos.push(p.x, 0, p.z, p.x, H, p.z); arriba.push(0, 1); });
      for (let k = 0; k < pts.length - 1; k++) { const b = k * 2; idx.push(b, b + 1, b + 2, b + 1, b + 3, b + 2); }
      const g = new T.BufferGeometry();
      g.setAttribute("position", new T.Float32BufferAttribute(pos, 3));
      g.setAttribute("aArriba", new T.Float32BufferAttribute(arriba, 1));
      g.setIndex(idx);
      const color = new T.Color(a.color);
      const mat = new T.ShaderMaterial({ vertexShader: VS_MURO, fragmentShader: FS_MURO, transparent: true, depthWrite: false, side: T.DoubleSide,
        blending: pal.mezcla, uniforms: { uColor: { value: color }, uOpacidad: { value: 0 } } });
      const muro = new T.Mesh(g, mat);
      const borde = new T.Line(new T.BufferGeometry().setFromPoints(pts.map((p) => new T.Vector3(p.x, H, p.z))),
        new T.LineBasicMaterial({ color, transparent: true, opacity: 0, depthWrite: false }));
      const grupo = new T.Group(); grupo.add(muro, borde); grupo.scale.y = 0.001; grupo.renderOrder = 6;
      grupo.userData = { mat, borde, H, desfase: i * 0.12 };
      mundo.add(grupo);
      muros.push(grupo);
      const medio = pts[Math.floor(pts.length * (0.1 + 0.19 * i))] || pts[0];
      etiquetas.push(crearEtiqueta(`${a.criterioEtiqueta.replace("Menor huella de CO2", "Menor CO₂")}<b>${fmt(a.emisionKgCO2, 2)} kg</b>`, "",
        () => new T.Vector3(medio.x, H * grupo.scale.y + 1.2, medio.z), () => estado.muros > 0.6 ? (estado.muros - 0.6) / 0.4 : 0, a.color));
    });
    $("#criterios3d").innerHTML = alts.map((a) => `<li><i style="background:${a.color};color:${a.color}"></i>
      <span>${a.criterioEtiqueta.replace("Menor huella de CO2", "Menor CO₂")}<small>${fmt(a.distanciaKm, 1)} km · ${fmt(a.tiempoMin)} min</small></span>
      <span class="barra"><span style="background:${a.color};--k:${(a.emisionKgCO2 / max).toFixed(3)}"></span></span><b>${fmt(a.emisionKgCO2, 2)} kg</b></li>`).join("");
  }

  function crearEtiqueta(html, tipo, posicion, visibilidad, color) {
    const el = document.createElement("div");
    el.className = "etiqueta3d" + (tipo ? " etiqueta3d--" + tipo : "");
    el.innerHTML = html;
    if (color) { el.style.color = color; el.style.borderColor = color; }
    capaEtiquetas.append(el);
    return { el, posicion, visibilidad };
  }

  // ---------- fotogramas clave por capítulo ----------
  const V = (x, y, z) => new T.Vector3(x, y, z);
  let K = [];
  function fotogramas() {
    const rc = datos.centroRuta, hc = datos.centroRiesgo;
    K = [
      { pos: V(74, 104, 150), obj: V(6, 0, 12), desp: 0.2, red: 0.85, part: 0.35, alt: 0.0, pil: 0, ruta: 1, muros: 0, giro: 1 },
      { pos: V(4, 172, 22), obj: V(0, 0, 0), desp: 0.2, red: 1.25, part: 0.08, alt: 0, pil: 0, ruta: 0, muros: 0, giro: 0.3 },
      { pos: hc.clone().add(V(-84, 66, 88)), obj: hc.clone(), desp: -0.2, red: 0.55, part: 1, alt: 1, pil: 1, ruta: 0, muros: 0, giro: 0.6 },
      { pos: rc.clone().add(V(32, 44, 54)), obj: rc.clone(), desp: 0.2, red: 0.6, part: 0.18, alt: 0, pil: 0, ruta: 1, muros: 0, giro: 0.5 },
      { pos: rc.clone().add(V(-78, 50, 70)), obj: rc.clone().add(V(0, 3, 0)), desp: -0.2, red: 0.5, part: 0.1, alt: 0, pil: 0, ruta: 0.3, muros: 1, giro: 0.4 },
      { pos: V(0, 128, 136), obj: V(0, 0, 4), desp: 0, red: 0.8, part: 0.55, alt: 0.35, pil: 0.35, ruta: 1, muros: 0.35, giro: 2.2 },
    ];
  }
  const estado = { pos: V(74, 104, 150), obj: V(6, 0, 12), desp: 0.2, red: 0.85, part: 0, alt: 0, pil: 0, ruta: 0, muros: 0, giro: 1 };
  const ESCALARES = ["desp", "red", "part", "alt", "pil", "ruta", "muros", "giro"];
  // El objetivo arranca igual al estado: así los fotogramas previos a la carga de datos no producen NaN.
  const objetivo = { pos: estado.pos.clone(), obj: estado.obj.clone() };
  ESCALARES.forEach((k) => { objetivo[k] = estado[k]; });

  // ---------- scroll ----------
  let centros = [];
  function medir() {
    centros = caps.map((el) => { const r = el.getBoundingClientRect(); return r.top + scrollY + r.height / 2; });
  }
  function progreso() {
    const c = scrollY + innerHeight / 2;
    if (!centros.length || c <= centros[0]) return 0;
    for (let i = 0; i < centros.length - 1; i++) if (c < centros[i + 1]) return i + (c - centros[i]) / (centros[i + 1] - centros[i]);
    return centros.length - 1;
  }
  const suave = (t) => t * t * (3 - 2 * t);
  let capActivo = -1, trazo = reducir ? 1 : 0, introRed = reducir ? 1.2 : 0;

  function calcularObjetivo(p) {
    if (!K.length) return;
    const i = Math.min(Math.floor(p), K.length - 1), j = Math.min(i + 1, K.length - 1);
    const f = suave(Math.min(1, Math.max(0, (p - i - 0.12) / 0.76)));
    const a = K[i], b = K[j];
    objetivo.pos.copy(a.pos).lerp(b.pos, f);
    objetivo.obj.copy(a.obj).lerp(b.obj, f);
    ESCALARES.forEach((k) => { objetivo[k] = a[k] + (b[k] - a[k]) * f; });
    const angosto = innerWidth < 900;
    if (angosto) {
      objetivo.desp = 0;
      objetivo.pos.sub(objetivo.obj).multiplyScalar(1.45).add(objetivo.obj);
      // En el móvil el texto va encima de la escena: se atenúa la red mientras hay párrafos delante.
      objetivo.red *= 0.55; objetivo.part *= 0.6;
    }
    const k = Math.round(p);
    if (k !== capActivo) {
      capActivo = k;
      caps.forEach((c, n) => c.classList.toggle("activo", n === k));
      botonesCap.forEach((b, n) => b.classList.toggle("activo", n === k));
      if (k === 3 && !reducir) trazo = 0;
      if (k === 1 && datos.red) { contar($("#h-nodos"), datos.red.nodos); contar($("#h-km"), datos.red.km_totales); }
      if (k === 2 && datos.nHeat) contar($("#h-acc"), datos.nHeat);
      if (k === 3 && datos.cifras) {
        contar($("#h-dist"), datos.cifras.distanciaKm, 1); contar($("#h-co2"), datos.cifras.emisionKgCO2, 2); contar($("#h-nodosexp"), datos.cifras.nodosExplorados);
      }
    }
  }

  botonesCap.forEach((b) => b.addEventListener("click", () => caps[+b.dataset.cap].scrollIntoView({ behavior: reducir ? "auto" : "smooth", block: "center" })));

  // ---------- ratón: ligero paralaje ----------
  const raton = { x: 0, y: 0, sx: 0, sy: 0 };
  addEventListener("pointermove", (e) => { raton.x = e.clientX / innerWidth - 0.5; raton.y = e.clientY / innerHeight - 0.5; }, { passive: true });

  // ---------- tamaño ----------
  let ancho = 0, alto = 0;
  function redimensionar() {
    ancho = innerWidth; alto = innerHeight;
    renderer.setSize(ancho, alto, false);
    camara.aspect = ancho / alto;
    camara.updateProjectionMatrix();
    medir();
  }
  addEventListener("resize", redimensionar);
  redimensionar();
  addEventListener("load", medir);
  if (document.fonts) document.fonts.ready.then(medir);

  // ---------- tema ----------
  document.addEventListener("gr:tema", () => {
    leerPaleta();
    Object.entries(capasRed).forEach(([tipo, c]) => {
      if (tipo === "rejilla") { c.mesh.material.color.copy(pal.ink); c.mesh.material.opacity = oscuro() ? 0.045 : 0.06; return; }
      c.mat.uniforms.uColor.value.copy(tipo === "rio" ? pal.rio : pal.ink); c.mat.blending = pal.mezcla; c.mat.needsUpdate = true;
    });
    if (particulas) {
      const u = particulas.material.uniforms;
      u.uFrio.value.copy(pal.frio); u.uMedio.value.copy(pal.medio); u.uCalido.value.copy(pal.mal);
      particulas.material.blending = pal.mezcla; particulas.material.needsUpdate = true;
    }
    if (pilares) pilares.children.forEach((m) => { m.material.color.copy(pal.mal); if (m.geometry.type === "CylinderGeometry") { m.material.blending = pal.mezcla; m.material.needsUpdate = true; } });
    if (ruta) { ruta.nucleo.material.color.copy(pal.acento); ruta.halo.material.color.copy(pal.acento); ruta.halo.material.blending = pal.mezcla; ruta.halo.material.needsUpdate = true; }
    if (marcasOD) marcasOD.children.forEach((m, i) => m.material.color.copy(i % 2 ? pal.acento : pal.ink));
    if (vehiculo) { vehiculo.userData.nucleoV.material.color.copy(pal.ink); vehiculo.userData.destello.material.color.copy(pal.acento); }
    muros.forEach((g) => { g.userData.mat.blending = pal.mezcla; g.userData.mat.needsUpdate = true; });
  });

  // ---------- bucle ----------
  let visible = true, ultimo = performance.now(), tiempo = 0, enPantalla = true;
  new IntersectionObserver((e) => { enPantalla = e[0].isIntersecting; if (enPantalla) arrancar(); }, { rootMargin: "200px 0px 200px 0px" }).observe(historia);
  document.addEventListener("visibilitychange", () => { visible = !document.hidden; if (visible) arrancar(); });
  let corriendo = false;
  function arrancar() { if (!corriendo) { corriendo = true; ultimo = performance.now(); requestAnimationFrame(cuadro); } }

  const tmp = new T.Vector3();
  function cuadro(ahora) {
    // dtReal sigue al reloj aunque el navegador vaya a pocos fotogramas; dt (acotado) suaviza la cámara.
    const dtReal = Math.max(0, Math.min(0.5, (ahora - ultimo) / 1000)), dt = Math.min(0.05, dtReal);
    ultimo = ahora; tiempo += dtReal;
    const p = progreso();
    calcularObjetivo(p);

    // amortiguación hacia el objetivo
    const k = 1 - Math.exp(-dt * (reducir ? 20 : 3.2));
    estado.pos.lerp(objetivo.pos, k); estado.obj.lerp(objetivo.obj, k);
    ESCALARES.forEach((n) => { estado[n] += (objetivo[n] - estado[n]) * k; });
    raton.sx += (raton.x - raton.sx) * k; raton.sy += (raton.y - raton.sy) * k;

    // cámara: órbita suave alrededor del objetivo + paralaje
    const rel = tmp.copy(estado.pos).sub(estado.obj);
    const ang = reducir ? 0 : (estado.giro > 2 ? tiempo * 0.06 : Math.sin(tiempo * 0.07) * 0.16 * estado.giro);
    rel.applyAxisAngle(V(0, 1, 0), ang + raton.sx * 0.18);
    camara.position.copy(estado.obj).add(rel);
    camara.position.y += -raton.sy * 6;
    camara.lookAt(estado.obj);
    camara.setViewOffset(ancho, alto, -estado.desp * ancho, 0, ancho, alto);

    // malla: crecimiento inicial + realce por capítulo
    if (introRed < 1.2) introRed = Math.min(1.2, introRed + dtReal * 0.42);
    Object.entries(capasRed).forEach(([tipo, c]) => {
      if (!c.mat) return;
      c.mat.uniforms.uProgreso.value = introRed;
      c.mat.uniforms.uOpacidad.value = Math.min(1, c.base * estado.red);
      if (tipo === "troncal") c.mat.uniforms.uColor.value.copy(pal.ink).lerp(pal.acento, Math.max(0, Math.min(1, (estado.red - 0.9) * 2.6)));
    });

    if (particulas) {
      const u = particulas.material.uniforms;
      u.uAltura.value = estado.alt; u.uOpacidad.value = estado.part * Math.min(1, introRed); u.uTiempo.value = tiempo;
    }
    if (pilares) pilares.children.forEach((m) => {
      if (m.userData.h) { m.scale.y = Math.max(0.001, m.userData.h * estado.pil); m.material.opacity = 0.55 * estado.pil; }
      else m.material.opacity = 0.7 * estado.pil * (0.6 + 0.4 * Math.sin(tiempo * 3));
    });

    if (ruta) {
      if (trazo < 1) trazo = Math.min(1, trazo + dtReal / 2.4);
      const f = suave(trazo) * Math.min(1, introRed);
      ruta.nucleo.geometry.setDrawRange(0, Math.floor(ruta.total * f / 3) * 3);
      ruta.halo.geometry.setDrawRange(0, Math.floor(ruta.totalHalo * f / 3) * 3);
      ruta.nucleo.material.opacity = estado.ruta;
      ruta.halo.material.opacity = 0.22 * estado.ruta;
      marcasOD.children.forEach((m, i) => { m.material.opacity = estado.ruta * (i % 2 ? 0.9 : 0.6); });
      const vis = trazo >= 1 ? estado.ruta : 0;
      const t = reducir ? 0.5 : (tiempo * 0.055) % 1;
      vehiculo.position.copy(ruta.curva.getPointAt(t)); vehiculo.position.y = 0.6;
      vehiculo.userData.nucleoV.material.opacity = vis;
      vehiculo.userData.destello.material.opacity = vis * (0.75 + 0.25 * Math.sin(tiempo * 6));
    }

    muros.forEach((g) => {
      const s = Math.max(0, Math.min(1, (estado.muros - g.userData.desfase * 0.5) / (1 - g.userData.desfase * 0.5)));
      g.scale.y = Math.max(0.001, suave(s));
      g.userData.mat.uniforms.uOpacidad.value = (oscuro() ? 0.3 : 0.42) * s;
      g.userData.borde.material.opacity = 0.95 * s;
    });

    // etiquetas HTML proyectadas
    camara.updateMatrixWorld();
    etiquetas.forEach((e) => {
      const v = e.visibilidad();
      if (v < 0.02) { e.el.style.opacity = 0; return; }
      const q = e.posicion().project(camara);
      if (q.z > 1) { e.el.style.opacity = 0; return; }
      e.el.style.transform = `translate(${((q.x + 1) / 2 * ancho).toFixed(1)}px, ${((1 - q.y) / 2 * alto - 14).toFixed(1)}px) translate(-50%, -100%)`;
      e.el.style.opacity = Math.min(1, v).toFixed(2);
    });

    // la escena se desvanece al salir del recorrido
    const fin = historia.getBoundingClientRect().bottom;
    const op = Math.max(0, Math.min(1, fin / (innerHeight * 0.7)));
    contenedor.style.opacity = document.documentElement.classList.contains("con-3d") ? op.toFixed(3) : "";
    document.getElementById("capitulos").classList.toggle("fuera", op < 0.3);

    renderer.render(escena, camara);
    if (visible && (enPantalla || op > 0)) requestAnimationFrame(cuadro); else corriendo = false;
  }

  // ---------- datos ----------
  (async () => {
    try {
      const [red, heat, pc, res] = await Promise.all([
        json("data/red_svg.json"), json("data/accidentes_heat.json"), json("data/puntos_criticos.json"), json("data/red_vial_resumen.json")]);
      P = red;
      datos.red = res; datos.nHeat = heat.length;
      construirRed(red); construirParticulas(heat); construirPilares(pc);
      fotogramas(); medir(); arrancar();
    } catch (e) {
      console.warn("Escena 3D sin datos", e);
      document.documentElement.classList.remove("con-3d");
      return;
    }
    try {
      const puntos = await json("api/puntos");
      const o = puntos.find((x) => x.categoria === "Puerto"), d = puntos.find((x) => x.categoria === "Aeropuerto");
      if (!o || !d) return;
      const q = `olat=${o.lat}&olon=${o.lon}&dlat=${d.lat}&dlon=${d.lon}&hora=7`;
      const [r, alts] = await Promise.all([
        json(`api/ruta?${q}&vehiculo=VAN-E&criterio=EMISIONES&carga=900`),
        json(`api/alternativas?${q}&vehiculo=CAM-D&criterio=EMISIONES&carga=6000`),
      ]);
      construirRuta(r); construirMuros(alts);
      if (window.GR.modoEstatico()) $("#h-vivo").textContent = "En esta versión publicada la ruta viene precalculada por el motor; con el motor Java en marcha se calcula en vivo.";
      fotogramas();
      trazo = reducir ? 1 : 0;
    } catch (e) {
      console.warn("Motor Java fuera de línea: la escena 3D se muestra sin ruta", e);
      $("#h-ms").textContent = "—";
      $("#criterios3d").innerHTML = '<li class="vacio">El motor Java no está en línea. Ejecuta <code>greenroute-java\\ejecutar.ps1</code> y recarga.</li>';
    }
  })();
})();
