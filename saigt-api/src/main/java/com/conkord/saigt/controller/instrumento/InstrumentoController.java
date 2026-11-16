package com.conkord.saigt.controller.instrumento;

import com.conkord.saigt.dto.core.PageResponse;
import com.conkord.saigt.dto.instrumento.DinamicaAssociavelResponse;
import com.conkord.saigt.dto.instrumento.AnularProcessoRequest;
import com.conkord.saigt.dto.instrumento.AtualizarDadosFormularioRequest;
import com.conkord.saigt.dto.instrumento.CriarDinamicaRequest;
import com.conkord.saigt.dto.instrumento.CriarInstrumentoRequest;
import com.conkord.saigt.dto.instrumento.CriarProcessoRequest;
import com.conkord.saigt.dto.instrumento.DesbloquearCampoRequest;
import com.conkord.saigt.dto.instrumento.DinamicaResponse;
import com.conkord.saigt.dto.instrumento.DevolverDgtRequest;
import com.conkord.saigt.dto.instrumento.EnviarPcmRequest;
import com.conkord.saigt.dto.instrumento.EstatisticasProcessoResponse;
import com.conkord.saigt.dto.instrumento.HistoricoProcessoResponse;
import com.conkord.saigt.dto.instrumento.IgtSnitResponse;
import com.conkord.saigt.dto.instrumento.InstrumentoDisponibilidadeResponse;
import com.conkord.saigt.dto.instrumento.InstrumentoPainelResponse;
import com.conkord.saigt.dto.instrumento.InstrumentoResponse;
import com.conkord.saigt.dto.instrumento.PainelProcessosResponse;
import com.conkord.saigt.dto.instrumento.ProcessoDetalheResponse;
import com.conkord.saigt.dto.instrumento.ProcessoFiltro;
import com.conkord.saigt.dto.instrumento.ProcessoListagemResponse;
import com.conkord.saigt.dto.instrumento.ProcessoResponse;
import com.conkord.saigt.dto.instrumento.ReatribuirAutorRequest;
import com.conkord.saigt.dto.instrumento.RegistoExternoProcessoRequest;
import com.conkord.saigt.dto.instrumento.RegistoExternoProcessoResponse;
import com.conkord.saigt.dto.instrumento.RejeitarProcessoRequest;
import com.conkord.saigt.dto.instrumento.SubmeterProcessoRequest;
import com.conkord.saigt.dto.instrumento.TerritorioInstrumentoResponse.ItemTerritorio;
import com.conkord.saigt.dto.instrumento.TipoInstrumentoResponse;
import com.conkord.saigt.dto.instrumento.ValidarProcessoRequest;
import com.conkord.saigt.dto.instrumento.VersaoProcessoRequest;
import com.conkord.saigt.exception.ExportacaoFormatoInvalidoException;
import com.conkord.saigt.exception.ValidacaoException;
import com.conkord.saigt.persistence.operacional.Dinamica;
import com.conkord.saigt.persistence.operacional.Instrumento;
import com.conkord.saigt.persistence.operacional.Processo;
import com.conkord.saigt.persistence.operacional.SituacaoPcm;
import com.conkord.saigt.service.instrumento.DinamicaService;
import com.conkord.saigt.service.instrumento.GrupoProcesso;
import com.conkord.saigt.service.instrumento.InstrumentoDisponibilidadeService;
import com.conkord.saigt.service.instrumento.InstrumentoPainelService;
import com.conkord.saigt.service.instrumento.InstrumentoService;
import com.conkord.saigt.service.instrumento.InstrumentoSnitService;
import com.conkord.saigt.service.instrumento.ProcessoConsultaService;
import com.conkord.saigt.service.instrumento.ProcessoService;
import com.conkord.saigt.service.relatorio.ExportacaoProcessosService;
import com.conkord.saigt.service.relatorio.FormatoExportacao;
import com.conkord.saigt.service.snit.ReferenciaSnitConsultaService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

