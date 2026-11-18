// Página de modelos: simulador de tráfico por hora (minutos y CO₂ de un recorrido de 10 km).
(function () {
  "use strict";
  const $ = (s) => document.querySelector(s);
  const { fmt, json, contar } = window.GR;

  async function simulador() {
    if (!$("#sim-trafico")) return;
    const g = await json("data/graficas.json");
    const factores = g.m1_factor_velocidad.series[0].valores;
    // Consumo relativo según la velocidad (1 = consumo a 50 km/h)
    const curva = (v) => { const vel = Math.max(v, 5); return (0.6 + 16 / vel + 0.00008 * vel * vel) / (0.6 + 16 / 50 + 0.00008 * 2500); };
    const LITROS_KM = (24 + 0.55 * 5) / 100;
    const datos = factores.map((f) => {
      const v = 50 * f;
      return { v, min: 10 / v * 60, co2: LITROS_KM * curva(v) * 10 * 2.68 };
    });
    const maxMin = Math.max(...datos.map((d) => d.min));
    const mejor = Math.min(...datos.map((d) => d.min));
    $("#st-barras").innerHTML = datos.map((d, h) => `<i data-h="${h}" style="height:${d.min / maxMin * 100}%" title="${h}:00 · ${fmt(d.min, 1)} min"></i>`).join("");
    const elegir = (h) => {
      const d = datos[h];
      $("#st-barras").querySelectorAll("i").forEach((el, i) => el.classList.toggle("sel", i === h));
      $("#st-hora").textContent = String(h).padStart(2, "0") + ":00";
      contar($("#st-vel"), d.v, 0);
      contar($("#st-min"), d.min, 1);
      contar($("#st-co2"), d.co2, 2);
      $("#st-extra").textContent = "+" + fmt((d.min / mejor - 1) * 100, 0) + " %";
    };
    $("#st-barras").addEventListener("mouseover", (e) => { const i = e.target.closest("i"); if (i) elegir(+i.dataset.h); });
    $("#st-barras").addEventListener("click", (e) => { const i = e.target.closest("i"); if (i) elegir(+i.dataset.h); });
    elegir(8);
  }

  simulador().catch(console.warn);
})();
