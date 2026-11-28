package com.example.sigg_backend;

import static org.hamcrest.Matchers.hasItem;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import com.jayway.jsonpath.JsonPath;

// Dump: tropa 2 (Tropa Escoteira) tem 8 escoteiros ativos; Pedro (esc 2007, mem 11, patrulha 3) tem 12 registros
// (9 P, 1 F, 2 J) = 75,0%; há registros em 2026-08-01 para todas as tropas; esc 2012 é da tropa 1.
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class RegistroDeFrequenciaTest {

    // Lançar e cancelar só vale para chamadas de hoje até 10 dias atrás
    private static final String DIA_LIVRE = LocalDate.now().minusDays(2).toString();
    private static final String MENSAGEM_PRAZO_LANCAR = "Só é possível lançar frequência dos últimos 10 dias";
    private static final String MENSAGEM_PRAZO_CANCELAR = "O lançamento só pode ser cancelado até 10 dias depois da data da chamada";
    private static final String MENSAGEM_DUPLICADA = "Já existe frequência desta tropa nesta data. Edite a chamada existente.";

    @Autowired
    MockMvc mvc;
    @Autowired
    JdbcTemplate jdbc;

    private List<Integer> escoteirosDaTropa(int tropa) {
        return jdbc.queryForList("SELECT e.esc_cod FROM escoteiro e JOIN membro m ON m.mem_cod = e.mem_cod"
                + " JOIN patrulha p ON p.pat_cod = e.patrulha_pat_cod2"
                + " WHERE p.tropa_tro_cod = ? AND m.mem_dt_fim IS NULL ORDER BY e.esc_cod", Integer.class, tropa);
    }

    private static String chamada(int tropa, String data, List<Integer> escoteiros, String status) {
        String itens = escoteiros.stream()
                .map(esc -> "{\"esc_cod\":" + esc + ",\"status\":\"" + status + "\"}").collect(Collectors.joining(","));
        return "{\"tropa\":{\"cod\":" + tropa + "},\"data\":" + (data == null ? "null" : "\"" + data + "\"")
                + ",\"itens\":[" + itens + "]}";
    }

    private ResultActions registrar(String json) throws Exception {
        return mvc.perform(post("/frequencias/chamada").contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private ResultActions cancelar(int tropa, String data) throws Exception {
        return mvc.perform(post("/frequencias/chamada/cancelar?tropa=" + tropa + "&data=" + data));
    }

    // Registros da data dos escoteiros ATUAIS da tropa (os desligados ficam de fora, como na chamada)
    private int registros(String data, int tropa) {
        return jdbc.queryForObject("SELECT count(*) FROM frequencia f JOIN escoteiro e ON e.esc_cod = f.escoteiro_esc_cod"
                + " JOIN membro m ON m.mem_cod = e.mem_cod JOIN patrulha p ON p.pat_cod = e.patrulha_pat_cod2"
                + " WHERE f.freq_data = DATE '" + data + "' AND p.tropa_tro_cod = ? AND m.mem_dt_fim IS NULL", Integer.class, tropa);
    }

    @Test
    void chamadaNovaListaOsEscoteirosDaTropaSemStatus() throws Exception {
        mvc.perform(get("/frequencias/chamada?tropa=2&data=" + DIA_LIVRE)).andExpect(status().isOk())
                .andExpect(jsonPath("$.tropa.nome").value("Tropa Escoteira"))
                .andExpect(jsonPath("$.data").value(DIA_LIVRE))
                .andExpect(jsonPath("$.itens.length()").value(8))
                .andExpect(jsonPath("$.itens[*].esc_cod").value(hasItem(2007)))
                .andExpect(jsonPath("$.itens[?(@.status != null)]").isEmpty())
                .andExpect(jsonPath("$.itens[?(@.esc_cod==2007)].percentual_ano").value(hasItem(75.0)));
    }

    @Test
    void registrarGravaUmRegistroPorEscoteiroEAparecemNaGrade() throws Exception {
        registrar(chamada(2, DIA_LIVRE, escoteirosDaTropa(2), "P")).andExpect(status().isCreated())
                .andExpect(jsonPath("$.itens.length()").value(8))
                .andExpect(jsonPath("$.itens[?(@.status != 'P')]").isEmpty());
        assertEquals(8, registros(DIA_LIVRE, 2));
        assertEquals(8, jdbc.queryForObject("SELECT count(*) FROM frequencia WHERE freq_data = DATE '" + DIA_LIVRE
                + "' AND atividade_especial_atv_cod IS NULL", Integer.class));
        mvc.perform(get("/frequencias?tropa=2&ano=2026")).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].data").value(DIA_LIVRE))
                .andExpect(jsonPath("$[0].presentes").value(8))
                .andExpect(jsonPath("$[0].total").value(8))
                .andExpect(jsonPath("$[0].tropa.cod").value(2));
    }

    @Test
    void registrarDuasVezesNaMesmaDataRecusa() throws Exception {
        registrar(chamada(2, DIA_LIVRE, escoteirosDaTropa(2), "P")).andExpect(status().isCreated());
        registrar(chamada(2, DIA_LIVRE, escoteirosDaTropa(2), "F")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erro").value(MENSAGEM_DUPLICADA));
    }

    @Test
    void dataStatusEItensInvalidosSaoRecusados() throws Exception {
        List<Integer> tropa2 = escoteirosDaTropa(2);
        registrar(chamada(2, LocalDate.now().plusDays(1).toString(), tropa2, "P")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erro").value("Data não pode ser futura"));
        registrar(chamada(2, null, tropa2, "P")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erro").value("Data é obrigatória"));
        registrar(chamada(2, DIA_LIVRE, tropa2, "X")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erro").value("Status de presença inválido: use P, F ou J"));
        registrar(chamada(2, DIA_LIVRE, List.of(), "P")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erro").value("Informe ao menos um escoteiro"));
        registrar(chamada(2, DIA_LIVRE, List.of(2012), "P")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erro").value("Escoteiro 2012 não pertence a essa tropa"));
        registrar(chamada(999, DIA_LIVRE, tropa2, "P")).andExpect(status().isNotFound());
        assertEquals(0, registros(DIA_LIVRE, 2));
    }

    @Test
    void lancarForaDoPrazoDeDezDiasRecusa() throws Exception {
        registrar(chamada(2, "2026-08-01", escoteirosDaTropa(2), "P")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erro").value(MENSAGEM_PRAZO_LANCAR));
        registrar(chamada(2, LocalDate.now().minusDays(11).toString(), escoteirosDaTropa(2), "P"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.erro").value(MENSAGEM_PRAZO_LANCAR));
        registrar(chamada(2, LocalDate.now().minusDays(10).toString(), escoteirosDaTropa(2), "P"))
                .andExpect(status().isCreated());
    }

    @Test
    void naoHaMaisEdicaoDaChamada() throws Exception {
        mvc.perform(put("/frequencias/chamada").contentType(MediaType.APPLICATION_JSON)
                .content(chamada(2, DIA_LIVRE, escoteirosDaTropa(2), "P"))).andExpect(status().isMethodNotAllowed());
    }

    private int comStatus(String data, int tropa, String status, int ativo) {
        return jdbc.queryForObject("SELECT count(*) FROM frequencia f JOIN escoteiro e ON e.esc_cod = f.escoteiro_esc_cod"
                + " JOIN membro m ON m.mem_cod = e.mem_cod JOIN patrulha p ON p.pat_cod = e.patrulha_pat_cod2"
                + " WHERE f.freq_data = DATE '" + data + "' AND p.tropa_tro_cod = ? AND m.mem_dt_fim IS NULL"
                + " AND f.freq_status = ? AND f.freq_ativo = ?", Integer.class, tropa, status, ativo);
    }

    // Cancelar não apaga nem troca o status: desativa (freq_ativo = 0) e guarda o P/F/J para a edição vir preenchida
    @Test
    void cancelarGuardaAsPresencasParaEditarELancarDeNovo() throws Exception {
        registrar(chamada(1, DIA_LIVRE, escoteirosDaTropa(1), "P")).andExpect(status().isCreated());
        registrar(chamada(2, DIA_LIVRE, escoteirosDaTropa(2), "F")).andExpect(status().isCreated());

        cancelar(2, DIA_LIVRE).andExpect(status().isOk()).andExpect(jsonPath("$.mensagem").exists());
        assertEquals(8, registros(DIA_LIVRE, 2)); // nada foi apagado
        assertEquals(8, comStatus(DIA_LIVRE, 2, "F", 0)); // status guardado, registro desativado
        assertEquals(10, comStatus(DIA_LIVRE, 1, "P", 1)); // a outra tropa não muda
        mvc.perform(get("/frequencias?tropa=2&ano=" + DIA_LIVRE.substring(0, 4))).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].data").value(DIA_LIVRE))
                .andExpect(jsonPath("$[0].total").value(0))
                .andExpect(jsonPath("$[0].presentes").value(0))
                .andExpect(jsonPath("$[0].pendentes").value(8));
        // a chamada consultada traz o status de antes do cancelamento: é o que preenche o formulário de edição
        mvc.perform(get("/frequencias/chamada?tropa=2&data=" + DIA_LIVRE)).andExpect(status().isOk())
                .andExpect(jsonPath("$.itens[?(@.status != 'F')]").isEmpty())
                .andExpect(jsonPath("$.itens[?(@.ativo != 0)]").isEmpty());
        cancelar(2, DIA_LIVRE).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.erro").value("Não há frequência lançada dessa tropa nessa data"));

        registrar(chamada(2, DIA_LIVRE, escoteirosDaTropa(2), "J")).andExpect(status().isCreated())
                .andExpect(jsonPath("$.itens[?(@.ativo != 1)]").isEmpty());
        assertEquals(8, comStatus(DIA_LIVRE, 2, "J", 1));
        assertEquals(8, registros(DIA_LIVRE, 2)); // reaproveita as linhas, não duplica
        registrar(chamada(2, DIA_LIVRE, escoteirosDaTropa(2), "P")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erro").value(MENSAGEM_DUPLICADA)); // lançada de novo, volta a bloquear
    }

    @Test
    void chamadaLancadaVemComRegistroAtivo() throws Exception {
        registrar(chamada(2, DIA_LIVRE, escoteirosDaTropa(2), "P")).andExpect(status().isCreated());
        mvc.perform(get("/frequencias/chamada?tropa=2&data=" + DIA_LIVRE)).andExpect(status().isOk())
                .andExpect(jsonPath("$.itens[?(@.ativo != 1)]").isEmpty());
        assertEquals(8, comStatus(DIA_LIVRE, 2, "P", 1));
    }

    @Test
    void cancelarForaDoPrazoRecusaEMantemOsRegistros() throws Exception {
        int antes = registros("2026-08-01", 2);
        cancelar(2, "2026-08-01").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erro").value(MENSAGEM_PRAZO_CANCELAR));
        assertEquals(antes, registros("2026-08-01", 2));
    }

    @Test
    void percentualDoAnoContaPresencasSobreOTotalDeRegistros() throws Exception {
        String escoteiro = """
                {"membro":{"nome":"Novo Escoteiro","dt_nascto":"2014-02-02","CPF":"52998224725","RG":"123456789",
                 "CEP":"19025080","endereco":"Rua Teste","endereco_num":10,"endereco_bairro":"Centro",
                 "cidade":"Presidente Prudente","estado":"SP"},"cat_mensalidade":2,"patrulha":{"cod":3}}""";
        MvcResult criado = mvc.perform(post("/escoteiros").contentType(MediaType.APPLICATION_JSON).content(escoteiro))
                .andExpect(status().isCreated()).andReturn();
        int escCod = JsonPath.read(criado.getResponse().getContentAsString(StandardCharsets.UTF_8), "$.esc_cod");
        // o registro desativado (lançamento cancelado) não conta: 2 P sobre 3 ativos
        String[][] registros = {{"2026-01-10", "P", "1"}, {"2026-01-17", "P", "1"}, {"2026-01-24", "F", "1"}, {"2026-01-31", "P", "0"}};
        for (String[] r : registros)
            jdbc.update("INSERT INTO frequencia (freq_cod, freq_data, freq_status, escoteiro_esc_cod, atividade_especial_atv_cod, freq_ativo)"
                    + " VALUES ((SELECT MAX(freq_cod) + 1 FROM frequencia), DATE '" + r[0] + "', '" + r[1] + "', ?, NULL, "
                    + r[2] + ")", escCod);

        mvc.perform(get("/frequencias/percentuais?tropa=2&ano=2026")).andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.esc_cod==" + escCod + ")].percentual_ano").value(hasItem(66.7)));
        mvc.perform(get("/frequencias/percentuais?tropa=2&ano=2025")).andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.esc_cod==" + escCod + ")].percentual_ano").value(hasItem(0)));
    }

    @Test
    void trocaDeTropaMantemOHistoricoDoEscoteiro() throws Exception {
        int antes = jdbc.queryForObject("SELECT count(*) FROM frequencia WHERE escoteiro_esc_cod = 2007", Integer.class);
        mvc.perform(put("/escoteiros/11").contentType(MediaType.APPLICATION_JSON)
                .content("{\"cat_mensalidade\":2,\"patrulha\":{\"cod\":5}}")).andExpect(status().isOk());
        assertEquals(antes, jdbc.queryForObject("SELECT count(*) FROM frequencia WHERE escoteiro_esc_cod = 2007", Integer.class));
        mvc.perform(get("/frequencias/percentuais?tropa=3&ano=2026")).andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.esc_cod==2007)].percentual_ano").value(hasItem(75.0)));
        mvc.perform(get("/frequencias/percentuais?tropa=2&ano=2026")).andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.esc_cod==2007)]").isEmpty());
    }

    @Test
    void listarChamadasFiltraPorAnoEOrdenaDaMaisRecente() throws Exception {
        mvc.perform(get("/frequencias?tropa=1&ano=2026")).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].data").value("2026-10-17"));
        mvc.perform(get("/frequencias?tropa=1&ano=2025")).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
    }
}
