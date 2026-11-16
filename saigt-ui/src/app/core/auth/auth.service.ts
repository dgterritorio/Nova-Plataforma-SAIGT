import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Injectable, InjectionToken, computed, inject, signal } from '@angular/core';
import { Observable, catchError, finalize, map, of, share, switchMap, tap, throwError } from 'rxjs';
import { environment } from '../../../environments/environment';
import { ApiError } from '../models/error-response.model';
import {
  EntidadeElegivel,
  ErroAutenticacao,
  JwtClaims,
  LoginRequest,
  LoginResponse,
  OAuthPreAutenticacaoResposta,
  OAuthUtilizadorResposta,
  PermissoesResponse,
  ResultadoLogin,
} from './auth.models';

/** Só dev, nunca prod (XSS): guarda o refresh token em `sessionStorage` para a sessão sobreviver a um refresh da página. */
export const PERSISTIR_SESSAO = new InjectionToken<boolean>('PERSISTIR_SESSAO', {
  providedIn: 'root',
  factory: () => environment.persistirSessao,
});

const CHAVE_REFRESH_TOKEN = 'saigt.refreshToken';

export const URL_TOKEN = `${environment.apiBase}/oauth2/token`;
export const URL_REVOGACAO = `${environment.apiBase}/oauth2/revoke`;
const CLIENTE_SAIGT_UI = 'saigt-ui';
export const GRANT_INCM_PASSWORD = 'urn:saigt:params:oauth:grant-type:incm-password';
export const GRANT_AD_PASSWORD = 'urn:saigt:params:oauth:grant-type:ad-password';
export const GRANT_ENTIDADE = 'urn:saigt:params:oauth:grant-type:entidade';
export const GRANT_REFRESH_TOKEN = 'refresh_token';
/** `error_description` do servidor para a pré-autenticação inválida, expirada ou já usada (grant `entidade`). */
export const DESCRICAO_PRE_AUTENTICACAO_INVALIDA = 'Pré-autenticação inválida, expirada ou já usada.';

