package co.edu.greenroute.escritorio;

import javax.swing.BorderFactory;
import javax.swing.JPanel;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.LayoutManager;
import java.awt.RenderingHints;

/** Panel con bordes redondeados. */
public class Tarjeta extends JPanel {

    private static final int RADIO = 18;

    public Tarjeta(LayoutManager layout) {
        super(layout);
        setOpaque(false);
        setBorder(BorderFactory.createEmptyBorder(18, 20, 18, 20));
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setColor(Tema.panel());
        g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, RADIO, RADIO);
        g2.setColor(Tema.borde());
        g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, RADIO, RADIO);
        g2.dispose();
        super.paintComponent(g);
    }
}
