package JuegoCoup;

public enum TipoCarta {

    DUQUE("Toma 3 monedas del tesoro. Bloquea la Ayuda Exterior."),
    ASESINO("Paga 3 monedas para realizar un asesinato (eliminar influencia)."),
    CONDESA("Bloquea el intento de asesinato en tu contra."),
    CAPITAN("Roba 2 monedas a otro jugador. Bloquea el robo de otro Capitán."),
    EMBAJADOR("Cambia tus cartas con el mazo. Bloquea el robo del Capitán.");


    private final String accion;


    TipoCarta(String accion) {
        this.accion = accion;
    }

    public String getAccion() {
        return accion;
    }
}