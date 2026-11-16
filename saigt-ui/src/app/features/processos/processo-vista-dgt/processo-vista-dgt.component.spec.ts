import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router, convertToParamMap, provideRouter } from '@angular/router';
import { Subject, of, throwError } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ListaValoresService } from '../../../services/lista-valores.service';
import { ReferenciaSnitService } from '../../../services/referencia-snit.service';
import { ProcessoVistaDgtComponent } from './processo-vista-dgt.component';
import { AuthService } from '../../../core/auth/auth.service';
import { ApiError } from '../../../core/models/error-response.model';
import { DocumentoProcesso, EstruturaDocumental } from '../../../models/documento.model';
import { AcaoProcesso, ProcessoDetalhe } from '../../../models/processo.model';
import { DocumentoService } from '../../../services/documento.service';
import { EntidadeService } from '../../../services/entidade.service';
import { UtilizadorService } from '../../../services/utilizador.service';
import { ProcessoService } from '../../../services/processo.service';

const DADOS_FORMULARIO = JSON.stringify({
  informacaoBase: {
    deliberacao: {
      prazoAnos: 1,
      prazoMeses: 2,
      prazoDias: 3,
      avaliacaoAmbiental: true,
      dataDeliberacao: '2026-09-01',
      textoPublicar: 'Texto a publicar.',
    },
    textoAto: { prazoAnos: 4, prazoMeses: null, prazoDias: null, avaliacaoAmbiental: false },
  },
});

function documento(parcial: Partial<DocumentoProcesso>): DocumentoProcesso {
  return {
    id: 'd-pub',
    documentoConfigId: 1,
    nomeFicheiro: 'deliberacao.docx',
    categoria: 'PUBLICACAO',
    extensao: 'docx',
    origem: 'CARREGADO',
    bloqueadoEdicao: false,
    estadoValidacao: 'POR_VALIDAR',
    tamanhoBytes: 1024,
    criadoEm: '2026-10-20T09:30:00Z',
    criadoPorId: 'u1',
    criadoPorNome: 'Helena Marques',
    tags: [],
    justificacaoDesbloqueio: null,
    ...parcial,
  };
}

const ESTRUTURA: EstruturaDocumental = {
  separadores: [
    {
      separador: 'Deliberação',
      elementoInformacaoBase: 'DELIBERACAO',
      publicacao: [
        {
          documentoConfigId: 1,
          codigo: 'DELIBERACAO_PUB',
          nome: 'Deliberação',
          categoria: 'PUBLICACAO',
          tipoElemento: 'PECA_ESCRITA',
          formato: 'EDITAVEL',
          obrigatorio: true,
          extensoesAceites: ['docx'],
          ficheiros: [documento({})],
          observacoes: null,
          pendente: false,
          obtidoDoGeoconform: false,
        },
      ],
      deposito: [
        {
          documentoConfigId: 2,
          codigo: 'DELIBERACAO_DEP',
          nome: 'Deliberação',
          categoria: 'DEPOSITO',
          tipoElemento: 'PECA_ESCRITA',
          formato: 'NAO_EDITAVEL',
          obrigatorio: false,
          extensoesAceites: ['pdf'],
          ficheiros: [documento({ id: 'd-dep', documentoConfigId: 2, nomeFicheiro: 'deposito.pdf', categoria: 'DEPOSITO' })],
          observacoes: null,
          pendente: false,
          obtidoDoGeoconform: false,
        },
      ],
    },
    { separador: 'Texto do Ato', elementoInformacaoBase: 'TEXTO_ATO', publicacao: [], deposito: [] },
  ],
  outros: [],
  pendencias: [],
};

function detalhe(parcial: Partial<ProcessoDetalhe> = {}): ProcessoDetalhe {
  return {
    id: 'p1',
    versao: 3,
    estado: 'EM_VALIDACAO_DGT',
    designacaoInstrumento: 'PDM de Sintra',
    numeroInstrumento: '1',
    tipoInstrumento: 'PDM',
    territorio: { concelhos: [], nutsII: [] },
    idus: null,
    tipoDinamica: 'ALTERACAO',
    nomeDinamica: 'Alteração',
    tipoProcedimento: 'DECISAO_INICIO',
    nomeProcedimento: 'Decisão de início',
    serieDr: 'SEGUNDA_H',
    formulario: 'INFORMACAO_GERAL',
    tipoAtoNome: 'Aviso',
    tiposAtoDisponiveis: [],
    sumario: 'Sumário gravado',
    normaLegal: 'Artigo 89.º do RJIGT',
    tipoInformacao: 'EXTRATO',
    publicarSuplemento: null,
    dataAssinatura: '2026-09-02',
    dadosFormulario: DADOS_FORMULARIO,
    editavel: false,
    acoesDisponiveis: ['validar', 'rejeitar'],
    informacaoBase: [
      { id: 'ib-delib', elemento: 'DELIBERACAO', estadoValidacao: 'POR_VALIDAR' },
      { id: 'ib-texto', elemento: 'TEXTO_ATO', estadoValidacao: 'POR_VALIDAR' },
    ],
    todosItensRevistos: false,
    rejeicoesEmValidacao: [],
    dinamicasATerminar: [],
    aceitaDinamicaAssociada: false,
    destino: 'PUBLICACAO_DEPOSITO',
    destinoEditavel: false,
    dinamicaAssociadaId: null,
    dinamicaAssociada: null,
    ...parcial,
  } as ProcessoDetalhe;
}

