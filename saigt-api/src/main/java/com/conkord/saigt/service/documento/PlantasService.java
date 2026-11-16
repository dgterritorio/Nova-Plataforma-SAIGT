package com.conkord.saigt.service.documento;

import com.conkord.saigt.dto.documento.GravarPlantasRequest;
import com.conkord.saigt.dto.documento.GravarPlantasRequest.AssociacaoPlanta;
import com.conkord.saigt.dto.documento.PlantasResponse;
import com.conkord.saigt.dto.documento.PlantasResponse.FicheiroPlanta;
import com.conkord.saigt.dto.documento.PlantasResponse.ItemPlantas;
import com.conkord.saigt.dto.documento.PlantasResponse.LinhaPlanta;
import com.conkord.saigt.exception.RecursoNaoEncontradoException;
import com.conkord.saigt.exception.ValidacaoException;
import com.conkord.saigt.exception.ValidacaoException.CampoErro;
import com.conkord.saigt.persistence.configuracao.DocumentoConfig;
import com.conkord.saigt.persistence.configuracao.DocumentoConfigRepository;
import com.conkord.saigt.persistence.configuracao.PaginaDocumento;
import com.conkord.saigt.persistence.configuracao.TipoElemento;
import com.conkord.saigt.persistence.operacional.EstadoValidacao;
import com.conkord.saigt.persistence.operacional.Planta;
import com.conkord.saigt.persistence.operacional.PlantaRepository;
import com.conkord.saigt.persistence.operacional.Processo;
import com.conkord.saigt.persistence.operacional.ProcessoDocumento;
import com.conkord.saigt.persistence.operacional.ProcessoDocumentoRepository;
import com.conkord.saigt.persistence.operacional.ProcessoRepository;
import com.conkord.saigt.service.core.ExtensoesAceites;
import com.conkord.saigt.service.documento.LeitorPlantas.Linha;
import com.conkord.saigt.service.documento.LeitorPlantas.Txt;
import com.conkord.saigt.service.documento.PlantaService.LinhaDaPagina;
import com.conkord.saigt.service.nucleo.AutorizacaoService;
import com.conkord.saigt.service.nucleo.ContextoUtilizador;
import com.conkord.saigt.service.nucleo.ContextoUtilizadorResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Página das Plantas de um Procedimento sem GeoConform (EF v1.5 §3.1.2, FR-170): ZIP de cada planta, TXT com os
 * nomes conforme o regulamento e a associação de cada linha a um TIFF dos ZIP, que se grava por processo.
 * Os ficheiros carregam-se pelo upload dos documentos, nos itens com `pagina = PLANTAS`.
 */
@Service
public class PlantasService {

    private static final Logger log = LoggerFactory.getLogger(PlantasService.class);
    static final String CODIGO_TXT = "PLANTAS_TXT_NOMES_DEP";
    static final String CODIGO_CARTOGRAMA = "PLANTAS_CARTOGRAMA_DEP";
    private static final String PREFIXO_PLANTA = "planta de ";
    private static final String TIPO_DINAMICA_ALTERACAO = "ALTERACAO";

    private final ProcessoRepository processoRepository;
    private final DocumentoConfigRepository documentoConfigRepository;
    private final ProcessoDocumentoRepository processoDocumentoRepository;
    private final PlantaRepository plantaRepository;
    private final PlantaService plantaService;
    private final ArmazenamentoDocumentos armazenamento;
    private final EstruturaDocumentalService estruturaDocumentalService;
    private final AutorizacaoService autorizacaoService;
    private final ContextoUtilizadorResolver contextoUtilizadorResolver;
    private final ProcessoAlteravel processoAlteravel;
    private final MetadadosMotuService metadadosMotuService;

    public PlantasService(ProcessoRepository processoRepository, DocumentoConfigRepository documentoConfigRepository,
                          ProcessoDocumentoRepository processoDocumentoRepository,
                          PlantaRepository plantaRepository, PlantaService plantaService,
                          ArmazenamentoDocumentos armazenamento,
                          EstruturaDocumentalService estruturaDocumentalService, AutorizacaoService autorizacaoService,
                          ContextoUtilizadorResolver contextoUtilizadorResolver, ProcessoAlteravel processoAlteravel,
                          MetadadosMotuService metadadosMotuService) {
        this.processoRepository = processoRepository;
        this.documentoConfigRepository = documentoConfigRepository;
        this.processoDocumentoRepository = processoDocumentoRepository;
        this.plantaRepository = plantaRepository;
        this.plantaService = plantaService;
        this.armazenamento = armazenamento;
        this.estruturaDocumentalService = estruturaDocumentalService;
        this.autorizacaoService = autorizacaoService;
        this.contextoUtilizadorResolver = contextoUtilizadorResolver;
        this.processoAlteravel = processoAlteravel;
        this.metadadosMotuService = metadadosMotuService;
    }

