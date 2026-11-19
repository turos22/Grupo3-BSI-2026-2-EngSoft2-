package com.example.sigg_backend.models;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

// RF_F6: corpo do POST /intencoes-participacao. O escoteiro (mem_cod) e as atividades pagas escolhidas.
public class RegistroIntencao {

    private int memCod;
    private List<Integer> atividades = new ArrayList<>();

    public RegistroIntencao() {
    }

    // Passo 3: o escoteiro informa as atividades. Códigos repetidos contam uma vez só.
    public String validar() {
        if (memCod <= 0)
            return "Informe o escoteiro";
        if (atividades == null || atividades.isEmpty())
            return "Selecione ao menos uma atividade paga";
        if (atividades.contains(null))
            return "Atividade paga inválida";
        atividades = new ArrayList<>(new LinkedHashSet<>(atividades));
        return null;
    }

    public int getMemCod() {
        return memCod;
    }

    public void setMemCod(int memCod) {
        this.memCod = memCod;
    }

    public List<Integer> getAtividades() {
        return atividades;
    }

    public void setAtividades(List<Integer> atividades) {
        this.atividades = atividades;
    }
}
