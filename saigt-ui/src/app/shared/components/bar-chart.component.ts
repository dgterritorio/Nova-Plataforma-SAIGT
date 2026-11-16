import { Component, Input } from '@angular/core';

export interface BarraDado {
  rotulo: string;
  valor: number;
}

@Component({
  selector: 'app-bar-chart',
  standalone: true,
  template: `
    <svg [attr.viewBox]="'0 0 ' + largura + ' ' + altura" class="grafico">
      @for (barra of barras(); track barra.rotulo) {
        <rect
          [attr.x]="barra.x"
          [attr.y]="barra.y"
          [attr.width]="larguraBarra"
          [attr.height]="barra.altura"
          [attr.fill]="cor"
          class="barra"
        />
        <text [attr.x]="barra.x + larguraBarra / 2" [attr.y]="altura - 6" class="rotulo" text-anchor="middle">
          {{ barra.rotulo }}
        </text>
        <text [attr.x]="barra.x + larguraBarra / 2" [attr.y]="barra.y - 4" class="valor" text-anchor="middle">
          {{ barra.dado.valor }}
        </text>
      } @empty {
        <text [attr.x]="largura / 2" [attr.y]="altura / 2" class="vazio" text-anchor="middle">Sem dados</text>
      }
    </svg>
  `,
  styles: [
    `
      .grafico {
        width: 100%;
        height: 160px;
      }


      .rotulo {
        font-size: 8px;
        fill: #64748b;
      }

      .valor {
        font-size: 8px;
        font-weight: 700;
        fill: #0f1923;
      }

      .vazio {
        font-size: 10px;
        fill: #94a3b8;
      }
    `,
  ],
})
export class BarChartComponent {
  @Input() dados: BarraDado[] = [];
  @Input() cor = '#1a56a0';

  readonly largura = 300;
  readonly altura = 130;
  private readonly alturaUtil = 90;
  private readonly margemInferior = 24;
  readonly larguraBarra = 28;

  barras(): { x: number; y: number; altura: number; rotulo: string; dado: BarraDado }[] {
    if (this.dados.length === 0) {
      return [];
    }
    const maximo = Math.max(...this.dados.map((d) => d.valor), 1);
    const espaco = this.largura / this.dados.length;
    return this.dados.map((dado, indice) => {
      const alturaBarra = (dado.valor / maximo) * this.alturaUtil;
      return {
        x: indice * espaco + (espaco - this.larguraBarra) / 2,
        y: this.altura - this.margemInferior - alturaBarra,
        altura: alturaBarra,
        rotulo: dado.rotulo,
        dado,
      };
    });
  }
}
