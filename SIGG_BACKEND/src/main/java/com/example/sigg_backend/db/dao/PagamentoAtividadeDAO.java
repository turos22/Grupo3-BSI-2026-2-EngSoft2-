package com.example.sigg_backend.db.dao;

import com.example.sigg_backend.db.util.Conexao;
import com.example.sigg_backend.db.util.Datas;
import com.example.sigg_backend.db.util.IDAO;
import com.example.sigg_backend.models.Escoteiro;
import com.example.sigg_backend.models.FormaPagamento;
import com.example.sigg_backend.models.PagamentoAtividade;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class PagamentoAtividadeDAO implements IDAO<PagamentoAtividade> {

    private static final int ATIVIDADE_MENSALIDADE = 1;

    @Override
    public boolean gravar(Conexao conexao, PagamentoAtividade pag) {
        return registrar(conexao, pag);
    }

    @Override
    public boolean alterar(Conexao conexao, PagamentoAtividade pag) {
        return registrar(conexao, pag);
    }

    @Override
    public boolean apagar(Conexao conexao, PagamentoAtividade pag) {
        return estornar(conexao, pag);
    }

    @Override
    public List<PagamentoAtividade> get(Conexao conexao) {
        return List.of(buscar(conexao, ""));
    }

    @Override
    public List<PagamentoAtividade> get(Conexao conexao, String filtro) {
        return List.of(buscar(conexao, "AND m.mem_nome ILIKE ?", "%" + filtro + "%"));
    }

    @Override
    public PagamentoAtividade get(Conexao conexao, int crCod) {
        return buscarPorConta(conexao, crCod);
    }

    public FormaPagamento[] listarFormasPagamento(Conexao conexao) {
        List<FormaPagamento> lista = new ArrayList<>();
        ResultSet rs = conexao.consultar(
                "SELECT tp_cod, tp_desc FROM tipo_pagamento WHERE tp_cod IN (1, 2, 3) ORDER BY tp_cod");
        try {
            while (rs != null && rs.next()) {
                lista.add(new FormaPagamento(rs.getInt("tp_cod"), rs.getString("tp_desc")));
            }
        } catch (SQLException sqlex) {
            lista.clear();
        }
        return lista.toArray(new FormaPagamento[0]);
    }

    public Escoteiro[] listarEscoteirosComResumo(Conexao conexao) {
        List<Escoteiro> lista = new ArrayList<>();
        String sql = "SELECT " + EscoteiroDAO.COLUNAS + " FROM escoteiro e"
                + " JOIN membro m ON m.mem_cod = e.mem_cod"
                + EscoteiroDAO.JUNCAO_PATRULHA
                + " WHERE m.mem_dt_fim IS NULL"
                + " ORDER BY e.esc_sit_fin ASC, m.mem_nome ASC";
        ResultSet rs = conexao.consultar(sql);
        try {
            EscoteiroDAO escDao = new EscoteiroDAO();
            while (rs != null && rs.next()) {
                Escoteiro esc = new Escoteiro();
                escDao.preencher(esc, rs);
                lista.add(esc);
            }
        } catch (SQLException sqlex) {
            lista.clear();
        }
        return lista.toArray(new Escoteiro[0]);
    }

    public BigDecimal calcularCreditoDisponivel(Conexao conexao, int escCod) {
        BigDecimal gerado = BigDecimal.ZERO;
        for (PagamentoAtividade a : listarPorEscoteiro(conexao, escCod)) {
            gerado = gerado.add(a.creditoGerado());
        }

        BigDecimal utilizado = BigDecimal.ZERO;
        String sql = "SELECT COALESCE(SUM(p.pg_valor_pago), 0) AS usado"
                + " FROM pagamento p"
                + " JOIN contas_receber cr ON cr.cr_cod = p.contas_receber_cr_cod"
                + " JOIN mensalidade me ON me.mens_cod = cr.mensalidade_mens_cod"
                + " WHERE me.escoteiro_esc_cod = ?"
                + " AND cr.atividade_especial_atv_cod <> " + ATIVIDADE_MENSALIDADE
                + " AND p.pg_dt_pagto IS NOT NULL"
                + " AND p.pg_desc LIKE 'Abatimento Credito%'";

        ResultSet rs = conexao.consultar(sql, escCod);
        try {
            if (rs != null && rs.next()) {
                utilizado = BigDecimal.valueOf(rs.getDouble("usado")).setScale(2, RoundingMode.HALF_UP);
            }
        } catch (SQLException sqlex) {
            utilizado = BigDecimal.ZERO;
        }

        BigDecimal disponivel = gerado.subtract(utilizado).setScale(2, RoundingMode.HALF_UP);
        return disponivel.signum() > 0 ? disponivel : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    }

    public PagamentoAtividade[] listarPorEscoteiro(Conexao conexao, int escCod) {
        return buscar(conexao, "AND me.escoteiro_esc_cod = ?", escCod);
    }

    public PagamentoAtividade buscarPorConta(Conexao conexao, int crCod) {
        PagamentoAtividade[] lista = buscar(conexao, "AND cr.cr_cod = ?", crCod);
        return lista.length == 0 ? null : lista[0];
    }

    public PagamentoAtividade[] listarLancamentosPorConta(Conexao conexao, int crCod) {
        String sql = "SELECT p.pg_cod, p.pg_dt_pagto, p.pg_valor_pago, p.pg_desc,"
                + " p.tipo_pagamento_tp_cod, tp.tp_desc"
                + " FROM pagamento p"
                + " LEFT JOIN tipo_pagamento tp ON tp.tp_cod = p.tipo_pagamento_tp_cod"
                + " WHERE p.contas_receber_cr_cod = ?"
                + "   AND p.pg_dt_pagto IS NOT NULL"
                + "   AND p.pg_valor_pago > 0"
                + " ORDER BY p.pg_cod DESC";

        List<PagamentoAtividade> lista = new ArrayList<>();
        ResultSet rs = conexao.consultar(sql, crCod);
        try {
            boolean primeiroDaLista = true;
            while (rs != null && rs.next()) {
                PagamentoAtividade item = new PagamentoAtividade();
                item.setId(rs.getInt("pg_cod"));
                item.setCrCod(crCod);
                item.setDataPagamento(Datas.ler(rs, "pg_dt_pagto"));
                item.setValorPago(BigDecimal.valueOf(rs.getDouble("pg_valor_pago")).setScale(2, RoundingMode.HALF_UP));
                item.setObservacao(rs.getString("pg_desc"));
                item.setFormaPagamentoCod(rs.getInt("tipo_pagamento_tp_cod"));
                item.setFormaPagamentoDesc(rs.getString("tp_desc"));
                item.setPodeEstornar(primeiroDaLista);
                primeiroDaLista = false;
                lista.add(item);
            }
        } catch (SQLException sqlex) {
            lista.clear();
        }
        return lista.toArray(new PagamentoAtividade[0]);
    }

    public boolean registrar(Conexao conexao, PagamentoAtividade pag) {
        LocalDate dtPagto = pag.getDataPagamento() != null ? pag.getDataPagamento() : LocalDate.now();
        BigDecimal recebido = pag.getValorRecebido() != null ? pag.getValorRecebido() : BigDecimal.ZERO;
        BigDecimal credito = pag.getCreditoUtilizado() != null ? pag.getCreditoUtilizado() : BigDecimal.ZERO;
        BigDecimal totalAplicado = recebido.add(credito).setScale(2, RoundingMode.HALF_UP);

        String descBase = limitar("Pgto " + pag.getAtividadeNome());

        if (recebido.compareTo(BigDecimal.ZERO) > 0) {
            boolean gravouBaixa;
            ResultSet rs = conexao.consultar(
                    "SELECT pg_cod FROM pagamento WHERE contas_receber_cr_cod = ? AND pg_dt_pagto IS NULL ORDER BY pg_cod LIMIT 1",
                    pag.getCrCod());
            try {
                if (rs != null && rs.next()) {
                    int pgCodAberto = rs.getInt("pg_cod");
                    pag.setId(pgCodAberto);
                    gravouBaixa = conexao.manipular(
                            "UPDATE pagamento SET pg_dt_pagto = ?, pg_valor_pago = ?, tipo_pagamento_tp_cod = ?, pg_desc = ?"
                                    + " WHERE pg_cod = ?",
                            dtPagto, recebido, pag.getFormaPagamentoCod(), descBase, pgCodAberto
                    );
                } else {
                    int ultimoPg = conexao.getMaxPK("pagamento", "pg_cod");
                    if (ultimoPg < 0) return false;
                    pag.setId(ultimoPg + 1);
                    LocalDate vencto = pag.getDataVencimento() != null ? pag.getDataVencimento() : dtPagto;
                    gravouBaixa = conexao.manipular(
                            "INSERT INTO pagamento (pg_cod, pg_dt_pagto, pg_dt_vencto, pg_desc, tipo_pagamento_tp_cod,"
                                    + " contas_receber_cr_cod, pg_valor_pago) VALUES (?, ?, ?, ?, ?, ?, ?)",
                            pag.getId(), dtPagto, vencto, descBase, pag.getFormaPagamentoCod(), pag.getCrCod(), recebido
                    );
                }
            } catch (SQLException sqlex) {
                return false;
            }
            if (!gravouBaixa) return false;
        }

        if (credito.compareTo(BigDecimal.ZERO) > 0) {
            String descCredito = limitar("Abatimento Credito " + pag.getAtividadeNome());
            if (recebido.compareTo(BigDecimal.ZERO) == 0) {
                ResultSet rs = conexao.consultar(
                        "SELECT pg_cod FROM pagamento WHERE contas_receber_cr_cod = ? AND pg_dt_pagto IS NULL ORDER BY pg_cod LIMIT 1",
                        pag.getCrCod());
                try {
                    if (rs != null && rs.next()) {
                        int pgCodAberto = rs.getInt("pg_cod");
                        pag.setId(pgCodAberto);
                        if (!conexao.manipular(
                                "UPDATE pagamento SET pg_dt_pagto = ?, pg_valor_pago = ?, tipo_pagamento_tp_cod = ?, pg_desc = ?"
                                        + " WHERE pg_cod = ?",
                                dtPagto, credito, pag.getFormaPagamentoCod(), descCredito, pgCodAberto)) {
                            return false;
                        }
                    } else {
                        int ultimoPg = conexao.getMaxPK("pagamento", "pg_cod");
                        if (ultimoPg < 0) return false;
                        pag.setId(ultimoPg + 1);
                        LocalDate vencto = pag.getDataVencimento() != null ? pag.getDataVencimento() : dtPagto;
                        if (!conexao.manipular(
                                "INSERT INTO pagamento (pg_cod, pg_dt_pagto, pg_dt_vencto, pg_desc, tipo_pagamento_tp_cod,"
                                        + " contas_receber_cr_cod, pg_valor_pago) VALUES (?, ?, ?, ?, ?, ?, ?)",
                                pag.getId(), dtPagto, vencto, descCredito, pag.getFormaPagamentoCod(), pag.getCrCod(), credito)) {
                            return false;
                        }
                    }
                } catch (SQLException sqlex) {
                    return false;
                }
            } else {
                int ultimoPg = conexao.getMaxPK("pagamento", "pg_cod");
                if (ultimoPg < 0) return false;
                LocalDate vencto = pag.getDataVencimento() != null ? pag.getDataVencimento() : dtPagto;
                if (!conexao.manipular(
                        "INSERT INTO pagamento (pg_cod, pg_dt_pagto, pg_dt_vencto, pg_desc, tipo_pagamento_tp_cod,"
                                + " contas_receber_cr_cod, pg_valor_pago) VALUES (?, ?, ?, ?, ?, ?, ?)",
                        ultimoPg + 1, dtPagto, vencto, descCredito, pag.getFormaPagamentoCod(), pag.getCrCod(), credito)) {
                    return false;
                }
            }
        }

        BigDecimal diferenca = pag.verificarDivergencia(totalAplicado);
        if (diferenca.compareTo(BigDecimal.ZERO) < 0) {
            int ultimoPg = conexao.getMaxPK("pagamento", "pg_cod");
            if (ultimoPg >= 0) {
                LocalDate vencto = pag.getDataVencimento() != null ? pag.getDataVencimento() : dtPagto;
                conexao.manipular(
                        "INSERT INTO pagamento (pg_cod, pg_dt_pagto, pg_dt_vencto, pg_desc, tipo_pagamento_tp_cod,"
                                + " contas_receber_cr_cod, pg_valor_pago) VALUES (?, NULL, ?, ?, ?, ?, 0)",
                        ultimoPg + 1, vencto, limitar("Saldo " + pag.getAtividadeNome()),
                        pag.getFormaPagamentoCod(), pag.getCrCod()
                );
            }
        }

        return true;
    }

    public boolean estornar(Conexao conexao, PagamentoAtividade pag) {
        try {
            int primeiroPg = -1;
            ResultSet rsMin = conexao.consultar(
                    "SELECT MIN(pg_cod) AS primeiro_pg FROM pagamento WHERE contas_receber_cr_cod = ?",
                    pag.getCrCod());
            if (rsMin != null && rsMin.next()) {
                primeiroPg = rsMin.getInt("primeiro_pg");
            }
            if (primeiroPg <= 0) return false;

            int ultimoPagoCod = -1;
            BigDecimal valorUltimoPago = BigDecimal.ZERO;
            LocalDate vencto = pag.getDataVencimento() != null ? pag.getDataVencimento() : LocalDate.now();
            int tpCod = 1;

            ResultSet rsUlt = conexao.consultar(
                    "SELECT pg_cod, pg_valor_pago, pg_dt_vencto, tipo_pagamento_tp_cod"
                            + " FROM pagamento"
                            + " WHERE contas_receber_cr_cod = ? AND pg_dt_pagto IS NOT NULL AND pg_valor_pago > 0"
                            + " ORDER BY pg_cod DESC LIMIT 1",
                    pag.getCrCod());
            if (rsUlt != null && rsUlt.next()) {
                ultimoPagoCod = rsUlt.getInt("pg_cod");
                valorUltimoPago = BigDecimal.valueOf(rsUlt.getDouble("pg_valor_pago")).setScale(2, RoundingMode.HALF_UP);
                LocalDate v = Datas.ler(rsUlt, "pg_dt_vencto");
                if (v != null) vencto = v;
                tpCod = rsUlt.getInt("tipo_pagamento_tp_cod");
            }

            if (ultimoPagoCod <= 0) return false;
            if (pag.getId() > 0 && pag.getId() != ultimoPagoCod) {
                return false;
            }
            pag.setValorEstornado(valorUltimoPago);

            if (ultimoPagoCod > primeiroPg) {
                boolean deletou = conexao.manipular("DELETE FROM pagamento WHERE pg_cod = ?", ultimoPagoCod);
                if (!deletou) return false;

                ResultSet rsAberto = conexao.consultar(
                        "SELECT pg_cod FROM pagamento WHERE contas_receber_cr_cod = ? AND pg_dt_pagto IS NULL LIMIT 1",
                        pag.getCrCod());
                boolean temAberto = rsAberto != null && rsAberto.next();
                if (!temAberto) {
                    int maxPg = conexao.getMaxPK("pagamento", "pg_cod");
                    if (maxPg >= 0) {
                        conexao.manipular(
                                "INSERT INTO pagamento (pg_cod, pg_dt_pagto, pg_dt_vencto, pg_desc, tipo_pagamento_tp_cod,"
                                        + " contas_receber_cr_cod, pg_valor_pago) VALUES (?, NULL, ?, ?, ?, ?, 0)",
                                maxPg + 1, vencto, limitar("Saldo " + pag.getAtividadeNome()),
                                tpCod, pag.getCrCod()
                        );
                    }
                }
                return true;
            } else {
                conexao.manipular(
                        "DELETE FROM pagamento WHERE contas_receber_cr_cod = ? AND pg_cod <> ?",
                        pag.getCrCod(), primeiroPg
                );
                return conexao.manipular(
                        "UPDATE pagamento SET pg_dt_pagto = NULL, pg_valor_pago = 0, pg_desc = ? WHERE pg_cod = ?",
                        limitar("Pgto " + pag.getAtividadeNome()), primeiroPg
                );
            }
        } catch (SQLException sqlex) {
            return false;
        }
    }

    private PagamentoAtividade[] buscar(Conexao conexao, String condicao, Object... valores) {
        String sql = "SELECT cr.cr_cod, cr.cr_valor, ae.atv_cod, ae.atv_nome, ae.atv_desc, ae.atv_data,"
                + " me.escoteiro_esc_cod, m.mem_nome, m.mem_cpf, e.esc_sit_fin,"
                + " MIN(p.pg_dt_vencto) AS vencimento,"
                + " MAX(p.pg_dt_pagto) AS ultimo_pagamento,"
                + " COALESCE(SUM(p.pg_valor_pago), 0) AS valor_pago,"
                + " MAX(CASE WHEN p.pg_dt_pagto IS NOT NULL THEN p.pg_cod ELSE 0 END) AS pg_cod,"
                + " MAX(CASE WHEN p.pg_dt_pagto IS NOT NULL THEN tp.tp_desc ELSE NULL END) AS forma_pagamento"
                + " FROM contas_receber cr"
                + " JOIN atividade_especial ae ON ae.atv_cod = cr.atividade_especial_atv_cod"
                + " JOIN mensalidade me ON me.mens_cod = cr.mensalidade_mens_cod"
                + " JOIN escoteiro e ON e.esc_cod = me.escoteiro_esc_cod"
                + " JOIN membro m ON m.mem_cod = e.mem_cod"
                + " LEFT JOIN pagamento p ON p.contas_receber_cr_cod = cr.cr_cod"
                + " LEFT JOIN tipo_pagamento tp ON tp.tp_cod = p.tipo_pagamento_tp_cod"
                + " WHERE cr.atividade_especial_atv_cod <> " + ATIVIDADE_MENSALIDADE + " " + condicao
                + " GROUP BY cr.cr_cod, ae.atv_cod, me.mens_cod, e.mem_cod, m.mem_cod"
                + " ORDER BY ae.atv_data DESC, cr.cr_cod DESC";

        List<PagamentoAtividade> lista = new ArrayList<>();
        LocalDate hoje = LocalDate.now();
        ResultSet rs = conexao.consultar(sql, valores);
        try {
            while (rs != null && rs.next()) {
                PagamentoAtividade item = new PagamentoAtividade();
                item.setId(rs.getInt("pg_cod"));
                item.setCrCod(rs.getInt("cr_cod"));
                item.setAtvCod(rs.getInt("atv_cod"));
                item.setEscCod(rs.getInt("escoteiro_esc_cod"));
                item.setEscoteiroNome(rs.getString("mem_nome"));
                item.setEscoteiroCpf(rs.getString("mem_cpf"));
                item.setAtividadeNome(rs.getString("atv_nome"));
                item.setAtividadeDesc(rs.getString("atv_desc"));
                item.setDataAtividade(Datas.ler(rs, "atv_data"));
                item.setDataVencimento(Datas.ler(rs, "vencimento"));
                item.setDataPagamento(Datas.ler(rs, "ultimo_pagamento"));
                item.setValorDevido(BigDecimal.valueOf(rs.getDouble("cr_valor")).setScale(2, RoundingMode.HALF_UP));
                item.setValorPago(BigDecimal.valueOf(rs.getDouble("valor_pago")).setScale(2, RoundingMode.HALF_UP));
                item.setFormaPagamentoDesc(rs.getString("forma_pagamento"));
                item.setInadimplente(rs.getInt("esc_sit_fin") == 1);

                BigDecimal saldo = item.saldoRestante();
                if (saldo.compareTo(BigDecimal.ZERO) == 0) {
                    item.setSituacao("PAGA");
                } else if (item.getDataVencimento() != null && item.getDataVencimento().isBefore(hoje)) {
                    item.setSituacao("ATRASADA");
                } else if (item.getValorPago().compareTo(BigDecimal.ZERO) > 0) {
                    item.setSituacao("PARCIAL");
                } else {
                    item.setSituacao("ABERTA");
                }
                lista.add(item);
            }
        } catch (SQLException sqlex) {
            lista.clear();
        }
        return lista.toArray(new PagamentoAtividade[0]);
    }

    private String limitar(String texto) {
        if (texto == null) return "Atividade";
        return texto.length() > 30 ? texto.substring(0, 30) : texto;
    }
}