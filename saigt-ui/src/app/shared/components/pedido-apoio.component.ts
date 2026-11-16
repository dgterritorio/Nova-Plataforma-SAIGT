import { ChangeDetectionStrategy, Component, inject, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ApiError } from '../../core/models/error-response.model';
import { NotificacaoService } from '../../services/notificacao.service';
import { AlertaComponent, BotaoComponent, CampoTextoComponent } from '../ui';
import { ModalComponent } from '../ui/modal/modal.component';

/** Botão fixo em todos os ecrãs da entidade; o pedido segue para o Master com o processo aberto, se houver. */
@Component({
  selector: 'app-pedido-apoio',
  imports: [AlertaComponent, BotaoComponent, CampoTextoComponent, FormsModule, ModalComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrl: './pedido-apoio.component.css',
  template: `
    <button type="button" class="botao-pedido-apoio" (click)="abrir()">
      <svg viewBox="0 0 16 16" width="16" height="16" fill="none" stroke="currentColor" stroke-width="1.33" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
        <path d="M14 10a1.33 1.33 0 0 1-1.33 1.33h-8L2 14V3.33A1.33 1.33 0 0 1 3.33 2h9.34A1.33 1.33 0 0 1 14 3.33z" />
      </svg>
      Pedido de Apoio
    </button>

    <app-modal titulo="Pedido de Apoio" [aberto]="aberto()" (fechar)="fechar()">
      @if (enviado()) {
        <app-alerta tipo="sucesso">O pedido foi enviado à DGT. A resposta chega ao Centro de Notificações.</app-alerta>
      } @else {
        <div class="campos">
          @if (processoId()) {
            <p class="contexto">O pedido fica associado ao processo aberto.</p>
          }
          <app-campo-texto rotulo="Assunto" [obrigatorio]="true" [maxlength]="200" [erro]="erroAssunto()" [(ngModel)]="assunto" />
          <app-campo-texto
            rotulo="Mensagem"
            [obrigatorio]="true"
            [multilinha]="true"
            [maxlength]="5000"
            [erro]="erroMensagem()"
            [(ngModel)]="mensagem"
          />
          @if (erro(); as texto) {
            <app-alerta tipo="erro">{{ texto }}</app-alerta>
          }
        </div>
      }
      <div rodape>
        @if (enviado()) {
          <button type="button" app-botao tamanho="pequeno" (click)="fechar()">Fechar</button>
        } @else {
          <button type="button" app-botao variante="secundario" tamanho="pequeno" [desativado]="aEnviar()" (click)="fechar()">Cancelar</button>
          <button type="button" app-botao tamanho="pequeno" [aCarregar]="aEnviar()" (click)="enviar()">Enviar</button>
        }
      </div>
    </app-modal>
  `,
})
export class PedidoApoioComponent {
  private readonly notificacaoService = inject(NotificacaoService);

  readonly processoId = input<string | null>(null);

  readonly aberto = signal(false);
  readonly enviado = signal(false);
  readonly aEnviar = signal(false);
  readonly erro = signal<string | null>(null);
  readonly erroAssunto = signal<string | null>(null);
  readonly erroMensagem = signal<string | null>(null);
  assunto = '';
  mensagem = '';

  abrir(): void {
    this.assunto = '';
    this.mensagem = '';
    this.enviado.set(false);
    this.erro.set(null);
    this.erroAssunto.set(null);
    this.erroMensagem.set(null);
    this.aberto.set(true);
  }

  fechar(): void {
    if (!this.aEnviar()) {
      this.aberto.set(false);
    }
  }

  enviar(): void {
    const titulo = this.assunto.trim();
    const texto = this.mensagem.trim();
    this.erroAssunto.set(titulo ? null : 'Indique o assunto.');
    this.erroMensagem.set(texto ? null : 'Escreva a mensagem.');
    if (!titulo || !texto) {
      return;
    }
    this.erro.set(null);
    this.aEnviar.set(true);
    this.notificacaoService
      .criar({ tipo: 'PEDIDO_APOIO', titulo, texto, processoId: this.processoId() ?? undefined, destinatarios: [] })
      .subscribe({
        next: () => {
          this.aEnviar.set(false);
          this.enviado.set(true);
        },
        error: (erro: ApiError) => {
          this.aEnviar.set(false);
          this.erro.set(erro?.mensagem ?? 'Não foi possível enviar o pedido. Tente novamente.');
        },
      });
  }
}
