package com.example.sigg_backend.db.dao;

import com.example.sigg_backend.db.util.Conexao;
import com.example.sigg_backend.db.util.IDAO;
import com.example.sigg_backend.models.CategoriaMensalidade;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class CategoriaMensalidadeDAO implements IDAO<CategoriaMensalidade> {

    private int proximoCodigo(Conexao conexao) {
        ResultSet rs = conexao.consultar("SELECT COALESCE(MAX(cat_mens_cod), 0) + 1 AS proximo FROM categoria_mensalidade");
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
    public boolean gravar(Conexao conexao, CategoriaMensalidade categoria) {
        int novoCod = proximoCodigo(conexao);
        categoria.setId(novoCod);
        return conexao.manipular(
                "INSERT INTO categoria_mensalidade (cat_mens_cod, cat_mens_desc, cat_mens_valor, cat_mens_qtd_irmao) VALUES (?, ?, ?, ?)",
                categoria.getId(),
                categoria.getDescricao().trim(),
                categoria.getValor(),
                categoria.getQtdIrmao()
        );
    }

    @Override
    public boolean alterar(Conexao conexao, CategoriaMensalidade categoria) {
        return conexao.manipular(
                "UPDATE categoria_mensalidade SET cat_mens_desc = ?, cat_mens_valor = ?, cat_mens_qtd_irmao = ? WHERE cat_mens_cod = ?",
                categoria.getDescricao().trim(),
                categoria.getValor(),
                categoria.getQtdIrmao(),
                categoria.getId()
        );
    }

    @Override
    public boolean apagar(Conexao conexao, CategoriaMensalidade categoria) {
        return conexao.manipular(
                "DELETE FROM categoria_mensalidade WHERE cat_mens_cod = ?",
                categoria.getId()
        );
    }

    @Override
    public List<CategoriaMensalidade> get(Conexao conexao) {
        return buscar(conexao, "");
    }

    @Override
    public List<CategoriaMensalidade> get(Conexao conexao, String filtro) {
        if (filtro == null || filtro.isBlank())
            return get(conexao);
        return buscar(conexao, "WHERE cat_mens_desc ILIKE ?", "%" + filtro.trim() + "%");
    }

    @Override
    public CategoriaMensalidade get(Conexao conexao, int id) {
        List<CategoriaMensalidade> lista = buscar(conexao, "WHERE cat_mens_cod = ?", id);
        return lista.isEmpty() ? null : lista.get(0);
    }

    public boolean possuiVinculos(Conexao conexao, int id) {
        ResultSet rs = conexao.consultar(
                "SELECT 1 FROM escoteiro WHERE categoria_mensalidade_cat_mens_cod = ?"
                        + " UNION ALL "
                        + "SELECT 1 FROM mensalidade WHERE categoria_mensalidade_cat_mens_cod = ?"
                        + " LIMIT 1",
                id, id
        );
        try {
            return rs != null && rs.next();
        } catch (SQLException sqlex) {
            return false;
        }
    }

    public boolean existeDescricao(Conexao conexao, String descricao, int ignorarId) {
        ResultSet rs = conexao.consultar(
                "SELECT 1 FROM categoria_mensalidade WHERE LOWER(TRIM(cat_mens_desc)) = LOWER(TRIM(?)) AND cat_mens_cod <> ? LIMIT 1",
                descricao, ignorarId
        );
        try {
            return rs != null && rs.next();
        } catch (SQLException sqlex) {
            return false;
        }
    }

    private CategoriaMensalidade preencher(ResultSet rs) throws SQLException {
        int cod = rs.getInt("cat_mens_cod");
        String desc = rs.getString("cat_mens_desc");
        if (desc == null || desc.isBlank()) {
            desc = "Categoria " + cod;
        }
        BigDecimal valor = BigDecimal.valueOf(rs.getDouble("cat_mens_valor")).setScale(2, RoundingMode.HALF_UP);
        int qtdIrmao = rs.getInt("cat_mens_qtd_irmao");
        return new CategoriaMensalidade(cod, desc, valor, qtdIrmao);
    }

    private List<CategoriaMensalidade> buscar(Conexao conexao, String condicao, Object... valores) {
        String sql = "SELECT cat_mens_cod, cat_mens_desc, cat_mens_valor, cat_mens_qtd_irmao FROM categoria_mensalidade "
                + condicao + " ORDER BY cat_mens_cod ASC";
        ResultSet rs = conexao.consultar(sql, valores);
        List<CategoriaMensalidade> lista = new ArrayList<>();
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