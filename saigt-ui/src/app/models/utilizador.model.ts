import type { PerfilInterno } from '../core/auth/auth.models';

export type { PerfilInterno };

export interface UtilizadorFiltro {
  origemAutenticacao?: string;
  perfilInterno?: PerfilInterno;
  entidadeId?: number;
  atuouDesde?: string;
}

/** `responsavel`: a pessoa é o responsável do termo de adesão da entidade. */
export interface EntidadeVinculo {
  entidadeId: number;
  entidadeNome: string;
  responsavel: boolean;
}

/**
 * Uma conta por pessoa e por origem.
 * `entidades` vem dos vínculos da adesão INCM (`utilizador_entidade`), nunca de `auditoria`: vazia para `AD_DGT`.
 * `ultimoLoginEm`: último evento `LOGIN`, `null` se nunca entrou.
 * `email`: `null` nas contas `AD_DGT` cujo AD não devolve email.
 * `ultimaAtuacaoEm`: só na listagem com `entidadeId`, para quem já atuou por essa entidade.
 */
export interface Utilizador {
  id: string;
  email: string | null;
  origemAutenticacao: string;
  perfilInterno: PerfilInterno | null;
  nome: string | null;
  cargo: string | null;
  entidades: EntidadeVinculo[];
  ultimoLoginEm: string | null;
  ultimaAtuacaoEm?: string;
}

export interface AlterarPerfilInternoRequest {
  perfilInterno: PerfilInterno;
}

export interface RevogarSessoesResponse {
  sessoesRevogadas: number;
}

/** Só para `origemAutenticacao = AD_DGT`. */
export const PERFIS_INTERNOS = ['MASTER', 'TECNICO', 'VISITANTE'] as const satisfies readonly PerfilInterno[];

/** Só para o ecrã, nunca em pedidos ao backend. */
export const PERFIL_INTERNO_LABEL: Record<PerfilInterno, string> = {
  MASTER: 'Master',
  TECNICO: 'Técnico',
  VISITANTE: 'Visitante',
};

/** Responsável do termo de adesão da entidade da sessão (tabela `entidade`). */
export interface ResponsavelEntidade {
  nome: string | null;
  email: string | null;
  cargo: string | null;
}

/** Entidade da sessão de um utilizador INCM no Perfil; sem morada (a EF não a pede). */
export interface EntidadeSessaoPerfil {
  codigo: string | null;
  nome: string | null;
  tipoEntidade: string | null;
  nipc: string | null;
  telefone: string | null;
  email: string | null;
  responsavel: ResponsavelEntidade | null;
}

/** `cargo` e `entidade` só nos INCM; `telefone`, `morada` e `perfilInterno` só nos AD_DGT. */
export interface UtilizadorMe {
  nome: string | null;
  email: string | null;
  cargo: string | null;
  telefone: string | null;
  morada: string | null;
  origemAutenticacao: 'INCM' | 'AD_DGT';
  perfilInterno: PerfilInterno | null;
  entidade: EntidadeSessaoPerfil | null;
}

/** Só AD_DGT. Campo omitido fica inalterado; vazio limpa. Qualquer outro campo dá 400. */
export interface AtualizarPerfilRequest {
  email?: string;
  telefone?: string;
  morada?: string;
}
