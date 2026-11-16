import { NgTemplateOutlet } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, contentChildren, input, output } from '@angular/core';
import { CelulaTabelaDirective } from './celula-tabela.directive';

export interface ColunaTabela {
  chave: string;
  titulo: string;
  largura?: string;
  alinhamento?: 'esquerda' | 'direita' | 'centro';
}

export type FamiliaTabela = 'listagem' | 'compacta' | 'relatorio';

const LINHAS_ESQUELETO = [0, 1, 2, 3, 4];

/**
 * `clicavel` liga o hover, o cursor e o Enter nas linhas: um `output()` não diz se alguém o subscreveu.
 * O rodapé (paginação) entra por `<… rodape>`, dentro do cartão.
 */
@Component({
  selector: 'app-tabela',
  imports: [NgTemplateOutlet],
  templateUrl: './tabela.component.html',
  styleUrl: './tabela.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { '[class]': "'tabela tabela-' + familia() + (familia() === 'relatorio' ? ' tabela-compacta' : '')" },
})
export class TabelaComponent<T extends object> {
  readonly colunas = input.required<ColunaTabela[]>();
  readonly linhas = input.required<readonly T[]>();
  readonly familia = input<FamiliaTabela>('listagem');
  readonly aCarregar = input(false);
  readonly mensagemVazia = input('Sem resultados.');
  readonly legenda = input<string | null>(null);
  /** Abaixo desta largura a tabela deixa de encolher e desliza na horizontal. */
  readonly larguraMinima = input<string | null>(null);
  readonly linhaDestacada = input<(linha: T) => boolean>(() => false);
  readonly clicavel = input(false);
  readonly linhaClicada = output<T>();

  readonly linhasEsqueleto = LINHAS_ESQUELETO;
  private readonly celulas = contentChildren(CelulaTabelaDirective);
  readonly templates = computed(() => new Map(this.celulas().map((celula) => [celula.chave(), celula.template])));

  valor(linha: T, chave: string): unknown {
    return (linha as Record<string, unknown>)[chave];
  }

  clicar(linha: T): void {
    if (this.clicavel()) {
      this.linhaClicada.emit(linha);
    }
  }
}
