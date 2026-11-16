import { TestBed } from '@angular/core/testing';
import { describe, expect, it } from 'vitest';
import { PaginacaoComponent, paginasVisiveis } from './paginacao.component';

function criar(pagina: number, total: number, tamanhoPagina = 10, mostrados: number | null = null) {
  const fixture = TestBed.createComponent(PaginacaoComponent);
  fixture.componentRef.setInput('pagina', pagina);
  fixture.componentRef.setInput('total', total);
  fixture.componentRef.setInput('tamanhoPagina', tamanhoPagina);
  fixture.componentRef.setInput('etiquetaItens', 'procedimentos');
  fixture.componentRef.setInput('mostrados', mostrados);
  fixture.detectChanges();
  const emitidas: number[] = [];
  fixture.componentInstance.paginaMudou.subscribe((n) => emitidas.push(n));
  const elemento: HTMLElement = fixture.nativeElement;
  const botoes = () => [...elemento.querySelectorAll('button')] as HTMLButtonElement[];
  const textos = () => [...elemento.querySelectorAll('.paginacao-numero, .paginacao-reticencias')].map((e) => e.textContent?.trim());
  return { elemento, botoes, textos, emitidas };
}

describe('paginasVisiveis', () => {
  it('mostra todas as páginas até 7', () => {
    expect(paginasVisiveis(3, 7)).toEqual([1, 2, 3, 4, 5, 6, 7]);
  });

  it('acima de 7 mostra a primeira, a atual ±1, a última e reticências', () => {
    expect(paginasVisiveis(5, 10)).toEqual([1, 'reticencias', 4, 5, 6, 'reticencias', 10]);
    expect(paginasVisiveis(1, 10)).toEqual([1, 2, 'reticencias', 10]);
    expect(paginasVisiveis(10, 10)).toEqual([1, 'reticencias', 9, 10]);
    expect(paginasVisiveis(3, 10)).toEqual([1, 2, 3, 4, 'reticencias', 10]);
  });
});

describe('PaginacaoComponent', () => {
  it('com 95 itens, 10 por página, na página 5 mostra 1 … 4 5 6 … 10 e "Mostrando 10 de 95"', () => {
    const { elemento, textos } = criar(5, 95);
    expect(textos()).toEqual(['1', '…', '4', '5', '6', '…', '10']);
    const atual = elemento.querySelector('[aria-current="page"]');
    expect(atual?.textContent?.trim()).toBe('5');
    expect(elemento.textContent).toContain('Mostrando 10 de 95 procedimentos');
  });

  it('na última página conta só os itens que lá estão', () => {
    expect(criar(10, 95).elemento.textContent).toContain('Mostrando 5 de 95 procedimentos');
  });

  it('usa a contagem indicada quando a página foi filtrada localmente', () => {
    expect(criar(1, 95, 10, 3).elemento.textContent).toContain('Mostrando 3 de 95 procedimentos');
  });

  it('na primeira página "Anterior" está desativado e na última "Próximo" também', () => {
    expect(criar(1, 95).botoes()[0].disabled).toBe(true);
    const ultima = criar(10, 95).botoes();
    expect(ultima[ultima.length - 1].disabled).toBe(true);
  });

  it('clicar num número, em Anterior ou em Próximo emite paginaMudou', () => {
    const { botoes, emitidas } = criar(5, 95);
    botoes().find((b) => b.textContent?.trim() === '6')!.click();
    botoes()[0].click();
    botoes()[botoes().length - 1].click();
    expect(emitidas).toEqual([6, 4, 6]);
  });

  it('não emite ao clicar na página atual', () => {
    const { elemento, emitidas } = criar(5, 95);
    (elemento.querySelector('[aria-current="page"]') as HTMLButtonElement).click();
    expect(emitidas).toEqual([]);
  });

  it('fica dentro de um nav com nome acessível', () => {
    expect(criar(1, 5).elemento.querySelector('nav')?.getAttribute('aria-label')).toBe('Paginação');
  });
});
