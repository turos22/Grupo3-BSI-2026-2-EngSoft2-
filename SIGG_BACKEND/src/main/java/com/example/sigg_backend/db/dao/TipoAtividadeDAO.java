package com.example.sigg_backend.db.dao;

import com.example.sigg_backend.db.util.Conexao;
import com.example.sigg_backend.db.util.IDAO;
import com.example.sigg_backend.models.TipoAtividade;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class TipoAtividadeDAO implements IDAO<TipoAtividade> {

    private void garantirEstrutura(Conexao conexao) {
        conexao.manipular(
                "CREATE TABLE IF NOT EXISTS tipo_atividade ("
                        + " ta_cod INTEGER PRIMARY KEY,"
                        + " ta_desc VARCHAR(100) NOT NULL"
                        + ")"
        );
        conexao.manipular(
                "ALTER TABLE atividade_especial ADD COLUMN IF NOT EXISTS tipo_atividade_ta_cod INTEGER"
        );
        ResultSet rs = conexao.consultar("SELECT COUNT(*) AS total FROM tipo_atividade");
        try {
            if (rs != null && rs.next() && rs.getInt("total") == 0) {
                conexao.manipular("INSERT INTO tipo_atividade (ta_cod, ta_desc) VALUES (1, 'Acampamento') ON CONFLICT (ta_cod) DO NOTHING");
                conexao.manipular("INSERT INTO tipo_atividade (ta_cod, ta_desc) VALUES (2, 'Excursão / Passeio') ON CONFLICT (ta_cod) DO NOTHING");
                conexao.manipular("INSERT INTO tipo_atividade (ta_cod, ta_desc) VALUES (3, 'Jamboree / Evento Regional') ON CONFLICT (ta_cod) DO NOTHING");
            }
        } catch (SQLException ignored) {
        }
    }

    @Override
    public boolean gravar(Conexao conexao, TipoAtividade tipo) {
        garantirEstrutura(conexao);
        int ultimo = conexao.getMaxPK("tipo_atividade", "ta_cod");
        if (ultimo < 0)
            ultimo = 0;
        tipo.setId(ultimo + 1);
        return conexao.manipular(
                "INSERT INTO tipo_atividade (ta_cod, ta_desc) VALUES (?, ?)",
                tipo.getId(), tipo.getDescricao().trim()
        );
    }

    @Override
    public boolean alterar(Conexao conexao, TipoAtividade tipo) {
        garantirEstrutura(conexao);
        return conexao.manipular(
                "UPDATE tipo_atividade SET ta_desc = ? WHERE ta_cod = ?",
                tipo.getDescricao().trim(), tipo.getId()
        );
    }

    @Override
    public boolean apagar(Conexao conexao, TipoAtividade tipo) {
        garantirEstrutura(conexao);
        return conexao.manipular(
                "DELETE FROM tipo_atividade WHERE ta_cod = ?",
                tipo.getId()
        );
    }

    @Override
    public List<TipoAtividade> get(Conexao conexao) {
        return buscar(conexao, "");
    }

    @Override
    public List<TipoAtividade> get(Conexao conexao, String filtro) {
        if (filtro == null || filtro.isBlank())
            return get(conexao);
        return buscar(conexao, "WHERE ta_desc ILIKE ?", "%" + filtro.trim() + "%");
    }

    @Override
    public TipoAtividade get(Conexao conexao, int id) {
        List<TipoAtividade> lista = buscar(conexao, "WHERE ta_cod = ?", id);
        return lista.isEmpty() ? null : lista.get(0);
    }

    public boolean possuiVinculos(Conexao conexao, int id) {
        garantirEstrutura(conexao);
        ResultSet rs = conexao.consultar(
                "SELECT 1 FROM atividade_especial WHERE tipo_atividade_ta_cod = ? LIMIT 1", id
        );
        try {
            return rs != null && rs.next();
        } catch (SQLException sqlex) {
            return false;
        }
    }

    public boolean existeDescricao(Conexao conexao, String descricao, int ignorarId) {
        garantirEstrutura(conexao);
        ResultSet rs = conexao.consultar(
                "SELECT 1 FROM tipo_atividade WHERE LOWER(TRIM(ta_desc)) = LOWER(TRIM(?)) AND ta_cod <> ? LIMIT 1",
                descricao, ignorarId
        );
        try {
            return rs != null && rs.next();
        } catch (SQLException sqlex) {
            return false;
        }
    }

    private TipoAtividade preencher(ResultSet rs) throws SQLException {
        return new TipoAtividade(rs.getInt("ta_cod"), rs.getString("ta_desc"));
    }

    private List<TipoAtividade> buscar(Conexao conexao, String condicao, Object... valores) {
        garantirEstrutura(conexao);
        String sql = "SELECT ta_cod, ta_desc FROM tipo_atividade " + condicao + " ORDER BY ta_cod ASC";
        ResultSet rs = conexao.consultar(sql, valores);
        List<TipoAtividade> lista = new ArrayList<>();
        try {
            while (rs != null && rs.next()) {
                lista.add(preencher(rs));
            }
        } catch (SQLException sqlex) {
            lista.clear();
        }
        return lista;
    }
}