import { TestBed } from '@angular/core/testing';
import { Router, UrlTree } from '@angular/router';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { AuthService } from './auth.service';
import { listaValoresGuard } from './lista-valores.guard';

describe('listaValoresGuard', () => {
  let perfil: string | null;
  let routerMock: { createUrlTree: ReturnType<typeof vi.fn> };

  beforeEach(() => {
    perfil = null;
    routerMock = { createUrlTree: vi.fn().mockReturnValue('url-tree-de-painel' as unknown as UrlTree) };
    TestBed.configureTestingModule({
      providers: [
        { provide: AuthService, useValue: { perfilInterno: () => perfil } },
        { provide: Router, useValue: routerMock },
      ],
    });
  });

  const correr = () => TestBed.runInInjectionContext(() => listaValoresGuard({} as never, {} as never));

  it.each(['MASTER', 'TECNICO'])('deixa entrar o perfil %s', (valor) => {
    perfil = valor;
    expect(correr()).toBe(true);
  });

  it.each(['VISITANTE', null])('envia %s para o painel', (valor) => {
    perfil = valor;
    expect(correr()).toBe('url-tree-de-painel');
    expect(routerMock.createUrlTree).toHaveBeenCalledWith(['/painel']);
  });
});
