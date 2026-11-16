import { Component, Input } from '@angular/core';

/**
 * Marca conteúdo de demonstração, nunca um `*` à mão: `grep app-marcador-estatico` dá o inventário e apagar
 * o componente parte o build onde ainda é usado. Sem flag de ambiente: se o conteúdo é falso, é-o também em produção.
 */
@Component({
  selector: 'app-marcador-estatico',
  standalone: true,
  template: `<span class="marcador-estatico" [attr.title]="titulo()" [attr.aria-label]="titulo()">*</span>`,
  styles: [
    `
      .marcador-estatico {
        color: #f59e0b;
        font-weight: 700;
        cursor: help;
      }
    `,
  ],
})
export class MarcadorEstaticoComponent {
  @Input({ required: true }) motivo!: string;
  /** Omitido quando não há issue própria a apontar. */
  @Input() issue: string | null = null;

  titulo(): string {
    return this.issue
      ? `Conteúdo de demonstração — ${this.motivo} (pendente de ${this.issue})`
      : `Conteúdo de demonstração — ${this.motivo}`;
  }
}
