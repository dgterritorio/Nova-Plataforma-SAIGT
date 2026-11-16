import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { EntidadeService } from './entidade.service';

describe('EntidadeService', () => {
  let service: EntidadeService;
  let httpTesting: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    service = TestBed.inject(EntidadeService);
    httpTesting = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpTesting.verify());

  it('pede a listagem só com os filtros preenchidos, mais page/size', () => {
    service.listar({ ativo: true, nome: 'Lisboa', tipoEntidade: undefined }, 0, 20).subscribe();
    const requisicao = httpTesting.expectOne((r) => r.url === '/api/v1/entidades');
    expect(requisicao.request.params.get('ativo')).toBe('true');
    expect(requisicao.request.params.get('nome')).toBe('Lisboa');
    expect(requisicao.request.params.has('tipoEntidade')).toBe(false);
    expect(requisicao.request.params.get('page')).toBe('0');
    expect(requisicao.request.params.get('size')).toBe('20');
    requisicao.flush({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 });
  });

  it('filtra por tipo de entidade', () => {
    service.listar({ tipoEntidade: 'PCM' }, 1, 20).subscribe();
    const requisicao = httpTesting.expectOne((r) => r.url === '/api/v1/entidades');
    expect(requisicao.request.params.get('tipoEntidade')).toBe('PCM');
    expect(requisicao.request.params.get('page')).toBe('1');
    requisicao.flush({ content: [], page: 1, size: 20, totalElements: 0, totalPages: 0 });
  });

  it('pede a lista das entidades emitentes de um tipo, sem paginação', () => {
    let lista: unknown;
    service.emitentes('CCDR_DR').subscribe((resposta) => (lista = resposta));
    const requisicao = httpTesting.expectOne((r) => r.url === '/api/v1/entidades/emitentes');
    expect(requisicao.request.method).toBe('GET');
    expect(requisicao.request.params.get('tipoEntidade')).toBe('CCDR_DR');
    expect(requisicao.request.params.has('page')).toBe(false);
    requisicao.flush([{ id: 1, nome: 'CCDR Norte' }]);
    expect(lista).toEqual([{ id: 1, nome: 'CCDR Norte' }]);
  });

  it('cria uma entidade com o corpo recebido', () => {
    const pedido = { codigo: '9001', descricaoIncm: 'Município X', descricaoSaigt: 'Câmara Municipal X', tipoEntidade: 'CM' as const };
    service.criar(pedido).subscribe();
    const requisicao = httpTesting.expectOne('/api/v1/entidades');
    expect(requisicao.request.method).toBe('POST');
    expect(requisicao.request.body).toEqual(pedido);
    requisicao.flush({});
  });

  it('atualiza só os campos recebidos, com vazio para limpar', () => {
    service.atualizar(7, { nipc: '', dico: '1111' }).subscribe();
    const requisicao = httpTesting.expectOne('/api/v1/entidades/7');
    expect(requisicao.request.method).toBe('PATCH');
    expect(requisicao.request.body).toEqual({ nipc: '', dico: '1111' });
    requisicao.flush({});
  });

  it('elimina uma entidade', () => {
    service.eliminar(7).subscribe();
    const requisicao = httpTesting.expectOne('/api/v1/entidades/7');
    expect(requisicao.request.method).toBe('DELETE');
    requisicao.flush(null, { status: 204, statusText: 'No Content' });
  });

  it('lista os contactos da entidade', () => {
    let recebidos: unknown;
    service.listarContactos(7).subscribe((contactos) => (recebidos = contactos));
    const requisicao = httpTesting.expectOne('/api/v1/entidades/7/contactos');
    expect(requisicao.request.method).toBe('GET');
    requisicao.flush([{ id: 1, email: 'a@cm.pt', principal: true, ativo: true }]);
    expect(recebidos).toEqual([{ id: 1, email: 'a@cm.pt', principal: true, ativo: true }]);
  });

  it('cria o contacto com o principal só quando pedido', () => {
    service.criarContacto(7, { email: 'geral@cm-sintra.pt', descricao: 'Urbanismo', telefone: null }).subscribe();
    const requisicao = httpTesting.expectOne('/api/v1/entidades/7/contactos');
    expect(requisicao.request.method).toBe('POST');
    expect(requisicao.request.body).toEqual({ email: 'geral@cm-sintra.pt', descricao: 'Urbanismo', telefone: null });
    expect('principal' in requisicao.request.body).toBe(false);
    requisicao.flush({});
  });

  it('altera um contacto só com os campos recebidos', () => {
    service.editarContacto(7, 3, { ativo: false }).subscribe();
    const requisicao = httpTesting.expectOne('/api/v1/entidades/7/contactos/3');
    expect(requisicao.request.method).toBe('PATCH');
    expect(requisicao.request.body).toEqual({ ativo: false });
    requisicao.flush({});
  });
});
