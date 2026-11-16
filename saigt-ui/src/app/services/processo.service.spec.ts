import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { beforeEach, describe, expect, it } from 'vitest';
import { errorEnvelopeInterceptor } from '../core/interceptors/error-envelope.interceptor';
import { ApiError } from '../core/models/error-response.model';
import { AtualizarDadosFormularioRequest, RegistoExternoRequest } from '../models/processo.model';
import { ProcessoService } from './processo.service';

describe('ProcessoService', () => {
  let service: ProcessoService;
  let httpTesting: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    service = TestBed.inject(ProcessoService);
    httpTesting = TestBed.inject(HttpTestingController);
  });

  it('pede a listagem só com os filtros preenchidos, mais page/size', () => {
    service.listar({ estado: 'ENVIADO_DGT', entidadeNif: undefined }, 0, 20).subscribe();
    const requisicao = httpTesting.expectOne((r) => r.url === '/api/v1/processos');
    expect(requisicao.request.params.get('estado')).toBe('ENVIADO_DGT');
    expect(requisicao.request.params.has('entidadeNif')).toBe(false);
    expect(requisicao.request.params.get('page')).toBe('0');
    expect(requisicao.request.params.get('size')).toBe('20');
    requisicao.flush({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 });
  });

  it('pede o histórico do processo com page e size', () => {
    service.historico('p1', 2, 20).subscribe();
    const requisicao = httpTesting.expectOne((r) => r.url === '/api/v1/processos/p1/historico');
    expect(requisicao.request.params.get('page')).toBe('2');
    expect(requisicao.request.params.get('size')).toBe('20');
    requisicao.flush({ content: [], page: 2, size: 20, totalElements: 0, totalPages: 0 });
  });

  it('pede as Dinâmicas associáveis do processo com page e size', () => {
    service.dinamicasAssociaveis('p1', 1, 20).subscribe((resposta) => expect(resposta.content[0].dinamicaId).toBe('d1'));
    const requisicao = httpTesting.expectOne((r) => r.url === '/api/v1/processos/p1/dinamicas-associaveis');
    expect(requisicao.request.method).toBe('GET');
    expect(requisicao.request.params.get('page')).toBe('1');
    expect(requisicao.request.params.get('size')).toBe('20');
    requisicao.flush({
      content: [
        {
          dinamicaId: 'd1',
          numeroDinamica: 'D-1',
          descritivo: 'Alteração',
          tipoDinamica: 'ALTERACAO',
          estado: 'EM_CURSO',
          instrumento: { id: 'i1', designacao: 'PDM', tipoInstrumento: 'PDM' },
        },
      ],
      page: 1,
      size: 20,
      totalElements: 21,
      totalPages: 2,
    });
  });

  it('pede as recusas numeradas do processo sem filtros', () => {
    service.estatisticas('p1').subscribe((resposta) => expect(resposta.numeroSubmissoes).toBe(2));
    const requisicao = httpTesting.expectOne('/api/v1/processos/p1/estatisticas');
    expect(requisicao.request.method).toBe('GET');
    expect(requisicao.request.params.keys()).toEqual([]);
    requisicao.flush({ numeroSubmissoes: 2, recusas: [] });
  });

  it('não envia nenhum filtro quando o filtro está vazio', () => {
    service.listar({}, 0, 20).subscribe();
    const requisicao = httpTesting.expectOne((r) => r.url === '/api/v1/processos');
    expect(requisicao.request.params.keys().sort()).toEqual(['page', 'size']);
    requisicao.flush({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 });
  });

  it('envia um parâmetro estado por cada valor quando o filtro é um array', () => {
    service.listar({ estado: ['REJEITADO_DGT', 'ANULADO_INCM'] }, 0, 20).subscribe();
    const requisicao = httpTesting.expectOne((r) => r.url === '/api/v1/processos');
    expect(requisicao.request.params.getAll('estado')).toEqual(['REJEITADO_DGT', 'ANULADO_INCM']);
    requisicao.flush({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 });
  });

  it('grava a validação dos itens num PUT com a versão e a decisão de cada item', () => {
    service.gravarValidacaoItens('p1', 3, [{ tipo: 'DOCUMENTO', itemId: 'd1', decisao: 'VALIDADO' }]).subscribe((resposta) => {
      expect(resposta.todosRevistos).toBe(false);
    });
    const requisicao = httpTesting.expectOne('/api/v1/processos/p1/validacao/itens');
    expect(requisicao.request.method).toBe('PUT');
    expect(requisicao.request.body).toEqual({ versao: 3, itens: [{ tipo: 'DOCUMENTO', itemId: 'd1', decisao: 'VALIDADO' }] });
    requisicao.flush({ itens: [{ tipo: 'DOCUMENTO', itemId: 'd1', estadoValidacao: 'VALIDADO' }], todosRevistos: false });
  });

  it('pede o painel numa só chamada, com o tamanho da lista de cada grupo', () => {
    service.painel(5).subscribe();
    const requisicao = httpTesting.expectOne((pedido) => pedido.url === '/api/v1/processos/painel');
    expect(requisicao.request.method).toBe('GET');
    expect(requisicao.request.params.get('size')).toBe('5');
    expect(requisicao.request.params.has('grupo')).toBe(false);
    requisicao.flush({ grupos: [{ grupo: 'PUBLICADOS', contagem: 22, processos: [] }] });
  });

  it('cria uma tentativa na Dinâmica indicada', () => {
    service.criar('d1', { instrumentoProcedimentoConfigId: 10, tipoAtoId: 5 }).subscribe();
    const requisicao = httpTesting.expectOne('/api/v1/dinamicas/d1/processos');
    expect(requisicao.request.method).toBe('POST');
    expect(requisicao.request.body).toEqual({ instrumentoProcedimentoConfigId: 10, tipoAtoId: 5 });
    requisicao.flush({});
  });

  it('regista um Procedimento publicado fora da SAIGT com POST ao caminho da Dinâmica e o corpo tipado', () => {
    const pedido: RegistoExternoRequest = {
      instrumentoProcedimentoConfigId: 12,
      entidadeId: 7,
      numeroDr: 'Aviso n.º 1/2026',
      dataPublicacaoDr: '2026-10-20',
      motivo: 'Publicado antes da adesão à SAIGT.',
    };
    service.registoExterno('d1', pedido).subscribe((processo) => expect(processo.numeroProcesso).toBe('00042'));
    const requisicao = httpTesting.expectOne('/api/v1/dinamicas/d1/processos/registo-externo');
    expect(requisicao.request.method).toBe('POST');
    expect(requisicao.request.body).toEqual(pedido);
    requisicao.flush({ id: 'p9', numeroProcesso: '00042', estado: 'PUBLICADO' });
  });

  it('grava o rascunho num só PUT, com a versao lida e os campos INCM, sem transicionar estado', () => {
    const request: AtualizarDadosFormularioRequest = {
      versao: 4,
      sumario: 'Resumo',
      dataAssinatura: '2026-09-01',
      normaLegal: null,
      tipoInformacao: 'EXTRATO',
      publicarSuplemento: null,
      dataSuplemento: null,
      contactoTramitacao: 'tramitacao@cm-sintra.pt',
      entidadeInstrutora: null,
      entidadeEmitente: 'Camara Municipal de Sintra',
      contactoPublicacao: 'Gabinete de Planeamento',
      elementosAfetados: ['PECAS_ESCRITAS'],
    };
    service.guardarRascunho('p1', request).subscribe();
    const requisicao = httpTesting.expectOne('/api/v1/processos/p1/dados-formulario');
    expect(requisicao.request.method).toBe('PUT');
    expect(requisicao.request.body).toEqual(request);
    httpTesting.expectNone('/api/v1/processos/p1/publicacao-incm');
    requisicao.flush({});
  });

  it('submete a tentativa com o corpo indicado', () => {
    service.submeter('p1', { versao: 3, sumario: 'Resumo' }).subscribe();
    const requisicao = httpTesting.expectOne('/api/v1/processos/p1/submeter');
    expect(requisicao.request.method).toBe('PATCH');
    expect(requisicao.request.body).toEqual({ versao: 3, sumario: 'Resumo' });
    requisicao.flush({});
  });

  it('submete as confirmações no corpo do pedido', () => {
    service.submeter('p1', { versao: 3, confirmacoes: ['CONTEUDO_PROCESSO'] }).subscribe();
    const requisicao = httpTesting.expectOne('/api/v1/processos/p1/submeter');
    expect(requisicao.request.body).toEqual({ versao: 3, confirmacoes: ['CONTEUDO_PROCESSO'] });
    requisicao.flush({});
  });

  it.each([
    ['resgatar', 'resgatar'],
    ['corrigir', 'corrigir'],
    ['capturar', 'capturar'],
    ['validar', 'validar'],
    ['enviarIncm', 'enviar-incm'],
  ] as const)('%s faz PATCH /processos/{id}/%s só com a versao', (metodo, caminho) => {
    service[metodo]('p1', 3).subscribe();
    const requisicao = httpTesting.expectOne(`/api/v1/processos/p1/${caminho}`);
    expect(requisicao.request.method).toBe('PATCH');
    expect(requisicao.request.body).toEqual({ versao: 3 });
    requisicao.flush({});
  });

  it('enviarPcm faz PATCH /processos/{id}/enviar-pcm com a versao e o utilizador da PCM e devolve o Processo', () => {
    let recebido: unknown;
    service.enviarPcm('p1', 3, 'u-pcm').subscribe((processo) => (recebido = processo));
    const requisicao = httpTesting.expectOne('/api/v1/processos/p1/enviar-pcm');
    expect(requisicao.request.method).toBe('PATCH');
    expect(requisicao.request.body).toEqual({ versao: 3, utilizadorId: 'u-pcm' });
    requisicao.flush({ id: 'p1', estado: 'VALIDADO_DGT', versao: 4, pcmAtribuidoAId: 'u-pcm' });
    expect(recebido).toEqual({ id: 'p1', estado: 'VALIDADO_DGT', versao: 4, pcmAtribuidoAId: 'u-pcm' });
  });

  it('rejeitar faz PATCH /processos/{id}/rejeitar com a versao e as notas gerais', () => {
    service.rejeitar('p1', 3, 'Rever o regulamento.').subscribe();
    const requisicao = httpTesting.expectOne('/api/v1/processos/p1/rejeitar');
    expect(requisicao.request.method).toBe('PATCH');
    expect(requisicao.request.body).toEqual({ versao: 3, notasGerais: 'Rever o regulamento.' });
    requisicao.flush({});
  });

  it('anular faz PATCH /processos/{id}/anular com a versao e o motivo', () => {
    service.anular('p1', 3, 'Pedido duplicado').subscribe();
    const requisicao = httpTesting.expectOne('/api/v1/processos/p1/anular');
    expect(requisicao.request.method).toBe('PATCH');
    expect(requisicao.request.body).toEqual({ versao: 3, motivo: 'Pedido duplicado' });
    requisicao.flush({});
  });

  it('reatribuirAutor faz PATCH /processos/{id}/reatribuir-autor com a versao e o novo autor, sem motivo', () => {
    service.reatribuirAutor('p1', 3, 'u2').subscribe();
    const requisicao = httpTesting.expectOne('/api/v1/processos/p1/reatribuir-autor');
    expect(requisicao.request.method).toBe('PATCH');
    expect(requisicao.request.body).toEqual({ versao: 3, novoUtilizadorId: 'u2' });
    expect('pedidoApoioId' in requisicao.request.body).toBe(false);
    requisicao.flush({});
  });

  it('reatribuirAutor acrescenta o pedidoApoioId ao corpo quando é passado', () => {
    service.reatribuirAutor('p1', 3, 'u2', 'n9').subscribe();
    const requisicao = httpTesting.expectOne('/api/v1/processos/p1/reatribuir-autor');
    expect(requisicao.request.method).toBe('PATCH');
    expect(requisicao.request.body).toEqual({ versao: 3, novoUtilizadorId: 'u2', pedidoApoioId: 'n9' });
    requisicao.flush({});
  });

  it('reatribuirAutor não envia pedidoApoioId vazio', () => {
    service.reatribuirAutor('p1', 3, 'u2', '').subscribe();
    const requisicao = httpTesting.expectOne('/api/v1/processos/p1/reatribuir-autor');
    expect(requisicao.request.body).toEqual({ versao: 3, novoUtilizadorId: 'u2' });
    expect('pedidoApoioId' in requisicao.request.body).toBe(false);
    requisicao.flush({});
  });

  describe('exportar', () => {
    const XLSX = 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet';

    it('pede a exportação com os filtros e o formato, sem page nem size', () => {
      service.exportar({ designacaoInstrumento: 'Lisboa', estado: 'EM_VALIDACAO_DGT' }, 'EXCEL').subscribe();
      const requisicao = httpTesting.expectOne((r) => r.url === '/api/v1/processos/exportacao');
      expect(requisicao.request.method).toBe('GET');
      expect(requisicao.request.responseType).toBe('blob');
      expect(requisicao.request.params.get('formato')).toBe('EXCEL');
      expect(requisicao.request.params.get('designacaoInstrumento')).toBe('Lisboa');
      expect(requisicao.request.params.get('estado')).toBe('EM_VALIDACAO_DGT');
      expect(requisicao.request.params.has('page')).toBe(false);
      expect(requisicao.request.params.has('size')).toBe(false);
      requisicao.flush(new Blob(['x'], { type: XLSX }));
    });

    it('leva exatamente os mesmos filtros que a listagem', () => {
      const filtro = { designacaoInstrumento: 'PDM', estado: ['REJEITADO_DGT', 'ANULADO_INCM'], grupo: 'PARA_CORRECAO' as const, motivoAlteracao: 'MOTIVO_ALTERACAO_LEI:L1' };
      service.listar(filtro, 3, 20).subscribe();
      service.exportar(filtro, 'PDF').subscribe();
      const listagem = httpTesting.expectOne((r) => r.url === '/api/v1/processos').request.params;
      const exportacao = httpTesting.expectOne((r) => r.url === '/api/v1/processos/exportacao').request.params;

      const semPaginacao = listagem.keys().filter((chave) => chave !== 'page' && chave !== 'size');
      const semFormato = exportacao.keys().filter((chave) => chave !== 'formato');
      expect(semFormato.sort()).toEqual(semPaginacao.sort());
      for (const chave of semPaginacao) {
        expect(exportacao.getAll(chave)).toEqual(listagem.getAll(chave));
      }
    });

    it('devolve o ficheiro com o nome do Content-Disposition', () => {
      let nome = '';
      let ficheiro: Blob | null = null;
      service.exportar({}, 'PDF').subscribe((resposta) => {
        nome = resposta.nomeFicheiro;
        ficheiro = resposta.ficheiro;
      });
      const corpo = new Blob(['%PDF'], { type: 'application/pdf' });
      httpTesting
        .expectOne((r) => r.url === '/api/v1/processos/exportacao')
        .flush(corpo, { headers: { 'Content-Disposition': 'attachment; filename="processos-2026.pdf"' } });

      expect(nome).toBe('processos-2026.pdf');
      expect(ficheiro).toBe(corpo);
    });

    it('prefere o filename* codificado quando vem com o filename simples', () => {
      let nome = '';
      service.exportar({}, 'EXCEL').subscribe((resposta) => (nome = resposta.nomeFicheiro));
      httpTesting
        .expectOne((r) => r.url === '/api/v1/processos/exportacao')
        .flush(new Blob(['x']), { headers: { 'Content-Disposition': `attachment; filename="processos.xlsx"; filename*=UTF-8''processos%20transi%C3%A7%C3%A3o.xlsx` } });

      expect(nome).toBe('processos transição.xlsx');
    });

    it.each([
      ['EXCEL', 'processos.xlsx'],
      ['PDF', 'processos.pdf'],
    ] as const)('sem Content-Disposition em %s usa %s', (formato, esperado) => {
      let nome = '';
      service.exportar({}, formato).subscribe((resposta) => (nome = resposta.nomeFicheiro));
      httpTesting.expectOne((r) => r.url === '/api/v1/processos/exportacao').flush(new Blob(['x']));

      expect(nome).toBe(esperado);
    });
  });
});

