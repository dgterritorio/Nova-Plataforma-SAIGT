import { ChangeDetectionStrategy, Component, computed, input, output } from '@angular/core';
import { ListaOpcoesComponent, OpcaoLista } from '../../../../shared/ui/lista-opcoes/lista-opcoes.component';
import { Modo } from '../criar-processo.tipos';

@Component({
  selector: 'app-pagina-acao',
  imports: [ListaOpcoesComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrl: './pagina.css',
  template: `
    <h2>O que pretende fazer?</h2>
    <p class="subtitulo">Escolha uma opção para continuar com <strong>{{ designacao() }}</strong>.</p>
    <app-lista-opcoes
      legenda="Ação"
      disposicao="grelha"
      [opcoes]="opcoes()"
      [valor]="modo()"
      (valorChange)="aoEscolher($event)"
    />
  `,
})
export class PaginaAcaoComponent {
  readonly designacao = input('');
  readonly modo = input<Modo | null>(null);
  readonly podeNova = input(false);
  readonly podeContinuar = input(false);
  readonly escolher = output<Modo>();

  /** Só as disponíveis, como no Figma; sem nenhuma, as duas aparecem desativadas para se perceber porquê. */
  readonly opcoes = computed<OpcaoLista[]>(() => {
    const todas = this.todasAsOpcoes();
    const disponiveis = todas.filter((opcao) => !opcao.desativada);
    return disponiveis.length > 0 ? disponiveis : todas;
  });

  private readonly todasAsOpcoes = computed<OpcaoLista[]>(() => [
    {
      valor: 'nova',
      titulo: 'Criar Nova Dinâmica',
      descricao: 'Iniciar uma nova dinâmica para este IGT.',
      icone: 'mais',
      desativada: !this.podeNova(),
    },
    {
      valor: 'continuar',
      titulo: 'Continuar Fase da Dinâmica',
      descricao: 'Prosseguir com uma dinâmica em aberto.',
      icone: 'seta-dupla',
      desativada: !this.podeContinuar(),
    },
  ]);

  aoEscolher(valor: string | null): void {
    if (valor === 'nova' || valor === 'continuar') {
      this.escolher.emit(valor);
    }
  }
}
