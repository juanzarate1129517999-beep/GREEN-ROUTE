package co.edu.greenroute.escritorio;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.WindowConstants;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

/** Ventana principal: barra lateral y cambio entre pantallas. */
public class VentanaPrincipal extends JFrame {

    private final ContextoApp contexto;
    private final List<Pantalla> pantallas = new ArrayList<>();
    private final List<BotonGR> botones = new ArrayList<>();
    private final CardLayout tarjetas = new CardLayout();
    private final JPanel area = new JPanel(tarjetas);

    public VentanaPrincipal(ContextoApp contexto) {
        super("Green Route · Rutas de carga de baja huella de carbono");
        this.contexto = contexto;
        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        setIconImage(icono());

        pantallas.add(new PantallaPlanificar(contexto));
        pantallas.add(new PantallaFlota(contexto));
        pantallas.add(new PantallaJerarquia(contexto));
        pantallas.add(new PantallaCodigo(contexto));

        JPanel raiz = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                g.setColor(Tema.fondo());
                g.fillRect(0, 0, getWidth(), getHeight());
            }
        };
        raiz.add(barraLateral(), BorderLayout.WEST);
        for (Pantalla p : pantallas) area.add(p, p.getTitulo());
        raiz.add(area, BorderLayout.CENTER);
        setContentPane(raiz);
        setJMenuBar(menu());
        setMinimumSize(new Dimension(1180, 760));
        setSize(1440, 900);
        setLocationRelativeTo(null);
        mostrar(0);
    }

    private JPanel barraLateral() {
        JPanel barra = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                g.setColor(Tema.panel());
                g.fillRect(0, 0, getWidth(), getHeight());
                g.setColor(Tema.borde());
                g.drawLine(getWidth() - 1, 0, getWidth() - 1, getHeight());
            }
        };
        barra.setLayout(new BoxLayout(barra, BoxLayout.Y_AXIS));
        barra.setBorder(BorderFactory.createEmptyBorder(26, 16, 22, 16));
        barra.setPreferredSize(new Dimension(250, 10));

        JLabel marca = new JLabel("Green Route") {
            @Override
            protected void paintComponent(Graphics g) {
                setForeground(Tema.tinta());
                super.paintComponent(g);
            }
        };
        marca.setFont(Tema.titulo(22));
        JLabel lema = new JLabel("Barranquilla · ODS 9") {
            @Override
            protected void paintComponent(Graphics g) {
                setForeground(Tema.tenue());
                super.paintComponent(g);
            }
        };
        lema.setFont(Tema.mono(12));
        barra.add(marca);
        barra.add(Box.createVerticalStrut(4));
        barra.add(lema);
        barra.add(Box.createVerticalStrut(30));

        for (int i = 0; i < pantallas.size(); i++) {
            final int indice = i;
            BotonGR b = new BotonGR((i + 1) + "   " + pantallas.get(i).getTitulo(), BotonGR.Estilo.NAVEGACION);
            b.setAlignmentX(Component.LEFT_ALIGNMENT);
            b.setMaximumSize(new Dimension(Integer.MAX_VALUE, 42));
            b.addActionListener(e -> mostrar(indice));
            botones.add(b);
            barra.add(b);
            barra.add(Box.createVerticalStrut(4));
        }
        barra.add(Box.createVerticalGlue());

        BotonGR asistente = new BotonGR("Asistente JOptionPane", BotonGR.Estilo.SECUNDARIO);
        BotonGR tema = new BotonGR(Tema.esOscuro() ? "Cambiar a modo claro" : "Cambiar a modo oscuro", BotonGR.Estilo.SECUNDARIO);
        for (BotonGR b : List.of(asistente, tema)) {
            b.setAlignmentX(Component.LEFT_ALIGNMENT);
            b.setMaximumSize(new Dimension(Integer.MAX_VALUE, 42));
        }
        asistente.addActionListener(e -> new AsistenteJOptionPane(contexto).iniciar(this));
        tema.addActionListener(e -> {
            Tema.alternar(this);
            tema.setText(Tema.esOscuro() ? "Cambiar a modo claro" : "Cambiar a modo oscuro");
        });
        barra.add(asistente);
        barra.add(Box.createVerticalStrut(8));
        barra.add(tema);
        return barra;
    }

    private JMenuBar menu() {
        JMenuBar barra = new JMenuBar();
        JMenu archivo = new JMenu("Archivo");
        JMenuItem salir = new JMenuItem("Salir");
        salir.addActionListener(e -> {
            int r = JOptionPane.showConfirmDialog(this, "¿Desea cerrar Green Route?", "Salir",
                    JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
            if (r == JOptionPane.YES_OPTION) dispose();
        });
        archivo.add(salir);
        JMenu ver = new JMenu("Ver");
        for (int i = 0; i < pantallas.size(); i++) {
            final int indice = i;
            JMenuItem item = new JMenuItem(pantallas.get(i).getTitulo());
            item.addActionListener(e -> mostrar(indice));
            ver.add(item);
        }
        JMenu ayuda = new JMenu("Ayuda");
        JMenuItem acerca = new JMenuItem("Acerca de Green Route");
        acerca.addActionListener(e -> JOptionPane.showMessageDialog(this,
                "<html><body style='width:320px'><h2 style='margin:0'>Green Route</h2>"
                        + "Proyecto integrador · Ingeniería de Datos e IA · T04<br><br>"
                        + String.format("Red cargada: <b>%,d</b> nodos y <b>%,d</b> tramos.<br>",
                        contexto.getPlanificador().getRed().totalNodos(), contexto.getPlanificador().getRed().totalTramos())
                        + "Flota: <b>" + contexto.getPlanificador().getFlota().tamano() + "</b> vehículos · Algoritmo: <b>"
                        + contexto.getPlanificador().getAlgoritmo().getClass().getSimpleName() + "</b></body></html>",
                "Acerca de", JOptionPane.INFORMATION_MESSAGE));
        ayuda.add(acerca);
        barra.add(archivo);
        barra.add(ver);
        barra.add(ayuda);
        return barra;
    }

    public void mostrar(int i) {
        Pantalla p = pantallas.get(i);
        tarjetas.show(area, p.getTitulo());
        for (int k = 0; k < botones.size(); k++) botones.get(k).setActivo(k == i);
        p.alMostrar();
    }

    public List<Pantalla> getPantallas() { return pantallas; }

    private static Image icono() {
        BufferedImage img = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        var g = img.createGraphics();
        g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(new java.awt.Color(0x12AD7A));
        g.fillOval(4, 4, 56, 56);
        g.setColor(java.awt.Color.WHITE);
        g.setFont(new java.awt.Font("Segoe UI", java.awt.Font.BOLD, 30));
        g.drawString("G", 21, 43);
        g.dispose();
        return img;
    }
}
