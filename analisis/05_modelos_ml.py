"""Paso 5: modelos de aprendizaje automático.

M1 · Regresión: concentración de CO por hora según clima y calendario. De aquí sale el perfil horario
     de tráfico que usa el motor de rutas Java (perfil_horario.csv).
M2 · Clasificación: probabilidad de que un accidente tenga víctimas.
M3 · Series de tiempo: pronóstico mensual de accidentes con víctimas.
Los resultados van a modelos.json y las gráficas a graficas.json.
"""
import json

import numpy as np
import pandas as pd
from sklearn.compose import ColumnTransformer
from sklearn.ensemble import (HistGradientBoostingClassifier, HistGradientBoostingRegressor,
                              RandomForestClassifier, RandomForestRegressor)
from sklearn.inspection import permutation_importance
from sklearn.linear_model import LinearRegression, LogisticRegression, Ridge
from sklearn.metrics import (accuracy_score, confusion_matrix, f1_score, mean_absolute_error,
                             mean_squared_error, precision_score, r2_score, recall_score,
                             roc_auc_score, roc_curve)
from sklearn.model_selection import GroupKFold, StratifiedKFold, cross_val_score, cross_validate, train_test_split
from sklearn.pipeline import make_pipeline
from sklearn.preprocessing import OneHotEncoder, StandardScaler

import graficas as gf
from config import DATA_PROC, DATA_RAW, JAVA_DATA, SEMILLA, WEB_DATA

RESULTADOS = {}


def codificar(categoricas, numericas, escalar=False):
    """Preprocesamiento: one-hot para las categóricas; las numéricas se escalan solo si escalar=True."""
    return ColumnTransformer([("cat", OneHotEncoder(handle_unknown="ignore"), categoricas),
                              ("num", StandardScaler() if escalar else "passthrough", numericas)])


def normalizar(v):
    """Lleva los valores al rango 0-1."""
    return (v - v.min()) / (v.max() - v.min())

# ---------------------------------------------------------------- M1 · CO


ETIQUETAS_M1 = {"hora": "Hora del día", "dia_semana": "Día de la semana", "mes": "Mes", "fin_semana": "Fin de semana",
                "temperatura": "Temperatura", "humedad": "Humedad relativa", "lluvia": "Lluvia",
                "viento": "Velocidad del viento", "viento_sin": "Dirección viento (seno)",
                "viento_cos": "Dirección viento (coseno)", "estacion": "Estación de monitoreo", "anio": "Año"}


def preparar_co():
    """Une las mediciones horarias de CO con el clima de esa hora."""
    aire = pd.read_csv(DATA_PROC / "aire_limpio.csv", parse_dates=["fecha"])
    co = aire[aire.msfl_code == "CO"].groupby(["estacion", "fecha"], as_index=False)["valor"].mean()
    clima = pd.read_csv(DATA_RAW / "clima_openmeteo.csv", parse_dates=["fecha_hora"]).rename(columns={
        "fecha_hora": "fecha", "temperature_2m": "temperatura", "relative_humidity_2m": "humedad",
        "precipitation": "lluvia", "wind_speed_10m": "viento", "wind_direction_10m": "dir_viento"})
    df = co.merge(clima, on="fecha", how="inner")
    df["hora"] = df.fecha.dt.hour
    df["dia_semana"] = df.fecha.dt.dayofweek
    df["mes"] = df.fecha.dt.month
    df["anio"] = df.fecha.dt.year
    df["fin_semana"] = (df.dia_semana >= 5).astype(int)
    # La dirección del viento es un ángulo: seno y coseno evitan que 359° y 1° parezcan lejanos
    df["viento_sin"] = np.sin(np.radians(df.dir_viento))
    df["viento_cos"] = np.cos(np.radians(df.dir_viento))
    # Rezagos: el CO de la hora anterior y de la misma hora del día anterior
    df = df.sort_values(["estacion", "fecha"])
    df["co_lag1"] = df.groupby("estacion")["valor"].shift(1)
    df["co_lag24"] = df.groupby("estacion")["valor"].shift(24)
    return df


def modelos_regresion(cols):
    calendario = ["hora", "dia_semana", "mes"]
    lineal = codificar(["estacion"] + calendario, [c for c in cols if c not in calendario], escalar=True)
    arboles = codificar(["estacion"], cols)
    return {
        "Regresión lineal": make_pipeline(lineal, LinearRegression()),
        "Random Forest": make_pipeline(arboles, RandomForestRegressor(n_estimators=120, max_depth=18, min_samples_leaf=5,
                                                                      n_jobs=-1, random_state=SEMILLA)),
        "Gradient Boosting": make_pipeline(arboles, HistGradientBoostingRegressor(max_iter=400, learning_rate=0.06,
                                                                                  random_state=SEMILLA)),
    }


