import { ComponentFixture, TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ConfirmacaoModalComponent } from './confirmacao-modal.component';

describe('ConfirmacaoModalComponent', () => {
  let fixture: ComponentFixture<ConfirmacaoModalComponent>;
  const el = () => fixture.nativeElement as HTMLElement;
  const botao = (classe: string) => el().querySelector<HTMLButtonElement>(classe)!;

  function abrir(aProcessar = false): void {
    fixture.componentRef.setInput('aberto', true);
    fixture.componentRef.setInput('aProcessar', aProcessar);
    fixture.detectChanges();
    vi.runAllTimers();
  }

  beforeEach(() => {
    vi.useFakeTimers();
    TestBed.configureTestingModule({ imports: [ConfirmacaoModalComponent] });
    fixture = TestBed.createComponent(ConfirmacaoModalComponent);
    fixture.componentRef.setInput('titulo', 'Submeter');
    fixture.componentRef.setInput('mensagem', 'Mensagem de teste.');
  });

  afterEach(() => vi.useRealTimers());

  it('não renderiza nada quando fechado', () => {
    fixture.componentRef.setInput('aberto', false);
    fixture.detectChanges();
    expect(el().querySelector('[role="dialog"]')).toBeNull();
  });

  it('é o modal partilhado na variante de confirmação, com o título e a mensagem do chamador', () => {
    abrir();

    const dialogo = el().querySelector('[role="dialog"]')!;
    expect(dialogo.classList).toContain('modal-confirmacao');
    expect(dialogo.querySelector('h2')?.textContent?.trim()).toBe('Submeter');
    expect(dialogo.querySelector('.mensagem')?.textContent?.trim()).toBe('Mensagem de teste.');
  });

  it('o diálogo é descrito pela mensagem, para o leitor de ecrã a ler ao abrir', () => {
    abrir();

    const dialogo = el().querySelector('[role="dialog"]')!;
    const descricao = el().querySelector(`#${dialogo.getAttribute('aria-describedby')}`);
    expect(descricao?.textContent?.trim()).toBe('Mensagem de teste.');
  });

  it('mostra os rótulos do chamador nos botões, mantendo Sim e Não por omissão', () => {
    abrir();
    expect(botao('.botao-sim').textContent?.trim()).toBe('Sim');
    expect(botao('.botao-nao').textContent?.trim()).toBe('Não');

    fixture.componentRef.setInput('rotuloSim', 'Corrigir');
    fixture.componentRef.setInput('rotuloNao', 'Cancelar');
    fixture.detectChanges();

    expect(botao('.botao-sim').textContent?.trim()).toBe('Corrigir');
    expect(botao('.botao-nao').textContent?.trim()).toBe('Cancelar');
  });

  it('emite sim ao clicar em Sim', () => {
    abrir();
    let emitido = false;
    fixture.componentInstance.sim.subscribe(() => (emitido = true));

    botao('.botao-sim').click();

    expect(emitido).toBe(true);
  });

  it('emite nao ao clicar em Não, no fundo ou com Esc — nunca sim', () => {
    abrir();
    let simEmitido = false;
    let naoEmitido = 0;
    fixture.componentInstance.sim.subscribe(() => (simEmitido = true));
    fixture.componentInstance.nao.subscribe(() => naoEmitido++);

    botao('.botao-nao').click();
    el().querySelector<HTMLElement>('.modal-fundo')!.click();
    el().querySelector('[role="dialog"]')!.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }));

    expect(naoEmitido).toBe(3);
    expect(simEmitido).toBe(false);
  });

  it('o foco começa no Não, a escolha que não faz nada', () => {
    abrir();
    document.body.appendChild(fixture.nativeElement);
    fixture.componentRef.setInput('aberto', false);
    fixture.detectChanges();
    abrir();

    expect(document.activeElement).toBe(botao('.botao-nao'));
    fixture.nativeElement.remove();
  });

  it('enquanto aProcessar, desativa os botões e não fecha com Esc nem com o fundo', () => {
    abrir(true);
    let naoEmitido = 0;
    fixture.componentInstance.nao.subscribe(() => naoEmitido++);

    expect(botao('.botao-sim').getAttribute('aria-disabled')).toBe('true');
    expect(botao('.botao-nao').getAttribute('aria-disabled')).toBe('true');
    expect(botao('.botao-sim').textContent?.trim()).toBe('A processar…');

    el().querySelector<HTMLElement>('.modal-fundo')!.click();
    el().querySelector('[role="dialog"]')!.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }));
    expect(naoEmitido).toBe(0);
  });
});