/** Visibilidade e ator decididos sempre em `InstrumentoService`/`AutorizacaoService`, nunca aqui. */
@RestController
public class InstrumentoController {

    private static final int TAMANHO_PAGINA_DEFEITO = 20;
    private static final int TAMANHO_PAGINA_MAXIMO = 100;
    private static final Pattern PADRAO_DTCC = Pattern.compile("\\d{4}");

    private final InstrumentoService instrumentoService;
    private final InstrumentoSnitService instrumentoSnitService;
    private final InstrumentoPainelService instrumentoPainelService;
    private final InstrumentoDisponibilidadeService instrumentoDisponibilidadeService;
    private final DinamicaService dinamicaService;
    private final ProcessoService processoService;
    private final ProcessoConsultaService processoConsultaService;
    private final ExportacaoProcessosService exportacaoProcessosService;
    private final ReferenciaSnitConsultaService referenciaSnitConsultaService;

    public InstrumentoController(InstrumentoService instrumentoService, InstrumentoSnitService instrumentoSnitService,
                                  InstrumentoPainelService instrumentoPainelService,
                                  InstrumentoDisponibilidadeService instrumentoDisponibilidadeService,
                                  DinamicaService dinamicaService, ProcessoService processoService,
                                  ProcessoConsultaService processoConsultaService,
                                  ExportacaoProcessosService exportacaoProcessosService,
                                  ReferenciaSnitConsultaService referenciaSnitConsultaService) {
        this.instrumentoService = instrumentoService;
        this.instrumentoSnitService = instrumentoSnitService;
        this.instrumentoPainelService = instrumentoPainelService;
        this.instrumentoDisponibilidadeService = instrumentoDisponibilidadeService;
        this.dinamicaService = dinamicaService;
        this.processoService = processoService;
        this.processoConsultaService = processoConsultaService;
        this.exportacaoProcessosService = exportacaoProcessosService;
        this.referenciaSnitConsultaService = referenciaSnitConsultaService;
    }

    @PostMapping("/api/v1/instrumentos")
    public ResponseEntity<InstrumentoResponse> criar(@Valid @RequestBody CriarInstrumentoRequest request) {
        Instrumento instrumento = instrumentoService.criar(request);
        List<String> concelhos = instrumentoService.concelhos(instrumento.getId());
        return ResponseEntity.status(201).body(InstrumentoResponse.de(instrumento, concelhos,
                referenciaSnitConsultaService.nomesConcelhosPorOrdem(concelhos)));
    }

    @GetMapping("/api/v1/instrumentos/{id}")
    public InstrumentoPainelResponse obter(@PathVariable UUID id) {
        return instrumentoPainelService.painel(id);
    }

    /**
     * Separado de `GET /api/v1/catalogos`, que só devolve nomes e é restrito a MASTER:
     * quem cria um Instrumento precisa do identificador e nem sempre é DGT.
     */
    @GetMapping("/api/v1/tipos-instrumento")
    public List<TipoInstrumentoResponse> listarTiposInstrumento() {
        return instrumentoService.listarTipos().stream().map(TipoInstrumentoResponse::de).toList();
    }

    /** Concelhos que se podem indicar ao criar um Instrumento: a mesma lista da Elaboração. */
    @GetMapping("/api/v1/instrumentos/concelhos")
    public List<ItemTerritorio> listarConcelhos() {
        return instrumentoService.concelhosDisponiveis();
    }

    /** O frontend só renderiza o que esta resposta trouxer; sem cache HTTP. */
    @GetMapping("/api/v1/instrumentos/{id}/disponivel")
    public InstrumentoDisponibilidadeResponse disponivel(@PathVariable UUID id) {
        return instrumentoDisponibilidadeService.disponivel(id);
    }

