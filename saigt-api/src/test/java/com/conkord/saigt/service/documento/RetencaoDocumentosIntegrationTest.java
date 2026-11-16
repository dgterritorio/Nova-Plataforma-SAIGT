package com.conkord.saigt.service.documento;

import com.conkord.saigt.configuration.StorageProperties;
import com.conkord.saigt.exception.StorageIndisponivelException;
import com.conkord.saigt.persistence.core.Auditoria;
import com.conkord.saigt.persistence.operacional.ProcessoDocumento;
import com.conkord.saigt.service.incm.EnvioIncmTesteBase;
import io.minio.GetObjectRetentionArgs;
import io.minio.MinioClient;
import io.minio.messages.Retention;
import io.minio.messages.RetentionMode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.context.ActiveProfiles;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Contra o RustFS real: o bucket de teste é criado pelo {@code InicializadorBucket}, com Object Lock. */
@SpringBootTest(properties = "app.publico.base-url=https://saigt.teste/")
@ActiveProfiles("test")
@ExtendWith(OutputCaptureExtension.class)
class RetencaoDocumentosIntegrationTest extends EnvioIncmTesteBase {

    @Autowired
    private RetencaoDocumentosService retencao;

    @Autowired
    private ArmazenamentoDocumentos armazenamentoDocumentos;

    @Autowired
    private MinioClient minioClient;

    @Autowired
    private StorageProperties storageProperties;

    @Test
    void deveReterCadaVersaoAtualEmGovernanceAteAoPrazoConfiguradoEAuditar(CapturedOutput saida) throws Exception {
        Cenario cenario = criarCenario("Aviso de início");
        ProcessoDocumento terceiro = adicionarDocumentoDePublicacao(cenario, "Relatorio.pdf", false);
        ZonedDateTime limiteEsperado = ZonedDateTime.now(ZoneOffset.UTC).plusYears(100);

        int retidos = reter(cenario, MomentoRetencao.ENTRADA_VALIDADO_DGT);

        assertThat(retidos).isEqualTo(3);
        for (ProcessoDocumento documento : List.of(cenario.publicacao(), cenario.deposito(), terceiro)) {
            Retention retencaoDaVersao = retencaoDe(documento);
            assertThat(retencaoDaVersao.mode()).isEqualTo(RetentionMode.GOVERNANCE);
            assertThat(Duration.between(limiteEsperado, retencaoDaVersao.retainUntilDate()).abs())
                    .isLessThan(Duration.ofMinutes(5));
        }
        assertThat(auditoria(cenario.processo())).extracting(Auditoria::getAcao).containsExactly("RETER_DOCUMENTOS");
        assertThat(auditoria(cenario.processo()).get(0).getDadosAlteracao())
                .contains("ENTRADA_VALIDADO_DGT").contains("\"ficheirosRetidos\":3");
        assertThat(saida.getOut()).contains("ficheirosRetidos=3")
                .doesNotContain(cenario.publicacao().getCaminhoFicheiro())
                .doesNotContain(storageProperties.secretKey());
    }

    @Test
    void deveReterNaSaidaSoODocumentoAcrescentadoSemAlterarAsVersoesJaRetidas() throws Exception {
        Cenario cenario = criarCenario("Aviso de início");
        reter(cenario, MomentoRetencao.ENTRADA_VALIDADO_DGT);
        Instant limiteAnterior = retencaoDe(cenario.publicacao()).retainUntilDate().toInstant();
        ProcessoDocumento acrescentado = adicionarDocumentoDePublicacao(cenario, "Anexo-PCM.pdf", false);
        assertThat(temRetencao(acrescentado)).isFalse();

        int retidos = reter(cenario, MomentoRetencao.SAIDA_VALIDADO_DGT);

        assertThat(retidos).isEqualTo(1);
        assertThat(temRetencao(acrescentado)).isTrue();
        assertThat(retencaoDe(cenario.publicacao()).retainUntilDate().toInstant()).isEqualTo(limiteAnterior);
    }

    @Test
    void naoDeveReterNadaNumaSegundaChamadaComTudoJaRetido() {
        Cenario cenario = criarCenario("Aviso de início");
        reter(cenario, MomentoRetencao.ENTRADA_VALIDADO_DGT);

        int retidos = reter(cenario, MomentoRetencao.ENTRADA_PUBLICADO);

        assertThat(retidos).isZero();
        assertThat(auditoria(cenario.processo())).hasSize(2);
    }

    @Test
    void deveRecusarRemoverUmaVersaoRetidaEMantela() {
        Cenario cenario = criarCenario("Aviso de início");
        reter(cenario, MomentoRetencao.ENTRADA_VALIDADO_DGT);
        ProcessoDocumento documento = recarregar(cenario.publicacao());

        assertThatThrownBy(() -> armazenamentoDocumentos.remover(documento.getCaminhoFicheiro()))
                .isInstanceOf(StorageIndisponivelException.class);

        assertThat(armazenamentoDocumentos.versoes(documento.getCaminhoFicheiro()))
                .extracting(VersaoObjeto::versionId).contains(documento.getVersaoObjeto());
    }

    private int reter(Cenario cenario, MomentoRetencao momento) {
        return transactionTemplate.execute(estado ->
                retencao.reterDocumentosSemRetencao(recarregar(cenario.processo()), momento));
    }

    private boolean temRetencao(ProcessoDocumento documento) {
        ProcessoDocumento atual = recarregar(documento);
        return armazenamentoDocumentos.temRetencao(atual.getCaminhoFicheiro(), atual.getVersaoObjeto());
    }

    private Retention retencaoDe(ProcessoDocumento documento) throws Exception {
        ProcessoDocumento atual = recarregar(documento);
        return minioClient.getObjectRetention(GetObjectRetentionArgs.builder().bucket(storageProperties.bucket())
                .object(atual.getCaminhoFicheiro()).versionId(atual.getVersaoObjeto()).build());
    }
}
