import { TestBed } from '@angular/core/testing';
import { Router, UrlTree } from '@angular/router';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { authGuard } from './auth.guard';
import { AuthService } from './auth.service';

describe('authGuard', () => {
  let authServiceMock: { estaAutenticado: () => boolean };
  let routerMock: { createUrlTree: ReturnType<typeof vi.fn> };

  beforeEach(() => {
    authServiceMock = { estaAutenticado: () => false };
    routerMock = { createUrlTree: vi.fn().mockReturnValue('url-tree-de-login' as unknown as UrlTree) };

    TestBed.configureTestingModule({
      providers: [
        { provide: AuthService, useValue: authServiceMock },
        { provide: Router, useValue: routerMock },
      ],
    });
  });

  it('permite o acesso quando há sessão autenticada', () => {
    authServiceMock.estaAutenticado = () => true;

    const resultado = TestBed.runInInjectionContext(() =>
      authGuard({} as never, { url: '/qualquer' } as never),
    );

    expect(resultado).toBe(true);
  });

  it('encaminha para o login quando não há sessão', () => {
    const resultado = TestBed.runInInjectionContext(() =>
      authGuard({} as never, { url: '/rota-protegida' } as never),
    );

    expect(routerMock.createUrlTree).toHaveBeenCalledWith(['/login']);
    expect(resultado).toBe('url-tree-de-login');
  });
});
