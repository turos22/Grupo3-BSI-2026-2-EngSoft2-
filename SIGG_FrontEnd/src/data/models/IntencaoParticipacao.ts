import type AtividadePaga from '@/data/models/AtividadePaga'

// Formatos de /intencoes-participacao (IntencaoParticipacaoController) — RF_F6

// Uma atividade paga aberta, vista por um escoteiro (models/OpcaoIntencao.java)
export interface OpcaoIntencao {
  atividade: AtividadePaga
  inscritos: number
  vagasRestantes: number | null // null = sem limite de vagas (atividades antigas)
  registrada: boolean // o escoteiro já manifestou interesse
}

// GET /intencoes-participacao/escoteiros/{memCod}
export interface PainelIntencao {
  mem_cod: number
  esc_cod: number
  nome: string
  patrulha: string | null
  mensalidadeEmDia: boolean
  atividades: OpcaoIntencao[]
}

// Resposta do POST /intencoes-participacao
export interface ResultadoIntencao {
  mensagem: string
  intencoes: { codigo: number; escCod: number; atividade: AtividadePaga }[]
}
