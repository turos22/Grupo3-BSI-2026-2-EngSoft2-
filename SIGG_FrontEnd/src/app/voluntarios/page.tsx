import { useEffect, useState } from 'react'
import Alert from '@mui/material/Alert'
import Box from '@mui/material/Box'
import Chip from '@mui/material/Chip'
import Stack from '@mui/material/Stack'
import CampoSelecao from '@/components/formulario/CampoSelecao'
import CampoTexto from '@/components/formulario/CampoTexto'
import Grade, { type ColunaGrade } from '@/components/grade/Grade'
import type Membro from '@/data/models/Membro'
import { listarMembros } from '@/data/services/membro'
import { combinaBusca } from '@/data/utils/busca'
import { formatarData } from '@/data/utils/datas'
import { mensagemDeErro } from '@/data/utils/erros'
import { formatarTelefone } from '@/data/utils/formatadores'

const TODOS = ''
const PAPEIS = ['Presidente', 'Chefe', 'Financeiro', 'Escoteiro', 'Responsável']
const SO_VOLUNTARIO = 'Só voluntário'

function idade(nascimento: string): number {
  const hoje = new Date()
  const nasc = new Date(`${nascimento}T00:00:00`)
  let anos = hoje.getFullYear() - nasc.getFullYear()
  if (hoje.getMonth() < nasc.getMonth() || (hoje.getMonth() === nasc.getMonth() && hoje.getDate() < nasc.getDate())) anos--
  return anos
}

const colunas: ColunaGrade<Membro>[] = [
  { id: 'nome', titulo: 'Nome', valor: (m) => m.nome },
  { id: 'idade', titulo: 'Idade', valor: (m) => idade(m.dt_nascto), largura: 90 },
  { id: 'nascimento', titulo: 'Nascimento', valor: (m) => m.dt_nascto, renderizar: (m) => formatarData(m.dt_nascto) },
  { id: 'telefone', titulo: 'Telefone', valor: (m) => m.telefone && formatarTelefone(m.telefone) },
  { id: 'email', titulo: 'E-mail', valor: (m) => m.email },
  {
    id: 'papeis',
    titulo: 'Papéis',
    renderizar: (m) => (
      <Stack direction="row" spacing={0.5} useFlexGap sx={{ flexWrap: 'wrap' }}>
        {m.papeis.filter((p) => p !== 'Voluntário').map((papel) => <Chip key={papel} size="small" label={papel} variant="outlined" />)}
      </Stack>
    ),
  },
]

// Voluntário não é cadastro: é qualquer pessoa ativa com 18 anos ou mais
export default function PaginaVoluntarios() {
  const [voluntarios, setVoluntarios] = useState<Membro[]>([])
  const [carregando, setCarregando] = useState(true)
  const [erro, setErro] = useState('')
  const [busca, setBusca] = useState('')
  const [papel, setPapel] = useState<string>(TODOS)

  const filtrados = voluntarios.filter((m) => {
    const outros = m.papeis.filter((p) => p !== 'Voluntário')
    return combinaBusca(busca, m.nome, m.CPF, m.email, m.telefone)
      && (papel === TODOS || (papel === SO_VOLUNTARIO ? outros.length === 0 : outros.includes(papel)))
  })

  useEffect(() => {
    let ativo = true
    listarMembros(true)
      .then((lista) => ativo && setVoluntarios(lista))
      .catch((falha: unknown) => ativo && setErro(mensagemDeErro(falha, 'Não foi possível carregar os voluntários.')))
      .finally(() => ativo && setCarregando(false))
    return () => {
      ativo = false
    }
  }, [])

  return (
    <main className="pagina">
      <header>
        <h1>Voluntários</h1>
        <p className="subtitulo">Pessoas do grupo com 18 anos ou mais</p>
      </header>
      {erro && <Alert severity="error">{erro}</Alert>}
      <Box sx={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(240px, 360px))', gap: 2 }}>
        <CampoTexto rotulo="Buscar" valor={busca} aoMudar={setBusca} placeholder="Nome, CPF, e-mail ou telefone" />
        <CampoSelecao
          rotulo="Também é"
          valor={papel}
          opcoes={[{ valor: TODOS, rotulo: 'Qualquer papel' }, ...PAPEIS.map((p) => ({ valor: p, rotulo: p })), { valor: SO_VOLUNTARIO, rotulo: SO_VOLUNTARIO }]}
          aoMudar={(v) => setPapel(v ?? TODOS)}
        />
      </Box>

      <Grade
        titulo="Voluntários ativos"
        subtitulo={`${filtrados.length} de ${voluntarios.length} ${voluntarios.length === 1 ? 'voluntário' : 'voluntários'}`}
        colunas={colunas}
        linhas={filtrados}
        chave={(m) => m.mem_cod}
        carregando={carregando}
        textoVazio="Nenhum voluntário encontrado."
        linhasPorPagina={10}
      />
    </main>
  )
}
