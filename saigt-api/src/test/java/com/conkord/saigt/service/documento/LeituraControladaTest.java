package com.conkord.saigt.service.documento;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LeituraControladaTest {

    @Test
    void deveContarBytesECalcularOSha256NumaSoLeitura() throws Exception {
        byte[] conteudo = "%PDF-1.4 conteúdo".getBytes(StandardCharsets.UTF_8);
        LeituraControlada leitura = new LeituraControlada(new ByteArrayInputStream(conteudo), 1024);

        leitura.transferTo(java.io.OutputStream.nullOutputStream());

        assertThat(leitura.bytesLidos()).isEqualTo(conteudo.length);
        assertThat(leitura.sha256())
                .isEqualTo(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(conteudo)));
        assertThat(leitura.limiteExcedido()).isFalse();
    }

    @Test
    void deveInterromperALeituraQuandoPassaOLimite() {
        LeituraControlada leitura = new LeituraControlada(new ByteArrayInputStream(new byte[100]), 10);

        assertThatThrownBy(leitura::readAllBytes).isInstanceOf(IOException.class);

        assertThat(leitura.limiteExcedido()).isTrue();
        assertThat(leitura.falhaLeitura()).isNull();
    }

    @Test
    void deveAceitarExatamenteOLimite() throws Exception {
        LeituraControlada leitura = new LeituraControlada(new ByteArrayInputStream(new byte[10]), 10);

        assertThat(leitura.readAllBytes()).hasSize(10);
        assertThat(leitura.limiteExcedido()).isFalse();
    }

    @Test
    void deveRegistarAFalhaQuandoOCorpoEInterrompido() {
        InputStream cortado = new InputStream() {
            @Override
            public int read() throws IOException {
                throw new IOException("ligação fechada");
            }
        };
        LeituraControlada leitura = new LeituraControlada(cortado, 10);

        assertThatThrownBy(leitura::readAllBytes).isInstanceOf(IOException.class);

        assertThat(leitura.falhaLeitura()).hasMessage("ligação fechada");
        assertThat(leitura.limiteExcedido()).isFalse();
    }
}
