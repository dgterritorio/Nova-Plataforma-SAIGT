import { ElementoInformacaoBase, EstadoValidacao } from './processo.model';

export type CategoriaDocumento = 'PUBLICACAO' | 'DEPOSITO';

export type OrigemDocumento = 'CARREGADO' | 'GERADO' | 'GEOCONFORM';

export type TipoElementoDocumental = 'PECA_ESCRITA' | 'PECA_GRAFICA' | 'OUTROS';

export type FormatoDocumental = 'EDITAVEL' | 'NAO_EDITAVEL';

/** Linha de ficheiro da estrutura documental; `documentoConfigId` nulo nos ficheiros do balde "outros". */
export interface DocumentoProcesso {
  id: string;
  documentoConfigId: number | null;
  nomeFicheiro: string;
  categoria: CategoriaDocumento;
  extensao: string;
  origem: OrigemDocumento;
  /** `true`: o `PUT` e o `DELETE` devolvem 409 `DOC_BLOQUEADO`. */
  bloqueadoEdicao: boolean;
  estadoValidacao: EstadoValidacao;
  tamanhoBytes: number;
  criadoEm: string;
  criadoPorId: string;
  criadoPorNome: string;
  /** `documentoConfigId` dos itens de depósito obrigatório que o ficheiro também satisfaz, por ordem crescente. */
  tags: number[];
  /** Justificação do último «Desbloquear ficheiro»; fica mesmo depois de o ficheiro voltar a ficar bloqueado. */
  justificacaoDesbloqueio: string | null;
}

/** Uma submissão do Procedimento no histórico documental, com os ficheiros que entregou. */
export interface ProcessoHistoricoDocumental {
  processoId: string;
  numeroProcesso: string;
  ocorrencia: number;
  estado: string;
  dataSubmissao: string | null;
  numeroDr: string | null;
  dataPublicacaoDr: string | null;
  linkDr: string | null;
  /** `true`: é o processo do pedido. */
  atual: boolean;
  documentos: DocumentoProcesso[];
}

/** Os processos do mesmo Procedimento na mesma Dinâmica, por `ocorrencia` ascendente. */
export interface HistoricoDocumental {
  processos: ProcessoHistoricoDocumental[];
}

/** Corpo do `PATCH …/classificacao` (API-DOC-004): `documentoConfigId` nulo = «Outros» do depósito; `tags` substitui as atuais. */
export interface ReclassificarDocumentoPedido {
  categoria: CategoriaDocumento;
  documentoConfigId: number | null;
  tags: number[];
}

/** Filtros opcionais da listagem de ficheiros do processo; combinam-se com «e». */
export interface FiltroDocumentos {
  categoria?: CategoriaDocumento;
  documentoConfigId?: number;
  estadoValidacao?: EstadoValidacao;
}

export interface ItemDocumental {
  documentoConfigId: number;
  codigo: string;
  nome: string;
  categoria: CategoriaDocumento;
  tipoElemento: TipoElementoDocumental;
  formato: FormatoDocumental;
  obrigatorio: boolean;
  extensoesAceites: string[];
  ficheiros: DocumentoProcesso[];
  /** Justificação de um item sem ficheiro («NA» se não aplicável); nula sem observações. */
  observacoes: string | null;
  /** `obrigatorio`, sem ficheiros e sem observações. */
  pendente: boolean;
  /** `true`: as peças vêm do GeoConform e o `POST` e o `PUT` do documento devolvem 409 `DOC_CANAL_GEOCONFORM`. */
  obtidoDoGeoconform: boolean;
}

export interface ObservacoesDocumento {
  documentoConfigId: number;
  observacoes: string;
}

export interface SeparadorDocumental {
  separador: string;
  elementoInformacaoBase: ElementoInformacaoBase | null;
  publicacao: ItemDocumental[];
  deposito: ItemDocumental[];
}

export interface PendenciaDocumental {
  documentoConfigId: number;
  codigo: string;
  nome: string;
  categoria: CategoriaDocumento;
  motivo: string;
}

/** Os seis itens da checklist do FR-092, pela ordem em que a API os devolve. */
export type VerificacaoChecklist =
  | 'CORRESPONDENCIA_ATO_TEXTO'
  | 'FORMATO_QUADRO_ANEXO'
  | 'GEORREFERENCIACAO_MATRICIAL'
  | 'CONSISTENCIA_VETORIAL_MATRICIAL'
  | 'FOLHA_NOME_TIF'
  | 'CORRECAO_ZIP';

/** `MANUAL`: a DGT confirma à mão (também quando a verificação automática não foi possível). */
export type ResultadoVerificacao = 'OK' | 'FALHA' | 'MANUAL';

/**
 * Uma linha da checklist de validação DGT. Nas linhas de um TIF, `documentoId` é o do ZIP e `nomeFicheiro` a entrada TIF;
 * ambos nulos nas verificações do Processo.
 */
export interface VerificacaoDocumental {
  verificacao: VerificacaoChecklist;
  documentoId: string | null;
  nomeFicheiro: string | null;
  resultado: ResultadoVerificacao;
  detalhe: string | null;
}

export interface EstruturaDocumental {
  separadores: SeparadorDocumental[];
  outros: DocumentoProcesso[];
  pendencias: PendenciaDocumental[];
  /** Da secção «Outros», que não tem item documental. */
  observacoesOutros?: string | null;
}
