package dc.unifacef.Jogo.Victor.model;

public class Jogador {
    private String idSessao;
    private String nome;
    private String funcao; // "OPERADOR", "ESPECIALISTA", "ANALISTA", "INVESTIGADOR"

    public Jogador() {}

    public Jogador(String idSessao, String nome) {
        this.idSessao = idSessao;
        this.nome = nome;
        this.funcao = "ESPECTADOR";
    }

    public String getIdSessao() { return idSessao; }
    public void setIdSessao(String idSessao) { this.idSessao = idSessao; }

    public String getNome() { return nome; }
    public void setNome(String nome) { this.nome = nome; }

    public String getFuncao() { return funcao; }
    public void setFuncao(String funcao) { this.funcao = funcao; }
}