package dc.unifacef.Jogo.Victor.controller;

import dc.unifacef.Jogo.Victor.dto.AcaoPayload;
import dc.unifacef.Jogo.Victor.model.Jogador;
import dc.unifacef.Jogo.Victor.model.Sala;
import dc.unifacef.Jogo.Victor.service.LobbyService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.*;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@CrossOrigin(origins = "*")
@EnableScheduling
public class JogoController {

    @Autowired
    private LobbyService lobbyService;

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    private static final List<Map<String, Object>> RANKING_GERAL =
            Collections.synchronizedList(new ArrayList<>());

    @PostMapping("/api/sala/criar")
    public Sala criarSala() {
        return lobbyService.criarSala();
    }

    @GetMapping("/api/sala/ranking")
    public List<Map<String, Object>> obterRanking() {
        return RANKING_GERAL;
    }

    // ── CRONÔMETRO GLOBAL (tick a cada segundo) ──────────────────
    @Scheduled(fixedRate = 1000)
    public void atualizarCronometrosGlobais() {
        try {
            Map<String, Sala> salas = lobbyService.getSalasAtivas();
            if (salas == null) return;

            List<String> paraCerrar = new ArrayList<>();

            for (Sala sala : salas.values()) {
                if (!sala.isJogoIniciado() || sala.isJogoDesarmado()) continue;

                // Proteção contra tempo negativo
                if (sala.getTempoRestante() <= 0) {
                    sala.setTempoRestante(0);
                    // Sala sem tempo: marca para limpeza após broadcast final
                    paraCerrar.add(sala.getCodigo());
                } else {
                    sala.decrementarTempo();
                }

                Map<String, Object> pacote = new HashMap<>();
                pacote.put("sala", sala);
                pacote.put("rankingGeral", RANKING_GERAL);
                messagingTemplate.convertAndSend("/topic/sala/" + sala.getCodigo(), pacote);
            }

            // Remove salas que esgotaram o tempo (sem vencedor)
            for (String cod : paraCerrar) {
                lobbyService.fecharSala(cod);
            }

        } catch (Exception e) {
            System.err.println("❌ Erro no agendador de tempo: " + e.getMessage());
        }
    }

    // ── PROCESSADOR DE AÇÕES ──────────────────────────────────────
    @MessageMapping("/sala/{codigo}/acao")
    public void processarAcao(
            @DestinationVariable String codigo,
            @Payload AcaoPayload payload,
            SimpMessageHeaderAccessor headerAccessor) {

        if (payload == null || codigo == null) return;

        Sala sala = lobbyService.obtenerSala(codigo);
        if (sala == null) {
            messagingTemplate.convertAndSend("/topic/sala/" + codigo,
                    Map.of("erro", "Sala não encontrada ou encerrada."));
            return;
        }

        String idSessao = headerAccessor.getSessionId();
        String tipoAcao = payload.getTipo() != null ? payload.getTipo() : "";

        switch (tipoAcao) {

            case "ENTRAR": {
                String nome = payload.getNomeJogador() != null
                        ? payload.getNomeJogador().trim() : "Agente_Anônimo";
                Jogador novo = new Jogador(idSessao, nome);
                sala.adicionarJogador(idSessao, novo);
                if (sala.getNomeHost() == null) sala.setNomeHost(nome);
                break;
            }

            case "ESCOLHER_FUNCAO": {
                if (payload.getFuncaoDefinida() != null) {
                    boolean ok = sala.selecionarFuncao(idSessao, payload.getFuncaoDefinida());
                    if (!ok) {
                        messagingTemplate.convertAndSend("/topic/sala/" + codigo,
                                Map.of("erro", "Função já ocupada por outro agente."));
                        return;
                    }
                }
                break;
            }

            case "INICIAR_JOGO": {
                Jogador executor = sala.getJogadores().get(idSessao);
                if (executor == null || !executor.getNome().equals(sala.getNomeHost())) {
                    System.out.println("⚠️ Bloqueado: jogador comum tentou iniciar.");
                    return;
                }
                long funcoesPreenchidas = sala.getJogadores().values().stream()
                        .map(Jogador::getFuncao)
                        .filter(f -> f != null && !f.equals("ESPECTADOR"))
                        .distinct().count();
                if (funcoesPreenchidas == 4) {
                    sala.setJogoIniciado(true);
                    sala.registrarInicio();
                }
                break;
            }

            case "CORTAR_FIO": {
                if ("VERMELHO".equals(payload.getFuncaoDefinida())) {
                    sala.setFaseAtualOperador(2);
                    sala.setFalhasGlobais(0);
                } else {
                    sala.aplicarPenalidadeGlobal();
                }
                break;
            }

            case "AVANCAR_PAINEL_C": {
                sala.setFaseAtualOperador(3);
                sala.setFalhasGlobais(0);
                break;
            }

            // Chave Alfa enviada pelo Analista ao Operador
            case "INTERCEPTAR_DADOS": {
                String token = payload.getNomeJogador() != null
                        ? payload.getNomeJogador().trim() : "";
                if ("NET-ALPHA-22".equals(token)) {
                    sala.setDadosInterceptadosFase2(true);
                } else {
                    sala.aplicarPenalidadeGlobal();
                }
                break;
            }

            // Token de Infecção enviado pelo Investigador ao Operador
            case "INTERCEPTAR_TOKEN": {
                String token = payload.getNomeJogador() != null
                        ? payload.getNomeJogador().trim() : "";
                if ("28".equals(token)) {
                    sala.setDadosInterceptadosFase3(true);
                } else {
                    sala.aplicarPenalidadeGlobal();
                }
                break;
            }

            case "TENTAR_DESARME": {
                if (payload.getFuncaoDefinida() == null) break;
                String[] dados = payload.getFuncaoDefinida().split(",");
                if (dados.length == 2
                        && "57-6-18-7-31".equals(dados[0].trim())
                        && "27".equals(dados[1].trim())) {

                    sala.setJogoDesarmado(true);

                    long tempoGasto = sala.calcularTempoGastoSegundos();
                    Map<String, Object> dadosEquipe = new HashMap<>();
                    dadosEquipe.put("sala", sala.getCodigo());
                    dadosEquipe.put("tempoGasto", tempoGasto);
                    dadosEquipe.put("errosCometidos", sala.getFalhasGlobais());
                    RANKING_GERAL.add(dadosEquipe);
                    RANKING_GERAL.sort(Comparator.comparingLong(m -> (Long) m.get("tempoGasto")));

                    // Agenda limpeza da sala após 60s (tempo para o front exibir vitória)
                    new Thread(() -> {
                        try { Thread.sleep(60_000); } catch (InterruptedException ignored) {}
                        lobbyService.fecharSala(sala.getCodigo());
                    }).start();

                } else {
                    sala.aplicarPenalidadeGlobal();
                }
                break;
            }

            case "ERRAR_NOTAS": {
                sala.aplicarPenalidadeGlobal();
                break;
            }
        }

        Map<String, Object> resposta = new HashMap<>();
        resposta.put("sala", sala);
        resposta.put("rankingGeral", RANKING_GERAL);
        messagingTemplate.convertAndSend("/topic/sala/" + codigo, resposta);
    }
}