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
        new ServidorMulti().iniciar(8080);
    }

    public void iniciar(int puerto) throws IOException {
        try (ServerSocket servidorSocket = new ServerSocket(puerto)) {
            while (true) {
                aceptarConexion(servidorSocket);
            }
        }
    }

    private void aceptarConexion(ServerSocket servidorSocket) throws IOException {
        Socket s = servidorSocket.accept();
        int idCliente = contadorClientes.getAndIncrement();
        String claveCliente = Integer.toString(idCliente);

        UnCliente unCliente = new UnCliente(s, claveCliente, this);
        Thread hilo = new Thread(unCliente);

        clientes.put(claveCliente, unCliente);
        hilo.start();

        System.out.println("Se conecto el chango #" + claveCliente);
        unCliente.enviarMensaje("Bienvenido al chat. Usa /login o /register para comenzar.");
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
        UnCliente clienteDesconectado = clientes.remove(claveCliente);

        if (clienteDesconectado != null) {
            if (clienteDesconectado.getNombreUsuario() != null) {
                getGestorPartida().eliminarJugador(clienteDesconectado.getNombreUsuario());
                removerSesionActiva(clienteDesconectado.getNombreUsuario());
                System.out.println("Sesión de " + clienteDesconectado.getNombreUsuario() + " eliminada al desconectarse.");
            }
        }
        System.out.println("El chango #" + claveCliente + " se desconectó.");
    }

    @Override
    public void difundirMensaje(String mensaje, UnCliente remitente) {
        for (UnCliente cliente : clientes.values()) {
            if (cliente.isAutenticado()) {
                cliente.enviarMensaje(mensaje);
            }
        }
    }

    @Override
    public void enviarMensajePrivado(String aQuien, String mensaje, UnCliente remitente) {
        String claveDestino = usuariosActivos.get(aQuien.toLowerCase());
        if (claveDestino != null) {
            UnCliente clienteDestino = clientes.get(claveDestino);
            if (clienteDestino != null) {
                clienteDestino.enviarMensaje(mensaje);
            }
        }
    }
    @Override
    public String getUsuariosConectados() {
        return String.join(", ", usuariosActivos.keySet());
    }
}