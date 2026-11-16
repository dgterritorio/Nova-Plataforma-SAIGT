import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, TestRequest, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { errorEnvelopeInterceptor } from '../interceptors/error-envelope.interceptor';
import {
  AuthService,
  DESCRICAO_PRE_AUTENTICACAO_INVALIDA,
  GRANT_AD_PASSWORD,
  GRANT_ENTIDADE,
  GRANT_INCM_PASSWORD,
  GRANT_REFRESH_TOKEN,
  PERSISTIR_SESSAO,
  URL_REVOGACAO,
  URL_TOKEN,
} from './auth.service';
import {
  EntidadeElegivel,
  ErroAutenticacao,
  JwtClaims,
  LoginRequest,
  OAuthPreAutenticacaoResposta,
  OAuthUtilizadorResposta,
  PermissoesResponse,
} from './auth.models';

/** UTF-8 antes do base64, como o backend: `btoa` direto codificaria em Latin-1 e esconderia a corrupção de acentos. */
function criarToken(claims: Partial<JwtClaims>): string {
  const bytes = new TextEncoder().encode(JSON.stringify(claims));
  const binario = Array.from(bytes, (b) => String.fromCharCode(b)).join('');
  const base64 = btoa(binario).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
  return `cabecalho.${base64}.assinatura`;
}

const DGT: LoginRequest = { utilizador: 'ana.silva', password: 'senha', utilizadorInternoDgt: true };
const INCM: LoginRequest = { utilizador: 'ricardo@incm.pt', password: 'senha', utilizadorInternoDgt: false };
const ENTIDADES: EntidadeElegivel[] = [
  { incmEnrollmentId: 'ENR-101', nipc: '505187531', nome: 'Município de Cascais' },
  { incmEnrollmentId: 'ENR-103', nipc: '999999990', nome: 'Entidade de Teste Sem Correspondência' },
];

function tokens(accessToken: string, refreshToken = 'r1'): OAuthUtilizadorResposta {
  return { access_token: accessToken, token_type: 'Bearer', expires_in: 900, refresh_token: refreshToken };
}

function preAutenticacao(entidades: EntidadeElegivel[] = ENTIDADES): OAuthPreAutenticacaoResposta {
  return { access_token: 'pre-auth-token', token_type: 'Bearer', expires_in: 300, entidades };
}

function parametros(requisicao: TestRequest): Record<string, string> {
  return Object.fromEntries(new URLSearchParams(requisicao.request.body as string));
}

function erroDoServidor(requisicao: TestRequest, error: string, descricao?: string, status = 400): void {
  requisicao.flush({ error, ...(descricao ? { error_description: descricao } : {}) }, { status, statusText: 'Erro' });
}

