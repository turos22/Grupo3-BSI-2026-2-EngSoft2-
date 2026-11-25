package com.example.sigg_backend.models;

import com.example.sigg_backend.db.dao.TipoAtividadeDAO;
import com.example.sigg_backend.db.util.Conexao;

import java.util.List;

@SuppressWarnings("unused")
public class TipoAtividade {
    private int id;
    private String descricao;

    public TipoAtividade() {
    }

    public TipoAtividade(Integer id, String descricao) {
        this.id = id != null ? id : 0;
        this.descricao = descricao;
    }

    public String validar() {
        if (descricao == null || descricao.trim().isEmpty())
            return "A descrição do tipo de atividade é obrigatória.";
        if (descricao.trim().length() > 100)
            return "A descrição do tipo de atividade deve ter no máximo 100 caracteres.";
        return null;
    }

    public boolean gravar(Conexao conexao) {
        return new TipoAtividadeDAO().gravar(conexao, this);
    }

    public boolean alterar(Conexao conexao) {
        return new TipoAtividadeDAO().alterar(conexao, this);
    }

    public boolean apagar(Conexao conexao) {
        return new TipoAtividadeDAO().apagar(conexao, this);
    }

    public List<TipoAtividade> listar(Conexao conexao) {
        return new TipoAtividadeDAO().get(conexao);
    }

    public List<TipoAtividade> pesquisar(Conexao conexao, String filtro) {
        return new TipoAtividadeDAO().get(conexao, filtro);
    }

    public TipoAtividade buscar(Conexao conexao, int id) {
        return new TipoAtividadeDAO().get(conexao, id);
    }

    public TipoAtividade buscarPorId(Conexao conexao, int id) {
        return buscar(conexao, id);
    }

    public boolean possuiVinculos(Conexao conexao, int id) {
        return new TipoAtividadeDAO().possuiVinculos(conexao, id);
    }

    public boolean existeDescricao(Conexao conexao, String descricao, int ignorarId) {
        return new TipoAtividadeDAO().existeDescricao(conexao, descricao, ignorarId);
    }

    public int getId() { return id; }
    public void setId(Integer id) { this.id = id != null ? id : 0; }

    public int getTa_cod() { return id; }
    public void setTa_cod(Integer ta_cod) {
        if (ta_cod != null) {
            this.id = ta_cod;
        }
    }

    public String getDescricao() { return descricao; }
    public void setDescricao(String descricao) {
        if (descricao != null) {
            this.descricao = descricao;
        }
    }

    public String getTa_desc() { return descricao; }
    public void setTa_desc(String ta_desc) {
        if (ta_desc != null) {
            this.descricao = ta_desc;
        }
    }
}