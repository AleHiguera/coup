package JuegoCoup;

import java.util.ArrayList;
import java.util.List;

public class Jugador {
    private String nombreUsuario;
    private int monedas;
    private final List<TipoCarta> mano; // Cartas ocultas
    private final List<TipoCarta> cartasPerdidas; // Cartas reveladas (influencias perdidas)

    private static final int MONEDAS_INICIALES = 2;
    private static final int MAX_MONEDAS = 10; // Regla del golpe de estado obligatorio

    public Jugador(String nombreUsuario) {
        this.nombreUsuario = nombreUsuario;
        this.monedas = MONEDAS_INICIALES;
        this.mano = new ArrayList<>();
        this.cartasPerdidas = new ArrayList<>();
    }

    // --- Gestión de Cartas ---

    public void recibirCarta(TipoCarta carta) {
        if (carta != null) {
            mano.add(carta);
        }
    }

    // Usado cuando el jugador pierde un reto o un golpe de estado
    public boolean perderInfluencia(TipoCarta cartaAPerder) {
        if (mano.contains(cartaAPerder)) {
            mano.remove(cartaAPerder);
            cartasPerdidas.add(cartaAPerder);
            return true;
        }
        return false;
    }

    // Usado por el Embajador para cambiar cartas
    public boolean devolverCartaAlMazo(TipoCarta carta) {
        return mano.remove(carta);
    }

    public boolean estaVivo() {
        return !mano.isEmpty();
    }

    public void actualizarMano(List<TipoCarta> nuevasCartas) {
        this.mano.clear();
        this.mano.addAll(nuevasCartas);
    }

    // --- Gestión de Monedas ---

    public void ganarMonedas(int cantidad) {
        if (cantidad > 0) {
            this.monedas += cantidad;
        }
    }

    public boolean pagar(int cantidad) {
        if (monedas >= cantidad) {
            monedas -= cantidad;
            return true;
        }
        return false;
    }

    // --- Getters de Información ---

    public String getNombreUsuario() {
        return nombreUsuario;
    }

    public int getMonedas() {
        return monedas;
    }

    public List<TipoCarta> getManoActual() {
        return new ArrayList<>(mano);
    }

    public List<TipoCarta> getCartasVistas() {
        return new ArrayList<>(cartasPerdidas);
    }

    public boolean debeDarGolpeEstado() {
        return monedas >= MAX_MONEDAS;
    }

    @Override
    public String toString() {
        return String.format("Jugador: %s | Monedas: %d | Influencias: %d",
                nombreUsuario, monedas, mano.size());
    }

    public void setNombreUsuarioParaTest(String nuevoNombre) {
        this.nombreUsuario = nuevoNombre;
    }
}
