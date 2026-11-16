import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { beforeEach, describe, expect, it } from 'vitest';
import { AdminService } from './admin.service';

describe('AdminService', () => {
  let service: AdminService;
  let httpTesting: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    service = TestBed.inject(AdminService);
    httpTesting = TestBed.inject(HttpTestingController);
  });

  it('pede as estatísticas do painel administrativo', () => {
    service.estatisticas().subscribe();
    const requisicao = httpTesting.expectOne('/api/v1/admin/estatisticas');
    expect(requisicao.request.method).toBe('GET');
    requisicao.flush({
      totalProcessos: 0,
      emAnalise: 0,
      publicadosComSucesso: 0,
      comPrazoExpirado: 0,
      submissoesPorMes: [],
      distribuicaoPorEstado: [],
      atividadeRecente: [],
    });
  });
});
