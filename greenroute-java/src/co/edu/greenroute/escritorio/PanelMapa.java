package co.edu.greenroute.escritorio;

import co.edu.greenroute.modelo.Coordenada;
import co.edu.greenroute.red.Nodo;
import co.edu.greenroute.red.RedTransporte;
import co.edu.greenroute.red.Tramo;
import co.edu.greenroute.ruteo.Ruta;

import javax.swing.JComponent;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.util.List;

/** Dibuja la red vial, el río y la ruta calculada. */
public class PanelMapa extends JComponent {

    private final Path2D.Float viales = new Path2D.Float();
    private final Path2D.Float fluviales = new Path2D.Float();
    private double minLon = 180, maxLon = -180, minLat = 90, maxLat = -90;
    private Ruta ruta;
    private Coordenada origen, destino;

    public PanelMapa(RedTransporte red) {
        for (Nodo n : red.getNodos()) {
            for (Tramo t : n.getSalientes()) {
                List<Coordenada> geo = t.getGeometria();
                Path2D.Float destinoPath = t.esFluvial() ? fluviales : viales;
                for (int i = 0; i < geo.size(); i++) {
                    Coordenada c = geo.get(i);
                    float x = (float) c.getLongitud(), y = (float) -c.getLatitud();
                    if (i == 0) destinoPath.moveTo(x, y); else destinoPath.lineTo(x, y);
                    if (!t.esFluvial()) {
                        minLon = Math.min(minLon, c.getLongitud()); maxLon = Math.max(maxLon, c.getLongitud());
                        minLat = Math.min(minLat, c.getLatitud());  maxLat = Math.max(maxLat, c.getLatitud());
                    }
                }
            }
        }
        setPreferredSize(new Dimension(620, 560));
    }

    public void mostrar(Ruta ruta, Coordenada origen, Coordenada destino) {
        this.ruta = ruta;
        this.origen = origen;
        this.destino = destino;
        repaint();
    }

    private AffineTransform transformacion() {
        double kLon = Math.cos(Math.toRadians((minLat + maxLat) / 2));
        double ancho = (maxLon - minLon) * kLon, alto = maxLat - minLat;
        double margen = 18;
        double escala = Math.min((getWidth() - 2 * margen) / ancho, (getHeight() - 2 * margen) / alto);
        double offX = (getWidth() - ancho * escala) / 2, offY = (getHeight() - alto * escala) / 2;
        AffineTransform at = new AffineTransform();
        at.translate(offX, offY);
        at.scale(escala * kLon, escala);
        at.translate(-minLon, maxLat);
        return at;
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g2.setColor(Tema.panel());
        g2.fillRoundRect(0, 0, getWidth(), getHeight(), 18, 18);
        AffineTransform at = transformacion();

        g2.setStroke(new BasicStroke(0.6f));
        g2.setColor(Tema.red());
        g2.draw(at.createTransformedShape(viales));
        g2.setStroke(new BasicStroke(3.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.setColor(Tema.fluvial());
        g2.draw(at.createTransformedShape(fluviales));

        if (ruta != null) {
            Path2D.Float camino = new Path2D.Float();
            boolean primero = true;
            for (double[] p : ruta.geometria()) {
                if (primero) camino.moveTo(p[1], -p[0]); else camino.lineTo(p[1], -p[0]);
                primero = false;
            }
            Shape trazo = at.createTransformedShape(camino);
            Color color = Color.decode(ruta.getCriterio().getColor());
            g2.setStroke(new BasicStroke(10f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), 70));
            g2.draw(trazo);
            g2.setStroke(new BasicStroke(4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.setColor(Tema.acento());
            g2.draw(trazo);
        }
        marcador(g2, at, origen, "A");
        marcador(g2, at, destino, "B");

        g2.setFont(Tema.mono(11));
        g2.setColor(Tema.tenue());
        g2.drawString("© OpenStreetMap · malla vial de Barranquilla y Soledad", 16, getHeight() - 14);
        g2.dispose();
    }

    private void marcador(Graphics2D g2, AffineTransform at, Coordenada c, String letra) {
        if (c == null) return;
        Point2D p = at.transform(new Point2D.Double(c.getLongitud(), -c.getLatitud()), null);
        double r = 13;
        g2.setColor(Tema.tinta());
        g2.fill(new Ellipse2D.Double(p.getX() - r, p.getY() - r, 2 * r, 2 * r));
        g2.setColor(Tema.fondo());
        g2.setFont(Tema.titulo(13));
        int w = g2.getFontMetrics().stringWidth(letra);
        g2.drawString(letra, (float) (p.getX() - w / 2.0), (float) (p.getY() + 5));
    }
}
