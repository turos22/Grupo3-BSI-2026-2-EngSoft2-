import { useEffect, useState } from 'react'
import Alert from '@mui/material/Alert'
import Box from '@mui/material/Box'
import Dialog from '@mui/material/Dialog'
import DialogActions from '@mui/material/DialogActions'
import DialogContent from '@mui/material/DialogContent'
import DialogTitle from '@mui/material/DialogTitle'
import Stack from '@mui/material/Stack'
import TextField from '@mui/material/TextField'
import Grade, { type ColunaGrade } from '@/components/grade/Grade'
import { BotaoCancelar, BotaoOk } from '@/components/formulario/Botoes'
import DialogoConfirmacao from '@/components/formulario/DialogoConfirmacao'
import type CategoriaMensalidade from '@/data/models/CategoriaMensalidade'
import { ErroDeApi } from '@/data/services/api'
import {
    buscarCategoriasMensalidade,
    cadastrarCategoriaMensalidade,
    alterarCategoriaMensalidade,
    apagarCategoriaMensalidade,
} from '@/data/services/categoriasMensalidade'
import { formatarMoeda } from '@/data/utils/formatadores'

const colunas: ColunaGrade<CategoriaMensalidade>[] = [
    { id: 'codigo', titulo: 'Código', valor: (c) => c.cat_mens_cod, largura: 110 },
    { id: 'descricao', titulo: 'Descrição', valor: (c) => c.descricao },
    {
        id: 'valor',
        titulo: 'Valor',
        valor: (c) => Number(c.valor ?? 0),
        renderizar: (c) => formatarMoeda(Number(c.valor ?? 0)),
        alinhamento: 'right',
    },
    { id: 'qtd_irmao', titulo: 'Qtd. Irmãos', valor: (c) => Number(c.qtd_irmao ?? 0), largura: 140 },
]

function extrairMensagemErro(err: unknown, padrao: string): string {
    if (err instanceof ErroDeApi) {
        try {
            const obj = JSON.parse(err.message)
            return obj.erro || obj.message || err.message
        } catch {
            return err.message || `O backend respondeu ${err.status}.`
        }
    }
    return padrao
}

