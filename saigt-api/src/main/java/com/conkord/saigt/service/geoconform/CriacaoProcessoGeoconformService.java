package com.conkord.saigt.service.geoconform;

import com.conkord.saigt.integracao.core.ReferenciaChamada;
import com.conkord.saigt.integracao.geoconform.ClienteGeoconform;
import com.conkord.saigt.integracao.geoconform.EntidadeGeoconform;
import com.conkord.saigt.integracao.geoconform.PedidoCriacaoProcessoGeoconform;
import com.conkord.saigt.integracao.geoconform.ProcessoGeoconform;
import com.conkord.saigt.integracao.geoconform.ResultadoGeoconform;
import com.conkord.saigt.persistence.core.Entidade;
import com.conkord.saigt.persistence.operacional.Dinamica;
import com.conkord.saigt.persistence.operacional.DinamicaRepository;
import com.conkord.saigt.persistence.operacional.EstadoProcesso;
import com.conkord.saigt.persistence.operacional.IdusProcesso;
import com.conkord.saigt.persistence.operacional.IdusProcessoRepository;
import com.conkord.saigt.persistence.operacional.Processo;
import com.conkord.saigt.persistence.operacional.ProcessoRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Cria no GeoConform o processo «adormecido» e a ficha de dados estatísticos vazia de uma Decisão de início publicada
 * numa Dinâmica com GeoConform. Corre depois do commit da publicação e fora de qualquer transação dela: os dados leem-se
 * numa transação curta, a chamada corre fora e o URL de acesso grava-se com commit próprio. Uma falha fica no log e não
 * desfaz a publicação; o reenvio é manual.
 */
@Slf4j
@Service
public class CriacaoProcessoGeoconformService {

    static final String DECISAO_INICIO = "DECISAO_INICIO";
    private static final Set<String> ESQUEMAS_URL = Set.of("http", "https");
    private static final int MAXIMO_URL = 2048;

    private final ProcessoRepository processoRepository;
    private final IdusProcessoRepository idusProcessoRepository;
    private final DinamicaRepository dinamicaRepository;
    private final ClienteGeoconform clienteGeoconform;
    private final TransactionTemplate transacaoLeitura;
    private final TransactionTemplate transacaoEscrita;

    public CriacaoProcessoGeoconformService(ProcessoRepository processoRepository,
                                            IdusProcessoRepository idusProcessoRepository,
                                            DinamicaRepository dinamicaRepository, ClienteGeoconform clienteGeoconform,
                                            PlatformTransactionManager transactionManager) {
        this.processoRepository = processoRepository;
        this.idusProcessoRepository = idusProcessoRepository;
        this.dinamicaRepository = dinamicaRepository;
        this.clienteGeoconform = clienteGeoconform;
        // Chamado no afterCommit da publicação: sem REQUIRES_NEW juntar-se-ia à transação já confirmada.
        this.transacaoLeitura = new TransactionTemplate(transactionManager);
        this.transacaoLeitura.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.transacaoLeitura.setReadOnly(true);
        this.transacaoEscrita = new TransactionTemplate(transactionManager);
        this.transacaoEscrita.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    record DadosCriacao(UUID dinamicaId, PedidoCriacaoProcessoGeoconform pedido) {
    }

    /** Nada acontece fora de uma Decisão de início `PUBLICADO` com `dinamica.com_geoconform` verdadeiro. */
    public void criarSeAplicavel(UUID processoId) {
        Objects.requireNonNull(processoId, "processoId");
        Optional<DadosCriacao> preparados = transacaoLeitura.execute(estado -> preparar(processoId));
        if (preparados == null || preparados.isEmpty()) {
            return;
        }
        DadosCriacao dados = preparados.get();
        String idus = dados.pedido().idus();
        ResultadoGeoconform<ProcessoGeoconform> resultado = clienteGeoconform.criarProcesso(dados.pedido(),
                new ReferenciaChamada(processoId, idus, null));
        if (!resultado.criado()) {
            log.error("Criação do processo no GeoConform falhou, sem nova tentativa — processoId={} idus={} falha={} "
                            + "httpStatus={} codigo={}", processoId, idus, resultado.chamada().falhaTecnica(),
                    resultado.chamada().httpStatus(), resultado.codigoErro());
            return;
        }
        boolean urlGravado = resultado.sucesso() && gravarUrl(dados.dinamicaId(), resultado.corpo(), processoId);
        log.info("Processo criado no GeoConform — processoId={} idus={} location={} httpStatus={} urlAcessoGravado={}",
                processoId, idus, resultado.location(), resultado.chamada().httpStatus(), urlGravado);
    }

    private Optional<DadosCriacao> preparar(UUID processoId) {
        Processo processo = processoRepository.findById(processoId).orElse(null);
        if (processo == null || processo.getEstado() != EstadoProcesso.PUBLICADO
                || !DECISAO_INICIO.equals(processo.getInstrumentoProcedimentoConfig().getTipoProcedimento().getCodigo())) {
            return Optional.empty();
        }
        Dinamica dinamica = processo.getDinamica();
        if (!Boolean.TRUE.equals(dinamica.getComGeoconform())) {
            return Optional.empty();
        }
        String idus = idusProcessoRepository.findById(processoId).map(IdusProcesso::getIdus)
                .filter(valor -> !valor.isBlank()).orElse(null);
        if (idus == null) {
            log.error("Criação do processo no GeoConform não feita, processo sem IDUS — processoId={}", processoId);
            return Optional.empty();
        }
        Entidade entidade = processo.getEntidade();
        EntidadeGeoconform entidadeGeoconform = entidade == null ? null
                : new EntidadeGeoconform(entidade.getDescricaoSaigt(), vazioComoNulo(entidade.getNipc()));
        // A ficha não se aplica à REN, mas a opção GeoConform só existe no PDM.
        PedidoCriacaoProcessoGeoconform pedido = new PedidoCriacaoProcessoGeoconform(idus,
                processo.getNumeroProcesso(), dinamica.designacao(), dinamica.getDescritivo(), entidadeGeoconform, true);
        return Optional.of(new DadosCriacao(dinamica.getId(), pedido));
    }

    /** O URL vai mais tarde ao utilizador: só se grava um URL http(s) absoluto. */
    private boolean gravarUrl(UUID dinamicaId, ProcessoGeoconform processo, UUID processoId) {
        String url = processo == null ? null : processo.urlAcesso();
        if (!urlAceitavel(url)) {
            log.warn("Resposta da criação no GeoConform sem urlAcesso http(s) válido, não gravado — processoId={}",
                    processoId);
            return false;
        }
        transacaoEscrita.executeWithoutResult(estado -> dinamicaRepository.findById(dinamicaId)
                .ifPresent(dinamica -> dinamica.setUrlGeoconform(url)));
        return true;
    }

    static boolean urlAceitavel(String url) {
        if (url == null || url.isBlank() || url.length() > MAXIMO_URL) {
            return false;
        }
        try {
            URI uri = new URI(url);
            return uri.isAbsolute() && uri.getScheme() != null
                    && ESQUEMAS_URL.contains(uri.getScheme().toLowerCase(Locale.ROOT)) && uri.getHost() != null;
        } catch (URISyntaxException e) {
            return false;
        }
    }

    private static String vazioComoNulo(String valor) {
        return valor == null || valor.isBlank() ? null : valor;
    }
}
