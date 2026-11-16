package com.conkord.saigt.service.instrumento;

import com.conkord.saigt.dto.core.PageResponse;
import com.conkord.saigt.dto.instrumento.IgtSnitResponse;
import com.conkord.saigt.exception.RecursoNaoEncontradoException;
import com.conkord.saigt.exception.ReferenciaDuplicadaException;
import com.conkord.saigt.exception.SnitIndisponivelException;
import com.conkord.saigt.exception.ValidacaoException;
import com.conkord.saigt.integracao.snit.ClienteSnit;
import com.conkord.saigt.integracao.snit.IgtSnit;
import com.conkord.saigt.integracao.snit.PaginaSnit;
import com.conkord.saigt.persistence.core.TipoInstrumento;
import com.conkord.saigt.persistence.core.TipoInstrumentoRepository;
import com.conkord.saigt.persistence.operacional.Instrumento;
import com.conkord.saigt.persistence.operacional.InstrumentoRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * IGT que já existem no SNIT. A pesquisa e a consulta para a adoção correm sem transação, para não segurarem uma ligação
 * à base de dados durante a chamada HTTP.
 */
@Service
public class InstrumentoSnitService {

    static final String IGT_JA_EXISTE = "O IGT já existe na SAIGT.";

    private static final int TAMANHO_MAXIMO_DESIGNACAO = 255;

    private final ClienteSnit clienteSnit;
    private final TipoInstrumentoRepository tipoInstrumentoRepository;
    private final InstrumentoRepository instrumentoRepository;

    public InstrumentoSnitService(ClienteSnit clienteSnit, TipoInstrumentoRepository tipoInstrumentoRepository,
                                  InstrumentoRepository instrumentoRepository) {
        this.clienteSnit = clienteSnit;
        this.tipoInstrumentoRepository = tipoInstrumentoRepository;
        this.instrumentoRepository = instrumentoRepository;
    }

    /** `page` conta a partir de 0, como no resto da API; a conversão para o SNIT (a partir de 1) fica aqui. */
    public PageResponse<IgtSnitResponse> pesquisar(Long tipoInstrumentoId, String municipio, int page, int size) {
        TipoInstrumento tipo = tipoInstrumentoRepository.findById(tipoInstrumentoId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Tipo de Instrumento não encontrado."));
        PaginaSnit pagina = clienteSnit.pesquisar(tipo.getCodigo(), municipio, page + 1, size);

        List<IgtSnit> igts = pagina.items().stream().filter(igt -> igt.idIgt() != null).toList();
        Map<String, UUID> existentes = existentesVisiveis(igts);
        List<IgtSnitResponse> conteudo = igts.stream()
                .map(igt -> new IgtSnitResponse(String.valueOf(igt.idIgt()), igt.designation(), igt.typeCode(),
                        existentes.get(String.valueOf(igt.idIgt()))))
                .toList();
        int totalPaginas = (int) ((pagina.total() + size - 1) / size);
        return new PageResponse<>(conteudo, page, size, pagina.total(), totalPaginas);
    }

    /**
     * O SNIT é a fonte do tipo e da designação: o pedido só diz qual IGT e de que tipo o utilizador julga que ele é.
     * Um IGT que já existe na SAIGT dá 409 mesmo que a sessão o não veja: a pesquisa só devolve o `instrumentoId`
     * dos visíveis.
     */
    public IgtSnit obterParaAdocao(String idIgtSnit, TipoInstrumento tipo) {
        if (instrumentoRepository.existsByIdIgtSnit(idIgtSnit)) {
            throw new ReferenciaDuplicadaException(IGT_JA_EXISTE);
        }
        IgtSnit igt = clienteSnit.obter(Long.parseLong(idIgtSnit))
                .orElseThrow(() -> new RecursoNaoEncontradoException("IGT não encontrado no SNIT."));
        if (igt.designation() == null || igt.designation().isBlank()) {
            throw new SnitIndisponivelException("Resposta do SNIT inválida.");
        }
        if (!igt.emVigor()) {
            throw campoInvalido("idIgtSnit", "O IGT não está em vigor no SNIT.");
        }
        if (!tipo.getCodigo().equals(igt.typeCode())) {
            throw campoInvalido("tipoInstrumentoId", "O IGT do SNIT é de outro tipo de Instrumento.");
        }
        return igt;
    }

    /** A coluna `designacao` tem 255 caracteres; o excesso da designação do SNIT é cortado. */
    public static String designacaoLocal(IgtSnit igt) {
        String designacao = igt.designation().strip();
        return designacao.length() > TAMANHO_MAXIMO_DESIGNACAO ? designacao.substring(0, TAMANHO_MAXIMO_DESIGNACAO) : designacao;
    }

    private Map<String, UUID> existentesVisiveis(List<IgtSnit> igts) {
        if (igts.isEmpty()) {
            return Map.of();
        }
        List<String> ids = igts.stream().map(igt -> String.valueOf(igt.idIgt())).toList();
        return instrumentoRepository.findAllByIdIgtSnitIn(ids).stream()
                .collect(Collectors.toMap(Instrumento::getIdIgtSnit, Instrumento::getId, (primeiro, segundo) -> primeiro));
    }

    private static ValidacaoException campoInvalido(String campo, String erro) {
        return new ValidacaoException("Payload inválido.", List.of(new ValidacaoException.CampoErro(campo, erro)));
    }
}
