package com.example.sigg_backend.models;

import com.example.sigg_backend.db.dao.PagamentoAtividadeDAO;
import com.example.sigg_backend.db.util.Conexao;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@SuppressWarnings("unused")
public class PagamentoAtividade {
    private int id;
    private int crCod;
    private int atvCod;
    private int escCod;
    private String escoteiroNome;
    private String escoteiroCpf;
    private String atividadeNome;
    private String atividadeDesc;
    private LocalDate dataAtividade;
    private LocalDate dataVencimento;
    private LocalDate dataPagamento;
    private BigDecimal valorDevido = BigDecimal.ZERO;
    private BigDecimal valorPago = BigDecimal.ZERO;
    private BigDecimal valorRecebido = BigDecimal.ZERO;
    private BigDecimal creditoUtilizado = BigDecimal.ZERO;
    private BigDecimal valorEstornado = BigDecimal.ZERO;
    private int formaPagamentoCod;
    private String formaPagamentoDesc;
    private String situacao;
    private boolean inadimplente;
    private boolean podeEstornar;
    private String observacao;

    public PagamentoAtividade() {
    }

    public String validar() {
        if (crCod <= 0)
            return "A conta a receber é obrigatória.";
        if (formaPagamentoCod <= 0)
            return "A forma de pagamento é obrigatória.";
        if (dataPagamento != null && dataPagamento.isAfter(LocalDate.now()))
            return "A data do pagamento não pode ser futura (superior à data atual).";
        BigDecimal recebido = valorRecebido != null ? valorRecebido : BigDecimal.ZERO;
        BigDecimal credito = creditoUtilizado != null ? creditoUtilizado : BigDecimal.ZERO;
        if (recebido.compareTo(BigDecimal.ZERO) < 0)
            return "O valor recebido não pode ser negativo.";
        if (credito.compareTo(BigDecimal.ZERO) < 0)
            return "O desconto de crédito não pode ser negativo.";
        if (recebido.compareTo(BigDecimal.ZERO) == 0 && credito.compareTo(BigDecimal.ZERO) == 0)
            return "O valor recebido deve ser maior que zero.";
        return null;
    }

