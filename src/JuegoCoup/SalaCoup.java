package JuegoCoup;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class SalaCoup {
    private static final int MAX_JUGADORES = 6;
    private final String idSala;
    private final String nombreSala;
    private final List<Jugador> jugadores = new ArrayList<>();
    private final Mazo mazo = new Mazo();
    private int indiceTurnoActual = 0;
    private boolean juegoIniciado = false;

    public SalaCoup(String id, String nombre) {
        this.idSala = id;
        this.nombreSala = nombre;
    }

    public String getIdSala() {
        return idSala;
    }

    public String getNombreSala() {
        return nombreSala;
    }

    public List<Jugador> getJugadores() {
        return new ArrayList<>(jugadores);
    }

    public boolean isJuegoIniciado() {
        return juegoIniciado;
    }

    public boolean agregarJugador(String nombre) {
        if (juegoIniciado || jugadores.size() >= MAX_JUGADORES) {
            return false;
        }
        if (buscarJugador(nombre) != null) {
            return false;
        }
        jugadores.add(new Jugador(nombre));
        return true;
    }

    // --- ESTE ES EL MÉTODO QUE FALTABA ---
    public void removerJugador(String nombreUsuario) {
        jugadores.removeIf(j -> j.getNombreUsuario().equalsIgnoreCase(nombreUsuario));
        if (juegoIniciado && indiceTurnoActual >= jugadores.size()) {
            indiceTurnoActual = 0;
            if (!jugadores.isEmpty() && !getJugadorActivo().estaVivo()) {
                siguienteTurno();
            }
        }
    }

    public void iniciarPartida() {
        if (jugadores.size() < 3) {
            throw new IllegalStateException("Min 3 jugadores.");
        }
        juegoIniciado = true;
        mazo.barajar();
        repartir();
    }

    private void repartir() {
        for (Jugador j : jugadores) {
            j.recibirCarta(mazo.robarCarta().orElseThrow());
            j.recibirCarta(mazo.robarCarta().orElseThrow());
        }
    }

    public Jugador getJugadorActivo() {
        if (jugadores.isEmpty()) {
            return null;
        }
        return jugadores.get(indiceTurnoActual);
    }

    public void siguienteTurno() {
        if (jugadores.isEmpty()) {
            return;
        }
        do {
            indiceTurnoActual = (indiceTurnoActual + 1) % jugadores.size();
        } while (!getJugadorActivo().estaVivo());
    }

    private boolean validarTurno(Jugador j) {
        return j != null && j.equals(getJugadorActivo());
    }

    private Jugador buscarJugador(String n) {
        return jugadores.stream().filter(j -> j.getNombreUsuario().equalsIgnoreCase(n)).findFirst().orElse(null);
    }

    public String realizarAccionIngreso(Jugador j) {
        if (!validarTurno(j)) {
            return "ERROR: No es tu turno.";
        }
        j.ganarMonedas(1);
        siguienteTurno();
        return "ACCION: " + j.getNombreUsuario() + " tomó Ingreso.";
    }

    public String iniciarAccionAyudaExterior(Jugador j) {
        if (!validarTurno(j)) {
            return "ERROR: Turno incorrecto.";
        }
        return "INTENTO:AYUDA:TODOS";
    }

    public String realizarAccionImpuestos(Jugador j) {
        if (!validarTurno(j)) {
            return "ERROR: Turno incorrecto.";
        }
        j.ganarMonedas(3);
        siguienteTurno();
        return "DUQUE: " + j.getNombreUsuario() + " cobró impuestos.";
    }

    public String realizarGolpeDeEstado(Jugador atq, String nomVic) {
        if (!validarTurno(atq)) {
            return "ERROR: Turno incorrecto.";
        }
        if (!atq.pagar(7)) {
            return "ERROR: Faltan monedas.";
        }
        return procesarAtaqueMortal(nomVic);
    }

    private String procesarAtaqueMortal(String nomVic) {
        Jugador vic = buscarJugador(nomVic);
        if (vic == null || !vic.estaVivo()) {
            return "ERROR: Objetivo inválido.";
        }
        return "ESPERA_CARTA:" + vic.getNombreUsuario();
    }

    public String iniciarAccionAsesinato(Jugador asesino, String nomVic) {
        if (!validarTurno(asesino)) {
            return "ERROR: Turno incorrecto.";
        }
        if (!asesino.pagar(3)) {
            return "ERROR: Faltan monedas.";
        }
        return validarObjetivoYRetornar(nomVic, "INTENTO:ASESINAR:");
    }

    public String iniciarAccionRobar(Jugador ladron, String nomVic) {
        if (!validarTurno(ladron)) {
            return "ERROR: Turno incorrecto.";
        }
        if (ladron.getNombreUsuario().equalsIgnoreCase(nomVic)) {
            return "ERROR: Auto-robo.";
        }
        return validarObjetivoYRetornar(nomVic, "INTENTO:ROBAR:");
    }

    private String validarObjetivoYRetornar(String nomVic, String prefijo) {
        Jugador vic = buscarJugador(nomVic);
        if (vic == null || !vic.estaVivo()) {
            return "ERROR: Objetivo inválido.";
        }
        return prefijo + vic.getNombreUsuario();
    }

    public String ejecutarAccionPendiente(String tipo, Jugador atq, String nomVic) {
        if (tipo.equals("AYUDA")) {
            return ejecutarAyuda(atq);
        }
        if (tipo.equals("ROBAR")) {
            return ejecutarRobo(atq, nomVic);
        }
        if (tipo.equals("ASESINAR")) {
            return "ESPERA_CARTA:" + nomVic;
        }
        return "ERROR: Acción desconocida.";
    }

    private String ejecutarAyuda(Jugador j) {
        j.ganarMonedas(2);
        siguienteTurno();
        return "EXITO: Ayuda Exterior completada.";
    }

    private String ejecutarRobo(Jugador ladron, String nomVic) {
        Jugador vic = buscarJugador(nomVic);
        int monto = Math.min(vic.getMonedas(), 2);
        vic.pagar(monto);
        ladron.ganarMonedas(monto);
        siguienteTurno();
        return "EXITO: Robo de " + monto + " monedas.";
    }

    public String realizarAccionEmbajador(Jugador j) {
        if (!validarTurno(j)) {
            return "ERROR: Turno incorrecto.";
        }
        intercambiarCartas(j);
        siguienteTurno();
        return "EMBAJADOR: Cartas cambiadas.";
    }

    private void intercambiarCartas(Jugador j) {
        List<TipoCarta> temp = new ArrayList<>(j.getManoActual());
        temp.add(mazo.robarCarta().orElse(null));
        temp.add(mazo.robarCarta().orElse(null));
        Collections.shuffle(temp);
        restaurarMano(j, temp);
    }

    private void restaurarMano(Jugador j, List<TipoCarta> temp) {
        List<TipoCarta> nueva = new ArrayList<>();
        int count = j.getManoActual().size();
        for (int i = 0; i < count; i++) {
            nueva.add(temp.get(i));
        }
        j.actualizarMano(nueva);
    }

    public String concretarDescarte(String nom, String cartaStr) {
        Jugador j = buscarJugador(nom);
        try {
            TipoCarta c = TipoCarta.valueOf(cartaStr.toUpperCase());
            if (j.perderInfluencia(c)) {
                siguienteTurno();
                return "DESCARTE_OK: " + nom + " perdió " + cartaStr;
            }
        } catch (Exception e) {
        }
        return "ERROR: Carta inválida.";
    }
}