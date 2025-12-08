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
            mensajeGlobal("El jugador " + nombre + " entro a la sala.");

            if (juego.getJugadores().size() == 3) {
                arrancarJuego();
            } else {
                mensajeGlobal("Esperando jugadores... (" + juego.getJugadores().size() + "/3)");
            }
        } else {
            clienteConexion.enviarMensaje("Error: La sala esta llena o el juego ya empezo.");
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
                }
            }
            anunciarTurno();
        } catch (Exception e) {
            mensajeGlobal("Error al iniciar: " + e.getMessage());
        }
    }


    public synchronized void procesarJugada(UnCliente cliente, String accion) {
        Jugador jugadorLogico = null;

        for (Jugador j : juego.getJugadores()) {
            if (j.getNombreUsuario().equals(cliente.getNombreUsuario())) {
                jugadorLogico = j;
                break;
            }
        }

        if (jugadorLogico == null) {
            cliente.enviarMensaje("No estas en la partida.");
            return;
        }

        String resultado = "";

        if (accion.equalsIgnoreCase("ingreso")) {
            resultado = juego.realizarAccionIngreso(jugadorLogico);
        } else {
            cliente.enviarMensaje("Accion no reconocida. Prueba: /jugar ingreso");
            return;
        }

        if (resultado.startsWith("ERROR")) {
            cliente.enviarMensaje(resultado);
        } else {
            mensajeGlobal(resultado);
            anunciarTurno();
        }
    }

    private void anunciarTurno() {
        Jugador activo = juego.getJugadorActivo();
        for (UnCliente c : puentesDeConexion.values()) {
            if (c.getNombreUsuario().equals(activo.getNombreUsuario())) {
                c.enviarMensaje(">>> ¡ES TU TURNO! (Escribe: /jugar ingreso) <<<");
            } else {
                c.enviarMensaje("Turno de: " + activo.getNombreUsuario());
            }
        }
    }

    private void mensajeGlobal(String msg) {
        for (UnCliente c : puentesDeConexion.values()) {
            c.enviarMensaje(msg);
        }
    }
}