def modelo_co():
    df = preparar_co()
    base = ["hora", "dia_semana", "mes", "fin_semana", "anio", "temperatura", "humedad", "lluvia", "viento",
            "viento_sin", "viento_cos"]
    resultados, mejor, explicativo = [], None, None

    # Variante A (explicativa): ¿qué factores explican el CO? Se valida con 5 grupos de semanas.
    # Variante B (pronóstico): además usa el CO de horas previas. Se entrena hasta 2024 y se prueba en 2025.
    for variante, cols in (("Explicativo (sin rezagos)", base),
                           ("Pronóstico (con rezagos t-1, t-24)", base + ["co_lag1", "co_lag24"])):
        d = df.dropna(subset=cols)
        X = ["estacion"] + cols
        tr, te = d[d.fecha < "2025-01-01"], d[d.fecha >= "2025-01-01"]
        es_explicativo = variante.startswith("Explicativo")
        for nombre, m in modelos_regresion(cols).items():
            if es_explicativo:
                cv = cross_validate(m, d[X], d["valor"], groups=d.fecha.dt.strftime("%G-%V"), cv=GroupKFold(5),
                                    n_jobs=-1, scoring=("r2", "neg_mean_absolute_error", "neg_root_mean_squared_error"))
                r = {"variante": variante, "modelo": nombre, "validacion": "GroupKFold (5) por semana",
                     "n_train": int(len(d) * 0.8), "n_test": int(len(d) * 0.2),
                     "MAE": round(float(-cv["test_neg_mean_absolute_error"].mean()), 2),
                     "RMSE": round(float(-cv["test_neg_root_mean_squared_error"].mean()), 2),
                     "R2": round(float(cv["test_r2"].mean()), 3)}
                m.fit(tr[X], tr["valor"])
                if nombre == "Gradient Boosting":
                    explicativo = (m, tr, X)
            else:
                m.fit(tr[X], tr["valor"])
                p = m.predict(te[X])
                r = {"variante": variante, "modelo": nombre, "validacion": "Hold-out temporal (prueba 2025)",
                     "n_train": len(tr), "n_test": len(te),
                     "MAE": round(float(mean_absolute_error(te["valor"], p)), 2),
                     "RMSE": round(float(np.sqrt(mean_squared_error(te["valor"], p))), 2),
                     "R2": round(float(r2_score(te["valor"], p)), 3)}
                if mejor is None or r["R2"] > mejor[0]["R2"]:
                    mejor = (r, m, te, X)
            resultados.append(r)
            print(f"  M1 {variante[:11]} {nombre:18s} R2={r['R2']:.3f} MAE={r['MAE']:.1f}")

    # Gráficas del mejor modelo de pronóstico
    r, m, te, X = mejor
    muestra = te.sample(min(2500, len(te)), random_state=SEMILLA)
    gf.dispersion("m1_real_vs_pred", muestra["valor"].values, m.predict(muestra[X]),
                  f"CO observado vs. predicho — {r['modelo']}",
                  f"Conjunto de prueba 2025 · R² = {r['R2']:.3f} · MAE = {r['MAE']:.0f} µg/m³",
                  eje_x="Observado (µg/m³)", eje_y="Predicho (µg/m³)", diagonal=True, formato=",.0f")
    estacion = te.estacion.value_counts().index[0]
    semana = te[te.estacion == estacion].iloc[24 * 30: 24 * 37]
    gf.lineas("m1_semana", [f.strftime("%d-%m %Hh") for f in semana.fecha],
              {"Observado": semana["valor"].values, "Predicho": m.predict(semana[X])},
              f"Una semana de predicción horaria de CO · estación {estacion}",
              f"{r['modelo']} con rezagos · datos de prueba 2025", eje_y="CO (µg/m³)",
              estilos={"Predicho": "punteada"}, formato=",.0f")

    # Importancia de cada variable en el modelo explicativo
    m_exp, tr_exp, X_exp = explicativo
    sub = tr_exp.sample(4000, random_state=SEMILLA)
    imp = permutation_importance(m_exp, sub[X_exp], sub["valor"], n_repeats=5, random_state=SEMILLA, n_jobs=-1)
    orden = np.argsort(imp.importances_mean)[::-1]
    gf.barras("m1_importancia", [ETIQUETAS_M1[X_exp[i]] for i in orden], {"Importancia": imp.importances_mean[orden]},
              "¿Qué explica la concentración de CO?",
              "Importancia por permutación (caída de R²) · Gradient Boosting explicativo", horizontal=True,
              eje_x="Disminución de R²", formato=".3f")

    RESULTADOS["m1"] = {"resultados": resultados, "mejor": r, "perfil": exportar_perfil_horario(m_exp, tr_exp, X_exp),
                        "periodo_train": "2020-2024", "periodo_test": "2025"}


