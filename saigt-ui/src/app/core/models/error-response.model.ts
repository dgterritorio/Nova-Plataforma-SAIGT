export interface CampoErro {
  path: string;
  erro: string;
}

export interface ErrorResponse {
  codigo: string;
  mensagem: string;
  timestamp: string;
  traceId: string;
  campos: CampoErro[] | null;
}

/** Propagado pelo `errorEnvelopeInterceptor` em vez do `HttpErrorResponse` em bruto. */
export class ApiError {
  constructor(
    public readonly codigo: string,
    public readonly mensagem: string,
    public readonly traceId: string,
    public readonly campos: CampoErro[] | null,
    /** Estado HTTP da resposta; 0 quando o pedido não teve resposta. Nulo nos erros criados fora do interceptor. */
    public readonly estado: number | null = null,
  ) {}
}

export const MENSAGEM_SEM_LIGACAO = 'Não foi possível comunicar com o servidor. Verifique a ligação e tente novamente.';

/** Sem resposta (0) ou o proxy sem a API atrás (502/503/504), sempre sem o envelope da API. */
const ESTADOS_SEM_LIGACAO = [0, 502, 503, 504];

export function semLigacao(erro: unknown): boolean {
  return erro instanceof ApiError && erro.codigo === 'ERRO_DESCONHECIDO' && erro.estado !== null && ESTADOS_SEM_LIGACAO.includes(erro.estado);
}
