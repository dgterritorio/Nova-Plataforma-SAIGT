import type { TipoAtoDisponivel, TipoInstrumento } from './instrumento.model';

/** Grupos de estados das listagens e dos painéis (BL-PROC-050); os que se aplicam dependem do tipo de utilizador. */
export type GrupoProcesso = 'PUBLICADOS' | 'EM_CURSO' | 'PARA_CORRECAO' | 'EM_VALIDACAO';

export const GRUPOS_PROCESSO: readonly GrupoProcesso[] = ['PUBLICADOS', 'EM_CURSO', 'PARA_CORRECAO', 'EM_VALIDACAO'];

/** Estados do utilizador externo em cada grupo pessoal (BL-PROC-050); só estes têm ecrã próprio. */
export const ESTADOS_DO_GRUPO: Partial<Record<GrupoProcesso, readonly string[]>> = {
  EM_CURSO: ['EM_SUBMISSAO', 'ENVIADO_DGT'],
  PARA_CORRECAO: ['REJEITADO_DGT', 'ANULADO_INCM'],
};

/**
 * Situação do processo face à PCM, derivada na API: `COM_PCM` atribuído à PCM, `DEVOLVIDO` devolvido pela PCM nesta
 * passagem por `VALIDADO_DGT`, `SEM_PCM` sem nenhuma das duas.
 */
export type SituacaoPcm = 'SEM_PCM' | 'COM_PCM' | 'DEVOLVIDO';

/** O selo «Devolvido pela PCM»: só enquanto o processo devolvido está em `VALIDADO_DGT` (a nova validação limpa a data). */
export function devolvidoPelaPcm(processo: { estado: string; situacaoPcm?: SituacaoPcm | null }): boolean {
  return processo.estado === 'VALIDADO_DGT' && processo.situacaoPcm === 'DEVOLVIDO';
}

/** `estado` com vários valores (`?estado=X&estado=Y`); com `grupo`, a API combina os dois por interseção. */
export interface ProcessoFiltro {
  designacaoInstrumento?: string;
  numeroInstrumento?: string;
  estado?: string | string[];
  grupo?: GrupoProcesso;
  numeroProcesso?: string;
  ano?: number;
  entidadeId?: number;
  tipoInstrumentoId?: number;
  entidadeNif?: string;
  dataDe?: string;
  dataAte?: string;
  numeroDr?: string;
  incmActIdentifier?: string;
  /** `<TIPO>:<codigo>`; só os perfis internos o veem aplicado. */
  motivoAlteracao?: string | string[];
  /** Repetível; só os perfis internos o veem aplicado. */
  situacaoPcm?: SituacaoPcm | SituacaoPcm[];
}

/** `contagem` é o total do grupo, não o da página de `processos`. */
export interface GrupoPainel {
  grupo: GrupoProcesso;
  contagem: number;
  processos: ProcessoListagem[];
}

export interface PainelProcessos {
  grupos: GrupoPainel[];
}

export type AssociacaoInstrumentos = 'UM' | 'UM_OU_MAIS' | 'OPCIONAL';

export interface InstrumentoAbrangido {
  id: string;
  designacao: string;
  /** Código do tipo, ex.: `PDM`. */
  tipoInstrumento: string;
}

/** Uma Dinâmica em curso que a Revogação do IGT declara terminar; `tipoDinamica` é o nome, para apresentação. */
export interface DinamicaATerminar {
  dinamicaId: string;
  numeroDinamica: string | null;
  tipoDinamica: string;
  descritivo: string | null;
}

/** `TERMINADA` e `instrumento` nulo só no detalhe de uma associação gravada; a lista traz só `EM_CURSO` e `CADUCADA`. */
export interface DinamicaAssociavel {
  dinamicaId: string;
  numeroDinamica: string;
  descritivo: string;
  tipoDinamica: string;
  estado: 'EM_CURSO' | 'CADUCADA' | 'TERMINADA';
  instrumento: { id: string; designacao: string; tipoInstrumento: string } | null;
}

/** «Processo em simultâneo com a formação de PMOT», guardada em `dadosFormulario.simultaneoComPmot`. */
export type RespostaSimultaneoPmot = 'SIM' | 'NAO';

