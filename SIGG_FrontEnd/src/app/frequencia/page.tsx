import { useEffect, useState } from 'react'
import Alert from '@mui/material/Alert'
import Chip from '@mui/material/Chip'
import Box from '@mui/material/Box'
import { BotaoCancelar, BotaoOk } from '@/components/formulario/Botoes'
import CampoInteiro from '@/components/formulario/CampoInteiro'
import CampoSelecao from '@/components/formulario/CampoSelecao'
import DialogoConfirmacao from '@/components/formulario/DialogoConfirmacao'
import FormChamada from '@/components/frequencia/FormChamada'
import VerChamada from '@/components/frequencia/VerChamada'
import Grade, { type ColunaGrade } from '@/components/grade/Grade'
import { useSessao } from '@/data/contexts/SessaoContexto'
import type { ResumoChamada } from '@/data/models/Chamada'
import type Tropa from '@/data/models/Tropa'
import {
  buscarMinhaTropa, cancelarChamada, listarChamadas, registrarChamada, type ItemParaGravar,
} from '@/data/services/frequencia'
import { AUTENTICACAO_ATIVA } from '@/data/services/sessao'
import { listarTropas } from '@/data/services/tropa'
import { formatarData } from '@/data/utils/datas'
import { mensagemDeErro } from '@/data/utils/erros'
import { dentroDoPrazo, PRAZO_DIAS } from '@/data/utils/prazoFrequencia'

const TODAS = 0
const ANO_ATUAL = new Date().getFullYear()

const colunas: ColunaGrade<ResumoChamada>[] = [
  { id: 'data', titulo: 'Data', valor: (c) => c.data, renderizar: (c) => formatarData(c.data) },
  { id: 'tropa', titulo: 'Tropa', valor: (c) => c.tropa.nome },
  {
    id: 'situacao',
    titulo: 'Situação',
    valor: (c) => (pendente(c) ? 'Pendente de lançamento' : 'Lançada'),
    renderizar: (c) => (pendente(c)
      ? <Chip size="small" label="Pendente de lançamento" color="warning" variant="outlined" />
      : <Chip size="small" label="Lançada" color="success" variant="outlined" />),
  },
  { id: 'presentes', titulo: 'Presentes', valor: (c) => c.presentes, renderizar: (c) => (pendente(c) ? '—' : `${c.presentes} de ${c.total}`) },
  {
    id: 'prazo',
    titulo: 'Prazo',
    valor: (c) => (!dentroDoPrazo(c.data) ? 'Prazo encerrado' : pendente(c) ? 'Lance de novo' : 'Ainda pode cancelar'),
  },
]

const chaveDe = (c: ResumoChamada) => `${c.tropa.cod}|${c.data}`

// Lançamento cancelado: os registros continuam, mas pendentes (C), esperando ser lançados de novo
const pendente = (c: ResumoChamada) => c.total === 0 && c.pendentes > 0

