package JuegoCoup;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

public class Mazo {
    private final List<TipoCarta> cartas;

    public Mazo() {
        cartas = new ArrayList<>();
        inicializarMazo();
    }

    private void inicializarMazo() {
        cartas.clear();
        for (TipoCarta tipo : TipoCarta.values()) {
            cartas.add(tipo);
            cartas.add(tipo);
            cartas.add(tipo);
        }
        barajar();
    }

    public void barajar() {
        Collections.shuffle(cartas);
    }

    public Optional<TipoCarta> robarCarta() {
        if (cartas.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(cartas.remove(0));
    }

    public void devolverCarta(TipoCarta carta) {
        cartas.add(carta);
        barajar();
    }

    public int getCantidadRestante() {
        return cartas.size();
    }
}
