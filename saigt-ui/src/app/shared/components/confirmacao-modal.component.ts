import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';
import { BotaoComponent } from '../ui/botao/botao.component';
import { ModalComponent } from '../ui/modal/modal.component';

let proximoId = 0;

/**
 * A `mensagem` é sempre específica da ação, nunca um texto genérico.
 * Enquanto `aProcessar`, não fecha: o pedido já foi feito e "Não" já não o desfaz.
 */
@Component({
  selector: 'app-confirmacao-modal',
  imports: [BotaoComponent, ModalComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <app-modal
      variante="confirmacao"
      largura="sm"
      [aberto]="aberto()"
      [titulo]="titulo()"
      [fecharComFundo]="!aProcessar()"
      [descritoPor]="idMensagem"
      (fechar)="recusar()"
    >
      <p class="mensagem" [id]="idMensagem">{{ mensagem() }}</p>
      <div rodape>
        <button type="button" app-botao variante="secundario" tamanho="pequeno" class="botao-nao" [desativado]="aProcessar()" (click)="nao.emit()">
          {{ rotuloNao() }}
        </button>
        <button type="button" app-botao tamanho="pequeno" class="botao-sim" [aCarregar]="aProcessar()" (click)="sim.emit()">
          {{ aProcessar() ? 'A processar…' : rotuloSim() }}
        </button>
      </div>
    </app-modal>
  `,
  styles: `
    .mensagem {
      margin: 0;
      font-size: var(--texto-base);
      line-height: var(--texto-base-linha);
      color: var(--cor-texto-rotulo);
    }

    [rodape] {
      display: contents;
    }
  `,
})
export class ConfirmacaoModalComponent {
  readonly aberto = input(false);
  readonly titulo = input.required<string>();
  readonly mensagem = input.required<string>();
  readonly aProcessar = input(false);
  readonly rotuloSim = input('Sim');
  readonly rotuloNao = input('Não');
  readonly sim = output<void>();
  readonly nao = output<void>();

  readonly idMensagem = `confirmacao-mensagem-${++proximoId}`;

  recusar(): void {
    if (!this.aProcessar()) {
      this.nao.emit();
    }
  }
}
