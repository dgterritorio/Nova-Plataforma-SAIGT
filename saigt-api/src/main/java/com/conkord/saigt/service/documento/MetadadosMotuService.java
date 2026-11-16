package com.conkord.saigt.service.documento;

import com.conkord.saigt.dto.documento.ConjuntoMotuDados;
import com.conkord.saigt.dto.documento.DadosMotu;
import com.conkord.saigt.dto.documento.MetadadosMotuResponse;
import com.conkord.saigt.dto.documento.MetadadosMotuResponse.FolhaConjunto;
import com.conkord.saigt.dto.documento.MetadadosMotuResponse.Registo;
import com.conkord.saigt.dto.documento.MetadadosMotuResponse.ZipConjunto;
import com.conkord.saigt.dto.documento.MetadadosResponse.Responsavel;
import com.conkord.saigt.dto.documento.SerieMotuDados;
import com.conkord.saigt.dto.documento.TipoCartografiaMotu;
import com.conkord.saigt.exception.MotuCamposInvalidosException;
import com.conkord.saigt.exception.MotuNaoAplicavelException;
import com.conkord.saigt.exception.MotuNaoRemovivelException;
import com.conkord.saigt.exception.MotuPecaComFichaException;
import com.conkord.saigt.exception.RecursoNaoEncontradoException;
import com.conkord.saigt.exception.ValidacaoException.CampoErro;
import com.conkord.saigt.persistence.configuracao.CategoriaDocumento;
import com.conkord.saigt.persistence.configuracao.DocumentoConfig;
import com.conkord.saigt.persistence.configuracao.DocumentoConfigRepository;
import com.conkord.saigt.persistence.configuracao.InstrumentoProcedimentoConfig;
import com.conkord.saigt.persistence.configuracao.InstrumentoProcedimentoConfigRepository;
import com.conkord.saigt.persistence.configuracao.PaginaDocumento;
import com.conkord.saigt.persistence.configuracao.TipoElemento;
import com.conkord.saigt.persistence.operacional.MetadadosMotu;
import com.conkord.saigt.persistence.operacional.MetadadosMotuRepository;
import com.conkord.saigt.persistence.operacional.Planta;
import com.conkord.saigt.persistence.operacional.PlantaRepository;
import com.conkord.saigt.persistence.operacional.Processo;
import com.conkord.saigt.persistence.operacional.ProcessoDocumento;
import com.conkord.saigt.persistence.operacional.ProcessoDocumentoRepository;
import com.conkord.saigt.persistence.operacional.ProcessoRepository;
import com.conkord.saigt.persistence.operacional.TipoMetadadosMotu;
import com.conkord.saigt.service.nucleo.AutorizacaoService;
import com.conkord.saigt.service.nucleo.ContextoUtilizadorResolver;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Service
@Transactional(propagation = Propagation.MANDATORY)
public class MetadadosMotuService {

    private static final Logger log = LoggerFactory.getLogger(MetadadosMotuService.class);

    private static final String CAMPO_SERIE = "metadados.serie";
    private static final String CAMPO_CONJUNTO = "metadados.conjunto.";
    private static final String CAMPO_ZIP = "processoDocumentoId";
    private static final String TABELA_FOLHAS = "tabela Plantas SAIGT SNIT";
    private static final int MAXIMO_FALTAS_POR_EXTENSO = 3;

    private final MetadadosMotuRepository metadadosMotuRepository;
    private final PlantaRepository plantaRepository;
    private final InstrumentoProcedimentoConfigRepository instrumentoProcedimentoConfigRepository;
    private final DocumentoConfigRepository documentoConfigRepository;
    private final ProcessoRepository processoRepository;
    private final ProcessoDocumentoRepository processoDocumentoRepository;
    private final AutorizacaoService autorizacaoService;
    private final ContextoUtilizadorResolver contextoUtilizadorResolver;
    private final ProcessoAlteravel processoAlteravel;
    private final ObjectMapper objectMapper;
    private final Validator validator;

