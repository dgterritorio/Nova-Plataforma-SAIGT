import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { beforeEach, describe, expect, it } from 'vitest';
import { UtilizadorService } from './utilizador.service';

describe('UtilizadorService', () => {
  let service: UtilizadorService;
  let httpTesting: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    service = TestBed.inject(UtilizadorService);
    httpTesting = TestBed.inject(HttpTestingController);
  });

  it('pede a listagem só com os filtros preenchidos, mais page/size', () => {
    service.listar({ perfilInterno: 'MASTER', entidadeId: undefined }, 0, 20).subscribe();
    const requisicao = httpTesting.expectOne((r) => r.url === '/api/v1/utilizadores');
    expect(requisicao.request.params.get('perfilInterno')).toBe('MASTER');
    expect(requisicao.request.params.has('entidadeId')).toBe(false);
    requisicao.flush({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 });
  });

  it('altera o perfil interno só com o perfil, sem motivo', () => {
    service.alterarPerfilInterno('u1', { perfilInterno: 'MASTER' }).subscribe();
    const requisicao = httpTesting.expectOne('/api/v1/utilizadores/u1/perfil-interno');
    expect(requisicao.request.method).toBe('PATCH');
    expect(requisicao.request.body).toEqual({ perfilInterno: 'MASTER' });
    requisicao.flush({});
  });

  it('revoga as sessões ativas sem corpo e devolve quantas revogou', () => {
    let revogadas: number | undefined;
    service.revogarSessoes('u1').subscribe((resposta) => (revogadas = resposta.sessoesRevogadas));
    const requisicao = httpTesting.expectOne('/api/v1/utilizadores/u1/sessoes/revogar');
    expect(requisicao.request.method).toBe('PATCH');
    expect(requisicao.request.body).toBeNull();
    requisicao.flush({ sessoesRevogadas: 2 });
    expect(revogadas).toBe(2);
  });

  it('não tem criar, desativar, reativar nem remover', () => {
    const metodos = service as unknown as Record<string, unknown>;
    for (const retirado of ['criar', 'desativar', 'reativar', 'remover']) {
      expect(metodos[retirado]).toBeUndefined();
    }
  });

  it('obtém os dados próprios do utilizador da sessão', () => {
    service.obterMe().subscribe();
    const requisicao = httpTesting.expectOne('/api/v1/utilizadores/me');
    expect(requisicao.request.method).toBe('GET');
    requisicao.flush({
      nome: 'Ana Teixeira',
      email: 'ana.teixeira@dgterritorio.gov.pt',
      cargo: null,
      telefone: null,
      morada: null,
      origemAutenticacao: 'AD_DGT',
      perfilInterno: 'MASTER',
      entidade: null,
    });
  });

  it('atualiza os dados próprios do utilizador da sessão', () => {
    service.atualizarMe({ email: 'ana.teixeira@dgterritorio.gov.pt', telefone: '912345678', morada: 'Rua Um, 1' }).subscribe();
    const requisicao = httpTesting.expectOne('/api/v1/utilizadores/me');
    expect(requisicao.request.method).toBe('PATCH');
    expect(requisicao.request.body).toEqual({ email: 'ana.teixeira@dgterritorio.gov.pt', telefone: '912345678', morada: 'Rua Um, 1' });
    requisicao.flush({});
  });
});
