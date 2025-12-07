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

    public UnCliente(Socket s, String claveCliente, IServidor servidor) throws IOException {
        this.socket = s;
        this.claveCliente = claveCliente;
        this.servidor = servidor;
        this.salida = new DataOutputStream(s.getOutputStream());
        this.entrada = new DataInputStream(s.getInputStream());
    }

    public boolean isAutenticado() { return autenticado; }
    public String getNombreUsuario() { return nombreUsuario; }

    @Override
    public void run() {
        while (!socket.isClosed()) {
            try {
                String mensaje = entrada.readUTF();
                procesarEntrada(mensaje);
            } catch (IOException ex) {
                cerrarConexion();
                break;
            } catch (SQLException e) {
                enviarMensaje("Error interno BD: " + e.getMessage());
            }
        }
    }

    private void procesarEntrada(String mensaje) throws IOException, SQLException {
        if (mensaje.startsWith("/")) {
            manejarComando(mensaje);
        } else if (autenticado) {
            if (mensaje.startsWith("@")) {
                String[] partes = mensaje.split(" ", 2);
                if (partes.length > 1) {
                    servidor.enviarMensajePrivado(partes[0].substring(1), partes[1], this);
                }
            } else {
                servidor.difundirMensaje(mensaje, this);
            }
        } else {
            enviarMensaje(Constantes.ERR_AUTH_REQUERIDA);
        }
    }

    private void manejarComando(String comandoCompleto) throws IOException, SQLException {
        String[] partes = comandoCompleto.split(" ", 3);
        String comando = partes[0].toLowerCase();

        switch (comando) {
            case Constantes.CMD_LOGIN:
                procesarLogin(partes);
                break;
            case Constantes.CMD_REGISTER:
                procesarRegistro(partes);
                break;
            case Constantes.CMD_EXIT:
                cerrarConexion();
                break;
            default:
                enviarMensaje("Comando desconocido.");
        }
    }

    private void procesarLogin(String[] partes) throws SQLException {
        if (autenticado) {
            enviarMensaje("Ya estás conectado como " + nombreUsuario);
            return;
        }
        if (partes.length < 3) {
            enviarMensaje("Uso: /login <usuario> <pass>");
            return;
        }
        String usuario = partes[1];
        String pass = partes[2];

        if (servidor.getGestorUsuarios().autenticar(usuario, pass)) {
            if (servidor.registrarSesionActiva(usuario, claveCliente)) {
                this.autenticado = true;
                this.nombreUsuario = usuario;
                enviarMensaje("Login exitoso. Hola " + usuario);
            } else {
                enviarMensaje(Constantes.ERR_LOGIN_DUPLICADO);
            }
        } else {
            enviarMensaje("Credenciales incorrectas.");
        }
    }

    private void procesarRegistro(String[] partes) throws SQLException {
        if (partes.length < 3) {
            enviarMensaje("Uso: /register <usuario> <pass>");
            return;
        }
        String res = servidor.getGestorUsuarios().registrarUsuario(partes[1], partes[2]);
        enviarMensaje(res.equals("REGISTRO_OK") ? "Registro exitoso. Ahora haz /login." : res);
    }

    public void enviarMensaje(String msg) {
        try {
            salida.writeUTF(msg);
        } catch (IOException e) { }
    }

    private void cerrarConexion() {
        try {
            socket.close();
        } catch (IOException e) {
            // Logging simple
        } finally {
            servidor.eliminarCliente(claveCliente);
        }
    }
}