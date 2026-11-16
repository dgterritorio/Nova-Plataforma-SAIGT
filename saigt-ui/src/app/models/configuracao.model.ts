import { CategoriaDocumento, FormatoDocumental, TipoElementoDocumental } from './documento.model';
import { ElementoInformacaoBase, FormularioFixo } from './processo.model';

export type CategoriaTipoInstrumento = 'PLANO' | 'PROGRAMA' | 'SRUP';

export type AssociacaoInstrumentos = 'UM' | 'UM_OU_MAIS' | 'OPCIONAL';

export type CatalogoTipos = 'instrumento' | 'dinamica' | 'procedimento';

export type FiltroEstadoConfig = 'todos' | 'ativos' | 'inativos';

export interface TipoInstrumentoConfig {
  id: number;
  codigo: string;
  nome: string;
  categoria: CategoriaTipoInstrumento;
  ativo: boolean;
}

export interface TipoDinamicaConfig {
  id: number;
  codigo: string;
  nome: string;
  ativo: boolean;
}

export interface TipoProcedimentoConfig {
  id: number;
  codigo: string;
  nome: string;
  ativo: boolean;
  /** Nulo: o tipo ainda não tem formulário desenvolvido na aplicação. */
  formulario: string | null;
}

export type TipoConfig = TipoInstrumentoConfig | TipoDinamicaConfig | TipoProcedimentoConfig;

export interface TipoResumo {
  id: number;
  codigo: string;
  nome: string;
}

export interface DinamicaConfig {
  id: number;
  tipoInstrumento: TipoResumo;
  tipoDinamica: TipoResumo;
  associacaoInstrumentos: AssociacaoInstrumentos;
  ativo: boolean;
  /** Já há Dinâmicas iniciadas com esta configuração: o par de tipos não pode mudar. */
  emUso: boolean;
}

export interface TipoInstrumentoConfigRequest {
  codigo: string;
  nome: string;
  categoria: CategoriaTipoInstrumento;
  ativo: boolean;
}

export interface TipoDinamicaConfigRequest {
  codigo: string;
  nome: string;
  ativo: boolean;
}

export interface TipoProcedimentoConfigRequest {
  codigo: string;
  nome: string;
  ativo: boolean;
}

export type TipoConfigRequest = TipoInstrumentoConfigRequest | TipoDinamicaConfigRequest | TipoProcedimentoConfigRequest;

export interface DinamicaConfigRequest {
  tipoInstrumentoId: number;
  tipoDinamicaId: number;
  associacaoInstrumentos: AssociacaoInstrumentos;
  ativo: boolean;
}

export type SerieDr = 'PRIMEIRA' | 'SEGUNDA_H' | 'SEGUNDA_C';

export type TipoEntidade = 'CM' | 'CCDR_DR' | 'AM' | 'CIM' | 'PCM' | 'SEC_ESTADO' | 'DGT' | 'ICNF' | 'APA' | 'DGPC' | 'IP';

export const TIPOS_ENTIDADE: readonly TipoEntidade[] = ['CM', 'CCDR_DR', 'AM', 'CIM', 'PCM', 'SEC_ESTADO', 'DGT', 'ICNF', 'APA', 'DGPC', 'IP'];

export interface ProcedimentoConfig {
  id: number;
  instrumentoDinamicaConfigId: number;
  tipoProcedimento: TipoResumo;
  ordem: number;
  obrigatorio: boolean;
  serieDr: SerieDr;
  /** Em bytes; nulo = sem limite. */
  tamanhoMaximoProcesso: number | null;
  repetivel: boolean;
  atoPontual: boolean;
  ativo: boolean;
  formulario: string | null;
  /** Já há processos desta configuração: o tipo de Procedimento não pode mudar. */
  emUso: boolean;
  /** Os processos ficam visíveis, só em leitura, às entidades com processos na mesma Dinâmica. */
  visivelNaDinamica: boolean;
}

export interface ProcedimentoConfigRequest {
  instrumentoDinamicaConfigId: number;
  tipoProcedimentoId: number;
  ordem: number;
  obrigatorio: boolean;
  serieDr: SerieDr;
  tamanhoMaximoProcesso: number | null;
  repetivel: boolean;
  atoPontual: boolean;
  ativo: boolean;
  /** Em falta: ao criar, falso; ao atualizar, mantém-se o gravado. */
  visivelNaDinamica?: boolean;
}

export interface PermissaoConfig {
  id: number;
  instrumentoProcedimentoConfigId: number;
  tipoProcedimento: TipoResumo;
  tipoEntidade: TipoEntidade;
  ativo: boolean;
}

