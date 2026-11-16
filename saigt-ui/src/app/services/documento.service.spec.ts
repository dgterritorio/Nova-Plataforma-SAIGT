import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, TestRequest, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { Observable, Subject, of, throwError } from 'rxjs';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { authInterceptor } from '../core/auth/auth.interceptor';
import { AuthService } from '../core/auth/auth.service';
import { errorEnvelopeInterceptor } from '../core/interceptors/error-envelope.interceptor';
import { ApiError } from '../core/models/error-response.model';
import { CODIGO_CARREGAMENTO_SEM_LIGACAO, DocumentoService } from './documento.service';

describe('DocumentoService', () => {
  let service: DocumentoService;
  let httpTesting: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    service = TestBed.inject(DocumentoService);
    httpTesting = TestBed.inject(HttpTestingController);
  });

  it('pede a estrutura documental do processo', () => {
    service.estrutura('p1').subscribe();

    const requisicao = httpTesting.expectOne('/api/v1/processos/p1/estrutura-documental');
    expect(requisicao.request.method).toBe('GET');
    requisicao.flush({ separadores: [], outros: [], pendencias: [] });
  });

  it('pede o histórico documental do processo', () => {
    let resposta: unknown;
    service.historico('p1').subscribe((valor) => (resposta = valor));

    const requisicao = httpTesting.expectOne('/api/v1/processos/p1/documentos/historico');
    expect(requisicao.request.method).toBe('GET');
    requisicao.flush({ processos: [] });
    expect(resposta).toEqual({ processos: [] });
  });

  it('lista uma página dos ficheiros do processo, por omissão a 0 com 20', () => {
    service.listar('p1').subscribe();

    const requisicao = httpTesting.expectOne((r) => r.url === '/api/v1/processos/p1/documentos');
    expect(requisicao.request.method).toBe('GET');
    expect(requisicao.request.params.get('page')).toBe('0');
    expect(requisicao.request.params.get('size')).toBe('20');
    expect(requisicao.request.params.has('categoria')).toBe(false);
    requisicao.flush({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 });
  });

  it('envia a página, o tamanho e só os filtros definidos', () => {
    service.listar('p1', { categoria: 'PUBLICACAO', documentoConfigId: 7, estadoValidacao: undefined }, 2, 100).subscribe();

    const requisicao = httpTesting.expectOne((r) => r.url === '/api/v1/processos/p1/documentos');
    expect(requisicao.request.params.get('page')).toBe('2');
    expect(requisicao.request.params.get('size')).toBe('100');
    expect(requisicao.request.params.get('categoria')).toBe('PUBLICACAO');
    expect(requisicao.request.params.get('documentoConfigId')).toBe('7');
    expect(requisicao.request.params.has('estadoValidacao')).toBe(false);
    requisicao.flush({ content: [], page: 2, size: 100, totalElements: 0, totalPages: 0 });
  });

  it('carrega um ficheiro com ficheiro, categoria e documentoConfigId em multipart', () => {
    const ficheiro = new File(['x'], 'ato.docx');
    service.carregar('p1', ficheiro, 'PUBLICACAO', 7).subscribe();

    const requisicao = httpTesting.expectOne('/api/v1/processos/p1/documentos');
    expect(requisicao.request.method).toBe('POST');
    const corpo = requisicao.request.body as FormData;
    expect(corpo.get('ficheiro')).toBe(ficheiro);
    expect(corpo.get('categoria')).toBe('PUBLICACAO');
    expect(corpo.get('documentoConfigId')).toBe('7');
    requisicao.flush({});
  });

  it('envia as partes de texto antes do ficheiro, que o servidor lê em streaming', () => {
    service.carregar('p1', new File(['x'], 'ato.docx'), 'PUBLICACAO', 7).subscribe();

    const requisicao = httpTesting.expectOne('/api/v1/processos/p1/documentos');
    const corpo = requisicao.request.body as FormData;
    expect([...corpo.keys()]).toEqual(['categoria', 'documentoConfigId', 'ficheiro']);
    requisicao.flush({});
  });

  it('envia cada tag numa parte própria, depois do item e antes do ficheiro', () => {
    service.carregar('p1', new File(['x'], 'relatorio.pdf'), 'DEPOSITO', 7, [11, 12]).subscribe();

    const requisicao = httpTesting.expectOne('/api/v1/processos/p1/documentos');
    const corpo = requisicao.request.body as FormData;
    expect([...corpo.keys()]).toEqual(['categoria', 'documentoConfigId', 'tags', 'tags', 'ficheiro']);
    expect(corpo.getAll('tags')).toEqual(['11', '12']);
    requisicao.flush({});
  });

  it('carrega para o balde "outros" sem documentoConfigId', () => {
    service.carregar('p1', new File(['x'], 'extra.pdf'), 'DEPOSITO').subscribe();

    const requisicao = httpTesting.expectOne('/api/v1/processos/p1/documentos');
    const corpo = requisicao.request.body as FormData;
    expect(corpo.get('categoria')).toBe('DEPOSITO');
    expect(corpo.has('documentoConfigId')).toBe(false);
    requisicao.flush({});
  });

  it('substitui o ficheiro de um documento só com o ficheiro no corpo', () => {
    const ficheiro = new File(['y'], 'novo.docx');
    service.substituir('p1', 'd1', ficheiro).subscribe();

    const requisicao = httpTesting.expectOne('/api/v1/processos/p1/documentos/d1');
    expect(requisicao.request.method).toBe('PUT');
    const corpo = requisicao.request.body as FormData;
    expect(corpo.get('ficheiro')).toBe(ficheiro);
    expect(corpo.has('categoria')).toBe(false);
    requisicao.flush({});
  });

  it('remove um documento', () => {
    service.remover('p1', 'd1').subscribe();

    const requisicao = httpTesting.expectOne('/api/v1/processos/p1/documentos/d1');
    expect(requisicao.request.method).toBe('DELETE');
    requisicao.flush(null);
  });

  it('desbloqueia um ficheiro com PATCH e a justificação no corpo', () => {
    let resposta: unknown;
    service.desbloquear('p1', 'd1', 'Legenda desatualizada.').subscribe((documento) => (resposta = documento));

    const requisicao = httpTesting.expectOne('/api/v1/processos/p1/documentos/d1/desbloquear');
    expect(requisicao.request.method).toBe('PATCH');
    expect(requisicao.request.body).toEqual({ justificacao: 'Legenda desatualizada.' });
    requisicao.flush({ id: 'd1', bloqueadoEdicao: false, justificacaoDesbloqueio: 'Legenda desatualizada.' });
    expect(resposta).toEqual({ id: 'd1', bloqueadoEdicao: false, justificacaoDesbloqueio: 'Legenda desatualizada.' });
  });

  it('reclassifica um ficheiro com PATCH …/classificacao e o item, a caixa e as tags no corpo', () => {
    let resposta: unknown;
    service
      .reclassificar('p1', 'd1', { categoria: 'DEPOSITO', documentoConfigId: null, tags: [4, 5] })
      .subscribe((documento) => (resposta = documento));

    const requisicao = httpTesting.expectOne('/api/v1/processos/p1/documentos/d1/classificacao');
    expect(requisicao.request.method).toBe('PATCH');
    expect(requisicao.request.body).toEqual({ categoria: 'DEPOSITO', documentoConfigId: null, tags: [4, 5] });
    requisicao.flush({ id: 'd1', categoria: 'DEPOSITO', documentoConfigId: null, tags: [4, 5] });
    expect(resposta).toEqual({ id: 'd1', categoria: 'DEPOSITO', documentoConfigId: null, tags: [4, 5] });
  });

  it('pede a checklist de validação do processo com GET …/documentos/verificacoes', () => {
    let resposta: unknown;
    service.verificacoes('p1').subscribe((linhas) => (resposta = linhas));

    const requisicao = httpTesting.expectOne('/api/v1/processos/p1/documentos/verificacoes');
    expect(requisicao.request.method).toBe('GET');
    const linhas = [
      { verificacao: 'CORRESPONDENCIA_ATO_TEXTO', documentoId: null, nomeFicheiro: null, resultado: 'MANUAL', detalhe: 'Confirme.' },
    ];
    requisicao.flush(linhas);
    expect(resposta).toEqual(linhas);
  });
});

