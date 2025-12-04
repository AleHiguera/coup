package Clientemulti;
import java.io.DataInputStream;
import java.io.IOException;
import java.net.Socket;

public class ParaRecibir implements Runnable {
    final DataInputStream entrada;
    private final Socket socket;

    public ParaRecibir(Socket s) throws IOException {
        this.socket = s;
        entrada = new DataInputStream(s.getInputStream());
    }

    @Override
    public void run() {
        String mensaje;
        while (!socket.isClosed()) {
            try {
                mensaje = entrada.readUTF();
                System.out.println(mensaje);
            } catch (IOException ex) {
                break;
            }
        }
        System.out.println("Desconectado del servidor.");
    }
}