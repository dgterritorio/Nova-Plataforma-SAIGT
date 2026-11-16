package com.conkord.saigt.integracao.storage;

import com.conkord.saigt.configuration.StorageProperties;
import com.conkord.saigt.exception.RecursoNaoEncontradoException;
import com.conkord.saigt.exception.StorageIndisponivelException;
import com.conkord.saigt.service.documento.ArmazenamentoDocumentos;
import com.conkord.saigt.service.documento.VersaoObjeto;
import io.minio.CopyObjectArgs;
import io.minio.CopySource;
import io.minio.GetObjectArgs;
import io.minio.GetObjectRetentionArgs;
import io.minio.IsObjectLegalHoldEnabledArgs;
import io.minio.ListObjectsArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.Result;
import io.minio.SetObjectRetentionArgs;
import io.minio.errors.ErrorResponseException;
import io.minio.messages.Item;
import io.minio.messages.Retention;
import io.minio.messages.RetentionMode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.unit.DataSize;

import java.io.InputStream;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Component
public class S3ArmazenamentoDocumentos implements ArmazenamentoDocumentos {

    private static final long TAMANHO_DESCONHECIDO = -1;
    private static final Set<String> OBJETO_INEXISTENTE = Set.of("NoSuchKey", "NoSuchVersion");
    // Respostas S3 de um bucket sem Object Lock ou de uma versão sem retenção nem legal hold.
    private static final Set<String> SEM_RETENCAO = Set.of("NoSuchObjectLockConfiguration",
            "ObjectLockConfigurationNotFoundError", "InvalidRequest");
    private static final String MSG_INDISPONIVEL = "Armazenamento de documentos temporariamente indisponível.";

    private final MinioClient minioClient;
    private final String bucket;
    private final long tamanhoParte;

    public S3ArmazenamentoDocumentos(MinioClient minioClient, StorageProperties properties,
                                     @Value("${app.documentos.tamanho-parte}") DataSize tamanhoParte) {
        this.minioClient = minioClient;
        this.bucket = properties.bucket();
        this.tamanhoParte = tamanhoParte.toBytes();
    }

    @Override
    public String guardar(String caminho, InputStream conteudo, String contentType) {
        try {
            return minioClient.putObject(PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(caminho)
                    .stream(conteudo, TAMANHO_DESCONHECIDO, tamanhoParte)
                    .contentType(contentType)
                    .build()).versionId();
        } catch (Exception e) {
            throw new StorageIndisponivelException(MSG_INDISPONIVEL, e);
        }
    }

    @Override
    public String copiar(String origem, String versaoOrigem, String destino) {
        try {
            CopySource.Builder fonte = CopySource.builder().bucket(bucket).object(origem);
            if (versaoOrigem != null && !versaoOrigem.isBlank()) {
                fonte.versionId(versaoOrigem);
            }
            return minioClient.copyObject(CopyObjectArgs.builder()
                    .bucket(bucket)
                    .object(destino)
                    .source(fonte.build())
                    .build()).versionId();
        } catch (Exception e) {
            throw new StorageIndisponivelException(MSG_INDISPONIVEL, e);
        }
    }

    @Override
    public void remover(String caminho) {
        removerTodasVersoes(caminho);
    }

    /**
     * Num bucket versionado, `removeObject` sem `versionId` só acrescenta um marcador e deixa o conteúdo no storage;
     * num bucket sem versionamento a única versão vem sem `versionId` e apaga-se pela chave.
     */
    @Override
    public void removerTodasVersoes(String caminho) {
        try {
            Iterable<Result<Item>> versoes = minioClient.listObjects(ListObjectsArgs.builder()
                    .bucket(bucket).prefix(caminho).includeVersions(true).build());
            for (Result<Item> resultado : versoes) {
                Item versao = resultado.get();
                if (caminho.equals(versao.objectName())) {
                    removerVersao(caminho, versao.versionId());
                }
            }
        } catch (StorageIndisponivelException e) {
            throw e;
        } catch (Exception e) {
            throw new StorageIndisponivelException(MSG_INDISPONIVEL, e);
        }
    }

    @Override
    public void marcarEliminado(String caminho) {
        removerVersao(caminho, null);
    }

    private void removerVersao(String caminho, String versionId) {
        try {
            RemoveObjectArgs.Builder pedido = RemoveObjectArgs.builder().bucket(bucket).object(caminho);
            if (versionId != null && !versionId.isBlank()) {
                pedido.versionId(versionId);
            }
            minioClient.removeObject(pedido.build());
        } catch (Exception e) {
            throw new StorageIndisponivelException(MSG_INDISPONIVEL, e);
        }
    }