/** Sessão só em memória, nunca `localStorage`/`sessionStorage` (XSS). Um refresh da página perde a sessão, de propósito. Exceção só em dev: `PERSISTIR_SESSAO`. */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly accessTokenSignal = signal<string | null>(null);
  private readonly refreshTokenSignal = signal<string | null>(null);
  private readonly tokenPreAutenticacaoSignal = signal<string | null>(null);
  private readonly preAutenticacaoExpiraEm = signal<number | null>(null);
  private readonly entidadesElegiveisSignal = signal<EntidadeElegivel[] | null>(null);
  private readonly permissoesSignal = signal<PermissoesResponse | null>(null);

  readonly accessToken = this.accessTokenSignal.asReadonly();
  readonly refreshToken = this.refreshTokenSignal.asReadonly();
  readonly tokenPreAutenticacao = this.tokenPreAutenticacaoSignal.asReadonly();
  readonly entidadesElegiveis = this.entidadesElegiveisSignal.asReadonly();
  readonly permissoes = this.permissoesSignal.asReadonly();
  readonly origem = computed(() => this.permissoesSignal()?.origem ?? null);
  /** `DGT` para MASTER e TECNICO; nulo para VISITANTE, que nunca atua sobre Procedimentos. */
  readonly ator = computed(() => this.permissoesSignal()?.ator ?? null);
  /** Só preenchido quando `origem() === 'AD_DGT'`. */
  readonly perfilInterno = computed(() => this.permissoesSignal()?.perfilInterno ?? null);
  /**
   * Como a Camada 3 (#400): só uma sessão com tipo de entidade e com permissões em procedimentos. Os internos (MASTER
   * incluído) e a PCM sem linhas em `permissao_acao` não veem o botão. Só affordance: a garantia é o `POST`.
   */
  readonly podeCriarInstrumento = computed(
    () => !!this.permissoesSignal()?.tipoEntidade && this.procedimentosPermitidos().length > 0,
  );
  readonly procedimentosPermitidos = computed(() => this.permissoesSignal()?.procedimentosPermitidos ?? []);
  readonly estaAutenticado = computed(() => this.accessTokenSignal() !== null);
  readonly emEscolhaDeEntidade = computed(() => this.tokenPreAutenticacaoSignal() !== null);

  /** Só para apresentação, nunca persistidas. */
  private readonly claims = computed<JwtClaims | null>(() => {
    const token = this.accessTokenSignal();
    return token ? this.decodeToken(token) : null;
  });

  readonly nome = computed(() => this.claims()?.name ?? null);
  readonly email = computed(() => this.claims()?.email ?? null);
  /** `sub` do token: o `criadoPorId`/`utilizadorId` das respostas do backend. */
  readonly utilizadorId = computed(() => this.claims()?.sub ?? null);
  /** Nunca inventado no cliente: nulo enquanto o token não trouxer a claim. */
  readonly cargo = computed(() => this.claims()?.cargo ?? null);
  /** Só preenchido para `origem() === 'INCM'`. */
  readonly entidade = computed(() => this.claims()?.entidade ?? null);

  private readonly http = inject(HttpClient);
  private readonly persistirSessao = inject(PERSISTIR_SESSAO);
  /** Renovação em curso, partilhada: com a rotação, dois refresh com o mesmo token dariam `invalid_grant` ao segundo. */
  private renovacaoEmCurso: Observable<LoginResponse> | null = null;

  /** No arranque da app. Nunca falha: sem sessão recuperável, fica por autenticar. */
  restaurarSessao(): Observable<unknown> {
    const refreshToken = this.persistirSessao ? sessionStorage.getItem(CHAVE_REFRESH_TOKEN) : null;
    if (!refreshToken) {
      return of(null);
    }

    this.refreshTokenSignal.set(refreshToken);
    return this.refresh().pipe(
      catchError(() => {
        this.limparSessao();
        return of(null);
      }),
    );
  }

  /**
   * INCM (`incm-password`): guarda o token de pré-autenticação e as entidades, e emite `ESCOLHA_ENTIDADE`. DGT
   * (`ad-password`): abre a sessão e só emite `SESSAO` com `permissoes()` já preenchidas (ver `aplicarSessao`).
   * Erros como `ErroAutenticacao`.
   */
  login(request: LoginRequest): Observable<ResultadoLogin> {
    const grant = request.utilizadorInternoDgt ? GRANT_AD_PASSWORD : GRANT_INCM_PASSWORD;
    return this.pedirToken<OAuthUtilizadorResposta | OAuthPreAutenticacaoResposta>({
      grant_type: grant,
      username: request.utilizador,
      password: request.password,
    }).pipe(
      catchError((erro: unknown) => throwError(() => paraErroAutenticacao(erro))),
      switchMap((resposta): Observable<ResultadoLogin> => {
        if ('entidades' in resposta) {
          this.tokenPreAutenticacaoSignal.set(resposta.access_token);
          this.preAutenticacaoExpiraEm.set(Date.now() + resposta.expires_in * 1000);
          this.entidadesElegiveisSignal.set(resposta.entidades);
          return of('ESCOLHA_ENTIDADE');
        }
        return this.aplicarSessao(paraSessao(resposta)).pipe(map(() => 'SESSAO'));
      }),
    );
  }

  /**
   * Segundo passo do login INCM, só com `emEscolhaDeEntidade()`, pela adesão (nunca pelo NIPC). Uma recusa da entidade
   * mantém a pré-autenticação, para se poder escolher outra; uma pré-autenticação expirada limpa-a.
   */
  escolherEntidade(incmEnrollmentId: string): Observable<void> {
    const tokenPreAutenticacao = this.tokenPreAutenticacaoSignal();
    if (!tokenPreAutenticacao) {
      throw new Error('Sem escolha de entidade em curso.');
    }

    return this.pedirToken<OAuthUtilizadorResposta>({
      grant_type: GRANT_ENTIDADE,
      pre_auth_token: tokenPreAutenticacao,
      incm_enrollment_id: incmEnrollmentId,
    }).pipe(
      catchError((erro: unknown) => {
        const erroAutenticacao = this.erroDaEscolha(paraErroAutenticacao(erro));
        if (erroAutenticacao.motivo === 'PRE_AUTENTICACAO_EXPIRADA') {
          this.limparPreAutenticacao();
        }
        return throwError(() => erroAutenticacao);
      }),
      switchMap((resposta) => {
        this.limparPreAutenticacao();
        return this.aplicarSessao(paraSessao(resposta)).pipe(map(() => undefined));
      }),
    );
  }

  /**
   * Renova antes de um pedido longo (carregamento de ficheiro) se o token de acesso expira dentro de `margemSegundos`:
   * um 401 a meio do envio do corpo chega ao browser como estado 0, sem dar ao interceptor a hipótese de repetir.
   * Sem sessão ou sem `exp` não renova. Erros como em `refresh()`.
   */
  renovarSeExpirarEm(margemSegundos: number): Observable<unknown> {
    const expiraEm = this.claims()?.exp;
    if (expiraEm === undefined || !this.refreshTokenSignal() || expiraEm * 1000 - Date.now() >= margemSegundos * 1000) {
      return of(null);
    }
    return this.refresh();
  }

  /**
   * Só para o interceptor, o arranque e os carregamentos de ficheiros, nunca para componentes. Grant `refresh_token` (RFC 6749 §6): o servidor roda o
   * refresh token em cada uso. Pedidos simultâneos partilham a mesma renovação. Um `invalid_grant` (sessão revogada,
   * expirada ou token já usado) termina a sessão local; quem chama decide a navegação. Mesma garantia de emissão do `login()`.
   */
  refresh(): Observable<LoginResponse> {
    if (this.renovacaoEmCurso) {
      return this.renovacaoEmCurso;
    }
    const refreshToken = this.refreshTokenSignal();
    if (!refreshToken) {
      throw new Error('Sem sessão em curso.');
    }

    this.renovacaoEmCurso = this.pedirToken<OAuthUtilizadorResposta>({
      grant_type: GRANT_REFRESH_TOKEN,
      refresh_token: refreshToken,
    }).pipe(
      catchError((erro: unknown) => {
        if (erro instanceof ApiError && erro.codigo === 'invalid_grant') {
          this.limparSessao();
        }
        return throwError(() => erro);
      }),
      switchMap((resposta) => {
        const sessao = paraSessao(resposta);
        return this.aplicarSessao(sessao).pipe(map(() => sessao));
      }),
      finalize(() => (this.renovacaoEmCurso = null)),
      share(),
    );
    return this.renovacaoEmCurso;
  }

  /**
   * Limpa já o estado local e só depois revoga o refresh token no servidor (`POST /oauth2/revoke`, RFC 7009), por isso
   * «Sair» termina sempre a sessão no browser. Nunca falha: um revoke sem resposta deixa a sessão no servidor até ao fim
   * das 8 h, e o access token em curso vale até expirar (no máximo 15 min), como no contrato.
   */
  logout(): Observable<void> {
    const refreshToken = this.refreshTokenSignal();
    this.limparSessao();
    if (!refreshToken) {
      return of(undefined);
    }
    const corpo = new URLSearchParams({
      token: refreshToken,
      token_type_hint: 'refresh_token',
      client_id: CLIENTE_SAIGT_UI,
    }).toString();
    return this.http
      .post(URL_REVOGACAO, corpo, {
        headers: new HttpHeaders({ 'Content-Type': 'application/x-www-form-urlencoded' }),
        responseType: 'text',
      })
      .pipe(
        map(() => undefined),
        catchError(() => of(undefined)),
      );
  }

  /** Nunca deixa estado parcial. */
  limparSessao(): void {
    this.accessTokenSignal.set(null);
    this.refreshTokenSignal.set(null);
    this.limparPreAutenticacao();
    this.permissoesSignal.set(null);
    if (this.persistirSessao) {
      sessionStorage.removeItem(CHAVE_REFRESH_TOKEN);
    }
  }

  private limparPreAutenticacao(): void {
    this.tokenPreAutenticacaoSignal.set(null);
    this.preAutenticacaoExpiraEm.set(null);
    this.entidadesElegiveisSignal.set(null);
  }

  /** O servidor recusa com `invalid_grant` tanto a adesão como a pré-autenticação: distingue-as pela descrição e pela validade. */
  private erroDaEscolha(erro: ErroAutenticacao): ErroAutenticacao {
    const expiraEm = this.preAutenticacaoExpiraEm();
    const expirou = expiraEm !== null && Date.now() >= expiraEm;
    if (erro.motivo === 'RECUSADO' && (expirou || erro.descricao === DESCRICAO_PRE_AUTENTICACAO_INVALIDA)) {
      return new ErroAutenticacao('PRE_AUTENTICACAO_EXPIRADA', erro.descricao);
    }
    return erro;
  }

  /** Corpo `application/x-www-form-urlencoded` pelo `URLSearchParams`, que codifica `+`, `&` e `=` das palavras-passe. */
  private pedirToken<T>(parametros: Record<string, string>): Observable<T> {
    const corpo = new URLSearchParams({ ...parametros, client_id: CLIENTE_SAIGT_UI }).toString();
    return this.http.post<T>(URL_TOKEN, corpo, {
      headers: new HttpHeaders({ 'Content-Type': 'application/x-www-form-urlencoded' }),
    });
  }

  /** Devolve `carregarPermissoes()` em vez de subscrever: quem chama só recebe a sessão com `perfilInterno()` já resolvido. */
  private aplicarSessao(resposta: LoginResponse): Observable<PermissoesResponse> {
    this.accessTokenSignal.set(resposta.accessToken);
    this.refreshTokenSignal.set(resposta.refreshToken);
    if (this.persistirSessao) {
      sessionStorage.setItem(CHAVE_REFRESH_TOKEN, resposta.refreshToken);
    }
    return this.carregarPermissoes();
  }

  /** Uma vez por login/refresh, nunca por navegação nem em polling. */
  private carregarPermissoes(): Observable<PermissoesResponse> {
    return this.http
      .get<PermissoesResponse>(`${environment.apiBase}/api/v1/sessao/me/permissoes`)
      .pipe(tap((permissoes) => this.permissoesSignal.set(permissoes)));
  }

  /**
   * Sem verificar assinatura: token da própria sessão e nada é decidido no cliente com base nele. Token malformado dá `null`.
   * `atob` devolve Latin-1 e o payload é UTF-8, daí o `TextDecoder` (senão os acentos corrompem).
   */
  private decodeToken(token: string): JwtClaims | null {
    try {
      const payload = token.split('.')[1];
      const base64 = payload.replaceAll('-', '+').replaceAll('_', '/');
      const preenchimento = base64.length % 4 === 0 ? '' : '='.repeat(4 - (base64.length % 4));
      const binario = atob(base64 + preenchimento);
      const bytes = Uint8Array.from(binario, (c) => c.codePointAt(0) ?? 0);
      const json = new TextDecoder('utf-8').decode(bytes);
      return JSON.parse(json) as JwtClaims;
    } catch {
      return null;
    }
  }
}

function paraSessao(resposta: OAuthUtilizadorResposta): LoginResponse {
  return { accessToken: resposta.access_token, refreshToken: resposta.refresh_token };
}

/**
 * O `errorEnvelopeInterceptor` passa os erros da RFC 6749 §5.2 a `ApiError` (`codigo` = `error`, `mensagem` =
 * `error_description`). Sem corpo reconhecível (`ERRO_DESCONHECIDO`) o pedido não chegou ao servidor de autorização.
 */
export function paraErroAutenticacao(erro: unknown): ErroAutenticacao {
  if (!(erro instanceof ApiError)) {
    return new ErroAutenticacao('OUTRO', null);
  }
  switch (erro.codigo) {
    case 'invalid_grant':
      return new ErroAutenticacao('RECUSADO', erro.mensagem || null);
    case 'server_error':
    case 'ERRO_DESCONHECIDO':
      return new ErroAutenticacao('INDISPONIVEL', null);
    default:
      return new ErroAutenticacao('OUTRO', null);
  }
}