/** Escolhido antes do carregamento dos ficheiros: só para depósito, sem itens de publicação nem envio à INCM. */
export type DestinoProcesso = 'PUBLICACAO_DEPOSITO' | 'SO_DEPOSITO';

export const ROTULO_DESTINO: Record<DestinoProcesso, string> = {
  PUBLICACAO_DEPOSITO: 'Publicação e depósito',
  SO_DEPOSITO: 'Só depósito',
};

/** Sem `instrumentoId` a linha não navega para o Instrumento. */
export interface ProcessoListagem {
  id: string;
  /** N.º do processo, só dígitos. */
  numeroProcesso: string;
  instrumentoId?: string;
  designacaoInstrumento: string;
  /** Nulo numa Dinâmica iniciada sem IGT. */
  numeroInstrumento: string | null;
  tipoInstrumento: string;
  tipoDinamica: string;

  nomeDinamica: string;
  tipoProcedimento: string;

  nomeProcedimento: string;
  entidadeNif: string;
  entidadeNome: string;
  estado: string;
  atualizadoEm: string;
  autorNome: string;
  /** Só para os perfis internos; nulos para as entidades e para a PCM. */
  situacaoPcm?: SituacaoPcm | null;
  pcmDevolvidoEm?: string | null;
  /** Designações do SNIT tal como vêm, pela ordem dos códigos DTCC; vazio sem Instrumento ou sem concelhos. */
  concelhos: string[];
}

export type SerieDr = 'PRIMEIRA' | 'SEGUNDA_H' | 'SEGUNDA_C';

/** O tipo de ato escolhe-se depois, no cabeçalho do processo; o assistente já não o envia. */
export interface CriarProcessoRequest {
  instrumentoProcedimentoConfigId: number;
  tipoAtoId?: number | null;
  /** Obrigatório nos atos pontuais (um dos `atosReferiveis` do `/disponivel`) e proibido nos outros. */
  processoReferidoId?: string | null;
}

/** Procedimento publicado no DR fora da SAIGT; só o MASTER. `dataPublicacaoDr` em `AAAA-MM-DD`. */
export interface RegistoExternoRequest {
  instrumentoProcedimentoConfigId: number;
  /** Entidade a que o Processo passa a pertencer: o MASTER não tem entidade de sessão. */
  entidadeId: number;
  numeroDr: string;
  dataPublicacaoDr: string;
  motivo: string;
}

export type TipoInformacao = 'TEXTO_INTEGRAL' | 'EXTRATO';

/** `versao` lida no último detalhe (ou devolvida pela última escrita); diverge da atual → 409 `VERSAO_DESATUALIZADA`. */
export interface VersaoProcessoRequest {
  versao: number;
}

/** Além de `versao`, nenhum campo obrigatório aqui: a série do Procedimento decide, no backend. */
export interface SubmeterProcessoRequest extends VersaoProcessoRequest {
  sumario?: string;
  dataAssinatura?: string;
  normaLegal?: string;
  tipoInformacao?: TipoInformacao;
  publicarSuplemento?: boolean;
  dataSuplemento?: string;
  /** Opcional: sem ela vale a resposta gravada na Dinâmica pelo formulário; fora de `exigeOpcaoGeoconform` é recusada. */
  comGeoconform?: boolean;
  /** Chaves das confirmações exigidas: `CONTEUDO_PROCESSO` e, com `dinamicasATerminar`, `DECLARACAO_TERMINO`. */
  confirmacoes?: string[];
  motivoProcedimentoId?: number | null;
  diplomaCorrigido?: string | null;
  regiaoCodigo?: string | null;
  municipioCodigo?: string | null;
  /** Só no formulário `RATIFICACAO`; omitida, vale a gravada no rascunho. */
  modalidadeRatificacao?: ModalidadeRatificacao;
}

/** A concordância do passo 3 com o conteúdo do processo. */
export const CONFIRMACAO_CONTEUDO_PROCESSO = 'CONTEUDO_PROCESSO';

/** A declaração de que a Revogação do IGT termina as outras Dinâmicas em curso (BL-PROC-040). */
export const CONFIRMACAO_DECLARACAO_TERMINO = 'DECLARACAO_TERMINO';

/** `campos[].path` do 400 `CONFIRMACAO_EM_FALTA` sem a declaração de término. */
export const CAMPO_DECLARACAO_TERMINO = 'confirmacoes';

