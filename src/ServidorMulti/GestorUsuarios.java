package ServidorMulti;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class GestorUsuarios {
    private final ManejadorBaseDatos bd;
    private final Set<String> comandosReservados = new HashSet<>(
            Arrays.asList("/login", "/register", "/exit", "login", "register", "exit")
    );

    public GestorUsuarios(ManejadorBaseDatos bd) {
        this.bd = bd;
    }

    public boolean esComandoReservado(String texto) {
        return comandosReservados.contains(texto.toLowerCase());
    }

    public String registrarUsuario(String usuario, String contrasena) throws SQLException {
        if (usuario.isEmpty() || contrasena.isEmpty()) {
            return "ERROR: Ambos campos son requeridos.";
        }
        if (esComandoReservado(usuario)) {
            return "ERROR: El nombre de usuario '" + usuario + "' no está permitido.";
        }
        if (bd.usuarioExiste(usuario)) {
            return "ERROR: El nombre de usuario '" + usuario + "' ya existe.";
        }

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