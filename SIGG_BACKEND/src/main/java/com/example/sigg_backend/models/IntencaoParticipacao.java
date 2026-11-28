package com.example.sigg_backend.models;

import java.time.LocalDate;
import java.util.Map;
import java.util.Set;

import com.example.sigg_backend.db.dao.IntencaoParticipacaoDAO;
import com.example.sigg_backend.db.util.Conexao;

// RF_F6 Registrar Intenção de Participação em Atividades Pagas.
// A intenção é a mesma "inscrição" que o RF_F7 lê para registrar o pagamento: uma linha de contas_receber
// ligada à atividade e a uma mensalidade do escoteiro (FK obrigatória no modelo), com um pagamento em aberto.
public class IntencaoParticipacao {

    private int codigo; // cr_cod
    private int escCod;
    private AtividadePaga atividade;

    public IntencaoParticipacao() {
    }

    public IntencaoParticipacao(int escCod, AtividadePaga atividade) {
        this.escCod = escCod;
        this.atividade = atividade;
    }

    // Vencimento do pagamento em aberto: o prazo de interesse (as atividades antigas, sem prazo, usam a data).
    public LocalDate vencimento() {
        return atividade.getPrazoInteresse() != null ? atividade.getPrazoInteresse() : atividade.getData();
    }

    public boolean gravar(Conexao conexao, int mensCod) {
        return new IntencaoParticipacaoDAO().gravar(conexao, this, mensCod);
    }

    public boolean jaRegistrada(Conexao conexao) {
        return new IntencaoParticipacaoDAO().existe(conexao, escCod, atividade.getCodigo());
    }

    public Set<Integer> atividadesDoEscoteiro(Conexao conexao, int escCod) {
        return new IntencaoParticipacaoDAO().atividadesDoEscoteiro(conexao, escCod);
    }

    public Map<Integer, Integer> inscritosPorAtividade(Conexao conexao) {
        return new IntencaoParticipacaoDAO().inscritosPorAtividade(conexao);
    }

    public int inscritos(Conexao conexao, int atvCod) {
        return new IntencaoParticipacaoDAO().contarInscritos(conexao, atvCod);
    }

    // contas_receber exige uma mensalidade: usa a mais recente do escoteiro (null quando não há nenhuma).
    public Integer mensalidadeParaVinculo(Conexao conexao) {
        return new IntencaoParticipacaoDAO().ultimaMensalidade(conexao, escCod);
    }

    public int getCodigo() {
        return codigo;
    }

    public void setCodigo(int codigo) {
        this.codigo = codigo;
    }

    public int getEscCod() {
        return escCod;
    }

    public void setEscCod(int escCod) {
        this.escCod = escCod;
    }

    public AtividadePaga getAtividade() {
        return atividade;
    }

    public void setAtividade(AtividadePaga atividade) {
        this.atividade = atividade;
    }
}
