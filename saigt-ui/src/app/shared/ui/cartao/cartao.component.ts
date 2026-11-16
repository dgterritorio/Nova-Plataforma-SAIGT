import { ChangeDetectionStrategy, Component, input } from '@angular/core';

/** `indicador`: o cartão elevado com a faixa de 4px no topo dos cartões de resumo dos painéis. */
export type VarianteCartao = 'normal' | 'elevado' | 'indicador';
export type EspacamentoCartao = 'normal' | 'nenhum';

@Component({
  selector: 'app-cartao',
  templateUrl: './cartao.component.html',
  styleUrl: './cartao.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: {
    '[class]': "'cartao cartao-' + variante() + ' cartao-espacamento-' + espacamento()",
  },
})
export class CartaoComponent {
  readonly titulo = input<string>();
  readonly variante = input<VarianteCartao>('normal');
  readonly espacamento = input<EspacamentoCartao>('normal');
}
