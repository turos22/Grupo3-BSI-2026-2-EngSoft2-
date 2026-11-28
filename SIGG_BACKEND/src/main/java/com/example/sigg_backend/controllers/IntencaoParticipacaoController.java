package com.example.sigg_backend.controllers;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.sigg_backend.facade.IntencaoParticipacaoFacade;
import com.example.sigg_backend.facade.RegraNegocioException;
import com.example.sigg_backend.models.RegistroIntencao;
import com.example.sigg_backend.db.util.Conexao;

// RF_F6 Registrar Intenção de Participação em Atividades Pagas
@CrossOrigin(origins = {"http://127.0.0.1:5500", "http://localhost:5173"})
@RestController
@RequestMapping("/intencoes-participacao")
public class IntencaoParticipacaoController {

    private final Conexao conexao;
    private final IntencaoParticipacaoFacade facade;

    public IntencaoParticipacaoController(Conexao conexao, IntencaoParticipacaoFacade facade) {
        this.conexao = conexao;
        this.facade = facade;
    }

    @GetMapping("/escoteiros")
    public ResponseEntity<Object> escoteiros(@RequestParam(required = false) String busca) {
        return ResponseEntity.ok(facade.escoteiros(conexao, busca));
    }

    @GetMapping("/escoteiros/{memCod}")
    public ResponseEntity<Object> painel(@PathVariable int memCod) {
        return ResponseEntity.ok(facade.painel(conexao, memCod));
    }

    @PostMapping
    public ResponseEntity<Object> registrar(@RequestBody RegistroIntencao registro) {
        if (registro == null)
            throw new RegraNegocioException("Os dados da intenção de participação são obrigatórios");
        String problema = registro.validar();
        if (problema != null)
            throw new RegraNegocioException(problema);
        return ResponseEntity.status(HttpStatus.CREATED).body(facade.registrar(conexao, registro));
    }
}
