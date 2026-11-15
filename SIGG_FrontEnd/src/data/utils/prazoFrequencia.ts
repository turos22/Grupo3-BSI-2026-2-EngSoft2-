// Mesmo prazo do backend (Chamada.PRAZO_DIAS): lançar e cancelar só para chamadas de hoje até 10 dias atrás
export const PRAZO_DIAS = 10

function diaLocal(deslocamento: number): string {
  const dia = new Date()
  dia.setDate(dia.getDate() + deslocamento)
  return dia.toLocaleDateString('sv-SE')
}

export const HOJE = diaLocal(0)
export const LIMITE_LANCAMENTO = diaLocal(-PRAZO_DIAS)

export function dentroDoPrazo(data: string): boolean {
  return data >= LIMITE_LANCAMENTO && data <= HOJE
}
