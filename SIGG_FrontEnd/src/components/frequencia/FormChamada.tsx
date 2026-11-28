import { useEffect, useState } from 'react'
import Alert from '@mui/material/Alert'
import Box from '@mui/material/Box'
import ToggleButton from '@mui/material/ToggleButton'
import ToggleButtonGroup from '@mui/material/ToggleButtonGroup'
import CampoData from '@/components/formulario/CampoData'
import Formulario from '@/components/formulario/Formulario'
import type { ItemChamada, StatusPresenca } from '@/data/models/Chamada'
import type Tropa from '@/data/models/Tropa'
import { consultarChamada, type ItemParaGravar } from '@/data/services/frequencia'
import { HOJE, LIMITE_LANCAMENTO, PRAZO_DIAS } from '@/data/utils/prazoFrequencia'
import { mensagemDeErro } from '@/data/utils/erros'

interface FormChamadaProps {
  tropa: Tropa
  dataFixa?: string // presente = edição de um lançamento pendente: tropa e data já definidas
  aoConfirmar: (data: string, itens: ItemParaGravar[]) => void
  aoCancelar: () => void
  carregando?: boolean
}

// Lançamento de chamada. Não há edição: lançamento errado é cancelado na grade e lançado de novo.
export default function FormChamada({ tropa, dataFixa, aoConfirmar, aoCancelar, carregando }: FormChamadaProps) {
  const [data, setData] = useState(dataFixa ?? HOJE)
  const [itens, setItens] = useState<ItemChamada[]>([])
  const [status, setStatus] = useState<Record<number, StatusPresenca>>({})
  const [jaExiste, setJaExiste] = useState(false)
  const [erro, setErro] = useState('')

  const erroData = !data ? 'Informe a data'
    : data > HOJE ? 'A data não pode ser futura'
    : data < LIMITE_LANCAMENTO ? `Só é possível lançar frequência dos últimos ${PRAZO_DIAS} dias`
    : undefined

  useEffect(() => {
    if (erroData) return
    let ativo = true
    consultarChamada(tropa.cod, data)
      .then((chamada) => {
        if (!ativo) return
        setItens(chamada.itens)
        // só registro ativo impede lançar; lançamento cancelado (ativo = 0) pode ser lançado de novo
        setJaExiste(chamada.itens.some((i) => i.ativo === 1))
        // lançamento cancelado volta preenchido com o que estava; quem não tem registro começa presente
        setStatus(Object.fromEntries(
          chamada.itens.map((i) => [
            i.esc_cod,
            (i.status === 'P' || i.status === 'F' || i.status === 'J' ? i.status : 'P') as StatusPresenca,
          ])
        ))
        setErro('')
      })
      .catch((falha: unknown) => ativo && setErro(mensagemDeErro(falha, 'Não foi possível carregar os escoteiros.')))
    return () => {
      ativo = false
    }
  }, [tropa.cod, data, erroData])

  function confirmar() {
    if (itens.length > 0) aoConfirmar(data, itens.map((i) => ({ esc_cod: i.esc_cod, status: status[i.esc_cod] ?? 'P' })))
  }

  return (
    <Formulario
      titulo={dataFixa ? `Editar lançamento pendente: ${tropa.nome}` : `Lançar frequência: ${tropa.nome}`}
      rotuloOk={dataFixa ? 'Salvar' : 'Lançar'}
      aoConfirmar={confirmar}
      aoCancelar={aoCancelar}
      carregando={carregando}
      desabilitarOk={jaExiste || !!erroData || itens.length === 0}
      largura="md"
    >
      {erro && <Alert severity="error">{erro}</Alert>}
      {jaExiste && !erroData && (
        <Alert severity="warning">
          Já existe frequência desta tropa nesta data. Para refazer, cancele o lançamento na grade e lance de novo.
        </Alert>
      )}
      <CampoData
        rotulo="Data"
        valor={data}
        aoMudar={setData}
        desabilitado={!!dataFixa}
        minimo={LIMITE_LANCAMENTO}
        maximo={HOJE}
        obrigatorio
        erro={erroData}
        ajuda={`De hoje até ${PRAZO_DIAS} dias atrás`}
      />
      {itens.length === 0 && !erro && !erroData && <span>Nenhum escoteiro ativo nesta tropa.</span>}
      {!erroData && !jaExiste && itens.map((item) => (
        <Box key={item.esc_cod} sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 2, flexWrap: 'wrap' }}>
          <Box>
            <strong>{item.nome}</strong>
            <Box component="span" sx={{ ml: 1, opacity: 0.75, fontSize: '0.85rem' }}>
              {item.patrulha} · presença no ano: {item.percentual_ano.toLocaleString('pt-BR', { minimumFractionDigits: 1 })}%
            </Box>
          </Box>
          <ToggleButtonGroup
            exclusive
            size="small"
            value={status[item.esc_cod] ?? 'P'}
            onChange={(_, novo: StatusPresenca | null) => novo && setStatus((atual) => ({ ...atual, [item.esc_cod]: novo }))}
            aria-label={`Presença de ${item.nome}`}
          >
            <ToggleButton value="P" color="success">Presente</ToggleButton>
            <ToggleButton value="F" color="error">Falta</ToggleButton>
            <ToggleButton value="J" color="warning">Justificada</ToggleButton>
          </ToggleButtonGroup>
        </Box>
      ))}
    </Formulario>
  )
}
