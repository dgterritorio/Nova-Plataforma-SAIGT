import { Component, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { readFileSync } from 'node:fs';
import { join } from 'node:path';
import { describe, expect, it } from 'vitest';
import { CelulaTabelaDirective } from './celula-tabela.directive';
import { ColunaTabela, FamiliaTabela, TabelaComponent } from './tabela.component';

interface Linha {
  id: string;
  nome: string;
  estado: string;
}

const COLUNAS: ColunaTabela[] = [
  { chave: 'nome', titulo: 'Nome' },
  { chave: 'estado', titulo: 'Estado', alinhamento: 'direita' },
];

@Component({
  imports: [TabelaComponent, CelulaTabelaDirective],
  template: `
    <app-tabela
      [colunas]="colunas"
      [linhas]="linhas()"
      [familia]="familia()"
      [aCarregar]="aCarregar()"
      [clicavel]="clicavel()"
      [larguraMinima]="larguraMinima()"
      [legenda]="legenda()"
      [linhaDestacada]="destacar"
      mensagemVazia="Nada para mostrar."
      (linhaClicada)="clicadas.push($event)"
    >
      <ng-template appCelula="estado" let-linha><strong class="estado-personalizado">{{ linha.estado }}!</strong></ng-template>
      <p rodape class="rodape-teste">rodapé</p>
    </app-tabela>
  `,
})
class AnfitriaoComponent {
  readonly colunas = COLUNAS;
  readonly linhas = signal<Linha[]>([
    { id: '1', nome: 'Sintra', estado: 'ok' },
    { id: '2', nome: 'Cascais', estado: 'atrasado' },
  ]);
  readonly familia = signal<FamiliaTabela>('listagem');
  readonly aCarregar = signal(false);
  readonly clicavel = signal(false);
  readonly larguraMinima = signal<string | null>(null);
  readonly legenda = signal<string | null>(null);
  readonly destacar = (linha: Linha) => linha.estado === 'atrasado';
  readonly clicadas: Linha[] = [];
}

function criar() {
  const fixture = TestBed.createComponent(AnfitriaoComponent);
  fixture.detectChanges();
  const tabela: HTMLElement = fixture.nativeElement.querySelector('app-tabela');
  return { fixture, tabela, anfitriao: fixture.componentInstance };
}

describe('TabelaComponent', () => {
  it('usa uma tabela semântica com os títulos em th scope="col"', () => {
    const { tabela } = criar();
    const titulos = [...tabela.querySelectorAll('thead th')];
    expect(titulos.map((th) => th.textContent?.trim())).toEqual(['Nome', 'Estado']);
    expect(titulos.every((th) => th.getAttribute('scope') === 'col')).toBe(true);
  });

  it('só tem caption quando recebe uma legenda', () => {
    const { fixture, tabela, anfitriao } = criar();
    expect(tabela.querySelector('caption')).toBeNull();

    anfitriao.legenda.set('Elementos rejeitados');
    fixture.detectChanges();

    expect(tabela.querySelector('table > caption')?.textContent?.trim()).toBe('Elementos rejeitados');
  });

  it('é da família listagem por omissão e compacta quando pedido', () => {
    const { fixture, tabela, anfitriao } = criar();
    expect(tabela.classList).toContain('tabela-listagem');

    anfitriao.familia.set('compacta');
    fixture.detectChanges();
    expect(tabela.classList).toContain('tabela-compacta');
  });

  it('a relatório é uma compacta com o cabeçalho do relatório', () => {
    const { fixture, anfitriao } = criar();
    anfitriao.familia.set('relatorio');
    fixture.detectChanges();
    const tabela: HTMLElement = fixture.nativeElement.querySelector('app-tabela');
    expect([...tabela.classList]).toEqual(expect.arrayContaining(['tabela-relatorio', 'tabela-compacta']));
    const css = readFileSync(join(process.cwd(), 'src/app/shared/ui/tabela/tabela.component.css'), 'utf-8');
    expect(css).toMatch(
      /:host\(\.tabela-relatorio\) th \{[^}]*background: var\(--cor-cabecalho-tabela-relatorio\);[^}]*color: var\(--cor-rotulo-indicador\);/,
    );
  });

  it('a compacta não tem moldura própria e o cabeçalho usa o cinza acessível', () => {
    // O jsdom não resolve variáveis CSS: confirma-se no CSS do componente.
    const css = readFileSync(join(process.cwd(), 'src/app/shared/ui/tabela/tabela.component.css'), 'utf-8');
    expect(css).toMatch(/:host\(\.tabela-compacta\) \{\s*border: 0;\s*border-radius: 0;/);
    expect(css).toMatch(/:host\(\.tabela-compacta\) th \{[^}]*color: var\(--cor-texto-nav\);[^}]*text-transform: uppercase;/);
  });

  it('mostra o valor da linha e usa o ng-template appCelula quando existe', () => {
    const { tabela } = criar();
    const primeira = tabela.querySelectorAll('tbody tr')[0];
    expect(primeira.querySelector('td.coluna-nome')?.textContent?.trim()).toBe('Sintra');
    expect(primeira.querySelector('td.coluna-estado .estado-personalizado')?.textContent).toBe('ok!');
    expect(primeira.querySelector('td.coluna-estado')?.classList).toContain('alinhar-direita');
  });

  it('só fixa uma largura mínima quando pedida, para a tabela deslizar em vez de encolher', () => {
    const { fixture, tabela, anfitriao } = criar();
    const elemento = tabela.querySelector('table') as HTMLElement;
    expect(elemento.style.minWidth).toBe('');

    anfitriao.larguraMinima.set('720px');
    fixture.detectChanges();

    expect(elemento.style.minWidth).toBe('720px');
  });

  it('destaca as linhas indicadas por linhaDestacada', () => {
    const { tabela } = criar();
    const linhas = tabela.querySelectorAll('tbody tr');
    expect(linhas[0].classList).not.toContain('tabela-linha-destacada');
    expect(linhas[1].classList).toContain('tabela-linha-destacada');
  });

  it('a carregar mostra 5 linhas esqueleto', () => {
    const { fixture, tabela, anfitriao } = criar();
    anfitriao.aCarregar.set(true);
    fixture.detectChanges();
    expect(tabela.querySelectorAll('tbody tr.tabela-esqueleto')).toHaveLength(5);
    expect(tabela.querySelector('table')?.getAttribute('aria-busy')).toBe('true');
  });

  it('sem linhas mostra a mensagem vazia numa célula que ocupa todas as colunas', () => {
    const { fixture, tabela, anfitriao } = criar();
    anfitriao.linhas.set([]);
    fixture.detectChanges();
    const vazio = tabela.querySelector('td.tabela-vazio');
    expect(vazio?.textContent?.trim()).toBe('Nada para mostrar.');
    expect(vazio?.getAttribute('colspan')).toBe('2');
  });

  it('só emite linhaClicada, por clique ou Enter, quando é clicável', () => {
    const { fixture, tabela, anfitriao } = criar();
    const linha = () => tabela.querySelectorAll('tbody tr')[0] as HTMLTableRowElement;

    linha().click();
    expect(anfitriao.clicadas).toEqual([]);
    expect(linha().getAttribute('tabindex')).toBeNull();

    anfitriao.clicavel.set(true);
    fixture.detectChanges();
    linha().click();
    linha().dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter' }));

    expect(linha().getAttribute('tabindex')).toBe('0');
    expect(anfitriao.clicadas.map((l) => l.id)).toEqual(['1', '1']);
  });

  it('projeta o rodapé dentro do cartão', () => {
    const { tabela } = criar();
    expect(tabela.querySelector('.rodape-teste')).not.toBeNull();
  });
});
