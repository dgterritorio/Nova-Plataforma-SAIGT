import { TipoInstrumento } from './instrumento.model';

export interface PassoWorkflow {
  nome: string;
  ordem: number;
  obrigatorio: boolean;
  serieDr: string;
  prazoDias: number | null;
  tipoPrazo: string | null;
  atores: string[];
}

export interface Workflow {
  id: number;
  titulo: string;
  tipoInstrumento: TipoInstrumento;
  ativo: boolean;
  numeroPassos: number;
  processosAtivos: number;
  passos: PassoWorkflow[];
}
