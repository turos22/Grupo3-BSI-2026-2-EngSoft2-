package com.example.sigg_backend.controllers;

import com.example.sigg_backend.db.util.Conexao;
import com.example.sigg_backend.facade.AutorizacaoFacade;
import com.example.sigg_backend.facade.NaoEncontradoException;
import com.example.sigg_backend.facade.RegraNegocioException;
import com.example.sigg_backend.models.TipoAtividade;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/tipos-atividade")
public class TipoAtividadeController {

    private final Conexao conexao;
    private final AutorizacaoFacade autorizacao;

    public TipoAtividadeController(Conexao conexao, AutorizacaoFacade autorizacao) {
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
        TipoAtividade model = new TipoAtividade();
        List<TipoAtividade> lista = (termo == null || termo.isBlank())
                ? model.listar(conexao)
                : model.pesquisar(conexao, termo);
        return ResponseEntity.ok(lista);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Object> buscarPorId(@PathVariable int id) {
        autorizacao.exigirDiretoria(conexao);
        TipoAtividade tipo = new TipoAtividade().buscar(conexao, id);
        if (tipo == null) {
            throw new NaoEncontradoException("Tipo de atividade não encontrado.");
        }
        return ResponseEntity.ok(tipo);
    }

    @PostMapping
    public ResponseEntity<Object> criar(@RequestBody Map<String, Object> corpo) {
        autorizacao.exigirDiretoria(conexao);
        if (corpo == null) {
            throw new RegraNegocioException("Dados do tipo de atividade são obrigatórios.");
        }

        TipoAtividade tipo = new TipoAtividade();
        preencherTipoAtividade(tipo, 0, corpo);

        String problema = tipo.validar();
        if (problema != null) {
            throw new RegraNegocioException(problema);
        }
        if (tipo.existeDescricao(conexao, tipo.getDescricao(), 0)) {
            throw new RegraNegocioException("Já existe um tipo de atividade cadastrado com esta descrição.");
        }
        if (!tipo.gravar(conexao)) {
            throw new IllegalStateException("Erro ao cadastrar tipo de atividade.");
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(tipo);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Object> atualizar(@PathVariable int id, @RequestBody Map<String, Object> corpo) {
        autorizacao.exigirDiretoria(conexao);
        TipoAtividade tipo = new TipoAtividade().buscar(conexao, id);
        if (tipo == null) {
            throw new NaoEncontradoException("Tipo de atividade não encontrado.");
        }

        preencherTipoAtividade(tipo, id, corpo);
        String problema = tipo.validar();
        if (problema != null) {
            throw new RegraNegocioException(problema);
        }
        if (tipo.existeDescricao(conexao, tipo.getDescricao(), id)) {
            throw new RegraNegocioException("Já existe outro tipo de atividade cadastrado com esta descrição.");
        }
        if (!tipo.alterar(conexao)) {
            throw new IllegalStateException("Erro ao atualizar tipo de atividade.");
        }
        return ResponseEntity.ok(tipo);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Object> excluir(@PathVariable int id) {
        autorizacao.exigirDiretoria(conexao);
        TipoAtividade tipo = new TipoAtividade().buscar(conexao, id);
        if (tipo == null) {
            throw new NaoEncontradoException("Tipo de atividade não encontrado.");
        }
        if (tipo.possuiVinculos(conexao, id)) {
            throw new RegraNegocioException("Não é possível excluir este tipo de atividade pois há atividades vinculadas a ele.");
        }
        if (!tipo.apagar(conexao)) {
            throw new IllegalStateException("Erro ao excluir tipo de atividade.");
        }
        return ResponseEntity.ok(Map.of("mensagem", "Tipo de atividade excluído com sucesso."));
    }

    private void preencherTipoAtividade(TipoAtividade tipo, int id, Map<String, Object> corpo) {
        tipo.setId(id);

        Object descObj = corpo.get("ta_desc");
        if (descObj == null) descObj = corpo.get("descricao");
        tipo.setDescricao(descObj != null ? descObj.toString() : "");
    }
}