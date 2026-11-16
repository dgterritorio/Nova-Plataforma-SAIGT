import { Component, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { readFileSync } from 'node:fs';
import { join } from 'node:path';
import { describe, expect, it } from 'vitest';
import { Separador, SeparadoresComponent } from './separadores.component';

const SEPARADORES: Separador[] = [
  { id: 'perfis', etiqueta: 'Perfis e permissões', icone: 'icones/administracao/perfis.svg' },
  { id: 'utilizadores', etiqueta: 'Utilizadores', contador: 4 },
  { id: 'historico', etiqueta: 'Histórico' },
];

@Component({
  imports: [SeparadoresComponent],
  selector: 'app-anfitriao-pilula',
  template: `<app-separadores variante="pilula" [separadores]="separadores" [(ativo)]="ativo" />`,
})
class AnfitriaoPilulaComponent {
  readonly separadores = SEPARADORES;
  readonly ativo = signal('utilizadores');
}

@Component({
  imports: [SeparadoresComponent],
  template: `
    <app-separadores #seps rotulo="Secções" [separadores]="separadores" [(ativo)]="ativo" (mudou)="mudancas.push($event)" />
    <div role="tabpanel" [id]="seps.idPainel(ativo())" [attr.aria-labelledby]="seps.idSeparador(ativo())">{{ ativo() }}</div>
  `,
})
class AnfitriaoComponent {
  readonly separadores = SEPARADORES;
  readonly ativo = signal('utilizadores');
  readonly mudancas: string[] = [];
}

function criar() {
  const fixture = TestBed.createComponent(AnfitriaoComponent);
  fixture.detectChanges();
  const el: HTMLElement = fixture.nativeElement;
  document.body.appendChild(el);
  const tabs = () => [...el.querySelectorAll<HTMLButtonElement>('[role="tab"]')];
  const teclar = (alvo: HTMLElement, key: string) => {
    const evento = new KeyboardEvent('keydown', { key, bubbles: true, cancelable: true });
    alvo.dispatchEvent(evento);
    fixture.detectChanges();
    return evento;
  };
  return { fixture, el, tabs, teclar };
}

describe('SeparadoresComponent', () => {
  it('é um tablist com tabs; só o ativo tem aria-selected e entra no Tab', () => {
    const { el, tabs } = criar();

    expect(el.querySelector('[role="tablist"]')?.getAttribute('aria-label')).toBe('Secções');
    expect(tabs().map((t) => t.getAttribute('aria-selected'))).toEqual(['false', 'true', 'false']);
    expect(tabs().map((t) => t.getAttribute('tabindex'))).toEqual(['-1', '0', '-1']);
    expect(tabs()[1].classList).toContain('separador-ativo');
  });

  it('liga o separador ativo ao painel por aria-controls e aria-labelledby', () => {
    const { el, tabs } = criar();
    const painel = el.querySelector('[role="tabpanel"]')!;

    expect(tabs()[1].getAttribute('aria-controls')).toBe(painel.id);
    expect(painel.getAttribute('aria-labelledby')).toBe(tabs()[1].id);
  });

  it('as setas mudam o foco, dão a volta, e não ativam', () => {
    const { fixture, tabs, teclar } = criar();
    tabs()[1].focus();

    expect(teclar(tabs()[1], 'ArrowRight').defaultPrevented).toBe(true);
    expect(document.activeElement).toBe(tabs()[2]);
    teclar(tabs()[2], 'ArrowRight');
    expect(document.activeElement).toBe(tabs()[0]);
    teclar(tabs()[0], 'ArrowLeft');
    expect(document.activeElement).toBe(tabs()[2]);
    expect(fixture.componentInstance.ativo()).toBe('utilizadores');
    expect(fixture.componentInstance.mudancas).toEqual([]);
  });

  it('Home e End vão ao primeiro e ao último', () => {
    const { tabs, teclar } = criar();

    teclar(tabs()[1], 'Home');
    expect(document.activeElement).toBe(tabs()[0]);
    teclar(tabs()[0], 'End');
    expect(document.activeElement).toBe(tabs()[2]);
  });

  it('Enter ativa o separador com foco, atualiza o two-way e emite mudou', () => {
    const { fixture, el, tabs, teclar } = criar();

    teclar(tabs()[1], 'ArrowRight');
    const enter = teclar(tabs()[2], 'Enter');

    expect(enter.defaultPrevented).toBe(true);
    expect(fixture.componentInstance.ativo()).toBe('historico');
    expect(fixture.componentInstance.mudancas).toEqual(['historico']);
    expect(tabs()[2].getAttribute('aria-selected')).toBe('true');
    expect(tabs()[2].getAttribute('tabindex')).toBe('0');
    expect(el.querySelector('[role="tabpanel"]')?.getAttribute('aria-labelledby')).toBe(tabs()[2].id);
  });

  it('Espaço e clique também ativam; ativar o já ativo não emite', () => {
    const { fixture, tabs, teclar } = criar();

    teclar(tabs()[0], ' ');
    expect(fixture.componentInstance.ativo()).toBe('perfis');
    tabs()[1].click();
    fixture.detectChanges();
    tabs()[1].click();
    expect(fixture.componentInstance.mudancas).toEqual(['perfis', 'utilizadores']);
  });

  it('mostra o contador e o ícone como máscara escondida do leitor de ecrã', () => {
    const { tabs } = criar();

    expect(tabs()[1].querySelector('.separador-contador')?.textContent?.trim()).toBe('4');
    expect(tabs()[0].querySelector('.separador-contador')).toBeNull();
    const icone = tabs()[0].querySelector<HTMLElement>('.separador-icone')!;
    expect(icone.getAttribute('aria-hidden')).toBe('true');
    expect(icone.style.getPropertyValue('--separador-icone')).toContain('icones/administracao/perfis.svg');
    expect(tabs()[2].querySelector('.separador-icone')).toBeNull();
  });

  it('o CSS usa as cores e o sublinhado de 2px da especificação, e tem anel de foco', () => {
    const css = readFileSync(join(process.cwd(), 'src/app/shared/ui/separadores/separadores.component.css'), 'utf-8');

    expect(css).toMatch(/\.separadores-lista\s*{[^}]*border-bottom: var\(--borda-largura\) solid var\(--cor-borda-controlo\)/);
    expect(css).toMatch(/\.separador\s*{[^}]*border-bottom: var\(--borda-largura-opcao\) solid transparent/);
    expect(css).toMatch(/\.separador-ativo\s*{[^}]*border-bottom-color: var\(--cor-primaria-variante\)[^}]*color: var\(--cor-primaria-variante\)/);
    expect(css).toMatch(/\.separador:focus-visible\s*{[^}]*outline: var\(--anel-foco\)/);
  });

  it('é sublinhado por omissão e aceita a variante pílula (fundo no ativo, sem linha)', () => {
    expect(criar().fixture.nativeElement.querySelector('app-separadores').classList).toContain('separadores-sublinhado');
    const fixture = TestBed.createComponent(AnfitriaoPilulaComponent);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('app-separadores').classList).toContain('separadores-pilula');
    const css = readFileSync(join(process.cwd(), 'src/app/shared/ui/separadores/separadores.component.css'), 'utf-8');
    expect(css).toMatch(/:host\(\.separadores-pilula\) \.separador-ativo\s*{[^}]*background: var\(--cor-info-fundo\)/);
  });
});