/** Rascunho dos campos de `SubmeterProcessoRequest`; `null` apaga o valor gravado. */
export interface PublicacaoIncmDados {
  sumario: string | null;
  dataAssinatura: string | null;
  normaLegal: string | null;
  tipoInformacao: TipoInformacao | null;
  publicarSuplemento: boolean | null;
  dataSuplemento: string | null;
}

/** Prazo e Avaliação Ambiental em comum; os limites de meses e dias não se impõem, o Figma não os define. */
export interface InformacaoBaseTextoAto {
  prazoAnos: number | null;
  prazoMeses: number | null;
  prazoDias: number | null;
  avaliacaoAmbiental: boolean | null;
}

export interface InformacaoBaseDeliberacao extends InformacaoBaseTextoAto {
  /** ISO `yyyy-MM-dd`. */
  dataDeliberacao: string | null;
  textoPublicar: string | null;
}

/** A data de assinatura do Texto do Ato não entra aqui: é `dataAssinatura`, no pedido. */
export interface InformacaoBase {
  deliberacao?: InformacaoBaseDeliberacao | null;
  textoAto?: InformacaoBaseTextoAto | null;
}

export type ElementoAfetado = 'PECAS_ESCRITAS' | 'PECAS_GRAFICAS' | 'OUTROS';

/** Valor de `MOTIVO_PROCEDIMENTO_REN`: o `id` é o que se envia em `motivoProcedimentoId`. */
export interface MotivoProcedimento {
  id: number;
  codigo: string;
  descricao: string;
}

/** Só no formulário `REN`: omitido ou nulo mantém o gravado; texto em branco apaga-o. */
export interface DadosFormularioRen {
  motivoProcedimentoId?: number | null;
  diplomaCorrigido?: string | null;
  regiaoCodigo?: string | null;
  municipioCodigo?: string | null;
}

export type FormularioFixo =
  | 'INFORMACAO_GERAL'
  | 'DISCUSSAO_PUBLICA'
  | 'PUBLICACAO_ALTERACAO'
  | 'SGIFR'
  | 'REN'
  | 'SRUP'
  | 'RATIFICACAO'
  | 'SUSPENSAO_CCDR'
  | 'SUSPENSAO_MP_GOVERNO'
  | 'PRORROGACAO_GOVERNO';

/** Rede de defesa do SGIFR: cada uma é uma folha do mesmo ato. */
export interface RedeDefesa {
  codigo: string;
  nome: string;
}

/** «Publicação – Ratificação» ou «Alteração – Ratificação» (EF v1.5 §3.1.2). */
export type ModalidadeRatificacao = 'PUBLICACAO' | 'ALTERACAO';

export const ROTULO_MODALIDADE_RATIFICACAO: Record<ModalidadeRatificacao, string> = {
  PUBLICACAO: 'Publicação – Ratificação',
  ALTERACAO: 'Alteração – Ratificação',
};

export type CategoriaInstrumento = 'PLANO' | 'PROGRAMA' | 'SRUP';

/**
 * Texto livre do formulário inicial; a entidade emitente é obrigatória em `submeter` nos Planos e Programas, a
 * instrutora só existe (e é obrigatória) nos Programas.
 */
export interface DadosEmitente {
  entidadeInstrutora: string | null;
  entidadeEmitente: string | null;
  contactoPublicacao: string | null;
}

export interface DadosTramitacao {
  contactoTramitacao: string | null;
  elementosAfetados: ElementoAfetado[] | null;
}

/** Códigos da lista `FUNDAMENTACAO_RJIGT`; opcional e só nos Procedimentos configurados para a pedir. Vazia grava nulo. */
export interface DadosFundamentacao {
  fundamentacaoRjigt: string[];
}

/** Item ativo de uma lista de valores, para escolha num formulário. */
export interface OpcaoLista {
  codigo: string;
  descricao: string;
}

/** CIM / AM do formulário SGIFR; `regiao` é o código da Região de que depende. */
export interface OpcaoCimAm extends OpcaoLista {
  regiao: string | null;
}

/** Item de uma lista de valores com o `id`, para os campos que gravam a referência ao item. */
export interface OpcaoListaComId extends OpcaoLista {
  id: number;
}

