import { useState } from 'react'
import Alert from '@mui/material/Alert'
import Button from '@mui/material/Button'
import Dialog from '@mui/material/Dialog'
import DialogActions from '@mui/material/DialogActions'
import DialogContent from '@mui/material/DialogContent'
import DialogTitle from '@mui/material/DialogTitle'
import LinearProgress from '@mui/material/LinearProgress'
import CampoTexto from '@/components/formulario/CampoTexto'
import Grade, { type ColunaGrade } from '@/components/grade/Grade'
import type Escoteiro from '@/data/models/Escoteiro'
import { combinaBusca } from '@/data/utils/busca'
import { formatarCpf } from '@/data/utils/formatadores'
import './SeletorDeEscoteiro.css'

interface SeletorDeEscoteiroProps {
  aberto: boolean
  titulo: string
  descricao: string
  escoteiros: Escoteiro[]
  carregando: boolean
  erro: string
  aoSelecionar: (escoteiro: Escoteiro) => void
  aoFechar: () => void
}

const colunas: ColunaGrade<Escoteiro>[] = [
  { id: 'nome', titulo: 'Nome', valor: (e) => e.membro.nome },
  { id: 'cpf', titulo: 'CPF', valor: (e) => e.membro.CPF, renderizar: (e) => formatarCpf(e.membro.CPF) },
  { id: 'matricula', titulo: 'Matrícula', valor: (e) => e.esc_cod, largura: 110 },
  { id: 'patrulha', titulo: 'Patrulha', valor: (e) => e.patrulha.nome },
]

export default function SeletorDeEscoteiro({
  aberto, titulo, descricao, escoteiros, carregando, erro, aoSelecionar, aoFechar,
}: SeletorDeEscoteiroProps) {
  const [busca, setBusca] = useState('')

  const filtrados = escoteiros.filter((e) => combinaBusca(busca, e.membro.nome, e.membro.CPF, e.esc_cod, e.patrulha.nome))

  const fechar = () => {
    setBusca('')
    aoFechar()
  }

  const selecionar = (escoteiro: Escoteiro) => {
    setBusca('')
    aoSelecionar(escoteiro)
  }

  return (
    <Dialog open={aberto} onClose={fechar} fullWidth maxWidth="md">
      <DialogTitle>{titulo}</DialogTitle>
      <DialogContent>
        <div className="seletor-escoteiro">
          <p className="subtitulo">{descricao}</p>
          {carregando && <LinearProgress />}
          {erro && <Alert severity="error">{erro}</Alert>}
          <CampoTexto rotulo="Pesquisar por nome, CPF, matrícula ou patrulha" valor={busca} aoMudar={setBusca} autoFocus />
          <p className="seletor-escoteiro-contagem subtitulo">
            {filtrados.length} de {escoteiros.length} escoteiros
          </p>
          <Grade
            colunas={colunas}
            linhas={filtrados}
            chave={(e) => e.esc_cod}
            aoClicarLinha={selecionar}
            textoVazio="Nenhum escoteiro encontrado."
            linhasPorPagina={6}
          />
        </div>
      </DialogContent>
      <DialogActions>
        <Button onClick={fechar}>Fechar</Button>
      </DialogActions>
    </Dialog>
  )
}
