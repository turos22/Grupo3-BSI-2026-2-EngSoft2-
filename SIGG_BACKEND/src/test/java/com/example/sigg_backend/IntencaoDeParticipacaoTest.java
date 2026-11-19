package com.example.sigg_backend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.Arrays;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

// RF_F6 Registrar Intenção de Participação em Atividades Pagas: fluxo básico e fluxos alternativos 4.1 e 4.2.
// Escoteiros do dump: 14 (esc 2009) e 15 (esc 2010) em dia; 16 (esc 2011) com mensalidade vencida.
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class IntencaoDeParticipacaoTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    JdbcTemplate jdbc;

    private final LocalDate prazo = LocalDate.now().plusWeeks(1);
    private int acampamento;

    // Atividade paga aberta, com 2 vagas, criada só para o teste
    @BeforeEach
    void ofertarAtividade() {
        acampamento = jdbc.queryForObject("SELECT max(atv_cod) FROM atividade_especial", Integer.class) + 1;
        jdbc.update("INSERT INTO atividade_especial (atv_cod, atv_nome, atv_desc, atv_data, atv_local, atv_valor,"
                + " atv_vagas, atv_prazo_interesse) VALUES (?, 'Acampamento de Teste', NULL, ?, 'Sítio', 150.5, 2, ?)",
                acampamento, LocalDate.now().plusWeeks(2), prazo);
    }

    private ResultActions registrar(int memCod, int... atividades) throws Exception {
        String lista = Arrays.toString(atividades);
        return mvc.perform(post("/intencoes-participacao").contentType(MediaType.APPLICATION_JSON)
                .content("{\"memCod\":%d,\"atividades\":%s}".formatted(memCod, lista)));
    }

    private int inscricoes(int escCod, int atvCod) {
        return jdbc.queryForObject("SELECT count(*) FROM contas_receber cr JOIN mensalidade me"
                + " ON me.mens_cod = cr.mensalidade_mens_cod WHERE me.escoteiro_esc_cod = ?"
                + " AND cr.atividade_especial_atv_cod = ?", Integer.class, escCod, atvCod);
    }

    @Test
    void registrarGravaAInscricaoQueOPagamentoDeAtividadeEnxerga() throws Exception {
        registrar(14, acampamento)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.mensagem")
                        .value("Intenção de participação em \"Acampamento de Teste\" registrada para Lucas Alves Oliveira"))
                .andExpect(jsonPath("$.intencoes[0].atividade.codigo").value(acampamento));

        assertEquals(1, inscricoes(2009, acampamento));
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM pagamento p JOIN contas_receber cr"
                + " ON cr.cr_cod = p.contas_receber_cr_cod WHERE cr.atividade_especial_atv_cod = ?"
                + " AND cr.cr_valor = 150.5 AND p.pg_dt_pagto IS NULL AND p.pg_valor_pago = 0 AND p.pg_dt_vencto = ?",
                Integer.class, acampamento, prazo));

        // RF_F7: a inscrição aparece para o financeiro como atividade em aberto
        mvc.perform(get("/pagamentos-atividade/escoteiros/2009"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.atividades[?(@.atvCod == %d)].situacao".formatted(acampamento)).value("ABERTA"));
    }

    @Test
    void painelListaAsAbertasComVagasEOQueOEscoteiroJaEscolheu() throws Exception {
        registrar(15, acampamento).andExpect(status().isCreated());

        mvc.perform(get("/intencoes-participacao/escoteiros/14"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mensalidadeEmDia").value(true))
                .andExpect(jsonPath("$.atividades[*].atividade.nome", Matchers.hasItem("Acampamento de Teste")))
                .andExpect(jsonPath("$.atividades[*].atividade.nome", Matchers.not(Matchers.hasItem("Passeio ao Zoológico"))))
                .andExpect(jsonPath("$.atividades[?(@.atividade.codigo == %d)].vagasRestantes".formatted(acampamento)).value(1))
                .andExpect(jsonPath("$.atividades[?(@.atividade.codigo == %d)].registrada".formatted(acampamento)).value(false));

        mvc.perform(get("/intencoes-participacao/escoteiros/15"))
                .andExpect(jsonPath("$.atividades[?(@.atividade.codigo == %d)].registrada".formatted(acampamento)).value(true));
    }

    @Test
    void atividadeNaoCadastradaOuEncerradaNaoEValida() throws Exception {
        registrar(14, 99999)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erro").value("A atividade paga informada (código 99999) não está cadastrada"));
        // Reunião semanal não é atividade paga
        registrar(14, 2)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erro").value("A atividade paga informada (código 2) não está cadastrada"));
        registrar(14, 6)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erro")
                        .value("O prazo para manifestar interesse em \"Passeio ao Zoológico\" terminou em 23/08/2026"));
    }

    @Test
    void mensalidadeAtrasadaEncerraSemRegistrar() throws Exception {
        registrar(16, acampamento)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erro").value("A mensalidade de Gabriel Alves Oliveira está atrasada."
                        + " Regularize a situação com o financeiro para manifestar interesse em atividades pagas."));
        assertEquals(0, inscricoes(2011, acampamento));
    }

    @Test
    void naoRegistraDuasVezesNemAlemDasVagas() throws Exception {
        registrar(14, acampamento).andExpect(status().isCreated());
        registrar(14, acampamento)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erro").value("Lucas Alves Oliveira já manifestou interesse em \"Acampamento de Teste\""));

        jdbc.update("UPDATE atividade_especial SET atv_vagas = 1 WHERE atv_cod = ?", acampamento);
        registrar(15, acampamento)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erro").value("Não há mais vagas em \"Acampamento de Teste\""));
    }

    @Test
    void variasAtividadesSaoRegistradasTodasOuNenhuma() throws Exception {
        registrar(14, acampamento, 99999).andExpect(status().isBadRequest());
        assertEquals(0, inscricoes(2009, acampamento));

        registrar(14, acampamento, 16, acampamento)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.mensagem").value("2 intenções de participação registradas para Lucas Alves Oliveira"));
        assertEquals(1, inscricoes(2009, acampamento));
        assertEquals(1, inscricoes(2009, 16));
    }

    @Test
    void semAtividadeInformadaERecusado() throws Exception {
        registrar(14)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erro").value("Selecione ao menos uma atividade paga"));
    }
}