export type PedeLocalizacao = 'NAO' | 'OPCIONAL' | 'OBRIGATORIA';

export type PedeMotivoAlteracao = 'NAO' | 'COM_PERGUNTA' | 'OBRIGATORIO';

export type PerguntaGeoconform = 'NAO_APLICAVEL' | 'OBRIGATORIA' | 'SE_PECAS_GRAFICAS';

export type TipoMotivoAlteracao = 'MOTIVO_ALTERACAO_LEI' | 'MOTIVO_ALTERACAO_PROGRAMA' | 'MOTIVO_ALTERACAO_PLANO';

export type TipoListaFormulario = 'FREGUESIA' | 'TOPONIMO' | TipoMotivoAlteracao;

export interface LocalizacaoPedido {
  aplicavelTodaArea: boolean | null;
  freguesia: string | null;
  toponimoCodigo: string | null;
  toponimoTexto: string | null;
}

export interface MotivoAlteracao {
  tipo: TipoMotivoAlteracao;
  codigo: string;
}

export interface LocalizacaoDetalhe {
  aplicavelTodaArea: boolean | null;
  freguesia: OpcaoLista | null;
  toponimo: OpcaoLista | null;
  toponimoTexto: string | null;
}

export interface MotivoAlteracaoDetalhe extends MotivoAlteracao {
  descricao: string | null;
}

export interface DadosLocalizacaoMotivo {
  localizacao: LocalizacaoPedido | null;
  conformaHierarquiaSuperior: boolean | null;
  motivosAlteracao: MotivoAlteracao[];
}

/** Um só `PUT`: os campos INCM, os de tramitação e a Informação Base; o que se omite fica nulo. */
export interface AtualizarDadosFormularioRequest
  extends VersaoProcessoRequest,
    PublicacaoIncmDados,
    DadosTramitacao,
    DadosEmitente,
    Partial<DadosFundamentacao>,
    Partial<DadosLocalizacaoMotivo>,
    DadosFormularioRen {
  informacaoBase?: InformacaoBase | null;
  /** Só no formulário `SGIFR`, quando o programa deixa escolher as redes (PSA). */
  redesDefesa?: string[];
  /** Só no formulário `SGIFR`, quando o detalhe os pede (`pedeRegiao`, `pedeCimAm`): enviados noutro caso dão 400. */
  regiao?: string | null;
  cimAm?: string | null;
  /** Só quando o Procedimento pergunta (`exigeOpcaoGeoconform`); nulo ou omitido deixa a resposta gravada na Dinâmica. */
  comGeoconform?: boolean | null;
  /** Só na Publicação da Alteração, com Peças Gráficas e sem GeoConform; grava-se em `dadosFormulario`. */
  introduzPlantaNova?: boolean | null;
  /** Nula ou omitida, o processo fica sem associação; só se aceita com `aceitaDinamicaAssociada`. */
  dinamicaAssociadaId?: string | null;
  /** Só com `aceitaDinamicaAssociada`. A chave é a do LLD (DB-PROC-014); o contrato ainda não a declara e o servidor ignora-a. */
  dadosFormulario?: { simultaneoComPmot: RespostaSimultaneoPmot };
  /** Omitido, o servidor mantém o gravado (ao contrário dos outros campos). */
  destino?: DestinoProcesso | null;
  /** Só no formulário `RATIFICACAO`; nos outros o servidor não a grava. */
  modalidadeRatificacao?: ModalidadeRatificacao | null;
  /** O tipo de ato do cabeçalho, de `tiposAtoDisponiveis`; omitido, o servidor mantém o gravado. */
  tipoAtoId?: number | null;
  /** Só com `seriesDrEscolhiveis` no detalhe (restantes servidões); omitida, o servidor mantém a gravada. */
  serieDr?: SerieDr | null;
}

/** `motivoId` de `lista_valores` com `tipo = MOTIVO_REJEICAO_DGT`. */
/** Os campos opcionais do Resumo Validados, gravados na passagem a `VALIDADO_DGT`. */
export interface CamposResumoValidados {
  notasIncm: string | null;
  notasDgt: string | null;
  regUsoSolo: boolean | null;
  alteracaoPeotId: number | null;
}

export interface ValidarProcessoRequest extends VersaoProcessoRequest, CamposResumoValidados {}

