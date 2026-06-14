package dc.unifacef.Jogo.Victor.dto;

public class AcaoPayload {
    private String tipo;
    private String nomeJogador;
    private String funcaoDefinida;

    public String getTipo() { return tipo; }
    public void setTipo(String tipo) { this.tipo = tipo; }

    public String getNomeJogador() { return nomeJogador; }
    public void setNomeJogador(String nomeJogador) { this.nomeJogador = nomeJogador; }

    public String getFuncaoDefinida() { return funcaoDefinida; }
    public void setFuncaoDefinida(String funcaoDefinida) { this.funcaoDefinida = funcaoDefinida; }
}