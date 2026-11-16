package com.conkord.saigt.service.instrumento;

import com.conkord.saigt.dto.instrumento.LocalizacaoRequest;
import com.conkord.saigt.dto.instrumento.MotivoAlteracaoRequest;
import com.conkord.saigt.exception.ConfirmacaoEmFaltaException;
import com.conkord.saigt.exception.ValidacaoException;
import com.conkord.saigt.exception.ValidacaoException.CampoErro;
import com.conkord.saigt.persistence.configuracao.AssociacaoInstrumentos;
import com.conkord.saigt.persistence.configuracao.DocumentoConfigRepository;
import com.conkord.saigt.persistence.configuracao.InstrumentoProcedimentoConfig;
import com.conkord.saigt.persistence.configuracao.PaginaDocumento;
import com.conkord.saigt.persistence.configuracao.PedeLocalizacao;
import com.conkord.saigt.persistence.configuracao.PedeMotivoAlteracao;
import com.conkord.saigt.persistence.configuracao.PerguntaGeoconform;
import com.conkord.saigt.persistence.configuracao.TipoElemento;
import com.conkord.saigt.persistence.core.EntidadeRepository;
import com.conkord.saigt.persistence.core.ListaValores;
import com.conkord.saigt.persistence.core.ListaValoresRepository;
import com.conkord.saigt.persistence.core.TipoListaValores;
import com.conkord.saigt.persistence.core.TipoAto;
import com.conkord.saigt.persistence.core.TipoEntidade;
import com.conkord.saigt.persistence.operacional.Dinamica;
import com.conkord.saigt.persistence.operacional.DinamicaRepository;
import com.conkord.saigt.persistence.operacional.ElementoInformacaoBase;
import com.conkord.saigt.persistence.operacional.Instrumento;
import com.conkord.saigt.persistence.operacional.Processo;
import com.conkord.saigt.service.core.ClaimsUtilizador.AtosAutorizados;
import com.conkord.saigt.service.documento.EstruturaDocumentalService;
import com.conkord.saigt.service.instrumento.formulario.CatalogoFormularios;
import com.conkord.saigt.service.instrumento.formulario.FormularioFixo;
import com.conkord.saigt.service.nucleo.ContextoUtilizador;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Única fonte das regras dos dados INCM, dos elementos afetados e da fundamentação RJIGT de um Processo: {@code ProcessoService#submeter}
 * recusa com elas e `podeSubmeter` mede-se por elas. A Camada 3c fica de fora porque só `submeter` a recusa.
 */
@Component
public class ValidadorSubmissaoProcesso {

    private static final Logger log = LoggerFactory.getLogger(ValidadorSubmissaoProcesso.class);
    private static final String CAMPO_DATA_DELIBERACAO = "informacaoBase.deliberacao.dataDeliberacao";
    private static final String CAMPO_PRAZO_TEXTO_ATO = "informacaoBase.textoAto.prazoAnos";
    private static final String CAMPO_AVALIACAO_TEXTO_ATO = "informacaoBase.textoAto.avaliacaoAmbiental";
    private static final List<String> CAMPOS_PRAZO = List.of("prazoAnos", "prazoMeses", "prazoDias");
    private static final String MSG_PRAZO_INCOERENTE = "O prazo tem de ser o mesmo da Deliberação.";
    private static final String MSG_AVALIACAO_INCOERENTE = "A avaliação ambiental tem de ser a mesma da Deliberação.";
    private static final String MSG_DATA_DEPOIS_DO_TEXTO_ATO =
            "A data da deliberação tem de ser igual ou anterior à data do texto do ato.";
    private static final String MSG_DATA_DEPOIS_DA_DATA_LIMITE =
            "A data da deliberação tem de ser igual ou anterior à data limite da dinâmica.";
    private static final String MSG_INFORMACAO_BASE_INCOERENTE = "A Informação Base tem campos incoerentes.";
    public static final String CAMPO_DATA_ASSINATURA = "dataAssinatura";
    public static final String MSG_DATA_ASSINATURA_EM_FALTA = "Indique a data de assinatura do ato.";
    private static final String MSG_DATA_ASSINATURA_NA_INFORMACAO_BASE =
            "Indique a data de assinatura do ato no bloco Informação Base do Texto do Ato.";
    private static final DateTimeFormatter DATA_PT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private static final String SERIE_DR_PRIMEIRA = "PRIMEIRA";
    private static final String SERIE_DR_SEGUNDA_H = "SEGUNDA_H";
    private static final String SERIE_DR_SEGUNDA_C = "SEGUNDA_C";
    private static final String TIPO_INFORMACAO_EXTRATO = "EXTRATO";
    private static final String CATEGORIA_PLANO = "PLANO";
    private static final String CATEGORIA_PROGRAMA = "PROGRAMA";
    private static final String TIPO_DINAMICA_ELABORACAO = "ELABORACAO";
    private static final String CAMPO_ELEMENTOS_AFETADOS = "elementosAfetados";
    public static final String CAMPO_INSTRUMENTOS = "instrumentos";
    public static final String MSG_INSTRUMENTOS_EM_FALTA = "Associe os instrumentos que esta dinâmica exige.";
    private static final String CAMPO_ENTIDADE_EMITENTE = "entidadeEmitente";
    public static final String CAMPO_ENTIDADE_INSTRUTORA = "entidadeInstrutora";
    private static final String CAMPO_FUNDAMENTACAO_RJIGT = "fundamentacaoRjigt";
    private static final String CAMPO_INTRODUZ_PLANTA_NOVA = "introduzPlantaNova";
    private static final String PECAS_ESCRITAS = TipoElemento.PECA_ESCRITA.codigoElementoAfetado();
    private static final String PECAS_GRAFICAS = TipoElemento.PECA_GRAFICA.codigoElementoAfetado();
    private static final String MSG_GEOCONFORM_AFETA_PECAS_GRAFICAS =
            "Numa dinâmica com GeoConform, a alteração afeta sempre as Peças Gráficas.";
    private static final String MSG_PLANTA_NOVA_EM_FALTA = "Indique se a alteração introduz alguma planta nova.";
    private static final String MSG_SEM_PERGUNTA_PLANTA_NOVA =
            "Este procedimento não pergunta se a alteração introduz uma planta nova.";
    private static final String MSG_PLANTA_NOVA_EXIGE_PECAS_ESCRITAS =
            "Introduzir uma planta nova exige alteração ao regulamento: assinale as Peças Escritas.";
    private static final String MSG_PUBLICACAO_ALTERACAO_INVALIDA = "A Publicação da Alteração tem campos inválidos.";
    public static final String TIPO_LISTA_FUNDAMENTACAO_RJIGT = TipoListaValores.FUNDAMENTACAO_RJIGT.name();
    private static final String CAMPO_LOCALIZACAO = "localizacao";
    private static final String CAMPO_APLICAVEL_TODA_AREA = "localizacao.aplicavelTodaArea";
    private static final String CAMPO_FREGUESIA = "localizacao.freguesia";
    private static final String CAMPO_TOPONIMO_CODIGO = "localizacao.toponimoCodigo";
    private static final String CAMPO_TOPONIMO_TEXTO = "localizacao.toponimoTexto";
    private static final String CAMPO_CONFORMA = "conformaHierarquiaSuperior";
    private static final String CAMPO_MOTIVOS = "motivosAlteracao";
    private static final String MSG_VALOR_INVALIDO = "Valor inválido.";
    private static final String MSG_MOTIVO_INVALIDO = "Motivo inválido.";
    private static final String MSG_SEM_MOTIVO = "Escolha pelo menos um motivo da alteração.";
    private static final String MSG_FORMULARIO_INVALIDO = "O formulário tem campos inválidos.";
    public static final String MSG_MODALIDADE_RATIFICACAO_EM_FALTA = "Indique se a ratificação é uma Publicação ou uma Alteração.";
    public static final String MSG_TIPO_ATO_EM_FALTA = "Indique o tipo de ato.";
    public static final String MSG_SERIE_DR_EM_FALTA = "Preencha os dados de publicação.";
    public static final String MSG_ENTIDADE_EMITENTE_CCDR = "Escolha a entidade emitente da lista das CCDR.";
    /** Um endereço com domínio e extensão (`nome@dominio.pt`); o `@Email` do rascunho aceita `nome@dominio`. */
    private static final Pattern EMAIL_COM_DOMINIO = Pattern.compile("^[^\\s@]+@[^\\s@.]+(\\.[^\\s@.]+)+$");
    private static final List<String> TIPOS_MOTIVO_ALTERACAO = List.of(TipoListaValores.MOTIVO_ALTERACAO_LEI.name(),
            TipoListaValores.MOTIVO_ALTERACAO_PROGRAMA.name(), TipoListaValores.MOTIVO_ALTERACAO_PLANO.name());

    private static final String PREFIXO_CONFIRMACOES = "confirmacoes.";
    private static final String MSG_CONFIRMACOES_EM_FALTA = "Confirme os campos assinalados.";
    private static final String MSG_CONFIRMACAO_OBRIGATORIA = "Confirmação obrigatória.";
    /** Código do catálogo `tipo_dinamica`, comum a todos os tipos de Instrumento. */
    public static final String CODIGO_DINAMICA_REVOGACAO = "REVOGACAO";
    public static final String CONFIRMACAO_DECLARACAO_TERMINO = "DECLARACAO_TERMINO";
    public static final String CAMPO_CONFIRMACOES = "confirmacoes";
    public static final String MSG_DECLARACAO_TERMINO_EM_FALTA = "Declare que a revogação termina os procedimentos em curso.";

    private final ListaValoresRepository listaValoresRepository;
    private final ObjectMapper objectMapper;
    private final CatalogoFormularios catalogoFormularios;
    private final DinamicaRepository dinamicaRepository;
    /** Só para a entidade emitente da Suspensão da CCDR; nulo nos construtores de teste que não a usam. */
    private final EntidadeRepository entidadeRepository;
    /** Os dois a seguir só para a Informação Base na submissão; nulos nos construtores de teste que não a usam. */
    private final DocumentoConfigRepository documentoConfigRepository;
    private final NumeroDepositoService numeroDepositoService;

    @Autowired
    public ValidadorSubmissaoProcesso(ListaValoresRepository listaValoresRepository, ObjectMapper objectMapper,
                                      CatalogoFormularios catalogoFormularios, DinamicaRepository dinamicaRepository,
                                      EntidadeRepository entidadeRepository, DocumentoConfigRepository documentoConfigRepository,
                                      NumeroDepositoService numeroDepositoService) {
        this.listaValoresRepository = listaValoresRepository;
        this.objectMapper = objectMapper;
        this.catalogoFormularios = catalogoFormularios;
        this.dinamicaRepository = dinamicaRepository;
        this.entidadeRepository = entidadeRepository;
        this.documentoConfigRepository = documentoConfigRepository;
        this.numeroDepositoService = numeroDepositoService;
    }

    public ValidadorSubmissaoProcesso(ListaValoresRepository listaValoresRepository, ObjectMapper objectMapper,
                                      CatalogoFormularios catalogoFormularios, DinamicaRepository dinamicaRepository,
                                      EntidadeRepository entidadeRepository) {
        this(listaValoresRepository, objectMapper, catalogoFormularios, dinamicaRepository, entidadeRepository, null, null);
    }

    public ValidadorSubmissaoProcesso(ListaValoresRepository listaValoresRepository, ObjectMapper objectMapper,
                                      CatalogoFormularios catalogoFormularios, DinamicaRepository dinamicaRepository) {
        this(listaValoresRepository, objectMapper, catalogoFormularios, dinamicaRepository, null);
    }

    public ValidadorSubmissaoProcesso(ListaValoresRepository listaValoresRepository, ObjectMapper objectMapper,
                                      DinamicaRepository dinamicaRepository) {
        this(listaValoresRepository, objectMapper, new CatalogoFormularios(), dinamicaRepository);
    }

    /** Sem repositório das Dinâmicas: só para processos fora da Revogação do IGT. */
    public ValidadorSubmissaoProcesso(ListaValoresRepository listaValoresRepository, ObjectMapper objectMapper) {
        this(listaValoresRepository, objectMapper, new CatalogoFormularios(), null);
    }

    /** Chaves a mais, que o formulário não exige, são ignoradas; todas as em falta seguem na mesma resposta. */
    public void verificarConfirmacoes(FormularioFixo formulario, List<String> confirmacoes) {
        Set<String> confirmadas = confirmacoes == null ? Set.of() : Set.copyOf(confirmacoes);
        List<CampoErro> emFalta = catalogoFormularios.confirmacoesExigidas(formulario).stream()
                .filter(chave -> !confirmadas.contains(chave))
                .sorted()
                .map(chave -> new CampoErro(PREFIXO_CONFIRMACOES + chave, MSG_CONFIRMACAO_OBRIGATORIA))
                .toList();
        if (!emFalta.isEmpty()) {
            throw new ConfirmacaoEmFaltaException(MSG_CONFIRMACOES_EM_FALTA, emFalta);
        }
    }

    /**
     * Na Revogação do IGT, as outras Dinâmicas `EM_CURSO` que abrangem algum dos Instrumentos que ela abrange
     * (`CADUCADA` e `TERMINADA` não contam); vazia em qualquer outra Dinâmica.
     */
    public List<Dinamica> dinamicasATerminar(Processo processo) {
        Dinamica dinamica = processo.getDinamica();
        if (!CODIGO_DINAMICA_REVOGACAO.equals(dinamica.getInstrumentoDinamicaConfig().getTipoDinamica().getCodigo())) {
            return List.of();
        }
        List<UUID> instrumentoIds = dinamica.getInstrumentos().stream().map(Instrumento::getId).toList();
        if (instrumentoIds.isEmpty()) {
            return List.of();
        }
        return dinamicaRepository.listarEmCursoQueAbrangem(instrumentoIds, dinamica.getId());
    }

    /**
     * BL-PROC-040: com Dinâmicas a terminar, a submissão exige `DECLARACAO_TERMINO` em `confirmacoes`. Devolve as
     * Dinâmicas que a declaração abrange (vazia quando não é exigida; a chave, se vier, é então ignorada).
     */
    public List<Dinamica> verificarDeclaracaoTermino(Processo processo, List<String> confirmacoes) {
        List<Dinamica> aTerminar = dinamicasATerminar(processo);
        if (!aTerminar.isEmpty() && (confirmacoes == null || !confirmacoes.contains(CONFIRMACAO_DECLARACAO_TERMINO))) {
            log.warn("Submissão recusada sem declaração de término | processoId={} dinamicasATerminar={}",
                    processo.getId(), aTerminar.stream().map(Dinamica::getId).toList());
            throw new ConfirmacaoEmFaltaException(MSG_DECLARACAO_TERMINO_EM_FALTA,
                    List.of(new CampoErro(CAMPO_CONFIRMACOES, MSG_DECLARACAO_TERMINO_EM_FALTA)));
        }
        return aTerminar;
    }

    /**
     * Discussão Pública: a data da deliberação não pode ser posterior à data limite da Dinâmica (a data de caducidade
     * fixada na publicação da Decisão de início). Recusa a data, não a ação: a caducidade em si nunca bloqueia.
     * Sem Deliberação com data, ou sem data limite, a regra não se aplica.
     */
    public void verificarDataDeliberacaoNoPrazo(Processo processo) {
        violacaoDataDeliberacaoNoPrazo(processo).ifPresent(violacao -> {
            throw new ValidacaoException(MSG_DATA_DEPOIS_DA_DATA_LIMITE, List.of(violacao));
        });
    }

    /**
     * A Informação Base na submissão, numa só recusa: a coerência entre a Deliberação e o Texto do ato, quando o
     * Procedimento prevê os dois (pela configuração dos itens, nunca pelo código do Procedimento), e a data limite da
     * Dinâmica na Discussão Pública e no procedimento final (EF v1.5 §3.1.4). Nunca na gravação do rascunho.
     */
    public void verificarInformacaoBase(Processo processo, FormularioFixo formulario) {
        List<CampoErro> erros = new ArrayList<>(errosCoerenciaInformacaoBase(processo));
        Optional<CampoErro> foraDoPrazo = aplicaDataLimite(processo, formulario)
                ? violacaoDataDeliberacaoNoPrazo(processo) : Optional.empty();
        foraDoPrazo.ifPresent(erros::add);
        if (erros.isEmpty()) {
            return;
        }
        log.warn("Submissão recusada, Informação Base incoerente — processoId={}, campos={}", processo.getId(),
                erros.stream().map(CampoErro::path).toList());
        String mensagem;
        if (erros.size() > 1) {
            mensagem = MSG_INFORMACAO_BASE_INCOERENTE;
        } else {
            mensagem = foraDoPrazo.isPresent() ? MSG_DATA_DEPOIS_DA_DATA_LIMITE : erros.get(0).erro();
        }
        throw new ValidacaoException(mensagem, erros);
    }

    /** As mesmas regras de `verificarInformacaoBase`, para o detalhe não deixar sair dos Documentos com elas por cumprir. */
    public List<CampoErro> pendenciasInformacaoBase(Processo processo, FormularioFixo formulario) {
        List<CampoErro> pendencias = new ArrayList<>(errosCoerenciaInformacaoBase(processo));
        if (aplicaDataLimite(processo, formulario)) {
            violacaoDataDeliberacaoNoPrazo(processo).ifPresent(pendencias::add);
        }
        return pendencias;
    }

    /** A Decisão de início nunca é o procedimento final: ainda não há data limite. */
    private boolean aplicaDataLimite(Processo processo, FormularioFixo formulario) {
        if (formulario == FormularioFixo.DISCUSSAO_PUBLICA) {
            return true;
        }
        boolean formularioDoProcedimentoFinal = formulario == FormularioFixo.INFORMACAO_GERAL
                || formulario == FormularioFixo.PUBLICACAO_ALTERACAO;
        return formularioDoProcedimentoFinal && numeroDepositoService != null
                && numeroDepositoService.doProcedimentoFinal(processo);
    }

    /**
     * Prazo e avaliação ambiental iguais, e a data da deliberação igual ou anterior à do Texto do ato
     * (`data_assinatura`). Cada regra só corre com os dois lados preenchidos; no prazo, nulo conta como 0 e os campos
     * comparam-se um a um (1 ano não é 12 meses).
     */
    public List<CampoErro> errosCoerenciaInformacaoBase(Processo processo) {
        if (!preveDeliberacaoETextoAto(processo)) {
            return List.of();
        }
        JsonNode informacaoBase = lerInformacaoBase(processo);
        JsonNode deliberacao = informacaoBase.path("deliberacao");
        JsonNode textoAto = informacaoBase.path("textoAto");
        List<CampoErro> erros = new ArrayList<>();
        if (temPrazo(deliberacao) && temPrazo(textoAto) && CAMPOS_PRAZO.stream()
                .anyMatch(campo -> inteiroOuZero(deliberacao, campo) != inteiroOuZero(textoAto, campo))) {
            erros.add(new CampoErro(CAMPO_PRAZO_TEXTO_ATO, MSG_PRAZO_INCOERENTE));
        }
        JsonNode avaliacaoDeliberacao = deliberacao.path("avaliacaoAmbiental");
        JsonNode avaliacaoTextoAto = textoAto.path("avaliacaoAmbiental");
        if (avaliacaoDeliberacao.isBoolean() && avaliacaoTextoAto.isBoolean()
                && avaliacaoDeliberacao.booleanValue() != avaliacaoTextoAto.booleanValue()) {
            erros.add(new CampoErro(CAMPO_AVALIACAO_TEXTO_ATO, MSG_AVALIACAO_INCOERENTE));
        }
        Optional<LocalDate> dataDeliberacao = dataDeliberacao(processo);
        if (dataDeliberacao.isPresent() && processo.getDataAssinatura() != null
                && dataDeliberacao.get().isAfter(processo.getDataAssinatura())) {
            erros.add(new CampoErro(CAMPO_DATA_DELIBERACAO, MSG_DATA_DEPOIS_DO_TEXTO_ATO));
        }
        return erros;
    }

    /**
     * Sem item ativo dos Documentos com a Informação Base do Texto do Ato aplicável ao destino do processo, não há
     * bloco onde indicar a data de assinatura: indica-se no cartão «Informação Geral» do formulário. Os itens da
     * Informação Base não dependem dos elementos afetados.
     */
    public boolean dataAssinaturaNoFormulario(Processo processo) {
        if (documentoConfigRepository == null || processo.getInstrumentoProcedimentoConfig() == null) {
            return false;
        }
        return documentoConfigRepository
                .findByInstrumentoProcedimentoConfigAndAtivoTrueOrderByOrdem(processo.getInstrumentoProcedimentoConfig())
                .stream()
                .noneMatch(item -> item.getPagina() == PaginaDocumento.DOCUMENTOS
                        && item.getElementoInformacaoBase() == ElementoInformacaoBase.TEXTO_ATO
                        && EstruturaDocumentalService.aplicaAoDestino(processo, item));
    }

    private boolean preveDeliberacaoETextoAto(Processo processo) {
        if (documentoConfigRepository == null || processo.getInstrumentoProcedimentoConfig() == null) {
            return false;
        }
        Set<ElementoInformacaoBase> previstos =
                documentoConfigRepository.findElementosInformacaoBaseAtivos(processo.getInstrumentoProcedimentoConfig());
        return previstos.contains(ElementoInformacaoBase.DELIBERACAO) && previstos.contains(ElementoInformacaoBase.TEXTO_ATO);
    }

    private JsonNode lerInformacaoBase(Processo processo) {
        String dadosFormulario = processo.getDadosFormulario();
        if (dadosFormulario == null || dadosFormulario.isBlank()) {
            return objectMapper.missingNode();
        }
        try {
            return objectMapper.readTree(dadosFormulario).path("informacaoBase");
        } catch (JsonProcessingException e) {
            return objectMapper.missingNode();
        }
    }

    private static boolean temPrazo(JsonNode elemento) {
        return CAMPOS_PRAZO.stream().anyMatch(campo -> elemento.path(campo).isNumber());
    }

    private static int inteiroOuZero(JsonNode elemento, String campo) {
        JsonNode valor = elemento.path(campo);
        return valor.isNumber() ? valor.intValue() : 0;
    }

    private Optional<CampoErro> violacaoDataDeliberacaoNoPrazo(Processo processo) {
        Optional<LocalDate> dataDeliberacao = dataDeliberacao(processo);
        if (dataDeliberacao.isEmpty()) {
            return Optional.empty();
        }
        Dinamica dinamica = processo.getDinamica();
        LocalDate dataLimite = dinamica.getDataCaducidade();
        if (dataLimite == null) {
            log.info("Data limite da Dinâmica em falta: data da deliberação não verificada | processoId={} dinamicaId={}",
                    processo.getId(), dinamica.getId());
            return Optional.empty();
        }
        if (dataDeliberacao.get().isAfter(dataLimite)) {
            return Optional.of(new CampoErro(CAMPO_DATA_DELIBERACAO,
                    "Posterior à data limite da dinâmica (" + dataLimite.format(DATA_PT) + ")."));
        }
        return Optional.empty();
    }

    /** O formato da data é validado com a Informação Base; uma data ilegível aqui fica para essa validação. */
    private Optional<LocalDate> dataDeliberacao(Processo processo) {
        String dadosFormulario = processo.getDadosFormulario();
        if (dadosFormulario == null || dadosFormulario.isBlank()) {
            return Optional.empty();
        }
        try {
            JsonNode data = objectMapper.readTree(dadosFormulario).path("informacaoBase").path("deliberacao")
                    .path("dataDeliberacao");
            return data.isTextual() && !data.asText().isBlank() ? Optional.of(LocalDate.parse(data.asText()))
                    : Optional.empty();
        } catch (JsonProcessingException | DateTimeParseException e) {
            return Optional.empty();
        }
    }

    /**
     * `incmEnrollmentId` é sempre exigido da sessão: sem ele o Processo não seria submetível à INCM. Inclui a
     * violação do extrato, para `podeSubmeter` não ficar mais largo do que `submeter`.
     */
    public List<CampoErro> pendencias(Processo processo, ContextoUtilizador contexto) {
        List<CampoErro> pendencias = new ArrayList<>();
        // O processo pode nascer sem tipo de ato, mas não se submete sem ele (BL-PROC-026).
        if (processo.getTipoAto() == null) {
            pendencias.add(new CampoErro(CamposFormularioInicial.TIPO_ATO_ID, MSG_TIPO_ATO_EM_FALTA));
        }
        // Sem série configurada no Procedimento, a escolhida no processo (BL-PROC-026, OI-196).
        if (processo.serieDrEfetiva() == null) {
            pendencias.add(new CampoErro(CamposFormularioInicial.SERIE_DR, MSG_SERIE_DR_EM_FALTA));
        }
        if (estaEmBranco(processo.getSumario())) {
            pendencias.add(new CampoErro("sumario", "Obrigatório na submissão."));
        }
        if (processo.getDataAssinatura() == null) {
            pendencias.add(new CampoErro(CAMPO_DATA_ASSINATURA, dataAssinaturaNoFormulario(processo)
                    ? MSG_DATA_ASSINATURA_EM_FALTA : MSG_DATA_ASSINATURA_NA_INFORMACAO_BASE));
        }
        if (contexto.entidadeIncmEnrollmentId() == null) {
            pendencias.add(new CampoErro("incmEnrollmentId", "Sessão sem incm_enrollment_id — não é possível submeter."));
        }

        String serieDr = serieDr(processo);
        if (eSegundaSerie(serieDr)) {
            if (estaEmBranco(processo.getNormaLegal())) {
                pendencias.add(new CampoErro("normaLegal", "Obrigatório na submissão de atos de 2.ª série."));
            }
            if (processo.getTipoInformacao() == null) {
                pendencias.add(new CampoErro("tipoInformacao", "Obrigatório na submissão de atos de 2.ª série."));
            }
            if (contexto.entidadeDrParteId() == null) {
                pendencias.add(new CampoErro("drParteId", "Sessão sem PartId da adesão INCM, não é possível submeter."));
            }
        } else if (SERIE_DR_PRIMEIRA.equals(serieDr)) {
            if (processo.getPublicarSuplemento() == null) {
                pendencias.add(new CampoErro("publicarSuplemento", "Obrigatório na submissão de atos de 1.ª série."));
            } else if (Boolean.TRUE.equals(processo.getPublicarSuplemento()) && processo.getDataSuplemento() == null) {
                pendencias.add(new CampoErro("dataSuplemento", "Obrigatório quando publicarSuplemento é verdadeiro."));
            }
        }

        violacaoDoExtrato(processo, contexto).ifPresent(pendencias::add);
        return pendencias;
    }

    /**
     * É uma validação do dado gravado, não um dado em falta: o rascunho e `submeter` recusam-na com
     * `VALIDACAO`, e não com `DADOS_INCM_EM_FALTA`.
     */
    public void verificarRegraDoExtrato(Processo processo, ContextoUtilizador contexto) {
        Optional<CampoErro> violacao = violacaoDoExtrato(processo, contexto);
        if (violacao.isPresent()) {
            throw new ValidacaoException(violacao.get().erro(), List.of(violacao.get()));
        }
    }

    public void verificarElementosAfetados(List<String> elementosAfetados) {
        if (elementosAfetados == null) {
            return;
        }
        Set<String> aceites = Arrays.stream(TipoElemento.values())
                .map(TipoElemento::codigoElementoAfetado)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (!aceites.containsAll(elementosAfetados)) {
            String mensagem = "Valores aceites: " + String.join(", ", aceites) + ".";
            throw new ValidacaoException(mensagem, List.of(new CampoErro(CAMPO_ELEMENTOS_AFETADOS, mensagem)));
        }
    }

    public Optional<CampoErro> pendenciaElementosAfetados(Processo processo) {
        List<String> elementosAfetados = processo.getElementosAfetados();
        if (!exigeElementosAfetados(processo) || (elementosAfetados != null && !elementosAfetados.isEmpty())) {
            return Optional.empty();
        }
        return Optional.of(new CampoErro(CAMPO_ELEMENTOS_AFETADOS, "Indique os elementos fundamentais afetados."));
    }

    /**
     * Os Procedimentos de Planos pedem os elementos afetados (FR-168, EF v1.5 §3.1.2), menos na Elaboração: um plano
     * novo não altera elementos, e o Figma da Elaboração não tem a pergunta. Os formulários sem a pergunta (restantes
     * servidões, Ratificação) também não os pedem.
     */
    public boolean exigeElementosAfetados(Processo processo) {
        Dinamica dinamica = processo.getDinamica();
        return CATEGORIA_PLANO.equals(dinamica.getInstrumentoDinamicaConfig().getTipoInstrumento().getCategoria())
                && !TIPO_DINAMICA_ELABORACAO.equals(dinamica.getInstrumentoDinamicaConfig().getTipoDinamica().getCodigo())
                && formularioPerguntaElementosAfetados(processo);
    }

    private boolean formularioPerguntaElementosAfetados(Processo processo) {
        InstrumentoProcedimentoConfig config = processo.getInstrumentoProcedimentoConfig();
        if (config == null || config.getTipoProcedimento() == null) {
            return true;
        }
        return catalogoFormularios.formularioDe(config.getTipoProcedimento())
                .map(FormularioFixo::perguntaElementosAfetados)
                .orElse(true);
    }

    /** Pela configuração do Procedimento, nunca pelo tipo de Instrumento. */
    public boolean exigeOpcaoGeoconform(Processo processo) {
        PerguntaGeoconform pergunta = processo.getInstrumentoProcedimentoConfig().getPerguntaGeoconform();
        if (pergunta == PerguntaGeoconform.OBRIGATORIA) {
            return true;
        }
        List<String> elementosAfetados = processo.getElementosAfetados();
        return pergunta == PerguntaGeoconform.SE_PECAS_GRAFICAS
                && elementosAfetados != null
                && elementosAfetados.contains(TipoElemento.PECA_GRAFICA.codigoElementoAfetado());
    }

    /**
     * Só depende de `com_geoconform` (configuração da Dinâmica) e das respostas, nunca do tipo de Instrumento.
     * A pergunta da planta nova só se faz com Peças Gráficas e sem GeoConform; as falhas vão todas na mesma recusa.
     */
    public void verificarPublicacaoAlteracao(Processo processo) {
        List<String> elementosAfetados = processo.getElementosAfetados() == null ? List.of() : processo.getElementosAfetados();
        boolean comGeoconform = Boolean.TRUE.equals(processo.getDinamica().getComGeoconform());
        boolean comPecasGraficas = elementosAfetados.contains(PECAS_GRAFICAS);
        boolean perguntaAplica = comPecasGraficas && !comGeoconform;
        Boolean introduzPlantaNova = CamposFormularioInicial.lerIntroduzPlantaNova(processo.getDadosFormulario(), objectMapper);

        List<CampoErro> erros = new ArrayList<>();
        if (comGeoconform && !comPecasGraficas) {
            erros.add(new CampoErro(CAMPO_ELEMENTOS_AFETADOS, MSG_GEOCONFORM_AFETA_PECAS_GRAFICAS));
        }
        if (perguntaAplica && introduzPlantaNova == null) {
            erros.add(new CampoErro(CAMPO_INTRODUZ_PLANTA_NOVA, MSG_PLANTA_NOVA_EM_FALTA));
        }
        if (!perguntaAplica && introduzPlantaNova != null) {
            erros.add(new CampoErro(CAMPO_INTRODUZ_PLANTA_NOVA, MSG_SEM_PERGUNTA_PLANTA_NOVA));
        }
        if (Boolean.TRUE.equals(introduzPlantaNova) && !elementosAfetados.contains(PECAS_ESCRITAS)) {
            erros.add(new CampoErro(CAMPO_ELEMENTOS_AFETADOS, MSG_PLANTA_NOVA_EXIGE_PECAS_ESCRITAS));
        }
        if (!erros.isEmpty()) {
            throw new ValidacaoException(erros.size() == 1 ? erros.get(0).erro() : MSG_PUBLICACAO_ALTERACAO_INVALIDA, erros);
        }
    }

    /**
     * O número de Instrumentos associados segue `associacao_instrumentos` da Dinâmica (BL-PROC-053): `UM_OU_MAIS`
     * exige pelo menos um; `OPCIONAL` aceita nenhum; `UM` fixa-se ao iniciar a Dinâmica e não se verifica aqui.
     */
    public Optional<CampoErro> pendenciaInstrumentosAssociados(Processo processo) {
        Dinamica dinamica = processo.getDinamica();
        if (dinamica.getInstrumentoDinamicaConfig().getAssociacaoInstrumentos() != AssociacaoInstrumentos.UM_OU_MAIS
                || !dinamica.getInstrumentos().isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new CampoErro(CAMPO_INSTRUMENTOS, MSG_INSTRUMENTOS_EM_FALTA));
    }

    /** Pela categoria do tipo de Instrumento, nunca pelo código do tipo. */
    public Optional<CampoErro> pendenciaEntidadeEmitente(Processo processo) {
        String categoria = processo.getDinamica().getInstrumentoDinamicaConfig().getTipoInstrumento().getCategoria();
        boolean exige = CATEGORIA_PLANO.equals(categoria) || CATEGORIA_PROGRAMA.equals(categoria);
        if (!exige || !estaEmBranco(processo.getEntidadeEmitente())) {
            return Optional.empty();
        }
        return Optional.of(new CampoErro(CAMPO_ENTIDADE_EMITENTE, "Indique a identificação da entidade emitente."));
    }

    /** Só os Programas pedem a entidade instrutora; decide-se pela categoria, nunca pelo código do tipo. */
    public boolean pedeEntidadeInstrutora(Processo processo) {
        return CATEGORIA_PROGRAMA.equals(processo.getDinamica().getInstrumentoDinamicaConfig().getTipoInstrumento().getCategoria());
    }

    public Optional<CampoErro> pendenciaEntidadeInstrutora(Processo processo) {
        boolean obrigatoria = processo.getDinamica().getInstrumentoDinamicaConfig().getTipoInstrumento()
                .isEntidadeInstrutoraObrigatoria();
        if (!pedeEntidadeInstrutora(processo) || !obrigatoria || !estaEmBranco(processo.getEntidadeInstrutora())) {
            return Optional.empty();
        }
        return Optional.of(new CampoErro(CAMPO_ENTIDADE_INSTRUTORA, "Indique a entidade instrutora."));
    }

    /**
     * Campo opcional: lista vazia passa. Devolve os códigos sem repetidos, pela ordem do pedido, ou nulo
     * quando não há nenhum.
     */
    public List<String> verificarFundamentacaoRjigt(Processo processo, List<String> codigos) {
        violacaoFundamentacaoRjigt(processo, codigos).ifPresent(violacao -> {
            throw new ValidacaoException(violacao.erro(), List.of(violacao));
        });
        return codigos == null || codigos.isEmpty() ? null : codigos.stream().distinct().toList();
    }

    /** Volta a verificar o valor gravado: o Master pode ter desativado um código depois da gravação. */
    public Optional<CampoErro> pendenciaFundamentacaoRjigt(Processo processo) {
        return violacaoFundamentacaoRjigt(processo,
                CamposFormularioInicial.lerFundamentacaoRjigt(processo.getDadosFormulario(), objectMapper));
    }

    /** Vazia quando o Procedimento não pede o campo, para não ler a lista sem necessidade. */
    public List<ListaValores> opcoesFundamentacaoRjigt(Processo processo) {
        if (!processo.getInstrumentoProcedimentoConfig().isPedeFundamentacaoRjigt()) {
            return List.of();
        }
        return listaValoresRepository.findByTipoAndAtivoTrueOrderByOrdemAsc(TIPO_LISTA_FUNDAMENTACAO_RJIGT);
    }

    /**
     * Bloqueado depois da 1.ª submissão, o valor já não pode mudar (o bloqueio recusa qualquer diferença): um código
     * que o Master desative depois não pode impedir o autor de gravar ou voltar a submeter.
     */
    private Optional<CampoErro> violacaoFundamentacaoRjigt(Processo processo, List<String> codigos) {
        if (codigos == null || codigos.isEmpty()
                || CamposFormularioInicial.camposBloqueados(processo).contains(CAMPO_FUNDAMENTACAO_RJIGT)) {
            return Optional.empty();
        }
        if (!processo.getInstrumentoProcedimentoConfig().isPedeFundamentacaoRjigt()) {
            return Optional.of(new CampoErro(CAMPO_FUNDAMENTACAO_RJIGT, "Este procedimento não pede a fundamentação RJIGT."));
        }
        Set<String> ativos = opcoesFundamentacaoRjigt(processo).stream()
                .map(ListaValores::getCodigo)
                .collect(Collectors.toSet());
        if (!ativos.containsAll(codigos)) {
            return Optional.of(new CampoErro(CAMPO_FUNDAMENTACAO_RJIGT, "Fundamentação RJIGT inválida."));
        }
        return Optional.empty();
    }


    public LocalizacaoRequest verificarLocalizacao(Processo processo, LocalizacaoRequest pedida) {
        LocalizacaoRequest localizacao = normalizarLocalizacao(pedida);
        lancarSeHouver(errosLocalizacao(processo, localizacao));
        return localizacao;
    }

    public List<MotivoAlteracaoRequest> verificarMotivoAlteracao(Processo processo, Boolean conformaHierarquiaSuperior,
                                                                 List<MotivoAlteracaoRequest> motivos) {
        List<MotivoAlteracaoRequest> pedidos = motivos == null ? List.of() : motivos;
        lancarSeHouver(errosMotivoAlteracao(processo, conformaHierarquiaSuperior, pedidos));
        return pedidos.isEmpty() ? null : pedidos.stream().distinct().toList();
    }

    public List<CampoErro> pendenciasLocalizacao(Processo processo) {
        LocalizacaoRequest localizacao = CamposFormularioInicial.lerLocalizacao(processo.getDadosFormulario(), objectMapper);
        List<CampoErro> erros = new ArrayList<>(errosLocalizacao(processo, localizacao));
        boolean indicada = localizacao != null
                && (Boolean.TRUE.equals(localizacao.aplicavelTodaArea()) || localizacao.freguesia() != null);
        if (erros.isEmpty() && !indicada
                && processo.getInstrumentoProcedimentoConfig().getPedeLocalizacao() == PedeLocalizacao.OBRIGATORIA
                && listaValoresRepository.existsByTipoAndAtivoTrue(TipoListaValores.FREGUESIA.name())) {
            erros.add(new CampoErro(CAMPO_LOCALIZACAO,
                    "Indique a freguesia ou escolha \"Aplicável a toda a área do IGT\"."));
        }
        return erros;
    }

    public List<CampoErro> pendenciasMotivoAlteracao(Processo processo) {
        Boolean conforma = CamposFormularioInicial.lerConformaHierarquiaSuperior(processo.getDadosFormulario(), objectMapper);
        List<MotivoAlteracaoRequest> motivos = CamposFormularioInicial.lerMotivosAlteracao(processo.getDadosFormulario(), objectMapper);
        List<CampoErro> erros = new ArrayList<>(errosMotivoAlteracao(processo, conforma, motivos));
        if (!erros.isEmpty()) {
            return erros;
        }
        PedeMotivoAlteracao pede = processo.getInstrumentoProcedimentoConfig().getPedeMotivoAlteracao();
        if (pede == PedeMotivoAlteracao.COM_PERGUNTA && conforma == null) {
            erros.add(new CampoErro(CAMPO_CONFORMA, "Responda se a alteração conforma o plano com uma lei, programa ou "
                    + "plano de hierarquia superior."));
            return erros;
        }
        boolean pedeMotivos = pede == PedeMotivoAlteracao.OBRIGATORIO
                || (pede == PedeMotivoAlteracao.COM_PERGUNTA && Boolean.TRUE.equals(conforma));
        if (pedeMotivos && motivos.isEmpty() && haMotivosAtivos()) {
            erros.add(new CampoErro(CAMPO_MOTIVOS, MSG_SEM_MOTIVO));
        }
        return erros;
    }

    private List<CampoErro> errosLocalizacao(Processo processo, LocalizacaoRequest localizacao) {
        List<CampoErro> erros = new ArrayList<>();
        if (localizacao == null) {
            return erros;
        }
        if (processo.getInstrumentoProcedimentoConfig().getPedeLocalizacao() == PedeLocalizacao.NAO) {
            erros.add(new CampoErro(CAMPO_LOCALIZACAO, "Este procedimento não pede a localização."));
            return erros;
        }
        boolean aplicavelTodaArea = Boolean.TRUE.equals(localizacao.aplicavelTodaArea());
        boolean comToponimo = localizacao.toponimoCodigo() != null || localizacao.toponimoTexto() != null;
        if (aplicavelTodaArea && (localizacao.freguesia() != null || comToponimo)) {
            erros.add(new CampoErro(CAMPO_APLICAVEL_TODA_AREA,
                    "Escolha \"Aplicável a toda a área do IGT\" ou indique a freguesia."));
        } else if (comToponimo && localizacao.freguesia() == null) {
            erros.add(new CampoErro(CAMPO_TOPONIMO_CODIGO, "O topónimo acompanha a freguesia."));
        }
        if (localizacao.toponimoCodigo() != null && localizacao.toponimoTexto() != null) {
            erros.add(new CampoErro(CAMPO_TOPONIMO_TEXTO, "Escolha um topónimo da lista ou escreva-o, não ambos."));
        }
        if (CamposFormularioInicial.camposBloqueados(processo).contains(CAMPO_LOCALIZACAO)) {
            return erros;
        }
        if (localizacao.freguesia() != null && !ativo(TipoListaValores.FREGUESIA.name(), localizacao.freguesia())) {
            erros.add(new CampoErro(CAMPO_FREGUESIA, MSG_VALOR_INVALIDO));
        }
        if (localizacao.toponimoCodigo() != null && !ativo(TipoListaValores.TOPONIMO.name(), localizacao.toponimoCodigo())) {
            erros.add(new CampoErro(CAMPO_TOPONIMO_CODIGO, MSG_VALOR_INVALIDO));
        }
        return erros;
    }

    private List<CampoErro> errosMotivoAlteracao(Processo processo, Boolean conforma, List<MotivoAlteracaoRequest> motivos) {
        List<CampoErro> erros = new ArrayList<>();
        PedeMotivoAlteracao pede = processo.getInstrumentoProcedimentoConfig().getPedeMotivoAlteracao();
        if (pede == PedeMotivoAlteracao.NAO) {
            if (conforma != null || !motivos.isEmpty()) {
                erros.add(new CampoErro(CAMPO_MOTIVOS, "Este procedimento não pede o motivo da alteração."));
            }
            return erros;
        }
        if (pede == PedeMotivoAlteracao.OBRIGATORIO && conforma != null) {
            erros.add(new CampoErro(CAMPO_CONFORMA, "Este procedimento não faz a pergunta da conformação."));
        }
        if (pede == PedeMotivoAlteracao.COM_PERGUNTA && Boolean.FALSE.equals(conforma) && !motivos.isEmpty()) {
            erros.add(new CampoErro(CAMPO_MOTIVOS, "Sem conformação, não há motivos a indicar."));
            return erros;
        }
        if (CamposFormularioInicial.camposBloqueados(processo).contains(CAMPO_MOTIVOS)) {
            return erros;
        }
        Set<String> ativos = motivosAtivos(motivos);
        IntStream.range(0, motivos.size())
                .filter(i -> !motivoValido(motivos.get(i), ativos))
                .forEach(i -> erros.add(new CampoErro(CAMPO_MOTIVOS + "[" + i + "]", MSG_MOTIVO_INVALIDO)));
        return erros;
    }

    private static boolean motivoValido(MotivoAlteracaoRequest motivo, Set<String> ativos) {
        return motivo != null && TIPOS_MOTIVO_ALTERACAO.contains(motivo.tipo())
                && ativos.contains(chaveMotivo(motivo.tipo(), motivo.codigo()));
    }

    /** Uma só consulta para todos os motivos do pedido. */
    private Set<String> motivosAtivos(List<MotivoAlteracaoRequest> motivos) {
        Set<String> codigos = motivos.stream().filter(Objects::nonNull).map(MotivoAlteracaoRequest::codigo)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        if (codigos.isEmpty()) {
            return Set.of();
        }
        return listaValoresRepository.findByTipoInAndCodigoIn(TIPOS_MOTIVO_ALTERACAO, codigos).stream()
                .filter(ListaValores::isAtivo)
                .map(valor -> chaveMotivo(valor.getTipo(), valor.getCodigo()))
                .collect(Collectors.toSet());
    }

    private boolean haMotivosAtivos() {
        return TIPOS_MOTIVO_ALTERACAO.stream().anyMatch(listaValoresRepository::existsByTipoAndAtivoTrue);
    }

    private boolean ativo(String tipo, String codigo) {
        return listaValoresRepository.findByTipoInAndCodigoIn(List.of(tipo), List.of(codigo)).stream()
                .anyMatch(ListaValores::isAtivo);
    }

    private static String chaveMotivo(String tipo, String codigo) {
        return tipo + ":" + codigo;
    }

    private static LocalizacaoRequest normalizarLocalizacao(LocalizacaoRequest pedida) {
        if (pedida == null) {
            return null;
        }
        LocalizacaoRequest localizacao = new LocalizacaoRequest(
                Boolean.TRUE.equals(pedida.aplicavelTodaArea()) ? Boolean.TRUE : null,
                emBrancoComoNulo(pedida.freguesia()), emBrancoComoNulo(pedida.toponimoCodigo()),
                emBrancoComoNulo(pedida.toponimoTexto()));
        return localizacao.equals(new LocalizacaoRequest(null, null, null, null)) ? null : localizacao;
    }

    private static String emBrancoComoNulo(String valor) {
        return valor == null || valor.isBlank() ? null : valor.strip();
    }

    private static void lancarSeHouver(List<CampoErro> erros) {
        if (!erros.isEmpty()) {
            throw new ValidacaoException(erros.size() == 1 ? erros.get(0).erro() : MSG_FORMULARIO_INVALIDO, erros);
        }
    }

    /**
     * Só na 2.ª série, a única cujo claim traz o indicador `extrato`; sem claim (utilizador interno) não
     * se aplica, como na Camada 3c. Um tipo de ato fora do claim conta como não permitindo extrato.
     */
    private Optional<CampoErro> violacaoDoExtrato(Processo processo, ContextoUtilizador contexto) {
        AtosAutorizados atosAutorizados = contexto.atosAutorizados();
        if (atosAutorizados == null
                || !TIPO_INFORMACAO_EXTRATO.equals(processo.getTipoInformacao())
                || !eSegundaSerie(serieDr(processo))) {
            return Optional.empty();
        }
        TipoAto tipoAto = processo.getTipoAto();
        Long idTipoAto = tipoAto == null ? null : tipoAto.getIncmActTypeIdSerie2();
        if (atosAutorizados.permiteExtrato(idTipoAto)) {
            return Optional.empty();
        }
        return Optional.of(new CampoErro("tipoInformacao", "O tipo de ato não permite publicar extrato."));
    }

    /**
     * Os campos que o formulário da Ratificação exige na submissão (EF v1.5 §3.1.2), todos na mesma recusa. A
     * «Publicação» não é obrigatória na EF, mas a INCM exige-a na 1.ª série; a data de assinatura é da Informação Geral
     * e pede-se no próprio formulário (a Ratificação não tem Informação Base). Vazia nos outros formulários.
     */
    public List<CampoErro> pendenciasRatificacao(Processo processo) {
        if (catalogoFormularios.formularioDe(processo.getInstrumentoProcedimentoConfig().getTipoProcedimento())
                .orElse(null) != FormularioFixo.RATIFICACAO) {
            return List.of();
        }
        List<CampoErro> pendencias = new ArrayList<>();
        if (CamposFormularioInicial.lerModalidadeRatificacao(processo.getDadosFormulario(), objectMapper) == null) {
            pendencias.add(new CampoErro(CamposFormularioInicial.MODALIDADE_RATIFICACAO, MSG_MODALIDADE_RATIFICACAO_EM_FALTA));
        }
        if (estaEmBranco(processo.getEntidadeEmitente())) {
            pendencias.add(new CampoErro(CAMPO_ENTIDADE_EMITENTE, "Indique a identificação da entidade emitente."));
        }
        if (estaEmBranco(processo.getContactoTramitacao())) {
            pendencias.add(new CampoErro("contactoTramitacao", "Indique um e-mail válido para a tramitação."));
        }
        if (estaEmBranco(processo.getContactoPublicacao())) {
            pendencias.add(new CampoErro("contactoPublicacao", "Indique o contacto para publicação."));
        }
        if (estaEmBranco(processo.getNormaLegal())) {
            pendencias.add(new CampoErro("normaLegal", "Indique a norma legal ou mera conveniência."));
        }
        if (processo.getTipoAto() == null) {
            pendencias.add(new CampoErro(CamposFormularioInicial.TIPO_ATO_ID, MSG_TIPO_ATO_EM_FALTA));
        }
        if (processo.getPublicarSuplemento() == null) {
            pendencias.add(new CampoErro("publicarSuplemento", "Indique se a publicação é no Diário ou em Suplemento."));
        } else if (Boolean.TRUE.equals(processo.getPublicarSuplemento()) && processo.getDataSuplemento() == null) {
            pendencias.add(new CampoErro("dataSuplemento", "Obrigatório quando publicarSuplemento é verdadeiro."));
        }
        if (processo.getTipoInformacao() == null) {
            pendencias.add(new CampoErro("tipoInformacao", "Indique o tipo de informação."));
        }
        if (estaEmBranco(processo.getSumario())) {
            pendencias.add(new CampoErro("sumario", "Indique o sumário do ato."));
        }
        if (processo.getDataAssinatura() == null) {
            pendencias.add(new CampoErro("dataAssinatura", "Indique a data de assinatura do ato."));
        }
        return pendencias;
    }

    private boolean formularioSuspensaoCcdr(Processo processo) {
        return catalogoFormularios.formularioDe(processo.getInstrumentoProcedimentoConfig().getTipoProcedimento())
                .orElse(null) == FormularioFixo.SUSPENSAO_CCDR;
    }

    /**
     * Na Suspensão da CCDR a entidade emitente é o nome (descrição SAIGT) de uma entidade ativa `CCDR_DR`. No rascunho
     * recusa só um valor fora da lista; o vazio só se exige na submissão. Nos outros formulários não faz nada.
     */
    public void verificarEntidadeEmitenteDaLista(Processo processo, String entidadeEmitente) {
        if (!formularioSuspensaoCcdr(processo) || estaEmBranco(entidadeEmitente) || entidadeCcdrAtiva(entidadeEmitente)) {
            return;
        }
        log.warn("Entidade emitente fora da lista das CCDR recusada — processoId={}", processo.getId());
        throw new ValidacaoException(MSG_ENTIDADE_EMITENTE_CCDR,
                List.of(new CampoErro(CAMPO_ENTIDADE_EMITENTE, MSG_ENTIDADE_EMITENTE_CCDR)));
    }

    private boolean entidadeCcdrAtiva(String nome) {
        return entidadeRepository != null
                && entidadeRepository.existsByTipoEntidadeAndDescricaoSaigtAndAtivoTrue(TipoEntidade.CCDR_DR, nome.strip());
    }

    /**
     * Os campos que o formulário da Suspensão da CCDR exige na submissão (EF v1.5 §3.1.2), todos na mesma recusa: a
     * entidade emitente da lista das CCDR, os contactos, a norma legal, o tipo de ato, o tipo de informação e o
     * sumário, e a data de assinatura do ato, que a INCM exige e que o formulário pede (sem Informação Base). Sem
     * elementos afetados. Vazia nos outros formulários.
     */
    public List<CampoErro> pendenciasSuspensaoCcdr(Processo processo) {
        if (!formularioSuspensaoCcdr(processo)) {
            return List.of();
        }
        List<CampoErro> pendencias = new ArrayList<>();
        if (estaEmBranco(processo.getEntidadeEmitente()) || !entidadeCcdrAtiva(processo.getEntidadeEmitente())) {
            pendencias.add(new CampoErro(CAMPO_ENTIDADE_EMITENTE, MSG_ENTIDADE_EMITENTE_CCDR));
        }
        if (estaEmBranco(processo.getContactoTramitacao())) {
            pendencias.add(new CampoErro("contactoTramitacao", "Indique um e-mail válido para a tramitação."));
        }
        if (estaEmBranco(processo.getContactoPublicacao())) {
            pendencias.add(new CampoErro("contactoPublicacao", "Indique o contacto para publicação."));
        }
        if (estaEmBranco(processo.getNormaLegal())) {
            pendencias.add(new CampoErro("normaLegal", "Indique a norma legal ou mera conveniência."));
        }
        if (processo.getTipoAto() == null) {
            pendencias.add(new CampoErro(CamposFormularioInicial.TIPO_ATO_ID, MSG_TIPO_ATO_EM_FALTA));
        }
        if (processo.getTipoInformacao() == null) {
            pendencias.add(new CampoErro("tipoInformacao", "Indique o tipo de informação."));
        }
        if (estaEmBranco(processo.getSumario())) {
            pendencias.add(new CampoErro("sumario", "Indique o sumário do ato."));
        }
        if (processo.getDataAssinatura() == null) {
            pendencias.add(new CampoErro("dataAssinatura", "Indique a data de assinatura do ato."));
        }
        return pendencias;
    }

    private boolean formularioGoverno(Processo processo) {
        FormularioFixo formulario = catalogoFormularios
                .formularioDe(processo.getInstrumentoProcedimentoConfig().getTipoProcedimento()).orElse(null);
        return formulario == FormularioFixo.SUSPENSAO_MP_GOVERNO || formulario == FormularioFixo.PRORROGACAO_GOVERNO;
    }

    /**
     * Os campos que o formulário da Suspensão e das Medidas Preventivas do Governo exige na submissão (EF v1.5 §3.1.2,
     * «X = obrigatório»), todos na mesma recusa: a entidade emitente, o contacto de tramitação com um e-mail válido, o
     * contacto para publicação, a norma legal, o tipo de ato, a «Publicação» (só na Publicação; com a data do
     * suplemento quando é Suplemento, TR-173), o tipo de informação e o sumário, e a data de assinatura do ato, que a
     * INCM exige e que o formulário pede (sem Informação Base). Sem elementos afetados. Vazia nos outros formulários.
     */
    public List<CampoErro> pendenciasGoverno(Processo processo) {
        if (!formularioGoverno(processo)) {
            return List.of();
        }
        boolean prorrogacao = catalogoFormularios.formularioDe(processo.getInstrumentoProcedimentoConfig()
                .getTipoProcedimento()).orElse(null) == FormularioFixo.PRORROGACAO_GOVERNO;
        List<CampoErro> pendencias = new ArrayList<>();
        if (estaEmBranco(processo.getEntidadeEmitente())) {
            pendencias.add(new CampoErro(CAMPO_ENTIDADE_EMITENTE, "Indique a identificação da entidade emitente."));
        }
        if (estaEmBranco(processo.getContactoTramitacao())
                || !EMAIL_COM_DOMINIO.matcher(processo.getContactoTramitacao().strip()).matches()) {
            pendencias.add(new CampoErro("contactoTramitacao", "Indique um e-mail válido para a tramitação."));
        }
        if (estaEmBranco(processo.getContactoPublicacao())) {
            pendencias.add(new CampoErro("contactoPublicacao", "Indique o contacto para publicação."));
        }
        if (estaEmBranco(processo.getNormaLegal())) {
            pendencias.add(new CampoErro("normaLegal", "Indique a norma legal ou mera conveniência."));
        }
        if (processo.getTipoAto() == null) {
            pendencias.add(new CampoErro(CamposFormularioInicial.TIPO_ATO_ID, MSG_TIPO_ATO_EM_FALTA));
        }
        if (!prorrogacao && processo.getPublicarSuplemento() == null) {
            pendencias.add(new CampoErro("publicarSuplemento", "Indique se a publicação é no Diário ou em Suplemento."));
        } else if (!prorrogacao && Boolean.TRUE.equals(processo.getPublicarSuplemento())
                && processo.getDataSuplemento() == null) {
            pendencias.add(new CampoErro("dataSuplemento", "Indique a data do suplemento."));
        }
        if (processo.getTipoInformacao() == null) {
            pendencias.add(new CampoErro("tipoInformacao", "Indique o tipo de informação."));
        }
        if (estaEmBranco(processo.getSumario())) {
            pendencias.add(new CampoErro("sumario", "Indique o sumário do ato."));
        }
        if (processo.getDataAssinatura() == null) {
            pendencias.add(new CampoErro("dataAssinatura", "Indique a data de assinatura do ato."));
        }
        return pendencias;
    }

    private static String serieDr(Processo processo) {
        return processo.serieDrEfetiva();
    }

    private static boolean eSegundaSerie(String serieDr) {
        return SERIE_DR_SEGUNDA_H.equals(serieDr) || SERIE_DR_SEGUNDA_C.equals(serieDr);
    }

    private static boolean estaEmBranco(String valor) {
        return valor == null || valor.isBlank();
    }
}
