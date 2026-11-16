import { ComponentFixture, TestBed } from '@angular/core/testing';
import { describe, expect, it } from 'vitest';
import { AlterarPerfilModalComponent } from './alterar-perfil-modal.component';

describe('AlterarPerfilModalComponent', () => {
  let fixture: ComponentFixture<AlterarPerfilModalComponent>;
  const el = () => fixture.nativeElement as HTMLElement;
  const botaoSubmeter = () => el().querySelector<HTMLButtonElement>('button[type="submit"]')!;

  function abrir(perfilAtual: 'MASTER' | 'TECNICO' | 'VISITANTE' | null = 'TECNICO'): void {
    TestBed.configureTestingModule({ imports: [AlterarPerfilModalComponent] });
    fixture = TestBed.createComponent(AlterarPerfilModalComponent);
    fixture.componentRef.setInput('perfilAtual', perfilAtual);
    fixture.componentRef.setInput('nome', 'Ana Ferreira');
    fixture.componentRef.setInput('aberto', true);
    fixture.detectChanges();
  }

  it('não renderiza nada quando fechado', () => {
    TestBed.configureTestingModule({ imports: [AlterarPerfilModalComponent] });
    fixture = TestBed.createComponent(AlterarPerfilModalComponent);
    fixture.componentRef.setInput('aberto', false);
    fixture.detectChanges();
    expect(el().querySelector('[role="dialog"]')).toBeNull();
  });

  it('usa o modal partilhado, estreito, com o diálogo identificado pelo título', () => {
    abrir();

    const dialogo = el().querySelector('app-modal [role="dialog"]')!;
    const titulo = dialogo.querySelector('h2')!;
    expect(dialogo.getAttribute('aria-labelledby')).toBe(titulo.id);
    expect(titulo.textContent?.trim()).toBe('Alterar perfil');
    expect(dialogo.classList).toContain('modal-sm');
    expect(dialogo.querySelector('.modal-corpo app-seletor')).not.toBeNull();
  });

  it('não tem campo de motivo e diz de quem é o perfil', () => {
    abrir();

    expect(el().querySelector('textarea, app-campo-texto')).toBeNull();
    expect(el().textContent).not.toContain('Motivo');
    expect(el().querySelector('.alvo')?.textContent?.trim()).toBe('Ana Ferreira');
  });

  it('mostra as opções Master, Técnico e Visitante, sem nenhum nome antigo', () => {
    abrir('TECNICO');

    const opcoes = [...el().querySelectorAll<HTMLOptionElement>('app-seletor option:not([disabled])')];
    expect(opcoes.map((opcao) => opcao.textContent?.trim())).toEqual(['Master', 'Técnico', 'Visitante']);
    expect(opcoes.map((opcao) => opcao.value)).toEqual(['MASTER', 'TECNICO', 'VISITANTE']);
    expect(el().textContent).not.toMatch(/ADMIN_DGT|TECNICO_DGT|TECNICO_SNIT/);
  });

  it('pré-seleciona o perfil atual ao abrir', () => {
    abrir('VISITANTE');
    expect(fixture.componentInstance.form.getRawValue().perfilInterno).toBe('VISITANTE');
    expect(el().querySelector<HTMLSelectElement>('app-seletor select')!.value).toBe('VISITANTE');
  });

  it('desativa a confirmação quando o perfil escolhido é igual ao atual', () => {
    abrir('TECNICO');
    fixture.componentInstance.form.setValue({ perfilInterno: 'TECNICO' });
    fixture.detectChanges();

    expect(fixture.componentInstance.perfilInalterado()).toBe(true);
    expect(botaoSubmeter().disabled).toBe(true);
  });

  it('ativa a confirmação só com um perfil diferente, sem pedir motivo', () => {
    abrir('TECNICO');
    fixture.componentInstance.form.setValue({ perfilInterno: 'MASTER' });
    fixture.detectChanges();

    expect(botaoSubmeter().disabled).toBe(false);
  });

  it('submete pelo botão Alterar perfil do rodapé e emite só o perfil escolhido', () => {
    abrir('TECNICO');
    document.body.appendChild(el());
    let emitido: unknown;
    fixture.componentInstance.confirmar.subscribe((valor) => (emitido = valor));

    const perfil = el().querySelector<HTMLSelectElement>('app-seletor select')!;
    perfil.value = 'VISITANTE';
    perfil.dispatchEvent(new Event('change'));
    fixture.detectChanges();
    botaoSubmeter().click();

    expect(emitido).toEqual({ perfilInterno: 'VISITANTE' });
    el().remove();
  });

  it('não emite confirmar quando o perfil é igual ao atual', () => {
    abrir('TECNICO');
    let emitido = false;
    fixture.componentInstance.confirmar.subscribe(() => (emitido = true));

    fixture.componentInstance.form.setValue({ perfilInterno: 'TECNICO' });
    fixture.componentInstance.confirmarClick();

    expect(emitido).toBe(false);
  });

  it('emite cancelar ao clicar em Cancelar, no Fechar ou no fundo', () => {
    abrir();
    let cancelados = 0;
    fixture.componentInstance.cancelar.subscribe(() => cancelados++);

    el().querySelector<HTMLButtonElement>('.botao-cancelar')!.click();
    el().querySelector<HTMLButtonElement>('button[aria-label="Fechar"]')!.click();
    el().querySelector<HTMLElement>('.modal-fundo')!.click();

    expect(cancelados).toBe(3);
  });

  it('fecha ao premir Escape', () => {
    abrir();
    let cancelados = 0;
    fixture.componentInstance.cancelar.subscribe(() => cancelados++);

    el().querySelector('[role="dialog"]')!.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }));

    expect(cancelados).toBe(1);
  });
});
