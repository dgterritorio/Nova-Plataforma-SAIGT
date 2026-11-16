package com.conkord.saigt.service.notificacao;

import com.conkord.saigt.persistence.operacional.EstadoNotificacao;
import com.conkord.saigt.persistence.operacional.Notificacao;
import com.conkord.saigt.persistence.operacional.PerfilInterno;
import com.conkord.saigt.persistence.operacional.TipoNotificacao;
import com.conkord.saigt.service.nucleo.ContextoUtilizador;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Component
public class NotificacaoVisibilidade {

    public Specification<Notificacao> visiveisPara(ContextoUtilizador contexto) {
        UUID utilizadorId = contexto.utilizadorId();
        Long entidadeId = contexto.perfilInterno() == null ? contexto.entidadeId() : null;
        boolean master = contexto.perfilInterno() == PerfilInterno.MASTER;
        return (root, query, cb) -> {
            List<Predicate> condicoes = new ArrayList<>();
            condicoes.add(cb.equal(root.get("utilizador").get("id"), utilizadorId));
            if (entidadeId != null) {
                condicoes.add(cb.equal(root.get("entidade").get("id"), entidadeId));
            }
            if (master) {
                condicoes.add(cb.equal(root.get("perfilDestino"), PerfilInterno.MASTER));
            }
            condicoes.add(cb.and(root.get("utilizador").isNull(), root.get("entidade").isNull(),
                    root.get("perfilDestino").isNull()));
            condicoes.add(cb.equal(root.get("criadoPor").get("id"), utilizadorId));
            return cb.or(condicoes.toArray(new Predicate[0]));
        };
    }

    /**
     * A Notícia deixa de ser apresentada no fim de vigência, calculado na leitura e sem job. O `isNotNull` evita
     * que um `prazo` nulo torne a negação desconhecida em SQL e esconda a linha.
     */
    public Specification<Notificacao> emVigencia(Instant agora) {
        return (root, query, cb) -> cb.not(cb.and(
                cb.equal(root.get("tipo"), TipoNotificacao.ESTATICA),
                cb.equal(root.get("estado"), EstadoNotificacao.ATIVA),
                cb.isNotNull(root.get("prazo")),
                cb.lessThan(root.get("prazo"), agora)));
    }
}
