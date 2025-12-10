package JuegoCoup;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class SalaCoup {
    private static final int MAX_JUGADORES = 6;
    private final String idSala;
    private final String nombreSala;

    private final List<Jugador> jugadores;
    private final Mazo mazo;
    private int indiceTurnoActual;
    private boolean juegoIniciado;

    public SalaCoup() {
        this("default-" + System.currentTimeMillis(), "Sala Predeterminada");
    }

    public SalaCoup(String idSala) {
        this(idSala, "Sala-" + idSala);
    }

    public SalaCoup(String idSala, String nombreSala) {
        this.idSala = idSala;
        this.nombreSala = nombreSala;
        this.jugadores = new ArrayList<>();
        this.mazo = new Mazo();
        this.indiceTurnoActual = 0;
        this.juegoIniciado = false;
    }

    public boolean agregarJugador(String nombreUsuario) {
        if (juegoIniciado) {
            return false;
        }
        if (jugadores.size() >= MAX_JUGADORES) {
            return false;
        }
        if (jugadores.stream().anyMatch(j -> j.getNombreUsuario().equalsIgnoreCase(nombreUsuario))) {
            return false;
        }

        Jugador nuevo = new Jugador(nombreUsuario);
        jugadores.add(nuevo);
        return true;
    }
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
            throw new IllegalStateException("Se necesitan mínimo 3 jugadores.");
        }
        juegoIniciado = true;
        mazo.barajar();
        repartirRecursosIniciales();
    }

    private void repartirRecursosIniciales() {
        for (Jugador j : jugadores) {
            // Se mantiene la lógica de reparto
            j.recibirCarta(mazo.robarCarta().orElseThrow());
            j.recibirCarta(mazo.robarCarta().orElseThrow());
        }
    }

    public Jugador getJugadorActivo() {
        if (jugadores.isEmpty()) return null;
        return jugadores.get(indiceTurnoActual);
    }

    public void siguienteTurno() {
        if (jugadores.isEmpty()) return;
        int intentos = 0;
        do {
            indiceTurnoActual = (indiceTurnoActual + 1) % jugadores.size();
            intentos++;
        } while (!getJugadorActivo().estaVivo() && intentos < jugadores.size());
    }

    private boolean esTurnoDe(Jugador j) {
        return j != null && j.equals(getJugadorActivo());
    }

    private Jugador buscarJugador(String nombre) {
        for (Jugador j : jugadores) {
            if (j.getNombreUsuario().equalsIgnoreCase(nombre)) {
                return j;
            }
        }
        return null;
    }

    public String realizarAccionIngreso(Jugador jugador) {
        if (!esTurnoDe(jugador)) return "ERROR: No es tu turno.";

        jugador.ganarMonedas(1);
        siguienteTurno();
        return String.format("ACCION: %s tomó Ingreso (+1 moneda).", jugador.getNombreUsuario());
    }

    public String iniciarAccionAyudaExterior(Jugador jugador) {
        if (!esTurnoDe(jugador)) return "ERROR: No es tu turno.";
        return "INTENTO:AYUDA:TODOS";
    }

    public String realizarAccionAyudaExterior(Jugador jugador) {
        if (!esTurnoDe(jugador)) return "ERROR: No es tu turno.";

        jugador.ganarMonedas(2);
        siguienteTurno();
        return String.format("ACCION: %s usó Ayuda Exterior (+2 monedas).", jugador.getNombreUsuario());
    }

    public String realizarGolpeDeEstado(Jugador atacante, String nombreVictima) {
        if (!esTurnoDe(atacante)) return "ERROR: No es tu turno.";

        Jugador victima = buscarJugador(nombreVictima);
        if (victima == null) return "ERROR: Jugador destino no encontrado.";
        if (!victima.estaVivo()) return "ERROR: Ese jugador ya está eliminado.";

        if (!atacante.pagar(7)) {
            return "ERROR: No tienes suficientes monedas (Necesitas 7).";
        }
        return "ESPERA_CARTA:" + victima.getNombreUsuario();
    }

    public String concretarDescarte(String nombreVictima, String nombreCarta) {
        Jugador victima = buscarJugador(nombreVictima);
        if (victima == null) return "ERROR: Jugador no encontrado.";

        TipoCarta cartaAEliminar;
        try {
            cartaAEliminar = TipoCarta.valueOf(nombreCarta.toUpperCase());
        } catch (IllegalArgumentException e) {
            return "ERROR: Carta inválida. Tus cartas son: " + victima.getManoActual();
        }

        if (victima.perderInfluencia(cartaAEliminar)) {
            siguienteTurno();
            return "DESCARTE_OK: " + nombreVictima + " perdió " + nombreCarta + ".";
        } else {
            return "ERROR: No tienes esa carta. Intenta de nuevo.";
        }
    }

    public String ejecutarAccionPendiente(String tipoAccion, Jugador atacante, String nombreVictima) {
        Jugador victima = buscarJugador(nombreVictima);

        if (tipoAccion.equals("ROBAR")) {
            int monto = (victima.getMonedas() >= 2) ? 2 : victima.getMonedas();
            victima.pagar(monto);
            atacante.ganarMonedas(monto);
            siguienteTurno();
            return "EXITO: " + atacante.getNombreUsuario() + " robó " + monto + " monedas a " + nombreVictima;

        } else if (tipoAccion.equals("ASESINAR")) {
            return "ESPERA_CARTA:" + nombreVictima;
        }

        if (tipoAccion.equals("AYUDA")) {
            atacante.ganarMonedas(2);
            siguienteTurno();
            return "EXITO: " + atacante.getNombreUsuario() + " recibió Ayuda Exterior (+2 monedas).";
        }

        return "ERROR: Acción pendiente desconocida.";
    }

    // 1. DUQUE
    public String realizarAccionImpuestos(Jugador jugador) {
        if (!esTurnoDe(jugador)) return "ERROR: No es tu turno.";
        jugador.ganarMonedas(3);
        siguienteTurno();
        return String.format("DUQUE: %s cobró impuestos (+3 monedas).", jugador.getNombreUsuario());
    }

    // 2. CAPITÁN
    public String realizarAccionRobar(Jugador ladron, String nombreVictima) {
        if (!esTurnoDe(ladron)) return "ERROR: No es tu turno.";

        Jugador victima = buscarJugador(nombreVictima);
        if (victima == null) return "ERROR: Víctima no encontrada.";
        if (ladron.equals(victima)) return "ERROR: No puedes robarte a ti mismo.";
        if (!victima.estaVivo()) return "ERROR: La víctima ya no juega.";

        int montoRobado = 0;
        if (victima.getMonedas() >= 2) {
            montoRobado = 2;
        } else {
            montoRobado = victima.getMonedas();
        }

        victima.pagar(montoRobado);
        ladron.ganarMonedas(montoRobado);

        siguienteTurno();
        return String.format("CAPITÁN: %s robó %d monedas a %s.",
                ladron.getNombreUsuario(), montoRobado, victima.getNombreUsuario());
    }
    public String iniciarAccionRobar(Jugador ladron, String nombreVictima) {
        if (!esTurnoDe(ladron)) return "ERROR: No es tu turno.";
        Jugador victima = buscarJugador(nombreVictima);
        if (victima == null || !victima.estaVivo()) return "ERROR: Objetivo inválido.";
        if (ladron.equals(victima)) return "ERROR: Auto-robo no permitido.";

        return "INTENTO:ROBAR:" + victima.getNombreUsuario();
    }

    // 3. ASESINO
    public String realizarAccionAsesinato(Jugador asesino, String nombreVictima) {
        if (!esTurnoDe(asesino)) return "ERROR: No es tu turno.";

        Jugador victima = buscarJugador(nombreVictima);
        if (victima == null) return "ERROR: Víctima no encontrada.";
        if (!victima.estaVivo()) return "ERROR: La víctima ya no juega.";

        if (!asesino.pagar(3)) {
            return "ERROR: No tienes 3 monedas para pagar al Asesino.";
        }

        return "ESPERA_CARTA:" + victima.getNombreUsuario();
    }

    public String iniciarAccionAsesinato(Jugador asesino, String nombreVictima) {
        if (!esTurnoDe(asesino)) return "ERROR: No es tu turno.";
        Jugador victima = buscarJugador(nombreVictima);
        if (victima == null || !victima.estaVivo()) return "ERROR: Objetivo inválido.";

        if (!asesino.pagar(3)) return "ERROR: No tienes 3 monedas.";

        return "INTENTO:ASESINAR:" + victima.getNombreUsuario();
    }

    // 4. EMBAJADOR
    public String realizarAccionEmbajador(Jugador jugador) {
        if (!esTurnoDe(jugador)) return "ERROR: No es tu turno.";

        List<TipoCarta> manoTemporal = new ArrayList<>(jugador.getManoActual());
        mazo.robarCarta().ifPresent(manoTemporal::add);
        mazo.robarCarta().ifPresent(manoTemporal::add);
        Collections.shuffle(manoTemporal);

        int cartasAConservar = jugador.getManoActual().size();
        List<TipoCarta> nuevaMano = new ArrayList<>();

        for (int i = 0; i < cartasAConservar; i++) {
            nuevaMano.add(manoTemporal.remove(0));
        }

        for (TipoCarta sobrante : manoTemporal) {
            mazo.devolverCarta(sobrante);
        }

        jugador.actualizarMano(nuevaMano);
        siguienteTurno();
        return String.format("EMBAJADOR: %s cambió sus cartas con el mazo.", jugador.getNombreUsuario());
    }

    public boolean isJuegoIniciado() {
        return juegoIniciado;
    }

    public List<Jugador> getJugadores() {
        return new ArrayList<>(jugadores);
    }

    // Getter de jugadores vivos (del Código 2)
    public List<Jugador> getJugadoresVivos() {
        return jugadores.stream()
                .filter(Jugador::estaVivo)
                .collect(Collectors.toList());
    }
    public String getIdSala() {
        return idSala;
    }

    public String getNombreSala() {
        return nombreSala;
    }
}