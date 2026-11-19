package com.example.sigg_backend.models;

import java.util.ArrayList;
import java.util.List;

public class CandidatosContribuicao {
    private List<Escoteiro> anjos = new ArrayList<>();
    private List<Escoteiro> adotados = new ArrayList<>();

    public CandidatosContribuicao() {
    }

    public List<Escoteiro> getAnjos() {
        return anjos;
    }

    public void setAnjos(List<Escoteiro> anjos) {
        this.anjos = anjos;
    }

    public List<Escoteiro> getAdotados() {
        return adotados;
    }

    public void setAdotados(List<Escoteiro> adotados) {
        this.adotados = adotados;
    }
}
