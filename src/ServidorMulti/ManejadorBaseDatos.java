package ServidorMulti;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class ManejadorBaseDatos {
    private static final String URL_BD = "jdbc:sqlite:chat_usuarios.db";
    private static final String TABLA_USUARIOS = "usuarios";

    public ManejadorBaseDatos() {
        crearTablaUsuarios();
    }
    private Connection obtenerConexion() throws SQLException {
        return DriverManager.getConnection(URL_BD);
    }

    private void crearTablaUsuarios() {
        String sql = "CREATE TABLE IF NOT EXISTS " + TABLA_USUARIOS + " ("
                + "usuario TEXT PRIMARY KEY,"
                + "contrasena TEXT NOT NULL"
                + ");";
        try (Connection conn = obtenerConexion();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.execute();
        } catch (SQLException e) {
            System.err.println("Error al crear tabla de usuarios: " + e.getMessage());
        }
    }

    public boolean usuarioExiste(String usuario) throws SQLException {
        String sql = "SELECT 1 FROM " + TABLA_USUARIOS + " WHERE usuario = ?;";
        try (Connection conn = obtenerConexion();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, usuario);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        }
    }

    public boolean registrarNuevoUsuario(String usuario, String contrasena) throws SQLException {
        String sql = "INSERT INTO " + TABLA_USUARIOS + " (usuario, contrasena) VALUES (?, ?);";
        try (Connection conn = obtenerConexion();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, usuario);
            stmt.setString(2, contrasena);
            return stmt.executeUpdate() > 0;
        }
    }

    public boolean autenticarUsuario(String usuario, String contrasena) throws SQLException {
        String sql = "SELECT contrasena FROM " + TABLA_USUARIOS + " WHERE usuario = ?;";
        try (Connection conn = obtenerConexion();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, usuario);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("contrasena").equals(contrasena);
                }
                return false;
            }
        }
    }
}