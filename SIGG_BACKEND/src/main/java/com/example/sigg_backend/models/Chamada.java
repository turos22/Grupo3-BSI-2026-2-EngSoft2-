package com.example.sigg_backend.models;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.example.sigg_backend.db.dao.FrequenciaDAO;
import com.example.sigg_backend.db.util.Conexao;

// A chamada de uma tropa numa data não é gravada como um todo: é o conjunto dos registros de frequência
// daquela data para os escoteiros que estão hoje nas patrulhas da tropa.
public class Chamada {

    // Não há edição: um lançamento errado é cancelado e lançado de novo, só para chamadas de hoje até 10 dias atrás
    public static final int PRAZO_DIAS = 10;

    private Tropa tropa;
    private LocalDate data;
    private List<ItemChamada> itens = new ArrayList<>();

    public String validar() {
        if (tropa == null || tropa.getCod() <= 0)
            return "Tropa é obrigatória";
        if (data == null)
            return "Data é obrigatória";
        if (data.isAfter(LocalDate.now()))
            return "Data não pode ser futura";
        if (!dentroDoPrazo(data))
            return "Só é possível lançar frequência dos últimos " + PRAZO_DIAS + " dias";
        if (itens == null || itens.isEmpty())
            return "Informe ao menos um escoteiro";
        Set<Integer> vistos = new HashSet<>();
        for (ItemChamada item : itens) {
            if (item.getStatus() == null || !Frequencia.STATUS_VALIDOS.contains(item.getStatus()))
                return "Status de presença inválido: use P, F ou J";
            if (!vistos.add(item.getEsc_cod()))
                return "Escoteiro " + item.getEsc_cod() + " aparece mais de uma vez";
        }
        return null;
    }

    public static boolean dentroDoPrazo(LocalDate data) {
        return !data.isBefore(LocalDate.now().minusDays(PRAZO_DIAS));
    }

    // Monta a chamada a partir do banco: todos os escoteiros atuais da tropa, com o status gravado na data
    public Chamada consultar(Conexao conexao) {
        Chamada chamada = new Chamada();
        chamada.setTropa(tropa);
        chamada.setData(data);
        chamada.setItens(new FrequenciaDAO().itensDaTropa(conexao, tropa.getCod(), data, data.getYear()));
        return chamada;
    }

    public List<ItemChamada> percentuais(Conexao conexao, int ano) {
        return new FrequenciaDAO().itensDaTropa(conexao, tropa.getCod(), null, ano);
    }

    public boolean temLancamento(Conexao conexao) {
        return new FrequenciaDAO().existeLancamento(conexao, tropa.getCod(), data);
    }

    public Integer primeiroEscoteiroDeOutraTropa(Conexao conexao) {
        Set<Integer> daTropa = new FrequenciaDAO().escoteirosDaTropa(conexao, tropa.getCod());
        return itens.stream().map(ItemChamada::getEsc_cod).filter(esc -> !daTropa.contains(esc)).findFirst().orElse(null);
    }

    public boolean registrar(Conexao conexao) {
        for (ItemChamada item : itens)
            if (!new Frequencia(item.getEsc_cod(), data, item.getStatus()).gravar(conexao))
                return false;
        return true;
    }

    public boolean cancelar(Conexao conexao) {
        return new FrequenciaDAO().cancelarDaTropa(conexao, tropa.getCod(), data);
    }

    public Tropa getTropa() {
        return tropa;
    }

    public void setTropa(Tropa tropa) {
        this.tropa = tropa;
    }

    public LocalDate getData() {
        return data;
    }

    public void setData(LocalDate data) {
        this.data = data;
    }

    public List<ItemChamada> getItens() {
        return itens;
    }

    public void setItens(List<ItemChamada> itens) {
        this.itens = itens;
    }
}
