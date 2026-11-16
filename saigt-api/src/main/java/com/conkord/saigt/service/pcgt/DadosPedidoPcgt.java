package com.conkord.saigt.service.pcgt;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * O que se envia à PCGT sobre um processo publicado, lido numa transação curta: os pedidos montam-se depois, fora
 * dela, com os URLs dos links públicos. Um campo nulo não vai no JSON.
 */
record DadosPedidoPcgt(
        UUID processoId,
        String numeroProcesso,
        String codigoProcesso,
        String idus,
        String codigoIdus,
        String municipioToken,
        String tipoAto,
        String numeroAto,
        LocalDate dataAto,
        String linkAto,
        String designacao,
        String tipoInstrumentoCodigo,
        String tipoProcedimentoCodigo,
        String codigoEntidade,
        ComissaoConsultiva comissaoConsultiva,
        Deliberacao aprovacaoCmEim,
        Deliberacao aprovacaoAm,
        Deposito deposito
) {

    record Documento(UUID id, String nome) {
    }

    record ComissaoConsultiva(String titulo, String url, List<Documento> ficheiros) {
    }

    record Deliberacao(LocalDate data, List<Documento> ficheiros) {
    }

    record Deposito(String numero, LocalDate data) {
    }

    /** Os documentos a referenciar, de todos os blocos: cada um precisa de um link público com destino PCGT. */
    List<Documento> documentos() {
        return Stream.of(comissaoConsultiva == null ? List.<Documento>of() : comissaoConsultiva.ficheiros(),
                        aprovacaoCmEim == null ? List.<Documento>of() : aprovacaoCmEim.ficheiros(),
                        aprovacaoAm == null ? List.<Documento>of() : aprovacaoAm.ficheiros())
                .flatMap(List::stream).distinct().toList();
    }
}
