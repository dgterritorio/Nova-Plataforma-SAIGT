package com.conkord.saigt.dto.instrumento;

import com.conkord.saigt.persistence.configuracao.InstrumentoProcedimentoConfig;
import com.conkord.saigt.persistence.core.ListaValores;
import com.conkord.saigt.persistence.core.TipoInstrumento;
import com.conkord.saigt.persistence.operacional.Dinamica;
import com.conkord.saigt.persistence.operacional.Instrumento;
import com.conkord.saigt.persistence.operacional.Processo;
import com.conkord.saigt.service.instrumento.CamposFormularioInicial;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Superconjunto exato de {@link ProcessoResponse}, para o frontend reutilizar o mesmo componente de formulário.
 * Construído dentro da transação de leitura: navega associações LAZY (open-in-view desligado).
 */
public record ProcessoDetalheResponse(
        UUID id,
        String numeroProcesso,
        UUID dinamicaId,
        Long instrumentoProcedimentoConfigId,
        String estado,
        int ocorrencia,
        /** Submissões já feitas (0 antes da primeira): a do IDUS ou, sem IDUS, as transições para `ENVIADO_DGT`. */
        int submissao,
        long versao,
        String origemDados,
        Long tipoAtoId,
        UUID criadoPorId,
        String dadosFormulario,
        Instant dataSubmissao,
        UUID pcmAtribuidoAId,
        Instant pcmDevolvidoEm,
        List<String> camposBloqueados,

        /** O Instrumento de início da Dinâmica; nulo numa Dinâmica iniciada sem IGT. */
        UUID instrumentoId,
        /** A do Instrumento de início ou, sem ele, o `descritivo` da Dinâmica. */
        String designacaoInstrumento,
        String numeroInstrumento,
        /** Do tipo de Instrumento da configuração da Dinâmica, que existe sempre. */
        String tipoInstrumento,
        /** `PLANO`, `PROGRAMA` ou `SRUP`: decide os campos do formulário inicial que dependem da categoria. */
        String categoriaInstrumento,
        /** Nos Programas, se a entidade instrutora é obrigatória (`tipo_instrumento.entidade_instrutora_obrigatoria`). */
        boolean entidadeInstrutoraObrigatoria,
        List<InstrumentoAbrangidoResponse> instrumentosAbrangidos,
        /** `UM`, `UM_OU_MAIS` ou `OPCIONAL`: fora de `UM` os Instrumentos associam-se no ecrã dos documentos. */
        String associacaoInstrumentos,
        /** Os tipos que a Dinâmica admite na associação; vazia em `UM`. */
        List<TipoInstrumentoResponse> tiposInstrumentoAdmitidos,
        /** O chamador pode chamar `PUT /dinamicas/{id}/instrumentos` agora: autor deste processo em `EM_SUBMISSAO`. */
        boolean podeAssociarInstrumentos,
        TerritorioInstrumentoResponse territorio,
        /** Nulo quando o Processo não tem IDUS: fora de Planos, ou sem os dados para o compor. */
        String idus,
        /** Para o `MASTER` listar os candidatos a novo autor (`GET /utilizadores?entidadeId=`). */
        Long entidadeId,
        String entidadeNif,
        String entidadeNome,
        String tipoDinamica,
        String nomeDinamica,
        String numeroDinamica,
        String descritivoDinamica,
        String estadoDinamica,
        /** Data limite da Dinâmica (data de caducidade); nula enquanto a publicação da Decisão de início não a fixou. */
        LocalDate dataCaducidadeDinamica,
        String tipoProcedimento,
        String nomeProcedimento,
        /**
         * A série efetiva: a do Procedimento ou, sem ela, a escolhida no processo. Decide os campos fixos de submissão
         * pedidos pela INCM (§16.1); nula enquanto não há nenhuma.
         */
        String serieDr,
        /** As séries que o processo pode escolher, só nos Procedimentos sem série configurada; nula nos outros. */
        List<String> seriesDrEscolhiveis,
        String formulario,
        /** Nulo enquanto o tipo de ato não foi escolhido no cabeçalho. */
        String tipoAtoNome,
        String autorNome,

        String sumario,
        LocalDate dataAssinatura,
        /** Sem Informação Base do Texto do Ato no Procedimento: a data indica-se no cartão «Informação Geral» do formulário. */
        boolean dataAssinaturaNoFormulario,
        String normaLegal,
        String tipoInformacao,
        Boolean publicarSuplemento,
        LocalDate dataSuplemento,
        String contactoTramitacao,
        /** Só nos Programas; nula nas outras categorias. */
        String entidadeInstrutora,
        String entidadeEmitente,
        String contactoPublicacao,
        List<String> elementosAfetados,
        /** Falso na Elaboração e fora dos Planos: o formulário não mostra a pergunta e os itens não dependem dela. */
        boolean exigeElementosAfetados,
        /** Sem GeoConform, o assistente mostra as páginas das Plantas e dos Metadados (EF v1.5 §3.1.2 e §3.6). */
        boolean exigePlantasEMetadados,
        /** A submissão exige `comGeoconform`: pela configuração do Procedimento e pelos `elementosAfetados` gravados. */
        boolean exigeOpcaoGeoconform,
        /** Só a configuração do Procedimento: o formulário mostra a pergunta antes de gravar os `elementosAfetados`. */
        String perguntaGeoconform,
        /** Resposta guardada na Dinâmica; nula enquanto não foi dada ou se a Dinâmica não pergunta. */
        Boolean comGeoconform,
        /** Só nos Procedimentos configurados para a pedir (EF v1.5 §3.1.2, nota *4). */
        boolean pedeFundamentacaoRjigt,
        /** Vazia por omissão, nunca nula. */
        List<String> fundamentacaoRjigt,
        /** Itens ativos por `ordem`, só quando `pedeFundamentacaoRjigt`: os externos não leem `GET /lista-valores`. */
        List<OpcaoListaResponse> opcoesFundamentacaoRjigt,
        /** `NAO`, `OPCIONAL` ou `OBRIGATORIA`; com a lista `FREGUESIA` vazia o campo não aparece nem é obrigatório. */
        String pedeLocalizacao,
        /** `NAO`, `COM_PERGUNTA` ou `OBRIGATORIO`; as listas `MOTIVO_ALTERACAO_*` vazias escondem os motivos. */
        String pedeMotivoAlteracao,
        /** Nula enquanto não foi indicada. */
        LocalizacaoResponse localizacao,
        Boolean conformaHierarquiaSuperior,
        /** Vazia por omissão, nunca nula. */
        List<MotivoAlteracaoResponse> motivosAlteracao,
        /** Só com `formulario = SGIFR` (nulas nos outros): as gravadas e as que o programa aceita, por `codigo`. */
        List<RedeDefesaResponse> redesDefesa,
        List<RedeDefesaResponse> redesDefesaDisponiveis,
        /** Só com `formulario = REN` (nulos nos outros): o motivo gravado e os valores ativos por `ordem`; vazia sem valores ativos. */
        MotivoProcedimentoResponse motivoProcedimento,
        List<MotivoProcedimentoResponse> motivosProcedimentoDisponiveis,
        /** Só com `formulario = REN`: a Dinâmica pede o «Diploma que corrige ou retifica». */
        Boolean pedeDiplomaCorrigido,
        String diplomaCorrigido,
        /** Só com `formulario = REN`: o tipo de informação imposto pela Dinâmica, ou nulo. */
        String tipoInformacaoFixo,
        /** Só com `formulario = REN`: códigos `NUT2` e `MUNICIPALITIES` da réplica do SNIT. */
        String regiaoCodigo,
        String municipioCodigo,
        /**
         * Só com `formulario = SGIFR` (opções e valores nulos nos outros). A Região pede-se na Delimitação; a CIM / AM
         * só no PSA, filtrada pela Região na Delimitação. Uma lista sem valores ativos não pede o campo. Os valores são
         * o `codigo` gravado, com a descrição atual.
         */
        boolean pedeRegiao,
        List<OpcaoListaResponse> opcoesRegiao,
        String regiao,
        String regiaoDescricao,
        boolean pedeCimAm,
        List<OpcaoCimAmResponse> opcoesCimAm,
        String cimAm,
        String cimAmDescricao,

        String numeroDr,
        LocalDate dataPublicacaoDr,
        /** Hiperligação do ato no Diário da República, devolvida pela INCM; nula até o Processo ser publicado. */
        String linkDr,
        /** Só no procedimento final publicado; nulo até ser atribuído. */
        String numeroDeposito,
        String incmActIdentifier,
        Instant criadoEm,
        Instant atualizadoEm,

        /** Nula quando a tentativa nunca foi rejeitada pela DGT; mantém-se depois de voltar a submeter. */
        UltimaRejeicaoDgtResponse ultimaRejeicaoDgt,
        /** Nula quando a INCM nunca recusou o Processo; mantém-se depois de voltar a submeter. */
        UltimaRejeicaoIncmResponse ultimaRejeicaoIncm,

        /** O chamador é o autor e o estado permite editar o formulário e a publicação INCM. */
        boolean editavel,
        /** Segmentos de `PATCH /processos/{id}/<acao>` que o chamador pode executar agora. */
        List<String> acoesDisponiveis,
        /** O autor pode editar e `pendenciasSubmissao` está vazia; não inclui a Camada 3c. A PCM nunca submete. */
        boolean podeSubmeter,
        /** Vazia quando o chamador não é o autor a poder editar; senão, o que falta para `podeSubmeter`, pela ordem das páginas. */
        List<PendenciaSubmissaoResponse> pendenciasSubmissao,

        /** Por ordem de elemento; vazia enquanto o Processo não foi capturado pela DGT. */
        List<InformacaoBaseResponse> informacaoBase,
        /** A regra que `validar` aplica: a UI não a repete. */
        boolean todosItensRevistos,
        List<RejeicaoEmValidacaoResponse> rejeicoesEmValidacao,
        /** Na Revogação do IGT, as outras Dinâmicas em curso que a submissão declara terminar; vazia nas outras. */
        List<DinamicaATerminarResponse> dinamicasATerminar,

        /** A Dinâmica do IGT a que o processo se associa; nula sem associação. */
        UUID dinamicaAssociadaId,
        /** A Dinâmica do processo admite a associação (`admite_associacao_igt` e tipos associáveis configurados). */
        boolean aceitaDinamicaAssociada,
        /** Os dados da associação gravada, como em `GET /processos/{id}/dinamicas-associaveis`; nula sem associação. */
        DinamicaAssociavelResponse dinamicaAssociada,

        /** `PUBLICACAO_DEPOSITO` ou `SO_DEPOSITO`. */
        String destino,
        /** O formulário admite «só depósito», o chamador é o autor, o estado é editável e ainda não há ficheiros. */
        boolean destinoEditavel,
        /** `PUBLICACAO` ou `ALTERACAO`, só no formulário da Ratificação; nula enquanto não foi indicada. */
        String modalidadeRatificacao,
        /**
         * Só na Decisão de início de uma Tentativa a partir da segunda: a entidade aproveita os atos e anexos
         * anteriores. Informativo, para a validação DGT; nulo nos outros processos.
         */
        Boolean aproveitaAtosAnteriores,
        /** As opções do «Tipo de ato» do cabeçalho, com a regra do `/disponivel`; nunca nula. */
        List<TipoAtoDisponivelResponse> tiposAtoDisponiveis,

        /** O Procedimento é um ato pontual de «Outros atos e anexos» (FR-135). */
        boolean atoPontual,
        /** O ato publicado que o ato pontual visa; nulos fora dos atos pontuais. */
        UUID processoReferidoId,
        String numeroProcessoReferido,
        String tipoProcedimentoReferido,

        /** Campos do Resumo Validados gravados pelo `validar`; só para o ator `DGT`, nulos para os outros. */
        String notasIncm,
        String notasDgt,
        Boolean regUsoSolo,
        Long alteracaoPeotId,
        /** A descrição do item gravado, mesmo desativado depois, para a leitura do resumo. */
        String alteracaoPeotDescricao,
        /** Itens ativos de `ALTERACAO_PEOT`, só para o ator `DGT`; vazia sem itens (o campo não aparece). */
        List<OpcaoListaComIdResponse> opcoesAlteracaoPeot,
        /** Ator `DGT`, `VALIDADO_DGT` devolvido pela PCM e algum documento `POR_VALIDAR` (os que a PCM acrescentou). */
        boolean revalidacaoPcm
) {
    private static final List<String> SERIES_DR = List.of("PRIMEIRA", "SEGUNDA_H", "SEGUNDA_C");

    public static ProcessoDetalheResponse de(Processo processo, String formulario, TerritorioInstrumentoResponse territorio, String idus,
                                             int submissao,
                                             UltimaRejeicaoDgtResponse ultimaRejeicaoDgt,
                                             UltimaRejeicaoIncmResponse ultimaRejeicaoIncm, boolean dataAssinaturaNoFormulario,
                                             boolean editavel, List<String> acoesDisponiveis,
                                             boolean editavelPeloAutor, List<PendenciaSubmissaoResponse> pendenciasSubmissao,
                                             boolean exigeOpcaoGeoconform, boolean exigeElementosAfetados,
                                             boolean exigePlantasEMetadados,
                                             List<InformacaoBaseResponse> informacaoBase, boolean todosItensRevistos,
                                             List<String> fundamentacaoRjigt, List<OpcaoListaResponse> opcoesFundamentacaoRjigt,
                                             LocalizacaoResponse localizacao, Boolean conformaHierarquiaSuperior,
                                             List<MotivoAlteracaoResponse> motivosAlteracao,
                                             List<RedeDefesaResponse> redesDefesa,
                                             List<RedeDefesaResponse> redesDefesaDisponiveis,
                                             FormularioRenResponse ren,
                                             TerritorioSgifrResponse territorioSgifr,
                                             List<RejeicaoEmValidacaoResponse> rejeicoesEmValidacao,
                                             List<TipoInstrumentoResponse> tiposInstrumentoAdmitidos,
                                             boolean podeAssociarInstrumentos,
                                             List<DinamicaATerminarResponse> dinamicasATerminar,
                                             boolean aceitaDinamicaAssociada,
                                             DinamicaAssociavelResponse dinamicaAssociada,
                                             boolean destinoEditavel, String modalidadeRatificacao,
                                             List<TipoAtoDisponivelResponse> tiposAtoDisponiveis,
                                             boolean mostraResumoValidacao,
                                             List<OpcaoListaComIdResponse> opcoesAlteracaoPeot,
                                             boolean revalidacaoPcm) {
        Dinamica dinamica = processo.getDinamica();
        Instrumento instrumento = dinamica.instrumentoDeInicio().orElse(null);
        TipoInstrumento tipoInstrumento = dinamica.getInstrumentoDinamicaConfig().getTipoInstrumento();
        InstrumentoProcedimentoConfig config = processo.getInstrumentoProcedimentoConfig();
        Processo referido = processo.getProcessoReferido();
        ListaValores alteracaoPeot = mostraResumoValidacao ? processo.getAlteracaoPeot() : null;

        return new ProcessoDetalheResponse(
                processo.getId(),
                processo.getNumeroProcesso(),
                dinamica.getId(),
                config.getId(),
                processo.getEstado().name(),
                processo.getOcorrencia(),
                submissao,
                processo.getVersao(),
                processo.getOrigemDados(),
                processo.getTipoAto() == null ? null : processo.getTipoAto().getId(),
                processo.getCriadoPor().getId(),
                processo.getDadosFormulario(),
                processo.getDataSubmissao(),
                processo.getPcmAtribuidoA() == null ? null : processo.getPcmAtribuidoA().getId(),
                processo.getPcmDevolvidoEm(),
                CamposFormularioInicial.camposBloqueados(processo),

                instrumento == null ? null : instrumento.getId(),
                instrumento == null ? dinamica.getDescritivo() : instrumento.getDesignacao(),
                instrumento == null ? null : instrumento.getNumero(),
                tipoInstrumento.getCodigo(),
                tipoInstrumento.getCategoria(),
                tipoInstrumento.isEntidadeInstrutoraObrigatoria(),
                InstrumentoAbrangidoResponse.de(dinamica),
                java.util.Objects.toString(dinamica.getInstrumentoDinamicaConfig().getAssociacaoInstrumentos(), null),
                tiposInstrumentoAdmitidos,
                podeAssociarInstrumentos,
                territorio,
                idus,
                processo.getEntidade().getId(),
                processo.getEntidade().getNipc(),
                processo.getEntidade().getDescricaoIncm(),
                dinamica.getInstrumentoDinamicaConfig().getTipoDinamica().getCodigo(),
                dinamica.getInstrumentoDinamicaConfig().getTipoDinamica().getNome(),
                dinamica.getNumeroDinamica(),
                dinamica.getDescritivo(),
                dinamica.getEstado().name(),
                dinamica.getDataCaducidade(),
                config.getTipoProcedimento().getCodigo(),
                config.getTipoProcedimento().getNome(),
                processo.serieDrEfetiva(),
                processo.escolheSerieDr() ? SERIES_DR : null,
                formulario,
                processo.getTipoAto() == null ? null : processo.getTipoAto().getNome(),
                processo.getCriadoPor().getNome(),

                processo.getSumario(),
                processo.getDataAssinatura(),
                dataAssinaturaNoFormulario,
                processo.getNormaLegal(),
                processo.getTipoInformacao(),
                processo.getPublicarSuplemento(),
                processo.getDataSuplemento(),
                processo.getContactoTramitacao(),
                processo.getEntidadeInstrutora(),
                processo.getEntidadeEmitente(),
                processo.getContactoPublicacao(),
                processo.getElementosAfetados(),
                exigeElementosAfetados,
                exigePlantasEMetadados,
                exigeOpcaoGeoconform,
                config.getPerguntaGeoconform().name(),
                dinamica.getComGeoconform(),
                config.isPedeFundamentacaoRjigt(),
                fundamentacaoRjigt,
                opcoesFundamentacaoRjigt,
                config.getPedeLocalizacao().name(),
                config.getPedeMotivoAlteracao().name(),
                localizacao,
                conformaHierarquiaSuperior,
                motivosAlteracao,
                redesDefesa,
                redesDefesaDisponiveis,
                ren == null ? null : ren.motivoProcedimento(),
                ren == null ? null : ren.motivosProcedimentoDisponiveis(),
                ren == null ? null : ren.pedeDiplomaCorrigido(),
                ren == null ? null : ren.diplomaCorrigido(),
                ren == null ? null : ren.tipoInformacaoFixo(),
                ren == null ? null : ren.regiaoCodigo(),
                ren == null ? null : ren.municipioCodigo(),
                territorioSgifr.pedeRegiao(),
                territorioSgifr.opcoesRegiao(),
                territorioSgifr.regiao(),
                territorioSgifr.regiaoDescricao(),
                territorioSgifr.pedeCimAm(),
                territorioSgifr.opcoesCimAm(),
                territorioSgifr.cimAm(),
                territorioSgifr.cimAmDescricao(),

                processo.getNumeroDr(),
                processo.getDataPublicacaoDr(),
                processo.getLinkDr(),
                processo.getNumeroDeposito(),
                processo.getIncmActIdentifier(),
                processo.getCriadoEm(),
                processo.getAtualizadoEm(),
                ultimaRejeicaoDgt,
                ultimaRejeicaoIncm,
                editavel,
                acoesDisponiveis,
                editavelPeloAutor && pendenciasSubmissao.isEmpty(),
                editavelPeloAutor ? pendenciasSubmissao : List.of(),
                informacaoBase,
                todosItensRevistos,
                rejeicoesEmValidacao,
                dinamicasATerminar,
                processo.getDinamicaAssociada() == null ? null : processo.getDinamicaAssociada().getId(),
                aceitaDinamicaAssociada,
                dinamicaAssociada,
                processo.getDestino().name(),
                destinoEditavel,
                modalidadeRatificacao,
                processo.getAproveitaAtosAnteriores(),
                tiposAtoDisponiveis,
                config.isAtoPontual(),
                referido == null ? null : referido.getId(),
                referido == null ? null : referido.getNumeroProcesso(),
                referido == null ? null : referido.getInstrumentoProcedimentoConfig().getTipoProcedimento().getCodigo(),
                mostraResumoValidacao ? processo.getNotasIncm() : null,
                mostraResumoValidacao ? processo.getNotasDgt() : null,
                mostraResumoValidacao ? processo.getRegUsoSolo() : null,
                alteracaoPeot == null ? null : alteracaoPeot.getId(),
                alteracaoPeot == null ? null : alteracaoPeot.getDescricao(),
                opcoesAlteracaoPeot,
                revalidacaoPcm);
    }
}
