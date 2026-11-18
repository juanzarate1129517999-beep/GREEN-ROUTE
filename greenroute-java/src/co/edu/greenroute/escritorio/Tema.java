package co.edu.greenroute.escritorio;

import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.plaf.ColorUIResource;
import javax.swing.plaf.FontUIResource;
import javax.swing.plaf.metal.DefaultMetalTheme;
import javax.swing.plaf.metal.MetalLookAndFeel;
import java.awt.Color;
import java.awt.Font;
import java.awt.Window;

/** Colores y fuentes de la app (modo oscuro y claro). */
public final class Tema {

    private static boolean oscuro = true;

    private Tema() {
    }

    public static boolean esOscuro() { return oscuro; }
    public static Color fondo()    { return oscuro ? new Color(0x000000) : new Color(0xFFFFFF); }
    public static Color panel()    { return oscuro ? new Color(0x0E0E0E) : new Color(0xF5F5F5); }
    public static Color elevado()  { return oscuro ? new Color(0x171717) : new Color(0xFFFFFF); }
    public static Color borde()    { return oscuro ? new Color(0x2A2A2A) : new Color(0xDEDEDE); }
    public static Color tinta()    { return oscuro ? new Color(0xFAFAFA) : new Color(0x0A0A0A); }
    public static Color tinta2()   { return oscuro ? new Color(0xA3A3A3) : new Color(0x525252); }
    public static Color tenue()    { return oscuro ? new Color(0x6B6B6B) : new Color(0x8A8A8A); }
    public static Color acento()   { return oscuro ? new Color(0x12AD7A) : new Color(0x1B7F4C); }
    public static Color alerta()   { return oscuro ? new Color(0xE8662F) : new Color(0xC2410C); }
    public static Color fluvial()  { return oscuro ? new Color(0x1E9AD6) : new Color(0x2A78D6); }
    public static Color red()      { return oscuro ? new Color(0x3A3A3A) : new Color(0xC9C9C9); }

    public static Font texto(float tam)   { return new Font("Segoe UI", Font.PLAIN, Math.round(tam)); }
    public static Font fuerte(float tam)  { return new Font("Segoe UI Semibold", Font.PLAIN, Math.round(tam)); }
    public static Font titulo(float tam)  { return new Font("Segoe UI", Font.BOLD, Math.round(tam)); }
    public static Font mono(float tam)    { return new Font("Consolas", Font.PLAIN, Math.round(tam)); }

    public static void aplicar() {
        try {
            MetalLookAndFeel.setCurrentTheme(new TemaGreenRoute());
            UIManager.setLookAndFeel(new MetalLookAndFeel());
        } catch (Exception e) {
            System.err.println("No se pudo aplicar el tema: " + e.getMessage());
        }
        UIManager.put("OptionPane.background", panel());
        UIManager.put("Panel.background", panel());
        UIManager.put("OptionPane.messageForeground", tinta());
        UIManager.put("OptionPane.messageFont", texto(14));
        UIManager.put("OptionPane.buttonFont", fuerte(13));
        UIManager.put("Table.gridColor", borde());
        UIManager.put("Table.rowHeight", 30);
        UIManager.put("TableHeader.background", elevado());
        UIManager.put("ScrollBar.width", 10);
        UIManager.put("ToolTip.background", elevado());
        UIManager.put("ToolTip.foreground", tinta());
        UIManager.put("OptionPane.informationIcon", new IconoDialogo(IconoDialogo.Tipo.INFORMACION));
        UIManager.put("OptionPane.questionIcon", new IconoDialogo(IconoDialogo.Tipo.PREGUNTA));
        UIManager.put("OptionPane.warningIcon", new IconoDialogo(IconoDialogo.Tipo.ADVERTENCIA));
        UIManager.put("OptionPane.errorIcon", new IconoDialogo(IconoDialogo.Tipo.ERROR));
    }

    public static void alternar(Window ventana) {
        oscuro = !oscuro;
        aplicar();
        SwingUtilities.updateComponentTreeUI(ventana);
        ventana.repaint();
    }

    public static String hex(Color c) {
        return String.format("#%02x%02x%02x", c.getRed(), c.getGreen(), c.getBlue());
    }

    static class TemaGreenRoute extends DefaultMetalTheme {
        @Override public String getName() { return "Green Route"; }
        @Override protected ColorUIResource getPrimary1()   { return new ColorUIResource(acento()); }
        @Override protected ColorUIResource getPrimary2()   { return new ColorUIResource(acento()); }
        @Override protected ColorUIResource getPrimary3()   { return new ColorUIResource(oscuro ? new Color(0x0F3D2C) : new Color(0xD6F2E6)); }
        @Override protected ColorUIResource getSecondary1() { return new ColorUIResource(tenue()); }
        @Override protected ColorUIResource getSecondary2() { return new ColorUIResource(borde()); }
        @Override protected ColorUIResource getSecondary3() { return new ColorUIResource(panel()); }
        @Override protected ColorUIResource getBlack()      { return new ColorUIResource(tinta()); }
        @Override protected ColorUIResource getWhite()      { return new ColorUIResource(elevado()); }
        @Override public ColorUIResource getFocusColor()    { return new ColorUIResource(acento()); }
        @Override public ColorUIResource getHighlightedTextColor() { return new ColorUIResource(tinta()); }
        @Override public FontUIResource getControlTextFont() { return new FontUIResource(texto(13)); }
        @Override public FontUIResource getSystemTextFont()  { return new FontUIResource(texto(13)); }
        @Override public FontUIResource getUserTextFont()    { return new FontUIResource(texto(13)); }
        @Override public FontUIResource getMenuTextFont()    { return new FontUIResource(texto(13)); }
        @Override public FontUIResource getWindowTitleFont() { return new FontUIResource(fuerte(13)); }
        @Override public FontUIResource getSubTextFont()     { return new FontUIResource(texto(11)); }
    }
}
