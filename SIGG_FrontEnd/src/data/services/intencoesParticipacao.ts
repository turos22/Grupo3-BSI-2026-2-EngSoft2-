import { requisitar } from '@/data/services/api'
import type Escoteiro from '@/data/models/Escoteiro'
import type { PainelIntencao, ResultadoIntencao } from '@/data/models/IntencaoParticipacao'

// Diretoria pesquisa (busca obrigatória); escoteiro e responsável recebem a si mesmos e os dependentes
export function buscarEscoteirosParaIntencao(busca?: string): Promise<Escoteiro[]> {
  const parametro = busca ? `?busca=${encodeURIComponent(busca)}` : ''
  return requisitar<Escoteiro[]>(`/intencoes-participacao/escoteiros${parametro}`)
}

export function buscarPainelDeIntencao(memCod: number): Promise<PainelIntencao> {
  return requisitar<PainelIntencao>(`/intencoes-participacao/escoteiros/${memCod}`)
}

export function registrarIntencao(memCod: number, atividades: number[]): Promise<ResultadoIntencao> {
  return requisitar<ResultadoIntencao>('/intencoes-participacao', {
    method: 'POST',
    body: JSON.stringify({ memCod, atividades }),
  })
}
