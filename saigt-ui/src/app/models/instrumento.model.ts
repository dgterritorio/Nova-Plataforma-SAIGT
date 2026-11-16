import { CategoriaInstrumento, DestinoProcesso, FormularioFixo, ItemTerritorio, PedeLocalizacao, Processo } from './processo.model';

export interface Instrumento {
  id: string;
  tipoInstrumentoId: number;
  tipoInstrumentoNome: string;
  numero: string;
  designacao: string;
  responsavelId: string;
  emVigor: boolean;
  origemDados: string;
  criadoPorId: string;
  criadoEm: string;
  /** Códigos DTCC, por ordem. */
  concelhos?: string[];
  /** Nome de cada código de `concelhos`, pela mesma ordem; ausente sem concelhos. */
  nomesConcelhos?: string[];
}

/** Pesquisa dos Instrumentos em vigor a associar; vazio ou nulo não filtra. */
export interface FiltroInstrumentosEmVigor {
  tiposInstrumentoId: number[];
  concelho: string | null;
  designacao: string | null;
}

export interface TipoInstrumento {
  id: number;
  codigo: string;
  nome: string;
  categoria: CategoriaInstrumento;
  /** Nos Programas, se a entidade instrutora é obrigatória (falso no PS). */
  entidadeInstrutoraObrigatoria: boolean;
}

/** `responsavelId` e `criadoPor` vêm sempre do token, nunca do corpo. */
export interface CriarInstrumentoRequest {
  tipoInstrumentoId: number;
  /** Com `idIgtSnit` continua obrigatória, mas a API usa a do SNIT. */
  designacao: string;
  /** DTCC de `InstrumentoService.listarConcelhos`; com `idIgtSnit` são os do SNIT e não se enviam. */
  concelhos?: string[];
  /** IGT que já existe no SNIT (`GET /api/v1/instrumentos/snit`). */
  idIgtSnit?: string;
}

/** IGT em vigor no SNIT; `instrumentoId` só quando já existe na SAIGT. */
export interface IgtSnit {
  idIgtSnit: string;
  designacao: string;
  tipoInstrumento: string;
  instrumentoId: string | null;
}

/** O que o formulário da Elaboração de um IGT novo precisa antes de haver Instrumento. */
export interface ElaboracaoNova {
  instrumentoDinamicaConfigId: number;
  nomeDinamica: string;
  procedimento: ProcedimentoDisponivel;
  exigeOpcaoGeoconform: boolean;
  /** Do primeiro Procedimento: o formulário abre antes de o Processo existir. */
  pedeLocalizacao: PedeLocalizacao;
  nutsII: ItemTerritorio[];
  concelhos: ConcelhoElaboracao[];
  concelhoEntidade: string | null;
  /** No intermunicipal a lista de concelhos não se filtra pela NUT II. */
  ambitoTerritorial?: AmbitoTerritorial | null;
}

export type AmbitoTerritorial = 'MUNICIPAL' | 'INTERMUNICIPAL' | 'REGIONAL' | 'NACIONAL';

/** `nutIICodigo` nulo: a réplica do SNIT não dá a NUT II do concelho. */
export interface ConcelhoElaboracao extends ItemTerritorio {
  nutIICodigo?: string | null;
}

export interface CriarElaboracaoRequest {
  tipoInstrumentoId: number;
  designacao: string;
  nutIICodigo: string;
  concelho: string;
  tipoAtoId: number;
}

export interface ElaboracaoCriada {
  instrumentoId: string;
  dinamicaId: string;
  processo: Processo;
}

export interface DocumentoExigido {
  /** `documentoConfigId` do upload. */
  id: number;
  nome: string;
  categoria: string;
  obrigatorio: boolean;
  extensoesAceites: string[];
}

/** Já filtrado pelo backend, nunca refiltrado aqui. */
export interface TipoAtoDisponivel {
  id: number;
  codigo: string;
  nome: string;
  /** Só no `/disponivel` dos Procedimentos sem série: as séries em que o tipo existe para a sessão. */
  seriesDr?: string[];
}

export interface TentativaEmCurso {
  processoId: string;
  numeroTentativa: number;
  estado: string;
}

/** Nula quando o Procedimento ainda não tem série configurada (ex.: SRUP). */
 /** Presente quando a última tentativa está em `EM_SUBMISSAO`: escolher retoma-a, nunca cria uma nova. */
export interface ProcedimentoDisponivel {
  instrumentoProcedimentoConfigId: number;
  tipoProcedimento: string;

  nomeProcedimento: string;
  ordem: number;
  obrigatorio: boolean;
  atoPontual: boolean;
  prazoDias: number | null;
  serieDr: string | null;
  formulario: FormularioFixo | null;
  documentosExigidos: DocumentoExigido[];
  tiposAtoDisponiveis: TipoAtoDisponivel[];
  tentativaEmCurso: TentativaEmCurso | null;
  /** Só nos atos pontuais: os atos publicados que o processo pode visar em `processoReferidoId`; vazia nos outros. */
  atosReferiveis?: AtoReferivel[];
}

