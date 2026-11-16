package com.conkord.saigt.service.core;

import com.nimbusds.jose.jwk.RSAKey;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.env.MockEnvironment;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtKeyProviderTest {

    @Test
    void deveGerarChaveEfemeraQuandoPathVazioNoPerfilTeste() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("test");
        JwtKeyProvider provider = new JwtKeyProvider("", env);

        provider.carregar();

        assertThat(provider.chaveAtual()).isNotNull();
        assertThat(provider.chaveAtual().getKeyID()).isNotBlank();
        assertThat(provider.jwkSet().getKeys()).hasSize(1);
    }

    @Test
    void deveFalharAoArrancarQuandoPathVazioForaDoPerfilTeste() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("producao");
        JwtKeyProvider provider = new JwtKeyProvider("", env);

        assertThatThrownBy(provider::carregar)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("app.jwt.private-key-path");
    }

    @Test
    void deveCarregarVariasChavesENuncaExporComponentePrivado(@TempDir Path tempDir) throws Exception {
        Path pem1 = escreverChavePem(tempDir, "chave1.pem");
        Path pem2 = escreverChavePem(tempDir, "chave2.pem");

        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("producao");
        JwtKeyProvider provider = new JwtKeyProvider(pem1 + "," + pem2, env);

        provider.carregar();

        assertThat(provider.jwkSet().getKeys()).hasSize(2);
        provider.jwkSet().getKeys().forEach(chave -> assertThat(((RSAKey) chave).isPrivate()).isFalse());
        assertThat(provider.chaveAtual().getKeyID()).isNotBlank();
    }

    @Test
    void deveDarOMesmoKidAMesmaChaveEmInstanciasDiferentes(@TempDir Path tempDir) throws Exception {
        Path pem = escreverChavePem(tempDir, "chave.pem");
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("producao");
        JwtKeyProvider instanciaA = new JwtKeyProvider(pem.toString(), env);
        JwtKeyProvider instanciaB = new JwtKeyProvider(pem.toString(), env);

        instanciaA.carregar();
        instanciaB.carregar();

        assertThat(instanciaA.chaveAtual().getKeyID())
                .isEqualTo(instanciaB.chaveAtual().getKeyID())
                .isEqualTo(instanciaA.chaveAtual().computeThumbprint().toString());
    }

    private Path escreverChavePem(Path dir, String nomeFicheiro) throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair keyPair = generator.generateKeyPair();
        String base64 = Base64.getEncoder().encodeToString(keyPair.getPrivate().getEncoded());
        String pem = "-----BEGIN PRIVATE KEY-----\n" + base64 + "\n-----END PRIVATE KEY-----\n";
        Path ficheiro = dir.resolve(nomeFicheiro);
        Files.writeString(ficheiro, pem);
        return ficheiro;
    }
}
