package co.edu.greenroute.escritorio;

import co.edu.greenroute.analitica.PerfilHorario;
import co.edu.greenroute.excepciones.GreenRouteException;
import co.edu.greenroute.red.CargadorRed;
import co.edu.greenroute.red.RedTransporte;
import co.edu.greenroute.ruteo.AEstrella;
import co.edu.greenroute.ruteo.Criterio;
import co.edu.greenroute.servicio.PlanificadorRutas;
import co.edu.greenroute.vehiculos.Flota;

import javax.imageio.ImageIO;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import java.awt.Container;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/** Punto de entrada de la aplicación de escritorio (Swing). */
public final class AppEscritorio {

    private AppEscritorio() {
    }

    public static void main(String[] args) throws Exception {
        System.setProperty("awt.useSystemAAFontSettings", "lcd");
        System.setProperty("sun.java2d.uiScale", System.getProperty("sun.java2d.uiScale", "1"));
        Path base = Paths.get(System.getProperty("user.dir"));
        Path datos = base.resolve("datos");
        String capturas = null;
        for (String a : args) if (a.startsWith("--capturas=")) capturas = a.substring(11);

        Tema.aplicar();
        ContextoApp contexto;
        try {
            contexto = cargar(base, datos);
        } catch (GreenRouteException e) {
            JOptionPane.showMessageDialog(null, "No se pudieron cargar los datos:\n" + e.getMessage()
                    + "\n\nEjecute la aplicación desde la carpeta greenroute-java.", "Green Route", JOptionPane.ERROR_MESSAGE);
            return;
        }
        if (capturas != null) {
            ContextoApp c = contexto;
            Path destino = Paths.get(capturas);
            SwingUtilities.invokeAndWait(() -> capturar(c, destino));
            System.exit(0);
        }
        ContextoApp c = contexto;
        SwingUtilities.invokeLater(() -> menuInicial(c));
    }

    static ContextoApp cargar(Path base, Path datos) throws GreenRouteException {
        RedTransporte red = CargadorRed.cargar(datos);
        PerfilHorario perfil = PerfilHorario.cargar(datos.resolve("perfil_horario.csv"));
        PlanificadorRutas planificador = new PlanificadorRutas(red, Flota.flotaDeReferencia(), perfil, new AEstrella());
        List<PuntoLogistico> puntos = PuntoLogistico.cargar(datos.resolve("puntos.csv"));
        return new ContextoApp(planificador, puntos, base.resolve("src"));
    }

    private static void menuInicial(ContextoApp contexto) {
        String[] opciones = {"Abrir la aplicación", "Asistente paso a paso", "Salir"};
        int r = JOptionPane.showOptionDialog(null,
                String.format("<html><body style='width:330px'><h2 style='margin:0'>Green Route</h2>"
                                + "Rutas de carga de baja huella de carbono · Barranquilla<br><br>"
                                + "Red vial cargada: <b>%,d</b> intersecciones y <b>%,d</b> tramos.<br>"
                                + "¿Cómo desea empezar?</body></html>",
                        contexto.getPlanificador().getRed().totalNodos(), contexto.getPlanificador().getRed().totalTramos()),
                "Green Route", JOptionPane.DEFAULT_OPTION, JOptionPane.INFORMATION_MESSAGE, null, opciones, opciones[0]);
        switch (r) {
            case 0 -> new VentanaPrincipal(contexto).setVisible(true);
            case 1 -> {
                new AsistenteJOptionPane(contexto).iniciar(null);
                menuInicial(contexto);
            }
            default -> System.exit(0);
        }
    }

