package Clientemulti;
import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Socket;

public class ParaMandar implements Runnable {
    final BufferedReader teclado = new BufferedReader(new InputStreamReader(System.in));
    final DataOutputStream salida;
    private final Socket socket;

    public ParaMandar(Socket s) throws IOException {
        this.socket = s;
        this.salida = new DataOutputStream(s.getOutputStream());
    }

    @Override
    public void run() {
        while (!socket.isClosed()) {
            String mensaje;
            try {
                mensaje = teclado.readLine();
                if (mensaje.equalsIgnoreCase("/exit")) {
                    salida.writeUTF(mensaje);
                    socket.close();
                    break;
                }
                salida.writeUTF(mensaje);
            } catch (IOException ex) {
                break;
            }
        }
        System.out.println("Hilo de envío finalizado.");
    }
}