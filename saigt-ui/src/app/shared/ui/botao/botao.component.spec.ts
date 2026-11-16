import { Component, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { describe, expect, it } from 'vitest';
import { BotaoComponent, TamanhoBotao, VarianteBotao } from './botao.component';

@Component({
  imports: [BotaoComponent],
  template: `
    <button
      type="button"
      app-botao
      [variante]="variante()"
      [tamanho]="tamanho()"
      [desativado]="desativado()"
      [aCarregar]="aCarregar()"
      (click)="cliques = cliques + 1"
    >
      <svg icone class="icone-teste"></svg>
      Guardar
    </button>
    <a app-botao variante="texto" href="/destino" [desativado]="desativado()">Ver</a>
  `,
})
class AnfitriaoComponent {
  readonly variante = signal<VarianteBotao>('primario');
  readonly tamanho = signal<TamanhoBotao>('normal');
  readonly desativado = signal(false);
  readonly aCarregar = signal(false);
  cliques = 0;
}

function criar() {
  const fixture = TestBed.createComponent(AnfitriaoComponent);
  fixture.detectChanges();
  const botao: HTMLButtonElement = fixture.nativeElement.querySelector('button');
  const ligacao: HTMLAnchorElement = fixture.nativeElement.querySelector('a');
  return { fixture, botao, ligacao, anfitriao: fixture.componentInstance };
}

describe('BotaoComponent', () => {
  it('mantém o elemento nativo e é primário e normal por omissão', () => {
    const { botao } = criar();
    expect(botao.tagName).toBe('BUTTON');
    expect(botao.classList).toContain('botao-primario');
    expect(botao.classList).toContain('botao-normal');
    expect(botao.textContent).toContain('Guardar');
    expect(botao.querySelector('.icone-teste')).not.toBeNull();
  });

  it.each<VarianteBotao>(['primario', 'secundario', 'suave', 'contorno', 'texto', 'perigo'])('aplica a variante %s', (variante) => {
    const { fixture, botao, anfitriao } = criar();
    anfitriao.variante.set(variante);
    fixture.detectChanges();
    expect(botao.classList).toContain(`botao-${variante}`);
  });

  it('aplica o tamanho pequeno', () => {
    const { fixture, botao, anfitriao } = criar();
    anfitriao.tamanho.set('pequeno');
    fixture.detectChanges();
    expect(botao.classList).toContain('botao-pequeno');
  });

  it('desativado não emite cliques', () => {
    const { fixture, botao, anfitriao } = criar();
    anfitriao.desativado.set(true);
    fixture.detectChanges();

    botao.click();

    expect(botao.disabled).toBe(true);
    expect(botao.getAttribute('aria-disabled')).toBe('true');
    expect(anfitriao.cliques).toBe(0);
  });

  it('a carregar fica desativado e mostra o indicador no lugar do ícone', () => {
    const { fixture, botao, anfitriao } = criar();
    anfitriao.aCarregar.set(true);
    fixture.detectChanges();

    botao.click();

    expect(anfitriao.cliques).toBe(0);
    expect(botao.getAttribute('aria-busy')).toBe('true');
    expect(botao.querySelector('.botao-indicador')).not.toBeNull();
    expect(botao.querySelector('.icone-teste')).toBeNull();
  });

  it('numa ligação desativada impede a navegação e tira-a da ordem do Tab', () => {
    const { fixture, ligacao, anfitriao } = criar();
    anfitriao.desativado.set(true);
    fixture.detectChanges();

    const evento = new MouseEvent('click', { cancelable: true, bubbles: true });
    ligacao.dispatchEvent(evento);

    expect(evento.defaultPrevented).toBe(true);
    expect(ligacao.getAttribute('tabindex')).toBe('-1');
  });
});
