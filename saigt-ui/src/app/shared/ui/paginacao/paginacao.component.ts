import { ChangeDetectionStrategy, Component, computed, input, output } from '@angular/core';

export type ItemPaginacao = number | 'reticencias';

const MAX_PAGINAS_SEM_RETICENCIAS = 7;

/** Até 7 páginas mostra todas; acima disso, a primeira, a atual ±1, a última e "…" entre elas. */
export function paginasVisiveis(pagina: number, totalPaginas: number): ItemPaginacao[] {
  if (totalPaginas <= MAX_PAGINAS_SEM_RETICENCIAS) {
    return Array.from({ length: totalPaginas }, (_, indice) => indice + 1);
  }
  const vizinhas = [pagina - 1, pagina, pagina + 1].filter((n) => n > 1 && n < totalPaginas);
  const paginas = [1, ...vizinhas, totalPaginas];
  const itens: ItemPaginacao[] = [];
  paginas.forEach((n, indice) => {
    if (indice > 0 && n - paginas[indice - 1] > 1) {
      itens.push('reticencias');
    }
    itens.push(n);
  });
  return itens;
}

/** `pagina` começa em 1. `mostrados` substitui a contagem quando a página carregada foi filtrada localmente. */
@Component({
  selector: 'app-paginacao',
  templateUrl: './paginacao.component.html',
  styleUrl: './paginacao.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PaginacaoComponent {
  readonly pagina = input.required<number>();
  readonly tamanhoPagina = input.required<number>();
  readonly total = input.required<number>();
  readonly etiquetaItens = input('itens');
  readonly mostrados = input<number | null>(null);
  readonly paginaMudou = output<number>();

  readonly totalPaginas = computed(() => Math.max(1, Math.ceil(this.total() / this.tamanhoPagina())));
  readonly itens = computed(() => paginasVisiveis(this.pagina(), this.totalPaginas()));
  readonly quantidade = computed(
    () => this.mostrados() ?? Math.max(0, Math.min(this.tamanhoPagina(), this.total() - (this.pagina() - 1) * this.tamanhoPagina())),
  );

  irPara(pagina: number): void {
    if (pagina >= 1 && pagina <= this.totalPaginas() && pagina !== this.pagina()) {
      this.paginaMudou.emit(pagina);
    }
  }
}