export interface RejeitarProcessoRequest extends VersaoProcessoRequest {
  notasGerais: string | null;
}

/** Texto livre, registado em `auditoria`. */
export interface AnularProcessoRequest extends VersaoProcessoRequest {
  motivo: string;
}

/** `campo`: uma chave de `camposBloqueados`. O motivo, em texto livre, fica em `auditoria`. */
export interface DesbloquearCampoRequest extends VersaoProcessoRequest {
  campo: string;
  motivo: string;
}

/**
 * `novoUtilizadorId` tem de ter vínculo à entidade do Instrumento e não ser o autor atual. `pedidoApoioId`:
 * o pedido de apoio da entidade que esta transferência resolve (opcional).
 */
export interface ReatribuirAutorRequest extends VersaoProcessoRequest {
  novoUtilizadorId: string;
  pedidoApoioId?: string;
}

/** `utilizadorId`: utilizador de uma entidade PCM ativa; senão 400 `PCM_UTILIZADOR_INVALIDO` no campo. */
export interface EnviarPcmRequest extends VersaoProcessoRequest {
  utilizadorId: string;
}

/** As observações são opcionais e ficam só na auditoria. */
export interface DevolverDgtRequest extends VersaoProcessoRequest {
  observacoes?: string | null;
}

export interface Processo {
  id: string;
  numeroProcesso: string;
  dinamicaId: string;
  instrumentoProcedimentoConfigId: number;
  estado: string;
  ocorrencia: number;
  versao: number;
  origemDados: string;
  tipoAtoId: number | null;
  criadoPorId: string;
  dadosFormulario: string | null;
  dataSubmissao: string | null;
  pcmAtribuidoAId: string | null;
  /** Última devolução à DGT pela PCM nesta passagem por `VALIDADO_DGT`; nula se não foi devolvido. */
  pcmDevolvidoEm?: string | null;
  /** Chaves do formulário inicial que já não mudam (as dos `campos[].path` dos erros); vazio até à primeira submissão. */
  camposBloqueados: string[];
  /** O ato publicado que o ato pontual visa; nulo fora dos atos pontuais. */
  processoReferidoId?: string | null;
}

/** Decisão da DGT sobre um item; a API grava `VALIDADO` ou `REJEITADO`. */
export type EstadoValidacao = 'POR_VALIDAR' | 'VALIDADO' | 'REJEITADO';

export type TipoItemValidacao = 'DOCUMENTO' | 'INFORMACAO_BASE';

/**
 * `itemId` é o `processo_documento.id` num `DOCUMENTO` e o `id` do elemento em `ProcessoDetalhe.informacaoBase` na `INFORMACAO_BASE`.
 * `categoriaId`, `motivoId` e `notas` só contam em `REJEITADO`; a categoria e o motivo só são exigidos quando a lista tem valores ativos.
 */
export interface ItemValidacao {
  tipo: TipoItemValidacao;
  itemId: string;
  decisao: EstadoValidacao;
  categoriaId?: number | null;
  motivoId?: number | null;
  notas?: string | null;
}

export interface RejeicaoItem {
  categoriaId: number | null;
  motivoId: number | null;
  notas: string;
}

/** Item rejeitado na validação em curso, ainda antes de «Confirmar Rejeição». */
export interface RejeicaoEmValidacao extends RejeicaoItem {
  tipo: TipoItemValidacao;
  itemId: string;
}

/** Tudo ou nada: se um item falhar, nenhum fica gravado. A `versao` não é incrementada. */
export interface GravarValidacaoItensRequest extends VersaoProcessoRequest {
  itens: ItemValidacao[];
}

export interface ItemValidacaoResponse {
  tipo: TipoItemValidacao;
  itemId: string;
  estadoValidacao: EstadoValidacao;
  /** Nula fora de `REJEITADO`. */
  rejeicao: RejeicaoItem | null;
}

/** `itens` traz todos os itens do Processo, não só os do pedido. */
export interface ValidacaoItensResponse {
  itens: ItemValidacaoResponse[];
  todosRevistos: boolean;
}

export type ElementoInformacaoBase = 'DELIBERACAO' | 'TEXTO_ATO';

