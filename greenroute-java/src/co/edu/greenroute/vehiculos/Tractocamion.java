package co.edu.greenroute.vehiculos;

/** Tractocamión articulado para contenedores: un CamionDiesel más grande. */
public class Tractocamion extends CamionDiesel {

    public Tractocamion(String id, String nombre) {
        super(id, nombre, 34_000, 15_000, 70, 33.0, 0.30, 1_100);
    }

    @Override
    public String getTecnologia() {
        return "Tractocamión articulado diésel (contenedores)";
    }
}