    /** Leitura do estado gravado, sem pendências: o que falta só se diz na submissão. */
    private record Estado(List<DocumentoConfig> itens, Map<Long, ProcessoDocumento> ficheiros, Txt txt,
                          Map<String, List<String>> tiffsPorZip, Map<String, ProcessoDocumento> zips,
                          List<Planta> associadas) {
    }

    @Transactional(readOnly = true)
    public PlantasResponse obter(UUID processoId) {
        Processo processo = processoRepository.findById(processoId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Processo não encontrado."));
        autorizacaoService.verificarVisibilidade(contextoUtilizadorResolver.resolver(), processo);
        if (!estruturaDocumentalService.exigePlantas(processo)) {
            return new PlantasResponse(false, List.of(), List.of(), List.of(), false, false);
        }
        Estado estado = ler(processo, true);
        boolean alteracao = alteracao(processo);
        List<ItemPlantas> itens = estado.itens().stream()
                .map(item -> new ItemPlantas(item.getId(), item.getCodigo(), item.getSeparador(), item.getDescricao(),
                        item.getCategoria().name(), obrigatorioNaPagina(item, alteracao), ExtensoesAceites.ler(item),
                        Optional.ofNullable(estado.ficheiros().get(item.getId()))
                                .map(ficheiro -> new FicheiroPlanta(ficheiro.getId(), ficheiro.getNomeFicheiro()))
                                .orElse(null)))
                .toList();
        List<LinhaPlanta> linhas = estado.txt().linhas().stream()
                .map(linha -> new LinhaPlanta(linha.ordem(), linha.tipo(), linha.nome(), linha.folha(),
                        tiffGravado(linha, estado), opcoes(linha, estado.tiffsPorZip())))
                .toList();
        return new PlantasResponse(true, itens, linhas, estado.txt().erros(), exigeCartograma(estado.txt().linhas()),
                cartograma(estado).isPresent() && porAssociar(estado).isEmpty());
    }

    public record PdfCartograma(String nomeFicheiro, byte[] conteudo) {
    }

    /** PDF do cartograma das folhas, gerado na hora a partir do cartograma carregado, do TXT e da associação gravada. */
    @Transactional(readOnly = true)
    public PdfCartograma cartogramaPdf(UUID processoId) {
        Processo processo = processoRepository.findById(processoId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Processo não encontrado."));
        autorizacaoService.verificarVisibilidade(contextoUtilizadorResolver.resolver(), processo);
        if (!estruturaDocumentalService.exigePlantas(processo)) {
            throw new ValidacaoException("Este processo não tem a página das Plantas.",
                    List.of(new CampoErro("plantas", "Só sem GeoConform há cartograma das folhas.")));
        }
        Estado estado = ler(processo, false);
        Optional<ProcessoDocumento> cartograma = cartograma(estado);
        List<CampoErro> erros = new ArrayList<>(porAssociar(estado));
        if (cartograma.isEmpty()) {
            erros.add(0, new CampoErro("plantas.cartograma", "Carregue o cartograma das folhas."));
        }
        if (!erros.isEmpty()) {
            throw new ValidacaoException("Ainda não se pode gerar o PDF do cartograma.", erros);
        }
        Map<String, CartogramaPdf.Planta> plantas = new LinkedHashMap<>();
        for (Linha linha : estado.txt().linhas()) {
            plantas.computeIfAbsent(linha.tipo() + "|" + linha.nome(), chave -> new CartogramaPdf.Planta(linha.nome(), new ArrayList<>()))
                    .tiffs().add(tiffGravado(linha, estado));
        }
        ProcessoDocumento ficheiro = cartograma.get();
        try (InputStream conteudo = armazenamento.abrir(ficheiro.getCaminhoFicheiro(), null, 0, 0)) {
            byte[] pdf = CartogramaPdf.gerar(conteudo.readAllBytes(), ficheiro.getExtensao(), List.copyOf(plantas.values()));
            log.info("PDF do cartograma gerado | processoId={} plantas={} bytes={}", processo.getId(), plantas.size(), pdf.length);
            return new PdfCartograma("cartograma-das-folhas.pdf", pdf);
        } catch (IOException e) {
            log.warn("Cartograma ilegível | processoId={} documentoId={}", processo.getId(), ficheiro.getId(), e);
            throw new ValidacaoException("Não foi possível ler o cartograma das folhas.",
                    List.of(new CampoErro("plantas.cartograma", "Carregue o cartograma de novo, em PDF, JPG ou PNG.")));
        }
    }

    /** Substitui as associações gravadas. Cada TIFF só se usa uma vez e tem de estar num ZIP do mesmo tipo. */
    @Transactional
    public PlantasResponse gravar(UUID processoId, GravarPlantasRequest request) {
        ContextoUtilizador contexto = contextoUtilizadorResolver.resolver();
        Processo processo = processoAlteravel.obter(processoId, contexto);
        if (!estruturaDocumentalService.exigePlantas(processo)) {
            throw new ValidacaoException("Este processo não tem a página das Plantas.",
                    List.of(new CampoErro("associacoes", "Só sem GeoConform se associam plantas.")));
        }
        Estado estado = ler(processo, true);
        Map<Integer, Linha> linhasPorOrdem = estado.txt().linhas().stream()
                .collect(Collectors.toMap(Linha::ordem, Function.identity()));
        List<CampoErro> erros = new ArrayList<>();
        Set<String> usados = new HashSet<>();
        Set<Integer> linhasUsadas = new HashSet<>();
        List<LinhaDaPagina> novas = new ArrayList<>();
        for (int i = 0; i < request.associacoes().size(); i++) {
            AssociacaoPlanta associacao = request.associacoes().get(i);
            String campo = "associacoes[" + i + "].nomeTiff";
            Linha linha = linhasPorOrdem.get(associacao.ordem());
            if (linha == null) {
                erros.add(new CampoErro("associacoes[" + i + "].ordem", "Não há esta linha no TXT dos nomes."));
            } else if (!linhasUsadas.add(linha.ordem())) {
                erros.add(new CampoErro("associacoes[" + i + "].ordem", "Esta linha já tem um TIFF associado."));
            } else if (!opcoes(linha, estado.tiffsPorZip()).contains(associacao.nomeTiff())) {
                erros.add(new CampoErro(campo, "O TIFF não está no ZIP desta planta."));
            } else if (!usados.add(associacao.nomeTiff())) {
                erros.add(new CampoErro(campo, "Este TIFF já está associado a outra planta."));
            } else {
                novas.add(new LinhaDaPagina(zipDoTiff(linha, associacao.nomeTiff(), estado), linha.ordem(), linha.nome(), linha.folha(),
                        associacao.nomeTiff()));
            }
        }
        if (!erros.isEmpty()) {
            throw new ValidacaoException("Associação das plantas inválida.", erros);
        }
        plantaService.substituirDaPaginaDasPlantas(processo, novas);
        metadadosMotuService.ligarFolhas(processoId);
        log.info("Plantas associadas | processoId={} linhas={} associadas={}", processo.getId(),
                estado.txt().linhas().size(), novas.size());
        return obter(processoId);
    }

    /** O que impede a submissão: linhas por ler ou sem TIFF e o cartograma em falta. Os ficheiros são da completude. */
    @Transactional(readOnly = true)
    public List<CampoErro> pendencias(Processo processo) {
        if (!estruturaDocumentalService.exigePlantas(processo)) {
            return List.of();
        }
        // Sem os TIFF: o detalhe chama isto sempre, e ler os ZIP obrigaria a descarregá-los.
        Estado estado = ler(processo, false);
        boolean alteracao = alteracao(processo);
        List<CampoErro> pendencias = new ArrayList<>();
        if (alteracao && estado.zips().isEmpty()) {
            pendencias.add(new CampoErro("plantas.zips", "Carregue pelo menos uma planta alterada."));
        }
        estado.itens().stream()
                .filter(item -> obrigatorioNaPagina(item, alteracao) && !estado.ficheiros().containsKey(item.getId()))
                .forEach(item -> pendencias.add(new CampoErro("plantas." + item.getCodigo(), "Carregue: " + item.getSeparador() + ".")));
        estado.txt().erros().forEach(erro -> pendencias.add(new CampoErro("plantas.txt", erro)));
        estado.txt().linhas().stream()
                .filter(linha -> tiffGravado(linha, estado) == null)
                .forEach(linha -> pendencias.add(porAssociar(linha)));
        if (exigeCartograma(estado.txt().linhas()) && cartograma(estado).isEmpty()) {
            pendencias.add(new CampoErro("plantas.cartograma", "Carregue o cartograma das folhas: há plantas com mais de uma folha."));
        }
        return pendencias;
    }

    /** Para o PDF: sem TXT lido não há plantas a listar, por isso também falta. */
    private static List<CampoErro> porAssociar(Estado estado) {
        if (estado.txt().linhas().isEmpty()) {
            return List.of(new CampoErro("plantas.txt", "Carregue o TXT com os nomes das plantas."));
        }
        return estado.txt().linhas().stream()
                .filter(linha -> tiffGravado(linha, estado) == null)
                .map(PlantasService::porAssociar)
                .toList();
    }

    private static CampoErro porAssociar(Linha linha) {
        return new CampoErro("plantas.linhas[" + linha.ordem() + "]",
                "Associe um TIFF a «" + linha.nome() + (linha.folha() == null ? "" : " – folha " + linha.folha()) + "».");
    }

    private static Optional<ProcessoDocumento> cartograma(Estado estado) {
        return estado.itens().stream()
                .filter(item -> CODIGO_CARTOGRAMA.equals(item.getCodigo()))
                .map(item -> estado.ficheiros().get(item.getId()))
                .filter(Objects::nonNull)
                .findFirst();
    }

    private Estado ler(Processo processo, boolean comTiffs) {
        List<DocumentoConfig> itens = documentoConfigRepository
                .findByInstrumentoProcedimentoConfigAndAtivoTrueOrderByOrdem(processo.getInstrumentoProcedimentoConfig())
                .stream().filter(item -> item.getPagina() == PaginaDocumento.PLANTAS).toList();
        Set<Long> idsItens = itens.stream().map(DocumentoConfig::getId).collect(Collectors.toSet());
        // O mais recente de cada item: a lista vem por data de criação.
        Map<Long, ProcessoDocumento> ficheiros = new LinkedHashMap<>();
        for (ProcessoDocumento ficheiro : processoDocumentoRepository.findByProcesso_IdOrderByCriadoEmAsc(processo.getId())) {
            if (ficheiro.getEstadoValidacao() != EstadoValidacao.SUBSTITUIDO && ficheiro.getDocumentoConfig() != null
                    && idsItens.contains(ficheiro.getDocumentoConfig().getId())) {
                ficheiros.put(ficheiro.getDocumentoConfig().getId(), ficheiro);
            }
        }
        Txt txt = itens.stream().filter(item -> CODIGO_TXT.equals(item.getCodigo()))
                .map(item -> ficheiros.get(item.getId())).filter(Objects::nonNull).findFirst()
                .map(this::lerTxt)
                .orElse(new Txt(List.of(), List.of()));
        Map<String, List<String>> tiffsPorZip = new LinkedHashMap<>();
        Map<String, ProcessoDocumento> zips = new LinkedHashMap<>();
        for (DocumentoConfig item : itens) {
            ProcessoDocumento ficheiro = ficheiros.get(item.getId());
            if (item.getTipoElemento() == TipoElemento.PECA_GRAFICA && ficheiro != null) {
                zips.put(item.getSeparador(), ficheiro);
                if (comTiffs) {
                    tiffsPorZip.put(item.getSeparador(), tiffs(ficheiro));
                }
            }
        }
        Set<UUID> idsZip = zips.values().stream().map(ProcessoDocumento::getId).collect(Collectors.toSet());
        List<Planta> associadas = plantaRepository.findByProcesso_Id(processo.getId()).stream()
                .filter(planta -> idsZip.contains(planta.getProcessoDocumento().getId())).toList();
        return new Estado(itens, ficheiros, txt, tiffsPorZip, zips, associadas);
    }

    private Txt lerTxt(ProcessoDocumento ficheiro) {
        try (InputStream conteudo = armazenamento.abrir(ficheiro.getCaminhoFicheiro(), null, 0, 0)) {
            return LeitorPlantas.lerTxt(conteudo);
        } catch (IOException e) {
            log.warn("TXT dos nomes das plantas ilegível | documentoId={}", ficheiro.getId(), e);
            return new Txt(List.of(), List.of("Não foi possível ler o TXT dos nomes. Carregue-o de novo."));
        }
    }

    private List<String> tiffs(ProcessoDocumento ficheiro) {
        try (InputStream conteudo = armazenamento.abrir(ficheiro.getCaminhoFicheiro(), null, 0, 0)) {
            return LeitorPlantas.tiffsDoZip(conteudo);
        } catch (IOException e) {
            log.warn("ZIP das plantas ilegível | documentoId={}", ficheiro.getId(), e);
            return List.of();
        }
    }

    /**
     * O tipo do TXT («Ordenamento») escolhe o ZIP («Planta de Ordenamento»). Sem ZIP desse tipo a linha fica sem
     * opções, para não se associar a folha de outra planta (EF v1.5 §3.1.2).
     */
    private static List<String> separadoresDoTipo(Linha linha, Collection<String> separadores) {
        String tipo = LeitorPlantas.normalizar(linha.tipo());
        return separadores.stream()
                .filter(separador -> {
                    String nomeZip = LeitorPlantas.normalizar(separador).replaceFirst("^" + PREFIXO_PLANTA, "");
                    return nomeZip.contains(tipo) || tipo.contains(nomeZip);
                })
                .toList();
    }

    private static List<String> opcoes(Linha linha, Map<String, List<String>> tiffsPorZip) {
        return separadoresDoTipo(linha, tiffsPorZip.keySet()).stream()
                .flatMap(separador -> tiffsPorZip.get(separador).stream())
                .toList();
    }

    private static ProcessoDocumento zipDoTiff(Linha linha, String nomeTiff, Estado estado) {
        return separadoresDoTipo(linha, estado.tiffsPorZip().keySet()).stream()
                .filter(separador -> estado.tiffsPorZip().get(separador).contains(nomeTiff))
                .map(estado.zips()::get)
                .findFirst().orElseThrow();
    }

    /**
     * Vale a associação gravada para a mesma linha do TXT (`linha_txt`) num ZIP do tipo, mesmo com o nome e a folha
     * corrigidos na ficha MOTU; as linhas anteriores à `linha_txt` reconhecem-se pelo nome e pela folha.
     */
    private static String tiffGravado(Linha linha, Estado estado) {
        Set<UUID> zipsDoTipo = separadoresDoTipo(linha, estado.zips().keySet()).stream()
                .map(separador -> estado.zips().get(separador).getId()).collect(Collectors.toSet());
        return estado.associadas().stream()
                .filter(planta -> zipsDoTipo.contains(planta.getProcessoDocumento().getId())
                        && planta.getNomeTiff() != null
                        && (planta.getLinhaTxt() != null
                        ? planta.getLinhaTxt() == linha.ordem()
                        : linha.nome().equals(planta.getNomeRegulamento())
                        && Objects.equals(linha.folha(), planta.getNumeroFolha())))
                .map(Planta::getNomeTiff)
                .findFirst().orElse(null);
    }

    /**
     * Os ZIP das plantas e o TXT dos nomes são obrigatórios na página das Plantas; o cartograma só com mais de uma
     * folha. Não se marcam obrigatórios na configuração: lá só a caixa de publicação o é. Na Alteração carregam-se só
     * as plantas que a Dinâmica muda (OI-248): nenhum ZIP é obrigatório por si, mas tem de haver pelo menos um.
     */
    private static boolean obrigatorioNaPagina(DocumentoConfig item, boolean alteracao) {
        if (CODIGO_CARTOGRAMA.equals(item.getCodigo())) {
            return false;
        }
        return !(alteracao && item.getTipoElemento() == TipoElemento.PECA_GRAFICA);
    }

    private static boolean alteracao(Processo processo) {
        return TIPO_DINAMICA_ALTERACAO.equals(processo.getDinamica().getInstrumentoDinamicaConfig().getTipoDinamica().getCodigo());
    }

    /** EF v1.5 §3.1.2: com peças gráficas de mais de uma folha, carrega-se o cartograma. */
    static boolean exigeCartograma(List<Linha> linhas) {
        Map<String, Long> folhasPorPlanta = linhas.stream()
                .collect(Collectors.groupingBy(linha -> linha.tipo() + "|" + linha.nome(), Collectors.counting()));
        return folhasPorPlanta.values().stream().anyMatch(folhas -> folhas > 1);
    }
}
