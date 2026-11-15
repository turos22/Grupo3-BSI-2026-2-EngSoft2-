import { useEffect, useState } from 'react'
import Alert from '@mui/material/Alert'
import Box from '@mui/material/Box'
import { BotaoCancelar, BotaoOk } from '@/components/formulario/Botoes'
import CampoInteiro from '@/components/formulario/CampoInteiro'
import CampoSelecao from '@/components/formulario/CampoSelecao'
import DialogoConfirmacao from '@/components/formulario/DialogoConfirmacao'
import FormChamada from '@/components/frequencia/FormChamada'
import Grade, { type ColunaGrade } from '@/components/grade/Grade'
import { useSessao } from '@/data/contexts/SessaoContexto'
import type { ResumoChamada } from '@/data/models/Chamada'
import type Tropa from '@/data/models/Tropa'
import {
  buscarMinhaTropa, excluirChamada, listarChamadas, registrarChamada, type ItemParaGravar,
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
  { id: 'presentes', titulo: 'Presentes', valor: (c) => c.presentes, renderizar: (c) => `${c.presentes} de ${c.total}` },
  { id: 'prazo', titulo: 'Cancelamento', valor: (c) => (dentroDoPrazo(c.data) ? 'Ainda pode cancelar' : 'Prazo encerrado') },
]

const chaveDe = (c: ResumoChamada) => `${c.tropa.cod}|${c.data}`

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
    } catch (falha) {
      setErro(mensagemDeErro(falha, falhaPadrao))
    } finally {
      setSalvando(false)
      setCancelando(false)
    }
  }

  const lancar = (data: string, itens: ItemParaGravar[]) =>
    executar(() => registrarChamada(tropaCod, data, itens), 'Frequência lançada.', 'Não foi possível lançar a frequência.')

  const cancelarLancamento = () => chamada
    ? executar(() => excluirChamada(chamada.tropa.cod, chamada.data), 'Lançamento cancelado. Você pode lançar de novo.', 'Não foi possível cancelar o lançamento.')
    : Promise.resolve()

  const podeCancelar = !!chamada && dentroDoPrazo(chamada.data)

  return (
    <main className="pagina">
      <header>
        <h1>Registro de frequência</h1>
        <p className="subtitulo">Lance a chamada da tropa; se errar, cancele o lançamento e lance de novo (até {PRAZO_DIAS} dias da data)</p>
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

      {lancando && tropa && (
        <FormChamada tropa={tropa} aoConfirmar={lancar} aoCancelar={() => setLancando(false)} carregando={salvando} />
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
        chaveSelecionada={selecionada}
        linhasPorPagina={10}
        rodape={(
          <Box sx={{ display: 'flex', justifyContent: 'flex-end', gap: '10px', flexWrap: 'wrap' }}>
            <BotaoOk rotulo="Lançar frequência" tipo="button" desabilitado={!tropa} aoClicar={() => setLancando(true)} />
            <BotaoCancelar rotulo="Cancelar lançamento" desabilitado={!podeCancelar} aoClicar={() => setCancelando(true)} />
          </Box>
        )}
      />
      {!tropa && !restrito && <p className="subtitulo">Escolha uma tropa para lançar a frequência.</p>}
    </main>
  )
}
