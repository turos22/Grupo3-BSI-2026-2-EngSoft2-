import { useEffect, useState, type FormEvent } from 'react'
import Alert from '@mui/material/Alert'
import Box from '@mui/material/Box'
import Chip from '@mui/material/Chip'
import Dialog from '@mui/material/Dialog'
import DialogActions from '@mui/material/DialogActions'
import DialogContent from '@mui/material/DialogContent'
import DialogTitle from '@mui/material/DialogTitle'
import FormControlLabel from '@mui/material/FormControlLabel'
import Stack from '@mui/material/Stack'
import Switch from '@mui/material/Switch'
import Grade, { type ColunaGrade } from '@/components/grade/Grade'
import { BotaoCancelar, BotaoOk } from '@/components/formulario/Botoes'
import DialogoConfirmacao from '@/components/formulario/DialogoConfirmacao'
import CampoData from '@/components/formulario/CampoData'
import CampoInteiro from '@/components/formulario/CampoInteiro'
import CampoMoeda from '@/components/formulario/CampoMoeda'
import CampoTexto from '@/components/formulario/CampoTexto'
import CampoTextoLongo from '@/components/formulario/CampoTextoLongo'
import type AtividadePaga from '@/data/models/AtividadePaga'
import {
  alterarAtividadePaga,
  buscarAtividadesPagas,
  excluirAtividadePaga,
  ofertarAtividadePaga,
} from '@/data/services/atividadesPagas'
import { combinaBusca } from '@/data/utils/busca'
import { formatarData } from '@/data/utils/datas'
import { mensagemDeErro } from '@/data/utils/erros'
import { formatarMoeda } from '@/data/utils/formatadores'
import { lerMoeda } from '@/data/utils/validadores'
import {
  errosDoBackendAtividadePaga,
  validarAtividadePaga,
  type CampoAtividadePaga,
  type DadosFormAtividadePaga,
  type DatasOriginais,
  type ErrosAtividadePaga,
} from '@/data/utils/validacaoAtividadePaga'

// Data de hoje no fuso do navegador, no formato do backend ("AAAA-MM-DD")
function hojeIso(): string {
  const agora = new Date()
  const mes = String(agora.getMonth() + 1).padStart(2, '0')
  const dia = String(agora.getDate()).padStart(2, '0')
  return `${agora.getFullYear()}-${mes}-${dia}`
}

function estaAberta(atividade: AtividadePaga): boolean {
  return (atividade.prazoInteresse ?? atividade.data) >= hojeIso()
}

const FORM_VAZIO: DadosFormAtividadePaga = {
  nome: '',
  descricao: '',
  data: '',
  prazoInteresse: '',
  local: '',
  valor: '',
  vagas: null,
}

function paraFormulario(atividade: AtividadePaga): DadosFormAtividadePaga {
  return {
    nome: atividade.nome,
    descricao: atividade.descricao ?? '',
    data: atividade.data,
    prazoInteresse: atividade.prazoInteresse ?? '',
    local: atividade.local ?? '',
    valor: atividade.valor.toFixed(2).replace('.', ','),
    vagas: atividade.vagas,
  }
}

// A data mínima do campo é hoje, mas na alteração a data já gravada continua aceita
function minimoDaData(hoje: string, original?: string): string {
  return original && original < hoje ? original : hoje
}

const colunas: ColunaGrade<AtividadePaga>[] = [
  { id: 'nome', titulo: 'Atividade', valor: (a) => a.nome },
  { id: 'data', titulo: 'Data', valor: (a) => a.data, renderizar: (a) => formatarData(a.data) },
  { id: 'local', titulo: 'Local', valor: (a) => a.local ?? '—' },
  { id: 'valor', titulo: 'Valor', valor: (a) => a.valor, renderizar: (a) => formatarMoeda(a.valor), alinhamento: 'right' },
  { id: 'vagas', titulo: 'Vagas', valor: (a) => a.vagas, renderizar: (a) => a.vagas ?? '—', alinhamento: 'center' },
  {
    id: 'prazo',
    titulo: 'Interesse até',
    valor: (a) => a.prazoInteresse,
    renderizar: (a) => (a.prazoInteresse ? formatarData(a.prazoInteresse) : '—'),
  },
  {
    id: 'situacao',
    titulo: 'Situação',
    valor: (a) => (estaAberta(a) ? 'Aberta' : 'Encerrada'),
    renderizar: (a) =>
      estaAberta(a) ? <Chip size="small" color="success" label="Aberta" /> : <Chip size="small" label="Encerrada" />,
  },
]

