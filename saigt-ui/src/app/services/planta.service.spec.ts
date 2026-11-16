import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { PlantaService } from './planta.service';

describe('PlantaService', () => {
  let service: PlantaService;
  let httpTesting: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    service = TestBed.inject(PlantaService);
    httpTesting = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpTesting.verify());

  it('lista com a página e só os filtros preenchidos (emVigor=false também conta)', () => {
    service.listar({ numeroProcesso: '2025/000123', numeroDeposito: undefined, emVigor: false }, 2, 20).subscribe();
    const pedido = httpTesting.expectOne((r) => r.url === '/api/v1/plantas');
    expect(pedido.request.method).toBe('GET');
    expect(pedido.request.params.get('page')).toBe('2');
    expect(pedido.request.params.get('size')).toBe('20');
    expect(pedido.request.params.get('numeroProcesso')).toBe('2025/000123');
    expect(pedido.request.params.has('numeroDeposito')).toBe(false);
    expect(pedido.request.params.get('emVigor')).toBe('false');
    pedido.flush({ content: [], page: 2, size: 20, totalElements: 0, totalPages: 0 });
  });

  it('atualiza por PATCH com as alterações no corpo', () => {
    service.atualizar('p-1', { numeroFolha: '12', emVigor: true }).subscribe();
    const pedido = httpTesting.expectOne('/api/v1/plantas/p-1');
    expect(pedido.request.method).toBe('PATCH');
    expect(pedido.request.body).toEqual({ numeroFolha: '12', emVigor: true });
    pedido.flush({});
  });
});
