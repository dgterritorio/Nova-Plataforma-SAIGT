/** Página das Plantas de um processo sem GeoConform (`GET /processos/{id}/plantas`). */
export interface Plantas {
  exigePlantas: boolean;
  itens: ItemPlantas[];
  linhas: LinhaPlanta[];
  errosTxt: string[];
  exigeCartograma: boolean;
  /** Há cartograma e todas as linhas têm TIFF gravado: já se pode pedir o PDF. */
  cartogramaPdfDisponivel: boolean;
}

export interface ItemPlantas {
  documentoConfigId: number;
  codigo: string;
  nome: string;
  descricao: string | null;
  categoria: 'PUBLICACAO' | 'DEPOSITO';
  obrigatorio: boolean;
  extensoesAceites: string[];
  ficheiro: { id: string; nomeFicheiro: string } | null;
}

export interface LinhaPlanta {
  ordem: number;
  tipo: string;
  nome: string;
  folha: string | null;
  nomeTiff: string | null;
  /** TIFF do ZIP do mesmo tipo de planta. */
  opcoes: string[];
}

export interface GravarPlantasRequest {
  associacoes: { ordem: number; nomeTiff: string }[];
}

export interface MetadadosProcesso {
  palavrasChave: string | null;
  resolucaoEspacialEscala: string | null;
  tituloAlternativo: string | null;
  resumo: string | null;
  sistemaReferencia: string | null;
  codigoSistema: string | null;
  cartografiaTipo: string | null;
  entidadeProprietaria: string | null;
  dataEdicao: string | null;
  entidadeProdutora: string | null;
  dataAtualizacao: string | null;
  atualizacaoComplemento: string | null;
  sistemaReferenciaNome: string | null;
  sistemaReferenciaCodigo: string | null;
  exatidaoPosicional: string | null;
  exatidaoTematica: string | null;
}

export interface ResponsavelMetadados {
  nomeOrganizacao: string | null;
  funcao: string | null;
  correioEletronico: string | null;
  enderecoUrl: string | null;
  rua: string | null;
  localidade: string | null;
  codigoPostal: string | null;
  pais: string | null;
  telefone: string | null;
  fax: string | null;
}

/** Ficha de metadados (MOTU) do processo (`GET /processos/{id}/metadados`). */
export interface Metadados {
  exigeMetadados: boolean;
  titulo: string;
  responsavel: ResponsavelMetadados;
  dados: MetadadosProcesso;
}

/** Campos da Série de Dados Geográficos da ficha MOTU (EF v1.5 §3.6.1). */
export interface SerieMotuDados {
  tituloAlternativo: string | null;
  resumo: string | null;
  palavrasChave: string | null;
  denominadorEscala: string | null;
  sistemaReferencia: string | null;
  codigoSistema: string | null;
  historico: string | null;
  relatorio: string | null;
}

export type TipoCartografiaMotu = 'SERIE_CARTOGRAFICA_OFICIAL' | 'CARTOGRAFIA_HOMOLOGADA' | 'OUTRO';

/** Campos do Conjunto de Dados Geográficos (EF v1.5 §3.6.2); os de um tipo de cartografia só existem com esse tipo. */
export interface ConjuntoMotuDados {
  tituloAlternativo: string | null;
  resumo: string | null;
  palavrasChave: string | null;
  denominadorEscala: string | null;
  sistemaReferencia: string | null;
  codigoSistema: string | null;
  cartografiaTipo: TipoCartografiaMotu | null;
  dataEdicao: string | null;
  dataAtualizacao: string | null;
  atualizacaoComplemento: string | null;
  entidadeProprietaria: string | null;
  entidadeProdutora: string | null;
  sistemaReferenciaNome: string | null;
  sistemaReferenciaCodigo: string | null;
  exatidaoPosicional: string | null;
  exatidaoTematica: string | null;
  serieCartograficaOficial?: string | null;
  numeroHomologacao?: string | null;
  dataHomologacao?: string | null;
  entidadeHomologacao?: string | null;
}

/** Folha da página das Plantas ligada a um Conjunto; só o nome do regulamento e a folha se corrigem. */
export interface FolhaConjuntoMotu {
  id: string;
  nomeRegulamento: string | null;
  numeroFolha: string | null;
  nomeTiff: string | null;
}

/** Corpo do PUT de um Conjunto: os campos e as folhas ao mesmo nível. */
export type GravarConjuntoMotu = Partial<ConjuntoMotuDados> & {
  /** Só nas fichas opcionais: ausente deixa o ZIP como está, `null` desliga-o. */
  processoDocumentoId?: string | null;
  plantas: Pick<FolhaConjuntoMotu, 'id' | 'nomeRegulamento' | 'numeroFolha'>[];
};

/** Um registo da ficha MOTU; `dados` depende do `tipo`. `zip` e `plantas` só vêm nos Conjuntos. */
export interface RegistoMotu {
  id: string;
  tipo: 'SERIE' | 'CDG';
  opcional: boolean;
  completo: boolean;
  titulo: string | null;
  zip?: { id: string; nome: string } | null;
  dados: SerieMotuDados | ConjuntoMotuDados | null;
  plantas?: FolhaConjuntoMotu[] | null;
}

/** Ficha MOTU do processo (`GET /processos/{id}/metadados-motu`). */
export interface MetadadosMotu {
  aplicaFichaMotu: boolean;
  responsavel: ResponsavelMetadados;
  registos: RegistoMotu[];
}
