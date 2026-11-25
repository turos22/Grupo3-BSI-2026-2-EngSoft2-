package com.example.sigg_backend.controllers;

import com.example.sigg_backend.db.util.Conexao;
import com.example.sigg_backend.facade.AutorizacaoFacade;
import com.example.sigg_backend.facade.NaoEncontradoException;
import com.example.sigg_backend.facade.RegraNegocioException;
import com.example.sigg_backend.models.Escoteiro;
import com.example.sigg_backend.models.Membro;
import com.example.sigg_backend.models.PagamentoAtividade;
import com.example.sigg_backend.models.PagamentoMensalidade;
import com.example.sigg_backend.models.Patrulha;
import com.example.sigg_backend.models.UsuarioLogado;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/pagamentos-atividade")
public class PagamentoAtividadeController {

    private final Conexao conexao;
    private final AutorizacaoFacade autorizacao;

    private record ItemResumoEscoteiro(
            int esc_cod,
            int esc_sit_fin,
            Membro membro,
            Patrulha patrulha,
            boolean mensalidadeInadimplente,
            int atividadesAbertas,
            int atividadesAtrasadas
    ) {}

    public PagamentoAtividadeController(Conexao conexao, AutorizacaoFacade autorizacao) {
        this.conexao = conexao;
        this.autorizacao = autorizacao;
    }

    @GetMapping("/formas-pagamento")
    public ResponseEntity<Object> listarFormasPagamento() {
        autorizacao.exigirDiretoria(conexao);
        PagamentoAtividade model = new PagamentoAtividade();
        return ResponseEntity.ok(model.listarFormasPagamento(conexao));
    }

    @GetMapping("/escoteiros")
    public ResponseEntity<Object> pesquisarEscoteiros(
            @RequestParam(value = "busca", required = false, defaultValue = "") String busca) {
        autorizacao.exigirDiretoria(conexao);
        Escoteiro escoteiroModel = new Escoteiro();
        PagamentoAtividade atvModel = new PagamentoAtividade();
        PagamentoMensalidade mensModel = new PagamentoMensalidade();

        String termo = busca != null ? busca.trim() : "";
        if (termo.matches("[\\d.\\-]+")) {
            termo = termo.replaceAll("\\D", "");
        }

        List<Escoteiro> lista = escoteiroModel.pesquisar(conexao, termo);
        if (!termo.isBlank() && lista.isEmpty()) {
            throw new NaoEncontradoException("Escoteiro não localizado no sistema.");
        }

        Map<Integer, int[]> pendenciasAtv = atvModel.resumirPendenciasPorEscoteiro(conexao);
        List<ItemResumoEscoteiro> resposta = new ArrayList<>();

        for (Escoteiro e : lista) {
            int[] contadores = pendenciasAtv.getOrDefault(e.getEsc_cod(), new int[]{0, 0});
            boolean mensInadimplente = e.getEsc_sit_fin() == 1
                    || mensModel.possuiMensalidadeVencidaEmAberto(conexao, e.getEsc_cod());

            resposta.add(new ItemResumoEscoteiro(
                    e.getEsc_cod(),
                    e.getEsc_sit_fin(),
                    e.getMembro(),
                    e.getPatrulha(),
                    mensInadimplente,
                    contadores[0],
                    contadores[1]
            ));
        }

        resposta.sort((a, b) -> {
            if (a.atividadesAtrasadas() != b.atividadesAtrasadas()) {
                return Integer.compare(b.atividadesAtrasadas(), a.atividadesAtrasadas());
            }
            if (a.atividadesAbertas() != b.atividadesAbertas()) {
                return Integer.compare(b.atividadesAbertas(), a.atividadesAbertas());
            }
            String nomeA = a.membro() != null && a.membro().getNome() != null ? a.membro().getNome() : "";
            String nomeB = b.membro() != null && b.membro().getNome() != null ? b.membro().getNome() : "";
            return nomeA.compareToIgnoreCase(nomeB);
        });

        return ResponseEntity.ok(resposta);
    }

    @GetMapping("/escoteiros/{escCod}")
    public ResponseEntity<Object> consultarAtividades(@PathVariable int escCod) {
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
        return ResponseEntity.ok(resumo);
    }

