package ServidorMulti;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.sql.SQLException;

public class UnCliente implements Runnable {
    private final Socket socket;
    private final DataOutputStream salida;
    private final DataInputStream entrada;
    private final String claveCliente;
    private final IServidor servidor;
    private boolean autenticado = false;
    private String nombreUsuario = null;

    public UnCliente(Socket s, String clave, IServidor serv) throws IOException {
        this.socket = s;
        this.claveCliente = clave;
        this.servidor = serv;
        this.salida = new DataOutputStream(s.getOutputStream());
        this.entrada = new DataInputStream(s.getInputStream());
    }

    public boolean isAutenticado() { return autenticado; }
    public String getNombreUsuario() { return nombreUsuario; }

    @Override
    public void run() {
        while (!socket.isClosed()) {
            try {
                cicloLectura();
            } catch (Exception ex) {
                cerrarConexion();
                break;
            }
        }
    }

    private void cicloLectura() throws IOException, SQLException {
        String mensaje = entrada.readUTF();
        procesarEntrada(mensaje);
    }

    private void procesarEntrada(String mensaje) throws IOException, SQLException {
        if (mensaje.startsWith("/")) {
            manejarComando(mensaje);
        } else {
            manejarMensajeChat(mensaje);
        }
    }

    private void manejarMensajeChat(String mensaje) {
        if (!autenticado) {
            enviarMensaje(Constantes.ERR_AUTH_REQUERIDA);
            return;
        }
        distribuirMensaje(mensaje);
    }

    private void distribuirMensaje(String mensaje) {
        if (mensaje.startsWith("@")) {
            enviarPrivado(mensaje);
        } else {
            servidor.difundirMensaje(mensaje, this);
        }
    }

    private void enviarPrivado(String mensaje) {
        String[] partes = mensaje.split(" ", 2);
        if (partes.length > 1) {
            servidor.enviarMensajePrivado(partes[0].substring(1), partes[1], this);
        }
    }

    private void manejarComando(String cmd) throws IOException, SQLException {
        String[] partes = cmd.split(" ", 4);
        String comando = partes[0].toLowerCase();

        if (esComandoAuth(comando)) procesarAuth(comando, partes);
        else if (esComandoSala(comando)) procesarSala(comando, partes, cmd);
        else if (esComandoJuego(comando)) procesarJuego(comando, partes, cmd);
        else if (esComandoEspectador(comando)) procesarEspectador(comando, partes); // [NUEVO]
        else if (Constantes.CMD_EXIT.equals(comando)) cerrarConexion();
        else enviarMensaje("Comando desconocido.");
    }

    private boolean esComandoAuth(String cmd) {
        return cmd.equals(Constantes.CMD_LOGIN) || cmd.equals(Constantes.CMD_REGISTER);
    }

    private void procesarAuth(String cmd, String[] partes) throws SQLException {
        if (cmd.equals(Constantes.CMD_LOGIN)) procesarLogin(partes);
        else procesarRegistro(partes);
    }

    private boolean esComandoSala(String cmd) {
        return cmd.equals("/crear") || cmd.equals("/unirse") || cmd.equals("/salas") ||
                cmd.equals("/abandonar") || cmd.equals("/eliminar") || cmd.equals("/invitar") ||
                cmd.equals("/si") || cmd.equals("/no") || cmd.equals("/iniciar");
    }

    private void procesarSala(String cmd, String[] partes, String fullCmd) {
        if (!verificarAuth()) return;
        if (cmd.equals("/crear")) servidor.getGestorPartida().crearSala(this, (partes.length > 1) ? partes[1] : null);
        else if (cmd.equals("/unirse")) procesarUnirse(partes);
        else if (cmd.equals("/salas")) servidor.getGestorPartida().mostrarSalasDisponibles(this);
        else if (cmd.equals("/abandonar")) enviarMensaje(servidor.getGestorPartida().abandonarSala(this.nombreUsuario));
        else if (cmd.equals("/eliminar")) procesarEliminar(partes);
        else procesarSalaExtras(cmd, partes, fullCmd);
    }

    private void procesarSalaExtras(String cmd, String[] partes, String fullCmd) {
        if (cmd.equals("/invitar")) procesarInvitar(partes, fullCmd);
        else if (cmd.equals("/si") || cmd.equals("/no")) servidor.getGestorPartida().responderInvitacion(this, cmd);
        else if (cmd.equals("/iniciar")) servidor.getGestorPartida().iniciarJuego(this);
    }

    private boolean esComandoJuego(String cmd) {

        return cmd.equals("/jugar") || cmd.equals("/dudar") || cmd.equals("/permitir");
    }

