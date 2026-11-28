import { requisitar } from '@/data/services/api'
import type AtividadePaga from '@/data/models/AtividadePaga'

export type DadosAtividadePaga = Omit<AtividadePaga, 'codigo'>

export function buscarAtividadesPagas(somenteAbertas = false): Promise<AtividadePaga[]> {
  return requisitar<AtividadePaga[]>(`/atividades-pagas${somenteAbertas ? '?abertas=true' : ''}`)
}

export function ofertarAtividadePaga(atividade: DadosAtividadePaga): Promise<AtividadePaga> {
  return requisitar<AtividadePaga>('/atividades-pagas', {
    method: 'POST',
    body: JSON.stringify(atividade),
  })
}

export function alterarAtividadePaga(codigo: number, atividade: DadosAtividadePaga): Promise<AtividadePaga> {
  return requisitar<AtividadePaga>(`/atividades-pagas/${codigo}`, {
    method: 'PUT',
    body: JSON.stringify(atividade),
  })
}

export function excluirAtividadePaga(codigo: number): Promise<{ mensagem: string }> {
  return requisitar(`/atividades-pagas/${codigo}`, { method: 'DELETE' })
}
