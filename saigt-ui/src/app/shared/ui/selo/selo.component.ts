import { ChangeDetectionStrategy, Component, input } from '@angular/core';

export type TomSelo = 'neutro' | 'info' | 'aviso' | 'sucesso' | 'erro' | 'cinza' | 'laranja' | 'violeta' | 'ciano';
export type FormaSelo = 'retangulo' | 'pill';
export type TamanhoSelo = 'normal' | 'pequeno';

@Component({
  selector: 'app-selo',
  templateUrl: './selo.component.html',
  styleUrl: './selo.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: {
    '[class]': "'selo selo-' + tom() + ' selo-' + forma() + ' selo-' + tamanho()",
  },
})
export class SeloComponent {
  readonly tom = input<TomSelo>('neutro');
  readonly forma = input<FormaSelo>('retangulo');
  /** Pequeno: o estado em maiúsculas das listas laterais (workflows). */
  readonly tamanho = input<TamanhoSelo>('normal');
}