export interface PermissaoConfigRequest {
  instrumentoProcedimentoConfigId: number;
  tipoEntidade: TipoEntidade;
  ativo: boolean;
}

export interface TipoAtoConfig {
  id: number;
  codigo: string;
  nome: string;
  incmActTypeIdSerie1: number | null;
  incmActTypeIdSerie2: number | null;
  ordem: number;
  ativo: boolean;
}

export interface TipoAtoConfigRequest {
  codigo: string;
  nome: string;
  incmActTypeIdSerie1: number | null;
  incmActTypeIdSerie2: number | null;
  ordem: number;
  ativo: boolean;
}

export interface RedeDefesaConfig {
  id: number;
  codigo: string;
  nome: string;
  aplicaPra: boolean;
  aplicaPsa: boolean;
  publicavel: boolean;
}

export interface RedeDefesaConfigRequest {
  codigo: string;
  nome: string;
  aplicaPra: boolean;
  aplicaPsa: boolean;
  publicavel: boolean;
}

export interface FiltroTipos {
  ativo?: boolean;
}

export interface FiltroDinamicasConfig {
  tipoInstrumentoId?: number;
  ativo?: boolean;
}

export interface FiltroProcedimentosConfig {
  instrumentoDinamicaConfigId?: number;
  ativo?: boolean;
}

export interface FiltroPermissoes {
  instrumentoProcedimentoConfigId?: number;
  ativo?: boolean;
}

/** As mesmas regras dos pedidos do servidor. */
export const PADRAO_CODIGO = /^[A-Z0-9_]+$/;
export const TAMANHO_MAXIMO_CODIGO: Record<CatalogoTipos, number> = { instrumento: 20, dinamica: 40, procedimento: 40 };
export const TAMANHO_MAXIMO_NOME = 200;
export const TAMANHO_MAXIMO_CODIGO_ATO_REDE = 10;
export const BYTES_POR_MB = 1_048_576;
const BYTES_POR_GB = BYTES_POR_MB * 1024;

export const ROTULOS_SERIE_DR: Record<SerieDr, string> = {
  PRIMEIRA: '1.ª série',
  SEGUNDA_H: '2.ª série — parte H',
  SEGUNDA_C: '2.ª série — parte C',
};

/** Em MB abaixo de 1 GB, senão em GB, com uma casa decimal só quando faz falta (5368709120 → "5 GB"). */
export function formatarLimite(bytes: number | null): string {
  if (bytes === null) {
    return 'Sem limite';
  }
  const [valor, unidade] = bytes >= BYTES_POR_GB ? [bytes / BYTES_POR_GB, 'GB'] : [bytes / BYTES_POR_MB, 'MB'];
  return `${new Intl.NumberFormat('pt-PT', { maximumFractionDigits: 1 }).format(valor)} ${unidade}`;
}

export const ROTULOS_CATEGORIA: Record<CategoriaTipoInstrumento, string> = {
  PLANO: 'Plano',
  PROGRAMA: 'Programa',
  SRUP: 'SRUP',
};

export const ROTULOS_ASSOCIACAO: Record<AssociacaoInstrumentos, string> = {
  UM: 'Um Instrumento',
  UM_OU_MAIS: 'Um ou mais Instrumentos',
  OPCIONAL: 'Opcional',
};

export const ROTULOS_FORMULARIO: Record<FormularioFixo, string> = {
  INFORMACAO_GERAL: 'Informação Geral',
  DISCUSSAO_PUBLICA: 'Discussão Pública',
  PUBLICACAO_ALTERACAO: 'Publicação da Alteração',
  SGIFR: 'SGIFR',
  REN: 'REN',
  SRUP: 'Restantes servidões (cabeçalho comum)',
  RATIFICACAO: 'Ratificação',
  SUSPENSAO_CCDR: 'Suspensão da iniciativa da CCDR',
  SUSPENSAO_MP_GOVERNO: 'Suspensão / Medidas Preventivas (Governo)',
  PRORROGACAO_GOVERNO: 'Prorrogação da Suspensão / Medidas Preventivas (Governo)',
};

export function ativoDoFiltro(filtro: FiltroEstadoConfig): boolean | undefined {
  if (filtro === 'ativos') return true;
  if (filtro === 'inativos') return false;
  return undefined;
}

export type RegraNomenclatura = 'GERAL' | 'SGIFR';

export type CompressaoTiff = 'NONE' | 'LZW';

/** Regras do GeoTIFF de uma peça gráfica: lista vazia ou campo nulo = não verificar. */
export interface RegrasTiff {
  sistemasReferencia: string[];
  compressoesAceites: CompressaoTiff[];
  bandaAlfaTolerada: boolean;
  modeloCor: 'RGB' | null;
  bitsPorBanda: number | null;
  resolucaoDpi: number | null;
  elementosLegenda: string[];
}

