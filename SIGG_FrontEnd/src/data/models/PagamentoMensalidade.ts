export type SituacaoParcelaMensalidade = 'ABERTA' | 'PARCIAL' | 'ATRASADA' | 'PAGA'

export interface FormaPagamento {
    tp_cod: number
    tp_desc: string
}

export interface LancamentoPagamentoMensalidade {
    pgCod: number
    dataPagamento: string | null
    valorPago: number
    descricao: string | null
    formaPagamentoCod: number
    formaPagamentoDesc: string | null
    podeEstornar: boolean
}

export interface PagamentoMensalidade {
    id: number
    crCod: number
    mensCod: number
    escCod: number
    escoteiroNome: string
    escoteiroCpf: string
    referencia: string
    dataVencimento: string | null
    dataPagamento: string | null
    valorDevido: number
    valorPago: number
    saldo: number
    formaPagamentoCod: number
    formaPagamentoDesc: string | null
    situacao: SituacaoParcelaMensalidade
    inadimplente: boolean
    observacao: string | null
}

export interface SituacaoFinanceiraEscoteiro {
    esc_cod: number
    mem_cod: number
    nome: string
    cpf: string
    patrulha: string
    categoria: string
    valorCategoria: number
    inadimplente: boolean
    creditoDisponivel?: number
    mensalidades: PagamentoMensalidade[]
}

export interface ReciboPagamentoMensalidade {
    numero: string
    escoteiro: string
    cpf: string
    matricula: number
    referencia: string
    valorDevido: number
    creditoUtilizado?: number
    valorPago: number
    diferenca: number
    observacao: string
    dataPagamento: string
    regularizado: boolean
}