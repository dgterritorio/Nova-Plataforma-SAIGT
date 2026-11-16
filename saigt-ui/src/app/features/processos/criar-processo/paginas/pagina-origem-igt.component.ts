import { ChangeDetectionStrategy, Component, computed, input, output } from '@angular/core';
import { ListaOpcoesComponent, OpcaoLista } from '../../../../shared/ui/lista-opcoes/lista-opcoes.component';
import { OrigemIgt } from '../criar-processo.tipos';

@Component({
  selector: 'app-pagina-origem-igt',
  imports: [ListaOpcoesComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrl: './pagina.css',
  template: `
    <h2>O IGT é novo ou existente?</h2>
    <p class="subtitulo">IGT selecionado: <strong>{{ tipoCodigo() }}</strong></p>
    <app-lista-opcoes legenda="Origem do IGT" disposicao="grelha" [opcoes]="opcoes()" [valor]="origem()" (valorChange)="aoEscolher($event)" />
    @if (origem() === 'novo') {
      <p class="faixa-dinamica" role="status">
        <svg viewBox="0 0 14 14" fill="none" stroke="currentColor" stroke-width="1.17" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
          <circle cx="7" cy="7" r="5.83" />
          <path d="M7 9.33V7M7 4.67h.01" />
        </svg>
        <span>Vai ser criada uma nova <strong>dinâmica de elaboração</strong></span>
      </p>
    }
  `,
})
export class PaginaOrigemIgtComponent {
  readonly tipoCodigo = input('');
  readonly origem = input<OrigemIgt | null>(null);
  /** Só quem age por uma entidade cria IGT; aos restantes a opção fica desativada. */
  readonly podeNovo = input(true);
  readonly escolher = output<OrigemIgt>();

  readonly opcoes = computed<OpcaoLista[]>(() => [
    {
      valor: 'novo',
      titulo: 'Novo IGT',
      descricao: 'Criar um novo instrumento de gestão territorial e iniciar a Elaboração/Decisão de Elaborar.',
      icone: 'mais',
      desativada: !this.podeNovo(),
    },
    {
      valor: 'existente',
      titulo: 'IGT Existente',
      descricao: 'Selecionar um IGT já criado para a sua entidade e associar ao processo.',
      icone: 'prancheta',
    },
  ]);

  aoEscolher(valor: string | null): void {
    if (valor === 'novo' || valor === 'existente') {
      this.escolher.emit(valor);
    }
  }
}
