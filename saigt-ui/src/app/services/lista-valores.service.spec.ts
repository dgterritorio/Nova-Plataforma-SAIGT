import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { beforeEach, describe, expect, it } from 'vitest';
import { TIPO_PAI_LISTA_VALORES } from '../models/lista-valores.model';
import { ListaValoresService } from './lista-valores.service';

describe('ListaValoresService', () => {
  let service: ListaValoresService;
  let httpTesting: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    service = TestBed.inject(ListaValoresService);
    httpTesting = TestBed.inject(HttpTestingController);
  });

  it('lista sem filtro de tipo', () => {
    service.listar().subscribe();
    const requisicao = httpTesting.expectOne((r) => r.url === '/api/v1/lista-valores');
    expect(requisicao.request.params.has('tipo')).toBe(false);
    requisicao.flush([]);
  });

  it('lista filtrada por tipo', () => {
    service.listar('MOTIVO_REJEICAO_DGT').subscribe();
    const requisicao = httpTesting.expectOne((r) => r.url === '/api/v1/lista-valores');
    expect(requisicao.request.params.get('tipo')).toBe('MOTIVO_REJEICAO_DGT');
    requisicao.flush([]);
  });

  it('lê as opções ativas de uma lista dos formulários', () => {
    let lidas: unknown;
    service.opcoes('FREGUESIA').subscribe((opcoes) => (lidas = opcoes));
    const requisicao = httpTesting.expectOne((r) => r.url === '/api/v1/lista-valores/opcoes');
    expect(requisicao.request.method).toBe('GET');
    expect(requisicao.request.params.get('tipo')).toBe('FREGUESIA');
    requisicao.flush([{ codigo: 'F01', descricao: 'Colares' }]);
    expect(lidas).toEqual([{ codigo: 'F01', descricao: 'Colares' }]);
  });

  it('cria um item num tipo existente', () => {
    service.criar({ tipo: 'MOTIVO_REJEICAO_DGT', codigo: 'NOVO', descricao: 'Novo motivo', ordem: 3 }).subscribe();
    const requisicao = httpTesting.expectOne('/api/v1/lista-valores');
    expect(requisicao.request.method).toBe('POST');
    expect(requisicao.request.body).toEqual({ tipo: 'MOTIVO_REJEICAO_DGT', codigo: 'NOVO', descricao: 'Novo motivo', ordem: 3 });
    requisicao.flush({});
  });

  it('cria um valor de uma lista com hierarquia com o paiId', () => {
    service.criar({ tipo: 'CLASSE_SRUP', codigo: 'C1', descricao: 'Classe 1', ordem: 1, paiId: 4 }).subscribe();
    const requisicao = httpTesting.expectOne('/api/v1/lista-valores');
    expect(requisicao.request.body).toEqual({ tipo: 'CLASSE_SRUP', codigo: 'C1', descricao: 'Classe 1', ordem: 1, paiId: 4 });
    requisicao.flush({});
  });

  it('devolve os tipos geridos pelo Master, com rótulo, sem pedir nada à API', () => {
    let tipos: { tipo: string; descricao: string }[] = [];
    service.tipos().subscribe((lidos) => (tipos = lidos));
    httpTesting.expectNone(() => true);
    expect(tipos.map((t) => t.tipo)).toEqual(
      expect.arrayContaining([
        'FREGUESIA',
        'TOPONIMO',
        'MOTIVO_ALTERACAO_LEI',
        'MOTIVO_ALTERACAO_PROGRAMA',
        'MOTIVO_ALTERACAO_PLANO',
        'ALTERACAO_PEOT',
        'CATEGORIA_REJEICAO_DGT',
        'MOTIVO_REJEICAO_DGT',
        'MOTIVO_PROCEDIMENTO_REN',
        'DOMINIO_SRUP',
        'CLASSE_SRUP',
        'TIPOLOGIA_SRUP',
        'FUNDAMENTACAO_RJIGT',
        'REGIAO',
        'CIM_AM',
      ]),
    );
    expect(tipos.find((t) => t.tipo === 'FREGUESIA')?.descricao).toBe('Freguesia');
  });

  it('inclui a Região e a CIM / AM do formulário SGIFR, com a CIM / AM dependente da Região', () => {
    let tipos: { tipo: string; descricao: string }[] = [];
    service.tipos().subscribe((lidos) => (tipos = lidos));
    expect(tipos.find((t) => t.tipo === 'REGIAO')?.descricao).toBe('Região');
    expect(tipos.find((t) => t.tipo === 'CIM_AM')?.descricao).toBe('CIM / AM');
    expect(TIPO_PAI_LISTA_VALORES['CIM_AM']).toBe('REGIAO');
    expect(TIPO_PAI_LISTA_VALORES['REGIAO']).toBeUndefined();
  });

  it('cria uma CIM / AM com a Região como pai', () => {
    service.criar({ tipo: 'CIM_AM', codigo: 'C01', descricao: 'CIM do Alto Minho', ordem: 1, paiId: 9 }).subscribe();
    const requisicao = httpTesting.expectOne('/api/v1/lista-valores');
    expect(requisicao.request.body).toEqual({ tipo: 'CIM_AM', codigo: 'C01', descricao: 'CIM do Alto Minho', ordem: 1, paiId: 9 });
    requisicao.flush({});
  });

  it('atualiza descricao/ordem/ativo de um item', () => {
    service.atualizar(7, { ativo: false }).subscribe();
    const requisicao = httpTesting.expectOne('/api/v1/lista-valores/7');
    expect(requisicao.request.method).toBe('PATCH');
    expect(requisicao.request.body).toEqual({ ativo: false });
    requisicao.flush({});
  });
});
