/** `PlantaResponse` do contrato: uma linha da tabela Plantas SAIGT/SNIT do backoffice. */
export interface Planta {
  id: string;
  processoId: string;
  numeroProcesso: string;
  numeroDeposito: string | null;
  idus: string | null;
  processoDocumentoId: string;
  nomeRegulamento: string | null;
  numeroFolha: string | null;
  nomeTiff: string | null;
  nomeZip: string | null;
  /** Gerado pelo sistema (link público INCM); só de leitura. */
  readonly linkJpeg: string | null;
  emVigor: boolean;
  atualizadoEm: string;
  atualizadoPorNome: string | null;
}

export interface PlantaFiltro {
  numeroProcesso?: string;
  numeroDeposito?: string;
  emVigor?: boolean;
}

/** Só os campos presentes mudam; uma string vazia grava nulo. */
export interface AtualizarPlantaRequest {
  nomeRegulamento?: string;
  numeroFolha?: string;
  nomeTiff?: string;
  nomeZip?: string;
  emVigor?: boolean;
}

export const LIMITES_PLANTA = {
  nomeRegulamento: 500,
  numeroFolha: 20,
  nomeTiff: 300,
  nomeZip: 300,
} as const;
