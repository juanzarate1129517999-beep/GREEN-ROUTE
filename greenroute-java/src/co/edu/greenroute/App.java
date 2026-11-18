package co.edu.greenroute;

import co.edu.greenroute.analitica.PerfilHorario;
import co.edu.greenroute.api.ServidorWeb;
import co.edu.greenroute.excepciones.GreenRouteException;
import co.edu.greenroute.modelo.Coordenada;
import co.edu.greenroute.modelo.Envio;
import co.edu.greenroute.modelo.Mercancia;
import co.edu.greenroute.modelo.TipoMercancia;
import co.edu.greenroute.red.CargadorRed;
import co.edu.greenroute.red.RedTransporte;
import co.edu.greenroute.ruteo.AEstrella;
import co.edu.greenroute.ruteo.Criterio;
import co.edu.greenroute.ruteo.Ruta;
import co.edu.greenroute.servicio.PlanificadorRutas;
import co.edu.greenroute.vehiculos.Flota;
import co.edu.greenroute.vehiculos.Vehiculo;

import java.nio.file.Path;
import java.nio.file.Paths;

/** Punto de entrada: carga la red y arranca el servidor web (o, con --demo, compara la flota en consola). */
public class App {

    public static void main(String[] args) throws Exception {
        Path base = Paths.get(System.getProperty("user.dir"));
        Path datos = base.resolve("datos");
        Path web = base.resolve("..").resolve("web").normalize();
        int puerto = 8080;
        boolean demo = false;
        for (String a : args) {
            if (a.equals("--demo")) demo = true;
            else if (a.startsWith("--puerto=")) puerto = Integer.parseInt(a.substring(9));
        }

        System.out.println("==============================================================");
        System.out.println("  GREEN ROUTE · Rutas de carga de baja huella de carbono");
        System.out.println("  Barranquilla, Colombia · ODS 9 — Industria, Innovación e Infraestructura");
        System.out.println("==============================================================");
        long t0 = System.currentTimeMillis();
        RedTransporte red = CargadorRed.cargar(datos);
        PerfilHorario perfil = PerfilHorario.cargar(datos.resolve("perfil_horario.csv"));
        System.out.printf("  · Red cargada: %,d nodos, %,d tramos, %.0f km viales, %.1f km fluviales (%d ms)%n",
                red.totalNodos(), red.totalTramos(), red.getKmViales(), red.getKmFluviales(),
                System.currentTimeMillis() - t0);
        System.out.println("  · Perfil de tráfico: " + perfil.getFuente());

        PlanificadorRutas planificador = new PlanificadorRutas(red, Flota.flotaDeReferencia(), perfil, new AEstrella());

        if (demo) {
            demostracion(planificador);
            return;
        }
        new ServidorWeb(planificador, web, datos).iniciar(puerto);
        System.out.println("  · Servidor listo en  http://localhost:" + puerto);
        System.out.println("    (Ctrl + C para detener)");
    }

    private static void demostracion(PlanificadorRutas planificador) {
        Envio envio = new Envio(
                new Mercancia("Repuestos industriales", TipoMercancia.GENERAL, 900),
                new Coordenada(10.96759, -74.76222),
                new Coordenada(10.88795, -74.77950),
                7);
        System.out.println();
        System.out.println("Envío: " + envio.getMercancia() + " · Puerto → Aeropuerto · salida 7:00");
        System.out.printf("%-24s %-20s %9s %9s %11s %12s%n", "Vehículo", "Clase", "km", "min", "kg CO2e", "COP");
        System.out.println("-".repeat(90));
        for (Vehiculo v : planificador.getFlota().getVehiculos()) {
            try {
                Ruta r = planificador.planificar(envio, v, Criterio.EMISIONES);
                System.out.printf("%-24s %-20s %9.2f %9.1f %11.2f %,12.0f%n", v.getNombre(),
                        v.getClass().getSimpleName(), r.getDistanciaKm(), r.getTiempoMin(),
                        r.getEmisionKgCO2(), r.getCostoCOP());
            } catch (GreenRouteException e) {
                System.out.printf("%-24s %-20s  ✗ %s: %s%n", v.getNombre(), v.getClass().getSimpleName(),
                        e.getCodigo(), e.getMessage());
            }
        }
    }
}
