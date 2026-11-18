package co.edu.greenroute.escritorio;

import co.edu.greenroute.excepciones.GreenRouteException;
import co.edu.greenroute.ruteo.Criterio;
import co.edu.greenroute.ruteo.Ruta;
import co.edu.greenroute.servicio.OpcionTransporte;
import co.edu.greenroute.vehiculos.Vehiculo;

import javax.swing.JOptionPane;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import java.awt.Component;
import java.awt.Dimension;
import java.util.List;
import java.util.Locale;

/** Asistente paso a paso con cuadros JOptionPane para planificar un envío. */
public class AsistenteJOptionPane {

    private static final String TITULO = "Green Route · Asistente";
    private final ContextoApp contexto;

    public AsistenteJOptionPane(ContextoApp contexto) {
        this.contexto = contexto;
    }

    public void iniciar(Component padre) {
        JOptionPane.showMessageDialog(padre,
                "<html><body style='width:330px'><h2 style='margin:0'>Planificador de envíos</h2><br>"
                        + "Le haré unas preguntas para calcular la ruta de <b>menor huella de carbono</b> "
                        + "entre dos puntos logísticos de Barranquilla.<br><br>"
                        + "Puede cancelar en cualquier momento.</body></html>",
                TITULO, JOptionPane.INFORMATION_MESSAGE);
        do {
            if (!planificarUnEnvio(padre)) return;
        } while (JOptionPane.showConfirmDialog(padre, "¿Desea planificar otro envío?", TITULO,
                JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE) == JOptionPane.YES_OPTION);
        JOptionPane.showMessageDialog(padre, "Gracias por usar Green Route. ¡Buen viaje!", TITULO,
                JOptionPane.INFORMATION_MESSAGE);
    }

    private boolean planificarUnEnvio(Component padre) {
        PuntoLogistico[] puntos = contexto.getPuntos().toArray(new PuntoLogistico[0]);

        PuntoLogistico origen = (PuntoLogistico) JOptionPane.showInputDialog(padre,
                "Paso 1 de 5 · ¿Desde dónde sale la carga?", TITULO, JOptionPane.QUESTION_MESSAGE,
                null, puntos, contexto.punto("Puerto"));
        if (origen == null) return false;
        PuntoLogistico destino;
        do {
            destino = (PuntoLogistico) JOptionPane.showInputDialog(padre,
                    "Paso 2 de 5 · ¿A dónde va?", TITULO, JOptionPane.QUESTION_MESSAGE,
                    null, puntos, contexto.punto("Aeropuerto"));
            if (destino == null) return false;
            if (destino == origen) {
                JOptionPane.showMessageDialog(padre, "El destino debe ser diferente del origen.", TITULO,
                        JOptionPane.WARNING_MESSAGE);
            }
        } while (destino == origen);

        double carga = pedirCarga(padre);
        if (carga < 0) return false;

        String[] horas = new String[24];
        for (int h = 0; h < 24; h++) horas[h] = String.format("%02d:00", h);
        String hora = (String) JOptionPane.showInputDialog(padre, "Paso 4 de 5 · Hora de salida",
                TITULO, JOptionPane.QUESTION_MESSAGE, null, horas, horas[7]);
        if (hora == null) return false;
        int h = Integer.parseInt(hora.substring(0, 2));

        Criterio[] criterios = Criterio.values();
        String[] etiquetas = new String[criterios.length];
        for (int i = 0; i < criterios.length; i++) etiquetas[i] = criterios[i].getEtiqueta();
        int elegido = JOptionPane.showOptionDialog(padre, "Paso 5 de 5 · ¿Qué quiere optimizar?", TITULO,
                JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE, null, etiquetas, etiquetas[0]);
        if (elegido < 0) return false;
        Criterio criterio = criterios[elegido];

        List<Vehiculo> flota = contexto.getPlanificador().getFlota().getVehiculos();
        while (true) {
            Vehiculo vehiculo = (Vehiculo) JOptionPane.showInputDialog(padre,
                    String.format(Locale.forLanguageTag("es-CO"), "¿Con qué vehículo? (carga: %,.0f kg)", carga),
                    TITULO, JOptionPane.QUESTION_MESSAGE, null, flota.toArray(), flota.get(0));
            if (vehiculo == null) return false;
            try {
                Ruta ruta = contexto.getPlanificador().planificar(origen.getCoordenada(), destino.getCoordenada(),
                        vehiculo, criterio, h, carga);
                JOptionPane.showMessageDialog(padre, Dialogos.resumen(ruta, origen.getNombre(), destino.getNombre()),
                        "Ruta calculada", JOptionPane.INFORMATION_MESSAGE);
                break;
            } catch (GreenRouteException e) {
                Dialogos.error(padre, e);
                int otra = JOptionPane.showConfirmDialog(padre, "¿Desea intentar con otro vehículo?", TITULO,
                        JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
                if (otra != JOptionPane.YES_OPTION) return true;
            }
        }

        if (JOptionPane.showConfirmDialog(padre, "¿Quiere comparar este envío con toda la flota?", TITULO,
                JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE) == JOptionPane.YES_OPTION) {
            mostrarComparacion(padre, origen, destino, h, carga, criterio);
        }
        return true;
    }

    private double pedirCarga(Component padre) {
        while (true) {
            String texto = JOptionPane.showInputDialog(padre, "Paso 3 de 5 · Peso de la carga en kilogramos:", "600");
            if (texto == null) return -1;
            try {
                double kg = Double.parseDouble(texto.trim().replace(",", "."));
                if (kg <= 0) throw new NumberFormatException("debe ser positivo");
                return kg;
            } catch (NumberFormatException e) {
                JOptionPane.showMessageDialog(padre, "«" + texto + "» no es un peso válido. Escriba un número mayor que 0.",
                        "Dato inválido", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void mostrarComparacion(Component padre, PuntoLogistico o, PuntoLogistico d, int h, double carga,
                                    Criterio criterio) {
        List<OpcionTransporte> ops = contexto.getPlanificador()
                .compararFlota(o.getCoordenada(), d.getCoordenada(), h, carga, criterio);
        ModeloTablaFlota modelo = new ModeloTablaFlota();
        modelo.actualizar(ops);
        JTable tabla = new JTable(modelo);
        tabla.setRowHeight(30);
        tabla.getColumnModel().getColumn(5).setCellRenderer(new RenderBarra(modelo));
        JScrollPane scroll = new JScrollPane(tabla);
        scroll.setPreferredSize(new Dimension(820, 270));
        JOptionPane.showMessageDialog(padre, scroll, "Comparación de la flota · " + criterio.getEtiqueta(),
                JOptionPane.PLAIN_MESSAGE);
    }
}
