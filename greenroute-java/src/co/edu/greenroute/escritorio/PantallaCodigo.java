package co.edu.greenroute.escritorio;

import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextPane;
import javax.swing.ListSelectionModel;
import javax.swing.text.BadLocationException;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Pantalla que muestra el código fuente Java del proyecto. */
public class PantallaCodigo extends Pantalla {

    private static final String[][] ARCHIVOS = {
            {"vehiculos/Vehiculo.java", "Abstracción · encapsulamiento · método plantilla"},
            {"vehiculos/VehiculoTerrestre.java", "Herencia nivel 2 (abstracta)"},
            {"vehiculos/CamionDiesel.java", "Herencia nivel 3"},
            {"vehiculos/Tractocamion.java", "Herencia nivel 4 · super(...)"},
            {"vehiculos/CamionElectrico.java", "extends + implements Recargable"},
            {"vehiculos/Barcaza.java", "Polimorfismo: puedeCircularPor()"},
            {"vehiculos/Recargable.java", "Interfaz con métodos default"},
            {"vehiculos/Emisor.java", "Interfaz"},
            {"ruteo/BusquedaCaminoMinimo.java", "Template Method (Dijkstra / A*)"},
            {"ruteo/AEstrella.java", "Herencia: solo cambia la heurística"},
            {"ruteo/Criterio.java", "Enum + Factory + Strategy"},
            {"excepciones/GreenRouteException.java", "Excepción propia (raíz)"},
            {"excepciones/CapacidadExcedidaException.java", "Subclase de excepción"},
            {"servicio/PlanificadorRutas.java", "Facade · composición · try/catch"},
            {"escritorio/AsistenteJOptionPane.java", "JOptionPane: entrada, confirmación, opciones"},
            {"escritorio/Pantalla.java", "Interfaz gráfica: clase abstracta"},
            {"escritorio/BotonGR.java", "extends JButton · paintComponent()"},
            {"escritorio/Dialogos.java", "switch con patrones sobre excepciones"},
    };

    private static final Pattern TOKENS = Pattern.compile(
            "(?<comentario>//[^\\n]*|/\\*[\\s\\S]*?\\*/)"
                    + "|(?<cadena>\"(?:\\\\.|[^\"\\\\])*\")"
                    + "|(?<poo>\\b(?:extends|implements|abstract|interface|super|JOptionPane|instanceof)\\b|@Override)"
                    + "|(?<clave>\\b(?:public|private|protected|static|final|class|enum|record|return|new|if|else|for|while|switch|case|default|try|catch|throw|throws|this|void|boolean|int|double|long|float|char|var|import|package|null|true|false)\\b)"
                    + "|(?<numero>\\b\\d[\\d_.]*\\b)");

    private JTextPane editor;
    private JTextArea numeros;
    private JLabel lblRuta;

    public PantallaCodigo(ContextoApp contexto) {
        super(contexto);
    }

    @Override public String getTitulo() { return "Código Java"; }
    @Override public String getSubtitulo() { return "Fuentes reales del proyecto (greenroute-java/src). En verde: extends, implements, abstract, @Override, JOptionPane."; }

