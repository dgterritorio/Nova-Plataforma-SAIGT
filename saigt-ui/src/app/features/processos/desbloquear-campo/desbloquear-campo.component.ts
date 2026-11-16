import { ChangeDetectionStrategy, Component, computed, inject, input } from '@angular/core';
import { DesbloqueioCampos } from './desbloqueio-campos';

/**
 * A ação «Desbloquear» junto do aviso de um campo bloqueado. Sem contexto, ou sem `desbloquear-campo` nas ações do
 * processo, não mostra nada: a API recusa o desbloqueio a quem não é o autor de um processo em correção.
 */
@Component({
  selector: 'app-desbloquear-campo',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (disponivel()) {
      <button type="button" class="desbloquear" [attr.aria-label]="'Desbloquear ' + rotulo()" (click)="pedir($event)">
        Desbloquear
      </button>
    }
  `,
  styles: `
    :host {
      display: block;
    }

    .desbloquear {
      padding: 0;
      border: 0;
      background: none;
      font: inherit;
      font-size: var(--texto-sm);
      line-height: var(--texto-sm-linha);
      font-weight: var(--peso-medio);
      color: var(--cor-primaria);
      text-decoration: underline;
      cursor: pointer;
    }

    .desbloquear:hover {
      color: var(--cor-primaria-hover);
    }

    .desbloquear:focus-visible {
      outline: 2px solid var(--cor-primaria);
      outline-offset: 2px;
    }
  `,
})
export class DesbloquearCampoComponent {
  private readonly contexto = inject(DesbloqueioCampos, { optional: true });

  readonly chave = input.required<string>();
  readonly rotulo = input.required<string>();
  readonly disponivel = computed(() => this.contexto?.alvo() != null);

  /** Muitos avisos estão dentro do `<label>` do campo: o clique não deve chegar ao controlo. */
  pedir(evento: Event): void {
    evento.preventDefault();
    this.contexto?.pedir(this.chave(), this.rotulo());
  }
}