def exportar_perfil_horario(modelo, tr, X):
    """Índice de actividad vehicular por hora = mitad CO (efecto de la hora en el modelo) + mitad accidentes.

    El motor Java reduce la velocidad de las vías así: factor = 1 − 0,45 × índice.
    """
    base = tr.sample(6000, random_state=SEMILLA)
    co_por_hora = np.array([float(modelo.predict(base[X].assign(hora=h)).mean()) for h in range(24)])
    acc = pd.read_csv(DATA_PROC / "accidentes_limpio.csv")
    acc_por_hora = acc["hora"].value_counts().reindex(range(24), fill_value=0).values.astype(float)
    indice = normalizar(0.5 * normalizar(co_por_hora) + 0.5 * normalizar(acc_por_hora))
    factor = 1 - 0.45 * indice

    pd.DataFrame({"hora": range(24), "indice_actividad": indice.round(4), "factor_velocidad": factor.round(4),
                  "co_dependencia_parcial": co_por_hora.round(2), "accidentes": acc_por_hora.astype(int)}
                 ).to_csv(JAVA_DATA / "perfil_horario.csv", index=False)
    gf.lineas("m1_perfil_horario", list(range(24)),
              {"CO (dependencia parcial, normalizado)": normalizar(co_por_hora),
               "Accidentes por hora (normalizado)": normalizar(acc_por_hora),
               "Índice de actividad vehicular": indice},
              "Perfil horario de actividad vehicular aprendido de los datos",
              "Se exporta al motor de rutas Java: velocidad = límite × (1 − 0,45 × índice)", eje_x="Hora",
              eje_y="Índice (0-1)", estilos={"CO (dependencia parcial, normalizado)": "punteada",
                                             "Accidentes por hora (normalizado)": "punteada"}, formato=".2f")
    gf.barras("m1_factor_velocidad", [f"{h}h" for h in range(24)], {"Factor de velocidad": factor},
              "Factor de velocidad por hora usado en el ruteo", "1,00 = flujo libre · 0,55 = hora pico más congestionada",
              eje_y="Factor", formato=".2f")
    return {"hora_pico": int(np.argmax(indice)), "hora_valle": int(np.argmin(indice)),
            "factor_min": round(float(factor.min()), 3)}

# ---------------------------------------------------------------- M2 · Severidad


ETIQUETAS_M2 = {"hora": "Hora", "dia_semana": "Día de la semana", "mes": "Mes",
                "n_vehiculos": "N.º de vehículos", "hay_carga": "Involucra vehículo de carga",
                "hay_moto": "Involucra motocicleta", "hay_bus": "Involucra bus", "hay_bici": "Involucra bicicleta",
                "hay_publico": "Involucra servicio público", "lat": "Latitud", "lon": "Longitud",
                "geocodificado": "Dirección geocodificable", "clase_accidente": "Clase de accidente"}


