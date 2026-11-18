package co.edu.greenroute.escritorio;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.Graphics;

/** Clase base de cada pantalla de la app: cada hija construye su propio contenido. */
public abstract class Pantalla extends JPanel {

    protected final ContextoApp contexto;
    private final JLabel lblTitulo = new JLabel();
    private final JLabel lblSubtitulo = new JLabel();

    protected Pantalla(ContextoApp contexto) {
        super(new BorderLayout(0, 20));
        this.contexto = contexto;
        setBorder(BorderFactory.createEmptyBorder(28, 32, 28, 32));
        add(encabezado(), BorderLayout.NORTH);
        add(construirContenido(), BorderLayout.CENTER);
    }

    public abstract String getTitulo();

    public abstract String getSubtitulo();

    protected abstract JComponent construirContenido();

    public void alMostrar() {
    }

    private JComponent encabezado() {
        JPanel caja = new JPanel();
        caja.setOpaque(false);
        caja.setLayout(new BoxLayout(caja, BoxLayout.Y_AXIS));
        lblTitulo.setFont(Tema.titulo(30));
        lblSubtitulo.setFont(Tema.texto(15));
        caja.add(lblTitulo);
        caja.add(Box.createVerticalStrut(6));
        caja.add(lblSubtitulo);
        return caja;
    }

    @Override
    public void addNotify() {
        super.addNotify();
        lblTitulo.setText(getTitulo());
        lblSubtitulo.setText(getSubtitulo());
    }

    @Override
    protected void paintComponent(Graphics g) {
        g.setColor(Tema.fondo());
        g.fillRect(0, 0, getWidth(), getHeight());
        lblTitulo.setForeground(Tema.tinta());
        lblSubtitulo.setForeground(Tema.tinta2());
    }

    protected static JLabel rotulo(String texto) {
        JLabel l = new JLabel(texto.toUpperCase()) {
            @Override
            protected void paintComponent(Graphics g) {
                setForeground(Tema.tenue());
                super.paintComponent(g);
            }
        };
        l.setFont(Tema.mono(11));
        return l;
    }
}
