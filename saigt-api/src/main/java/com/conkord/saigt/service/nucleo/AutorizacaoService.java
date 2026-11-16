package com.conkord.saigt.service.nucleo;

import com.conkord.saigt.exception.AcaoNaoAutorizadaException;
import com.conkord.saigt.exception.DinamicaNaoElegivelException;
import com.conkord.saigt.exception.EstadoInvalidoException;
import com.conkord.saigt.exception.ParteDrNaoAutorizadaException;
import com.conkord.saigt.exception.PcmAtorNaoAutorizadoException;
import com.conkord.saigt.exception.ProcessoEmCursoException;
import com.conkord.saigt.exception.RecursoNaoEncontradoException;
import com.conkord.saigt.exception.SequenciamentoNaoCumpridoException;
import com.conkord.saigt.exception.TipoAtoNaoAutorizadoException;
import com.conkord.saigt.persistence.configuracao.InstrumentoDinamicaConfig;
import com.conkord.saigt.persistence.configuracao.InstrumentoProcedimentoConfig;
import com.conkord.saigt.persistence.configuracao.InstrumentoProcedimentoConfigRepository;
import com.conkord.saigt.persistence.configuracao.PermissaoAcaoRepository;
import com.conkord.saigt.persistence.core.TipoAto;
import com.conkord.saigt.persistence.core.TipoEntidade;
import com.conkord.saigt.persistence.core.TipoInstrumento;
import com.conkord.saigt.persistence.operacional.Dinamica;
import com.conkord.saigt.persistence.operacional.EstadoDinamica;
import com.conkord.saigt.persistence.operacional.EstadoProcesso;
import com.conkord.saigt.persistence.operacional.Instrumento;
import com.conkord.saigt.persistence.operacional.PerfilInterno;
import com.conkord.saigt.persistence.operacional.Processo;
import com.conkord.saigt.persistence.operacional.ProcessoRepository;
import com.conkord.saigt.persistence.regras.Regra;
import com.conkord.saigt.persistence.regras.RegraRepository;
import com.conkord.saigt.service.core.ClaimsUtilizador;
import com.conkord.saigt.service.regras.MotorRegrasService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Autorização por camadas: 2 visibilidade, 3 tipo de entidade, 3b autoria, 3c publicação INCM, 4 estado/regras
 * ({@link #procedimentoDisponivel}). A Camada 1 (JWT) é validada antes, pelo resource server.
 * <p>
 * A visibilidade transversal interna avalia-se por `perfilInterno`, não por `ator`: os três perfis internos
 * veem tudo, mas `VISITANTE` nunca tem `ator`. O ator `DGT` (`MASTER`/`TECNICO`) valida e envia; o que a
 * matriz de perfis reserva ao `MASTER` exige ainda {@link #verificarPerfilMaster}.
 */
@Service
public class AutorizacaoService {

    private static final Logger log = LoggerFactory.getLogger(AutorizacaoService.class);

    private static final String ATOR_ENTIDADE = "ENTIDADE";
    private static final String ATOR_PCM = "PCM";
    private static final String ATOR_DGT = "DGT";
    private static final String SERIE_DR_PRIMEIRA = "PRIMEIRA";
    private static final String SERIE_DR_SEGUNDA_H = "SEGUNDA_H";
    private static final String SERIE_DR_SEGUNDA_C = "SEGUNDA_C";
    static final String MSG_PCM_NAO_CRIA = "A PCM não cria Instrumentos, Dinâmicas nem processos.";
    static final String MSG_DINAMICA_TERMINADA = "Esta dinâmica está terminada.";

    private final PermissaoAcaoRepository permissaoAcaoRepository;
    private final RegraRepository regraRepository;
    private final InstrumentoProcedimentoConfigRepository instrumentoProcedimentoConfigRepository;
    private final ProcessoRepository processoRepository;
    private final MotorRegrasService motorRegrasService;

    public AutorizacaoService(PermissaoAcaoRepository permissaoAcaoRepository, RegraRepository regraRepository,
                               InstrumentoProcedimentoConfigRepository instrumentoProcedimentoConfigRepository,
                               ProcessoRepository processoRepository, MotorRegrasService motorRegrasService) {
        this.permissaoAcaoRepository = permissaoAcaoRepository;
        this.regraRepository = regraRepository;
        this.instrumentoProcedimentoConfigRepository = instrumentoProcedimentoConfigRepository;
        this.processoRepository = processoRepository;
        this.motorRegrasService = motorRegrasService;
    }

    /** Falha com 404, nunca 403, para não revelar a existência do recurso. */
    public void verificarVisibilidade(ContextoUtilizador contexto, Processo processo) {
        if (!processoVisivel(contexto, processo)) {
            throw new RecursoNaoEncontradoException("Recurso não encontrado.");
        }
    }

    /**
     * Camada 2 de um processo em leitura, sem lançar: o processo da entidade da sessão (pelo id, nunca pelo NIF);
     * o `VALIDADO_DGT` atribuído ao utilizador PCM; ou o de um Procedimento `visivel_na_dinamica` quando a entidade
     * da sessão tem outro processo na mesma Dinâmica. Os Instrumentos não têm entidade e são visíveis a todos.
     */
    public boolean processoVisivel(ContextoUtilizador contexto, Processo processo) {
        return contexto.temVisibilidadeTransversal() || processoProprio(contexto, processo)
                || pcmAtribuido(contexto, processo) || visivelPelaDinamica(contexto, processo);
    }

    /** O processo é da entidade da sessão, ou da PCM por atribuição: o que dá direito a agir, e não só a ler. */
    private boolean processoDoAtor(ContextoUtilizador contexto, Processo processo) {
        return contexto.temVisibilidadeTransversal() || processoProprio(contexto, processo) || pcmAtribuido(contexto, processo);
    }

    private static boolean processoProprio(ContextoUtilizador contexto, Processo processo) {
        return contexto.entidadeId() != null && contexto.entidadeId().equals(processo.getEntidade().getId());
    }

    /** Só em `VALIDADO_DGT` e só para o utilizador a quem a DGT o atribuiu (`pcm_atribuido_a`). */
    private static boolean pcmAtribuido(ContextoUtilizador contexto, Processo processo) {
        return contexto.pcm() && processo.getEstado() == EstadoProcesso.VALIDADO_DGT
                && processo.getPcmAtribuidoA() != null
                && processo.getPcmAtribuidoA().getId().equals(contexto.utilizadorId());
    }

    private boolean visivelPelaDinamica(ContextoUtilizador contexto, Processo processo) {
        return contexto.entidadeId() != null && processo.getInstrumentoProcedimentoConfig().isVisivelNaDinamica()
                && processoRepository.existsByDinamicaAndEntidade_Id(processo.getDinamica(), contexto.entidadeId());
    }

    /**
     * Criar um processo: os Instrumentos e as Dinâmicas são visíveis a todos, o processo nasce na entidade da
     * sessão. Recebe a Dinâmica concreta porque o sequenciamento é por ocorrência, não por catálogo.
     */
    public void verificarPossibilidadeDeAgir(ContextoUtilizador contexto, Dinamica dinamica,
                                              InstrumentoProcedimentoConfig instrumentoProcedimentoConfig) {
        recusarCriacaoPelaPcm(contexto, "criar processo");
        verificarCamada3(contexto, instrumentoProcedimentoConfig);

        if (dinamica.getEstado() == EstadoDinamica.TERMINADA && procedimentosPosterioresAoFinal(dinamica).stream()
                .noneMatch(posterior -> mesmoProcedimento(posterior, instrumentoProcedimentoConfig))) {
            log.warn("Procedimento recusado numa dinâmica terminada | utilizadorId={} dinamicaId={} procedimentoId={}",
                    contexto.utilizadorId(), dinamica.getId(), instrumentoProcedimentoConfig.getId());
            throw new EstadoInvalidoException(MSG_DINAMICA_TERMINADA);
        }
        if (processoEmCursoBloqueia(processosEmCurso(dinamica), instrumentoProcedimentoConfig)) {
            throw new ProcessoEmCursoException("Já existe um processo em curso nesta dinâmica.");
        }
        if (!sequenciamentoDisponivel(dinamica, instrumentoProcedimentoConfig)) {
            throw new SequenciamentoNaoCumpridoException("Procedimento indisponível — há Procedimentos anteriores obrigatórios por publicar.");
        }
        if (!elegivel(dinamica, instrumentoProcedimentoConfig)) {
            throw new EstadoInvalidoException("Procedimento indisponível — regra de elegibilidade não cumprida.");
        }
    }

    /**
     * O que uma Dinâmica `TERMINADA` ainda oferece (a Prorrogação depois da Publicação): os Procedimentos ativos, não
     * obrigatórios e não `ato_pontual` com `ordem` maior do que a do Procedimento final, sem mudar o estado da Dinâmica.
     * Vazio noutros estados. Regra partilhada pela criação e pelo `/disponivel`; decide só a configuração.
     */
    public List<InstrumentoProcedimentoConfig> procedimentosPosterioresAoFinal(Dinamica dinamica) {
        return procedimentosPosterioresAoFinalPorDinamica(List.of(dinamica)).getOrDefault(dinamica.getId(), List.of());
    }

    /**
     * {@link #procedimentosPosterioresAoFinal(Dinamica)} de várias Dinâmicas, pelo id, com duas consultas ao todo (as
     * configurações e os processos), nunca por Dinâmica. Sai o Procedimento cuja última ocorrência na Tentativa corrente
     * não está `ANULADO` nem `EM_SUBMISSAO` (este retoma-se, não se cria), salvo o `repetivel` com a última `PUBLICADO`.
     * Só entram no mapa as Dinâmicas com algum Procedimento por fazer.
     */
    public Map<UUID, List<InstrumentoProcedimentoConfig>> procedimentosPosterioresAoFinalPorDinamica(Collection<Dinamica> dinamicas) {
        List<Dinamica> terminadas = dinamicas.stream().filter(dinamica -> dinamica.getEstado() == EstadoDinamica.TERMINADA).toList();
        if (terminadas.isEmpty()) {
            return Map.of();
        }
        Map<Long, List<InstrumentoProcedimentoConfig>> candidatosPorConfig = instrumentoProcedimentoConfigRepository
                .listarAtivosPorConfigs(terminadas.stream().map(dinamica -> dinamica.getInstrumentoDinamicaConfig().getId())
                        .collect(Collectors.toSet()))
                .stream()
                .collect(Collectors.groupingBy(config -> config.getInstrumentoDinamicaConfig().getId())).entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, entrada -> posterioresAoFinal(entrada.getValue())));
        List<InstrumentoProcedimentoConfig> candidatos = candidatosPorConfig.values().stream().flatMap(List::stream).toList();
        if (candidatos.isEmpty()) {
            return Map.of();
        }

        Map<UUID, Integer> tentativaPorDinamica = terminadas.stream()
                .collect(Collectors.toMap(Dinamica::getId, Dinamica::getTentativa, (uma, outra) -> uma));
        Map<UUID, Map<Long, Processo>> ultimoPorDinamica = new HashMap<>();
        for (Processo processo : processoRepository.findByDinamicaInAndInstrumentoProcedimentoConfigIn(terminadas, candidatos)) {
            if (Integer.valueOf(processo.getTentativa()).equals(tentativaPorDinamica.get(processo.getDinamica().getId()))) {
                ultimoPorDinamica.computeIfAbsent(processo.getDinamica().getId(), id -> new HashMap<>())
                        .merge(processo.getInstrumentoProcedimentoConfig().getId(), processo,
                                (atual, outro) -> outro.getOcorrencia() > atual.getOcorrencia() ? outro : atual);
            }
        }

        Map<UUID, List<InstrumentoProcedimentoConfig>> resultado = new HashMap<>();
        for (Dinamica dinamica : terminadas) {
            Map<Long, Processo> ultimos = ultimoPorDinamica.getOrDefault(dinamica.getId(), Map.of());
            List<InstrumentoProcedimentoConfig> porFazer = candidatosPorConfig
                    .getOrDefault(dinamica.getInstrumentoDinamicaConfig().getId(), List.of()).stream()
                    .filter(candidato -> aindaPorFazer(candidato, ultimos.get(candidato.getId())))
                    .toList();
            if (!porFazer.isEmpty()) {
                resultado.put(dinamica.getId(), porFazer);
            }
        }
        return resultado;
    }

    /**
     * O Procedimento final é o que termina a Dinâmica: o obrigatório de maior `ordem`, sem os atos pontuais (o mesmo de
     * {@code InstrumentoProcedimentoConfigRepository#findFirstByInstrumentoDinamicaConfigAndAtivoTrueAndObrigatorioTrueAndAtoPontualFalseOrderByOrdemDesc}).
     */
    private static List<InstrumentoProcedimentoConfig> posterioresAoFinal(List<InstrumentoProcedimentoConfig> ativosDaConfig) {
        return ativosDaConfig.stream()
                .filter(config -> config.isAtivo() && config.isObrigatorio() && !config.isAtoPontual())
                .mapToInt(InstrumentoProcedimentoConfig::getOrdem).max()
                .stream().boxed()
                .flatMap(ordemFinal -> ativosDaConfig.stream().filter(config -> config.isAtivo() && !config.isObrigatorio()
                        && !config.isAtoPontual() && config.getOrdem() > ordemFinal))
                .toList();
    }

    private static boolean aindaPorFazer(InstrumentoProcedimentoConfig candidato, Processo ultimo) {
        if (ultimo == null) {
            return true;
        }
        EstadoProcesso estado = ultimo.getEstado();
        return EstadoProcesso.PERMITEM_NOVA_TENTATIVA.contains(estado) || estado == EstadoProcesso.EM_SUBMISSAO
                || (candidato.isRepetivel() && estado == EstadoProcesso.PUBLICADO);
    }

    private static boolean mesmoProcedimento(InstrumentoProcedimentoConfig um, InstrumentoProcedimentoConfig outro) {
        return um == outro || (um.getId() != null && um.getId().equals(outro.getId()));
    }

    /**
     * Camadas 2 e 3, sem a disponibilidade do Procedimento: para agir sobre um Processo que já existe, onde a
     * Camada 4 é o estado dele e não o sequenciamento, já avaliado na criação.
     */
    public void verificarPermissaoDeAgir(ContextoUtilizador contexto, Processo processo) {
        verificarVisibilidade(contexto, processo);
        if (!processoDoAtor(contexto, processo)) {
            log.warn("Processo visível só em leitura | utilizadorId={} processoId={}", contexto.utilizadorId(), processo.getId());
            throw new AcaoNaoAutorizadaException("O processo é só de leitura para a entidade da sessão.");
        }
        verificarCamada3(contexto, processo.getInstrumentoProcedimentoConfig());
    }

    private void verificarCamada3(ContextoUtilizador contexto, InstrumentoProcedimentoConfig instrumentoProcedimentoConfig) {
        if (!camada3Autoriza(contexto, instrumentoProcedimentoConfig)) {
            log.warn("Camada 3 recusou agir no procedimento | utilizadorId={} procedimentoId={}",
                    contexto.utilizadorId(), instrumentoProcedimentoConfig.getId());
            throw new AcaoNaoAutorizadaException("Tipo da entidade da sessão sem permissão neste procedimento.");
        }
    }

    private boolean camada3Autoriza(ContextoUtilizador contexto, InstrumentoProcedimentoConfig instrumentoProcedimentoConfig) {
        return tipoEntidadeDaSessao(contexto)
                .map(tipo -> permissaoAcaoRepository.existsByTipoEntidadeAndInstrumentoProcedimentoConfigAndAtivoTrue(
                        tipo, instrumentoProcedimentoConfig))
                .orElse(false);
    }

    /**
     * «Devolver à DGT»: só o ator `PCM`. Na Camada 2 basta o processo estar atribuído ao utilizador da sessão, em
     * qualquer estado, para que um processo anulado enquanto estava com a PCM dê 409 pelo estado e não 404; o processo
     * nunca atribuído, já devolvido ou atribuído a outro utilizador PCM dá 404. A Camada 3 é a linha `PCM` da
     * configuração do Procedimento.
     */
    public void verificarPossibilidadeDeDevolverDgt(ContextoUtilizador contexto, Processo processo) {
        if (!contexto.pcm()) {
            throw new PcmAtorNaoAutorizadoException("Só a PCM pode devolver processos à DGT.");
        }
        if (!atribuidoAoUtilizador(contexto, processo) && !processoVisivel(contexto, processo)) {
            throw new RecursoNaoEncontradoException("Recurso não encontrado.");
        }
        if (!camada3Autoriza(contexto, processo.getInstrumentoProcedimentoConfig())) {
            log.warn("Camada 3 recusou a devolução à DGT | utilizadorId={} procedimentoId={}",
                    contexto.utilizadorId(), processo.getInstrumentoProcedimentoConfig().getId());
            throw new PcmAtorNaoAutorizadoException("Tipo da entidade da sessão sem permissão neste procedimento.");
        }
    }

    /** {@link #verificarPossibilidadeDeDevolverDgt} sem lançar, e só com o processo atribuído ao utilizador da sessão. */
    public boolean podeDevolverDgt(ContextoUtilizador contexto, Processo processo) {
        return atribuidoAoUtilizador(contexto, processo)
                && autorizado(() -> verificarPossibilidadeDeDevolverDgt(contexto, processo));
    }

    /**
     * Gravar o formulário e acrescentar documentos, as duas escritas que a PCM partilha com o autor. A PCM a quem o
     * processo está atribuído passa a Camada 2 em qualquer estado, para que fora de `VALIDADO_DGT` receba o 409 de
     * estado de quem chama e não 404; precisa da linha `PCM` na Camada 3 e não tem autoria (Camada 3b). Os outros
     * atores, e a PCM sem o processo atribuído, seguem as camadas de sempre.
     */
    public void verificarPermissaoDeEditar(ContextoUtilizador contexto, Processo processo) {
        if (correcaoPelaPcm(contexto, processo)) {
            verificarCamada3(contexto, processo.getInstrumentoProcedimentoConfig());
            return;
        }
        verificarPermissaoDeAgir(contexto, processo);
        verificarAutoria(contexto, processo);
    }

    /** A sessão é a PCM a quem o processo está atribuído: o estado exigido às escritas é então `VALIDADO_DGT`. */
    public boolean correcaoPelaPcm(ContextoUtilizador contexto, Processo processo) {
        return contexto.pcm() && atribuidoAoUtilizador(contexto, processo);
    }

    /** A PCM pode gravar o formulário e acrescentar documentos agora: atribuído, em `VALIDADO_DGT` e com a linha `PCM`. */
    public boolean pcmPodeCorrigir(ContextoUtilizador contexto, Processo processo) {
        return correcaoPelaPcm(contexto, processo) && processo.getEstado() == EstadoProcesso.VALIDADO_DGT
                && camada3Autoriza(contexto, processo.getInstrumentoProcedimentoConfig());
    }

    private static boolean atribuidoAoUtilizador(ContextoUtilizador contexto, Processo processo) {
        return processo.getPcmAtribuidoA() != null && processo.getPcmAtribuidoA().getId().equals(contexto.utilizadorId());
    }

    /**
     * O tipo da entidade da sessão, que decide a Camada 3. Vazio para utilizadores internos, que não criam
     * nem iniciam, e para sessões sem entidade ou sem tipo. Nunca se lê a entidade pelo NIF.
     */
    public Optional<TipoEntidade> tipoEntidadeDaSessao(ContextoUtilizador contexto) {
        if (contexto.perfilInterno() != null) {
            return Optional.empty();
        }
        return Optional.ofNullable(contexto.tipoEntidade());
    }

    /**
     * O tipo de entidade que decide o que a sessão pode criar ou iniciar: o da sessão, salvo a PCM. A linha `PCM`
     * dos Procedimentos de 1.ª série serve só para agir no processo que lhe foi atribuído (FR-153).
     */
    public Optional<TipoEntidade> tipoEntidadeQueCria(ContextoUtilizador contexto) {
        return tipoEntidadeDaSessao(contexto).filter(tipo -> tipo != TipoEntidade.PCM);
    }

    /**
     * A PCM nunca cria Instrumentos, Dinâmicas nem processos, mesmo com linha `PCM` na configuração. Não se aplica
     * a {@link #verificarPermissaoDeAgir}: é por aí que a PCM completa o processo que lhe foi atribuído.
     */
    public void recusarCriacaoPelaPcm(ContextoUtilizador contexto) {
        recusarCriacaoPelaPcm(contexto, "criar");
    }

    private void recusarCriacaoPelaPcm(ContextoUtilizador contexto, String operacao) {
        if (tipoEntidadeDaSessao(contexto).filter(tipo -> tipo == TipoEntidade.PCM).isPresent()) {
            log.warn("A PCM não cria | utilizadorId={} operacao={}", contexto.utilizadorId(), operacao);
            throw new AcaoNaoAutorizadaException(MSG_PCM_NAO_CRIA);
        }
    }

    /**
     * Os processos da Tentativa corrente da Dinâmica que impedem criar outro: fora de `PUBLICADO` e `ANULADO`
     * (`ANULADO_INCM` e `REJEITADO_DGT` contam, corrigem-se no mesmo processo) e sem os de Procedimentos
     * `ato_pontual`. Uma só consulta; quem avalia vários candidatos da mesma Dinâmica chama-a uma vez.
     */
    public List<Processo> processosEmCurso(Dinamica dinamica) {
        return processoRepository.findEmCursoDaTentativa(dinamica, dinamica.getTentativa(), EstadoProcesso.TERMINAIS);
    }

    /** Regra única de "um processo em curso por Dinâmica", partilhada pela criação e pelo `/disponivel`. */
    public boolean processoEmCursoBloqueia(List<Processo> processosEmCurso,
                                            InstrumentoProcedimentoConfig candidato) {
        return !processosEmCurso.isEmpty() && !candidato.isAtoPontual();
    }

    /**
     * Por omissão, sequenciamento por `ordem`. Uma {@code Regra} com `substituiSequenciamento` substitui-o por
     * inteiro: reproduzir o caminho normal cabe a quem configura a regra.
     * Não lança excepção porque também filtra listas de elegibilidade.
     */
    public boolean procedimentoDisponivel(Dinamica dinamica, InstrumentoProcedimentoConfig instrumentoProcedimentoConfig,
                                           List<Processo> processosEmCurso) {
        return !processoEmCursoBloqueia(processosEmCurso, instrumentoProcedimentoConfig)
                && sequenciamentoDisponivel(dinamica, instrumentoProcedimentoConfig)
                && elegivel(dinamica, instrumentoProcedimentoConfig);
    }

    /** Os atos pontuais não são Procedimentos da Dinâmica: ficam fora do sequenciamento (BL-PROC-003). */
    private boolean sequenciamentoDisponivel(Dinamica dinamica, InstrumentoProcedimentoConfig instrumentoProcedimentoConfig) {
        if (instrumentoProcedimentoConfig.isAtoPontual()) {
            return true;
        }
        boolean substituiSequenciamento = regraRepository.findByInstrumentoProcedimentoConfigAndAtivoTrue(instrumentoProcedimentoConfig)
                .map(Regra::isSubstituiSequenciamento)
                .orElse(false);

        return substituiSequenciamento || sequenciamentoCumprido(dinamica, instrumentoProcedimentoConfig);
    }

    private boolean elegivel(Dinamica dinamica, InstrumentoProcedimentoConfig instrumentoProcedimentoConfig) {
        return dinamica.instrumentoDeInicio()
                .map(instrumento -> motorRegrasService.avaliarElegibilidadeProcedimento(
                        instrumentoProcedimentoConfig, instrumento.getId()))
                .orElseGet(() -> motorRegrasService.avaliarElegibilidadeProcedimentoSemInstrumento(instrumentoProcedimentoConfig));
    }

    private boolean sequenciamentoCumprido(Dinamica dinamica, InstrumentoProcedimentoConfig instrumentoProcedimentoConfig) {
        return instrumentoProcedimentoConfigRepository
                .findByInstrumentoDinamicaConfigAndOrdemLessThanAndObrigatorioTrue(
                        instrumentoProcedimentoConfig.getInstrumentoDinamicaConfig(), instrumentoProcedimentoConfig.getOrdem())
                .stream()
                .allMatch(anterior -> processoRepository.existsByDinamicaAndTentativaAndInstrumentoProcedimentoConfigAndEstado(
                        dinamica, dinamica.getTentativa(), anterior, EstadoProcesso.PUBLICADO));
    }

    /**
     * Sem Camada 2: os Instrumentos são visíveis a todos os utilizadores autenticados.
     * A verificação de tipo replica a de {@code findByTipoInstrumentoAndAtivoTrue} nas dinâmicas disponíveis.
     */
    public void verificarPossibilidadeDeIniciarDinamica(ContextoUtilizador contexto, Instrumento instrumento,
                                                          InstrumentoDinamicaConfig instrumentoDinamicaConfig) {
        verificarPossibilidadeDeIniciarDinamica(contexto, instrumentoDinamicaConfig);

        boolean pertenceAoTipo = instrumentoDinamicaConfig.getTipoInstrumento().equals(instrumento.getTipoInstrumento());
        if (!pertenceAoTipo || !motorRegrasService.avaliarElegibilidadeDinamica(instrumentoDinamicaConfig, instrumento.getId())) {
            throw new DinamicaNaoElegivelException("Dinâmica indisponível — tipo não configurado para este Instrumento ou regra de elegibilidade não cumprida.");
        }
    }

    /**
     * A Camada 3 de iniciar uma Dinâmica, com ou sem Instrumento de partida: linha ativa do tipo de entidade da
     * sessão no Procedimento inicial da configuração.
     */
    public void verificarPossibilidadeDeIniciarDinamica(ContextoUtilizador contexto,
                                                          InstrumentoDinamicaConfig instrumentoDinamicaConfig) {
        recusarCriacaoPelaPcm(contexto, "iniciar dinamica");
        if (!podeIniciarDinamica(contexto, instrumentoDinamicaConfig)) {
            log.warn("Camada 3 recusou iniciar a dinâmica | utilizadorId={} dinamicaConfigId={}",
                    contexto.utilizadorId(), instrumentoDinamicaConfig.getId());
            throw new AcaoNaoAutorizadaException("Tipo da entidade da sessão sem permissão para iniciar esta Dinâmica.");
        }
    }

    /**
     * O Procedimento inicial de uma Dinâmica: o ativo de menor `ordem`, o que a inicia. Vazio quando a configuração
     * não tem Procedimentos ativos, e então ninguém a inicia.
     */
    public Optional<InstrumentoProcedimentoConfig> procedimentoInicial(InstrumentoDinamicaConfig instrumentoDinamicaConfig) {
        return instrumentoProcedimentoConfigRepository
                .findFirstByInstrumentoDinamicaConfigAndAtivoTrueOrderByOrdemAsc(instrumentoDinamicaConfig);
    }

    /**
     * Iniciar uma Dinâmica exige linha ativa do tipo de entidade da sessão no Procedimento inicial dela, e não
     * em qualquer Procedimento: quem só tem linha mais adiante (a CCDR na Constituição da CC) não a inicia.
     */
    public boolean podeIniciarDinamica(ContextoUtilizador contexto, InstrumentoDinamicaConfig instrumentoDinamicaConfig) {
        return tipoEntidadeQueCria(contexto)
                .flatMap(tipo -> procedimentoInicial(instrumentoDinamicaConfig)
                        .map(inicial -> permissaoAcaoRepository
                                .existsByTipoEntidadeAndInstrumentoProcedimentoConfigAndAtivoTrue(tipo, inicial)))
                .orElse(false);
    }

    /**
     * Criar um Instrumento exige que o tipo de entidade da sessão tenha linha ativa no Procedimento inicial de pelo
     * menos uma Dinâmica ativa do tipo de Instrumento: criar o Instrumento é o passo que antecede iniciá-la.
     */
    public void verificarPossibilidadeDeCriar(ContextoUtilizador contexto, TipoInstrumento tipoInstrumento) {
        recusarCriacaoPelaPcm(contexto, "criar instrumento");
        boolean autorizado = tipoEntidadeDaSessao(contexto)
                .map(tipo -> permissaoAcaoRepository.existeNoProcedimentoInicialDoTipo(tipo, tipoInstrumento))
                .orElse(false);
        if (!autorizado) {
            log.warn("Camada 3 recusou criar o instrumento | utilizadorId={} tipoInstrumento={}",
                    contexto.utilizadorId(), tipoInstrumento.getCodigo());
            throw new AcaoNaoAutorizadaException("Tipo da entidade da sessão sem permissão para o tipo de Instrumento.");
        }
    }

    /** Validar e enviar: `MASTER` e `TECNICO` resolvem para ator `DGT`; `VISITANTE` não tem ator. */
    public void verificarPossibilidadeDeAgirComoDgt(ContextoUtilizador contexto, Processo processo) {
        verificarVisibilidade(contexto, processo);
        if (!ATOR_DGT.equals(contexto.ator())) {
            throw new AcaoNaoAutorizadaException("Só a DGT (MASTER ou TECNICO) pode agir sobre esta tentativa.");
        }
    }

    /** «Enviar PCM»: como {@link #verificarPossibilidadeDeAgirComoDgt}, com o código próprio do fluxo PCM. */
    public void verificarPossibilidadeDeEnviarPcm(ContextoUtilizador contexto, Processo processo) {
        verificarVisibilidade(contexto, processo);
        if (!ATOR_DGT.equals(contexto.ator())) {
            throw new PcmAtorNaoAutorizadoException("Só a DGT (MASTER ou TECNICO) pode enviar processos à PCM.");
        }
    }

    /** O ator `DGT` não chega: também resolve do `TECNICO`, a quem a matriz de perfis não dá estas ações. */
    public void verificarPerfilMaster(ContextoUtilizador contexto) {
        if (contexto.perfilInterno() != PerfilInterno.MASTER) {
            throw new AcaoNaoAutorizadaException("Só MASTER pode executar esta ação.");
        }
    }

    public void verificarPossibilidadeDeAgirComoMaster(ContextoUtilizador contexto, Processo processo) {
        verificarVisibilidade(contexto, processo);
        verificarPerfilMaster(contexto);
    }

    public void verificarPossibilidadeDeEnviarIncm(ContextoUtilizador contexto, Processo processo) {
        verificarVisibilidade(contexto, processo);
        if (!ATOR_DGT.equals(contexto.ator())) {
            throw new AcaoNaoAutorizadaException("Só a DGT pode enviar esta tentativa à INCM.");
        }
    }

    /**
     * Para listar as ações disponíveis sem lançar: reutilizam as mesmas verificações dos endpoints, por isso
     * lista e endpoint não divergem. Ao contrário de {@link #verificarAutoria}, exige sempre ser o autor.
     */
    public boolean podeAgirComoAutor(ContextoUtilizador contexto, Processo processo) {
        return processo.getCriadoPor().getId().equals(contexto.utilizadorId())
                && autorizado(() -> verificarPermissaoDeAgir(contexto, processo));
    }

    public boolean podePublicar(ContextoUtilizador contexto, Processo processo) {
        return autorizado(() -> verificarAutorizacaoPublicacao(contexto, processo));
    }

    public boolean podeAgirComoDgt(ContextoUtilizador contexto, Processo processo) {
        return autorizado(() -> verificarPossibilidadeDeAgirComoDgt(contexto, processo));
    }

    public boolean podeAgirComoMaster(ContextoUtilizador contexto, Processo processo) {
        return autorizado(() -> verificarPossibilidadeDeAgirComoMaster(contexto, processo));
    }

    public boolean podeEnviarPcm(ContextoUtilizador contexto, Processo processo) {
        return autorizado(() -> verificarPossibilidadeDeEnviarPcm(contexto, processo));
    }

    public boolean podeEnviarIncm(ContextoUtilizador contexto, Processo processo) {
        return autorizado(() -> verificarPossibilidadeDeEnviarIncm(contexto, processo));
    }

    private static boolean autorizado(Runnable verificacao) {
        try {
            verificacao.run();
            return true;
        } catch (AcaoNaoAutorizadaException | RecursoNaoEncontradoException e) {
            return false;
        }
    }

    /**
     * Estão sujeitos, em qualquer estado do Processo, o ator `ENTIDADE` (salvo a entidade de tipo `DGT`) e o `PCM`,
     * que só fica isento no processo que lhe foi atribuído.
     */
    public void verificarAutoria(ContextoUtilizador contexto, Processo processo) {
        boolean sujeito = (ATOR_ENTIDADE.equals(contexto.ator()) && !contexto.entidadeDgt())
                || (ATOR_PCM.equals(contexto.ator()) && !pcmAtribuido(contexto, processo));
        if (!sujeito) {
            return;
        }
        if (!processo.getCriadoPor().getId().equals(contexto.utilizadorId())) {
            throw new AcaoNaoAutorizadaException("Só o autor da tentativa pode editá-la.");
        }
    }

    /**
     * Rede de segurança para ações que criam ou submetem Procedimentos a publicar; só para externos
     * (na DGT `atosAutorizados()` é nulo). A série é a efetiva do processo (a do Procedimento ou a escolhida), nunca de
     * `tipo_instrumento.categoria`: um Instrumento pode ter Procedimentos em séries diferentes. O tipo de ato
     * compara-se por identificador INCM (catálogos distintos por série); `SEGUNDA_H`/`SEGUNDA_C` exigem ainda
     * que `entidade.dr_parte.letra` coincida com a letra da série.
     */
    public void verificarAutorizacaoPublicacao(ContextoUtilizador contexto, Processo processo) {
        if (contexto.atosAutorizados() == null) {
            return;
        }

        String serieDr = processo.serieDrEfetiva();
        // Série ainda por escolher no processo: a submissão recusa-a nas pendências (BL-PROC-026).
        if (serieDr == null) {
            return;
        }

        TipoAto tipoAto = processo.getTipoAto();
        // Sem tipo de ato (o processo nasce sem ele) só a parte da adesão se verifica; a falta é da submissão (BL-PROC-026).
        if (tipoAto != null) {
            verificarTipoAtoAutorizado(contexto.atosAutorizados(), serieDr, tipoAto);
        }

        if (SERIE_DR_SEGUNDA_H.equals(serieDr) || SERIE_DR_SEGUNDA_C.equals(serieDr)) {
            String parteExigida = serieDr.substring(serieDr.indexOf('_') + 1);
            if (!parteExigida.equals(contexto.entidadeDrParteLetra())) {
                throw new ParteDrNaoAutorizadaException("Parte da adesão da entidade não coincide com a exigida pelo procedimento.");
            }
        }
    }

    private static void verificarTipoAtoAutorizado(ClaimsUtilizador.AtosAutorizados atosAutorizados, String serieDr,
                                                   TipoAto tipoAto) {
        boolean tipoAutorizado = switch (serieDr) {
            case SERIE_DR_PRIMEIRA -> atosAutorizados.serie1().contains(tipoAto.getIncmActTypeIdSerie1());
            case SERIE_DR_SEGUNDA_H, SERIE_DR_SEGUNDA_C -> atosAutorizados.serie2().stream()
                    .map(ClaimsUtilizador.AtoAutorizado::id)
                    .anyMatch(id -> id.equals(tipoAto.getIncmActTypeIdSerie2()));
            default -> throw new IllegalStateException(
                    "serie_dr não mapeado para a Camada 3c: " + serieDr);
        };
        if (!tipoAutorizado) {
            throw new TipoAtoNaoAutorizadoException("Tipo de ato não autorizado ao utilizador pela INCM.");
        }
    }
}