    /**
     * Só aceita os tipos listados em `dinamicasDisponiveis` de {@code GET .../disponivel},
     * verificados pelo mesmo motor de regras.
     */
    @PostMapping("/api/v1/instrumentos/{id}/dinamicas")
    public ResponseEntity<DinamicaResponse> iniciarDinamica(@PathVariable UUID id, @Valid @RequestBody CriarDinamicaRequest request) {
        Dinamica dinamica = dinamicaService.iniciar(id, request);
        return ResponseEntity.status(201).body(DinamicaResponse.de(dinamica));
    }

    /** `processo` é sempre a tentativa, nunca o Instrumento. Autorização toda via `AutorizacaoService`. */
    @PostMapping("/api/v1/dinamicas/{id}/processos")
    public ResponseEntity<ProcessoResponse> criarProcesso(@PathVariable UUID id, @Valid @RequestBody CriarProcessoRequest request) {
        Processo processo = processoService.criar(id, request);
        return ResponseEntity.status(201).body(ProcessoResponse.de(processo));
    }

    /** `MASTER`; nasce já em `PUBLICADO`, sem sequenciamento nem motor de regras: regista um facto já ocorrido. */
    @PostMapping("/api/v1/dinamicas/{id}/processos/registo-externo")
    public ResponseEntity<RegistoExternoProcessoResponse> registoExterno(@PathVariable UUID id, @Valid @RequestBody RegistoExternoProcessoRequest request) {
        return ResponseEntity.status(201).body(RegistoExternoProcessoResponse.de(processoService.registoExterno(id, request)));
    }

    /** Só por ação explícita do autor, nunca por temporizador. Sem validações de submissão; não transiciona estado. */
    @PutMapping("/api/v1/processos/{id}/dados-formulario")
    public ProcessoResponse atualizarDadosFormulario(@PathVariable UUID id, @Valid @RequestBody AtualizarDadosFormularioRequest request) {
        return ProcessoResponse.de(processoService.atualizarDadosFormulario(id, request));
    }

    /** De `EM_SUBMISSAO` para `ENVIADO_DGT`. */
    @PatchMapping("/api/v1/processos/{id}/submeter")
    public ProcessoResponse submeter(@PathVariable UUID id, @Valid @RequestBody SubmeterProcessoRequest request) {
        return ProcessoResponse.de(processoService.submeter(id, request));
    }

    /** De `REJEITADO_DGT` ou `ANULADO_INCM` para `EM_SUBMISSAO`, no mesmo processo. */
    @PatchMapping("/api/v1/processos/{id}/corrigir")
    public ProcessoResponse corrigir(@PathVariable UUID id, @Valid @RequestBody VersaoProcessoRequest request) {
        return ProcessoResponse.de(processoService.corrigir(id, request));
    }

    /** O autor, num processo em correção; não muda o estado. */
    @PatchMapping("/api/v1/processos/{id}/desbloquear-campo")
    public ProcessoResponse desbloquearCampo(@PathVariable UUID id, @Valid @RequestBody DesbloquearCampoRequest request) {
        return ProcessoResponse.de(processoService.desbloquearCampo(id, request));
    }

    /** Só antes de a DGT capturar; a corrida resolve-se por optimistic locking. */
    @PatchMapping("/api/v1/processos/{id}/resgatar")
    public ProcessoResponse resgatar(@PathVariable UUID id, @Valid @RequestBody VersaoProcessoRequest request) {
        return ProcessoResponse.de(processoService.resgatar(id, request));
    }

    /** Ator `DGT`: `MASTER` ou `TECNICO`. */
    @PatchMapping("/api/v1/processos/{id}/capturar")
    public ProcessoResponse capturar(@PathVariable UUID id, @Valid @RequestBody VersaoProcessoRequest request) {
        return ProcessoResponse.de(processoService.capturar(id, request));
    }

    /** Ator `DGT`: `MASTER` ou `TECNICO`; grava os campos opcionais do Resumo Validados. */
    @PatchMapping("/api/v1/processos/{id}/validar")
    public ProcessoResponse validar(@PathVariable UUID id, @Valid @RequestBody ValidarProcessoRequest request) {
        return ProcessoResponse.de(processoService.validar(id, request));
    }