export default function PaginaFrequencia() {
  const { usuario } = useSessao()
  // O chefe só registra a tropa dele (o combo vem travado); presidente e admin escolhem qualquer uma
  const restrito = AUTENTICACAO_ATIVA && !usuario?.perfis.some((p) => p === 'PRESIDENTE' || p === 'ADMIN')
  const [tropas, setTropas] = useState<Tropa[]>([])
  const [tropaCod, setTropaCod] = useState<number>(TODAS)
  const [ano, setAno] = useState<number | null>(ANO_ATUAL)
  const [chamadas, setChamadas] = useState<ResumoChamada[]>([])
  const [selecionada, setSelecionada] = useState<string | null>(null)
  const [lancando, setLancando] = useState(false)
  const [cancelando, setCancelando] = useState(false)
  const [vendo, setVendo] = useState(false)
  const [editando, setEditando] = useState(false) // lançamento pendente: reabre a chamada daquela tropa e data
  const [pronto, setPronto] = useState(false)
  const [carregando, setCarregando] = useState(true)
  const [salvando, setSalvando] = useState(false)
  const [erro, setErro] = useState('')
  const [aviso, setAviso] = useState('')

  const tropa = tropas.find((t) => t.cod === tropaCod) ?? null
  const chamada = chamadas.find((c) => chaveDe(c) === selecionada) ?? null

  useEffect(() => {
    let ativo = true
    const trava = restrito ? buscarMinhaTropa().then((t) => t.cod) : Promise.resolve(TODAS)
    Promise.all([listarTropas(), trava])
      .then(([lista, inicial]) => {
        if (!ativo) return
        setTropas(lista)
        setTropaCod(inicial)
        setPronto(true)
      })
      .catch((falha: unknown) => {
        if (!ativo) return
        setErro(mensagemDeErro(falha, 'Não foi possível carregar as tropas.'))
        setCarregando(false)
      })
    return () => {
      ativo = false
    }
  }, [restrito])

  useEffect(() => {
    if (!pronto) return
    let ativo = true
    listarChamadas(tropaCod === TODAS ? undefined : tropaCod, ano ?? ANO_ATUAL)
      .then((lista) => ativo && setChamadas(lista))
      .catch((falha: unknown) => ativo && setErro(mensagemDeErro(falha, 'Não foi possível carregar as chamadas.')))
      .finally(() => ativo && setCarregando(false))
    return () => {
      ativo = false
    }
  }, [pronto, tropaCod, ano])

  async function executar(acao: () => Promise<unknown>, sucesso: string, falhaPadrao: string) {
    setSalvando(true)
    setErro('')
    setAviso('')
    try {
      await acao()
      setChamadas(await listarChamadas(tropaCod === TODAS ? undefined : tropaCod, ano ?? ANO_ATUAL))
      setAviso(sucesso)
      setLancando(false)
      setEditando(false)
    } catch (falha) {
      setErro(mensagemDeErro(falha, falhaPadrao))
    } finally {
      setSalvando(false)
      setCancelando(false)
    }
  }

  const lancar = (tropaAlvo: number) => (data: string, itens: ItemParaGravar[]) =>
    executar(() => registrarChamada(tropaAlvo, data, itens), 'Frequência lançada.', 'Não foi possível lançar a frequência.')

  // Duplo clique e botão: chamada pendente abre para editar; lançada abre só para ver
  const abrir = (c: ResumoChamada | null) => {
    if (!c) return
    if (pendente(c)) setEditando(true)
    else setVendo(true)
  }

  const cancelarLancamento = () => chamada
    ? executar(() => cancelarChamada(chamada.tropa.cod, chamada.data), 'Lançamento cancelado: a chamada ficou pendente de lançamento. Lance de novo pelo botão "Lançar frequência".', 'Não foi possível cancelar o lançamento.')
    : Promise.resolve()

  const podeCancelar = !!chamada && !pendente(chamada) && dentroDoPrazo(chamada.data)

  return (
    <main className="pagina">
      <header>
        <h1>Registro de frequência</h1>
        <p className="subtitulo">Duplo clique numa chamada mostra as presenças. Lance a chamada da tropa; se errar, cancele o lançamento e lance de novo (até {PRAZO_DIAS} dias da data)</p>
      </header>

      {erro && <Alert severity="error" onClose={() => setErro('')}>{erro}</Alert>}
      {aviso && <Alert severity="success" onClose={() => setAviso('')}>{aviso}</Alert>}

      <Box sx={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(220px, 320px))', gap: 2 }}>
        <CampoSelecao
          rotulo="Tropa"
          valor={tropaCod}
          opcoes={[...(restrito ? [] : [{ valor: TODAS, rotulo: 'Todas as tropas' }]), ...tropas.map((t) => ({ valor: t.cod, rotulo: t.nome }))]}
          aoMudar={(cod) => setTropaCod(cod ?? TODAS)}
          desabilitado={restrito}
          ajuda={restrito ? 'Você registra a frequência da sua tropa' : undefined}
        />
        <CampoInteiro rotulo="Ano" valor={ano} aoMudar={setAno} minimo={2000} maximo={2100} />
      </Box>

      {vendo && chamada && <VerChamada tropa={chamada.tropa} data={chamada.data} aoFechar={() => setVendo(false)} />}
      {lancando && tropa && (
        <FormChamada tropa={tropa} aoConfirmar={lancar(tropa.cod)} aoCancelar={() => setLancando(false)} carregando={salvando} />
      )}
      {editando && chamada && (
        <FormChamada
          tropa={chamada.tropa}
          dataFixa={chamada.data}
          aoConfirmar={lancar(chamada.tropa.cod)}
          aoCancelar={() => setEditando(false)}
          carregando={salvando}
        />
      )}

      <DialogoConfirmacao
        aberto={cancelando}
        titulo="Cancelar lançamento"
        rotuloConfirmar="Cancelar lançamento"
        rotuloCancelar="Voltar"
        mensagem={<>Cancelar o lançamento de <strong>{chamada?.tropa.nome}</strong> em <strong>{chamada ? formatarData(chamada.data) : ''}</strong>?</>}
        aoConfirmar={cancelarLancamento}
        aoCancelar={() => setCancelando(false)}
        carregando={salvando}
      />

      <Grade
        titulo="Chamadas registradas"
        subtitulo={`${chamadas.length} ${chamadas.length === 1 ? 'chamada' : 'chamadas'}`}
        colunas={colunas}
        linhas={chamadas}
        chave={chaveDe}
        carregando={carregando}
        textoVazio="Nenhuma chamada registrada neste período."
        aoClicarLinha={(c) => setSelecionada(chaveDe(c))}
        aoDuploClique={(c) => {
          setSelecionada(chaveDe(c))
          abrir(c)
        }}
        chaveSelecionada={selecionada}
        linhasPorPagina={10}
        rodape={(
          <Box sx={{ display: 'flex', justifyContent: 'flex-end', gap: '10px', flexWrap: 'wrap' }}>
            <BotaoOk rotulo="Lançar frequência" tipo="button" desabilitado={!tropa} aoClicar={() => setLancando(true)} />
            {chamada && pendente(chamada)
              ? <BotaoOk rotulo="Editar" tipo="button" desabilitado={!dentroDoPrazo(chamada.data)} aoClicar={() => abrir(chamada)} />
              : <BotaoOk rotulo="Ver presenças" tipo="button" desabilitado={!chamada} aoClicar={() => abrir(chamada)} />}
            <BotaoCancelar rotulo="Cancelar lançamento" desabilitado={!podeCancelar} aoClicar={() => setCancelando(true)} />
          </Box>
        )}
      />
      {!tropa && !restrito && <p className="subtitulo">Escolha uma tropa para lançar a frequência.</p>}
    </main>
  )
}
