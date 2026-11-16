import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { NotificacaoService } from './notificacao.service';

const ID = '3f0e8a52-0000-4000-8000-000000000001';

describe('NotificacaoService', () => {
  let service: NotificacaoService;
  let httpTesting: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    service = TestBed.inject(NotificacaoService);
    httpTesting = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpTesting.verify());

  it('repete os parâmetros das listas e junta page e size', () => {
    service.listar({ estado: ['LIDA', 'RESOLVIDA'], tipo: ['TEMPORARIA', 'ALERTA'], processoId: ID }, 2, 20).subscribe();

    const pedido = httpTesting.expectOne((r) => r.url === '/api/v1/notificacoes');
    expect(pedido.request.params.getAll('estado')).toEqual(['LIDA', 'RESOLVIDA']);
    expect(pedido.request.params.getAll('tipo')).toEqual(['TEMPORARIA', 'ALERTA']);
    expect(pedido.request.params.get('processoId')).toBe(ID);
    expect(pedido.request.urlWithParams).toContain('estado=LIDA&estado=RESOLVIDA');
    expect(pedido.request.params.get('page')).toBe('2');
    expect(pedido.request.params.get('size')).toBe('20');
    pedido.flush({ content: [], page: 2, size: 20, totalElements: 0, totalPages: 0 });
  });

  it('sem filtros só manda page e size', () => {
    service.listar({}, 0, 5).subscribe();

    const pedido = httpTesting.expectOne((r) => r.url === '/api/v1/notificacoes');
    expect(pedido.request.params.keys()).toEqual(['page', 'size']);
    pedido.flush({ content: [], page: 0, size: 5, totalElements: 0, totalPages: 0 });
  });

  it('obtém o detalhe pelo id', () => {
    service.obter(ID).subscribe();

    httpTesting.expectOne(`/api/v1/notificacoes/${ID}`).flush({});
  });

  it('responder faz POST de { texto } em /notificacoes/{id}/mensagens e devolve a mensagem e o estado', () => {
    let resposta: unknown;
    service.responder(ID, 'Pedido tratado.').subscribe((valor) => (resposta = valor));

    const pedido = httpTesting.expectOne(`/api/v1/notificacoes/${ID}/mensagens`);
    expect(pedido.request.method).toBe('POST');
    expect(pedido.request.body).toEqual({ texto: 'Pedido tratado.' });
    const corpo = {
      mensagem: { id: 'm1', autorId: 'u1', autorNome: 'Marta Master', texto: 'Pedido tratado.', criadoEm: '2026-07-22T09:00:00Z' },
      estado: 'RESOLVIDA',
    };
    pedido.flush(corpo);
    expect(resposta).toEqual(corpo);
  });

  it('contarNaoLidas pede uma página de 1 só com TEMPORARIA e ALERTA ativas e devolve o totalElements', () => {
    let total: number | undefined;
    service.contarNaoLidas().subscribe((valor) => (total = valor));

    const pedido = httpTesting.expectOne((r) => r.url === '/api/v1/notificacoes');
    expect(pedido.request.urlWithParams).toBe('/api/v1/notificacoes?estado=ATIVA&tipo=TEMPORARIA&tipo=ALERTA&page=0&size=1');
    pedido.flush({ content: [], page: 0, size: 1, totalElements: 3, totalPages: 3 });
    expect(total).toBe(3);
  });

  it('criar faz POST do pedido e devolve as notificações e o lote', () => {
    let resposta: unknown;
    const corpo = {
      tipo: 'ESTATICA' as const,
      titulo: 'Aviso',
      texto: 'Texto',
      destinatarios: [{ entidadeId: 1 }, { entidadeId: 2 }],
    };
    service.criar(corpo).subscribe((r) => (resposta = r));

    const pedido = httpTesting.expectOne('/api/v1/notificacoes');
    expect(pedido.request.method).toBe('POST');
    expect(pedido.request.body).toEqual(corpo);
    pedido.flush({ notificacoes: [], loteId: ID }, { status: 201, statusText: 'Created' });
    expect(resposta).toEqual({ notificacoes: [], loteId: ID });
  });

  it('reabrir faz PATCH sem corpo em /notificacoes/{id}/reabrir e devolve o resumo', () => {
    let resposta: unknown;
    service.reabrir(ID).subscribe((r) => (resposta = r));

    const pedido = httpTesting.expectOne(`/api/v1/notificacoes/${ID}/reabrir`);
    expect(pedido.request.method).toBe('PATCH');
    expect(pedido.request.body).toBeNull();
    pedido.flush({ id: ID, estado: 'ATIVA', resolvidoEm: null });
    expect(resposta).toEqual({ id: ID, estado: 'ATIVA', resolvidoEm: null });
  });

  it('ler, resolver e remover fazem PATCH sem corpo na operação do id', () => {
    let remocao: unknown;
    service.ler(ID).subscribe();
    service.resolver(ID).subscribe();
    service.remover(ID).subscribe((r) => (remocao = r));

    for (const operacao of ['ler', 'resolver', 'remover']) {
      const pedido = httpTesting.expectOne(`/api/v1/notificacoes/${ID}/${operacao}`);
      expect(pedido.request.method).toBe('PATCH');
      expect(pedido.request.body).toBeNull();
      pedido.flush(operacao === 'remover' ? { linhasAtualizadas: 3 } : {});
    }
    expect(remocao).toEqual({ linhasAtualizadas: 3 });
  });
  it('anexar envia um FormData com a parte ficheiro', () => {
    const ficheiro = new File(['abc'], 'oficio.pdf', { type: 'application/pdf' });
    service.anexar(ID, ficheiro).subscribe();

    const pedido = httpTesting.expectOne(`/api/v1/notificacoes/${ID}/documentos`);
    expect(pedido.request.method).toBe('POST');
    expect((pedido.request.body as FormData).get('ficheiro')).toBe(ficheiro);
    pedido.flush({ id: 'd1', nomeFicheiro: 'oficio.pdf', tamanhoBytes: 3, criadoEm: '2026-11-13T10:00:00Z' });
  });

  it('urlDownload faz POST à descarga do anexo, sem corpo', () => {
    let url = '';
    service.urlDownload(ID, 'd1').subscribe((resposta) => (url = resposta.url));

    const pedido = httpTesting.expectOne(`/api/v1/notificacoes/${ID}/documentos/d1/descarga`);
    expect(pedido.request.method).toBe('POST');
    expect(pedido.request.body).toBeNull();
    pedido.flush({ url: 'http://localhost:8080/api/v1/descargas/t' });
    expect(url).toBe('http://localhost:8080/api/v1/descargas/t');
  });
});
