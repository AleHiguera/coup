package ServidorMulti;

import JuegoCoup.SalaCoup;
import JuegoCoup.Jugador;
import java.util.HashMap;
import java.util.Map;

public class GestorPartida {
    private SalaCoup juego;

    private Map<String, UnCliente> puentesDeConexion;

    public GestorPartida() {
        this.juego = new SalaCoup();
        this.puentesDeConexion = new HashMap<>();
    }



    public synchronized void unirJugador(UnCliente clienteConexion) {
        String nombre = clienteConexion.getNombreUsuario();


        boolean aceptado = juego.agregarJugador(nombre);

        if (aceptado) {
            puentesDeConexion.put(nombre, clienteConexion);

            mensajeGlobal("El jugador " + nombre + " entró a la sala.");


            if (juego.getJugadores().size() == 3) {
                arrancarJuego();
            }
        } else {
            clienteConexion.enviarMensaje("Error: La sala está llena (máx 6) o el juego ya empezó.");
        }
    }

    private void arrancarJuego() {
        try {
            juego.iniciarPartida();
            mensajeGlobal("--- ¡PARTIDA INICIADA! ---");


            for (Jugador jLogico : juego.getJugadores()) {
                UnCliente socketDestino = puentesDeConexion.get(jLogico.getNombreUsuario());

                if (socketDestino != null) {
                    socketDestino.enviarMensaje("Tus Cartas: " + jLogico.getManoActual());
                    socketDestino.enviarMensaje("Tus Monedas: " + jLogico.getMonedas());

                    if (jLogico.equals(juego.getJugadorActivo())) {
                        socketDestino.enviarMensaje(">>> ¡ES TU TURNO! <<<");
                    } else {
                        socketDestino.enviarMensaje("Turno de: " + juego.getJugadorActivo().getNombreUsuario());
                    }
                }
            }
        } catch (Exception e) {
            mensajeGlobal("Error al iniciar: " + e.getMessage());
        }
    }


    private void mensajeGlobal(String msg) {
        for (UnCliente c : puentesDeConexion.values()) {
            c.enviarMensaje(msg);
        }
    }
}