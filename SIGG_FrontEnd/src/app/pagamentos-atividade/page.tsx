import { useEffect, useMemo, useState } from 'react'
import { useNavigate } from 'react-router'
import Alert from '@mui/material/Alert'
import Box from '@mui/material/Box'
import Button from '@mui/material/Button'
import Card from '@mui/material/Card'
import CardContent from '@mui/material/CardContent'
import Checkbox from '@mui/material/Checkbox'
import Chip from '@mui/material/Chip'
import Dialog from '@mui/material/Dialog'
import DialogActions from '@mui/material/DialogActions'
import DialogContent from '@mui/material/DialogContent'
import DialogTitle from '@mui/material/DialogTitle'
import Divider from '@mui/material/Divider'
import FormControlLabel from '@mui/material/FormControlLabel'
import MenuItem from '@mui/material/MenuItem'
import Stack from '@mui/material/Stack'
import TextField from '@mui/material/TextField'
import Tooltip from '@mui/material/Tooltip'
import Typography from '@mui/material/Typography'
import Grade, { type ColunaGrade } from '@/components/grade/Grade'
import { BotaoCancelar, BotaoOk } from '@/components/formulario/Botoes'
import CampoSelecao from '@/components/formulario/CampoSelecao'
import CampoTexto from '@/components/formulario/CampoTexto'
import DialogoConfirmacao from '@/components/formulario/DialogoConfirmacao'
import type Escoteiro from '@/data/models/Escoteiro'
import type Tropa from '@/data/models/Tropa'
import type { FormaPagamento } from '@/data/models/PagamentoMensalidade'
import type {
    AtividadesFinanceiroEscoteiro,
    LancamentoPagamentoAtividade,
    PagamentoAtividade,
    ReciboPagamentoAtividade,
    SituacaoPagamentoAtividade,
} from '@/data/models/PagamentoAtividade'
import {
    buscarFormasPagamentoAtividade,
    consultarAtividadesEscoteiro,
    estornarPagamentoAtividade,
    listarLancamentosAtividade,
    pesquisarEscoteirosAtividade,
    registrarPagamentoAtividade,
} from '@/data/services/pagamentosAtividade'
import { useParametrizacao } from '@/data/contexts/ParametrizacaoContexto'
import { listarTropas } from '@/data/services/tropa'
import { formatarData } from '@/data/utils/datas'
import { mensagemDeErro } from '@/data/utils/erros'
import { formatarCpf, formatarMoeda } from '@/data/utils/formatadores'

const COR_SITUACAO: Record<SituacaoPagamentoAtividade, 'success' | 'error' | 'warning' | 'default'> = {
    PAGA: 'success',
    ATRASADA: 'error',
    PARCIAL: 'warning',
    ABERTA: 'default',
}

const ROTULO_SITUACAO: Record<SituacaoPagamentoAtividade, string> = {
    PAGA: 'Paga',
    ATRASADA: 'Atrasada',
    PARCIAL: 'Parcial',
    ABERTA: 'Em aberto',
}

const TODAS = 0

function dataHojeIso(): string {
    const hoje = new Date()
    const ano = hoje.getFullYear()
    const mes = String(hoje.getMonth() + 1).padStart(2, '0')
    const dia = String(hoje.getDate()).padStart(2, '0')
    return `${ano}-${mes}-${dia}`
}

function obterValorAtividade(a?: PagamentoAtividade | null): number {
    if (!a) return 0
    return Number(a.valorAtividade ?? a.valorDevido ?? 0)
}

function obterDataAtividade(a?: PagamentoAtividade | null): string | null {
    if (!a) return null
    return a.atividadeData ?? a.dataAtividade ?? null
}

function combinaBusca(e: Escoteiro, termo: string): boolean {
    const t = termo.trim().toLowerCase()
    if (!t) return true
    const digitos = t.replace(/\D/g, '')
    const nome = (e.membro?.nome ?? '').toLowerCase()
    const cpf = e.membro?.CPF ?? ''
    return nome.includes(t) || (digitos !== '' && (cpf.includes(digitos) || String(e.esc_cod) === digitos))
}

