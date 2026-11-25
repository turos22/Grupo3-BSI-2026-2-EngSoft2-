package com.example.sigg_backend.db.dao;

import com.example.sigg_backend.db.util.Conexao;
import com.example.sigg_backend.db.util.IDAO;
import com.example.sigg_backend.models.TipoAtividade;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class TipoAtividadeDAO implements IDAO<TipoAtividade> {

    private int proximoCodigo(Conexao conexao) {
        ResultSet rs = conexao.consultar("SELECT COALESCE(MAX(ta_cod), 0) + 1 AS proximo FROM tipo_atividade");
        try {
            if (rs != null && rs.next()) {
                return rs.getInt("proximo");
            }
        } catch (SQLException sqlex) {
            return 1;
        }
        return 1;
    }

    @Override
    public boolean gravar(Conexao conexao, TipoAtividade tipo) {
        int novoCod = proximoCodigo(conexao);
        tipo.setId(novoCod);
        return conexao.manipular(
                "INSERT INTO tipo_atividade (ta_cod, ta_desc) VALUES (?, ?)",
                tipo.getId(),
                tipo.getDescricao().trim()
        );
    }

    @Override
    public boolean alterar(Conexao conexao, TipoAtividade tipo) {
        return conexao.manipular(
                "UPDATE tipo_atividade SET ta_desc = ? WHERE ta_cod = ?",
                tipo.getDescricao().trim(),
                tipo.getId()
        );
    }

    @Override
    public boolean apagar(Conexao conexao, TipoAtividade tipo) {
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