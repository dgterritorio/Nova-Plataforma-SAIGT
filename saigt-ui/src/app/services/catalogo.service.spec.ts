import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { beforeEach, describe, expect, it } from 'vitest';
import { CatalogoService } from './catalogo.service';

describe('CatalogoService', () => {
  let service: CatalogoService;
  let httpTesting: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    service = TestBed.inject(CatalogoService);
    httpTesting = TestBed.inject(HttpTestingController);
  });

  it('pede os catálogos globais', () => {
    service.listar().subscribe();
    const requisicao = httpTesting.expectOne('/api/v1/catalogos');
    expect(requisicao.request.method).toBe('GET');
    requisicao.flush([
      { titulo: 'Tipos IGT', itens: ['PDM'] },
      { titulo: 'Tipos de procedimento', itens: [] },
    ]);
  });
});
