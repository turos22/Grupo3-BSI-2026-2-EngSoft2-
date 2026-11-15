import type { Chamada, ResumoChamada, StatusPresenca } from '@/data/models/Chamada'
import type Tropa from '@/data/models/Tropa'
import { requisitar } from '@/data/services/api'

export function listarChamadas(tropa?: number, ano?: number): Promise<ResumoChamada[]> {
  const filtros = new URLSearchParams()
  if (tropa) filtros.set('tropa', String(tropa))
  if (ano) filtros.set('ano', String(ano))
  const texto = filtros.toString()
  return requisitar<ResumoChamada[]>(texto ? `/frequencias?${texto}` : '/frequencias')
}

// Tropa do chefe logado (404 se a pessoa não é chefe com gestão em andamento)
export function buscarMinhaTropa(): Promise<Tropa> {
  return requisitar<Tropa>('/frequencias/minha-tropa')
}

export function consultarChamada(tropa: number, data: string): Promise<Chamada> {
  return requisitar<Chamada>(`/frequencias/chamada?tropa=${tropa}&data=${data}`)
}

export interface ItemParaGravar {
  esc_cod: number
  status: StatusPresenca
}

function corpo(tropa: number, data: string, itens: ItemParaGravar[]) {
  return JSON.stringify({ tropa: { cod: tropa }, data, itens })
}

export function registrarChamada(tropa: number, data: string, itens: ItemParaGravar[]): Promise<Chamada> {
  return requisitar<Chamada>('/frequencias/chamada', { method: 'POST', body: corpo(tropa, data, itens) })
}

// Cancelar não apaga: os registros ficam "pendente de lançamento" até a chamada ser lançada de novo
export function cancelarChamada(tropa: number, data: string): Promise<{ mensagem: string }> {
  return requisitar(`/frequencias/chamada/cancelar?tropa=${tropa}&data=${data}`, { method: 'POST' })
}
