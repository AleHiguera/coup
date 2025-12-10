package ServidorMulti;

public interface IServidor {

    void difundirMensaje(String mensaje, UnCliente remitente);
    void enviarMensajePrivado(String usuarioDestino, String mensaje, UnCliente remitente);
    void eliminarCliente(String claveCliente);
    GestorUsuarios getGestorUsuarios();
    GestorPartida getGestorPartida();

    boolean registrarSesionActiva(String usuario, String claveCliente);
    void removerSesionActiva(String usuario);
    String getUsuariosConectados();
}