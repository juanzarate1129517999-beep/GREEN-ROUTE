package co.edu.greenroute.modelo;

/** Punto geográfico (latitud, longitud) con distancia en metros (fórmula de Haversine). */
public final class Coordenada {
    private static final double RADIO_TIERRA_M = 6_371_000.0;

    private final double latitud;
    private final double longitud;

    public Coordenada(double latitud, double longitud) {
        if (latitud < -90 || latitud > 90 || longitud < -180 || longitud > 180) {
            throw new IllegalArgumentException("Coordenada fuera de rango: " + latitud + ", " + longitud);
        }
        this.latitud = latitud;
        this.longitud = longitud;
    }

    public double getLatitud() { return latitud; }
    public double getLongitud() { return longitud; }

    public double distanciaMetros(Coordenada otra) {
        double dLat = Math.toRadians(otra.latitud - latitud);
        double dLon = Math.toRadians(otra.longitud - longitud);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(latitud)) * Math.cos(Math.toRadians(otra.latitud))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return 2 * RADIO_TIERRA_M * Math.asin(Math.sqrt(a));
    }

    public double[] comoArreglo() {
        return new double[]{latitud, longitud};
    }

    @Override
    public String toString() {
        return String.format("(%.5f, %.5f)", latitud, longitud);
    }
}