def modelo_severidad():
    # Solo 2018-2022: desde 2023 casi no se registran accidentes sin víctimas
    a = pd.read_csv(DATA_PROC / "accidentes_limpio.csv").dropna(subset=["hora"])
    a = a[a["anio"] <= 2022]
    for c in ("hay_carga", "hay_moto", "hay_bus", "hay_bici", "hay_publico"):
        a[c] = a[c].fillna(False).astype(int)
    a["n_vehiculos"] = a["n_vehiculos"].fillna(1)
    a["geocodificado"] = a["lat"].notna().astype(int)
    a["lat"] = a["lat"].fillna(a["lat"].median())
    a["lon"] = a["lon"].fillna(a["lon"].median())

    num = ["hora", "dia_semana", "mes", "n_vehiculos", "hay_carga", "hay_moto", "hay_bus", "hay_bici",
           "hay_publico", "lat", "lon", "geocodificado"]
    cat = ["clase_accidente"]
    cols = num + cat
    y = a["con_victimas"]
    Xtr, Xte, ytr, yte = train_test_split(a[cols], y, test_size=0.25, stratify=y, random_state=SEMILLA)
    modelos = {
        "Regresión logística": make_pipeline(codificar(cat, num, escalar=True),
                                             LogisticRegression(max_iter=2000, class_weight="balanced")),
        "Random Forest": make_pipeline(codificar(cat, num), RandomForestClassifier(
            n_estimators=300, min_samples_leaf=4, class_weight="balanced", n_jobs=-1, random_state=SEMILLA)),
        "Gradient Boosting": make_pipeline(codificar(cat, num), HistGradientBoostingClassifier(
            max_iter=300, learning_rate=0.06, class_weight="balanced", random_state=SEMILLA)),
    }

    cv = StratifiedKFold(5, shuffle=True, random_state=SEMILLA)
    malla = np.linspace(0, 1, 51)  # eje x común para dibujar las curvas ROC
    resultados, curvas, mejor = [], {}, None
    for nombre, m in modelos.items():
        auc_cv = cross_val_score(m, Xtr, ytr, cv=cv, scoring="roc_auc", n_jobs=-1)
        m.fit(Xtr, ytr)
        prob = m.predict_proba(Xte)[:, 1]
        pred = (prob >= 0.5).astype(int)
        metricas = {"accuracy": accuracy_score(yte, pred), "precision": precision_score(yte, pred),
                    "recall": recall_score(yte, pred), "f1": f1_score(yte, pred), "auc": roc_auc_score(yte, prob),
                    "auc_cv_media": auc_cv.mean(), "auc_cv_std": auc_cv.std()}
        r = {"modelo": nombre, **{k: round(float(v), 3) for k, v in metricas.items()}}
        resultados.append(r)
        fpr, tpr, _ = roc_curve(yte, prob)
        curvas[f"{nombre} (AUC {r['auc']:.3f})"] = np.interp(malla, fpr, tpr)
        print(f"  M2 {nombre:20s} AUC={r['auc']:.3f} F1={r['f1']:.3f} CV={r['auc_cv_media']:.3f}±{r['auc_cv_std']:.3f}")
        if mejor is None or r["auc"] > mejor[0]["auc"]:
            mejor = (r, m, pred)

    curvas["Clasificador aleatorio"] = malla
    gf.lineas("m2_roc", [round(x, 2) for x in malla], curvas, "Curvas ROC — severidad del accidente",
              "Conjunto de prueba (25 %) · mayor área bajo la curva = mejor discriminación",
              eje_x="Tasa de falsos positivos", eje_y="Tasa de verdaderos positivos",
              estilos={"Clasificador aleatorio": "punteada"}, formato=".2f")

    r, m, pred = mejor
    confusion = confusion_matrix(yte, pred)
    gf.mapa_calor("m2_confusion", ["Predicho: solo daños", "Predicho: con víctimas"],
                  ["Real: solo daños", "Real: con víctimas"], confusion, f"Matriz de confusión — {r['modelo']}",
                  f"n = {len(yte):,} accidentes de prueba".replace(",", "."), anotar=True)

    muestra = Xte.sample(3000, random_state=SEMILLA).index
    imp = permutation_importance(m, Xte.loc[muestra], yte.loc[muestra], scoring="roc_auc", n_repeats=5,
                                 random_state=SEMILLA, n_jobs=-1)
    top10 = np.argsort(imp.importances_mean)[::-1][:10]
    nombres_top = [ETIQUETAS_M2[cols[i]] for i in top10]
    gf.barras("m2_importancia", nombres_top, {"Importancia": imp.importances_mean[top10]},
              "Factores que predicen un accidente con víctimas",
              f"Importancia por permutación (caída de AUC) · {r['modelo']}", horizontal=True,
              eje_x="Disminución de AUC", formato=".3f")

    tasa_moto = a.groupby("hay_moto")["con_victimas"].mean()
    tasa_carga = a.groupby("hay_carga")["con_victimas"].mean()
    RESULTADOS["m2"] = {"resultados": resultados, "mejor": r, "n_train": len(Xtr), "n_test": len(Xte),
                        "periodo": "2018-2022", "importancia_top": nombres_top[:3],
                        "confusion": confusion.tolist(), "balance_clases": round(float(y.mean()), 3),
                        "pct_victimas_con_moto": round(float(tasa_moto.get(1, 0) * 100), 1),
                        "pct_victimas_sin_moto": round(float(tasa_moto.get(0, 0) * 100), 1),
                        "pct_victimas_con_carga": round(float(tasa_carga.get(1, 0) * 100), 1)}

