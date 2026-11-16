package com.conkord.saigt.controller.configuracao;

import com.conkord.saigt.dto.configuracao.DinamicaConfigRequest;
import com.conkord.saigt.dto.configuracao.DinamicaConfigResponse;
import com.conkord.saigt.dto.configuracao.ProcedimentoConfigRequest;
import com.conkord.saigt.dto.configuracao.PermissaoConfigRequest;
import com.conkord.saigt.dto.configuracao.PermissaoConfigResponse;
import com.conkord.saigt.dto.configuracao.ProcedimentoConfigResponse;
import com.conkord.saigt.dto.configuracao.RedeDefesaConfigRequest;
import com.conkord.saigt.dto.configuracao.RedeDefesaConfigResponse;
import com.conkord.saigt.dto.configuracao.RegraConfigRequest;
import com.conkord.saigt.dto.configuracao.RegraConfigResponse;
import com.conkord.saigt.dto.configuracao.TipoAtoConfigRequest;
import com.conkord.saigt.dto.configuracao.TipoAtoConfigResponse;
import com.conkord.saigt.dto.configuracao.TipoDinamicaConfigRequest;
import com.conkord.saigt.dto.configuracao.TipoDinamicaConfigResponse;
import com.conkord.saigt.dto.configuracao.TipoInstrumentoConfigRequest;
import com.conkord.saigt.dto.configuracao.TipoInstrumentoConfigResponse;
import com.conkord.saigt.dto.configuracao.TipoProcedimentoConfigRequest;
import com.conkord.saigt.dto.configuracao.TipoProcedimentoConfigResponse;
import com.conkord.saigt.dto.core.PageResponse;
import com.conkord.saigt.persistence.core.TipoEntidade;
import com.conkord.saigt.service.configuracao.ConfiguracaoEstruturaService;
import com.conkord.saigt.service.configuracao.ConfiguracaoPermissoesCatalogosService;
import com.conkord.saigt.service.configuracao.ConfiguracaoRegrasService;
import com.conkord.saigt.service.configuracao.ConfiguracaoTiposService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Só MASTER. */
@RestController
public class ConfiguracaoController {

    private static final int TAMANHO_PAGINA_DEFEITO = 20;
    private static final int TAMANHO_PAGINA_MAXIMO = 100;

    private final ConfiguracaoTiposService configuracaoTiposService;
    private final ConfiguracaoEstruturaService configuracaoEstruturaService;
    private final ConfiguracaoPermissoesCatalogosService configuracaoPermissoesCatalogosService;
    private final ConfiguracaoRegrasService configuracaoRegrasService;

    public ConfiguracaoController(ConfiguracaoTiposService configuracaoTiposService,
                                  ConfiguracaoEstruturaService configuracaoEstruturaService,
                                  ConfiguracaoPermissoesCatalogosService configuracaoPermissoesCatalogosService,
                                  ConfiguracaoRegrasService configuracaoRegrasService) {
        this.configuracaoTiposService = configuracaoTiposService;
        this.configuracaoEstruturaService = configuracaoEstruturaService;
        this.configuracaoPermissoesCatalogosService = configuracaoPermissoesCatalogosService;
        this.configuracaoRegrasService = configuracaoRegrasService;
    }

