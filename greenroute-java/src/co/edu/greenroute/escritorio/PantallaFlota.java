package co.edu.greenroute.escritorio;

import co.edu.greenroute.ruteo.Criterio;
import co.edu.greenroute.servicio.OpcionTransporte;

import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** Pantalla que compara emisiones, tiempo y costo de todos los vehículos. */
public class PantallaFlota extends Pantalla {

    private ModeloTablaFlota modelo;
    private JLabel lblConclusion;

    public PantallaFlota(ContextoApp contexto) {
        super(contexto);
    }

    @Override public String getTitulo() { return "Comparar flota"; }
    @Override public String getSubtitulo() { return "Puerto → Aeropuerto con 600 kg a las 7:00: una misma llamada, siete respuestas distintas."; }

    @Override
    protected JComponent construirContenido() {
        modelo = new ModeloTablaFlota();
        JTable tabla = new JTable(modelo) {
            @Override
            protected void paintComponent(Graphics g) {
                setBackground(Tema.elevado());
                setForeground(Tema.tinta());
                setGridColor(Tema.borde());
                getTableHeader().setBackground(Tema.panel());
                getTableHeader().setForeground(Tema.tinta2());
                super.paintComponent(g);
            }
        };
        tabla.setRowHeight(32);
        tabla.setFont(Tema.texto(14));
        tabla.getTableHeader().setFont(Tema.fuerte(13));
        tabla.setShowVerticalLines(false);
        tabla.getColumnModel().getColumn(5).setCellRenderer(new RenderBarra(modelo));
        tabla.getColumnModel().getColumn(5).setPreferredWidth(220);
        tabla.getColumnModel().getColumn(0).setPreferredWidth(190);
        tabla.getColumnModel().getColumn(2).setPreferredWidth(180);
        tabla.getColumnModel().getColumn(1).setPreferredWidth(190);
        tabla.getColumnModel().getColumn(6).setPreferredWidth(230);
        tabla.getColumnModel().getColumn(3).setPreferredWidth(70);
        tabla.getColumnModel().getColumn(4).setPreferredWidth(70);
        tabla.getColumnModel().getColumn(1).setCellRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object v, boolean s, boolean f, int r, int c) {
                super.getTableCellRendererComponent(t, v, s, f, r, c);
                setFont(Tema.mono(13));
                setForeground(Tema.acento());
                return this;
            }
        });
        DefaultTableCellRenderer numeros = new DefaultTableCellRenderer() {
            @Override
            protected void setValue(Object v) {
                setHorizontalAlignment(RIGHT);
                setText(v == null ? "—" : String.format(Locale.forLanguageTag("es-CO"), "%.1f", (Double) v));
            }
        };
        tabla.getColumnModel().getColumn(3).setCellRenderer(numeros);
        tabla.getColumnModel().getColumn(4).setCellRenderer(numeros);
        tabla.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 2 && tabla.getSelectedRow() >= 0) detalle(modelo.getOpcion(tabla.getSelectedRow()));
            }
        });

        JScrollPane scroll = new JScrollPane(tabla);
        scroll.setBorder(BorderFactory.createLineBorder(Tema.borde()));
        scroll.getViewport().setBackground(Tema.elevado());

        BotonGR recalcular = new BotonGR("Volver a comparar", BotonGR.Estilo.PRIMARIO);
        recalcular.addActionListener(e -> comparar());
        lblConclusion = new JLabel(" ");
        lblConclusion.setFont(Tema.texto(15));
        JPanel pie = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        pie.setOpaque(false);
        pie.add(recalcular);
        pie.add(javax.swing.Box.createHorizontalStrut(18));
        pie.add(lblConclusion);

        scroll.setPreferredSize(new Dimension(900, 32 * 7 + 32));

        Tarjeta codigo = new Tarjeta(new BorderLayout(0, 10));
        JLabel titulo = rotulo("El código detrás de la tabla · PlanificadorRutas.compararFlota()");
        JLabel fragmento = new JLabel(htmlFragmento()) {
            private boolean temaOscuro = Tema.esOscuro();

            @Override
            protected void paintComponent(Graphics g) {
                if (temaOscuro != Tema.esOscuro()) {
                    temaOscuro = Tema.esOscuro();
                    setText(htmlFragmento());
                }
                super.paintComponent(g);
            }
        };
        codigo.add(titulo, BorderLayout.NORTH);
        codigo.add(fragmento, BorderLayout.CENTER);
        return armar(scroll, codigo, pie);
    }

    private static String htmlFragmento() {
        String v = Tema.hex(Tema.acento()), c = Tema.hex(Tema.tenue()), t = Tema.hex(Tema.tinta());
        return "<html><pre style='font-family:Consolas;font-size:11pt;color:" + t + "'>"
                        + "<b style='color:" + v + "'>for</b> (Vehiculo v : flota.getVehiculos()) {   <span style='color:" + c + "'>// List&lt;Vehiculo&gt;: tipo abstracto</span>\n"
                        + "    <b style='color:" + v + "'>try</b> {\n"
                        + "        Ruta r = planificar(o, d, v, criterio, hora, carga);  <span style='color:" + c + "'>// polimorfismo: cada clase calcula su consumo</span>\n"
                        + "        opciones.add(OpcionTransporte.factible(v, r));\n"
                        + "    } <b style='color:" + v + "'>catch</b> (GreenRouteException e) {               <span style='color:" + c + "'>// excepción propia: barcaza fuera del río</span>\n"
                        + "        opciones.add(OpcionTransporte.noFactible(v, e));\n"
                        + "    }\n}</pre></html>";
    }

    private static JComponent armar(JScrollPane scroll, Tarjeta codigo, JPanel pie) {
        JPanel arriba = new JPanel(new BorderLayout(0, 18));
        arriba.setOpaque(false);
        arriba.add(scroll, BorderLayout.NORTH);
        arriba.add(codigo, BorderLayout.CENTER);
        JPanel cuerpo = new JPanel(new BorderLayout(0, 18));
        cuerpo.setOpaque(false);
        cuerpo.add(arriba, BorderLayout.NORTH);
        cuerpo.add(pie, BorderLayout.SOUTH);
        return cuerpo;
    }

    @Override
    public void alMostrar() {
        if (modelo.getRowCount() == 0) comparar();
    }

    public void comparar() {
        PuntoLogistico o = contexto.punto("Puerto"), d = contexto.punto("Aeropuerto");
        List<OpcionTransporte> ops = contexto.getPlanificador()
                .compararFlota(o.getCoordenada(), d.getCoordenada(), 7, 600, Criterio.EMISIONES);
        modelo.actualizar(ops);
        Optional<OpcionTransporte> mejor = contexto.getPlanificador().recomendar(ops);
        double peor = modelo.getMaximaEmision();
        mejor.ifPresent(m -> lblConclusion.setText(String.format(Locale.forLanguageTag("es-CO"),
                "<html>Recomendado: <b>%s</b> · %.2f kg CO₂e, <b>%.0f %% menos</b> que la peor opción. "
                        + "<span style='color:%s'>(doble clic en una fila para ver el detalle)</span></html>",
                m.getVehiculo().getNombre(), m.getRuta().getEmisionKgCO2(),
                (1 - m.getRuta().getEmisionKgCO2() / peor) * 100, Tema.hex(Tema.tenue()))));
    }

    private void detalle(OpcionTransporte op) {
        if (op.esFactible()) {
            JOptionPane.showMessageDialog(this, Dialogos.resumen(op.getRuta(), "Puerto", "Aeropuerto"),
                    op.getVehiculo().getClass().getSimpleName(), JOptionPane.INFORMATION_MESSAGE);
        } else {
            JOptionPane.showMessageDialog(this, "<html><body style='width:320px'>" + op.aMapa().get("motivo") + "</body></html>",
                    op.getVehiculo().getNombre() + " · no factible", JOptionPane.WARNING_MESSAGE);
        }
    }
}
