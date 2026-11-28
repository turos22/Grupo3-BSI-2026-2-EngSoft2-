package com.example.sigg_backend.controllers;

import com.example.sigg_backend.db.util.Conexao;
import com.example.sigg_backend.facade.AutorizacaoFacade;
import com.example.sigg_backend.facade.NaoEncontradoException;
import com.example.sigg_backend.facade.RegraNegocioException;
import com.example.sigg_backend.models.CategoriaMensalidade;
import com.example.sigg_backend.models.Escoteiro;
import com.example.sigg_backend.models.PagamentoMensalidade;
import com.example.sigg_backend.models.UsuarioLogado;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/pagamentos-mensalidade")
public class PagamentoMensalidadeController {

    private final Conexao conexao;
    private final AutorizacaoFacade autorizacao;

    public PagamentoMensalidadeController(Conexao conexao, AutorizacaoFacade autorizacao) {
        this.conexao = conexao;
        this.autorizacao = autorizacao;
    }

    @GetMapping("/formas-pagamento")
    public ResponseEntity<Object> listarFormasPagamento() {
        autorizacao.exigirDiretoria(conexao);
        PagamentoMensalidade model = new PagamentoMensalidade();
        return ResponseEntity.ok(model.listarFormasPagamento(conexao));
    }

    @GetMapping("/escoteiros")
    public ResponseEntity<Object> pesquisarEscoteiros(
            @RequestParam(value = "busca", required = false, defaultValue = "") String busca) {
        autorizacao.exigirDiretoria(conexao);
        Escoteiro escoteiroModel = new Escoteiro();
        String termo = busca != null ? busca.trim() : "";
        if (termo.matches("[\\d.\\-]+")) {
            termo = termo.replaceAll("\\D", "");
        }
        List<Escoteiro> lista = termo.isBlank()
                ? escoteiroModel.pesquisar(conexao, "")
                : escoteiroModel.pesquisar(conexao, termo);
        if (!termo.isBlank() && lista.isEmpty()) {
            throw new NaoEncontradoException("Escoteiro não localizado no sistema.");
        }
        return ResponseEntity.ok(lista.toArray(new Escoteiro[0]));
    }

    @GetMapping("/situacoes")
    public ResponseEntity<Object> listarSituacoes(@RequestParam(value = "tropa", required = false) Integer tropa) {
        autorizacao.exigirDiretoria(conexao);
        PagamentoMensalidade model = new PagamentoMensalidade();
        return ResponseEntity.ok(model.listarSituacoes(conexao, tropa));
    }

    @PostMapping("/escoteiros/{escCod}/inadimplente")
    @Transactional
    public ResponseEntity<Object> marcarInadimplente(@PathVariable int escCod) {
        autorizacao.exigirPerfil(conexao, List.of(UsuarioLogado.ADMIN, UsuarioLogado.FINANCEIRO));
        Escoteiro escoteiro = new Escoteiro().buscarPorCodigo(conexao, escCod);
        if (escoteiro == null) {
            throw new NaoEncontradoException("Escoteiro não encontrado.");
        }
        if (escoteiro.getEsc_sit_fin() == 1) {
            throw new RegraNegocioException("Este escoteiro já está inadimplente");
        }
        PagamentoMensalidade model = new PagamentoMensalidade();
        if (!model.possuiMensalidadeVencidaEmAberto(conexao, escCod)) {
            throw new RegraNegocioException("Este escoteiro não possui mensalidades em atraso e não pode ser marcado como Inadimplente");
        }
        if (!model.atualizarSituacaoFinanceiraEscoteiro(conexao, escCod, 1)) {
            throw new IllegalStateException("Não foi possível marcar o escoteiro como inadimplente.");
        }
        return ResponseEntity.ok(Map.of("mensagem", escoteiro.getMembro().getNome() + " foi marcado(a) como inadimplente."));
    }

