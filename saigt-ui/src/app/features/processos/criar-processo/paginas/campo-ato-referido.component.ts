import { ChangeDetectionStrategy, Component, LOCALE_ID, computed, inject, input, output } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { AtoReferivel } from '../../../../models/instrumento.model';
import { OpcaoSeletor, SeletorComponent } from '../../../../shared/ui/seletor/seletor.component';
import { AJUDA_ATO_REFERIDO, rotuloAtoReferivel } from '../../ato-referido';

/** O ato publicado que um dos «Outros atos e anexos» visa; a API volta a validá-lo na criação. */
@Component({
  selector: 'app-campo-ato-referido',
  imports: [FormsModule, SeletorComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrl: './pagina.css',
  template: `
    <app-seletor
      rotulo="Ato publicado a que respeita"
      [obrigatorio]="true"
      [opcoes]="opcoes()"
      [ajuda]="ajuda"
      [erro]="erro()"
      [ngModel]="valor()"
      (ngModelChange)="escolher.emit($event)"
    />
  `,
})
export class CampoAtoReferidoComponent {
  private readonly localizacao = inject(LOCALE_ID);

  readonly atos = input.required<readonly AtoReferivel[]>();
  readonly valor = input<string | null>(null);
  /** Nome de cada tipo de Procedimento pelo código, quando se conhece. */
  readonly nomesTipos = input<Readonly<Record<string, string>>>({});
  readonly erro = input<string | null>(null);
  readonly escolher = output<string | null>();

  readonly ajuda = AJUDA_ATO_REFERIDO;
  readonly opcoes = computed<OpcaoSeletor[]>(() =>
    this.atos().map((ato) => ({ valor: ato.processoId, rotulo: rotuloAtoReferivel(ato, this.localizacao, this.nomesTipos()) })),
  );
}
