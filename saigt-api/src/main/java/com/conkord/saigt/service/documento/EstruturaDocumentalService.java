package com.conkord.saigt.service.documento;

import com.conkord.saigt.dto.documento.EstruturaDocumentalResponse;
import com.conkord.saigt.dto.documento.ItemDocumentalResponse;
import com.conkord.saigt.dto.documento.PendenciaDocumentalResponse;
import com.conkord.saigt.dto.documento.ProcessoDocumentoListagemResponse;
import com.conkord.saigt.exception.RecursoNaoEncontradoException;
import com.conkord.saigt.persistence.configuracao.CategoriaDocumento;
import com.conkord.saigt.persistence.configuracao.DocumentoConfig;
import com.conkord.saigt.persistence.configuracao.PaginaDocumento;
import com.conkord.saigt.persistence.configuracao.DocumentoConfigRepository;
import com.conkord.saigt.persistence.configuracao.TipoElemento;
import com.conkord.saigt.persistence.operacional.DestinoProcesso;
import com.conkord.saigt.persistence.operacional.Dinamica;
import com.conkord.saigt.persistence.operacional.ElementoInformacaoBase;
import com.conkord.saigt.persistence.operacional.EstadoValidacao;
import com.conkord.saigt.persistence.operacional.Processo;
import com.conkord.saigt.persistence.operacional.ProcessoDocumento;
import com.conkord.saigt.persistence.operacional.ProcessoDocumentoObservacao;
import com.conkord.saigt.persistence.operacional.ProcessoDocumentoObservacaoRepository;
import com.conkord.saigt.persistence.operacional.ProcessoDocumentoRepository;
import com.conkord.saigt.persistence.operacional.ProcessoRepository;
import com.conkord.saigt.service.core.ExtensoesAceites;
import com.conkord.saigt.service.documento.EstruturaDocumental.Item;
import com.conkord.saigt.service.documento.EstruturaDocumental.Separador;
import com.conkord.saigt.service.instrumento.ValidadorSubmissaoProcesso;
import com.conkord.saigt.service.nucleo.AutorizacaoService;
import com.conkord.saigt.service.nucleo.ContextoUtilizadorResolver;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Itens documentais do Procedimento do Processo, por separador e caixa, com os ficheiros já carregados e as
 * pendências. É uma leitura: só Camadas 1 e 2, como a listagem de documentos.
 */
@Service
public class EstruturaDocumentalService {

    private static final String MOTIVO_PENDENCIA = "Falta carregar um ficheiro ou indicar observações.";
    private static final String PECAS_GRAFICAS = TipoElemento.PECA_GRAFICA.codigoElementoAfetado();
    static final String CODIGO_CARTOGRAMA_DEPOSITO = "CARTOGRAMA_DEP";

    private final ProcessoRepository processoRepository;
    private final ProcessoDocumentoRepository processoDocumentoRepository;
    private final DocumentoConfigRepository documentoConfigRepository;
    private final ProcessoDocumentoObservacaoRepository observacaoRepository;
    private final AutorizacaoService autorizacaoService;
    private final ContextoUtilizadorResolver contextoUtilizadorResolver;
    private final ValidadorSubmissaoProcesso validadorSubmissaoProcesso;

    public EstruturaDocumentalService(ProcessoRepository processoRepository,
                                      ProcessoDocumentoRepository processoDocumentoRepository,
                                      DocumentoConfigRepository documentoConfigRepository,
                                      ProcessoDocumentoObservacaoRepository observacaoRepository,
                                      AutorizacaoService autorizacaoService,
                                      ContextoUtilizadorResolver contextoUtilizadorResolver,
                                      ValidadorSubmissaoProcesso validadorSubmissaoProcesso) {
        this.processoRepository = processoRepository;
        this.processoDocumentoRepository = processoDocumentoRepository;
        this.documentoConfigRepository = documentoConfigRepository;
        this.observacaoRepository = observacaoRepository;
        this.autorizacaoService = autorizacaoService;
        this.contextoUtilizadorResolver = contextoUtilizadorResolver;
        this.validadorSubmissaoProcesso = validadorSubmissaoProcesso;
    }

