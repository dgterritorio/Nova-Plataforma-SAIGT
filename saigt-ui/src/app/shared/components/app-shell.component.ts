import { Component, ElementRef, OnInit, computed, inject, input, signal, viewChild } from '@angular/core';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { Title } from '@angular/platform-browser';
import { NavigationEnd, Router, RouterLink, RouterLinkActive } from '@angular/router';
import { filter, map } from 'rxjs';
import { AuthService } from '../../core/auth/auth.service';
import { temCentroNotificacoes } from '../../core/auth/perfis-backoffice';
import { NotificacoesNaoLidasService } from '../../services/notificacoes-nao-lidas.service';
import { PERFIL_INTERNO_LABEL } from '../../models/utilizador.model';
import { ROUTES } from '../routes';
import { ItemMenu, menuAtivo, menuDoPerfil } from './app-shell.menu';
import { NotificacoesDropdownComponent } from './notificacoes-dropdown.component';
import { PedidoApoioComponent } from './pedido-apoio.component';

type MenuAberto = 'administracao' | 'utilizador';

/**
 * Cabeçalho dos ecrãs autenticados (Figma UI-077/078). Os itens sem página (Plantas SAIGT, Ajuda e Suporte)
 * aparecem desativados. Os dois menus seguem o padrão "menu button" do WAI-ARIA.
 */
@Component({
  selector: 'app-shell',
  imports: [RouterLink, RouterLinkActive, NotificacoesDropdownComponent, PedidoApoioComponent],
  styleUrl: './app-shell.component.css',
  host: { '(document:click)': 'aoClicarFora($event)' },
  template: `
    <div class="shell">
      <div class="barra-utilitaria">
        <div class="barra-utilitaria-links">
          <span class="link-utilitario" role="link" aria-disabled="true">Ajuda</span>
          <span class="link-utilitario" role="link" aria-disabled="true">Suporte</span>
        </div>
      </div>

      <header class="cabecalho">
        <div class="cabecalho-esquerda">
          <a class="logotipo" [routerLink]="rotas.PAINEL">
            <img
              src="marca/saigt-logotipo.png"
              alt="SAIGT — Submissão Automática de Instrumentos de Gestão Territorial"
              width="181"
              height="35"
            />
          </a>

          <nav class="navegacao" aria-label="Navegação principal">
            @for (item of menu(); track item.rotulo) {
              @if (item.itens) {
                <div class="menu-envolvente" #envolventeAdministracao>
                  <button
                    #botaoAdministracao
                    type="button"
                    class="nav-link nav-link-menu"
                    [class.nav-link-ativo]="ativo(item)"
                    [attr.aria-current]="ativo(item) ? 'page' : null"
                    aria-haspopup="menu"
                    [attr.aria-expanded]="menuAberto() === 'administracao'"
                    (click)="alternar('administracao', $event)"
                    (keydown.arrowdown)="abrir('administracao', $event)"
                  >
                    {{ item.rotulo }}
                    <svg class="chevron" viewBox="0 0 20 20" fill="currentColor" aria-hidden="true">
                      <path
                        fill-rule="evenodd"
                        d="M5.23 7.21a.75.75 0 011.06.02L10 10.94l3.71-3.71a.75.75 0 111.08 1.04l-4.25 4.25a.75.75 0 01-1.08 0L5.21 8.27a.75.75 0 01.02-1.06z"
                        clip-rule="evenodd"
                      />
                    </svg>
                  </button>
                  @if (menuAberto() === 'administracao') {
                    <div class="menu" role="menu" tabindex="-1" [attr.aria-label]="item.rotulo" (keydown)="teclaNoMenu($event, 'administracao')">
                      @for (filho of item.itens; track filho.rotulo) {
                        @if (filho.rota) {
                          <a
                            class="menu-item"
                            role="menuitem"
                            tabindex="-1"
                            [routerLink]="filho.rota"
                            routerLinkActive="menu-item-ativo"
                            ariaCurrentWhenActive="page"
                            (click)="fechar(false)"
                          >
                            {{ filho.rotulo }}
                          </a>
                        } @else {
                          <span class="menu-item menu-item-inativo" role="menuitem" tabindex="-1" aria-disabled="true">{{ filho.rotulo }}</span>
                        }
                      }
                    </div>
                  }
                </div>
              } @else if (item.rota) {
                <a class="nav-link" [routerLink]="item.rota" routerLinkActive="nav-link-ativo" ariaCurrentWhenActive="page">
                  {{ item.rotulo }}
                </a>
              } @else {
                <span class="nav-link nav-link-inativo" role="link" aria-disabled="true">{{ item.rotulo }}</span>
              }
            }
          </nav>
        </div>

        <div class="cabecalho-direita">
          @if (temNotificacoes()) {
            <app-notificacoes-dropdown />
          }

          <div class="menu-envolvente" #envolventeUtilizador>
            <button
              #botaoUtilizador
              type="button"
              class="botao-utilizador"
              aria-haspopup="menu"
              [attr.aria-expanded]="menuAberto() === 'utilizador'"
              (click)="alternar('utilizador', $event)"
              (keydown.arrowdown)="abrir('utilizador', $event)"
            >
              <span class="avatar" aria-hidden="true">{{ iniciais() }}</span>
              <span class="utilizador-info">
                <span class="utilizador-nome">{{ authService.nome() ?? '—' }}</span>
                <span class="utilizador-subtitulo">{{ subtitulo() }}</span>
              </span>
              <svg class="chevron" viewBox="0 0 20 20" fill="currentColor" aria-hidden="true">
                <path
                  fill-rule="evenodd"
                  d="M5.23 7.21a.75.75 0 011.06.02L10 10.94l3.71-3.71a.75.75 0 111.08 1.04l-4.25 4.25a.75.75 0 01-1.08 0L5.21 8.27a.75.75 0 01.02-1.06z"
                  clip-rule="evenodd"
                />
              </svg>
            </button>
            @if (menuAberto() === 'utilizador') {
              <div class="menu menu-utilizador" role="menu" tabindex="-1" aria-label="Utilizador" (keydown)="teclaNoMenu($event, 'utilizador')">
                <a class="menu-item" role="menuitem" tabindex="-1" [routerLink]="rotas.PERFIL" routerLinkActive="menu-item-ativo" (click)="fechar(false)">
                  Perfil
                </a>
                <button type="button" class="menu-item botao-sair" role="menuitem" tabindex="-1" (click)="sair()">Terminar Sessão</button>
              </div>
            }
          </div>
        </div>
      </header>

      <!-- Como no Figma, a camada "Fundo" fica só na área de conteúdo, por cima do --cor-fundo do body. -->
      <main class="conteudo" [class.conteudo-total]="conteudoTotal()">
        <ng-content />
      </main>
      @if (podePedirApoio()) {
        <app-pedido-apoio [processoId]="processoAberto()" />
      }
    </div>
  `,
})
export class AppShellComponent implements OnInit {
  readonly breadcrumb = input.required<string>();
  /** Ecrãs que no Figma ocupam a área toda, sem margens (ex.: Centro de Notificações). */
  readonly conteudoTotal = input(false);

