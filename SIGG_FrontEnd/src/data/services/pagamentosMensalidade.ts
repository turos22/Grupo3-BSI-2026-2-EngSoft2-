import { requisitar } from '@/data/services/api'
import type Escoteiro from '@/data/models/Escoteiro'
import type { SituacaoEscoteiro } from '@/data/models/SituacaoEscoteiro'
import type {
    FormaPagamento,
    LancamentoPagamentoMensalidade,
    ReciboPagamentoMensalidade,
    SituacaoFinanceiraEscoteiro,
} from '@/data/models/PagamentoMensalidade'

export function buscarFormasPagamento(): Promise<FormaPagamento[]> {
    return requisitar<FormaPagamento[]>('/pagamentos-mensalidade/formas-pagamento')
}

export function pesquisarEscoteirosMensalidade(busca: string): Promise<Escoteiro[]> {
    return requisitar<Escoteiro[]>(`/pagamentos-mensalidade/escoteiros?busca=${encodeURIComponent(busca)}`)
}

export function listarSituacoes(tropa?: number): Promise<SituacaoEscoteiro[]> {
    const query = tropa !== undefined ? `?tropa=${tropa}` : ''
    return requisitar<SituacaoEscoteiro[]>(`/pagamentos-mensalidade/situacoes${query}`)
}

export function marcarInadimplente(escCod: number): Promise<{ mensagem: string }> {
    return requisitar(`/pagamentos-mensalidade/escoteiros/${escCod}/inadimplente`, {
        method: 'POST',
    })
}

export function consultarSituacaoFinanceiraEscoteiro(escCod: number): Promise<SituacaoFinanceiraEscoteiro> {
    return requisitar<SituacaoFinanceiraEscoteiro>(`/pagamentos-mensalidade/escoteiros/${escCod}`)
}

export function listarLancamentosMensalidade(crCod: number): Promise<LancamentoPagamentoMensalidade[]> {
    return requisitar<LancamentoPagamentoMensalidade[]>(`/pagamentos-mensalidade/${crCod}/lancamentos`)
}

export function registrarPagamentoMensalidade(
    crCod: number,
    dados: {
        valorRecebido: number
        formaPagamentoCod: number
        dataPagamento: string
        creditoUtilizado?: number
    }
): Promise<{ mensagem: string; recibo: ReciboPagamentoMensalidade }> {
    return requisitar(`/pagamentos-mensalidade/${crCod}/registrar`, {
        method: 'POST',
        body: JSON.stringify(dados),
    })
}

export function estornarPagamentoMensalidade(crCod: number, pgCod?: number): Promise<{ mensagem: string }> {
    const sufixo = pgCod ? `?pgCod=${pgCod}` : ''
    return requisitar(`/pagamentos-mensalidade/${crCod}/estornar${sufixo}`, {
        method: 'DELETE',
    })
}