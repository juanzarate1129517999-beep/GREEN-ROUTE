package co.edu.greenroute.ruteo;

import co.edu.greenroute.excepciones.RutaNoEncontradaException;
import co.edu.greenroute.red.Nodo;
import co.edu.greenroute.red.RedTransporte;
import co.edu.greenroute.vehiculos.Vehiculo;

/** Contrato de un algoritmo que busca la mejor ruta en la red. */
public interface AlgoritmoRuta {

    Ruta buscar(RedTransporte red, Nodo origen, Nodo destino, Vehiculo vehiculo,
                Criterio criterio, ContextoViaje contexto) throws RutaNoEncontradaException;

    String getNombre();
}
