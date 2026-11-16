import { DOCUMENT } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, inject, input } from '@angular/core';
import type { EChartsCoreOption, EChartsInitOpts } from 'echarts/core';
import { NgxEchartsDirective, provideEchartsCore } from 'ngx-echarts';
import { PontoSerie } from '../../../models/dashboard.model';

export type TipoGraficoKpi = 'barras-horizontais' | 'barras-verticais' | 'donut';

/** Valores de recurso quando os tokens não estão disponíveis (ex.: fora do browser). */
const COR_RECURSO = 'currentColor';

/**
 * Gráfico de um KPI com o ECharts (renderizador SVG). As `cores` são nomes de tokens semânticos (`--cor-*`),
 * resolvidos no `:root`: o ECharts escreve as cores em atributos SVG, onde `var()` não funciona.
 */
@Component({
  selector: 'app-kpi-grafico',
  imports: [NgxEchartsDirective],
  providers: [provideEchartsCore({ echarts: () => import('./echarts-modular') })],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="linha-grafico">
      <div
        echarts
        role="img"
        [class]="'grafico grafico-' + tipo()"
        [attr.aria-label]="resumo()"
        [options]="opcoes()"
        [initOpts]="opcoesInicio"
      ></div>
      <ng-content />
    </div>
    <details class="ver-dados">
      <summary>Ver dados</summary>
      <table>
        <caption>{{ designacao() }}</caption>
        <thead>
          <tr>
            <th scope="col">{{ rotuloColuna() }}</th>
            <th scope="col">Valor</th>
          </tr>
        </thead>
        <tbody>
          @for (ponto of serie(); track ponto.rotulo) {
            <tr>
              <th scope="row">{{ ponto.rotulo }}</th>
              <td>{{ ponto.valor }}</td>
            </tr>
          } @empty {
            <tr>
              <td colspan="2">Sem dados.</td>
            </tr>
          }
        </tbody>
      </table>
    </details>
  `,
  styles: `
    :host {
      display: flex;
      flex-direction: column;
      gap: var(--espaco-2);
      min-width: 0;
    }

    .linha-grafico {
      display: flex;
      align-items: center;
      gap: var(--espaco-6);
    }

    .grafico {
      flex: 1;
      min-width: 0;
      height: 220px;
    }

    .grafico-donut {
      flex: 0 0 160px;
      height: 160px;
    }

    .ver-dados summary {
      width: fit-content;
      font-size: var(--texto-sm);
      line-height: var(--texto-sm-linha);
      color: var(--cor-primaria);
      cursor: pointer;
    }

    .ver-dados summary:focus-visible {
      outline: var(--anel-foco);
      outline-offset: var(--anel-foco-afastamento);
    }

    table {
      width: 100%;
      margin-top: var(--espaco-2);
      border-collapse: collapse;
      font-size: var(--texto-sm);
      line-height: var(--texto-sm-linha);
      color: var(--cor-texto-rotulo);
    }

    caption {
      padding-bottom: var(--espaco-1);
      font-weight: var(--peso-semi);
      text-align: left;
    }

    th,
    td {
      padding: var(--espaco-1) var(--espaco-2);
      border-bottom: var(--borda-largura) solid var(--cor-borda-suave);
      font-weight: var(--peso-normal);
      text-align: left;
    }

    thead th {
      font-weight: var(--peso-semi);
    }

    td {
      text-align: right;
    }
  `,
})
export class KpiGraficoComponent {
  private readonly documento = inject(DOCUMENT);

  readonly tipo = input.required<TipoGraficoKpi>();
  readonly serie = input.required<PontoSerie[]>();
  /** Um token por ponto no donut; nas barras só o primeiro conta. */
  readonly cores = input.required<string[]>();
  readonly designacao = input.required<string>();
  readonly rotuloColuna = input('Categoria');

  readonly opcoesInicio: EChartsInitOpts = { renderer: 'svg' };

  readonly resumo = computed(
    () => `${this.designacao()}: ${this.serie().map((ponto) => `${ponto.rotulo} ${ponto.valor}`).join(', ') || 'sem dados'}`,
  );

  readonly opcoes = computed<EChartsCoreOption>(() => {
    const serie = this.serie();
    const cores = this.cores().map((token) => this.resolver(token));
    const corRotulo = this.resolver('--cor-rotulo-indicador');
    const corBorda = this.resolver('--cor-borda');
    const base = {
      animation: !this.movimentoReduzido(),
      textStyle: { fontFamily: this.resolver('--fonte-base', 'sans-serif') },
    };

    if (this.tipo() === 'donut') {
      return {
        ...base,
        series: [
          {
            type: 'pie',
            radius: ['55%', '92%'],
            silent: true,
            label: { show: false },
            labelLine: { show: false },
            itemStyle: { borderColor: this.resolver('--cor-superficie'), borderWidth: 2 },
            data: serie.map((ponto, indice) => ({ name: ponto.rotulo, value: ponto.valor, itemStyle: { color: cores[indice] } })),
          },
        ],
      };
    }

    const rotulos = serie.map((ponto) => ponto.rotulo);
    const valores = serie.map((ponto) => ponto.valor);
    const eixoValores = {
      type: 'value',
      minInterval: 1,
      axisLabel: { color: corRotulo, fontSize: 11 },
      splitLine: { lineStyle: { color: corBorda, type: 'dashed' } },
    };
    const eixoCategorias = {
      type: 'category',
      data: rotulos,
      axisTick: { show: false },
      axisLine: { lineStyle: { color: corBorda } },
      axisLabel: { color: corRotulo, fontSize: 11 },
    };

    if (this.tipo() === 'barras-horizontais') {
      return {
        ...base,
        grid: { left: 8, right: 16, top: 8, bottom: 8 },
        xAxis: eixoValores,
        yAxis: { ...eixoCategorias, inverse: true },
        series: [{ type: 'bar', data: valores, silent: true, barMaxWidth: 28, barCategoryGap: '35%', itemStyle: { color: cores[0], borderRadius: [0, 4, 4, 0] } }],
      };
    }

    return {
      ...base,
      grid: { left: 8, right: 8, top: 12, bottom: 8 },
      xAxis: eixoCategorias,
      yAxis: eixoValores,
      series: [{ type: 'bar', data: valores, silent: true, barMaxWidth: 52, itemStyle: { color: cores[0], borderRadius: [4, 4, 0, 0] } }],
    };
  });

  private resolver(token: string, recurso = COR_RECURSO): string {
    const vista = this.documento.defaultView;
    const valor = vista?.getComputedStyle(this.documento.documentElement).getPropertyValue(token).trim();
    return valor || recurso;
  }

  private movimentoReduzido(): boolean {
    return this.documento.defaultView?.matchMedia?.('(prefers-reduced-motion: reduce)').matches ?? false;
  }
}
