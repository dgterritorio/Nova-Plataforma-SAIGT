import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { guestGuard } from './guest.guard';
import { AuthService } from './auth.service';

describe('guestGuard', () => {
  let authServiceMock: { estaAutenticado: () => boolean; logout: ReturnType<typeof vi.fn> };

  beforeEach(() => {
    authServiceMock = { estaAutenticado: () => false, logout: vi.fn().mockReturnValue(of(undefined)) };

    TestBed.configureTestingModule({
      providers: [{ provide: AuthService, useValue: authServiceMock }],
    });
  });

  it('deixa sempre entrar na página de login', () => {
    const resultado = TestBed.runInInjectionContext(() => guestGuard({} as never, {} as never));
    expect(resultado).toBe(true);
  });

  it('não termina sessão quando não há nenhuma em curso', () => {
    TestBed.runInInjectionContext(() => guestGuard({} as never, {} as never));
    expect(authServiceMock.logout).not.toHaveBeenCalled();
  });

  it('termina a sessão quando o utilizador ainda está autenticado (ex.: voltou atrás no browser)', () => {
    authServiceMock.estaAutenticado = () => true;

    const resultado = TestBed.runInInjectionContext(() => guestGuard({} as never, {} as never));

    expect(authServiceMock.logout).toHaveBeenCalled();
    expect(resultado).toBe(true);
  });

  it('não deixa um erro no logout impedir o acesso à página de login', () => {
    authServiceMock.estaAutenticado = () => true;
    authServiceMock.logout.mockReturnValue(throwError(() => new Error('falhou')));

    const resultado = TestBed.runInInjectionContext(() => guestGuard({} as never, {} as never));

    expect(resultado).toBe(true);
  });
});