export default function PaginaCategoriasMensalidade() {
    const [categorias, setCategorias] = useState<CategoriaMensalidade[]>([])
    const [carregando, setCarregando] = useState(true)
    const [salvando, setSalvando] = useState(false)
    const [erro, setErro] = useState('')
    const [erroModal, setErroModal] = useState('')
    const [erroDescricao, setErroDescricao] = useState('')
    const [erroValor, setErroValor] = useState('')
    const [erroQtdIrmao, setErroQtdIrmao] = useState('')
    const [sucesso, setSucesso] = useState('')

    const [selecionada, setSelecionada] = useState<CategoriaMensalidade | null>(null)
    const [modalAberto, setModalAberto] = useState(false)
    const [confirmandoExclusao, setConfirmandoExclusao] = useState(false)

    const [descricao, setDescricao] = useState('')
    const [valor, setValor] = useState('')
    const [qtdIrmao, setQtdIrmao] = useState('0')
    const [filtro, setFiltro] = useState('')

    const carregar = async (termo = filtro) => {
        setCarregando(true)
        try {
            const lista = await buscarCategoriasMensalidade(termo)
            setCategorias(lista)
            setErro('')
        } catch (err) {
            setErro(extrairMensagemErro(err, 'Não foi possível carregar as categorias de mensalidade.'))
        } finally {
            setCarregando(false)
        }
    }

    useEffect(() => {
        let ativo = true
        buscarCategoriasMensalidade('')
            .then((lista) => {
                if (ativo) {
                    setCategorias(lista)
                    setErro('')
                }
            })
            .catch((err: unknown) => {
                if (ativo) setErro(extrairMensagemErro(err, 'Não foi possível carregar as categorias de mensalidade.'))
            })
            .finally(() => {
                if (ativo) setCarregando(false)
            })
        return () => {
            ativo = false
        }
    }, [])

    const abrirNova = () => {
        setSelecionada(null)
        setDescricao('')
        setValor('')
        setQtdIrmao('0')
        setErro('')
        setErroModal('')
        setErroDescricao('')
        setErroValor('')
        setErroQtdIrmao('')
        setSucesso('')
        setModalAberto(true)
    }

    const abrirEdicao = (item: CategoriaMensalidade) => {
        setSelecionada(item)
        setDescricao(item.descricao ?? '')
        setValor(Number(item.valor ?? 0).toFixed(2))
        setQtdIrmao(String(item.qtd_irmao ?? 0))
        setErro('')
        setErroModal('')
        setErroDescricao('')
        setErroValor('')
        setErroQtdIrmao('')
        setSucesso('')
        setModalAberto(true)
    }

    const handleSalvar = async (e: React.FormEvent) => {
        e.preventDefault()
        setErro('')
        setErroModal('')
        setErroDescricao('')
        setErroValor('')
        setErroQtdIrmao('')
        setSucesso('')

        let invalido = false

        const descLimpa = descricao.trim()
        if (!descLimpa) {
            setErroDescricao('A descrição da categoria de mensalidade é obrigatória.')
            invalido = true
        } else if (descLimpa.length > 100) {
            setErroDescricao('A descrição deve ter no máximo 100 caracteres.')
            invalido = true
        }

        const valorNum = parseFloat(valor)
        if (valor.trim() === '' || Number.isNaN(valorNum)) {
            setErroValor('Informe o valor da mensalidade.')
            invalido = true
        } else if (valorNum < 0) {
            setErroValor('O valor não pode ser negativo.')
            invalido = true
        }

        const qtdNum = parseInt(qtdIrmao, 10)
        if (qtdIrmao.trim() === '' || Number.isNaN(qtdNum)) {
            setErroQtdIrmao('Informe a quantidade de irmãos.')
            invalido = true
        } else if (qtdNum < 0) {
            setErroQtdIrmao('A quantidade de irmãos não pode ser negativa.')
            invalido = true
        }

        if (invalido) return

        setSalvando(true)
        try {
            if (selecionada) {
                await alterarCategoriaMensalidade(selecionada.cat_mens_cod, {
                    cat_mens_cod: selecionada.cat_mens_cod,
                    descricao: descLimpa,
                    valor: valorNum,
                    qtd_irmao: qtdNum,
                })
                setSucesso('Categoria atualizada com sucesso!')
            } else {
                await cadastrarCategoriaMensalidade({
                    descricao: descLimpa,
                    valor: valorNum,
                    qtd_irmao: qtdNum,
                })
                setSucesso('Categoria cadastrada com sucesso!')
            }
            setModalAberto(false)
            setSelecionada(null)
            await carregar()
        } catch (err) {
            const msg = extrairMensagemErro(err, 'Erro inesperado ao salvar categoria.')
            if (msg.toLowerCase().includes('descrição')) {
                setErroDescricao(msg)
            } else {
                setErroModal(msg)
            }
        } finally {
            setSalvando(false)
        }
    }

    const handleExcluir = async () => {
        if (!selecionada) return
        const alvo = selecionada
        setConfirmandoExclusao(false)
        setErro('')
        setSucesso('')

        try {
            await apagarCategoriaMensalidade(alvo.cat_mens_cod)
            setSucesso('Categoria excluída com sucesso!')
            setSelecionada(null)
            await carregar()
        } catch (err) {
            setErro(extrairMensagemErro(err, 'Erro ao excluir categoria.'))
        }
    }

    return (
        <main className="pagina">
            <header>
                <h1>Categorias de Mensalidade</h1>
                <p className="subtitulo">Configuração de faixas de mensalidade e descontos por irmãos</p>
            </header>

            {erro && (
                <Alert severity="error" onClose={() => setErro('')} sx={{ mb: 2 }}>
                    {erro}
                </Alert>
            )}
            {sucesso && (
                <Alert severity="success" onClose={() => setSucesso('')} sx={{ mb: 2 }}>
                    {sucesso}
                </Alert>
            )}

            <Box
                component="form"
                onSubmit={(e) => {
                    e.preventDefault()
                    carregar(filtro)
                }}
                sx={{ display: 'flex', gap: 1.5, mb: 2, alignItems: 'center' }}
            >
                <TextField
                    size="small"
                    label="Filtrar por descrição"
                    value={filtro}
                    onChange={(e) => setFiltro(e.target.value)}
                />
                <BotaoOk rotulo="Buscar" tipo="submit" />
                {filtro && (
                    <BotaoCancelar
                        rotulo="Limpar"
                        aoClicar={() => {
                            setFiltro('')
                            carregar('')
                        }}
                    />
                )}
            </Box>

            <Dialog open={modalAberto} onClose={() => setModalAberto(false)} maxWidth="xs" fullWidth>
                <form onSubmit={handleSalvar} noValidate>
                    <DialogTitle>{selecionada ? 'Editar Categoria de Mensalidade' : 'Nova Categoria de Mensalidade'}</DialogTitle>
                    <DialogContent>
                        <Stack spacing={2} sx={{ mt: 1 }}>
                            {erroModal && (
                                <Alert severity="error" onClose={() => setErroModal('')}>
                                    {erroModal}
                                </Alert>
                            )}
                            <TextField
                                label="Descrição"
                                value={descricao}
                                onChange={(e) => {
                                    setDescricao(e.target.value)
                                    setErroDescricao('')
                                    setErroModal('')
                                }}
                                slotProps={{ htmlInput: { maxLength: 100 } }}
                                error={Boolean(erroDescricao)}
                                helperText={erroDescricao}
                                required
                                fullWidth
                            />
                            <TextField
                                label="Valor (R$)"
                                type="number"
                                slotProps={{ htmlInput: { step: '0.01', min: '0' } }}
                                value={valor}
                                onChange={(e) => {
                                    setValor(e.target.value)
                                    setErroValor('')
                                    setErroModal('')
                                }}
                                error={Boolean(erroValor)}
                                helperText={erroValor}
                                required
                                fullWidth
                            />
                            <TextField
                                label="Quantidade de Irmãos"
                                type="number"
                                slotProps={{ htmlInput: { step: '1', min: '0' } }}
                                value={qtdIrmao}
                                onChange={(e) => {
                                    setQtdIrmao(e.target.value)
                                    setErroQtdIrmao('')
                                    setErroModal('')
                                }}
                                error={Boolean(erroQtdIrmao)}
                                helperText={erroQtdIrmao}
                                required
                                fullWidth
                            />
                        </Stack>
                    </DialogContent>
                    <DialogActions sx={{ px: 3, pb: 2 }}>
                        <BotaoCancelar rotulo="Cancelar" aoClicar={() => setModalAberto(false)} />
                        <BotaoOk
                            rotulo={selecionada ? 'Atualizar' : 'Cadastrar'}
                            tipo="submit"
                            carregando={salvando}
                        />
                    </DialogActions>
                </form>
            </Dialog>

            <DialogoConfirmacao
                aberto={confirmandoExclusao}
                mensagem={`Deseja realmente excluir a categoria "${selecionada?.descricao ?? ''}"?`}
                aoConfirmar={handleExcluir}
                aoCancelar={() => setConfirmandoExclusao(false)}
                carregando={false}
            />

            <Grade
                titulo="Categorias cadastradas"
                subtitulo={`${categorias.length} ${categorias.length === 1 ? 'categoria' : 'categorias'}`}
                colunas={colunas}
                linhas={categorias}
                chave={(c) => c.cat_mens_cod}
                carregando={carregando}
                textoVazio="Nenhuma categoria de mensalidade encontrada."
                aoClicarLinha={(linha) => setSelecionada(linha)}
                aoDuploClique={(linha) => abrirEdicao(linha)}
                chaveSelecionada={selecionada?.cat_mens_cod ?? null}
                linhasPorPagina={10}
                rodape={
                    <Box sx={{ display: 'flex', justifyContent: 'flex-end', gap: '10px' }}>
                        <BotaoOk rotulo="Adicionar Categoria" aoClicar={abrirNova} tipo="button" />
                        <BotaoOk
                            rotulo="Editar"
                            aoClicar={() => selecionada && abrirEdicao(selecionada)}
                            desabilitado={selecionada === null}
                            tipo="button"
                        />
                        <BotaoCancelar
                            rotulo="Excluir"
                            aoClicar={() => setConfirmandoExclusao(true)}
                            desabilitado={selecionada === null}
                        />
                    </Box>
                }
            />
        </main>
    )
}