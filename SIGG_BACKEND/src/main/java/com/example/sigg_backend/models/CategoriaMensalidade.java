package com.example.sigg_backend.models;

import com.example.sigg_backend.db.dao.CategoriaMensalidadeDAO;
import com.example.sigg_backend.db.util.Conexao;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@SuppressWarnings("unused")
public class CategoriaMensalidade {
    private int id;
    private String descricao;
    private BigDecimal valor = BigDecimal.ZERO;
    private int qtdIrmao;

    public CategoriaMensalidade() {
    }

    public CategoriaMensalidade(Integer id, String descricao, BigDecimal valor) {
        this(id, descricao, valor, 0);
    }

    public CategoriaMensalidade(Integer id, String descricao, BigDecimal valor, Integer qtdIrmao) {
        this.id = id != null ? id : 0;
        this.descricao = descricao;
        this.valor = valor != null ? valor.setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO;
        this.qtdIrmao = qtdIrmao != null ? qtdIrmao : 0;
    }

    public String validar() {
        if (descricao == null || descricao.trim().isEmpty())
            return "A descrição da categoria de mensalidade é obrigatória.";
        if (descricao.trim().length() > 100)
            return "A descrição da categoria deve ter no máximo 100 caracteres.";
        if (valor == null || valor.compareTo(BigDecimal.ZERO) < 0)
            return "O valor da categoria não pode ser negativo.";
        if (qtdIrmao < 0)
            return "A quantidade de irmãos não pode ser negativa.";
        return null;
    }

    public boolean gravar(Conexao conexao) {
        return new CategoriaMensalidadeDAO().gravar(conexao, this);
    }

    public boolean alterar(Conexao conexao) {
        return new CategoriaMensalidadeDAO().alterar(conexao, this);
    }

    public boolean apagar(Conexao conexao) {
        return new CategoriaMensalidadeDAO().apagar(conexao, this);
    }

    public List<CategoriaMensalidade> listar(Conexao conexao) {
        return new CategoriaMensalidadeDAO().get(conexao);
    }

    public List<CategoriaMensalidade> pesquisar(Conexao conexao, String filtro) {
        return new CategoriaMensalidadeDAO().get(conexao, filtro);
    }

    public CategoriaMensalidade buscar(Conexao conexao, int id) {
        return new CategoriaMensalidadeDAO().get(conexao, id);
    }

    public boolean possuiVinculos(Conexao conexao, int id) {
        return new CategoriaMensalidadeDAO().possuiVinculos(conexao, id);
    }

    public boolean existeDescricao(Conexao conexao, String descricao, int ignorarId) {
        return new CategoriaMensalidadeDAO().existeDescricao(conexao, descricao, ignorarId);
    }

    public static CategoriaMensalidade buscarPorId(Conexao conexao, int id) {
        return new CategoriaMensalidade().buscar(conexao, id);
    }

    public static CategoriaMensalidade[] listarTodas(Conexao conexao) {
        return new CategoriaMensalidade().listar(conexao).toArray(new CategoriaMensalidade[0]);
    }

    public int getId() { return id; }
    public void setId(Integer id) { this.id = id != null ? id : 0; }

    public int getCat_mens_cod() { return id; }
    public void setCat_mens_cod(Integer cat_mens_cod) {
        if (cat_mens_cod != null) {
            this.id = cat_mens_cod;
        }
    }

    public int getCat_cod() { return id; }
    public void setCat_cod(Integer cat_cod) {
        if (cat_cod != null) {
            this.id = cat_cod;
        }
    }

    public String getDescricao() { return descricao; }
    public void setDescricao(String descricao) {
        if (descricao != null) {
            this.descricao = descricao;
        }
    }

    public String getCat_desc() { return descricao; }
    public void setCat_desc(String cat_desc) {
        if (cat_desc != null) {
            this.descricao = cat_desc;
        }
    }

    public String getCat_mens_desc() { return descricao; }
    public void setCat_mens_desc(String cat_mens_desc) {
        if (cat_mens_desc != null) {
            this.descricao = cat_mens_desc;
        }
    }

    public BigDecimal getValor() { return valor; }
    public void setValor(BigDecimal valor) {
        this.valor = valor != null ? valor.setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO;
    }

    public BigDecimal getCat_valor() { return valor; }
    public void setCat_valor(BigDecimal cat_valor) {
        if (cat_valor != null) {
            setValor(cat_valor);
        }
    }

    public BigDecimal getCat_mens_valor() { return valor; }
    public void setCat_mens_valor(BigDecimal cat_mens_valor) {
        if (cat_mens_valor != null) {
            setValor(cat_mens_valor);
        }
    }

    public int getQtdIrmao() { return qtdIrmao; }
    public void setQtdIrmao(Integer qtdIrmao) {
        this.qtdIrmao = qtdIrmao != null ? qtdIrmao : 0;
    }

    public int getQtd_irmao() { return qtdIrmao; }
    public void setQtd_irmao(Integer qtd_irmao) {
        if (qtd_irmao != null) {
            this.qtdIrmao = qtd_irmao;
        }
    }

    public int getCat_mens_qtd_irmao() { return qtdIrmao; }
    public void setCat_mens_qtd_irmao(Integer cat_mens_qtd_irmao) {
        if (cat_mens_qtd_irmao != null) {
            this.qtdIrmao = cat_mens_qtd_irmao;
        }
    }
}