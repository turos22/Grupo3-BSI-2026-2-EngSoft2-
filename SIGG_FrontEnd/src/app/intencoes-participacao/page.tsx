import { useEffect, useState } from 'react'
import Alert from '@mui/material/Alert'
import Box from '@mui/material/Box'
import Checkbox from '@mui/material/Checkbox'
import Chip from '@mui/material/Chip'
import Dialog from '@mui/material/Dialog'
import DialogActions from '@mui/material/DialogActions'
import DialogContent from '@mui/material/DialogContent'
import DialogTitle from '@mui/material/DialogTitle'
import LinearProgress from '@mui/material/LinearProgress'
import Grade, { type ColunaGrade } from '@/components/grade/Grade'
import { BotaoCancelar, BotaoOk } from '@/components/formulario/Botoes'
import CampoTexto from '@/components/formulario/CampoTexto'
import { useSessao } from '@/data/contexts/SessaoContexto'
import type Escoteiro from '@/data/models/Escoteiro'
import type { OpcaoIntencao, PainelIntencao } from '@/data/models/IntencaoParticipacao'
import {
  buscarEscoteirosParaIntencao,
  buscarPainelDeIntencao,
  registrarIntencao,
} from '@/data/services/intencoesParticipacao'
import { AUTENTICACAO_ATIVA } from '@/data/services/sessao'
import { formatarData } from '@/data/utils/datas'
import { mensagemDeErro } from '@/data/utils/erros'
import { formatarCpf, formatarMoeda } from '@/data/utils/formatadores'
import { DIRETORIA } from '@/data/utils/permissoes'

const colunasEscoteiros: ColunaGrade<Escoteiro>[] = [
  { id: 'nome', titulo: 'Nome', valor: (e) => e.membro.nome },
  { id: 'cpf', titulo: 'CPF', valor: (e) => e.membro.CPF, renderizar: (e) => formatarCpf(e.membro.CPF) },
  { id: 'matricula', titulo: 'Matrícula', valor: (e) => e.esc_cod, largura: 120 },
  { id: 'patrulha', titulo: 'Patrulha', valor: (e) => e.patrulha?.nome },
]

function podeEscolher(opcao: OpcaoIntencao): boolean {
  return !opcao.registrada && opcao.vagasRestantes !== 0
}

function prazoDe(opcao: OpcaoIntencao): string {
  return opcao.atividade.prazoInteresse ?? opcao.atividade.data
}

