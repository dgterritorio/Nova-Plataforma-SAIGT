package com.conkord.saigt.controller.instrumento;

import com.conkord.saigt.dto.instrumento.AssociacaoInstrumentosResponse;
import com.conkord.saigt.dto.instrumento.AssociarInstrumentosRequest;
import com.conkord.saigt.dto.instrumento.DinamicaResponse;
import com.conkord.saigt.dto.instrumento.DinamicasDisponiveisResponse;
import com.conkord.saigt.dto.instrumento.IniciarDinamicaRequest;
import com.conkord.saigt.dto.instrumento.TiposProcessoResponse;
import com.conkord.saigt.service.instrumento.AssociacaoInstrumentosService;
import com.conkord.saigt.service.instrumento.DinamicaService;
import com.conkord.saigt.service.instrumento.DinamicasSemIgtDisponiveisService;
import com.conkord.saigt.service.instrumento.TiposProcessoService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * O assistente «Criar Processo» e as Dinâmicas que se iniciam sem IGT de partida. Autorização toda nos serviços
 * (`AutorizacaoService`), nunca aqui.
 */
@RestController
public class DinamicaSemIgtController {

    private final TiposProcessoService tiposProcessoService;
    private final DinamicaService dinamicaService;
    private final DinamicasSemIgtDisponiveisService dinamicasSemIgtDisponiveisService;
    private final AssociacaoInstrumentosService associacaoInstrumentosService;

    public DinamicaSemIgtController(TiposProcessoService tiposProcessoService, DinamicaService dinamicaService,
                                    DinamicasSemIgtDisponiveisService dinamicasSemIgtDisponiveisService,
                                    AssociacaoInstrumentosService associacaoInstrumentosService) {
        this.tiposProcessoService = tiposProcessoService;
        this.dinamicaService = dinamicaService;
        this.dinamicasSemIgtDisponiveisService = dinamicasSemIgtDisponiveisService;
        this.associacaoInstrumentosService = associacaoInstrumentosService;
    }

    /** API-PROC-029. Sem cache: depende das permissões configuradas em backoffice. */
    @GetMapping("/api/v1/criacao/tipos-processo")
    public TiposProcessoResponse tiposProcesso(@RequestParam(required = false) String modulo) {
        return tiposProcessoService.tiposProcesso(modulo);
    }

    /** As Dinâmicas iniciadas sem IGT em curso que a sessão acompanha, com os Procedimentos disponíveis. Sem cache HTTP. */
    @GetMapping("/api/v1/dinamicas/disponivel")
    public DinamicasDisponiveisResponse disponivel() {
        return dinamicasSemIgtDisponiveisService.disponivel();
    }

    /** API-PROC-004 sem IGT de partida: a Dinâmica nasce sem Instrumentos, que se associam depois. */
    @PostMapping("/api/v1/dinamicas")
    public ResponseEntity<DinamicaResponse> iniciar(@Valid @RequestBody IniciarDinamicaRequest request) {
        return ResponseEntity.status(201).body(DinamicaResponse.de(dinamicaService.iniciarSemIgt(request)));
    }

    /** API-PROC-037: substitui o conjunto de Instrumentos da Suspensão ou das Medidas Preventivas. */
    @PutMapping("/api/v1/dinamicas/{id}/instrumentos")
    public AssociacaoInstrumentosResponse associarInstrumentos(@PathVariable UUID id,
                                                               @Valid @RequestBody AssociarInstrumentosRequest request) {
        return AssociacaoInstrumentosResponse.de(associacaoInstrumentosService.associar(id, request));
    }
}
