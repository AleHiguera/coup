package ServidorMulti;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class ServidorMulti implements IServidor {
    private final Map<String, UnCliente> clientes = new ConcurrentHashMap<>();
    private final Map<String, String> usuariosActivos = new ConcurrentHashMap<>();

    private final AtomicInteger contadorClientes = new AtomicInteger(0);
    private final GestorUsuarios gestorUsuarios;
    private final GestorPartida gestorPartida;

    public ServidorMulti() {
        this.gestorUsuarios = new GestorUsuarios(new ManejadorBaseDatos());
        this.gestorPartida = new GestorPartida();
    }

    public static void main(String[] args) throws IOException {
        System.out.println("Iniciando Servidor...");
        ServidorMulti servidor = new ServidorMulti();
        servidor.iniciar(8080);
    }

    public void iniciar(int puerto) throws IOException {
        try (ServerSocket servidorSocket = new ServerSocket(puerto)) {
            System.out.println("Servidor escuchando en el puerto " + puerto);
            while (true) {
                Socket s = servidorSocket.accept();
                String claveCliente = String.valueOf(contadorClientes.getAndIncrement());

                UnCliente nuevoCliente = new UnCliente(s, claveCliente, this);
                clientes.put(claveCliente, nuevoCliente);

                new Thread(nuevoCliente).start();
                System.out.println("Nuevo cliente conectado ID: " + claveCliente);
                nuevoCliente.enviarMensaje(Constantes.MSG_BIENVENIDA);
            }
        }
    }

    @Override
    public GestorUsuarios getGestorUsuarios() {
        return this.gestorUsuarios;
    }

    @Override
    public GestorPartida getGestorPartida() {
        return this.gestorPartida;
    }

    @Override
    public boolean registrarSesionActiva(String usuario, String claveCliente) {
        return usuariosActivos.putIfAbsent(usuario.toLowerCase(), claveCliente) == null;
    }

    @Override
    public void removerSesionActiva(String usuario) {
        if (usuario != null) {
            usuariosActivos.remove(usuario.toLowerCase());
        }
    }

    @Override
    public void eliminarCliente(String claveCliente) {
        UnCliente cliente = clientes.remove(claveCliente);
        if (cliente != null) {
            removerSesionActiva(cliente.getNombreUsuario());
            System.out.println("Cliente " + claveCliente + " eliminado.");
        }
    }

    @Override
    public void difundirMensaje(String mensaje, UnCliente remitente) {
        for (UnCliente c : clientes.values()) {
            if (c.isAutenticado()) {
                c.enviarMensaje(remitente.getNombreUsuario() + ": " + mensaje);
            }
        }
    }

    @Override
    public void enviarMensajePrivado(String usuarioDestino, String mensaje, UnCliente remitente) {
        String claveDestino = usuariosActivos.get(usuarioDestino.toLowerCase());
        if (claveDestino != null) {
            UnCliente destino = clientes.get(claveDestino);
            if (destino != null) {
                destino.enviarMensaje("(Privado de " + remitente.getNombreUsuario() + "): " + mensaje);
                remitente.enviarMensaje("(Privado a " + usuarioDestino + "): " + mensaje);
                return;
            }
        }
        remitente.enviarMensaje("Error: Usuario '" + usuarioDestino + "' no encontrado o desconectado.");
    }
}