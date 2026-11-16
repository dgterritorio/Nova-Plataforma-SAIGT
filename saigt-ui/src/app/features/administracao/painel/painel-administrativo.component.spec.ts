import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { provideEchartsCore } from 'ngx-echarts';
import { Observable, Subject, of, throwError } from 'rxjs';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { AuthService } from '../../../core/auth/auth.service';
import { ApiError } from '../../../core/models/error-response.model';
import { EstatisticasAdmin } from '../../../models/admin.model';
import { PontoSerie, PreferenciaKpi, PreferenciaKpiPedido, ValorKpi } from '../../../models/dashboard.model';
import { AdminService } from '../../../services/admin.service';
import { DashboardService } from '../../../services/dashboard.service';
import { KpiGraficoComponent } from './kpi-grafico.component';
import { PERIODO_ATUALIZACAO_MS, PainelAdministrativoComponent, agruparPorEstado, rotuloMes } from './painel-administrativo.component';

const CALCULADO_EM = '2026-11-13T10:22:31Z';

const SERIE_ESTADOS: PontoSerie[] = [
  { rotulo: 'EM_SUBMISSAO', valor: 3 },
  { rotulo: 'ENVIADO_DGT', valor: 0 },
  { rotulo: 'EM_VALIDACAO_DGT', valor: 2 },
  { rotulo: 'VALIDADO_DGT', valor: 4 },
  { rotulo: 'REJEITADO_DGT', valor: 1 },
  { rotulo: 'ENVIADO_INCM', valor: 0 },
  { rotulo: 'EM_VALIDACAO_INCM', valor: 0 },
  { rotulo: 'PUBLICADO', valor: 0 },
  { rotulo: 'ANULADO', valor: 2 },
  { rotulo: 'ANULADO_INCM', valor: 1 },
];

function kpisCom(calculadoEm = CALCULADO_EM, totalPorEstado = 13): ValorKpi[] {
  return [
    {
      codigo: 'GRAFICO_MES',
      designacao: 'Gráfico de processos por mês',
      valor: null,
      serie: [
        { rotulo: '2026-10', valor: 3 },
        { rotulo: '2026-11', valor: 5 },
      ],
      calculadoEm,
    },
    { codigo: 'PROCESSOS_POR_ESTADO', designacao: 'Processos nos principais estados, por IGT', valor: totalPorEstado, serie: SERIE_ESTADOS, calculadoEm },
    {
      codigo: 'TOTAL_POR_TIPOLOGIA',
      designacao: 'Total de processos por tipologia',
      valor: 13,
      serie: [
        { rotulo: 'PDM', valor: 8 },
        { rotulo: 'PU', valor: 5 },
      ],
      calculadoEm,
    },
    { codigo: 'GRAFICO_ESTADO', designacao: 'Gráfico de processos por estado', valor: null, serie: SERIE_ESTADOS, calculadoEm },
    { codigo: 'TEMPO_MEDIO_INSTRUCAO', designacao: 'Tempos médios de instrução', valor: 42, serie: [{ rotulo: 'PDM', valor: 40 }], calculadoEm },
  ];
}

const CATALOGO: [string, string][] = [
  ['TOTAL_POR_TIPOLOGIA', 'Total de processos por tipologia'],
  ['PROCESSOS_POR_ESTADO', 'Processos nos principais estados, por IGT'],
  ['A_CADUCAR', 'Dinâmicas a caducar'],
  ['A_INATIVAR', 'Processos a inativar'],
  ['GRAFICO_ESTADO', 'Gráfico de processos por estado'],
  ['GRAFICO_MES', 'Gráfico de processos por mês'],
  ['TEMPO_MEDIO_INSTRUCAO', 'Tempos médios de instrução'],
  ['PADROES_TAMANHO_FICHEIROS', 'Padrões de tamanho de ficheiros'],
  ['TAXA_REJEICAO', 'Taxa de rejeição'],
  ['ATIVIDADE_RECENTE', 'Atividade recente'],
];

/** Gravadas: o mês primeiro e o resto pela ordem do catálogo; a atividade recente escondida. */
function preferenciasGravadas(): PreferenciaKpi[] {
  const ordem = ['GRAFICO_MES', ...CATALOGO.map(([codigo]) => codigo).filter((codigo) => codigo !== 'GRAFICO_MES')];
  return CATALOGO.map(([kpiCodigo, designacao]) => ({
    kpiCodigo,
    designacao,
    visivel: kpiCodigo !== 'ATIVIDADE_RECENTE',
    ordem: ordem.indexOf(kpiCodigo),
  })).reverse();
}

