package com.example.sigg_backend.controllers;

import com.example.sigg_backend.db.util.Conexao;
import com.example.sigg_backend.facade.AutorizacaoFacade;
import com.example.sigg_backend.facade.NaoEncontradoException;
import com.example.sigg_backend.facade.PagamentoAtividadeFacade;
import com.example.sigg_backend.models.PagamentoAtividade;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

@RestController
@RequestMapping("/pagamentos-atividade")
public class PagamentoAtividadeController {

    private final Conexao conexao;
    private final PagamentoAtividadeFacade facade;
    private final AutorizacaoFacade autorizacao;

    public PagamentoAtividadeController(
            Conexao conexao,
            PagamentoAtividadeFacade facade,
            AutorizacaoFacade autorizacao
    ) {
        this.conexao = conexao;
        this.facade = facade;
        this.autorizacao = autorizacao;
    }

    @GetMapping("/formas-pagamento")
    public ResponseEntity<Object> listarFormasPagamento() {
        autorizacao.exigirDiretoria(conexao);
        return ResponseEntity.ok(new PagamentoAtividade().listarFormasPagamento(conexao));
    }

    @GetMapping("/escoteiros")
    public ResponseEntity<Object> pesquisarEscoteiros(
            @RequestParam(value = "busca", required = false, defaultValue = "") String busca) {
        return ResponseEntity.ok(facade.pesquisarEscoteiros(conexao, busca));
    }

    @GetMapping("/escoteiros/{escCod}")
    public ResponseEntity<Object> consultarAtividades(@PathVariable int escCod) {
        return ResponseEntity.ok(facade.consultarAtividadesEscoteiro(conexao, escCod));
    }

    @GetMapping("/{crCod}/lancamentos")
    public ResponseEntity<Object> listarLancamentos(@PathVariable int crCod) {
        autorizacao.exigirDiretoria(conexao);
        PagamentoAtividade model = new PagamentoAtividade();
        if (model.buscarPorConta(conexao, crCod) == null) {
            throw new NaoEncontradoException("Inscrição em atividade não encontrada.");
        }
        return ResponseEntity.ok(model.listarLancamentos(conexao, crCod));
    }

    @PostMapping("/{crCod}/registrar")
    public ResponseEntity<Object> registrar(@PathVariable int crCod, @RequestBody Map<String, Object> corpo) {
        BigDecimal valorRecebido = corpo.get("valorRecebido") != null && !corpo.get("valorRecebido").toString().isBlank()
                ? new BigDecimal(corpo.get("valorRecebido").toString())
                : BigDecimal.ZERO;
        BigDecimal creditoUtilizado = corpo.get("creditoUtilizado") != null && !corpo.get("creditoUtilizado").toString().isBlank()
                ? new BigDecimal(corpo.get("creditoUtilizado").toString())
                : BigDecimal.ZERO;
        int formaPagamentoCod = Integer.parseInt(corpo.get("formaPagamentoCod").toString());
        LocalDate dataPagamento = corpo.get("dataPagamento") != null && !corpo.get("dataPagamento").toString().isBlank()
                ? LocalDate.parse(corpo.get("dataPagamento").toString())
                : LocalDate.now();

        return ResponseEntity.ok(
                facade.registrarPagamento(
                        conexao, crCod, valorRecebido, formaPagamentoCod, dataPagamento, creditoUtilizado
                )
        );
    }

    @DeleteMapping("/{crCod}/estornar")
    public ResponseEntity<Object> estornar(
            @PathVariable int crCod,
            @RequestParam(value = "pgCod", required = false, defaultValue = "0") int pgCod) {
        return ResponseEntity.ok(facade.estornarPagamento(conexao, crCod, pgCod));
    }
}