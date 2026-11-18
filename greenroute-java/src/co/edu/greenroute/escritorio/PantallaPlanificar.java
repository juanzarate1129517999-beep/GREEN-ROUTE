package co.edu.greenroute.escritorio;

import co.edu.greenroute.excepciones.GreenRouteException;
import co.edu.greenroute.ruteo.Criterio;
import co.edu.greenroute.ruteo.Ruta;
import co.edu.greenroute.vehiculos.Vehiculo;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.util.Locale;

/** Pantalla principal: elegir origen, destino, vehículo y criterio, y ver la ruta. */
public class PantallaPlanificar extends Pantalla {

    private JComboBox<PuntoLogistico> cbOrigen;
    private JComboBox<PuntoLogistico> cbDestino;
    private JComboBox<Vehiculo> cbVehiculo;
    private JComboBox<Criterio> cbCriterio;
    private JSpinner spCarga;
    private JSlider slHora;
    private JLabel lblHora;
    private JLabel lblResultado;
    private PanelMapa mapa;

    public PantallaPlanificar(ContextoApp contexto) {
        super(contexto);
    }

    @Override public String getTitulo() { return "Planificar ruta"; }
    @Override public String getSubtitulo() { return "Elija origen, destino, vehículo y criterio: el motor A* calcula la ruta sobre la malla vial real."; }

    @Override
    protected JComponent construirContenido() {
        cbOrigen = new JComboBox<>(contexto.getPuntos().toArray(new PuntoLogistico[0]));
        cbDestino = new JComboBox<>(contexto.getPuntos().toArray(new PuntoLogistico[0]));
        cbOrigen.setSelectedItem(contexto.punto("Puerto"));
        cbDestino.setSelectedItem(contexto.punto("Aeropuerto"));
        cbVehiculo = new JComboBox<>(contexto.getPlanificador().getFlota().getVehiculos().toArray(new Vehiculo[0]));
        cbVehiculo.setRenderer((lista, v, i, sel, foco) -> new JLabel(v == null ? "" : v.getNombre()) {{
            setOpaque(true);
            setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
            setBackground(sel ? Tema.borde() : Tema.elevado());
            setForeground(Tema.tinta());
        }});
        cbCriterio = new JComboBox<>(Criterio.values());
        cbCriterio.setRenderer((lista, c, i, sel, foco) -> new JLabel(c == null ? "" : c.getEtiqueta()) {{
            setOpaque(true);
            setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
            setBackground(sel ? Tema.borde() : Tema.elevado());
            setForeground(Tema.tinta());
        }});
        spCarga = new JSpinner(new SpinnerNumberModel(600, 50, 40_000, 50));
        slHora = new JSlider(0, 23, 7);
        lblHora = new JLabel();
        slHora.addChangeListener(e -> lblHora.setText(String.format("Salida: %02d:00", slHora.getValue())));
        lblHora.setText("Salida: 07:00");

        BotonGR calcular = new BotonGR("Calcular ruta", BotonGR.Estilo.PRIMARIO);
        BotonGR asistente = new BotonGR("Asistente paso a paso", BotonGR.Estilo.SECUNDARIO);
        calcular.addActionListener(e -> calcular());
        asistente.addActionListener(e -> new AsistenteJOptionPane(contexto).iniciar(this));

        Tarjeta formulario = new Tarjeta(null);
        formulario.setLayout(new BoxLayout(formulario, BoxLayout.Y_AXIS));
        campo(formulario, "Origen", cbOrigen);
        campo(formulario, "Destino", cbDestino);
        campo(formulario, "Vehículo", cbVehiculo);
        campo(formulario, "Criterio de optimización", cbCriterio);
        campo(formulario, "Carga (kg)", spCarga);
        campo(formulario, "Hora de salida", slHora);
        formulario.add(lblHora);
        formulario.add(Box.createVerticalStrut(16));
        JPanel botones = new JPanel(new GridLayout(1, 2, 10, 0));
        botones.setOpaque(false);
        botones.setAlignmentX(Component.LEFT_ALIGNMENT);
        botones.add(calcular);
        botones.add(asistente);
        botones.setMaximumSize(new Dimension(Integer.MAX_VALUE, 44));
        formulario.add(botones);
        formulario.add(Box.createVerticalStrut(18));
        lblResultado = new JLabel("<html>Presione <b>Calcular ruta</b>.</html>");
        lblResultado.setAlignmentX(Component.LEFT_ALIGNMENT);
        lblResultado.setVerticalAlignment(JLabel.TOP);
        formulario.add(lblResultado);
        formulario.setPreferredSize(new Dimension(430, 600));

        mapa = new PanelMapa(contexto.getPlanificador().getRed());
        JPanel cuerpo = new JPanel(new BorderLayout(20, 0));
        cuerpo.setOpaque(false);
        cuerpo.add(formulario, BorderLayout.WEST);
        cuerpo.add(mapa, BorderLayout.CENTER);
        return cuerpo;
    }

    private static void campo(JPanel destino, String rotulo, JComponent control) {
        JLabel r = rotulo(rotulo);
        r.setAlignmentX(Component.LEFT_ALIGNMENT);
        r.setBorder(BorderFactory.createEmptyBorder(0, 0, 6, 0));
        control.setAlignmentX(Component.LEFT_ALIGNMENT);
        control.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));
        destino.add(r);
        destino.add(control);
        destino.add(Box.createVerticalStrut(12));
    }

    public void calcular() {
        PuntoLogistico o = (PuntoLogistico) cbOrigen.getSelectedItem();
        PuntoLogistico d = (PuntoLogistico) cbDestino.getSelectedItem();
        Vehiculo v = (Vehiculo) cbVehiculo.getSelectedItem();
        Criterio c = (Criterio) cbCriterio.getSelectedItem();
        double carga = ((Number) spCarga.getValue()).doubleValue();
        try {
            Ruta r = contexto.getPlanificador().planificar(o.getCoordenada(), d.getCoordenada(), v, c,
                    slHora.getValue(), carga);
            mapa.mostrar(r, o.getCoordenada(), d.getCoordenada());
            lblResultado.setText(Dialogos.resumen(r, o.getNombre(), d.getNombre()));
        } catch (GreenRouteException ex) {
            mapa.mostrar(null, o.getCoordenada(), d.getCoordenada());
            lblResultado.setText(String.format(Locale.ROOT, "<html><b>Sin ruta:</b> %s</html>", ex.getMessage()));
            Dialogos.error(this, ex);
        }
    }

    void preparar(String origen, String destino, int indiceVehiculo, Criterio criterio, double carga) {
        cbOrigen.setSelectedItem(contexto.punto(origen));
        cbDestino.setSelectedItem(contexto.punto(destino));
        cbVehiculo.setSelectedIndex(indiceVehiculo);
        cbCriterio.setSelectedItem(criterio);
        spCarga.setValue((int) carga);
        calcular();
    }
}