const ESTATISTICAS: EstatisticasAdmin = {
  totalProcessos: 284,
  emAnalise: 47,
  publicadosComSucesso: 23,
  comPrazoExpirado: 1,
  submissoesPorMes: [],
  distribuicaoPorEstado: [],
  atividadeRecente: [
    {
      tipoOcorrencia: 'PROCESSO',
      idOcorrencia: 'p1',
      acao: 'SUBMETER',
      utilizadorId: 'u1',
      utilizadorNome: 'Ana Ferreira',
      criadoEm: '2026-08-19T10:00:00Z',
    },
  ],
  publicadosPorEntidade: [
    { entidade: 'CM Lisboa', pdm: 2, pp: 14, pu: 7, prot: 0, total: 23, caducados: 1, ultimaPublicacao: '2026-03-15' },
    { entidade: 'CM Porto', pdm: 1, pp: 9, pu: 4, prot: 0, total: 14, caducados: 0, ultimaPublicacao: null },
  ],
};

describe('PainelAdministrativoComponent', () => {
  let fixture: ComponentFixture<PainelAdministrativoComponent>;
  let adminServiceMock: { estatisticas: ReturnType<typeof vi.fn> };
  let dashboardMock: {
    kpis: ReturnType<typeof vi.fn<() => Observable<ValorKpi[]>>>;
    preferencias: ReturnType<typeof vi.fn<() => Observable<PreferenciaKpi[]>>>;
    gravarPreferencias: ReturnType<typeof vi.fn<(lista: PreferenciaKpiPedido[]) => Observable<PreferenciaKpi[]>>>;
  };

  const elemento = (): HTMLElement => fixture.nativeElement as HTMLElement;
  const texto = (): string => elemento().textContent ?? '';
  const codigosDosBlocos = (): (string | null)[] =>
    [...elemento().querySelectorAll('.grelha-kpi [data-kpi]')].map((bloco) => bloco.getAttribute('data-kpi'));
  const bloco = (codigo: string): HTMLElement => elemento().querySelector(`.grelha-kpi [data-kpi="${codigo}"]`) as HTMLElement;
  const botao = (seletor: string): HTMLButtonElement => elemento().querySelector(seletor) as HTMLButtonElement;
  const linhasPainel = (): HTMLElement[] => [...elemento().querySelectorAll<HTMLElement>('.linha-preferencia')];

  function renderizar(): void {
    fixture.detectChanges();
    TestBed.tick();
  }

  function criar(): void {
    fixture = TestBed.createComponent(PainelAdministrativoComponent);
    fixture.detectChanges();
    vi.advanceTimersByTime(0);
    renderizar();
  }

  function abrirPainel(): void {
    botao('.botao-personalizar').click();
    renderizar();
    vi.advanceTimersByTime(0);
    renderizar();
  }

  beforeEach(() => {
    vi.useFakeTimers();
    vi.stubGlobal(
      'ResizeObserver',
      class {
        observe = vi.fn();
        unobserve = vi.fn();
        disconnect = vi.fn();
      },
    );
    adminServiceMock = { estatisticas: vi.fn().mockReturnValue(of(ESTATISTICAS)) };
    dashboardMock = {
      kpis: vi.fn<() => Observable<ValorKpi[]>>().mockImplementation(() => of(kpisCom())),
      preferencias: vi.fn<() => Observable<PreferenciaKpi[]>>().mockImplementation(() => of(preferenciasGravadas())),
      gravarPreferencias: vi.fn<(lista: PreferenciaKpiPedido[]) => Observable<PreferenciaKpi[]>>(),
    };
    const graficoFalso = { setOption: vi.fn(), dispose: vi.fn(), isDisposed: () => false, resize: vi.fn() };

    TestBed.configureTestingModule({
      imports: [PainelAdministrativoComponent],
      providers: [
        provideRouter([]),
        { provide: AdminService, useValue: adminServiceMock },
        { provide: DashboardService, useValue: dashboardMock },
        {
          provide: AuthService,
          useValue: { nome: () => 'Ana Teixeira', origem: () => 'AD_DGT', entidade: () => null, perfilInterno: () => 'MASTER' },
        },
      ],
    });
    TestBed.overrideComponent(KpiGraficoComponent, {
      set: { providers: [provideEchartsCore({ echarts: { init: () => graficoFalso } })] },
    });
  });

  afterEach(() => {
    fixture?.destroy();
    vi.useRealTimers();
    vi.unstubAllGlobals();
  });

  describe('grelha de KPIs', () => {
    beforeEach(() => criar());

    it('pede os KPIs ao abrir e mostra os blocos pela ordem da resposta, só os que nela vêm', () => {
      expect(dashboardMock.kpis).toHaveBeenCalledTimes(1);
      expect(codigosDosBlocos()).toEqual(['GRAFICO_MES', 'PROCESSOS_POR_ESTADO', 'TOTAL_POR_TIPOLOGIA', 'GRAFICO_ESTADO', 'TEMPO_MEDIO_INSTRUCAO']);
      expect(bloco('A_CADUCAR')).toBeNull();
    });

    it('mostra PROCESSOS_POR_ESTADO num cartão de número com a designação, o valor e «Total de processos»', () => {
      const cartao = bloco('PROCESSOS_POR_ESTADO');
      expect(cartao.classList).toContain('bloco-numero');
      expect(cartao.querySelector('.rotulo-kpi')?.textContent?.trim()).toBe('Processos nos principais estados, por IGT');
      expect(cartao.querySelector('.numero')?.textContent?.trim()).toBe('13');
      expect(cartao.querySelector('.legenda-kpi')?.textContent?.trim()).toBe('Total de processos');
    });

    it('desenha TOTAL_POR_TIPOLOGIA em barras horizontais do ECharts, com o total no título', () => {
      const cartao = bloco('TOTAL_POR_TIPOLOGIA');
      const grafico = cartao.querySelector('app-kpi-grafico [echarts]');
      expect(grafico?.classList).toContain('grafico-barras-horizontais');
      expect(cartao.querySelector('h2 .total-grafico')?.textContent?.trim()).toBe('13');
      expect(grafico?.getAttribute('aria-label')).toBe('Total de processos por tipologia: PDM 8, PU 5');
    });

    it('desenha GRAFICO_ESTADO num donut do ECharts agrupado pelos 4 estados macro, com legenda de rótulo e valor', () => {
      const cartao = bloco('GRAFICO_ESTADO');
      expect(cartao.querySelector('app-kpi-grafico [echarts]')?.classList).toContain('grafico-donut');
      const legenda = [...cartao.querySelectorAll('.legenda-donut li')].map((li) =>
        [...li.querySelectorAll('span')].map((parte) => parte.textContent?.trim()).filter(Boolean).join(' '),
      );
      expect(legenda).toEqual(['Em análise 6', 'Rejeitado 4', 'Pendente 3', 'Validado 0']);
      const pendente = [...cartao.querySelectorAll('.legenda-donut li')].find((li) => li.textContent?.includes('Pendente'));
      expect(pendente?.getAttribute('title')).toBe('Em submissão (3)');
    });

    it('desenha GRAFICO_MES em barras verticais do ECharts com os meses em «Mmm AAAA»', () => {
      const cartao = bloco('GRAFICO_MES');
      const grafico = cartao.querySelector('app-kpi-grafico [echarts]');
      expect(grafico?.classList).toContain('grafico-barras-verticais');
      expect(grafico?.getAttribute('aria-label')).toBe('Gráfico de processos por mês: Out 2026 3, Nov 2026 5');
      expect(rotuloMes('2026-01')).toBe('Jan 2026');
    });

    it('mostra um código que o ecrã não conhece no cartão genérico com a designação, o valor e a tabela da série', () => {
      const cartao = bloco('TEMPO_MEDIO_INSTRUCAO');
      expect(cartao.classList).toContain('bloco-generico');
      expect(cartao.querySelector('.rotulo-kpi')?.textContent?.trim()).toBe('Tempos médios de instrução');
      expect(cartao.querySelector('.numero')?.textContent?.trim()).toBe('42');
      const linhas = [...cartao.querySelectorAll('tbody tr')].map((tr) => [...tr.children].map((c) => c.textContent?.trim()));
      expect(linhas).toEqual([['PDM', '40']]);
    });

    it('dá a cada gráfico o resumo no aria-label e a tabela «Ver dados» com os mesmos valores', () => {
      const cartao = bloco('GRAFICO_ESTADO');
      const grafico = cartao.querySelector('[echarts]');
      expect(grafico?.getAttribute('role')).toBe('img');
      expect(grafico?.getAttribute('aria-label')).toBe('Gráfico de processos por estado: Em análise 6, Rejeitado 4, Pendente 3, Validado 0');
      expect(cartao.querySelector('details caption')?.textContent?.trim()).toBe('Gráfico de processos por estado');
      const linhas = [...cartao.querySelectorAll('details tbody tr')].map((tr) => [...tr.children].map((c) => c.textContent?.trim()).join(' '));
      expect(linhas).toEqual(['Em análise 6', 'Rejeitado 4', 'Pendente 3', 'Validado 0']);
    });

    it('já não tem os valores de exemplo, os marcadores estáticos nem o filtro por tipo', () => {
      expect(texto()).not.toContain('142d');
      expect(texto()).not.toContain('78%');
      expect(texto()).not.toContain('+3%');
      expect(texto()).not.toContain('Tempo médio de instrução por tipo');
      expect(elemento().querySelector('app-marcador-estatico, app-legenda-marcador-estatico')).toBeNull();
      expect(elemento().querySelector('.filtros-tipo')).toBeNull();
      expect([...elemento().querySelectorAll('button')].map((b) => b.textContent?.trim())).not.toContain('Todos');
    });

    it('mostra «Atualizado às» com a hora de Lisboa de calculadoEm numa região aria-live', () => {
      const atualizado = elemento().querySelector('.atualizado');
      expect(atualizado?.getAttribute('aria-live')).toBe('polite');
      expect(atualizado?.textContent?.trim()).toBe('Atualizado às 10:22:31');
    });

    it('«Atualizar» faz um novo pedido, atualiza os blocos e a hora', () => {
      dashboardMock.kpis.mockImplementation(() => of(kpisCom('2026-11-13T10:25:00Z', 14)));
      botao('.botao-atualizar').click();
      vi.advanceTimersByTime(0);
      renderizar();

      expect(dashboardMock.kpis).toHaveBeenCalledTimes(2);
      expect(bloco('PROCESSOS_POR_ESTADO').querySelector('.numero')?.textContent?.trim()).toBe('14');
      expect(elemento().querySelector('.atualizado')?.textContent?.trim()).toBe('Atualizado às 10:25:00');
    });

    it('continua a mostrar «Atividade recente» e «IGT publicados por entidade» a partir das estatísticas', () => {
      expect(adminServiceMock.estatisticas).toHaveBeenCalledTimes(1);
      expect(texto()).toContain('SUBMETER');
      expect(texto()).toContain('Ana Ferreira');
      expect(texto()).not.toContain('u1');
      const linhas = elemento().querySelectorAll('.tabela-entidades tbody tr');
      expect([...linhas[0].querySelectorAll('td')].map((td) => td.textContent?.trim())).toEqual([
        'CM Lisboa',
        '2',
        '14',
        '7',
        '0',
        '23',
        '1',
        '15/03/2026',
      ]);
      expect([...linhas[1].querySelectorAll('td')].map((td) => td.textContent?.trim())[7]).toBe('—');
    });
  });

  describe('polling', () => {
    it('pede os KPIs a cada 60 s e mantém os blocos no ecrã enquanto carrega', () => {
      criar();
      const cartaoAntes = bloco('PROCESSOS_POR_ESTADO');
      const pendente = new Subject<ValorKpi[]>();
      dashboardMock.kpis.mockImplementation(() => pendente);

      vi.advanceTimersByTime(PERIODO_ATUALIZACAO_MS);
      renderizar();
      expect(dashboardMock.kpis).toHaveBeenCalledTimes(2);
      expect(elemento().querySelector('.grelha-kpi')?.getAttribute('aria-busy')).toBe('true');
      expect(bloco('PROCESSOS_POR_ESTADO')).toBe(cartaoAntes);

      pendente.next(kpisCom('2026-11-13T10:23:31Z', 20));
      pendente.complete();
      renderizar();
      expect(bloco('PROCESSOS_POR_ESTADO')).toBe(cartaoAntes);
      expect(cartaoAntes.querySelector('.numero')?.textContent?.trim()).toBe('20');
      expect(elemento().querySelector('.grelha-kpi')?.getAttribute('aria-busy')).toBe('false');
    });

    it('não faz outro pedido enquanto o anterior não terminou', () => {
      criar();
      const pendente = new Subject<ValorKpi[]>();
      dashboardMock.kpis.mockImplementation(() => pendente);

      vi.advanceTimersByTime(PERIODO_ATUALIZACAO_MS * 3);
      expect(dashboardMock.kpis).toHaveBeenCalledTimes(2);

      pendente.next(kpisCom());
      pendente.complete();
      dashboardMock.kpis.mockImplementation(() => of(kpisCom()));
      vi.advanceTimersByTime(PERIODO_ATUALIZACAO_MS);
      expect(dashboardMock.kpis).toHaveBeenCalledTimes(3);
    });

    it('deixa de pedir quando se sai do ecrã', () => {
      criar();
      fixture.destroy();
      vi.advanceTimersByTime(PERIODO_ATUALIZACAO_MS * 3);
      expect(dashboardMock.kpis).toHaveBeenCalledTimes(1);
    });

    it('«Atualizar» a meio da contagem reinicia os 60 s', () => {
      criar();
      vi.advanceTimersByTime(30_000);
      botao('.botao-atualizar').click();
      vi.advanceTimersByTime(0);
      expect(dashboardMock.kpis).toHaveBeenCalledTimes(2);

      vi.advanceTimersByTime(PERIODO_ATUALIZACAO_MS - 1);
      expect(dashboardMock.kpis).toHaveBeenCalledTimes(2);
      vi.advanceTimersByTime(1);
      expect(dashboardMock.kpis).toHaveBeenCalledTimes(3);
    });

    it('num erro 500 do polling mostra a mensagem, mantém os blocos e continua a pedir', () => {
      criar();
      dashboardMock.kpis.mockImplementation(() => throwError(() => new ApiError('ERRO_INTERNO', 'Erro interno do servidor.', 't1', null)));
      vi.advanceTimersByTime(PERIODO_ATUALIZACAO_MS);
      renderizar();
      expect(elemento().querySelector('app-error-banner')?.textContent).toContain('Erro interno do servidor.');
      expect(codigosDosBlocos().length).toBe(5);

      dashboardMock.kpis.mockImplementation(() => of(kpisCom()));
      vi.advanceTimersByTime(PERIODO_ATUALIZACAO_MS);
      renderizar();
      expect(dashboardMock.kpis).toHaveBeenCalledTimes(3);
      expect(elemento().querySelector('app-error-banner')?.textContent?.trim()).toBe('');
    });
  });

  describe('estados', () => {
    it('sem KPIs visíveis mostra o aviso a sugerir «Personalizar»', () => {
      dashboardMock.kpis.mockImplementation(() => of([]));
      criar();
      expect(elemento().querySelector('.alerta-sem-kpis')?.textContent?.trim()).toBe(
        'Não há indicadores visíveis. Use «Personalizar» para escolher os indicadores.',
      );
      expect(elemento().querySelector('.grelha-kpi')).toBeNull();
    });

    it('com 403 mostra a mensagem e nenhuma grelha', () => {
      dashboardMock.kpis.mockImplementation(() => throwError(() => new ApiError('SEM_PERMISSAO', 'Sem permissão.', 't1', null)));
      criar();
      expect(elemento().querySelector('app-error-banner')?.textContent).toContain('Não tem permissão para executar esta ação.');
      expect(elemento().querySelector('.grelha-kpi')).toBeNull();
    });

    it('com 500 ao abrir mostra a mensagem do envelope e nenhum bloco', () => {
      dashboardMock.kpis.mockImplementation(() => throwError(() => new ApiError('ERRO_INTERNO', 'Erro interno do servidor.', 't1', null)));
      criar();
      expect(elemento().querySelector('app-error-banner')?.textContent).toContain('Erro interno do servidor.');
      expect(codigosDosBlocos()).toEqual([]);
    });

    it('agrupa os estados reais com a dica dos estados de cada grupo, do maior para o menor', () => {
      expect(agruparPorEstado(SERIE_ESTADOS)).toEqual([
        { rotulo: 'Em análise', valor: 6, cor: '--cor-primaria', dica: 'Validado DGT (4), Em Validação DGT (2)' },
        { rotulo: 'Rejeitado', valor: 4, cor: '--cor-erro', dica: 'Anulado (2), Rejeitado DGT (1), Anulado INCM (1)' },
        { rotulo: 'Pendente', valor: 3, cor: '--cor-aviso-grafico', dica: 'Em submissão (3)' },
        { rotulo: 'Validado', valor: 0, cor: '--cor-sucesso-grafico', dica: '' },
      ]);
    });
  });

  describe('personalização', () => {
    beforeEach(() => {
      criar();
      abrirPainel();
    });

    it('lista os KPIs do catálogo pela ordem das preferências, com «Mostrar», «Subir» e «Descer» desativados nas pontas', () => {
      const linhas = linhasPainel();
      expect(linhas.map((linha) => linha.getAttribute('data-kpi'))).toEqual([
        'GRAFICO_MES',
        'TOTAL_POR_TIPOLOGIA',
        'PROCESSOS_POR_ESTADO',
        'A_CADUCAR',
        'A_INATIVAR',
        'GRAFICO_ESTADO',
        'TEMPO_MEDIO_INSTRUCAO',
        'PADROES_TAMANHO_FICHEIROS',
        'TAXA_REJEICAO',
        'ATIVIDADE_RECENTE',
      ]);
      const caixa = linhas[0].querySelector<HTMLInputElement>('input[type="checkbox"]');
      expect(caixa?.checked).toBe(true);
      expect(linhas[0].querySelector('label')?.textContent?.replace(/\s+/g, ' ').trim()).toBe('Mostrar Gráfico de processos por mês');
      expect(linhas[9].querySelector<HTMLInputElement>('input')?.checked).toBe(false);
      expect(linhas[0].querySelector('[aria-label="Subir Gráfico de processos por mês"]')?.hasAttribute('disabled')).toBe(true);
      expect(linhas[0].querySelector('[aria-label="Descer Gráfico de processos por mês"]')?.hasAttribute('disabled')).toBe(false);
      expect(linhas[9].querySelector('[aria-label="Descer Atividade recente"]')?.hasAttribute('disabled')).toBe(true);
    });

    it('marca com «Ainda sem cálculo» só os visíveis que não vieram nos KPIs', () => {
      const comSelo = linhasPainel()
        .filter((linha) => linha.querySelector('.selo-sem-calculo'))
        .map((linha) => linha.getAttribute('data-kpi'));
      expect(comSelo).toEqual(['A_CADUCAR', 'A_INATIVAR', 'PADROES_TAMANHO_FICHEIROS', 'TAXA_REJEICAO']);
    });

    it('descer o primeiro, esconder o terceiro e gravar envia todos os KPIs com ordem 0..n-1, fecha e recarrega a grelha', () => {
      const gravadas = preferenciasGravadas();
      dashboardMock.gravarPreferencias.mockReturnValue(of(gravadas));
      botao('#descer-GRAFICO_MES').click();
      renderizar();
      const terceira = linhasPainel()[2].querySelector<HTMLInputElement>('input')!;
      terceira.click();
      renderizar();
      botao('.botao-gravar').click();
      renderizar();
      vi.advanceTimersByTime(0);
      renderizar();

      const pedido = dashboardMock.gravarPreferencias.mock.calls[0][0];
      expect(pedido.map((p) => p.ordem)).toEqual([0, 1, 2, 3, 4, 5, 6, 7, 8, 9]);
      expect(pedido.slice(0, 3)).toEqual([
        { kpiCodigo: 'TOTAL_POR_TIPOLOGIA', visivel: true, ordem: 0 },
        { kpiCodigo: 'GRAFICO_MES', visivel: true, ordem: 1 },
        { kpiCodigo: 'PROCESSOS_POR_ESTADO', visivel: false, ordem: 2 },
      ]);
      expect(pedido[9]).toEqual({ kpiCodigo: 'ATIVIDADE_RECENTE', visivel: false, ordem: 9 });
      expect(fixture.componentInstance.painelAberto()).toBe(false);
      expect(elemento().querySelector('.alerta-gravado')?.textContent).toContain('Preferências gravadas.');
      expect(dashboardMock.kpis).toHaveBeenCalledTimes(2);
    });

    it('com o painel aberto, um pedido do polling não muda a lista do painel', () => {
      botao('#descer-GRAFICO_MES').click();
      renderizar();
      const antes = linhasPainel().map((linha) => linha.getAttribute('data-kpi'));

      dashboardMock.kpis.mockImplementation(() => of(kpisCom('2026-11-13T10:30:00Z')));
      vi.advanceTimersByTime(PERIODO_ATUALIZACAO_MS);
      renderizar();

      expect(dashboardMock.kpis).toHaveBeenCalledTimes(2);
      expect(linhasPainel().map((linha) => linha.getAttribute('data-kpi'))).toEqual(antes);
      expect(dashboardMock.preferencias).toHaveBeenCalledTimes(1);
    });

    it('«Repor predefinição» envia uma lista vazia e mostra a lista devolvida', () => {
      const omissao = CATALOGO.map(([kpiCodigo, designacao], ordem) => ({ kpiCodigo, designacao, visivel: true, ordem }));
      dashboardMock.gravarPreferencias.mockReturnValue(of(omissao));
      botao('.botao-repor').click();
      vi.advanceTimersByTime(0);
      renderizar();

      expect(dashboardMock.gravarPreferencias).toHaveBeenCalledWith([]);
      expect(linhasPainel().map((linha) => linha.getAttribute('data-kpi'))).toEqual(CATALOGO.map(([codigo]) => codigo));
      expect(linhasPainel().every((linha) => linha.querySelector<HTMLInputElement>('input')?.checked)).toBe(true);
      expect(fixture.componentInstance.painelAberto()).toBe(true);
    });

    it('«Cancelar» fecha sem gravar e devolve o foco a «Personalizar»', () => {
      botao('#descer-GRAFICO_MES').click();
      renderizar();
      botao('.botao-cancelar').click();
      renderizar();

      expect(dashboardMock.gravarPreferencias).not.toHaveBeenCalled();
      expect(fixture.componentInstance.painelAberto()).toBe(false);
      expect(document.activeElement).toBe(botao('.botao-personalizar'));
    });

    it('Escape fecha sem gravar e devolve o foco a «Personalizar»', () => {
      document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }));
      renderizar();

      expect(dashboardMock.gravarPreferencias).not.toHaveBeenCalled();
      expect(fixture.componentInstance.painelAberto()).toBe(false);
      expect(document.activeElement).toBe(botao('.botao-personalizar'));
    });

    it('«Subir» só com teclado mantém o foco no botão da linha movida e anuncia a nova posição', () => {
      const subir = botao('#subir-PROCESSOS_POR_ESTADO');
      subir.focus();
      subir.click();
      renderizar();

      expect(linhasPainel()[1].getAttribute('data-kpi')).toBe('PROCESSOS_POR_ESTADO');
      expect(document.activeElement?.id).toBe('subir-PROCESSOS_POR_ESTADO');
      expect(elemento().querySelector('[aria-live="polite"].visualmente-oculto')?.textContent?.trim()).toBe(
        'Processos nos principais estados, por IGT passou para a posição 2.',
      );
    });

    it('ao chegar ao topo, o foco passa ao «Descer» da mesma linha', () => {
      botao('#subir-TOTAL_POR_TIPOLOGIA').click();
      renderizar();

      expect(linhasPainel()[0].getAttribute('data-kpi')).toBe('TOTAL_POR_TIPOLOGIA');
      expect(document.activeElement?.id).toBe('descer-TOTAL_POR_TIPOLOGIA');
    });

    it('com 400 na gravação mostra a mensagem e os campos no painel e não fecha', () => {
      dashboardMock.gravarPreferencias.mockReturnValue(
        throwError(() => new ApiError('VALIDACAO', 'Existem campos por corrigir.', 't1', [{ path: '[0].ordem', erro: 'Posição repetida no dashboard.' }])),
      );
      botao('.botao-gravar').click();
      renderizar();

      const alerta = elemento().querySelector('.alerta-painel');
      expect(alerta?.textContent).toContain('Existem campos por corrigir.');
      expect(alerta?.textContent).toContain('[0].ordem: Posição repetida no dashboard.');
      expect(fixture.componentInstance.painelAberto()).toBe(true);
      expect(linhasPainel().length).toBe(10);
    });
  });
});
