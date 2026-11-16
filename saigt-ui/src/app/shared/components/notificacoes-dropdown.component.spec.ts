import { Component, WritableSignal, signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { NotificacaoResumo } from '../../models/notificacao.model';
import { NotificacaoService } from '../../services/notificacao.service';
import { NotificacoesNaoLidasService } from '../../services/notificacoes-nao-lidas.service';
import { NOTIFICACOES_NO_PAINEL, NotificacoesDropdownComponent } from './notificacoes-dropdown.component';

@Component({ template: '' })
class PaginaVaziaComponent {}

function notificacao(id: string, campos: Partial<NotificacaoResumo> = {}): NotificacaoResumo {
  return {
    id,
    tipo: 'ESTATICA',
    titulo: `Notícia ${id}`,
    texto: `Texto ${id}`,
    processoId: null,
    numeroProcesso: null,
    idIgtSnit: null,
    descricaoIgt: null,
    prazo: null,
    estado: 'ATIVA',
    criadoEm: new Date(Date.now() - 2 * 60_000).toISOString(),
    lidoEm: null,
    resolvidoEm: null,
    criadoPorNome: null,
    numeroMensagens: 0,
    numeroDocumentos: 0,
    ...campos,
  };
}

describe('NotificacoesDropdownComponent', () => {
  let fixture: ComponentFixture<NotificacoesDropdownComponent>;
  let router: Router;
  let listar: ReturnType<typeof vi.fn>;
  let naoLidas: WritableSignal<number>;
  let atualizar: ReturnType<typeof vi.fn>;

  beforeEach(async () => {
    naoLidas = signal(0);
    atualizar = vi.fn();
    listar = vi.fn().mockReturnValue(
      of({
        content: [notificacao('n1', { tipo: 'TEMPORARIA', titulo: 'Validação pendente' }), notificacao('n2')],
        page: 0,
        size: NOTIFICACOES_NO_PAINEL,
        totalElements: 2,
        totalPages: 1,
      }),
    );
    TestBed.configureTestingModule({
      providers: [
        provideRouter([{ path: '**', component: PaginaVaziaComponent }]),
        { provide: NotificacaoService, useValue: { listar } },
        { provide: NotificacoesNaoLidasService, useValue: { naoLidas, atualizar } },
      ],
    });
    router = TestBed.inject(Router);
    await router.navigateByUrl('/painel');
    fixture = TestBed.createComponent(NotificacoesDropdownComponent);
    fixture.autoDetectChanges();
    document.body.appendChild(fixture.nativeElement);
  });

  afterEach(() => fixture.nativeElement.remove());

  const el = () => fixture.nativeElement as HTMLElement;
  const sino = () => el().querySelector<HTMLButtonElement>('.botao-sino')!;
  const painel = () => el().querySelector<HTMLElement>('#painel-notificacoes')!;

  function abrir(): void {
    sino().click();
    fixture.detectChanges();
  }

  it('com 2 por ler: ponto vermelho e «Notificações, 2 por ler»; com 0 sem ponto', () => {
    naoLidas.set(2);
    fixture.detectChanges();
    expect(sino().getAttribute('aria-label')).toBe('Notificações, 2 por ler');
    expect(el().querySelector('.ponto-sino')).not.toBeNull();

    naoLidas.set(0);
    fixture.detectChanges();
    expect(sino().getAttribute('aria-label')).toBe('Notificações');
    expect(el().querySelector('.ponto-sino')).toBeNull();
  });

  it('é um disclosure: aria-haspopup, aria-controls e aria-expanded', () => {
    expect(sino().getAttribute('aria-haspopup')).toBe('true');
    expect(sino().getAttribute('aria-controls')).toBe('painel-notificacoes');
    expect(sino().getAttribute('aria-expanded')).toBe('false');
    expect(painel().hidden).toBe(true);
  });

  it('abrir mostra as 5 mais recentes, com ponto azul nas não lidas, tempo relativo e «Ver todas»', () => {
    abrir();

    expect(sino().getAttribute('aria-expanded')).toBe('true');
    expect(painel().hidden).toBe(false);
    expect(listar).toHaveBeenCalledWith({}, 0, NOTIFICACOES_NO_PAINEL);
    expect(atualizar).toHaveBeenCalled();
    const linhas = [...el().querySelectorAll('.linha')];
    expect(linhas).toHaveLength(2);
    expect(linhas[0].querySelector('.ponto-nao-lida')).not.toBeNull();
    expect(linhas[0].classList).toContain('linha-nao-lida');
    expect(linhas[1].querySelector('.ponto-nao-lida')).toBeNull();
    expect(linhas[0].querySelector('.linha-tempo')?.textContent?.trim()).toBe('2 min atrás');
    expect(el().querySelector('.ver-todas')?.textContent?.trim()).toBe('Ver todas as notificações →');
    expect(el().querySelector('.ver-todas')?.getAttribute('href')).toBe('/notificacoes');
  });

  it('Escape fecha e devolve o foco ao sino', () => {
    abrir();
    el().querySelector<HTMLElement>('.linha')!.focus();

    el().querySelector('.linha')!.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }));
    fixture.detectChanges();

    expect(sino().getAttribute('aria-expanded')).toBe('false');
    expect(document.activeElement).toBe(sino());
  });

  it('clicar fora fecha', () => {
    abrir();

    document.body.click();
    fixture.detectChanges();

    expect(sino().getAttribute('aria-expanded')).toBe('false');
  });

  it('clicar numa linha navega para /notificacoes?id= e fecha', async () => {
    abrir();

    el().querySelector<HTMLAnchorElement>('.linha')!.click();
    await fixture.whenStable();

    expect(router.url).toBe('/notificacoes?id=n1');
    expect(sino().getAttribute('aria-expanded')).toBe('false');
  });

  it('sem notificações e com erro mostra a mensagem no painel', () => {
    listar.mockReturnValueOnce(of({ content: [], page: 0, size: 5, totalElements: 0, totalPages: 0 }));
    abrir();
    expect(painel().textContent).toContain('Não tem notificações.');

    sino().click();
    listar.mockReturnValueOnce(throwError(() => new Error('rede')));
    abrir();
    expect(painel().textContent).toContain('Não foi possível carregar as notificações.');
  });
});
