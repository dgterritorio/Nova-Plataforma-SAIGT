package com.conkord.saigt.service.instrumento;

import com.conkord.saigt.persistence.operacional.EstadoProcesso;
import com.conkord.saigt.persistence.operacional.PerfilInterno;
import com.conkord.saigt.service.instrumento.GrupoProcesso.Criterio;
import com.conkord.saigt.service.nucleo.ContextoUtilizador;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class GrupoProcessoTest {

    private static ContextoUtilizador externo(String ator) {
        return new ContextoUtilizador(UUID.randomUUID(), "INCM", null, "501000001", ator, null, null, null, null, null, null);
    }

    private static ContextoUtilizador interno(PerfilInterno perfil) {
        return new ContextoUtilizador(UUID.randomUUID(), "AD_DGT", perfil, null, null, null, null, null, null, null, null);
    }

    @Test
    void devePublicadosSerSoPublicadoParaExternoEInterno() {
        Criterio externo = GrupoProcesso.PUBLICADOS.criterio(externo("ENTIDADE")).orElseThrow();
        Criterio interno = GrupoProcesso.PUBLICADOS.criterio(interno(PerfilInterno.MASTER)).orElseThrow();

        assertThat(externo).isEqualTo(new Criterio(Set.of(EstadoProcesso.PUBLICADO), false, false));
        assertThat(interno).isEqualTo(externo);
    }

    @Test
    void deveEmCursoDoExternoSerOsSeusEmSubmissaoEEnviadoDgt() {
        Criterio criterio = GrupoProcesso.EM_CURSO.criterio(externo("ENTIDADE")).orElseThrow();

        assertThat(criterio.estados()).containsExactlyInAnyOrder(EstadoProcesso.EM_SUBMISSAO, EstadoProcesso.ENVIADO_DGT);
        assertThat(criterio.apenasProprios()).isTrue();
        assertThat(criterio.incluiValidadosAtribuidos()).isFalse();
    }

    @Test
    void deveParaCorrecaoDoExternoSerOsSeusRejeitadosEAnuladosSemOsValidadosAtribuidos() {
        Criterio criterio = GrupoProcesso.PARA_CORRECAO.criterio(externo("ENTIDADE")).orElseThrow();

        assertThat(criterio.estados()).containsExactlyInAnyOrder(EstadoProcesso.REJEITADO_DGT, EstadoProcesso.ANULADO_INCM);
        assertThat(criterio.apenasProprios()).isTrue();
        assertThat(criterio.incluiValidadosAtribuidos()).isFalse();
    }

    @Test
    void deveParaCorrecaoDoPcmIncluirOsValidadosAtribuidosASi() {
        Criterio criterio = GrupoProcesso.PARA_CORRECAO.criterio(externo("PCM")).orElseThrow();

        assertThat(criterio.estados()).containsExactlyInAnyOrder(EstadoProcesso.REJEITADO_DGT, EstadoProcesso.ANULADO_INCM);
        assertThat(criterio.apenasProprios()).isTrue();
        assertThat(criterio.incluiValidadosAtribuidos()).isTrue();
    }

    @Test
    void deveEmValidacaoDoExternoSerDeQualquerAutor() {
        Criterio criterio = GrupoProcesso.EM_VALIDACAO.criterio(externo("ENTIDADE")).orElseThrow();

        assertThat(criterio.estados()).containsExactlyInAnyOrder(EstadoProcesso.EM_VALIDACAO_DGT, EstadoProcesso.VALIDADO_DGT,
                EstadoProcesso.ENVIADO_INCM, EstadoProcesso.EM_VALIDACAO_INCM);
        assertThat(criterio.apenasProprios()).isFalse();
        assertThat(criterio.excluiValidadosComPcm()).isFalse();
    }

    @Test
    void deveEmValidacaoDoPerfilInternoSerAFilaDaDgtSemEmSubmissao() {
        for (PerfilInterno perfil : PerfilInterno.values()) {
            Criterio criterio = GrupoProcesso.EM_VALIDACAO.criterio(interno(perfil)).orElseThrow();

            assertThat(criterio.estados()).containsExactlyInAnyOrder(
                    EstadoProcesso.ENVIADO_DGT, EstadoProcesso.EM_VALIDACAO_DGT, EstadoProcesso.VALIDADO_DGT);
            assertThat(criterio.apenasProprios()).isFalse();
            assertThat(criterio.excluiValidadosComPcm()).isTrue();
        }
    }

    @Test
    void deveEmCursoEParaCorrecaoNaoSeAplicarAoPerfilInterno() {
        for (PerfilInterno perfil : PerfilInterno.values()) {
            assertThat(GrupoProcesso.EM_CURSO.criterio(interno(perfil))).isEmpty();
            assertThat(GrupoProcesso.PARA_CORRECAO.criterio(interno(perfil))).isEmpty();
        }
    }
}
