package com.example.sigg_backend.facade;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.sigg_backend.models.AtividadePaga;
import com.example.sigg_backend.models.UsuarioLogado;
import com.example.sigg_backend.db.util.Conexao;

// Caso de uso RF_F5 "Ofertar Novas Atividades Pagas" (ator: Presidente).
// Os dados de entrada chegam já conferidos pelo AtividadePagaController.
@Service
public class AtividadePagaFacade {

    private static final List<String> QUEM_OFERTA = List.of(UsuarioLogado.ADMIN, UsuarioLogado.PRESIDENTE);

    private final AutorizacaoFacade autorizacao;

    public AtividadePagaFacade(AutorizacaoFacade autorizacao) {
        this.autorizacao = autorizacao;
    }

    // Passo 2: o sistema busca e exibe as atividades pagas já cadastradas.
    public List<AtividadePaga> listar(Conexao conexao, boolean somenteAbertas) {
        autorizacao.exigirDiretoria(conexao);
        return new AtividadePaga().listar(conexao, somenteAbertas);
    }

    public AtividadePaga buscar(Conexao conexao, int codigo) {
        autorizacao.exigirDiretoria(conexao);
        return existente(conexao, codigo);
    }

    // Passos 5 e 6: valida, registra e deixa disponível na lista de atividades abertas.
    @Transactional
    public AtividadePaga ofertar(Conexao conexao, AtividadePaga atividade) {
        autorizacao.exigirPerfil(conexao, QUEM_OFERTA);
        atividade.setCodigo(0);
        LocalDate hoje = LocalDate.now();
        if (atividade.getData().isBefore(hoje))
            throw new RegraNegocioException("A data de realização não pode estar no passado");
        if (atividade.getPrazoInteresse().isBefore(hoje))
            throw new RegraNegocioException("O prazo para manifestação de interesse não pode estar no passado");
        exigirNaoRepetida(conexao, atividade);
        if (!atividade.gravar(conexao))
            throw new IllegalStateException("Não foi possível registrar a atividade paga");
        return atividade;
    }

    // Alterar uma oferta: mesmas regras do cadastro. Data e prazo só não podem ir para o passado
    // quando forem mudados (assim dá para corrigir o local ou a descrição de uma atividade já encerrada).
    @Transactional
    public AtividadePaga alterar(Conexao conexao, int codigo, AtividadePaga dados) {
        autorizacao.exigirPerfil(conexao, QUEM_OFERTA);
        AtividadePaga atual = existente(conexao, codigo);
        dados.setCodigo(codigo);
        LocalDate hoje = LocalDate.now();
        if (!dados.getData().equals(atual.getData()) && dados.getData().isBefore(hoje))
            throw new RegraNegocioException("A data de realização não pode estar no passado");
        if (!dados.getPrazoInteresse().equals(atual.getPrazoInteresse()) && dados.getPrazoInteresse().isBefore(hoje))
            throw new RegraNegocioException("O prazo para manifestação de interesse não pode estar no passado");
        exigirNaoRepetida(conexao, dados);
        if (!dados.alterar(conexao))
            throw new IllegalStateException("Não foi possível alterar a atividade paga");
        return dados;
    }

    // Excluir uma oferta: só enquanto nenhum escoteiro estiver inscrito ou tiver pago por ela.
    @Transactional
    public Map<String, String> excluir(Conexao conexao, int codigo) {
        autorizacao.exigirPerfil(conexao, QUEM_OFERTA);
        AtividadePaga atividade = existente(conexao, codigo);
        if (atividade.temVinculos(conexao))
            throw new RegraNegocioException(
                    "Não é possível excluir a atividade: ela já tem inscrições ou pagamentos registrados");
        if (!atividade.apagar(conexao))
            throw new IllegalStateException("Não foi possível excluir a atividade paga");
        return Map.of("mensagem", "Atividade " + atividade.getNome() + " excluída");
    }

    private AtividadePaga existente(Conexao conexao, int codigo) {
        AtividadePaga atividade = new AtividadePaga().buscar(conexao, codigo);
        if (atividade == null)
            throw new NaoEncontradoException("Atividade paga " + codigo + " não encontrada");
        return atividade;
    }

    private void exigirNaoRepetida(Conexao conexao, AtividadePaga atividade) {
        if (atividade.jaCadastrada(conexao))
            throw new RegraNegocioException(
                    "Já existe uma atividade paga cadastrada com o mesmo nome e data de realização");
    }
}
