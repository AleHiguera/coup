package JuegoCoup;

import java.util.ArrayList;
import java.util.Collections;
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

    // --- GESTIÓN DE JUGADORES Y PARTIDA ---

    public boolean agregarJugador(String nombreUsuario) {
        if (juegoIniciado) {
            return false;
        }
        if (jugadores.size() >= 6) {
            return false;
        }
        for (Jugador j : jugadores) {
            if (j.getNombreUsuario().equalsIgnoreCase(nombreUsuario)) return false;
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

    // --- CONTROL DE TURNOS ---

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

    // --- ACCIONES GENERALES ---

    public String realizarAccionIngreso(Jugador jugador) {
        if (!esTurnoDe(jugador)) return "ERROR: No es tu turno.";

        jugador.ganarMonedas(1);
        siguienteTurno();
        return String.format("ACCION: %s tomó Ingreso (+1 moneda).", jugador.getNombreUsuario());
    }

    public String iniciarAccionAyudaExterior(Jugador jugador) {
        if (!esTurnoDe(jugador)) return "ERROR: No es tu turno.";

        // NO damos monedas todavía.
        // Usamos "TODOS" como víctima porque cualquiera puede bloquear la Ayuda Exterior.
        return "INTENTO:AYUDA:TODOS";
    }

    public String realizarAccionAyudaExterior(Jugador jugador) {
        if (!esTurnoDe(jugador)) return "ERROR: No es tu turno.";

        // Lógica simplificada (sin bloqueo del Duque por ahora)
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

    // --- ACCIONES DE PERSONAJES (CARTAS) ---

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
            montoRobado = victima.getMonedas(); // Roba lo que tenga
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

        // No robamos aún, solo validamos.
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

        // Cobramos las monedas YA. Si lo bloquean, las pierde (Regla estricta).
        // Retornamos señal de intento.
        return "INTENTO:ASESINAR:" + victima.getNombreUsuario();
    }

    // 4. EMBAJADOR
    public String realizarAccionEmbajador(Jugador jugador) {
        if (!esTurnoDe(jugador)) return "ERROR: No es tu turno.";

        // Copia de la mano actual
        List<TipoCarta> manoTemporal = new ArrayList<>(jugador.getManoActual());

        // Robar 2 cartas del mazo
        mazo.robarCarta().ifPresent(manoTemporal::add);
        mazo.robarCarta().ifPresent(manoTemporal::add);

        // Barajar las opciones
        Collections.shuffle(manoTemporal);

        // Determinar cuántas cartas debe quedarse el jugador (su vida actual)
        int cartasAConservar = jugador.getManoActual().size();

        List<TipoCarta> nuevaMano = new ArrayList<>();

        // El jugador se queda con las primeras 'n' cartas (Aleatorio simplificado)
        // En un juego real, aquí se le preguntaría al cliente cuáles quiere.
        for (int i = 0; i < cartasAConservar; i++) {
            nuevaMano.add(manoTemporal.remove(0));
        }

        // Las sobrantes vuelven al mazo
        for (TipoCarta sobrante : manoTemporal) {
            mazo.devolverCarta(sobrante);
        }

        // Actualizar la mano del jugador
        jugador.actualizarMano(nuevaMano);

        siguienteTurno();
        return String.format("EMBAJADOR: %s cambió sus cartas con el mazo.", jugador.getNombreUsuario());
    }

    // --- GETTERS ---

    public boolean isJuegoIniciado() {
        return juegoIniciado;
    }

    public List<Jugador> getJugadores() {
        return new ArrayList<>(jugadores);
    }
}