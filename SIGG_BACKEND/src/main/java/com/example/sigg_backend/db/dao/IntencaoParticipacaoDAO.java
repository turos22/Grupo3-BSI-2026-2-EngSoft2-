package com.example.sigg_backend.db.dao;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.example.sigg_backend.db.util.Conexao;
import com.example.sigg_backend.models.IntencaoParticipacao;

// Intenções de participação (RF_F6) = contas_receber de atividade paga + pagamento em aberto, no mesmo formato
// das inscrições do dump, para o RF_F7 (PagamentoAtividadeDAO) listar e quitar sem mudança.
// atv_cod 1 é o registro técnico das mensalidades e nunca conta como inscrição.
public class IntencaoParticipacaoDAO {

    private static final int ATIVIDADE_MENSALIDADE = 1;
    // tipo_pagamento é obrigatório até no pagamento em aberto; o RF_F7 troca pela forma real ao quitar.
    private static final int FORMA_PAGAMENTO_PROVISORIA = 1;

    public boolean gravar(Conexao conexao, IntencaoParticipacao intencao, int mensCod) {
        // O dump não gera chaves sozinho. MAX+1 pode repetir com dois registros simultâneos.
        int ultimaConta = conexao.getMaxPK("contas_receber", "cr_cod");
        int ultimoPagamento = conexao.getMaxPK("pagamento", "pg_cod");
        if (ultimaConta < 0 || ultimoPagamento < 0)
            return false;
        intencao.setCodigo(ultimaConta + 1);
        String nome = intencao.getAtividade().getNome();
        return conexao.manipular(
                "INSERT INTO contas_receber (cr_cod, cr_valor, cr_desc, atividade_especial_atv_cod, mensalidade_mens_cod)"
                        + " VALUES (?, ?, ?, ?, ?)",
                intencao.getCodigo(), intencao.getAtividade().getValor(), limitar(nome),
                intencao.getAtividade().getCodigo(), mensCod)
                && conexao.manipular(
                        "INSERT INTO pagamento (pg_cod, pg_dt_pagto, pg_dt_vencto, pg_desc, tipo_pagamento_tp_cod,"
                                + " contas_receber_cr_cod, pg_valor_pago) VALUES (?, NULL, ?, ?, ?, ?, 0)",
                        ultimoPagamento + 1, intencao.vencimento(), limitar("Pgto " + nome),
                        FORMA_PAGAMENTO_PROVISORIA, intencao.getCodigo());
    }

    public boolean existe(Conexao conexao, int escCod, int atvCod) {
        ResultSet rs = conexao.consultar(
                "SELECT 1 FROM contas_receber cr JOIN mensalidade me ON me.mens_cod = cr.mensalidade_mens_cod"
                        + " WHERE me.escoteiro_esc_cod = ? AND cr.atividade_especial_atv_cod = ?",
                escCod, atvCod);
        try {
            return rs != null && rs.next();
        } catch (SQLException sqlex) {
            return false;
        }
    }

    public Set<Integer> atividadesDoEscoteiro(Conexao conexao, int escCod) {
        Set<Integer> atividades = new HashSet<>();
        ResultSet rs = conexao.consultar(
                "SELECT DISTINCT cr.atividade_especial_atv_cod FROM contas_receber cr"
                        + " JOIN mensalidade me ON me.mens_cod = cr.mensalidade_mens_cod"
                        + " WHERE me.escoteiro_esc_cod = ? AND cr.atividade_especial_atv_cod <> " + ATIVIDADE_MENSALIDADE,
                escCod);
        try {
            while (rs != null && rs.next())
                atividades.add(rs.getInt(1));
        } catch (SQLException sqlex) {
            atividades.clear();
        }
        return atividades;
    }

    public Map<Integer, Integer> inscritosPorAtividade(Conexao conexao) {
        Map<Integer, Integer> inscritos = new HashMap<>();
        ResultSet rs = conexao.consultar(
                "SELECT atividade_especial_atv_cod, COUNT(*) FROM contas_receber"
                        + " WHERE atividade_especial_atv_cod <> " + ATIVIDADE_MENSALIDADE
                        + " GROUP BY atividade_especial_atv_cod");
        try {
            while (rs != null && rs.next())
                inscritos.put(rs.getInt(1), rs.getInt(2));
        } catch (SQLException sqlex) {
            inscritos.clear();
        }
        return inscritos;
    }

    public int contarInscritos(Conexao conexao, int atvCod) {
        ResultSet rs = conexao.consultar(
                "SELECT COUNT(*) FROM contas_receber WHERE atividade_especial_atv_cod = ?", atvCod);
        try {
            return rs != null && rs.next() ? rs.getInt(1) : 0;
        } catch (SQLException sqlex) {
            return 0;
        }
    }

    public Integer ultimaMensalidade(Conexao conexao, int escCod) {
        ResultSet rs = conexao.consultar(
                "SELECT mens_cod FROM mensalidade WHERE escoteiro_esc_cod = ?"
                        + " ORDER BY mens_ano DESC, mens_mes DESC, mens_cod DESC LIMIT 1",
                escCod);
        try {
            return rs != null && rs.next() ? rs.getInt(1) : null;
        } catch (SQLException sqlex) {
            return null;
        }
    }

    private String limitar(String texto) {
        return texto.length() > 30 ? texto.substring(0, 30) : texto;
    }
}