export interface DocumentoConfig {
  id: number;
  instrumentoProcedimentoConfigId: number;
  codigo: string;
  nome: string;
  separador: string;
  descricao: string | null;
  categoria: CategoriaDocumento;
  tipoElemento: TipoElementoDocumental;
  formato: FormatoDocumental;
  extensoesAceites: string[];
  obrigatorio: boolean;
  ordem: number;
  elementoInformacaoBase: ElementoInformacaoBase | null;
  /** Em bytes. */
  tamanhoMaximoFicheiro: number | null;
  tamanhoMaximoTotal: number | null;
  numeroMaximoFicheiros: number | null;
  linkPublicoPermitido: boolean;
  ativo: boolean;
  /** Só numa peça gráfica. */
  regrasTiff: RegrasTiff | null;
  regraNomenclatura: RegraNomenclatura;
}

/** O `codigo` e o Procedimento não mudam depois de criar. */
export interface AtualizarDocumentoConfigPedido {
  nome: string;
  separador: string;
  descricao: string | null;
  categoria: CategoriaDocumento;
  tipoElemento: TipoElementoDocumental;
  formato: FormatoDocumental;
  extensoesAceites: string[];
  obrigatorio: boolean;
  ordem: number;
  elementoInformacaoBase: ElementoInformacaoBase | null;
  tamanhoMaximoFicheiro: number | null;
  tamanhoMaximoTotal: number | null;
  numeroMaximoFicheiros: number | null;
  linkPublicoPermitido: boolean;
  ativo: boolean;
  /** Ausente: o servidor mantém as gravadas (ou, ao criar uma peça gráfica, usa as iniciais gerais). */
  regrasTiff?: RegrasTiff;
  regraNomenclatura: RegraNomenclatura;
}

export interface CriarDocumentoConfigPedido extends AtualizarDocumentoConfigPedido {
  instrumentoProcedimentoConfigId: number;
  codigo: string;
}

export interface FiltroDocumentoConfig {
  instrumentoProcedimentoConfigId: number;
  ativo?: boolean;
}

export const ROTULOS_CAIXA: Record<CategoriaDocumento, string> = { PUBLICACAO: 'Publicação', DEPOSITO: 'Depósito' };

export const ROTULOS_TIPO_ELEMENTO: Record<TipoElementoDocumental, string> = {
  PECA_ESCRITA: 'Peça escrita',
  PECA_GRAFICA: 'Peça gráfica',
  OUTROS: 'Outros',
};

export const ROTULOS_FORMATO: Record<FormatoDocumental, string> = { EDITAVEL: 'Editável', NAO_EDITAVEL: 'Não editável' };

export const ROTULOS_INFORMACAO_BASE: Record<ElementoInformacaoBase, string> = {
  DELIBERACAO: 'Deliberação',
  TEXTO_ATO: 'Texto do ato',
};

export const ROTULOS_REGRA_NOMENCLATURA: Record<RegraNomenclatura, string> = {
  GERAL: 'Geral (20 caracteres, sem espaços nem pontos)',
  SGIFR: 'SGIFR (Nome_região_temática)',
};

/** Tabela de extensões da EF v1.5 (BL-DOC-022), a mesma do servidor; `OUTROS` não tem linha. */
export const EXTENSOES_EF: Partial<Record<TipoElementoDocumental, Record<CategoriaDocumento, readonly string[]>>> = {
  PECA_ESCRITA: {
    PUBLICACAO: ['doc', 'docx', 'rtf', 'odf', 'odt', 'xls', 'xlsx', 'csv', 'ods', 'pdf', 'txt'],
    DEPOSITO: ['pdf', 'jpg', 'png'],
  },
  PECA_GRAFICA: { PUBLICACAO: ['zip'], DEPOSITO: ['zip'] },
};

/** Ponto de partida do grupo TIFF ao criar uma peça gráfica: a configuração inicial geral do servidor. */
export const REGRAS_TIFF_INICIAIS: RegrasTiff = {
  sistemasReferencia: ['EPSG:3763', 'EPSG:5014', 'EPSG:5015', 'EPSG:5016'],
  compressoesAceites: ['NONE', 'LZW'],
  bandaAlfaTolerada: true,
  modeloCor: null,
  bitsPorBanda: null,
  resolucaoDpi: null,
  elementosLegenda: ['ID do plano', 'N.º de folha', 'Data de aprovação', 'Identificação do procedimento'],
};