/** Ato publicado da Dinâmica (Decisão de início ou Discussão pública) que um ato pontual pode visar. */
export interface AtoReferivel {
  processoId: string;
  numeroProcesso: string;
  /** Código do tipo de Procedimento (ex.: `DECISAO_INICIO`). */
  tipoProcedimento: string;
  numeroDr: string | null;
  /** `yyyy-MM-dd`. */
  dataPublicacaoDr: string | null;
}

/** Tipo elegível, ainda não iniciado. */
export interface DinamicaDisponivel {
  instrumentoDinamicaConfigId: number;
  tipoDinamica: string;

  nomeDinamica: string;
}

 /** Distingue Dinâmicas do mesmo tipo em aberto no passo 1 de Criar Processo. */
export interface DinamicaEmCurso {
  dinamicaId: string;
  tipoDinamica: string;

  nomeDinamica: string;
  descritivo: string;
  /** `TERMINADA` só quando ainda oferece Procedimentos posteriores ao final (a Prorrogação); continua terminada. */
  estado: 'EM_CURSO' | 'CADUCADA' | 'TERMINADA';
  dataInicio: string;
  /** Nula enquanto nenhum Procedimento da Dinâmica estiver publicado. */
  dataCaducidade: string | null;
  procedimentosDisponiveis: ProcedimentoDisponivel[];
}

export interface UltimaDinamicaPublicada {
  dinamicaId: string;
  tipoDinamica: string;

  nomeDinamica: string;
  dataConclusao: string;
}

/** O frontend não conhece a estrutura do processo: renderiza só o que esta resposta trouxer. */
/** Dinâmica `TERMINADA` mais recente, `null` se não há nenhuma. */
export interface InstrumentoDisponibilidade {
  dinamicasDisponiveis: DinamicaDisponivel[];
  dinamicasEmCurso: DinamicaEmCurso[];
  ultimaDinamicaPublicada: UltimaDinamicaPublicada | null;
}

export type Semaforo = 'VERDE' | 'AMARELO' | 'VERMELHO' | 'CINZENTO';

export interface ProcessoPainel {
  id: string;
  numeroProcesso: string;
  ocorrencia: number;
  estado: string;
  numeroDr: string | null;
  dataPublicacaoDr: string | null;
  linkDr: string | null;
  destino?: DestinoProcesso;
  /** Nos atos pontuais, o ato publicado que visam. */
  processoReferidoId?: string | null;
  numeroProcessoReferido?: string | null;
}

export interface ProcedimentoPainel {
  instrumentoProcedimentoConfigId: number;
  tipoProcedimento: string;

  nomeProcedimento: string;
  ordem: number;
  obrigatorio: boolean;
  atoPontual?: boolean;
  /** Por `ocorrencia`; só os visíveis pela sessão. */
  processos: ProcessoPainel[];
}

/** Uma Dinâmica configurada e ainda não iniciada vem sem `id`, `descritivo`, `estado` nem datas. */
export interface DinamicaPainel {
  id: string | null;
  numeroDinamica: string | null;
  descritivo: string | null;
  tipoDinamica: string;

  nomeDinamica: string;
  estado: 'EM_CURSO' | 'TERMINADA' | 'CADUCADA' | null;
  tentativa: number | null;
  semaforo: Semaforo;
  dataInicio: string | null;
  dataConclusao: string | null;
  dataCaducidade: string | null;
  historica: boolean;
  procedimentos: ProcedimentoPainel[];
}

export interface InstrumentoPainel {
  id: string;
  tipoInstrumentoId: number;
  designacao: string;
  numero: string;
  idIgtSnit: string | null;
  emVigor: boolean;
  origemDados: string;
  criadoPorId: string;
  criadoEm: string;
  /** As iniciadas por `dataInicio`, depois as configuradas e não iniciadas. */
  dinamicas: DinamicaPainel[];
}

export interface EstatisticasProcessoDinamica {
  processoId: string;
  numeroProcesso: string;
  tipoProcedimento: string;
  ocorrencia: number;
  numeroRejeicoesDgt: number;
  numeroAnulacoesIncm: number;
  numeroCiclosResubmissao: number | null;
  numeroComunicacoes: number | null;
}

export interface EstatisticasDinamica {
  dinamicaId: string;
  numeroRejeicoesDgt: number;
  numeroAnulacoesIncm: number;
  numeroCiclosResubmissao: number | null;
  numeroComunicacoes: number | null;
  processos: EstatisticasProcessoDinamica[];
}

export interface CriarDinamicaRequest {
  instrumentoDinamicaConfigId: number;
  /** Até 50 caracteres; aparece em todos os processos da Dinâmica. */
  descritivo: string;
}

export interface Dinamica {
  id: string;
  numeroDinamica: string;
  descritivo: string;
  tentativa: number;
  /** Nulo numa Dinâmica iniciada sem IGT de partida. */
  instrumentoId: string | null;
  /** Os Instrumentos abrangidos, o de início primeiro; vazia numa Dinâmica sem IGT antes da associação. */
  instrumentoIds: string[];
  instrumentoDinamicaConfigId: number;
  tipoDinamica: string;

  nomeDinamica: string;
  estado: string;
  origemDados: string;
  dataInicio: string;
  dataConclusao: string | null;
  criadoPorId: string;
}
