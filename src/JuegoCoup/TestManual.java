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
            System.out.println("✅ Test Duque (Fase Intento + Ejecución): PASÓ");

            testDescarteYMuerte();
            System.out.println("✅ Test Descarte y Muerte: PASÓ");

            testGolpeDeEstadoObligatorio();
            System.out.println("✅ Test Golpe de Estado obligatorio: PASÓ");

            testDesafioAccion();
            System.out.println("✅ Test Regla de Desafío (Mentira y Verdad): PASÓ");

            testEmbajadorSeleccionManual();
            System.out.println("✅ TEST EMBAJADOR MANUAL SUPERADO");

        } catch (Exception e) {
            System.err.println("\n🛑 ERROR CRÍTICO EN PRUEBAS: " + e.getMessage());
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
        if (sala.getJugadores().size() > 6) throw new RuntimeException("Fallo Concurrencia");
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

    private static void testDuqueCobraImpuestos() {
        SalaCoup sala = new SalaCoup("T3", "Sala Duque");
        sala.agregarJugador("J1");
        sala.agregarJugador("J2");
        sala.iniciarPartida();

        Jugador j1 = sala.getJugadorActivo();
        int monedasAntes = j1.getMonedas();


        String respuesta = sala.realizarAccionImpuestos(j1);
        if (!respuesta.startsWith("INTENTO:IMPUESTOS")) {
            throw new RuntimeException("El Duque debe devolver INTENTO. Recibido: " + respuesta);
        }
        if (j1.getMonedas() != monedasAntes) {
            throw new RuntimeException("Error: Se dieron monedas antes de tiempo.");
        }

        sala.ejecutarAccionPendiente("IMPUESTOS", j1, "TODOS");

        if (j1.getMonedas() != monedasAntes + 3) {
            throw new RuntimeException("El Duque no sumó 3 monedas tras ejecutar.");
        }
    }

    private static void testDescarteYMuerte() {
        SalaCoup sala = new SalaCoup("T4", "Sala Muerte");
        sala.agregarJugador("Vic"); sala.agregarJugador("Atq");
        sala.iniciarPartida();
        Jugador victima = sala.getJugadores().get(0);
        List<TipoCarta> mano = new ArrayList<>();
        mano.add(TipoCarta.DUQUE); mano.add(TipoCarta.DUQUE);
        victima.actualizarMano(mano);

        sala.concretarDescarte("Vic", "DUQUE");
        if (!victima.estaVivo() || victima.getManoActual().size() != 1) throw new RuntimeException("Fallo vida 1");
        sala.concretarDescarte("Vic", "DUQUE");
        if (victima.estaVivo()) throw new RuntimeException("Fallo muerte final");
    }

    private static void testGolpeDeEstadoObligatorio() {
        SalaCoup sala = new SalaCoup("T5", "Sala Golpe");
        sala.agregarJugador("Rico"); sala.agregarJugador("Pobre");
        sala.iniciarPartida();
        Jugador rico = sala.getJugadorActivo();
        rico.ganarMonedas(9);
        String res = sala.realizarGolpeDeEstado(rico, "Pobre");
        if (!res.startsWith("ESPERA_CARTA")) throw new RuntimeException("Golpe falló");
        if (rico.getMonedas() != 4) throw new RuntimeException("Cobro incorrecto");
    }

    private static void testDesafioAccion() {
        SalaCoup sala = new SalaCoup("T6", "Sala Desafios");
        sala.agregarJugador("Mentiroso");
        sala.agregarJugador("Honesto");
        sala.iniciarPartida();

        Jugador mentiroso = sala.getJugadores().get(0);
        List<TipoCarta> manoSinDuque = new ArrayList<>();
        manoSinDuque.add(TipoCarta.CAPITAN);
        manoSinDuque.add(TipoCarta.CONDESA);
        mentiroso.actualizarMano(manoSinDuque);

        sala.realizarAccionImpuestos(mentiroso);
        String resMentira = sala.resolverDesafio("Mentiroso", "Honesto", "IMPUESTOS");

        if (!resMentira.equals("DESAFIO_EXITOSO:Mentiroso")) {
            throw new RuntimeException("Fallo: No detectó la mentira. Res: " + resMentira);
        }

        Jugador honesto = sala.getJugadores().get(1);
        List<TipoCarta> manoConDuque = new ArrayList<>();
        manoConDuque.add(TipoCarta.DUQUE);
        manoConDuque.add(TipoCarta.ASESINO);
        honesto.actualizarMano(manoConDuque);

        sala.realizarAccionImpuestos(honesto);
        String resVerdad = sala.resolverDesafio("Honesto", "Mentiroso", "IMPUESTOS");

        if (!resVerdad.equals("DESAFIO_FALLIDO:Mentiroso")) {
            throw new RuntimeException("Fallo: No validó la verdad. Res: " + resVerdad);
        }

        if (honesto.getManoActual().size() != 2) {
            throw new RuntimeException("Fallo: El honesto perdió carta injustamente.");
        }
    }

    private static void testEmbajadorSeleccionManual() {
        System.out.println("\n--- Test: Embajador Selección Manual ---");
        SalaCoup sala = new SalaCoup("TestEmb", "Sala Embajador");
        sala.agregarJugador("Emba");
        sala.agregarJugador("Rival");
        sala.iniciarPartida();

        Jugador emba = sala.getJugadores().get(0);

        List<TipoCarta> manoInicial = new ArrayList<>();
        manoInicial.add(TipoCarta.EMBAJADOR);
        manoInicial.add(TipoCarta.CONDESA);
        emba.actualizarMano(manoInicial);

        System.out.println("1. Iniciando acción Embajador...");
        String resInicio = sala.realizarAccionEmbajador(emba);
        if (!resInicio.startsWith("INTENTO:EMBAJADOR")) throw new RuntimeException("Fallo al iniciar acción.");
        System.out.println("2. Ejecutando acción pendiente...");
        String resEjecucion = sala.ejecutarAccionPendiente("EMBAJADOR", emba, "TODOS");

        if (!resEjecucion.equals("SELECCION_EMBAJADOR")) {
            throw new RuntimeException("Fallo: Esperaba 'SELECCION_EMBAJADOR' pero recibí: " + resEjecucion);
        }
        System.out.println("   -> Correcto: La sala pide intervención manual.");
        System.out.println("3. Obteniendo cartas del mazo (Opciones)...");
        List<TipoCarta> opciones = sala.obtenerOpcionesEmbajador(emba);
        System.out.println("   Cartas disponibles para elegir: " + opciones);
        if (opciones.size() != 4) {
            throw new RuntimeException("Fallo: Debería haber 4 cartas (2 mano + 2 mazo). Hay: " + opciones.size());
        }
        List<TipoCarta> seleccionadas = new ArrayList<>();
        seleccionadas.add(opciones.get(0));
        seleccionadas.add(opciones.get(3));

        System.out.println("4. Jugador elige: " + seleccionadas);
        String resFinal = sala.concretarSeleccionEmbajador(emba, seleccionadas, opciones);
        if (!resFinal.startsWith("EXITO")) throw new RuntimeException("Fallo al concretar selección: " + resFinal);

        if (emba.getManoActual().size() != 2) {
            throw new RuntimeException("Fallo: El jugador terminó con " + emba.getManoActual().size() + " cartas. Debería tener 2.");
        }

        if (!emba.getManoActual().containsAll(seleccionadas)) {
            throw new RuntimeException("Fallo: La mano final no coincide con lo que eligió el jugador.");
        }
        if (sala.getJugadorActivo().equals(emba)) {
            throw new RuntimeException("Fallo: El turno no avanzó después del Embajador.");
        }

    }
}