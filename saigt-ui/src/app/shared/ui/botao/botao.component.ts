import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

export type VarianteBotao = 'primario' | 'secundario' | 'suave' | 'contorno' | 'texto' | 'perigo';
export type TamanhoBotao = 'normal' | 'pequeno';

/** Seletor de atributo para manter o `<button>`/`<a>` nativo: foco, teclado e semântica vêm do browser. */
@Component({
  // eslint-disable-next-line @angular-eslint/component-selector -- seletor de atributo, prefixado com app-
  selector: 'button[app-botao], a[app-botao]',
  templateUrl: './botao.component.html',
  styleUrl: './botao.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: {
    '[class]': "'botao botao-' + variante() + ' botao-' + tamanho()",
    '[class.botao-a-carregar]': 'aCarregar()',
    '[attr.disabled]': 'inativo() || null',
    '[attr.aria-disabled]': 'inativo() || null',
    '[attr.aria-busy]': 'aCarregar() || null',
    '[attr.tabindex]': 'inativo() ? -1 : null',
    '(click)': 'impedirSeInativo($event)',
  },
})
export class BotaoComponent {
  readonly variante = input<VarianteBotao>('primario');
  readonly tamanho = input<TamanhoBotao>('normal');
  readonly desativado = input(false);
  readonly aCarregar = input(false);

  readonly inativo = computed(() => this.desativado() || this.aCarregar());

  /** Num `<a>` o atributo `disabled` não impede a navegação. */
  impedirSeInativo(evento: Event): void {
    if (this.inativo()) {
      evento.preventDefault();
      evento.stopImmediatePropagation();
    }
  }
}
