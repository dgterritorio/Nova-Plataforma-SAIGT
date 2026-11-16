import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import type { TomSelo } from '../../shared/ui/selo/selo.component';
import type { IconeNotificacao } from './apresentacao-notificacao';

@Component({
  selector: 'app-icone-notificacao',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: {
    'aria-hidden': 'true',
    '[class]': "mosaico() ? 'mosaico mosaico-' + tom() + (grande() ? ' mosaico-grande' : '') : 'icone-solto'",
  },
  styles: `
    :host {
      display: inline-flex;
      align-items: center;
      justify-content: center;
      flex-shrink: 0;
      width: 32px;
      height: 32px;
      border-radius: var(--raio-md);
    }

    :host(.mosaico-grande) {
      width: 36px;
      height: 36px;
    }

    :host(.icone-solto) {
      width: 10px;
      height: 10px;
      border-radius: 0;
    }

    svg {
      width: 16px;
      height: 16px;
    }

    :host(.mosaico-grande) svg {
      width: 18px;
      height: 18px;
    }

    :host(.icone-solto) svg {
      width: 10px;
      height: 10px;
    }

    :host(.mosaico-erro) { background: var(--cor-erro-fundo); color: var(--cor-selo-erro); }
    :host(.mosaico-cinza) { background: var(--cor-neutra-fundo); color: var(--cor-neutra); }
    :host(.mosaico-info) { background: var(--cor-info-fundo); color: var(--cor-info); }
    :host(.mosaico-violeta) { background: var(--cor-selo-violeta-fundo); color: var(--cor-selo-violeta); }
    :host(.mosaico-ciano) { background: var(--cor-selo-ciano-fundo); color: var(--cor-selo-ciano); }
    :host(.mosaico-sucesso) { background: var(--cor-selo-sucesso-fundo); color: var(--cor-selo-sucesso); }
    :host(.mosaico-aviso) { background: var(--cor-selo-aviso-fundo); color: var(--cor-aviso); }
  `,
  template: `
    <svg viewBox="0 0 16 16" fill="none" stroke="currentColor" stroke-width="1.33" stroke-linecap="round" stroke-linejoin="round">
      @switch (simbolo()) {
        @case ('triangulo') {
          <path d="M8 2.2L1.6 13.3h12.8L8 2.2z" />
          <path d="M8 6.5v3M8 11.5h.01" />
        }
        @case ('relogio') {
          <circle cx="8" cy="8" r="6" />
          <path d="M8 4.7V8l2.2 1.4" />
        }
        @case ('megafone') {
          <path d="M2.5 6.5v3h2l5 3v-9l-5 3h-2z" />
          <path d="M12 6a2.5 2.5 0 010 4" />
        }
        @case ('balao') {
          <path d="M13.5 10a1.3 1.3 0 01-1.3 1.3H5.3L2.5 13.8V3.8a1.3 1.3 0 011.3-1.3h8.4a1.3 1.3 0 011.3 1.3V10z" />
        }
        @case ('documento') {
          <path d="M9.3 1.8H4.2a1.2 1.2 0 00-1.2 1.2v10a1.2 1.2 0 001.2 1.2h7.6a1.2 1.2 0 001.2-1.2V5.5L9.3 1.8z" />
          <path d="M9.3 1.8v3.7H13M5.5 8.5h5M5.5 11h5" />
        }
      }
    </svg>
  `,
})
export class IconeNotificacaoComponent {
  readonly simbolo = input.required<IconeNotificacao>();
  readonly tom = input<TomSelo>('cinza');
  readonly grande = input(false);
  readonly mosaico = input(true);
}
