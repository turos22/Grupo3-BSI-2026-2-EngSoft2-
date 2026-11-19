package com.example.sigg_backend.facade;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.sigg_backend.models.AtividadePaga;
import com.example.sigg_backend.models.Escoteiro;
import com.example.sigg_backend.models.IntencaoParticipacao;
import com.example.sigg_backend.models.OpcaoIntencao;
import com.example.sigg_backend.models.PagamentoMensalidade;
import com.example.sigg_backend.models.RegistroIntencao;
import com.example.sigg_backend.models.UsuarioLogado;
import com.example.sigg_backend.db.util.Conexao;

// Caso de uso RF_F6 "Registrar Intenção de Participação em Atividades Pagas" (ator: Escoteiro).
// O escoteiro registra para si; o responsável, para os dependentes; a diretoria, em nome de qualquer escoteiro.
// Fluxo 4.3 (seguro anual) fica para o RF_F9, que ainda não guarda o seguro no banco.
// Os dados de entrada chegam já conferidos pelo IntencaoParticipacaoController.
@Service
public class IntencaoParticipacaoFacade {

    private static final String MENSAGEM_NEGADO = "Você só pode registrar intenção de participação para você ou seus dependentes";
    private static final DateTimeFormatter DATA_BR = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final AutorizacaoFacade autorizacao;

    public IntencaoParticipacaoFacade(AutorizacaoFacade autorizacao) {
        this.autorizacao = autorizacao;
    }

    // Para quem o usuário pode registrar: diretoria pesquisa qualquer escoteiro;
    // escoteiro vê a si mesmo e responsável vê os dependentes.
    public List<Escoteiro> escoteiros(Conexao conexao, String busca) {
        UsuarioLogado usuario = autorizacao.segurancaAtiva() ? autorizacao.exigirAutenticado(conexao) : null;
        if (usuario == null || usuario.ehDiretoria()) {
            if (busca == null || busca.isBlank())
                throw new RegraNegocioException("Informe CPF, nome ou matrícula do escoteiro");
            String termo = busca.trim();
            if (termo.matches("[\\d.\\-]+"))
                termo = termo.replaceAll("\\D", "");
            List<Escoteiro> encontrados = new Escoteiro().pesquisar(conexao, termo);
            if (encontrados.isEmpty())
                throw new NaoEncontradoException("Escoteiro não localizado no sistema");
            return encontrados;
        }
        List<Escoteiro> perfis = new ArrayList<>();
        if (usuario.temPerfil(UsuarioLogado.ESCOTEIRO)) {
            Escoteiro proprio = new Escoteiro().buscar(conexao, usuario.getMem_cod());
            if (proprio != null)
                perfis.add(proprio);
        }
        if (usuario.temPerfil(UsuarioLogado.RESPONSAVEL))
            for (Escoteiro dependente : new Escoteiro().doResponsavel(conexao, usuario.getMem_cod()))
                if (perfis.stream().noneMatch(e -> e.getEsc_cod() == dependente.getEsc_cod()))
                    perfis.add(dependente);
        return perfis;
    }

    // Passo 2: busca as atividades pagas abertas, marcando as que o escoteiro já escolheu e as vagas restantes.
    public Map<String, Object> painel(Conexao conexao, int memCod) {
        Escoteiro escoteiro = escoteiroPermitido(conexao, memCod);
        IntencaoParticipacao modelo = new IntencaoParticipacao();
        Set<Integer> registradas = modelo.atividadesDoEscoteiro(conexao, escoteiro.getEsc_cod());
        Map<Integer, Integer> inscritos = modelo.inscritosPorAtividade(conexao);
        List<OpcaoIntencao> opcoes = new ArrayList<>();
        for (AtividadePaga atividade : new AtividadePaga().listar(conexao, true))
            opcoes.add(new OpcaoIntencao(atividade, inscritos.getOrDefault(atividade.getCodigo(), 0),
                    registradas.contains(atividade.getCodigo())));

        Map<String, Object> painel = new LinkedHashMap<>();
        painel.put("mem_cod", escoteiro.getMembro().getMem_cod());
        painel.put("esc_cod", escoteiro.getEsc_cod());
        painel.put("nome", escoteiro.getMembro().getNome());
        painel.put("patrulha", escoteiro.getPatrulha() != null ? escoteiro.getPatrulha().getNome() : null);
        painel.put("mensalidadeEmDia", mensalidadeEmDia(conexao, escoteiro));
        painel.put("atividades", opcoes);
        return painel;
    }

