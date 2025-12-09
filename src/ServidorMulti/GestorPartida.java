package ServidorMulti;

import JuegoCoup.SalaCoup;
import JuegoCoup.Jugador;

import java.util.HashMap;
import java.util.Map;

public class GestorPartida {
    private SalaCoup juego;
    private Map<String, UnCliente> puentesDeConexion;
    private String jugadorPendienteDeDescarte = null;
    private String atacantePendiente = null;
    private String victimaPendiente = null;
    private String accionPendiente = null;

    public GestorPartida() {
        this.juego = new SalaCoup();
        this.puentesDeConexion = new HashMap<>();
    }

    // -----------------------------------------------------------------
    // LÓGICA DE CONEXIÓN
    // -----------------------------------------------------------------
    public synchronized void unirJugador(UnCliente clienteConexion) {
        String nombre = clienteConexion.getNombreUsuario();

        boolean aceptado = juego.agregarJugador(nombre);

        if (aceptado) {
            puentesDeConexion.put(nombre, clienteConexion);
            mensajeGlobal("El jugador " + nombre + " entró a la sala.");

            if (juego.getJugadores().size() == 3) {
                arrancarJuego();
            } else {
                mensajeGlobal("Esperando jugadores... (" + juego.getJugadores().size() + "/3)");
            }
        } else {
            clienteConexion.enviarMensaje("Error: La sala está llena, el nombre ya existe o el juego ya empezó.");
        }
    }

    private void arrancarJuego() {
        try {
            juego.iniciarPartida();
            mensajeGlobal("--- ¡PARTIDA INICIADA! ---");

            for (Jugador jLogico : juego.getJugadores()) {
                UnCliente socketDestino = puentesDeConexion.get(jLogico.getNombreUsuario());
                if (socketDestino != null) {
                    enviarEstadoJugador(socketDestino, jLogico);
                }
            }
            anunciarTurno();
        } catch (Exception e) {
            mensajeGlobal("Error al iniciar: " + e.getMessage());
        }
    }

    // -----------------------------------------------------------------
    // LÓGICA PRINCIPAL DEL JUEGO
    // -----------------------------------------------------------------
    public synchronized void procesarJugada(UnCliente cliente, String comandoCompleto) {
        String nombreJugador = cliente.getNombreUsuario();

        if (jugadorPendienteDeDescarte != null) {
            procesarFaseDescarte(cliente, comandoCompleto);
            return;
        }

        if (accionPendiente != null) {
            procesarFaseBloqueo(cliente, comandoCompleto);
            return;
        }

        procesarFaseNormal(cliente, comandoCompleto);
    }

    private void procesarFaseBloqueo(UnCliente cliente, String comandoCompleto) {
        String nombreJugador = cliente.getNombreUsuario();
        String comando = comandoCompleto.toLowerCase();

        boolean esAyudaExterior = "TODOS".equals(victimaPendiente) && "AYUDA".equals(accionPendiente);
        if (!esAyudaExterior && !nombreJugador.equals(victimaPendiente)) {
            cliente.enviarMensaje("SILENCIO: Solo " + victimaPendiente + " puede responder.");
            return;
        }

        // El atacante no puede bloquearse a sí mismo
        if (nombreJugador.equals(atacantePendiente)) {
            cliente.enviarMensaje("No puedes bloquear tu propia acción.");
            return;
        }

        // OPCIÓN A: BLOQUEAR CON DUQUE
        if (comando.startsWith("/bloquear")) {
            // Verificamos si es Ayuda Exterior, solo el Duque bloquea
            if (esAyudaExterior) {
                // Aquí podríamos validar que escriban "/bloquear duque" explícitamente
                mensajeGlobal("¡BLOQUEO! " + nombreJugador + " dice tener al DUQUE y bloquea la Ayuda Exterior.");
            } else {
                mensajeGlobal("¡BLOQUEO! " + nombreJugador + " bloqueó el ataque.");
            }

            // Cancelamos la acción y pasamos turno
            accionPendiente = null;
            atacantePendiente = null;
            victimaPendiente = null;
            juego.siguienteTurno();
            anunciarTurno();
            return;
        }

        // OPCIÓN A: LA VÍCTIMA PERMITE LA ACCIÓN
        if (comando.startsWith("/permitir")) {
            mensajeGlobal(victimaPendiente + " decidió NO bloquear.");

            Jugador atacante = obtenerJugador(atacantePendiente);
            String res = juego.ejecutarAccionPendiente(accionPendiente, atacante, victimaPendiente);

            accionPendiente = null;
            atacantePendiente = null;
            victimaPendiente = null;

            manejarResultadoAccion(cliente, res, atacante);
            return;
        }

        // OPCIÓN B: LA VÍCTIMA BLOQUEA
        if (comando.startsWith("/bloquear")) {
            String cartaBloqueo = comando.split(" ").length > 1 ? comando.split(" ")[1] : "carta";
            mensajeGlobal("¡BLOQUEO! " + victimaPendiente + " bloqueó el ataque usando " + cartaBloqueo + ".");
            accionPendiente = null;
            atacantePendiente = null;
            victimaPendiente = null;
            juego.siguienteTurno();
            anunciarTurno();
            return;
        }

        cliente.enviarMensaje("OPCIONES: Escribe '/permitir' para aceptar o '/bloquear <carta>' para defenderte.");
    }

