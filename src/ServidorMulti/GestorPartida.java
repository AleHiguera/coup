package ServidorMulti;

import JuegoCoup.Jugador;
import JuegoCoup.SalaCoup;
import JuegoCoup.TipoCarta;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class GestorPartida {
    private static class EstadoPartida {
        String jugadorPendienteDeDescarte, atacantePendiente, victimaPendiente, accionPendiente;
        boolean segundaMuertePendiente = false;
        List<TipoCarta> cartasEmbajadorPendientes;
    }

    private final Map<String, SalaCoup> salasActivas = new ConcurrentHashMap<>();
    private final Map<String, String> jugadorEnSala = new ConcurrentHashMap<>();
    private final Map<String, UnCliente> puentesDeConexion = new ConcurrentHashMap<>();
    private final Map<String, String> invitacionesPendientes = new ConcurrentHashMap<>();
    private final Map<String, EstadoPartida> estadosPorSala = new ConcurrentHashMap<>();

    private final Map<String, Timer> timersPorSala = new ConcurrentHashMap<>();
    private final Map<String, String> espectadorEnSala = new ConcurrentHashMap<>();
    private static final String MSG_ELIMINADO = "ESTADO:ELIMINADO";

    private static final int MIN_JUGADORES = 3, MAX_JUGADORES = 6;

    private void iniciarTemporizador(String idSala, int segundos, Runnable accionExpiracion) {
        cancelarTemporizador(idSala);
        Timer timer = new Timer();
        timer.schedule(new TimerTask() {
            @Override
            public void run() {

                synchronized (GestorPartida.this) {

                    if (salasActivas.containsKey(idSala)) {
                        accionExpiracion.run();
                    }
                    timersPorSala.remove(idSala);
                }
            }
        }, segundos * 1000L);
        timersPorSala.put(idSala, timer);
    }


    private void cancelarTemporizador(String idSala) {
        Timer t = timersPorSala.remove(idSala);
        if (t != null) {
            t.cancel();}
    }

    public synchronized void registrarCliente(UnCliente c) {
        if (c.getNombreUsuario() != null && !puentesDeConexion.containsKey(c.getNombreUsuario())) {
            puentesDeConexion.put(c.getNombreUsuario(), c);
            c.enviarMensaje("Opciones: /crear, /unirse, /salas, /espectador");}
    }

    public void crearSala(UnCliente creador, String nombreDeseado) {
        if (jugadorEnSala.containsKey(creador.getNombreUsuario())) {
            creador.enviarMensaje("Ya estas en una sala."); return;
        }
        generarYRegistrarSala(creador, nombreDeseado);
    }

    private void generarYRegistrarSala(UnCliente creador, String nombreDeseado) {
        String idSala = UUID.randomUUID().toString().substring(0, 5).toUpperCase();
        String nombre = (nombreDeseado != null && !nombreDeseado.isBlank()) ? nombreDeseado.trim() : "Sala-"+idSala;
        inicializarSala(idSala, nombre, creador);}

    private void inicializarSala(String id, String nombre, UnCliente creador) {
        SalaCoup sala = new SalaCoup(id, nombre);
        sala.agregarJugador(creador.getNombreUsuario());
        salasActivas.put(id, sala);
        jugadorEnSala.put(creador.getNombreUsuario(), id);
        estadosPorSala.put(id, new EstadoPartida());
        notificarCreacion(creador, nombre, id);}

    private void notificarCreacion(UnCliente c, String nom, String id) {
        c.enviarMensaje("SALA CREADA: " + nom + " (ID: " + id + ")");
        c.enviarMensaje("Comandos: /iniciar, /invitar, /abandonar, /eliminar");}

    public boolean unirseASala(String idSala, UnCliente cliente) {
        SalaCoup sala = salasActivas.get(idSala);
        if (validarUnion(sala, cliente)) {
            ejecutarUnion(sala, cliente, idSala);
            return true;
        }
        return false;
    }

    private boolean validarUnion(SalaCoup sala, UnCliente c) {
        if (sala == null) {
            c.enviarMensaje("Sala no existe.");
            c.enviarMensaje("Opciones: /crear, /unirse, /salas, /espectador");
            return false;}
        if (sala.isJuegoIniciado()) {
            c.enviarMensaje("UPS, demasiado tarde. Esta sala ya inició.");
            c.enviarMensaje("Opciones: /crear, /unirse, /salas, /espectador");
            return false;}

        if (sala.getJugadores().size() >= MAX_JUGADORES) {
            c.enviarMensaje("Sala llena.");
            c.enviarMensaje("Opciones: /crear, /unirse, /salas, /espectador");
            return false;}
        return true;
    }

    private void ejecutarUnion(SalaCoup sala, UnCliente c, String id) {
        sala.agregarJugador(c.getNombreUsuario());
        jugadorEnSala.put(c.getNombreUsuario(), id);
        mensajeGlobalEnSala(id, c.getNombreUsuario() + " se unio.");
        c.enviarMensaje("Usa /abandonar para salir.");}

    public synchronized void procesarJugada(UnCliente cliente, String cmd) {
        SalaCoup sala = obtenerSala(cliente);
        if (!validarJugadaActiva(sala, cliente)) return;

        cancelarTemporizador(sala.getIdSala());

        EstadoPartida estado = estadosPorSala.get(sala.getIdSala());
        delegarFaseJuego(cliente, cmd, sala, estado);}

    private boolean validarJugadaActiva(SalaCoup sala, UnCliente c) {
        if (sala == null || !sala.isJuegoIniciado()) {
            c.enviarMensaje("No estas en partida activa."); return false;
        }
        return true;
    }

    private void delegarFaseJuego(UnCliente c, String cmd, SalaCoup s, EstadoPartida e) {
        if (e.jugadorPendienteDeDescarte != null) procesarFaseDescarte(c, cmd, s, e);
        else if ("ESPERANDO_SELECCION_EMBAJADOR".equals(e.accionPendiente)) procesarSeleccionEmbajador(c, cmd, s, e);
        else if (e.accionPendiente != null) procesarFaseBloqueo(c, cmd, s, e);
        else procesarFaseNormal(c, cmd, s, e);
    }

    private void procesarFaseNormal(UnCliente c, String cmd, SalaCoup s, EstadoPartida e) {
        Jugador j = obtenerJugador(s, c.getNombreUsuario());
        if (j == null) return;

        String[] partes = cmd.split(" ");
        String accion = partes[0].toLowerCase();

        if (j.getMonedas() >= 10 && !accion.equals("golpe")) {
            c.enviarMensaje("¡REGLA DE ORO! Tienes " + j.getMonedas() + " monedas.");
            c.enviarMensaje("Estás OBLIGADO a dar un Golpe de Estado.");
            c.enviarMensaje("Uso: /jugar golpe <jugador>");

            iniciarTemporizador(s.getIdSala(), 60, () -> forzarFinTurno(s));
            return;}

        String res = ejecutarAccionEnSala(s, j, cmd);
        procesarResultadoAccion(c, res, s, e, j);
    }

    private String ejecutarAccionEnSala(SalaCoup s, Jugador j, String cmd) {
        String[] partes = cmd.split(" ");
        String accion = partes[0].toLowerCase();
        String obj = (partes.length > 1) ? partes[1] : null;
        return despacharAccion(s, j, accion, obj);}

    private String despacharAccion(SalaCoup s, Jugador j, String act, String obj) {
        if (act.equals(Constantes.ACCION_INGRESO)) return s.realizarAccionIngreso(j);
        if (act.equals(Constantes.ACCION_AYUDA)) return s.iniciarAccionAyudaExterior(j);
        if (act.equals(Constantes.ACCION_GOLPE)) return (obj != null) ? s.realizarGolpeDeEstado(j, obj) : Constantes.PREFIJO_ERROR + " Falta objetivo";
        return despacharAccionCompleja(s, j, act, obj);}

    private String despacharAccionCompleja(SalaCoup s, Jugador j, String act, String obj) {
        if (act.equals(Constantes.ACCION_IMPUESTOS)) return s.realizarAccionImpuestos(j);
        if (act.equals(Constantes.ACCION_EMBAJADOR)) return s.realizarAccionEmbajador(j);
        if (act.equals(Constantes.ACCION_ROBAR)) return (obj!=null) ? s.iniciarAccionRobar(j, obj) : Constantes.PREFIJO_ERROR + " Falta objetivo";
        if (act.equals(Constantes.ACCION_ASESINAR)) return (obj!=null) ? s.iniciarAccionAsesinato(j, obj) : Constantes.PREFIJO_ERROR + " Falta objetivo";
        return "Accion desconocida.";}

    private void procesarResultadoAccion(UnCliente c, String res, SalaCoup s, EstadoPartida e, Jugador j) {
        if (res.startsWith(Constantes.PREFIJO_ERROR)) {
            c.enviarMensaje(res);
            iniciarTemporizador(s.getIdSala(), 60, () -> forzarFinTurno(s));
            return;}

        if (res.startsWith(Constantes.PREFIJO_INTENTO)) iniciarDesafio(res, s, e, c.getNombreUsuario());
        else if (res.startsWith(Constantes.PREFIJO_ESPERA)) iniciarDescarte(res, s, e);
        else finalizarTurnoNormal(s, res, c, j);
    }

    private void iniciarDesafio(String res, SalaCoup s, EstadoPartida e, String atacante) {
        String[] datos = res.split(":");
        configurarEstadoDesafio(e, datos[1], datos[2], atacante);
        notificarDesafio(s.getIdSala(), atacante, e);}

    private void configurarEstadoDesafio(EstadoPartida e, String acc, String vic, String atq) {
        e.accionPendiente = acc; e.victimaPendiente = vic; e.atacantePendiente = atq;
    }

    private void notificarDesafio(String idSala, String atq, EstadoPartida e) {
        mensajeGlobalEnSala(idSala, ">>> " + atq + " quiere " + e.accionPendiente + " a " + e.victimaPendiente);
        mensajeGlobalEnSala(idSala, "Cualquiera puede: /dudar");
        UnCliente v = puentesDeConexion.get(e.victimaPendiente);
        if (v != null) v.enviarMensaje("Responde: /permitir, /bloquear o /dudar");

        mensajeGlobalEnSala(idSala, "10 segundos para reaccionar...");
        iniciarTemporizador(idSala, 10, () -> {
            mensajeGlobalEnSala(idSala, "Tiempo agotado. Acción permitida tácitamente.");
            ejecutarPermitir(salasActivas.get(idSala), e);
        });
    }

    private void finalizarTurnoNormal(SalaCoup s, String res, UnCliente c, Jugador j) {
        mensajeGlobalEnSala(s.getIdSala(), res);
        enviarEstadoJugador(c, j);
        anunciarTurno(s);}

    private void procesarFaseBloqueo(UnCliente c, String cmd, SalaCoup s, EstadoPartida e) {
        if (cmd.equalsIgnoreCase("/dudar")) {
            if (c.getNombreUsuario().equals(e.atacantePendiente)) {
                c.enviarMensaje("No puedes dudar de ti mismo.");
                iniciarTemporizador(s.getIdSala(), 10, () -> {
                    mensajeGlobalEnSala(s.getIdSala(), "Tiempo agotado. Acción permitida tácitamente.");
                    ejecutarPermitir(s, e);
                });
                return;
            }
            ejecutarDuda(s, e, c.getNombreUsuario());
            return;
        }

        if (!validarInteraccionBloqueo(c, e)) {
            iniciarTemporizador(s.getIdSala(), 10, () -> {
                mensajeGlobalEnSala(s.getIdSala(), "Tiempo agotado. Acción permitida tácitamente.");
                ejecutarPermitir(s, e);
            });
            return;}

        if (cmd.startsWith("/bloquear")) ejecutarBloqueo(s, e, c.getNombreUsuario());
        else if (cmd.startsWith("/permitir")) ejecutarPermitir(s, e);
        else {
            c.enviarMensaje("Opciones: /permitir, /bloquear o /dudar");
            iniciarTemporizador(s.getIdSala(), 10, () -> {
                mensajeGlobalEnSala(s.getIdSala(), "Tiempo agotado. Acción permitida tácitamente.");
                ejecutarPermitir(s, e);
            });
        }
    }

    private void ejecutarDuda(SalaCoup s, EstadoPartida e, String retador) {
        String resultado = s.resolverDesafio(e.atacantePendiente, retador, e.accionPendiente);

        if (resultado.startsWith(Constantes.PREFIJO_DESAFIO_EXITOSO)) {
            String acusado = resultado.split(":")[1];
            mensajeGlobalEnSala(s.getIdSala(), "¡" + retador + " GANÓ! " + acusado + " mintió y pierde carta.");
            if (e.accionPendiente.equals("BLOQUEO_ASESINATO") || e.accionPendiente.equals(Constantes.ACCION_ASESINAR)) {
                e.segundaMuertePendiente = true;
            }
            iniciarDescarte(Constantes.PREFIJO_ESPERA + acusado, s, e);
        } else if (resultado.startsWith(Constantes.PREFIJO_DESAFIO_FALLIDO)) {
            String perdedor = resultado.split(":")[1];
            mensajeGlobalEnSala(s.getIdSala(), "¡FALLASTE! " + e.atacantePendiente + " tenía la carta. " + perdedor + " pierde vida.");

            String resAccion = s.ejecutarAccionPendiente(e.accionPendiente, obtenerJugador(s, e.atacantePendiente), e.victimaPendiente);
            if (resAccion.equals("SELECCION_EMBAJADOR")) {
                iniciarFaseSeleccionEmbajador(s, e, e.atacantePendiente);
                return;
            }
            if (e.accionPendiente.equals(Constantes.ACCION_ASESINAR)) {
                e.segundaMuertePendiente = true;
            } else {
                mensajeGlobalEnSala(s.getIdSala(), "Efecto de la acción: " + resAccion);
            }
            iniciarDescarte(Constantes.PREFIJO_ESPERA + perdedor, s, e);
        } else {
            mensajeGlobalEnSala(s.getIdSala(), resultado);
            iniciarTemporizador(s.getIdSala(), 10, () -> {
                mensajeGlobalEnSala(s.getIdSala(), "Tiempo agotado. Acción permitida tácitamente.");
                ejecutarPermitir(s, e);
            });
        }
    }

    private boolean validarInteraccionBloqueo(UnCliente c, EstadoPartida e) {
        boolean esAyuda = "TODOS".equals(e.victimaPendiente) && "AYUDA".equals(e.accionPendiente);
        if (!esAyuda && !c.getNombreUsuario().equals(e.victimaPendiente)) {
            c.enviarMensaje("Espera tu turno."); return false;
        }
        return true;
    }

    private void ejecutarBloqueo(SalaCoup s, EstadoPartida e, String quien) {
        mensajeGlobalEnSala(s.getIdSala(), "¡BLOQUEO por " + quien + "!");
        limpiarEstado(e);
        s.siguienteTurno();
        anunciarTurno(s);
    }

    private void ejecutarPermitir(SalaCoup s, EstadoPartida e) {
        mensajeGlobalEnSala(s.getIdSala(), e.victimaPendiente + " permitio la accion.");
        String res = s.ejecutarAccionPendiente(e.accionPendiente, obtenerJugador(s, e.atacantePendiente), e.victimaPendiente);
        if (res.equals("SELECCION_EMBAJADOR")) {
            iniciarFaseSeleccionEmbajador(s, e, e.atacantePendiente);
            return;
        }
        String atacantePrevio = e.atacantePendiente;
        limpiarEstado(e);
        if (res.startsWith("ESPERA_CARTA:")) iniciarDescarte(res, s, e);
        else finalizarResolucionPermitida(s, res, atacantePrevio);
    }

    private void finalizarResolucionPermitida(SalaCoup s, String res, String atacante) {
        mensajeGlobalEnSala(s.getIdSala(), res);
        UnCliente c = puentesDeConexion.get(atacante);
        if (c != null) enviarEstadoJugador(c, obtenerJugador(s, atacante));
        anunciarTurno(s);
    }

    private void procesarFaseDescarte(UnCliente c, String cmd, SalaCoup s, EstadoPartida e) {
        if (!c.getNombreUsuario().equals(e.jugadorPendienteDeDescarte)) return;
        if (!cmd.startsWith("descartar ")) {
            c.enviarMensaje("Usa: /jugar descartar <carta>");
            return;
        }
        ejecutarDescarte(c, cmd.split(" ")[1], s, e);
    }

    private void ejecutarDescarte(UnCliente c, String carta, SalaCoup s, EstadoPartida e) {
        String res = s.concretarDescarte(c.getNombreUsuario(), carta);
        if (res.startsWith("ERROR")) {
            c.enviarMensaje(res);
        }
        else finalizarDescarte(s, e, c, res);
    }

    private void finalizarDescarte(SalaCoup s, EstadoPartida e, UnCliente c, String res) {
        mensajeGlobalEnSala(s.getIdSala(), res);
        e.jugadorPendienteDeDescarte = null;
        if (res.startsWith(Constantes.PREFIJO_ELIMINADO)) {
            c.enviarMensaje(MSG_ELIMINADO);
            jugadorEnSala.remove(c.getNombreUsuario());
            espectadorEnSala.put(c.getNombreUsuario(), s.getIdSala());

            c.enviarMensaje("Has perdido toda tu influencia.");
            c.enviarMensaje("Opciones: /abandonar, /espectador_quedarme");

            iniciarTemporizador(s.getIdSala(), 30, () -> forzarAbandonoOEspectador(s, c.getNombreUsuario()));
            limpiarEstado(e);
            String resultadoFin = s.verificarGanador();
            if (resultadoFin != null) {
                finalizarPartida(s, resultadoFin);
                return;
            }
            anunciarTurno(s);
            return;
        }
        if (e.segundaMuertePendiente) {
            Jugador victima = obtenerJugador(s, c.getNombreUsuario());
            if (victima != null && victima.estaVivo()) {
                e.segundaMuertePendiente = false;
                mensajeGlobalEnSala(s.getIdSala(), "☠️ ¡DOBLE PELIGRO! El asesinato sigue en pie (o mentiste sobre la Condesa).");
                mensajeGlobalEnSala(s.getIdSala(), victima.getNombreUsuario() + " debe perder otra influencia inmediatamente.");
                iniciarDescarte(Constantes.PREFIJO_ESPERA + victima.getNombreUsuario(), s, e);
                return;
            }
        }
        limpiarEstado(e);
        anunciarTurno(s);
    }

    private void iniciarDescarte(String res, SalaCoup s, EstadoPartida e) {
        String victima = res.split(":")[1];
        e.jugadorPendienteDeDescarte = victima;
        notificarNecesidadDescarte(s.getIdSala(), victima);
    }

    private void notificarNecesidadDescarte(String id, String victima) {
        mensajeGlobalEnSala(id, victima + " debe perder una influencia.");
        UnCliente c = puentesDeConexion.get(victima);
        if (c != null) c.enviarMensaje("Pierdes un reto. Usa: /jugar descartar <carta>");

        mensajeGlobalEnSala(id, "Tienes 60s para descartar...");
        iniciarTemporizador(id, 60, () -> {
            mensajeGlobalEnSala(id, "Tiempo agotado. Se descarta automáticamente.");
            SalaCoup sala = salasActivas.get(id);
            Jugador j = obtenerJugador(sala, victima);
            EstadoPartida est = estadosPorSala.get(id);
            if (j != null && !j.getManoActual().isEmpty()) {
                TipoCarta primeraCarta = j.getManoActual().get(0);
                UnCliente clienteVic = puentesDeConexion.get(victima);
                ejecutarDescarte(clienteVic, primeraCarta.name(), sala, est);
            }
        });
    }

    private void limpiarEstado(EstadoPartida e) {
        e.accionPendiente = null; e.atacantePendiente = null; e.victimaPendiente = null; e.segundaMuertePendiente = false;
    }

    private SalaCoup obtenerSala(UnCliente c) {
        String nombre = c.getNombreUsuario();
        String idSala = jugadorEnSala.get(nombre);
        return salasActivas.get(idSala);
    }

    private Jugador obtenerJugador(SalaCoup s, String n) {
        for(Jugador j : s.getJugadores()) if(j.getNombreUsuario().equals(n)) return j; return null;
    }

    private void mensajeGlobalEnSala(String id, String m) {
        SalaCoup s = salasActivas.get(id);
        if (s != null) {
            for (Jugador j : s.getJugadores()) enviarAC(j.getNombreUsuario(), m);
            for (Map.Entry<String, String> entry : espectadorEnSala.entrySet()) {
                if (entry.getValue().equals(id)) {
                    enviarAC(entry.getKey(), "[Espectador] " + m);}
            }
        }
    }

    private void enviarAC(String u, String m) { UnCliente c = puentesDeConexion.get(u); if(c!=null) c.enviarMensaje(m); }

    private void enviarEstadoJugador(UnCliente c, Jugador j) {
        c.enviarMensaje("Cartas: " + j.getManoActual() + " | Monedas: " + j.getMonedas());
    }

    private void anunciarTurno(SalaCoup s) {
        Jugador act = s.getJugadorActivo();
        if (act != null) for (Jugador j : s.getJugadores()) notificarTurnoIndividual(j, act, s.getNombreSala());

        mensajeGlobalEnSala(s.getIdSala(), "Turno de " + act.getNombreUsuario() + " (60s)");
        iniciarTemporizador(s.getIdSala(), 60, () -> forzarFinTurno(s));
    }

    private void forzarFinTurno(SalaCoup s) {
        mensajeGlobalEnSala(s.getIdSala(), "TIEMPO DE TURNO AGOTADO. Se pasa el turno.");
        s.siguienteTurno();
        anunciarTurno(s);}

    private void notificarTurnoIndividual(Jugador j, Jugador act, String sala) {
        UnCliente c = puentesDeConexion.get(j.getNombreUsuario());
        if (c == null) return;
        if (j.equals(act)) c.enviarMensaje(">>> TU TURNO en " + sala + " <<<");
        else c.enviarMensaje("Turno de " + act.getNombreUsuario());}

    public void mostrarSalasDisponibles(UnCliente c) {
        if (salasActivas.isEmpty()) { c.enviarMensaje("No hay salas activas."); return; }
        c.enviarMensaje("--- Salas ---");
        salasActivas.values().forEach(s -> c.enviarMensaje("- " + s.getNombreSala()));}

    public String abandonarSala(String u) {
        return eliminarJugador(u);}

    public void eliminarJugadorDeSala(UnCliente c, String v) {
        SalaCoup s = obtenerSala(c);
        if (s == null) { c.enviarMensaje("No estas en sala."); return; }
        if (!s.getJugadores().get(0).getNombreUsuario().equals(c.getNombreUsuario())) { c.enviarMensaje("Solo administrador"); return; }
        eliminarJugador(v);
        mensajeGlobalEnSala(s.getIdSala(), "Admin eliminó a " + v);}

    public void invitarUsuarios(UnCliente c, String[] invs) {
        SalaCoup s = obtenerSala(c);
        if (s == null) { c.enviarMensaje("Sin sala."); return; }
        for (String i : invs) enviarInvitacion(s, c, i);
    }
    private void enviarInvitacion(SalaCoup s, UnCliente remitente, String invitado) {
        if (remitente.getNombreUsuario().equalsIgnoreCase(invitado)) {
            remitente.enviarMensaje("ERROR: No puedes invitarte a ti mismo.");
            return;}

        UnCliente c = puentesDeConexion.get(invitado);

        if (c != null) {
            if (jugadorEnSala.containsKey(invitado)) {
                remitente.enviarMensaje("ERROR: " + invitado + " ya está en una sala.");
                return;}

            invitacionesPendientes.put(invitado, s.getIdSala());
            c.enviarMensaje("INVITACION de " + remitente.getNombreUsuario() + " para unirte a la sala " + s.getNombreSala() + ". Usa /si o /no.");
            remitente.enviarMensaje("INVITACIÓN ENVIADA a " + invitado + ".");
        } else {
            remitente.enviarMensaje("ERROR: El usuario " + invitado + " no está conectado o no existe.");
        }
    }
    public void responderInvitacion(UnCliente c, String r) {
        String id = invitacionesPendientes.remove(c.getNombreUsuario());
        if (id == null) { c.enviarMensaje("Sin invitaciones pendientes."); return; }

        SalaCoup sala = salasActivas.get(id);
        if (sala == null) {
            c.enviarMensaje("ERROR: La sala de la invitación ya no existe.");
            return;}

        if (r.equals("/si")) {
            boolean unido = unirseASala(id, c);

            if (unido) {
                c.enviarMensaje("¡Unión exitosa! ID de la sala para compartir: " + id);
                mensajeGlobalEnSala(id, c.getNombreUsuario() + " aceptó la invitación y se unió a la sala.");
            } else {
                mensajeGlobalEnSala(id, c.getNombreUsuario() + " intentó unirse, pero el juego ya había comenzado o la sala estaba llena.");
            }
        } else {
            c.enviarMensaje("Rechazada.");
            mensajeGlobalEnSala(id,  c.getNombreUsuario() + " rechazó la invitación.");}
    }
    public void iniciarJuego(UnCliente c) {
        SalaCoup s = obtenerSala(c);
        if(s!=null && s.getJugadores().size()>=2) arrancarJuego(s);
        else c.enviarMensaje("Faltan jugadores.");}

    private void arrancarJuego(SalaCoup s) {
        try {
            s.iniciarPartida();
            mensajeGlobalEnSala(s.getIdSala(), "Iniciando...");
            for (Jugador j : s.getJugadores()) {
                UnCliente c = puentesDeConexion.get(j.getNombreUsuario());
                if (c != null) enviarEstadoJugador(c, j);}
            anunciarTurno(s);
        } catch(Exception e) {
            mensajeGlobalEnSala(s.getIdSala(), "Error: "+e.getMessage());}
    }
    public String eliminarJugador(String u) {
        String id = jugadorEnSala.get(u);
        if (id != null && timersPorSala.containsKey(id)) {}
        espectadorEnSala.remove(u);

        String idRemovido = jugadorEnSala.remove(u);
        if (idRemovido != null) procesarSalida(idRemovido, u);
        return "Saliste.";
    }

    private void procesarSalida(String id, String u) {
        SalaCoup s = salasActivas.get(id);
        if (s != null) {
            s.removerJugador(u);
            mensajeGlobalEnSala(id, u + " salió.");
            String resultadoFin = s.verificarGanador();
            if (resultadoFin != null) {
                finalizarPartida(s, resultadoFin);
                return;}

            if (s.getJugadores().isEmpty()) {
                salasActivas.remove(id);
                estadosPorSala.remove(id);
                cancelarTemporizador(id);}
        }
    }
    private void finalizarPartida(SalaCoup s, String mensajeGanador) {
        String idSala = s.getIdSala();

        mensajeGlobalEnSala(idSala, "----------------------------------");
        mensajeGlobalEnSala(idSala, "¡FIN DEL JUEGO! EL GANADOR ES: " + mensajeGanador.split(": ")[1]);
        mensajeGlobalEnSala(idSala, "----------------------------------");

        cancelarTemporizador(idSala);

        for (Jugador j : s.getJugadores()) {
            jugadorEnSala.remove(j.getNombreUsuario());
            UnCliente c = puentesDeConexion.get(j.getNombreUsuario());
            if (c != null) {
                c.enviarMensaje("El juego ha terminado. Opciones: /crear, /unirse, /salas, /espectador");}
        }

        Set<String> espectadoresARemover = new HashSet<>();
        for (Map.Entry<String, String> entry : espectadorEnSala.entrySet()) {
            if (entry.getValue().equals(idSala)) {
                espectadoresARemover.add(entry.getKey());
                UnCliente c = puentesDeConexion.get(entry.getKey());
                if (c != null) {
                    c.enviarMensaje("La partida que estabas viendo ha terminado.");
                    c.enviarMensaje("Opciones: /crear, /unirse, /salas, /espectador");}
            }
        }
        espectadoresARemover.forEach(espectadorEnSala::remove);
        salasActivas.remove(idSala);
        estadosPorSala.remove(idSala);
    }

    private void forzarAbandonoOEspectador(SalaCoup s, String usuario) {
        UnCliente c = puentesDeConexion.get(usuario);
        if (c != null) {
            if (espectadorEnSala.containsKey(usuario) && espectadorEnSala.get(usuario).equals(s.getIdSala())) {
                c.enviarMensaje("Tiempo de elección agotado. Permaneces como espectador.");}
        }
    }
    public void procesarEleccionEliminado(UnCliente c, String cmd) {
        String usuario = c.getNombreUsuario();
        String idSala = espectadorEnSala.get(usuario);

        if (idSala == null) {
            c.enviarMensaje("No tienes una elección pendiente. Opciones: /crear, /unirse, /salas, /espectador");
            return;}

        if (cmd.equalsIgnoreCase("/abandonar")) {
            espectadorEnSala.remove(usuario);
            c.enviarMensaje("Has abandonado la sala.");
            c.enviarMensaje("Opciones: /crear, /unirse, /salas, /espectador");

            SalaCoup s = salasActivas.get(idSala);
            if (s != null && s.isJuegoIniciado()) {mensajeGlobalEnSala(idSala, usuario + " dejó de espectar y abandonó la sala.");}

            cancelarTemporizador(idSala);

        } else if (cmd.equalsIgnoreCase("/espectador_quedarme")) {
            c.enviarMensaje("Permaneces como espectador en la partida.");
            cancelarTemporizador(idSala);

        } else {c.enviarMensaje("Comando no válido. Opciones: /abandonar, /espectador_quedarme");}
    }
    public void iniciarEspectadorDesdeLobby(UnCliente c, String idSala) {
        if (jugadorEnSala.containsKey(c.getNombreUsuario())) {
            c.enviarMensaje("Ya eres jugador activo. Usa /abandonar para salir antes.");
            return;}

        SalaCoup sala = salasActivas.get(idSala);
        if (sala == null) {
            c.enviarMensaje("ERROR: La sala " + idSala + " no existe.");
            return;}

        if (!sala.isJuegoIniciado()) {
            c.enviarMensaje("No puedes espectar esta sala porque aún no ha iniciado.");
            return;}

        if (espectadorEnSala.containsKey(c.getNombreUsuario())) {
            espectadorEnSala.remove(c.getNombreUsuario());}

        espectadorEnSala.put(c.getNombreUsuario(), idSala);

        c.enviarMensaje("Has entrado a espectar la sala: " + sala.getNombreSala());
        c.enviarMensaje("Estado actual de la partida:");
        mensajeGlobalEnSala(idSala, c.getNombreUsuario() + " ha entrado como espectador.");
    }
    public void mostrarSalasParaEspectar(UnCliente c) {
        c.enviarMensaje("--- Salas Activas ---");
        boolean algunaActiva = false;
        for (SalaCoup s : salasActivas.values()) {
            if (s.isJuegoIniciado()) {
                c.enviarMensaje("- " + s.getNombreSala() + " (ID: " + s.getIdSala() + ") - Jugadores: " + s.getJugadores().size());
                algunaActiva = true;}
        }
        if (!algunaActiva) {c.enviarMensaje("No hay salas de juego activas para espectar.");}
    }

    private void iniciarFaseSeleccionEmbajador(SalaCoup s, EstadoPartida e, String nombreJugador) {
        Jugador j = obtenerJugador(s, nombreJugador);
        if (j == null) return;
        List<TipoCarta> opciones = s.obtenerOpcionesEmbajador(j);
        e.cartasEmbajadorPendientes = opciones;
        e.accionPendiente = "ESPERANDO_SELECCION_EMBAJADOR";
        UnCliente c = puentesDeConexion.get(nombreJugador);
        if (c != null) {
            c.enviarMensaje("--- FASE EMBAJADOR ---");
            c.enviarMensaje("Tus opciones son: " + opciones.toString());
            c.enviarMensaje("Escribe: /seleccionar CARTA1 [CARTA2]");
            c.enviarMensaje("(Debes quedarte con " + j.getManoActual().size() + " cartas)");
        }
        mensajeGlobalEnSala(s.getIdSala(), nombreJugador + " está seleccionando cartas...");
        iniciarTemporizador(s.getIdSala(), 60, () -> {
            if ("ESPERANDO_SELECCION_EMBAJADOR".equals(e.accionPendiente)) {
                List<TipoCarta> auto = new ArrayList<>();
                for(int i=0; i < j.getManoActual().size(); i++) auto.add(opciones.get(i));
                s.concretarSeleccionEmbajador(j, auto, opciones);
                mensajeGlobalEnSala(s.getIdSala(), "Tiempo agotado. Selección automática.");
                limpiarEstado(e);
                anunciarTurno(s);
            }
        });
    }

    private void procesarSeleccionEmbajador(UnCliente c, String cmd, SalaCoup s, EstadoPartida e) {
        if (!c.getNombreUsuario().equals(e.atacantePendiente)) return;

        if (!cmd.toLowerCase().startsWith("/seleccionar ")) {
            c.enviarMensaje("Formato incorrecto. Usa: /seleccionar <Carta1> [Carta2]");
            return;
        }

        String[] partes = cmd.toUpperCase().split(" ");
        List<TipoCarta> seleccionadas = new ArrayList<>();
        List<TipoCarta> copiaVerificacion = new ArrayList<>(e.cartasEmbajadorPendientes);

        try {
            for (int i = 1; i < partes.length; i++) {
                TipoCarta carta = TipoCarta.valueOf(partes[i]);
                if (copiaVerificacion.contains(carta)) {
                    seleccionadas.add(carta);
                    copiaVerificacion.remove(carta);
                } else {
                    c.enviarMensaje("ERROR: No tienes la carta " + partes[i] + " en tus opciones.");
                    return;
                }
            }
        } catch (IllegalArgumentException ex) {
            c.enviarMensaje("ERROR: Nombre de carta no válido.");
            return;
        }
        Jugador j = obtenerJugador(s, c.getNombreUsuario());
        String res = s.concretarSeleccionEmbajador(j, seleccionadas, e.cartasEmbajadorPendientes);
        if (res.startsWith(Constantes.PREFIJO_ERROR)) {
            c.enviarMensaje(res);
        } else {
            mensajeGlobalEnSala(s.getIdSala(), res);
            e.cartasEmbajadorPendientes = null;
            limpiarEstado(e);
            anunciarTurno(s);
        }
    }
}