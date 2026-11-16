import { ChangeDetectionStrategy, Component, computed, input, output } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { CampoTextoComponent } from '../../../../shared/ui/campo-texto/campo-texto.component';
import { OpcaoSeletor, SeletorComponent } from '../../../../shared/ui/seletor/seletor.component';
import { ItemTerritorio } from '../../../../models/processo.model';

@Component({
  selector: 'app-pagina-novo-igt',
  imports: [CampoTextoComponent, FormsModule, SeletorComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrl: './pagina.css',
  template: `
    <h2>Novo IGT</h2>
    <p class="subtitulo">
      IGT selecionado: <strong>{{ tipoCodigo() }}</strong>. Entidade responsável: <strong>{{ entidade() }}</strong>. O IGT nasce sempre nesta
      entidade.
    </p>
    <app-campo-texto
      rotulo="Designação"
      ajuda="O número do IGT é atribuído automaticamente."
      placeholder="PDM de Lisboa"
      [obrigatorio]="true"
      [maxlength]="255"
      [erro]="erro()"
      [disabled]="aCriar()"
      [ngModel]="designacao()"
      (ngModelChange)="alterarDesignacao.emit($event)"
    />
    <app-seletor
      rotulo="Concelho"
      [obrigatorio]="true"
      [opcoes]="opcoesConcelhos()"
      [erro]="erroConcelho()"
      [disabled]="aCriar()"
      [ngModel]="concelho()"
      (ngModelChange)="alterarConcelho.emit($event)"
    />
  `,
})
export class PaginaNovoIgtComponent {
  readonly tipoCodigo = input('');
  readonly entidade = input('—');
  readonly designacao = input('');
  readonly erro = input<string | null>(null);
  readonly aCriar = input(false);
  readonly alterarDesignacao = output<string>();
  readonly concelhos = input<ItemTerritorio[]>([]);
  readonly concelho = input<string | null>(null);
  readonly erroConcelho = input<string | null>(null);
  readonly alterarConcelho = output<string | null>();

  readonly opcoesConcelhos = computed<OpcaoSeletor[]>(() =>
    this.concelhos().map((item) => ({ valor: item.codigo, rotulo: item.nome ?? item.codigo })),
  );
}
