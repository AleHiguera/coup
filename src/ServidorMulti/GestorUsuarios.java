package ServidorMulti;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GestorUsuarios {

    private final ManejadorBaseDatos bd;
    private final Set<String> comandosReservados = new HashSet<>(
            Arrays.asList("/login", "/register", "/exit", "login", "register", "exit")
    );

    private static final String PATRON_USUARIO_FORMATO = "^[a-zA-Z0-9]{3,20}$";
    private static final Pattern COMPILADOR_PATRON = Pattern.compile(PATRON_USUARIO_FORMATO);


    public GestorUsuarios(ManejadorBaseDatos bd) {
        this.bd = bd;
    }
    private String validarCamposRequeridos(String usuario, String contrasena) {
        if (usuario.isEmpty() || contrasena.isEmpty()) {
            return "ERROR: Ambos campos son requeridos.";
        }
        return "OK";
    }

    private String validarFormatoUsuario(String usuario) {
        Matcher matcher = COMPILADOR_PATRON.matcher(usuario);
        if (!matcher.matches()) {
            return "ERROR: El usuario debe tener entre 3 y 20 caracteres, y solo puede contener letras y números.";
        }
        return "OK";
    }

    private String validarUsuarioReservado(String usuario) {
        if (esComandoReservado(usuario)) {
            return "ERROR: El nombre de usuario '" + usuario + "' no está permitido.";
        }
        return "OK";
    }

    public boolean esComandoReservado(String texto) {
        return comandosReservados.contains(texto.toLowerCase());
    }
    public String registrarUsuario(String usuario, String contrasena) throws SQLException {
        String resultadoValidacion = realizarValidaciones(usuario, contrasena);

        if (!resultadoValidacion.equals("OK")) {
            return resultadoValidacion;
        }

        if (bd.usuarioExiste(usuario)) {
            return "ERROR: El nombre de usuario '" + usuario + "' ya existe.";
        }

        return intentarRegistroBD(usuario, contrasena);
    }

    private String realizarValidaciones(String usuario, String contrasena) {
        String resultado = validarCamposRequeridos(usuario, contrasena);
        if (!resultado.equals("OK")) return resultado;

        resultado = validarFormatoUsuario(usuario);
        if (!resultado.equals("OK")) return resultado;

        resultado = validarUsuarioReservado(usuario);
        return resultado;
    }

    private String intentarRegistroBD(String usuario, String contrasena) throws SQLException {
        if (bd.registrarNuevoUsuario(usuario, contrasena)) {
            return "REGISTRO_OK";
        } else {
            return "ERROR: No se pudo completar el registro.";
        }
    }
    public boolean autenticar(String usuario, String contrasena) throws SQLException {
        if (usuario.isEmpty() || contrasena.isEmpty()) {
            return false;
        }
        return bd.autenticarUsuario(usuario, contrasena);
    }
}