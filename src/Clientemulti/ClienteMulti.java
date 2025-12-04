package Clientemulti;
import java.io.IOException;
import java.net.Socket;

public class ClienteMulti {

    public static void main(String[] args) throws IOException {
        System.out.println("Intentando conectar...");
        try (Socket s = new Socket("localhost", 8080)) {
            System.out.println("Conectado. Esperando mensajes del servidor...");

            ParaMandar paraMandar = new ParaMandar(s);
            Thread hiloParaMandar = new Thread(paraMandar);
            hiloParaMandar.start();

            ParaRecibir paraRecibir = new ParaRecibir(s);
            Thread hiloParaRecibir = new Thread(paraRecibir);
            hiloParaRecibir.start();

            try {
                hiloParaMandar.join();
                hiloParaRecibir.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

        } catch (IOException e) {
            System.err.println("Error de conexión: " + e.getMessage());
        }
    }
}