  readonly authService = inject(AuthService);
  private readonly router = inject(Router);
  private readonly title = inject(Title);
  readonly rotas = ROUTES;

  readonly menu = computed(() => menuDoPerfil(this.authService.perfilInterno()));
  readonly temNotificacoes = computed(() => temCentroNotificacoes(this.authService.perfilInterno()));
  readonly menuAberto = signal<MenuAberto | null>(null);
  /** Só o utilizador de uma entidade pede apoio: os perfis internos são quem responde. */
  readonly podePedirApoio = computed(() => this.authService.perfilInterno() === null);

  private readonly url = toSignal(
    this.router.events.pipe(
      filter((evento) => evento instanceof NavigationEnd),
      map(() => this.router.url),
    ),
    { initialValue: this.router.url },
  );

  private readonly botaoAdministracao = viewChild<ElementRef<HTMLButtonElement>>('botaoAdministracao');
  private readonly botaoUtilizador = viewChild<ElementRef<HTMLButtonElement>>('botaoUtilizador');
  private readonly envolventeAdministracao = viewChild<ElementRef<HTMLElement>>('envolventeAdministracao');
  private readonly envolventeUtilizador = viewChild<ElementRef<HTMLElement>>('envolventeUtilizador');

  /**
   * Cada página tem o seu `app-shell`, criado antes do `NavigationEnd` da navegação que o abriu: contar só
   * aqui dá um pedido por navegação, incluindo a primeira depois do login.
   */
  constructor() {
    const naoLidas = inject(NotificacoesNaoLidasService);
    this.router.events
      .pipe(
        filter((evento) => evento instanceof NavigationEnd),
        takeUntilDestroyed(),
      )
      .subscribe(() => {
        if (this.temNotificacoes()) {
          naoLidas.atualizar();
        }
      });
  }

