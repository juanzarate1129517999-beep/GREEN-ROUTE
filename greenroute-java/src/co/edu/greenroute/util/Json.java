package co.edu.greenroute.util;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** Convierte mapas y listas a texto JSON (sin librerías externas). */
public final class Json {

    private Json() {
    }

    public static Map<String, Object> obj(Object... paresClaveValor) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i + 1 < paresClaveValor.length; i += 2) {
            m.put(String.valueOf(paresClaveValor[i]), paresClaveValor[i + 1]);
        }
        return m;
    }

    public static String escribir(Object valor) {
        StringBuilder sb = new StringBuilder(1024);
        escribir(valor, sb);
        return sb.toString();
    }

    private static void escribir(Object v, StringBuilder sb) {
        if (v == null) {
            sb.append("null");
        } else if (v instanceof String) {
            cadena((String) v, sb);
        } else if (v instanceof Double || v instanceof Float) {
            double d = ((Number) v).doubleValue();
            if (Double.isNaN(d) || Double.isInfinite(d)) {
                sb.append("null");
            } else if (d == Math.rint(d) && Math.abs(d) < 1e15) {
                sb.append((long) d);
            } else {
                sb.append(String.format(Locale.ROOT, "%.5f", d).replaceAll("0+$", ""));
            }
        } else if (v instanceof Number || v instanceof Boolean) {
            sb.append(v);
        } else if (v instanceof Map) {
            sb.append('{');
            boolean primero = true;
            for (Map.Entry<?, ?> e : ((Map<?, ?>) v).entrySet()) {
                if (!primero) sb.append(',');
                primero = false;
                cadena(String.valueOf(e.getKey()), sb);
                sb.append(':');
                escribir(e.getValue(), sb);
            }
            sb.append('}');
        } else if (v instanceof Collection) {
            sb.append('[');
            boolean primero = true;
            for (Object o : (Collection<?>) v) {
                if (!primero) sb.append(',');
                primero = false;
                escribir(o, sb);
            }
            sb.append(']');
        } else if (v instanceof double[]) {
            double[] arr = (double[]) v;
            sb.append('[');
            for (int i = 0; i < arr.length; i++) {
                if (i > 0) sb.append(',');
                escribir(arr[i], sb);
            }
            sb.append(']');
        } else if (v instanceof Object[]) {
            Object[] arr = (Object[]) v;
            sb.append('[');
            for (int i = 0; i < arr.length; i++) {
                if (i > 0) sb.append(',');
                escribir(arr[i], sb);
            }
            sb.append(']');
        } else if (v instanceof Enum) {
            cadena(((Enum<?>) v).name(), sb);
        } else {
            cadena(v.toString(), sb);
        }
    }

    private static void cadena(String s, StringBuilder sb) {
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"':  sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        sb.append('"');
    }
}
