package com.example.sigg_backend.facade;

import com.example.sigg_backend.db.util.Conexao;
import com.example.sigg_backend.models.CategoriaMensalidade;
import com.example.sigg_backend.models.Escoteiro;
import com.example.sigg_backend.models.FormaPagamento;
import com.example.sigg_backend.models.PagamentoMensalidade;
import com.example.sigg_backend.models.SituacaoEscoteiro;
import com.example.sigg_backend.models.UsuarioLogado;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class PagamentoMensalidadeFacade {

    private final AutorizacaoFacade autorizacao;

    public PagamentoMensalidadeFacade(AutorizacaoFacade autorizacao) {
        this.autorizacao = autorizacao;
    }

    public FormaPagamento[] listarFormasPagamento(Conexao conexao) {
        autorizacao.exigirDiretoria(conexao);
        return new PagamentoMensalidade().listarFormasPagamento(conexao);
    }

    public Escoteiro[] pesquisarEscoteiros(Conexao conexao, String busca) {
        autorizacao.exigirDiretoria(conexao);
        if (busca == null || busca.isBlank()) {
            return new PagamentoMensalidade().listarEscoteirosComResumo(conexao);
        }
        String termo = busca.trim();
        if (termo.matches("[\\d.\\-]+")) {
            termo = termo.replaceAll("\\D", "");
        }
        List<Escoteiro> lista = new Escoteiro().pesquisar(conexao, termo);
        if (lista.isEmpty()) {
            throw new NaoEncontradoException("Escoteiro não localizado no sistema.");
        }
        return lista.toArray(new Escoteiro[0]);
    }

    public List<SituacaoEscoteiro> listarSituacoes(Conexao conexao, Integer troCod) {
        autorizacao.exigirDiretoria(conexao);
        return new PagamentoMensalidade().listarSituacoes(conexao, troCod);
    }

    @Transactional
    public Map<String, String> marcarInadimplente(Conexao conexao, int escCod) {
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
        return Map.of("mensagem", escoteiro.getMembro().getNome() + " foi marcado(a) como inadimplente.");
    }

    public Map<String, Object> consultarSituacaoEscoteiro(Conexao conexao, int escCod) {
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
        return resumo;
    }

    public PagamentoMensalidade[] listarPagamentosDaMensalidade(Conexao conexao, int crCod) {
        autorizacao.exigirDiretoria(conexao);
        PagamentoMensalidade model = new PagamentoMensalidade();
        PagamentoMensalidade pag = model.buscarPorConta(conexao, crCod);
        if (pag == null) {
            throw new NaoEncontradoException("Mensalidade não encontrada.");
        }
        return model.listarLancamentos(conexao, crCod);
    }

    @Transactional
    public Map<String, Object> registrarPagamento(
            Conexao conexao,
            int crCod,
            BigDecimal valorRecebido,
            int formaPagamentoCod,
            LocalDate dataPagamento
    ) {
        return registrarPagamento(conexao, crCod, valorRecebido, formaPagamentoCod, dataPagamento, BigDecimal.ZERO);
    }

    @Transactional
    public Map<String, Object> registrarPagamento(
            Conexao conexao,
            int crCod,
            BigDecimal valorRecebido,
            int formaPagamentoCod,
            LocalDate dataPagamento,
            BigDecimal creditoUtilizado
    ) {
        autorizacao.exigirPerfil(conexao, List.of(UsuarioLogado.ADMIN, UsuarioLogado.FINANCEIRO));

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
        BigDecimal creditoFinal = creditoUtilizado != null
                ? creditoUtilizado.setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

        if (creditoFinal.compareTo(creditoDisp) > 0) {
            throw new RegraNegocioException("Crédito informado (R$ " + creditoFinal + ") excede o saldo disponível (R$ " + creditoDisp + ").");
        }
        if (creditoFinal.compareTo(saldoDevido) > 0) {
            creditoFinal = saldoDevido;
        }

        BigDecimal valorFinal = valorRecebido != null
                ? valorRecebido.setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

        LocalDate dtPagto = dataPagamento != null ? dataPagamento : LocalDate.now();
        pag.setValorRecebido(valorFinal);
        pag.setCreditoUtilizado(creditoFinal);
        pag.setFormaPagamentoCod(formaPagamentoCod);
        pag.setDataPagamento(dtPagto);

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
        recibo.put("dataPagamento", dtPagto.toString());
        recibo.put("regularizado", regularizado);

        Map<String, Object> resposta = new LinkedHashMap<>();
        resposta.put("mensagem", regularizado
                ? "Pagamento registrado! A situação financeira do escoteiro foi regularizada automaticamente."
                : "Pagamento de mensalidade registrado com sucesso!");
        resposta.put("recibo", recibo);
        return resposta;
    }

    @Transactional
    public Map<String, Object> estornarPagamento(Conexao conexao, int crCod) {
        return estornarPagamento(conexao, crCod, 0);
    }

    @Transactional
    public Map<String, Object> estornarPagamento(Conexao conexao, int crCod, int pgCod) {
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

        return Map.of("mensagem", msg);
    }
}