    public MetadadosMotuService(MetadadosMotuRepository metadadosMotuRepository, PlantaRepository plantaRepository,
                                InstrumentoProcedimentoConfigRepository instrumentoProcedimentoConfigRepository,
                                DocumentoConfigRepository documentoConfigRepository, ProcessoRepository processoRepository,
                                ProcessoDocumentoRepository processoDocumentoRepository, AutorizacaoService autorizacaoService, ContextoUtilizadorResolver contextoUtilizadorResolver,
                                ProcessoAlteravel processoAlteravel, ObjectMapper objectMapper, Validator validator) {
        this.processoRepository = processoRepository;
        this.processoDocumentoRepository = processoDocumentoRepository;
        this.autorizacaoService = autorizacaoService;
        this.contextoUtilizadorResolver = contextoUtilizadorResolver;
        this.processoAlteravel = processoAlteravel;
        this.objectMapper = objectMapper;
        this.validator = validator;
        this.metadadosMotuRepository = metadadosMotuRepository;
        this.plantaRepository = plantaRepository;
        this.instrumentoProcedimentoConfigRepository = instrumentoProcedimentoConfigRepository;
        this.documentoConfigRepository = documentoConfigRepository;
    }

    public boolean aplicaFichaMotu(Processo processo) {
        InstrumentoProcedimentoConfig procedimento = processo.getInstrumentoProcedimentoConfig();
        return procedimentoFinal(procedimento) && documentoConfigRepository
                .findByInstrumentoProcedimentoConfigAndAtivoTrueOrderByOrdem(procedimento).stream()
                .anyMatch(MetadadosMotuService::pecaGraficaDePublicacao);
    }

    public boolean aplicaFichaMotu(ProcessoDocumento documento) {
        return pecaGraficaDePublicacao(documento.getDocumentoConfig()) && aplicaFichaMotu(documento.getProcesso());
    }

    /**
     * O que falta para submeter, onde a ficha MOTU se aplica: a Série e cada Conjunto não opcional têm de estar
     * `completo`. As fichas opcionais ficam fora, completas ou não (BL-DOC-003, passo 3, e BL-DOC-008, passo 5, do
     * LLD v30; OI-461): não diverge do LLD. Uma pendência por registo, com o campo `metadados.serie` ou
     * `metadados.conjunto.{id}`.
     */
    @Transactional(readOnly = true)
    public List<CampoErro> pendencias(Processo processo) {
        if (!aplicaFichaMotu(processo)) {
            return List.of();
        }
        List<MetadadosMotu> registos = metadadosMotuRepository.findByProcesso_Id(processo.getId());
        List<CampoErro> pendencias = new ArrayList<>();
        boolean serieCompleta = registos.stream()
                .anyMatch(ficha -> ficha.getTipo() == TipoMetadadosMotu.SERIE && ficha.isCompleto());
        if (!serieCompleta) {
            pendencias.add(new CampoErro(CAMPO_SERIE, "Preencha a Série de Dados Geográficos."));
        }
        List<MetadadosMotu> incompletos = registos.stream()
                .filter(MetadadosMotuService::conjuntoDeDocumento)
                .filter(conjunto -> !conjunto.isCompleto())
                .sorted(Comparator.comparing(MetadadosMotuService::ordemDoItem)
                        .thenComparing(MetadadosMotu::getCriadoEm))
                .toList();
        if (incompletos.isEmpty()) {
            return pendencias;
        }
        Map<UUID, List<Planta>> folhas = folhasPorConjunto(processo.getId());
        incompletos.forEach(conjunto -> pendencias.add(new CampoErro(CAMPO_CONJUNTO + conjunto.getId(),
                mensagemConjuntoIncompleto(conjunto, folhas.getOrDefault(conjunto.getId(), List.of())))));
        return pendencias;
    }

    /**
     * Diz qual é a ficha (pelo nome do item) e o que falta, com os rótulos do ecrã. Até três faltas por extenso, para a
     * mensagem caber no Resumo; sem faltas que se vejam (completo desatualizado), a mensagem genérica.
     */
    private String mensagemConjuntoIncompleto(MetadadosMotu conjunto, List<Planta> folhas) {
        String nome = nomeDoItem(conjunto.getProcessoDocumento());
        List<String> emFalta = new ArrayList<>(deJsonConjunto(conjunto.getDados()).camposEmFalta().stream()
                .map(rotulo -> "«" + rotulo + "»").toList());
        if (folhas.isEmpty()) {
            emFalta.add("uma folha na " + TABELA_FOLHAS);
        } else {
            if (folhas.stream().anyMatch(folha -> semTexto(folha.getNomeRegulamento()))) {
                emFalta.add("o Nome Regulamento na " + TABELA_FOLHAS);
            }
            if (folhas.stream().anyMatch(folha -> semTexto(folha.getNumeroFolha()))) {
                emFalta.add("a Folha N na " + TABELA_FOLHAS);
            }
        }
        if (emFalta.isEmpty()) {
            return "Preencha o Conjunto de Dados Geográficos da " + nome + ".";
        }
        if (emFalta.size() == 1) {
            return nome + ": falta " + emFalta.get(0) + ".";
        }
        if (emFalta.size() <= MAXIMO_FALTAS_POR_EXTENSO) {
            return nome + ": faltam " + String.join(", ", emFalta.subList(0, emFalta.size() - 1)) + " e "
                    + emFalta.get(emFalta.size() - 1) + ".";
        }
        return nome + ": faltam " + String.join(", ", emFalta.subList(0, MAXIMO_FALTAS_POR_EXTENSO)) + " e mais "
                + (emFalta.size() - MAXIMO_FALTAS_POR_EXTENSO) + " campos.";
    }

