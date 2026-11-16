import { Injectable, inject, signal } from '@angular/core';
import { Subscription } from 'rxjs';
import { NotificacaoService } from './notificacao.service';

@Injectable({ providedIn: 'root' })
export class NotificacoesNaoLidasService {
  private readonly notificacaoService = inject(NotificacaoService);
  private pedido: Subscription | null = null;

  readonly naoLidas = signal(0);

  atualizar(): void {
    this.pedido?.unsubscribe();
    this.pedido = this.notificacaoService.contarNaoLidas().subscribe({
      next: (total) => this.naoLidas.set(total),
      error: () => undefined,
    });
  }
}
