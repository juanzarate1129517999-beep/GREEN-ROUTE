package co.edu.greenroute.escritorio;

import javax.swing.JTable;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.util.Locale;

/** Dibuja una barra proporcional dentro de una celda de la tabla. */
public class RenderBarra extends DefaultTableCellRenderer {

    private final ModeloTablaFlota modelo;
    private double fraccion;
    private boolean minima;

    public RenderBarra(ModeloTablaFlota modelo) {
        this.modelo = modelo;
        setHorizontalAlignment(LEFT);
    }

    @Override
    public Component getTableCellRendererComponent(JTable tabla, Object valor, boolean sel, boolean foco,
                                                   int fila, int col) {
        super.getTableCellRendererComponent(tabla, "", sel, false, fila, col);
        if (valor instanceof Double kg) {
            fraccion = kg / modelo.getMaximaEmision();
            minima = fila == indiceMinimo();
            setText(String.format(Locale.forLanguageTag("es-CO"), "   %.2f", kg));
        } else {
            fraccion = 0;
            minima = false;
            setText("   —");
        }
        setForeground(Tema.tinta());
        setBackground(sel ? Tema.borde() : Tema.elevado());
        return this;
    }

    private int indiceMinimo() {
        int mejor = -1;
        double min = Double.MAX_VALUE;
        for (int i = 0; i < modelo.getRowCount(); i++) {
            Object v = modelo.getValueAt(i, 5);
            if (v instanceof Double d && d < min) { min = d; mejor = i; }
        }
        return mejor;
    }

    @Override
    protected void paintComponent(Graphics g) {
        g.setColor(getBackground());
        g.fillRect(0, 0, getWidth(), getHeight());
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        int ancho = (int) Math.round((getWidth() - 80) * fraccion);
        g2.setColor(minima ? Tema.acento() : Tema.tenue());
        g2.fillRoundRect(64, getHeight() / 2 - 5, Math.max(ancho, fraccion > 0 ? 3 : 0), 10, 4, 4);
        g2.dispose();
        setOpaque(false);
        super.paintComponent(g);
        setOpaque(true);
    }
}
