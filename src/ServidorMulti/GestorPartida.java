package ServidorMulti;

import JuegoCoup.Jugador;
import JuegoCoup.SalaCoup;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class GestorPartida {
    private static class EstadoPartida {
        String jugadorPendienteDeDescarte = null;
        String atacantePendiente = null;
        String victimaPendiente = null;
        String accionPendiente = null;
    }
    private final Map<String, SalaCoup> salasActivas;
    private final Map<String, String> jugadorEnSala;
    private final Map<String, UnCliente> puentesDeConexion;
    private final Map<String, String> invitacionesPendientes;
    private final Map<String, EstadoPartida> estadosPorSala;

    private static final int MIN_JUGADORES = 3;
    private static final int MAX_JUGADORES = 6;

    public GestorPartida() {
        this.salasActivas = new ConcurrentHashMap<>();
        this.jugadorEnSala = new ConcurrentHashMap<>();
        this.puentesDeConexion = new ConcurrentHashMap<>();
        this.invitacionesPendientes = new ConcurrentHashMap<>();
        this.estadosPorSala = new ConcurrentHashMap<>();
    }
    private SalaCoup obtenerSala(UnCliente cliente) {
        String idSala = jugadorEnSala.get(cliente.getNombreUsuario());
        return salasActivas.get(idSala);
    }

    private EstadoPartida obtenerEstado(String idSala) {
        return estadosPorSala.get(idSala);
    }

    private Jugador obtenerJugador(SalaCoup sala, String nombre) {
        for (Jugador j : sala.getJugadores()) {
            if (j.getNombreUsuario().equals(nombre)) return j;
        }
        return null;
    }

    private void anunciarTurno(SalaCoup sala) {
        Jugador activo = sala.getJugadorActivo();
        if (activo == null) return;

        String nombreSala = sala.getNombreSala();

        for (Jugador j : sala.getJugadores()) {
            UnCliente c = puentesDeConexion.get(j.getNombreUsuario());
            if (c != null) {
                if (j.getNombreUsuario().equals(activo.getNombreUsuario())) {
                    c.enviarMensaje(">>> ¡ES TU TURNO en " + nombreSala + "! <<<");
                } else {
                    c.enviarMensaje("Turno en " + nombreSala + ": " + activo.getNombreUsuario());
                }
            }
        }
    }

    private void mensajeGlobalEnSala(String idSala, String msg) {
        SalaCoup sala = salasActivas.get(idSala);
        if (sala != null) {
            for (Jugador j : sala.getJugadores()) {
                UnCliente c = puentesDeConexion.get(j.getNombreUsuario());
                if (c != null) {
                    c.enviarMensaje(msg);
                }
            }
        }
    }

    private void enviarEstadoJugador(UnCliente cliente, Jugador j) {
        cliente.enviarMensaje("Tus Cartas: " + j.getManoActual() + " | Monedas: " + j.getMonedas());
    }
    public synchronized void registrarCliente(UnCliente clienteConexion) {
        String nombre = clienteConexion.getNombreUsuario();
        if (nombre != null && !puentesDeConexion.containsKey(nombre)) {
            puentesDeConexion.put(nombre, clienteConexion);
            clienteConexion.enviarMensaje("¡Listo! Opciones: /crear <nombre> | /unirse <ID> | /salas");
        }
    }

    public synchronized void eliminarJugador(String nombreUsuario) {
        invitacionesPendientes.remove(nombreUsuario);

        String idSala = jugadorEnSala.remove(nombreUsuario);
        if (idSala != null) {
            SalaCoup sala = salasActivas.get(idSala);
            if (sala != null) {
                sala.removerJugador(nombreUsuario);
                mensajeGlobalEnSala(idSala, "El jugador " + nombreUsuario + " ha abandonado la sala.");

                if (sala.getJugadores().isEmpty()) {
                    salasActivas.remove(idSala);
                    estadosPorSala.remove(idSala);
                    System.out.println("Sala " + idSala + " eliminada por estar vacía.");
                } else if (sala.isJuegoIniciado()) {
                    sala.siguienteTurno();
                    anunciarTurno(sala);
                }
            }
        }
    }

    public void mostrarSalasDisponibles(UnCliente cliente) {
        if (salasActivas.isEmpty()) {
            cliente.enviarMensaje("No hay salas activas. Usa /crear <nombre> para iniciar una.");
            return;
        }
        StringBuilder sb = new StringBuilder("Salas Activas:\n");
        salasActivas.forEach((id, sala) -> {
            sb.append("- Nombre: ").append(sala.getNombreSala())
                    .append(" (ID: ").append(id).append(")")
                    .append(" | Jugadores: ").append(sala.getJugadores().size())
                    .append("/" + MAX_JUGADORES + " ").append(sala.isJuegoIniciado() ? "(En curso)" : "(Esperando)").append("\n");
        });
        cliente.enviarMensaje(sb.toString());
    }
    public String abandonarSala(String nombreUsuario) {
        String idSala = jugadorEnSala.get(nombreUsuario);

        if (idSala == null) {
            return "ERROR: No estás actualmente en ninguna sala.";
        }

        SalaCoup sala = salasActivas.get(idSala);

        eliminarJugador(nombreUsuario);

        if (sala != null && sala.isJuegoIniciado()) {
            return "PARTIDA_ABANDONADA: Has abandonado la sala '" + sala.getNombreSala() + "' (Juego en curso).";
        }

        return "SALA_ABANDONADA: Has salido de la sala.";
    }
    public void eliminarJugadorDeSala(UnCliente creador, String nombreVictima) {
        String creadorNombre = creador.getNombreUsuario();
        String idSala = jugadorEnSala.get(creadorNombre);
        SalaCoup sala = salasActivas.get(idSala);

        if (sala == null) {
            creador.enviarMensaje("ERROR: No estás en ninguna sala.");
            return;
        }
        if (sala.isJuegoIniciado()) {
            creador.enviarMensaje("ERROR: No puedes eliminar jugadores una vez que la partida ha iniciado.");
            return;
        }
        if (!creadorNombre.equals(sala.getJugadores().get(0).getNombreUsuario())) {
            creador.enviarMensaje("ERROR: Solo el creador de la sala puede eliminar jugadores.");
            return;
        }
        if (nombreVictima.equals(creadorNombre)) {
            creador.enviarMensaje("ERROR: Usa /abandonar para salir de la sala.");
            return;
        }

        if (jugadorEnSala.containsKey(nombreVictima) && jugadorEnSala.get(nombreVictima).equals(idSala)) {

            eliminarJugador(nombreVictima);

            UnCliente clienteVictima = puentesDeConexion.get(nombreVictima);
            if (clienteVictima != null) {
                clienteVictima.enviarMensaje("Has sido eliminado de la sala " + sala.getNombreSala() + " por el anfitrión.");
                clienteVictima.enviarMensaje("¡Vuelve al menú principal! Opciones: /crear, /unirse, /salas");
            }
            creador.enviarMensaje("Jugador " + nombreVictima + " eliminado exitosamente.");
            mensajeGlobalEnSala(idSala, "El anfitrión ha eliminado a " + nombreVictima + " de la sala.");
        } else {
            creador.enviarMensaje("ERROR: El usuario " + nombreVictima + " no está en tu sala.");
        }
    }
    public void invitarUsuarios(UnCliente remitente, String[] invitados) {
        String remitenteNombre = remitente.getNombreUsuario();
        String idSala = jugadorEnSala.get(remitenteNombre);
        SalaCoup sala = salasActivas.get(idSala);

        if (sala == null) {
            remitente.enviarMensaje("ERROR: Debes estar en una sala para invitar.");
            return;
        }
        if (sala.isJuegoIniciado()) {
            remitente.enviarMensaje("ERROR: La partida ya inició.");
            return;
        }

        for (String invitadoNombre : invitados) {

            if (invitadoNombre.equals(remitenteNombre)) {
                remitente.enviarMensaje("ERROR: No puedes invitarte a ti mismo.");
                continue;
            }

            UnCliente invitadoCliente = puentesDeConexion.get(invitadoNombre);

            if (sala.getJugadores().size() >= MAX_JUGADORES) {
                remitente.enviarMensaje("ERROR: La sala " + sala.getNombreSala() + " ya está llena.");
                break;
            }

            if (invitadoCliente != null) {
                if (!jugadorEnSala.containsKey(invitadoNombre)) {
                    invitacionesPendientes.put(invitadoNombre, idSala);
                    invitadoCliente.enviarMensaje("INVITACIÓN: Has sido invitado a la sala " + sala.getNombreSala() + " (ID: " + idSala + ") por " + remitenteNombre + ". Responde /si o /no.");
                    remitente.enviarMensaje("Invitación enviada a " + invitadoNombre);
                } else {
                    SalaCoup otraSala = salasActivas.get(jugadorEnSala.get(invitadoNombre));
                    remitente.enviarMensaje("ERROR: El usuario " + invitadoNombre + " ya está en la sala " + (otraSala != null ? otraSala.getNombreSala() : "desconocida") + ".");
                }
            } else {
                remitente.enviarMensaje("ERROR: El usuario " + invitadoNombre + " no está conectado o el nombre de usuario no existe.");
            }
        }
    }
    public void responderInvitacion(UnCliente cliente, String respuesta) {
        String usuario = cliente.getNombreUsuario();
        String idSala = invitacionesPendientes.remove(usuario);

        if (idSala == null) {
            cliente.enviarMensaje("No tienes invitaciones pendientes.");
            return;
        }

        SalaCoup sala = salasActivas.get(idSala);
        if (sala == null) {
            cliente.enviarMensaje("ERROR: La sala ya no existe.");
            return;
        }

        String anfitrionNombre = sala.getJugadores().get(0).getNombreUsuario();
        UnCliente remitente = puentesDeConexion.get(anfitrionNombre);

        if (respuesta.equalsIgnoreCase("/si")) {

            if (sala.isJuegoIniciado()) {
                cliente.enviarMensaje("UPS, DEMASIADO TARDE: La partida ya fue iniciada en la sala " + sala.getNombreSala() + ".");
                if (remitente != null) remitente.enviarMensaje("NOTIFICACIÓN: " + usuario + " intentó unirse, pero el juego ya estaba en curso.");
                return;
            }

            if (sala.getJugadores().size() < MAX_JUGADORES) {
                sala.agregarJugador(usuario);
                jugadorEnSala.put(usuario, idSala);
                mensajeGlobalEnSala(idSala, usuario + " se ha unido a la sala.");
                cliente.enviarMensaje("Menú de Sala: /abandonar");

                if (sala.getJugadores().size() >= MIN_JUGADORES) {
                    cliente.enviarMensaje("La sala tiene " + sala.getJugadores().size() + "/" + MAX_JUGADORES + " jugadores. El creador de la sala puede usar /iniciar.");
                }
            } else {
                cliente.enviarMensaje("ERROR: La sala está llena.");
            }
        } else if (respuesta.equalsIgnoreCase("/no")) {
            cliente.enviarMensaje("Invitación rechazada.");
            if (remitente != null) {
                remitente.enviarMensaje("NOTIFICACIÓN: El usuario " + usuario + " rechazó tu invitación a la sala " + sala.getNombreSala() + ".");
            }
        }
    }


    public void crearSala(UnCliente creador, String nombreDeseado) {
        String usuario = creador.getNombreUsuario();
        String idSalaActual = jugadorEnSala.get(usuario);

        if (idSalaActual != null) {
            SalaCoup salaActual = salasActivas.get(idSalaActual);
            if (salaActual != null) {
                creador.enviarMensaje("ERROR: Ya estás en la sala " + salaActual.getNombreSala() + " (ID: " + idSalaActual + ").");
            }
            return;
        }

        String idSala = UUID.randomUUID().toString().substring(0, 5).toUpperCase();

        String nombreSala = (nombreDeseado != null && !nombreDeseado.trim().isEmpty()) ?
                nombreDeseado.trim() :
                "Sala-" + idSala;

        SalaCoup nuevaSala = new SalaCoup(idSala, nombreSala);
        nuevaSala.agregarJugador(usuario);

        salasActivas.put(idSala, nuevaSala);
        jugadorEnSala.put(usuario, idSala);
        estadosPorSala.put(idSala, new EstadoPartida());

        creador.enviarMensaje("SALA CREADA: " + nombreSala + " (ID: " + idSala + "). Jugadores: 1/" + MAX_JUGADORES + ".");
        creador.enviarMensaje("Menú de Sala: /iniciar | /abandonar | /eliminar <usuario> | /invitar <usuario>");
    }

    public void unirseASala(String idSala, UnCliente cliente) {
        SalaCoup sala = salasActivas.get(idSala);
        if (sala != null && !sala.isJuegoIniciado()) {
            if (sala.getJugadores().size() < MAX_JUGADORES) {
                sala.agregarJugador(cliente.getNombreUsuario());
                jugadorEnSala.put(cliente.getNombreUsuario(), idSala);
                mensajeGlobalEnSala(idSala, cliente.getNombreUsuario() + " se ha unido a la sala " + sala.getNombreSala() + ".");
                cliente.enviarMensaje("Menú de Sala: /abandonar");
            } else {
                cliente.enviarMensaje("ERROR: La sala " + sala.getNombreSala() + " está llena.");
            }
        } else {
            cliente.enviarMensaje("ERROR: La sala " + idSala + " no existe o el juego ya ha comenzado.");
        }
    }
    public void iniciarJuego(UnCliente cliente) {
        String idSala = jugadorEnSala.get(cliente.getNombreUsuario());
        SalaCoup sala = salasActivas.get(idSala);

        if (sala == null) {
            cliente.enviarMensaje("ERROR: No estás en una sala.");
            return;
        }

        if (!cliente.getNombreUsuario().equals(sala.getJugadores().get(0).getNombreUsuario())) {
            cliente.enviarMensaje("ERROR: Solo el creador de la sala puede iniciar la partida.");
            return;
        }
        if (sala.getJugadores().size() < MIN_JUGADORES) {
            cliente.enviarMensaje("ERROR: Se necesitan al menos " + MIN_JUGADORES + " jugadores para iniciar. Jugadores actuales: " + sala.getJugadores().size());
            return;
        }

        arrancarJuego(sala);
    }

    private void arrancarJuego(SalaCoup sala) {
        String idSala = sala.getIdSala();
        String nombreSala = sala.getNombreSala();
        try {
            sala.iniciarPartida();
            mensajeGlobalEnSala(idSala, "--- ¡PARTIDA INICIADA en la sala " + nombreSala + "! ---");

            for (Jugador jLogico : sala.getJugadores()) {
                UnCliente socketDestino = puentesDeConexion.get(jLogico.getNombreUsuario());
                if (socketDestino != null) {
                    enviarEstadoJugador(socketDestino, jLogico);
                }
            }
            anunciarTurno(sala);
        } catch (Exception e) {
            mensajeGlobalEnSala(idSala, "Error al iniciar: " + e.getMessage());
        }
    }
    public synchronized void procesarJugada(UnCliente cliente, String comandoCompleto) {
        SalaCoup sala = obtenerSala(cliente);

        if (sala == null || !sala.isJuegoIniciado()) {
            cliente.enviarMensaje("ERROR: No estás en una partida activa.");
            return;
        }

        EstadoPartida estado = obtenerEstado(sala.getIdSala());
        if (estado == null) return;

        if (estado.jugadorPendienteDeDescarte != null) {
            procesarFaseDescarte(cliente, comandoCompleto, sala, estado);
            return;
        }

        if (estado.accionPendiente != null) {
            procesarFaseBloqueo(cliente, comandoCompleto, sala, estado);
            return;
        }

        procesarFaseNormal(cliente, comandoCompleto, sala, estado);
    }

    private void procesarFaseBloqueo(UnCliente cliente, String comandoCompleto, SalaCoup sala, EstadoPartida estado) {
        String nombreJugador = cliente.getNombreUsuario();
        String comando = comandoCompleto.toLowerCase();
        String idSala = sala.getIdSala();

        boolean esAyudaExterior = "TODOS".equals(estado.victimaPendiente) && "AYUDA".equals(estado.accionPendiente);

        if (!esAyudaExterior && !nombreJugador.equals(estado.victimaPendiente)) {
            cliente.enviarMensaje("SILENCIO: Solo " + estado.victimaPendiente + " puede responder.");
            return;
        }

        if (nombreJugador.equals(estado.atacantePendiente)) {
            cliente.enviarMensaje("No puedes bloquear tu propia acción.");
            return;
        }

        if (comando.startsWith("/bloquear")) {
            if (esAyudaExterior) {
                mensajeGlobalEnSala(idSala, "¡BLOQUEO! " + nombreJugador + " dice tener al DUQUE y bloquea la Ayuda Exterior.");
            } else {
                mensajeGlobalEnSala(idSala, "¡BLOQUEO! " + nombreJugador + " bloqueó el ataque.");
            }
            estado.accionPendiente = null;
            estado.atacantePendiente = null;
            estado.victimaPendiente = null;
            sala.siguienteTurno();
            anunciarTurno(sala);
            return;
        }

        if (comando.startsWith("/permitir")) {
            mensajeGlobalEnSala(idSala, estado.victimaPendiente + " decidió NO bloquear.");

            Jugador atacante = obtenerJugador(sala, estado.atacantePendiente);
            String res = sala.ejecutarAccionPendiente(estado.accionPendiente, atacante, estado.victimaPendiente);

            estado.accionPendiente = null;
            estado.atacantePendiente = null;
            estado.victimaPendiente = null;

            manejarResultadoAccion(cliente, res, atacante, sala, estado);
            return;
        }

        cliente.enviarMensaje("OPCIONES: Escribe '/permitir' para aceptar o '/bloquear <carta>' para defenderte.");
    }

    private void procesarFaseNormal(UnCliente cliente, String comandoCompleto, SalaCoup sala, EstadoPartida estado) {
        String nombreJugador = cliente.getNombreUsuario();
        Jugador jugadorLogico = obtenerJugador(sala, nombreJugador);
        String idSala = sala.getIdSala();

        if (jugadorLogico == null) {
            cliente.enviarMensaje("Error crítico: No estás en la lógica del juego.");
            return;
        }

        String[] partes = comandoCompleto.split(" ");
        String accion = partes[0].toLowerCase();
        String objetivo = (partes.length > 1) ? partes[1] : null;

        String resultado = "";

        switch (accion) {
            case "ingreso": resultado = sala.realizarAccionIngreso(jugadorLogico); break;
            case "ayuda": resultado = sala.iniciarAccionAyudaExterior(jugadorLogico); break;
            case "impuestos": resultado = sala.realizarAccionImpuestos(jugadorLogico); break;
            case "embajador": resultado = sala.realizarAccionEmbajador(jugadorLogico); break;
            case "robar":
                if (objetivo == null) resultado = "ERROR: Faltó objetivo.";
                else resultado = sala.iniciarAccionRobar(jugadorLogico, objetivo);
                break;
            case "asesinar":
                if (objetivo == null) resultado = "ERROR: Faltó objetivo.";
                else resultado = sala.iniciarAccionAsesinato(jugadorLogico, objetivo);
                break;
            case "golpe":
                if (objetivo == null) resultado = "ERROR: Faltó decir a quién dar golpe. Uso: /jugar golpe <nombre>";
                else resultado = sala.realizarGolpeDeEstado(jugadorLogico, objetivo);
                break;
            default:
                cliente.enviarMensaje("Acción desconocida. Comandos: ingreso, ayuda, impuestos, robar, asesinar, embajador, golpe.");
                return;
        }

        if (resultado.startsWith("INTENTO:")) {
            String[] datos = resultado.split(":");
            estado.accionPendiente = datos[1];
            estado.victimaPendiente = datos[2];
            estado.atacantePendiente = nombreJugador;

            mensajeGlobalEnSala(idSala, ">>> ¡ATAQUE! " + nombreJugador + " quiere " + estado.accionPendiente + " a " + estado.victimaPendiente + " <<<");

            UnCliente clienteVictima = puentesDeConexion.get(estado.victimaPendiente);
            if (clienteVictima != null) {
                clienteVictima.enviarMensaje("¿Qué haces? Escribe: '/permitir' o '/bloquear <carta>'");
            }
        } else {
            manejarResultadoAccion(cliente, resultado, jugadorLogico, sala, estado);
        }
    }

    private void procesarFaseDescarte(UnCliente cliente, String comandoCompleto, SalaCoup sala, EstadoPartida estado) {
        String nombreJugador = cliente.getNombreUsuario();
        String idSala = sala.getIdSala();

        if (!nombreJugador.equals(estado.jugadorPendienteDeDescarte)) {
            cliente.enviarMensaje("ESPERA: Estamos esperando que " + estado.jugadorPendienteDeDescarte + " descarte una carta.");
            return;
        }

        if (!comandoCompleto.toLowerCase().startsWith("descartar ")) {
            cliente.enviarMensaje("¡ACCIÓN REQUERIDA! Debes eliminar una influencia. Escribe: /jugar descartar <NombreCarta>");
            return;
        }

        String carta = comandoCompleto.split(" ")[1];
        String res = sala.concretarDescarte(nombreJugador, carta);

        if (res.startsWith("ERROR")) {
            cliente.enviarMensaje(res);
        } else {
            mensajeGlobalEnSala(idSala, res);
            estado.jugadorPendienteDeDescarte = null;

            Jugador j = obtenerJugador(sala, nombreJugador);
            enviarEstadoJugador(cliente, j);

            anunciarTurno(sala);
        }
    }

    private void manejarResultadoAccion(UnCliente cliente, String resultado, Jugador jugadorLogico, SalaCoup sala, EstadoPartida estado) {
        String idSala = sala.getIdSala();

        if (resultado.startsWith("ERROR")) {
            cliente.enviarMensaje(resultado);
            return;
        }
        if (resultado.startsWith("ESPERA_CARTA:")) {
            String victima = resultado.split(":")[1];
            estado.jugadorPendienteDeDescarte = victima;

            mensajeGlobalEnSala(idSala, "¡ATENCIÓN! " + victima + " ha sido impactado y debe perder una influencia.");

            UnCliente clienteVictima = puentesDeConexion.get(victima);
            if (clienteVictima != null) {
                clienteVictima.enviarMensaje(">>> ¡TE HAN ATACADO! <<<");
                clienteVictima.enviarMensaje("Debes elegir qué carta perder. Escribe: /jugar descartar <carta>");
                clienteVictima.enviarMensaje("Tus opciones: " + obtenerJugador(sala, victima).getManoActual());
            }
            return;
        }
        mensajeGlobalEnSala(idSala, resultado);
        enviarEstadoJugador(cliente, jugadorLogico);

        anunciarTurno(sala);
    }
}