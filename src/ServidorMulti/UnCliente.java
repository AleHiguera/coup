package ServidorMulti;
import java.io.*;
import java.net.Socket;
import java.sql.SQLException;

public class UnCliente implements Runnable {
    private final Socket socket;
    private final DataOutputStream salida;
    private final DataInputStream entrada;
    private final String claveCliente;

    boolean estaAutenticado = false;
    String nombreUsuario = null;

    UnCliente(Socket s, String claveCliente) throws IOException {
        this.socket = s;
        this.claveCliente = claveCliente;
        this.salida = new DataOutputStream(s.getOutputStream());
        this.entrada = new DataInputStream(s.getInputStream());
    }

    @Override
    public void run() {
        while (!socket.isClosed()) {
            try {
                String mensaje = entrada.readUTF();
                procesarMensaje(mensaje);
            } catch (IOException ex) {
                cerrarConexion();
                break;
            } catch (SQLException e) {
                enviarMensaje("ERROR en base de datos: " + e.getMessage());
            }
        }
    }

    private void procesarMensaje(String mensaje) throws IOException, SQLException {
        if (mensaje.startsWith("/")) {
            manejarComando(mensaje);
        } else if (estaAutenticado) {
            manejarChat(mensaje);
        } else {
            enviarMensaje("Debes autenticarte primero. Usa /login o /register.");
        }
    }

    private void manejarChat(String mensaje) {
        if (mensaje.startsWith("@")) {
            ServidorMulti.enviarMensajePrivado(
                    "algunaClave",
                    "MENSAJE PRIVADO DE " + nombreUsuario + ": " + mensaje
            );
        } else {
            String mensajeADifundir = String.format("%s: %s", nombreUsuario, mensaje);
            ServidorMulti.difundirMensaje(mensajeADifundir);
        }
    }

    private void manejarComando(String comandoCompleto) throws IOException, SQLException {
        String[] partes = comandoCompleto.split(" ", 2);
        String comando = partes[0].toLowerCase();

        switch (comando) {
            case "/login":
                manejarLogin(partes);
                break;
            case "/register":
                manejarRegistro(partes);
                break;
            case "/exit":
                cerrarSesionYConexion();
                break;
            default:
                enviarMensaje("Comando desconocido: " + comando);
        }
    }

    private void manejarLogin(String[] partes) throws SQLException {
        if (estaAutenticado) {
            enviarMensaje("Ya has iniciado sesión como " + nombreUsuario + ". Usa /exit para cerrar sesión primero.");
            return;
        }
        if (partes.length < 2) {
            enviarMensaje("Uso: /login <usuario> <contraseña>");
            return;
        }

        String[] credenciales = partes[1].split(" ", 2);
        if (credenciales.length != 2) {
            enviarMensaje("Uso: /login <usuario> <contraseña>");
            return;
        }

        String usuario = credenciales[0];
        String contrasena = credenciales[1];

        if (ServidorMulti.estaUsuarioActivo(usuario)) {
            enviarMensaje("ERROR: El usuario '" + usuario + "' ya tiene una sesión activa.");
            return;
        }

        if (ServidorMulti.getGestorUsuarios().autenticar(usuario, contrasena)) {
            establecerSesion(usuario);
        } else {
            enviarMensaje("ERROR: Usuario o contraseña incorrectos.");
        }
    }

    private void manejarRegistro(String[] partes) throws SQLException {
        if (estaAutenticado) {
            enviarMensaje("Ya has iniciado sesión como " + nombreUsuario + ". Usa /exit para cerrar sesión primero.");
            return;
        }
        if (partes.length < 2) {
            enviarMensaje("Uso: /register <usuario> <contraseña>");
            return;
        }

        String[] credenciales = partes[1].split(" ", 2);
        if (credenciales.length != 2) {
            enviarMensaje("Uso: /register <usuario> <contraseña>");
            return;

        }
        String usuario = credenciales[0];
        String contrasena = credenciales[1];

        String resultado = ServidorMulti.getGestorUsuarios().registrarUsuario(usuario, contrasena);

        if (resultado.equals("REGISTRO_OK")) {
            enviarMensaje("REGISTRO EXITOSO. Por favor, usa /login para iniciar sesión.");
        } else {
            enviarMensaje(resultado);
        }
    }

    private void establecerSesion(String usuario) {
        this.nombreUsuario = usuario;
        this.estaAutenticado = true;
        ServidorMulti.agregarUsuarioActivo(usuario, claveCliente);

        enviarMensaje("INICIO DE SESIÓN EXITOSO. Bienvenido, " + usuario + ".");
        System.out.println("Cliente " + claveCliente + " autenticado como: " + usuario);
    }

    public void enviarMensaje(String mensaje) {
        try {
            salida.writeUTF(mensaje);
        } catch (IOException e) {
        }
    }
    private void cerrarSesionYConexion() {
        if (estaAutenticado && nombreUsuario != null) {
            ServidorMulti.removerUsuarioActivo(nombreUsuario);
            System.out.println(nombreUsuario + " ha cerrado sesión.");
            this.estaAutenticado = false;
            this.nombreUsuario = null;
        }
        cerrarConexion();
    }

    private void cerrarConexion() {
        try {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (IOException e) {
        } finally {
            ServidorMulti.eliminarCliente(claveCliente);
        }
    }
}