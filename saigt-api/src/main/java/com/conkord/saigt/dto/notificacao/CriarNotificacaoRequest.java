package com.conkord.saigt.dto.notificacao;

import com.conkord.saigt.persistence.operacional.TipoNotificacao;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record CriarNotificacaoRequest(
        @NotNull(message = "Obrigatório.") TipoNotificacao tipo,
        @NotBlank(message = "Obrigatório.") @Size(max = 200, message = "Máximo 200 caracteres.") String titulo,
        @NotBlank(message = "Obrigatório.") @Size(max = 5000, message = "Máximo 5000 caracteres.") String texto,
        UUID processoId,
        @Size(max = 50, message = "Máximo 50 caracteres.") String idIgtSnit,
        @Size(max = 500, message = "Máximo 500 caracteres.") String descricaoIgt,
        OffsetDateTime prazo,
        List<DestinatarioNotificacaoRequest> destinatarios) {

    public List<DestinatarioNotificacaoRequest> destinatariosOuVazio() {
        return destinatarios == null ? List.of() : destinatarios;
    }
}