describe('AuthService', () => {
  let service: AuthService;
  let httpTesting: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      // Os testes correm com o `environment.ts` de dev: fixa o comportamento de prod.
      providers: [
        provideHttpClient(withInterceptors([errorEnvelopeInterceptor])),
        provideHttpClientTesting(),
        { provide: PERSISTIR_SESSAO, useValue: false },
      ],
    });
    service = TestBed.inject(AuthService);
    httpTesting = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    sessionStorage.clear();
    vi.useRealTimers();
  });

  /** Login DGT (`ad-password`), já com as permissões respondidas. */
  function entrarDgt(accessToken = 'a1', refreshToken = 'r1'): void {
    service.login(DGT).subscribe();
    httpTesting.expectOne(URL_TOKEN).flush(tokens(accessToken, refreshToken));
    responderPermissoes();
  }

  function iniciarEscolha(entidades: EntidadeElegivel[] = ENTIDADES): void {
    service.login(INCM).subscribe();
    httpTesting.expectOne(URL_TOKEN).flush(preAutenticacao(entidades));
  }

  function capturarErro(observavel: { subscribe: (o: { error: (e: unknown) => void }) => unknown }): () => unknown {
    let erro: unknown = null;
    observavel.subscribe({ error: (e: unknown) => (erro = e) });
    return () => erro;
  }

  it('restaurarSessao ignora o sessionStorage e não chama o backend', () => {
    sessionStorage.setItem('saigt.refreshToken', 'r-guardado');
    let concluiu = false;

    service.restaurarSessao().subscribe({ complete: () => (concluiu = true) });

    expect(concluiu).toBe(true);
    expect(service.estaAutenticado()).toBe(false);
    httpTesting.verify();
  });

  function responderPermissoes(): void {
    const permissoes: PermissoesResponse = {
      origem: 'INCM',
      ator: 'ENTIDADE',
      perfilInterno: null,
      procedimentosPermitidos: [1, 2],
    };
    httpTesting.expectOne('/api/v1/sessao/me/permissoes').flush(permissoes);
  }

  it('login DGT: pede o grant ad-password em form-urlencoded pelo username, sem Bearer, e guarda os tokens em memória', () => {
    service.login({ utilizador: 'ana.silva', password: 'a+b&c=d', utilizadorInternoDgt: true }).subscribe();

    const requisicao = httpTesting.expectOne(URL_TOKEN);
    expect(requisicao.request.method).toBe('POST');
    expect(requisicao.request.headers.get('Content-Type')).toBe('application/x-www-form-urlencoded');
    expect(parametros(requisicao)).toEqual({
      grant_type: GRANT_AD_PASSWORD,
      client_id: 'saigt-ui',
      username: 'ana.silva',
      password: 'a+b&c=d',
    });
    requisicao.flush(tokens('access-1', 'refresh-1'));
    responderPermissoes();

    expect(service.accessToken()).toBe('access-1');
    expect(service.refreshToken()).toBe('refresh-1');
    expect(service.estaAutenticado()).toBe(true);
    expect(service.emEscolhaDeEntidade()).toBe(false);
    expect(service.origem()).toBe('INCM');
    expect(service.ator()).toBe('ENTIDADE');
    expect(service.procedimentosPermitidos()).toEqual([1, 2]);
  });

  it('login DGT emite SESSAO só depois de perfilInterno() estar preenchido', () => {
    let perfilNoMomentoDoNext: string | null = 'AINDA_NAO_EMITIU';
    let resultado: string | null = null;
    service.login(DGT).subscribe((r) => {
      resultado = r;
      perfilNoMomentoDoNext = service.perfilInterno();
    });

    httpTesting.expectOne(URL_TOKEN).flush(tokens('a1'));
    expect(perfilNoMomentoDoNext).toBe('AINDA_NAO_EMITIU');

    httpTesting
      .expectOne('/api/v1/sessao/me/permissoes')
      .flush({ origem: 'AD_DGT', ator: null, perfilInterno: 'MASTER', procedimentosPermitidos: [] } satisfies PermissoesResponse);

    expect(perfilNoMomentoDoNext).toBe('MASTER');
    expect(resultado).toBe('SESSAO');
  });

  it('expõe ator nulo e perfilInterno quando a sessão é de um utilizador interno sem ator', () => {
    service.login(DGT).subscribe();
    httpTesting.expectOne(URL_TOKEN).flush(tokens('a1'));
    httpTesting
      .expectOne('/api/v1/sessao/me/permissoes')
      .flush({ origem: 'AD_DGT', ator: null, perfilInterno: 'VISITANTE', procedimentosPermitidos: [] } satisfies PermissoesResponse);

    expect(service.origem()).toBe('AD_DGT');
    expect(service.ator()).toBeNull();
    expect(service.perfilInterno()).toBe('VISITANTE');
  });

  it.each([
    ['ENTIDADE', null, 'CM', [101], true],
    ['ENTIDADE', null, 'CCDR_DR', [101], true],
    ['ENTIDADE', null, 'PCM', [], false],
    ['DGT', 'MASTER', null, [], false],
    ['DGT', 'TECNICO', null, [], false],
    [null, 'VISITANTE', null, [], false],
  ] as const)('podeCriarInstrumento para o ator %s, perfil %s e tipo %s é %s', (ator, perfilInterno, tipoEntidade, procedimentos, esperado) => {
    service.login(DGT).subscribe();
    httpTesting.expectOne(URL_TOKEN).flush(tokens('a1'));
    httpTesting
      .expectOne('/api/v1/sessao/me/permissoes')
      .flush({ origem: 'AD_DGT', ator, perfilInterno, tipoEntidade, procedimentosPermitidos: [...procedimentos] } satisfies PermissoesResponse);

    expect(service.podeCriarInstrumento()).toBe(esperado);
  });

  it('login INCM: pede o grant incm-password e guarda a pré-autenticação e as entidades, sem autenticar', () => {
    let resultado: string | null = null;
    service.login(INCM).subscribe((r) => (resultado = r));

    const requisicao = httpTesting.expectOne(URL_TOKEN);
    expect(parametros(requisicao)).toEqual({
      grant_type: GRANT_INCM_PASSWORD,
      client_id: 'saigt-ui',
      username: 'ricardo@incm.pt',
      password: 'senha',
    });
    requisicao.flush(preAutenticacao());

    expect(resultado).toBe('ESCOLHA_ENTIDADE');
    expect(service.estaAutenticado()).toBe(false);
    expect(service.emEscolhaDeEntidade()).toBe(true);
    expect(service.tokenPreAutenticacao()).toBe('pre-auth-token');
    expect(service.entidadesElegiveis()).toEqual(ENTIDADES);
    httpTesting.verify();
  });

  it('login INCM com uma só entidade também passa pela escolha', () => {
    let resultado: string | null = null;
    service.login(INCM).subscribe((r) => (resultado = r));
    httpTesting.expectOne(URL_TOKEN).flush(preAutenticacao([ENTIDADES[0]]));

    expect(resultado).toBe('ESCOLHA_ENTIDADE');
    expect(service.entidadesElegiveis()).toHaveLength(1);
    expect(service.estaAutenticado()).toBe(false);
  });

  it('escolha: envia o pre_auth_token e o incm_enrollment_id, nunca o NIPC, e abre a sessão', () => {
    iniciarEscolha();

    service.escolherEntidade('ENR-101').subscribe();
    const requisicao = httpTesting.expectOne(URL_TOKEN);
    const corpo = parametros(requisicao);
    expect(corpo).toEqual({
      grant_type: GRANT_ENTIDADE,
      client_id: 'saigt-ui',
      pre_auth_token: 'pre-auth-token',
      incm_enrollment_id: 'ENR-101',
    });
    expect(requisicao.request.body as string).not.toContain('505187531');
    expect(corpo).not.toHaveProperty('nipc');
    requisicao.flush(tokens('access-final', 'refresh-final'));
    responderPermissoes();

    expect(service.estaAutenticado()).toBe(true);
    expect(service.accessToken()).toBe('access-final');
    expect(service.emEscolhaDeEntidade()).toBe(false);
    expect(service.entidadesElegiveis()).toBeNull();
  });

  it('escolha recusada por entidade sem correspondência: devolve a descrição e mantém a pré-autenticação', () => {
    iniciarEscolha();
    const descricao = 'A entidade não está criada na SAIGT. Foi enviado um pedido de apoio à DGT.';

    const erro = capturarErro(service.escolherEntidade('ENR-103'));
    erroDoServidor(httpTesting.expectOne(URL_TOKEN), 'invalid_grant', descricao);

    expect(erro()).toEqual(new ErroAutenticacao('RECUSADO', descricao));
    expect(service.emEscolhaDeEntidade()).toBe(true);
    expect(service.entidadesElegiveis()).toEqual(ENTIDADES);
    expect(service.estaAutenticado()).toBe(false);

    service.escolherEntidade('ENR-101').subscribe();
    expect(parametros(httpTesting.expectOne(URL_TOKEN))['pre_auth_token']).toBe('pre-auth-token');
  });

  it('escolha com a pré-autenticação recusada pelo servidor: PRE_AUTENTICACAO_EXPIRADA e limpa a escolha', () => {
    iniciarEscolha();

    const erro = capturarErro(service.escolherEntidade('ENR-101'));
    erroDoServidor(httpTesting.expectOne(URL_TOKEN), 'invalid_grant', DESCRICAO_PRE_AUTENTICACAO_INVALIDA);

    expect((erro() as ErroAutenticacao).motivo).toBe('PRE_AUTENTICACAO_EXPIRADA');
    expect(service.emEscolhaDeEntidade()).toBe(false);
    expect(service.entidadesElegiveis()).toBeNull();
  });

  it('escolha recusada depois dos 5 minutos da pré-autenticação conta como expirada, qualquer que seja a descrição', () => {
    vi.useFakeTimers();
    iniciarEscolha();
    vi.advanceTimersByTime(301_000);

    const erro = capturarErro(service.escolherEntidade('ENR-101'));
    erroDoServidor(httpTesting.expectOne(URL_TOKEN), 'invalid_grant', 'Outra descrição.');

    expect((erro() as ErroAutenticacao).motivo).toBe('PRE_AUTENTICACAO_EXPIRADA');
    expect(service.emEscolhaDeEntidade()).toBe(false);
  });

  it('escolha com server_error: INDISPONIVEL e mantém a escolha em curso', () => {
    iniciarEscolha();

    const erro = capturarErro(service.escolherEntidade('ENR-101'));
    erroDoServidor(httpTesting.expectOne(URL_TOKEN), 'server_error', 'Serviço da INCM temporariamente indisponível.', 500);

    expect((erro() as ErroAutenticacao).motivo).toBe('INDISPONIVEL');
    expect(service.emEscolhaDeEntidade()).toBe(true);
  });

  it.each([
    ['invalid_grant', 'Credenciais inválidas.', 400, 'RECUSADO', 'Credenciais inválidas.'],
    ['invalid_grant', 'Utilizador sem entidade associada na INCM.', 400, 'RECUSADO', 'Utilizador sem entidade associada na INCM.'],
    ['server_error', 'Serviço da INCM temporariamente indisponível.', 500, 'INDISPONIVEL', null],
    ['invalid_request', 'Parâmetro em falta.', 400, 'OUTRO', null],
  ] as const)('login recusado com %s ("%s") dá %s', (codigo, descricao, status, motivo, descricaoEsperada) => {
    const erro = capturarErro(service.login(INCM));
    erroDoServidor(httpTesting.expectOne(URL_TOKEN), codigo, descricao, status);

    expect(erro()).toEqual(new ErroAutenticacao(motivo, descricaoEsperada));
    expect(service.estaAutenticado()).toBe(false);
    expect(service.emEscolhaDeEntidade()).toBe(false);
  });

  it('login sem resposta do servidor de autorização (gateway sem a API) dá INDISPONIVEL', () => {
    const erro = capturarErro(service.login(DGT));
    httpTesting.expectOne(URL_TOKEN).flush('<html>502</html>', { status: 502, statusText: 'Bad Gateway' });

    expect((erro() as ErroAutenticacao).motivo).toBe('INDISPONIVEL');
  });

  it('nunca escreve nenhum token em localStorage nem sessionStorage (login INCM com escolha e login DGT)', () => {
    iniciarEscolha();
    service.escolherEntidade('ENR-101').subscribe();
    httpTesting.expectOne(URL_TOKEN).flush(tokens('access-1', 'refresh-1'));
    responderPermissoes();
    entrarDgt('access-2', 'refresh-2');

    expect(localStorage).toHaveLength(0);
    expect(sessionStorage).toHaveLength(0);
  });

  it('refresh: pede o grant refresh_token em form-urlencoded com o client_id e guarda o par rodado em memória', () => {
    entrarDgt('a1', 'r1');
    let sessao: unknown = null;

    service.refresh().subscribe((resposta) => (sessao = resposta));
    const requisicao = httpTesting.expectOne(URL_TOKEN);
    expect(requisicao.request.method).toBe('POST');
    expect(requisicao.request.headers.get('Content-Type')).toBe('application/x-www-form-urlencoded');
    expect(parametros(requisicao)).toEqual({ grant_type: GRANT_REFRESH_TOKEN, refresh_token: 'r1', client_id: 'saigt-ui' });
    requisicao.flush(tokens('a2', 'r2'));
    responderPermissoes();

    expect(sessao).toEqual({ accessToken: 'a2', refreshToken: 'r2' });
    expect(service.accessToken()).toBe('a2');
    expect(service.refreshToken()).toBe('r2');
    expect(sessionStorage).toHaveLength(0);
  });

  it('refresh: pedidos simultâneos partilham uma só renovação (o refresh token é de uso único)', () => {
    entrarDgt('a1', 'r1');
    const recebidos: string[] = [];

    service.refresh().subscribe((sessao) => recebidos.push(sessao.accessToken));
    service.refresh().subscribe((sessao) => recebidos.push(sessao.accessToken));
    httpTesting.expectOne(URL_TOKEN).flush(tokens('a2', 'r2'));
    responderPermissoes();

    expect(recebidos).toEqual(['a2', 'a2']);
    httpTesting.verify();

    service.refresh().subscribe();
    expect(parametros(httpTesting.expectOne(URL_TOKEN))['refresh_token']).toBe('r2');
  });

  it('refresh recusado com invalid_grant termina a sessão local e propaga o erro', () => {
    entrarDgt('a1', 'r1');

    const erro = capturarErro(service.refresh());
    erroDoServidor(httpTesting.expectOne(URL_TOKEN), 'invalid_grant', 'Refresh token inválido, expirado ou já usado.');

    expect(erro()).toBeTruthy();
    expect(service.estaAutenticado()).toBe(false);
    expect(service.refreshToken()).toBeNull();
    expect(service.permissoes()).toBeNull();
  });

  describe('renovarSeExpirarEm', () => {
    const tokenQueExpiraEm = (segundos: number): string =>
      criarToken({ sub: 'u1', exp: Math.floor(Date.now() / 1000) + segundos });

    it('renova antes do pedido quando o token de acesso expira dentro da margem', () => {
      entrarDgt(tokenQueExpiraEm(30), 'r1');
      let renovou = false;

      service.renovarSeExpirarEm(60).subscribe(() => (renovou = true));
      const requisicao = httpTesting.expectOne(URL_TOKEN);
      expect(parametros(requisicao)['grant_type']).toBe(GRANT_REFRESH_TOKEN);
      requisicao.flush(tokens('a2', 'r2'));
      responderPermissoes();

      expect(renovou).toBe(true);
      expect(service.accessToken()).toBe('a2');
    });

    it('renova quando o token de acesso já expirou', () => {
      entrarDgt(tokenQueExpiraEm(-120), 'r1');

      service.renovarSeExpirarEm(60).subscribe();

      httpTesting.expectOne(URL_TOKEN).flush(tokens('a2', 'r2'));
      responderPermissoes();
      expect(service.refreshToken()).toBe('r2');
    });

    it('não renova quando o token de acesso ainda tem validade para lá da margem', () => {
      entrarDgt(tokenQueExpiraEm(600), 'r1');
      let emitiu = false;

      service.renovarSeExpirarEm(60).subscribe(() => (emitiu = true));

      expect(emitiu).toBe(true);
      httpTesting.expectNone(URL_TOKEN);
    });

    it('sem sessão não renova nem falha', () => {
      let emitiu = false;

      service.renovarSeExpirarEm(60).subscribe(() => (emitiu = true));

      expect(emitiu).toBe(true);
      httpTesting.expectNone(URL_TOKEN);
    });
  });

  it('logout: limpa o estado antes da resposta e revoga o refresh token pelo /oauth2/revoke', () => {
    entrarDgt('a1', 'r1');
    let concluiu = false;

    service.logout().subscribe({ complete: () => (concluiu = true) });

    expect(service.estaAutenticado()).toBe(false);
    expect(service.accessToken()).toBeNull();
    expect(service.refreshToken()).toBeNull();
    expect(service.permissoes()).toBeNull();
    const requisicao = httpTesting.expectOne(URL_REVOGACAO);
    expect(requisicao.request.method).toBe('POST');
    expect(requisicao.request.headers.get('Content-Type')).toBe('application/x-www-form-urlencoded');
    expect(parametros(requisicao)).toEqual({ token: 'r1', token_type_hint: 'refresh_token', client_id: 'saigt-ui' });
    requisicao.flush('', { status: 200, statusText: 'OK' });
    expect(concluiu).toBe(true);
  });

  it('logout com o revoke a falhar: o estado local fica limpo e o logout não falha', () => {
    entrarDgt('a1', 'r1');
    let emitiu = false;
    let falhou = false;

    service.logout().subscribe({ next: () => (emitiu = true), error: () => (falhou = true) });
    httpTesting.expectOne(URL_REVOGACAO).flush('<html>502</html>', { status: 502, statusText: 'Bad Gateway' });

    expect(emitiu).toBe(true);
    expect(falhou).toBe(false);
    expect(service.estaAutenticado()).toBe(false);
    expect(service.refreshToken()).toBeNull();
  });

  it('logout sem sessão não chama o servidor e emite na mesma', () => {
    let emitiu = false;

    service.logout().subscribe(() => (emitiu = true));

    expect(emitiu).toBe(true);
    httpTesting.verify();
  });

  it('limparSessao repõe todo o estado, incluindo permissões e escolha de entidade em curso', () => {
    iniciarEscolha();

    service.limparSessao();

    expect(service.tokenPreAutenticacao()).toBeNull();
    expect(service.entidadesElegiveis()).toBeNull();
    expect(service.emEscolhaDeEntidade()).toBe(false);
    expect(service.permissoes()).toBeNull();
    expect(service.estaAutenticado()).toBe(false);
  });

  it('decodifica nome, email e entidade a partir do accessToken de um utilizador INCM', () => {
    const token = criarToken({
      sub: 'u1',
      name: 'Maria Santos',
      email: 'maria.santos@cm-lisboa.pt',
      entidade: { nipc: '501234567', nome: 'Câmara Municipal de Lisboa', telefone: '213456789' },
    });
    entrarDgt(token);

    expect(service.nome()).toBe('Maria Santos');
    expect(service.email()).toBe('maria.santos@cm-lisboa.pt');
    expect(service.entidade()).toEqual({ nipc: '501234567', nome: 'Câmara Municipal de Lisboa', telefone: '213456789' });
  });

  it('devolve cargo nulo quando o token não tem essa claim', () => {
    entrarDgt(criarToken({ sub: 'u1', name: 'Maria Santos', email: 'maria.santos@cm-lisboa.pt' }));

    expect(service.cargo()).toBeNull();
  });

  it('mostra o cargo quando a claim existir no token', () => {
    entrarDgt(criarToken({ sub: 'u1', name: 'Maria Santos', cargo: 'Técnica Superior de Urbanismo' }));

    expect(service.cargo()).toBe('Técnica Superior de Urbanismo');
  });

  it('não expõe entidade nem cargo para um utilizador interno (token só com name/email)', () => {
    entrarDgt(criarToken({ sub: 'u2', name: 'Ana Teixeira', email: 'ana.teixeira@dgterritorio.gov.pt' }));

    expect(service.nome()).toBe('Ana Teixeira');
    expect(service.entidade()).toBeNull();
    expect(service.cargo()).toBeNull();
  });

  it('decodifica corretamente nomes e entidades com acentos', () => {
    entrarDgt(
      criarToken({
        sub: 'u1',
        name: 'Maria José Santos Ferreira',
        cargo: 'Técnico Superior',
        entidade: { nipc: '501234567', nome: 'Câmara Municipal de Setúbal' },
      }),
    );

    expect(service.nome()).toBe('Maria José Santos Ferreira');
    expect(service.cargo()).toBe('Técnico Superior');
    expect(service.entidade()?.nome).toBe('Câmara Municipal de Setúbal');
  });

  it('nunca lança e devolve nulo para um accessToken que não é um JWT decodificável', () => {
    entrarDgt('token-opaco');

    expect(service.nome()).toBeNull();
    expect(service.email()).toBeNull();
    expect(service.entidade()).toBeNull();
  });
});

