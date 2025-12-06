package ServidorMulti;

public class Constantes {
    public static final String CMD_LOGIN = "/login";
    public static final String CMD_REGISTER = "/register";
    public static final String CMD_EXIT = "/exit";

    // Mensajes del Sistema
    public static final String MSG_BIENVENIDA = "Bienvenido. Usa /login o /register.";
    public static final String ERR_LOGIN_DUPLICADO = "ERROR: Usuario ya tiene sesión activa.";
    public static final String ERR_AUTH_REQUERIDA = "Debes autenticarte primero.";

    // Configuración BD
    public static final String DB_URL = "jdbc:sqlite:chat_usuarios.db";
}