    private static void capturar(ContextoApp contexto, Path carpeta) {
        try {
            Files.createDirectories(carpeta);
            for (boolean oscuro : new boolean[]{true, false}) {
                if (Tema.esOscuro() != oscuro) {
                    Tema.alternar(new javax.swing.JWindow());
                }
                String sufijo = oscuro ? "" : "_claro";
                VentanaPrincipal v = new VentanaPrincipal(contexto);
                v.setSize(1440, 900);
                v.pack();
                v.setSize(1440, 900);
                v.validate();
                ((PantallaPlanificar) v.getPantallas().get(0)).preparar("Puerto", "Aeropuerto", 0, Criterio.EMISIONES, 600);
                guardar(v.getContentPane(), carpeta.resolve("escritorio_planificar" + sufijo + ".png"));
                v.mostrar(1);
                guardar(v.getContentPane(), carpeta.resolve("escritorio_flota" + sufijo + ".png"));
                v.mostrar(2);
                guardar(v.getContentPane(), carpeta.resolve("escritorio_herencia" + sufijo + ".png"));
                v.mostrar(3);
                ((PantallaCodigo) v.getPantallas().get(3)).abrirPorNombre("escritorio/AsistenteJOptionPane.java");
                guardar(v.getContentPane(), carpeta.resolve("escritorio_codigo" + sufijo + ".png"));
                v.dispose();
            }
            if (!Tema.esOscuro()) Tema.alternar(new javax.swing.JWindow());
            dialogo(new JOptionPane(new Object[]{"Paso 1 de 5 · ¿Desde dónde sale la carga?"}, JOptionPane.QUESTION_MESSAGE,
                    JOptionPane.OK_CANCEL_OPTION, null), contexto, true, carpeta.resolve("joption_origen.png"));
            String[] etiquetas = new String[Criterio.values().length];
            for (int i = 0; i < etiquetas.length; i++) etiquetas[i] = Criterio.values()[i].getEtiqueta();
            dialogo(new JOptionPane("Paso 5 de 5 · ¿Qué quiere optimizar?", JOptionPane.QUESTION_MESSAGE,
                    JOptionPane.DEFAULT_OPTION, null, etiquetas, etiquetas[0]), contexto, false, carpeta.resolve("joption_criterio.png"));
            var ruta = contexto.getPlanificador().planificar(contexto.punto("Puerto").getCoordenada(),
                    contexto.punto("Aeropuerto").getCoordenada(), contexto.getPlanificador().getFlota().getVehiculos().get(0),
                    Criterio.EMISIONES, 7, 600);
            dialogo(new JOptionPane(Dialogos.resumen(ruta, "Puerto de Barranquilla", "Aeropuerto Ernesto Cortissoz"),
                    JOptionPane.INFORMATION_MESSAGE), contexto, false, carpeta.resolve("joption_resultado.png"));
            try {
                contexto.getPlanificador().getFlota().getVehiculos().get(0).validarCarga(5000);
            } catch (GreenRouteException e) {
                dialogo(new JOptionPane("<html><body style='width:340px'><b>" + e.getMessage()
                        + "</b><br><br>Reduzca la carga o elija un vehículo más grande.<br><br><code>"
                        + e.getClass().getSimpleName() + " · " + e.getCodigo() + "</code></body></html>",
                        JOptionPane.WARNING_MESSAGE), contexto, false, carpeta.resolve("joption_error.png"));
            }
            System.out.println("Capturas guardadas en " + carpeta.toAbsolutePath());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void dialogo(JOptionPane panel, ContextoApp contexto, boolean conLista, Path archivo) throws IOException {
        if (conLista) {
            panel.setWantsInput(true);
            panel.setSelectionValues(contexto.getPuntos().toArray());
            panel.setInitialSelectionValue(contexto.punto("Puerto"));
        }
        JDialog d = panel.createDialog(null, "Green Route · Asistente");
        d.pack();
        guardar(d.getRootPane(), archivo, 2);
        d.dispose();
    }

    private static void guardar(Container c, Path archivo) throws IOException {
        guardar(c, archivo, 1);
    }

    private static void guardar(Container c, Path archivo, int escala) throws IOException {
        c.doLayout();
        c.validate();
        BufferedImage img = new BufferedImage(c.getWidth() * escala, c.getHeight() * escala, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.scale(escala, escala);
        if (c instanceof JComponent jc) jc.setDoubleBuffered(false);
        c.printAll(g);
        g.dispose();
        ImageIO.write(img, "png", new File(archivo.toString()));
    }
}
