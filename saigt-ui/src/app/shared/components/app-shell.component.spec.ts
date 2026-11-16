import { Component, WritableSignal, signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { AppShellComponent } from './app-shell.component';
import { AuthService } from '../../core/auth/auth.service';
import { NotificacoesNaoLidasService } from '../../services/notificacoes-nao-lidas.service';
import { ROUTES } from '../routes';

@Component({ template: '' })
class PaginaVaziaComponent {}

describe('AppShellComponent', () => {
  let fixture: ComponentFixture<AppShellComponent>;
  let router: Router;
  let authServiceMock: {
    nome: ReturnType<typeof vi.fn>;
    origem: ReturnType<typeof vi.fn>;
    entidade: ReturnType<typeof vi.fn>;
    perfilInterno: ReturnType<typeof vi.fn>;
    logout: ReturnType<typeof vi.fn>;
  };
  let naoLidasMock: { naoLidas: WritableSignal<number>; atualizar: ReturnType<typeof vi.fn> };

  async function criarComponente(url = '/painel'): Promise<void> {
    TestBed.configureTestingModule({
      imports: [AppShellComponent],
      providers: [
        provideRouter([{ path: '**', component: PaginaVaziaComponent }]),
        { provide: AuthService, useValue: authServiceMock },
        { provide: NotificacoesNaoLidasService, useValue: naoLidasMock },
      ],
    });
    router = TestBed.inject(Router);
    await router.navigateByUrl(url);
    vi.spyOn(router, 'navigateByUrl').mockResolvedValue(true);
    fixture = TestBed.createComponent(AppShellComponent);
    fixture.componentRef.setInput('breadcrumb', 'Painel Principal');
    fixture.detectChanges();
  }

  const el = () => fixture.nativeElement as HTMLElement;
  const textos = (seletor: string) => [...el().querySelectorAll(seletor)].map((e) => e.textContent?.trim());
  const botaoAdministracao = () => el().querySelector<HTMLButtonElement>('.nav-link-menu')!;
  const botaoUtilizador = () => el().querySelector<HTMLButtonElement>('.botao-utilizador')!;

  /** O 1.º item recebe o foco num `setTimeout`: o menu só existe depois da deteção de alterações. */
  function abrir(botao: HTMLButtonElement): void {
    botao.click();
    fixture.detectChanges();
    vi.runAllTimers();
  }

  function tecla(alvo: Element, key: string): void {
    alvo.dispatchEvent(new KeyboardEvent('keydown', { key, bubbles: true }));
    fixture.detectChanges();
  }

  beforeEach(() => {
    vi.useFakeTimers();
    authServiceMock = {
      nome: vi.fn().mockReturnValue('Ana Teixeira'),
      origem: vi.fn().mockReturnValue('AD_DGT'),
      entidade: vi.fn().mockReturnValue(null),
      perfilInterno: vi.fn().mockReturnValue('MASTER'),
      logout: vi.fn().mockReturnValue(of(undefined)),
    };
    naoLidasMock = { naoLidas: signal(0), atualizar: vi.fn() };
  });

  afterEach(() => vi.useRealTimers());

  describe('menu por perfil', () => {
    it('entidade: Painel Principal · Processos · Notificações e a entidade na 2.ª linha', async () => {
      authServiceMock.origem.mockReturnValue('INCM');
      authServiceMock.perfilInterno.mockReturnValue(null);
      authServiceMock.entidade.mockReturnValue({ nipc: '500051062', nome: 'Câmara Municipal de Sintra' });
      await criarComponente();

      expect(textos('.navegacao > .nav-link')).toEqual(['Painel Principal', 'Processos', 'Notificações']);
      expect(el().querySelector('.utilizador-subtitulo')?.textContent?.trim()).toBe('Câmara Municipal de Sintra');
    });

    it('DGT Master: Administração com os 9 itens, sem Formulários, e o perfil na 2.ª linha', async () => {
      await criarComponente();

      expect(textos('.navegacao > .nav-link, .navegacao .nav-link-menu')).toEqual(['Painel Principal', 'Notificações', 'Administração']);
      expect(el().querySelector('.utilizador-subtitulo')?.textContent?.trim()).toBe('Master');

      abrir(botaoAdministracao());
      expect(textos('.menu .menu-item')).toEqual([
        'Análise e Relatórios',
        'Gestão de Utilizadores',
        'Alteração de Owner',
        'Processos',
        'Gestão de Entidades',
        'Gestão de Notificações',
        'Listas de Valores',
        'Workflows',
        'Configuração de Procedimentos',
        'Plantas SAIGT',
      ]);
      expect([...el().querySelectorAll('.menu a.menu-item')].map((a) => a.getAttribute('href'))).toEqual([
        ROUTES.ADMINISTRACAO_PAINEL,
        ROUTES.ADMINISTRACAO_UTILIZADORES,
        ROUTES.ADMINISTRACAO_ALTERACAO_OWNER,
        ROUTES.PROCESSOS,
        ROUTES.ADMINISTRACAO_ENTIDADES,
        ROUTES.ADMINISTRACAO_NOTIFICACOES,
        ROUTES.ADMINISTRACAO_LISTA_VALORES,
        ROUTES.ADMINISTRACAO_WORKFLOWS,
        ROUTES.ADMINISTRACAO_CONFIGURACAO,
        ROUTES.ADMINISTRACAO_PLANTAS,
      ]);
    });

    it('DGT Técnico: Administração com Processos, Listas de Valores e Plantas SAIGT', async () => {
      authServiceMock.perfilInterno.mockReturnValue('TECNICO');
      await criarComponente();

      abrir(botaoAdministracao());
      expect(textos('.menu .menu-item')).toEqual(['Processos', 'Listas de Valores', 'Plantas SAIGT']);
      expect(el().querySelector('.menu a.menu-item:last-child')?.getAttribute('href')).toBe(ROUTES.ADMINISTRACAO_PLANTAS);
    });

    it('DGT Visitante: Processos no topo, sem Administração nem Notificações', async () => {
      authServiceMock.perfilInterno.mockReturnValue('VISITANTE');
      await criarComponente();

      expect(textos('.navegacao > .nav-link')).toEqual(['Painel Principal', 'Processos']);
      expect(el().querySelector('.nav-link-menu')).toBeNull();
    });
  });

  describe('item ativo', () => {
    it('marca o link da página atual com o sublinhado e aria-current="page"', async () => {
      authServiceMock.perfilInterno.mockReturnValue('VISITANTE');
      await criarComponente('/processos');

      const ativo = el().querySelector('.navegacao .nav-link-ativo');
      expect(ativo?.textContent?.trim()).toBe('Processos');
      expect(ativo?.getAttribute('aria-current')).toBe('page');
    });

    it('na Gestão de Notificações, Administração e o item do menu ficam com aria-current="page"', async () => {
      await criarComponente(ROUTES.ADMINISTRACAO_NOTIFICACOES);

      expect(botaoAdministracao().getAttribute('aria-current')).toBe('page');
      abrir(botaoAdministracao());
      // O routerLinkActive atualiza-se numa microtarefa depois de o menu aparecer.
      await fixture.whenStable();
      fixture.detectChanges();
      const item = [...el().querySelectorAll('.menu a.menu-item')].find((a) => a.textContent?.trim() === 'Gestão de Notificações');
      expect(item?.getAttribute('aria-current')).toBe('page');
      expect(item?.classList).toContain('menu-item-ativo');
      const outro = [...el().querySelectorAll('.menu a.menu-item')].find((a) => a.textContent?.trim() === 'Gestão de Entidades');
      expect(outro?.getAttribute('aria-current')).toBeNull();
    });

    it('marca Administração quando a página é de um dos seus itens', async () => {
      await criarComponente('/administracao/utilizadores');

      expect(botaoAdministracao().classList).toContain('nav-link-ativo');
      expect(botaoAdministracao().getAttribute('aria-current')).toBe('page');
    });
  });

  describe('logótipo e itens sem página', () => {
    it('mostra o logótipo oficial a 181×35 com o alt definido, com link para o painel', async () => {
      await criarComponente();

      const logotipo = el().querySelector<HTMLImageElement>('.logotipo img')!;
      expect(logotipo.getAttribute('src')).toBe('marca/saigt-logotipo.png');
      expect(logotipo.getAttribute('alt')).toBe('SAIGT — Submissão Automática de Instrumentos de Gestão Territorial');
      expect([logotipo.getAttribute('width'), logotipo.getAttribute('height')]).toEqual(['181', '35']);
      expect(el().querySelector('.logotipo')?.getAttribute('href')).toBe(ROUTES.PAINEL);
    });

    it('Ajuda e Suporte não navegam e têm aria-disabled', async () => {
      await criarComponente();
      abrir(botaoAdministracao());

      const desativados = [...el().querySelectorAll('[aria-disabled="true"]')];
      expect(desativados.map((e) => e.textContent?.trim() || e.getAttribute('aria-label'))).toEqual(['Ajuda', 'Suporte']);
      expect(desativados.some((e) => e.hasAttribute('href'))).toBe(false);
    });
  });

  describe('área de conteúdo', () => {
    it('tem margens por omissão e fica sem elas com conteudoTotal', async () => {
      await criarComponente();
      expect(el().querySelector('main.conteudo')?.classList).not.toContain('conteudo-total');

      fixture.componentRef.setInput('conteudoTotal', true);
      fixture.detectChanges();
      expect(el().querySelector('main.conteudo')?.classList).toContain('conteudo-total');
    });
  });

  describe('notificações', () => {
    const linkNotificacoes = () =>
      [...el().querySelectorAll<HTMLAnchorElement>('.navegacao > .nav-link')].find((link) => link.textContent?.trim() === 'Notificações');

    it.each(['MASTER', null])('%s: o item Notificações abre /notificacoes e fica ativo com aria-current="page"', async (perfil) => {
      authServiceMock.perfilInterno.mockReturnValue(perfil);
      await criarComponente(ROUTES.NOTIFICACOES);

      expect(linkNotificacoes()?.getAttribute('href')).toBe(ROUTES.NOTIFICACOES);
      expect(linkNotificacoes()?.getAttribute('aria-current')).toBe('page');
      expect(el().querySelector('app-notificacoes-dropdown')).not.toBeNull();
    });

    it.each(['TECNICO', 'VISITANTE'])('%s: sem o item Notificações nem o sino', async (perfil) => {
      authServiceMock.perfilInterno.mockReturnValue(perfil);
      await criarComponente();

      expect(linkNotificacoes()).toBeUndefined();
      expect(el().querySelector('app-notificacoes-dropdown')).toBeNull();
    });

    it('pede a contagem de não lidas a cada navegação que termina', async () => {
      await criarComponente();
      expect(naoLidasMock.atualizar).not.toHaveBeenCalled();

      vi.mocked(router.navigateByUrl).mockRestore();
      await router.navigateByUrl('/processos');
      await router.navigateByUrl('/perfil');

      expect(naoLidasMock.atualizar).toHaveBeenCalledTimes(2);
    });

    it.each(['TECNICO', 'VISITANTE'])('não pede a contagem para %s, que não tem Centro de Notificações', async (perfil) => {
      authServiceMock.perfilInterno.mockReturnValue(perfil);
      await criarComponente();

      vi.mocked(router.navigateByUrl).mockRestore();
      await router.navigateByUrl('/processos');

      expect(naoLidasMock.atualizar).not.toHaveBeenCalled();
    });
  });

  describe('menus por teclado e rato', () => {
    it('abrir põe o foco no 1.º item; as setas, Home e End percorrem os itens', async () => {
      await criarComponente();
      abrir(botaoAdministracao());

      const itens = [...el().querySelectorAll<HTMLElement>('.menu [role="menuitem"]')];
      expect(botaoAdministracao().getAttribute('aria-expanded')).toBe('true');
      expect(document.activeElement).toBe(itens[0]);

      tecla(itens[0], 'ArrowDown');
      expect(document.activeElement).toBe(itens[1]);
      tecla(itens[1], 'ArrowUp');
      tecla(itens[0], 'ArrowUp');
      expect(document.activeElement).toBe(itens[itens.length - 1]);
      tecla(itens[itens.length - 1], 'Home');
      expect(document.activeElement).toBe(itens[0]);
      tecla(itens[0], 'End');
      expect(document.activeElement).toBe(itens[itens.length - 1]);
    });

    it('seta para baixo no botão abre o menu', async () => {
      await criarComponente();
      tecla(botaoAdministracao(), 'ArrowDown');
      vi.runAllTimers();

      expect(el().querySelector('.menu')).not.toBeNull();
      expect(document.activeElement?.textContent?.trim()).toBe('Análise e Relatórios');
    });

    it('Esc fecha o menu e devolve o foco ao botão', async () => {
      await criarComponente();
      abrir(botaoAdministracao());

      tecla(document.activeElement!, 'Escape');

      expect(el().querySelector('.menu')).toBeNull();
      expect(document.activeElement).toBe(botaoAdministracao());
    });

    it('clicar fora fecha o menu e devolve o foco ao botão', async () => {
      await criarComponente();
      abrir(botaoUtilizador());

      document.body.click();
      fixture.detectChanges();

      expect(el().querySelector('.menu')).toBeNull();
      expect(document.activeElement).toBe(botaoUtilizador());
    });

    it('fechar repõe aria-expanded a false', async () => {
      await criarComponente();
      abrir(botaoAdministracao());
      tecla(document.activeElement!, 'Escape');

      expect(botaoAdministracao().getAttribute('aria-expanded')).toBe('false');
    });

    it('Tab fecha o menu sem prender o foco', async () => {
      await criarComponente();
      abrir(botaoUtilizador());

      tecla(document.activeElement!, 'Tab');

      expect(el().querySelector('.menu')).toBeNull();
    });

    it('clicar fora num campo fecha o menu sem lhe tirar o foco', async () => {
      await criarComponente();
      abrir(botaoUtilizador());
      const campo = document.createElement('input');
      document.body.appendChild(campo);

      campo.focus();
      campo.click();
      fixture.detectChanges();

      expect(el().querySelector('.menu')).toBeNull();
      expect(document.activeElement).toBe(campo);
      campo.remove();
    });

    it('abrir um menu fecha o outro', async () => {
      await criarComponente();
      abrir(botaoAdministracao());
      abrir(botaoUtilizador());

      expect(textos('.menu .menu-item')).toEqual(['Perfil', 'Terminar Sessão']);
    });
  });

  describe('menu do utilizador', () => {
    it('chama logout() e navega para /login ao terminar sessão', async () => {
      await criarComponente();
      abrir(botaoUtilizador());

      el().querySelector<HTMLButtonElement>('.botao-sair')!.click();

      expect(authServiceMock.logout).toHaveBeenCalledTimes(1);
      expect(router.navigateByUrl).toHaveBeenCalledWith(ROUTES.LOGIN);
    });

    it('navega para /login mesmo quando logout() falha', async () => {
      authServiceMock.logout.mockReturnValue(throwError(() => new Error('falha de rede')));
      await criarComponente();
      abrir(botaoUtilizador());

      el().querySelector<HTMLButtonElement>('.botao-sair')!.click();

      expect(router.navigateByUrl).toHaveBeenCalledWith(ROUTES.LOGIN);
    });
  });

  describe('Pedido de Apoio', () => {
    it('aparece ao utilizador de uma entidade', async () => {
      authServiceMock.perfilInterno.mockReturnValue(null);
      await criarComponente();

      expect(el().querySelector('app-pedido-apoio .botao-pedido-apoio')).not.toBeNull();
    });

    it.each(['MASTER', 'TECNICO', 'VISITANTE'])('não aparece ao perfil %s, que é quem responde', async (perfil) => {
      authServiceMock.perfilInterno.mockReturnValue(perfil);
      await criarComponente();

      expect(el().querySelector('app-pedido-apoio')).toBeNull();
    });

    it('leva o processo aberto como contexto', async () => {
      authServiceMock.perfilInterno.mockReturnValue(null);
      await criarComponente('/processos/123e4567-e89b-12d3-a456-426614174000/historico');

      expect(fixture.componentInstance.processoAberto()).toBe('123e4567-e89b-12d3-a456-426614174000');
    });

    it('fora de um processo não leva contexto', async () => {
      authServiceMock.perfilInterno.mockReturnValue(null);
      await criarComponente('/processos/criar');

      expect(fixture.componentInstance.processoAberto()).toBeNull();
    });
  });
});