    // Passos 4 a 6: valida as atividades (4.1) e a mensalidade (4.2) e registra todas ou nenhuma.
    @Transactional
    public Map<String, Object> registrar(Conexao conexao, RegistroIntencao registro) {
        Escoteiro escoteiro = escoteiroPermitido(conexao, registro.getMemCod());
        String nome = escoteiro.getMembro().getNome();
        LocalDate hoje = LocalDate.now();

        List<IntencaoParticipacao> intencoes = new ArrayList<>();
        for (int codigo : registro.getAtividades()) {
            AtividadePaga atividade = new AtividadePaga().buscar(conexao, codigo);
            if (atividade == null)
                throw new RegraNegocioException("A atividade paga informada (código " + codigo + ") não está cadastrada");
            IntencaoParticipacao intencao = new IntencaoParticipacao(escoteiro.getEsc_cod(), atividade);
            if (intencao.vencimento().isBefore(hoje))
                throw new RegraNegocioException("O prazo para manifestar interesse em \"" + atividade.getNome()
                        + "\" terminou em " + intencao.vencimento().format(DATA_BR));
            if (intencao.jaRegistrada(conexao))
                throw new RegraNegocioException(nome + " já manifestou interesse em \"" + atividade.getNome() + "\"");
            if (atividade.getVagas() != null && intencao.inscritos(conexao, codigo) >= atividade.getVagas())
                throw new RegraNegocioException("Não há mais vagas em \"" + atividade.getNome() + "\"");
            intencoes.add(intencao);
        }

        if (!mensalidadeEmDia(conexao, escoteiro))
            throw new RegraNegocioException("A mensalidade de " + nome + " está atrasada. Regularize a situação com o"
                    + " financeiro para manifestar interesse em atividades pagas.");

        Integer mensCod = intencoes.get(0).mensalidadeParaVinculo(conexao);
        if (mensCod == null)
            throw new RegraNegocioException(nome + " ainda não tem mensalidade lançada. Procure o financeiro.");

        for (IntencaoParticipacao intencao : intencoes)
            if (!intencao.gravar(conexao, mensCod))
                throw new IllegalStateException("Não foi possível registrar a intenção em " + intencao.getAtividade().getNome());

        Map<String, Object> resposta = new LinkedHashMap<>();
        resposta.put("mensagem", intencoes.size() == 1
                ? "Intenção de participação em \"" + intencoes.get(0).getAtividade().getNome() + "\" registrada para " + nome
                : intencoes.size() + " intenções de participação registradas para " + nome);
        resposta.put("intencoes", intencoes);
        return resposta;
    }

    // Mesma regra do RF_F7: em dia = não marcado como inadimplente e sem mensalidade vencida em aberto.
    private boolean mensalidadeEmDia(Conexao conexao, Escoteiro escoteiro) {
        return escoteiro.getEsc_sit_fin() != 1
                && !new PagamentoMensalidade().possuiMensalidadeVencidaEmAberto(conexao, escoteiro.getEsc_cod());
    }

    private Escoteiro escoteiroPermitido(Conexao conexao, int memCod) {
        if (autorizacao.segurancaAtiva()) {
            UsuarioLogado usuario = autorizacao.exigirAutenticado(conexao);
            boolean proprio = usuario.temPerfil(UsuarioLogado.ESCOTEIRO) && usuario.getMem_cod() == memCod;
            boolean dependente = usuario.temPerfil(UsuarioLogado.RESPONSAVEL) && new Escoteiro()
                    .doResponsavel(conexao, usuario.getMem_cod()).stream()
                    .anyMatch(e -> e.getMembro().getMem_cod() == memCod);
            if (!usuario.ehDiretoria() && !proprio && !dependente)
                throw new AcessoNegadoException(MENSAGEM_NEGADO);
        }
        Escoteiro escoteiro = new Escoteiro().buscar(conexao, memCod);
        if (escoteiro == null)
            throw new NaoEncontradoException("Escoteiro " + memCod + " não encontrado");
        return escoteiro;
    }
}