export default function PaginaIntencoesParticipacao() {
  const { usuario } = useSessao()
  const diretoria = !AUTENTICACAO_ATIVA || (usuario?.perfis.some((perfil) => DIRETORIA.includes(perfil)) ?? false)

  const [busca, setBusca] = useState('')
  const [escoteiros, setEscoteiros] = useState<Escoteiro[]>([])
  const [carregandoEscoteiros, setCarregandoEscoteiros] = useState(!diretoria)
  const [memCod, setMemCod] = useState<number | null>(null)
  const [painel, setPainel] = useState<PainelIntencao | null>(null)
  const [carregandoPainel, setCarregandoPainel] = useState(false)
  const [escolhidas, setEscolhidas] = useState<number[]>([])
  const [confirmando, setConfirmando] = useState(false)
  const [registrando, setRegistrando] = useState(false)
  const [erro, setErro] = useState('')
  const [sucesso, setSucesso] = useState('')

  async function abrirPainel(codigo: number) {
    setMemCod(codigo)
    setEscolhidas([])
    setCarregandoPainel(true)
    try {
      setPainel(await buscarPainelDeIntencao(codigo))
      setErro('')
    } catch (falha) {
      setPainel(null)
      setErro(mensagemDeErro(falha, 'Não foi possível carregar as atividades pagas.'))
    } finally {
      setCarregandoPainel(false)
    }
  }

  // Escoteiro e responsável: o próprio cadastro e os dependentes; com um só, já abre as atividades
  useEffect(() => {
    if (diretoria) return
    let ativo = true
    buscarEscoteirosParaIntencao()
      .then((lista) => {
        if (!ativo) return
        setEscoteiros(lista)
        if (lista.length === 1) abrirPainel(lista[0].membro.mem_cod)
      })
      .catch((falha: unknown) => ativo && setErro(mensagemDeErro(falha, 'Não foi possível carregar seus escoteiros.')))
      .finally(() => ativo && setCarregandoEscoteiros(false))
    return () => {
      ativo = false
    }
  }, [diretoria])

  async function pesquisar() {
    setPainel(null)
    setMemCod(null)
    setEscoteiros([])
    setSucesso('')
    setCarregandoEscoteiros(true)
    try {
      const lista = await buscarEscoteirosParaIntencao(busca.trim())
      setEscoteiros(lista)
      setErro('')
      if (lista.length === 1) await abrirPainel(lista[0].membro.mem_cod)
    } catch (falha) {
      setErro(mensagemDeErro(falha, 'Não foi possível pesquisar o escoteiro.'))
    } finally {
      setCarregandoEscoteiros(false)
    }
  }

  function alternar(opcao: OpcaoIntencao) {
    if (!podeEscolher(opcao)) return
    const codigo = opcao.atividade.codigo
    setEscolhidas((atual) => (atual.includes(codigo) ? atual.filter((c) => c !== codigo) : [...atual, codigo]))
  }

  // Passos 5 e 6: o escoteiro confirma e o sistema registra; nos fluxos 4.1 e 4.2 mostra a mensagem do backend
  async function confirmar() {
    if (!painel) return
    setRegistrando(true)
    try {
      const resultado = await registrarIntencao(painel.mem_cod, escolhidas)
      setConfirmando(false)
      setSucesso(`${resultado.mensagem}. O pagamento é feito com o financeiro.`)
      await abrirPainel(painel.mem_cod)
    } catch (falha) {
      setConfirmando(false)
      // recarrega antes de mostrar o erro (vagas e situação podem ter mudado) para a mensagem não ser apagada
      await abrirPainel(painel.mem_cod)
      setErro(mensagemDeErro(falha, 'Não foi possível registrar a intenção de participação.'))
    } finally {
      setRegistrando(false)
    }
  }

  const opcoes = painel?.atividades ?? []
  const selecionadas = opcoes.filter((o) => escolhidas.includes(o.atividade.codigo))
  const total = selecionadas.reduce((soma, o) => soma + o.atividade.valor, 0)

  const colunasAtividades: ColunaGrade<OpcaoIntencao>[] = [
    {
      id: 'escolha',
      titulo: '',
      largura: 48,
      ordenavel: false,
      renderizar: (o) => (
        <Checkbox
          size="small"
          checked={o.registrada || escolhidas.includes(o.atividade.codigo)}
          disabled={!podeEscolher(o) || !painel?.mensalidadeEmDia}
          slotProps={{ input: { 'aria-label': `Escolher ${o.atividade.nome}` } }}
        />
      ),
    },
    { id: 'nome', titulo: 'Atividade', valor: (o) => o.atividade.nome },
    { id: 'data', titulo: 'Data', valor: (o) => o.atividade.data, renderizar: (o) => formatarData(o.atividade.data) },
    { id: 'local', titulo: 'Local', valor: (o) => o.atividade.local ?? '—' },
    {
      id: 'valor',
      titulo: 'Valor',
      valor: (o) => o.atividade.valor,
      renderizar: (o) => formatarMoeda(o.atividade.valor),
      alinhamento: 'right',
    },
    { id: 'prazo', titulo: 'Interesse até', valor: prazoDe, renderizar: (o) => formatarData(prazoDe(o)) },
    {
      id: 'vagas',
      titulo: 'Vagas',
      valor: (o) => o.vagasRestantes,
      renderizar: (o) => (o.vagasRestantes === null ? 'Sem limite' : o.vagasRestantes),
      alinhamento: 'center',
    },
    {
      id: 'situacao',
      titulo: 'Situação',
      valor: (o) => (o.registrada ? 'Registrada' : o.vagasRestantes === 0 ? 'Esgotada' : 'Disponível'),
      renderizar: (o) =>
        o.registrada ? (
          <Chip size="small" color="success" label="Interesse registrado" />
        ) : o.vagasRestantes === 0 ? (
          <Chip size="small" label="Vagas esgotadas" />
        ) : (
          <Chip size="small" color="info" variant="outlined" label="Disponível" />
        ),
    },
  ]

  return (
    <main className="pagina">
      <header>
        <h1>Manifestar interesse</h1>
        <p className="subtitulo">Registro da intenção de participação em atividades pagas (acampamentos, passeios, eventos)</p>
      </header>

      {erro && <Alert severity="error" onClose={() => setErro('')}>{erro}</Alert>}
      {sucesso && <Alert severity="success" onClose={() => setSucesso('')}>{sucesso}</Alert>}

      {diretoria && (
        <Box
          component="form"
          onSubmit={(evento) => {
            evento.preventDefault()
            pesquisar()
          }}
          sx={{ display: 'flex', gap: 1, alignItems: 'flex-start' }}
        >
          <CampoTexto rotulo="CPF, nome ou matrícula do escoteiro" valor={busca} aoMudar={setBusca} obrigatorio />
          <BotaoOk rotulo="Buscar" carregando={carregandoEscoteiros} />
        </Box>
      )}

      {!diretoria && carregandoEscoteiros && <LinearProgress />}
      {!diretoria && !carregandoEscoteiros && !erro && escoteiros.length === 0 && (
        <Alert severity="info">Não há escoteiros vinculados ao seu cadastro.</Alert>
      )}

      {escoteiros.length > 1 && (
        <Grade
          titulo={diretoria ? 'Escoteiros encontrados' : 'Seus escoteiros'}
          subtitulo="Selecione o escoteiro que vai participar"
          colunas={colunasEscoteiros}
          linhas={escoteiros}
          chave={(e) => e.membro.mem_cod}
          chaveSelecionada={memCod}
          aoClicarLinha={(e) => {
            setSucesso('')
            abrirPainel(e.membro.mem_cod)
          }}
          linhasPorPagina={5}
        />
      )}

      {carregandoPainel && !painel && <LinearProgress />}

      {painel && (
        <>
          {!painel.mensalidadeEmDia && (
            <Alert severity="warning">
              A mensalidade de {painel.nome} está atrasada. Regularize a situação com o financeiro para manifestar interesse em
              atividades pagas.
            </Alert>
          )}
          <Grade
            titulo={`Atividades pagas abertas — ${painel.nome}`}
            subtitulo={
              painel.patrulha
                ? `Patrulha ${painel.patrulha}. Marque as atividades em que tem intenção de participar.`
                : 'Marque as atividades em que tem intenção de participar.'
            }
            colunas={colunasAtividades}
            linhas={opcoes}
            chave={(o) => o.atividade.codigo}
            aoClicarLinha={painel.mensalidadeEmDia ? alternar : undefined}
            carregando={carregandoPainel}
            textoVazio="Nenhuma atividade paga aberta para manifestação de interesse no momento."
            linhasPorPagina={10}
            rodape={
              <BotaoOk
                rotulo={escolhidas.length > 1 ? `Registrar intenção (${escolhidas.length})` : 'Registrar intenção'}
                tipo="button"
                desabilitado={escolhidas.length === 0 || !painel.mensalidadeEmDia}
                aoClicar={() => {
                  setErro('')
                  setSucesso('')
                  setConfirmando(true)
                }}
              />
            }
          />
        </>
      )}

      <Dialog open={confirmando} onClose={() => !registrando && setConfirmando(false)} maxWidth="xs" fullWidth>
        <DialogTitle>Confirmar intenção de participação</DialogTitle>
        <DialogContent>
          <p>
            {painel?.nome} tem intenção de participar de:
          </p>
          <ul>
            {selecionadas.map((o) => (
              <li key={o.atividade.codigo}>
                {o.atividade.nome} ({formatarData(o.atividade.data)}) — {formatarMoeda(o.atividade.valor)}
              </li>
            ))}
          </ul>
          <p>
            Total: <strong>{formatarMoeda(total)}</strong>, a pagar com o financeiro.
          </p>
        </DialogContent>
        <DialogActions sx={{ px: 3, pb: 2 }}>
          <BotaoCancelar aoClicar={() => setConfirmando(false)} desabilitado={registrando} />
          <BotaoOk rotulo="Confirmar" tipo="button" aoClicar={confirmar} carregando={registrando} />
        </DialogActions>
      </Dialog>
    </main>
  )
}
