package com.conkord.saigt.integracao.storage;

import com.conkord.saigt.configuration.StorageProperties;
import com.conkord.saigt.exception.StorageIndisponivelException;
import io.minio.GetObjectArgs;
import io.minio.ListObjectsArgs;
import io.minio.MinioClient;
import io.minio.ObjectWriteResponse;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.Result;
import io.minio.messages.Item;
import okhttp3.Headers;
import org.junit.jupiter.api.Test;
import org.springframework.util.unit.DataSize;
import org.mockito.ArgumentCaptor;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class S3ArmazenamentoDocumentosTest {

    private static final byte[] CONTEUDO = "%PDF-1.4".getBytes();
    private static final String CAMINHO = "privado/a/b/PUBLICACAO/c-d.pdf";

    private final MinioClient minioClient = mock(MinioClient.class);
    private final S3ArmazenamentoDocumentos armazenamento = new S3ArmazenamentoDocumentos(minioClient,
            new StorageProperties("http://localhost:9000", "chave", "segredo", "bucket", false, false, java.time.Duration.ofSeconds(10), java.time.Duration.ofSeconds(60)), DataSize.ofMegabytes(10));

    @Test
    void deveEnviarOStreamSemTamanhoEmPartesDeTamanhoFixo() throws Exception {
        when(minioClient.putObject(any(PutObjectArgs.class))).thenReturn(resposta("v-1"));

        guardar();

        ArgumentCaptor<PutObjectArgs> pedido = ArgumentCaptor.forClass(PutObjectArgs.class);
        verify(minioClient).putObject(pedido.capture());
        assertThat(pedido.getValue().objectSize()).isEqualTo(-1);
        assertThat(pedido.getValue().partSize()).isEqualTo(DataSize.ofMegabytes(10).toBytes());
    }

    @Test
    void deveDevolverOVersionIdDoObjetoGravado() throws Exception {
        when(minioClient.putObject(any(PutObjectArgs.class))).thenReturn(resposta("v-1"));

        assertThat(guardar()).isEqualTo("v-1");
    }

    @Test
    void deveDevolverNuloQuandoOBucketNaoTemVersionamento() throws Exception {
        when(minioClient.putObject(any(PutObjectArgs.class))).thenReturn(resposta(null));

        assertThat(guardar()).isNull();
    }

    @Test
    void deveRemoverTodasAsVersoesDoObjeto() throws Exception {
        existem(versao(CAMINHO, "v-2"), versao(CAMINHO, "v-1"));

        armazenamento.remover(CAMINHO);

        assertThat(versoesRemovidas()).containsExactly("v-2", "v-1");
    }

    @Test
    void deveRemoverSemVersionIdQuandoOBucketNaoTemVersionamento() throws Exception {
        existem(versao(CAMINHO, null));

        armazenamento.remover(CAMINHO);

        assertThat(versoesRemovidas()).containsExactly((String) null);
    }

    @Test
    void naoDeveRemoverObjetosQueApenasPartilhamOPrefixo() throws Exception {
        existem(versao(CAMINHO + "x", "v-9"), versao(CAMINHO, "v-1"));

        armazenamento.remover(CAMINHO);

        assertThat(versoesRemovidas()).containsExactly("v-1");
    }

    @Test
    void naoDeveFalharQuandoOObjetoNaoExiste() throws Exception {
        existem();

        armazenamento.remover(CAMINHO);

        verify(minioClient, never()).removeObject(any(RemoveObjectArgs.class));
    }

    @Test
    void deveFalharQuandoOStorageEstaIndisponivel() throws Exception {
        existem(versao(CAMINHO, "v-1"));
        doThrow(new IOException("sem ligação")).when(minioClient).removeObject(any(RemoveObjectArgs.class));

        assertThatThrownBy(() -> armazenamento.remover(CAMINHO)).isInstanceOf(StorageIndisponivelException.class);
    }

    @Test
    void deveFalharComStorageIndisponivelQuandoALeituraParcialNaoChegaAoStorage() throws Exception {
        when(minioClient.getObject(any(GetObjectArgs.class))).thenThrow(new IOException("sem ligação"));

        assertThatThrownBy(() -> armazenamento.abrir(CAMINHO, "v-1", 1024, 64 * 1024))
                .isInstanceOf(StorageIndisponivelException.class);
    }

    private void existem(Item... itens) {
        List<Result<Item>> resultados = Arrays.stream(itens).map(Result::new).toList();
        when(minioClient.listObjects(any(ListObjectsArgs.class))).thenReturn(resultados);
    }

    private List<String> versoesRemovidas() throws Exception {
        ArgumentCaptor<RemoveObjectArgs> pedidos = ArgumentCaptor.forClass(RemoveObjectArgs.class);
        verify(minioClient, atLeastOnce()).removeObject(pedidos.capture());
        return pedidos.getAllValues().stream().map(RemoveObjectArgs::versionId).toList();
    }

    private static Item versao(String objectName, String versionId) {
        Item item = mock(Item.class);
        when(item.objectName()).thenReturn(objectName);
        when(item.versionId()).thenReturn(versionId);
        return item;
    }

    private String guardar() {
        return armazenamento.guardar("privado/a/b/PUBLICACAO/c-d.pdf", new ByteArrayInputStream(CONTEUDO),
                "application/pdf");
    }

    private static ObjectWriteResponse resposta(String versionId) {
        return new ObjectWriteResponse(Headers.of(), "bucket", null, "privado/a/b/PUBLICACAO/c-d.pdf", "etag",
                versionId);
    }
}
