import { Component, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { readFileSync } from 'node:fs';
import { join } from 'node:path';
import { describe, expect, it } from 'vitest';
import { CartaoComponent, EspacamentoCartao, VarianteCartao } from './cartao.component';

@Component({
  imports: [CartaoComponent],
  template: `
    <app-cartao [titulo]="titulo()" [variante]="variante()" [espacamento]="espacamento()">
      <button cabecalho-acoes type="button" class="acao-teste">Editar</button>
      <p class="conteudo-teste">conteúdo</p>
    </app-cartao>
  `,
})
class AnfitriaoComponent {
  readonly titulo = signal<string | undefined>('Dados gerais');
  readonly variante = signal<VarianteCartao>('normal');
  readonly espacamento = signal<EspacamentoCartao>('normal');
}

function criar() {
  const fixture = TestBed.createComponent(AnfitriaoComponent);
  fixture.detectChanges();
  const cartao: HTMLElement = fixture.nativeElement.querySelector('app-cartao');
  return { fixture, cartao, anfitriao: fixture.componentInstance };
}

/** O jsdom não resolve variáveis CSS: as regras visuais confirmam-se no CSS do componente. */
function regra(seletor: string): string {
  const css = readFileSync(join(process.cwd(), 'src/app/shared/ui/cartao/cartao.component.css'), 'utf-8');
  const blocos = [...css.matchAll(/([^{}]+)\{([^}]*)\}/g)].filter(([, seletores]) =>
    seletores.split(',').some((s) => s.trim() === seletor),
  );
  return blocos.map(([, , corpo]) => corpo).join('\n');
}

describe('CartaoComponent', () => {
  it('com título, mostra o cabeçalho com o título num h2 e as ações projetadas', () => {
    const { cartao } = criar();

    const cabecalho = cartao.querySelector('.cartao-cabecalho');
    expect(cabecalho?.querySelector('h2.cartao-titulo')?.textContent?.trim()).toBe('Dados gerais');
    expect(cabecalho?.querySelector('.acao-teste')).not.toBeNull();
    expect(cartao.querySelector('.cartao-conteudo .conteudo-teste')).not.toBeNull();
  });

  it('o cabeçalho tem o divisor suave e o título fica em maiúsculas', () => {
    expect(regra('.cartao-cabecalho')).toContain('border-bottom: var(--borda-largura) solid var(--cor-borda-suave)');
    expect(regra('.cartao-titulo')).toContain('text-transform: uppercase');
    expect(regra('.cartao-titulo')).toContain('letter-spacing: 0.6px');
  });

  it('sem título, não mostra o cabeçalho', () => {
    const { fixture, cartao, anfitriao } = criar();
    anfitriao.titulo.set(undefined);
    fixture.detectChanges();

    expect(cartao.querySelector('.cartao-cabecalho')).toBeNull();
    expect(cartao.querySelector('.conteudo-teste')).not.toBeNull();
  });

  it('é normal, com espaçamento, por omissão, e o normal não tem sombra', () => {
    const { cartao } = criar();

    expect(cartao.classList).toContain('cartao-normal');
    expect(cartao.classList).toContain('cartao-espacamento-normal');
    expect(regra(':host')).not.toContain('box-shadow');
  });

  it.each<VarianteCartao>(['elevado', 'indicador'])('a variante %s tem a classe e a --sombra-cartao', (variante) => {
    const { fixture, cartao, anfitriao } = criar();
    anfitriao.variante.set(variante);
    fixture.detectChanges();

    expect(cartao.classList).toContain(`cartao-${variante}`);
    expect(regra(`:host(.cartao-${variante})`)).toContain('box-shadow: var(--sombra-cartao)');
  });

  it('a variante indicador tem a faixa de 4px no topo', () => {
    expect(regra(':host(.cartao-indicador)')).toContain('border-top-width: var(--borda-largura-indicador)');
  });

  it('espacamento="nenhum" tira o padding do conteúdo', () => {
    const { fixture, cartao, anfitriao } = criar();
    anfitriao.espacamento.set('nenhum');
    fixture.detectChanges();

    expect(cartao.classList).toContain('cartao-espacamento-nenhum');
    expect(regra(':host(.cartao-espacamento-nenhum) .cartao-conteudo')).toContain('padding: 0');
  });
});
