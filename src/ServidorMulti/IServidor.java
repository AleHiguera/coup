package ServidorMulti;

public interface IServidor {

    void difundirMensaje(String mensaje, UnCliente remitente);
    void enviarMensajePrivado(String usuarioDestino, String mensaje, UnCliente remitente);
    void eliminarCliente(String claveCliente);

    // Gestión de usuarios y sesión
    GestorUsuarios getGestorUsuarios();
    boolean registrarSesionActiva(String usuario, String claveCliente);
    void removerSesionActiva(String usuario);
}
