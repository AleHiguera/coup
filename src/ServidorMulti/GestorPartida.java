package ServidorMulti;

import JuegoCoup.Jugador;
import JuegoCoup.SalaCoup;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class GestorPartida {
    private static class EstadoPartida {
        String jugadorPendienteDeDescarte, atacantePendiente, victimaPendiente, accionPendiente;
    }
    private final Map<String, SalaCoup> salasActivas = new ConcurrentHashMap<>();
    private final Map<String, String> jugadorEnSala = new ConcurrentHashMap<>();
    private final Map<String, UnCliente> puentesDeConexion = new ConcurrentHashMap<>();
    private final Map<String, String> invitacionesPendientes = new ConcurrentHashMap<>();
    private final Map<String, EstadoPartida> estadosPorSala = new ConcurrentHashMap<>();
    private static final int MIN_JUGADORES = 3, MAX_JUGADORES = 6;

    public synchronized void registrarCliente(UnCliente c) {
        if (c.getNombreUsuario() != null && !puentesDeConexion.containsKey(c.getNombreUsuario())) {
            puentesDeConexion.put(c.getNombreUsuario(), c);
            c.enviarMensaje("Opciones: /crear, /unirse, /salas");
        }
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
        inicializarSala(idSala, nombre, creador);
    }

    private void inicializarSala(String id, String nombre, UnCliente creador) {
        SalaCoup sala = new SalaCoup(id, nombre);
        sala.agregarJugador(creador.getNombreUsuario());
        salasActivas.put(id, sala);
        jugadorEnSala.put(creador.getNombreUsuario(), id);
        estadosPorSala.put(id, new EstadoPartida());
        notificarCreacion(creador, nombre, id);
    }

    private void notificarCreacion(UnCliente c, String nom, String id) {
        c.enviarMensaje("SALA CREADA: " + nom + " (ID: " + id + ")");
        c.enviarMensaje("Comandos: /iniciar, /invitar, /abandonar");
    }

    public void unirseASala(String idSala, UnCliente cliente) {
        SalaCoup sala = salasActivas.get(idSala);
        if (validarUnion(sala, cliente)) ejecutarUnion(sala, cliente, idSala);
    }

    private boolean validarUnion(SalaCoup sala, UnCliente c) {
        if (sala == null || sala.isJuegoIniciado()) {
            c.enviarMensaje("Sala no existe o juego iniciado."); return false;
        }
        if (sala.getJugadores().size() >= MAX_JUGADORES) {
            c.enviarMensaje("Sala llena."); return false;
        }
        return true;
    }

    private void ejecutarUnion(SalaCoup sala, UnCliente c, String id) {
        sala.agregarJugador(c.getNombreUsuario());
        jugadorEnSala.put(c.getNombreUsuario(), id);
        mensajeGlobalEnSala(id, c.getNombreUsuario() + " se unio.");
        c.enviarMensaje("Usa /abandonar para salir.");
    }

    public synchronized void procesarJugada(UnCliente cliente, String cmd) {
        SalaCoup sala = obtenerSala(cliente);
        if (!validarJugadaActiva(sala, cliente)) return;
        EstadoPartida estado = estadosPorSala.get(sala.getIdSala());
        delegarFaseJuego(cliente, cmd, sala, estado);
    }

    private boolean validarJugadaActiva(SalaCoup sala, UnCliente c) {
        if (sala == null || !sala.isJuegoIniciado()) {
            c.enviarMensaje("No estas en partida activa."); return false;
        }
        return true;
    }

    private void delegarFaseJuego(UnCliente c, String cmd, SalaCoup s, EstadoPartida e) {
        if (e.jugadorPendienteDeDescarte != null) procesarFaseDescarte(c, cmd, s, e);
        else if (e.accionPendiente != null) procesarFaseBloqueo(c, cmd, s, e);
        else procesarFaseNormal(c, cmd, s, e);
    }

    private void procesarFaseNormal(UnCliente c, String cmd, SalaCoup s, EstadoPartida e) {
        Jugador j = obtenerJugador(s, c.getNombreUsuario());
        if (j == null) return;
        String res = ejecutarAccionEnSala(s, j, cmd);
        procesarResultadoAccion(c, res, s, e, j);
    }

    private String ejecutarAccionEnSala(SalaCoup s, Jugador j, String cmd) {
        String[] partes = cmd.split(" ");
        String accion = partes[0].toLowerCase();
        String obj = (partes.length > 1) ? partes[1] : null;
        return despacharAccion(s, j, accion, obj);
    }

    private String despacharAccion(SalaCoup s, Jugador j, String act, String obj) {
        if (act.equals("ingreso")) return s.realizarAccionIngreso(j);
        if (act.equals("ayuda")) return s.iniciarAccionAyudaExterior(j);
        if (act.equals("golpe")) return (obj != null) ? s.realizarGolpeDeEstado(j, obj) : "ERROR: Falta objetivo";
        return despacharAccionCompleja(s, j, act, obj);
    }

    private String despacharAccionCompleja(SalaCoup s, Jugador j, String act, String obj) {
        if (act.equals("impuestos")) return s.realizarAccionImpuestos(j);
        if (act.equals("embajador")) return s.realizarAccionEmbajador(j);
        if (act.equals("robar")) return (obj!=null) ? s.iniciarAccionRobar(j, obj) : "ERROR: Falta objetivo";
        if (act.equals("asesinar")) return (obj!=null) ? s.iniciarAccionAsesinato(j, obj) : "ERROR: Falta objetivo";
        return "Accion desconocida.";
    }

    private void procesarResultadoAccion(UnCliente c, String res, SalaCoup s, EstadoPartida e, Jugador j) {
        if (res.startsWith("INTENTO:")) iniciarDesafio(res, s, e, c.getNombreUsuario());
        else if (res.startsWith("ESPERA_CARTA:")) iniciarDescarte(res, s, e);
        else if (res.startsWith("ERROR")) c.enviarMensaje(res);
        else finalizarTurnoNormal(s, res, c, j);
    }

    private void iniciarDesafio(String res, SalaCoup s, EstadoPartida e, String atacante) {
        String[] datos = res.split(":");
        configurarEstadoDesafio(e, datos[1], datos[2], atacante);
        notificarDesafio(s.getIdSala(), atacante, e);
    }

    private void configurarEstadoDesafio(EstadoPartida e, String acc, String vic, String atq) {
        e.accionPendiente = acc; e.victimaPendiente = vic; e.atacantePendiente = atq;
    }

    private void notificarDesafio(String idSala, String atq, EstadoPartida e) {
        mensajeGlobalEnSala(idSala, ">>> " + atq + " quiere " + e.accionPendiente + " a " + e.victimaPendiente);
        UnCliente v = puentesDeConexion.get(e.victimaPendiente);
        if (v != null) v.enviarMensaje("Responde: /permitir o /bloquear <carta>");
    }

    private void finalizarTurnoNormal(SalaCoup s, String res, UnCliente c, Jugador j) {
        mensajeGlobalEnSala(s.getIdSala(), res);
        enviarEstadoJugador(c, j);
        anunciarTurno(s);
    }

    private void procesarFaseBloqueo(UnCliente c, String cmd, SalaCoup s, EstadoPartida e) {
        if (!validarInteraccionBloqueo(c, e)) return;
        if (cmd.startsWith("/bloquear")) ejecutarBloqueo(s, e, c.getNombreUsuario());
        else if (cmd.startsWith("/permitir")) ejecutarPermitir(s, e);
        else c.enviarMensaje("Opciones: /permitir o /bloquear <carta>");
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
        if (!cmd.startsWith("descartar ")) { c.enviarMensaje("Usa: /jugar descartar <carta>"); return; }
        ejecutarDescarte(c, cmd.split(" ")[1], s, e);
    }

    private void ejecutarDescarte(UnCliente c, String carta, SalaCoup s, EstadoPartida e) {
        String res = s.concretarDescarte(c.getNombreUsuario(), carta);
        if (res.startsWith("ERROR")) c.enviarMensaje(res);
        else finalizarDescarte(s, e, c, res);
    }

    private void finalizarDescarte(SalaCoup s, EstadoPartida e, UnCliente c, String res) {
        mensajeGlobalEnSala(s.getIdSala(), res);
        e.jugadorPendienteDeDescarte = null;
        enviarEstadoJugador(c, obtenerJugador(s, c.getNombreUsuario()));
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
    }

    private void limpiarEstado(EstadoPartida e) {
        e.accionPendiente = null; e.atacantePendiente = null; e.victimaPendiente = null;
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
        if (s != null) for (Jugador j : s.getJugadores()) enviarAC(j.getNombreUsuario(), m);
    }
    private void enviarAC(String u, String m) { UnCliente c = puentesDeConexion.get(u); if(c!=null) c.enviarMensaje(m); }
    private void enviarEstadoJugador(UnCliente c, Jugador j) {
        c.enviarMensaje("Cartas: " + j.getManoActual() + " | Monedas: " + j.getMonedas());
    }
    private void anunciarTurno(SalaCoup s) {
        Jugador act = s.getJugadorActivo();
        if (act != null) for (Jugador j : s.getJugadores()) notificarTurnoIndividual(j, act, s.getNombreSala());
    }
    private void notificarTurnoIndividual(Jugador j, Jugador act, String sala) {
        UnCliente c = puentesDeConexion.get(j.getNombreUsuario());
        if (c == null) return;
        if (j.equals(act)) c.enviarMensaje(">>> TU TURNO en " + sala + " <<<");
        else c.enviarMensaje("Turno de " + act.getNombreUsuario());
    }

    public void mostrarSalasDisponibles(UnCliente c) {
        if (salasActivas.isEmpty()) { c.enviarMensaje("No hay salas activas."); return; }
        c.enviarMensaje("--- Salas ---");
        salasActivas.values().forEach(s -> c.enviarMensaje("- " + s.getNombreSala()));
    }

    public String abandonarSala(String u) {
        return eliminarJugador(u);
    }

    public void eliminarJugadorDeSala(UnCliente c, String v) {
        SalaCoup s = obtenerSala(c);
        if (s == null) { c.enviarMensaje("No estas en sala."); return; }
        if (!s.getJugadores().get(0).getNombreUsuario().equals(c.getNombreUsuario())) { c.enviarMensaje("Solo administrador"); return; }
        eliminarJugador(v);
        mensajeGlobalEnSala(s.getIdSala(), "Admin eliminó a " + v);
    }

    public void invitarUsuarios(UnCliente c, String[] invs) {
        SalaCoup s = obtenerSala(c);
        if (s == null) { c.enviarMensaje("Sin sala."); return; }
        for (String i : invs) enviarInvitacion(s, c, i);
    }

    private void enviarInvitacion(SalaCoup s, UnCliente remitente, String invitado) {
        UnCliente c = puentesDeConexion.get(invitado);
        if (c != null && !jugadorEnSala.containsKey(invitado)) {
            invitacionesPendientes.put(invitado, s.getIdSala());
            c.enviarMensaje("INVITACION de " + remitente.getNombreUsuario() + ". /si o /no");
        }
    }

    public void responderInvitacion(UnCliente c, String r) {
        String id = invitacionesPendientes.remove(c.getNombreUsuario());
        if (id == null) { c.enviarMensaje("Sin invitaciones."); return; }
        if (r.equals("/si")) unirseASala(id, c);
        else c.enviarMensaje("Rechazada.");
    }

    public void iniciarJuego(UnCliente c) {
        SalaCoup s = obtenerSala(c);
        if(s!=null && s.getJugadores().size()>=2) arrancarJuego(s);
        else c.enviarMensaje("Faltan jugadores.");
    }

    private void arrancarJuego(SalaCoup s) {
        try { s.iniciarPartida(); mensajeGlobalEnSala(s.getIdSala(), "Iniciando..."); anunciarTurno(s); }
        catch(Exception e) { mensajeGlobalEnSala(s.getIdSala(), "Error: "+e.getMessage()); }
    }

    public String eliminarJugador(String u) {
        String id = jugadorEnSala.remove(u);
        if (id != null) procesarSalida(id, u);
        return "Saliste.";
    }

    private void procesarSalida(String id, String u) {
        SalaCoup s = salasActivas.get(id);
        if (s != null) {
            s.removerJugador(u);
            mensajeGlobalEnSala(id, u + " salió.");
            if (s.getJugadores().isEmpty()) { salasActivas.remove(id); estadosPorSala.remove(id); }
        }
    }
}