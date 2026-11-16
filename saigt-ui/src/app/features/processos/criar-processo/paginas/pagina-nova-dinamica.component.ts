import { ChangeDetectionStrategy, Component, computed, input, output } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { DinamicaDisponivel } from '../../../../models/instrumento.model';
import { CampoTextoComponent } from '../../../../shared/ui/campo-texto/campo-texto.component';
import { ListaOpcoesComponent, OpcaoLista } from '../../../../shared/ui/lista-opcoes/lista-opcoes.component';

@Component({
  selector: 'app-pagina-nova-dinamica',
  imports: [CampoTextoComponent, FormsModule, ListaOpcoesComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrl: './pagina.css',
  template: `
    <h2>Nova Dinâmica</h2>
    <p class="subtitulo">Selecione o tipo de dinâmica e adicione um descritivo identificativo.</p>

    <p class="grupo-rotulo">Dinâmica selecionada</p>
    <app-lista-opcoes legenda="Tipo de dinâmica" [opcoes]="opcoes()" [valor]="valor()" (valorChange)="escolher.emit($event)" />

    <app-campo-texto
      rotulo="Descritivo da Dinâmica"
      ajuda="Até 50 caracteres. Aparece em todos os processos da Dinâmica."
      [obrigatorio]="true"
      [maxlength]="50"
      [ngModel]="descritivo()"
      (ngModelChange)="alterarDescritivo.emit($event)"
    />
  `,
})
export class PaginaNovaDinamicaComponent {
  readonly dinamicas = input.required<readonly DinamicaDisponivel[]>();
  readonly dinamicaId = input<number | null>(null);
  readonly descritivo = input('');
  readonly escolher = output<string | null>();
  readonly alterarDescritivo = output<string>();

  readonly valor = computed(() => (this.dinamicaId() === null ? null : String(this.dinamicaId())));
  readonly opcoes = computed<OpcaoLista[]>(() =>
    this.dinamicas().map((dinamica) => ({ valor: String(dinamica.instrumentoDinamicaConfigId), titulo: dinamica.nomeDinamica })),
  );
}
