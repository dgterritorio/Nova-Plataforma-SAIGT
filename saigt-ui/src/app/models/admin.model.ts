/**
 * `emAnalise` = `EM_VALIDACAO_DGT`; `publicadosComSucesso` = `ENVIADO_INCM` + `PUBLICADO`;
 * `comPrazoExpirado` = Dinâmicas `EM_CURSO` com `dataCaducidade` passada.
 */
export interface EstatisticasAdmin {
  totalProcessos: number;
  emAnalise: number;
  publicadosComSucesso: number;
  comPrazoExpirado: number;
  submissoesPorMes: SubmissaoMensal[];
  distribuicaoPorEstado: EstadoContagem[];
  atividadeRecente: AtividadeRecente[];
  publicadosPorEntidade: PublicadosPorEntidade[];
}

/**
 * Mesmos estados de sucesso de `publicadosComSucesso`. `total` soma todos os tipos, não só as 4 colunas.
 * `ultimaPublicacao` `null` sem nenhum ato com `dataPublicacaoDr`, só preenchida pela integração INCM (por construir).
 */
export interface PublicadosPorEntidade {
  entidade: string;
  pdm: number;
  pp: number;
  pu: number;
  prot: number;
  total: number;
  caducados: number;
  ultimaPublicacao: string | null;
}

export interface SubmissaoMensal {
  mes: string;
  total: number;
}

export interface EstadoContagem {
  estado: string;
  total: number;
}

/** `utilizadorNome`: `nome` do ator, ou o `email` se ainda não entrou. Nunca nulo. */
export interface AtividadeRecente {
  tipoOcorrencia: string;
  idOcorrencia: string;
  acao: string;
  utilizadorId: string;
  utilizadorNome: string;
  criadoEm: string;
}