# ---------------------------------------------------------------- M3 · Pronóstico mensual


def mape(pred, real):
    """Error porcentual absoluto medio."""
    return float(np.mean(np.abs(pred - real) / real) * 100)


def modelo_pronostico():
    """Ridge sobre log(accidentes con víctimas por mes) con rezagos de 1, 2 y 12 meses, mes y COVID."""
    a = pd.read_csv(DATA_PROC / "accidentes_limpio.csv", parse_dates=["fecha"])
    serie = a[a["con_victimas"] == 1].set_index("fecha").resample("MS").size().astype(float)
    log = np.log(serie)
    df = pd.DataFrame({"y": log, "lag1": log.shift(1), "lag2": log.shift(2), "lag12": log.shift(12)})
    df["covid"] = ((df.index >= "2020-03-01") & (df.index <= "2020-08-01")).astype(int)
    meses = pd.get_dummies(serie.index.month, prefix="m", drop_first=True).astype(int).set_axis(df.index)
    df = pd.concat([df, meses], axis=1).dropna()
    variables = ["lag1", "lag2", "lag12", "covid"] + list(meses.columns)

    tr, te = df[df.index < "2025-01-01"], df[df.index >= "2025-01-01"]
    m = Ridge(alpha=1.0).fit(tr[variables], tr.y)
    pred = np.exp(m.predict(te[variables]))
    real = np.exp(te.y.values)
    es_2025 = te.index.year == 2025

    # Se compara contra dos métodos sencillos (líneas base)
    comparacion = []
    for nombre, p in (("Ridge autorregresivo (log)", pred),
                      ("Ingenuo estacional (mismo mes año anterior)", serie.shift(12).loc[te.index].values),
                      ("Media móvil 12 meses", serie.shift(1).rolling(12).mean().loc[te.index].values)):
        comparacion.append({"modelo": nombre, "MAPE_total": round(mape(p, real), 1),
                            "MAPE_2025": round(mape(p[es_2025], real[es_2025]), 1),
                            "MAE": round(float(mean_absolute_error(real, p)), 1)})
        print(f"  M3 {nombre:45s} MAPE={comparacion[-1]['MAPE_total']:.1f}%  (2025: {comparacion[-1]['MAPE_2025']:.1f}%)")

    # Banda del 95 % a partir del error en entrenamiento
    sigma = float(np.std(tr.y - m.predict(tr[variables])))
    vacio_tr, vacio_te = np.full(len(tr), np.nan), np.full(len(te), np.nan)
    gf.lineas("m3_pronostico", [d.strftime("%Y-%m") for d in df.index],
              {"Observado": np.exp(df.y.values),
               "Ajuste (entrenamiento)": np.concatenate([np.exp(m.predict(tr[variables])), vacio_te]),
               "Pronóstico a un mes (prueba)": np.concatenate([vacio_tr, pred])},
              "Pronóstico mensual de accidentes con víctimas (Ridge autorregresivo)",
              f"Entrenamiento 2019-2024 · prueba ene 2025 - sep 2026 · MAPE 2025 = {comparacion[0]['MAPE_2025']:.1f} %",
              eje_y="Accidentes con víctimas por mes", estilos={"Ajuste (entrenamiento)": "punteada"}, formato=",.0f",
              banda=(np.concatenate([vacio_tr, pred * np.exp(-1.96 * sigma)]),
                     np.concatenate([vacio_tr, pred * np.exp(1.96 * sigma)])))
    RESULTADOS["m3"] = {"modelo": "Ridge autorregresivo en escala log (rezagos 1, 2 y 12 + estacionalidad + COVID)",
                        "comparacion": comparacion, "meses_test": len(te),
                        "promedio_mensual_2025": round(float(real[es_2025].mean()), 1),
                        "promedio_mensual_2026": round(float(real[~es_2025].mean()), 1)}


def main():
    print("M1 · Regresión de CO")
    modelo_co()
    print("M2 · Clasificación de severidad")
    modelo_severidad()
    print("M3 · Pronóstico mensual")
    modelo_pronostico()
    # M4 son los puntos críticos (DBSCAN) del paso 3
    RESULTADOS["m4"] = json.loads((WEB_DATA / "puntos_criticos.json").read_text(encoding="utf-8"))
    del RESULTADOS["m4"]["puntos"]
    (WEB_DATA / "modelos.json").write_text(json.dumps(RESULTADOS, ensure_ascii=False, indent=1), encoding="utf-8")
    gf.exportar()


if __name__ == "__main__":
    main()
