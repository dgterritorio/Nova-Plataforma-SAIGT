package com.conkord.saigt.service.documento;

import com.conkord.saigt.exception.LinkPublicoNaoPermitidoException;
import com.conkord.saigt.persistence.operacional.DestinoLinkPublico;
import com.conkord.saigt.persistence.operacional.DocumentoLinkPublico;
import com.conkord.saigt.persistence.operacional.DocumentoLinkPublicoRepository;
import com.conkord.saigt.persistence.operacional.MotivoRevogacaoLink;
import com.conkord.saigt.persistence.operacional.ProcessoDocumento;
import com.conkord.saigt.service.documento.LinkPublicoService.LinkPublico;
import com.conkord.saigt.service.incm.EnvioIncmTesteBase;
import com.conkord.saigt.service.incm.LinksPublicosIncm;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = "app.publico.base-url=https://saigt.teste/")
@ActiveProfiles("test")
class LinkPublicoServiceIntegrationTest extends EnvioIncmTesteBase {

    @Autowired
    private LinkPublicoService linkPublicoService;

    @Autowired
    private DocumentoLinkPublicoRepository linkRepository;

    @Autowired
    private LinksPublicosIncm linksPublicosIncm;

    @Test
    void deveCriarUmLinkComCommitProprioEReutilizaloNoPedidoSeguinte() {
        Cenario cenario = criarCenario("Aviso de início");

        LinkPublico primeiro = linkPublicoService.obterOuCriar(cenario.publicacao().getId(), DestinoLinkPublico.INCM);
        LinkPublico segundo = linkPublicoService.obterOuCriar(cenario.publicacao().getId(), DestinoLinkPublico.INCM);

        DocumentoLinkPublico link = linksAtivos(cenario.publicacao()).get(0);
        assertThat(segundo).isEqualTo(primeiro);
        assertThat(primeiro.url()).isEqualTo("https://saigt.teste/api/v1/publico/" + link.getToken() + "/Regulamento.pdf");
        assertThat(link.getToken().version()).isEqualTo(4);
    }

    @Test
    void deveTerUmLinkAtivoPorDestino() {
        Cenario cenario = criarCenario("Aviso de início");

        LinkPublico incm = linkPublicoService.obterOuCriar(cenario.publicacao().getId(), DestinoLinkPublico.INCM);
        LinkPublico pcgt = linkPublicoService.obterOuCriar(cenario.publicacao().getId(), DestinoLinkPublico.PCGT);

        assertThat(incm.url()).isNotEqualTo(pcgt.url());
        assertThat(linksAtivos(cenario.publicacao())).extracting(DocumentoLinkPublico::getDestino)
                .containsExactlyInAnyOrder(DestinoLinkPublico.INCM, DestinoLinkPublico.PCGT);
    }

    @Test
    void deveRecusarSemCriarLinhaUmTipoSemLinkPublicoPermitido() {
        Cenario cenario = criarCenario("Aviso de início");
        ProcessoDocumento deposito = cenario.deposito();

        assertThatThrownBy(() -> linkPublicoService.obterOuCriar(deposito.getId(), DestinoLinkPublico.INCM))
                .isInstanceOf(LinkPublicoNaoPermitidoException.class);

        assertThat(linkRepository.findAll()).noneMatch(link -> link.getProcessoDocumento().getId()
                .equals(deposito.getId()));
    }

    @Test
    void deveRevogarNaSubstituicaoELimparOUrlDaPlanta() {
        Cenario cenario = criarCenario("Aviso de início");
        ProcessoDocumento zip = adicionarPlantaComNomeTiff(cenario, "Planta.zip", "Planta.tif");
        ProcessoDocumento imagem = adicionarImagemDePublicacao(cenario, "Planta.jpg");
        transactionTemplate.executeWithoutResult(estado ->
                linksPublicosIncm.preparar(processoRepository.findById(cenario.processo().getId()).orElseThrow()));
        assertThat(linkJpeg(zip)).isNotNull();

        transactionTemplate.executeWithoutResult(estado ->
                linkPublicoService.revogar(imagem.getId(), MotivoRevogacaoLink.SUBSTITUICAO));

        assertThat(linksAtivos(imagem)).isEmpty();
        assertThat(linkRepository.findAll()).filteredOn(link -> link.getProcessoDocumento().getId().equals(imagem.getId()))
                .singleElement().satisfies(link -> {
                    assertThat(link.getRevogadoEm()).isNotNull();
                    assertThat(link.getMotivoRevogacao()).isEqualTo(MotivoRevogacaoLink.SUBSTITUICAO);
                });
        assertThat(linkJpeg(zip)).isNull();
    }

    @Test
    void deveLimparOUrlDaPlantaNaRemocaoDoJpeg() {
        Cenario cenario = criarCenario("Aviso de início");
        ProcessoDocumento zip = adicionarPlantaComNomeTiff(cenario, "Planta.zip", "Planta");
        ProcessoDocumento imagem = adicionarImagemDePublicacao(cenario, "Planta.jpeg");
        transactionTemplate.executeWithoutResult(estado ->
                linksPublicosIncm.preparar(processoRepository.findById(cenario.processo().getId()).orElseThrow()));
        assertThat(linkJpeg(zip)).isNotNull();

        transactionTemplate.executeWithoutResult(estado ->
                linkPublicoService.revogar(imagem.getId(), MotivoRevogacaoLink.REMOCAO));

        assertThat(linkJpeg(zip)).isNull();
    }

    @Test
    void deveCriarUmLinkNovoDepoisDeRevogado() {
        Cenario cenario = criarCenario("Aviso de início");
        LinkPublico antigo = linkPublicoService.obterOuCriar(cenario.publicacao().getId(), DestinoLinkPublico.INCM);
        transactionTemplate.executeWithoutResult(estado ->
                linkPublicoService.revogar(cenario.publicacao().getId(), MotivoRevogacaoLink.REMOCAO));

        LinkPublico novo = linkPublicoService.obterOuCriar(cenario.publicacao().getId(), DestinoLinkPublico.INCM);

        assertThat(novo.url()).isNotEqualTo(antigo.url());
        assertThat(linksAtivos(cenario.publicacao())).hasSize(1);
    }

    @Test
    void deveMascararOTokenDosLinksPublicos() {
        String texto = "{\"FileURLList\":[\"https://saigt.pt/api/v1/publico/0b9e6d2c-5f1a-4c3e-9a7b-2d8f1e4c6a90/"
                + "Regulamento.pdf\"]} GET /api/v1/publico/abc/x.pdf";

        assertThat(LinkPublicoService.mascararTokens(texto))
                .isEqualTo("{\"FileURLList\":[\"https://saigt.pt/api/v1/publico/***/Regulamento.pdf\"]} "
                        + "GET /api/v1/publico/***/x.pdf");
    }
}
