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

    // --- ACCESO SEGURO A LA LISTA DE JUGADORES ---

    public synchronized List<Jugador> getJugadores() {
        return new ArrayList<>(jugadores);
    }

    public synchronized boolean isJuegoIniciado() {
        return juegoIniciado;
    }

    // --- GESTIÓN DE JUGADORES (SYNCHRONIZED) ---

    public synchronized boolean agregarJugador(String nombre) {
        if (juegoIniciado || jugadores.size() >= MAX_JUGADORES) {
            return false;
        }
        if (buscarJugadorInterno(nombre) != null) {
            return false;
        }
        jugadores.add(new Jugador(nombre));
        return true;
    }

    public synchronized void removerJugador(String nombreUsuario) {
        boolean estabaJugando = jugadores.removeIf(j -> j.getNombreUsuario().equalsIgnoreCase(nombreUsuario));

        if (estabaJugando && juegoIniciado) {
            if (indiceTurnoActual >= jugadores.size()) {
                indiceTurnoActual = 0;
            }
            if (!jugadores.isEmpty()) {
                if (!getJugadorActivo().estaVivo()) {
                    siguienteTurno();
                }
            }
        }
    }

    public synchronized void iniciarPartida() {
        if (jugadores.size() < 3) { //
            throw new IllegalStateException("Min 2 jugadores.");
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

    // --- CONTROL DE TURNOS (SYNCHRONIZED) ---

    public synchronized Jugador getJugadorActivo() {
        if (jugadores.isEmpty()) {
            return null;
        }
        if (indiceTurnoActual >= jugadores.size()) {
            indiceTurnoActual = 0;
        }
        return jugadores.get(indiceTurnoActual);
    }

    public synchronized void siguienteTurno() {
        if (jugadores.isEmpty()) return;

        int intentos = 0;
        do {
            indiceTurnoActual = (indiceTurnoActual + 1) % jugadores.size();
            intentos++;
        } while (!jugadores.get(indiceTurnoActual).estaVivo() && intentos <= jugadores.size());
    }

    // --- MÉTODOS AUXILIARES INTERNOS ---
    private Jugador buscarJugadorInterno(String n) {
        for (Jugador j : jugadores) {
            if (j.getNombreUsuario().equalsIgnoreCase(n)) return j;
        }
        return null;
    }

    private boolean validarTurno(Jugador j) {
        Jugador activo = getJugadorActivo();
        return activo != null && activo.getNombreUsuario().equals(j.getNombreUsuario());
    }

    // --- ACCIONES DEL JUEGO (TODAS SYNCHRONIZED) ---
    // Esto evita que dos acciones ocurran simultáneamente y corrompan el estado

    public synchronized String realizarAccionIngreso(Jugador j) {
        if (!validarTurno(j)) return "ERROR: No es tu turno.";
        j.ganarMonedas(1);
        siguienteTurno();
        return "ACCION: " + j.getNombreUsuario() + " tomó Ingreso.";
    }

    public synchronized String iniciarAccionAyudaExterior(Jugador j) {
        if (!validarTurno(j)) return "ERROR: Turno incorrecto.";
        return "INTENTO:AYUDA:TODOS";
    }

    public synchronized String realizarAccionImpuestos(Jugador j) {
        if (!validarTurno(j)) return "ERROR: Turno incorrecto.";
        j.ganarMonedas(3);
        siguienteTurno();
        return "DUQUE: " + j.getNombreUsuario() + " cobró impuestos.";
    }

    public synchronized String realizarGolpeDeEstado(Jugador atq, String nomVic) {
        if (!validarTurno(atq)) {
            return "ERROR: Turno incorrecto.";
        }
        if (!atq.pagar(7)) {
            return "ERROR: No tienes suficientes monedas (Costo: 7).";
        }
        Jugador vic = buscarJugadorInterno(nomVic);
        if (vic == null || !vic.estaVivo()) {
            atq.ganarMonedas(7);
            return "ERROR: Objetivo inválido o eliminado.";
        }
        return "ESPERA_CARTA:" + vic.getNombreUsuario();
    }

    private String procesarAtaqueMortal(String nomVic) {
        Jugador vic = buscarJugadorInterno(nomVic);
        if (vic == null || !vic.estaVivo()) return "ERROR: Objetivo inválido.";
        return "ESPERA_CARTA:" + vic.getNombreUsuario();
    }

    public synchronized String iniciarAccionAsesinato(Jugador asesino, String nomVic) {
        if (!validarTurno(asesino)) return "ERROR: Turno incorrecto.";
        if (!asesino.pagar(3)) return "ERROR: Faltan monedas.";
        return validarObjetivoYRetornar(nomVic, "INTENTO:ASESINAR:");
    }

    public synchronized String iniciarAccionRobar(Jugador ladron, String nomVic) {
        if (!validarTurno(ladron)) return "ERROR: Turno incorrecto.";
        if (ladron.getNombreUsuario().equalsIgnoreCase(nomVic)) return "ERROR: Auto-robo.";
        return validarObjetivoYRetornar(nomVic, "INTENTO:ROBAR:");
    }

    private String validarObjetivoYRetornar(String nomVic, String prefijo) {
        Jugador vic = buscarJugadorInterno(nomVic);
        if (vic == null || !vic.estaVivo()) return "ERROR: Objetivo inválido.";
        return prefijo + vic.getNombreUsuario();
    }

    public synchronized String ejecutarAccionPendiente(String tipo, Jugador atq, String nomVic) {
        if (tipo.equals("AYUDA")) return ejecutarAyuda(atq);
        if (tipo.equals("ROBAR")) return ejecutarRobo(atq, nomVic);
        if (tipo.equals("ASESINAR")) return "ESPERA_CARTA:" + nomVic;
        return "ERROR: Acción desconocida.";
    }

    private String ejecutarAyuda(Jugador j) {
        j.ganarMonedas(2);
        siguienteTurno();
        return "EXITO: Ayuda Exterior completada.";
    }

    private String ejecutarRobo(Jugador ladron, String nomVic) {
        Jugador vic = buscarJugadorInterno(nomVic);
        if (vic == null) return "ERROR: Víctima desapareció.";

        int monto = Math.min(vic.getMonedas(), 2);
        vic.pagar(monto);
        ladron.ganarMonedas(monto);
        siguienteTurno();
        return "EXITO: Robo de " + monto + " monedas.";
    }

    public synchronized String realizarAccionEmbajador(Jugador j) {
        if (!validarTurno(j)) return "ERROR: Turno incorrecto.";
        intercambiarCartas(j);
        siguienteTurno();
        return "EMBAJADOR: Cartas cambiadas.";
    }

    private void intercambiarCartas(Jugador j) {
        List<TipoCarta> temp = new ArrayList<>(j.getManoActual());
        mazo.robarCarta().ifPresent(temp::add);
        mazo.robarCarta().ifPresent(temp::add);

        Collections.shuffle(temp);
        restaurarMano(j, temp);
    }

    private void restaurarMano(Jugador j, List<TipoCarta> temp) {
        List<TipoCarta> nueva = new ArrayList<>();
        int count = j.getManoActual().size();
        int limite = Math.min(count, temp.size());

        for (int i = 0; i < limite; i++) {
            nueva.add(temp.remove(0));
        }
        for (TipoCarta c : temp) {
            mazo.devolverCarta(c);
        }

        j.actualizarMano(nueva);
    }

    public synchronized String concretarDescarte(String nom, String cartaStr) {
        Jugador j = buscarJugadorInterno(nom);
        if (j == null) return "ERROR: Jugador no encontrado.";

        try {
            TipoCarta c = TipoCarta.valueOf(cartaStr.toUpperCase());
            if (j.perderInfluencia(c)) {
                siguienteTurno();

                if (!j.estaVivo()) {
                    return "ELIMINADO: " + nom + " ha perdido todas sus influencias.";
                }
                return "DESCARTE_OK: " + nom + " perdió " + cartaStr;
            }
        } catch (IllegalArgumentException e) {
            return "ERROR: Esa carta no existe.";
        }
        return "ERROR: No tienes esa carta.";
    }
}