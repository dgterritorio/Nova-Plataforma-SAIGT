import { Component, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { describe, expect, it } from 'vitest';
import { AlertaComponent, TipoAlerta } from './alerta.component';

@Component({
  imports: [AlertaComponent],
  template: `
    <app-alerta [tipo]="tipo()" [titulo]="titulo()" [fechavel]="fechavel()" [urgente]="urgente()" (fechar)="fechos = fechos + 1">
      Consulte a deliberação.
    </app-alerta>
  `,
})
class AnfitriaoComponent {
  readonly tipo = signal<TipoAlerta>('info');
  readonly titulo = signal<string | null>(null);
  readonly fechavel = signal(false);
  readonly urgente = signal(false);
  fechos = 0;
}

function criar() {
  const fixture = TestBed.createComponent(AnfitriaoComponent);
  fixture.detectChanges();
  const alerta = (): HTMLElement => fixture.nativeElement.querySelector('app-alerta');
  return { fixture, alerta, anfitriao: fixture.componentInstance };
}

describe('AlertaComponent', () => {
  it('o erro tem role="alert"', () => {
    const { fixture, alerta, anfitriao } = criar();
    anfitriao.tipo.set('erro');
    fixture.detectChanges();

    expect(alerta().getAttribute('role')).toBe('alert');
    expect(alerta().classList).toContain('alerta-erro');
  });

  it.each<TipoAlerta>(['info', 'sucesso', 'aviso'])('%s tem role="status"', (tipo) => {
    const { fixture, alerta, anfitriao } = criar();
    anfitriao.tipo.set(tipo);
    fixture.detectChanges();

    expect(alerta().getAttribute('role')).toBe('status');
    expect(alerta().classList).toContain(`alerta-${tipo}`);
  });

  it('um aviso urgente tem role="alert" sem deixar de ser aviso', () => {
    const { fixture, alerta, anfitriao } = criar();
    anfitriao.tipo.set('aviso');
    anfitriao.urgente.set(true);
    fixture.detectChanges();

    expect(alerta().getAttribute('role')).toBe('alert');
    expect(alerta().classList).toContain('alerta-aviso');
  });

  it('mostra o título e a mensagem projetada, com o ícone escondido do leitor de ecrã', () => {
    const { fixture, alerta, anfitriao } = criar();
    anfitriao.titulo.set('Atenção');
    fixture.detectChanges();

    expect(alerta().querySelector('.alerta-titulo')?.textContent?.trim()).toBe('Atenção');
    expect(alerta().querySelector('.alerta-mensagem')?.textContent?.trim()).toBe('Consulte a deliberação.');
    expect(alerta().querySelector('svg.alerta-icone')?.getAttribute('aria-hidden')).toBe('true');
  });

  it('sem fechavel não tem botão de fechar', () => {
    const { alerta } = criar();
    expect(alerta().querySelector('.alerta-fechar')).toBeNull();
  });

  it('fechavel: o botão tem nome acessível e emite fechar', () => {
    const { fixture, alerta, anfitriao } = criar();
    anfitriao.fechavel.set(true);
    fixture.detectChanges();

    const botao = alerta().querySelector<HTMLButtonElement>('.alerta-fechar')!;
    expect(botao.getAttribute('aria-label')).toBe('Fechar aviso');
    botao.click();

    expect(anfitriao.fechos).toBe(1);
  });
});
