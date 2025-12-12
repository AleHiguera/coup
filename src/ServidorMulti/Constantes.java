package ServidorMulti;

public class Constantes {
    // --- COMANDOS GENERALES ---
    public static final String CMD_LOGIN = "/login";
    public static final String CMD_REGISTER = "/register";
    public static final String CMD_EXIT = "/exit";
    public static final String CMD_JUGAR = "/jugar";

    // --- MENSAJES DEL SISTEMA ---
    public static final String MSG_BIENVENIDA = "Bienvenido. Usa /login o /register.";
    public static final String ERR_LOGIN_DUPLICADO = "ERROR: Usuario ya tiene sesión activa.";
    public static final String ERR_AUTH_REQUERIDA = "Debes autenticarte primero.";

    // --- CONFIGURACIÓN ---
    public static final String DB_URL = "jdbc:sqlite:chat_usuarios.db";

    // Prefijos de Respuesta Interna (SalaCoup -> GestorPartida)
    public static final String PREFIJO_ERROR = "ERROR:";
    public static final String PREFIJO_EXITO = "EXITO:";
    public static final String PREFIJO_INTENTO = "INTENTO:";
    public static final String PREFIJO_ESPERA = "ESPERA_CARTA:";
    public static final String PREFIJO_ELIMINADO = "ELIMINADO:";
    public static final String PREFIJO_DESAFIO_EXITOSO = "DESAFIO_EXITOSO:";
    public static final String PREFIJO_DESAFIO_FALLIDO = "DESAFIO_FALLIDO:";

    // Objetivos Especiales
    public static final String OBJ_TODOS = "TODOS";

    // Acciones del Juego (Para no escribir "impuestos" a mano cada vez)
    public static final String ACCION_INGRESO = "INGRESO";
    public static final String ACCION_AYUDA = "AYUDA";
    public static final String ACCION_IMPUESTOS = "IMPUESTOS";
    public static final String ACCION_ROBAR = "ROBAR";
    public static final String ACCION_ASESINAR = "ASESINAR";
    public static final String ACCION_GOLPE = "GOLPE";
    public static final String ACCION_EMBAJADOR = "EMBAJADOR";
}