/** Elemento da Informação Base como item de validação; os valores estão em `dadosFormulario`. */
export interface InformacaoBaseItem {
  id: string;
  elemento: ElementoInformacaoBase;
  estadoValidacao: EstadoValidacao;
}

/** Segmento de `PATCH /processos/{id}/<acao>`. */
export type AcaoProcesso =
  | 'submeter'
  | 'resgatar'
  | 'corrigir'
  | 'capturar'
  | 'validar'
  | 'rejeitar'
  | 'anular'
  | 'enviar-incm'
  | 'enviar-pcm'
  | 'devolver-dgt'
  | 'reatribuir-autor'
  | 'desbloquear-campo';

export interface ItemTerritorio {
  codigo: string;
  nome: string | null;
}

export interface TerritorioInstrumento {
  concelhos: ItemTerritorio[];
  nutsII: ItemTerritorio[];
}

/** Página do assistente Criar Processo onde se corrige uma pendência da submissão. */
export type PaginaPendencia = 'FORMULARIO' | 'PLANTAS' | 'DOCUMENTOS' | 'METADADOS';

/** `campo` e `erro` são os que `submeter` devolveria. */
export interface PendenciaSubmissao {
  pagina: PaginaPendencia;
  campo: string;
  erro: string;
}

/** As recusas (`ultimaRejeicaoDgt`, `ultimaRejeicaoIncm`) são nulas se nunca houve; mantêm-se após `corrigir` e nova submissão. */
export interface ProcessoDetalhe extends Processo {
  /** Submissões já feitas (0 antes da primeira); a `ocorrencia` conta os processos do Procedimento, não as submissões. */
  submissao?: number;
  /** O chamador é o autor e o estado permite editar. */
  editavel: boolean;
  /** O que o chamador pode executar agora: o ecrã não deduz transições do estado nem do perfil. */
  acoesDisponiveis: AcaoProcesso[];
  /** Dados INCM completos e, quando existir verificação documental, documentos obrigatórios presentes. Não inclui a Camada 3c. */
  podeSubmeter: boolean;
  /** O que falta para `podeSubmeter`, pela ordem das páginas do assistente; vazia sem edição. Ausente numa API anterior. */
  pendenciasSubmissao?: PendenciaSubmissao[];
  /** A submissão exige `comGeoconform`, pelo que está gravado; a pergunta mostra-se por `perguntaGeoconform`. */
  exigeOpcaoGeoconform: boolean;
  /** Configuração do Procedimento: com `SE_PECAS_GRAFICAS` a pergunta segue a caixa das Peças gráficas do formulário. */
  perguntaGeoconform: PerguntaGeoconform;
  /** Falso na Elaboração e fora dos Planos: o formulário não pergunta pelos elementos afetados e aparecem todos os itens. */
  exigeElementosAfetados: boolean;
  /** Sem GeoConform: o assistente mostra as páginas das Plantas e dos Metadados. */
  exigePlantasEMetadados: boolean;
  /** Resposta guardada na Dinâmica; nula até ser dada ou se a Dinâmica não pergunta. */
  comGeoconform: boolean | null;
  /** Vazia enquanto a DGT não capturou o processo e em procedimentos sem elementos esperados. */
  informacaoBase: InformacaoBaseItem[];
  /** O `validar` não seria recusado com `ITENS_POR_REVER`; não decide `acoesDisponiveis`. */
  todosItensRevistos: boolean;
  /** Só para a DGT com o processo em `EM_VALIDACAO_DGT`; vazia para os outros. */
  rejeicoesEmValidacao: RejeicaoEmValidacao[];
  /** Na Revogação do IGT, as outras Dinâmicas em curso que a submissão declara terminar; vazia nas outras. */
  dinamicasATerminar: DinamicaATerminar[];
  /** Só na Delimitação e na Alteração da REN: o formulário pergunta pela Dinâmica do IGT associada. */
  aceitaDinamicaAssociada: boolean;
  dinamicaAssociadaId: string | null;
  dinamicaAssociada: DinamicaAssociavel | null;
  destino: DestinoProcesso;
  /** O formulário admite «só depósito», o chamador é o autor, o estado é editável e ainda não há ficheiros. */
  destinoEditavel: boolean;
  /** Só no formulário `RATIFICACAO`; nula enquanto não foi indicada. */
  modalidadeRatificacao?: ModalidadeRatificacao | null;
  /** Nulo numa Dinâmica iniciada sem IGT: a designação é então o descritivo da Dinâmica. */
  instrumentoId: string | null;
  designacaoInstrumento: string;
  numeroInstrumento: string | null;
  tipoInstrumento: string;
  /** Os Instrumentos que a Dinâmica abrange, o de início primeiro; vazia numa Dinâmica sem IGT antes da associação. */
  instrumentosAbrangidos: InstrumentoAbrangido[];
  /** Fora de `UM` (Suspensão e Medidas Preventivas) os Instrumentos associam-se no ecrã dos documentos. */
  associacaoInstrumentos: AssociacaoInstrumentos;
  /** Os tipos que a Dinâmica admite na associação; vazia em `UM`. */
  tiposInstrumentoAdmitidos: TipoInstrumento[];
  /** O chamador é o autor deste processo em `EM_SUBMISSAO` e pode associar agora. */
  podeAssociarInstrumentos: boolean;
  categoriaInstrumento: CategoriaInstrumento;
  /** Nos Programas, se a entidade instrutora é obrigatória (falso no PS). */
  entidadeInstrutoraObrigatoria: boolean;
  territorio: TerritorioInstrumento;
  /** Nulo nos processos sem IDUS (fora de Planos, ou sem concelhos no instrumento). */
  idus: string | null;
  entidadeId: number;
  entidadeNif: string;
  entidadeNome: string;
  tipoDinamica: string;