describe('AuthService com PERSISTIR_SESSAO (só dev)', () => {
  let service: AuthService;
  let httpTesting: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([errorEnvelopeInterceptor])),
        provideHttpClientTesting(),
        { provide: PERSISTIR_SESSAO, useValue: true },
      ],
    });
    service = TestBed.inject(AuthService);
    httpTesting = TestBed.inject(HttpTestingController);
  });

  afterEach(() => sessionStorage.clear());

  const permissoes: PermissoesResponse = {
    origem: 'INCM',
    ator: 'ENTIDADE',
    perfilInterno: null,
    procedimentosPermitidos: [1],
  };

  it('guarda só o refresh token em sessionStorage e remove-o no logout', () => {
    service.login(DGT).subscribe();
    httpTesting.expectOne(URL_TOKEN).flush(tokens('a1', 'r1'));
    httpTesting.expectOne('/api/v1/sessao/me/permissoes').flush(permissoes);

    expect(sessionStorage).toHaveLength(1);
    expect(sessionStorage.getItem('saigt.refreshToken')).toBe('r1');
    expect(localStorage).toHaveLength(0);

    service.logout().subscribe();
    httpTesting.expectOne(URL_REVOGACAO).flush('', { status: 200, statusText: 'OK' });

    expect(sessionStorage).toHaveLength(0);
  });

  it('restaurarSessao renova com o refresh token guardado e repõe tokens e permissões', () => {
    sessionStorage.setItem('saigt.refreshToken', 'r-guardado');

    service.restaurarSessao().subscribe();
    const requisicao = httpTesting.expectOne(URL_TOKEN);
    expect(parametros(requisicao)).toEqual({ grant_type: GRANT_REFRESH_TOKEN, refresh_token: 'r-guardado', client_id: 'saigt-ui' });
    requisicao.flush(tokens('a2', 'r2'));
    httpTesting.expectOne('/api/v1/sessao/me/permissoes').flush(permissoes);

    expect(service.estaAutenticado()).toBe(true);
    expect(service.permissoes()).toEqual(permissoes);
    expect(sessionStorage.getItem('saigt.refreshToken')).toBe('r2');
  });

  it('restaurarSessao não falha e limpa tudo quando o refresh é recusado', () => {
    sessionStorage.setItem('saigt.refreshToken', 'r-expirado');
    let concluiu = false;

    service.restaurarSessao().subscribe({ complete: () => (concluiu = true) });
    erroDoServidor(httpTesting.expectOne(URL_TOKEN), 'invalid_grant', 'Refresh token inválido, expirado ou já usado.');

    expect(concluiu).toBe(true);
    expect(service.estaAutenticado()).toBe(false);
    expect(service.refreshToken()).toBeNull();
    expect(sessionStorage).toHaveLength(0);
  });

  it('restaurarSessao sem nada guardado não chama o backend', () => {
    service.restaurarSessao().subscribe();

    expect(service.estaAutenticado()).toBe(false);
    httpTesting.verify();
  });
});
