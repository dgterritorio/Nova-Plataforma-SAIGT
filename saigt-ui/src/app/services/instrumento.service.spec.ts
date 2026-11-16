import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { beforeEach, describe, expect, it } from 'vitest';
import { InstrumentoService } from './instrumento.service';
import { EstatisticasDinamica, InstrumentoDisponibilidade } from '../models/instrumento.model';

describe('InstrumentoService', () => {
  let service: InstrumentoService;
  let httpTesting: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    service = TestBed.inject(InstrumentoService);
    httpTesting = TestBed.inject(HttpTestingController);
  });

  it('pede a listagem paginada com page/size', () => {
    service.listar(1, 20).subscribe();
    const requisicao = httpTesting.expectOne((r) => r.url === '/api/v1/instrumentos');
    expect(requisicao.request.params.get('page')).toBe('1');
    expect(requisicao.request.params.get('size')).toBe('20');
    requisicao.flush({ content: [], page: 1, size: 20, totalElements: 0, totalPages: 0 });
  });

  it('pesquisa os IGT em vigor no SNIT pelo tipo, sem município', () => {
    let pagina: unknown;
    service.pesquisarSnit(1, 0, 100).subscribe((resposta) => (pagina = resposta));
    const requisicao = httpTesting.expectOne((r) => r.url === '/api/v1/instrumentos/snit');
    expect(requisicao.request.method).toBe('GET');
    expect(requisicao.request.params.get('tipoInstrumentoId')).toBe('1');
    expect(requisicao.request.params.get('page')).toBe('0');
    expect(requisicao.request.params.get('size')).toBe('100');
    expect(requisicao.request.params.has('municipio')).toBe(false);
    const corpo = {
      content: [{ idIgtSnit: '1001', designacao: 'PDM de Sintra (SNIT)', tipoInstrumento: 'PDM', instrumentoId: null }],
      page: 0,
      size: 100,
      totalElements: 1,
      totalPages: 1,
    };
    requisicao.flush(corpo);
    expect(pagina).toEqual(corpo);
  });

  it('cria o Instrumento a partir de um IGT do SNIT com idIgtSnit e sem concelhos', () => {
    service.criar({ tipoInstrumentoId: 1, designacao: 'PDM de Sintra (SNIT)', idIgtSnit: '1001' }).subscribe();
    const requisicao = httpTesting.expectOne('/api/v1/instrumentos');
    expect(requisicao.request.method).toBe('POST');
    expect(requisicao.request.body).toEqual({ tipoInstrumentoId: 1, designacao: 'PDM de Sintra (SNIT)', idIgtSnit: '1001' });
    requisicao.flush({});
  });

  it('pesquisa só Instrumentos em vigor, repetindo o tipo e com concelho e designação', () => {
    service.pesquisarEmVigor({ tiposInstrumentoId: [1, 3], concelho: '1111', designacao: ' Sintra ' }, 0, 20).subscribe();
    const requisicao = httpTesting.expectOne((r) => r.url === '/api/v1/instrumentos');
    expect(requisicao.request.params.get('emVigor')).toBe('true');
    expect(requisicao.request.params.getAll('tipoInstrumentoId')).toEqual(['1', '3']);
    expect(requisicao.request.params.get('concelho')).toBe('1111');
    expect(requisicao.request.params.get('designacao')).toBe('Sintra');
    requisicao.flush({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 });
  });

  it('sem concelho nem designação não os envia', () => {
    service.pesquisarEmVigor({ tiposInstrumentoId: [1], concelho: null, designacao: '  ' }, 0, 20).subscribe();
    const requisicao = httpTesting.expectOne((r) => r.url === '/api/v1/instrumentos');
    expect(requisicao.request.params.has('concelho')).toBe(false);
    expect(requisicao.request.params.has('designacao')).toBe(false);
    requisicao.flush({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 });
  });

  it('pede os concelhos que se podem indicar ao criar um Instrumento', () => {
    const concelhos = [{ codigo: '1111', nome: 'Sintra' }];
    service.listarConcelhos().subscribe((r) => expect(r).toEqual(concelhos));
    const requisicao = httpTesting.expectOne('/api/v1/instrumentos/concelhos');
    expect(requisicao.request.method).toBe('GET');
    requisicao.flush(concelhos);
  });

  it('pede os tipos de processo dos dois grupos do primeiro passo', () => {
    const resposta = { comAssociacaoIgt: [{ tipoInstrumentoId: 1, codigo: 'PDM', nome: 'Plano Diretor Municipal' }], semAssociacaoIgt: [] };
    service.tiposProcesso().subscribe((r) => expect(r).toEqual(resposta));
    const requisicao = httpTesting.expectOne('/api/v1/criacao/tipos-processo');
    expect(requisicao.request.method).toBe('GET');
    requisicao.flush(resposta);
  });

  it('chama GET /disponivel para o id indicado', () => {
    const resposta: InstrumentoDisponibilidade = { dinamicasDisponiveis: [], dinamicasEmCurso: [], ultimaDinamicaPublicada: null };
    service.disponivel('i1').subscribe((r) => expect(r).toEqual(resposta));
    httpTesting.expectOne('/api/v1/instrumentos/i1/disponivel').flush(resposta);
  });

  it('pede o painel do Instrumento com um único GET /api/v1/instrumentos/{id}', () => {
    service.painel('i1').subscribe();
    const requisicao = httpTesting.expectOne('/api/v1/instrumentos/i1');
    expect(requisicao.request.method).toBe('GET');
    requisicao.flush({});
    httpTesting.verify();
  });

  it('pede as estatísticas da Dinâmica com GET /api/v1/dinamicas/{id}/estatisticas e devolve o modelo', () => {
    const resposta: EstatisticasDinamica = {
      dinamicaId: 'd1',
      numeroRejeicoesDgt: 2,
      numeroAnulacoesIncm: 1,
      numeroCiclosResubmissao: null,
      numeroComunicacoes: null,
      processos: [
        {
          processoId: 'p1',
          numeroProcesso: '12',
          tipoProcedimento: 'DECISAO_INICIO',
          ocorrencia: 1,
          numeroRejeicoesDgt: 2,
          numeroAnulacoesIncm: 1,
          numeroCiclosResubmissao: null,
          numeroComunicacoes: null,
        },
      ],
    };
    let recebida: EstatisticasDinamica | undefined;
    service.obterEstatisticasDinamica('d1').subscribe((r) => (recebida = r));
    const requisicao = httpTesting.expectOne('/api/v1/dinamicas/d1/estatisticas');
    expect(requisicao.request.method).toBe('GET');
    requisicao.flush(resposta);
    expect(recebida).toEqual(resposta);
    httpTesting.verify();
  });

  it('inicia uma Dinâmica com o instrumentoDinamicaConfigId e o descritivo indicados', () => {
    service.iniciarDinamica('i1', { instrumentoDinamicaConfigId: 7, descritivo: 'Alteração da planta' }).subscribe();
    const requisicao = httpTesting.expectOne('/api/v1/instrumentos/i1/dinamicas');
    expect(requisicao.request.method).toBe('POST');
    expect(requisicao.request.body).toEqual({ instrumentoDinamicaConfigId: 7, descritivo: 'Alteração da planta' });
    requisicao.flush({});
  });
});
