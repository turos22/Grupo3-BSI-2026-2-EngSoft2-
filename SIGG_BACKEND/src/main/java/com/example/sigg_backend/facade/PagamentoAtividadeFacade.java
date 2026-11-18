package com.example.sigg_backend.facade;

import com.example.sigg_backend.db.util.Conexao;
import com.example.sigg_backend.models.Escoteiro;
import com.example.sigg_backend.models.FormaPagamento;
import com.example.sigg_backend.models.PagamentoAtividade;
import com.example.sigg_backend.models.PagamentoMensalidade;
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
public class PagamentoAtividadeFacade {

    private final AutorizacaoFacade autorizacao;

    public PagamentoAtividadeFacade(AutorizacaoFacade autorizacao) {
        this.autorizacao = autorizacao;
    }

    public FormaPagamento[] listarFormasPagamento(Conexao conexao) {
        autorizacao.exigirDiretoria(conexao);
        return new PagamentoAtividade().listarFormasPagamento(conexao);
    }

    public Escoteiro[] pesquisarEscoteiros(Conexao conexao, String busca) {
        autorizacao.exigirDiretoria(conexao);
        if (busca == null || busca.isBlank()) {
            return new PagamentoAtividade().listarEscoteirosComResumo(conexao);
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

    public Map<String, Object> consultarAtividadesEscoteiro(Conexao conexao, int escCod) {
        autorizacao.exigirDiretoria(conexao);
        Escoteiro escoteiro = new Escoteiro().buscarPorCodigo(conexao, escCod);
        if (escoteiro == null) {
            throw new NaoEncontradoException("Escoteiro não encontrado.");
        }

        PagamentoAtividade modelAtv = new PagamentoAtividade();
        PagamentoMensalidade modelMens = new PagamentoMensalidade();

        PagamentoAtividade[] atividades = modelAtv.listarPorEscoteiro(conexao, escCod);
        boolean possuiMensalidadeAtrasada = modelMens.possuiMensalidadeVencidaEmAberto(conexao, escCod);
        boolean inadimplente = escoteiro.getEsc_sit_fin() == 1 || possuiMensalidadeAtrasada;
        BigDecimal creditoDisponivel = modelAtv.calcularCreditoDisponivel(conexao, escCod);

        Map<String, Object> resumo = new LinkedHashMap<>();
        resumo.put("esc_cod", escoteiro.getEsc_cod());
        resumo.put("mem_cod", escoteiro.getMembro().getMem_cod());
        resumo.put("nome", escoteiro.getMembro().getNome());
        resumo.put("cpf", escoteiro.getMembro().getCPF());
        resumo.put("patrulha", escoteiro.getPatrulha() != null ? escoteiro.getPatrulha().getNome() : "—");
        resumo.put("inadimplente", inadimplente);
        resumo.put("creditoDisponivel", creditoDisponivel);
        resumo.put("atividades", atividades);
        return resumo;
    }

    public PagamentoAtividade[] listarPagamentosDaAtividade(Conexao conexao, int crCod) {
        autorizacao.exigirDiretoria(conexao);
        PagamentoAtividade model = new PagamentoAtividade();
        PagamentoAtividade pag = model.buscarPorConta(conexao, crCod);
        if (pag == null) {
            throw new NaoEncontradoException("Inscrição em atividade não encontrada.");
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

        PagamentoAtividade model = new PagamentoAtividade();
        PagamentoAtividade pag = model.buscarPorConta(conexao, crCod);
        if (pag == null) {
            throw new NaoEncontradoException("Inscrição em atividade paga não encontrada.");
        }

        PagamentoMensalidade modelMens = new PagamentoMensalidade();
        if (pag.isInadimplente() || modelMens.possuiMensalidadeVencidaEmAberto(conexao, pag.getEscCod())) {
            throw new RegraNegocioException(
                    "Este escoteiro está inadimplente. Não é permitido registrar pagamento de atividades pagas enquanto houver mensalidades pendentes. Regularize a situação financeira primeiro."
            );
        }

        BigDecimal saldoDevido = pag.saldoRestante();
        if (saldoDevido.compareTo(BigDecimal.ZERO) <= 0) {
            throw new RegraNegocioException("Esta atividade já se encontra totalmente paga para este escoteiro.");
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
            obs.append("Pagamento parcial da atividade. Diferença de R$ ").append(diferenca.abs()).append(" mantida em aberto.");
        } else if (diferenca.compareTo(BigDecimal.ZERO) > 0) {
            obs.append("Pagamento superior ao valor da atividade. Excedente de R$ ").append(diferenca).append(" registrado como crédito.");
        }
        String obsDivergencia = obs.toString().trim();
        pag.setObservacao(obsDivergencia);

        if (!pag.registrar(conexao)) {
            throw new IllegalStateException("Não foi possível registrar o pagamento da atividade.");
        }

        Map<String, Object> recibo = new LinkedHashMap<>();
        recibo.put("numero", "REC-ATV-" + crCod + "-" + (System.currentTimeMillis() % 10000));
        recibo.put("escoteiro", pag.getEscoteiroNome());
        recibo.put("cpf", pag.getEscoteiroCpf());
        recibo.put("matricula", pag.getEscCod());
        recibo.put("atividade", pag.getAtividadeNome());
        recibo.put("referencia", pag.getAtividadeNome());
        recibo.put("dataAtividade", pag.getDataAtividade() != null ? pag.getDataAtividade().toString() : "—");
        recibo.put("valorAtividade", pag.getValorDevido());
        recibo.put("valorDevido", saldoDevido);
        recibo.put("creditoUtilizado", creditoFinal);
        recibo.put("valorPago", valorFinal);
        recibo.put("diferenca", diferenca);
        recibo.put("observacao", obsDivergencia);
        recibo.put("dataPagamento", dtPagto.toString());

        Map<String, Object> resposta = new LinkedHashMap<>();
        resposta.put("mensagem", "Pagamento da atividade " + pag.getAtividadeNome() + " registrado com sucesso!");
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

        PagamentoAtividade model = new PagamentoAtividade();
        PagamentoAtividade pag = model.buscarPorConta(conexao, crCod);
        if (pag == null) {
            throw new NaoEncontradoException("Inscrição em atividade não encontrada.");
        }
        if (pag.getValorPago().compareTo(BigDecimal.ZERO) <= 0) {
            throw new RegraNegocioException("Esta atividade não possui pagamento registrado para estorno.");
        }

        if (pgCod > 0) {
            pag.setId(pgCod);
        }

        if (!pag.estornar(conexao)) {
            throw new RegraNegocioException(
                    "O estorno deve respeitar a ordem do último pagamento realizado até o inicial."
            );
        }

        PagamentoAtividade atualizado = model.buscarPorConta(conexao, crCod);
        BigDecimal pagoRestante = atualizado != null ? atualizado.getValorPago() : BigDecimal.ZERO;

        String msg = pagoRestante.compareTo(BigDecimal.ZERO) > 0
                ? "Lançamento de R$ " + pag.getValorEstornado() + " estornado! A atividade " + pag.getAtividadeNome() + " permanece com R$ " + pagoRestante + " pagos."
                : "Estorno concluído (R$ " + pag.getValorEstornado() + ")! A participação na atividade " + pag.getAtividadeNome() + " voltou para em aberto.";

        return Map.of("mensagem", msg);
    }
}