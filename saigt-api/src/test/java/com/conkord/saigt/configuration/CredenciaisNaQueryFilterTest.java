package com.conkord.saigt.configuration;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class CredenciaisNaQueryFilterTest {

    @Test
    void deveDetetarSegredoOuTokenNaQuery() {
        assertThat(CredenciaisNaQueryFilter.temParametroProibido("client_secret=x")).isTrue();
        assertThat(CredenciaisNaQueryFilter.temParametroProibido("a=1&access_token=x")).isTrue();
        assertThat(CredenciaisNaQueryFilter.temParametroProibido("client%5Fsecret=x")).isTrue();
        assertThat(CredenciaisNaQueryFilter.temParametroProibido("access_token")).isTrue();
        assertThat(CredenciaisNaQueryFilter.temParametroProibido("refresh_token=x")).isTrue();
        assertThat(CredenciaisNaQueryFilter.temParametroProibido("token=x&token_type_hint=refresh_token")).isTrue();
    }

    @Test
    void naoDeveRecusarQueriesSemCredenciais() {
        assertThat(CredenciaisNaQueryFilter.temParametroProibido(null)).isFalse();
        assertThat(CredenciaisNaQueryFilter.temParametroProibido("")).isFalse();
        assertThat(CredenciaisNaQueryFilter.temParametroProibido("grant_type=client_credentials&%zz=1")).isFalse();
    }

    @Test
    void deveResponder400InvalidRequestEmUtf8SemChamarOResto() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/oauth2/token");
        request.setQueryString("client_secret=x");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        new CredenciaisNaQueryFilter(new ObjectMapper()).doFilter(request, response, chain);

        assertThat(chain.getRequest()).isNull();
        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(response.getContentType()).isEqualTo("application/json;charset=UTF-8");
        String corpo = new String(response.getContentAsByteArray(), StandardCharsets.UTF_8);
        assertThat(corpo).contains("\"error\":\"invalid_request\"", "não são aceites");
    }
}
