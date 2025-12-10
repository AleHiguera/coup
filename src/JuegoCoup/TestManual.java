package JuegoCoup;

import java.util.ArrayList;
import java.util.List;

public class TestManual {

    public static void main(String[] args) {
        System.out.println("--- INICIANDO PRUEBAS MANUALES ---");

        try {
            testAgregarJugadoresConcurrente();
            System.out.println("✅ Test Concurrencia: PASÓ");

            testTurnosYEliminacion();
            System.out.println("✅ Test Turnos y Eliminación: PASÓ");

            testDuqueCobraImpuestos();
            System.out.println("✅ Test Duque (Impuestos): PASÓ");

            testDescarteYMuerte();
            System.out.println("✅ Test Descarte y Muerte: PASÓ");


        } catch (Exception e) {
            System.err.println("\n ERROR CRÍTICO EN PRUEBAS: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // 1. Prueba de Concurrencia (Simula 50 hilos entrando a la vez)
    private static void testAgregarJugadoresConcurrente() throws InterruptedException {
        SalaCoup sala = new SalaCoup("T1", "Sala Test");
        int numHilos = 50;
        List<Thread> hilos = new ArrayList<>();

        for (int i = 0; i < numHilos; i++) {
            int finalI = i;
            Thread t = new Thread(() -> {
                sala.agregarJugador("Jugador" + finalI);
            });
            hilos.add(t);
            t.start();
        }

        for (Thread t : hilos) {
            t.join();
        }

        if (sala.getJugadores().size() > 6) {
            throw new RuntimeException("Fallo de Concurrencia: Entraron " + sala.getJugadores().size() + " jugadores (Max permitido 6).");
        }
    }

    // 2. Prueba de que el turno pasa si borras al jugador activo
    private static void testTurnosYEliminacion() {
        SalaCoup sala = new SalaCoup("T2", "Sala Turnos");
        sala.agregarJugador("A");
        sala.agregarJugador("B");
        sala.agregarJugador("C");
        sala.iniciarPartida();

        if (!sala.getJugadorActivo().getNombreUsuario().equals("A")) {
            throw new RuntimeException("El primer turno no es de A.");
        }
        sala.removerJugador("A");

        if (!sala.getJugadorActivo().getNombreUsuario().equals("B")) {
            throw new RuntimeException("El turno no avanzó a B tras eliminar a A.");
        }

        sala.removerJugador("C");
        if (!sala.getJugadorActivo().getNombreUsuario().equals("B")) {
            throw new RuntimeException("El turno cambió incorrectamente tras eliminar a C.");
        }
    }

    // 3. Prueba lógica del Duque
    private static void testDuqueCobraImpuestos() {
        SalaCoup sala = new SalaCoup("T3", "Sala Duque");
        sala.agregarJugador("J1");
        sala.agregarJugador("J2");
        sala.agregarJugador("J3");
        sala.iniciarPartida();

        Jugador j1 = sala.getJugadorActivo();
        int monedasAntes = j1.getMonedas();

        sala.realizarAccionImpuestos(j1);

        if (j1.getMonedas() != monedasAntes + 3) {
            throw new RuntimeException("El Duque no sumó 3 monedas. Tiene: " + j1.getMonedas());
        }
    }

    // 4. Prueba de descarte y eliminación definitiva
    private static void testDescarteYMuerte() {
        SalaCoup sala = new SalaCoup("T4", "Sala Muerte");
        sala.agregarJugador("Vic");
        sala.agregarJugador("Atq");
        sala.agregarJugador("Espectador");
        sala.iniciarPartida();

        Jugador victima = sala.getJugadores().get(0);

        // Trucamos la mano
        List<TipoCarta> manoFalsa = new ArrayList<>();
        manoFalsa.add(TipoCarta.DUQUE);
        manoFalsa.add(TipoCarta.DUQUE);
        victima.actualizarMano(manoFalsa);

        // Perder primera vida
        String res1 = sala.concretarDescarte("Vic", "DUQUE");
        if (!victima.estaVivo() || victima.getManoActual().size() != 1) {
            throw new RuntimeException("Fallo al perder primera vida.");
        }

        // Perder segunda vida
        String res2 = sala.concretarDescarte("Vic", "DUQUE");
        if (victima.estaVivo()) {
            throw new RuntimeException("El jugador debería estar muerto y sigue vivo.");
        }
    }
}
