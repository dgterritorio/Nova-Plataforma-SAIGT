import { Component, signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { Observable, Subject, of, throwError } from 'rxjs';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { AuthService } from '../../../core/auth/auth.service';
import { ApiError } from '../../../core/models/error-response.model';
import { PageResponse } from '../../../core/models/page-response.model';
import {
  CriarMensagemNotificacaoResposta,
  NotificacaoDetalhe,
  NotificacaoFiltro,
  NotificacaoResumo,
} from '../../../models/notificacao.model';
import { LOCALIZACAO } from '../../../services/descarga.service';
import { NotificacaoService } from '../../../services/notificacao.service';
import { NotificacoesNaoLidasService } from '../../../services/notificacoes-nao-lidas.service';
import { ROUTES } from '../../../shared/routes';
import { AVISO_REMOVER_NOTICIA, GestaoNotificacoesComponent } from './gestao-notificacoes.component';

@Component({ template: '' })
class DestinoComponent {}

const PROCESSO = 'a1b2c3d4-0000-4000-8000-000000000001';

const RESUMO: NotificacaoResumo = {
  id: 'n1',
  tipo: 'PEDIDO_APOIO',
  titulo: 'Pedido de apoio — acesso à plataforma',
  texto: 'Não consigo submeter.',
  processoId: null,
  numeroProcesso: null,
  idIgtSnit: null,
  descricaoIgt: null,
  prazo: null,
  estado: 'ATIVA',
  criadoEm: '2026-07-20T10:05:00Z',
  lidoEm: null,
  resolvidoEm: null,
  criadoPorNome: 'Rui Fictício',
  numeroMensagens: 0,
  numeroDocumentos: 0,
};

function detalhe(parcial: Partial<NotificacaoDetalhe> = {}): NotificacaoDetalhe {
  return {
    ...RESUMO,
    perfilDestino: 'MASTER',
    destinatarioUtilizadorNome: null,
    destinatarioEntidadeNome: null,
    mensagens: [],
    documentos: [],
    acoesDisponiveis: ['RESPONDER'],
    ...parcial,
  };
}

function pagina(itens: NotificacaoResumo[], total = itens.length): PageResponse<NotificacaoResumo> {
  return { content: itens, page: 0, size: 20, totalElements: total, totalPages: Math.ceil(total / 20) };
}

const CONTADORES: Record<string, number> = { PEDIDO_APOIO: 3, REGULARIZACAO_PROCEDIMENTO: 1, ALERTA: 2, ESTATICA: 0 };

describe('GestaoNotificacoesComponent', () => {
  let fixture: ComponentFixture<GestaoNotificacoesComponent>;
  let listar: ReturnType<typeof vi.fn>;
  let obter: ReturnType<typeof vi.fn>;
  let responder: ReturnType<typeof vi.fn>;
  let ler: ReturnType<typeof vi.fn>;
  let resolver: ReturnType<typeof vi.fn>;
  let remover: ReturnType<typeof vi.fn>;
  let urlDownload: ReturnType<typeof vi.fn>;
  let assign: ReturnType<typeof vi.fn>;
  let atualizarNaoLidas: ReturnType<typeof vi.fn>;
  let linhas: NotificacaoResumo[];
  let contadores: Record<string, number>;

  const el = () => fixture.nativeElement as HTMLElement;
  const texto = (seletor: string) => el().querySelector(seletor)?.textContent?.replace(/\s+/g, ' ').trim();
  const modal = () => document.querySelector<HTMLElement>('[role="dialog"]');
  const pedidosLista = () => listar.mock.calls.filter((chamada) => chamada[2] === 20);
  const pedidosContadores = () => listar.mock.calls.filter((chamada) => chamada[2] === 1);

  function respostaLista(filtro: NotificacaoFiltro, _pagina: number, tamanho: number): Observable<PageResponse<NotificacaoResumo>> {
    if (tamanho === 1) {
      return of(pagina([], contadores[filtro.tipo![0]] ?? 0));
    }
    return of(pagina(linhas));
  }

  async function criar(): Promise<void> {
    fixture = TestBed.createComponent(GestaoNotificacoesComponent);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
  }

  async function abrirLinha(indice = 0): Promise<void> {
    el().querySelectorAll<HTMLTableRowElement>('app-tabela tbody tr')[indice].click();
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
  }

  function escrever(valor: string): HTMLTextAreaElement {
    const campo = modal()!.querySelector<HTMLTextAreaElement>('textarea')!;
    campo.value = valor;
    campo.dispatchEvent(new Event('input'));
    fixture.detectChanges();
    return campo;
  }

  function botao(rotulo: string): HTMLButtonElement | undefined {
    return [...(modal()?.querySelectorAll<HTMLButtonElement>('button') ?? [])].find((b) => b.textContent?.trim() === rotulo);
  }

  /** O modal de confirmação abre por cima do detalhe: é o último diálogo do documento. */
  const confirmacao = () => {
    const dialogos = document.querySelectorAll<HTMLElement>('[role="dialog"]');
    return dialogos.length > 1 ? dialogos[dialogos.length - 1] : null;
  };

  function botaoConfirmacao(rotulo: string): HTMLButtonElement | undefined {
    return [...(confirmacao()?.querySelectorAll<HTMLButtonElement>('button') ?? [])].find((b) => b.textContent?.trim() === rotulo);
  }

  const operacoes = () =>
    [...(modal()?.querySelectorAll<HTMLButtonElement>('[role="group"][aria-label="Ações da notificação"] button') ?? [])].map((b) =>
      b.textContent?.trim(),
    );

  beforeEach(() => {
    linhas = [RESUMO];
    contadores = { ...CONTADORES };
    listar = vi.fn(respostaLista);
    obter = vi.fn().mockReturnValue(of(detalhe()));
    responder = vi.fn();
    ler = vi.fn();
    resolver = vi.fn();
    remover = vi.fn();
    urlDownload = vi.fn();
    assign = vi.fn();
    atualizarNaoLidas = vi.fn();
    TestBed.configureTestingModule({
      imports: [GestaoNotificacoesComponent],
      providers: [
        provideRouter([{ path: 'processos/:id', component: DestinoComponent }]),
        { provide: NotificacaoService, useValue: { listar, obter, responder, ler, resolver, remover, urlDownload } },
        { provide: NotificacoesNaoLidasService, useValue: { naoLidas: signal(0), atualizar: atualizarNaoLidas } },
        { provide: LOCALIZACAO, useValue: { assign } },
        {
          provide: AuthService,
          useValue: {
            perfilInterno: vi.fn().mockReturnValue('MASTER'),
            nome: vi.fn().mockReturnValue('Marta Master'),
            origem: vi.fn().mockReturnValue('AD_DGT'),
            entidade: vi.fn().mockReturnValue(null),
          },
        },
      ],
    });
  });

  afterEach(() => {
    fixture?.destroy();
    vi.useRealTimers();
  });

  describe('fila e separadores', () => {
    it('mostra o título, os 4 separadores com os contadores de ativas e lista os pedidos de apoio sem filtro de estado', async () => {
      await criar();

      expect(texto('h1')).toBe('Gestão de Notificações');
      expect(texto('.subtitulo')).toBe('Pedidos de apoio, regularizações, alertas gerais e notícias');
      expect(el().querySelector('[role="tablist"]')?.getAttribute('aria-label')).toBe('Tipos de notificação');
      const separadores = [...el().querySelectorAll('[role="tab"]')].map((tab) => tab.textContent?.replace(/\s+/g, ' ').trim());
      expect(separadores).toEqual(['Pedidos de apoio 3', 'Regularizações 1', 'Alertas gerais 2', 'Notícias 0']);
      expect(el().querySelector('[role="tab"][aria-selected="true"]')?.textContent).toContain('Pedidos de apoio');

      expect(pedidosLista()).toEqual([[{ tipo: ['PEDIDO_APOIO'] }, 0, 20]]);
      expect(pedidosContadores().map(([filtro]) => filtro)).toEqual([
        { tipo: ['PEDIDO_APOIO'], estado: ['ATIVA'] },
        { tipo: ['REGULARIZACAO_PROCEDIMENTO'], estado: ['ATIVA'] },
        { tipo: ['ALERTA'], estado: ['ATIVA'] },
        { tipo: ['ESTATICA'], estado: ['ATIVA'] },
      ]);
      expect(pedidosContadores().every((chamada) => chamada[1] === 0)).toBe(true);
    });

    it('o painel do separador ativo é um tabpanel ligado ao separador', async () => {
      await criar();

      const painel = el().querySelector('[role="tabpanel"]')!;
      const ativo = el().querySelector('[role="tab"][aria-selected="true"]')!;
      expect(painel.getAttribute('aria-labelledby')).toBe(ativo.id);
      expect(ativo.getAttribute('aria-controls')).toBe(painel.id);
    });

    it('cada separador pede o seu tipo, a partir da 1.ª página', async () => {
      await criar();

      const tabs = el().querySelectorAll<HTMLButtonElement>('[role="tab"]');
      for (const [indice, tipo] of (['REGULARIZACAO_PROCEDIMENTO', 'ALERTA', 'ESTATICA'] as const).entries()) {
        tabs[indice + 1].click();
        fixture.detectChanges();
        expect(pedidosLista().at(-1)).toEqual([{ tipo: [tipo] }, 0, 20]);
      }
    });

    it('as colunas mostram título, remetente (ou o sistema), processo, data em Lisboa, estado, mensagens e anexos', async () => {
      linhas = [
        { ...RESUMO, processoId: PROCESSO, numeroProcesso: 'PDM-2026-0001', numeroMensagens: 2, numeroDocumentos: 1 },
        { ...RESUMO, id: 'n2', criadoPorNome: null, descricaoIgt: 'PDM de Exemplo', estado: 'RESOLVIDA' },
        { ...RESUMO, id: 'n3', tipo: 'ESTATICA', criadoPorNome: null },
      ];
      await criar();

      const celulas = [...el().querySelectorAll('app-tabela tbody tr')].map((tr) =>
        [...tr.querySelectorAll('td')].map((td) => td.textContent?.replace(/\s+/g, ' ').trim()),
      );
      expect(celulas[0]).toEqual(['Pedido de apoio — acesso à plataforma', 'Rui Fictício', 'PDM-2026-0001', '20/07/2026 11:05', 'Pend. Resposta', '2', '1']);
      expect(celulas[1]).toEqual(['Pedido de apoio — acesso à plataforma', 'SAIGT — Sistema', 'PDM de Exemplo', '20/07/2026 11:05', 'Resolvida', '0', '0']);
      expect(celulas[2][2]).toBe('—');
      expect(celulas[2][4]).toBe('Ativa');
      expect(el().querySelector('app-tabela tbody tr a')?.getAttribute('href')).toBe(ROUTES.PROCESSO_DETALHE(PROCESSO));
    });

    it('a ligação do processo abre o detalhe do processo sem abrir o modal', async () => {
      linhas = [{ ...RESUMO, processoId: PROCESSO, numeroProcesso: 'PDM-2026-0001' }];
      await criar();
      const router = TestBed.inject(Router);

      el().querySelector<HTMLAnchorElement>('app-tabela tbody tr a')!.click();
      await fixture.whenStable();

      expect(router.url).toBe(ROUTES.PROCESSO_DETALHE(PROCESSO));
      expect(obter).not.toHaveBeenCalled();
    });

    it('em ecrãs estreitos a tabela desliza na horizontal em vez de espremer as colunas', async () => {
      await criar();
      expect(el().querySelector<HTMLTableElement>('app-tabela table')!.style.minWidth).toBe('880px');
    });

    it('o contador da listagem concorda em número', async () => {
      await criar();
      expect(texto('.barra-filtros .contador')).toBe('1 notificação');
    });

    it('sem linhas mostra «Sem notificações.»', async () => {
      linhas = [];
      await criar();
      expect(texto('.barra-filtros .contador')).toBe('0 notificações');
      expect(texto('app-tabela tbody')).toBe('Sem notificações.');
    });

    it('erro a carregar mostra o alerta e «Tentar de novo» volta a pedir', async () => {
      listar.mockImplementation((filtro: NotificacaoFiltro, p: number, tamanho: number) =>
        tamanho === 20 ? throwError(() => new ApiError('ERRO_INTERNO', 'Serviço indisponível.', 't', null)) : respostaLista(filtro, p, tamanho),
      );
      await criar();

      expect(el().querySelector('app-alerta[role="alert"]')?.textContent).toContain('Serviço indisponível.');
      expect(el().querySelector('app-tabela')).toBeNull();
      listar.mockImplementation(respostaLista);
      [...el().querySelectorAll<HTMLButtonElement>('button')].find((b) => b.textContent?.trim() === 'Tentar de novo')!.click();
      fixture.detectChanges();

      expect(pedidosLista()).toHaveLength(2);
      expect(el().querySelector('app-tabela')).not.toBeNull();
    });

    it('a paginação tem 20 por página e pede a página escolhida', async () => {
      listar.mockImplementation((filtro: NotificacaoFiltro, p: number, tamanho: number) =>
        tamanho === 20 ? of({ ...pagina(linhas, 45), page: p }) : respostaLista(filtro, p, tamanho),
      );
      await criar();

      fixture.componentInstance.irParaPagina(2);

      expect(pedidosLista().at(-1)).toEqual([{ tipo: ['PEDIDO_APOIO'] }, 2, 20]);
    });
  });

  describe('filtro Histórico', () => {
    it('pede LIDA, RESOLVIDA e REMOVIDA e as linhas abrem só em leitura', async () => {
      obter.mockReturnValue(of(detalhe({ estado: 'RESOLVIDA', acoesDisponiveis: ['RESPONDER'] })));
      await criar();

      fixture.componentInstance.filtroEstadoControlo.setValue('HISTORICO');
      fixture.detectChanges();
      expect(pedidosLista().at(-1)).toEqual([{ tipo: ['PEDIDO_APOIO'], estado: ['LIDA', 'RESOLVIDA', 'REMOVIDA'] }, 0, 20]);

      await abrirLinha();
      expect(modal()).not.toBeNull();
      expect(modal()!.querySelector('form')).toBeNull();
      expect(modal()!.querySelector('textarea')).toBeNull();
    });

    it('o seletor «Estado» tem as opções Ativas e Histórico', async () => {
      await criar();

      const opcoes = [...el().querySelectorAll('app-seletor option:not([disabled])')].map((o) => o.textContent?.trim());
      expect(opcoes).toEqual(['Ativas', 'Histórico']);
      expect(el().querySelector('app-seletor label')?.textContent).toContain('Estado');
    });
  });

  describe('detalhe', () => {
    it('a linha abre o modal com o título, o texto, a IGT, o prazo e o histórico por ordem', async () => {
      obter.mockReturnValue(
        of(
          detalhe({
            idIgtSnit: 'IGT-123',
            descricaoIgt: 'PDM de Exemplo',
            prazo: '2026-08-01T10:00:00Z',
            mensagens: [
              { id: 'm1', autorId: 'u1', autorNome: 'Rui Fictício', texto: 'Primeira', criadoEm: '2026-07-20T10:05:00Z' },
              { id: 'm2', autorId: 'u2', autorNome: 'Marta Master', texto: 'Segunda', criadoEm: '2026-07-20T11:00:00Z' },
            ],
            documentos: [{ id: 'd1', nomeFicheiro: 'comprovativo.pdf', tamanhoBytes: 2048, criadoEm: '2026-07-20T10:05:00Z' }],
          }),
        ),
      );
      await criar();
      await abrirLinha();

      expect(obter).toHaveBeenCalledWith('n1');
      expect(modal()!.querySelector('h2')?.textContent?.trim()).toBe('Pedido de apoio — acesso à plataforma');
      expect(modal()!.querySelector('.texto-notificacao')?.textContent).toBe('Não consigo submeter.');
      expect(modal()!.textContent).toContain('IGT-123');
      expect(modal()!.textContent).toContain('PDM de Exemplo');
      expect(modal()!.textContent).toContain('2026-08-01');
      expect([...modal()!.querySelectorAll('.mensagem-texto')].map((m) => m.textContent)).toEqual(['Primeira', 'Segunda']);
      expect(modal()!.querySelector('.documento-nome')?.textContent).toBe('comprovativo.pdf');
    });

    it('marcação HTML no título, no texto e nas mensagens aparece como texto', async () => {
      const malicioso = '<img src=x onerror="alert(1)"><b>negrito</b>';
      obter.mockReturnValue(
        of(
          detalhe({
            texto: malicioso,
            mensagens: [{ id: 'm1', autorId: 'u1', autorNome: 'Rui', texto: malicioso, criadoEm: '2026-07-20T10:05:00Z' }],
          }),
        ),
      );
      linhas = [{ ...RESUMO, titulo: malicioso }];
      await criar();

      expect(el().querySelector('app-tabela tbody img, app-tabela tbody b')).toBeNull();
      expect(texto('.celula-titulo')).toBe(malicioso);
      await abrirLinha();
      expect(modal()!.querySelector('img, b')).toBeNull();
      expect(modal()!.querySelector('.texto-notificacao')?.textContent).toBe(malicioso);
      expect(modal()!.querySelector('.mensagem-texto')?.textContent).toBe(malicioso);
    });

    it('sem ações em acoesDisponiveis não há caixa de resposta nem botões de ação', async () => {
      obter.mockReturnValue(of(detalhe({ acoesDisponiveis: [] })));
      await criar();
      await abrirLinha();

      expect(modal()!.querySelector('form, textarea')).toBeNull();
      const rotulos = [...modal()!.querySelectorAll('button')].map((b) => b.textContent?.trim() || b.getAttribute('aria-label'));
      expect(rotulos).toEqual(['Fechar', 'Fechar']);
    });

    it('se a linha que abriu o modal saiu da lista, o foco vai para o painel ao fechar', async () => {
      vi.useFakeTimers({ shouldAdvanceTime: true });
      await criar();
      await abrirLinha();
      linhas = [];
      fixture.componentInstance.irParaPagina(0);
      fixture.detectChanges();

      botao('Fechar')!.click();
      fixture.detectChanges();
      vi.runOnlyPendingTimers();

      expect(document.activeElement).toBe(el().querySelector('[role="tabpanel"]'));
    });

    it('fechar o modal limpa a seleção', async () => {
      await criar();
      await abrirLinha();

      botao('Fechar')!.click();
      fixture.detectChanges();

      expect(modal()).toBeNull();
      expect(fixture.componentInstance.selecionada()).toBeNull();
    });
  });

  describe('responder', () => {
    it('com a resposta vazia o botão está desativado e o erro de obrigatório fica associado ao campo', async () => {
      await criar();
      await abrirLinha();

      expect(botao('Responder')!.disabled).toBe(true);
      const campo = escrever('   ');
      campo.dispatchEvent(new Event('blur'));
      campo.dispatchEvent(new FocusEvent('focusout', { bubbles: true }));
      fixture.detectChanges();

      expect(botao('Responder')!.disabled).toBe(true);
      expect(campo.getAttribute('aria-invalid')).toBe('true');
      const erro = modal()!.querySelector('.campo-texto-erro')!;
      expect(erro.textContent?.trim()).toBe('A resposta é obrigatória.');
      expect(campo.getAttribute('aria-describedby')?.split(' ')).toContain(erro.id);
      expect(responder).not.toHaveBeenCalled();
    });

    it('num pedido de apoio faz o POST, a mensagem entra no histórico, o selo passa a Resolvida e o contador desce', async () => {
      vi.useFakeTimers({ shouldAdvanceTime: true });
      const resposta: CriarMensagemNotificacaoResposta = {
        mensagem: { id: 'm9', autorId: 'u2', autorNome: 'Marta Master', texto: 'Já está tratado.', criadoEm: '2026-07-20T12:00:00Z' },
        estado: 'RESOLVIDA',
      };
      responder.mockReturnValue(of(resposta));
      await criar();
      await abrirLinha();

      obter.mockReturnValue(of(detalhe({ estado: 'RESOLVIDA', acoesDisponiveis: [], mensagens: [resposta.mensagem], numeroMensagens: 1 })));
      contadores = { ...CONTADORES, PEDIDO_APOIO: 2 };
      linhas = [];
      escrever('  Já está tratado.  ');
      botao('Responder')!.click();
      fixture.detectChanges();

      expect(responder).toHaveBeenCalledWith('n1', 'Já está tratado.');
      expect(modal()!.querySelector('.mensagem-texto')?.textContent).toBe('Já está tratado.');
      expect(modal()!.querySelector('.selo-detalhe')?.textContent?.trim()).toBe('Resolvida');
      expect(el().querySelector('[role="tab"]')?.textContent?.replace(/\s+/g, ' ').trim()).toBe('Pedidos de apoio 2');
      expect(obter).toHaveBeenCalledTimes(2);
      expect(pedidosLista()).toHaveLength(2);
      expect(pedidosContadores()).toHaveLength(8);

      const sucesso = modal()!.querySelector('.anuncio app-alerta');
      expect(modal()!.querySelector('.anuncio')?.getAttribute('aria-live')).toBe('polite');
      expect(sucesso?.textContent).toContain('O pedido de apoio ficou resolvido.');
      expect(sucesso?.getAttribute('role')).toBe('status');
      vi.advanceTimersByTime(4000);
      fixture.detectChanges();
      expect(modal()!.querySelector('.anuncio app-alerta')).toBeNull();
    });

    it('numa regularização o botão é «Pedir mais informação», continua Pend. Resposta e não há resolver', async () => {
      const regularizacao = detalhe({ tipo: 'REGULARIZACAO_PROCEDIMENTO', perfilDestino: 'MASTER' });
      obter.mockReturnValue(of(regularizacao));
      responder.mockReturnValue(
        of({
          mensagem: { id: 'm9', autorId: 'u2', autorNome: 'Marta Master', texto: 'Falta a ata.', criadoEm: '2026-07-20T12:00:00Z' },
          estado: 'ATIVA',
        }),
      );
      linhas = [{ ...RESUMO, tipo: 'REGULARIZACAO_PROCEDIMENTO' }];
      await criar();
      await abrirLinha();

      expect(botao('Responder')).toBeUndefined();
      expect(modal()!.querySelector('.nota-regularizacao')?.textContent).toBe('Esta notificação resolve-se com o registo do procedimento.');
      obter.mockReturnValue(
        of({
          ...regularizacao,
          mensagens: [{ id: 'm9', autorId: 'u2', autorNome: 'Marta Master', texto: 'Falta a ata.', criadoEm: '2026-07-20T12:00:00Z' }],
          numeroMensagens: 1,
        }),
      );
      escrever('Falta a ata.');
      botao('Pedir mais informação')!.click();
      fixture.detectChanges();

      expect(responder).toHaveBeenCalledWith('n1', 'Falta a ata.');
      expect(modal()!.querySelector('.mensagem-texto')?.textContent).toBe('Falta a ata.');
      expect(modal()!.querySelector('.selo-detalhe')?.textContent?.trim()).toBe('Pend. Resposta');
      expect([...modal()!.querySelectorAll('button')].some((b) => /resolver/i.test(b.textContent ?? ''))).toBe(false);
      expect(modal()!.querySelector('.anuncio')?.textContent).toContain('Pedido de mais informação enviado.');
    });

    it.each([
      ['NOTIF_ESTADO_INVALIDO', 'A notificação já não está ativa.'],
      ['NOTIF_PERFIL_NAO_AUTORIZADO', 'Não pode responder a esta notificação.'],
      ['NOTIF_NAO_ENCONTRADA', 'Notificação não encontrada.'],
    ])('com %s mostra a mensagem do envelope no modal e relê a linha', async (codigo, mensagem) => {
      responder.mockReturnValue(throwError(() => new ApiError(codigo, mensagem, 't', null)));
      await criar();
      await abrirLinha();
      obter.mockReturnValue(of(detalhe({ estado: 'RESOLVIDA', acoesDisponiveis: [] })));

      escrever('Resposta tardia.');
      botao('Responder')!.click();
      fixture.detectChanges();

      expect(modal()!.querySelector('app-error-banner [role="alert"]')?.textContent?.trim()).toBe(mensagem);
      expect(obter).toHaveBeenCalledTimes(2);
      expect(pedidosLista()).toHaveLength(2);
      expect(modal()!.querySelector('.selo-detalhe')?.textContent?.trim()).toBe('Resolvida');
      expect(modal()!.querySelector('form')).toBeNull();
    });

    it('um 400 de validação do texto fica no campo, sem banner', async () => {
      responder.mockReturnValue(
        throwError(() => new ApiError('VALIDACAO', 'Dados inválidos.', 't', [{ path: 'texto', erro: 'Texto demasiado longo.' }])),
      );
      await criar();
      await abrirLinha();

      escrever('Algo');
      botao('Responder')!.click();
      fixture.detectChanges();

      expect(modal()!.querySelector('.campo-texto-erro')?.textContent?.trim()).toBe('Texto demasiado longo.');
      expect(modal()!.querySelector('app-error-banner [role="alert"]')).toBeNull();
    });

    it('não envia duas vezes enquanto o pedido está em curso', async () => {
      const emCurso = new Subject<CriarMensagemNotificacaoResposta>();
      responder.mockReturnValue(emCurso);
      await criar();
      await abrirLinha();

      escrever('Uma vez');
      fixture.componentInstance.responder();
      fixture.componentInstance.responder();

      expect(responder).toHaveBeenCalledTimes(1);
    });
  });

  describe('ler, resolver e remover', () => {
    const ALERTA: NotificacaoResumo = {
      ...RESUMO,
      id: 'a1',
      tipo: 'ALERTA',
      titulo: 'Falha na integração com o SNIT',
      criadoPorNome: null,
    };
    const NOTICIA: NotificacaoResumo = { ...RESUMO, id: 'e1', tipo: 'ESTATICA', titulo: 'Manutenção programada' };

    it('num pedido de apoio com RESOLVER pede confirmação, faz o PATCH …/resolver e o pedido fica Resolvida', async () => {
      obter.mockReturnValue(of(detalhe({ acoesDisponiveis: ['RESPONDER', 'RESOLVER'] })));
      resolver.mockReturnValue(of({ ...RESUMO, estado: 'RESOLVIDA', resolvidoEm: '2026-07-20T12:00:00Z' }));
      await criar();
      await abrirLinha();

      expect(operacoes()).toEqual(['Resolver sem responder']);
      botao('Resolver sem responder')!.click();
      fixture.detectChanges();
      expect(confirmacao()?.querySelector('h2')?.textContent?.trim()).toBe('Resolver sem responder');
      expect(confirmacao()?.textContent).toContain('Resolver este pedido de apoio sem enviar resposta?');
      expect(resolver).not.toHaveBeenCalled();

      obter.mockReturnValue(of(detalhe({ estado: 'RESOLVIDA', acoesDisponiveis: [] })));
      contadores = { ...CONTADORES, PEDIDO_APOIO: 2 };
      linhas = [];
      botaoConfirmacao('Resolver')!.click();
      fixture.detectChanges();

      expect(resolver).toHaveBeenCalledWith('n1');
      expect(confirmacao()).toBeNull();
      expect(modal()!.querySelector('.selo-detalhe')?.textContent?.trim()).toBe('Resolvida');
      expect(operacoes()).toEqual([]);
      expect(modal()!.querySelector('form')).toBeNull();
      expect(modal()!.querySelector('.anuncio app-alerta')?.textContent).toContain('Pedido de apoio resolvido.');
      expect(obter).toHaveBeenCalledTimes(2);
      expect(pedidosLista()).toHaveLength(2);
      expect(pedidosContadores()).toHaveLength(8);
      expect(el().querySelector('[role="tab"]')?.textContent?.replace(/\s+/g, ' ').trim()).toBe('Pedidos de apoio 2');
    });

    it('cancelar a confirmação não faz o pedido e mantém o botão', async () => {
      obter.mockReturnValue(of(detalhe({ acoesDisponiveis: ['RESPONDER', 'RESOLVER'] })));
      await criar();
      await abrirLinha();

      botao('Resolver sem responder')!.click();
      fixture.detectChanges();
      botaoConfirmacao('Cancelar')!.click();
      fixture.detectChanges();

      expect(confirmacao()).toBeNull();
      expect(resolver).not.toHaveBeenCalled();
      expect(operacoes()).toEqual(['Resolver sem responder']);
    });

    it('num alerta com LER e REMOVER, «Marcar como lido» faz o PATCH …/ler sem confirmação e o alerta sai das ativas', async () => {
      vi.useFakeTimers({ shouldAdvanceTime: true });
      linhas = [ALERTA];
      obter.mockReturnValue(of(detalhe({ ...ALERTA, acoesDisponiveis: ['LER', 'REMOVER'] })));
      ler.mockReturnValue(of({ ...ALERTA, estado: 'LIDA', lidoEm: '2026-07-20T12:00:00Z' }));
      await criar();
      fixture.componentInstance.mudarSeparador('ALERTA');
      fixture.detectChanges();
      await abrirLinha();

      expect(operacoes()).toEqual(['Marcar como lido', 'Descartar']);
      obter.mockReturnValue(of(detalhe({ ...ALERTA, estado: 'LIDA', acoesDisponiveis: [] })));
      contadores = { ...CONTADORES, ALERTA: 1 };
      linhas = [];
      botao('Marcar como lido')!.click();
      fixture.detectChanges();

      expect(ler).toHaveBeenCalledWith('a1');
      expect(confirmacao()).toBeNull();
      expect(remover).not.toHaveBeenCalled();
      expect(modal()!.querySelector('.selo-detalhe')?.textContent?.trim()).toBe('Lida');
      expect(operacoes()).toEqual([]);
      expect(modal()!.querySelector('.anuncio app-alerta')?.textContent).toContain('Alerta marcado como lido.');
      expect(pedidosLista().at(-1)).toEqual([{ tipo: ['ALERTA'] }, 0, 20]);
      expect(el().querySelector('app-tabela')?.textContent).toContain('Sem notificações.');
      expect(atualizarNaoLidas).toHaveBeenCalled();
      vi.runOnlyPendingTimers();
      expect(document.activeElement).toBe(modal()!.querySelector('.selos'));
    });

    it('num alerta, «Descartar» pede confirmação e faz o PATCH …/remover', async () => {
      linhas = [ALERTA];
      obter.mockReturnValue(of(detalhe({ ...ALERTA, acoesDisponiveis: ['LER', 'REMOVER'] })));
      remover.mockReturnValue(of({ linhasAtualizadas: 1 }));
      await criar();
      fixture.componentInstance.mudarSeparador('ALERTA');
      fixture.detectChanges();
      await abrirLinha();

      botao('Descartar')!.click();
      fixture.detectChanges();
      expect(confirmacao()?.querySelector('h2')?.textContent?.trim()).toBe('Descartar alerta');
      expect(confirmacao()?.textContent).toContain('Descartar este alerta?');
      expect(confirmacao()?.textContent).not.toContain(AVISO_REMOVER_NOTICIA);
      obter.mockReturnValue(of(detalhe({ ...ALERTA, estado: 'REMOVIDA', acoesDisponiveis: [] })));
      botaoConfirmacao('Descartar')!.click();
      fixture.detectChanges();

      expect(remover).toHaveBeenCalledWith('a1');
      expect(ler).not.toHaveBeenCalled();
      expect(modal()!.querySelector('.selo-detalhe')?.textContent?.trim()).toBe('Removida');
      expect(modal()!.querySelector('.anuncio app-alerta')?.textContent).toContain('Alerta descartado.');
    });

    it('numa notícia de lote, «Remover notícia» avisa que sai para todos os destinatários, faz o PATCH …/remover e passa ao histórico', async () => {
      linhas = [NOTICIA];
      obter.mockReturnValue(of(detalhe({ ...NOTICIA, acoesDisponiveis: ['REMOVER'] })));
      remover.mockReturnValue(of({ linhasAtualizadas: 2 }));
      await criar();
      fixture.componentInstance.mudarSeparador('ESTATICA');
      fixture.detectChanges();
      await abrirLinha();

      expect(operacoes()).toEqual(['Remover notícia']);
      botao('Remover notícia')!.click();
      fixture.detectChanges();
      expect(confirmacao()?.querySelector('h2')?.textContent?.trim()).toBe('Remover notícia');
      expect(confirmacao()?.textContent).toContain(AVISO_REMOVER_NOTICIA);
      expect(confirmacao()?.getAttribute('aria-describedby')).toBeTruthy();

      obter.mockReturnValue(of(detalhe({ ...NOTICIA, estado: 'REMOVIDA', acoesDisponiveis: [] })));
      linhas = [];
      botaoConfirmacao('Remover')!.click();
      fixture.detectChanges();

      expect(remover).toHaveBeenCalledWith('e1');
      expect(modal()!.querySelector('.selo-detalhe')?.textContent?.trim()).toBe('Removida');
      expect(modal()!.querySelector('.anuncio app-alerta')?.textContent).toContain('Notícia removida para todos os destinatários.');
      expect(pedidosLista().at(-1)).toEqual([{ tipo: ['ESTATICA'] }, 0, 20]);
    });

    it.each([
      ['resolver', ['RESPONDER', 'RESOLVER'], 'Resolver sem responder', 'Resolver', 'NOTIF_ESTADO_INVALIDO', 'A notificação já não está ativa.'],
      ['ler', ['LER', 'REMOVER'], 'Marcar como lido', null, 'NOTIF_ESTADO_INVALIDO', 'A notificação já não está ativa.'],
      ['remover', ['LER', 'REMOVER'], 'Descartar', 'Descartar', 'NOTIF_TIPO_NAO_REMOVIVEL', 'Este tipo de notificação não pode ser removido.'],
      ['resolver', ['RESPONDER', 'RESOLVER'], 'Resolver sem responder', 'Resolver', 'NOTIF_PERFIL_NAO_AUTORIZADO', 'Só um utilizador Master pode resolver esta notificação.'],
      ['ler', ['LER', 'REMOVER'], 'Marcar como lido', null, 'NOTIF_NAO_ENCONTRADA', 'A notificação não existe ou não está disponível.'],
    ] as const)('%s com %s: %s → %s, com %s mostra a mensagem do envelope no modal e relê a linha', async (operacao, acoes, rotulo, rotuloSim, codigo, mensagem) => {
      const servico = { resolver, ler, remover }[operacao];
      servico.mockReturnValue(throwError(() => new ApiError(codigo, mensagem, 't', null)));
      obter.mockReturnValue(of(detalhe({ acoesDisponiveis: [...acoes] })));
      await criar();
      await abrirLinha();
      obter.mockReturnValue(of(detalhe({ estado: 'RESOLVIDA', acoesDisponiveis: [] })));

      botao(rotulo)!.click();
      fixture.detectChanges();
      if (rotuloSim) {
        botaoConfirmacao(rotuloSim)!.click();
        fixture.detectChanges();
      }

      expect(servico).toHaveBeenCalledWith('n1');
      expect(confirmacao()).toBeNull();
      expect(modal()!.querySelector('app-error-banner [role="alert"]')?.textContent?.trim()).toBe(mensagem);
      expect(obter).toHaveBeenCalledTimes(2);
      expect(pedidosLista()).toHaveLength(2);
      expect(modal()!.querySelector('.selo-detalhe')?.textContent?.trim()).toBe('Resolvida');
      expect(operacoes()).toEqual([]);
    });

    it('não repete a operação enquanto o pedido está em curso', async () => {
      linhas = [ALERTA];
      obter.mockReturnValue(of(detalhe({ ...ALERTA, acoesDisponiveis: ['LER', 'REMOVER'] })));
      ler.mockReturnValue(new Subject());
      await criar();
      await abrirLinha();

      fixture.componentInstance.marcarComoLido();
      fixture.componentInstance.marcarComoLido();
      fixture.detectChanges();

      expect(ler).toHaveBeenCalledTimes(1);
      expect(botao('Descartar')!.disabled).toBe(true);
    });

    it('no histórico não há botões de ler, resolver nem remover, mesmo que a API os ofereça', async () => {
      obter.mockReturnValue(of(detalhe({ ...ALERTA, estado: 'LIDA', acoesDisponiveis: ['LER', 'RESOLVER', 'REMOVER'] })));
      await criar();
      fixture.componentInstance.filtroEstadoControlo.setValue('HISTORICO');
      fixture.detectChanges();
      await abrirLinha();

      expect(operacoes()).toEqual([]);
      fixture.componentInstance.marcarComoLido();
      expect(ler).not.toHaveBeenCalled();
    });

    it('cada ação só corre com a sua entrada em acoesDisponiveis', async () => {
      obter.mockReturnValue(of(detalhe({ acoesDisponiveis: ['RESPONDER'] })));
      await criar();
      await abrirLinha();

      expect(operacoes()).toEqual([]);
      fixture.componentInstance.pedirConfirmacao('RESOLVER');
      fixture.detectChanges();
      botaoConfirmacao('Resolver')!.click();
      fixture.detectChanges();
      expect(resolver).not.toHaveBeenCalled();
    });
  });

  describe('descarregar anexos', () => {
    const COM_ANEXO = detalhe({
      acoesDisponiveis: [],
      documentos: [{ id: 'd1', nomeFicheiro: 'comprovativo.pdf', tamanhoBytes: 2048, criadoEm: '2026-07-20T10:05:00Z' }],
      numeroDocumentos: 1,
    });
    const botaoDescarregar = () => modal()!.querySelector<HTMLButtonElement>('button[aria-label="Descarregar comprovativo.pdf"]')!;

    it('«Descarregar {nome}» pede o URL com ticket (API-NOTIF-010) e navega logo para ele', async () => {
      obter.mockReturnValue(of(COM_ANEXO));
      urlDownload.mockReturnValue(of({ url: 'https://saigt.local/api/v1/descargas/tkt' }));
      await criar();
      await abrirLinha();

      expect(botaoDescarregar().textContent).toBe('comprovativo.pdf');
      botaoDescarregar().click();
      fixture.detectChanges();

      expect(urlDownload).toHaveBeenCalledWith('n1', 'd1');
      expect(assign).toHaveBeenCalledWith('https://saigt.local/api/v1/descargas/tkt');
      expect(modal()).not.toBeNull();
      expect(modal()!.querySelector('.erro-descarga')).toBeNull();
    });

    it('um erro da descarga mostra a mensagem do envelope e não navega', async () => {
      obter.mockReturnValue(of(COM_ANEXO));
      urlDownload.mockReturnValue(
        throwError(() => new ApiError('NOTIF_NAO_ENCONTRADA', 'A notificação não existe ou não está disponível.', 't', null)),
      );
      await criar();
      await abrirLinha();

      botaoDescarregar().click();
      fixture.detectChanges();

      expect(assign).not.toHaveBeenCalled();
      const erro = modal()!.querySelector('.erro-descarga');
      expect(erro?.textContent?.trim()).toBe('A notificação não existe ou não está disponível.');
      expect(erro?.getAttribute('role')).toBe('alert');
    });

    it('um erro sem envelope mostra a mensagem genérica da descarga', async () => {
      obter.mockReturnValue(of(COM_ANEXO));
      urlDownload.mockReturnValue(throwError(() => new Error('rede')));
      await criar();
      await abrirLinha();

      botaoDescarregar().click();
      fixture.detectChanges();

      expect(modal()!.querySelector('.erro-descarga')?.textContent?.trim()).toBe('Não foi possível descarregar o ficheiro. Tente novamente.');
    });

    it('no histórico os anexos também se descarregam (é só leitura)', async () => {
      obter.mockReturnValue(of({ ...COM_ANEXO, estado: 'RESOLVIDA' }));
      urlDownload.mockReturnValue(of({ url: 'https://saigt.local/api/v1/descargas/tkt2' }));
      await criar();
      fixture.componentInstance.filtroEstadoControlo.setValue('HISTORICO');
      fixture.detectChanges();
      await abrirLinha();

      botaoDescarregar().click();
      fixture.detectChanges();

      expect(assign).toHaveBeenCalledWith('https://saigt.local/api/v1/descargas/tkt2');
    });

    it('não pede dois tickets enquanto o primeiro está em curso', async () => {
      obter.mockReturnValue(of(COM_ANEXO));
      urlDownload.mockReturnValue(new Subject());
      await criar();
      await abrirLinha();

      botaoDescarregar().click();
      fixture.detectChanges();
      botaoDescarregar().click();
      fixture.detectChanges();

      expect(urlDownload).toHaveBeenCalledTimes(1);
      expect(botaoDescarregar().getAttribute('aria-busy')).toBe('true');
    });
  });
});
