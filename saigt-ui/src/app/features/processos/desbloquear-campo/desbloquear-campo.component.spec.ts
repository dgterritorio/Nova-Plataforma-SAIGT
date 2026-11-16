import { Component, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { describe, expect, it } from 'vitest';
import { DesbloquearCampoComponent } from './desbloquear-campo.component';
import { AlvoDesbloqueio, DesbloqueioCampos } from './desbloqueio-campos';

@Component({
  imports: [DesbloquearCampoComponent],
  template: `<label>Sumário <textarea></textarea><app-desbloquear-campo chave="sumario" rotulo="Sumário" /></label>`,
})
class AnfitriaoComponent {}

describe('DesbloquearCampoComponent', () => {
  function montar(alvo: AlvoDesbloqueio | null, comContexto = true) {
    const contexto = new DesbloqueioCampos();
    contexto.ligar(signal(alvo), () => undefined);
    TestBed.configureTestingModule({
      imports: [AnfitriaoComponent],
      providers: comContexto ? [{ provide: DesbloqueioCampos, useValue: contexto }] : [],
    });
    const fixture = TestBed.createComponent(AnfitriaoComponent);
    fixture.detectChanges();
    return { fixture, contexto, botao: () => fixture.nativeElement.querySelector('app-desbloquear-campo button') as HTMLButtonElement | null };
  }

  it('sem o contexto do ecrã não mostra a ação', () => {
    const { botao } = montar({ processoId: 'p1', versao: 1 }, false);

    expect(botao()).toBeNull();
  });

  it('sem processo em correção (sem «desbloquear-campo» nas ações) não mostra a ação', () => {
    const { botao } = montar(null);

    expect(botao()).toBeNull();
  });

  it('com o processo em correção mostra «Desbloquear» com o nome do campo e pede o desbloqueio dessa chave', () => {
    const { fixture, contexto, botao } = montar({ processoId: 'p1', versao: 1 });

    expect(botao()!.textContent!.trim()).toBe('Desbloquear');
    expect(botao()!.getAttribute('aria-label')).toBe('Desbloquear Sumário');
    botao()!.click();
    fixture.detectChanges();

    expect(contexto.pendente()).toEqual({ chave: 'sumario', rotulo: 'Sumário' });
    expect(fixture.nativeElement.querySelector('textarea') === document.activeElement).toBe(false);
  });
});
