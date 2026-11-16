import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { DescargaService, LOCALIZACAO } from './descarga.service';

describe('DescargaService', () => {
  let service: DescargaService;
  let httpTesting: HttpTestingController;
  let assign: ReturnType<typeof vi.fn>;

  beforeEach(() => {
    assign = vi.fn();
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), { provide: LOCALIZACAO, useValue: { assign } }],
    });
    service = TestBed.inject(DescargaService);
    httpTesting = TestBed.inject(HttpTestingController);
  });

  it('faz POST à descarga do documento e navega para o url devolvido', () => {
    service.descarregar('p1', 'd1');

    const requisicao = httpTesting.expectOne('/api/v1/processos/p1/documentos/d1/descarga');
    expect(requisicao.request.method).toBe('POST');
    requisicao.flush({ url: 'https://saigt.test/api/v1/descargas/ticket-1' });

    expect(assign).toHaveBeenCalledWith('https://saigt.test/api/v1/descargas/ticket-1');
    expect(service.erro()).toBeNull();
    httpTesting.verify();
  });

  it('não faz GET dos bytes do ficheiro', () => {
    service.descarregar('p1', 'd1');
    httpTesting.expectOne('/api/v1/processos/p1/documentos/d1/descarga').flush({ url: 'https://saigt.test/api/v1/descargas/t' });

    expect(httpTesting.match(() => true)).toHaveLength(0);
  });

  it('em erro expõe a mensagem e não navega', () => {
    service.descarregar('p1', 'd1');

    httpTesting.expectOne('/api/v1/processos/p1/documentos/d1/descarga').flush(null, { status: 500, statusText: 'Erro' });

    expect(service.erro()).toBe('Não foi possível descarregar o ficheiro. Tente novamente.');
    expect(assign).not.toHaveBeenCalled();
  });

  it('expõe o documento em descarga enquanto o ticket é pedido, também quando falha', () => {
    service.descarregar('p1', 'd1');
    expect(service.emCurso()).toBe('d1');
    httpTesting.expectOne('/api/v1/processos/p1/documentos/d1/descarga').flush({ url: 'https://saigt.test/api/v1/descargas/t' });
    expect(service.emCurso()).toBeNull();

    service.descarregar('p1', 'd2');
    expect(service.emCurso()).toBe('d2');
    httpTesting.expectOne('/api/v1/processos/p1/documentos/d2/descarga').flush(null, { status: 500, statusText: 'Erro' });
    expect(service.emCurso()).toBeNull();
  });

  it('limpa o erro ao tentar de novo', () => {
    service.descarregar('p1', 'd1');
    httpTesting.expectOne('/api/v1/processos/p1/documentos/d1/descarga').flush(null, { status: 500, statusText: 'Erro' });

    service.descarregar('p1', 'd1');

    expect(service.erro()).toBeNull();
    httpTesting.expectOne('/api/v1/processos/p1/documentos/d1/descarga');
  });
});