    private static boolean semTexto(String texto) {
        return texto == null || texto.isBlank();
    }

    @Transactional(readOnly = true)
    public MetadadosMotuResponse obter(UUID processoId) {
        Processo processo = processoRepository.findById(processoId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Processo não encontrado."));
        autorizacaoService.verificarVisibilidade(contextoUtilizadorResolver.resolver(), processo);
        Responsavel responsavel = MetadadosService.responsavel(processo);
        if (!aplicaFichaMotu(processo)) {
            return new MetadadosMotuResponse(false, responsavel, List.of());
        }
        String titulo = processo.getDinamica().designacao();
        Map<UUID, List<Planta>> folhas = folhasPorConjunto(processoId);
        List<Registo> registos = metadadosMotuRepository.findByProcesso_Id(processoId).stream()
                .filter(registo -> !conjuntoJaNaoPedido(registo))
                .sorted(Comparator.comparing(MetadadosMotu::getTipo)
                        .thenComparing(MetadadosMotuService::ordemDoItem)
                        .thenComparing(MetadadosMotu::getCriadoEm))
                .map(registo -> registo(registo, titulo, folhas.getOrDefault(registo.getId(), List.of())))
                .toList();
        return new MetadadosMotuResponse(true, responsavel, registos);
    }

    /** O corpo depende do tipo do registo: a Série tem só os campos; o Conjunto traz também as suas folhas. */
    @Transactional
    public Registo gravar(UUID processoId, UUID motuId, JsonNode corpo) {
        Processo processo = processoAlteravel.obter(processoId, contextoUtilizadorResolver.resolver());
        if (!aplicaFichaMotu(processo)) {
            throw new MotuNaoAplicavelException("A ficha MOTU não se aplica a este Procedimento.");
        }
        MetadadosMotu registo = metadadosMotuRepository.findById(motuId)
                .filter(ficha -> ficha.getProcesso().getId().equals(processoId))
                .orElseThrow(() -> new RecursoNaoEncontradoException("Ficha MOTU não encontrada neste processo."));
        String titulo = processo.getDinamica().designacao();
        if (registo.getTipo() == TipoMetadadosMotu.SERIE) {
            SerieMotuDados dados = ler(corpo, SerieMotuDados.class);
            registo.setDados(paraJson(dados));
            registo.setCompleto(dados.completa());
            metadadosMotuRepository.saveAndFlush(registo);
            log.info("Ficha MOTU de Série gravada — processoId={} metadadosMotuId={} completo={}", processoId, motuId,
                    registo.isCompleto());
            return registo(registo, titulo, List.of());
        }
        gravarConjunto(processo, registo, corpo);
        return registo(registo, titulo, folhasPorConjunto(processoId).getOrDefault(motuId, List.of()));
    }

    private void gravarConjunto(Processo processo, MetadadosMotu conjunto, JsonNode corpo) {
        UUID processoId = processo.getId();
        ConjuntoMotuDados lidos = ler(corpo, ConjuntoMotuDados.class);
        List<CampoErro> erros = new ArrayList<>(camposDeOutroTipo(lidos));
        List<FolhaGravada> gravadas = lerFolhas(corpo, erros);
        ProcessoDocumento zip = lerZip(processoId, conjunto, corpo, erros);
        if (!erros.isEmpty()) {
            throw new MotuCamposInvalidosException("Campos da ficha MOTU inválidos.", erros);
        }
        if (zip != null && !zip.getId().equals(idDe(conjunto.getProcessoDocumento())) && metadadosMotuRepository
                .existsByProcessoDocumento_IdAndTipoAndIdNot(zip.getId(), TipoMetadadosMotu.CDG, conjunto.getId())) {
            throw new MotuPecaComFichaException("Esta peça gráfica já tem ficha de Conjunto.");
        }
        if (conjunto.isOpcional() && corpo.has(CAMPO_ZIP)) {
            conjunto.setProcessoDocumento(zip);
        }
        ligarFolhas(processoId);
        Map<UUID, Planta> daFicha = new HashMap<>();
        folhasPorConjunto(processoId).getOrDefault(conjunto.getId(), List.of())
                .forEach(planta -> daFicha.put(planta.getId(), planta));
        for (int i = 0; i < gravadas.size(); i++) {
            if (!daFicha.containsKey(gravadas.get(i).id())) {
                erros.add(new CampoErro("plantas[" + i + "].id", "Esta folha não é deste Conjunto."));
            }
        }
        if (!erros.isEmpty()) {
            throw new MotuCamposInvalidosException("Campos da ficha MOTU inválidos.", erros);
        }
        gravadas.forEach(gravada -> {
            Planta planta = daFicha.get(gravada.id());
            planta.setNomeRegulamento(gravada.nomeRegulamento());
            planta.setNumeroFolha(gravada.numeroFolha());
        });
        plantaRepository.saveAll(daFicha.values());
        ConjuntoMotuDados dados = lidos.semOsCamposDeOutroTipo();
        conjunto.setDados(paraJson(dados));
        conjunto.setCompleto(conjuntoCompleto(dados, daFicha.values(), exigeFolhas(processo)));
        metadadosMotuRepository.saveAndFlush(conjunto);
        log.info("Ficha MOTU de Conjunto gravada — processoId={} metadadosMotuId={} completo={} folhas={}", processoId,
                conjunto.getId(), conjunto.isCompleto(), daFicha.size());
    }

    private static UUID idDe(ProcessoDocumento documento) {
        return documento == null ? null : documento.getId();
    }

    /**
     * O ZIP só se liga a uma ficha opcional. Campo ausente: fica como está; `null`: desliga; senão tem de ser uma peça
     * gráfica do processo.
     */
    private ProcessoDocumento lerZip(UUID processoId, MetadadosMotu conjunto, JsonNode corpo, List<CampoErro> erros) {
        if (!corpo.has(CAMPO_ZIP)) {
            return null;
        }
        JsonNode no = corpo.get(CAMPO_ZIP);
        if (!conjunto.isOpcional()) {
            if (!(no.isNull() ? conjunto.getProcessoDocumento() == null
                    : no.asText().equals(String.valueOf(idDe(conjunto.getProcessoDocumento()))))) {
                erros.add(new CampoErro(CAMPO_ZIP, "O ZIP de uma ficha não opcional não se altera."));
            }
            return null;
        }
        if (no.isNull()) {
            return null;
        }
        UUID id = null;
        try {
            id = no.isTextual() ? UUID.fromString(no.asText()) : null;
        } catch (IllegalArgumentException e) {
            id = null;
        }
        if (id == null) {
            erros.add(new CampoErro(CAMPO_ZIP, "Documento inválido."));
            return null;
        }
        ProcessoDocumento documento = processoDocumentoRepository.findByIdAndProcesso_Id(id, processoId).orElse(null);
        if (documento == null || documento.getDocumentoConfig() == null
                || documento.getDocumentoConfig().getTipoElemento() != TipoElemento.PECA_GRAFICA) {
            erros.add(new CampoErro(CAMPO_ZIP, "Tem de ser uma peça gráfica deste processo."));
            return null;
        }
        return documento;
    }

    /** A última ficha de Conjunto do processo, a que a nova reproduz quando se pede. */
    private ConjuntoMotuDados dadosDaUltimaFicha(UUID processoId) {
        return metadadosMotuRepository.findByProcesso_Id(processoId).stream()
                .filter(ficha -> ficha.getTipo() == TipoMetadadosMotu.CDG)
                .max(Comparator.comparing(MetadadosMotu::getCriadoEm))
                .map(ficha -> deJsonConjunto(ficha.getDados()).semOTituloAlternativo())
                .orElse(ConjuntoMotuDados.vazio());
    }

    /** Acrescenta uma ficha de Conjunto opcional, sem ZIP e incompleta; pode reproduzir os dados da última ficha. */
    @Transactional
    public Registo criarOpcional(UUID processoId, boolean reproduzirUltima) {
        Processo processo = processoAlteravel.obter(processoId, contextoUtilizadorResolver.resolver());
        if (!aplicaFichaMotu(processo)) {
            throw new MotuNaoAplicavelException("A ficha MOTU não se aplica a este Procedimento.");
        }
        ConjuntoMotuDados dados = reproduzirUltima ? dadosDaUltimaFicha(processoId) : ConjuntoMotuDados.vazio();
        MetadadosMotu ficha = metadadosMotuRepository.saveAndFlush(MetadadosMotu.conjuntoOpcional(processo,
                paraJson(dados)));
        log.info("Ficha MOTU de Conjunto opcional criada — processoId={} metadadosMotuId={} reproduzida={}", processoId,
                ficha.getId(), reproduzirUltima);
        return registo(ficha, processo.getDinamica().designacao(), List.of());
    }

    @Transactional
    public void removerOpcional(UUID processoId, UUID motuId) {
        processoAlteravel.obter(processoId, contextoUtilizadorResolver.resolver());
        MetadadosMotu ficha = metadadosMotuRepository.findById(motuId)
                .filter(registo -> registo.getProcesso().getId().equals(processoId))
                .orElseThrow(() -> new RecursoNaoEncontradoException("Ficha MOTU não encontrada neste processo."));
        if (!ficha.isOpcional()) {
            throw new MotuNaoRemovivelException("Só as fichas de Conjunto opcionais se removem.");
        }
        plantaRepository.findByMetadadosMotu_Id(motuId).forEach(planta -> planta.setMetadadosMotu(null));
        metadadosMotuRepository.delete(ficha);
        log.info("Ficha MOTU opcional removida — processoId={} metadadosMotuId={}", processoId, motuId);
    }

    private record FolhaGravada(UUID id, String nomeRegulamento, String numeroFolha) {
    }

    private static final int LIMITE_NOME_REGULAMENTO = 500;
    private static final int LIMITE_NUMERO_FOLHA = 20;

    /** Um campo que só existe com outro tipo de cartografia não se aceita com valor. */
    private static List<CampoErro> camposDeOutroTipo(ConjuntoMotuDados dados) {
        List<CampoErro> erros = new ArrayList<>();
        TipoCartografiaMotu tipo = dados.cartografiaTipo();
        if (dados.temCampoDoTipoOficial() && tipo != TipoCartografiaMotu.SERIE_CARTOGRAFICA_OFICIAL) {
            erros.add(new CampoErro("serieCartograficaOficial", "Só existe com a Série Cartográfica Oficial."));
        }
        if (tipo != TipoCartografiaMotu.CARTOGRAFIA_HOMOLOGADA) {
            if (dados.numeroHomologacao() != null && !dados.numeroHomologacao().isBlank()) {
                erros.add(new CampoErro("numeroHomologacao", "Só existe com a Cartografia Homologada."));
            }
            if (dados.dataHomologacao() != null) {
                erros.add(new CampoErro("dataHomologacao", "Só existe com a Cartografia Homologada."));
            }
            if (dados.entidadeHomologacao() != null && !dados.entidadeHomologacao().isBlank()) {
                erros.add(new CampoErro("entidadeHomologacao", "Só existe com a Cartografia Homologada."));
            }
        }
        return erros;
    }

    private List<FolhaGravada> lerFolhas(JsonNode corpo, List<CampoErro> erros) {
        JsonNode plantas = corpo.get("plantas");
        List<FolhaGravada> folhas = new ArrayList<>();
        if (plantas == null || plantas.isNull()) {
            return folhas;
        }
        if (!plantas.isArray()) {
            erros.add(new CampoErro("plantas", "Tem de ser uma lista."));
            return folhas;
        }
        Set<UUID> vistos = new HashSet<>();
        for (int i = 0; i < plantas.size(); i++) {
            JsonNode no = plantas.get(i);
            UUID id = idDaFolha(no);
            if (id == null) {
                erros.add(new CampoErro("plantas[" + i + "].id", "Folha em falta ou inválida."));
                continue;
            }
            if (!vistos.add(id)) {
                erros.add(new CampoErro("plantas[" + i + "].id", "Folha repetida."));
                continue;
            }
            String nome = textoOuNulo(no, "nomeRegulamento");
            String folha = textoOuNulo(no, "numeroFolha");
            if (nome != null && nome.length() > LIMITE_NOME_REGULAMENTO) {
                erros.add(new CampoErro("plantas[" + i + "].nomeRegulamento",
                        "Excede " + LIMITE_NOME_REGULAMENTO + " caracteres."));
            }
            if (folha != null && folha.length() > LIMITE_NUMERO_FOLHA) {
                erros.add(new CampoErro("plantas[" + i + "].numeroFolha",
                        "Excede " + LIMITE_NUMERO_FOLHA + " caracteres."));
            }
            folhas.add(new FolhaGravada(id, nome, folha));
        }
        return folhas;
    }

    private static UUID idDaFolha(JsonNode folha) {
        if (!folha.hasNonNull("id")) {
            return null;
        }
        try {
            return UUID.fromString(folha.get("id").asText());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static String textoOuNulo(JsonNode no, String campo) {
        if (!no.hasNonNull(campo)) {
            return null;
        }
        String texto = no.get(campo).asText().trim();
        return texto.isEmpty() ? null : texto;
    }

    /** Completo: os obrigatórios do tipo escolhido e pelo menos uma folha, todas com o nome do regulamento e o número. */
    /**
     * As folhas só existem quando o processo tem a página das Plantas (sem GeoConform e com itens da página): sem ela,
     * o Conjunto completa-se com os dados do perfil, porque não há folhas a descrever.
     */
    private boolean exigeFolhas(Processo processo) {
        if (processo == null || processo.getDinamica() == null) {
            return true;
        }
        return Boolean.FALSE.equals(processo.getDinamica().getComGeoconform()) && documentoConfigRepository
                .findByInstrumentoProcedimentoConfigAndAtivoTrueOrderByOrdem(processo.getInstrumentoProcedimentoConfig())
                .stream().anyMatch(item -> item.getPagina() == PaginaDocumento.PLANTAS);
    }

    private static boolean conjuntoCompleto(ConjuntoMotuDados dados, Collection<Planta> folhas, boolean exigeFolhas) {
        return dados.completa() && (!exigeFolhas || !folhas.isEmpty()) && folhas.stream().allMatch(folha ->
                folha.getNomeRegulamento() != null && !folha.getNomeRegulamento().isBlank()
                        && folha.getNumeroFolha() != null && !folha.getNumeroFolha().isBlank());
    }

    /**
     * Liga cada folha da página das Plantas ao Conjunto do mesmo nome de item, no mesmo Procedimento (as folhas dos ZIP
     * de depósito ao Conjunto da peça de publicação), e recalcula o `completo` dos Conjuntos, que depende das folhas.
     */
    public void ligarFolhas(UUID processoId) {
        List<MetadadosMotu> conjuntos = metadadosMotuRepository.findByProcesso_Id(processoId).stream()
                .filter(MetadadosMotuService::conjuntoDeDocumento)
                .sorted(Comparator.comparing(MetadadosMotu::getCriadoEm))
                .toList();
        Map<String, MetadadosMotu> porNomeDoItem = new HashMap<>();
        conjuntos.forEach(conjunto -> porNomeDoItem.putIfAbsent(nomeDoItem(conjunto.getProcessoDocumento()), conjunto));
        List<Planta> plantas = plantaRepository.findByProcesso_Id(processoId).stream()
                .filter(MetadadosMotuService::daPaginaDasPlantas).toList();
        for (Planta planta : plantas) {
            MetadadosMotu alvo = porNomeDoItem.get(nomeDoItem(planta.getProcessoDocumento()));
            MetadadosMotu atual = planta.getMetadadosMotu();
            if (!Objects.equals(alvo == null ? null : alvo.getId(), atual == null ? null : atual.getId())) {
                planta.setMetadadosMotu(alvo);
            }
        }
        plantaRepository.saveAll(plantas);
        Map<UUID, List<Planta>> folhas = new HashMap<>();
        plantas.stream().filter(planta -> planta.getMetadadosMotu() != null)
                .forEach(planta -> folhas.computeIfAbsent(planta.getMetadadosMotu().getId(), id -> new ArrayList<>())
                        .add(planta));
        boolean exigeFolhas = exigeFolhas(processoRepository.findById(processoId).orElse(null));
        for (MetadadosMotu conjunto : conjuntos) {
            boolean completo = conjuntoCompleto(deJsonConjunto(conjunto.getDados()),
                    folhas.getOrDefault(conjunto.getId(), List.of()), exigeFolhas);
            if (completo != conjunto.isCompleto()) {
                conjunto.setCompleto(completo);
                metadadosMotuRepository.save(conjunto);
            }
        }
    }

    private static boolean conjuntoDeDocumento(MetadadosMotu ficha) {
        return conjuntoDeItem(ficha) && pecaGraficaDePublicacao(ficha.getProcessoDocumento().getDocumentoConfig());
    }

    private static boolean conjuntoDeItem(MetadadosMotu ficha) {
        return ficha.getTipo() == TipoMetadadosMotu.CDG && !ficha.isOpcional() && ficha.getProcessoDocumento() != null
                && ficha.getProcessoDocumento().getDocumentoConfig() != null;
    }

    /**
     * O Conjunto de um item que deixou de ser peça gráfica de publicação (configuração mudada com o processo em curso,
     * como o Cartograma) já não se pede: sai da página e das pendências; o documento continua no item.
     */
    private static boolean conjuntoJaNaoPedido(MetadadosMotu ficha) {
        return conjuntoDeItem(ficha) && !conjuntoDeDocumento(ficha);
    }

    private static boolean daPaginaDasPlantas(Planta planta) {
        DocumentoConfig item = planta.getProcessoDocumento().getDocumentoConfig();
        return item != null && item.getPagina() == PaginaDocumento.PLANTAS;
    }

    private static String nomeDoItem(ProcessoDocumento documento) {
        DocumentoConfig item = documento.getDocumentoConfig();
        return item == null ? "" : item.getNome();
    }

    private static int ordemDoItem(MetadadosMotu ficha) {
        return conjuntoDeDocumento(ficha) ? ficha.getProcessoDocumento().getDocumentoConfig().getOrdem()
                : Integer.MAX_VALUE;
    }

    /** As folhas da página das Plantas ligadas a cada Conjunto, pela ordem do número de folha. */
    private Map<UUID, List<Planta>> folhasPorConjunto(UUID processoId) {
        Map<UUID, List<Planta>> folhas = new HashMap<>();
        plantaRepository.findByProcesso_Id(processoId).stream()
                .filter(planta -> planta.getMetadadosMotu() != null && daPaginaDasPlantas(planta))
                .sorted(Comparator.comparing(Planta::getNumeroFolha,
                                Comparator.nullsLast(MetadadosMotuService::compararFolhas))
                        .thenComparing(Planta::getCriadoEm))
                .forEach(planta -> folhas.computeIfAbsent(planta.getMetadadosMotu().getId(), id -> new ArrayList<>())
                        .add(planta));
        return folhas;
    }

    private static int compararFolhas(String a, String b) {
        try {
            return Long.compare(Long.parseLong(a.trim()), Long.parseLong(b.trim()));
        } catch (NumberFormatException e) {
            return a.compareToIgnoreCase(b);
        }
    }

    public void criarSerie(Processo processo) {
        if (aplicaFichaMotu(processo)) {
            gravarSerie(processo);
        }
    }

    public void registarPecaGrafica(ProcessoDocumento documento) {
        if (!aplicaFichaMotu(documento)) {
            return;
        }
        Processo processo = documento.getProcesso();
        if (metadadosMotuRepository.findByProcesso_IdAndTipo(processo.getId(), TipoMetadadosMotu.SERIE).isEmpty()) {
            gravarSerie(processo);
        }
        MetadadosMotu conjunto = metadadosMotuRepository.save(MetadadosMotu.conjunto(processo, documento));
        List<Planta> plantas = plantaRepository.findByProcessoDocumento_Id(documento.getId());
        plantas.forEach(planta -> planta.setMetadadosMotu(conjunto));
        ligarFolhas(processo.getId());
        log.info("Ficha MOTU de Conjunto criada — processoId={} metadadosMotuId={} processoDocumentoId={} plantas={}",
                processo.getId(), conjunto.getId(), documento.getId(), plantas.size());
    }

    /** Substituição de um recusado: as fichas (Conjunto e opcionais) ligadas ao ZIP passam ao documento novo. */
    public void passarAoSubstituto(ProcessoDocumento recusado, ProcessoDocumento novo) {
        metadadosMotuRepository.findByProcessoDocumento_Id(recusado.getId())
                .forEach(ficha -> ficha.setProcessoDocumento(novo));
    }

    public void removerDoDocumento(UUID processoDocumentoId) {
        metadadosMotuRepository.findByProcessoDocumento_IdAndOpcionalTrue(processoDocumentoId)
                .forEach(opcional -> opcional.setProcessoDocumento(null));
        metadadosMotuRepository.findByProcessoDocumento_IdAndTipoAndOpcionalFalse(processoDocumentoId,
                TipoMetadadosMotu.CDG).ifPresent(conjunto -> {
                    plantaRepository.findByMetadadosMotu_Id(conjunto.getId())
                            .forEach(planta -> planta.setMetadadosMotu(null));
                    metadadosMotuRepository.delete(conjunto);
                    log.info("Ficha MOTU de Conjunto removida — processoId={} metadadosMotuId={} processoDocumentoId={}",
                            conjunto.getProcesso().getId(), conjunto.getId(), processoDocumentoId);
                });
    }

    public void removerConjuntosDoProcesso(UUID processoId) {
        List<MetadadosMotu> conjuntos = metadadosMotuRepository.findByProcesso_Id(processoId).stream()
                .filter(ficha -> ficha.getTipo() == TipoMetadadosMotu.CDG)
                .toList();
        if (conjuntos.isEmpty()) {
            return;
        }
        metadadosMotuRepository.deleteAll(conjuntos);
        log.info("Fichas MOTU de Conjunto removidas — processoId={} metadadosMotuIds={}", processoId,
                conjuntos.stream().map(MetadadosMotu::getId).toList());
    }

    private Registo registo(MetadadosMotu registo, String tituloDoInstrumento, List<Planta> folhas) {
        if (registo.getTipo() == TipoMetadadosMotu.SERIE) {
            return new Registo(registo.getId(), registo.getTipo(), registo.isOpcional(), registo.isCompleto(),
                    tituloDoInstrumento, null, deJson(registo.getDados()), null);
        }
        ProcessoDocumento documento = registo.getProcessoDocumento();
        String titulo = documento != null && documento.getDocumentoConfig() != null
                ? documento.getDocumentoConfig().getNome() : null;
        ZipConjunto zip = documento == null ? null : new ZipConjunto(documento.getId(), documento.getNomeFicheiro());
        List<FolhaConjunto> plantas = folhas.stream()
                .map(folha -> new FolhaConjunto(folha.getId(), folha.getNomeRegulamento(), folha.getNumeroFolha(),
                        folha.getNomeTiff()))
                .toList();
        return new Registo(registo.getId(), registo.getTipo(), registo.isOpcional(), registo.isCompleto(), titulo, zip,
                deJsonConjunto(registo.getDados()), plantas);
    }

    private <T extends DadosMotu> T ler(JsonNode corpo, Class<T> tipo) {
        T dados;
        try {
            dados = objectMapper.treeToValue(corpo, tipo);
        } catch (JsonMappingException e) {
            boolean semCampo = e.getPath().isEmpty() || e.getPath().get(0).getFieldName() == null;
            throw new MotuCamposInvalidosException("Campos da ficha MOTU inválidos.",
                    List.of(new CampoErro(semCampo ? "dados" : e.getPath().get(0).getFieldName(),
                            semCampo ? "Corpo ilegível." : "Valor inválido.")));
        } catch (JsonProcessingException e) {
            throw new MotuCamposInvalidosException("Campos da ficha MOTU inválidos.",
                    List.of(new CampoErro("dados", "Corpo ilegível.")));
        }
        if (dados == null) {
            throw new MotuCamposInvalidosException("Campos da ficha MOTU inválidos.",
                    List.of(new CampoErro("dados", "Corpo em falta.")));
        }
        List<CampoErro> erros = validator.validate(dados).stream()
                .sorted(Comparator.comparing((ConstraintViolation<T> violacao) -> violacao.getPropertyPath().toString()))
                .map(violacao -> new CampoErro(violacao.getPropertyPath().toString(), violacao.getMessage()))
                .toList();
        if (!erros.isEmpty()) {
            throw new MotuCamposInvalidosException("Campos da ficha MOTU inválidos.", erros);
        }
        return dados;
    }

    private String paraJson(DadosMotu dados) {
        try {
            return objectMapper.writeValueAsString(dados);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Não foi possível serializar a ficha MOTU.", e);
        }
    }

    private SerieMotuDados deJson(String json) {
        try {
            return objectMapper.readValue(json, SerieMotuDados.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Ficha MOTU gravada ilegível.", e);
        }
    }

    private ConjuntoMotuDados deJsonConjunto(String json) {
        try {
            return objectMapper.readValue(json, ConjuntoMotuDados.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Ficha MOTU gravada ilegível.", e);
        }
    }

    private void gravarSerie(Processo processo) {
        MetadadosMotu serie = metadadosMotuRepository.save(MetadadosMotu.serie(processo));
        log.info("Ficha MOTU de Série criada — processoId={} metadadosMotuId={}", processo.getId(), serie.getId());
    }

    private boolean procedimentoFinal(InstrumentoProcedimentoConfig procedimento) {
        return instrumentoProcedimentoConfigRepository
                .findFirstByInstrumentoDinamicaConfigAndAtivoTrueAndObrigatorioTrueOrderByOrdemDesc(
                        procedimento.getInstrumentoDinamicaConfig())
                .map(InstrumentoProcedimentoConfig::getId)
                .filter(id -> Objects.equals(id, procedimento.getId()))
                .isPresent();
    }

    static boolean pecaGraficaDePublicacao(DocumentoConfig item) {
        return item != null && item.getTipoElemento() == TipoElemento.PECA_GRAFICA
                && item.getCategoria() == CategoriaDocumento.PUBLICACAO;
    }
}