    @GetMapping("/escoteiros/{escCod}")
    public ResponseEntity<Object> consultarSituacao(@PathVariable int escCod) {
        autorizacao.exigirDiretoria(conexao);
        Escoteiro escoteiro = new Escoteiro().buscarPorCodigo(conexao, escCod);
        if (escoteiro == null) {
            throw new NaoEncontradoException("Escoteiro não encontrado.");
        }

        PagamentoMensalidade model = new PagamentoMensalidade();
        CategoriaMensalidade categoria = new CategoriaMensalidade().buscar(conexao, escoteiro.getCat_mensalidade());
        PagamentoMensalidade[] mensalidades = model.listarPorEscoteiro(conexao, escCod);
        BigDecimal creditoDisponivel = model.calcularCreditoDisponivel(conexao, escCod);

        Map<String, Object> resumo = new LinkedHashMap<>();
        resumo.put("esc_cod", escoteiro.getEsc_cod());
        resumo.put("mem_cod", escoteiro.getMembro().getMem_cod());
        resumo.put("nome", escoteiro.getMembro().getNome());
        resumo.put("cpf", escoteiro.getMembro().getCPF());
        resumo.put("patrulha", escoteiro.getPatrulha() != null ? escoteiro.getPatrulha().getNome() : "—");
        resumo.put("categoria", categoria != null ? categoria.getDescricao() : "Categoria " + escoteiro.getCat_mensalidade());
        resumo.put("valorCategoria", categoria != null ? categoria.getValor() : BigDecimal.ZERO);
        resumo.put("inadimplente", escoteiro.getEsc_sit_fin() == 1);
        resumo.put("creditoDisponivel", creditoDisponivel);
        resumo.put("mensalidades", mensalidades);
        return ResponseEntity.ok(resumo);
    }

    @GetMapping("/{crCod}/lancamentos")
    public ResponseEntity<Object> listarLancamentos(@PathVariable int crCod) {
        autorizacao.exigirDiretoria(conexao);
        PagamentoMensalidade model = new PagamentoMensalidade();
        PagamentoMensalidade pag = model.buscarPorConta(conexao, crCod);
        if (pag == null) {
            throw new NaoEncontradoException("Mensalidade não encontrada.");
        }
        return ResponseEntity.ok(model.listarLancamentos(conexao, crCod));
    }

    @PostMapping("/{crCod}/registrar")
    @Transactional
    public ResponseEntity<Object> registrar(@PathVariable int crCod, @RequestBody Map<String, Object> corpo) {
        autorizacao.exigirPerfil(conexao, List.of(UsuarioLogado.ADMIN, UsuarioLogado.FINANCEIRO));

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

        PagamentoMensalidade model = new PagamentoMensalidade();
        PagamentoMensalidade pag = model.buscarPorConta(conexao, crCod);
        if (pag == null) {
            throw new NaoEncontradoException("Mensalidade não encontrada.");
        }

        BigDecimal saldoDevido = pag.saldoRestante();
        if (saldoDevido.compareTo(BigDecimal.ZERO) <= 0) {
            throw new RegraNegocioException("Esta mensalidade já se encontra totalmente quitada.");
        }

        String refAnteriorPendente = pag.buscarMensalidadeAnteriorEmAberto(conexao);
        if (refAnteriorPendente != null) {
            throw new RegraNegocioException(
                    "Não é permitido pagar a mensalidade " + pag.getReferencia()
                            + " enquanto houver pendências na mensalidade anterior (" + refAnteriorPendente + "). Quite a mais antiga primeiro."
            );
        }

        BigDecimal creditoDisp = model.calcularCreditoDisponivel(conexao, pag.getEscCod());
        BigDecimal creditoFinal = creditoUtilizado.setScale(2, RoundingMode.HALF_UP);

        if (creditoFinal.compareTo(creditoDisp) > 0) {
            throw new RegraNegocioException("Crédito informado (R$ " + creditoFinal + ") excede o saldo disponível (R$ " + creditoDisp + ").");
        }
        if (creditoFinal.compareTo(saldoDevido) > 0) {
            creditoFinal = saldoDevido;
        }

        BigDecimal valorFinal = valorRecebido.setScale(2, RoundingMode.HALF_UP);
        pag.setValorRecebido(valorFinal);
        pag.setCreditoUtilizado(creditoFinal);
        pag.setFormaPagamentoCod(formaPagamentoCod);
        pag.setDataPagamento(dataPagamento);

        String problema = pag.validar();
        if (problema != null) {
            throw new RegraNegocioException(problema);
        }

        BigDecimal totalAplicado = valorFinal.add(creditoFinal).setScale(2, RoundingMode.HALF_UP);
        BigDecimal diferenca = pag.verificarDivergencia(totalAplicado);

        StringBuilder obs = new StringBuilder();
        if (creditoFinal.compareTo(BigDecimal.ZERO) > 0) {
            obs.append("Desconto de R$ ").append(creditoFinal).append(" aplicado do saldo excedente. ");
        }
        if (diferenca.compareTo(BigDecimal.ZERO) < 0) {
            obs.append("Pagamento parcial. Diferença de R$ ").append(diferenca.abs()).append(" mantida em aberto.");
        } else if (diferenca.compareTo(BigDecimal.ZERO) > 0) {
            obs.append("Pagamento superior ao devido. Excedente de R$ ").append(diferenca).append(" registrado como crédito.");
        }
        String obsDivergencia = obs.toString().trim();
        pag.setObservacao(obsDivergencia);

        if (!pag.registrar(conexao)) {
            throw new IllegalStateException("Não foi possível registrar o pagamento da mensalidade.");
        }

        boolean regularizado = false;
        if (pag.isInadimplente() && !model.possuiMensalidadeVencidaEmAberto(conexao, pag.getEscCod())) {
            model.atualizarSituacaoFinanceiraEscoteiro(conexao, pag.getEscCod(), 0);
            regularizado = true;
        }

        Map<String, Object> recibo = new LinkedHashMap<>();
        recibo.put("numero", "REC-MENS-" + crCod + "-" + (System.currentTimeMillis() % 10000));
        recibo.put("escoteiro", pag.getEscoteiroNome());
        recibo.put("cpf", pag.getEscoteiroCpf());
        recibo.put("matricula", pag.getEscCod());
        recibo.put("referencia", pag.getReferencia());
        recibo.put("valorDevido", saldoDevido);
        recibo.put("creditoUtilizado", creditoFinal);
        recibo.put("valorPago", valorFinal);
        recibo.put("diferenca", diferenca);
        recibo.put("observacao", obsDivergencia);
        recibo.put("dataPagamento", dataPagamento.toString());
        recibo.put("regularizado", regularizado);

        Map<String, Object> resposta = new LinkedHashMap<>();
        resposta.put("mensagem", regularizado
                ? "Pagamento registrado! A situação financeira do escoteiro foi regularizada automaticamente."
                : "Pagamento de mensalidade registrado com sucesso!");
        resposta.put("recibo", recibo);
        return ResponseEntity.ok(resposta);
    }

