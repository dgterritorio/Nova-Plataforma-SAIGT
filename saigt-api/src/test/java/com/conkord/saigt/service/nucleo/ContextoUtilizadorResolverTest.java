package com.conkord.saigt.service.nucleo;

import com.conkord.saigt.persistence.core.TipoEntidade;
import com.conkord.saigt.persistence.operacional.PerfilInterno;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ContextoUtilizadorResolverTest {

    private final ContextoUtilizadorResolver resolver = new ContextoUtilizadorResolver();

    @AfterEach
    void limparContexto() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void deveResolverAtorDgtParaMaster() {
        UUID utilizadorId = UUID.randomUUID();
        autenticarComoInterno(utilizadorId.toString(), "MASTER");

        ContextoUtilizador contexto = resolver.resolver();

        assertThat(contexto.utilizadorId()).isEqualTo(utilizadorId);
        assertThat(contexto.origem()).isEqualTo("AD_DGT");
        assertThat(contexto.perfilInterno()).isEqualTo(PerfilInterno.MASTER);
        assertThat(contexto.entidadeNif()).isNull();
        assertThat(contexto.ator()).isEqualTo("DGT");
        assertThat(contexto.atosAutorizados()).isNull();
    }

    @Test
    void deveResolverAtorDgtParaTecnico() {
        UUID utilizadorId = UUID.randomUUID();
        autenticarComoInterno(utilizadorId.toString(), "TECNICO");

        ContextoUtilizador contexto = resolver.resolver();

        assertThat(contexto.perfilInterno()).isEqualTo(PerfilInterno.TECNICO);
        assertThat(contexto.ator()).isEqualTo("DGT");
    }

    @Test
    void deveResolverAtorNuloParaVisitante() {
        autenticarComoInterno(UUID.randomUUID().toString(), "VISITANTE");

        ContextoUtilizador contexto = resolver.resolver();

        assertThat(contexto.perfilInterno()).isEqualTo(PerfilInterno.VISITANTE);
        assertThat(contexto.ator()).isNull();
    }

    @Test
    void deveFicarSemPerfilNemAtorQuandoClaimTrazValorAntigo() {
        autenticarComoInterno(UUID.randomUUID().toString(), "ADMIN_DGT");

        ContextoUtilizador contexto = resolver.resolver();

        assertThat(contexto.perfilInterno()).isNull();
        assertThat(contexto.ator()).isNull();
    }

    @Test
    void deveLerONipcDoClaimEntidadeEPreferiloAoNifAntigo() {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .claim(JwtClaimNames.SUB, UUID.randomUUID().toString())
                .claim("origem", "INCM")
                .claim("entidade", Map.of("id", 7L, "codigo", "5245", "tipo_entidade", "CM", "nipc", "500051062",
                        "nif", "000000000", "nome", "Entidade Teste", "pcm", false))
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));

        assertThat(resolver.resolver().entidadeNif()).isEqualTo("500051062");
    }

    @Test
    void aEntidadeDoTokenTemCodigoNomeETipoEFicaVaziaNosInternos() {
        autenticarComEntidade(Map.of("id", 7L, "codigo", "5245", "tipo_entidade", "CM", "nipc", "500051062",
                "nome", "Município de Teste", "pcm", false));

        assertThat(resolver.entidadeDoToken())
                .contains(new ContextoUtilizadorResolver.EntidadeDoToken("5245", "Município de Teste", "CM"));

        autenticarComoInterno(UUID.randomUUID().toString(), "MASTER");
        assertThat(resolver.entidadeDoToken()).isEmpty();
    }

    @Test
    void deveLerOTipoDaEntidadeDaClaimEntidadeTipoEntidade() {
        autenticarComEntidade(Map.of("nipc", "500051062", "nome", "Entidade Teste", "pcm", false, "tipo_entidade", "CCDR_DR"));

        assertThat(resolver.resolver().tipoEntidade()).isEqualTo(TipoEntidade.CCDR_DR);
    }

    @Test
    void deveLerOTipoDaEntidadeTambemDeUmTokenComNifAntigo() {
        autenticarComEntidade(Map.of("nif", "500051062", "nome", "Entidade Teste", "pcm", false, "tipo_entidade", "CM"));

        ContextoUtilizador contexto = resolver.resolver();

        assertThat(contexto.tipoEntidade()).isEqualTo(TipoEntidade.CM);
        assertThat(contexto.entidadeNif()).isEqualTo("500051062");
    }

    @Test
    void deveFicarSemTipoQuandoAClaimNaoTrazTipoEntidade() {
        autenticarComEntidade(Map.of("nipc", "500051062", "nome", "Entidade Teste", "pcm", false));

        assertThat(resolver.resolver().tipoEntidade()).isNull();
    }

    @Test
    void deveFicarSemTipoSemLancarQuandoOValorEDesconhecido() {
        autenticarComEntidade(Map.of("nipc", "500051062", "tipo_entidade", "XPTO"));

        assertThat(resolver.resolver().tipoEntidade()).isNull();
    }

    @Test
    void deveFicarSemTipoSemLancarQuandoOValorNaoETexto() {
        autenticarComEntidade(Map.of("nipc", "500051062", "tipo_entidade", 7));

        assertThat(resolver.resolver().tipoEntidade()).isNull();
    }

    @Test
    void deveLerOIdDaEntidadeDaClaimEntidadeId() {
        autenticarComEntidade(Map.of("id", 3001L, "nipc", "500051062", "nome", "Entidade Teste", "pcm", false, "tipo_entidade", "CM"));

        assertThat(resolver.resolver().entidadeId()).isEqualTo(3001L);
    }

    @Test
    void deveLerOIdDaEntidadeQuandoOTokenOTraziComoInteiro() {
        autenticarComEntidade(Map.of("id", 3007, "nipc", "500051062", "nome", "Entidade Teste", "pcm", false));

        assertThat(resolver.resolver().entidadeId()).isEqualTo(3007L);
    }

    @Test
    void deveFicarSemIdDaEntidadeSemLancarQuandoAClaimFaltaOuNaoENumero() {
        autenticarComEntidade(Map.of("nipc", "500051062", "nome", "Entidade Teste", "pcm", false));
        assertThat(resolver.resolver().entidadeId()).isNull();

        autenticarComEntidade(Map.of("id", "3001", "nipc", "500051062", "nome", "Entidade Teste", "pcm", false));
        assertThat(resolver.resolver().entidadeId()).isNull();
    }

    @Test
    void deveDistinguirDuasEntidadesComOMesmoNipcPeloId() {
        autenticarComEntidade(Map.of("id", 3001L, "nipc", "500051062", "nome", "A", "pcm", false));
        ContextoUtilizador primeira = resolver.resolver();
        autenticarComEntidade(Map.of("id", 3007L, "nipc", "500051062", "nome", "B", "pcm", false));
        ContextoUtilizador segunda = resolver.resolver();

        assertThat(primeira.entidadeNif()).isEqualTo(segunda.entidadeNif());
        assertThat(primeira.entidadeId()).isNotEqualTo(segunda.entidadeId());
    }

    @Test
    void naoDeveTerIdDeEntidadeUmUtilizadorInterno() {
        autenticarComoInterno(UUID.randomUUID().toString(), "MASTER");

        assertThat(resolver.resolver().entidadeId()).isNull();
    }

    @Test
    void naoDeveTerTipoDeEntidadeUmUtilizadorInterno() {
        autenticarComoInterno(UUID.randomUUID().toString(), "MASTER");

        assertThat(resolver.resolver().tipoEntidade()).isNull();
    }

    @Test
    void deveResolverAtorEntidadeQuandoEntidadeSemPcm() {
        UUID utilizadorId = UUID.randomUUID();
        autenticarComoExterno(utilizadorId.toString(), "512523698", false, null);

        ContextoUtilizador contexto = resolver.resolver();

        assertThat(contexto.origem()).isEqualTo("INCM");
        assertThat(contexto.entidadeNif()).isEqualTo("512523698");
        assertThat(contexto.perfilInterno()).isNull();
        assertThat(contexto.ator()).isEqualTo("ENTIDADE");
        assertThat(contexto.entidadeDrParteLetra()).isNull();
        assertThat(contexto.entidadeIncmEnrollmentId()).isNull();
        assertThat(contexto.entidadeDrParteId()).isNull();
    }

    @Test
    void deveExtrairIncmEnrollmentIdEDrParteIdDoClaimEntidadeAninhado() {
        UUID utilizadorId = UUID.randomUUID();
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .claim(JwtClaimNames.SUB, utilizadorId.toString())
                .claim("origem", "INCM")
                .claim("entidade", Map.of("tipo_entidade", "CM", "nif", "512523698", "nome", "Entidade Teste", "pcm", false,
                        "incm_enrollment_id", "2015482",
                        "dr_parte", Map.of("id", 1234567891234567L, "letra", "H")))
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));

        ContextoUtilizador contexto = resolver.resolver();

        assertThat(contexto.entidadeIncmEnrollmentId()).isEqualTo("2015482");
        assertThat(contexto.entidadeDrParteId()).isEqualTo(1234567891234567L);
    }

    @Test
    void deveExtrairDrParteLetraDoClaimEntidadeAninhado() {
        UUID utilizadorId = UUID.randomUUID();
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .claim(JwtClaimNames.SUB, utilizadorId.toString())
                .claim("entidade", Map.of("tipo_entidade", "CM", "nif", "512523698", "nome", "Entidade Teste", "pcm", false,
                        "dr_parte", Map.of("id", 1234567891234567L, "letra", "H")))
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));

        ContextoUtilizador contexto = resolver.resolver();

        assertThat(contexto.entidadeDrParteLetra()).isEqualTo("H");
    }

    @Test
    void deveResolverAtorPcmQuandoEntidadeComPcm() {
        UUID utilizadorId = UUID.randomUUID();
        autenticarComoExterno(utilizadorId.toString(), "600000000", true, null);

        ContextoUtilizador contexto = resolver.resolver();

        assertThat(contexto.ator()).isEqualTo("PCM");
    }

    @Test
    void deveExtrairAtosAutorizadosDoClaimEntidadeAninhado() {
        UUID utilizadorId = UUID.randomUUID();
        Map<String, Object> atosAutorizados = Map.of(
                "serie_1", List.<Object>of(),
                "serie_2", List.<Object>of(Map.of("id", 1234567891234568L, "extrato", true)));
        autenticarComoExterno(utilizadorId.toString(), "512523698", false, atosAutorizados);

        ContextoUtilizador contexto = resolver.resolver();

        assertThat(contexto.atosAutorizados().serie1()).isEmpty();
        assertThat(contexto.atosAutorizados().serie2()).hasSize(1);
        assertThat(contexto.atosAutorizados().serie2().get(0).id()).isEqualTo(1234567891234568L);
        assertThat(contexto.atosAutorizados().serie2().get(0).extrato()).isTrue();
    }

    private void autenticarComEntidade(Map<String, Object> entidade) {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .claim(JwtClaimNames.SUB, UUID.randomUUID().toString())
                .claim("origem", "INCM")
                .claim("entidade", entidade)
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
    }

    private void autenticarComoInterno(String subject, String perfilInterno) {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .claim(JwtClaimNames.SUB, subject)
                .claim("origem", "AD_DGT")
                .claim("perfil_interno", perfilInterno)
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
    }

    private void autenticarComoExterno(String subject, String nif, boolean pcm, Map<String, Object> atosAutorizados) {
        Map<String, Object> entidade = atosAutorizados == null
                ? Map.of("nif", nif, "nome", "Entidade Teste", "pcm", pcm)
                : Map.of("nif", nif, "nome", "Entidade Teste", "pcm", pcm, "atos_autorizados", atosAutorizados);
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .claim(JwtClaimNames.SUB, subject)
                .claim("origem", "INCM")
                .claim("entidade", entidade)
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
    }
}
