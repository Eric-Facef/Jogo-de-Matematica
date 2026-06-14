package dc.unifacef.Jogo.Victor.service;

import dc.unifacef.Jogo.Victor.model.Sala; // Ajustado para corresponder ao seu pacote Model
import org.springframework.stereotype.Service;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class LobbyService {

    private final Map<String, Sala> salasAtivas = new ConcurrentHashMap<>();

    // 🌐 EXTRATO PARA O CONTROLLER PODER GERENCIAR O CRONÔMETRO CENTRALIZADO
    public Map<String, Sala> getSalasAtivas() {
        return this.salasAtivas;
    }

    public Sala criarSala() {
        String codigo = UUID.randomUUID().toString().substring(0, 4).toUpperCase();

        while (salasAtivas.containsKey(codigo)) {
            codigo = UUID.randomUUID().toString().substring(0, 4).toUpperCase();
        }

        Sala novaSala = new Sala(codigo);
        salasAtivas.put(codigo, novaSala);
        return novaSala;
    }

    public Sala obtenerSala(String codigo) {
        if (codigo == null) return null;
        return salasAtivas.get(codigo.toUpperCase());
    }

    public void fecharSala(String codigo) {
        salasAtivas.remove(codigo.toUpperCase());
    }
}