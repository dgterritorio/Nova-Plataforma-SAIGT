package com.conkord.saigt.service.core;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;

/**
 * `app.jwt.private-key-path`: lista de PEM (PKCS8) separada por vírgulas; a primeira assina, todas vão para o JWKS.
 * Rotação: acrescentar a nova à frente e remover a antiga só depois de os seus tokens expirarem.
 * Fora do perfil de teste falha o arranque em vez de gerar uma chave.
 */
@Component
public class JwtKeyProvider {

    private final String privateKeyPathConfig;
    private final boolean perfilTeste;
    private List<RSAKey> chaves;

    public JwtKeyProvider(@Value("${app.jwt.private-key-path:}") String privateKeyPathConfig, Environment environment) {
        this.privateKeyPathConfig = privateKeyPathConfig;
        this.perfilTeste = environment.acceptsProfiles(Profiles.of("test"));
    }

    @PostConstruct
    void carregar() {
        List<String> caminhos = privateKeyPathConfig == null ? List.of() :
                Arrays.stream(privateKeyPathConfig.split(",")).map(String::trim).filter(s -> !s.isBlank()).toList();

        if (caminhos.isEmpty()) {
            if (!perfilTeste) {
                throw new IllegalStateException(
                        "app.jwt.private-key-path não está configurado — obrigatório fora do perfil de teste.");
            }
            chaves = List.of(gerarChaveEfemera());
            return;
        }

        List<RSAKey> carregadas = new ArrayList<>();
        for (String caminho : caminhos) {
            carregadas.add(lerChavePem(caminho));
        }
        chaves = List.copyOf(carregadas);
    }

    public RSAKey chaveAtual() {
        return chaves.get(0);
    }

    public JWKSet jwkSet() {
        return new JWKSet(new ArrayList<JWK>(chaves)).toPublicJWKSet();
    }

    private RSAKey gerarChaveEfemera() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            KeyPair keyPair = generator.generateKeyPair();
            return construirRsaKey((RSAPublicKey) keyPair.getPublic(), (RSAPrivateKey) keyPair.getPrivate());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Não foi possível gerar a chave RSA efémera de teste.", e);
        }
    }

    private RSAKey lerChavePem(String caminho) {
        try {
            String pem = Files.readString(Path.of(caminho));
            String base64 = pem
                    .replace("-----BEGIN PRIVATE KEY-----", "")
                    .replace("-----END PRIVATE KEY-----", "")
                    .replaceAll("\\s", "");
            byte[] bytes = Base64.getDecoder().decode(base64);

            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            RSAPrivateCrtKey privateKey = (RSAPrivateCrtKey) keyFactory.generatePrivate(new PKCS8EncodedKeySpec(bytes));
            RSAPublicKey publicKey = (RSAPublicKey) keyFactory.generatePublic(
                    new RSAPublicKeySpec(privateKey.getModulus(), privateKey.getPublicExponent()));

            return construirRsaKey(publicKey, privateKey);
        } catch (IOException e) {
            throw new IllegalStateException("Não foi possível ler a chave privada em " + caminho, e);
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new IllegalStateException("Chave privada inválida em " + caminho + " — esperado PEM PKCS8 RSA.", e);
        }
    }

    /** `kid` = thumbprint RFC 7638: igual em todas as instâncias com a mesma chave, para um token valer em qualquer uma. */
    private RSAKey construirRsaKey(RSAPublicKey publicKey, RSAPrivateKey privateKey) {
        try {
            return new RSAKey.Builder(publicKey)
                    .privateKey(privateKey)
                    .keyIDFromThumbprint()
                    .build();
        } catch (JOSEException e) {
            throw new IllegalStateException("Não foi possível calcular o kid da chave RSA.", e);
        }
    }
}