    @Override
    public InputStream abrir(String caminho, String versao, long inicio, long comprimento) {
        try {
            GetObjectArgs.Builder pedido = GetObjectArgs.builder().bucket(bucket).object(caminho);
            if (comprimento > 0) {
                pedido.offset(inicio).length(comprimento);
            }
            if (versao != null && !versao.isBlank()) {
                pedido.versionId(versao);
            }
            return minioClient.getObject(pedido.build());
        } catch (ErrorResponseException e) {
            if (OBJETO_INEXISTENTE.contains(e.errorResponse().code())) {
                throw new RecursoNaoEncontradoException("Objeto não encontrado no armazenamento.");
            }
            throw new StorageIndisponivelException(MSG_INDISPONIVEL, e);
        } catch (Exception e) {
            throw new StorageIndisponivelException(MSG_INDISPONIVEL, e);
        }
    }

    @Override
    public List<VersaoObjeto> versoes(String caminho) {
        try {
            List<VersaoObjeto> versoes = new ArrayList<>();
            Iterable<Result<Item>> resultados = minioClient.listObjects(ListObjectsArgs.builder()
                    .bucket(bucket).prefix(caminho).includeVersions(true).build());
            for (Result<Item> resultado : resultados) {
                Item item = resultado.get();
                if (caminho.equals(item.objectName())) {
                    boolean retida = !item.isDeleteMarker() && retida(caminho, item.versionId());
                    versoes.add(new VersaoObjeto(item.versionId(), retida));
                }
            }
            return versoes;
        } catch (StorageIndisponivelException e) {
            throw e;
        } catch (Exception e) {
            throw new StorageIndisponivelException(MSG_INDISPONIVEL, e);
        }
    }

    /** Nunca envia `bypassGovernanceMode`: a identidade da aplicação não pode encurtar nem levantar uma retenção. */
    @Override
    public void reter(String caminho, String versionId, Instant ate) {
        try {
            minioClient.setObjectRetention(SetObjectRetentionArgs.builder()
                    .bucket(bucket).object(caminho).versionId(versao(versionId))
                    .config(new Retention(RetentionMode.GOVERNANCE, ate.atZone(ZoneOffset.UTC)))
                    .build());
        } catch (Exception e) {
            throw new StorageIndisponivelException(MSG_INDISPONIVEL, e);
        }
    }

    @Override
    public boolean temRetencao(String caminho, String versionId) {
        return retidoAte(caminho, versionId).isPresent();
    }

    @Override
    public Optional<Instant> retidoAte(String caminho, String versionId) {
        try {
            return retencaoAtiva(caminho, versao(versionId));
        } catch (Exception e) {
            throw new StorageIndisponivelException(MSG_INDISPONIVEL, e);
        }
    }

    /** Retenção com data no futuro ou legal hold. Sem Object Lock no bucket, nenhuma versão está retida. */
    private boolean retida(String caminho, String versionId) throws Exception {
        String versao = versao(versionId);
        if (retencaoAtiva(caminho, versao).isPresent()) {
            return true;
        }
        try {
            return minioClient.isObjectLegalHoldEnabled(IsObjectLegalHoldEnabledArgs.builder()
                    .bucket(bucket).object(caminho).versionId(versao).build());
        } catch (ErrorResponseException e) {
            if (SEM_RETENCAO.contains(e.errorResponse().code())) {
                return false;
            }
            throw e;
        }
    }

    private Optional<Instant> retencaoAtiva(String caminho, String versao) throws Exception {
        try {
            Retention retencao = minioClient.getObjectRetention(GetObjectRetentionArgs.builder()
                    .bucket(bucket).object(caminho).versionId(versao).build());
            if (retencao == null || retencao.mode() == null || retencao.retainUntilDate() == null
                    || !retencao.retainUntilDate().isAfter(ZonedDateTime.now())) {
                return Optional.empty();
            }
            return Optional.of(retencao.retainUntilDate().toInstant());
        } catch (ErrorResponseException e) {
            if (SEM_RETENCAO.contains(e.errorResponse().code())) {
                return Optional.empty();
            }
            throw e;
        }
    }

    private static String versao(String versionId) {
        return versionId == null || versionId.isBlank() ? null : versionId;
    }
}
