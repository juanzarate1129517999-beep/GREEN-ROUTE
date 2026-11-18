package co.edu.greenroute.escritorio;

import javax.swing.JButton;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

/** Botón Swing con el estilo visual de Green Route (dibujado a mano). */
public class BotonGR extends JButton {

    public enum Estilo { PRIMARIO, SECUNDARIO, NAVEGACION }

    private final Estilo estilo;
    private boolean activo;

    public BotonGR(String texto, Estilo estilo) {
        super(texto);
        this.estilo = estilo;
        setContentAreaFilled(false);
        setBorderPainted(false);
        setFocusPainted(false);
        setOpaque(false);
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        setFont(estilo == Estilo.NAVEGACION ? Tema.texto(14) : Tema.fuerte(14));
        setHorizontalAlignment(estilo == Estilo.NAVEGACION ? LEFT : CENTER);
    }

    public void setActivo(boolean activo) {
        this.activo = activo;
        repaint();
    }

    @Override
    public Dimension getPreferredSize() {
        FontMetrics fm = getFontMetrics(getFont());
        int ancho = fm.stringWidth(getText()) + (estilo == Estilo.NAVEGACION ? 40 : 36);
        return new Dimension(ancho, estilo == Estilo.NAVEGACION ? 40 : 42);
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB);
        boolean encima = getModel().isRollover();
        boolean presionado = getModel().isPressed();
        int w = getWidth(), h = getHeight(), radio = estilo == Estilo.NAVEGACION ? 12 : h;

        switch (estilo) {
            case PRIMARIO -> {
                g2.setColor(presionado ? Tema.tinta2() : Tema.tinta());
                g2.fillRoundRect(0, 0, w - 1, h - 1, radio, radio);
                g2.setColor(Tema.fondo());
            }
            case SECUNDARIO -> {
                g2.setColor(encima ? Tema.elevado() : Tema.panel());
                g2.fillRoundRect(0, 0, w - 1, h - 1, radio, radio);
                g2.setColor(encima ? Tema.tinta2() : Tema.borde());
                g2.drawRoundRect(0, 0, w - 1, h - 1, radio, radio);
                g2.setColor(Tema.tinta());
            }
            case NAVEGACION -> {
                if (activo || encima) {
                    g2.setColor(activo ? Tema.elevado() : Tema.panel());
                    g2.fillRoundRect(0, 0, w - 1, h - 1, radio, radio);
                }
                if (activo) {
                    g2.setColor(Tema.acento());
                    g2.fillRoundRect(0, 10, 4, h - 20, 4, 4);
                }
                g2.setColor(activo ? Tema.tinta() : Tema.tinta2());
            }
        }
        g2.setFont(getFont());
        FontMetrics fm = g2.getFontMetrics();
        int x = estilo == Estilo.NAVEGACION ? 18 : (w - fm.stringWidth(getText())) / 2;
        int y = (h - fm.getHeight()) / 2 + fm.getAscent();
        g2.drawString(getText(), x, y);
        g2.dispose();
    }
}
