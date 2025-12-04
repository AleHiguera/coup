package ServidorMulti;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class ServidorMulti {
    static final Map<String, UnCliente> clientes = new HashMap<>();
    static final Map<String, String> usuariosActivos = new HashMap<>();

    private static final AtomicInteger contadorClientes = new AtomicInteger(0);
    private static final GestorUsuarios gestorUsuarios = new GestorUsuarios(new ManejadorBaseDatos());

    public static GestorUsuarios getGestorUsuarios() {
        return gestorUsuarios;
    }
    public static boolean estaUsuarioActivo(String nombreUsuario) {
        return usuariosActivos.containsKey(nombreUsuario.toLowerCase());
    }
    public static void agregarUsuarioActivo(String nombreUsuario, String claveCliente) {
        usuariosActivos.put(nombreUsuario.toLowerCase(), claveCliente);
    }
    public static void removerUsuarioActivo(String nombreUsuario) {
        usuariosActivos.remove(nombreUsuario.toLowerCase());
    }

    public static void main(String[] args) throws IOException {
        System.out.println("Iniciando Servidor...");
        ServerSocket servidorSocket = new ServerSocket(8080);

        while (true) {
            aceptarConexion(servidorSocket);
        }
    }

    private static void aceptarConexion(ServerSocket servidorSocket) throws IOException {
        Socket s = servidorSocket.accept();
        int idCliente = contadorClientes.getAndIncrement();
        String claveCliente = Integer.toString(idCliente);

        UnCliente unCliente = new UnCliente(s, claveCliente);
        Thread hilo = new Thread(unCliente);

        clientes.put(claveCliente, unCliente);
        hilo.start();

        System.out.println("Se conectó el chango #" + claveCliente);
        unCliente.enviarMensaje("Bienvenido al chat. Usa /login o /register para comenzar.");
    }

    public static void eliminarCliente(String claveCliente) {
        UnCliente clienteDesconectado = clientes.get(claveCliente);
        if (clienteDesconectado != null && clienteDesconectado.estaAutenticado && clienteDesconectado.nombreUsuario != null) {
            removerUsuarioActivo(clienteDesconectado.nombreUsuario);
            System.out.println("Sesión de " + clienteDesconectado.nombreUsuario + " eliminada al desconectarse.");
        }

        clientes.remove(claveCliente);
        System.out.println("El chango #" + claveCliente + " se desconectó.");
    }

    public static void difundirMensaje(String mensaje) {
        for (UnCliente cliente : clientes.values()) {
            if (cliente.estaAutenticado) {
                cliente.enviarMensaje(mensaje);
            }
        }
    }

    public static void enviarMensajePrivado(String aQuien, String mensaje) {
        UnCliente clienteDestino = clientes.get(aQuien);
        if (clienteDestino != null) {
            clienteDestino.enviarMensaje(mensaje);
        }
    }
}