describe('ProcessoService — erro da exportação em blob', () => {
  it('lê o envelope de erro que chega em Blob e devolve o ApiError com a mensagem da API', async () => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(withInterceptors([errorEnvelopeInterceptor])), provideHttpClientTesting()],
    });
    const service = TestBed.inject(ProcessoService);
    const httpTesting = TestBed.inject(HttpTestingController);

    const erro = new Promise<ApiError>((resolve) => service.exportar({}, 'EXCEL').subscribe({ error: resolve }));
    const envelope = {
      codigo: 'EXPORTACAO_LIMITE_EXCEDIDO',
      mensagem: 'A exportação excede o limite de 5000 linhas. Aplique mais filtros e tente de novo.',
      campos: [],
      timestamp: '2026-11-13T10:00:00Z',
      traceId: 't1',
    };
    httpTesting
      .expectOne((r) => r.url === '/api/v1/processos/exportacao')
      .flush(new Blob([JSON.stringify(envelope)], { type: 'application/json' }), { status: 400, statusText: 'Bad Request' });

    const recebido = await erro;
    expect(recebido).toBeInstanceOf(ApiError);
    expect(recebido.codigo).toBe('EXPORTACAO_LIMITE_EXCEDIDO');
    expect(recebido.mensagem).toBe(envelope.mensagem);
  });
});
