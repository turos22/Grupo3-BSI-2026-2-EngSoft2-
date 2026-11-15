package com.example.sigg_backend.models;

import java.time.LocalDate;
import java.util.List;

import com.example.sigg_backend.db.dao.FrequenciaDAO;
import com.example.sigg_backend.db.util.Conexao;

// Uma chamada na grade: tropa, data, presentes sobre os lançados e quantos estão pendentes (lançamento cancelado)
public class ResumoChamada {

    private Tropa tropa;
    private LocalDate data;
    private int presentes;
    private int total;
    private int pendentes;

    public ResumoChamada() {
    }

    public ResumoChamada(Tropa tropa, LocalDate data, int presentes, int total, int pendentes) {
        this.tropa = tropa;
        this.data = data;
        this.presentes = presentes;
        this.total = total;
        this.pendentes = pendentes;
    }

    public List<ResumoChamada> listar(Conexao conexao, Integer troCod, int ano) {
        return new FrequenciaDAO().listarResumos(conexao, troCod, ano);
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

    public int getPresentes() {
        return presentes;
    }

    public void setPresentes(int presentes) {
        this.presentes = presentes;
    }

    public int getTotal() {
        return total;
    }

    public void setTotal(int total) {
        this.total = total;
    }

    public int getPendentes() {
        return pendentes;
    }

    public void setPendentes(int pendentes) {
        this.pendentes = pendentes;
    }
}
