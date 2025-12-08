package JuegoCoup;

import java.util.ArrayList;
import java.util.List;

public class SalaCoup {
    private final List<Jugador> jugadores;
    private final Mazo mazo;
    private int indiceTurnoActual;
    private boolean juegoIniciado;

    public SalaCoup() {
        this.jugadores = new ArrayList<>();
        this.mazo = new Mazo();
        this.indiceTurnoActual = 0;
        this.juegoIniciado = false;
    }


    public boolean agregarJugador(String nombreUsuario) {
        if (juegoIniciado) {
            return false;
        }
        if (jugadores.size() >= 6) {
            return false;
        }
        Jugador nuevo = new Jugador(nombreUsuario);
        jugadores.add(nuevo);
        return true;
    }

    public void iniciarPartida() {
        if (jugadores.size() < 2) {
            throw new IllegalStateException("Se necesitan mínimo 2 jugadores.");
        }

        juegoIniciado = true;
        mazo.barajar();

        repartirRecursosIniciales();
    }

    private void repartirRecursosIniciales() {
        for (Jugador j : jugadores) {

            j.recibirCarta(mazo.robarCarta().orElseThrow());
            j.recibirCarta(mazo.robarCarta().orElseThrow());


        }
    }



    public Jugador getJugadorActivo() {
        return jugadores.get(indiceTurnoActual);
    }

    public void siguienteTurno() {
        do {
            indiceTurnoActual = (indiceTurnoActual + 1) % jugadores.size();
        } while (!getJugadorActivo().estaVivo());
    }


    public String realizarAccionIngreso(Jugador jugadorQueAcciona) {

        if (!jugadorQueAcciona.equals(getJugadorActivo())) {
            return "ERROR: No es tu turno.";
        }


        jugadorQueAcciona.ganarMonedas(1);

        siguienteTurno();
        return "ACCION_OK: " + jugadorQueAcciona.getNombreUsuario() + " tomó 1 moneda (Ingreso).";
    }

    public boolean isJuegoIniciado() {
        return juegoIniciado;
    }

    public List<Jugador> getJugadores() {
        return new ArrayList<>(jugadores);
    }
}