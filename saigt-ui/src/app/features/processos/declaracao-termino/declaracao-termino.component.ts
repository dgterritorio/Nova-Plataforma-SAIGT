import { ChangeDetectionStrategy, Component, ElementRef, effect, input, output, viewChild } from '@angular/core';
import { DinamicaATerminar } from '../../../models/processo.model';

/** BL-PROC-040: na Revogação do IGT com outras Dinâmicas em curso, a submissão exige esta declaração (FR-042). */
@Component({
  selector: 'app-declaracao-termino',
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrl: './declaracao-termino.component.css',
  template: `
    <fieldset class="declaracao" [class.com-erro]="erro()">
      <legend>Declaração de término</legend>
      <p class="introducao" id="declaracao-termino-lista">Procedimentos em curso no plano a revogar:</p>
      <ul class="lista" aria-labelledby="declaracao-termino-lista">
        @for (dinamica of dinamicas(); track dinamica.dinamicaId) {
          <li>{{ descricao(dinamica) }}</li>
        }
      </ul>
      <label class="caixa">
        <input
          #caixa
          id="declaracao-termino"
          type="checkbox"
          required
          [checked]="declarado()"
          [attr.aria-invalid]="erro() ? 'true' : null"
          [attr.aria-describedby]="erro() ? 'erro-declaracao-termino' : null"
          (change)="aoAlternar($event)"
        />
        <span>Declaro que a revogação do plano termina também os procedimentos em curso acima indicados.</span>
      </label>
      @if (erro(); as mensagem) {
        <p class="erro-campo" id="erro-declaracao-termino" role="alert">{{ mensagem }}</p>
      }
    </fieldset>
  `,
})
export class DeclaracaoTerminoComponent {
  readonly dinamicas = input.required<DinamicaATerminar[]>();
  readonly declarado = input(false);
  readonly erro = input<string | null>(null);
  readonly alterado = output<boolean>();

  private readonly caixa = viewChild<ElementRef<HTMLInputElement>>('caixa');

  constructor() {
    effect(() => {
      const caixa = this.caixa();
      if (this.erro() && caixa) {
        caixa.nativeElement.focus();
      }
    });
  }

  descricao(dinamica: DinamicaATerminar): string {
    return [dinamica.numeroDinamica, dinamica.tipoDinamica, dinamica.descritivo].filter((parte) => !!parte).join(' · ');
  }

  aoAlternar(evento: Event): void {
    this.alterado.emit((evento.target as HTMLInputElement).checked);
  }
}
