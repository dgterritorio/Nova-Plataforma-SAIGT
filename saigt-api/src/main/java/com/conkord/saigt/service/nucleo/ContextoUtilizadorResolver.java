package com.conkord.saigt.service.nucleo;

import com.conkord.saigt.persistence.core.TipoEntidade;
import com.conkord.saigt.persistence.operacional.PerfilInterno;
import com.conkord.saigt.service.core.ClaimsUtilizador;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Única fonte do {@link ContextoUtilizador}: lê só o access token validado e resolve o `ator`, que não vem no token. */
@Component
public class ContextoUtilizadorResolver {

    private static final String ATOR_ENTIDADE = "ENTIDADE";
    private static final String ATOR_PCM = "PCM";
    private static final String ATOR_DGT = "DGT";
    private static final String CLAIM_DR_PARTE = "dr_parte";

    public ContextoUtilizador resolver() {
        Jwt jwt = (Jwt) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        UUID utilizadorId = UUID.fromString(jwt.getSubject());
        String origem = jwt.getClaimAsString("origem");
        PerfilInterno perfilInterno = PerfilInterno.deTexto(jwt.getClaimAsString("perfil_interno")).orElse(null);

        if (jwt.hasClaim("entidade")) {
            Map<String, Object> entidade = jwt.getClaim("entidade");
            String nif = extrairNipc(entidade);
            boolean pcm = Boolean.TRUE.equals(entidade.get("pcm"));
            String ator = pcm ? ATOR_PCM : ATOR_ENTIDADE;
            return new ContextoUtilizador(utilizadorId, origem, null, nif, ator, extrairAtosAutorizados(entidade),
                    extrairDrParteLetra(entidade), extrairIncmEnrollmentId(entidade), extrairDrParteId(entidade),
                    extrairTipoEntidade(entidade), extrairEntidadeId(entidade));
        }

        String ator = perfilInterno == PerfilInterno.MASTER || perfilInterno == PerfilInterno.TECNICO ? ATOR_DGT : null;
        return new ContextoUtilizador(utilizadorId, origem, perfilInterno, null, ator, null, null, null, null, null, null);
    }

    /**
     * Identificação da entidade da sessão como o token a traz (`codigo`, `nome`, `tipo_entidade`), para o Perfil;
     * vazio nos internos. Não decide autorização: para isso há o {@link ContextoUtilizador}.
     */
    public Optional<EntidadeDoToken> entidadeDoToken() {
        Jwt jwt = (Jwt) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (!jwt.hasClaim("entidade")) {
            return Optional.empty();
        }
        Map<String, Object> entidade = jwt.getClaim("entidade");
        return Optional.of(new EntidadeDoToken(textoOuNulo(entidade.get("codigo")), textoOuNulo(entidade.get("nome")),
                textoOuNulo(entidade.get("tipo_entidade"))));
    }

    public record EntidadeDoToken(String codigo, String nome, String tipoEntidade) {
    }

    private static String textoOuNulo(Object valor) {
        return valor instanceof String texto ? texto : null;
    }

    /** Nulo quando a claim falta ou não é um número: o que depende da entidade da sessão recusa, nunca lança. */
    private Long extrairEntidadeId(Map<String, Object> entidade) {
        return entidade.get("id") instanceof Number id ? Long.valueOf(id.longValue()) : null;
    }

    /** Nulo quando a claim falta ou o valor não é um {@link TipoEntidade}: a Camada 3 recusa, nunca lança. */
    private TipoEntidade extrairTipoEntidade(Map<String, Object> entidade) {
        if (!(entidade.get("tipo_entidade") instanceof String valor)) {
            return null;
        }
        try {
            return TipoEntidade.valueOf(valor);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** `nipc` (OI-494); `nif` só para tokens emitidos antes da troca, que vivem no máximo 15 min. */
    private String extrairNipc(Map<String, Object> entidade) {
        Object nipc = entidade.get("nipc");
        return (String) (nipc != null ? nipc : entidade.get("nif"));
    }

    /** Nulo quando a entidade não tem parte de 2.ª série. */
    @SuppressWarnings("unchecked")
    private String extrairDrParteLetra(Map<String, Object> entidade) {
        if (!entidade.containsKey(CLAIM_DR_PARTE)) {
            return null;
        }
        Map<String, Object> drParte = (Map<String, Object>) entidade.get(CLAIM_DR_PARTE);
        return (String) drParte.get("letra");
    }

    /** Opcional: ausente se a INCM não o devolver. */
    private String extrairIncmEnrollmentId(Map<String, Object> entidade) {
        return (String) entidade.get("incm_enrollment_id");
    }

    /** Nulo quando a entidade não tem parte de 2.ª série. */
    @SuppressWarnings("unchecked")
    private Long extrairDrParteId(Map<String, Object> entidade) {
        if (!entidade.containsKey(CLAIM_DR_PARTE)) {
            return null;
        }
        Map<String, Object> drParte = (Map<String, Object>) entidade.get(CLAIM_DR_PARTE);
        Number id = (Number) drParte.get("id");
        return id == null ? null : id.longValue();
    }

    /** Opcional: ausente se a INCM não o devolver. */
    @SuppressWarnings("unchecked")
    private ClaimsUtilizador.AtosAutorizados extrairAtosAutorizados(Map<String, Object> entidade) {
        if (!entidade.containsKey("atos_autorizados")) {
            return null;
        }
        Map<String, Object> raw = (Map<String, Object>) entidade.get("atos_autorizados");

        List<Long> serie1 = ((List<?>) raw.get("serie_1")).stream()
                .map(valor -> ((Number) valor).longValue())
                .toList();
        List<ClaimsUtilizador.AtoAutorizado> serie2 = ((List<?>) raw.get("serie_2")).stream()
                .map(item -> (Map<?, ?>) item)
                .map(item -> new ClaimsUtilizador.AtoAutorizado(((Number) item.get("id")).longValue(),
                        (String) item.get("nome"), (Boolean) item.get("extrato")))
                .toList();

        return new ClaimsUtilizador.AtosAutorizados(serie1, serie2);
    }
}