export default function PaginaAtividadesPagas() {
  const [atividades, setAtividades] = useState<AtividadePaga[]>([])
  const [somenteAbertas, setSomenteAbertas] = useState(false)
  const [carregando, setCarregando] = useState(true)
  const [erro, setErro] = useState('')
  const [sucesso, setSucesso] = useState('')
  const [busca, setBusca] = useState('')
  const [selecionadaCod, setSelecionadaCod] = useState<number | null>(null)
  // Atividade sendo alterada; null = oferta nova
  const [editando, setEditando] = useState<AtividadePaga | null>(null)
  const [confirmarExclusao, setConfirmarExclusao] = useState(false)
  const [excluindo, setExcluindo] = useState(false)

  const [modalAberto, setModalAberto] = useState(false)
  const [salvando, setSalvando] = useState(false)
  const [erroModal, setErroModal] = useState('')
  const [dados, setDados] = useState<DadosFormAtividadePaga>(FORM_VAZIO)
  // Padrão de validação do Bootstrap: os erros aparecem ao tentar enviar e, a partir daí, acompanham a digitação
  const [validado, setValidado] = useState(false)
  const [errosBackend, setErrosBackend] = useState<ErrosAtividadePaga>({})

  // Passo 2: busca e exibe as atividades pagas já cadastradas
  useEffect(() => {
    let ativo = true
    buscarAtividadesPagas(somenteAbertas)
      .then((lista) => {
        if (!ativo) return
        setAtividades(lista)
        setErro('')
      })
      .catch((falha: unknown) => ativo && setErro(mensagemDeErro(falha, 'Não foi possível carregar as atividades pagas.')))
      .finally(() => ativo && setCarregando(false))
    return () => {
      ativo = false
    }
  }, [somenteAbertas])

  async function recarregar() {
    try {
      setAtividades(await buscarAtividadesPagas(somenteAbertas))
    } catch (falha) {
      setErro(mensagemDeErro(falha, 'Não foi possível carregar as atividades pagas.'))
    }
  }

  const selecionada = atividades.find((a) => a.codigo === selecionadaCod) ?? null
  const filtradas = atividades.filter((a) => combinaBusca(busca, a.nome, a.local, a.descricao))

  // Passos 3 e 4: solicita o cadastro e apresenta os campos
  function abrirNovaOferta() {
    abrirFormulario(null)
  }

  function abrirAlteracao(atividade: AtividadePaga) {
    setSelecionadaCod(atividade.codigo)
    abrirFormulario(atividade)
  }

  function abrirFormulario(atividade: AtividadePaga | null) {
    setEditando(atividade)
    setDados(atividade ? paraFormulario(atividade) : FORM_VAZIO)
    setValidado(false)
    setErrosBackend({})
    setErroModal('')
    setSucesso('')
    setModalAberto(true)
  }

  // Passos 5 e 6: confirma a oferta (ou a alteração); o backend valida e registra
  async function salvar(evento: FormEvent) {
    evento.preventDefault()
    setErroModal('')
    setErrosBackend({})
    setValidado(true)
    if (Object.keys(validarAtividadePaga(dados, hojeIso(), datasOriginais)).length > 0) return
    setSalvando(true)
    const corpo = {
      nome: dados.nome.trim(),
      descricao: dados.descricao.trim() || null,
      data: dados.data,
      local: dados.local.trim(),
      valor: lerMoeda(dados.valor) ?? 0,
      vagas: dados.vagas,
      prazoInteresse: dados.prazoInteresse,
    }
    try {
      if (editando) {
        const alterada = await alterarAtividadePaga(editando.codigo, corpo)
        setSucesso(`Atividade "${alterada.nome}" alterada com sucesso!`)
      } else {
        const nova = await ofertarAtividadePaga(corpo)
        setSucesso(`Atividade "${nova.nome}" ofertada com sucesso! Ela já está disponível para manifestação de interesse.`)
      }
      setModalAberto(false)
      await recarregar()
    } catch (falha) {
      // Fluxos alternativos 6.1 e 6.3: mostra a mensagem e continua no formulário (passo 4)
      const mensagem = mensagemDeErro(falha, editando ? 'Não foi possível alterar a atividade.' : 'Não foi possível ofertar a atividade.')
      const porCampo = errosDoBackendAtividadePaga(mensagem)
      if (porCampo) setErrosBackend(porCampo)
      else setErroModal(mensagem)
    } finally {
      setSalvando(false)
    }
  }

  // Só pode excluir a oferta que ainda não tem inscrições ou pagamentos; o backend confere
  async function excluir() {
    if (!selecionada) return
    setExcluindo(true)
    setErro('')
    setSucesso('')
    try {
      await excluirAtividadePaga(selecionada.codigo)
      setSucesso(`Atividade "${selecionada.nome}" excluída.`)
      setSelecionadaCod(null)
      await recarregar()
    } catch (falha) {
      setErro(mensagemDeErro(falha, 'Não foi possível excluir a atividade.'))
    } finally {
      setExcluindo(false)
      setConfirmarExclusao(false)
    }
  }

  const hoje = hojeIso()
  const datasOriginais: DatasOriginais | undefined = editando
    ? { data: editando.data, prazoInteresse: editando.prazoInteresse ?? '' }
    : undefined
  const erros: ErrosAtividadePaga = {
    ...(validado ? validarAtividadePaga(dados, hoje, datasOriginais) : {}),
    ...errosBackend,
  }

  function mudar<C extends CampoAtividadePaga>(campo: C) {
    return (valor: DadosFormAtividadePaga[C]) => {
      setDados((atual) => ({ ...atual, [campo]: valor }))
      // o erro do backend (ex.: atividade repetida) some quando o campo é corrigido
      if (errosBackend.nome || errosBackend.data) setErrosBackend({})
    }
  }

  return (
    <main className="pagina">
      <header>
        <h1>Atividades pagas</h1>
        <p className="subtitulo">Oferta de novas atividades pagas (acampamentos, passeios, eventos) para manifestação de interesse dos escoteiros</p>
      </header>

      {erro && <Alert severity="error" sx={{ mb: 2 }} onClose={() => setErro('')}>{erro}</Alert>}
      {sucesso && <Alert severity="success" sx={{ mb: 2 }} onClose={() => setSucesso('')}>{sucesso}</Alert>}

      {confirmarExclusao && selecionada && (
        <DialogoConfirmacao
          aberto
          mensagem={`Deseja realmente excluir a atividade "${selecionada.nome}"? Atividades com inscrições ou pagamentos não podem ser excluídas.`}
          aoConfirmar={excluir}
          aoCancelar={() => setConfirmarExclusao(false)}
          carregando={excluindo}
        />
      )}

      <Box sx={{ display: 'flex', alignItems: 'center', flexWrap: 'wrap', gap: 2, mb: 1 }}>
        <Box sx={{ width: { xs: '100%', sm: 360 } }}>
          <CampoTexto rotulo="Buscar" valor={busca} aoMudar={setBusca} placeholder="Nome, local ou descrição" />
        </Box>
        <FormControlLabel
          control={<Switch checked={somenteAbertas} onChange={(e) => setSomenteAbertas(e.target.checked)} />}
          label="Mostrar só as abertas para manifestação de interesse"
        />
      </Box>

      <Grade
        titulo="Atividades pagas cadastradas"
        subtitulo={`${filtradas.length} de ${atividades.length} ${atividades.length === 1 ? 'atividade' : 'atividades'}`}
        colunas={colunas}
        linhas={filtradas}
        chave={(a) => a.codigo}
        carregando={carregando}
        textoVazio="Nenhuma atividade paga encontrada."
        linhasPorPagina={10}
        aoClicarLinha={(a) => setSelecionadaCod(a.codigo)}
        aoDuploClique={abrirAlteracao}
        chaveSelecionada={selecionadaCod}
        rodape={
          <Box sx={{ display: 'flex', justifyContent: 'flex-end', gap: '10px', flexWrap: 'wrap' }}>
            <BotaoOk rotulo="Ofertar nova atividade" tipo="button" aoClicar={abrirNovaOferta} />
            <BotaoOk
              rotulo="Alterar"
              tipo="button"
              desabilitado={!selecionada}
              aoClicar={() => selecionada && abrirAlteracao(selecionada)}
            />
            <BotaoCancelar rotulo="Excluir" desabilitado={!selecionada} aoClicar={() => setConfirmarExclusao(true)} />
          </Box>
        }
      />

      <Dialog open={modalAberto} onClose={() => !salvando && setModalAberto(false)} maxWidth="sm" fullWidth>
        <form onSubmit={salvar} noValidate>
          <DialogTitle>{editando ? 'Alterar atividade paga' : 'Ofertar nova atividade paga'}</DialogTitle>
          <DialogContent>
            <Stack spacing={2} sx={{ mt: 1 }}>
              {erroModal && <Alert severity="error">{erroModal}</Alert>}
              <CampoTexto rotulo="Nome" valor={dados.nome} aoMudar={mudar('nome')} tamanhoMaximo={30} obrigatorio autoFocus erro={erros.nome} />
              <CampoTextoLongo
                rotulo="Descrição"
                valor={dados.descricao}
                aoMudar={mudar('descricao')}
                tamanhoMaximo={200}
                linhas={2}
                erro={erros.descricao}
              />
              <Box sx={{ display: 'grid', gap: 2, gridTemplateColumns: { xs: '1fr', sm: '1fr 1fr' } }}>
                <CampoData rotulo="Data de realização" valor={dados.data} aoMudar={mudar('data')} minimo={minimoDaData(hoje, datasOriginais?.data)} obrigatorio erro={erros.data} />
                <CampoData
                  rotulo="Interesse até"
                  valor={dados.prazoInteresse}
                  aoMudar={mudar('prazoInteresse')}
                  minimo={minimoDaData(hoje, datasOriginais?.prazoInteresse)}
                  maximo={dados.data || undefined}
                  obrigatorio
                  erro={erros.prazoInteresse}
                  ajuda="Prazo final para manifestação de interesse"
                />
              </Box>
              <CampoTexto rotulo="Local" valor={dados.local} aoMudar={mudar('local')} tamanhoMaximo={100} obrigatorio erro={erros.local} />
              <Box sx={{ display: 'grid', gap: 2, gridTemplateColumns: { xs: '1fr', sm: '1fr 1fr' } }}>
                <CampoMoeda rotulo="Valor" valor={dados.valor} aoMudar={mudar('valor')} obrigatorio erro={erros.valor} />
                <CampoInteiro rotulo="Número de vagas" valor={dados.vagas} aoMudar={mudar('vagas')} obrigatorio erro={erros.vagas} />
              </Box>
            </Stack>
          </DialogContent>
          <DialogActions sx={{ px: 3, pb: 2 }}>
            <BotaoCancelar aoClicar={() => setModalAberto(false)} desabilitado={salvando} />
            <BotaoOk rotulo={editando ? 'Salvar alterações' : 'Confirmar oferta'} carregando={salvando} />
          </DialogActions>
        </form>
      </Dialog>
    </main>
  )
}
