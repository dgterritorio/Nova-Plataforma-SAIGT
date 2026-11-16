import { Component, DestroyRef, ElementRef, inject, signal, viewChild } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { NavigationEnd, Router, RouterLink } from '@angular/router';
import { Subscription, filter } from 'rxjs';
import { ehNaoLida, tempoRelativo } from '../../features/notificacoes/apresentacao-notificacao';
import { NotificacaoResumo } from '../../models/notificacao.model';
import { NotificacaoService } from '../../services/notificacao.service';
import { NotificacoesNaoLidasService } from '../../services/notificacoes-nao-lidas.service';
import { ROUTES } from '../routes';

export const NOTIFICACOES_NO_PAINEL = 5;

/**
 * Sino do cabeçalho (UI-084/UI-082). Padrão "disclosure" do WAI-ARIA e não "menu button": o painel tem
 * ligações para notificações, não ações de menu. Sem "Marcar todas como lidas": a API não tem leitura em lote.
 */
@Component({
  selector: 'app-notificacoes-dropdown',
  imports: [RouterLink],
  styleUrl: './notificacoes-dropdown.component.css',
  host: { '(document:click)': 'aoClicarFora($event)', '(keydown.escape)': 'fechar(true)' },
  template: `
    <button
      #botao
      type="button"
      class="botao-sino"
      [attr.aria-label]="naoLidas() > 0 ? 'Notificações, ' + naoLidas() + ' por ler' : 'Notificações'"
      aria-haspopup="true"
      [attr.aria-expanded]="aberto()"
      aria-controls="painel-notificacoes"
      (click)="alternar()"
    >
      <svg viewBox="0 0 20 20" fill="none" stroke="currentColor" stroke-width="1.5" aria-hidden="true">
        <path
          d="M10 2.75a5 5 0 00-5 5c0 2.06-.5 3.6-1.3 4.85a.6.6 0 00.5.9h11.6a.6.6 0 00.5-.9C15.5 11.35 15 9.81 15 7.75a5 5 0 00-5-5z"
          stroke-linejoin="round"
        />
        <path d="M8.25 15.75a1.85 1.85 0 003.5 0" stroke-linecap="round" />
      </svg>
      @if (naoLidas() > 0) {
        <span class="ponto-sino" aria-hidden="true"></span>
      }
    </button>

    <div id="painel-notificacoes" class="painel" role="region" aria-labelledby="painel-notificacoes-titulo" [hidden]="!aberto()">
      <p id="painel-notificacoes-titulo" class="painel-titulo">Notificações</p>
      @if (aberto()) {
        @if (erro()) {
          <p class="painel-estado">Não foi possível carregar as notificações.</p>
        } @else if (aCarregar()) {
          <p class="painel-estado">A carregar…</p>
        } @else {
          <ul class="linhas">
            @for (n of itens(); track n.id) {
              @let porLer = ehNaoLida(n);
              <li>
                <a class="linha" [class.linha-nao-lida]="porLer" [routerLink]="rotas.NOTIFICACOES" [queryParams]="{ id: n.id }" (click)="fechar(false)">
                  <span class="ponto" [class.ponto-nao-lida]="porLer" aria-hidden="true"></span>
                  <span class="linha-corpo">
                    <span class="linha-titulo">
                      @if (porLer) {
                        <span class="visualmente-oculto">Por ler: </span>
                      }
                      {{ n.titulo }}
                    </span>
                    <span class="linha-texto">{{ n.texto }}</span>
                    <time class="linha-tempo" [attr.datetime]="n.criadoEm">{{ tempoRelativo(n.criadoEm) }}</time>
                  </span>
                </a>
              </li>
            } @empty {
              <li class="painel-estado">Não tem notificações.</li>
            }
          </ul>
        }
        <a class="ver-todas" [routerLink]="rotas.NOTIFICACOES" (click)="fechar(false)">Ver todas as notificações →</a>
      }
    </div>
  `,
})
export class NotificacoesDropdownComponent {
  private readonly notificacaoService = inject(NotificacaoService);
  private readonly naoLidasService = inject(NotificacoesNaoLidasService);
  private readonly elemento = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly destroyRef = inject(DestroyRef);
  private readonly botao = viewChild.required<ElementRef<HTMLButtonElement>>('botao');
  private pedido: Subscription | null = null;

  readonly rotas = ROUTES;
  readonly naoLidas = this.naoLidasService.naoLidas;
  readonly aberto = signal(false);
  readonly itens = signal<NotificacaoResumo[]>([]);
  readonly aCarregar = signal(false);
  readonly erro = signal(false);

  readonly ehNaoLida = ehNaoLida;
  readonly tempoRelativo = tempoRelativo;

  constructor() {
    inject(Router)
      .events.pipe(
        filter((evento) => evento instanceof NavigationEnd),
        takeUntilDestroyed(),
      )
      .subscribe(() => this.fechar(false));
  }

  alternar(): void {
    if (this.aberto()) {
      this.fechar(false);
    } else {
      this.abrir();
    }
  }

  fechar(devolverFoco: boolean): void {
    if (!this.aberto()) {
      return;
    }
    this.aberto.set(false);
    this.pedido?.unsubscribe();
    if (devolverFoco) {
      this.botao().nativeElement.focus();
    }
  }

  aoClicarFora(evento: MouseEvent): void {
    if (this.aberto() && !this.elemento.nativeElement.contains(evento.target as Node)) {
      this.fechar(false);
    }
  }

  private abrir(): void {
    this.aberto.set(true);
    this.aCarregar.set(true);
    this.erro.set(false);
    this.naoLidasService.atualizar();
    this.pedido?.unsubscribe();
    this.pedido = this.notificacaoService
      .listar({}, 0, NOTIFICACOES_NO_PAINEL)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (pagina) => {
          this.itens.set(pagina.content);
          this.aCarregar.set(false);
        },
        error: () => {
          this.erro.set(true);
          this.aCarregar.set(false);
        },
      });
  }
}
