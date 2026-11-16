package com.conkord.saigt.service.notificacao;

import com.conkord.saigt.configuration.NotificacoesAsyncConfig;
import com.conkord.saigt.configuration.NotificacoesEmailProperties;
import com.conkord.saigt.persistence.configuracao.EntidadeContacto;
import com.conkord.saigt.persistence.configuracao.EntidadeContactoRepository;
import com.conkord.saigt.persistence.operacional.Notificacao;
import com.conkord.saigt.persistence.operacional.NotificacaoRepository;
import com.conkord.saigt.persistence.operacional.Processo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Cópia de cortesia por email, depois do commit e fora da thread do pedido: a notificação é a via autoritativa e o
 * email nunca a bloqueia nem a reverte. Corre na instância que fez o commit, sem coordenação entre instâncias.
 * Os logs levam só ids e contagens: nunca endereços, títulos, textos nem nomes.
 */
@Component
public class CopiaEmailNotificacaoListener {

    private static final Logger log = LoggerFactory.getLogger(CopiaEmailNotificacaoListener.class);

    private final NotificacaoRepository notificacaoRepository;
    private final EntidadeContactoRepository entidadeContactoRepository;
    private final EnvioEmail envioEmail;
    private final NotificacoesEmailProperties propriedades;
    private final TransactionTemplate transacao;
    private final Clock relogio;

    public CopiaEmailNotificacaoListener(NotificacaoRepository notificacaoRepository,
                                         EntidadeContactoRepository entidadeContactoRepository,
                                         EnvioEmail envioEmail, NotificacoesEmailProperties propriedades,
                                         PlatformTransactionManager transactionManager, Clock relogio) {
        this.notificacaoRepository = notificacaoRepository;
        this.entidadeContactoRepository = entidadeContactoRepository;
        this.envioEmail = envioEmail;
        this.propriedades = propriedades;
        this.transacao = new TransactionTemplate(transactionManager);
        this.transacao.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.relogio = relogio;
    }

    @Async(NotificacoesAsyncConfig.EXECUTOR_EMAIL)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void aoNotificacaoCriada(NotificacaoCriadaEvento evento) {
        if (!propriedades.ativo()) {
            log.debug("Cópia por email desligada | notificacaoIds={}", evento.notificacaoIds());
            return;
        }
        try {
            // As colunas registam a tentativa: ficam confirmadas antes do envio, mesmo que o relay falhe.
            List<Envio> envios = transacao.execute(estado -> prepararEnvios(evento));
            enviar(evento, envios == null ? List.of() : envios);
        } catch (RuntimeException e) {
            log.warn("Falha na cópia por email | notificacaoIds={} loteId={} erro={}", evento.notificacaoIds(),
                    evento.loteId(), e.getClass().getName());
        }
    }

    private List<Envio> prepararEnvios(NotificacaoCriadaEvento evento) {
        List<Notificacao> linhas = linhasPorOrdem(evento);
        if (linhas.isEmpty()) {
            return List.of();
        }
        Instant agora = Instant.now(relogio);
        if (evento.emailContactoTramitacao()) {
            return envioAoContactoDeTramitacao(evento, linhas, agora);
        }
        Map<Long, String> contactosPrincipais = contactosPrincipais(linhas);
        Map<String, Envio> porEndereco = new LinkedHashMap<>();
        for (Notificacao linha : linhas) {
            String endereco = endereco(linha, contactosPrincipais);
            if (endereco == null) {
                continue;
            }
            marcar(linha, agora);
            porEndereco.putIfAbsent(endereco.toLowerCase(Locale.ROOT), Envio.de(endereco, linha));
        }
        if (porEndereco.isEmpty()) {
            log.debug("Cópia por email sem destinatário de email | notificacaoIds={}", evento.notificacaoIds());
        }
        return List.copyOf(porEndereco.values());
    }

    private List<Envio> envioAoContactoDeTramitacao(NotificacaoCriadaEvento evento, List<Notificacao> linhas,
                                                    Instant agora) {
        Processo processo = linhas.stream().map(Notificacao::getProcesso).filter(Objects::nonNull).findFirst()
                .orElse(null);
        String contacto = processo == null ? null : semVazio(processo.getContactoTramitacao());
        if (contacto == null) {
            log.info("Cópia por email sem contacto de tramitação | processoId={} notificacaoIds={}",
                    processo == null ? null : processo.getId(), evento.notificacaoIds());
            return List.of();
        }
        linhas.forEach(linha -> marcar(linha, agora));
        return List.of(Envio.de(contacto, linhas.get(0)));
    }

    private List<Notificacao> linhasPorOrdem(NotificacaoCriadaEvento evento) {
        List<UUID> ids = evento.notificacaoIds();
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return notificacaoRepository.findComDestinosByIdIn(ids).stream()
                .sorted(Comparator.comparingInt(linha -> ids.indexOf(linha.getId())))
                .toList();
    }

    /** Só o contacto principal ativo: nem o email do responsável da entidade, nem o geral, nem o dos utilizadores. */
    private Map<Long, String> contactosPrincipais(List<Notificacao> linhas) {
        List<Long> entidadeIds = linhas.stream()
                .filter(linha -> linha.getUtilizador() == null && linha.getEntidade() != null)
                .map(linha -> linha.getEntidade().getId()).distinct().toList();
        if (entidadeIds.isEmpty()) {
            return Map.of();
        }
        return entidadeContactoRepository.findAllByEntidadeIdInAndPrincipalTrueAndAtivoTrue(entidadeIds).stream()
                .collect(Collectors.toMap(contacto -> contacto.getEntidade().getId(), EntidadeContacto::getEmail,
                        (primeiro, segundo) -> primeiro));
    }

    /** Linhas sem destino (todos) ou só com `perfil_destino` não têm regra de endereço: ficam sem email. */
    private static String endereco(Notificacao linha, Map<Long, String> contactosPrincipais) {
        if (linha.getUtilizador() != null) {
            return semVazio(linha.getUtilizador().getEmail());
        }
        if (linha.getEntidade() != null) {
            return semVazio(contactosPrincipais.get(linha.getEntidade().getId()));
        }
        return null;
    }

    private static void marcar(Notificacao linha, Instant agora) {
        linha.setCanalEmail(true);
        linha.setEmailEnviadoEm(agora);
    }

    private void enviar(NotificacaoCriadaEvento evento, List<Envio> envios) {
        int enviados = 0;
        for (Envio envio : envios) {
            try {
                envioEmail.enviar(envio.endereco(), envio.titulo(), envio.texto());
                enviados++;
            } catch (RuntimeException e) {
                // Sem repetição: a política de repetição não está definida. A notificação continua criada e visível.
                log.warn("Falha no envio da cópia por email | notificacaoId={} loteId={} erro={}",
                        envio.notificacaoId(), evento.loteId(), e.getClass().getName());
            }
        }
        if (enviados > 0) {
            log.info("Cópia por email enviada | notificacaoIds={} enderecos={}", evento.notificacaoIds(), enviados);
        }
    }

    private static String semVazio(String valor) {
        return valor == null || valor.isBlank() ? null : valor.strip();
    }

    private record Envio(String endereco, UUID notificacaoId, String titulo, String texto) {

        static Envio de(String endereco, Notificacao linha) {
            return new Envio(endereco, linha.getId(), linha.getTitulo(), linha.getTexto());
        }
    }
}
