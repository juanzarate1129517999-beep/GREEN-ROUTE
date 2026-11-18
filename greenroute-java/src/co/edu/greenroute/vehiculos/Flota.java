package co.edu.greenroute.vehiculos;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/** Conjunto de vehículos de la empresa. */
public class Flota {

    private final String empresa;
    private final List<Vehiculo> vehiculos = new ArrayList<>();

    public Flota(String empresa) {
        this.empresa = empresa;
    }

    public void agregar(Vehiculo vehiculo) {
        if (vehiculos.contains(vehiculo)) {
            throw new IllegalArgumentException("Ya existe un vehículo con id " + vehiculo.getId());
        }
        vehiculos.add(vehiculo);
    }

    public Optional<Vehiculo> buscar(String id) {
        return vehiculos.stream().filter(v -> v.getId().equalsIgnoreCase(id)).findFirst();
    }

    public List<Vehiculo> conCapacidadPara(double cargaKg) {
        return vehiculos.stream().filter(v -> v.getCapacidadKg() >= cargaKg).collect(Collectors.toList());
    }

    public List<Vehiculo> getVehiculos() {
        return Collections.unmodifiableList(vehiculos);
    }

    public String getEmpresa() {
        return empresa;
    }

    public int tamano() {
        return vehiculos.size();
    }

    public static Flota flotaDeReferencia() {
        Flota f = new Flota("Green Route Logística S.A.S.");
        f.agregar(new FurgonetaElectrica("VAN-E", "Furgoneta eléctrica"));
        f.agregar(new FurgonetaGasolina("VAN-G", "Furgoneta a gasolina"));
        f.agregar(new CamionElectrico("CAM-E", "Camión eléctrico"));
        f.agregar(new CamionGNV("CAM-GNV", "Camión a gas natural"));
        f.agregar(new CamionDiesel("CAM-D", "Camión diésel"));
        f.agregar(new Tractocamion("TRACTO", "Tractocamión diésel"));
        f.agregar(new Barcaza("BARCAZA", "Barcaza fluvial"));
        return f;
    }
}
