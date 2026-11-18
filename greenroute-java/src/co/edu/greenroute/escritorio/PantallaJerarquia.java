package co.edu.greenroute.escritorio;

import co.edu.greenroute.vehiculos.Vehiculo;

import javax.swing.JComponent;
import javax.swing.JOptionPane;
import java.awt.BasicStroke;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/** Pantalla que dibuja el árbol de herencia de los vehículos. */
public class PantallaJerarquia extends Pantalla {

    public PantallaJerarquia(ContextoApp contexto) {
        super(contexto);
    }

    @Override public String getTitulo() { return "Herencia y polimorfismo"; }
    @Override public String getSubtitulo() { return "Árbol real de clases obtenido por reflexión. Haga clic en una clase para ver qué métodos redefine."; }

    @Override
    protected JComponent construirContenido() {
        return new Arbol(contexto.getPlanificador().getFlota().getVehiculos());
    }

    private static final class Arbol extends JComponent {

        private final Map<Class<?>, List<Class<?>>> hijos = new LinkedHashMap<>();
        private final Map<Class<?>, Vehiculo> instancia = new LinkedHashMap<>();
        private final Map<Class<?>, Rectangle> cajas = new LinkedHashMap<>();
        private final Map<Class<?>, Double> columna = new LinkedHashMap<>();
        private int hojas;