  readonly processoAberto = computed(() => /^\/processos\/([0-9a-f-]{36})(?:[/?#]|$)/i.exec(this.url())?.[1] ?? null);

  ngOnInit(): void {
    this.title.setTitle(`${this.breadcrumb()} · SAIGT`);
  }

  ativo(item: ItemMenu): boolean {
    return menuAtivo(item, this.url());
  }

  alternar(menu: MenuAberto, evento: Event): void {
    evento.stopPropagation();
    if (this.menuAberto() === menu) {
      this.fechar(true);
    } else {
      this.abrir(menu, evento);
    }
  }

  abrir(menu: MenuAberto, evento: Event): void {
    evento.preventDefault();
    this.menuAberto.set(menu);
    // O menu só existe depois da deteção de alterações: o foco vai para o 1.º item no ciclo seguinte.
    setTimeout(() => this.itens(menu)[0]?.focus());
  }

  fechar(devolverFoco: boolean): void {
    const menu = this.menuAberto();
    this.menuAberto.set(null);
    if (menu && devolverFoco) {
      this.botao(menu)?.focus();
    }
  }

  teclaNoMenu(evento: KeyboardEvent, menu: MenuAberto): void {
    const itens = this.itens(menu);
    const atual = itens.indexOf(document.activeElement as HTMLElement);
    const destino: Record<string, number> = {
      ArrowDown: (atual + 1) % itens.length,
      ArrowUp: (atual - 1 + itens.length) % itens.length,
      Home: 0,
      End: itens.length - 1,
    };
    if (evento.key in destino) {
      evento.preventDefault();
      itens[destino[evento.key]]?.focus();
    } else if (evento.key === 'Escape') {
      evento.preventDefault();
      this.fechar(true);
    } else if (evento.key === 'Tab') {
      this.fechar(false);
    }
  }

  /** Só devolve o foco ao botão se ele estava no menu: não o rouba a um campo onde se clicou. */
  aoClicarFora(evento: MouseEvent): void {
    const menu = this.menuAberto();
    if (!menu || this.envolvente(menu)?.contains(evento.target as Node)) {
      return;
    }
    const focoNoMenu = this.envolvente(menu)?.contains(document.activeElement) || document.activeElement === document.body;
    this.fechar(focoNoMenu);
  }

  iniciais(): string {
    const nome = this.authService.nome();
    if (!nome) {
      return '?';
    }
    return nome
      .split(' ')
      .filter((parte) => parte.length > 0)
      .slice(0, 2)
      .map((parte) => parte[0]!.toUpperCase())
      .join('');
  }

  subtitulo(): string {
    if (this.authService.origem() === 'AD_DGT') {
      const perfil = this.authService.perfilInterno();
      return perfil ? PERFIL_INTERNO_LABEL[perfil] : '';
    }
    return this.authService.entidade()?.nome ?? '';
  }

  /** `logout()` já limpa o estado local antes do pedido, por isso navega para login em sucesso ou erro. */
  sair(): void {
    this.menuAberto.set(null);
    this.authService.logout().subscribe({
      next: () => this.router.navigateByUrl(this.rotas.LOGIN),
      error: () => this.router.navigateByUrl(this.rotas.LOGIN),
    });
  }

  private botao(menu: MenuAberto): HTMLButtonElement | undefined {
    return (menu === 'administracao' ? this.botaoAdministracao() : this.botaoUtilizador())?.nativeElement;
  }

  private envolvente(menu: MenuAberto): HTMLElement | undefined {
    return (menu === 'administracao' ? this.envolventeAdministracao() : this.envolventeUtilizador())?.nativeElement;
  }

  private itens(menu: MenuAberto): HTMLElement[] {
    return Array.from(this.envolvente(menu)?.querySelectorAll<HTMLElement>('[role="menuitem"]') ?? []);
  }
}
