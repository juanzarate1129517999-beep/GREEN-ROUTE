package co.edu.greenroute.escritorio;

import javax.swing.Icon;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

/** Icono dibujado para los cuadros de diálogo (información, pregunta, advertencia, error). */
public class IconoDialogo implements Icon {

    public enum Tipo { INFORMACION, PREGUNTA, ADVERTENCIA, ERROR }

    private static final int TAM = 40;
    private final Tipo tipo;

    public IconoDialogo(Tipo tipo) {
        this.tipo = tipo;
    }

    @Override public int getIconWidth() { return TAM; }
    @Override public int getIconHeight() { return TAM; }

    @Override
    public void paintIcon(Component c, Graphics g, int x, int y) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        Color color = switch (tipo) {
            case INFORMACION, PREGUNTA -> Tema.acento();
            case ADVERTENCIA -> new Color(0xD99A00);
            case ERROR -> Tema.alerta();
        };
        String simbolo = switch (tipo) {
            case INFORMACION -> "i";
            case PREGUNTA -> "?";
            case ADVERTENCIA, ERROR -> "!";
        };
        g2.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), 45));
        g2.fillOval(x, y, TAM, TAM);
        g2.setColor(color);
        g2.fillOval(x + 7, y + 7, TAM - 14, TAM - 14);
        g2.setColor(Color.WHITE);
        g2.setFont(new Font("Segoe UI", Font.BOLD, 17));
        int w = g2.getFontMetrics().stringWidth(simbolo);
        g2.drawString(simbolo, x + (TAM - w) / 2, y + TAM / 2 + 6);
        g2.dispose();
    }
}
