import { useEffect, useState } from 'react'
import Alert from '@mui/material/Alert'
import Box from '@mui/material/Box'
import Button from '@mui/material/Button'
import ResumoVerificacao from '@/components/contribuicoes/ResumoVerificacao'
import SeletorDeEscoteiro from '@/components/contribuicoes/SeletorDeEscoteiro'
import CampoCpf from '@/components/formulario/CampoCpf'
import CampoTexto from '@/components/formulario/CampoTexto'
import DialogoConfirmacao from '@/components/formulario/DialogoConfirmacao'
import { BotaoCancelar, BotaoOk } from '@/components/formulario/Botoes'
import Grade, { type ColunaGrade } from '@/components/grade/Grade'
import type CandidatosContribuicao from '@/data/models/CandidatosContribuicao'
import type Contribuicao from '@/data/models/Contribuicao'
import type Escoteiro from '@/data/models/Escoteiro'
import type VerificacaoContribuicao from '@/data/models/VerificacaoContribuicao'
import {
  buscarCandidatosContribuicao,
  buscarContribuicoes,
  registrarContribuicoes,
  verificarContribuicao,
} from '@/data/services/contribuicao'
import { combinaBusca } from '@/data/utils/busca'
import { formatarData } from '@/data/utils/datas'
import { mensagemDeErro } from '@/data/utils/erros'
import { formatarCpf } from '@/data/utils/formatadores'
import { cpfValido } from '@/data/utils/validadores'

type TipoDeSeletor = 'anjo' | 'adotado'

const LIMITE_DO_RESUMO = 6

const colunasContribuicoes: ColunaGrade<Contribuicao>[] = [
  { id: 'anjo', titulo: 'Anjo', valor: (c) => c.nome_anjo },
  { id: 'cpf_anjo', titulo: 'CPF do Anjo', valor: (c) => c.cpf_anjo, renderizar: (c) => formatarCpf(c.cpf_anjo) },
  { id: 'adotado', titulo: 'Adotado', valor: (c) => c.nome_adotado },
  { id: 'cpf_adotado', titulo: 'CPF do adotado', valor: (c) => c.cpf_adotado, renderizar: (c) => formatarCpf(c.cpf_adotado) },
  { id: 'desde', titulo: 'Desde', valor: (c) => c.con_dt, renderizar: (c) => formatarData(c.con_dt) },
]

const colunasAdotados: ColunaGrade<Escoteiro>[] = [
  { id: 'nome', titulo: 'Adotado', valor: (e) => e.membro.nome },
  { id: 'cpf', titulo: 'CPF', valor: (e) => e.membro.CPF, renderizar: (e) => formatarCpf(e.membro.CPF) },
  { id: 'patrulha', titulo: 'Patrulha', valor: (e) => e.patrulha.nome },
]

