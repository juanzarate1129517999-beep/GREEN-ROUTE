// Página codigo.html: explorador del código Java con resaltado y marcas de conceptos POO (data/codigo_java.json).
(function () {
  "use strict";

  const CONCEPTOS = ["Todos", "Herencia", "Polimorfismo", "Interfaces", "Abstracción", "Encapsulamiento", "Excepciones", "JOptionPane", "Swing"];
  const TOKENS = new RegExp([
    "(\\/\\/[^\\n]*|\\/\\*[\\s\\S]*?\\*\\/)",
    "(\"(?:\\\\.|[^\"\\\\])*\")",
    "(\\b(?:extends|implements|abstract|interface|super|JOptionPane|instanceof)\\b|@Override)",
    "(\\b(?:public|private|protected|static|final|class|enum|record|return|new|if|else|for|while|switch|case|default|try|catch|throw|throws|this|void|boolean|int|double|long|float|char|var|import|package|null|true|false|do|break|continue)\\b)",
    "(\\b\\d[\\d_.]*\\b)",
  ].join("|"), "g");

  const $ = (s) => document.querySelector(s);
  let datos = null, actual = null, concepto = "Todos";

  const esc = (s) => s.replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;");

  function resaltar(codigo) {
    let html = "", ultimo = 0;
    codigo.replace(TOKENS, (m, c, s, p, k, n, idx) => {
      html += esc(codigo.slice(ultimo, idx));
      const cls = c ? "tk-c" : s ? "tk-s" : p ? "tk-p" : k ? "tk-k" : "tk-n";
      html += m.split("\n").map((parte) => `<span class="${cls}">${esc(parte)}</span>`).join("\n");
      ultimo = idx + m.length;
      return m;
    });
    html += esc(codigo.slice(ultimo));
    return html.split("\n");
  }

  function arbol() {
    $("#arbol").innerHTML = datos.paquetes.map((p) => {
      const archivos = datos.archivos.filter((a) => a.paquete === p.paquete);
      return `<div class="ide__paquete"><span>${p.paquete.replace("co.edu.greenroute", "") || "/"}</span><small>${p.descripcion}</small></div>`
        + archivos.map((a) => `<button class="ide__archivo" data-ruta="${a.ruta}">${a.clase}.java${a.abstracta ? " <i>abstract</i>" : a.tipo !== "class" ? ` <i>${a.tipo}</i>` : ""}</button>`).join("");
    }).join("");
    $("#arbol").addEventListener("click", (e) => {
      const b = e.target.closest(".ide__archivo");
      if (b) abrir(b.dataset.ruta);
    });
  }

  function filtros() {
    $("#filtros").innerHTML = CONCEPTOS.map((c) => `<button type="button" aria-pressed="${c === concepto}" data-c="${c}">${c}</button>`).join("");
    $("#filtros").addEventListener("click", (e) => {
      const b = e.target.closest("button");
      if (!b) return;
      concepto = b.dataset.c;
      $("#filtros").querySelectorAll("button").forEach((x) => x.setAttribute("aria-pressed", x === b));
      document.querySelectorAll(".ide__archivo").forEach((el) => {
        const a = datos.archivos.find((x) => x.ruta === el.dataset.ruta);
        el.classList.toggle("tenue", concepto !== "Todos" && !a.conceptos[concepto]);
      });
      if (concepto !== "Todos" && !actual.conceptos[concepto]) {
        const primero = datos.archivos.find((a) => a.conceptos[concepto]);
        if (primero) return abrir(primero.ruta);
      }
      pintar();
    });
  }

  function abrir(ruta) {
    actual = datos.archivos.find((a) => a.ruta === ruta);
    document.querySelectorAll(".ide__archivo").forEach((el) => el.setAttribute("aria-current", el.dataset.ruta === ruta));
    history.replaceState(null, "", "#" + ruta);
    pintar();
    $("#codigo").scrollTop = 0;
  }

  function pintar() {
    const a = actual;
    $("#ruta").textContent = `src/${a.ruta} · ${a.lineas} líneas`;
    const etiquetas = Object.entries(a.conceptos).map(([k, v]) => `<span class="etq"><b>${k}</b> · ${v.length}</span>`).join("");
    $("#decl").innerHTML = `<span>${esc(a.declaracion || a.clase)}</span>${etiquetas}`;
    const marcadas = new Set(concepto === "Todos" ? [] : (a.conceptos[concepto] || []));
    $("#codigo").innerHTML = resaltar(a.codigo).map((l, i) =>
      `<span class="l${marcadas.has(i + 1) ? " marcada" : ""}">${l || " "}</span>`).join("");
    const primera = $("#codigo .marcada");
    if (primera) primera.scrollIntoView({ block: "center", behavior: "smooth" });
  }

  function galeria() {
    const sufijo = GR.temaActual() === "dark" ? "" : "_claro";
    document.querySelectorAll("#galeria img").forEach((img) => { img.src = `img/escritorio/${img.dataset.base}${sufijo}.png`; });
  }

  function visor() {
    const v = $("#visor");
    document.querySelectorAll(".galeria img, .dialogos img").forEach((img) => img.addEventListener("click", () => {
      v.querySelector("img").src = img.src;
      v.classList.add("abierto");
    }));
    v.addEventListener("click", () => v.classList.remove("abierto"));
    document.addEventListener("keydown", (e) => { if (e.key === "Escape") v.classList.remove("abierto"); });
  }

  async function iniciar() {
    galeria();
    document.addEventListener("gr:tema", galeria);
    visor();
    datos = await GR.json("data/codigo_java.json");
    const herencia = datos.archivos.filter((a) => a.conceptos.Herencia).length;
    const joption = datos.archivos.reduce((s, a) => s + (a.conceptos.JOptionPane || []).length, 0);
    GR.contar($("#k-archivos"), datos.total_archivos);
    GR.contar($("#k-lineas"), datos.total_lineas);
    GR.contar($("#k-herencia"), herencia);
    GR.contar($("#k-joption"), joption);
    arbol();
    filtros();
    const inicial = decodeURIComponent(location.hash.slice(1));
    abrir(datos.archivos.some((a) => a.ruta === inicial) ? inicial : "co/edu/greenroute/vehiculos/Vehiculo.java");
  }
  iniciar();
})();
