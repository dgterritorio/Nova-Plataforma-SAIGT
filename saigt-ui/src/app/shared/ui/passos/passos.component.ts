import { ChangeDetectionStrategy, Component, input } from '@angular/core';

export interface Passo {
  etiqueta: string;
}

/** Os passos antes de `atual` (0-based) contam como concluídos. */
@Component({
  selector: 'app-passos',
  templateUrl: './passos.component.html',
  styleUrl: './passos.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PassosComponent {
  readonly passos = input.required<readonly Passo[]>();
  readonly atual = input.required<number>();

  estado(indice: number): 'concluido' | 'atual' | 'pendente' {
    const atual = this.atual();
    return indice < atual ? 'concluido' : indice === atual ? 'atual' : 'pendente';
  }
}
