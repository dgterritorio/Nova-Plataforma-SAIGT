import { TestBed } from '@angular/core/testing';
import { Router, UrlTree } from '@angular/router';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { adminGuard } from './admin.guard';
import { AuthService } from './auth.service';

describe('adminGuard', () => {
  let authServiceMock: { perfilInterno: () => string | null };
  let routerMock: { createUrlTree: ReturnType<typeof vi.fn> };

  beforeEach(() => {
    authServiceMock = { perfilInterno: () => null };
    routerMock = { createUrlTree: vi.fn().mockReturnValue('url-tree-de-painel' as unknown as UrlTree) };

    TestBed.configureTestingModule({
      providers: [
        { provide: AuthService, useValue: authServiceMock },
        { provide: Router, useValue: routerMock },
      ],
    });
  });

  it('permite o acesso a MASTER', () => {
    authServiceMock.perfilInterno = () => 'MASTER';

    const resultado = TestBed.runInInjectionContext(() => adminGuard({} as never, {} as never));

    expect(resultado).toBe(true);
  });

  it('redireciona TECNICO para o painel — o backoffice é só do MASTER', () => {
    authServiceMock.perfilInterno = () => 'TECNICO';

    const resultado = TestBed.runInInjectionContext(() => adminGuard({} as never, {} as never));

    expect(routerMock.createUrlTree).toHaveBeenCalledWith(['/painel']);
    expect(resultado).toBe('url-tree-de-painel');
  });

  it('bloqueia VISITANTE — nunca administra utilizadores, tal como no backend', () => {
    authServiceMock.perfilInterno = () => 'VISITANTE';

    const resultado = TestBed.runInInjectionContext(() => adminGuard({} as never, {} as never));

    expect(routerMock.createUrlTree).toHaveBeenCalledWith(['/painel']);
    expect(resultado).toBe('url-tree-de-painel');
  });

  it('bloqueia um utilizador externo (INCM, sem perfilInterno)', () => {
    authServiceMock.perfilInterno = () => null;

    const resultado = TestBed.runInInjectionContext(() => adminGuard({} as never, {} as never));

    expect(resultado).toBe('url-tree-de-painel');
  });
});