    /** `MASTER`; a entidade corrige na mesma linha de `processo`. */
    @PatchMapping("/api/v1/processos/{id}/rejeitar")
    public ProcessoResponse rejeitar(@PathVariable UUID id, @Valid @RequestBody RejeitarProcessoRequest request) {
        return ProcessoResponse.de(processoService.rejeitar(id, request));
    }

    /** `MASTER`; sempre manual. */
    @PatchMapping("/api/v1/processos/{id}/anular")
    public ProcessoResponse anular(@PathVariable UUID id, @Valid @RequestBody AnularProcessoRequest request) {
        return ProcessoResponse.de(processoService.anular(id, request));
    }

    /** `MASTER`, só em Em Submissão, Enviado DGT, Rejeitado DGT e Anulado INCM; resolve o `pedidoApoioId` indicado. */
    @PatchMapping("/api/v1/processos/{id}/reatribuir-autor")
    public ProcessoResponse reatribuirAutor(@PathVariable UUID id, @Valid @RequestBody ReatribuirAutorRequest request) {
        return ProcessoResponse.de(processoService.reatribuirAutor(id, request));
    }

    /** Ator DGT (`MASTER` ou `TECNICO`), só 1.ª série, para um utilizador da PCM; não transiciona estado. */
    @PatchMapping("/api/v1/processos/{id}/enviar-pcm")
    public ProcessoResponse enviarPcm(@PathVariable UUID id, @Valid @RequestBody EnviarPcmRequest request) {
        return ProcessoResponse.de(processoService.enviarPcm(id, request));
    }

    /** Ator PCM, no processo que lhe foi atribuído; não transiciona estado e limpa a atribuição. */
    @PatchMapping("/api/v1/processos/{id}/devolver-dgt")
    public ProcessoResponse devolverDgt(@PathVariable UUID id, @Valid @RequestBody DevolverDgtRequest request) {
        return ProcessoResponse.de(processoService.devolverDgt(id, request));
    }

    /** `VALIDADO_DGT` → `EM_VALIDACAO_INCM` ou `ANULADO_INCM`, só pelo ator DGT e sem o processo com a PCM. */
    @PatchMapping("/api/v1/processos/{id}/enviar-incm")
    public ProcessoResponse enviarIncm(@PathVariable UUID id, @Valid @RequestBody VersaoProcessoRequest request) {
        return ProcessoResponse.de(processoService.enviarIncm(id, request));
    }

