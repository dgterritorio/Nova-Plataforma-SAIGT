import { InstrumentoAbrangido } from './processo.model';
import { CriarDinamicaRequest, DinamicaEmCurso } from './instrumento.model';

export type AssociacaoInstrumentos = 'UM' | 'UM_OU_MAIS' | 'OPCIONAL';

/** Tipo de Instrumento do grupo «Processo com associação a IGT»: o processo começa pela escolha do IGT. */
export interface TipoComAssociacaoIgt {
  tipoInstrumentoId: number;
  codigo: string;
  nome: string;
}

/** Os Instrumentos associam-se depois do formulário inicial; aqui só se escolhe a configuração. */
export interface ConfiguracaoSemIgt {
  instrumentoDinamicaConfigId: number;
  tipoInstrumentoId: number;
  tipoInstrumentoCodigo: string;
  tipoInstrumentoNome: string;
  associacaoInstrumentos: AssociacaoInstrumentos;
}

export interface TipoDinamicaSemIgt {
  tipoDinamicaId: number;
  codigo: string;
  nome: string;
  configuracoes: ConfiguracaoSemIgt[];
}

/** Primeiro passo de Criar Processo: o que a sessão pode iniciar. */
export interface TiposProcesso {
  comAssociacaoIgt: TipoComAssociacaoIgt[];
  semAssociacaoIgt: TipoDinamicaSemIgt[];
}

/** `POST /api/v1/dinamicas`: a Dinâmica nasce sem Instrumentos. */
export type IniciarDinamicaRequest = CriarDinamicaRequest;

export interface InstrumentoDaDinamica {
  id: string;
  designacao: string;
  tipoInstrumento: string;
  /** Códigos DTCC. */
  concelhos: string[];
}

/** O último processo `PUBLICADO` da Dinâmica visível à sessão. */
export interface AtoPublicado {
  processoId: string;
  numeroDr: string | null;
  dataPublicacaoDr: string | null;
}

export interface DinamicaSemIgtEmCurso extends DinamicaEmCurso {
  numeroDinamica: string;
  /** Vazia antes da associação. */
  instrumentos: InstrumentoDaDinamica[];
  atoPublicado: AtoPublicado | null;
}

export interface DinamicasSemIgtDisponiveis {
  dinamicasEmCurso: DinamicaSemIgtEmCurso[];
}

/** `PUT /api/v1/dinamicas/{id}/instrumentos`: o conjunto completo, sem repetidos; vazio desassocia todos. */
export interface AssociarInstrumentosRequest {
  instrumentoIds: string[];
}

export interface AssociacaoInstrumentosResponse {
  instrumentos: InstrumentoAbrangido[];
}
