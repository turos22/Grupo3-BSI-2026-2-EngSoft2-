import { useEffect, useState } from 'react'
import Alert from '@mui/material/Alert'
import Box from '@mui/material/Box'
import Chip from '@mui/material/Chip'
import Stack from '@mui/material/Stack'
import Formulario from '@/components/formulario/Formulario'
import type { ItemChamada, StatusPresenca } from '@/data/models/Chamada'
import type Tropa from '@/data/models/Tropa'
import { consultarChamada } from '@/data/services/frequencia'
import { formatarData } from '@/data/utils/datas'
import { mensagemDeErro } from '@/data/utils/erros'

const ROTULO: Record<StatusPresenca, string> = { P: 'Presente', F: 'Falta', J: 'Justificada' }
const COR: Record<StatusPresenca, 'success' | 'error' | 'warning'> = { P: 'success', F: 'error', J: 'warning' }

interface VerChamadaProps {
  tropa: Tropa
  data: string
  aoFechar: () => void
}

// Consulta de um lançamento já feito, só leitura: para conferir antes de decidir se cancela
export default function VerChamada({ tropa, data, aoFechar }: VerChamadaProps) {
  const [itens, setItens] = useState<ItemChamada[] | null>(null)
  const [erro, setErro] = useState('')

  useEffect(() => {
    let ativo = true
    consultarChamada(tropa.cod, data)
      .then((chamada) => ativo && setItens(chamada.itens))
      .catch((falha: unknown) => ativo && setErro(mensagemDeErro(falha, 'Não foi possível carregar as presenças.')))
    return () => {
      ativo = false
    }
  }, [tropa.cod, data])

  const contar = (status: StatusPresenca) => itens?.filter((i) => i.status === status).length ?? 0

  return (
    <Formulario titulo={`Presenças: ${tropa.nome} em ${formatarData(data)}`} aoConfirmar={aoFechar} rotuloOk="Fechar" largura="md">
      {erro && <Alert severity="error">{erro}</Alert>}
      {itens && (
        <>
          <Stack direction="row" spacing={1} useFlexGap sx={{ flexWrap: 'wrap' }}>
            <Chip label={`${contar('P')} presentes`} color="success" variant="outlined" />
            <Chip label={`${contar('F')} faltas`} color="error" variant="outlined" />
            <Chip label={`${contar('J')} justificadas`} color="warning" variant="outlined" />
          </Stack>
          {itens.map((item) => (
            <Box key={item.esc_cod} sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 2, flexWrap: 'wrap' }}>
              <Box>
                <strong>{item.nome}</strong>
                <Box component="span" sx={{ ml: 1, opacity: 0.75, fontSize: '0.85rem' }}>{item.patrulha}</Box>
              </Box>
              {item.status
                ? <Chip size="small" label={ROTULO[item.status]} color={COR[item.status]} />
                : <Chip size="small" label="Sem registro" variant="outlined" />}
            </Box>
          ))}
        </>
      )}
    </Formulario>
  )
}
