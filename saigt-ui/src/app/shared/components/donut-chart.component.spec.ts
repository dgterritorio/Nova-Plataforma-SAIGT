import { ComponentFixture, TestBed } from '@angular/core/testing';
import { describe, expect, it } from 'vitest';
import { DonutChartComponent } from './donut-chart.component';

describe('DonutChartComponent', () => {
  it('devolve uma lista vazia sem dados', () => {
    const componente = new DonutChartComponent();
    componente.dados = [];

    expect(componente.segmentos()).toEqual([]);
  });

  it('devolve uma lista vazia quando o total é zero', () => {
    const componente = new DonutChartComponent();
    componente.dados = [{ rotulo: 'A', valor: 0 }];

    expect(componente.segmentos()).toEqual([]);
  });

  it('reparte a circunferência proporcionalmente ao valor de cada segmento', () => {
    const componente = new DonutChartComponent();
    componente.dados = [
      { rotulo: 'A', valor: 25 },
      { rotulo: 'B', valor: 75 },
    ];

    const segmentos = componente.segmentos();
    const circunferencia = 2 * Math.PI * 35;
    const comprimentoA = Number(segmentos[0].dasharray.split(' ')[0]);
    const comprimentoB = Number(segmentos[1].dasharray.split(' ')[0]);

    expect(comprimentoA).toBeCloseTo(circunferencia * 0.25, 3);
    expect(comprimentoB).toBeCloseTo(circunferencia * 0.75, 3);
    expect(segmentos[0].dashoffset).toBe(-0);
    expect(segmentos[1].dashoffset).toBeCloseTo(-comprimentoA, 3);
  });

  it('usa a cor explícita do dado quando indicada, em vez da paleta por posição', () => {
    const componente = new DonutChartComponent();
    componente.dados = [
      { rotulo: 'A', valor: 1 },
      { rotulo: 'B', valor: 1, cor: '#dc2626' },
    ];

    const segmentos = componente.segmentos();

    expect(segmentos[0].cor).toBe('#1a56a0');
    expect(segmentos[1].cor).toBe('#dc2626');
  });

  it('mostra um <title> por segmento com o rótulo e o valor, para tooltip ao passar o rato', () => {
    TestBed.configureTestingModule({ imports: [DonutChartComponent] });
    const fixture: ComponentFixture<DonutChartComponent> = TestBed.createComponent(DonutChartComponent);
    fixture.componentInstance.dados = [
      { rotulo: 'Validado', valor: 234 },
      { rotulo: 'Em análise', valor: 187 },
    ];
    fixture.detectChanges();

    const titulos = [...fixture.nativeElement.querySelectorAll('circle title')].map((titulo: Element) => titulo.textContent);
    expect(titulos).toEqual(['Validado (234)', 'Em análise (187)']);
  });

  it('usa `tooltip`, quando presente, no <title> do segmento e no atributo title da legenda, sem substituir o texto visível', () => {
    TestBed.configureTestingModule({ imports: [DonutChartComponent] });
    const fixture: ComponentFixture<DonutChartComponent> = TestBed.createComponent(DonutChartComponent);
    fixture.componentInstance.dados = [{ rotulo: 'Pendente', valor: 6, tooltip: 'Enviado DGT (4), Em submissão (2)' }];
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('circle title').textContent).toBe('Enviado DGT (4), Em submissão (2)');
    const item = fixture.nativeElement.querySelector('.legenda li');
    expect(item.getAttribute('title')).toBe('Enviado DGT (4), Em submissão (2)');
    expect(item.textContent.trim()).toBe('Pendente (6)');
  });
});
