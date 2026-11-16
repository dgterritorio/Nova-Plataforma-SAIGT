package com.conkord.saigt.service.notificacao;

import com.conkord.saigt.dto.notificacao.CriarNotificacaoRequest;
import com.conkord.saigt.dto.notificacao.CriarNotificacaoResponse;
import com.conkord.saigt.dto.notificacao.DestinatarioNotificacaoRequest;
import com.conkord.saigt.dto.notificacao.NotificacaoResumoResponse;
import com.conkord.saigt.exception.NotificacaoDestinatarioInexistenteException;
import com.conkord.saigt.exception.NotificacaoDestinatariosInvalidosException;
import com.conkord.saigt.exception.NotificacaoPerfilNaoAutorizadoException;
import com.conkord.saigt.exception.NotificacaoPrazoNaoAplicavelException;
import com.conkord.saigt.exception.NotificacaoTipoNaoCriavelException;
import com.conkord.saigt.exception.ValidacaoException;
import com.conkord.saigt.exception.ValidacaoException.CampoErro;
import com.conkord.saigt.persistence.core.Entidade;
import com.conkord.saigt.persistence.core.EntidadeRepository;
import com.conkord.saigt.persistence.operacional.Notificacao;
import com.conkord.saigt.persistence.operacional.NotificacaoRepository;
import com.conkord.saigt.persistence.operacional.PerfilInterno;
import com.conkord.saigt.persistence.operacional.Processo;
import com.conkord.saigt.persistence.operacional.ProcessoRepository;
import com.conkord.saigt.persistence.operacional.TipoNotificacao;
import com.conkord.saigt.persistence.operacional.Utilizador;
import com.conkord.saigt.persistence.operacional.UtilizadorRepository;
import com.conkord.saigt.service.nucleo.AutorizacaoService;
import com.conkord.saigt.service.nucleo.ContextoUtilizador;
import com.conkord.saigt.service.nucleo.ContextoUtilizadorResolver;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class NotificacaoCriacaoService {

    private static final Logger log = LoggerFactory.getLogger(NotificacaoCriacaoService.class);

    private static final Set<TipoNotificacao> TIPOS_NAO_CRIAVEIS =
            Set.of(TipoNotificacao.TEMPORARIA, TipoNotificacao.ALERTA);
    /** Criados pela entidade e dirigidos sempre aos Master, sem destinatários escolhidos. */
    private static final Set<TipoNotificacao> TIPOS_DA_ENTIDADE =
            Set.of(TipoNotificacao.PEDIDO_APOIO, TipoNotificacao.REGULARIZACAO_PROCEDIMENTO);

    private final NotificacaoRepository notificacaoRepository;
    private final EntidadeRepository entidadeRepository;
    private final UtilizadorRepository utilizadorRepository;
    private final ProcessoRepository processoRepository;
    private final ContextoUtilizadorResolver contextoUtilizadorResolver;
    private final AutorizacaoService autorizacaoService;
    private final Validator validator;
    private final ApplicationEventPublisher publicador;

    public NotificacaoCriacaoService(NotificacaoRepository notificacaoRepository,
                                     EntidadeRepository entidadeRepository,
                                     UtilizadorRepository utilizadorRepository,
                                     ProcessoRepository processoRepository,
                                     ContextoUtilizadorResolver contextoUtilizadorResolver,
                                     AutorizacaoService autorizacaoService, Validator validator,
                                     ApplicationEventPublisher publicador) {
        this.notificacaoRepository = notificacaoRepository;
        this.entidadeRepository = entidadeRepository;
        this.utilizadorRepository = utilizadorRepository;
        this.processoRepository = processoRepository;
        this.contextoUtilizadorResolver = contextoUtilizadorResolver;
        this.autorizacaoService = autorizacaoService;
        this.validator = validator;
        this.publicador = publicador;
    }

    @Transactional
    public CriarNotificacaoResponse criar(CriarNotificacaoRequest pedido) {
        if (TIPOS_NAO_CRIAVEIS.contains(pedido.tipo())) {
            throw new NotificacaoTipoNaoCriavelException();
        }
        ContextoUtilizador contexto = contextoUtilizadorResolver.resolver();
        if (TIPOS_DA_ENTIDADE.contains(pedido.tipo())) {
            return criarPedidoDaEntidade(pedido, contexto);
        }
        if (contexto.perfilInterno() != PerfilInterno.MASTER) {
            throw new NotificacaoPerfilNaoAutorizadoException("Só um utilizador Master pode criar esta notificação.");
        }
        validarFormato(pedido);
        List<DestinatarioNotificacaoRequest> destinatarios = destinatariosValidos(pedido);

        Map<Long, Entidade> entidades = entidades(destinatarios);
        Map<UUID, Utilizador> utilizadores = utilizadores(destinatarios);
        Processo processo = pedido.processoId() == null ? null
                : processoRepository.findById(pedido.processoId())
                        .orElseThrow(NotificacaoDestinatarioInexistenteException::new);

        Utilizador autor = utilizadorRepository.getReferenceById(contexto.utilizadorId());
        UUID loteId = destinatarios.size() > 1 ? UUID.randomUUID() : null;
        List<Notificacao> linhas = new ArrayList<>();
        if (destinatarios.isEmpty()) {
            linhas.add(nova(pedido, autor, processo, loteId));
        }
        for (DestinatarioNotificacaoRequest destinatario : destinatarios) {
            Notificacao linha = nova(pedido, autor, processo, loteId);
            linha.setEntidade(destinatario.entidadeId() == null ? null : entidades.get(destinatario.entidadeId()));
            linha.setUtilizador(destinatario.utilizadorId() == null ? null
                    : utilizadores.get(destinatario.utilizadorId()));
            linhas.add(linha);
        }
        List<Notificacao> criadas = notificacaoRepository.saveAll(linhas);

        publicador.publishEvent(new NotificacaoCriadaEvento(criadas.stream().map(Notificacao::getId).toList(), loteId,
                false));
        log.info("Notificação criada: tipo={}, linhas={}, loteId={}, utilizadorId={}", pedido.tipo(), criadas.size(),
                loteId, contexto.utilizadorId());
        return new CriarNotificacaoResponse(criadas.stream()
                .map(notificacao -> NotificacaoResumoResponse.de(notificacao, 0, 0)).toList(), loteId);
    }

    /**
     * Pedido de apoio e regularização: só o utilizador externo os cria, para os Master. O processo, quando vem, tem
     * de ser visível à sessão, com o mesmo 404 para o inexistente e para o de outra entidade.
     */
    private CriarNotificacaoResponse criarPedidoDaEntidade(CriarNotificacaoRequest pedido,
                                                           ContextoUtilizador contexto) {
        if (contexto.perfilInterno() != null) {
            throw new NotificacaoPerfilNaoAutorizadoException("Este tipo de notificação é criado pela entidade.");
        }
        validarFormato(pedido);
        if (pedido.prazo() != null) {
            throw new NotificacaoPrazoNaoAplicavelException();
        }
        if (!pedido.destinatariosOuVazio().isEmpty()) {
            throw new NotificacaoDestinatariosInvalidosException(
                    "Este tipo de notificação não aceita destinatários: segue sempre para os Master.");
        }
        if (pedido.tipo() == TipoNotificacao.REGULARIZACAO_PROCEDIMENTO && semTexto(pedido.descricaoIgt())) {
            throw new ValidacaoException("Payload inválido.", List.of(new CampoErro("descricaoIgt", "Obrigatório.")));
        }
        Processo processo = pedido.processoId() == null ? null
                : processoRepository.findById(pedido.processoId())
                        .filter(encontrado -> autorizacaoService.processoVisivel(contexto, encontrado))
                        .orElseThrow(NotificacaoDestinatarioInexistenteException::new);

        Notificacao linha = nova(pedido, utilizadorRepository.getReferenceById(contexto.utilizadorId()), processo,
                null);
        linha.setIdIgtSnit(semTexto(pedido.idIgtSnit()) ? null : pedido.idIgtSnit().strip());
        linha.setDescricaoIgt(semTexto(pedido.descricaoIgt()) ? null : pedido.descricaoIgt().strip());
        linha.setPerfilDestino(PerfilInterno.MASTER);
        List<Notificacao> criadas = notificacaoRepository.saveAll(List.of(linha));

        publicador.publishEvent(new NotificacaoCriadaEvento(criadas.stream().map(Notificacao::getId).toList(), null,
                false));
        log.info("Notificação criada pela entidade: tipo={}, notificacaoId={}, utilizadorId={}", pedido.tipo(),
                criadas.get(0).getId(), contexto.utilizadorId());
        return new CriarNotificacaoResponse(criadas.stream()
                .map(notificacao -> NotificacaoResumoResponse.de(notificacao, 0, 0)).toList(), null);
    }

    private static boolean semTexto(String valor) {
        return valor == null || valor.isBlank();
    }

    private void validarFormato(CriarNotificacaoRequest pedido) {
        List<CampoErro> erros = validator.validate(pedido).stream()
                .sorted(Comparator.comparing((ConstraintViolation<CriarNotificacaoRequest> violacao) ->
                        violacao.getPropertyPath().toString()))
                .map(violacao -> new CampoErro(violacao.getPropertyPath().toString(), violacao.getMessage()))
                .toList();
        if (!erros.isEmpty()) {
            throw new ValidacaoException("Payload inválido.", erros);
        }
    }

    private static List<DestinatarioNotificacaoRequest> destinatariosValidos(CriarNotificacaoRequest pedido) {
        if (pedido.destinatariosOuVazio().stream().anyMatch(d -> d == null || !d.temExatamenteUmDestino())) {
            throw new NotificacaoDestinatariosInvalidosException(
                    "Cada destinatário indica uma entidade ou um utilizador, e só um.");
        }
        List<DestinatarioNotificacaoRequest> unicos = List.copyOf(new LinkedHashSet<>(pedido.destinatariosOuVazio()));
        if (pedido.tipo() == TipoNotificacao.RESPOSTA_OBRIGATORIA && unicos.size() != 1) {
            throw new NotificacaoDestinatariosInvalidosException(
                    "A resposta obrigatória tem exatamente um destinatário.");
        }
        return unicos;
    }

    private Map<Long, Entidade> entidades(List<DestinatarioNotificacaoRequest> destinatarios) {
        Set<Long> ids = destinatarios.stream().map(DestinatarioNotificacaoRequest::entidadeId)
                .filter(id -> id != null).collect(Collectors.toSet());
        Map<Long, Entidade> encontradas = ids.isEmpty() ? Map.of() : entidadeRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(Entidade::getId, Function.identity()));
        if (encontradas.size() != ids.size()) {
            throw new NotificacaoDestinatarioInexistenteException();
        }
        return encontradas;
    }

    private Map<UUID, Utilizador> utilizadores(List<DestinatarioNotificacaoRequest> destinatarios) {
        Set<UUID> ids = destinatarios.stream().map(DestinatarioNotificacaoRequest::utilizadorId)
                .filter(id -> id != null).collect(Collectors.toSet());
        Map<UUID, Utilizador> encontrados = ids.isEmpty() ? Map.of() : utilizadorRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(Utilizador::getId, Function.identity()));
        if (encontrados.size() != ids.size()) {
            throw new NotificacaoDestinatarioInexistenteException();
        }
        return encontrados;
    }

    private static Notificacao nova(CriarNotificacaoRequest pedido, Utilizador autor, Processo processo,
                                    UUID loteId) {
        Notificacao notificacao = new Notificacao(pedido.tipo(), pedido.titulo().strip(), pedido.texto().strip(),
                autor);
        notificacao.setProcesso(processo);
        notificacao.setIdIgtSnit(pedido.idIgtSnit());
        notificacao.setDescricaoIgt(pedido.descricaoIgt());
        notificacao.setPrazo(pedido.prazo() == null ? null : pedido.prazo().toInstant());
        notificacao.setLoteId(loteId);
        return notificacao;
    }
}
