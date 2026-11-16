import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { DinamicasSemIgtDisponiveis } from '../models/dinamica.model';
import { DinamicaService } from './dinamica.service';

describe('DinamicaService', () => {
  let service: DinamicaService;
  let httpTesting: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    service = TestBed.inject(DinamicaService);
    httpTesting = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpTesting.verify());

  it('inicia a Dinâmica sem IGT com a configuração e o descritivo, sem Instrumentos', () => {
    service.iniciar({ instrumentoDinamicaConfigId: 901, descritivo: 'Suspensão parcial PDM Sintra e Cascais' }).subscribe();
    const requisicao = httpTesting.expectOne('/api/v1/dinamicas');
    expect(requisicao.request.method).toBe('POST');
    expect(requisicao.request.body).toEqual({ instrumentoDinamicaConfigId: 901, descritivo: 'Suspensão parcial PDM Sintra e Cascais' });
    expect(requisicao.request.body).not.toHaveProperty('instrumentoIds');
    requisicao.flush({ id: 'd1' });
  });

  it('associa os Instrumentos com o conjunto completo num PUT', () => {
    const resposta = { instrumentos: [{ id: 'i1', designacao: 'PDM de Sintra', tipoInstrumento: 'PDM' }] };
    service.associarInstrumentos('d1', ['i1']).subscribe((r) => expect(r).toEqual(resposta));
    const requisicao = httpTesting.expectOne('/api/v1/dinamicas/d1/instrumentos');
    expect(requisicao.request.method).toBe('PUT');
    expect(requisicao.request.body).toEqual({ instrumentoIds: ['i1'] });
    requisicao.flush(resposta);
  });

  it('pede as Dinâmicas sem IGT em curso', () => {
    const resposta: DinamicasSemIgtDisponiveis = { dinamicasEmCurso: [] };
    service.disponivel().subscribe((r) => expect(r).toEqual(resposta));
    const requisicao = httpTesting.expectOne('/api/v1/dinamicas/disponivel');
    expect(requisicao.request.method).toBe('GET');
    requisicao.flush(resposta);
  });
});
