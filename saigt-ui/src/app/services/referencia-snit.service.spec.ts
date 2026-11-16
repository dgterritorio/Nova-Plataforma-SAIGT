import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { ReferenciaSnitService } from './referencia-snit.service';

describe('ReferenciaSnitService', () => {
  let service: ReferenciaSnitService;
  let httpTesting: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    service = TestBed.inject(ReferenciaSnitService);
    httpTesting = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpTesting.verify());

  it('lê os concelhos da réplica, sem filtro', () => {
    let recebidos: unknown;
    service.concelhos().subscribe((concelhos) => (recebidos = concelhos));
    const requisicao = httpTesting.expectOne((r) => r.url === '/api/v1/referencias-snit/MUNICIPALITIES');
    expect(requisicao.request.method).toBe('GET');
    expect(requisicao.request.params.has('nut2')).toBe(false);
    requisicao.flush([{ codigo: '1111', nome: 'SINTRA', nut2: 'LISBOA E VALE DO TEJO' }]);
    expect(recebidos).toEqual([{ codigo: '1111', nome: 'SINTRA', nut2: 'LISBOA E VALE DO TEJO' }]);
  });

  it('restringe os concelhos à NUTS II indicada', () => {
    service.concelhos('NORTE').subscribe();
    const requisicao = httpTesting.expectOne((r) => r.url === '/api/v1/referencias-snit/MUNICIPALITIES');
    expect(requisicao.request.params.get('nut2')).toBe('NORTE');
    requisicao.flush([]);
  });
});
