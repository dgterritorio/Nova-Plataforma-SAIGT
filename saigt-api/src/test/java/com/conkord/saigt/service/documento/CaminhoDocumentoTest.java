package com.conkord.saigt.service.documento;

import com.conkord.saigt.persistence.configuracao.CategoriaDocumento;
import com.conkord.saigt.persistence.operacional.OrigemDocumento;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CaminhoDocumentoTest {

    private static final UUID DOCUMENTO = UUID.fromString("0b9e6d2c-5f1a-4c3e-9a7b-2d8f1e4c6a90");

    @Test
    void deveMontarAChaveDeDepositoComONumeroDoProcessoESemInstrumento() {
        assertThat(CaminhoDocumento.privado("00123", CategoriaDocumento.DEPOSITO, OrigemDocumento.CARREGADO, DOCUMENTO,
                "Regulamento Final.pdf"))
                .isEqualTo("processos/00123/DEPOSITO/0b9e6d2c-5f1a-4c3e-9a7b-2d8f1e4c6a90-Regulamento_Fina.pdf");
    }

    @Test
    void deveMontarAChaveDoAnexoDeNotificacaoComONomeSanitizado() {
        UUID notificacao = UUID.fromString("7c1d2e3f-4a5b-4c6d-8e9f-0a1b2c3d4e5f");
        assertThat(CaminhoDocumento.notificacao(notificacao, DOCUMENTO, "Ofício nº 3.pdf"))
                .isEqualTo("notificacoes/" + notificacao + "/" + DOCUMENTO + "-Oficio_n_3.pdf");
    }

    @Test
    void deveMontarAChaveDePublicacao() {
        assertThat(CaminhoDocumento.privado("00123", CategoriaDocumento.PUBLICACAO, OrigemDocumento.CARREGADO,
                DOCUMENTO, "planta.pdf"))
                .isEqualTo("processos/00123/PUBLICACAO/" + DOCUMENTO + "-planta.pdf");
    }

    @Test
    void deveGuardarOsOutputsDoGeoconformNaSubpastaGeoconform() {
        assertThat(CaminhoDocumento.privado("00123", CategoriaDocumento.DEPOSITO, OrigemDocumento.GEOCONFORM, DOCUMENTO,
                "relatorio.pdf"))
                .isEqualTo("processos/00123/DEPOSITO/GEOCONFORM/" + DOCUMENTO + "-relatorio.pdf");
    }

    @Test
    void deveAcrescentarARevisaoNaChaveDoFicheiroSubstituto() {
        assertThat(CaminhoDocumento.privadoSubstituto("00123", CategoriaDocumento.DEPOSITO, OrigemDocumento.CARREGADO,
                DOCUMENTO, "a1b2c3d4", "planta.pdf"))
                .isEqualTo("processos/00123/DEPOSITO/" + DOCUMENTO + "-a1b2c3d4-planta.pdf");
    }

    @Test
    void deveMontarAChaveDoSubstituidoNaPastaSubstituidos() {
        assertThat(CaminhoDocumento.substituido("00123", OrigemDocumento.CARREGADO, DOCUMENTO, "a1b2c3d4",
                "planta.pdf"))
                .isEqualTo("processos/00123/SUBSTITUIDOS/" + DOCUMENTO + "-a1b2c3d4-planta.pdf");
        assertThat(CaminhoDocumento.substituido("00123", OrigemDocumento.GEOCONFORM, DOCUMENTO, "a1b2c3d4",
                "planta.pdf"))
                .isEqualTo("processos/00123/SUBSTITUIDOS/GEOCONFORM/" + DOCUMENTO + "-a1b2c3d4-planta.pdf");
    }

    @Test
    void naoDeveConterInstrumentoNemPrefixoPrivadoNemPastaOutros() {
        for (CategoriaDocumento categoria : CategoriaDocumento.values()) {
            for (OrigemDocumento origem : OrigemDocumento.values()) {
                assertThat(CaminhoDocumento.privado("00123", categoria, origem, DOCUMENTO, "a.pdf"))
                        .startsWith("processos/00123/")
                        .doesNotContain("privado/")
                        .doesNotContain("OUTROS");
            }
        }
    }

    @Test
    void naoDeveExistirACategoriaOutros() {
        assertThat(Arrays.stream(CaminhoDocumento.class.getDeclaredFields()).map(Field::getName))
                .doesNotContain("CATEGORIA_SEM_CONFIG");
    }

    @Test
    void deveRetirarAcentosECaracteresEspeciais() {
        assertThat(CaminhoDocumento.sanitizar("Relatório (v2)#.pdf")).isEqualTo("Relatorio_v2.pdf");
    }

    @Test
    void naoDeveDeixarSeparadorNoFimDoNomeCortado() {
        assertThat(CaminhoDocumento.sanitizar("Relatório (final)#1.pdf")).isEqualTo("Relatorio_final.pdf");
    }

    @Test
    void deveCortarONomeMantendoAExtensaoNoMaximoDe20Caracteres() {
        String sanitizado = CaminhoDocumento.sanitizar("regulamento-do-plano-diretor-municipal.docx");

        assertThat(sanitizado).hasSize(20).isEqualTo("regulamento-do-.docx");
    }

    @Test
    void deveUsarNomePorOmissaoQuandoNaoSobraNadaDoNome() {
        assertThat(CaminhoDocumento.sanitizar("###.pdf")).isEqualTo("documento.pdf");
    }

    @Test
    void deveAceitarNomeSemExtensao() {
        assertThat(CaminhoDocumento.sanitizar("memoria descritiva")).isEqualTo("memoria_descritiva");
    }

    @Test
    void deveDevolverExtensaoEmMinusculas() {
        assertThat(CaminhoDocumento.extensao("Planta.PDF")).isEqualTo("pdf");
        assertThat(CaminhoDocumento.extensao("sem-extensao")).isEmpty();
    }
}
