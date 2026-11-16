import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { beforeEach, describe, expect, it } from 'vitest';
import { ApiError, ErrorResponse } from '../models/error-response.model';
import { PageResponse } from '../models/page-response.model';
import { errorEnvelopeInterceptor } from './error-envelope.interceptor';

describe('errorEnvelopeInterceptor', () => {
  let httpClient: HttpClient;
  let httpTesting: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([errorEnvelopeInterceptor])),
        provideHttpClientTesting(),
      ],
    });
    httpClient = TestBed.inject(HttpClient);
    httpTesting = TestBed.inject(HttpTestingController);
  });

  it('propaga ApiError com campos tipados numa resposta 400 com campos preenchido', async () => {
    const envelope: ErrorResponse = {
      codigo: 'VALIDACAO',
      mensagem: 'Dados inválidos.',
      timestamp: '2026-08-11T10:00:00Z',
      traceId: 'trace-1',
      campos: [{ path: 'nome', erro: 'obrigatório' }],
    };

    const promise = new Promise<ApiError>((resolve, reject) => {
      httpClient.get('/api/v1/exemplo').subscribe({
        next: () => reject(new Error('esperava erro')),
        error: (erro) => resolve(erro),
      });
    });

    httpTesting.expectOne('/api/v1/exemplo').flush(envelope, { status: 400, statusText: 'Bad Request' });

    const apiError = await promise;
    expect(apiError).toBeInstanceOf(ApiError);
    expect(apiError.codigo).toBe('VALIDACAO');
    expect(apiError.campos).toEqual([{ path: 'nome', erro: 'obrigatório' }]);
  });

  it('lê o envelope de erro de um pedido de ficheiro, que chega como Blob', async () => {
    const envelope: ErrorResponse = {
      codigo: 'VALIDACAO',
      mensagem: 'Ainda não se pode gerar o PDF do cartograma.',
      timestamp: '2026-11-09T10:00:00Z',
      traceId: 'trace-blob',
      campos: [{ path: 'plantas.cartograma', erro: 'Carregue o cartograma das folhas.' }],
    };

    const promise = new Promise<ApiError>((resolve, reject) => {
      httpClient.get('/api/v1/exemplo.pdf', { responseType: 'blob' }).subscribe({
        next: () => reject(new Error('esperava erro')),
        error: (erro) => resolve(erro),
      });
    });

    httpTesting
      .expectOne('/api/v1/exemplo.pdf')
      .flush(new Blob([JSON.stringify(envelope)], { type: 'application/json' }), { status: 400, statusText: 'Bad Request' });

    const apiError = await promise;
    expect(apiError).toBeInstanceOf(ApiError);
    expect(apiError.codigo).toBe('VALIDACAO');
    expect(apiError.campos).toEqual([{ path: 'plantas.cartograma', erro: 'Carregue o cartograma das folhas.' }]);
  });

  it('propaga mensagem e traceId sem lançar excepção quando campos é null (resposta 500)', async () => {
    const envelope: ErrorResponse = {
      codigo: 'ERRO_INTERNO',
      mensagem: 'Ocorreu um erro interno.',
      timestamp: '2026-08-11T10:00:00Z',
      traceId: 'trace-2',
      campos: null,
    };

    const promise = new Promise<ApiError>((resolve, reject) => {
      httpClient.get('/api/v1/exemplo').subscribe({
        next: () => reject(new Error('esperava erro')),
        error: (erro) => resolve(erro),
      });
    });

    httpTesting.expectOne('/api/v1/exemplo').flush(envelope, { status: 500, statusText: 'Internal Server Error' });

    const apiError = await promise;
    expect(apiError.mensagem).toBe('Ocorreu um erro interno.');
    expect(apiError.traceId).toBe('trace-2');
    expect(apiError.campos).toBeNull();
  });

  it('não interfere numa resposta paginada de sucesso — content/page/size/totalElements/totalPages acessíveis sem cast', async () => {
    const pagina: PageResponse<{ id: number }> = {
      content: [{ id: 1 }, { id: 2 }],
      page: 0,
      size: 20,
      totalElements: 2,
      totalPages: 1,
    };

    const promise = new Promise<PageResponse<{ id: number }>>((resolve, reject) => {
      httpClient.get<PageResponse<{ id: number }>>('/api/v1/exemplo').subscribe({
        next: (resposta) => resolve(resposta),
        error: (erro) => reject(erro),
      });
    });

    httpTesting.expectOne('/api/v1/exemplo').flush(pagina);

    const resposta = await promise;
    expect(resposta.content).toHaveLength(2);
    expect(resposta.page).toBe(0);
    expect(resposta.totalElements).toBe(2);
    expect(resposta.totalPages).toBe(1);
  });

  it('não interfere numa resposta de sucesso não paginada (entidade direta, sem envelope)', async () => {
    const promise = new Promise<{ id: number; nome: string }>((resolve, reject) => {
      httpClient.get<{ id: number; nome: string }>('/api/v1/exemplo').subscribe({
        next: (resposta) => resolve(resposta),
        error: (erro) => reject(erro),
      });
    });

    httpTesting.expectOne('/api/v1/exemplo').flush({ id: 1, nome: 'Teste' });

    const resposta = await promise;
    expect(resposta).toEqual({ id: 1, nome: 'Teste' });
  });

  it('passa a ApiError um erro do servidor de autorização (RFC 6749 §5.2), com o error e a error_description', async () => {
    const promise = new Promise<ApiError>((resolve, reject) => {
      httpClient.post('/oauth2/token', 'grant_type=x').subscribe({
        next: () => reject(new Error('esperava erro')),
        error: (erro) => resolve(erro),
      });
    });

    httpTesting
      .expectOne('/oauth2/token')
      .flush({ error: 'invalid_grant', error_description: 'Credenciais inválidas.' }, { status: 400, statusText: 'Bad Request' });

    const apiError = await promise;
    expect(apiError).toBeInstanceOf(ApiError);
    expect(apiError.codigo).toBe('invalid_grant');
    expect(apiError.mensagem).toBe('Credenciais inválidas.');
  });

  it('dá ERRO_DESCONHECIDO a uma resposta sem corpo reconhecível, como a de um gateway sem a API', async () => {
    const promise = new Promise<ApiError>((resolve, reject) => {
      httpClient.post('/oauth2/token', 'grant_type=x').subscribe({
        next: () => reject(new Error('esperava erro')),
        error: (erro) => resolve(erro),
      });
    });

    httpTesting.expectOne('/oauth2/token').flush('<html>502 Bad Gateway</html>', { status: 502, statusText: 'Bad Gateway' });

    expect((await promise).codigo).toBe('ERRO_DESCONHECIDO');
  });

  function erroDe(pedido: () => ReturnType<HttpClient['get']>): Promise<ApiError> {
    return new Promise<ApiError>((resolve, reject) => {
      pedido().subscribe({ next: () => reject(new Error('esperava erro')), error: (erro) => resolve(erro) });
    });
  }

  it('dá a mensagem da ligação, e não o texto do HttpErrorResponse, a um pedido que termina com estado 0', async () => {
    const promise = erroDe(() => httpClient.get('/api/v1/exemplo'));

    httpTesting.expectOne('/api/v1/exemplo').error(new ProgressEvent('error'), { status: 0, statusText: 'Unknown Error' });

    const apiError = await promise;
    expect(apiError).toBeInstanceOf(ApiError);
    expect(apiError.codigo).toBe('ERRO_DESCONHECIDO');
    expect(apiError.estado).toBe(0);
    expect(apiError.mensagem).toBe('Não foi possível comunicar com o servidor. Verifique a ligação e tente novamente.');
    expect(apiError.mensagem).not.toContain('Http failure');
  });

  it('dá a mensagem da ligação ao 502 do proxy sem a API atrás', async () => {
    const promise = erroDe(() => httpClient.get('/api/v1/exemplo'));

    httpTesting.expectOne('/api/v1/exemplo').flush('<html>502 Bad Gateway</html>', { status: 502, statusText: 'Bad Gateway' });

    const apiError = await promise;
    expect(apiError.estado).toBe(502);
    expect(apiError.mensagem).toBe('Não foi possível comunicar com o servidor. Verifique a ligação e tente novamente.');
  });

  it('nunca passa o texto «Http failure response…» numa resposta sem envelope de outro estado', async () => {
    const promise = erroDe(() => httpClient.get('/api/v1/exemplo'));

    httpTesting.expectOne('/api/v1/exemplo').flush(null, { status: 401, statusText: 'Unauthorized' });

    const apiError = await promise;
    expect(apiError.codigo).toBe('ERRO_DESCONHECIDO');
    expect(apiError.estado).toBe(401);
    expect(apiError.mensagem).toBe('Não foi possível concluir a operação. Tente novamente.');
  });

  it('guarda o estado HTTP também nos erros com o envelope da API', async () => {
    const envelope: ErrorResponse = { codigo: 'NAO_AUTENTICADO', mensagem: 'x', timestamp: '', traceId: 't', campos: null };
    const promise = erroDe(() => httpClient.get('/api/v1/exemplo'));

    httpTesting.expectOne('/api/v1/exemplo').flush(envelope, { status: 401, statusText: 'Unauthorized' });

    const apiError = await promise;
    expect(apiError.codigo).toBe('NAO_AUTENTICADO');
    expect(apiError.estado).toBe(401);
  });

  it('dá DOC_TAMANHO_EXCEDIDO, com a mensagem do BL-DOC-017, ao 413 do nginx, que vem sem envelope', async () => {
    const promise = new Promise<ApiError>((resolve, reject) => {
      httpClient.post('/api/v1/processos/p1/documentos', new FormData()).subscribe({
        next: () => reject(new Error('esperava erro')),
        error: (erro) => resolve(erro),
      });
    });

    httpTesting
      .expectOne('/api/v1/processos/p1/documentos')
      .flush('<html><head><title>413 Request Entity Too Large</title></head></html>', { status: 413, statusText: 'Request Entity Too Large' });

    const apiError = await promise;
    expect(apiError).toBeInstanceOf(ApiError);
    expect(apiError.codigo).toBe('DOC_TAMANHO_EXCEDIDO');
    expect(apiError.mensagem).toBe('O ficheiro excede o tamanho máximo permitido.');
    expect(apiError.campos).toBeNull();
  });

  it('mantém o envelope de um 413 que venha da API', async () => {
    const envelope: ErrorResponse = {
      codigo: 'PEDIDO_DEMASIADO_GRANDE',
      mensagem: 'Pedido demasiado grande.',
      timestamp: '2026-11-12T10:00:00Z',
      traceId: 'trace-413',
      campos: null,
    };
    const promise = new Promise<ApiError>((resolve, reject) => {
      httpClient.post('/api/v1/exemplo', {}).subscribe({
        next: () => reject(new Error('esperava erro')),
        error: (erro) => resolve(erro),
      });
    });

    httpTesting.expectOne('/api/v1/exemplo').flush(envelope, { status: 413, statusText: 'Payload Too Large' });

    const apiError = await promise;
    expect(apiError.codigo).toBe('PEDIDO_DEMASIADO_GRANDE');
    expect(apiError.traceId).toBe('trace-413');
  });
});
