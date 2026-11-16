import { ChangeDetectionStrategy, Component, ElementRef, EventEmitter, Input, OnChanges, Output, SimpleChanges, computed, inject, signal } from '@angular/core';
import { DestinoProcesso, ROTULO_DESTINO } from '../../../models/processo.model';

let proximoId = 0;

export const AJUDA_DESTINO =
  'Indique-o antes de carregar os ficheiros. Um processo só para depósito não é publicado no Diário da República.';
export const AJUDA_DESTINO_COM_FICHEIROS = 'Para mudar o destino, remova primeiro os ficheiros carregados.';

/**
 * «Destino do processo» (TR-227): publicação e depósito ou só depósito. Só emite a escolha; quem grava é o ecrã.
 * Os botões de opção partilham o `name`, por isso as setas mudam a opção como em qualquer grupo nativo.
 */
@Component({
  selector: 'app-destino-processo',
  standalone: true,
  templateUrl: './destino-processo.component.html',
  styleUrl: './destino-processo.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DestinoProcessoComponent implements OnChanges {
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);

  @Input({ required: true }) valor: DestinoProcesso = 'PUBLICACAO_DEPOSITO';
  @Input() editavel = false;
  /** Sem edição e com ficheiros, diz como voltar a poder mudar. */
  @Input() comFicheiros = false;
  /** Recusa do servidor (400/409), ligada ao grupo por `aria-describedby`. */
  @Input() erro: string | null = null;
  /** Enquanto grava, outra escolha é ignorada; as opções não se desativam para o foco não sair do grupo. */
  @Input() aGravar = false;
  @Output() readonly alterado = new EventEmitter<DestinoProcesso>();

  readonly id = `destino-processo-${++proximoId}`;
  readonly idAjuda = `${this.id}-ajuda`;
  readonly idErro = `${this.id}-erro`;
  readonly ajuda = AJUDA_DESTINO;
  readonly ajudaComFicheiros = AJUDA_DESTINO_COM_FICHEIROS;
  readonly opcoes: DestinoProcesso[] = ['PUBLICACAO_DEPOSITO', 'SO_DEPOSITO'];
  readonly rotulos = ROTULO_DESTINO;

  /** A opção marcada: volta ao gravado quando o valor ou o erro mudam (uma gravação recusada não fica marcada). */
  readonly escolhido = signal<DestinoProcesso>('PUBLICACAO_DEPOSITO');
  private readonly comErro = signal(false);
  private readonly comAjuda = signal(true);

  readonly descritoPor = computed(() =>
    [this.comAjuda() ? this.idAjuda : null, this.comErro() ? this.idErro : null].filter(Boolean).join(' ') || null,
  );

  ngOnChanges(changes: SimpleChanges): void {
    if ('valor' in changes || 'erro' in changes) {
      this.escolhido.set(this.valor);
      this.marcarNoEcra();
    }
    this.comErro.set(!!this.erro);
    this.comAjuda.set(this.editavel || this.comFicheiros);
  }

  /**
   * Uma recusa pode chegar no mesmo ciclo do clique: a ligação `[checked]` não vê a mudança e o browser deixava marcada
   * a opção recusada.
   */
  private marcarNoEcra(): void {
    for (const campo of Array.from(this.host.nativeElement.querySelectorAll<HTMLInputElement>('input[type="radio"]'))) {
      campo.checked = campo.value === this.escolhido();
    }
  }

  escolher(destino: DestinoProcesso): void {
    if (!this.editavel || this.aGravar || destino === this.escolhido()) {
      this.marcarNoEcra();
      return;
    }
    this.escolhido.set(destino);
    this.alterado.emit(destino);
  }
}
