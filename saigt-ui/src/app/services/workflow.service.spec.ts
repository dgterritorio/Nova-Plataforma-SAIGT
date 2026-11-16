import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { beforeEach, describe, expect, it } from 'vitest';
import { WorkflowService } from './workflow.service';

describe('WorkflowService', () => {
  let service: WorkflowService;
  let httpTesting: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    service = TestBed.inject(WorkflowService);
    httpTesting = TestBed.inject(HttpTestingController);
  });

  it('pede os workflows configurados', () => {
    service.listar().subscribe();
    const requisicao = httpTesting.expectOne('/api/v1/workflows');
    expect(requisicao.request.method).toBe('GET');
    requisicao.flush([
      { id: 101, titulo: 'PDM — Elaboração', ativo: true, numeroPassos: 0, processosAtivos: 0, passos: [] },
    ]);
  });
});