describe('DocumentoService — carregamento com a sessão a expirar', () => {
  const URL_CARREGAR = '/api/v1/processos/p1/documentos';
  const SEM_LIGACAO = (): ApiError =>
    new ApiError('ERRO_DESCONHECIDO', 'Não foi possível comunicar com o servidor. Verifique a ligação e tente novamente.', 'unknown', null, 0);

  let service: DocumentoService;
  let httpTesting: HttpTestingController;
  let tokenAtual: string;
  let auth: {
    accessToken: () => string;
    renovarSeExpirarEm: ReturnType<typeof vi.fn>;
    refresh: ReturnType<typeof vi.fn>;
    limparSessao: ReturnType<typeof vi.fn>;
  };
  let router: { navigate: ReturnType<typeof vi.fn> };

  function renovacaoComSucesso(): Observable<unknown> {
    tokenAtual = 'access-novo';
    return of({ accessToken: 'access-novo', refreshToken: 'refresh-novo' });
  }

  function carregar(): { resultado: () => unknown; erro: () => unknown } {
    let resultado: unknown = null;
    let erro: unknown = null;
    service.carregar('p1', new File(['zip'], 'Planta_de_Ordenamento.zip'), 'PUBLICACAO', 7).subscribe({
      next: (valor) => (resultado = valor),
      error: (e: unknown) => (erro = e),
    });
    return { resultado: () => resultado, erro: () => erro };
  }

  function semResposta(requisicao: TestRequest): void {
    requisicao.error(new ProgressEvent('error'), { status: 0, statusText: 'Unknown Error' });
  }

  beforeEach(() => {
    tokenAtual = 'access-atual';
    auth = {
      accessToken: () => tokenAtual,
      renovarSeExpirarEm: vi.fn(() => of(null)),
      refresh: vi.fn(renovacaoComSucesso),
      limparSessao: vi.fn(),
    };
    router = { navigate: vi.fn() };
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([errorEnvelopeInterceptor, authInterceptor])),
        provideHttpClientTesting(),
        { provide: AuthService, useValue: auth },
        { provide: Router, useValue: router },
      ],
    });
    service = TestBed.inject(DocumentoService);
    httpTesting = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpTesting.verify());

  it('com o token a menos de 60 s de expirar, renova antes de enviar e envia com o token novo', () => {
    const renovacao = new Subject<unknown>();
    auth.renovarSeExpirarEm.mockReturnValue(renovacao);

    const { resultado, erro } = carregar();

    expect(auth.renovarSeExpirarEm).toHaveBeenCalledWith(60);
    httpTesting.expectNone(URL_CARREGAR);
    tokenAtual = 'access-novo';
    renovacao.next(null);
    renovacao.complete();

    const requisicao = httpTesting.expectOne(URL_CARREGAR);
    expect(requisicao.request.headers.get('Authorization')).toBe('Bearer access-novo');
    requisicao.flush({ id: 'd1' }, { status: 201, statusText: 'Created' });
    expect(resultado()).toEqual({ id: 'd1' });
    expect(erro()).toBeNull();
  });

  it('num 401, renova e repete o envio uma vez com o mesmo ficheiro', () => {
    const { resultado } = carregar();

    const primeira = httpTesting.expectOne(URL_CARREGAR);
    const ficheiro = (primeira.request.body as FormData).get('ficheiro');
    primeira.flush(null, { status: 401, statusText: 'Unauthorized' });

    const segunda = httpTesting.expectOne(URL_CARREGAR);
    expect(segunda.request.headers.get('Authorization')).toBe('Bearer access-novo');
    expect((segunda.request.body as FormData).get('ficheiro')).toBe(ficheiro);
    segunda.flush({ id: 'd1' }, { status: 201, statusText: 'Created' });

    expect(resultado()).toEqual({ id: 'd1' });
    expect(auth.refresh).toHaveBeenCalledTimes(1);
  });

  it('não repete mais do que uma vez: um segundo 401 chega a quem chamou', () => {
    const { erro } = carregar();

    httpTesting.expectOne(URL_CARREGAR).flush(null, { status: 401, statusText: 'Unauthorized' });
    httpTesting.expectOne(URL_CARREGAR).flush(null, { status: 401, statusText: 'Unauthorized' });

    httpTesting.expectNone(URL_CARREGAR);
    expect(auth.refresh).toHaveBeenCalledTimes(1);
    expect((erro() as ApiError).estado).toBe(401);
    expect(auth.limparSessao).not.toHaveBeenCalled();
  });

  it('um envio sem resposta (o 401 que chega a meio do corpo) renova e repete uma vez', () => {
    const { resultado } = carregar();

    semResposta(httpTesting.expectOne(URL_CARREGAR));
    const segunda = httpTesting.expectOne(URL_CARREGAR);
    expect(segunda.request.headers.get('Authorization')).toBe('Bearer access-novo');
    segunda.flush({ id: 'd1' }, { status: 201, statusText: 'Created' });

    expect(resultado()).toEqual({ id: 'd1' });
    expect(auth.refresh).toHaveBeenCalledTimes(1);
  });

  it('a substituição também renova e repete uma vez com o mesmo ficheiro', () => {
    const ficheiro = new File(['y'], 'novo.pdf');
    let resultado: unknown = null;
    service.substituir('p1', 'd1', ficheiro).subscribe((valor) => (resultado = valor));

    expect(auth.renovarSeExpirarEm).toHaveBeenCalledWith(60);
    semResposta(httpTesting.expectOne('/api/v1/processos/p1/documentos/d1'));
    const segunda = httpTesting.expectOne('/api/v1/processos/p1/documentos/d1');
    expect(segunda.request.method).toBe('PUT');
    expect((segunda.request.body as FormData).get('ficheiro')).toBe(ficheiro);
    segunda.flush({ id: 'd1' });

    expect(resultado).toEqual({ id: 'd1' });
  });

  it('com a renovação recusada depois de um 401, termina a sessão, vai para o login e não repete', () => {
    const recusa = new ApiError('invalid_grant', 'Refresh token inválido, expirado ou já usado.', 'unknown', null, 400);
    auth.refresh.mockReturnValue(throwError(() => recusa));

    const { erro } = carregar();
    httpTesting.expectOne(URL_CARREGAR).flush(null, { status: 401, statusText: 'Unauthorized' });

    httpTesting.expectNone(URL_CARREGAR);
    expect(erro()).toBe(recusa);
    expect(auth.limparSessao).toHaveBeenCalledTimes(1);
    expect(router.navigate).toHaveBeenCalledWith(['/login']);
  });

  it('com a renovação antecipada recusada, termina a sessão e vai para o login sem enviar o ficheiro', () => {
    auth.renovarSeExpirarEm.mockReturnValue(throwError(() => new ApiError('invalid_grant', '', 'unknown', null, 400)));

    const { erro } = carregar();

    httpTesting.expectNone(URL_CARREGAR);
    expect(erro()).toBeInstanceOf(ApiError);
    expect(auth.limparSessao).toHaveBeenCalledTimes(1);
    expect(router.navigate).toHaveBeenCalledWith(['/login']);
  });

  it('com o servidor inacessível, dá a mensagem do carregamento e mantém a sessão', () => {
    auth.refresh.mockReturnValue(throwError(SEM_LIGACAO));

    const { resultado, erro } = carregar();
    semResposta(httpTesting.expectOne(URL_CARREGAR));

    httpTesting.expectNone(URL_CARREGAR);
    expect(resultado()).toBeNull();
    expect(erro()).toBeInstanceOf(ApiError);
    expect((erro() as ApiError).codigo).toBe(CODIGO_CARREGAMENTO_SEM_LIGACAO);
    expect((erro() as ApiError).mensagem).toBe('Não foi possível carregar o ficheiro. Tente novamente.');
    expect(auth.limparSessao).not.toHaveBeenCalled();
    expect(router.navigate).not.toHaveBeenCalled();
  });

  it('com a API em baixo atrás do proxy (502), não repete e dá a mensagem do carregamento', () => {
    const { erro } = carregar();

    httpTesting.expectOne(URL_CARREGAR).flush('<html>502 Bad Gateway</html>', { status: 502, statusText: 'Bad Gateway' });

    httpTesting.expectNone(URL_CARREGAR);
    expect(auth.refresh).not.toHaveBeenCalled();
    expect((erro() as ApiError).mensagem).toBe('Não foi possível carregar o ficheiro. Tente novamente.');
  });

  it('mantém as recusas da API (400 com envelope) sem renovar nem repetir', () => {
    const { erro } = carregar();

    httpTesting
      .expectOne(URL_CARREGAR)
      .flush(
        { codigo: 'DOC_ZIP_INVALIDO', mensagem: 'ZIP inválido.', timestamp: '', traceId: 't1', campos: null },
        { status: 400, statusText: 'Bad Request' },
      );

    expect(auth.refresh).not.toHaveBeenCalled();
    expect((erro() as ApiError).codigo).toBe('DOC_ZIP_INVALIDO');
  });
});