  nomeDinamica: string;
  numeroDinamica: string;
  descritivoDinamica: string;
  estadoDinamica: string;
  /** Data limite da Dinâmica (`yyyy-MM-dd`); nula enquanto a Decisão de início não a fixou. */
  dataCaducidadeDinamica: string | null;
  tipoProcedimento: string;

  nomeProcedimento: string;
  /** A série efetiva: a do Procedimento ou, sem ela, a escolhida no processo; nula enquanto não há nenhuma. */
  serieDr: string | null;
  /** As séries que o processo pode escolher, só quando o Procedimento não tem série; nula nos outros. */
  seriesDrEscolhiveis?: SerieDr[] | null;
  formulario: FormularioFixo | null;
  /** Nulo enquanto o tipo de ato não foi escolhido no cabeçalho. */
  tipoAtoNome: string | null;
  /** As opções do «Tipo de ato» do cabeçalho, com a regra do `/disponivel`; nunca nula. */
  tiposAtoDisponiveis: TipoAtoDisponivel[];
  autorNome: string;
  sumario: string | null;
  dataAssinatura: string | null;
  /**
   * O Procedimento não tem a Informação Base do Texto do Ato: a data de assinatura indica-se no cartão «Informação Geral»
   * e a pendência dela é da página `FORMULARIO`.
   */
  dataAssinaturaNoFormulario?: boolean;
  normaLegal: string | null;
  tipoInformacao: TipoInformacao | null;
  publicarSuplemento: boolean | null;
  dataSuplemento: string | null;
  contactoTramitacao: string | null;
  /** Só nos Programas; nula nas outras categorias. */
  entidadeInstrutora: string | null;
  entidadeEmitente: string | null;
  contactoPublicacao: string | null;
  elementosAfetados: ElementoAfetado[] | null;
  pedeFundamentacaoRjigt: boolean;
  /** Vazia por omissão, nunca nula. */
  fundamentacaoRjigt: string[];
  /** Itens ativos por ordem, só quando `pedeFundamentacaoRjigt`: os externos não leem as listas de valores. */
  opcoesFundamentacaoRjigt: OpcaoLista[];
  pedeLocalizacao: PedeLocalizacao;
  pedeMotivoAlteracao: PedeMotivoAlteracao;
  localizacao: LocalizacaoDetalhe | null;
  conformaHierarquiaSuperior: boolean | null;
  motivosAlteracao: MotivoAlteracaoDetalhe[];
  /** Só com `formulario = 'SGIFR'`: as gravadas e as que o programa aceita; nulas nos outros formulários. */
  redesDefesa?: RedeDefesa[] | null;
  redesDefesaDisponiveis?: RedeDefesa[] | null;
  /** Só com `formulario = 'REN'` (nulos nos outros); `motivosProcedimentoDisponiveis` vazia esconde o campo. */
  motivoProcedimento?: MotivoProcedimento | null;
  motivosProcedimentoDisponiveis?: MotivoProcedimento[] | null;
  pedeDiplomaCorrigido?: boolean | null;
  diplomaCorrigido?: string | null;
  tipoInformacaoFixo?: TipoInformacao | null;
  regiaoCodigo?: string | null;
  municipioCodigo?: string | null;
  /**
   * Só com `formulario = 'SGIFR'` (opções nulas nos outros). A Região pede-se na Delimitação; a CIM / AM só no PSA,
   * filtrada pela Região na Delimitação. Uma lista sem valores ativos não pede o campo.
   */
  pedeRegiao?: boolean;
  opcoesRegiao?: OpcaoLista[] | null;
  regiao?: string | null;
  regiaoDescricao?: string | null;
  pedeCimAm?: boolean;
  opcoesCimAm?: OpcaoCimAm[] | null;
  cimAm?: string | null;
  cimAmDescricao?: string | null;
  numeroDr: string | null;
  dataPublicacaoDr: string | null;
  linkDr: string | null;
  incmActIdentifier: string | null;
  criadoEm: string;
  atualizadoEm: string;
  ultimaRejeicaoDgt: UltimaRejeicaoDgt | null;
  ultimaRejeicaoIncm: UltimaRejeicaoIncm | null;
  /** O Procedimento é um ato pontual de «Outros atos e anexos». */
  atoPontual: boolean;
  /** N.º de processo e código do tipo de Procedimento do ato visado; nulos fora dos atos pontuais. */
  numeroProcessoReferido?: string | null;
  tipoProcedimentoReferido?: string | null;
  /** Campos do Resumo Validados gravados pelo `validar`; só para a DGT, nulos para os outros. */
  notasIncm?: string | null;
  notasDgt?: string | null;
  regUsoSolo?: boolean | null;
  alteracaoPeotId?: number | null;
  /** A descrição do item gravado, mesmo desativado depois. */
  alteracaoPeotDescricao?: string | null;
  /** Itens ativos de `ALTERACAO_PEOT`, só para a DGT; vazia sem itens, e então o campo não aparece. */
  opcoesAlteracaoPeot?: OpcaoListaComId[];
  /** Ator `DGT`, `VALIDADO_DGT` devolvido pela PCM e algum documento por validar (os que a PCM acrescentou). */
  revalidacaoPcm?: boolean;
}

