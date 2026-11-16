import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { PreferenciaKpi, ValorKpi } from '../models/dashboard.model';
import { DashboardService } from './dashboard.service';

describe('DashboardService', () => {
  let service: DashboardService;
  let httpTesting: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    service = TestBed.inject(DashboardService);
    httpTesting = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpTesting.verify());

  it('pede os KPIs com GET /api/v1/dashboard/kpis', () => {
    const kpis: ValorKpi[] = [
      { codigo: 'PROCESSOS_POR_ESTADO', designacao: 'Processos', valor: 3, serie: [], calculadoEm: '2026-11-13T10:00:00Z' },
    ];
    let recebidos: ValorKpi[] | undefined;
    service.kpis().subscribe((resposta) => (recebidos = resposta));

    const pedido = httpTesting.expectOne('/api/v1/dashboard/kpis');
    expect(pedido.request.method).toBe('GET');
    pedido.flush(kpis);
    expect(recebidos).toEqual(kpis);
  });

  it('pede as preferências com GET /api/v1/dashboard/preferencias', () => {
    service.preferencias().subscribe();

    const pedido = httpTesting.expectOne('/api/v1/dashboard/preferencias');
    expect(pedido.request.method).toBe('GET');
    pedido.flush([]);
  });

  it('grava as preferências com PUT /api/v1/dashboard/preferencias e devolve a lista gravada', () => {
    const gravadas: PreferenciaKpi[] = [{ kpiCodigo: 'GRAFICO_MES', designacao: 'Mês', visivel: true, ordem: 0 }];
    let recebidas: PreferenciaKpi[] | undefined;
    service.gravarPreferencias([{ kpiCodigo: 'GRAFICO_MES', visivel: true, ordem: 0 }]).subscribe((resposta) => (recebidas = resposta));

    const pedido = httpTesting.expectOne('/api/v1/dashboard/preferencias');
    expect(pedido.request.method).toBe('PUT');
    expect(pedido.request.body).toEqual([{ kpiCodigo: 'GRAFICO_MES', visivel: true, ordem: 0 }]);
    pedido.flush(gravadas);
    expect(recebidas).toEqual(gravadas);
  });
});
