package dc.unifacef.Jogo.Victor.model;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class Sala {
    private String codigo;
    private Map<String, Jogador> jogadores = new ConcurrentHashMap<>();
    private boolean jogoIniciado = false;
    private String nomeHost = null;
    private long tempoInicialMilis;

    private int tempoRestante = 25 * 60;
    private int falhasGlobais = 0;
    private boolean lockdownAtivo = false;
    private int tempoLockdown = 0;

    private int faseAtualOperador = 1;
    private boolean dadosInterceptadosFase2 = false;
    private boolean dadosInterceptadosFase3 = false;
    private boolean jogoDesarmado = false;

    public Sala(String codigo) {
        this.codigo = codigo;
    }

    public void adicionarJogador(String idSessao, Jogador jogador) {
        jogadores.put(idSessao, jogador);
    }

    public void removerJogador(String idSessao) {
        jogadores.remove(idSessao);
    }

    public boolean selecionarFuncao(String idSessao, String novaFuncao) {
        // ANALISTA pode ser escolhido por múltiplos (apenas função que permite duplicata)
        if (!novaFuncao.equals("ANALISTA")) {
            for (Jogador j : jogadores.values()) {
                if (novaFuncao.equals(j.getFuncao())) return false;
            }
        }
        Jogador jogador = jogadores.get(idSessao);
        if (jogador != null) {
            jogador.setFuncao(novaFuncao);
            return true;
        }
        return false;
    }

    // ── PENALIDADE ────────────────────────────────────────────────
    public void aplicarPenalidadeGlobal() {
        // Proteção: não penaliza tempo abaixo de 0
        this.tempoRestante = Math.max(0, this.tempoRestante - 120);
        this.falhasGlobais++;
        if (this.falhasGlobais >= 3) {
            this.lockdownAtivo = true;
            this.tempoLockdown = 30;
            this.falhasGlobais = 0; // Zera contador após ativar lockdown
        }
    }

    // ── TICK DO CRONÔMETRO ────────────────────────────────────────
    public void decrementarTempo() {
        if (lockdownAtivo) {
            tempoLockdown--;
            if (tempoLockdown <= 0) {
                lockdownAtivo = false;
                tempoLockdown = 0;
            }
        } else {
            tempoRestante = Math.max(0, tempoRestante - 1);
        }
    }

    // ── GETTERS / SETTERS ─────────────────────────────────────────
    public String getCodigo() { return codigo; }
    public Map<String, Jogador> getJogadores() { return jogadores; }
    public boolean isJogoIniciado() { return jogoIniciado; }
    public void setJogoIniciado(boolean v) { this.jogoIniciado = v; }
    public int getTempoRestante() { return tempoRestante; }
    public void setTempoRestante(int v) { this.tempoRestante = v; }
    public int getFalhasGlobais() { return falhasGlobais; }
    public void setFalhasGlobais(int v) { this.falhasGlobais = v; }
    public boolean isLockdownAtivo() { return lockdownAtivo; }
    public void setLockdownAtivo(boolean v) { this.lockdownAtivo = v; }
    public int getTempoLockdown() { return tempoLockdown; }
    public int getFaseAtualOperador() { return faseAtualOperador; }
    public void setFaseAtualOperador(int v) { this.faseAtualOperador = v; }
    public boolean isDadosInterceptadosFase2() { return dadosInterceptadosFase2; }
    public void setDadosInterceptadosFase2(boolean v) { this.dadosInterceptadosFase2 = v; }
    public boolean isDadosInterceptadosFase3() { return dadosInterceptadosFase3; }
    public void setDadosInterceptadosFase3(boolean v) { this.dadosInterceptadosFase3 = v; }
    public boolean isJogoDesarmado() { return jogoDesarmado; }
    public void setJogoDesarmado(boolean v) { this.jogoDesarmado = v; }
    public String getNomeHost() { return nomeHost; }
    public void setNomeHost(String v) { this.nomeHost = v; }

    public void registrarInicio() {
        this.tempoInicialMilis = System.currentTimeMillis();
    }

    public long calcularTempoGastoSegundos() {
        return (System.currentTimeMillis() - this.tempoInicialMilis) / 1000;
    }
}