    /**
     * Só Camadas 1+2, sem ator: `VISITANTE` usa-o, ao contrário das ações
     * de {@code ProcessoService}.
     */
    @GetMapping("/api/v1/processos")
    public PageResponse<ProcessoListagemResponse> listarProcessos(
            @RequestParam(required = false) String designacaoInstrumento,
            @RequestParam(required = false) String numeroInstrumento,
            @RequestParam(required = false) List<String> estado,
            @RequestParam(required = false) String tipoInstrumentoId,
            @RequestParam(required = false) String entidadeId,
            @RequestParam(required = false) String numeroProcesso,
            @RequestParam(required = false) String ano,
            @RequestParam(required = false) String grupo,
            @RequestParam(required = false) String dataDe,
            @RequestParam(required = false) String dataAte,
            @RequestParam(required = false) String numeroDr,
            @RequestParam(required = false) String incmActIdentifier,
            @RequestParam(required = false) String concelho,
            @RequestParam(required = false) String nutsII,
            @RequestParam(required = false) List<String> motivoAlteracao,
            @RequestParam(required = false) List<String> situacaoPcm,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "" + TAMANHO_PAGINA_DEFEITO) int size) {
        LeitorFiltroProcessos.Leitura leitura = LeitorFiltroProcessos.ler(new LeitorFiltroProcessos.Parametros(
                designacaoInstrumento, numeroInstrumento, estado, tipoInstrumentoId, entidadeId, numeroProcesso, ano,
                grupo, dataDe, dataAte, numeroDr, incmActIdentifier, concelho, nutsII, motivoAlteracao, situacaoPcm));
        if (!leitura.erros().isEmpty()) {
            throw new ValidacaoException("Parâmetros de filtro inválidos.", leitura.erros());
        }
        ProcessoFiltro filtro = leitura.filtro();
        Page<ProcessoListagemResponse> resultado = processoConsultaService.listar(filtro,
                paginaValida(page, size, ordenacaoDaListagem(filtro.grupo())));

        return new PageResponse<>(resultado.getContent(), resultado.getNumber(), resultado.getSize(),
                resultado.getTotalElements(), resultado.getTotalPages());
    }

    /**
     * O ficheiro tem as linhas que {@link #listarProcessos} daria para os mesmos filtros, somando as páginas, pela
     * mesma ordem e com a mesma visibilidade. Ao contrário da listagem, recusa parâmetros que não conhece (também
     * `page` e `size`), para um filtro mal escrito não exportar em silêncio o conjunto inteiro.
     */
    @GetMapping("/api/v1/processos/exportacao")
    public ResponseEntity<byte[]> exportarProcessos(
            @RequestParam(required = false) String formato,
            @RequestParam(required = false) String designacaoInstrumento,
            @RequestParam(required = false) String numeroInstrumento,
            @RequestParam(required = false) List<String> estado,
            @RequestParam(required = false) String tipoInstrumentoId,
            @RequestParam(required = false) String entidadeId,
            @RequestParam(required = false) String numeroProcesso,
            @RequestParam(required = false) String ano,
            @RequestParam(required = false) String grupo,
            @RequestParam(required = false) String dataDe,
            @RequestParam(required = false) String dataAte,
            @RequestParam(required = false) String numeroDr,
            @RequestParam(required = false) String incmActIdentifier,
            @RequestParam(required = false) String concelho,
            @RequestParam(required = false) String nutsII,
            @RequestParam(required = false) List<String> motivoAlteracao,
            @RequestParam(required = false) List<String> situacaoPcm,
            @RequestParam MultiValueMap<String, String> parametros) {
        FormatoExportacao formatoExportacao = FormatoExportacao.de(formato)
                .orElseThrow(ExportacaoFormatoInvalidoException::new);
        parametros.keySet().stream()
                .filter(nome -> !nome.equals("formato") && !LeitorFiltroProcessos.NOMES.contains(nome))
                .sorted()
                .findFirst()
                .ifPresent(nome -> {
                    throw new ValidacaoException("Filtro não suportado nesta listagem: " + nome + ".",
                            List.of(new ValidacaoException.CampoErro(nome, "Filtro não suportado.")));
                });
        LeitorFiltroProcessos.Leitura leitura = LeitorFiltroProcessos.ler(new LeitorFiltroProcessos.Parametros(
                designacaoInstrumento, numeroInstrumento, estado, tipoInstrumentoId, entidadeId, numeroProcesso, ano,
                grupo, dataDe, dataAte, numeroDr, incmActIdentifier, concelho, nutsII, motivoAlteracao, situacaoPcm));
        if (!leitura.erros().isEmpty()) {
            throw new ValidacaoException("Valor inválido para o filtro " + leitura.erros().get(0).path() + ".",
                    leitura.erros());
        }
        ExportacaoProcessosService.Ficheiro ficheiro = exportacaoProcessosService.exportar(leitura.filtro(),
                ordenacaoDaListagem(leitura.filtro().grupo()), formatoExportacao);

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(ficheiro.formato().tipoConteudo()))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(ficheiro.nome()).build().toString())
                .cacheControl(CacheControl.noStore())
                .body(ficheiro.conteudo());
    }

    /**
     * Painel Principal (API-PROC-031): contagem e página de cada grupo de estados, no mesmo âmbito de
     * {@link #listarProcessos}. Sem `grupo` devolve os grupos do tipo de utilizador; com `grupo`, só esse.
     * A ordem é sempre por n.º de processo descendente, `PARA_CORRECAO` incluído.
     */
    @GetMapping("/api/v1/processos/painel")
    public PainelProcessosResponse painelProcessos(
            @RequestParam(required = false) String grupo,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "" + TAMANHO_PAGINA_DEFEITO) int size) {
        List<ValidacaoException.CampoErro> erros = new ArrayList<>();
        GrupoProcesso grupoProcesso = LeitorFiltroProcessos.lerGrupo(grupo, erros);
        if (!erros.isEmpty()) {
            throw new ValidacaoException("Parâmetros de filtro inválidos.", erros);
        }
        return processoConsultaService.painel(grupoProcesso,
                paginaValida(page, size, Sort.by("numeroProcesso").descending().and(Sort.by("id").ascending())));
    }

    /**
     * Torna uma tentativa alcançável por URL próprio:
     * {@code GET /instrumentos/{id}/disponivel} não lista as tentativas já criadas.
     */
    @GetMapping("/api/v1/processos/{id}")
    public ProcessoDetalheResponse obterProcesso(@PathVariable UUID id) {
        return processoConsultaService.detalhe(id);
    }

    /** Mais recente primeiro. */
    @GetMapping("/api/v1/processos/{id}/historico")
    public PageResponse<HistoricoProcessoResponse> historicoProcesso(@PathVariable UUID id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "" + TAMANHO_PAGINA_DEFEITO) int size) {
        Page<HistoricoProcessoResponse> resultado = processoConsultaService.historico(id, paginaValida(page, size, Sort.unsorted()));

        return new PageResponse<>(resultado.getContent(), resultado.getNumber(), resultado.getSize(),
                resultado.getTotalElements(), resultado.getTotalPages());
    }

    /** Dinâmicas de IGT que o autor pode associar ao processo (BL-PROC-062), da mais recente para a mais antiga. */
    @GetMapping("/api/v1/processos/{id}/dinamicas-associaveis")
    public PageResponse<DinamicaAssociavelResponse> dinamicasAssociaveis(@PathVariable UUID id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "" + TAMANHO_PAGINA_DEFEITO) int size) {
        Page<DinamicaAssociavelResponse> resultado = processoConsultaService.dinamicasAssociaveis(id,
                paginaValida(page, size, Sort.unsorted()));
        return new PageResponse<>(resultado.getContent(), resultado.getNumber(), resultado.getSize(),
                resultado.getTotalElements(), resultado.getTotalPages());
    }

    @GetMapping("/api/v1/processos/{id}/estatisticas")
    public EstatisticasProcessoResponse estatisticasProcesso(@PathVariable UUID id,
            @RequestParam(required = false) Integer submissao,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataDe,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataAte) {
        if (submissao != null && submissao < 1) {
            throw new ValidacaoException("Parâmetros inválidos.",
                    List.of(new ValidacaoException.CampoErro("submissao", "Tem de ser 1 ou mais.")));
        }
        return processoConsultaService.estatisticas(id, submissao, dataDe, dataAte);
    }

    /**
     * Pesquisa dos IGT em vigor no SNIT (`municipio` é o código DTCC), para escolher o IGT existente em «Criar
     * Processo». Não partilha o caminho do LLD (`GET /api/v1/instrumentos`) porque esse já é a listagem local.
     */
    @GetMapping("/api/v1/instrumentos/snit")
    public PageResponse<IgtSnitResponse> pesquisarSnit(
            @RequestParam(required = false) Long tipoInstrumentoId,
            @RequestParam(required = false) String municipio,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "" + TAMANHO_PAGINA_DEFEITO) int size) {
        if (tipoInstrumentoId == null) {
            throw new ValidacaoException("Parâmetros de pesquisa inválidos.",
                    List.of(new ValidacaoException.CampoErro("tipoInstrumentoId", "Obrigatório.")));
        }
        PageRequest pagina = paginaValida(page, size, Sort.unsorted());
        return instrumentoSnitService.pesquisar(tipoInstrumentoId, municipio, pagina.getPageNumber(), pagina.getPageSize());
    }

    /**
     * `size` capado a {@value #TAMANHO_PAGINA_MAXIMO}, nunca rejeitado. Os filtros são opcionais e combináveis; sem
     * nenhum, a resposta é a de sempre. `concelho` é o código DTCC (4 algarismos); `tipoInstrumentoId` pode repetir-se.
     */
    @GetMapping("/api/v1/instrumentos")
    public PageResponse<InstrumentoResponse> listar(
            @RequestParam(required = false) List<Long> tipoInstrumentoId,
            @RequestParam(required = false) String concelho,
            @RequestParam(required = false) Boolean emVigor,
            @RequestParam(required = false) String designacao,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "" + TAMANHO_PAGINA_DEFEITO) int size) {
        if (concelho != null && !PADRAO_DTCC.matcher(concelho).matches()) {
            throw new ValidacaoException("Parâmetros de filtro inválidos.",
                    List.of(new ValidacaoException.CampoErro("concelho", "Tem de ter 4 algarismos (código DTCC).")));
        }
        int tamanhoAplicado = Math.min(size, TAMANHO_PAGINA_MAXIMO);
        String designacaoAplicada = designacao == null || designacao.isBlank() ? null : designacao.strip();
        Page<Instrumento> resultado = instrumentoService.listar(
                new InstrumentoService.FiltroInstrumentos(tipoInstrumentoId, concelho, emVigor, designacaoAplicada),
                PageRequest.of(page, tamanhoAplicado, Sort.by("criadoEm").descending()));
        Map<UUID, List<String>> concelhos = instrumentoService.concelhos(
                resultado.getContent().stream().map(Instrumento::getId).toList());
        Map<String, String> nomesConcelhos = referenciaSnitConsultaService.nomesConcelhos(
                concelhos.values().stream().flatMap(List::stream).toList());

        return new PageResponse<>(
                resultado.getContent().stream()
                        .map(instrumento -> {
                            List<String> codigos = concelhos.getOrDefault(instrumento.getId(), List.of());
                            return InstrumentoResponse.de(instrumento, codigos,
                                    codigos.stream().map(nomesConcelhos::get).toList());
                        })
                        .toList(),
                resultado.getNumber(),
                resultado.getSize(),
                resultado.getTotalElements(),
                resultado.getTotalPages());
    }

    /** Ao contrário de {@code GET /instrumentos}, os endpoints de processos recusam o que a convenção da API não admite. */
    private static PageRequest paginaValida(int page, int size, Sort ordenacao) {
        List<ValidacaoException.CampoErro> erros = new ArrayList<>();
        if (page < 0) {
            erros.add(new ValidacaoException.CampoErro("page", "Não pode ser negativo."));
        }
        if (size < 1 || size > TAMANHO_PAGINA_MAXIMO) {
            erros.add(new ValidacaoException.CampoErro("size", "Tem de estar entre 1 e " + TAMANHO_PAGINA_MAXIMO + "."));
        }
        if (!erros.isEmpty()) {
            throw new ValidacaoException("Parâmetros de paginação inválidos.", erros);
        }
        return PageRequest.of(page, size, ordenacao);
    }

    /** `numero_processo` é único, por isso o `id` só desempata por segurança. */
    private static Sort ordenacaoDaListagem(GrupoProcesso grupo) {
        Sort porNumero = grupo == GrupoProcesso.PARA_CORRECAO
                ? Sort.by("numeroProcesso").ascending()
                : Sort.by("numeroProcesso").descending();
        return porNumero.and(Sort.by("id").ascending());
    }
}
