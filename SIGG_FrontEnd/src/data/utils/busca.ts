function normalizar(texto: string): string {
  return texto.normalize('NFD').replace(/[̀-ͯ]/g, '').toLowerCase()
}

// Busca dos filtros das grades: ignora acento e maiúscula; com 3+ dígitos, compara também só os números (CPF com ou sem máscara)
export function combinaBusca(termo: string, ...campos: (string | number | null | undefined)[]): boolean {
  const procurado = normalizar(termo.trim())
  if (!procurado) return true
  const digitos = termo.replace(/\D/g, '')
  return campos.some((campo) => {
    if (campo === null || campo === undefined) return false
    const texto = String(campo)
    return normalizar(texto).includes(procurado) || (digitos.length >= 3 && texto.replace(/\D/g, '').includes(digitos))
  })
}
