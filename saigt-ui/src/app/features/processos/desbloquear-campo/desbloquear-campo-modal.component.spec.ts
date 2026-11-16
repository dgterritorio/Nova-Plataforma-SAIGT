import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ApiError } from '../../../core/models/error-response.model';
import { Processo } from '../../../models/processo.model';
import { ProcessoService } from '../../../services/processo.service';
import { DesbloquearCampoModalComponent } from './desbloquear-campo-modal.component';
import { DesbloqueioCampos } from './desbloqueio-campos';

describe('DesbloquearCampoModalComponent', () => {
  let fixture: ComponentFixture<DesbloquearCampoModalComponent>;
  let contexto: DesbloqueioCampos;
  let desbloquearCampo: ReturnType<typeof vi.fn>;
  let recebido: Processo | null;

  const RESPOSTA = { id: 'p1', estado: 'EM_SUBMISSAO', versao: 8, camposBloqueados: ['normaLegal'] } as unknown as Processo;

  beforeEach(() => {
    recebido = null;
    desbloquearCampo = vi.fn().mockReturnValue(of(RESPOSTA));
    contexto = new DesbloqueioCampos();
    contexto.ligar(signal({ processoId: 'p1', versao: 7 }), (processo) => (recebido = processo));
    TestBed.configureTestingModule({
      imports: [DesbloquearCampoModalComponent],
      providers: [
        { provide: DesbloqueioCampos, useValue: contexto },
        { provide: ProcessoService, useValue: { desbloquearCampo } },
      ],
    });
    fixture = TestBed.createComponent(DesbloquearCampoModalComponent);
    fixture.detectChanges();
  });

  function abrir(): void {
    contexto.pedir('sumario', 'Sumário conteúdo do ato');
    fixture.detectChanges();
  }

  function botao(texto: string): HTMLButtonElement {
    return Array.from((fixture.nativeElement as HTMLElement).querySelectorAll<HTMLButtonElement>('[role="dialog"] button')).find(
      (b) => b.textContent?.trim() === texto,
    )!;
  }

  function escrever(texto: string): void {
    const caixa = (fixture.nativeElement as HTMLElement).querySelector<HTMLTextAreaElement>('#motivo-desbloqueio-campo')!;
    caixa.value = texto;
    caixa.dispatchEvent(new Event('input'));
    fixture.detectChanges();
  }

  it('fechado enquanto não há pedido', () => {
    expect((fixture.nativeElement as HTMLElement).querySelector('[role="dialog"]')).toBeNull();
  });

  it('pede o motivo do campo escolhido e «Confirmar» fica desativado enquanto o motivo está vazio', () => {
    abrir();

    expect((fixture.nativeElement as HTMLElement).querySelector('[role="dialog"]')!.textContent).toContain('Sumário conteúdo do ato');
    expect(botao('Confirmar').disabled).toBe(true);
    escrever('   ');
    expect(botao('Confirmar').disabled).toBe(true);
    escrever('A DGT recusou o sumário.');
    expect(botao('Confirmar').disabled).toBe(false);
  });

  it('confirmar chama o PATCH com a chave, o motivo e a versão e entrega a resposta ao ecrã', () => {
    abrir();
    escrever('  A DGT recusou o sumário. ');
    botao('Confirmar').click();
    fixture.detectChanges();

    expect(desbloquearCampo).toHaveBeenCalledWith('p1', { versao: 7, campo: 'sumario', motivo: 'A DGT recusou o sumário.' });
    expect(recebido).toBe(RESPOSTA);
    expect(contexto.pendente()).toBeNull();
  });

  it('um 400 mostra o erro junto do motivo e o modal continua aberto', () => {
    desbloquearCampo.mockReturnValue(
      throwError(() => new ApiError('VALIDACAO', 'Payload inválido.', 't1', [{ path: 'motivo', erro: 'Indique o motivo do desbloqueio.' }])),
    );
    abrir();
    escrever('x');
    botao('Confirmar').click();
    fixture.detectChanges();

    const erro = (fixture.nativeElement as HTMLElement).querySelector('#motivo-desbloqueio-campo-erro')!;
    expect(erro.textContent).toContain('Indique o motivo do desbloqueio.');
    expect((fixture.nativeElement as HTMLElement).querySelector('#motivo-desbloqueio-campo')!.getAttribute('aria-invalid')).toBe('true');
    expect(contexto.pendente()).not.toBeNull();
    expect(recebido).toBeNull();
  });

  it('um 409 mostra a mensagem do servidor', () => {
    desbloquearCampo.mockReturnValue(
      throwError(() => new ApiError('ESTADO_INVALIDO', 'Só é possível desbloquear campos de um processo em correção.', 't1', null)),
    );
    abrir();
    escrever('Motivo.');
    botao('Confirmar').click();
    fixture.detectChanges();

    expect((fixture.nativeElement as HTMLElement).querySelector('[role="dialog"] app-alerta')!.textContent).toContain(
      'Só é possível desbloquear campos de um processo em correção.',
    );
    expect(recebido).toBeNull();
  });

  it('«Cancelar» fecha sem chamar o PATCH', () => {
    abrir();
    botao('Cancelar').click();
    fixture.detectChanges();

    expect(contexto.pendente()).toBeNull();
    expect(desbloquearCampo).not.toHaveBeenCalled();
  });
});