        Arbol(List<Vehiculo> flota) {
            for (Vehiculo v : flota) {
                instancia.put(v.getClass(), v);
                Class<?> c = v.getClass();
                while (c != Vehiculo.class) {
                    Class<?> padre = c.getSuperclass();
                    List<Class<?>> lista = hijos.computeIfAbsent(padre, k -> new ArrayList<>());
                    if (!lista.contains(c)) lista.add(c);
                    c = padre;
                }
            }
            ubicar(Vehiculo.class);
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    cajas.forEach((clase, r) -> { if (r.contains(e.getPoint())) explicar(clase); });
                }
            });
        }

        private double ubicar(Class<?> c) {
            List<Class<?>> h = hijos.getOrDefault(c, List.of());
            double x;
            if (h.isEmpty()) {
                x = hojas++;
            } else {
                x = h.stream().mapToDouble(this::ubicar).average().orElse(0);
            }
            columna.put(c, x);
            return x;
        }

        private int nivel(Class<?> c) {
            int n = 0;
            while (c != Vehiculo.class) { c = c.getSuperclass(); n++; }
            return n;
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB);
            int anchoCaja = 178, altoCaja = 64, sepY = 120;
            double paso = (getWidth() - anchoCaja - 20) / Math.max(1.0, hojas - 1);
            cajas.clear();
            columna.forEach((c, col) -> cajas.put(c, new Rectangle((int) (10 + col * paso), 46 + nivel(c) * sepY, anchoCaja, altoCaja)));

            Rectangle raiz = cajas.get(Vehiculo.class);
            Rectangle emisor = new Rectangle(raiz.x, 0, anchoCaja, 30);
            g2.setColor(Tema.tenue());
            g2.setStroke(new BasicStroke(1.4f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10, new float[]{5, 4}, 0));
            g2.drawLine(raiz.x + anchoCaja / 2, raiz.y, raiz.x + anchoCaja / 2, emisor.y + emisor.height);
            g2.setStroke(new BasicStroke(1.4f));
            g2.drawRoundRect(emisor.x, emisor.y, emisor.width, emisor.height, 10, 10);
            g2.setFont(Tema.mono(12));
            centrado(g2, "«interface» Emisor", emisor, emisor.y + 20);

            g2.setStroke(new BasicStroke(1.6f));
            for (Map.Entry<Class<?>, List<Class<?>>> e : hijos.entrySet()) {
                Rectangle p = cajas.get(e.getKey());
                int ty = p.y + p.height + 12, px = p.x + p.width / 2;
                int medio = ty + 22;
                g2.setColor(Tema.tenue());
                for (Class<?> h : e.getValue()) {
                    Rectangle r = cajas.get(h);
                    int hx = r.x + r.width / 2;
                    g2.drawLine(hx, r.y, hx, medio);
                    g2.drawLine(hx, medio, px, medio);
                }
                g2.drawLine(px, medio, px, ty);
                Polygon tri = new Polygon(new int[]{px - 8, px + 8, px}, new int[]{ty, ty, ty - 12}, 3);
                g2.setColor(Tema.fondo());
                g2.fill(tri);
                g2.setColor(Tema.tinta2());
                g2.draw(tri);
            }

            for (Map.Entry<Class<?>, Rectangle> e : cajas.entrySet()) caja(g2, e.getKey(), e.getValue());

            int y0 = 46 + 3 * sepY + altoCaja + 50;
            g2.setColor(Tema.panel());
            g2.fillRoundRect(10, y0, getWidth() - 20, 112, 16, 16);
            g2.setColor(Tema.borde());
            g2.drawRoundRect(10, y0, getWidth() - 20, 112, 16, 16);
            g2.setFont(Tema.mono(14));
            String[] codigo = {
                    "for (Vehiculo v : flota.getVehiculos()) {          // el tipo declarado es la clase abstracta",
                    "    double g = v.emisionMinimaPorMetro(600) * 1e6;  // enlace dinámico: cada subclase usa su consumoPorKm()",
                    "}"};
            for (int i = 0; i < codigo.length; i++) {
                int cx = 30, cy = y0 + 34 + i * 26;
                String linea = codigo[i];
                int corte = linea.indexOf("//");
                String sentencia = corte >= 0 ? linea.substring(0, corte) : linea;
                g2.setColor(Tema.tinta());
                g2.drawString(sentencia, cx, cy);
                if (corte >= 0) {
                    g2.setColor(Tema.acento());
                    g2.drawString(linea.substring(corte), cx + g2.getFontMetrics().stringWidth(sentencia), cy);
                }
            }
            g2.dispose();
        }

        private void caja(Graphics2D g2, Class<?> c, Rectangle r) {
            boolean abstracta = Modifier.isAbstract(c.getModifiers());
            Vehiculo v = instancia.get(c);
            g2.setColor(abstracta ? Tema.panel() : Tema.elevado());
            g2.fillRoundRect(r.x, r.y, r.width, r.height, 14, 14);
            g2.setColor(v != null ? Tema.acento() : Tema.borde());
            g2.setStroke(new BasicStroke(v != null ? 1.8f : 1.2f));
            g2.drawRoundRect(r.x, r.y, r.width, r.height, 14, 14);
            g2.setFont(Tema.mono(11));
            g2.setColor(Tema.tenue());
            String estereotipo = abstracta ? "«abstract»" : Arrays.stream(c.getInterfaces())
                    .map(i -> "implements " + i.getSimpleName()).collect(Collectors.joining(", "));
            centrado(g2, estereotipo, r, r.y + 18);
            g2.setFont(Tema.fuerte(14));
            g2.setColor(Tema.tinta());
            centrado(g2, c.getSimpleName(), r, r.y + 38);
            if (v != null) {
                g2.setFont(Tema.mono(12));
                g2.setColor(Tema.acento());
                double g = v.emisionMinimaPorMetro(600) * 1_000_000;
                centrado(g2, String.format(Locale.forLanguageTag("es-CO"), "mín. %,.0f g CO₂/km", g), r, r.y + 56);
            }
        }

        private static void centrado(Graphics2D g2, String s, Rectangle r, int y) {
            int w = g2.getFontMetrics().stringWidth(s);
            g2.drawString(s, r.x + (r.width - w) / 2, y);
        }

        private void explicar(Class<?> c) {
            String metodos = Arrays.stream(c.getDeclaredMethods())
                    .filter(m -> !m.isSynthetic() && !Modifier.isPrivate(m.getModifiers()))
                    .map(Method::getName).distinct().sorted()
                    .map(n -> "<li><code>" + n + "()</code></li>").collect(Collectors.joining());
            String padre = c.getSuperclass().getSimpleName();
            String interfaces = Arrays.stream(c.getInterfaces()).map(Class::getSimpleName).collect(Collectors.joining(", "));
            JOptionPane.showMessageDialog(this, "<html><body style='width:330px'>"
                    + "<code>public " + (Modifier.isAbstract(c.getModifiers()) ? "abstract " : "") + "class "
                    + c.getSimpleName() + " extends " + padre + (interfaces.isEmpty() ? "" : " implements " + interfaces)
                    + "</code><br><br><b>Métodos que declara o redefine:</b><ul>" + metodos + "</ul></body></html>",
                    c.getSimpleName(), JOptionPane.INFORMATION_MESSAGE);
        }
    }
}
