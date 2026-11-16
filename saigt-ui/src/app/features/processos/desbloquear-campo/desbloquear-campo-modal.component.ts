import { ChangeDetectionStrategy, Component, computed, effect, inject, signal, untracked } from '@angular/core';
import { ApiError } from '../../../core/models/error-response.model';
import { ProcessoService } from '../../../services/processo.service';
import { AlertaComponent } from '../../../shared/ui/alerta/alerta.component';
import { BotaoComponent } from '../../../shared/ui';
import { ModalComponent } from '../../../shared/ui/modal/modal.component';
import { DesbloqueioCampos } from './desbloqueio-campos';

const MENSAGEM_SEM_RESPOSTA = 'Não foi possível concluir a operação. Tente novamente.';
const CAMPO_MOTIVO = 'motivo';

/**
 * Pede o motivo do desbloqueio e chama o `PATCH`. Um 400 fica junto da caixa do motivo; um 409 (estado, `versao`)
 * mostra a mensagem do servidor. Fica no ecrã que fornece {@link DesbloqueioCampos}, fora dos `<label>` dos campos.
 */
@Component({
  selector: 'app-desbloquear-campo-modal',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ModalComponent, BotaoComponent, AlertaComponent],
  template: `
    <app-modal titulo="Desbloquear campo" [aberto]="pendente() !== null" (fechar)="cancelar()">
      @if (pendente(); as pedido) {
        <div class="desbloqueio">
          <p class="desbloqueio-campo">Campo: <strong>{{ pedido.rotulo }}</strong></p>
          @if (erroGeral(); as mensagem) {
            <app-alerta tipo="erro">{{ mensagem }}</app-alerta>
          }
          <label class="rotulo" for="motivo-desbloqueio-campo">Motivo <span class="obrigatorio" aria-hidden="true">*</span></label>
          <textarea
            id="motivo-desbloqueio-campo"
            rows="5"
            required
            [value]="motivo()"
            [disabled]="aDesbloquear()"
            [attr.aria-invalid]="erroMotivo() ? true : null"
            [attr.aria-describedby]="'motivo-desbloqueio-campo-ajuda' + (erroMotivo() ? ' motivo-desbloqueio-campo-erro' : '')"
            (input)="escrever($event)"
          ></textarea>
          <p class="desbloqueio-ajuda" id="motivo-desbloqueio-campo-ajuda">
            O desbloqueio fica registado com o motivo. Depois de alterado e gravado, o campo volta a ficar bloqueado.
          </p>
          @if (erroMotivo(); as mensagem) {
            <p class="erro-campo" role="alert" id="motivo-desbloqueio-campo-erro">{{ mensagem }}</p>
          }
        </div>
      }
      <div rodape>
        <button type="button" app-botao variante="secundario" tamanho="pequeno" [desativado]="aDesbloquear()" (click)="cancelar()">
          Cancelar
        </button>
        <button type="button" app-botao tamanho="pequeno" [desativado]="semMotivo()" [aCarregar]="aDesbloquear()" (click)="confirmar()">
          Confirmar
        </button>
      </div>
    </app-modal>
  `,
  styles: `
    .desbloqueio {
      display: flex;
      flex-direction: column;
      gap: var(--espaco-2);
    }

    .desbloqueio-campo,
    .desbloqueio-ajuda,
    .erro-campo {
      margin: 0;
    }

    .desbloqueio-campo,
    .rotulo {
      font-size: var(--texto-base);
      color: var(--cor-texto);
    }

    .obrigatorio {
      color: var(--cor-erro);
    }

    textarea {
      box-sizing: border-box;
      width: 100%;
      min-height: 120px;
      padding: var(--espaco-2) var(--espaco-2-5);
      border: 1px solid var(--cor-borda-controlo);
      border-radius: var(--raio-md);
      background: var(--cor-superficie);
      font: inherit;
      font-size: var(--texto-base);
      resize: vertical;
    }

    textarea[aria-invalid='true'] {
      border-color: var(--cor-erro);
    }

    .desbloqueio-ajuda {
      font-size: var(--texto-sm);
      color: var(--cor-texto-secundario);
    }

    .erro-campo {
      font-size: var(--texto-sm);
      color: var(--cor-erro);
    }
  `,
})
export class DesbloquearCampoModalComponent {
  private readonly contexto = inject(DesbloqueioCampos);
  private readonly processoService = inject(ProcessoService);

  readonly pendente = this.contexto.pendente;
  readonly motivo = signal('');
  readonly aDesbloquear = signal(false);
  readonly erroMotivo = signal<string | null>(null);
  readonly erroGeral = signal<string | null>(null);
  readonly semMotivo = computed(() => this.motivo().trim() === '');

  constructor() {
    // Cada pedido começa vazio, sem os erros do anterior.
    effect(() => {
      if (this.pendente()) {
        untracked(() => {
          this.motivo.set('');
          this.erroMotivo.set(null);
          this.erroGeral.set(null);
        });
      }
    });
  }

  escrever(evento: Event): void {
    this.motivo.set((evento.target as HTMLTextAreaElement).value);
    this.erroMotivo.set(null);
  }

  cancelar(): void {
    if (!this.aDesbloquear()) {
      this.contexto.cancelar();
    }
  }

  confirmar(): void {
    const pedido = this.pendente();
    const alvo = this.contexto.alvo();
    if (!pedido || !alvo || this.semMotivo() || this.aDesbloquear()) {
      return;
    }
    this.aDesbloquear.set(true);
    this.erroGeral.set(null);
    this.processoService
      .desbloquearCampo(alvo.processoId, { versao: alvo.versao, campo: pedido.chave, motivo: this.motivo().trim() })
      .subscribe({
        next: (processo) => {
          this.aDesbloquear.set(false);
          this.contexto.concluir(processo);
        },
        error: (erro: ApiError) => {
          this.aDesbloquear.set(false);
          const doMotivo = erro.campos?.find((campo) => campo.path === CAMPO_MOTIVO)?.erro;
          if (doMotivo) {
            this.erroMotivo.set(doMotivo);
            return;
          }
          this.erroGeral.set(erro.codigo === 'ERRO_DESCONHECIDO' ? MENSAGEM_SEM_RESPOSTA : erro.mensagem);
        },
      });
  }
}