describe('ProcessoVistaDgtComponent', () => {
  let fixture: ComponentFixture<ProcessoVistaDgtComponent>;
  let estruturaPedida: Subject<EstruturaDocumental>;
  let obter: ReturnType<typeof vi.fn>;
  let gravarValidacaoItens: ReturnType<typeof vi.fn>;
  let estrutura: ReturnType<typeof vi.fn>;
  let rejeitar: ReturnType<typeof vi.fn>;
  let estatisticas: ReturnType<typeof vi.fn>;
  let verificacoes: ReturnType<typeof vi.fn>;
  let perfilInterno: ReturnType<typeof vi.fn>;
  let validar: ReturnType<typeof vi.fn>;
  let enviarIncm: ReturnType<typeof vi.fn>;
  let enviarPcm: ReturnType<typeof vi.fn>;
  let listarEntidades: ReturnType<typeof vi.fn>;
  let listarUtilizadores: ReturnType<typeof vi.fn>;

  /** A estrutura chega depois do primeiro ciclo, como no HTTP. */
  function abrir(conteudo: EstruturaDocumental = ESTRUTURA): void {
    fixture = TestBed.createComponent(ProcessoVistaDgtComponent);
    fixture.detectChanges();
    estruturaPedida.next(conteudo);
    fixture.detectChanges();
  }

  /** Só o conteúdo da vista: o `app-shell` à volta tem os seus próprios campos e textos. */
  function texto(): string {
    return (fixture.nativeElement.querySelector('.conteudo')?.textContent ?? '') as string;
  }

  function botoes(rotulo: string): HTMLButtonElement[] {
    const todos = Array.from(fixture.nativeElement.querySelectorAll('.conteudo button')) as HTMLButtonElement[];
    return todos.filter((b) => b.textContent?.trim() === rotulo);
  }

  beforeEach(() => {
    estruturaPedida = new Subject<EstruturaDocumental>();
    estrutura = vi.fn().mockReturnValue(estruturaPedida);
    obter = vi.fn().mockReturnValue(of(detalhe()));
    rejeitar = vi.fn().mockReturnValue(of({ id: 'p1', estado: 'REJEITADO_DGT' }));
    estatisticas = vi.fn().mockReturnValue(of({ numeroSubmissoes: 1, recusas: [] }));
    verificacoes = vi.fn().mockReturnValue(
      of([{ verificacao: 'CORRESPONDENCIA_ATO_TEXTO', documentoId: null, nomeFicheiro: null, resultado: 'MANUAL', detalhe: 'Confirme o ato.' }]),
    );
    perfilInterno = vi.fn().mockReturnValue('TECNICO');
    validar = vi.fn().mockReturnValue(of({ id: 'p1', estado: 'VALIDADO_DGT', versao: 4 }));
    enviarIncm = vi.fn().mockReturnValue(of({ id: 'p1', estado: 'EM_VALIDACAO_INCM', versao: 5 }));
    enviarPcm = vi.fn().mockReturnValue(of({ id: 'p1', estado: 'VALIDADO_DGT', versao: 4, pcmAtribuidoAId: 'u-pcm' }));
    listarEntidades = vi.fn().mockReturnValue(of({ content: [{ id: 90 }], page: 0, size: 100, totalElements: 1, totalPages: 1 }));
    listarUtilizadores = vi.fn().mockReturnValue(
      of({
        content: [{ id: 'u-pcm', nome: 'Rita Paiva', email: 'rita.paiva@pcm.exemplo.pt', entidades: [] }],
        page: 0,
        size: 100,
        totalElements: 1,
        totalPages: 1,
      }),
    );
    gravarValidacaoItens = vi.fn().mockReturnValue(
      of({ itens: [{ tipo: 'DOCUMENTO', itemId: 'd-pub', estadoValidacao: 'VALIDADO' }], todosRevistos: false }),
    );
    TestBed.configureTestingModule({
      imports: [ProcessoVistaDgtComponent],
      providers: [
        provideRouter([]),
        { provide: ProcessoService, useValue: { obter, gravarValidacaoItens, rejeitar, estatisticas, validar, enviarIncm, enviarPcm } },
        { provide: EntidadeService, useValue: { listar: listarEntidades } },
        { provide: UtilizadorService, useValue: { listar: listarUtilizadores } },
        { provide: DocumentoService, useValue: { estrutura, verificacoes } },
        { provide: ListaValoresService, useValue: { opcoes: vi.fn().mockReturnValue(of([])), listar: vi.fn().mockReturnValue(of([])) } },
        { provide: ReferenciaSnitService, useValue: { nutsII: vi.fn().mockReturnValue(of([])), concelhos: vi.fn().mockReturnValue(of([])) } },
        {
          provide: AuthService,
          useValue: {
            nome: () => 'Ana Teixeira',
            origem: () => 'AD_DGT',
            entidade: () => null,
            perfilInterno,
            utilizadorId: () => 'u-ana',
            ator: () => 'DGT',
          },
        },
        { provide: ActivatedRoute, useValue: { snapshot: { paramMap: convertToParamMap({ processoId: 'p1' }) } } },
      ],
    });
  });

  it('lê o processo da rota e mostra o cabeçalho com o estado', () => {
    abrir();

    expect(obter).toHaveBeenCalledWith('p1');
    expect(fixture.nativeElement.querySelector('h1').textContent).toContain('PDM de Sintra');
    expect(fixture.nativeElement.querySelector('app-estado-processo-badge').textContent).toContain('Em Validação DGT');
  });

  it('mostra a linha do destino no cabeçalho', () => {
    abrir();
    expect(fixture.nativeElement.querySelector('.destino-processo').textContent).toBe('Destino: Publicação e depósito');

    obter.mockReturnValue(of(detalhe({ destino: 'SO_DEPOSITO' })));
    abrir();
    expect(fixture.nativeElement.querySelector('.destino-processo').textContent).toBe('Destino: Só depósito');
  });

  it('num processo do Governo mostra em leitura os campos, com a Publicação só na Publicação', () => {
    obter.mockReturnValue(
      of(
        detalhe({
          formulario: 'SUSPENSAO_MP_GOVERNO',
          serieDr: 'PRIMEIRA',
          nomeProcedimento: 'Publicação das medidas preventivas (Governo)',
          entidadeEmitente: 'Secretaria de Estado do Ordenamento do Território',
          normaLegal: 'Artigo 126.º do RJIGT',
          tipoInformacao: 'EXTRATO',
          publicarSuplemento: true,
          exigeElementosAfetados: false,
          dataAssinatura: '2026-11-02',
          instrumentosAbrangidos: [{ id: 'i1', designacao: 'PDM de Sintra', tipoInstrumento: 'PDM' }],
        }),
      ),
    );
    abrir();

    const cartao: HTMLElement = fixture.nativeElement.querySelector('app-processo-formulario-inicial');
    expect(cartao.querySelector('.publicacao')!.textContent!.trim()).toBe('Suplemento');
    expect(cartao.querySelector('.data-assinatura')!.textContent!.trim()).toBe('02/11/2026');
    expect(cartao.textContent).toContain('Secretaria de Estado do Ordenamento do Território');
    expect(cartao.textContent).toContain('Artigo 126.º do RJIGT');
    expect(cartao.textContent).toContain('Extrato');
    expect(cartao.textContent).toContain('1.ª Série');
    expect(cartao.querySelector('.instrumentos-associados')!.textContent).toContain('PDM de Sintra');
    expect(cartao.querySelector('select, input')).toBeNull();
  });

  it('numa Prorrogação do Governo a leitura não tem a Publicação', () => {
    obter.mockReturnValue(
      of(
        detalhe({
          formulario: 'PRORROGACAO_GOVERNO',
          serieDr: 'PRIMEIRA',
          nomeProcedimento: 'Prorrogação da suspensão (Governo)',
          normaLegal: 'Artigo 126.º do RJIGT',
          tipoInformacao: 'TEXTO_INTEGRAL',
          publicarSuplemento: false,
          exigeElementosAfetados: false,
          dataAssinatura: '2026-11-02',
        }),
      ),
    );
    abrir();

    const cartao: HTMLElement = fixture.nativeElement.querySelector('app-processo-formulario-inicial');
    expect(cartao.querySelector('.publicacao')).toBeNull();
    expect(cartao.textContent).not.toContain('Publicar suplemento');
    expect(cartao.querySelector('.data-assinatura')!.textContent!.trim()).toBe('02/11/2026');
  });

  it('numa Suspensão da CCDR mostra em leitura a entidade emitente e a data de assinatura, sem a publicação', () => {
    obter.mockReturnValue(
      of(
        detalhe({
          formulario: 'SUSPENSAO_CCDR',
          serieDr: 'SEGUNDA_C',
          nomeProcedimento: 'Suspensão (CCDR)',
          entidadeEmitente: 'Comissão de Coordenação e Desenvolvimento Regional de Lisboa e Vale do Tejo, IP',
          normaLegal: 'Artigo 126.º do RJIGT',
          tipoInformacao: 'TEXTO_INTEGRAL',
          exigeElementosAfetados: false,
          dataAssinatura: '2026-11-02',
        }),
      ),
    );
    abrir();

    const cartao: HTMLElement = fixture.nativeElement.querySelector('app-processo-formulario-inicial');
    expect(cartao.querySelector('.data-assinatura')!.textContent!.trim()).toBe('02/11/2026');
    expect(cartao.textContent).toContain('Comissão de Coordenação e Desenvolvimento Regional de Lisboa e Vale do Tejo, IP');
    expect(cartao.textContent).toContain('Artigo 126.º do RJIGT');
    expect(cartao.querySelector('.publicacao')).toBeNull();
    expect(cartao.querySelector('select, input')).toBeNull();
  });

  it('numa Ratificação mostra em leitura a modalidade, a norma legal, o tipo de informação e a publicação', () => {
    obter.mockReturnValue(
      of(
        detalhe({
          formulario: 'RATIFICACAO',
          serieDr: 'PRIMEIRA',
          nomeProcedimento: 'Ratificação',
          modalidadeRatificacao: 'ALTERACAO',
          normaLegal: 'Artigo 91.º do RJIGT',
          tipoInformacao: 'TEXTO_INTEGRAL',
          publicarSuplemento: false,
          exigeElementosAfetados: false,
          dataAssinatura: '2026-10-01',
        }),
      ),
    );
    abrir();

    const cartao: HTMLElement = fixture.nativeElement.querySelector('app-processo-formulario-inicial');
    expect(cartao.querySelector('.tipo-procedimento')!.textContent!.trim()).toBe('Alteração – Ratificação');
    expect(cartao.querySelector('.publicacao')!.textContent!.trim()).toBe('Diário');
    expect(cartao.querySelector('.data-assinatura')!.textContent!.trim()).toBe('01/10/2026');
    expect(cartao.textContent).toContain('Artigo 91.º do RJIGT');
    expect(cartao.textContent).toContain('Texto integral');
    expect(cartao.textContent).not.toContain('Publicar suplemento');
    expect(cartao.textContent).not.toContain('Elementos fundamentais afetados');
  });

  it('num Procedimento sem Informação Base do Texto do Ato a Informação Geral mostra a data de assinatura', () => {
    obter.mockReturnValue(
      of(
        detalhe({
          nomeProcedimento: 'Constituição da Comissão Consultiva',
          serieDr: 'SEGUNDA_C',
          dataAssinaturaNoFormulario: true,
          dataAssinatura: '2026-11-10',
        }),
      ),
    );
    abrir();

    const cartao: HTMLElement = fixture.nativeElement.querySelector('app-processo-formulario-inicial');
    expect(cartao.querySelectorAll('.data-assinatura')).toHaveLength(1);
    expect(cartao.querySelector('.data-assinatura')!.textContent!.trim()).toBe('10/11/2026');
  });

  it('com a Informação Base no Procedimento a Informação Geral não repete a data de assinatura', () => {
    obter.mockReturnValue(of(detalhe({ dataAssinaturaNoFormulario: false, dataAssinatura: '2026-11-10' })));
    abrir();

    const cartao: HTMLElement = fixture.nativeElement.querySelector('app-processo-formulario-inicial');
    expect(cartao.querySelector('.data-assinatura')).toBeNull();
  });

  it('mostra a mesma secção de recusas que o detalhe do autor', () => {
    estatisticas.mockReturnValue(
      of({
        numeroSubmissoes: 2,
        recusas: [
          { sequencia: 1, origem: 'INCM', submissao: 1, data: '2026-06-25T15:51:05Z', notasGerais: null, motivoIncm: ['ActDate inválida.'], itens: [] },
        ],
      }),
    );
    abrir();

    expect(estatisticas).toHaveBeenCalledWith('p1');
    expect(texto()).toContain('Recusa 1');
    expect(texto()).toContain('INCM');
    expect(texto()).toContain('ActDate inválida.');
  });

  it('mostra o formulário, a Informação Base, os grupos e os ficheiros, sem controlos de edição', () => {
    abrir();

    expect(texto()).toContain('Informação Geral');
    expect(texto()).toContain('Artigo 89.º do RJIGT');
    expect(texto()).toContain('Sumário gravado');
    expect(texto()).toContain('1 ano, 2 meses, 3 dias');
    expect(texto()).toContain('01/09/2026');
    expect(texto()).toContain('Texto a publicar.');
    expect(texto()).toContain('02/09/2026');
    expect(texto()).toContain('Publicação INCM e Depósito DGT');
    expect(texto()).toContain('Deliberação');
    expect(texto()).toContain('Texto do Ato');
    expect(texto()).toContain('Outros');
    expect(texto()).toContain('deliberacao.docx');
    expect(texto()).toContain('deposito.pdf');
    expect(
      fixture.nativeElement.querySelectorAll('.conteudo input:not([disabled]), .conteudo textarea, .conteudo select, .conteudo form'),
    ).toHaveLength(0);
    for (const rotulo of ['Guardar', 'Carregar', 'Escolher ficheiro', 'Substituir', 'Remover']) {
      expect(texto()).not.toContain(rotulo);
    }
  });

  it('reaproveita o cartão Informação Geral e o componente Documentos em modo só de leitura', () => {
    abrir();

    expect(fixture.nativeElement.querySelectorAll('app-processo-formulario-inicial')).toHaveLength(1);
    expect(fixture.nativeElement.querySelectorAll('app-processo-documentos')).toHaveLength(1);
    expect(fixture.nativeElement.querySelector('app-processo-formulario-inicial dl.leitura')).not.toBeNull();
  });

  it('num processo SGIFR mostra a classe e os nomes das redes gravadas, só de leitura', () => {
    obter.mockReturnValue(
      of(
        detalhe({
          formulario: 'SGIFR',
          categoriaInstrumento: 'PROGRAMA',
          exigeElementosAfetados: false,
          redesDefesa: [
            { codigo: 'APPS', nome: 'Áreas prioritárias de prevenção e segurança' },
            { codigo: 'RVF', nome: 'Rede viária florestal' },
          ],
          redesDefesaDisponiveis: [
            { codigo: 'APPS', nome: 'Áreas prioritárias de prevenção e segurança' },
            { codigo: 'RVF', nome: 'Rede viária florestal' },
          ],
        }),
      ),
    );
    abrir();

    const leitura = fixture.nativeElement.querySelector('app-processo-formulario-inicial dl.leitura') as HTMLElement;
    expect(leitura.textContent).toContain('Classe de servidão');
    expect(leitura.textContent).toContain('Sistema de Gestão Integrada de Fogos Rurais');
    expect(leitura.textContent).toContain('Áreas prioritárias de prevenção e segurança, Rede viária florestal');
    expect(fixture.nativeElement.querySelector('app-processo-formulario-inicial input')).toBeNull();
  });

  it('tem «Validar» e «Rejeitar» por item, sem «Guardar validação» nem «Bloquear»', () => {
    abrir();

    expect(botoes('Validar').length).toBeGreaterThan(0);
    expect(botoes('Rejeitar')).toHaveLength(botoes('Validar').length);
    for (const rotulo of ['Guardar validação', 'Bloquear']) {
      expect(texto()).not.toContain(rotulo);
    }
  });

  it.each([
    ['TECNICO', ['validar', 'rejeitar'], 4],
    ['MASTER', ['validar', 'rejeitar', 'anular', 'reatribuir-autor'], 4],
    ['autor da entidade, noutro estado', ['resgatar'], 0],
    ['VISITANTE', [], 0],
  ] as const)('%s: botões «Validar» conforme as ações disponíveis', (_quem, acoes, esperados) => {
    obter.mockReturnValue(of(detalhe({ acoesDisponiveis: [...acoes] as AcaoProcesso[] })));

    abrir();

    expect(botoes('Validar')).toHaveLength(esperados);
  });

  it('«Validar» num item grava-o com a versão do detalhe, mostra «Validado» e relê o detalhe', () => {
    abrir();

    botoes('Validar')[0].click();
    fixture.detectChanges();

    expect(gravarValidacaoItens).toHaveBeenCalledExactlyOnceWith('p1', 3, [
      { tipo: 'DOCUMENTO', itemId: 'd-pub', decisao: 'VALIDADO' },
    ]);
    expect(fixture.nativeElement.querySelector('.validado').textContent.trim()).toBe('Validado');
    expect(obter).toHaveBeenCalledTimes(2);
    expect(fixture.nativeElement.querySelector('.estado-carregamento')).toBeNull();
  });

  it('depois de reler o detalhe sem acoesDisponiveis de validar, os botões desaparecem', () => {
    abrir();
    obter.mockReturnValue(of(detalhe({ estado: 'VALIDADO_DGT', acoesDisponiveis: ['enviar-incm'] })));

    botoes('Validar')[0].click();
    fixture.detectChanges();

    expect(botoes('Validar')).toHaveLength(0);
  });

  describe('checklist de validação', () => {
    it.each([
      ['EM_VALIDACAO_DGT', 'TECNICO', true],
      ['EM_VALIDACAO_DGT', 'MASTER', true],
      ['EM_VALIDACAO_DGT', 'VISITANTE', false],
      ['ENVIADO_DGT', 'TECNICO', false],
      ['ENVIADO_DGT', 'MASTER', false],
    ] as const)('%s com %s: aparece = %s', (estado, perfil, aparece) => {
      obter.mockReturnValue(of(detalhe({ estado })));
      perfilInterno.mockReturnValue(perfil);

      abrir();

      expect(fixture.nativeElement.querySelector('app-checklist-validacao') !== null).toBe(aparece);
      expect(texto().includes('Checklist de validação')).toBe(aparece);
      expect(verificacoes).toHaveBeenCalledTimes(aparece ? 1 : 0);
    });

    it('aparece entre o formulário inicial e a validação por item, com as linhas da API', () => {
      abrir();

      const checklist = fixture.nativeElement.querySelector('app-checklist-validacao') as HTMLElement;
      const formulario = fixture.nativeElement.querySelector('app-processo-formulario-inicial') as HTMLElement;
      const validacao = fixture.nativeElement.querySelector('app-processo-validacao') as HTMLElement;
      expect(verificacoes).toHaveBeenCalledWith('p1');
      expect(formulario.compareDocumentPosition(checklist) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
      expect(checklist.compareDocumentPosition(validacao) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
      expect(checklist.textContent).toContain('Correspondência entre o ato e o texto do diploma');
    });

    it('com a checklist a falhar, mostra o erro nela e a validação por item continua utilizável', () => {
      verificacoes.mockReturnValue(throwError(() => new ApiError('ERRO_INTERNO', 'Checklist indisponível.', 't1', null)));

      abrir();

      const checklist = fixture.nativeElement.querySelector('app-checklist-validacao') as HTMLElement;
      expect(checklist.querySelector('[role="alert"]')?.textContent).toContain('Checklist indisponível.');
      expect(botoes('Tentar novamente').length).toBeGreaterThan(0);
      expect(fixture.nativeElement.querySelector('app-error-banner .error-banner')?.closest('app-checklist-validacao')).toBe(checklist);
      expect(botoes('Validar').length).toBeGreaterThan(0);

      botoes('Validar')[0].click();
      fixture.detectChanges();
      expect(gravarValidacaoItens).toHaveBeenCalled();
    });
  });

  it('o link «Ver detalhe» leva ao detalhe do processo, onde está Alterações', () => {
    abrir();

    const ligacao = fixture.nativeElement.querySelector('a.ver-detalhe') as HTMLAnchorElement;
    expect(ligacao.getAttribute('href')).toBe('/processos/p1');
  });

  it('sem documentos mostra «Sem documentos carregados» e só os blocos da Informação Base', () => {
    abrir({ separadores: [{ separador: 'Deliberação', elementoInformacaoBase: 'DELIBERACAO', publicacao: [], deposito: [] }], outros: [], pendencias: [] });

    expect(texto()).toContain('Sem documentos carregados');
    expect(fixture.nativeElement.querySelectorAll('.informacao-base')).toHaveLength(2);
    expect(fixture.nativeElement.querySelectorAll('.ficheiro')).toHaveLength(0);
  });

  it('se os documentos não carregam, mostra o erro e Tentar novamente, que os lê de novo', () => {
    // A checklist também lê a estrutura (justificações de desbloqueio): sem ela, as contagens são só as dos documentos.
    perfilInterno.mockReturnValue('VISITANTE');
    estrutura
      .mockReturnValueOnce(throwError(() => new ApiError('ERRO_INTERNO', 'Serviço indisponível.', 't1', null)))
      .mockReturnValueOnce(of(ESTRUTURA));
    fixture = TestBed.createComponent(ProcessoVistaDgtComponent);
    fixture.detectChanges();
    expect(texto()).toContain('Serviço indisponível.');

    botoes('Tentar novamente')[0].click();
    fixture.detectChanges();

    expect(estrutura).toHaveBeenCalledTimes(2);
    expect(texto()).toContain('deliberacao.docx');
    expect(texto()).not.toContain('Serviço indisponível.');
  });

  it('se o processo não carrega, mostra a mensagem e nada do conteúdo', () => {
    obter.mockReturnValue(throwError(() => new ApiError('NAO_ENCONTRADO', 'Não existe.', 't1', null)));

    fixture = TestBed.createComponent(ProcessoVistaDgtComponent);
    fixture.detectChanges();

    expect(texto()).toContain('Este recurso não existe ou não está disponível para si.');
    expect(fixture.nativeElement.querySelector('h1')).toBeNull();
    expect(fixture.nativeElement.querySelector('app-processo-validacao')).toBeNull();
  });
  describe('resumo da rejeição', () => {
    const REVISTO_COM_REJEICAO = {
      informacaoBase: [
        { id: 'ib-delib', elemento: 'DELIBERACAO', estadoValidacao: 'VALIDADO' },
        { id: 'ib-texto', elemento: 'TEXTO_ATO', estadoValidacao: 'VALIDADO' },
      ],
      rejeicoesEmValidacao: [{ tipo: 'DOCUMENTO', itemId: 'd-dep', categoriaId: null, motivoId: null, notas: 'Ilegível.' }],
    } as Partial<ProcessoDetalhe>;

    function abrirResumo(): void {
      obter.mockReturnValue(of(detalhe(REVISTO_COM_REJEICAO)));
      const revista: EstruturaDocumental = JSON.parse(JSON.stringify(ESTRUTURA));
      revista.separadores[0].publicacao[0].ficheiros[0].estadoValidacao = 'VALIDADO';
      abrir(revista);
      estrutura.mockReturnValue(of(revista));
      botoes('Seguinte')[0].click();
      fixture.detectChanges();
    }

    it('«Seguinte» troca a vista pelo resumo, e «Anterior» volta sem chamar a API', () => {
      abrirResumo();

      expect(fixture.nativeElement.querySelector('app-resumo-rejeicao')).not.toBeNull();
      expect(fixture.nativeElement.querySelector('app-processo-validacao')).toBeNull();
      expect(texto()).toContain('deposito.pdf');

      botoes('Anterior')[0].click();
      fixture.detectChanges();

      expect(fixture.nativeElement.querySelector('app-resumo-rejeicao')).toBeNull();
      expect(fixture.nativeElement.querySelector('app-processo-validacao')).not.toBeNull();
      expect(rejeitar).not.toHaveBeenCalled();
    });

    it('depois de «Confirmar Rejeição» vai para o detalhe do processo', () => {
      const navegar = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
      abrirResumo();

      botoes('Recusar')[0].click();
      fixture.detectChanges();
      botoes('Confirmar Rejeição')[0].click();
      fixture.detectChanges();

      expect(rejeitar).toHaveBeenCalledWith('p1', expect.any(Number), null);
      expect(navegar).toHaveBeenCalledWith(['/processos/p1']);
    });
  });

  describe('Resumo Validados', () => {
    const TUDO_VALIDADO = {
      todosItensRevistos: true,
      informacaoBase: [
        { id: 'ib-delib', elemento: 'DELIBERACAO', estadoValidacao: 'VALIDADO' },
        { id: 'ib-texto', elemento: 'TEXTO_ATO', estadoValidacao: 'VALIDADO' },
      ],
      opcoesAlteracaoPeot: [],
    } as Partial<ProcessoDetalhe>;

    function estruturaValidada(): EstruturaDocumental {
      const revista: EstruturaDocumental = JSON.parse(JSON.stringify(ESTRUTURA));
      for (const separador of revista.separadores) {
        for (const item of [...separador.publicacao, ...separador.deposito]) {
          item.ficheiros.forEach((ficheiro) => (ficheiro.estadoValidacao = 'VALIDADO'));
        }
      }
      return revista;
    }

    it('com todos os itens validados, «Seguinte» abre o Resumo Validados e «Voltar» volta à revisão', () => {
      obter.mockReturnValue(of(detalhe(TUDO_VALIDADO)));
      abrir(estruturaValidada());

      botoes('Seguinte')[0].click();
      fixture.detectChanges();

      expect(fixture.nativeElement.querySelector('app-resumo-validados')).not.toBeNull();
      expect(fixture.nativeElement.querySelector('app-resumo-rejeicao')).toBeNull();
      expect(fixture.nativeElement.querySelector('app-processo-validacao')).toBeNull();

      botoes('Voltar')[0].click();
      fixture.detectChanges();
      expect(fixture.nativeElement.querySelector('app-resumo-validados')).toBeNull();
      expect(fixture.nativeElement.querySelector('app-processo-validacao')).not.toBeNull();
      expect(validar).not.toHaveBeenCalled();
    });

    it('«Gravar» valida com os campos do resumo, relê o detalhe e mostra «Publicar»', () => {
      obter.mockReturnValue(of(detalhe(TUDO_VALIDADO)));
      abrir(estruturaValidada());
      botoes('Seguinte')[0].click();
      fixture.detectChanges();
      obter.mockReturnValue(
        of(detalhe({ ...TUDO_VALIDADO, estado: 'VALIDADO_DGT', versao: 4, acoesDisponiveis: ['enviar-incm'], notasDgt: 'Conferido.' })),
      );

      const caixa = fixture.nativeElement.querySelector('#resumo-notasDgt') as HTMLTextAreaElement;
      caixa.value = 'Conferido.';
      caixa.dispatchEvent(new Event('input'));
      botoes('Gravar')[0].click();
      fixture.detectChanges();

      expect(validar).toHaveBeenCalledWith('p1', 3, { notasIncm: null, notasDgt: 'Conferido.', regUsoSolo: null, alteracaoPeotId: null });
      expect(obter).toHaveBeenCalledTimes(2);
      expect(fixture.nativeElement.querySelector('app-resumo-validados')).not.toBeNull();
      expect(botoes('Publicar')).toHaveLength(1);
      expect(botoes('Gravar')).toHaveLength(0);
      expect(texto()).toContain('Conferido.');
    });

    it('um processo já VALIDADO_DGT abre logo no Resumo Validados, só de leitura', () => {
      obter.mockReturnValue(
        of(detalhe({ estado: 'VALIDADO_DGT', acoesDisponiveis: ['enviar-incm', 'enviar-pcm'], regUsoSolo: true, opcoesAlteracaoPeot: [] })),
      );
      fixture = TestBed.createComponent(ProcessoVistaDgtComponent);
      fixture.detectChanges();

      expect(fixture.nativeElement.querySelector('app-resumo-validados')).not.toBeNull();
      expect(fixture.nativeElement.querySelector('app-processo-validacao')).toBeNull();
      expect(botoes('Enviar PCM')).toHaveLength(1);
      expect(botoes('Publicar')).toHaveLength(1);
      expect(botoes('Gravar')).toHaveLength(0);
    });

    it('devolvido pela PCM com o documento dela por validar abre na revisão; validado, «Seguinte» leva ao resumo com os dois envios', () => {
      const devolvido = { ...TUDO_VALIDADO, estado: 'VALIDADO_DGT', acoesDisponiveis: [], todosItensRevistos: false };
      obter.mockReturnValue(of(detalhe({ ...devolvido, revalidacaoPcm: true, pcmDevolvidoEm: '2026-11-14T10:00:00Z' })));
      const estruturaDaPcm = estruturaValidada();
      estruturaDaPcm.separadores[0].publicacao[0].ficheiros[0].estadoValidacao = 'POR_VALIDAR';
      gravarValidacaoItens.mockReturnValue(
        of({
          itens: [
            { tipo: 'DOCUMENTO', itemId: 'd-pub', estadoValidacao: 'VALIDADO', rejeicao: null },
            { tipo: 'DOCUMENTO', itemId: 'd-dep', estadoValidacao: 'VALIDADO', rejeicao: null },
            { tipo: 'INFORMACAO_BASE', itemId: 'ib-delib', estadoValidacao: 'VALIDADO', rejeicao: null },
            { tipo: 'INFORMACAO_BASE', itemId: 'ib-texto', estadoValidacao: 'VALIDADO', rejeicao: null },
          ],
          todosRevistos: true,
        }),
      );
      abrir(estruturaDaPcm);

      expect(fixture.nativeElement.querySelector('app-resumo-validados')).toBeNull();
      expect(fixture.nativeElement.querySelector('app-processo-validacao')).not.toBeNull();
      expect(botoes('Validar')).toHaveLength(1);
      expect(botoes('Rejeitar')).toHaveLength(0);

      obter.mockReturnValue(
        of(detalhe({ ...devolvido, acoesDisponiveis: ['enviar-incm', 'enviar-pcm'], revalidacaoPcm: false, todosItensRevistos: true })),
      );
      botoes('Validar')[0].click();
      fixture.detectChanges();

      expect(gravarValidacaoItens).toHaveBeenCalledWith('p1', 3, [{ tipo: 'DOCUMENTO', itemId: 'd-pub', decisao: 'VALIDADO' }]);
      expect(texto()).toContain('Já pode enviar o processo à INCM ou de novo à PCM.');
      botoes('Seguinte')[0].click();
      fixture.detectChanges();
      expect(fixture.nativeElement.querySelector('app-resumo-validados')).not.toBeNull();
      expect(botoes('Publicar')).toHaveLength(1);
      expect(botoes('Enviar PCM')).toHaveLength(1);
      expect(validar).not.toHaveBeenCalled();
    });

    it('«Publicar» envia à INCM e relê o detalhe', () => {
      obter.mockReturnValue(of(detalhe({ estado: 'VALIDADO_DGT', acoesDisponiveis: ['enviar-incm'], opcoesAlteracaoPeot: [] })));
      fixture = TestBed.createComponent(ProcessoVistaDgtComponent);
      fixture.detectChanges();
      obter.mockReturnValue(of(detalhe({ estado: 'EM_VALIDACAO_INCM', acoesDisponiveis: [], opcoesAlteracaoPeot: [] })));

      botoes('Publicar')[0].click();
      fixture.detectChanges();

      expect(enviarIncm).toHaveBeenCalledWith('p1', 3);
      expect(obter).toHaveBeenCalledTimes(2);
      expect(botoes('Publicar')).toHaveLength(0);
      expect(texto()).toContain('Em validação INCM');
    });

    describe('diálogo «Enviar PCM»', () => {
      const VALIDADO_PCM = () =>
        detalhe({ estado: 'VALIDADO_DGT', serieDr: 'PRIMEIRA', acoesDisponiveis: ['enviar-incm', 'enviar-pcm'], opcoesAlteracaoPeot: [] });

      function dialogo(): HTMLElement | null {
        return fixture.nativeElement.querySelector('app-enviar-pcm [role="dialog"]');
      }

      function abrirDialogo(): void {
        obter.mockReturnValue(of(VALIDADO_PCM()));
        fixture = TestBed.createComponent(ProcessoVistaDgtComponent);
        fixture.detectChanges();
        botoes('Enviar PCM')[0].click();
        fixture.detectChanges();
      }

      function escolherUtilizador(id: string): void {
        const select = fixture.nativeElement.querySelector('app-seletor[data-campo="utilizador-pcm"] select') as HTMLSelectElement;
        select.value = id;
        select.dispatchEvent(new Event('change'));
        fixture.detectChanges();
      }

      it('«Enviar PCM» do rodapé abre o diálogo com os utilizadores da PCM; Cancelar fecha-o sem enviar', () => {
        abrirDialogo();

        expect(fixture.componentInstance.envioPcmPedido()).toBe(true);
        expect(dialogo()?.textContent).toContain('Enviar PCM');
        expect(listarEntidades).toHaveBeenCalledWith({ tipoEntidade: 'PCM', ativo: true }, 0, 100);
        expect(listarUtilizadores).toHaveBeenCalledWith({ entidadeId: 90 }, 0, 100);

        botoes('Cancelar')[0].click();
        fixture.detectChanges();

        expect(dialogo()).toBeNull();
        expect(enviarPcm).not.toHaveBeenCalled();
        expect(botoes('Enviar PCM')).toHaveLength(1);
      });

      it('Confirmar envia, fecha o diálogo, mostra «Processo enviado à PCM.», tira o botão e relê o detalhe', () => {
        abrirDialogo();
        obter.mockReturnValue(of(detalhe({ ...VALIDADO_PCM(), versao: 4, pcmAtribuidoAId: 'u-pcm', acoesDisponiveis: ['enviar-incm'] })));

        escolherUtilizador('u-pcm');
        botoes('Confirmar')[0].click();
        fixture.detectChanges();

        expect(enviarPcm).toHaveBeenCalledExactlyOnceWith('p1', 3, 'u-pcm');
        expect(dialogo()).toBeNull();
        expect(fixture.nativeElement.querySelector('app-resumo-validados .sucesso')?.textContent).toContain('Processo enviado à PCM.');
        expect(botoes('Enviar PCM')).toHaveLength(0);
        expect(botoes('Publicar')).toHaveLength(1);
        expect(obter).toHaveBeenCalledTimes(2);
        expect(fixture.componentInstance.processo()?.pcmAtribuidoAId).toBe('u-pcm');
      });

      it('Recarregar depois de um 409 fecha o diálogo e relê o detalhe, sem repetir o envio', () => {
        enviarPcm.mockReturnValue(
          throwError(() => new ApiError('PCM_JA_ATRIBUIDO', 'O processo já foi enviado à PCM.', 't1', null)),
        );
        abrirDialogo();
        escolherUtilizador('u-pcm');
        botoes('Confirmar')[0].click();
        fixture.detectChanges();
        obter.mockReturnValue(of(detalhe({ ...VALIDADO_PCM(), pcmAtribuidoAId: 'u-outro', acoesDisponiveis: ['enviar-incm'] })));

        botoes('Recarregar')[0].click();
        fixture.detectChanges();

        expect(enviarPcm).toHaveBeenCalledOnce();
        expect(dialogo()).toBeNull();
        expect(obter).toHaveBeenCalledTimes(2);
        expect(botoes('Enviar PCM')).toHaveLength(0);
      });
    });
  });
});