    public BigDecimal saldoRestante() {
        BigDecimal saldo = valorDevido.subtract(valorPago).setScale(2, RoundingMode.HALF_UP);
        return saldo.signum() < 0 ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP) : saldo;
    }

    public BigDecimal creditoGerado() {
        BigDecimal exc = valorPago.subtract(valorDevido).setScale(2, RoundingMode.HALF_UP);
        return exc.signum() > 0 ? exc : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    }

    public BigDecimal verificarDivergencia(BigDecimal valorInformado) {
        if (valorInformado == null) return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        return valorInformado.subtract(saldoRestante()).setScale(2, RoundingMode.HALF_UP);
    }

    public boolean registrar(Conexao conexao) {
        return new PagamentoAtividadeDAO().gravar(conexao, this);
    }

    public boolean estornar(Conexao conexao) {
        return new PagamentoAtividadeDAO().apagar(conexao, this);
    }

    public PagamentoAtividade buscarPorConta(Conexao conexao, int crCod) {
        return new PagamentoAtividadeDAO().get(conexao, crCod);
    }

    public PagamentoAtividade[] listarPorEscoteiro(Conexao conexao, int escCod) {
        return new PagamentoAtividadeDAO().listarPorEscoteiro(conexao, escCod);
    }

    public List<PagamentoAtividade> listarTodos(Conexao conexao) {
        return new PagamentoAtividadeDAO().get(conexao);
    }

    public FormaPagamento[] listarFormasPagamento(Conexao conexao) {
        return new PagamentoAtividadeDAO().listarFormasPagamento(conexao);
    }

    public Map<Integer, int[]> resumirPendenciasPorEscoteiro(Conexao conexao) {
        return new PagamentoAtividadeDAO().resumirPendenciasPorEscoteiro(conexao);
    }

    public BigDecimal calcularCreditoDisponivel(Conexao conexao, int escCod) {
        return new PagamentoAtividadeDAO().calcularCreditoDisponivel(conexao, escCod);
    }

    public PagamentoAtividade[] listarLancamentos(Conexao conexao, int crCod) {
        return new PagamentoAtividadeDAO().listarLancamentosPorConta(conexao, crCod);
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public int getPgCod() { return id; }
    public void setPgCod(int pgCod) { this.id = pgCod; }

    public int getCrCod() { return crCod; }
    public void setCrCod(int crCod) { this.crCod = crCod; }

    public int getAtvCod() { return atvCod; }
    public void setAtvCod(int atvCod) { this.atvCod = atvCod; }

    public int getEscCod() { return escCod; }
    public void setEscCod(int escCod) { this.escCod = escCod; }

    public String getEscoteiroNome() { return escoteiroNome; }
    public void setEscoteiroNome(String escoteiroNome) { this.escoteiroNome = escoteiroNome; }

    public String getEscoteiroCpf() { return escoteiroCpf; }
    public void setEscoteiroCpf(String escoteiroCpf) { this.escoteiroCpf = escoteiroCpf; }

    public String getAtividadeNome() { return atividadeNome; }
    public void setAtividadeNome(String atividadeNome) { this.atividadeNome = atividadeNome; }

    public String getAtividadeDesc() { return atividadeDesc; }
    public void setAtividadeDesc(String atividadeDesc) { this.atividadeDesc = atividadeDesc; }

    public LocalDate getDataAtividade() { return dataAtividade; }
    public void setDataAtividade(LocalDate dataAtividade) { this.dataAtividade = dataAtividade; }

    public LocalDate getAtividadeData() { return dataAtividade; }
    public void setAtividadeData(LocalDate atividadeData) { this.dataAtividade = atividadeData; }

    public LocalDate getDataVencimento() { return dataVencimento; }
    public void setDataVencimento(LocalDate dataVencimento) { this.dataVencimento = dataVencimento; }

    public LocalDate getDataPagamento() { return dataPagamento; }
    public void setDataPagamento(LocalDate dataPagamento) { this.dataPagamento = dataPagamento; }

    public BigDecimal getValorDevido() { return valorDevido; }
    public void setValorDevido(BigDecimal valorDevido) {
        this.valorDevido = valorDevido != null ? valorDevido : BigDecimal.ZERO;
    }

    public BigDecimal getValorAtividade() { return valorDevido; }
    public void setValorAtividade(BigDecimal valorAtividade) {
        this.valorDevido = valorAtividade != null ? valorAtividade : BigDecimal.ZERO;
    }

    public BigDecimal getValorPago() { return valorPago; }
    public void setValorPago(BigDecimal valorPago) {
        this.valorPago = valorPago != null ? valorPago : BigDecimal.ZERO;
    }

    public BigDecimal getValorRecebido() { return valorRecebido; }
    public void setValorRecebido(BigDecimal valorRecebido) {
        this.valorRecebido = valorRecebido != null ? valorRecebido : BigDecimal.ZERO;
    }

    public BigDecimal getCreditoUtilizado() { return creditoUtilizado; }
    public void setCreditoUtilizado(BigDecimal creditoUtilizado) {
        this.creditoUtilizado = creditoUtilizado != null ? creditoUtilizado : BigDecimal.ZERO;
    }

    public BigDecimal getValorEstornado() { return valorEstornado; }
    public void setValorEstornado(BigDecimal valorEstornado) {
        this.valorEstornado = valorEstornado != null ? valorEstornado : BigDecimal.ZERO;
    }

    public int getFormaPagamentoCod() { return formaPagamentoCod; }
    public void setFormaPagamentoCod(int formaPagamentoCod) { this.formaPagamentoCod = formaPagamentoCod; }

    public String getFormaPagamentoDesc() { return formaPagamentoDesc; }
    public void setFormaPagamentoDesc(String formaPagamentoDesc) { this.formaPagamentoDesc = formaPagamentoDesc; }

    public String getSituacao() { return situacao; }
    public void setSituacao(String situacao) { this.situacao = situacao; }

    public boolean isInadimplente() { return inadimplente; }
    public void setInadimplente(boolean inadimplente) { this.inadimplente = inadimplente; }

    public boolean isPodeEstornar() { return podeEstornar; }
    public void setPodeEstornar(boolean podeEstornar) { this.podeEstornar = podeEstornar; }

    public String getObservacao() { return observacao; }
    public void setObservacao(String observacao) { this.observacao = observacao; }

    public String getDescricao() { return observacao; }
    public void setDescricao(String descricao) { this.observacao = descricao; }

    public BigDecimal getSaldo() { return saldoRestante(); }
}