    @GetMapping("/{crCod}/lancamentos")
    public ResponseEntity<Object> listarLancamentos(@PathVariable int crCod) {
        autorizacao.exigirDiretoria(conexao);
        PagamentoAtividade model = new PagamentoAtividade();
        PagamentoAtividade pag = model.buscarPorConta(conexao, crCod);
        if (pag == null) {
            throw new NaoEncontradoException("Inscrição em atividade não encontrada.");
        }
        return ResponseEntity.ok(model.listarLancamentos(conexao, crCod));
    }

    @PostMapping("/{crCod}/registrar")
    @Transactional
    public ResponseEntity<Object> registrar(@PathVariable int crCod, @RequestBody Map<String, Object> corpo) {
        autorizacao.exigirPerfil(conexao, List.of(UsuarioLogado.ADMIN, UsuarioLogado.FINANCEIRO));

        BigDecimal valorRecebido = lerBigDecimal(corpo, "valorRecebido");
        BigDecimal creditoUtilizado = lerBigDecimal(corpo, "creditoUtilizado");
        int formaPagamentoCod = Integer.parseInt(corpo.get("formaPagamentoCod").toString());
        LocalDate dataPagamento = lerData(corpo, "dataPagamento");

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
        String obsDivergencia = montarObservacaoDivergencia(creditoFinal, diferenca);
        pag.setObservacao(obsDivergencia);

        if (!pag.registrar(conexao)) {
            throw new IllegalStateException("Não foi possível registrar o pagamento da atividade.");
        }

        Map<String, Object> recibo = montarRecibo(crCod, pag, saldoDevido, creditoFinal, valorFinal, diferenca, obsDivergencia, dataPagamento);
        Map<String, Object> resposta = new LinkedHashMap<>();
        resposta.put("mensagem", "Pagamento da atividade " + pag.getAtividadeNome() + " registrado com sucesso!");
        resposta.put("recibo", recibo);
        return ResponseEntity.ok(resposta);
    }

    @DeleteMapping("/{crCod}/estornar")
    @Transactional
    public ResponseEntity<Object> estornar(
            @PathVariable int crCod,
            @RequestParam(value = "pgCod", required = false, defaultValue = "0") int pgCod) {
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

        return ResponseEntity.ok(Map.of("mensagem", msg));
    }

    private BigDecimal lerBigDecimal(Map<String, Object> corpo, String chave) {
        Object val = corpo.get(chave);
        return (val != null && !val.toString().isBlank())
                ? new BigDecimal(val.toString())
                : BigDecimal.ZERO;
    }

    private LocalDate lerData(Map<String, Object> corpo, String chave) {
        Object val = corpo.get(chave);
        return (val != null && !val.toString().isBlank())
                ? LocalDate.parse(val.toString())
                : LocalDate.now();
    }

    private String montarObservacaoDivergencia(BigDecimal creditoFinal, BigDecimal diferenca) {
        StringBuilder obs = new StringBuilder();
        if (creditoFinal.compareTo(BigDecimal.ZERO) > 0) {
            obs.append("Desconto de R$ ").append(creditoFinal).append(" aplicado do saldo excedente. ");
        }
        if (diferenca.compareTo(BigDecimal.ZERO) < 0) {
            obs.append("Pagamento parcial da atividade. Diferença de R$ ").append(diferenca.abs()).append(" mantida em aberto.");
        } else if (diferenca.compareTo(BigDecimal.ZERO) > 0) {
            obs.append("Pagamento superior ao valor da atividade. Excedente de R$ ").append(diferenca).append(" registrado como crédito.");
        }
        return obs.toString().trim();
    }

    private Map<String, Object> montarRecibo(
            int crCod,
            PagamentoAtividade pag,
            BigDecimal saldoDevido,
            BigDecimal creditoFinal,
            BigDecimal valorFinal,
            BigDecimal diferenca,
            String obsDivergencia,
            LocalDate dataPagamento
    ) {
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
        recibo.put("dataPagamento", dataPagamento.toString());
        return recibo;
    }
}