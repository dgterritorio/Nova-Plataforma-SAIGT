import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideEchartsCore } from 'ngx-echarts';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { PontoSerie } from '../../../models/dashboard.model';
import { KpiGraficoComponent, TipoGraficoKpi } from './kpi-grafico.component';

interface SerieOpcoes {
  type: string;
  data: unknown[];
  itemStyle?: { color?: string };
  radius?: string[];
}

interface Opcoes {
  animation: boolean;
  textStyle: { fontFamily: string };
  series: SerieOpcoes[];
  xAxis?: { type: string; data?: string[] };
  yAxis?: { type: string; data?: string[]; inverse?: boolean };
}

const TOKENS: Record<string, string> = {
  '--cor-primaria': '#1a56a0',
  '--cor-erro': '#dc2626',
  '--cor-grafico-barras': '#fca5a5',
  '--cor-grafico-barras-horizontais': '#2fa3e7',
  '--cor-rotulo-indicador': '#62748e',
  '--cor-borda': '#e2e8f0',
  '--cor-superficie': '#ffffff',
  '--fonte-base': 'Inter',
};

const SERIE: PontoSerie[] = [
  { rotulo: 'PDM', valor: 5 },
  { rotulo: 'PU', valor: 2 },
];

describe('KpiGraficoComponent', () => {
  let fixture: ComponentFixture<KpiGraficoComponent>;
  let init: ReturnType<typeof vi.fn>;
  let setOption: ReturnType<typeof vi.fn>;
  let movimentoReduzido = false;

  beforeEach(() => {
    movimentoReduzido = false;
    for (const [token, valor] of Object.entries(TOKENS)) {
      document.documentElement.style.setProperty(token, valor);
    }
    vi.stubGlobal(
      'ResizeObserver',
      class {
        observe = vi.fn();
        unobserve = vi.fn();
        disconnect = vi.fn();
      },
    );
    vi.stubGlobal('matchMedia', (consulta: string) => ({ matches: movimentoReduzido && consulta.includes('reduce') }));
    setOption = vi.fn();
    init = vi.fn().mockReturnValue({ setOption, dispose: vi.fn(), isDisposed: () => false, resize: vi.fn() });

    TestBed.configureTestingModule({ imports: [KpiGraficoComponent] });
    TestBed.overrideComponent(KpiGraficoComponent, { set: { providers: [provideEchartsCore({ echarts: { init } })] } });
  });

  afterEach(() => {
    for (const token of Object.keys(TOKENS)) {
      document.documentElement.style.removeProperty(token);
    }
    vi.unstubAllGlobals();
  });

  function criar(tipo: TipoGraficoKpi, serie: PontoSerie[] = SERIE, cores = ['--cor-grafico-barras-horizontais']): Opcoes {
    fixture = TestBed.createComponent(KpiGraficoComponent);
    fixture.componentRef.setInput('tipo', tipo);
    fixture.componentRef.setInput('serie', serie);
    fixture.componentRef.setInput('cores', cores);
    fixture.componentRef.setInput('designacao', 'Total por tipologia');
    fixture.detectChanges();
    return fixture.componentInstance.opcoes() as unknown as Opcoes;
  }

  it('monta as barras horizontais com uma barra por ponto, os rótulos no eixo vertical e a cor do token', () => {
    const opcoes = criar('barras-horizontais');

    expect(opcoes.yAxis).toMatchObject({ type: 'category', data: ['PDM', 'PU'], inverse: true });
    expect(opcoes.xAxis?.type).toBe('value');
    expect(opcoes.series[0]).toMatchObject({ type: 'bar', data: [5, 2], itemStyle: { color: '#2fa3e7' } });
    expect(opcoes.textStyle.fontFamily).toBe('Inter');
  });

  it('monta as barras verticais com os rótulos no eixo horizontal', () => {
    const opcoes = criar('barras-verticais', [{ rotulo: 'Out 2026', valor: 3 }], ['--cor-grafico-barras']);

    expect(opcoes.xAxis).toMatchObject({ type: 'category', data: ['Out 2026'] });
    expect(opcoes.yAxis?.type).toBe('value');
    expect(opcoes.series[0]).toMatchObject({ type: 'bar', data: [3], itemStyle: { color: '#fca5a5' } });
  });

  it('monta o donut com uma fatia por ponto, cada uma com a cor do seu token', () => {
    const opcoes = criar(
      'donut',
      [
        { rotulo: 'Em análise', valor: 6 },
        { rotulo: 'Rejeitado', valor: 4 },
      ],
      ['--cor-primaria', '--cor-erro'],
    );

    expect(opcoes.series[0].type).toBe('pie');
    expect(opcoes.series[0].data).toEqual([
      { name: 'Em análise', value: 6, itemStyle: { color: '#1a56a0' } },
      { name: 'Rejeitado', value: 4, itemStyle: { color: '#dc2626' } },
    ]);
  });

  it('anima por omissão e desliga a animação com prefers-reduced-motion', () => {
    expect(criar('barras-verticais').animation).toBe(true);

    movimentoReduzido = true;
    expect(criar('barras-verticais').animation).toBe(false);
  });

  it('desenha com o ECharts no renderizador SVG e entrega-lhe as opções montadas', async () => {
    const opcoes = criar('barras-horizontais');
    await new Promise((resolver) => setTimeout(resolver, 0));
    await fixture.whenStable();

    expect(init).toHaveBeenCalledWith(expect.any(HTMLElement), undefined, { renderer: 'svg' });
    expect(setOption).toHaveBeenCalledWith(opcoes, true);
  });

  it('dá ao gráfico role="img" e o resumo no aria-label, e a tabela «Ver dados» tem os mesmos valores', () => {
    criar('barras-horizontais');
    const elemento = fixture.nativeElement as HTMLElement;
    const grafico = elemento.querySelector('[echarts]');

    expect(grafico?.getAttribute('role')).toBe('img');
    expect(grafico?.getAttribute('aria-label')).toBe('Total por tipologia: PDM 5, PU 2');
    expect(elemento.querySelector('details summary')?.textContent?.trim()).toBe('Ver dados');
    expect(elemento.querySelector('caption')?.textContent?.trim()).toBe('Total por tipologia');
    const linhas = [...elemento.querySelectorAll('tbody tr')].map((tr) =>
      [...tr.querySelectorAll('th, td')].map((celula) => celula.textContent?.trim()),
    );
    expect(linhas).toEqual([
      ['PDM', '5'],
      ['PU', '2'],
    ]);
  });
});
