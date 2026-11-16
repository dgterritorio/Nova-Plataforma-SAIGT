import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Observable } from 'rxjs';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { AtualizarDocumentoConfigPedido, CriarDocumentoConfigPedido, RegraConfigRequest, RegrasTiff } from '../models/configuracao.model';
import { ConfiguracaoService } from './configuracao.service';

const PAGINA_VAZIA = { content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 };

describe('ConfiguracaoService', () => {
  let service: ConfiguracaoService;
  let httpTesting: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    service = TestBed.inject(ConfiguracaoService);
    httpTesting = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpTesting.verify());

  it.each([
    ['listarTiposInstrumento', '/api/v1/configuracao/tipos-instrumento'],
    ['listarTiposDinamica', '/api/v1/configuracao/tipos-dinamica'],
    ['listarTiposProcedimento', '/api/v1/configuracao/tipos-procedimento'],
  ] as const)('%s lista por GET em %s com page, size e ativo=false', (metodo, url) => {
    service[metodo]({ ativo: false }, 2, 20).subscribe();
    const pedido = httpTesting.expectOne((r) => r.url === url);
    expect(pedido.request.method).toBe('GET');
    expect(pedido.request.params.get('page')).toBe('2');
    expect(pedido.request.params.get('size')).toBe('20');
    expect(pedido.request.params.get('ativo')).toBe('false');
    pedido.flush(PAGINA_VAZIA);
  });

  it('sem filtro de estado não envia ativo', () => {
    service.listarTiposInstrumento({ ativo: undefined }, 0, 100).subscribe();
    const pedido = httpTesting.expectOne((r) => r.url === '/api/v1/configuracao/tipos-instrumento');
    expect(pedido.request.params.has('ativo')).toBe(false);
    expect(pedido.request.params.get('size')).toBe('100');
    pedido.flush(PAGINA_VAZIA);
  });

  it('lista as Dinâmicas configuradas com tipoInstrumentoId e ativo=true', () => {
    service.listarDinamicasConfig({ tipoInstrumentoId: 7, ativo: true }, 0, 20).subscribe();
    const pedido = httpTesting.expectOne((r) => r.url === '/api/v1/configuracao/dinamicas-config');
    expect(pedido.request.method).toBe('GET');
    expect(pedido.request.params.get('tipoInstrumentoId')).toBe('7');
    expect(pedido.request.params.get('ativo')).toBe('true');
    expect(pedido.request.params.get('page')).toBe('0');
    pedido.flush(PAGINA_VAZIA);
  });

  it('cria e atualiza os tipos por POST e PUT com o corpo do contrato', () => {
    const instrumento = { codigo: 'PIOT', nome: 'Plano Intermunicipal', categoria: 'PLANO' as const, ativo: true };
    service.criarTipoInstrumento(instrumento).subscribe();
    service.atualizarTipoInstrumento(3, { ...instrumento, ativo: false }).subscribe();
    service.criarTipoDinamica({ codigo: 'ALTERACAO_X', nome: 'Alteração X', ativo: true }).subscribe();
    service.atualizarTipoDinamica(4, { codigo: 'ALTERACAO_X', nome: 'Alteração X', ativo: false }).subscribe();
    service.criarTipoProcedimento({ codigo: 'PARECER', nome: 'Parecer', ativo: true }).subscribe();
    service.atualizarTipoProcedimento(5, { codigo: 'PARECER', nome: 'Parecer', ativo: true }).subscribe();

    const esperados: [string, string, unknown][] = [
      ['POST', '/api/v1/configuracao/tipos-instrumento', instrumento],
      ['PUT', '/api/v1/configuracao/tipos-instrumento/3', { ...instrumento, ativo: false }],
      ['POST', '/api/v1/configuracao/tipos-dinamica', { codigo: 'ALTERACAO_X', nome: 'Alteração X', ativo: true }],
      ['PUT', '/api/v1/configuracao/tipos-dinamica/4', { codigo: 'ALTERACAO_X', nome: 'Alteração X', ativo: false }],
      ['POST', '/api/v1/configuracao/tipos-procedimento', { codigo: 'PARECER', nome: 'Parecer', ativo: true }],
      ['PUT', '/api/v1/configuracao/tipos-procedimento/5', { codigo: 'PARECER', nome: 'Parecer', ativo: true }],
    ];
    for (const [metodo, url, corpo] of esperados) {
      const pedido = httpTesting.expectOne({ method: metodo, url });
      expect(pedido.request.body).toEqual(corpo);
      pedido.flush({});
    }
  });

  it('cria e atualiza a configuração de uma Dinâmica por POST e PUT', () => {
    const corpo = { tipoInstrumentoId: 1, tipoDinamicaId: 2, associacaoInstrumentos: 'UM' as const, ativo: true };
    service.criarDinamicaConfig(corpo).subscribe();
    service.atualizarDinamicaConfig(9, { ...corpo, ativo: false }).subscribe();

    const criar = httpTesting.expectOne({ method: 'POST', url: '/api/v1/configuracao/dinamicas-config' });
    expect(criar.request.body).toEqual(corpo);
    criar.flush({});
    const atualizar = httpTesting.expectOne({ method: 'PUT', url: '/api/v1/configuracao/dinamicas-config/9' });
    expect(atualizar.request.body).toEqual({ ...corpo, ativo: false });
    atualizar.flush({});
  });

  it.each([
    ['listarProcedimentosConfig', '/api/v1/configuracao/procedimentos-config', { instrumentoDinamicaConfigId: 7, ativo: true }],
    ['listarPermissoes', '/api/v1/configuracao/permissoes', { instrumentoProcedimentoConfigId: 7, ativo: true }],
  ] as const)('%s lista por GET em %s com os filtros', (metodo, url, filtro) => {
    const listar = (service[metodo] as (f: object, p: number, s: number) => Observable<unknown>).bind(service);
    listar(filtro, 1, 100).subscribe();
    const pedido = httpTesting.expectOne((r) => r.url === url);
    expect(pedido.request.method).toBe('GET');
    for (const [chave, valor] of Object.entries(filtro)) {
      expect(pedido.request.params.get(chave)).toBe(String(valor));
    }
    expect(pedido.request.params.get('page')).toBe('1');
    expect(pedido.request.params.get('size')).toBe('100');
    pedido.flush(PAGINA_VAZIA);
  });

  it('lista os tipos de ato com ativo e as redes de defesa só com a página', () => {
    service.listarTiposAto({ ativo: false }, 0, 20).subscribe();
    service.listarRedesDefesa(0, 20).subscribe();

    const atos = httpTesting.expectOne((r) => r.url === '/api/v1/configuracao/tipos-ato');
    expect(atos.request.params.get('ativo')).toBe('false');
    atos.flush(PAGINA_VAZIA);
    const redes = httpTesting.expectOne((r) => r.url === '/api/v1/configuracao/redes-defesa');
    expect(redes.request.method).toBe('GET');
    expect(redes.request.params.keys().sort()).toEqual(['page', 'size']);
    redes.flush(PAGINA_VAZIA);
  });

  it('cria e atualiza Procedimentos, permissões, tipos de ato e redes por POST e PUT com o corpo do contrato', () => {
    const procedimento = {
      instrumentoDinamicaConfigId: 3,
      tipoProcedimentoId: 4,
      ordem: 1,
      obrigatorio: true,
      serieDr: 'SEGUNDA_H' as const,
      tamanhoMaximoProcesso: 5368709120,
      repetivel: false,
      atoPontual: false,
      ativo: true,
    };
    const permissao = { instrumentoProcedimentoConfigId: 5, tipoEntidade: 'CCDR_DR' as const, ativo: true };
    const ato = { codigo: 'DELIB', nome: 'Deliberação', incmActTypeIdSerie1: null, incmActTypeIdSerie2: 45, ordem: 1, ativo: true };
    const rede = { codigo: 'RVF', nome: 'Rede viária florestal', aplicaPra: true, aplicaPsa: false, publicavel: true };
    service.criarProcedimentoConfig(procedimento).subscribe();
    service.atualizarProcedimentoConfig(6, procedimento).subscribe();
    service.criarPermissao(permissao).subscribe();
    service.atualizarPermissao(7, { ...permissao, ativo: false }).subscribe();
    service.criarTipoAto(ato).subscribe();
    service.atualizarTipoAto(8, ato).subscribe();
    service.criarRedeDefesa(rede).subscribe();
    service.atualizarRedeDefesa(9, rede).subscribe();

    const esperados: [string, string, unknown][] = [
      ['POST', '/api/v1/configuracao/procedimentos-config', procedimento],
      ['PUT', '/api/v1/configuracao/procedimentos-config/6', procedimento],
      ['POST', '/api/v1/configuracao/permissoes', permissao],
      ['PUT', '/api/v1/configuracao/permissoes/7', { ...permissao, ativo: false }],
      ['POST', '/api/v1/configuracao/tipos-ato', ato],
      ['PUT', '/api/v1/configuracao/tipos-ato/8', ato],
      ['POST', '/api/v1/configuracao/redes-defesa', rede],
      ['PUT', '/api/v1/configuracao/redes-defesa/9', rede],
    ];
    for (const [metodo, url, corpo] of esperados) {
      const pedido = httpTesting.expectOne({ method: metodo, url });
      expect(pedido.request.body).toEqual(corpo);
      pedido.flush({});
    }
  });

  describe('itens documentais (/api/v1/documento-config)', () => {
    const PEDIDO: AtualizarDocumentoConfigPedido = {
      nome: 'Planta de ordenamento',
      separador: 'Planta de ordenamento',
      descricao: null,
      categoria: 'DEPOSITO',
      tipoElemento: 'PECA_GRAFICA',
      formato: 'NAO_EDITAVEL',
      extensoesAceites: ['zip'],
      obrigatorio: true,
      ordem: 3,
      elementoInformacaoBase: null,
      tamanhoMaximoFicheiro: 524288000,
      tamanhoMaximoTotal: null,
      numeroMaximoFicheiros: null,
      linkPublicoPermitido: false,
      ativo: true,
      regraNomenclatura: 'SGIFR',
    };
    const REGRAS: RegrasTiff = {
      sistemasReferencia: ['EPSG:3763'],
      compressoesAceites: ['NONE'],
      bandaAlfaTolerada: false,
      modeloCor: 'RGB',
      bitsPorBanda: 8,
      resolucaoDpi: 300,
      elementosLegenda: ['ID do plano'],
    };

    it('lista por GET com instrumentoProcedimentoConfigId, ativo, page e size', () => {
      service.listarDocumentoConfig({ instrumentoProcedimentoConfigId: 209, ativo: false }, 1, 20).subscribe();
      const pedido = httpTesting.expectOne((r) => r.url === '/api/v1/documento-config');
      expect(pedido.request.method).toBe('GET');
      expect(pedido.request.params.get('instrumentoProcedimentoConfigId')).toBe('209');
      expect(pedido.request.params.get('ativo')).toBe('false');
      expect(pedido.request.params.get('page')).toBe('1');
      expect(pedido.request.params.get('size')).toBe('20');
      pedido.flush(PAGINA_VAZIA);
    });

    it('sem filtro de estado não envia ativo', () => {
      service.listarDocumentoConfig({ instrumentoProcedimentoConfigId: 209 }, 0, 20).subscribe();
      const pedido = httpTesting.expectOne((r) => r.url === '/api/v1/documento-config');
      expect(pedido.request.params.has('ativo')).toBe(false);
      pedido.flush(PAGINA_VAZIA);
    });

    it('cria por POST com o código e o Procedimento, com e sem regrasTiff', () => {
      const semRegras: CriarDocumentoConfigPedido = { ...PEDIDO, instrumentoProcedimentoConfigId: 209, codigo: 'PLANTA_ORD' };
      const comRegras: CriarDocumentoConfigPedido = { ...semRegras, regrasTiff: REGRAS };
      for (const corpo of [semRegras, comRegras]) {
        service.criarDocumentoConfig(corpo).subscribe();
        const pedido = httpTesting.expectOne('/api/v1/documento-config');
        expect(pedido.request.method).toBe('POST');
        expect(pedido.request.body).toEqual(corpo);
        pedido.flush({});
      }
      expect('regrasTiff' in semRegras).toBe(false);
    });

    it('atualiza por PUT no id, com e sem regrasTiff', () => {
      for (const corpo of [PEDIDO, { ...PEDIDO, regrasTiff: REGRAS }]) {
        service.atualizarDocumentoConfig(77, corpo).subscribe();
        const pedido = httpTesting.expectOne('/api/v1/documento-config/77');
        expect(pedido.request.method).toBe('PUT');
        expect(pedido.request.body).toEqual(corpo);
        pedido.flush({});
      }
    });
  });
  it('lista as regras por GET com os filtros indicados, incluindo dinamicaDosProcedimentosId', () => {
    service.listarRegras({ instrumentoDinamicaConfigId: 101 }, 0, 100).subscribe();
    service.listarRegras({ dinamicaDosProcedimentosId: 101, ativo: true }, 1, 20).subscribe();
    const [daDinamica, dosProcedimentos] = httpTesting.match((r) => r.url === '/api/v1/configuracao/regras');
    expect(daDinamica.request.method).toBe('GET');
    expect(daDinamica.request.params.get('instrumentoDinamicaConfigId')).toBe('101');
    expect(daDinamica.request.params.has('dinamicaDosProcedimentosId')).toBe(false);
    expect(daDinamica.request.params.get('size')).toBe('100');
    expect(dosProcedimentos.request.params.get('dinamicaDosProcedimentosId')).toBe('101');
    expect(dosProcedimentos.request.params.get('ativo')).toBe('true');
    expect(dosProcedimentos.request.params.get('page')).toBe('1');
    daDinamica.flush(PAGINA_VAZIA);
    dosProcedimentos.flush(PAGINA_VAZIA);
  });

  it('cria uma regra por POST e atualiza-a por PUT com o corpo do contrato', () => {
    const pedido: RegraConfigRequest = {
      instrumentoDinamicaConfigId: null,
      instrumentoProcedimentoConfigId: 501,
      substituiSequenciamento: true,
      operador: 'OR',
      descricao: null,
      ativo: true,
      condicoes: [{ avaliador: 'INSTRUMENTO_EM_VIGOR', parametros: {}, grupo: 1, ativo: true }],
    };
    service.criarRegra(pedido).subscribe();
    service.atualizarRegra(7, { ...pedido, ativo: false }).subscribe();
    const criar = httpTesting.expectOne((r) => r.url === '/api/v1/configuracao/regras');
    expect(criar.request.method).toBe('POST');
    expect(criar.request.body).toEqual(pedido);
    const atualizar = httpTesting.expectOne((r) => r.url === '/api/v1/configuracao/regras/7');
    expect(atualizar.request.method).toBe('PUT');
    expect(atualizar.request.body.ativo).toBe(false);
    criar.flush({});
    atualizar.flush({});
  });
});
