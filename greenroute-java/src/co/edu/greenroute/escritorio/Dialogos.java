package co.edu.greenroute.escritorio;

import co.edu.greenroute.excepciones.AutonomiaInsuficienteException;
import co.edu.greenroute.excepciones.CapacidadExcedidaException;
import co.edu.greenroute.excepciones.GreenRouteException;
import co.edu.greenroute.excepciones.RutaNoEncontradaException;
import co.edu.greenroute.excepciones.VehiculoIncompatibleException;
import co.edu.greenroute.ruteo.Ruta;

import javax.swing.JOptionPane;
import java.awt.Component;
import java.util.Locale;

/** Mensajes JOptionPane reutilizables (errores y resumen de una ruta). */
public final class Dialogos {

    private Dialogos() {
    }

    public static void error(Component padre, GreenRouteException e) {
        String titulo;
        int tipo;
        String consejo;
        if (e instanceof CapacidadExcedidaException c) {
            titulo = "Carga excedida";
            tipo = JOptionPane.WARNING_MESSAGE;
            consejo = String.format(Locale.ROOT, "Reduzca la carga a %.0f kg o elija un vehículo más grande.", c.getCapacidadKg());
        } else if (e instanceof AutonomiaInsuficienteException) {
            titulo = "Autonomía insuficiente";
            tipo = JOptionPane.WARNING_MESSAGE;
            consejo = "La batería no alcanza: use un vehículo con más autonomía o una parada de recarga.";
        } else if (e instanceof VehiculoIncompatibleException) {
            titulo = "Vehículo incompatible";
            tipo = JOptionPane.ERROR_MESSAGE;
            consejo = "La barcaza solo navega el río Magdalena; los camiones no pueden usar el río.";
        } else if (e instanceof RutaNoEncontradaException) {
            titulo = "Sin ruta";
            tipo = JOptionPane.ERROR_MESSAGE;
            consejo = "Elija un origen y un destino diferentes.";
        } else {
            titulo = "Green Route";
            tipo = JOptionPane.ERROR_MESSAGE;
            consejo = "";
        }
        JOptionPane.showMessageDialog(padre,
                "<html><body style='width:340px'><b>" + e.getMessage() + "</b><br><br>"
                        + "<span style='color:" + Tema.hex(Tema.tinta2()) + "'>" + consejo + "</span><br><br>"
                        + "<code>" + e.getClass().getSimpleName() + " · " + e.getCodigo() + "</code></body></html>",
                titulo, tipo);
    }

    public static String resumen(Ruta r, String origen, String destino) {
        String gris = Tema.hex(Tema.tinta2());
        String verde = Tema.hex(Tema.acento());
        return String.format(Locale.forLanguageTag("es-CO"), """
                <html><body style='width:360px'>
                <div style='font-size:15pt'><b>%s</b></div>
                <div style='color:%s'>%s → %s</div><br>
                <table cellpadding='3'>
                  <tr><td style='color:%s'>Distancia</td><td><b>%.2f km</b></td></tr>
                  <tr><td style='color:%s'>Tiempo</td><td><b>%.0f min</b></td></tr>
                  <tr><td style='color:%s'>Emisiones</td><td><b style='color:%s'>%.2f kg CO₂e</b></td></tr>
                  <tr><td style='color:%s'>Costo de energía</td><td><b>$ %,.0f COP</b></td></tr>
                  <tr><td style='color:%s'>Criterio</td><td>%s</td></tr>
                  <tr><td style='color:%s'>Algoritmo</td><td>%s · %,d nodos explorados</td></tr>
                </table></body></html>""",
                r.getVehiculo().getNombre(), gris, origen, destino,
                gris, r.getDistanciaKm(), gris, r.getTiempoMin(), gris, verde, r.getEmisionKgCO2(),
                gris, r.getCostoCOP(), gris, r.getCriterio().getEtiqueta(), gris, r.getAlgoritmo(),
                r.getNodosExplorados())
                .replace("\n", "");
    }
}
