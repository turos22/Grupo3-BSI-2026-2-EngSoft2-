package com.example.sigg_backend.controllers;

import com.example.sigg_backend.db.util.Conexao;
import com.example.sigg_backend.facade.AutorizacaoFacade;
import com.example.sigg_backend.facade.NaoEncontradoException;
import com.example.sigg_backend.facade.RegraNegocioException;
import com.example.sigg_backend.models.CategoriaMensalidade;
import com.example.sigg_backend.models.UsuarioLogado;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/categorias-mensalidade")
public class CategoriaMensalidadeController {

    private final Conexao conexao;
    private final AutorizacaoFacade autorizacao;

    public CategoriaMensalidadeController(Conexao conexao, AutorizacaoFacade autorizacao) {
        this.conexao = conexao;
        this.autorizacao = autorizacao;
    }

    @GetMapping
    public ResponseEntity<Object> listar(
            @RequestParam(value = "filtro", required = false, defaultValue = "") String filtro,
            @RequestParam(value = "busca", required = false, defaultValue = "") String busca
    ) {
        autorizacao.exigirDiretoria(conexao);
        String termo = (filtro != null && !filtro.isBlank()) ? filtro : busca;
        CategoriaMensalidade model = new CategoriaMensalidade();
        List<CategoriaMensalidade> lista = (termo == null || termo.isBlank())
                ? model.listar(conexao)
                : model.pesquisar(conexao, termo);
        return ResponseEntity.ok(lista);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Object> buscarPorId(@PathVariable int id) {
        autorizacao.exigirDiretoria(conexao);
        CategoriaMensalidade categoria = new CategoriaMensalidade().buscar(conexao, id);
        if (categoria == null) {
            throw new NaoEncontradoException("Categoria de mensalidade não encontrada.");
        }
        return ResponseEntity.ok(categoria);
    }

    @PostMapping
    public ResponseEntity<Object> criar(@RequestBody Map<String, Object> corpo) {
        autorizacao.exigirPerfil(conexao, List.of(UsuarioLogado.ADMIN, UsuarioLogado.FINANCEIRO));
        if (corpo == null) {
            throw new RegraNegocioException("Dados da categoria são obrigatórios.");
        }

        CategoriaMensalidade categoria = new CategoriaMensalidade();
        preencherCategoria(categoria, 0, corpo);

        String problema = categoria.validar();
        if (problema != null) {
            throw new RegraNegocioException(problema);
        }
        if (categoria.existeDescricao(conexao, categoria.getDescricao(), 0)) {
            throw new RegraNegocioException("Já existe uma categoria cadastrada com esta descrição.");
        }
        if (!categoria.gravar(conexao)) {
            throw new IllegalStateException("Erro ao cadastrar categoria de mensalidade.");
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(categoria);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Object> atualizar(@PathVariable int id, @RequestBody Map<String, Object> corpo) {
        autorizacao.exigirPerfil(conexao, List.of(UsuarioLogado.ADMIN, UsuarioLogado.FINANCEIRO));
        CategoriaMensalidade categoria = new CategoriaMensalidade().buscar(conexao, id);
        if (categoria == null) {
            throw new NaoEncontradoException("Categoria de mensalidade não encontrada.");
        }

        preencherCategoria(categoria, id, corpo);
        String problema = categoria.validar();
        if (problema != null) {
            throw new RegraNegocioException(problema);
        }
        if (categoria.existeDescricao(conexao, categoria.getDescricao(), id)) {
            throw new RegraNegocioException("Já existe outra categoria cadastrada com esta descrição.");
        }
        if (!categoria.alterar(conexao)) {
            throw new IllegalStateException("Erro ao atualizar categoria de mensalidade.");
        }
        return ResponseEntity.ok(categoria);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Object> excluir(@PathVariable int id) {
        autorizacao.exigirPerfil(conexao, List.of(UsuarioLogado.ADMIN, UsuarioLogado.FINANCEIRO));
        CategoriaMensalidade categoria = new CategoriaMensalidade().buscar(conexao, id);
        if (categoria == null) {
            throw new NaoEncontradoException("Categoria de mensalidade não encontrada.");
        }
        if (categoria.possuiVinculos(conexao, id)) {
            throw new RegraNegocioException("Não é possível excluir esta categoria pois há escoteiros ou mensalidades vinculados a ela.");
        }
        if (!categoria.apagar(conexao)) {
            throw new IllegalStateException("Erro ao excluir categoria de mensalidade.");
        }
        return ResponseEntity.ok(Map.of("mensagem", "Categoria excluída com sucesso."));
    }

    private void preencherCategoria(CategoriaMensalidade cat, int id, Map<String, Object> corpo) {
        cat.setId(id);

        Object descObj = corpo.get("descricao");
        if (descObj == null) descObj = corpo.get("cat_mens_desc");
        if (descObj == null) descObj = corpo.get("cat_desc");
        cat.setDescricao(descObj != null ? descObj.toString() : "");

        Object valorObj = corpo.get("valor");
        if (valorObj == null) valorObj = corpo.get("cat_mens_valor");
        if (valorObj == null) valorObj = corpo.get("cat_valor");
        if (valorObj != null && !valorObj.toString().isBlank()) {
            try {
                cat.setValor(new BigDecimal(valorObj.toString()));
            } catch (NumberFormatException e) {
                cat.setValor(BigDecimal.valueOf(-1));
            }
        } else {
            cat.setValor(null);
        }

        Object qtdObj = corpo.get("qtd_irmao");
        if (qtdObj == null) qtdObj = corpo.get("qtdIrmao");
        if (qtdObj == null) qtdObj = corpo.get("cat_mens_qtd_irmao");
        if (qtdObj != null && !qtdObj.toString().isBlank()) {
            try {
                cat.setQtdIrmao(Integer.parseInt(qtdObj.toString()));
            } catch (NumberFormatException e) {
                cat.setQtdIrmao(-1);
            }
        } else {
            cat.setQtdIrmao(0);
        }
    }
}