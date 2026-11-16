package com.conkord.saigt.service.snit;

import com.conkord.saigt.dto.referencia.ConcelhoSnitResponse;
import com.conkord.saigt.integracao.snit.ListaReferenciaSnit;
import com.conkord.saigt.persistence.core.SnitReferencia;
import com.conkord.saigt.persistence.core.SnitReferenciaRepository;
import com.conkord.saigt.persistence.core.TipoInstrumento;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ReferenciaSnitConsultaServiceTest {

    private static final Instant AGORA = Instant.parse("2026-11-09T03:00:00Z");

    private final SnitReferenciaRepository repository = mock(SnitReferenciaRepository.class);
    private final ReferenciaSnitConsultaService service = new ReferenciaSnitConsultaService(repository, new ObjectMapper());

    private static SnitReferencia concelho(String dtcc, String nome, String nut2) {
        return new SnitReferencia(ListaReferenciaSnit.MUNICIPALITIES, dtcc, nome,
                "{\"dtcc\":\"" + dtcc + "\",\"name\":\"" + nome + "\",\"nut2Code\":\"" + nut2 + "\"}", AGORA);
    }

    @Test
    void concelhosComONomeIgualDevemFicarPelaOrdemDoCodigoSejaQualForAOrdemDaBase() {
        when(repository.findByListaAndAtivoTrue(ListaReferenciaSnit.MUNICIPALITIES)).thenReturn(List.of(
                concelho("4501", "CALHETA", "CENTRAL"), concelho("3101", "CALHETA", "MADEIRA"),
                concelho("1111", "SINTRA", "LISBOA E VALE DO TEJO")));

        List<?> concelhos = service.listar("MUNICIPALITIES", null);

        assertThat(concelhos).extracting("codigo").containsExactly("3101", "4501", "1111");
        assertThat(concelhos).first().isEqualTo(new ConcelhoSnitResponse("3101", "CALHETA", "MADEIRA"));
    }

    @Test
    void osNomesDosConcelhosVemPelaOrdemDosCodigosEUmCodigoSemNomeNaReplicaDaOProprioCodigo() {
        SnitReferencia desativado = concelho("1105", "CASCAIS", "LISBOA E VALE DO TEJO");
        desativado.setAtivo(false);
        when(repository.findByListaAndCodigoIn(eq(ListaReferenciaSnit.MUNICIPALITIES), any())).thenReturn(List.of(
                concelho("1111", "SINTRA", "LISBOA E VALE DO TEJO"), desativado));

        assertThat(service.nomesConcelhosPorOrdem(List.of("1111", "9999", "1105")))
                .containsExactly("SINTRA", "9999", "CASCAIS");
        assertThat(service.nomesConcelhos(List.of("1105", "9999")))
                .containsEntry("1105", "CASCAIS").containsEntry("9999", "9999");
    }

    @Test
    void semCodigosNaoConsultaAReplica() {
        assertThat(service.nomesConcelhosPorOrdem(List.of())).isEmpty();
        assertThat(service.nomesConcelhos(List.of())).isEmpty();
        verifyNoInteractions(repository);
    }

    private static SnitReferencia regiao(String codigo) {
        return new SnitReferencia(ListaReferenciaSnit.NUT2, codigo, codigo,
                "{\"code\":\"" + codigo + "\",\"label\":\"" + codigo + "\"}", AGORA);
    }

    @Test
    void nutsIIDevemSerSoAsRegioesComConcelhoAtivoPeloNome() {
        when(repository.findByListaAndAtivoTrue(ListaReferenciaSnit.MUNICIPALITIES)).thenReturn(List.of(
                concelho("1111", "SINTRA", "LISBOA E VALE DO TEJO"), concelho("0412", "VINHAIS", "NORTE"),
                concelho("4501", "CALHETA", "REGIAO AUTONOMA DOS ACORES - GRUPO CENTRAL")));
        when(repository.findByListaAndAtivoTrue(ListaReferenciaSnit.NUT2)).thenReturn(List.of(
                regiao("NORTE"), regiao("ACORES"), regiao("REGIAO AUTONOMA DOS ACORES - GRUPO CENTRAL"),
                regiao("LISBOA E VALE DO TEJO"), regiao("ALGARVE")));

        assertThat(service.nutsII()).extracting("codigo").containsExactly(
                "LISBOA E VALE DO TEJO", "NORTE", "REGIAO AUTONOMA DOS ACORES - GRUPO CENTRAL");
    }

    private static TipoInstrumento tipoComSnitCodigo(String snitCodigo) {
        TipoInstrumento tipo = mock(TipoInstrumento.class);
        when(tipo.getSnitCodigo()).thenReturn(snitCodigo);
        return tipo;
    }

    private static SnitReferencia tipoSnit(String id, String dadosOrigem) {
        return new SnitReferencia(ListaReferenciaSnit.INSTRUMENT_TYPES, id, "Plano Diretor Municipal", dadosOrigem, AGORA);
    }

    @Test
    void classIdDoPdmDeveSerOClassIdDoSeuRegistoNaReplica() {
        when(repository.findByListaAndCodigo(ListaReferenciaSnit.INSTRUMENT_TYPES, "1")).thenReturn(Optional.of(
                tipoSnit("1", "{\"id\":1,\"code\":\"PDM\",\"label\":\"Plano Diretor Municipal\",\"classId\":3}")));

        assertThat(service.classIdDoTipo(tipoComSnitCodigo("1"))).contains(3);
    }

    @Test
    void classIdDeveFicarVazioSemSnitCodigoSemRegistoAtivoOuSemClassIdNumerico() {
        SnitReferencia inativo = tipoSnit("6", "{\"id\":6,\"classId\":3}");
        inativo.setAtivo(false);
        when(repository.findByListaAndCodigo(ListaReferenciaSnit.INSTRUMENT_TYPES, "6")).thenReturn(Optional.of(inativo));
        when(repository.findByListaAndCodigo(ListaReferenciaSnit.INSTRUMENT_TYPES, "7")).thenReturn(Optional.empty());
        when(repository.findByListaAndCodigo(ListaReferenciaSnit.INSTRUMENT_TYPES, "37")).thenReturn(Optional.of(
                tipoSnit("37", "{\"id\":37,\"classId\":\"quatro\"}")));

        assertThat(service.classIdDoTipo(tipoComSnitCodigo(null))).isEmpty();
        assertThat(service.classIdDoTipo(tipoComSnitCodigo(" "))).isEmpty();
        assertThat(service.classIdDoTipo(tipoComSnitCodigo("6"))).isEmpty();
        assertThat(service.classIdDoTipo(tipoComSnitCodigo("7"))).isEmpty();
        assertThat(service.classIdDoTipo(tipoComSnitCodigo("37"))).isEmpty();
    }
}
