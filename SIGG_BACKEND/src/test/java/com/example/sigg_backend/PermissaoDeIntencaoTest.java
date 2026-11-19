package com.example.sigg_backend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import com.example.sigg_backend.db.util.Conexao;
import com.example.sigg_backend.facade.AcessoNegadoException;
import com.example.sigg_backend.facade.IntencaoParticipacaoFacade;
import com.example.sigg_backend.facade.RegraNegocioException;
import com.example.sigg_backend.models.Escoteiro;
import com.example.sigg_backend.models.RegistroIntencao;

// Segurança ligada. Contas do dump: 6 = escoteira adulta (menor de idade não tem conta) e isenta, 7 = chefe,
// 13 = responsável por 14, 15 e 16.
@SpringBootTest(properties = "sigg.seguranca.ativa=true")
@Transactional
class PermissaoDeIntencaoTest {

    @Autowired
    IntencaoParticipacaoFacade facade;
    @Autowired
    Conexao conexao;
    @Autowired
    JdbcTemplate jdbc;

    private int atividade;

    @BeforeEach
    void preparar() {
        atividade = jdbc.queryForObject("SELECT max(atv_cod) FROM atividade_especial", Integer.class) + 1;
        jdbc.update("INSERT INTO atividade_especial (atv_cod, atv_nome, atv_data, atv_local, atv_valor, atv_vagas,"
                + " atv_prazo_interesse) VALUES (?, 'Trilha de Teste', ?, 'Parque', 20, 10, ?)",
                atividade, LocalDate.now().plusWeeks(2), LocalDate.now().plusWeeks(1));
    }

    @AfterEach
    void sair() {
        ComoUsuario.sair();
    }

    private RegistroIntencao registro(int memCod) {
        RegistroIntencao registro = new RegistroIntencao();
        registro.setMemCod(memCod);
        registro.setAtividades(List.of(atividade));
        return registro;
    }

    private int inscritos() {
        return jdbc.queryForObject("SELECT count(*) FROM contas_receber WHERE atividade_especial_atv_cod = ?",
                Integer.class, atividade);
    }

    @Test
    void escoteiroRegistraSoParaSiMesmo() {
        // a inscrição fica ligada a uma mensalidade (contas_receber exige): dá uma à escoteira isenta
        int mensalidade = jdbc.queryForObject("SELECT max(mens_cod) FROM mensalidade", Integer.class) + 1;
        jdbc.update("INSERT INTO mensalidade (mens_cod, mens_mes, mens_ano, mens_dt_lancto, mens_valor,"
                + " escoteiro_esc_cod, categoria_mensalidade_cat_mens_cod) VALUES (?, 11, 2026, CURRENT_DATE, 0, 2004, 5)",
                mensalidade);
        ComoUsuario.entrar(6, "ESCOTEIRO");
        facade.registrar(conexao, registro(6));
        assertThrows(AcessoNegadoException.class, () -> facade.registrar(conexao, registro(14)));
        assertEquals(List.of(6), facade.escoteiros(conexao, null).stream().map(e -> e.getMembro().getMem_cod()).toList());
        assertEquals(1, inscritos());
    }

    @Test
    void escoteiroSemMensalidadeLancadaNaoConsegueRegistrar() {
        ComoUsuario.entrar(6, "ESCOTEIRO");
        RegraNegocioException erro = assertThrows(RegraNegocioException.class, () -> facade.registrar(conexao, registro(6)));
        assertEquals("Renata Cavalcante Dias ainda não tem mensalidade lançada. Procure o financeiro.", erro.getMessage());
        assertEquals(0, inscritos());
    }

    @Test
    void responsavelRegistraParaOsDependentesENaoParaOutros() {
        ComoUsuario.entrar(13, "RESPONSAVEL");
        facade.registrar(conexao, registro(15));
        assertThrows(AcessoNegadoException.class, () -> facade.registrar(conexao, registro(19)));
        assertThrows(AcessoNegadoException.class, () -> facade.painel(conexao, 19));
        assertEquals(1, inscritos());
    }

    @Test
    void responsavelVeSoOsDependentes() {
        ComoUsuario.entrar(13, "RESPONSAVEL");
        List<Integer> memCods = facade.escoteiros(conexao, null).stream().map(e -> e.getMembro().getMem_cod()).toList();
        assertEquals(List.of(14, 15, 16), memCods.stream().sorted().toList());
    }

    @Test
    void diretoriaPesquisaQualquerEscoteiroERegistraEmNomeDele() {
        ComoUsuario.entrar(7, "CHEFE");
        assertThrows(RegraNegocioException.class, () -> facade.escoteiros(conexao, " "));
        List<Escoteiro> encontrados = facade.escoteiros(conexao, "Davi Pires");
        assertEquals(19, encontrados.get(0).getMembro().getMem_cod());
        facade.registrar(conexao, registro(14));
        assertEquals(1, inscritos());
    }
}
