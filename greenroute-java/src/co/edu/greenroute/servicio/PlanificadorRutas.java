package co.edu.greenroute.servicio;

import co.edu.greenroute.analitica.PerfilHorario;
import co.edu.greenroute.excepciones.GreenRouteException;
import co.edu.greenroute.excepciones.RutaNoEncontradaException;
import co.edu.greenroute.excepciones.VehiculoIncompatibleException;
import co.edu.greenroute.modelo.Coordenada;
import co.edu.greenroute.modelo.Envio;
import co.edu.greenroute.red.Nodo;
import co.edu.greenroute.red.RedTransporte;
import co.edu.greenroute.ruteo.AlgoritmoRuta;
import co.edu.greenroute.ruteo.ContextoViaje;
import co.edu.greenroute.ruteo.Criterio;
import co.edu.greenroute.ruteo.Ruta;
import co.edu.greenroute.vehiculos.Flota;
import co.edu.greenroute.vehiculos.MedioTransporte;
import co.edu.greenroute.vehiculos.Recargable;
import co.edu.greenroute.vehiculos.Vehiculo;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Fachada del motor: ubica origen y destino, valida el vehículo y llama al algoritmo. */
public class PlanificadorRutas {
    public static final double MAX_ACCESO_FLUVIAL_M = 2_500;
    public static final double MAX_ACCESO_VIAL_M = 2_500;

    private final RedTransporte red;
    private final Flota flota;
    private final PerfilHorario perfil;
    private final AlgoritmoRuta algoritmo;

    public PlanificadorRutas(RedTransporte red, Flota flota, PerfilHorario perfil, AlgoritmoRuta algoritmo) {
        this.red = red;
        this.flota = flota;
        this.perfil = perfil;
        this.algoritmo = algoritmo;
    }

    public Ruta planificar(Envio envio, Vehiculo vehiculo, Criterio criterio) throws GreenRouteException {
        return planificar(envio.getOrigen(), envio.getDestino(), vehiculo, criterio,
                envio.getHoraSalida(), envio.getMercancia().getPesoKg());
    }

    public Ruta planificar(Coordenada origen, Coordenada destino, Vehiculo vehiculo, Criterio criterio,
                           int hora, double cargaKg) throws GreenRouteException {
        vehiculo.validarCarga(cargaKg);
        ContextoViaje contexto = new ContextoViaje(hora, cargaKg, perfil);
        Nodo nOrigen = ubicar(origen, vehiculo, "origen");
        Nodo nDestino = ubicar(destino, vehiculo, "destino");
        if (nOrigen == nDestino) {
            throw new RutaNoEncontradaException("El origen y el destino están en el mismo punto de la red");
        }
        Ruta ruta = algoritmo.buscar(red, nOrigen, nDestino, vehiculo, criterio, contexto);
        if (vehiculo instanceof Recargable) {
            ((Recargable) vehiculo).verificarAutonomia(vehiculo.getNombre(), ruta.getDistanciaKm(), cargaKg);
        }
        return ruta;
    }

    private Nodo ubicar(Coordenada punto, Vehiculo vehiculo, String rol) throws VehiculoIncompatibleException {
        Nodo nodo = red.nodoMasCercano(punto, vehiculo.getMedio());
        double distancia = nodo.getCoordenada().distanciaMetros(punto);
        if (vehiculo.getMedio() == MedioTransporte.FLUVIAL && distancia > MAX_ACCESO_FLUVIAL_M) {
            throw new VehiculoIncompatibleException(String.format(
                    "La barcaza solo opera entre puntos ribereños del Magdalena: el %s está a %.1f km del río",
                    rol, distancia / 1000));
        }
        if (vehiculo.getMedio() == MedioTransporte.TERRESTRE && distancia > MAX_ACCESO_VIAL_M) {
            throw new VehiculoIncompatibleException(String.format(
                    "El %s está fuera del área de cobertura de la malla vial (%.1f km)", rol, distancia / 1000));
        }
        return nodo;
    }

    public Map<Criterio, Ruta> alternativas(Coordenada o, Coordenada d, Vehiculo v, int hora, double carga)
            throws GreenRouteException {
        Map<Criterio, Ruta> resultado = new EnumMap<>(Criterio.class);
        GreenRouteException ultimo = null;
        for (Criterio c : Criterio.values()) {
            try {
                resultado.put(c, planificar(o, d, v, c, hora, carga));
            } catch (GreenRouteException e) {
                ultimo = e;
            }
        }
        if (resultado.isEmpty() && ultimo != null) {
            throw ultimo;
        }
        return resultado;
    }

    public List<OpcionTransporte> compararFlota(Coordenada o, Coordenada d, int hora, double carga,
                                                Criterio criterio) {
        List<OpcionTransporte> opciones = new ArrayList<>();
        for (Vehiculo v : flota.getVehiculos()) {
            try {
                opciones.add(OpcionTransporte.factible(v, planificar(o, d, v, criterio, hora, carga)));
            } catch (GreenRouteException e) {
                opciones.add(OpcionTransporte.noFactible(v, e));
            }
        }
        return opciones;
    }

    public Optional<OpcionTransporte> recomendar(List<OpcionTransporte> opciones) {
        return opciones.stream()
                .filter(OpcionTransporte::esFactible)
                .min(Comparator.comparingDouble(op -> op.getRuta().getEmisionKgCO2()));
    }

    public RedTransporte getRed() { return red; }
    public Flota getFlota() { return flota; }
    public PerfilHorario getPerfil() { return perfil; }
    public AlgoritmoRuta getAlgoritmo() { return algoritmo; }
}
