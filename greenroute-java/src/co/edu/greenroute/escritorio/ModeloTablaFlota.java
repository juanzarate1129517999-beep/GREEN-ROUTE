package co.edu.greenroute.escritorio;

import co.edu.greenroute.servicio.OpcionTransporte;
import co.edu.greenroute.vehiculos.Recargable;
import co.edu.greenroute.vehiculos.Vehiculo;

import javax.swing.table.AbstractTableModel;
import java.util.ArrayList;
import java.util.List;

/** Modelo de la tabla que compara los vehículos de la flota. */
public class ModeloTablaFlota extends AbstractTableModel {

    private static final String[] COLUMNAS = {"Vehículo", "Clase Java", "Energía", "km", "min", "kg CO₂e", "Estado"};
    private final List<OpcionTransporte> opciones = new ArrayList<>();
    private double maximaEmision = 1;

    public void actualizar(List<OpcionTransporte> nuevas) {
        opciones.clear();
        opciones.addAll(nuevas);
        maximaEmision = nuevas.stream().filter(OpcionTransporte::esFactible)
                .mapToDouble(o -> o.getRuta().getEmisionKgCO2()).max().orElse(1);
        fireTableDataChanged();
    }

    public double getMaximaEmision() { return maximaEmision; }
    public OpcionTransporte getOpcion(int fila) { return opciones.get(fila); }

    @Override public int getRowCount() { return opciones.size(); }
    @Override public int getColumnCount() { return COLUMNAS.length; }
    @Override public String getColumnName(int c) { return COLUMNAS[c]; }

    @Override
    public Class<?> getColumnClass(int c) {
        return (c >= 3 && c <= 5) ? Double.class : String.class;
    }

    @Override
    public Object getValueAt(int fila, int col) {
        OpcionTransporte op = opciones.get(fila);
        Vehiculo v = op.getVehiculo();
        boolean ok = op.esFactible();
        return switch (col) {
            case 0 -> v.getNombre();
            case 1 -> v.getClass().getSimpleName();
            case 2 -> v.getTipoEnergia().getDescripcion();
            case 3 -> ok ? op.getRuta().getDistanciaKm() : null;
            case 4 -> ok ? op.getRuta().getTiempoMin() : null;
            case 5 -> ok ? op.getRuta().getEmisionKgCO2() : null;
            default -> ok ? (v instanceof Recargable ? "Factible · batería ok" : "Factible") : "No factible: solo navega el río";
        };
    }
}
