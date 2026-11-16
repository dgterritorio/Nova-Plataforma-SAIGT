/** `utilizador` é o email INCM ou, com `utilizadorInternoDgt`, o nome de autenticação no AD da DGT. */
export interface LoginRequest {
  utilizador: string;
  password: string;
  utilizadorInternoDgt: boolean;
}

/** Tokens da sessão ativa, já tirados da resposta do `POST /oauth2/token`. */
export interface LoginResponse {
  accessToken: string;
  refreshToken: string;
}

/** Resposta de sucesso do `POST /oauth2/token` (RFC 6749 §5.1) para os grants `entidade` e `ad-password`. */
export interface OAuthUtilizadorResposta {
  access_token: string;
  token_type: string;
  expires_in: number;
  refresh_token: string;
}

/** Resposta do grant `incm-password`: o `access_token` é o token de pré-autenticação e só serve para o grant `entidade`. */
export interface OAuthPreAutenticacaoResposta {
  access_token: string;
  token_type: string;
  expires_in: number;
  entidades: EntidadeElegivel[];
}

/** Adesão INCM. A escolha envia sempre o `incmEnrollmentId`: o NIPC não é único. */
export interface EntidadeElegivel {
  incmEnrollmentId: string;
  nipc: string;
  nome: string;
}

export type ResultadoLogin = 'ESCOLHA_ENTIDADE' | 'SESSAO';

/**
 * `RECUSADO` traz a `descricao` do servidor (`invalid_grant`); `INDISPONIVEL` é `server_error` ou um pedido sem resposta
 * do servidor de autorização.
 */
export type MotivoErroAutenticacao = 'RECUSADO' | 'INDISPONIVEL' | 'PRE_AUTENTICACAO_EXPIRADA' | 'OUTRO';

export class ErroAutenticacao {
  constructor(
    public readonly motivo: MotivoErroAutenticacao,
    public readonly descricao: string | null,
  ) {}
}

export type PerfilInterno = 'MASTER' | 'TECNICO' | 'VISITANTE';

export interface PermissoesResponse {
  origem: 'INCM' | 'AD_DGT';
  ator: string | null;
  perfilInterno: PerfilInterno | null;
  /** Tipo da entidade da sessão (`CM`, `PCM`, `CCDR_DR`, `DGT`…); nulo para internos. */
  tipoEntidade?: string | null;
  procedimentosPermitidos: number[];
}

/** Só para apresentação: a autorização fica no backend. Sem `perfil_interno`, que já chega por `PermissoesResponse`. */
export interface JwtClaims {
  sub: string;
  /** Fim da validade, em segundos desde a época (RFC 7519). */
  exp?: number;
  name?: string;
  email?: string;
  /** Só para `origem = INCM`: o AD da DGT não o tem. */
  cargo?: string;
  entidade?: JwtClaimsEntidade;
}

export interface JwtClaimsEntidade {
  nipc: string;
  id?: number;
  incm_enrollment_id?: string;
  codigo?: string;
  tipo_entidade?: string;
  nome: string;
  telefone?: string;
  email?: string;
  morada?: JwtClaimsMorada;
  /** Responsável do termo de adesão INCM, distinto do contacto institucional (`entidade_contacto`), que não passa pelo token. */
  responsavel?: JwtClaimsResponsavel;
}

export interface JwtClaimsMorada {
  arteria: string | null;
  numero: string | null;
  piso: string | null;
  codigo_postal: string | null;
  designacao_postal: string | null;
  localidade: string | null;
}

export interface JwtClaimsResponsavel {
  nome: string;
  email: string;
  cargo: string;
}