    @Override
    protected JComponent construirContenido() {
        JList<String[]> lista = new JList<>(ARCHIVOS);
        lista.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        lista.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> l, Object v, int i, boolean sel, boolean foco) {
                String[] a = (String[]) v;
                String nombre = a[0].substring(a[0].indexOf('/') + 1);
                super.getListCellRendererComponent(l, "<html><b>" + nombre + "</b><br><span style='color:"
                        + Tema.hex(Tema.tenue()) + "'>" + a[1] + "</span></html>", i, sel, false);
                setBorder(BorderFactory.createEmptyBorder(8, 12, 8, 12));
                setBackground(sel ? Tema.borde() : Tema.panel());
                setForeground(Tema.tinta());
                return this;
            }
        });
        lista.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && lista.getSelectedValue() != null) abrir(lista.getSelectedValue()[0]);
        });

        editor = new JTextPane();
        editor.setEditable(false);
        editor.setFont(Tema.mono(14));
        numeros = new JTextArea();
        numeros.setEditable(false);
        numeros.setFont(Tema.mono(14));
        numeros.setBorder(BorderFactory.createEmptyBorder(3, 10, 0, 10));
        JScrollPane scrollCodigo = new JScrollPane(editor);
        scrollCodigo.setRowHeaderView(numeros);
        scrollCodigo.setBorder(BorderFactory.createLineBorder(Tema.borde()));
        scrollCodigo.getVerticalScrollBar().setUnitIncrement(18);

        lblRuta = new JLabel(" ");
        lblRuta.setFont(Tema.mono(12));
        JPanel derecha = new JPanel(new BorderLayout(0, 8));
        derecha.setOpaque(false);
        derecha.add(lblRuta, BorderLayout.NORTH);
        derecha.add(scrollCodigo, BorderLayout.CENTER);

        JScrollPane scrollLista = new JScrollPane(lista);
        scrollLista.setPreferredSize(new Dimension(330, 400));
        scrollLista.setBorder(BorderFactory.createLineBorder(Tema.borde()));
        JPanel cuerpo = new JPanel(new BorderLayout(18, 0));
        cuerpo.setOpaque(false);
        cuerpo.add(scrollLista, BorderLayout.WEST);
        cuerpo.add(derecha, BorderLayout.CENTER);
        lista.setSelectedIndex(3);
        return cuerpo;
    }

    public void abrir(String relativo) {
        Path archivo = contexto.getCarpetaFuentes().resolve("co/edu/greenroute").resolve(relativo);
        String texto;
        try {
            texto = Files.readString(archivo, StandardCharsets.UTF_8).replace("\r\n", "\n");
        } catch (IOException e) {
            texto = "// No se encontró " + archivo + "\n// Ejecute la aplicación desde la carpeta greenroute-java.";
        }
        lblRuta.setText("src/co/edu/greenroute/" + relativo + "   ·   " + texto.split("\n").length + " líneas");
        lblRuta.setForeground(Tema.tinta2());
        resaltar(texto);
        StringBuilder sb = new StringBuilder();
        List<String> lineas = texto.lines().toList();
        for (int i = 1; i <= lineas.size(); i++) sb.append(i).append('\n');
        numeros.setText(sb.toString());
        numeros.setBackground(Tema.panel());
        numeros.setForeground(Tema.tenue());
        editor.setCaretPosition(0);
    }

    private void resaltar(String texto) {
        editor.setText("");
        editor.setBackground(Tema.elevado());
        StyledDocument doc = editor.getStyledDocument();
        SimpleAttributeSet base = estilo(Tema.tinta(), false);
        try {
            doc.insertString(0, texto, base);
            Matcher m = TOKENS.matcher(texto);
            while (m.find()) {
                SimpleAttributeSet s;
                if (m.group("comentario") != null) s = estilo(Tema.tenue(), false);
                else if (m.group("cadena") != null) s = estilo(Tema.esOscuro() ? new Color(0xE5C07B) : new Color(0x9A6700), false);
                else if (m.group("poo") != null) s = estilo(Tema.acento(), true);
                else if (m.group("clave") != null) s = estilo(Tema.esOscuro() ? new Color(0x8FB4FF) : new Color(0x1F4FBF), false);
                else s = estilo(Tema.esOscuro() ? new Color(0xF0A070) : new Color(0xB54708), false);
                doc.setCharacterAttributes(m.start(), m.end() - m.start(), s, true);
            }
        } catch (BadLocationException e) {
            editor.setText(texto);
        }
    }

    private static SimpleAttributeSet estilo(Color color, boolean negrita) {
        SimpleAttributeSet s = new SimpleAttributeSet();
        StyleConstants.setForeground(s, color);
        StyleConstants.setBold(s, negrita);
        StyleConstants.setFontFamily(s, "Consolas");
        StyleConstants.setFontSize(s, 14);
        return s;
    }

    void abrirPorNombre(String relativo) {
        abrir(relativo);
    }
}
