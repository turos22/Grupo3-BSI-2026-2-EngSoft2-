package com.example.sigg_backend.models;

// RF_F6, passo 2: uma atividade paga aberta, vista por um escoteiro (se já manifestou interesse e quantas vagas restam).
public class OpcaoIntencao {

    private AtividadePaga atividade;
    private int inscritos;
    private Integer vagasRestantes; // null = atividade sem limite de vagas (as antigas)
    private boolean registrada;

    public OpcaoIntencao() {
    }

    public OpcaoIntencao(AtividadePaga atividade, int inscritos, boolean registrada) {
        this.atividade = atividade;
        this.inscritos = inscritos;
        this.registrada = registrada;
        this.vagasRestantes = atividade.getVagas() == null ? null : Math.max(0, atividade.getVagas() - inscritos);
    }

    public AtividadePaga getAtividade() {
        return atividade;
    }

    public int getInscritos() {
        return inscritos;
    }

    public Integer getVagasRestantes() {
        return vagasRestantes;
    }

    public boolean isRegistrada() {
        return registrada;
    }
}