    @Transactional(readOnly = true)
    public EstruturaDocumentalResponse obter(UUID processoId) {
        Processo processo = processoRepository.findById(processoId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Processo não encontrado."));
        autorizacaoService.verificarVisibilidade(contextoUtilizadorResolver.resolver(), processo);
        return paraResposta(montar(processo), processo.getDinamica(), processo.getObservacoesOutros());
    }

    /** Sem autorização: quem chama (a completude na submissão) já a verificou. */
    @Transactional(readOnly = true)
    public EstruturaDocumental montar(Processo processo) {
        List<ProcessoDocumento> ficheiros = processoDocumentoRepository.findByProcesso_IdOrderByCriadoEmAsc(processo.getId())
                .stream().filter(ficheiro -> ficheiro.getEstadoValidacao() != EstadoValidacao.SUBSTITUIDO).toList();
        Map<Long, List<ProcessoDocumento>> ficheirosPorItem = ficheirosPorItem(ficheiros);

        Map<Long, String> observacoesPorItem = observacaoRepository.findByIdProcessoId(processo.getId()).stream()
                .collect(Collectors.toMap(observacao -> observacao.getId().getDocumentoConfigId(),
                        ProcessoDocumentoObservacao::getObservacoes));

        List<Item> itens = itensAplicaveis(processo).stream()
                .map(config -> new Item(config, ficheirosPorItem.getOrDefault(config.getId(), List.of()),
                        observacoesPorItem.get(config.getId())))
                .toList();
        List<ProcessoDocumento> outros = ficheiros.stream()
                .filter(ficheiro -> ficheiro.getDocumentoConfig() == null)
                .toList();
        // Os itens da página das Plantas não aparecem nos separadores, mas contam para a completude.
        List<Item> daPaginaDosDocumentos = itens.stream()
                .filter(item -> item.config().getPagina() == PaginaDocumento.DOCUMENTOS)
                .toList();
        return new EstruturaDocumental(separadores(daPaginaDosDocumentos), outros, itens.stream().filter(Item::pendente).toList());
    }

    /**
     * Um ficheiro conta no seu item e nos itens das suas tags (BL-DOC-003: «diretamente ou por tag»); mantém o seu
     * `documentoConfigId`, para o ecrã distinguir os dois casos. Os do balde «outros» só entram pelas tags.
     */
    private static Map<Long, List<ProcessoDocumento>> ficheirosPorItem(List<ProcessoDocumento> ficheiros) {
        Map<Long, List<ProcessoDocumento>> porItem = new HashMap<>();
        for (ProcessoDocumento ficheiro : ficheiros) {
            Set<Long> itens = new LinkedHashSet<>();
            if (ficheiro.getDocumentoConfig() != null) {
                itens.add(ficheiro.getDocumentoConfig().getId());
            }
            itens.addAll(ficheiro.idsDasTags());
            itens.forEach(item -> porItem.computeIfAbsent(item, id -> new ArrayList<>()).add(ficheiro));
        }
        return porItem;
    }

    /**
     * Sem GeoConform, só se o Procedimento tiver itens na página das Plantas e, quando exige elementos afetados, só
     * com peças gráficas entre eles (EF v1.5 §3.1.2).
     */
    @Transactional(readOnly = true)
    public boolean exigePlantas(Processo processo) {
        return exigePlantas(processo, itensAtivos(processo));
    }

    private boolean exigePlantas(Processo processo, List<DocumentoConfig> itensAtivos) {
        return semGeoconform(processo)
                && itensAtivos.stream().anyMatch(item -> item.getPagina() == PaginaDocumento.PLANTAS)
                && (!validadorSubmissaoProcesso.exigeElementosAfetados(processo) || afeta(processo, PECAS_GRAFICAS));
    }

    private List<DocumentoConfig> itensAplicaveis(Processo processo) {
        List<DocumentoConfig> ativos = itensAtivos(processo);
        boolean comPaginaDasPlantas = exigePlantas(processo, ativos);
        // A planta carrega-se na página das Plantas: o item de depósito com o mesmo nome do ZIP sai da lista.
        Set<String> plantasDaPagina = comPaginaDasPlantas
                ? ativos.stream()
                        .filter(item -> item.getPagina() == PaginaDocumento.PLANTAS && item.getTipoElemento() == TipoElemento.PECA_GRAFICA)
                        .map(DocumentoConfig::getNome)
                        .collect(Collectors.toSet())
                : Set.of();
        // O cartograma de depósito é o «Cartograma das folhas» da página das Plantas: não se pede duas vezes.
        boolean cartogramaNaPagina = comPaginaDasPlantas && ativos.stream()
                .anyMatch(item -> item.getPagina() == PaginaDocumento.PLANTAS
                        && PlantasService.CODIGO_CARTOGRAMA.equals(item.getCodigo()));
        List<DocumentoConfig> itens = ativos.stream()
                .filter(item -> item.getPagina() == PaginaDocumento.DOCUMENTOS
                        ? !substituidoPelaPagina(item, plantasDaPagina)
                                && !(cartogramaNaPagina && CODIGO_CARTOGRAMA_DEPOSITO.equals(item.getCodigo()))
                        : semGeoconform(processo))
                .filter(item -> aplicaAoDestino(processo, item))
                .toList();
        return filtrarPorElementosAfetados(processo, itens);
    }

    /** Há peça gráfica a publicar: um item `PECA_GRAFICA` de publicação que os elementos afetados não excluem. */
    @Transactional(readOnly = true)
    public boolean temPecaGraficaDePublicacaoAplicavel(Processo processo) {
        return filtrarPorElementosAfetados(processo, itensAtivos(processo)).stream()
                .anyMatch(item -> item.getTipoElemento() == TipoElemento.PECA_GRAFICA
                        && item.getCategoria() == CategoriaDocumento.PUBLICACAO);
    }

    private List<DocumentoConfig> filtrarPorElementosAfetados(Processo processo, List<DocumentoConfig> itens) {
        if (!validadorSubmissaoProcesso.exigeElementosAfetados(processo)) {
            return itens;
        }
        List<String> elementosAfetados = processo.getElementosAfetados();
        if (elementosAfetados == null || elementosAfetados.isEmpty()) {
            return List.of();
        }
        // Todo o ato publicado tem Texto do Ato e Deliberação: os itens da Informação Base não dependem dos elementos afetados.
        return itens.stream()
                .filter(item -> item.isIndependenteElementosAfetados() || item.getElementoInformacaoBase() != null
                        || (item.getPagina() == PaginaDocumento.PLANTAS ? elementosAfetados.contains(PECAS_GRAFICAS)
                                : elementosAfetados.contains(item.getTipoElemento().codigoElementoAfetado())))
                .toList();
    }

    private List<DocumentoConfig> itensAtivos(Processo processo) {
        return documentoConfigRepository
                .findByInstrumentoProcedimentoConfigAndAtivoTrueOrderByOrdem(processo.getInstrumentoProcedimentoConfig());
    }

    private static boolean substituidoPelaPagina(DocumentoConfig item, Set<String> plantasDaPagina) {
        return item.getCategoria() == CategoriaDocumento.DEPOSITO && item.getTipoElemento() == TipoElemento.PECA_GRAFICA
                && plantasDaPagina.contains(item.getNome());
    }

    /** Só para depósito, o processo não leva o que segue para publicação (BL-PROC-024): saem os itens `PUBLICACAO`. */
    public static boolean aplicaAoDestino(Processo processo, DocumentoConfig item) {
        return processo.getDestino() != DestinoProcesso.SO_DEPOSITO || item.getCategoria() != CategoriaDocumento.PUBLICACAO;
    }

    private static boolean semGeoconform(Processo processo) {
        return Boolean.FALSE.equals(processo.getDinamica().getComGeoconform());
    }

    private static boolean afeta(Processo processo, String elemento) {
        List<String> elementosAfetados = processo.getElementosAfetados();
        return elementosAfetados != null && elementosAfetados.contains(elemento);
    }

    /** Os itens chegam por `ordem`: a primeira ocorrência de cada separador fixa a sua posição. */
    private static List<Separador> separadores(List<Item> itens) {
        Map<String, Separador> porNome = new LinkedHashMap<>();
        for (Item item : itens) {
            Separador separador = porNome.computeIfAbsent(item.config().getSeparador(),
                    nome -> new Separador(nome, new ArrayList<>(), new ArrayList<>()));
            (item.config().getCategoria() == CategoriaDocumento.PUBLICACAO ? separador.publicacao() : separador.deposito())
                    .add(item);
        }
        return List.copyOf(porNome.values());
    }

    private static EstruturaDocumentalResponse paraResposta(EstruturaDocumental estrutura, Dinamica dinamica,
                                                            String observacoesOutros) {
        return new EstruturaDocumentalResponse(
                estrutura.separadores().stream()
                        .map(separador -> new EstruturaDocumentalResponse.Separador(separador.nome(),
                                elementoInformacaoBase(separador),
                                separador.publicacao().stream().map(item -> paraResposta(item, dinamica)).toList(),
                                separador.deposito().stream().map(item -> paraResposta(item, dinamica)).toList()))
                        .toList(),
                estrutura.outros().stream()
                        .map(ProcessoDocumentoListagemResponse::de)
                        .toList(),
                estrutura.pendencias().stream().map(EstruturaDocumentalService::pendencia).toList(),
                observacoesOutros);
    }

    /** O primeiro item do separador, por `ordem`, que tenha a marca. */
    private static ElementoInformacaoBase elementoInformacaoBase(Separador separador) {
        return Stream.concat(separador.publicacao().stream(), separador.deposito().stream())
                .map(Item::config)
                .filter(config -> config.getElementoInformacaoBase() != null)
                .min(Comparator.comparingInt(DocumentoConfig::getOrdem))
                .map(DocumentoConfig::getElementoInformacaoBase)
                .orElse(null);
    }

    private static ItemDocumentalResponse paraResposta(Item item, Dinamica dinamica) {
        DocumentoConfig config = item.config();
        return new ItemDocumentalResponse(config.getId(), config.getCodigo(), config.getNome(),
                config.getCategoria().name(), config.getTipoElemento().name(), config.getFormato().name(),
                config.isObrigatorio(), ExtensoesAceites.ler(config),
                item.ficheiros().stream().map(ProcessoDocumentoListagemResponse::de).toList(),
                item.observacoes(), item.pendente(), CanalGeoconform.obtidoDoGeoconform(dinamica, config));
    }

    private static PendenciaDocumentalResponse pendencia(Item item) {
        DocumentoConfig config = item.config();
        return new PendenciaDocumentalResponse(config.getId(), config.getCodigo(), config.getNome(),
                config.getCategoria().name(), MOTIVO_PENDENCIA);
    }
}
