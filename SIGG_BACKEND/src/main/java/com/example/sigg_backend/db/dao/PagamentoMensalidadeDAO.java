package com.example.sigg_backend.db.dao;

import com.example.sigg_backend.db.util.Conexao;
import com.example.sigg_backend.db.util.Datas;
import com.example.sigg_backend.db.util.IDAO;
import com.example.sigg_backend.models.FormaPagamento;
import com.example.sigg_backend.models.PagamentoMensalidade;
import com.example.sigg_backend.models.SituacaoEscoteiro;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class PagamentoMensalidadeDAO implements IDAO<PagamentoMensalidade> {

    private static final int ATIVIDADE_MENSALIDADE = 1;

    @Override
    public boolean gravar(Conexao conexao, PagamentoMensalidade pag) {
        return registrar(conexao, pag);
    }

    @Override
    public boolean alterar(Conexao conexao, PagamentoMensalidade pag) {
        return registrar(conexao, pag);
    }

    @Override
    public boolean apagar(Conexao conexao, PagamentoMensalidade pag) {
        return estornar(conexao, pag);
    }

    @Override
    public List<PagamentoMensalidade> get(Conexao conexao) {
        return List.of(buscar(conexao, ""));
    }

    @Override
    public List<PagamentoMensalidade> get(Conexao conexao, String filtro) {
        return List.of(buscar(conexao, "AND m.mem_nome ILIKE ?", "%" + filtro + "%"));
    }

    @Override
    public PagamentoMensalidade get(Conexao conexao, int crCod) {
        return buscarPorConta(conexao, crCod);
    }

    public FormaPagamento[] listarFormasPagamento(Conexao conexao) {
        List<FormaPagamento> lista = new ArrayList<>();
        ResultSet rs = conexao.consultar("SELECT tp_cod, tp_desc FROM tipo_pagamento ORDER BY tp_cod");
        try {
            while (rs != null && rs.next()) {
                lista.add(new FormaPagamento(rs.getInt("tp_cod"), rs.getString("tp_desc")));
            }
        } catch (SQLException sqlex) {
            lista.clear();
        }
        return lista.toArray(new FormaPagamento[0]);
    }

    public BigDecimal calcularCreditoDisponivel(Conexao conexao, int escCod) {
        BigDecimal gerado = BigDecimal.ZERO;
        for (PagamentoMensalidade m : listarPorEscoteiro(conexao, escCod)) {
            gerado = gerado.add(m.creditoGerado());
        }

        BigDecimal utilizado = BigDecimal.ZERO;
        String sql = "SELECT COALESCE(SUM(p.pg_valor_pago), 0) AS usado"
                + " FROM pagamento p"
                + " JOIN contas_receber cr ON cr.cr_cod = p.contas_receber_cr_cod"
                + " JOIN mensalidade me ON me.mens_cod = cr.mensalidade_mens_cod"
                + " WHERE me.escoteiro_esc_cod = ?"
                + " AND cr.atividade_especial_atv_cod = " + ATIVIDADE_MENSALIDADE
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

    public List<SituacaoEscoteiro> listarSituacoes(Conexao conexao, Integer troCod) {
        List<Object> valores = new ArrayList<>();
        String filtro = "";
        if (troCod != null) {
            filtro = " AND t.tro_cod = ?";
            valores.add(troCod);
        }
        String sql = "SELECT e.esc_cod, m.mem_cod, m.mem_nome, m.mem_cpf, t.tro_nome, p.pat_nome,"
                + " COALESCE(e.esc_sit_fin, 0) AS sit_fin, COALESCE(ab.abertas, 0) AS abertas,"
                + " COALESCE(ab.vencidas, 0) AS vencidas"
                + " FROM escoteiro e JOIN membro m ON m.mem_cod = e.mem_cod"
                + " JOIN patrulha p ON p.pat_cod = e.patrulha_pat_cod2"
                + " JOIN tropa t ON t.tro_cod = p.tropa_tro_cod"
                + " LEFT JOIN (SELECT x.esc_cod, COUNT(*) AS abertas,"
                + "      COUNT(*) FILTER (WHERE x.vencimento < CURRENT_DATE) AS vencidas"
                + "   FROM (SELECT me.escoteiro_esc_cod AS esc_cod, cr.cr_cod, cr.cr_valor,"
                + "           COALESCE(SUM(pg.pg_valor_pago), 0) AS pago, MIN(pg.pg_dt_vencto) AS vencimento"
                + "         FROM mensalidade me JOIN contas_receber cr ON cr.mensalidade_mens_cod = me.mens_cod"
                + "           AND cr.atividade_especial_atv_cod = " + ATIVIDADE_MENSALIDADE
                + "         LEFT JOIN pagamento pg ON pg.contas_receber_cr_cod = cr.cr_cod"
                + "         GROUP BY me.escoteiro_esc_cod, cr.cr_cod, cr.cr_valor) x"
                + "   WHERE x.pago < x.cr_valor GROUP BY x.esc_cod) ab ON ab.esc_cod = e.esc_cod"
                + " WHERE m.mem_dt_fim IS NULL" + filtro
                + " ORDER BY (CASE WHEN COALESCE(e.esc_sit_fin, 0) = 1 THEN 0"
                + "   WHEN COALESCE(ab.vencidas, 0) > 0 THEN 1 ELSE 2 END), m.mem_nome";
        List<SituacaoEscoteiro> situacoes = new ArrayList<>();
        ResultSet rs = conexao.consultar(sql, valores.toArray());
        try {
            while (rs != null && rs.next()) {
                SituacaoEscoteiro situacao = new SituacaoEscoteiro();
                situacao.setEsc_cod(rs.getInt("esc_cod"));
                situacao.setMem_cod(rs.getInt("mem_cod"));
                situacao.setNome(rs.getString("mem_nome"));
                situacao.setCpf(rs.getString("mem_cpf"));
                situacao.setTropa(rs.getString("tro_nome"));
                situacao.setPatrulha(rs.getString("pat_nome"));
                situacao.setMensalidades_abertas(rs.getInt("abertas"));
                situacao.setSituacao(SituacaoEscoteiro.calcular(rs.getInt("sit_fin"), rs.getInt("vencidas")));
                situacoes.add(situacao);
            }
        } catch (SQLException sqlex) {
            situacoes.clear();
        }
        return situacoes;
    }

    public PagamentoMensalidade[] listarPorEscoteiro(Conexao conexao, int escCod) {
        return buscar(conexao, "AND me.escoteiro_esc_cod = ?", escCod);
    }

    public PagamentoMensalidade buscarPorConta(Conexao conexao, int crCod) {
        PagamentoMensalidade[] lista = buscar(conexao, "AND cr.cr_cod = ?", crCod);
        return lista.length == 0 ? null : lista[0];
    }

    public PagamentoMensalidade[] listarLancamentosPorConta(Conexao conexao, int crCod) {
        String sql = "SELECT p.pg_cod, p.pg_dt_pagto, p.pg_valor_pago, p.pg_desc,"
                + " p.tipo_pagamento_tp_cod, tp.tp_desc"
                + " FROM pagamento p"
                + " LEFT JOIN tipo_pagamento tp ON tp.tp_cod = p.tipo_pagamento_tp_cod"
                + " WHERE p.contas_receber_cr_cod = ?"
                + "   AND p.pg_dt_pagto IS NOT NULL"
                + "   AND p.pg_valor_pago > 0"
                + " ORDER BY p.pg_cod DESC";

        List<PagamentoMensalidade> lista = new ArrayList<>();
        ResultSet rs = conexao.consultar(sql, crCod);
        try {
            boolean primeiroDaLista = true;
            while (rs != null && rs.next()) {
                PagamentoMensalidade item = new PagamentoMensalidade();
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
        return lista.toArray(new PagamentoMensalidade[0]);
    }

    public boolean registrar(Conexao conexao, PagamentoMensalidade pag) {
        LocalDate dtPagto = pag.getDataPagamento() != null ? pag.getDataPagamento() : LocalDate.now();
        BigDecimal recebido = pag.getValorRecebido() != null ? pag.getValorRecebido() : BigDecimal.ZERO;
        BigDecimal credito = pag.getCreditoUtilizado() != null ? pag.getCreditoUtilizado() : BigDecimal.ZERO;
        BigDecimal totalAplicado = recebido.add(credito).setScale(2, RoundingMode.HALF_UP);

        String descParcela = limitar((pag.getFormaPagamentoCod() == 4 ? "Apadrinhamento " : "Mensalidade ") + pag.getReferencia());

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
                            dtPagto, recebido, pag.getFormaPagamentoCod(), descParcela, pgCodAberto
                    );
                } else {
                    int ultimoPg = conexao.getMaxPK("pagamento", "pg_cod");
                    if (ultimoPg < 0) return false;
                    pag.setId(ultimoPg + 1);
                    LocalDate vencto = pag.getDataVencimento() != null ? pag.getDataVencimento() : dtPagto;
                    gravouBaixa = conexao.manipular(
                            "INSERT INTO pagamento (pg_cod, pg_dt_pagto, pg_dt_vencto, pg_desc, tipo_pagamento_tp_cod,"
                                    + " contas_receber_cr_cod, pg_valor_pago) VALUES (?, ?, ?, ?, ?, ?, ?)",
                            pag.getId(), dtPagto, vencto, descParcela, pag.getFormaPagamentoCod(), pag.getCrCod(), recebido
                    );
                }
            } catch (SQLException sqlex) {
                return false;
            }
            if (!gravouBaixa) return false;
        }

        if (credito.compareTo(BigDecimal.ZERO) > 0) {
            String descCredito = limitar("Abatimento Credito " + pag.getReferencia());
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
                        ultimoPg + 1, vencto, limitar("Saldo Mensalidade " + pag.getReferencia()),
                        pag.getFormaPagamentoCod(), pag.getCrCod()
                );
            }
        }

        return true;
    }

    public boolean estornar(Conexao conexao, PagamentoMensalidade pag) {
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
                                maxPg + 1, vencto, limitar("Saldo Mensalidade " + pag.getReferencia()),
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
                        limitar("Mensalidade " + pag.getReferencia()), primeiroPg
                );
            }
        } catch (SQLException sqlex) {
            return false;
        }
    }

    public boolean possuiMensalidadeVencidaEmAberto(Conexao conexao, int escCod) {
        ResultSet rs = conexao.consultar(
                "SELECT 1 FROM mensalidade me"
                        + " JOIN contas_receber cr ON cr.mensalidade_mens_cod = me.mens_cod"
                        + " AND cr.atividade_especial_atv_cod = " + ATIVIDADE_MENSALIDADE
                        + " LEFT JOIN pagamento p ON p.contas_receber_cr_cod = cr.cr_cod"
                        + " WHERE me.escoteiro_esc_cod = ?"
                        + " GROUP BY me.mens_cod, cr.cr_cod"
                        + " HAVING COALESCE(SUM(p.pg_valor_pago), 0) < cr.cr_valor"
                        + " AND MIN(p.pg_dt_vencto) < CURRENT_DATE",
                escCod);
        try {
            return rs != null && rs.next();
        } catch (SQLException sqlex) {
            return false;
        }
    }

    public String buscarMensalidadeAnteriorEmAberto(Conexao conexao, PagamentoMensalidade pag) {
        String sql = "SELECT me.mens_mes, me.mens_ano"
                + " FROM mensalidade me"
                + " JOIN contas_receber cr ON cr.mensalidade_mens_cod = me.mens_cod"
                + "   AND cr.atividade_especial_atv_cod = " + ATIVIDADE_MENSALIDADE
                + " JOIN contas_receber cr_atual ON cr_atual.cr_cod = ?"
                + " JOIN mensalidade me_atual ON me_atual.mens_cod = cr_atual.mensalidade_mens_cod"
                + " LEFT JOIN pagamento p ON p.contas_receber_cr_cod = cr.cr_cod"
                + " WHERE me.escoteiro_esc_cod = ?"
                + "   AND cr.cr_cod <> ?"
                + "   AND (me.mens_ano < me_atual.mens_ano"
                + "        OR (me.mens_ano = me_atual.mens_ano AND me.mens_mes < me_atual.mens_mes)"
                + "        OR (me.mens_ano = me_atual.mens_ano AND me.mens_mes = me_atual.mens_mes AND cr.cr_cod < cr_atual.cr_cod))"
                + " GROUP BY me.mens_cod, me.mens_ano, me.mens_mes, cr.cr_cod, cr.cr_valor"
                + " HAVING COALESCE(SUM(p.pg_valor_pago), 0) < cr.cr_valor"
                + " ORDER BY me.mens_ano ASC, me.mens_mes ASC, cr.cr_cod ASC"
                + " LIMIT 1";

        ResultSet rs = conexao.consultar(sql, pag.getCrCod(), pag.getEscCod(), pag.getCrCod());
        try {
            if (rs != null && rs.next()) {
                return String.format("%02d/%04d", rs.getInt("mens_mes"), rs.getInt("mens_ano"));
            }
        } catch (SQLException sqlex) {
            return null;
        }
        return null;
    }

    public String buscarMensalidadePosteriorComPagamento(Conexao conexao, PagamentoMensalidade pag) {
        String sql = "SELECT me.mens_mes, me.mens_ano"
                + " FROM mensalidade me"
                + " JOIN contas_receber cr ON cr.mensalidade_mens_cod = me.mens_cod"
                + "   AND cr.atividade_especial_atv_cod = " + ATIVIDADE_MENSALIDADE
                + " JOIN contas_receber cr_atual ON cr_atual.cr_cod = ?"
                + " JOIN mensalidade me_atual ON me_atual.mens_cod = cr_atual.mensalidade_mens_cod"
                + " LEFT JOIN pagamento p ON p.contas_receber_cr_cod = cr.cr_cod"
                + " WHERE me.escoteiro_esc_cod = ?"
                + "   AND cr.cr_cod <> ?"
                + "   AND (me.mens_ano > me_atual.mens_ano"
                + "        OR (me.mens_ano = me_atual.mens_ano AND me.mens_mes > me_atual.mens_mes)"
                + "        OR (me.mens_ano = me_atual.mens_ano AND me.mens_mes = me_atual.mens_mes AND cr.cr_cod > cr_atual.cr_cod))"
                + " GROUP BY me.mens_cod, me.mens_ano, me.mens_mes, cr.cr_cod"
                + " HAVING COALESCE(SUM(p.pg_valor_pago), 0) > 0"
                + " ORDER BY me.mens_ano DESC, me.mens_mes DESC, cr.cr_cod DESC"
                + " LIMIT 1";

        ResultSet rs = conexao.consultar(sql, pag.getCrCod(), pag.getEscCod(), pag.getCrCod());
        try {
            if (rs != null && rs.next()) {
                return String.format("%02d/%04d", rs.getInt("mens_mes"), rs.getInt("mens_ano"));
            }
        } catch (SQLException sqlex) {
            return null;
        }
        return null;
    }

    public boolean atualizarSituacaoFinanceiraEscoteiro(Conexao conexao, int escCod, int sitFin) {
        return conexao.manipular(
                "UPDATE escoteiro SET esc_sit_fin = ? WHERE esc_cod = ?",
                sitFin, escCod
        );
    }

    private PagamentoMensalidade[] buscar(Conexao conexao, String condicao, Object... valores) {
        String sql = "SELECT cr.cr_cod, cr.cr_valor, me.mens_cod, me.mens_mes, me.mens_ano,"
                + " me.escoteiro_esc_cod, m.mem_nome, m.mem_cpf, e.esc_sit_fin,"
                + " MIN(p.pg_dt_vencto) AS vencimento,"
                + " MAX(p.pg_dt_pagto) AS ultimo_pagamento,"
                + " COALESCE(SUM(p.pg_valor_pago), 0) AS valor_pago,"
                + " MAX(CASE WHEN p.pg_dt_pagto IS NOT NULL THEN p.pg_cod ELSE 0 END) AS pg_cod,"
                + " MAX(CASE WHEN p.pg_dt_pagto IS NOT NULL THEN tp.tp_desc ELSE NULL END) AS forma_pagamento"
                + " FROM mensalidade me"
                + " JOIN contas_receber cr ON cr.mensalidade_mens_cod = me.mens_cod"
                + " AND cr.atividade_especial_atv_cod = " + ATIVIDADE_MENSALIDADE
                + " JOIN escoteiro e ON e.esc_cod = me.escoteiro_esc_cod"
                + " JOIN membro m ON m.mem_cod = e.mem_cod"
                + " LEFT JOIN pagamento p ON p.contas_receber_cr_cod = cr.cr_cod"
                + " LEFT JOIN tipo_pagamento tp ON tp.tp_cod = p.tipo_pagamento_tp_cod"
                + " WHERE 1 = 1 " + condicao
                + " GROUP BY cr.cr_cod, me.mens_cod, e.mem_cod, m.mem_cod"
                + " ORDER BY me.mens_ano ASC, me.mens_mes ASC, cr.cr_cod ASC";

        List<PagamentoMensalidade> lista = new ArrayList<>();
        LocalDate hoje = LocalDate.now();
        ResultSet rs = conexao.consultar(sql, valores);
        try {
            while (rs != null && rs.next()) {
                PagamentoMensalidade item = new PagamentoMensalidade();
                item.setId(rs.getInt("pg_cod"));
                item.setCrCod(rs.getInt("cr_cod"));
                item.setMensCod(rs.getInt("mens_cod"));
                item.setEscCod(rs.getInt("escoteiro_esc_cod"));
                item.setEscoteiroNome(rs.getString("mem_nome"));
                item.setEscoteiroCpf(rs.getString("mem_cpf"));
                item.setReferencia(String.format("%02d/%04d", rs.getInt("mens_mes"), rs.getInt("mens_ano")));
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
        return lista.toArray(new PagamentoMensalidade[0]);
    }

    private String limitar(String texto) {
        if (texto == null) return "Mensalidade";
        return texto.length() > 30 ? texto.substring(0, 30) : texto;
    }
}