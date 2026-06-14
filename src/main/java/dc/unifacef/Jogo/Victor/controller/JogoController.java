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

    // ── CRONÔMETRO GLOBAL ─────────────────────────────────────────
    @Scheduled(fixedRate = 1000)
    public void atualizarCronometrosGlobais() {
        try {
            Map<String, Sala> salas = lobbyService.getSalasAtivas();
            if (salas == null) return;

            List<String> paraCerrar = new ArrayList<>();

            for (Sala sala : salas.values()) {
                if (!sala.isJogoIniciado() || sala.isJogoDesarmado()) continue;

                if (sala.getTempoRestante() <= 0) {
                    sala.setTempoRestante(0);
                    sala.setJogoEncerradoPorTempo(true);
                    // Broadcast final antes de fechar
                    Map<String, Object> pacoteFinal = new HashMap<>();
                    pacoteFinal.put("sala", sala);
                    pacoteFinal.put("rankingGeral", RANKING_GERAL);
                    messagingTemplate.convertAndSend("/topic/sala/" + sala.getCodigo(), pacoteFinal);
                    paraCerrar.add(sala.getCodigo());
                } else {
                    sala.decrementarTempo();
                }

                Map<String, Object> pacote = new HashMap<>();
                pacote.put("sala", sala);
                pacote.put("rankingGeral", RANKING_GERAL);
                messagingTemplate.convertAndSend("/topic/sala/" + sala.getCodigo(), pacote);
            }

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
                if (sala.getNomeHost() == null) {
                    sala.setNomeHost(nome);
                    if (payload.getNomeEquipe() != null && !payload.getNomeEquipe().isBlank()) {
                        sala.setNomeEquipe(payload.getNomeEquipe().trim());
                    }
                }
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

            // ── FASE 1: CORTAR FIO ────────────────────────────────
            case "CORTAR_FIO": {
                if ("VERMELHO".equals(payload.getFuncaoDefinida())) {
                    // Fio correto → avança para Fase 2 (Alinhamento Quântico)
                    sala.setFaseAtualOperador(2);
                    sala.setFalhasGlobais(0);
                } else {
                    sala.aplicarPenalidadeGlobal();
                }
                break;
            }

            // ── FASE 2: ALINHAMENTO QUÂNTICO ─────────────────────
            // Operador insere a sequência "6,9,2" fornecida pelo Analista
            case "INSERIR_SEQUENCIA": {
                String sequencia = payload.getFuncaoDefinida() != null
                        ? payload.getFuncaoDefinida().trim() : "";
                if ("6,9,2".equals(sequencia)) {
                    sala.setSequenciaQuadrantesCorreta(true);
                    sala.setFaseAtualOperador(3);
                    sala.setFalhasGlobais(0);
                } else {
                    sala.aplicarPenalidadeGlobal();
                }
                break;
            }

            // ── FASE 3: TOKENS ────────────────────────────────────
            // Chave Alfa enviada pelo Analista
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

            // Token de Infecção enviado pelo Investigador
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

            case "AVANCAR_PAINEL_C": {
                // Só avança se os dois tokens da fase 3 foram aceitos
                if (sala.isDadosInterceptadosFase2() && sala.isDadosInterceptadosFase3()) {
                    sala.setFaseAtualOperador(4);
                    sala.setFalhasGlobais(0);
                }
                break;
            }

            // ── FASE 4: DESARME FINAL ─────────────────────────────
            case "TENTAR_DESARME": {
                if (payload.getFuncaoDefinida() == null) break;
                String[] dados = payload.getFuncaoDefinida().split(",");
                if (dados.length == 3
                        && "57-6-18-7-31".equals(dados[0].trim())
                        && "17".equals(dados[1].trim())
                        && "7".equals(dados[2].trim())) {

                    sala.setJogoDesarmado(true);

                    long tempoGasto = sala.calcularTempoGastoSegundos();
                    Map<String, Object> dadosEquipe = new HashMap<>();
                    dadosEquipe.put("sala", sala.getCodigo());
                    dadosEquipe.put("nomeEquipe", sala.getNomeEquipe() != null ? sala.getNomeEquipe() : sala.getCodigo());
                    dadosEquipe.put("tempoGasto", tempoGasto);
                    dadosEquipe.put("errosCometidos", sala.getFalhasGlobais());
                    RANKING_GERAL.add(dadosEquipe);
                    RANKING_GERAL.sort(Comparator.comparingLong(m -> (Long) m.get("tempoGasto")));

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