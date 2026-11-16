import { describe, expect, it } from 'vitest';
import { BarChartComponent } from './bar-chart.component';

describe('BarChartComponent', () => {
  it('devolve uma lista vazia sem dados', () => {
    const componente = new BarChartComponent();
    componente.dados = [];

    expect(componente.barras()).toEqual([]);
  });

  it('escala a altura da barra ao máximo do conjunto', () => {
    const componente = new BarChartComponent();
    componente.dados = [
      { rotulo: 'jan', valor: 5 },
      { rotulo: 'fev', valor: 10 },
    ];

    const barras = componente.barras();

    expect(barras).toHaveLength(2);
    expect(barras[1].altura).toBeGreaterThan(barras[0].altura);
    expect(barras[1].altura).toBeCloseTo(barras[0].altura * 2, 5);
  });
});
