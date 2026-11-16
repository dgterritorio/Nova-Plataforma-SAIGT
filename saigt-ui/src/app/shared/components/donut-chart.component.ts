import { Component, Input } from '@angular/core';

/** Sem `cor`, usa a paleta por posição. `tooltip`: estados reais de um segmento agrupado, só ao passar o rato. */
export interface DonutDado {
  rotulo: string;
  valor: number;
  cor?: string;
  tooltip?: string;
}

const CORES = ['#1a56a0', '#16a34a', '#eebd50', '#dc2626', '#7ab5c8', '#94a3b8', '#7c3aed', '#0891b2'];

/** Cada segmento é um círculo com `stroke-dasharray` só na sua fração, rodado -90° para começar no topo. */
@Component({
  selector: 'app-donut-chart',
  standalone: true,
  template: `
    <div class="wrapper">
      <svg viewBox="0 0 100 100" class="donut">
        @for (segmento of segmentos(); track segmento.rotulo) {
          <circle
            cx="50"
            cy="50"
            r="35"
            fill="none"
            [style.stroke]="segmento.cor"
            stroke-width="16"
            [attr.stroke-dasharray]="segmento.dasharray"
            [attr.stroke-dashoffset]="segmento.dashoffset"
            transform="rotate(-90 50 50)"
          >
            <title>{{ segmento.dado.tooltip ?? segmento.rotulo + ' (' + segmento.dado.valor + ')' }}</title>
          </circle>
        }
        @if (segmentos().length === 0) {
          <circle cx="50" cy="50" r="35" fill="none" stroke="#e2e8f0" stroke-width="16" />
        }
      </svg>
      <ul class="legenda">
        @for (segmento of segmentos(); track segmento.rotulo) {
          <li [title]="segmento.dado.tooltip">
            <span class="marcador" [style.background]="segmento.cor"></span>
            {{ segmento.rotulo }} ({{ segmento.dado.valor }})
          </li>
        } @empty {
          <li class="vazio">Sem dados</li>
        }
      </ul>
    </div>
  `,
  styles: [
    `
      .wrapper {
        display: flex;
        align-items: center;
        gap: 16px;
      }

      .donut {
        width: 160px;
        height: 160px;
        flex-shrink: 0;
      }

      .donut circle {
        cursor: help;
      }

      .legenda {
        list-style: none;
        margin: 0;
        padding: 0;
        display: flex;
        flex-direction: column;
        gap: 6px;
        font-size: 12px;
        color: #334155;
      }

      .legenda li {
        cursor: help;
      }

      .marcador {
        display: inline-block;
        width: 8px;
        height: 8px;
        border-radius: 50%;
        margin-right: 6px;
      }

      .legenda li.vazio {
        color: #94a3b8;
        cursor: default;
      }
    `,
  ],
})
export class DonutChartComponent {
  @Input() dados: DonutDado[] = [];

  private static readonly CIRCUNFERENCIA = 2 * Math.PI * 35;

  segmentos(): { rotulo: string; cor: string; dasharray: string; dashoffset: number; dado: DonutDado }[] {
    const total = this.dados.reduce((soma, dado) => soma + dado.valor, 0);
    if (total === 0) {
      return [];
    }
    const circunferencia = DonutChartComponent.CIRCUNFERENCIA;
    let acumulado = 0;
    return this.dados.map((dado, indice) => {
      const fracao = dado.valor / total;
      const comprimento = fracao * circunferencia;
      const dashoffset = -acumulado;
      acumulado += comprimento;
      return {
        rotulo: dado.rotulo,
        cor: dado.cor ?? CORES[indice % CORES.length],
        dasharray: `${comprimento} ${circunferencia - comprimento}`,
        dashoffset,
        dado,
      };
    });
  }
}