    private void procesarFaseNormal(UnCliente cliente, String comandoCompleto) {
        String nombreJugador = cliente.getNombreUsuario();
        Jugador jugadorLogico = obtenerJugador(nombreJugador);

        if (jugadorLogico == null) {
            cliente.enviarMensaje("Error crítico: No estás en la lógica del juego.");
            return;
        }

        String[] partes = comandoCompleto.split(" ");
        String accion = partes[0].toLowerCase();
        String objetivo = (partes.length > 1) ? partes[1] : null;

        String resultado = "";

        switch (accion) {
            case "ingreso":
                resultado = juego.realizarAccionIngreso(jugadorLogico);
                break;
            case "ayuda":
                resultado = juego.iniciarAccionAyudaExterior(jugadorLogico);
                break;
            case "impuestos":
                resultado = juego.realizarAccionImpuestos(jugadorLogico);
                break;
            case "embajador":
                resultado = juego.realizarAccionEmbajador(jugadorLogico);
                break;
            case "robar":
                if (objetivo == null) resultado = "ERROR: Faltó objetivo.";
                else resultado = juego.iniciarAccionRobar(jugadorLogico, objetivo);
                break;
            case "asesinar":
                if (objetivo == null) resultado = "ERROR: Faltó objetivo.";
                else resultado = juego.iniciarAccionAsesinato(jugadorLogico, objetivo);
                break;
            case "golpe":
                if (objetivo == null) resultado = "ERROR: Faltó decir a quién dar golpe. Uso: /jugar golpe <nombre>";
                else resultado = juego.realizarGolpeDeEstado(jugadorLogico, objetivo);
                break;
            default:
                cliente.enviarMensaje("Acción desconocida. Comandos: ingreso, ayuda, impuestos, robar, asesinar, embajador, golpe.");
                return;
        }
        if (resultado.startsWith("INTENTO:")) {
            String[] datos = resultado.split(":");
            this.accionPendiente = datos[1];
            this.victimaPendiente = datos[2];
            this.atacantePendiente = nombreJugador;

            mensajeGlobal(">>> ¡ATAQUE! " + nombreJugador + " quiere " + accionPendiente + " a " + victimaPendiente + " <<<");

            UnCliente clienteVictima = puentesDeConexion.get(victimaPendiente);
            if (clienteVictima != null) {
                clienteVictima.enviarMensaje("¿Qué haces? Escribe: '/permitir' o '/bloquear <carta>'");
            }
        } else {
            manejarResultadoAccion(cliente, resultado, jugadorLogico);
        }
    }

    private void procesarFaseDescarte(UnCliente cliente, String comandoCompleto) {
        String nombreJugador = cliente.getNombreUsuario();

        if (!nombreJugador.equals(jugadorPendienteDeDescarte)) {
            cliente.enviarMensaje("ESPERA: Estamos esperando que " + jugadorPendienteDeDescarte + " descarte una carta.");
            return;
        }

        if (!comandoCompleto.toLowerCase().startsWith("descartar ")) {
            cliente.enviarMensaje("¡ACCIÓN REQUERIDA! Debes eliminar una influencia. Escribe: /jugar descartar <NombreCarta>");
            return;
        }

        String carta = comandoCompleto.split(" ")[1];
        String res = juego.concretarDescarte(nombreJugador, carta);

        if (res.startsWith("ERROR")) {
            cliente.enviarMensaje(res);
        } else {
            mensajeGlobal(res);
            jugadorPendienteDeDescarte = null;
            Jugador j = obtenerJugador(nombreJugador);
            enviarEstadoJugador(cliente, j);

            anunciarTurno();
        }
    }

    private void manejarResultadoAccion(UnCliente cliente, String resultado, Jugador jugadorLogico) {
        if (resultado.startsWith("ERROR")) {
            cliente.enviarMensaje(resultado);
            return;
        }
        if (resultado.startsWith("ESPERA_CARTA:")) {
            String victima = resultado.split(":")[1];
            jugadorPendienteDeDescarte = victima;

            mensajeGlobal("¡ATENCIÓN! " + victima + " ha sido impactado y debe perder una influencia.");

            UnCliente clienteVictima = puentesDeConexion.get(victima);
            if (clienteVictima != null) {
                clienteVictima.enviarMensaje(">>> ¡TE HAN ATACADO! <<<");
                clienteVictima.enviarMensaje("Debes elegir qué carta perder. Escribe: /jugar descartar <carta>");
                clienteVictima.enviarMensaje("Tus opciones: " + obtenerJugador(victima).getManoActual());
            }
            return;
        }
        mensajeGlobal(resultado);
        enviarEstadoJugador(cliente, jugadorLogico);

        anunciarTurno();
    }

    // -----------------------------------------------------------------
    // UTILIDADES
    // -----------------------------------------------------------------

    private void anunciarTurno() {
        Jugador activo = juego.getJugadorActivo();
        if (activo == null) return;

        for (UnCliente c : puentesDeConexion.values()) {
            if (c.getNombreUsuario().equals(activo.getNombreUsuario())) {
                c.enviarMensaje(">>> ¡ES TU TURNO! <<<");
            } else {
                c.enviarMensaje("Turno de: " + activo.getNombreUsuario());
            }
        }
    }

    private void mensajeGlobal(String msg) {
        for (UnCliente c : puentesDeConexion.values()) {
            c.enviarMensaje(msg);
        }
    }

    private void enviarEstadoJugador(UnCliente cliente, Jugador j) {
        cliente.enviarMensaje("Tus Cartas: " + j.getManoActual() + " | Monedas: " + j.getMonedas());
    }

    private Jugador obtenerJugador(String nombre) {
        for (Jugador j : juego.getJugadores()) {
            if (j.getNombreUsuario().equals(nombre)) return j;
        }
        return null;
    }
}