const colunasEscoteiros: ColunaGrade<Escoteiro>[] = [
    { id: 'nome', titulo: 'Nome', valor: (e) => e.membro.nome },
    { id: 'cpf', titulo: 'CPF', valor: (e) => e.membro.CPF, renderizar: (e) => formatarCpf(e.membro.CPF) },
    { id: 'matricula', titulo: 'Matrícula', valor: (e) => e.esc_cod, largura: 110 },
    { id: 'tropa', titulo: 'Tropa', valor: (e) => e.patrulha?.tropa?.nome ?? '—' },
    { id: 'patrulha', titulo: 'Patrulha', valor: (e) => e.patrulha?.nome ?? '—' },
    {
        id: 'situacao',
        titulo: 'Situação Financeira',
        valor: (e) => e.esc_sit_fin,
        renderizar: (e) => (
            <Chip
                size="small"
                color={e.esc_sit_fin === 1 ? 'error' : 'success'}
                label={e.esc_sit_fin === 1 ? 'Inadimplente' : 'Em dia'}
            />
        ),
    },
]

export default function PaginaPagamentosAtividade() {
    const { parametrizacao } = useParametrizacao()
    const navegar = useNavigate()
    const [busca, setBusca] = useState('')
    const [encontrados, setEncontrados] = useState<Escoteiro[]>([])
    const [tropas, setTropas] = useState<Tropa[]>([])
    const [tropaFiltro, setTropaFiltro] = useState<number>(TODAS)
    const [dadosEscoteiro, setDadosEscoteiro] = useState<AtividadesFinanceiroEscoteiro | null>(null)
    const [formasPagamento, setFormasPagamento] = useState<FormaPagamento[]>([
        { tp_cod: 1, tp_desc: 'Dinheiro' },
        { tp_cod: 2, tp_desc: 'PIX' },
        { tp_cod: 3, tp_desc: 'Cartão' },
    ])

    const [carregando, setCarregando] = useState(false)
    const [erro, setErro] = useState('')
    const [erroModal, setErroModal] = useState('')
    const [sucesso, setSucesso] = useState('')

    const [atividadePagar, setAtividadePagar] = useState<PagamentoAtividade | null>(null)
    const [valorRecebido, setValorRecebido] = useState('')
    const [usarCredito, setUsarCredito] = useState(false)
    const [formaPagamentoCod, setFormaPagamentoCod] = useState<number>(2)
    const [dataPagamento, setDataPagamento] = useState(() => dataHojeIso())
    const [confirmouDivergencia, setConfirmouDivergencia] = useState(false)

    const [atividadeHistorico, setAtividadeHistorico] = useState<PagamentoAtividade | null>(null)
    const [lancamentos, setLancamentos] = useState<LancamentoPagamentoAtividade[]>([])
    const [carregandoLancamentos, setCarregandoLancamentos] = useState(false)
    const [erroHistorico, setErroHistorico] = useState('')
    const [lancamentoConfirmarEstorno, setLancamentoConfirmarEstorno] = useState<LancamentoPagamentoAtividade | null>(null)

    const [recibo, setRecibo] = useState<ReciboPagamentoAtividade | null>(null)

    const carregarListaInicial = () => {
        pesquisarEscoteirosAtividade('')
            .then((lista) => setEncontrados(lista))
            .catch(() => {})
    }

    useEffect(() => {
        buscarFormasPagamentoAtividade()
            .then((lista) => {
                if (lista.length > 0) setFormasPagamento(lista)
            })
            .catch(() => {})

        listarTropas()
            .then((lista) => setTropas(lista))
            .catch(() => {})

        carregarListaInicial()
    }, [])

    const escoteirosFiltrados = useMemo(() => {
        return encontrados.filter((e) => {
            if (!combinaBusca(e, busca)) return false
            if (tropaFiltro !== TODAS && e.patrulha?.tropa?.cod !== tropaFiltro) return false
            return true
        })
    }, [encontrados, busca, tropaFiltro])

    const carregarAtividades = async (escCod: number) => {
        setCarregando(true)
        setErro('')
        try {
            const dados = await consultarAtividadesEscoteiro(escCod)
            setDadosEscoteiro(dados)
            return dados
        } catch (err) {
            setDadosEscoteiro(null)
            setErro(mensagemDeErro(err, 'Erro ao consultar atividades do escoteiro.'))
            return null
        } finally {
            setCarregando(false)
        }
    }

    const handlePesquisar = async (e: React.FormEvent) => {
        e.preventDefault()
        setErro('')
        setSucesso('')
        setDadosEscoteiro(null)

        if (escoteirosFiltrados.length === 0) {
            setErro('Escoteiro não localizado no sistema.')
        } else if (escoteirosFiltrados.length === 1) {
            await carregarAtividades(escoteirosFiltrados[0].esc_cod)
        }
    }

    const handleLimparBusca = () => {
        setBusca('')
        setTropaFiltro(TODAS)
        setDadosEscoteiro(null)
        setErro('')
        setSucesso('')
        carregarListaInicial()
    }

    const creditoDisponivel = Number(dadosEscoteiro?.creditoDisponivel ?? 0)

    const abrirModalPagamento = (item: PagamentoAtividade) => {
        setAtividadePagar(item)
        setUsarCredito(false)
        const saldoItem = Number(item.saldo ?? 0)
        setValorRecebido(saldoItem.toFixed(2))
        setFormaPagamentoCod(2)
        setDataPagamento(dataHojeIso())
        setConfirmouDivergencia(false)
        setErro('')
        setErroModal('')
        setSucesso('')
    }

    const abrirModalHistoricoPagamentos = async (item: PagamentoAtividade) => {
        setAtividadeHistorico(item)
        setErroHistorico('')
        setErro('')
        setSucesso('')
        setCarregandoLancamentos(true)
        try {
            const lista = await listarLancamentosAtividade(item.crCod)
            setLancamentos(lista)
        } catch (err) {
            setLancamentos([])
            setErroHistorico(mensagemDeErro(err, 'Não foi possível carregar os pagamentos realizados nesta atividade.'))
        } finally {
            setCarregandoLancamentos(false)
        }
    }

    const valorBase = atividadePagar ? Number(atividadePagar.saldo ?? 0) : 0
    const creditoAplicado = usarCredito ? Math.min(creditoDisponivel, valorBase) : 0
    const valorNumerico = parseFloat(valorRecebido) || 0
    const totalAplicado = Number((valorNumerico + creditoAplicado).toFixed(2))
    const diferenca = Number((totalAplicado - valorBase).toFixed(2))
    const temDivergencia = atividadePagar !== null && Math.abs(diferenca) >= 0.01

    const handleAlternarCredito = (marcado: boolean) => {
        setUsarCredito(marcado)
        setConfirmouDivergencia(false)
        setErroModal('')
        if (marcado) {
            const abatimento = Math.min(creditoDisponivel, valorBase)
            const restante = Math.max(0, valorBase - abatimento)
            setValorRecebido(restante.toFixed(2))
        } else {
            setValorRecebido(valorBase.toFixed(2))
        }
    }

    const handleConfirmarPagamento = async (e: React.FormEvent) => {
        e.preventDefault()
        if (!atividadePagar || !dadosEscoteiro) return

        if (valorNumerico < 0) {
            setErroModal('O valor recebido não pode ser negativo.')
            return
        }
        if (valorNumerico === 0 && creditoAplicado === 0) {
            setErroModal('Informe um valor recebido maior que zero.')
            return
        }

        const hojeIso = dataHojeIso()
        if (!dataPagamento || dataPagamento > hojeIso) {
            setErroModal('A data do pagamento não pode ser futura (superior à data de hoje).')
            return
        }

        if (temDivergencia && !confirmouDivergencia) {
            setConfirmouDivergencia(true)
            return
        }

        setCarregando(true)
        setErroModal('')
        setErro('')
        try {
            const resp = await registrarPagamentoAtividade(atividadePagar.crCod, {
                valorRecebido: valorNumerico,
                formaPagamentoCod,
                dataPagamento,
                creditoUtilizado: creditoAplicado > 0 ? creditoAplicado : undefined,
            })
            setSucesso(resp.mensagem)
            setRecibo(resp.recibo)
            setAtividadePagar(null)
            await carregarAtividades(dadosEscoteiro.esc_cod)
        } catch (err) {
            setErroModal(mensagemDeErro(err, 'Não foi possível registrar o pagamento da atividade.'))
        } finally {
            setCarregando(false)
        }
    }

    const handleConfirmarEstorno = async () => {
        if (!atividadeHistorico || !lancamentoConfirmarEstorno || !dadosEscoteiro) return
        const crCodAlvo = atividadeHistorico.crCod
        const pgCodAlvo = lancamentoConfirmarEstorno.pgCod
        setLancamentoConfirmarEstorno(null)
        setCarregandoLancamentos(true)
        setErroHistorico('')
        setErro('')
        setSucesso('')

        try {
            const resp = await estornarPagamentoAtividade(crCodAlvo, pgCodAlvo)
            setSucesso(resp.mensagem)
            const dadosAtualizados = await carregarAtividades(dadosEscoteiro.esc_cod)
            const listaRestante = await listarLancamentosAtividade(crCodAlvo)
            setLancamentos(listaRestante)
            const atvAtualizada = dadosAtualizados?.atividades.find((a) => a.crCod === crCodAlvo) ?? null
            if (!atvAtualizada || listaRestante.length === 0) {
                setAtividadeHistorico(null)
            } else {
                setAtividadeHistorico(atvAtualizada)
            }
        } catch (err) {
            setErroHistorico(mensagemDeErro(err, 'Não foi possível estornar o pagamento selecionado.'))
        } finally {
            setCarregandoLancamentos(false)
        }
    }

    const bloqueadoPorInadimplencia = dadosEscoteiro?.inadimplente ?? false

    const colunasAtividades: ColunaGrade<PagamentoAtividade>[] = [
        { id: 'atividade', titulo: 'Atividade', valor: (a) => a.atividadeNome },
        {
            id: 'data',
            titulo: 'Data da Atividade',
            valor: (a) => obterDataAtividade(a) ?? '',
            renderizar: (a) => {
                const dt = obterDataAtividade(a)
                return dt ? formatarData(dt) : '—'
            },
        },
        {
            id: 'valorAtividade',
            titulo: 'Valor',
            valor: (a) => obterValorAtividade(a),
            renderizar: (a) => formatarMoeda(obterValorAtividade(a)),
            alinhamento: 'right',
        },
        {
            id: 'valorPago',
            titulo: 'Valor Pago',
            valor: (a) => Number(a.valorPago ?? 0),
            renderizar: (a) => formatarMoeda(Number(a.valorPago ?? 0)),
            alinhamento: 'right',
        },
        {
            id: 'saldo',
            titulo: 'Saldo',
            valor: (a) => Number(a.saldo ?? 0),
            renderizar: (a) => formatarMoeda(Number(a.saldo ?? 0)),
            alinhamento: 'right',
        },
        {
            id: 'forma',
            titulo: 'Último Pgto',
            valor: (a) => a.formaPagamentoDesc ?? '',
            renderizar: (a) =>
                a.dataPagamento ? `${a.formaPagamentoDesc ?? 'Pago'} (${formatarData(a.dataPagamento)})` : '—',
        },
        {
            id: 'situacao',
            titulo: 'Situação',
            valor: (a) => a.situacao,
            renderizar: (a) => (
                <Chip
                    size="small"
                    variant="outlined"
                    color={COR_SITUACAO[a.situacao] ?? 'default'}
                    label={ROTULO_SITUACAO[a.situacao] ?? a.situacao}
                />
            ),
        },
        {
            id: 'acoes',
            titulo: 'Ações',
            valor: (a) => a.crCod,
            renderizar: (a) => {
                const saldoNum = Number(a.saldo ?? 0)
                const pagoNum = Number(a.valorPago ?? 0)
                const ehParcial = pagoNum > 0 && saldoNum > 0
                return (
                    <Stack direction="row" spacing={1}>
                        {saldoNum > 0 && (
                            <Button
                                size="small"
                                variant="contained"
                                color="success"
                                disabled={bloqueadoPorInadimplencia}
                                onClick={() => abrirModalPagamento(a)}
                            >
                                {ehParcial ? 'Quitar Saldo' : 'Registrar Pagamento'}
                            </Button>
                        )}
                        {pagoNum > 0 && (
                            <Button
                                size="small"
                                variant="outlined"
                                color="error"
                                onClick={() => abrirModalHistoricoPagamentos(a)}
                            >
                                Pagamentos / Estornar
                            </Button>
                        )}
                    </Stack>
                )
            },
        },
    ]

    return (
        <main className="pagina">
            <header>
                <h1>Registrar Pagamento de Atividade</h1>
                <p className="subtitulo">Controle de pagamentos de acampamentos, passeios e eventos especiais</p>
            </header>

            {!dadosEscoteiro && erro && (
                <Alert severity="error" onClose={() => setErro('')} sx={{ mb: 2 }}>
                    {erro}
                </Alert>
            )}
            {!dadosEscoteiro && sucesso && (
                <Alert severity="success" onClose={() => setSucesso('')} sx={{ mb: 2 }}>
                    {sucesso}
                </Alert>
            )}

            <Box
                component="form"
                onSubmit={handlePesquisar}
                sx={{ display: 'flex', gap: 1.5, alignItems: 'flex-start', mb: 2 }}
            >
                <CampoTexto rotulo="Filtrar por Nome, CPF ou Matrícula" valor={busca} aoMudar={setBusca} />
                <BotaoOk rotulo="Buscar" carregando={carregando} />
                {(busca || dadosEscoteiro || tropaFiltro !== TODAS) && (
                    <BotaoCancelar rotulo="Ver Todos" aoClicar={handleLimparBusca} />
                )}
            </Box>

            <Box sx={{ maxWidth: 320, mb: 2 }}>
                <CampoSelecao
                    rotulo="Tropa"
                    valor={tropaFiltro}
                    opcoes={[{ valor: TODAS, rotulo: 'Todas as tropas' }, ...tropas.map((t) => ({ valor: t.cod, rotulo: t.nome }))]}
                    aoMudar={(cod) => setTropaFiltro(cod ?? TODAS)}
                />
            </Box>

            {encontrados.length > 0 && (
                <Grade
                    titulo="Escoteiros (Inadimplentes bloqueados para atividades pagas)"
                    subtitulo="Clique sobre o escoteiro para consultar suas inscrições em atividades"
                    colunas={colunasEscoteiros}
                    linhas={escoteirosFiltrados}
                    chave={(e) => e.esc_cod}
                    chaveSelecionada={dadosEscoteiro?.esc_cod ?? null}
                    textoVazio="Nenhum escoteiro encontrado para os filtros selecionados."
                    aoClicarLinha={(e) => {
                        setSucesso('')
                        carregarAtividades(e.esc_cod)
                    }}
                    linhasPorPagina={8}
                />
            )}

            {dadosEscoteiro && (
                <Stack spacing={2.5} sx={{ mt: 2 }}>
                    {erro && (
                        <Alert severity="error" onClose={() => setErro('')}>
                            {erro}
                        </Alert>
                    )}
                    {sucesso && (
                        <Alert severity="success" onClose={() => setSucesso('')}>
                            {sucesso}
                        </Alert>
                    )}

                    <Card variant="outlined">
                        <CardContent sx={{ display: 'flex', justifyContent: 'space-between', flexWrap: 'wrap', gap: 2 }}>
                            <div>
                                <Typography variant="h6">{dadosEscoteiro.nome}</Typography>
                                <Typography variant="body2" color="text.secondary">
                                    CPF: {formatarCpf(dadosEscoteiro.cpf)} | Matrícula: {dadosEscoteiro.esc_cod} | Patrulha:{' '}
                                    {dadosEscoteiro.patrulha}
                                </Typography>
                            </div>
                            <Stack direction="row" spacing={1} sx={{ alignItems: 'center' }}>
                                {creditoDisponivel > 0 && (
                                    <Chip
                                        color="info"
                                        variant="outlined"
                                        label={`Crédito disponível: ${formatarMoeda(creditoDisponivel)}`}
                                    />
                                )}
                                <Chip
                                    color={dadosEscoteiro.inadimplente ? 'error' : 'success'}
                                    label={dadosEscoteiro.inadimplente ? 'Mensalidades: INADIMPLENTE' : 'Mensalidades: EM DIA'}
                                />
                            </Stack>
                        </CardContent>
                    </Card>

                    {dadosEscoteiro.inadimplente && (
                        <Alert
                            severity="error"
                            action={
                                <Button color="inherit" size="small" onClick={() => navegar('/pagamentos-mensalidade')}>
                                    Regularizar Mensalidades
                                </Button>
                            }
                        >
                            Este escoteiro está inadimplente. Não é permitido registrar pagamento de atividades pagas enquanto houver
                            mensalidades pendentes. Regularize a situação financeira primeiro.
                        </Alert>
                    )}

                    {dadosEscoteiro.atividades.length === 0 ? (
                        <Alert severity="info">Este escoteiro não possui inscrições em atividades pagas.</Alert>
                    ) : (
                        <Grade
                            titulo="Inscrições em Atividades Pagas"
                            subtitulo={`${dadosEscoteiro.atividades.length} atividade(s) vinculada(s)`}
                            colunas={colunasAtividades}
                            linhas={dadosEscoteiro.atividades}
                            chave={(a) => a.crCod}
                            linhasPorPagina={10}
                        />
                    )}
                </Stack>
            )}

            <Dialog open={atividadePagar !== null} onClose={() => setAtividadePagar(null)} maxWidth="xs" fullWidth>
                <form onSubmit={handleConfirmarPagamento}>
                    <DialogTitle>Pagamento de Atividade — {atividadePagar?.atividadeNome}</DialogTitle>
                    <DialogContent>
                        <Stack spacing={2} sx={{ mt: 1 }}>
                            {erroModal && (
                                <Alert severity="error" onClose={() => setErroModal('')}>
                                    {erroModal}
                                </Alert>
                            )}

                            <Typography variant="body2">
                                Escoteiro: <strong>{atividadePagar?.escoteiroNome}</strong>
                                <br />
                                Valor da Atividade: <strong>{formatarMoeda(obterValorAtividade(atividadePagar))}</strong>
                                <br />
                                Saldo Pendente: <strong>{formatarMoeda(Number(atividadePagar?.saldo ?? 0))}</strong>
                            </Typography>

                            {creditoDisponivel > 0 && (
                                <Box sx={{ p: 1.5, borderRadius: 1, bgcolor: 'action.hover' }}>
                                    <FormControlLabel
                                        control={
                                            <Checkbox
                                                checked={usarCredito}
                                                onChange={(e) => handleAlternarCredito(e.target.checked)}
                                            />
                                        }
                                        label={
                                            <Typography variant="body2">
                                                Usar crédito disponível ({formatarMoeda(creditoDisponivel)}) — abate{' '}
                                                <strong>{formatarMoeda(Math.min(creditoDisponivel, valorBase))}</strong>
                                            </Typography>
                                        }
                                    />
                                </Box>
                            )}

                            <TextField
                                label="Valor Recebido (R$)"
                                type="number"
                                slotProps={{ htmlInput: { step: '0.01', min: creditoAplicado > 0 ? '0.00' : '0.01' } }}
                                value={valorRecebido}
                                onChange={(e) => {
                                    setValorRecebido(e.target.value)
                                    setConfirmouDivergencia(false)
                                    setErroModal('')
                                }}
                                required
                                fullWidth
                            />

                            {temDivergencia && (
                                <Alert severity={diferenca < 0 ? 'warning' : 'info'}>
                                    Total aplicado: <strong>{formatarMoeda(totalAplicado)}</strong> | Saldo pendente:{' '}
                                    <strong>{formatarMoeda(valorBase)}</strong> | Diferença:{' '}
                                    <strong>{formatarMoeda(Math.abs(diferenca))}</strong>.
                                    <br />
                                    {diferenca < 0
                                        ? `Pagamento parcial: a diferença de ${formatarMoeda(Math.abs(diferenca))} será mantida em aberto. Clique novamente em Confirmar para continuar.`
                                        : `O valor supera o saldo pendente: o excedente de ${formatarMoeda(diferenca)} ficará disponível como crédito. Clique novamente em Confirmar para continuar.`}
                                </Alert>
                            )}

                            <TextField
                                select
                                label="Forma de Pagamento"
                                value={formaPagamentoCod}
                                onChange={(e) => setFormaPagamentoCod(Number(e.target.value))}
                                fullWidth
                            >
                                {formasPagamento.map((fp) => (
                                    <MenuItem key={fp.tp_cod} value={fp.tp_cod}>
                                        {fp.tp_desc}
                                    </MenuItem>
                                ))}
                            </TextField>

                            <TextField
                                label="Data do Pagamento"
                                type="date"
                                slotProps={{
                                    inputLabel: { shrink: true },
                                    htmlInput: { max: dataHojeIso() },
                                }}
                                value={dataPagamento}
                                onChange={(e) => {
                                    setDataPagamento(e.target.value)
                                    setErroModal('')
                                }}
                                required
                                fullWidth
                            />
                        </Stack>
                    </DialogContent>
                    <DialogActions sx={{ px: 3, pb: 2 }}>
                        <BotaoCancelar rotulo="Cancelar" aoClicar={() => setAtividadePagar(null)} />
                        <BotaoOk
                            rotulo={temDivergencia && !confirmouDivergencia ? 'Confirmar com divergência' : 'Confirmar Pagamento'}
                            tipo="submit"
                            carregando={carregando}
                        />
                    </DialogActions>
                </form>
            </Dialog>

            <Dialog
                open={atividadeHistorico !== null}
                onClose={() => setAtividadeHistorico(null)}
                maxWidth="sm"
                fullWidth
            >
                <DialogTitle>
                    Pagamentos Realizados — {atividadeHistorico?.atividadeNome}
                </DialogTitle>
                <DialogContent>
                    <Stack spacing={2} sx={{ mt: 1 }}>
                        {erroHistorico && (
                            <Alert severity="error" onClose={() => setErroHistorico('')}>
                                {erroHistorico}
                            </Alert>
                        )}

                        {atividadeHistorico && (
                            <Box sx={{ p: 1.5, borderRadius: 1, bgcolor: 'action.hover' }}>
                                <Typography variant="body2">
                                    <strong>Escoteiro:</strong> {atividadeHistorico.escoteiroNome}
                                </Typography>
                                <Typography variant="body2">
                                    <strong>Valor da Atividade:</strong> {formatarMoeda(obterValorAtividade(atividadeHistorico))} |{' '}
                                    <strong>Total Pago:</strong> {formatarMoeda(Number(atividadeHistorico.valorPago ?? 0))} |{' '}
                                    <strong>Saldo Restante:</strong> {formatarMoeda(Number(atividadeHistorico.saldo ?? 0))}
                                </Typography>
                            </Box>
                        )}

                        {lancamentos.length > 1 && (
                            <Alert severity="info">
                                Os estornos seguem a ordem do último pagamento realizado para o primeiro. Estorne o lançamento mais
                                recente para liberar os anteriores.
                            </Alert>
                        )}

                        <Divider />

                        {carregandoLancamentos ? (
                            <Typography variant="body2" color="text.secondary">
                                Carregando pagamentos...
                            </Typography>
                        ) : lancamentos.length === 0 ? (
                            <Alert severity="info">Nenhum lançamento pago encontrado para esta atividade.</Alert>
                        ) : (
                            <Stack spacing={1.5}>
                                {lancamentos.map((lanc, idx) => (
                                    <Card key={lanc.pgCod} variant="outlined">
                                        <CardContent
                                            sx={{
                                                display: 'flex',
                                                justifyContent: 'space-between',
                                                alignItems: 'center',
                                                flexWrap: 'wrap',
                                                gap: 1.5,
                                                py: '12px !important',
                                            }}
                                        >
                                            <Box>
                                                <Typography variant="subtitle2" sx={{ fontWeight: 'bold' }}>
                                                    {idx === 0 ? 'Último Pagamento' : `${lancamentos.length - idx}º Pagamento`} —{' '}
                                                    {formatarMoeda(Number(lanc.valorPago ?? 0))}
                                                </Typography>
                                                <Typography variant="body2" color="text.secondary">
                                                    Data: {lanc.dataPagamento ? formatarData(lanc.dataPagamento) : '—'} | Forma:{' '}
                                                    {lanc.formaPagamentoDesc ?? '—'}
                                                </Typography>
                                                {lanc.descricao && (
                                                    <Typography variant="caption" color="text.secondary">
                                                        {lanc.descricao}
                                                    </Typography>
                                                )}
                                            </Box>
                                            <Tooltip
                                                title={
                                                    lanc.podeEstornar
                                                        ? 'Estornar este lançamento'
                                                        : 'Estorne primeiro o lançamento mais recente acima'
                                                }
                                            >
                                                <span>
                                                    <Button
                                                        size="small"
                                                        variant={lanc.podeEstornar ? 'contained' : 'outlined'}
                                                        color="error"
                                                        disabled={!lanc.podeEstornar || carregandoLancamentos}
                                                        onClick={() => setLancamentoConfirmarEstorno(lanc)}
                                                    >
                                                        Estornar
                                                    </Button>
                                                </span>
                                            </Tooltip>
                                        </CardContent>
                                    </Card>
                                ))}
                            </Stack>
                        )}
                    </Stack>
                </DialogContent>
                <DialogActions sx={{ px: 3, pb: 2 }}>
                    <BotaoCancelar rotulo="Fechar" aoClicar={() => setAtividadeHistorico(null)} />
                </DialogActions>
            </Dialog>

            <DialogoConfirmacao
                aberto={lancamentoConfirmarEstorno !== null}
                mensagem={`Deseja confirmar o estorno do lançamento de ${lancamentoConfirmarEstorno ? formatarMoeda(Number(lancamentoConfirmarEstorno.valorPago ?? 0)) : ''} (${lancamentoConfirmarEstorno?.formaPagamentoDesc ?? 'Pagamento'} em ${lancamentoConfirmarEstorno?.dataPagamento ? formatarData(lancamentoConfirmarEstorno.dataPagamento) : '—'}) referente à atividade "${atividadeHistorico?.atividadeNome ?? ''}"?`}
                aoConfirmar={handleConfirmarEstorno}
                aoCancelar={() => setLancamentoConfirmarEstorno(null)}
                carregando={carregandoLancamentos}
            />

            <Dialog open={recibo !== null} onClose={() => setRecibo(null)} maxWidth="sm" fullWidth>
                <DialogTitle>Recibo de Pagamento de Atividade — {recibo?.numero}</DialogTitle>
                <DialogContent>
                    {recibo && (
                        <Box sx={{ p: 2, border: '1px solid #ccc', borderRadius: 1, bgcolor: '#fafafa' }}>
                            <Typography variant="subtitle1" gutterBottom sx={{ fontWeight: 'bold' }}>
                                {parametrizacao.nomeGrupo} — Recibo de Atividade
                            </Typography>
                            <Typography variant="body2">
                                <strong>Escoteiro:</strong> {recibo.escoteiro} (Matrícula {recibo.matricula})
                            </Typography>
                            <Typography variant="body2">
                                <strong>CPF:</strong> {formatarCpf(recibo.cpf)}
                            </Typography>
                            <Typography variant="body2">
                                <strong>Atividade:</strong> {recibo.atividade}
                            </Typography>
                            <Typography variant="body2">
                                <strong>Data da Atividade:</strong>{' '}
                                {recibo.dataAtividade && recibo.dataAtividade !== '—' ? formatarData(recibo.dataAtividade) : '—'}
                            </Typography>
                            <Typography variant="body2">
                                <strong>Data do Pagamento:</strong> {formatarData(recibo.dataPagamento)}
                            </Typography>
                            <Typography variant="body2">
                                <strong>Valor da Atividade:</strong> {formatarMoeda(Number(recibo.valorAtividade ?? 0))}
                            </Typography>
                            {Number(recibo.creditoUtilizado ?? 0) > 0 && (
                                <Typography variant="body2">
                                    <strong>Crédito Abatido:</strong> {formatarMoeda(Number(recibo.creditoUtilizado ?? 0))}
                                </Typography>
                            )}
                            <Typography variant="body2">
                                <strong>Valor Pago:</strong> {formatarMoeda(Number(recibo.valorPago ?? 0))}
                            </Typography>
                            {recibo.observacao && (
                                <Typography variant="body2" color="warning.main" sx={{ mt: 1 }}>
                                    <strong>Observação:</strong> {recibo.observacao}
                                </Typography>
                            )}
                        </Box>
                    )}
                </DialogContent>
                <DialogActions sx={{ px: 3, pb: 2 }}>
                    <Button variant="outlined" onClick={() => window.print()}>
                        Imprimir / Salvar PDF
                    </Button>
                    <BotaoOk rotulo="Fechar" tipo="button" aoClicar={() => setRecibo(null)} />
                </DialogActions>
            </Dialog>
        </main>
    )
}