/** Avaliadores implementados em código no motor de regras; um avaliador novo acrescenta-se em código, não aqui. */
export type Avaliador = 'OCORRENCIA_EM_ESTADO' | 'OCORRENCIA_NAO_EXISTE' | 'INSTRUMENTO_EM_VIGOR' | 'DINAMICA_BLOQUEADA_LEGADO';

/** Combinação entre grupos; as condições do mesmo grupo combinam sempre com E. */
export type OperadorRegra = 'AND' | 'OR';

export type TipoOcorrencia = 'DINAMICA' | 'PROCESSO';

export type EstadoDinamica = 'EM_CURSO' | 'TERMINADA' | 'CADUCADA';

/** Chaves à letra, como o servidor as lê; uma chave a mais é recusada. */
export interface ParametrosCondicao {
  tipo_ocorrencia?: TipoOcorrencia;
  codigo?: string;
  estado?: string;
}

export interface CondicaoConfig {
  id: number | null;
  avaliador: Avaliador;
  parametros: ParametrosCondicao;
  grupo: number;
  ativo: boolean;
}

/** Elegibilidade de uma Dinâmica ou exceção ao sequenciamento de um Procedimento: exatamente um dos dois alvos. */
export interface RegraConfig {
  id: number;
  instrumentoDinamicaConfigId: number | null;
  instrumentoProcedimentoConfigId: number | null;
  substituiSequenciamento: boolean;
  operador: OperadorRegra;
  descricao: string | null;
  ativo: boolean;
  condicoes: CondicaoConfig[];
}

/** Sem `id`, a condição é nova; uma condição gravada que não vem na lista passa a inativa no servidor. */
export interface CondicaoConfigRequest {
  id?: number;
  avaliador: Avaliador;
  parametros: ParametrosCondicao;
  grupo: number;
  ativo: boolean;
}

export interface RegraConfigRequest {
  instrumentoDinamicaConfigId: number | null;
  instrumentoProcedimentoConfigId: number | null;
  substituiSequenciamento: boolean;
  operador: OperadorRegra;
  descricao: string | null;
  ativo: boolean;
  condicoes: CondicaoConfigRequest[];
}

export interface FiltroRegras {
  instrumentoDinamicaConfigId?: number;
  instrumentoProcedimentoConfigId?: number;
  /** As regras dos Procedimentos dessa Dinâmica, sem as de elegibilidade da Dinâmica. */
  dinamicaDosProcedimentosId?: number;
  ativo?: boolean;
}

export const TAMANHO_MAXIMO_DESCRICAO_REGRA = 300;

export const AVALIADORES: readonly Avaliador[] = ['OCORRENCIA_EM_ESTADO', 'OCORRENCIA_NAO_EXISTE', 'INSTRUMENTO_EM_VIGOR', 'DINAMICA_BLOQUEADA_LEGADO'];

export const ROTULOS_AVALIADOR: Record<Avaliador, string> = {
  OCORRENCIA_EM_ESTADO: 'Existe ocorrência num estado',
  OCORRENCIA_NAO_EXISTE: 'Não existe ocorrência',
  INSTRUMENTO_EM_VIGOR: 'O Instrumento está em vigor',
  DINAMICA_BLOQUEADA_LEGADO: 'Dinâmica em curso no sistema anterior',
};

export const ROTULOS_TIPO_OCORRENCIA: Record<TipoOcorrencia, string> = { DINAMICA: 'Dinâmica', PROCESSO: 'Processo' };

export const ESTADOS_DINAMICA: readonly EstadoDinamica[] = ['EM_CURSO', 'TERMINADA', 'CADUCADA'];

/** Em minúsculas: entram a meio da frase do resumo ("não existe Dinâmica Revisão em curso"). */
export const ROTULOS_ESTADO_DINAMICA: Record<EstadoDinamica, string> = { EM_CURSO: 'em curso', TERMINADA: 'terminada', CADUCADA: 'caducada' };

/** A regra gravada tal como está, pronta a reenviar (ex.: só com o `ativo` trocado). */
export function pedidoDaRegra(regra: RegraConfig): RegraConfigRequest {
  return {
    instrumentoDinamicaConfigId: regra.instrumentoDinamicaConfigId,
    instrumentoProcedimentoConfigId: regra.instrumentoProcedimentoConfigId,
    substituiSequenciamento: regra.substituiSequenciamento,
    operador: regra.operador,
    descricao: regra.descricao,
    ativo: regra.ativo,
    condicoes: regra.condicoes.map((condicao) => ({
      ...(condicao.id !== null ? { id: condicao.id } : {}),
      avaliador: condicao.avaliador,
      parametros: { ...condicao.parametros },
      grupo: condicao.grupo,
      ativo: condicao.ativo,
    })),
  };
}
