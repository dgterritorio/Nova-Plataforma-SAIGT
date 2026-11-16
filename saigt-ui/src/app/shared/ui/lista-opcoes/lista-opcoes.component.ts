import { ChangeDetectionStrategy, Component, input, model } from '@angular/core';
import { SeloComponent } from '../selo/selo.component';

export type IconeOpcao = 'mais' | 'prancheta' | 'seta-dupla';

export interface OpcaoLista {
  valor: string;
  titulo: string;
  descricao?: string;
  /** A descrição é um texto do utilizador (ex.: o descritivo da Dinâmica): mostra-se entre aspas, em itálico. */
  citacao?: boolean;
  /** Linha de texto simples por baixo da descrição (ex.: os Instrumentos de uma Dinâmica). */
  detalhe?: string;
  /** Linha de rótulo e valor destacado por baixo da descrição (ex.: Último concluído). */
  nota?: { rotulo: string; valor: string; tom: 'sucesso' | 'aviso' };
  /**
   * Linha de prazo dentro do rótulo do radio, para o leitor de ecrã a anunciar com a opção (ex.: Caduca em).
   * `datetime` em `yyyy-MM-dd`; o `selo`, se houver, sai em tom de aviso.
   */
  prazo?: { rotulo: string; data: string; datetime: string; selo?: string };
  /** Data no canto superior direito, em monoespaçada. */
  data?: string;
  /** Código curto à esquerda do título, em monoespaçada (ex.: `PDM`). */
  etiqueta?: string;
  /** Estado à direita do título. */
  selo?: string;
  icone?: IconeOpcao;
  desativada?: boolean;
}

let proximoId = 0;

/** Grupo de `<input type="radio">` nativos apresentados como cartões: o teclado e o leitor de ecrã já sabem usá-los. */
@Component({
  selector: 'app-lista-opcoes',
  imports: [SeloComponent],
  templateUrl: './lista-opcoes.component.html',
  styleUrl: './lista-opcoes.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ListaOpcoesComponent {
  readonly legenda = input.required<string>();
  readonly opcoes = input.required<readonly OpcaoLista[]>();
  /** `grelha` põe as opções em duas colunas, com o ícone por cima (Novo IGT / IGT Existente). */
  readonly disposicao = input<'lista' | 'grelha'>('lista');
  readonly desativado = input(false);
  readonly valor = model<string | null>(null);

  readonly nome = `lista-opcoes-${++proximoId}`;
}