    private void procesarJuego(String cmd, String[] partes, String fullCmd) {
        if (!verificarAuth()) return;

        if (cmd.equals("/jugar")) {
            if (partes.length < 2) enviarMensaje("Uso: /jugar <accion>");
            else servidor.getGestorPartida().procesarJugada(this, fullCmd.substring(fullCmd.indexOf(' ') + 1));
        }
        else {
            servidor.getGestorPartida().procesarJugada(this, fullCmd);
        }
    }

    private void procesarUnirse(String[] partes) {
        if (partes.length < 2) enviarMensaje("Uso: /unirse <ID>");
        else servidor.getGestorPartida().unirseASala(partes[1], this);
    }

    private void procesarEliminar(String[] partes) {
        if (partes.length < 2) enviarMensaje("Uso: /eliminar <usuario>");
        else servidor.getGestorPartida().eliminarJugadorDeSala(this, partes[1]);
    }
    private void procesarInvitar(String[] partes, String fullCmd) {
        if (partes.length < 2) {
            String usuariosConectados = servidor.getUsuariosConectados();
            if (usuariosConectados.isEmpty()) {
                enviarMensaje("No hay otros usuarios conectados para invitar.");
            } else {
                enviarMensaje("Uso: /invitar <usuario1> [usuario2...]");
                enviarMensaje("Usuarios conectados (puedes invitar): " + usuariosConectados);
            }
        } else {
            servidor.getGestorPartida().invitarUsuarios(this, fullCmd.substring(fullCmd.indexOf(' ') + 1).split(" "));
        }
    }
    private boolean verificarAuth() {
        if (!autenticado) enviarMensaje("Debes iniciar sesion primero.");
        return autenticado;
    }

    private void procesarLogin(String[] partes) throws SQLException {
        if (autenticado) {
            enviarMensaje("ERROR: Ya tienes una sesión activa como '" + nombreUsuario + "'.");
            enviarMensaje("Debes cerrar la sesión actual con /exit antes de intentar un nuevo /login.");
            return;
        }

        if (partes.length < 3) { enviarMensaje("Uso: /login <user> <pass>"); return; }
        ejecutarLogin(partes[1], partes[2]);
    }

    private void ejecutarLogin(String user, String pass) throws SQLException {
        if (servidor.getGestorUsuarios().autenticar(user, pass)) completarLogin(user);
        else enviarMensaje("Credenciales incorrectas.");
    }

    private void completarLogin(String user) {
        if (servidor.registrarSesionActiva(user, claveCliente)) {
            autenticado = true;
            nombreUsuario = user;
            enviarMensaje("Login exitoso.");
            servidor.getGestorPartida().registrarCliente(this);
        } else enviarMensaje(Constantes.ERR_LOGIN_DUPLICADO);
    }

    private void procesarRegistro(String[] partes) throws SQLException {
        if (autenticado) {
            enviarMensaje("ERROR: Ya tienes una sesión activa (" + nombreUsuario + ").");
            enviarMensaje("Debes salir (/exit) antes de registrar una cuenta nueva.");
            return;
        }
        if (partes.length < 3) { enviarMensaje("Uso: /register <user> <pass>"); return; }

        String res = servidor.getGestorUsuarios().registrarUsuario(partes[1], partes[2]);
        if (res.equals("REGISTRO_OK")) {
            enviarMensaje("Registro exitoso.");
            enviarMensaje("Por favor, usa /login " + partes[1] + " <contrasena> para iniciar sesión.");
        } else {
            enviarMensaje(res);
        }
    }

    public void enviarMensaje(String msg) {
        try { salida.writeUTF(msg); } catch (IOException e) { }
    }

    private void cerrarConexion() {
        try { socket.close(); } catch (IOException e) { }
        finally { limpiarSesion(); }
    }

    private void limpiarSesion() {
        servidor.eliminarCliente(claveCliente);
        if (nombreUsuario != null) servidor.getGestorPartida().eliminarJugador(nombreUsuario);
    }

    private boolean esComandoEspectador(String cmd) {
        return cmd.equals("/espectador") || cmd.equals("/espectador_quedarme");
    }

    private void procesarEspectador(String cmd, String[] partes) {
        if (!verificarAuth()) return;

        if (cmd.equals("/espectador")) {
            procesarEspectadorLobby(partes);
        } else if (cmd.equals("/espectador_quedarme")) {
            servidor.getGestorPartida().procesarEleccionEliminado(this, cmd);
        }
    }

    private void procesarEspectadorLobby(String[] partes) {
        if (partes.length < 2) {
            servidor.getGestorPartida().mostrarSalasParaEspectar(this);
            enviarMensaje("Uso: /espectador <ID_Sala> para unirte.");
        } else {
            servidor.getGestorPartida().iniciarEspectadorDesdeLobby(this, partes[1]);
        }
    }
}