export default function PaginaContribuicoes() {
  const [cpfAnjo, setCpfAnjo] = useState('')
  const [cpfAdotado, setCpfAdotado] = useState('')
  const [verificacao, setVerificacao] = useState<VerificacaoContribuicao | null>(null)
  const [anjo, setAnjo] = useState<Escoteiro | null>(null)
  const [adotados, setAdotados] = useState<Escoteiro[]>([])
  const [contribuicoes, setContribuicoes] = useState<Contribuicao[]>([])
  const [busca, setBusca] = useState('')
  const [carregando, setCarregando] = useState(true)
  const [confirmando, setConfirmando] = useState(false)
  const [seletor, setSeletor] = useState<TipoDeSeletor | null>(null)
  const [candidatos, setCandidatos] = useState<CandidatosContribuicao | null>(null)
  const [carregandoCandidatos, setCarregandoCandidatos] = useState(false)
  const [erroDoSeletor, setErroDoSeletor] = useState('')
  const [erro, setErro] = useState('')
  const [aviso, setAviso] = useState('')

  const verificar = async (cpfDoAnjo: string, cpfDoAdotado: string) => {
    if (!cpfValido(cpfDoAnjo) || !cpfValido(cpfDoAdotado)) {
      setErro('Informe CPFs válidos para o Anjo e para o adotado.')
      return
    }
    setErro('')
    setAviso('')
    setVerificacao(null)
    setCarregando(true)
    try {
      setVerificacao(await verificarContribuicao(cpfDoAnjo, cpfDoAdotado))
    } catch (falha) {
      setErro(mensagemDeErro(falha, 'Não foi possível verificar os cadastros.'))
    } finally {
      setCarregando(false)
    }
  }

  const abrirSeletor = async (tipo: TipoDeSeletor) => {
    setSeletor(tipo)
    setErroDoSeletor('')
    setCarregandoCandidatos(true)
    try {
      setCandidatos(await buscarCandidatosContribuicao())
    } catch (falha) {
      setErroDoSeletor(mensagemDeErro(falha, 'Não foi possível carregar a lista de escoteiros.'))
    } finally {
      setCarregandoCandidatos(false)
    }
  }

  const selecionarDaLista = (escolhido: Escoteiro) => {
    const tipo = seletor
    setSeletor(null)
    setVerificacao(null)
    setErro('')
    if (tipo === 'anjo') {
      setCpfAnjo(escolhido.membro.CPF)
      return
    }
    setCpfAdotado(escolhido.membro.CPF)
    const cpfDoAnjo = anjo ? anjo.membro.CPF : cpfAnjo
    if (cpfValido(cpfDoAnjo)) verificar(cpfDoAnjo, escolhido.membro.CPF)
  }

  const adicionarALista = () => {
    if (!verificacao) return
    if (adotados.some((a) => a.esc_cod === verificacao.adotado.esc_cod)) {
      setErro(`${verificacao.adotado.membro.nome} já está na lista de adotados.`)
      return
    }
    setErro('')
    setAnjo(verificacao.anjo)
    setAdotados([...adotados, verificacao.adotado])
    setVerificacao(null)
    setCpfAdotado('')
  }

  const removerDaLista = (escCod: number) => {
    const restantes = adotados.filter((a) => a.esc_cod !== escCod)
    setAdotados(restantes)
    if (restantes.length === 0) setAnjo(null)
  }

  const cancelar = () => {
    setAnjo(null)
    setAdotados([])
    setVerificacao(null)
    setCpfAnjo('')
    setCpfAdotado('')
    setErro('')
  }

  const registrar = async () => {
    if (!anjo) return
    setCarregando(true)
    try {
      const registradas = await registrarContribuicoes({
        cpf_anjo: anjo.membro.CPF,
        cpfs_adotados: adotados.map((a) => a.membro.CPF),
      })
      setConfirmando(false)
      cancelar()
      setAviso(`${registradas.length} contribuição(ões) registrada(s) com sucesso.`)
      setContribuicoes(await buscarContribuicoes())
    } catch (falha) {
      setConfirmando(false)
      setErro(mensagemDeErro(falha, 'Não foi possível registrar a contribuição.'))
    } finally {
      setCarregando(false)
    }
  }

  useEffect(() => {
    let ativo = true
    buscarContribuicoes()
      .then((lista) => ativo && setContribuicoes(lista))
      .catch((falha: unknown) => ativo && setErro(mensagemDeErro(falha, 'Não foi possível carregar as contribuições.')))
      .finally(() => ativo && setCarregando(false))
    return () => {
      ativo = false
    }
  }, [])

  const cpfDoAnjoAtual = anjo ? anjo.membro.CPF : cpfAnjo
  const escoteirosDoSeletor =
    seletor === 'anjo'
      ? (candidatos?.anjos ?? []).filter((e) => e.membro.CPF !== cpfAdotado)
      : (candidatos?.adotados ?? []).filter(
          (e) => e.membro.CPF !== cpfDoAnjoAtual && !adotados.some((a) => a.esc_cod === e.esc_cod),
        )

  const buscaAtiva = busca.trim() !== ''
  const casaComAnjo = (c: Contribuicao) => combinaBusca(busca, c.nome_anjo, c.cpf_anjo, c.anjo)
  const casaComAdotado = (c: Contribuicao) => combinaBusca(busca, c.nome_adotado, c.cpf_adotado, c.adotado)
  const filtradas = buscaAtiva ? contribuicoes.filter((c) => casaComAnjo(c) || casaComAdotado(c)) : contribuicoes

  const resumo: string[] = []
  if (buscaAtiva) {
    const anjosVistos = new Set<number>()
    for (const c of filtradas) {
      if (!casaComAnjo(c) || anjosVistos.has(c.anjo)) continue
      anjosVistos.add(c.anjo)
      const total = filtradas.filter((x) => x.anjo === c.anjo).length
      resumo.push(`Anjo ${c.nome_anjo}: ${total} ${total === 1 ? 'adotado' : 'adotados'}`)
    }
    for (const c of filtradas) {
      if (casaComAdotado(c)) resumo.push(`${c.nome_adotado} é adotado por ${c.nome_anjo}`)
    }
  }

  return (
    <main className="pagina">
      <header>
        <h1>Contribuições (Anjos)</h1>
        <p className="subtitulo">Registre quais escoteiros terão as mensalidades custeadas por um Anjo</p>
      </header>

      {erro && <Alert severity="error">{erro}</Alert>}
      {aviso && <Alert severity="success">{aviso}</Alert>}

      <Box
        component="form"
        className="cartao"
        onSubmit={(evento) => {
          evento.preventDefault()
          verificar(cpfDoAnjoAtual, cpfAdotado)
        }}
        sx={{ display: 'grid', gap: 2 }}
      >
        <h2>Nova contribuição</h2>
        <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', md: '1fr 1fr' }, gap: 2 }}>
          <Box sx={{ display: 'flex', gap: 1, alignItems: 'flex-start' }}>
            <CampoCpf
              rotulo="CPF do Anjo"
              valor={cpfDoAnjoAtual}
              aoMudar={setCpfAnjo}
              desabilitado={anjo !== null}
              ajuda={anjo ? anjo.membro.nome : undefined}
              obrigatorio
            />
            <Button
              variant="outlined"
              size="small"
              sx={{ whiteSpace: 'nowrap', height: 40 }}
              disabled={anjo !== null}
              onClick={() => abrirSeletor('anjo')}
            >
              Escolher da lista
            </Button>
          </Box>
          <Box sx={{ display: 'flex', gap: 1, alignItems: 'flex-start' }}>
            <CampoCpf rotulo="CPF do adotado" valor={cpfAdotado} aoMudar={setCpfAdotado} obrigatorio />
            <Button variant="outlined" size="small" sx={{ whiteSpace: 'nowrap', height: 40 }} onClick={() => abrirSeletor('adotado')}>
              Escolher da lista
            </Button>
          </Box>
        </Box>
        <Box sx={{ display: 'flex', justifyContent: 'flex-end' }}>
          <BotaoOk rotulo="Verificar cadastros" carregando={carregando} />
        </Box>

        {verificacao && (
          <>
            <ResumoVerificacao anjo={verificacao.anjo} adotado={verificacao.adotado} />
            <Box sx={{ display: 'flex', justifyContent: 'flex-end', gap: 1 }}>
              <BotaoCancelar rotulo="Descartar" aoClicar={() => setVerificacao(null)} />
              <BotaoOk rotulo="Adicionar à lista" tipo="button" aoClicar={adicionarALista} />
            </Box>
          </>
        )}
      </Box>

      <SeletorDeEscoteiro
        aberto={seletor !== null}
        titulo={seletor === 'anjo' ? 'Escolher o Anjo (contribuinte)' : 'Escolher o adotado'}
        descricao={
          seletor === 'anjo'
            ? 'Escoteiros que ainda não são adotados. Clique em uma linha para selecionar.'
            : 'Escoteiros sem Anjo e que não são Anjos de outros. Clique em uma linha para selecionar.'
        }
        escoteiros={escoteirosDoSeletor}
        carregando={carregandoCandidatos}
        erro={erroDoSeletor}
        aoSelecionar={selecionarDaLista}
        aoFechar={() => setSeletor(null)}
      />

      {adotados.length > 0 && anjo && (
        <Grade
          titulo={`Adotados de ${anjo.membro.nome}`}
          subtitulo="Confirme a lista para registrar a contribuição"
          colunas={colunasAdotados}
          linhas={adotados}
          chave={(a) => a.esc_cod}
          acoes={(a) => <BotaoCancelar rotulo="Remover" aoClicar={() => removerDaLista(a.esc_cod)} />}
          rodape={(
            <Box sx={{ display: 'flex', gap: 1 }}>
              <BotaoCancelar rotulo="Cancelar registro" aoClicar={cancelar} />
              <BotaoOk rotulo="Registrar contribuição" tipo="button" aoClicar={() => setConfirmando(true)} />
            </Box>
          )}
          linhasPorPagina={5}
        />
      )}

      <DialogoConfirmacao
        aberto={confirmando}
        titulo="Confirmar contribuição"
        rotuloConfirmar="Registrar"
        mensagem={`${anjo?.membro.nome ?? ''} passará a custear as mensalidades de: ${adotados.map((a) => a.membro.nome).join(', ')}. Confirma a operação?`}
        aoConfirmar={registrar}
        aoCancelar={() => setConfirmando(false)}
        carregando={carregando}
      />

      <section className="cartao" style={{ display: 'grid', gap: 12 }}>
        <h2>Contribuições registradas</h2>
        <CampoTexto
          rotulo="Pesquisar por nome, CPF ou matrícula"
          valor={busca}
          aoMudar={setBusca}
          placeholder="Digite o nome ou CPF de um Anjo ou de um adotado"
          ajuda="Ao pesquisar um Anjo, aparecem todos os adotados dele. Ao pesquisar um adotado, aparece o seu Anjo."
        />
        {buscaAtiva && filtradas.length === 0 && (
          <Alert severity="warning">Nenhuma contribuição encontrada para a pesquisa.</Alert>
        )}
        {resumo.length > 0 && (
          <Alert severity="info">
            <Box component="ul" sx={{ m: 0, pl: 2 }}>
              {resumo.slice(0, LIMITE_DO_RESUMO).map((linha) => (
                <li key={linha}>{linha}</li>
              ))}
              {resumo.length > LIMITE_DO_RESUMO && <li>e mais {resumo.length - LIMITE_DO_RESUMO}</li>}
            </Box>
          </Alert>
        )}
        <Grade
          subtitulo={`${filtradas.length} ${filtradas.length === 1 ? 'adotado' : 'adotados'} com Anjo${buscaAtiva ? ' na pesquisa' : ''}`}
          colunas={colunasContribuicoes}
          linhas={filtradas}
          chave={(c) => c.con_cod}
          carregando={carregando}
          textoVazio="Nenhuma contribuição registrada."
          linhasPorPagina={10}
        />
      </section>
    </main>
  )
}
