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
import type TipoAtividade from '@/data/models/TipoAtividade'
import { ErroDeApi } from '@/data/services/api'
import {
    buscarTiposAtividade,
    cadastrarTipoAtividade,
    alterarTipoAtividade,
    apagarTipoAtividade,
} from '@/data/services/tiposAtividade'

const colunas: ColunaGrade<TipoAtividade>[] = [
    { id: 'codigo', titulo: 'Código', valor: (t) => t.ta_cod, largura: 120 },
    { id: 'descricao', titulo: 'Descrição', valor: (t) => t.ta_desc },
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

export default function PaginaTiposAtividade() {
    const [tipos, setTipos] = useState<TipoAtividade[]>([])
    const [carregando, setCarregando] = useState(true)
    const [salvando, setSalvando] = useState(false)
    const [erro, setErro] = useState('')
    const [erroModal, setErroModal] = useState('')
    const [sucesso, setSucesso] = useState('')

    const [selecionado, setSelecionado] = useState<TipoAtividade | null>(null)
    const [modalAberto, setModalAberto] = useState(false)
    const [confirmandoExclusao, setConfirmandoExclusao] = useState(false)

    const [descricao, setDescricao] = useState('')
    const [filtro, setFiltro] = useState('')

    const carregar = async (termo = filtro) => {
        setCarregando(true)
        try {
            const lista = await buscarTiposAtividade(termo)
            setTipos(lista)
            setErro('')
        } catch (err) {
            setErro(extrairMensagemErro(err, 'Não foi possível carregar os tipos de atividade.'))
        } finally {
            setCarregando(false)
        }
    }

    useEffect(() => {
        let ativo = true
        buscarTiposAtividade('')
            .then((lista) => {
                if (ativo) {
                    setTipos(lista)
                    setErro('')
                }
            })
            .catch((err: unknown) => {
                if (ativo) setErro(extrairMensagemErro(err, 'Não foi possível carregar os tipos de atividade.'))
            })
            .finally(() => {
                if (ativo) setCarregando(false)
            })
        return () => {
            ativo = false
        }
    }, [])

    const abrirNovo = () => {
        setSelecionado(null)
        setDescricao('')
        setErro('')
        setErroModal('')
        setSucesso('')
        setModalAberto(true)
    }

    const abrirEdicao = (item: TipoAtividade) => {
        setSelecionado(item)
        setDescricao(item.ta_desc)
        setErro('')
        setErroModal('')
        setSucesso('')
        setModalAberto(true)
    }

    const handleSalvar = async (e: React.FormEvent) => {
        e.preventDefault()
        setErro('')
        setErroModal('')
        setSucesso('')

        const descLimpa = descricao.trim()
        if (!descLimpa) {
            setErroModal('A descrição do tipo de atividade é obrigatória.')
            return
        }

        setSalvando(true)
        try {
            if (selecionado) {
                await alterarTipoAtividade(selecionado.ta_cod, {
                    ta_cod: selecionado.ta_cod,
                    ta_desc: descLimpa,
                })
                setSucesso('Tipo de atividade atualizado com sucesso!')
            } else {
                await cadastrarTipoAtividade({ ta_desc: descLimpa })
                setSucesso('Tipo de atividade cadastrado com sucesso!')
            }
            setModalAberto(false)
            setSelecionado(null)
            await carregar()
        } catch (err) {
            setErroModal(extrairMensagemErro(err, 'Erro inesperado ao salvar.'))
        } finally {
            setSalvando(false)
        }
    }

    const handleExcluir = async () => {
        if (!selecionado) return
        setConfirmandoExclusao(false)
        setErro('')
        setSucesso('')

        try {
            await apagarTipoAtividade(selecionado.ta_cod)
            setSucesso('Tipo de atividade excluído com sucesso!')
            setSelecionado(null)
            await carregar()
        } catch (err) {
            setErro(extrairMensagemErro(err, 'Erro ao excluir registro.'))
        }
    }

    return (
        <main className="pagina">
            <header>
                <h1>Tipos de Atividade</h1>
                <p className="subtitulo">Gerenciamento dos tipos de atividade</p>
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
                <form onSubmit={handleSalvar}>
                    <DialogTitle>{selecionado ? 'Editar Tipo de Atividade' : 'Novo Tipo de Atividade'}</DialogTitle>
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
                                    setErroModal('')
                                }}
                                slotProps={{ htmlInput: { maxLength: 100 } }}
                                required
                                fullWidth
                            />
                        </Stack>
                    </DialogContent>
                    <DialogActions sx={{ px: 3, pb: 2 }}>
                        <BotaoCancelar rotulo="Cancelar" aoClicar={() => setModalAberto(false)} />
                        <BotaoOk
                            rotulo={selecionado ? 'Atualizar' : 'Cadastrar'}
                            tipo="submit"
                            carregando={salvando}
                        />
                    </DialogActions>
                </form>
            </Dialog>

            <DialogoConfirmacao
                aberto={confirmandoExclusao}
                mensagem={`Deseja realmente excluir o tipo de atividade "${selecionado?.ta_desc ?? ''}"?`}
                aoConfirmar={handleExcluir}
                aoCancelar={() => setConfirmandoExclusao(false)}
                carregando={false}
            />

            <Grade
                titulo="Tipos cadastrados"
                subtitulo={`${tipos.length} ${tipos.length === 1 ? 'registro' : 'registros'}`}
                colunas={colunas}
                linhas={tipos}
                chave={(t) => t.ta_cod}
                carregando={carregando}
                textoVazio="Nenhum tipo de atividade encontrado."
                aoClicarLinha={(linha) => setSelecionado(linha)}
                aoDuploClique={(linha) => abrirEdicao(linha)}
                chaveSelecionada={selecionado?.ta_cod ?? null}
                linhasPorPagina={10}
                rodape={
                    <Box sx={{ display: 'flex', justifyContent: 'flex-end', gap: '10px' }}>
                        <BotaoOk rotulo="Adicionar Tipo" aoClicar={abrirNovo} tipo="button" />
                        <BotaoOk
                            rotulo="Editar"
                            aoClicar={() => selecionado && abrirEdicao(selecionado)}
                            desabilitado={selecionado === null}
                            tipo="button"
                        />
                        <BotaoCancelar
                            rotulo="Excluir"
                            aoClicar={() => setConfirmandoExclusao(true)}
                            desabilitado={selecionado === null}
                        />
                    </Box>
                }
            />
        </main>
    )
}