/**
 * A última recusa da DGT, com as notas gerais e sem motivo (os motivos são por item). Nas rejeições anteriores
 * às recusas numeradas vem o motivo único, com a descrição atual em `lista_valores`, e sem notas.
 */
export interface UltimaRejeicaoDgt {
  motivoCodigo: string | null;
  motivoDescricao: string | null;
  data: string;
  notasGerais: string | null;
}

/** A última recusa da INCM em `auditoria`; `mensagens` vem vazia se a INCM não devolveu texto. */
export interface UltimaRejeicaoIncm {
  mensagens: string[];
  data: string;
}

export interface ValorLista {
  codigo: string;
  descricao: string;
}

export interface ItemRecusa {
  tipo: TipoItemValidacao;
  documentoId: string | null;
  nomeFicheiro: string | null;
  separador: string | null;
  elemento: ElementoInformacaoBase | null;
  categoria: ValorLista | null;
  motivo: ValorLista | null;
  notas: string;
}

export type OrigemRecusa = 'DGT' | 'INCM';

export interface Recusa {
  sequencia: number;
  origem: OrigemRecusa;
  submissao: number;
  data: string;
  notasGerais: string | null;
  motivoIncm: string[];
  itens: ItemRecusa[];
}

export interface EstatisticasProcesso {
  numeroSubmissoes: number;
  recusas: Recusa[];
}

/** Uma entrada de `auditoria`; `estadoAntes`/`estadoDepois` guardam os nomes antigos nas entradas anteriores à EF v1.5. */
export interface HistoricoProcessoEntrada {
  id: number;
  acao: string;
  estadoAntes: string | null;
  estadoDepois: string | null;
  utilizadorId: string | null;
  utilizadorNome: string | null;
  dadosAlteracao: string | null;
  criadoEm: string;
}
