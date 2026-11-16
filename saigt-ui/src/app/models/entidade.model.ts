export type TipoEntidade = 'CM' | 'CCDR_DR' | 'AM' | 'CIM' | 'PCM' | 'SEC_ESTADO' | 'DGT' | 'ICNF' | 'APA' | 'DGPC' | 'IP';

/** Rótulos curtos só para o ecrã; a API recebe e devolve o valor. */
export const TIPOS_ENTIDADE: readonly { valor: TipoEntidade; rotulo: string }[] = [
  { valor: 'CM', rotulo: 'CM' },
  { valor: 'CCDR_DR', rotulo: 'CCDR/DR' },
  { valor: 'AM', rotulo: 'AM' },
  { valor: 'CIM', rotulo: 'CIM' },
  { valor: 'PCM', rotulo: 'PCM' },
  { valor: 'SEC_ESTADO', rotulo: 'SecEstado' },
  { valor: 'DGT', rotulo: 'DGT' },
  { valor: 'ICNF', rotulo: 'ICNF' },
  { valor: 'APA', rotulo: 'APA' },
  { valor: 'DGPC', rotulo: 'DGPC' },
  { valor: 'IP', rotulo: 'IP' },
];

export function rotuloTipoEntidade(tipo: TipoEntidade | null | undefined): string | null {
  return TIPOS_ENTIDADE.find((opcao) => opcao.valor === tipo)?.rotulo ?? null;
}

/** Uma opção da lista das entidades emitentes (`GET /entidades/emitentes`): só o id e o nome. */
export interface EntidadeEmitente {
  id: number;
  nome: string;
}

export interface EntidadeFiltro {
  ativo?: boolean;
  /** Contém, no código e nas descrições SAIGT e INCM. */
  nome?: string;
  tipoEntidade?: TipoEntidade;
}

export interface ContactoPrincipal {
  id: number;
  descricao: string | null;
  email: string;
  telefone: string | null;
}

/** `contactoPrincipal` `null` sem contacto principal ativo em `entidade_contacto`. */
export interface Entidade {
  id: number;
  codigo: string | null;
  /** Opcional e não único. */
  nipc: string | null;
  /** DTCC do concelho; só nas CM. */
  dico: string | null;
  descricaoIncm: string;
  descricaoSaigt: string | null;
  tipoEntidade: TipoEntidade | null;
  /** Telefone e email devolvidos pela INCM; vazios sem login. */
  telefone: string | null;
  email: string | null;
  ativo: boolean;
  atualizadoEm: string | null;
  /** Obsoleto na API, igual a `descricaoIncm`. */
  nome: string;
  utilizadorNome: string | null;
  utilizadorEmail: string | null;
  utilizadorCargo: string | null;
  contactoPrincipal: ContactoPrincipal | null;
}

export interface CriarEntidadeRequest {
  codigo: string;
  descricaoIncm: string;
  descricaoSaigt: string;
  tipoEntidade: TipoEntidade;
  nipc?: string;
  dico?: string;
}

/** Ausente mantém o valor; `nipc` e `dico` vazios limpam-nos. */
export type AtualizarEntidadeRequest = Partial<CriarEntidadeRequest>;

export interface Contacto {
  id: number;
  email: string;
  descricao: string | null;
  telefone: string | null;
  principal: boolean;
  ativo: boolean;
  criadoEm: string;
}

export interface ContactoRequest {
  email: string;
  descricao?: string | null;
  telefone?: string | null;
  principal?: boolean;
}

/** Ausente mantém o valor; `descricao` e `telefone` vazios limpam-nos. */
export interface AtualizarContactoRequest {
  email?: string;
  descricao?: string;
  telefone?: string;
  principal?: boolean;
  ativo?: boolean;
}

export interface Concelho {
  /** DTCC, 4 algarismos. */
  codigo: string;
  nome: string;
  nut2: string | null;
}