    @DeleteMapping("/{crCod}/estornar")
    @Transactional
    public ResponseEntity<Object> estornar(
            @PathVariable int crCod,
            @RequestParam(value = "pgCod", required = false, defaultValue = "0") int pgCod) {
        autorizacao.exigirPerfil(conexao, List.of(UsuarioLogado.ADMIN, UsuarioLogado.FINANCEIRO));

        PagamentoMensalidade model = new PagamentoMensalidade();
        PagamentoMensalidade pag = model.buscarPorConta(conexao, crCod);
        if (pag == null) {
            throw new NaoEncontradoException("Mensalidade não encontrada.");
        }
        if (pag.getValorPago().compareTo(BigDecimal.ZERO) <= 0) {
            throw new RegraNegocioException("Esta mensalidade não possui pagamento registrado para estorno.");
        }

        String refPosteriorPaga = pag.buscarMensalidadePosteriorComPagamento(conexao);
        if (refPosteriorPaga != null) {
            throw new RegraNegocioException(
                    "Não é permitido estornar a mensalidade " + pag.getReferencia()
                            + " pois existe pagamento registrado em competência posterior (" + refPosteriorPaga + "). Estorne a mais recente primeiro."
            );
        }

        if (pgCod > 0) {
            pag.setId(pgCod);
        }

        if (!pag.estornar(conexao)) {
            throw new RegraNegocioException(
                    "O estorno deve respeitar a ordem do último pagamento realizado até o inicial."
            );
        }

        PagamentoMensalidade atualizado = model.buscarPorConta(conexao, crCod);
        BigDecimal pagoRestante = atualizado != null ? atualizado.getValorPago() : BigDecimal.ZERO;

        String msg = pagoRestante.compareTo(BigDecimal.ZERO) > 0
                ? "Lançamento de R$ " + pag.getValorEstornado() + " estornado! A mensalidade " + pag.getReferencia() + " permanece com R$ " + pagoRestante + " pagos."
                : "Estorno concluído (R$ " + pag.getValorEstornado() + ")! A mensalidade " + pag.getReferencia() + " voltou para em aberto.";

        return ResponseEntity.ok(Map.of("mensagem", msg));
    }
}