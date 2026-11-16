import { Injectable, Signal, computed, signal } from '@angular/core';
import { Processo } from '../../../models/processo.model';

/** O processo em correção cujos campos o autor pode desbloquear: só existe com `desbloquear-campo` nas ações. */
export interface AlvoDesbloqueio {
  processoId: string;
  versao: number;
}

export interface PedidoDesbloqueio {
  chave: string;
  rotulo: string;
}

/**
 * Partilhado pelo ecrã que mostra o formulário (detalhe ou assistente) com os avisos de bloqueio dos cartões que
 * contém, sem passar entradas por cada um. O ecrã fornece-o, liga-o ao processo e recebe a resposta do `PATCH`.
 */
@Injectable()
export class DesbloqueioCampos {
  private readonly fonte = signal<Signal<AlvoDesbloqueio | null>>(signal(null));
  private aoDesbloquear: (processo: Processo) => void = () => undefined;

  readonly alvo = computed(() => this.fonte()());
  readonly pendente = signal<PedidoDesbloqueio | null>(null);

  ligar(alvo: Signal<AlvoDesbloqueio | null>, aoDesbloquear: (processo: Processo) => void): void {
    this.fonte.set(alvo);
    this.aoDesbloquear = aoDesbloquear;
  }

  pedir(chave: string, rotulo: string): void {
    if (this.alvo()) {
      this.pendente.set({ chave, rotulo });
    }
  }

  cancelar(): void {
    this.pendente.set(null);
  }

  concluir(processo: Processo): void {
    this.pendente.set(null);
    this.aoDesbloquear(processo);
  }
}
