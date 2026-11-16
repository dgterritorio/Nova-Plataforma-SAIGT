import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { NotificacaoService } from './notificacao.service';
import { NotificacoesNaoLidasService } from './notificacoes-nao-lidas.service';

describe('NotificacoesNaoLidasService', () => {
  let contarNaoLidas: ReturnType<typeof vi.fn>;
  let service: NotificacoesNaoLidasService;

  beforeEach(() => {
    contarNaoLidas = vi.fn();
    TestBed.configureTestingModule({ providers: [{ provide: NotificacaoService, useValue: { contarNaoLidas } }] });
    service = TestBed.inject(NotificacoesNaoLidasService);
  });

  it('começa em 0 e guarda a contagem', () => {
    expect(service.naoLidas()).toBe(0);
    contarNaoLidas.mockReturnValue(of(2));

    service.atualizar();

    expect(service.naoLidas()).toBe(2);
  });

  it('uma falha mantém o último valor', () => {
    contarNaoLidas.mockReturnValueOnce(of(4)).mockReturnValueOnce(throwError(() => new Error('rede')));

    service.atualizar();
    service.atualizar();

    expect(service.naoLidas()).toBe(4);
  });
});
