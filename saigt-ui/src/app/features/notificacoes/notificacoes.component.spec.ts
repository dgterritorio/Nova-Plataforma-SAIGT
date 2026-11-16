import { Component, WritableSignal, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { Router, provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { Observable, of, throwError } from 'rxjs';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { AuthService } from '../../core/auth/auth.service';
import { ApiError } from '../../core/models/error-response.model';
import { PageResponse } from '../../core/models/page-response.model';
import { NotificacaoDetalhe, NotificacaoResumo } from '../../models/notificacao.model';
import { FILTRO_NAO_LIDAS, NotificacaoService } from '../../services/notificacao.service';
import { NotificacoesNaoLidasService } from '../../services/notificacoes-nao-lidas.service';
import { EntidadeService } from '../../services/entidade.service';
import { ProcessoService } from '../../services/processo.service';
import { UtilizadorService } from '../../services/utilizador.service';
import { ESPERA_PESQUISA_MS } from '../../shared/ui/campo-pesquisa/campo-pesquisa.component';
import { CriarNotificacaoModalComponent } from './criar-notificacao-modal/criar-notificacao-modal.component';
import { NotificacaoDetalheComponent } from './notificacao-detalhe/notificacao-detalhe.component';
import { MENSAGEM_NAO_ENCONTRADA, NotificacoesComponent } from './notificacoes.component';

@Component({ template: '' })
class PaginaVaziaComponent {}

const PROCESSO = 'a1b2c3d4-0000-4000-8000-000000000001';

function notificacao(id: string, campos: Partial<NotificacaoResumo> = {}): NotificacaoResumo {
  return {
    id,
    tipo: 'ESTATICA',
    titulo: `Notícia ${id}`,
    texto: 'Texto',
    processoId: null,
    numeroProcesso: null,
    idIgtSnit: null,
    descricaoIgt: null,
    prazo: null,
    estado: 'ATIVA',
    criadoEm: '2026-07-20T10:00:00Z',
    lidoEm: null,
    resolvidoEm: null,
    criadoPorNome: 'Ana Teixeira',
    numeroMensagens: 0,
    numeroDocumentos: 0,
    ...campos,
  };
}

const TEMPORARIA = notificacao('n1', { tipo: 'TEMPORARIA', titulo: 'Submissão confirmada — PDM Setúbal', criadoPorNome: null });
const APOIO_GERAL = notificacao('n2', { tipo: 'PEDIDO_APOIO', titulo: 'Impossibilidade de criar procedimento', criadoPorNome: 'Filipa Costa' });
const APOIO_PROCESSO = notificacao('n3', {
  tipo: 'PEDIDO_APOIO',
  titulo: 'Dúvida sobre responsável técnico',
  criadoPorNome: 'Carlos Mendes',
  processoId: PROCESSO,
  numeroProcesso: 'PDM-SIN-2024-003',
});
const COM_PRAZO = notificacao('n4', {
  tipo: 'RESPOSTA_OBRIGATORIA',
  titulo: 'Regularização necessária',
  prazo: '2026-08-20T00:00:00Z',
  numeroDocumentos: 1,
});
const TODAS = [TEMPORARIA, APOIO_GERAL, APOIO_PROCESSO, COM_PRAZO, ...['n5', 'n6', 'n7', 'n8'].map((id) => notificacao(id))];

function pagina(itens: NotificacaoResumo[], total = itens.length): PageResponse<NotificacaoResumo> {
  return { content: itens, page: 0, size: 20, totalElements: total, totalPages: 1 };
}

function paginaVazia<T>(): PageResponse<T> {
  return { content: [], page: 0, size: 100, totalElements: 0, totalPages: 0 };
}

function detalhe(resumo: NotificacaoResumo): NotificacaoDetalhe {
  return {
    ...resumo,
    perfilDestino: null,
    destinatarioUtilizadorNome: null,
    destinatarioEntidadeNome: null,
    mensagens: [],
    documentos: [],
    acoesDisponiveis: [],
  };
}

describe('NotificacoesComponent', () => {
  let harness: RouterTestingHarness;
  let router: Router;
  let listar: ReturnType<typeof vi.fn>;
  let obter: ReturnType<typeof vi.fn>;
  let naoLidas: WritableSignal<number>;
  let atualizarNaoLidas: ReturnType<typeof vi.fn>;
  let perfil: string | null;
  let listarProcessos: ReturnType<typeof vi.fn>;

  async function abrir(url = '/notificacoes'): Promise<void> {
    TestBed.configureTestingModule({
      providers: [
        provideRouter([
          { path: 'notificacoes', component: NotificacoesComponent },
          { path: '**', component: PaginaVaziaComponent },
        ]),
        { provide: NotificacaoService, useValue: { listar, obter, contarNaoLidas: () => of(naoLidas()) } },
        { provide: NotificacoesNaoLidasService, useValue: { naoLidas, atualizar: atualizarNaoLidas } },
        { provide: EntidadeService, useValue: { listar: () => of(paginaVazia()) } },
        { provide: UtilizadorService, useValue: { listar: () => of(paginaVazia()) } },
        { provide: ProcessoService, useValue: { listar: listarProcessos } },
        {
          provide: AuthService,
          useValue: {
            nome: () => 'Ana Teixeira',
            origem: () => 'AD_DGT',
            entidade: () => null,
            perfilInterno: () => perfil,
            logout: () => of(undefined),
          },
        },
      ],
    });
    router = TestBed.inject(Router);
    harness = await RouterTestingHarness.create();
    await harness.navigateByUrl(url, NotificacoesComponent);
    harness.detectChanges();
  }

  const el = () => harness.routeNativeElement as HTMLElement;
  const texto = (seletor: string) => el().querySelector(seletor)?.textContent?.replace(/\s+/g, ' ').trim();
  const itens = () => [...el().querySelectorAll<HTMLButtonElement>('button.item')];
  const item = (id: string) => el().querySelector<HTMLButtonElement>(`[data-notificacao="${id}"]`)!;
  const separador = (etiqueta: string) =>
    [...el().querySelectorAll<HTMLButtonElement>('[role="tab"]')].find((tab) => tab.textContent?.includes(etiqueta))!;

  async function estabilizar(): Promise<void> {
    await harness.fixture.whenStable();
    harness.detectChanges();
  }

  beforeEach(() => {
    naoLidas = signal(1);
    atualizarNaoLidas = vi.fn();
    perfil = 'MASTER';
    listarProcessos = vi.fn(() => of(paginaVazia()));
    listar = vi.fn((filtro: object) => of(pagina(Object.keys(filtro).length ? [TEMPORARIA] : TODAS)));
    obter = vi.fn((id: string) => of(detalhe(TODAS.find((n) => n.id === id)!)));
  });

  afterEach(() => vi.useRealTimers());

  it('mostra o título, «1 não lida · 8 total» e os contadores dos separadores', async () => {
    await abrir();

    expect(texto('h1')).toBe('Centro de Notificações');
    expect(texto('.resumo')).toBe('1 não lida · 8 total');
    expect(texto('[role="tab"]:nth-child(1) .separador-contador')).toBe('8');
    expect(separador('Não lidas').querySelector('.separador-contador')?.textContent?.trim()).toBe('1');
    expect(listar).toHaveBeenCalledWith({}, 0, 20);
    expect(itens()).toHaveLength(8);
    expect(obter).not.toHaveBeenCalled();
  });

  it('o separador «Não lidas» pede só as TEMPORARIA e ALERTA ativas, da página 0', async () => {
    await abrir();

    separador('Não lidas').click();
    harness.detectChanges();

    expect(listar).toHaveBeenLastCalledWith(FILTRO_NAO_LIDAS, 0, 20);
    expect(itens().map((b) => b.dataset['notificacao'])).toEqual(['n1']);
  });

  it('a pesquisa filtra a página carregada sem novo pedido, sem maiúsculas nem acentos', async () => {
    vi.useFakeTimers();
    await abrir();
    const pedidos = listar.mock.calls.length;
    const campo = el().querySelector<HTMLInputElement>('app-campo-pesquisa input')!;

    campo.value = 'carlos MENDES';
    campo.dispatchEvent(new Event('input'));
    vi.advanceTimersByTime(ESPERA_PESQUISA_MS);
    harness.detectChanges();
    expect(itens().map((b) => b.dataset['notificacao'])).toEqual(['n3']);

    campo.value = 'pdm-sin';
    campo.dispatchEvent(new Event('input'));
    vi.advanceTimersByTime(ESPERA_PESQUISA_MS);
    harness.detectChanges();
    expect(itens().map((b) => b.dataset['notificacao'])).toEqual(['n3']);

    campo.value = 'setubal';
    campo.dispatchEvent(new Event('input'));
    vi.advanceTimersByTime(ESPERA_PESQUISA_MS);
    harness.detectChanges();
    expect(itens().map((b) => b.dataset['notificacao'])).toEqual(['n1']);

    expect(listar.mock.calls.length).toBe(pedidos);
  });

  it('Apoio Geral (verde) sem processo e Apoio Proc. (ciano) com o chip do processo', async () => {
    await abrir();

    const geral = item('n2').querySelector('.item-selos app-selo')!;
    expect(geral.textContent?.trim()).toBe('Apoio Geral');
    expect(geral.classList).toContain('selo-sucesso');
    expect(item('n2').querySelector('.meta-processo')).toBeNull();

    const processo = item('n3').querySelector('.item-selos app-selo')!;
    expect(processo.textContent?.trim()).toBe('Apoio Proc.');
    expect(processo.classList).toContain('selo-ciano');
    expect(item('n3').querySelector('.meta-processo')?.textContent?.trim()).toBe('PDM-SIN-2024-003');
  });

  it('sem criadoPorNome o remetente é «SAIGT — Sistema»', async () => {
    await abrir();

    expect(item('n1').querySelector('.item-remetente')?.textContent?.trim()).toBe('SAIGT — Sistema');
  });

  it('a meta mostra a data de criação, o clipe com 1 anexo e o prazo em âmbar', async () => {
    await abrir();

    const meta = item('n4').querySelector('.item-meta')!;
    expect(meta.querySelector('time')?.textContent?.trim()).toBe('2026-07-20 11:00');
    expect(meta.querySelector('.meta-anexos')?.textContent?.replace(/\s+/g, ' ').trim()).toBe('1 anexo');
    expect(meta.querySelector('.meta-prazo')?.textContent?.replace(/\s+/g, ' ').trim()).toBe('Prazo 2026-08-20');
  });

  it('a não lida tem o ponto e o selo «Não lida»; os selos de estado seguem o modelo', async () => {
    await abrir();

    expect(item('n1').classList).toContain('item-nao-lida');
    expect(item('n1').querySelector('.item-ponto-visivel')).not.toBeNull();
    expect([...item('n1').querySelectorAll('app-selo')].map((s) => s.textContent?.trim())).toEqual(['Temporária', 'Não lida']);
    expect([...item('n5').querySelectorAll('app-selo')].map((s) => s.textContent?.trim())).toEqual(['Estática']);
  });

  it('clicar num item põe ?id= no URL, marca aria-current e abre o detalhe', async () => {
    await abrir();

    item('n3').click();
    await estabilizar();

    expect(router.url).toBe('/notificacoes?id=n3');
    expect(item('n3').getAttribute('aria-current')).toBe('true');
    expect(item('n1').hasAttribute('aria-current')).toBe(false);
    expect(obter).toHaveBeenCalledWith('n3');
    expect(texto('app-notificacao-detalhe h2')).toBe('Dúvida sobre responsável técnico');
  });

  it('uma resposta enviada no detalhe volta a pedir a página atual da lista', async () => {
    await abrir('/notificacoes?id=n2');
    const pedidosAntes = listar.mock.calls.length;

    harness.fixture.debugElement.query(By.directive(NotificacaoDetalheComponent)).componentInstance.alterada.emit();
    await estabilizar();

    expect(listar.mock.calls.length).toBe(pedidosAntes + 1);
  });

  it('?id= no URL abre logo essa notificação', async () => {
    await abrir('/notificacoes?id=n4');

    expect(item('n4').getAttribute('aria-current')).toBe('true');
    expect(texto('app-notificacao-detalhe h2')).toBe('Regularização necessária');
  });

  it('?id= de uma notificação inexistente mostra o aviso e tira o id do URL', async () => {
    obter.mockReturnValue(throwError(() => new ApiError('NOTIF_NAO_ENCONTRADA', 'A notificação não existe.', 't', null)));
    await abrir('/notificacoes?id=desconhecida');
    await estabilizar();

    expect(router.url).toBe('/notificacoes');
    expect(texto('.lista-aviso')).toContain(MENSAGEM_NAO_ENCONTRADA);
    expect(el().querySelector('app-notificacao-detalhe')).toBeNull();
  });

  it('«Fechar detalhe» fecha o detalhe e devolve o foco ao item', async () => {
    await abrir('/notificacoes?id=n2');
    document.body.appendChild(harness.fixture.nativeElement);

    el().querySelector<HTMLButtonElement>('[aria-label="Fechar detalhe"]')!.click();
    await estabilizar();
    await new Promise((resolver) => setTimeout(resolver));

    expect(router.url).toBe('/notificacoes');
    expect(el().querySelector('app-notificacao-detalhe')).toBeNull();
    expect(document.activeElement).toBe(item('n2'));
    harness.fixture.nativeElement.remove();
  });

  it('erro 500: alerta com a mensagem do envelope e «Tentar de novo» repete o pedido', async () => {
    listar.mockReturnValueOnce(throwError(() => new ApiError('ERRO_INTERNO', 'Erro inesperado no servidor.', 't', null)));
    await abrir();

    expect(texto('app-alerta')).toBe('Erro inesperado no servidor.');
    const tentar = [...el().querySelectorAll<HTMLButtonElement>('button')].find((b) => b.textContent?.trim() === 'Tentar de novo')!;
    tentar.click();
    harness.detectChanges();

    expect(listar).toHaveBeenCalledTimes(2);
    expect(itens()).toHaveLength(8);
  });

  it('vazio: mensagem própria em cada separador', async () => {
    listar.mockReturnValue(of(pagina([])));
    await abrir();
    expect(texto('.vazio')).toBe('Não tem notificações.');

    separador('Não lidas').click();
    harness.detectChanges();
    expect(texto('.vazio')).toBe('Não tem notificações por ler.');
  });

  it('a carregar: a lista fica com aria-busy e mostra o esqueleto', async () => {
    listar.mockReturnValue(new Observable());
    await abrir();

    expect(el().querySelector('ul.itens')?.getAttribute('aria-busy')).toBe('true');
    expect(el().querySelectorAll('.item-esqueleto')).toHaveLength(3);
  });

  it('com tudo numa página não mostra a paginação, como no Figma', async () => {
    await abrir();

    expect(el().querySelector('app-paginacao')).toBeNull();
  });

  it('mudar de página pede a página seguinte com 20 por página', async () => {
    listar.mockReturnValue(of(pagina(TODAS, 45)));
    await abrir();

    el().querySelector<HTMLButtonElement>('[aria-label="Página 2"]')!.click();

    expect(listar).toHaveBeenLastCalledWith({}, 1, 20);
  });

  describe('criação de notificações', () => {
    const botaoNova = () =>
      [...el().querySelectorAll<HTMLButtonElement>('button')].find((b) => b.textContent?.includes('Nova notificação'));

    it('o Master vê o botão «Nova notificação» e abre o modal', async () => {
      await abrir();

      botaoNova()!.click();
      harness.detectChanges();

      const dialogo = document.querySelector('[role="dialog"]');
      expect(dialogo?.textContent).toContain('Criar nova notificação');
    });

    it.each([['TECNICO'], ['VISITANTE']])('o perfil %s não vê o botão', async (outro) => {
      perfil = outro;
      await abrir();

      expect(botaoNova()).toBeUndefined();
      expect(harness.fixture.debugElement.query(By.directive(CriarNotificacaoModalComponent))).toBeNull();
    });

    it('depois de criar fecha o modal, mostra «Notificação criada.» e recarrega a lista e a contagem', async () => {
      await abrir();
      botaoNova()!.click();
      harness.detectChanges();
      listar.mockClear();

      const modal = harness.fixture.debugElement.query(By.directive(CriarNotificacaoModalComponent))
        .componentInstance as CriarNotificacaoModalComponent;
      modal.criada.emit({ notificacoes: [], loteId: null });
      harness.detectChanges();

      expect(document.querySelector('[role="dialog"]')).toBeNull();
      expect(texto('.cabecalho-sucesso')).toBe('Notificação criada.');
      expect(listar).toHaveBeenCalledWith({}, 0, 20);
      expect(atualizarNaoLidas).toHaveBeenCalled();
    });

    it('o utilizador externo vê «Nova notificação» e o modal abre nos tipos da entidade', async () => {
      perfil = null;
      await abrir();

      botaoNova()!.click();
      harness.detectChanges();

      const dialogo = document.querySelector('[role="dialog"]');
      expect(dialogo?.textContent).toContain('Pedido de apoio');
      expect(dialogo?.textContent).toContain('Regularização de procedimento');
      expect(dialogo?.textContent).not.toContain('Estática (geral)');
    });

    it('depois de o utilizador externo criar, mostra «Pedido enviado à DGT.», recarrega e seleciona a criada', async () => {
      perfil = null;
      await abrir();
      botaoNova()!.click();
      harness.detectChanges();
      listar.mockClear();

      const modal = harness.fixture.debugElement.query(By.directive(CriarNotificacaoModalComponent))
        .componentInstance as CriarNotificacaoModalComponent;
      const criada = notificacao('novo-pedido', { tipo: 'PEDIDO_APOIO' });
      obter.mockReturnValue(of(detalhe(criada)));
      modal.criada.emit({ notificacoes: [criada], loteId: null });
      await estabilizar();

      expect(document.querySelector('[role="dialog"]')).toBeNull();
      expect(texto('.cabecalho-sucesso')).toBe('Pedido enviado à DGT.');
      expect(listar).toHaveBeenCalledWith({}, 0, 20);
      expect(router.url).toContain('id=novo-pedido');
    });

    it('se algum documento não foi anexado avisa e abre o detalhe da notificação criada', async () => {
      await abrir();
      botaoNova()!.click();
      harness.detectChanges();
      const navegar = vi.spyOn(router, 'navigate');

      const modal = harness.fixture.debugElement.query(By.directive(CriarNotificacaoModalComponent))
        .componentInstance as CriarNotificacaoModalComponent;
      modal.criada.emit({ notificacoes: [{ id: 'n9' } as NotificacaoResumo], loteId: null, anexosFalhados: 2 });
      harness.detectChanges();

      expect(texto('.cabecalho-aviso')).toBe('Notificação criada; 2 documento(s) não foram anexados.');
      expect(document.querySelector('.cabecalho-sucesso:not(.cabecalho-aviso)')).toBeNull();
      expect(navegar).toHaveBeenCalledWith([], expect.objectContaining({ queryParams: { id: 'n9' } }));
    });
  });

  describe('separador «Histórico»', () => {
    const PROCESSO_LISTADO = {
      id: PROCESSO,
      numeroProcesso: '2024003',
      designacaoInstrumento: 'PDM de Sintra',
      numeroInstrumento: '1',
      tipoInstrumento: 'PDM',
      tipoDinamica: 'REV',
      nomeDinamica: 'Revisão',
      tipoProcedimento: 'DEC',
      nomeProcedimento: 'Deliberação',
      entidadeNif: '500000000',
      entidadeNome: 'CM Sintra',
      estado: 'EM_PREPARACAO',
      atualizadoEm: '2026-07-20T10:00:00Z',
      autorNome: 'Carlos Mendes',
    };
    const filtro = (nome: string) => el().querySelector(`[data-filtro="${nome}"]`)!;
    const escolher = (nome: string, valor: string) => {
      const select = filtro(nome).querySelector('select')!;
      select.value = valor;
      select.dispatchEvent(new Event('change'));
    };
    const escrever = (nome: string, valor: string) => {
      const input = filtro(nome).querySelector('input')!;
      input.value = valor;
      input.dispatchEvent(new Event('input'));
    };
    const aplicar = () => el().querySelector<HTMLButtonElement>('.filtros-aplicar')!.click();

    async function abrirHistorico(): Promise<void> {
      await abrir();
      separador('Histórico').click();
      harness.detectChanges();
    }

    it('pede as lidas e as resolvidas, da página 0, e mostra os filtros só neste separador', async () => {
      await abrir();
      expect(el().querySelector('.filtros')).toBeNull();

      separador('Histórico').click();
      harness.detectChanges();

      expect(listar).toHaveBeenLastCalledWith({ estado: ['LIDA', 'RESOLVIDA'] }, 0, 20);
      expect(el().querySelector('.filtros')).not.toBeNull();
      expect([...filtro('tipo').querySelectorAll('option')].map((o) => o.textContent?.trim())).toEqual([
        'Todos os tipos',
        'Estática',
        'Temporária',
        'Resposta obrigatória',
        'Pedido de apoio',
        'Regularização de procedimento',
        'Alerta',
      ]);
    });

    it('os filtros de tipo, processo e datas acrescentam tipo, processoId, desde e ate ao pedido', async () => {
      listarProcessos.mockReturnValue(of({ content: [PROCESSO_LISTADO], page: 0, size: 20, totalElements: 1, totalPages: 1 }));
      vi.useFakeTimers();
      await abrirHistorico();

      escolher('tipo', 'PEDIDO_APOIO');
      escrever('pesquisa-processo', 'Sintra');
      vi.advanceTimersByTime(ESPERA_PESQUISA_MS);
      harness.detectChanges();
      expect(listarProcessos).toHaveBeenCalledWith({ designacaoInstrumento: 'Sintra' }, 0, 20);
      escolher('processo', PROCESSO);
      escrever('desde', '2026-07-01');
      escrever('ate', '2026-07-31');
      aplicar();
      harness.detectChanges();

      expect(listar).toHaveBeenLastCalledWith(
        { estado: ['LIDA', 'RESOLVIDA'], tipo: ['PEDIDO_APOIO'], processoId: PROCESSO, desde: '2026-07-01', ate: '2026-07-31' },
        0,
        20,
      );
    });

    it('«Desde» depois de «Até» mostra o erro do intervalo e não faz pedido', async () => {
      await abrirHistorico();
      const pedidos = listar.mock.calls.length;

      escrever('desde', '2026-08-01');
      escrever('ate', '2026-07-01');
      aplicar();
      harness.detectChanges();

      expect(listar.mock.calls.length).toBe(pedidos);
      expect(filtro('desde').textContent).toContain('A data «Até» não pode ser anterior à data «Desde».');
      expect(filtro('desde').querySelector('input')?.getAttribute('aria-invalid')).toBe('true');
    });

    it('«Limpar» volta ao filtro só de estado', async () => {
      await abrirHistorico();
      escolher('tipo', 'ALERTA');
      aplicar();
      harness.detectChanges();

      el().querySelector<HTMLButtonElement>('.filtros-limpar')!.click();
      harness.detectChanges();

      expect(listar).toHaveBeenLastCalledWith({ estado: ['LIDA', 'RESOLVIDA'] }, 0, 20);
    });

    it('as linhas do histórico abrem o detalhe só para leitura', async () => {
      obter.mockImplementation((id: string) => of({ ...detalhe(TEMPORARIA), id, estado: 'LIDA', acoesDisponiveis: ['LER'] }));
      await abrir('/notificacoes?id=n1');
      separador('Histórico').click();
      harness.detectChanges();
      await estabilizar();

      const detalheRef = harness.fixture.debugElement.query(By.directive(NotificacaoDetalheComponent));
      expect((detalheRef.componentInstance as NotificacaoDetalheComponent).soLeitura()).toBe(true);
      expect(el().querySelector('.operacoes')).toBeNull();
    });
  });
});
