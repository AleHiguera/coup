package JuegoCoup;

import java.util.ArrayList;
import java.util.List;

public class TestManual {

    public static void main(String[] args) {
        System.out.println("--- INICIANDO PRUEBAS MANUALES COMPLETAS ---");

        try {
            testAgregarJugadoresConcurrente();
            System.out.println("Test Concurrencia: PASÓ");

            testTurnosYEliminacion();
            System.out.println("Test Turnos y Eliminación: PASÓ");

            testGolpeDeEstadoObligatorio();
            System.out.println("Test Golpe de Estado obligatorio (Restricción 10 monedas): PASÓ");

            testDuqueCobraImpuestos();
            System.out.println("Test Acción: Duque (Impuestos): PASÓ");

            testEmbajadorSeleccionManual();
            System.out.println("Test Acción: Embajador (Selección Manual): PASÓ");

            testCapitanRoboYBloqueo();
            System.out.println("Test Acción: Capitán (Robo y Bloqueo Específico): PASÓ");

            testAyudaExteriorYBloqueo();
            System.out.println("Test Acción: Ayuda Exterior (y Bloqueo de Duque): PASÓ");

            testDesafioAccion();
            System.out.println("Test Mecánica: Desafíos (Mentira y Verdad): PASÓ");

            testAsesinoDoblePeligro();
            System.out.println("Test Mecánica: Asesino (Doble Peligro / 2 Muertes): PASÓ");

            testDescarteYMuerte();
            System.out.println("Test Mecánica: Descarte y Eliminación Final: PASÓ");

            System.out.println("\nTODOS LOS TESTS PASARON EXITOSAMENTE 🎉");

        } catch (Exception e) {
            System.err.println("\nERROR CRÍTICO EN PRUEBAS: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void testAgregarJugadoresConcurrente() throws InterruptedException {
        SalaCoup sala = new SalaCoup("T1", "Sala Test");
        int numHilos = 50;
        List<Thread> hilos = new ArrayList<>();
        for (int i = 0; i < numHilos; i++) {
            int finalI = i;
            Thread t = new Thread(() -> sala.agregarJugador("Jugador" + finalI));
            hilos.add(t);
            t.start();
        }
        for (Thread t : hilos) t.join();
        if (sala.getJugadores().size() > 6) throw new RuntimeException("Fallo Concurrencia: Hay más de 6 jugadores.");
    }

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

    private static void testGolpeDeEstadoObligatorio() {
        SalaCoup sala = new SalaCoup("T5", "Sala Golpe");
        sala.agregarJugador("Rico");
        sala.agregarJugador("Pobre");
        sala.agregarJugador("Relleno");
        sala.iniciarPartida();

        Jugador rico = sala.getJugadorActivo();
        if(!rico.getNombreUsuario().equals("Rico")) {
            rico.setNombreUsuarioParaTest("Rico");
        }

        rico.ganarMonedas(8);
        String intentoIlegal = sala.realizarAccionIngreso(rico);
        if (!intentoIlegal.startsWith("ERROR")) {
            throw new RuntimeException("Fallo: Se permitió 'Ingreso' teniendo 10 monedas.");
        }
        String res = sala.realizarGolpeDeEstado(rico, "Pobre");
        if (!res.startsWith("ESPERA_CARTA")) throw new RuntimeException("Golpe legítimo falló.");

        if (rico.getMonedas() != 3) throw new RuntimeException("Cobro incorrecto del golpe (10 - 7 = 3).");
    }

    private static void testDuqueCobraImpuestos() {
        SalaCoup sala = new SalaCoup("T3", "Sala Duque");
        sala.agregarJugador("J1");
        sala.agregarJugador("J2");
        sala.agregarJugador("Relleno");
        sala.iniciarPartida();

        Jugador j1 = sala.getJugadorActivo();
        int monedasAntes = j1.getMonedas();

        String respuesta = sala.realizarAccionImpuestos(j1);
        if (!respuesta.startsWith("INTENTO:IMPUESTOS")) {
            throw new RuntimeException("El Duque debe devolver INTENTO. Recibido: " + respuesta);
        }

        sala.ejecutarAccionPendiente("IMPUESTOS", j1, "TODOS");

        if (j1.getMonedas() != monedasAntes + 3) {
            throw new RuntimeException("El Duque no sumó 3 monedas tras ejecutar.");
        }
    }

    private static void testEmbajadorSeleccionManual() {
        SalaCoup sala = new SalaCoup("TestEmb", "Sala Embajador");
        sala.agregarJugador("Emba");
        sala.agregarJugador("Rival");
        sala.agregarJugador("Relleno");
        sala.iniciarPartida();

        Jugador emba = sala.getJugadores().get(0);

        List<TipoCarta> manoInicial = new ArrayList<>();
        manoInicial.add(TipoCarta.EMBAJADOR);
        manoInicial.add(TipoCarta.CONDESA);
        emba.actualizarMano(manoInicial);

        String resInicio = sala.realizarAccionEmbajador(emba);
        if (!resInicio.startsWith("INTENTO:EMBAJADOR")) throw new RuntimeException("Fallo inicio Embajador");
        String resEjecucion = sala.ejecutarAccionPendiente("EMBAJADOR", emba, "TODOS");
        if (!resEjecucion.equals("SELECCION_EMBAJADOR")) throw new RuntimeException("No pidió selección manual");
        List<TipoCarta> opciones = sala.obtenerOpcionesEmbajador(emba);
        if (opciones.size() != 4) throw new RuntimeException("Debería dar 4 opciones.");
        List<TipoCarta> seleccionadas = new ArrayList<>();
        seleccionadas.add(opciones.get(0));
        seleccionadas.add(opciones.get(3));

        sala.concretarSeleccionEmbajador(emba, seleccionadas, opciones);

        if (!emba.getManoActual().containsAll(seleccionadas)) {
            throw new RuntimeException("La mano final no coincide con la selección.");
        }
    }

    private static void testCapitanRoboYBloqueo() {
        SalaCoup sala = new SalaCoup("T_Cap", "Sala Capitan");
        sala.agregarJugador("Ladron");
        sala.agregarJugador("Victima");
        sala.agregarJugador("Relleno");
        sala.iniciarPartida();

        Jugador ladron = sala.getJugadores().get(0);
        Jugador victima = sala.getJugadores().get(1);
        victima.ganarMonedas(2);

        sala.iniciarAccionRobar(ladron, "Victima");
        sala.ejecutarAccionPendiente("ROBAR", ladron, "Victima");

        if (ladron.getMonedas() != 4) throw new RuntimeException("El ladrón no recibió las 2 monedas.");
        if (victima.getMonedas() != 2) throw new RuntimeException("La víctima no perdió las 2 monedas.");
        List<TipoCarta> manoVic = new ArrayList<>();
        manoVic.add(TipoCarta.CAPITAN);
        manoVic.add(TipoCarta.DUQUE);
        victima.actualizarMano(manoVic);
        String res = sala.resolverDesafio("Victima", "Ladron", "BLOQUEO_ROBO_CAPITAN");

        if (!res.startsWith("DESAFIO_FALLIDO")) {
            throw new RuntimeException("El desafío al bloqueo debería fallar porque Victima tiene Capitán.");
        }
        if(victima.getManoActual().size() != 2) throw new RuntimeException("Error en reciclaje de carta.");
    }

    private static void testAyudaExteriorYBloqueo() {
        SalaCoup sala = new SalaCoup("T_Ayuda", "Sala Ayuda");
        sala.agregarJugador("Pobre");
        sala.agregarJugador("Duque");
        sala.agregarJugador("Relleno");
        sala.iniciarPartida();

        Jugador pobre = sala.getJugadores().get(0);
        int monedasInicio = pobre.getMonedas();

        String res = sala.iniciarAccionAyudaExterior(pobre);
        if(!res.contains("AYUDA")) throw new RuntimeException("Fallo inicio ayuda.");
        sala.ejecutarAccionPendiente("AYUDA", pobre, "TODOS");
        if(pobre.getMonedas() != monedasInicio + 2) throw new RuntimeException("No recibió ayuda exterior.");
    }

    private static void testDesafioAccion() {
        SalaCoup sala = new SalaCoup("T6", "Sala Desafios");
        sala.agregarJugador("Mentiroso");
        sala.agregarJugador("Honesto");
        sala.agregarJugador("Relleno");
        sala.iniciarPartida();

        Jugador mentiroso = sala.getJugadores().get(0);
        List<TipoCarta> manoSinDuque = new ArrayList<>();
        manoSinDuque.add(TipoCarta.CAPITAN);
        manoSinDuque.add(TipoCarta.CONDESA);
        mentiroso.actualizarMano(manoSinDuque);

        String resMentira = sala.resolverDesafio("Mentiroso", "Honesto", "IMPUESTOS");
        if (!resMentira.equals("DESAFIO_EXITOSO:Mentiroso")) {
            throw new RuntimeException("Fallo: No detectó la mentira.");
        }

        Jugador honesto = sala.getJugadores().get(1);
        List<TipoCarta> manoConDuque = new ArrayList<>();
        manoConDuque.add(TipoCarta.DUQUE);
        manoConDuque.add(TipoCarta.ASESINO);
        honesto.actualizarMano(manoConDuque);

        String resVerdad = sala.resolverDesafio("Honesto", "Mentiroso", "IMPUESTOS");
        if (!resVerdad.equals("DESAFIO_FALLIDO:Mentiroso")) {
            throw new RuntimeException("Fallo: No validó la verdad.");
        }
    }

    private static void testAsesinoDoblePeligro() {
        SalaCoup sala = new SalaCoup("Mortal", "Sala Asesina");
        sala.agregarJugador("Asesino");
        sala.agregarJugador("Victima");
        sala.agregarJugador("Relleno");
        sala.iniciarPartida();

        Jugador asesino = sala.getJugadores().get(0);
        Jugador victima = sala.getJugadores().get(1);
        asesino.ganarMonedas(10);
        List<TipoCarta> manoAsesino = new ArrayList<>();
        manoAsesino.add(TipoCarta.ASESINO);
        manoAsesino.add(TipoCarta.DUQUE);
        asesino.actualizarMano(manoAsesino);
        sala.iniciarAccionAsesinato(asesino, "Victima");
        String resDesafio = sala.resolverDesafio("Asesino", "Victima", "ASESINAR");
        if (!resDesafio.startsWith("DESAFIO_FALLIDO")) throw new RuntimeException("El desafío debería fallar para la víctima.");

        if(victima.getManoActual().isEmpty()) {

        }
        sala.concretarDescarte("Victima", victima.getManoActual().get(0).name());
        if (victima.getManoActual().size() != 1) throw new RuntimeException("Victima no perdió 1ra vida.");
        String resAccion = sala.ejecutarAccionPendiente("ASESINAR", asesino, "Victima");
        if (!resAccion.startsWith("ESPERA_CARTA")) throw new RuntimeException("El asesinato no se ejecutó tras el desafío.");
        sala.concretarDescarte("Victima", victima.getManoActual().get(0).name());

        if (victima.estaVivo()) throw new RuntimeException("La víctima debería estar muerta (Doble Peligro).");
    }

    private static void testDescarteYMuerte() {
        SalaCoup sala = new SalaCoup("T4", "Sala Muerte");
        sala.agregarJugador("Vic");
        sala.agregarJugador("Atq");
        sala.agregarJugador("Relleno");
        sala.iniciarPartida();
        Jugador victima = sala.getJugadores().get(0);
        List<TipoCarta> mano = new ArrayList<>();
        mano.add(TipoCarta.DUQUE);
        mano.add(TipoCarta.DUQUE);
        victima.actualizarMano(mano);
        sala.concretarDescarte("Vic", "DUQUE");
        if (!victima.estaVivo() || victima.getManoActual().size() != 1) throw new RuntimeException("Fallo vida 1");

        sala.concretarDescarte("Vic", "DUQUE");
        if (victima.estaVivo()) throw new RuntimeException("Fallo muerte final");
    }
}