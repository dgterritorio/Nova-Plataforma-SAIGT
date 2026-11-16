import { HttpClient, HttpContext, HttpErrorResponse, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { of, throwError } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { RENOVACAO_PELO_CHAMADOR, authInterceptor } from './auth.interceptor';
import { AuthService } from './auth.service';
import { LoginResponse } from './auth.models';
import { ApiError } from '../models/error-response.model';

describe('authInterceptor', () => {
  let httpClient: HttpClient;
  let httpTesting: HttpTestingController;
  let tokenAtual: string | null;
  let authServiceMock: {
    accessToken: () => string | null;
    refresh: ReturnType<typeof vi.fn>;
    limparSessao: ReturnType<typeof vi.fn>;
  };
  let routerMock: { navigate: ReturnType<typeof vi.fn> };

  beforeEach(() => {
    tokenAtual = 'access-atual';
    authServiceMock = {
      accessToken: () => tokenAtual,
      refresh: vi.fn(),
      limparSessao: vi.fn(),
    };
    routerMock = { navigate: vi.fn() };

    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([authInterceptor])),
        provideHttpClientTesting(),
        { provide: AuthService, useValue: authServiceMock },
        { provide: Router, useValue: routerMock },
      ],
    });
    httpClient = TestBed.inject(HttpClient);
    httpTesting = TestBed.inject(HttpTestingController);
  });

  it('anexa Authorization Bearer aos pedidos à API', () => {
    httpClient.get('/api/v1/exemplo').subscribe();

    const requisicao = httpTesting.expectOne('/api/v1/exemplo');
    expect(requisicao.request.headers.get('Authorization')).toBe('Bearer access-atual');
    requisicao.flush({});
  });

  it('numa resposta 401, renova o token e repete o pedido original de forma transparente', async () => {
    authServiceMock.refresh.mockImplementation(() => {
      tokenAtual = 'access-novo';
      return of({ accessToken: 'access-novo', refreshToken: 'refresh-novo' } as LoginResponse);
    });

    const promise = new Promise((resolve, reject) => {
      httpClient.get('/api/v1/exemplo').subscribe({ next: resolve, error: reject });
    });

    const primeiraTentativa = httpTesting.expectOne('/api/v1/exemplo');
    primeiraTentativa.flush({ codigo: 'ERRO' }, { status: 401, statusText: 'Unauthorized' });

    const segundaTentativa = httpTesting.expectOne('/api/v1/exemplo');
    expect(segundaTentativa.request.headers.get('Authorization')).toBe('Bearer access-novo');
    segundaTentativa.flush({ ok: true });

    await expect(promise).resolves.toEqual({ ok: true });
    expect(authServiceMock.refresh).toHaveBeenCalledTimes(1);
  });

  it('limpa a sessão e encaminha para o login quando a renovação falha, sem repetir o pedido', async () => {
    authServiceMock.refresh.mockReturnValue(throwError(() => new Error('refresh token inválido')));

    const promise = new Promise((_resolve, reject) => {
      httpClient.get('/api/v1/exemplo').subscribe({ next: () => reject(new Error('esperava erro')), error: reject });
    });

    httpTesting.expectOne('/api/v1/exemplo').flush({ codigo: 'ERRO' }, { status: 401, statusText: 'Unauthorized' });

    await expect(promise).rejects.toBeInstanceOf(Error);
    expect(authServiceMock.limparSessao).toHaveBeenCalledTimes(1);
    expect(routerMock.navigate).toHaveBeenCalledWith(['/login']);
    httpTesting.verify();
  });

  it('um refresh recusado (invalid_grant) limpa a sessão e encaminha para o login', async () => {
    const recusa = new ApiError('invalid_grant', 'Refresh token inválido, expirado ou já usado.', '', null);
    authServiceMock.refresh.mockReturnValue(throwError(() => recusa));

    const promise = new Promise((_resolve, reject) => {
      httpClient.get('/api/v1/exemplo').subscribe({ next: () => reject(new Error('esperava erro')), error: reject });
    });
    httpTesting.expectOne('/api/v1/exemplo').flush(null, { status: 401, statusText: 'Unauthorized' });

    await expect(promise).rejects.toBe(recusa);
    expect(authServiceMock.limparSessao).toHaveBeenCalledTimes(1);
    expect(routerMock.navigate).toHaveBeenCalledWith(['/login']);
    httpTesting.verify();
  });

  it('num 401 de um pedido enviado antes de outra renovação, repete com o token novo sem gastar outro refresh', async () => {
    const promise = new Promise((resolve, reject) => {
      httpClient.get('/api/v1/exemplo').subscribe({ next: resolve, error: reject });
    });
    const primeira = httpTesting.expectOne('/api/v1/exemplo');
    expect(primeira.request.headers.get('Authorization')).toBe('Bearer access-atual');
    tokenAtual = 'access-renovado';
    primeira.flush(null, { status: 401, statusText: 'Unauthorized' });

    const segunda = httpTesting.expectOne('/api/v1/exemplo');
    expect(segunda.request.headers.get('Authorization')).toBe('Bearer access-renovado');
    segunda.flush({ ok: true });

    await expect(promise).resolves.toEqual({ ok: true });
    expect(authServiceMock.refresh).not.toHaveBeenCalled();
  });

  it('num 401 de um pedido marcado com RENOVACAO_PELO_CHAMADOR anexa o Bearer mas não renova nem repete', async () => {
    const context = new HttpContext().set(RENOVACAO_PELO_CHAMADOR, true);
    const promise = new Promise((_resolve, reject) => {
      httpClient.post('/api/v1/processos/p1/documentos', new FormData(), { context }).subscribe({
        next: () => reject(new Error('esperava erro')),
        error: reject,
      });
    });

    const requisicao = httpTesting.expectOne('/api/v1/processos/p1/documentos');
    expect(requisicao.request.headers.get('Authorization')).toBe('Bearer access-atual');
    requisicao.flush(null, { status: 401, statusText: 'Unauthorized' });

    await expect(promise).rejects.toBeInstanceOf(HttpErrorResponse);
    expect(authServiceMock.refresh).not.toHaveBeenCalled();
    expect(authServiceMock.limparSessao).not.toHaveBeenCalled();
    httpTesting.verify();
  });

  it('nunca anexa Authorization nem renova num 401 do endpoint de revogação', async () => {
    const promise = new Promise((_resolve, reject) => {
      httpClient.post('/oauth2/revoke', 'token=x').subscribe({ next: () => reject(new Error('esperava erro')), error: reject });
    });

    const requisicao = httpTesting.expectOne('/oauth2/revoke');
    expect(requisicao.request.headers.has('Authorization')).toBe(false);
    requisicao.flush({ error: 'invalid_client' }, { status: 401, statusText: 'Unauthorized' });

    await expect(promise).rejects.toBeTruthy();
    expect(authServiceMock.refresh).not.toHaveBeenCalled();
    httpTesting.verify();
  });

  it('nunca anexa Authorization ao endpoint de tokens do servidor de autorização', () => {
    httpClient.post('/oauth2/token', 'grant_type=x').subscribe();

    const requisicao = httpTesting.expectOne('/oauth2/token');
    expect(requisicao.request.headers.has('Authorization')).toBe(false);
    requisicao.flush({});
  });

  it('nunca tenta renovar num 401 do endpoint de tokens', async () => {
    const promise = new Promise((_resolve, reject) => {
      httpClient.post('/oauth2/token', 'grant_type=x').subscribe({ next: () => reject(new Error('esperava erro')), error: reject });
    });

    httpTesting.expectOne('/oauth2/token').flush({ error: 'invalid_client' }, { status: 401, statusText: 'Unauthorized' });

    await expect(promise).rejects.toBeTruthy();
    expect(authServiceMock.refresh).not.toHaveBeenCalled();
    httpTesting.verify();
  });
});