    @GetMapping("/api/v1/configuracao/dinamicas-config")
    public PageResponse<DinamicaConfigResponse> listarDinamicasConfig(
            @RequestParam(required = false) Long tipoInstrumentoId,
            @RequestParam(required = false) Boolean ativo,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "" + TAMANHO_PAGINA_DEFEITO) int size) {
        return paginar(configuracaoEstruturaService.listarDinamicas(tipoInstrumentoId, ativo, paginaOrdenadaNaConsulta(page, size)));
    }

    @PostMapping("/api/v1/configuracao/dinamicas-config")
    @ResponseStatus(HttpStatus.CREATED)
    public DinamicaConfigResponse criarDinamicaConfig(@Valid @RequestBody DinamicaConfigRequest request) {
        return configuracaoEstruturaService.criarDinamica(request);
    }

    @PutMapping("/api/v1/configuracao/dinamicas-config/{id}")
    public DinamicaConfigResponse atualizarDinamicaConfig(@PathVariable Long id,
                                                          @Valid @RequestBody DinamicaConfigRequest request) {
        return configuracaoEstruturaService.atualizarDinamica(id, request);
    }

    @GetMapping("/api/v1/configuracao/procedimentos-config")
    public PageResponse<ProcedimentoConfigResponse> listarProcedimentosConfig(
            @RequestParam(required = false) Long instrumentoDinamicaConfigId,
            @RequestParam(required = false) Boolean ativo,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "" + TAMANHO_PAGINA_DEFEITO) int size) {
        return paginar(configuracaoEstruturaService.listarProcedimentos(instrumentoDinamicaConfigId, ativo,
                paginaOrdenadaNaConsulta(page, size)));
    }

    @PostMapping("/api/v1/configuracao/procedimentos-config")
    @ResponseStatus(HttpStatus.CREATED)
    public ProcedimentoConfigResponse criarProcedimentoConfig(@Valid @RequestBody ProcedimentoConfigRequest request) {
        return configuracaoEstruturaService.criarProcedimento(request);
    }

    @PutMapping("/api/v1/configuracao/procedimentos-config/{id}")
    public ProcedimentoConfigResponse atualizarProcedimentoConfig(@PathVariable Long id,
                                                                  @Valid @RequestBody ProcedimentoConfigRequest request) {
        return configuracaoEstruturaService.atualizarProcedimento(id, request);
    }

    @GetMapping("/api/v1/configuracao/tipos-instrumento")
    public PageResponse<TipoInstrumentoConfigResponse> listarTiposInstrumento(
            @RequestParam(required = false) Boolean ativo,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "" + TAMANHO_PAGINA_DEFEITO) int size) {
        return paginar(configuracaoTiposService.listarTiposInstrumento(ativo, pagina(page, size)));
    }

    @PostMapping("/api/v1/configuracao/tipos-instrumento")
    @ResponseStatus(HttpStatus.CREATED)
    public TipoInstrumentoConfigResponse criarTipoInstrumento(@Valid @RequestBody TipoInstrumentoConfigRequest request) {
        return configuracaoTiposService.criarTipoInstrumento(request);
    }

    @PutMapping("/api/v1/configuracao/tipos-instrumento/{id}")
    public TipoInstrumentoConfigResponse atualizarTipoInstrumento(@PathVariable Long id,
                                                                  @Valid @RequestBody TipoInstrumentoConfigRequest request) {
        return configuracaoTiposService.atualizarTipoInstrumento(id, request);
    }

    @GetMapping("/api/v1/configuracao/tipos-dinamica")
    public PageResponse<TipoDinamicaConfigResponse> listarTiposDinamica(
            @RequestParam(required = false) Boolean ativo,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "" + TAMANHO_PAGINA_DEFEITO) int size) {
        return paginar(configuracaoTiposService.listarTiposDinamica(ativo, pagina(page, size)));
    }

    @PostMapping("/api/v1/configuracao/tipos-dinamica")
    @ResponseStatus(HttpStatus.CREATED)
    public TipoDinamicaConfigResponse criarTipoDinamica(@Valid @RequestBody TipoDinamicaConfigRequest request) {
        return configuracaoTiposService.criarTipoDinamica(request);
    }

    @PutMapping("/api/v1/configuracao/tipos-dinamica/{id}")
    public TipoDinamicaConfigResponse atualizarTipoDinamica(@PathVariable Long id,
                                                            @Valid @RequestBody TipoDinamicaConfigRequest request) {
        return configuracaoTiposService.atualizarTipoDinamica(id, request);
    }

    @GetMapping("/api/v1/configuracao/tipos-procedimento")
    public PageResponse<TipoProcedimentoConfigResponse> listarTiposProcedimento(
            @RequestParam(required = false) Boolean ativo,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "" + TAMANHO_PAGINA_DEFEITO) int size) {
        return paginar(configuracaoTiposService.listarTiposProcedimento(ativo, pagina(page, size)));
    }

    @PostMapping("/api/v1/configuracao/tipos-procedimento")
    @ResponseStatus(HttpStatus.CREATED)
    public TipoProcedimentoConfigResponse criarTipoProcedimento(@Valid @RequestBody TipoProcedimentoConfigRequest request) {
        return configuracaoTiposService.criarTipoProcedimento(request);
    }

    @PutMapping("/api/v1/configuracao/tipos-procedimento/{id}")
    public TipoProcedimentoConfigResponse atualizarTipoProcedimento(@PathVariable Long id,
                                                                    @Valid @RequestBody TipoProcedimentoConfigRequest request) {
        return configuracaoTiposService.atualizarTipoProcedimento(id, request);
    }

    @GetMapping("/api/v1/configuracao/permissoes")
    public PageResponse<PermissaoConfigResponse> listarPermissoes(
            @RequestParam(required = false) Long instrumentoDinamicaConfigId,
            @RequestParam(required = false) Long instrumentoProcedimentoConfigId,
            @RequestParam(required = false) TipoEntidade tipoEntidade,
            @RequestParam(required = false) Boolean ativo,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "" + TAMANHO_PAGINA_DEFEITO) int size) {
        return paginar(configuracaoPermissoesCatalogosService.listarPermissoes(instrumentoDinamicaConfigId,
                instrumentoProcedimentoConfigId, tipoEntidade, ativo, paginaOrdenadaNaConsulta(page, size)));
    }

    @PostMapping("/api/v1/configuracao/permissoes")
    @ResponseStatus(HttpStatus.CREATED)
    public PermissaoConfigResponse criarPermissao(@Valid @RequestBody PermissaoConfigRequest request) {
        return configuracaoPermissoesCatalogosService.criarPermissao(request);
    }

    @PutMapping("/api/v1/configuracao/permissoes/{id}")
    public PermissaoConfigResponse atualizarPermissao(@PathVariable Long id,
                                                      @Valid @RequestBody PermissaoConfigRequest request) {
        return configuracaoPermissoesCatalogosService.atualizarPermissao(id, request);
    }

    @GetMapping("/api/v1/configuracao/tipos-ato")
    public PageResponse<TipoAtoConfigResponse> listarTiposAto(
            @RequestParam(required = false) Boolean ativo,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "" + TAMANHO_PAGINA_DEFEITO) int size) {
        return paginar(configuracaoPermissoesCatalogosService.listarTiposAto(ativo, pagina(page, size, "ordem")));
    }

    @PostMapping("/api/v1/configuracao/tipos-ato")
    @ResponseStatus(HttpStatus.CREATED)
    public TipoAtoConfigResponse criarTipoAto(@Valid @RequestBody TipoAtoConfigRequest request) {
        return configuracaoPermissoesCatalogosService.criarTipoAto(request);
    }

    @PutMapping("/api/v1/configuracao/tipos-ato/{id}")
    public TipoAtoConfigResponse atualizarTipoAto(@PathVariable Long id, @Valid @RequestBody TipoAtoConfigRequest request) {
        return configuracaoPermissoesCatalogosService.atualizarTipoAto(id, request);
    }

    @GetMapping("/api/v1/configuracao/redes-defesa")
    public PageResponse<RedeDefesaConfigResponse> listarRedesDefesa(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "" + TAMANHO_PAGINA_DEFEITO) int size) {
        return paginar(configuracaoPermissoesCatalogosService.listarRedesDefesa(pagina(page, size)));
    }

    @PostMapping("/api/v1/configuracao/redes-defesa")
    @ResponseStatus(HttpStatus.CREATED)
    public RedeDefesaConfigResponse criarRedeDefesa(@Valid @RequestBody RedeDefesaConfigRequest request) {
        return configuracaoPermissoesCatalogosService.criarRedeDefesa(request);
    }

    @PutMapping("/api/v1/configuracao/redes-defesa/{id}")
    public RedeDefesaConfigResponse atualizarRedeDefesa(@PathVariable Long id,
                                                        @Valid @RequestBody RedeDefesaConfigRequest request) {
        return configuracaoPermissoesCatalogosService.atualizarRedeDefesa(id, request);
    }

    @GetMapping("/api/v1/configuracao/regras")
    public PageResponse<RegraConfigResponse> listarRegras(
            @RequestParam(required = false) Long instrumentoDinamicaConfigId,
            @RequestParam(required = false) Long instrumentoProcedimentoConfigId,
            @RequestParam(required = false) Long dinamicaDosProcedimentosId,
            @RequestParam(required = false) Boolean ativo,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "" + TAMANHO_PAGINA_DEFEITO) int size) {
        return paginar(configuracaoRegrasService.listar(instrumentoDinamicaConfigId, instrumentoProcedimentoConfigId,
                dinamicaDosProcedimentosId, ativo, paginaOrdenadaNaConsulta(page, size)));
    }

    @PostMapping("/api/v1/configuracao/regras")
    @ResponseStatus(HttpStatus.CREATED)
    public RegraConfigResponse criarRegra(@Valid @RequestBody RegraConfigRequest request) {
        return configuracaoRegrasService.criar(request);
    }

    @PutMapping("/api/v1/configuracao/regras/{id}")
    public RegraConfigResponse atualizarRegra(@PathVariable Long id, @Valid @RequestBody RegraConfigRequest request) {
        return configuracaoRegrasService.atualizar(id, request);
    }

    private static Pageable pagina(int page, int size) {
        return pagina(page, size, "codigo");
    }

    private static Pageable pagina(int page, int size, String ordenacao) {
        int tamanhoAplicado = Math.max(1, Math.min(size, TAMANHO_PAGINA_MAXIMO));
        return PageRequest.of(Math.max(page, 0), tamanhoAplicado, Sort.by(ordenacao).and(Sort.by("id")));
    }

    /** A ordem vem da própria consulta (`ORDER BY` com `JOIN FETCH`). */
    private static Pageable paginaOrdenadaNaConsulta(int page, int size) {
        return PageRequest.of(Math.max(page, 0), Math.max(1, Math.min(size, TAMANHO_PAGINA_MAXIMO)));
    }

    private static <T> PageResponse<T> paginar(Page<T> resultado) {
        return new PageResponse<>(resultado.getContent(), resultado.getNumber(), resultado.getSize(),
                resultado.getTotalElements(), resultado.getTotalPages());
    }
}
