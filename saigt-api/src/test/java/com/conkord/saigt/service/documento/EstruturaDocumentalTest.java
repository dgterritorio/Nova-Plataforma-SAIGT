package com.conkord.saigt.service.documento;

import com.conkord.saigt.persistence.configuracao.DocumentoConfig;
import com.conkord.saigt.persistence.operacional.ProcessoDocumento;
import com.conkord.saigt.service.documento.EstruturaDocumental.Item;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EstruturaDocumentalTest {

    private static DocumentoConfig item(boolean obrigatorio) {
        DocumentoConfig config = mock(DocumentoConfig.class);
        when(config.isObrigatorio()).thenReturn(obrigatorio);
        return config;
    }

    @Test
    void pendenteSoQuandoObrigatorioSemFicheirosESemObservacoes() {
        ProcessoDocumento ficheiro = mock(ProcessoDocumento.class);

        assertThat(new Item(item(true), List.of(), null).pendente()).isTrue();
        assertThat(new Item(item(true), List.of(), "NA").pendente()).isFalse();
        assertThat(new Item(item(true), List.of(ficheiro), null).pendente()).isFalse();
        assertThat(new Item(item(true), List.of(ficheiro), "NA").pendente()).isFalse();
        assertThat(new Item(item(false), List.of(), null).pendente()).isFalse();
    }
}
