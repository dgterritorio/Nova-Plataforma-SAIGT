import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { Router, provideRouter } from '@angular/router';
import { ProcessoDocumentosComponent } from '../processo-documentos/processo-documentos.component';
import { Subject, of, throwError } from 'rxjs';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ListaValoresService } from '../../../services/lista-valores.service';
import { ReferenciaSnitService } from '../../../services/referencia-snit.service';
import { CriarProcessoComponent } from './criar-processo.component';
import { AuthService } from '../../../core/auth/auth.service';
import { ApiError } from '../../../core/models/error-response.model';
import {
  AtoReferivel,
  DinamicaEmCurso,
  DinamicaPainel,
  ElaboracaoNova,
  IgtSnit,
  Instrumento,
  InstrumentoDisponibilidade,
  InstrumentoPainel,
  ProcedimentoDisponivel,
  ProcedimentoPainel,
  TipoInstrumento,
} from '../../../models/instrumento.model';
import { DocumentoProcesso, EstruturaDocumental } from '../../../models/documento.model';
import { DinamicaAssociavel, PendenciaSubmissao, Processo, ProcessoDetalhe } from '../../../models/processo.model';
import { DinamicaSemIgtEmCurso, TipoDinamicaSemIgt, TiposProcesso } from '../../../models/dinamica.model';
import { DinamicaService } from '../../../services/dinamica.service';
import { DocumentoService } from '../../../services/documento.service';
import { InstrumentoService } from '../../../services/instrumento.service';
import { PlantasService } from '../../../services/plantas.service';
import { ProcessoService } from '../../../services/processo.service';

function documento(parcial: Partial<DocumentoProcesso> = {}): DocumentoProcesso {
  return {
    id: 'doc-1',
    documentoConfigId: 1,
    nomeFicheiro: 'deliberacao.pdf',
    categoria: 'PUBLICACAO',
    extensao: 'pdf',
    origem: 'CARREGADO',
    bloqueadoEdicao: false,
    estadoValidacao: 'POR_VALIDAR',
    tamanhoBytes: 1024,
    criadoEm: '2026-10-20T09:30:00Z',
    criadoPorId: 'u1',
    criadoPorNome: 'Helena Sintra',
    tags: [],
    justificacaoDesbloqueio: null,
    ...parcial,
  };
}

const ESTRUTURA_DOCUMENTAL: EstruturaDocumental = {
  separadores: [
    { separador: 'Deliberação', elementoInformacaoBase: 'DELIBERACAO', publicacao: [], deposito: [] },
    { separador: 'Texto do Ato', elementoInformacaoBase: 'TEXTO_ATO', publicacao: [], deposito: [] },
  ],
  outros: [],
  pendencias: [],
};

describe('CriarProcessoComponent', () => {
  let fixture: ComponentFixture<CriarProcessoComponent>;
  let documentoServiceMock: {
    estrutura: ReturnType<typeof vi.fn>;
    listar: ReturnType<typeof vi.fn>;
  };
  let plantasServiceMock: {
    obterPlantas: ReturnType<typeof vi.fn>;
    gravarPlantas: ReturnType<typeof vi.fn>;
    obterMetadados: ReturnType<typeof vi.fn>;
    gravarMetadados: ReturnType<typeof vi.fn>;
    obterMetadadosMotu: ReturnType<typeof vi.fn>;
    gravarSerieMotu: ReturnType<typeof vi.fn>;
  };
  let instrumentoServiceMock: {
    listarTipos: ReturnType<typeof vi.fn>;
    listar: ReturnType<typeof vi.fn>;
    listarConcelhos: ReturnType<typeof vi.fn>;
    pesquisarSnit: ReturnType<typeof vi.fn>;
    criar: ReturnType<typeof vi.fn>;
    disponivel: ReturnType<typeof vi.fn>;
    painel: ReturnType<typeof vi.fn>;
    iniciarDinamica: ReturnType<typeof vi.fn>;
    elaboracaoNova: ReturnType<typeof vi.fn>;
    criarElaboracao: ReturnType<typeof vi.fn>;
    tiposProcesso: ReturnType<typeof vi.fn>;
  };
  let dinamicaServiceMock: {
    iniciar: ReturnType<typeof vi.fn>;
    disponivel: ReturnType<typeof vi.fn>;
  };
  let authMock: {
    nome: () => string;
    origem: () => string;
    entidade: () => { nome: string; nipc: string } | null;
    perfilInterno: () => string | null;
    podeCriarInstrumento: () => boolean;
  };
  let processoServiceMock: {
    dinamicasAssociaveis?: ReturnType<typeof vi.fn>;
    criar: ReturnType<typeof vi.fn>;
    guardarRascunho: ReturnType<typeof vi.fn>;
    submeter: ReturnType<typeof vi.fn>;
    obter: ReturnType<typeof vi.fn>;
  };

  const tipos: TipoInstrumento[] = [
    { id: 1, codigo: 'PDM', nome: 'Plano Diretor Municipal', categoria: 'PLANO', entidadeInstrutoraObrigatoria: true },
    { id: 2, codigo: 'PU', nome: 'Plano de Urbanização', categoria: 'PLANO', entidadeInstrutoraObrigatoria: true },
  ];

  function instrumento(overrides: Partial<Instrumento> = {}): Instrumento {
    return {
      id: 'instrumento-1',
      tipoInstrumentoId: 1,
      tipoInstrumentoNome: 'Plano Diretor Municipal',
      numero: '1',
      designacao: 'PDM de Sintra',
      responsavelId: 'u1',
      emVigor: false,
      origemDados: 'NATIVO',
      criadoPorId: 'u1',
      criadoEm: '2026-01-01T10:00:00Z',
      ...overrides,
    };
  }

  function procedimento(overrides: Partial<ProcedimentoDisponivel> = {}): ProcedimentoDisponivel {
    return {
      instrumentoProcedimentoConfigId: 214,
      tipoProcedimento: 'DECISAO_INICIO',
      nomeProcedimento: 'Decisão de início',
      ordem: 1,
      obrigatorio: true,
      atoPontual: false,
      prazoDias: 30,
      serieDr: 'SEGUNDA_H',
      formulario: 'INFORMACAO_GERAL',
      documentosExigidos: [],
      tiposAtoDisponiveis: [{ id: 5, codigo: 'DESP', nome: 'Despacho' }],
      tentativaEmCurso: null,
      ...overrides,
    };
  }

  function dinamicaEmCurso(overrides: Partial<DinamicaEmCurso> = {}): DinamicaEmCurso {
    return {
      dinamicaId: 'dinamica-1',
      tipoDinamica: 'ALTERACAO_SIMPLIFICADA_ART123',
      nomeDinamica: 'Alteração simplificada (art. 123.º)',
      descritivo: 'Alteração da planta',
      estado: 'EM_CURSO',
      dataInicio: '2026-05-30T10:00:00Z',
      dataCaducidade: null,
      procedimentosDisponiveis: [procedimento()],
      ...overrides,
    };
  }

  function procedimentoPainel(overrides: Partial<ProcedimentoPainel> = {}): ProcedimentoPainel {
    return {
      instrumentoProcedimentoConfigId: 201,
      tipoProcedimento: 'DECISAO_INICIO',
      nomeProcedimento: 'Decisão de início',
      ordem: 1,
      obrigatorio: true,
      processos: [
        {
          id: 'processo-antigo',
          numeroProcesso: '00007',
          ocorrencia: 1,
          estado: 'PUBLICADO',
          numeroDr: 'DR II S. n.º 28/2016',
          dataPublicacaoDr: '2016-02-16',
          linkDr: 'https://diariodarepublica.pt/dr/detalhe/exemplo',
        },
      ],
      ...overrides,
    };
  }

  function dinamicaPainel(overrides: Partial<DinamicaPainel> = {}): DinamicaPainel {
    return {
      id: 'dinamica-antiga',
      numeroDinamica: '00001',
      descritivo: 'Elaboração do PDM',
      tipoDinamica: 'ELABORACAO',
      nomeDinamica: 'Elaboração',
      estado: 'TERMINADA',
      tentativa: 1,
      semaforo: 'VERDE',
      dataInicio: '2016-01-10T10:00:00Z',
      dataConclusao: '2018-01-17T10:00:00Z',
      dataCaducidade: null,
      historica: false,
      procedimentos: [procedimentoPainel()],
      ...overrides,
    };
  }

  function painel(dinamicas: DinamicaPainel[] = [dinamicaPainel()]): InstrumentoPainel {
    return {
      id: 'instrumento-1',
      tipoInstrumentoId: 1,
      designacao: 'PDM de Sintra',
      numero: '1',
      idIgtSnit: null,
      emVigor: true,
      origemDados: 'NATIVO',
      criadoPorId: 'u1',
      criadoEm: '2026-01-01T10:00:00Z',
      dinamicas,
    };
  }

  function disponibilidade(overrides: Partial<InstrumentoDisponibilidade> = {}): InstrumentoDisponibilidade {
    return {
      dinamicasDisponiveis: [{ instrumentoDinamicaConfigId: 103, tipoDinamica: 'ALTERACAO', nomeDinamica: 'Alteração' }],
      dinamicasEmCurso: [dinamicaEmCurso()],
      ultimaDinamicaPublicada: null,
      ...overrides,
    };
  }

  function processo(overrides: Partial<Processo> = {}): Processo {
    return {
      id: 'processo-1',
      dinamicaId: 'dinamica-1',
      instrumentoProcedimentoConfigId: 214,
      estado: 'EM_SUBMISSAO',
      numeroProcesso: '00001',
      ocorrencia: 1,
      versao: 0,
      origemDados: 'NATIVO',
      tipoAtoId: 5,
      criadoPorId: 'u1',
      dadosFormulario: null,
      dataSubmissao: null,
      pcmAtribuidoAId: null,
      camposBloqueados: [],
      ...overrides,
    };
  }

  /** Superconjunto de `processo()`, para `retomarTentativa()`. */
  function processoDetalhe(overrides: Partial<ProcessoDetalhe> = {}): ProcessoDetalhe {
    return {
      ...processo(),
      instrumentoId: 'instrumento-1',
      designacaoInstrumento: 'PDM de Sintra',
      numeroInstrumento: '1',
      tipoInstrumento: 'PDM',
      instrumentosAbrangidos: [{ id: 'instrumento-1', designacao: 'PDM de Sintra', tipoInstrumento: 'PDM' }],
      associacaoInstrumentos: 'UM',
      tiposInstrumentoAdmitidos: [],
      podeAssociarInstrumentos: false,
      categoriaInstrumento: 'PLANO',
      entidadeInstrutoraObrigatoria: true,
      territorio: { concelhos: [], nutsII: [] },
      idus: null,
      entidadeId: 1,
      entidadeNif: '500051062',
      entidadeNome: 'Câmara Municipal de Sintra',
      tipoDinamica: 'ALTERACAO_SIMPLIFICADA_ART123',
      nomeDinamica: 'Alteração simplificada (art. 123.º)',
      numeroDinamica: '00001',
      descritivoDinamica: 'Alteração da planta',
      estadoDinamica: 'EM_CURSO',
      dataCaducidadeDinamica: null,
      tipoProcedimento: 'DECISAO_INICIO',
      nomeProcedimento: 'Decisão de início',
      serieDr: 'SEGUNDA_H',
      formulario: 'INFORMACAO_GERAL',
      tipoAtoNome: 'Despacho',
      tiposAtoDisponiveis: [{ id: 5, codigo: 'DESPACHO', nome: 'Despacho' }],
      autorNome: 'Helena Sintra',
      sumario: null,
      dataAssinatura: null,
      normaLegal: null,
      tipoInformacao: null,
      publicarSuplemento: null,
      dataSuplemento: null,
      contactoTramitacao: null,
      entidadeInstrutora: null,
      entidadeEmitente: null,
      contactoPublicacao: null,
      elementosAfetados: null,
      pedeFundamentacaoRjigt: false,
      fundamentacaoRjigt: [],
      opcoesFundamentacaoRjigt: [],
      pedeLocalizacao: 'NAO',
      pedeMotivoAlteracao: 'NAO',
      localizacao: null,
      conformaHierarquiaSuperior: null,
      motivosAlteracao: [],
      numeroDr: null,
      dataPublicacaoDr: null,
      linkDr: null,
      incmActIdentifier: null,
      criadoEm: '2026-09-20T10:00:00Z',
      atualizadoEm: '2026-09-20T10:00:00Z',
      ultimaRejeicaoDgt: null,
      ultimaRejeicaoIncm: null,
      editavel: true,
      acoesDisponiveis: ['submeter'],
      podeSubmeter: true,
      exigeOpcaoGeoconform: false,
      perguntaGeoconform: 'NAO_APLICAVEL',
      exigeElementosAfetados: true,
      exigePlantasEMetadados: false,
      comGeoconform: null,
      informacaoBase: [],
      todosItensRevistos: true,
      rejeicoesEmValidacao: [],
      dinamicasATerminar: [],
      aceitaDinamicaAssociada: false,
      destino: 'PUBLICACAO_DEPOSITO',
      destinoEditavel: false,
      atoPontual: false,
      dinamicaAssociadaId: null,
      dinamicaAssociada: null,
      ...overrides,
    };
  }

  beforeEach(() => {
    instrumentoServiceMock = {
      listarTipos: vi.fn().mockReturnValue(of(tipos)),
      listar: vi.fn().mockReturnValue(of({ content: [instrumento()], page: 0, size: 100, totalElements: 1, totalPages: 1 })),
      listarConcelhos: vi.fn().mockReturnValue(of([{ codigo: '1111', nome: 'Sintra' }])),
      pesquisarSnit: vi.fn().mockReturnValue(of({ content: [], page: 0, size: 100, totalElements: 0, totalPages: 0 })),
      criar: vi.fn().mockReturnValue(of(instrumento({ id: 'instrumento-novo', designacao: 'PDM de Odivelas' }))),
      disponivel: vi.fn().mockReturnValue(of(disponibilidade())),
      painel: vi.fn().mockReturnValue(of(painel())),
      iniciarDinamica: vi.fn().mockReturnValue(of({ id: 'dinamica-2' })),
      // Por omissão o tipo não tem Elaboração e o Novo IGT segue pela página da designação.
      elaboracaoNova: vi.fn().mockReturnValue(throwError(() => new ApiError('NAO_ENCONTRADO', 'Sem Elaboração.', 't0', null))),
      criarElaboracao: vi.fn(),
      tiposProcesso: vi.fn().mockReturnValue(
        of({
          comAssociacaoIgt: tipos.map((tipo) => ({ tipoInstrumentoId: tipo.id, codigo: tipo.codigo, nome: tipo.nome })),
          semAssociacaoIgt: [],
        }),
      ),
    };
    dinamicaServiceMock = {
      iniciar: vi.fn(),
      disponivel: vi.fn().mockReturnValue(of({ dinamicasEmCurso: [] })),
    };
    processoServiceMock = {
      criar: vi.fn().mockReturnValue(of(processo())),
      guardarRascunho: vi.fn().mockReturnValue(of(processo({ versao: 1 }))),
      submeter: vi.fn().mockReturnValue(of(processo({ estado: 'ENVIADO_DGT', dataSubmissao: '2026-09-17T16:00:00Z' }))),
      obter: vi.fn().mockReturnValue(of(processoDetalhe())),
      dinamicasAssociaveis: vi.fn().mockReturnValue(of({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 })),
    };

    documentoServiceMock = {
      estrutura: vi.fn().mockReturnValue(of(ESTRUTURA_DOCUMENTAL)),
      listar: vi.fn().mockReturnValue(of({ content: [documento()], page: 0, size: 100, totalElements: 1, totalPages: 1 })),
    };

    plantasServiceMock = {
      obterPlantas: vi.fn().mockReturnValue(of({ exigePlantas: true, itens: [], linhas: [], errosTxt: [], exigeCartograma: false, cartogramaPdfDisponivel: false })),
      gravarPlantas: vi.fn().mockReturnValue(of({ exigePlantas: true, itens: [], linhas: [], errosTxt: [], exigeCartograma: false, cartogramaPdfDisponivel: false })),
      obterMetadados: vi.fn().mockReturnValue(
        of({ exigeMetadados: true, titulo: 'PDM', responsavel: {}, dados: {} }),
      ),
      gravarMetadados: vi.fn(),
      obterMetadadosMotu: vi.fn().mockReturnValue(of({ aplicaFichaMotu: false, responsavel: {}, registos: [] })),
      gravarSerieMotu: vi.fn(),
    };

    authMock = {
      nome: () => 'Helena Sintra',
      origem: () => 'INCM',
      entidade: () => ({ nome: 'Câmara Municipal de Sintra', nipc: '500051062' }),
      perfilInterno: () => null,
      podeCriarInstrumento: () => true,
    };

    TestBed.configureTestingModule({
      imports: [CriarProcessoComponent],
      providers: [
        provideRouter([]),
        { provide: InstrumentoService, useValue: instrumentoServiceMock },
        { provide: DinamicaService, useValue: dinamicaServiceMock },
        { provide: DocumentoService, useValue: documentoServiceMock },
        { provide: ProcessoService, useValue: processoServiceMock },
        { provide: AuthService, useValue: authMock },
        { provide: ListaValoresService, useValue: { opcoes: vi.fn().mockReturnValue(of([])) } },
        { provide: ReferenciaSnitService, useValue: { nutsII: vi.fn().mockReturnValue(of([])), concelhos: vi.fn().mockReturnValue(of([])) } },
        { provide: PlantasService, useValue: plantasServiceMock },
      ],
    });

    fixture = TestBed.createComponent(CriarProcessoComponent);
    fixture.detectChanges();
  });

  /** Chega ao fim do passo 1 sem ainda ter criado nada. */
  function completarPasso1(): void {
    const componente = fixture.componentInstance;
    componente.selecionarTipo('1');
    componente.selecionarInstrumento('instrumento-1');
    componente.escolherModo('continuar');
    componente.selecionarDinamica('dinamica-1');
    componente.selecionarProcedimento('214');
    fixture.detectChanges();
  }

  /** O detalhe por omissão pede os elementos afetados: sem nenhum, o «Seguinte» do cabeçalho não avança. */
  function assinalarPecasEscritas(): void {
    fixture.componentInstance.formularioRef()!.form.controls.elementosAfetados.patchValue({ PECAS_ESCRITAS: true });
    fixture.detectChanges();
  }

  function etiquetasPassos(): string[] {
    return [...fixture.nativeElement.querySelectorAll('app-passos .passo-etiqueta')].map((passo: Element) => passo.textContent?.trim() ?? '');
  }

  function radios(): HTMLInputElement[] {
    return [...fixture.nativeElement.querySelectorAll('app-lista-opcoes input[type="radio"]')];
  }

  // --- passo 1 ---

  it('abre no Tipo IGT, com três passos, sem Voltar e sem poder avançar', () => {
    expect(fixture.componentInstance.paginaInicial()).toBe('tipo');
    expect(etiquetasPassos()).toEqual(['Tipo IGT', 'IGT Novo/Existente', 'Formulário']);
    expect(fixture.nativeElement.querySelector('app-pagina-tipo-igt')).not.toBeNull();
    expect(fixture.nativeElement.querySelector('.navegacao-topo')).toBeNull();
    expect(fixture.componentInstance.podeAvancar()).toBe(false);
  });

  it('o Tipo IGT mostra os tipos pela ordem do Figma, seja qual for a ordem do backend', () => {
    fixture.componentInstance.tipos.set([
      { id: 3, codigo: 'PP', nome: 'Plano de Pormenor', categoria: 'PLANO', entidadeInstrutoraObrigatoria: true },
      { id: 2, codigo: 'PU', nome: 'Plano de Urbanização', categoria: 'PLANO', entidadeInstrutoraObrigatoria: true },
      { id: 9, codigo: 'OUTRO', nome: 'Outro tipo', categoria: 'PLANO', entidadeInstrutoraObrigatoria: true },
      { id: 1, codigo: 'PDM', nome: 'Plano Diretor Municipal', categoria: 'PLANO', entidadeInstrutoraObrigatoria: true },
    ]);
    fixture.componentInstance.tiposProcesso.set({
      comAssociacaoIgt: [3, 2, 9, 1].map((id) => ({ tipoInstrumentoId: id, codigo: '', nome: '' })),
      semAssociacaoIgt: [],
    });
    fixture.detectChanges();

    const etiquetas = [...fixture.nativeElement.querySelectorAll('app-lista-opcoes .opcao-etiqueta')].map((e: Element) => e.textContent);
    expect(etiquetas).toEqual(['PDM', 'PU', 'PP', 'OUTRO']);
  });

  it('a Nova Dinâmica não mostra texto técnico e, com uma só dinâmica possível, já a traz escolhida', () => {
    const componente = fixture.componentInstance;
    componente.selecionarTipo('1');
    componente.paginaInicial.set('igt');
    componente.selecionarInstrumento('instrumento-1');
    componente.paginaInicial.set('acao');
    componente.escolherModo('nova');
    componente.avancar();
    fixture.detectChanges();

    expect(componente.paginaInicial()).toBe('nova');
    expect(componente.dinamicaNovaId()).toBe(103);
    expect(fixture.nativeElement.querySelector('app-pagina-nova-dinamica').textContent).not.toContain('backend');
  });

  it('o Tipo IGT lista os tipos do backend e escolher um deixa avançar', () => {
    const titulos = [...fixture.nativeElement.querySelectorAll('app-lista-opcoes .opcao-titulo')].map((e: Element) => e.textContent);
    const etiquetas = [...fixture.nativeElement.querySelectorAll('app-lista-opcoes .opcao-etiqueta')].map((e: Element) => e.textContent);
    expect(titulos).toEqual(['Plano Diretor Municipal', 'Plano de Urbanização']);
    expect(etiquetas).toEqual(['PDM', 'PU']);

    radios()[0].click();
    fixture.detectChanges();

    expect(fixture.componentInstance.tipoEscolhidoId()).toBe(1);
    expect(fixture.componentInstance.podeAvancar()).toBe(true);
  });

  it('percorre as páginas pela ordem do Figma e o Voltar regressa à anterior', () => {
    const componente = fixture.componentInstance;
    componente.selecionarTipo('1');
    componente.avancar();
    expect(componente.paginaInicial()).toBe('origem');
    expect(componente.podeAvancar()).toBe(false);

    componente.escolherOrigem('existente');
    componente.avancar();
    expect(componente.paginaInicial()).toBe('igt');
    expect(componente.podeAvancar()).toBe(false);

    componente.selecionarInstrumento('instrumento-1');
    expect(componente.podeAvancar()).toBe(true);
    componente.avancar();
    expect(componente.paginaInicial()).toBe('acao');
    expect(componente.podeAvancar()).toBe(false);

    componente.escolherModo('continuar');
    componente.avancar();
    expect(componente.paginaInicial()).toBe('continuar');

    componente.voltarPasso();
    componente.voltarPasso();
    componente.voltarPasso();
    componente.voltarPasso();
    expect(componente.paginaInicial()).toBe('tipo');
  });

  describe('IGT Existente com os IGT em vigor do SNIT', () => {
    const PDM_SINTRA_SNIT: IgtSnit = { idIgtSnit: '1001', designacao: 'PDM de Sintra (SNIT)', tipoInstrumento: 'PDM', instrumentoId: null };
    const PDM_CASCAIS_SNIT: IgtSnit = { idIgtSnit: '1002', designacao: 'PDM de Cascais (SNIT)', tipoInstrumento: 'PDM', instrumentoId: null };

    function paginaSnit(content: IgtSnit[]) {
      return of({ content, page: 0, size: 100, totalElements: content.length, totalPages: 1 });
    }

    function abrirIgtExistente(): void {
      const componente = fixture.componentInstance;
      componente.selecionarTipo('1');
      componente.avancar();
      componente.escolherOrigem('existente');
      componente.avancar();
      fixture.detectChanges();
    }

    function titulosOpcoes(): string[] {
      return [...fixture.nativeElement.querySelectorAll('app-pagina-igt-existente .opcao-titulo')].map((e: Element) => e.textContent?.trim() ?? '');
    }

    it('pesquisa o tipo escolhido no SNIT e lista os IGT em vigor que ainda não existem na SAIGT a seguir aos da SAIGT', () => {
      const jaNaSaigt: IgtSnit = { ...PDM_CASCAIS_SNIT, instrumentoId: 'instrumento-1' };
      instrumentoServiceMock.pesquisarSnit.mockReturnValue(paginaSnit([PDM_SINTRA_SNIT, jaNaSaigt]));

      abrirIgtExistente();

      expect(instrumentoServiceMock.pesquisarSnit).toHaveBeenCalledWith(1, 0, 100);
      expect(titulosOpcoes()).toEqual(['PDM de Sintra', 'PDM de Sintra (SNIT)']);
      expect(fixture.nativeElement.querySelector('app-pagina-igt-existente').textContent).toContain('No SNIT, ainda não existe na SAIGT');
    });

    it('pesquisa o SNIT uma vez por tipo, mesmo voltando à página', () => {
      abrirIgtExistente();
      const componente = fixture.componentInstance;
      componente.voltarPasso();
      componente.avancar();

      expect(componente.paginaInicial()).toBe('igt');
      expect(instrumentoServiceMock.pesquisarSnit).toHaveBeenCalledTimes(1);
    });

    it('ao escolher um IGT do SNIT o botão passa a «Criar IGT», o Seguinte fica desativado e não lê dinâmicas', () => {
      instrumentoServiceMock.pesquisarSnit.mockReturnValue(paginaSnit([PDM_SINTRA_SNIT]));
      abrirIgtExistente();
      const componente = fixture.componentInstance;

      radios()[1].click();
      fixture.detectChanges();

      expect(componente.igtSnitEscolhido()).toEqual(PDM_SINTRA_SNIT);
      expect(componente.instrumentoEscolhidoId()).toBeNull();
      expect(componente.podeAvancar()).toBe(true);
      expect(componente.rotuloAvancar()).toBe('Criar IGT');
      expect(componente.seguinteDesativado()).toBe(true);
      expect(instrumentoServiceMock.disponivel).not.toHaveBeenCalled();
      expect(fixture.nativeElement.querySelector('app-pagina-igt-existente').textContent).toContain('ainda não existe na SAIGT');
    });

    it('cria o Instrumento com o idIgtSnit do escolhido, sem concelhos, e segue para a Ação com a Alteração disponível', () => {
      instrumentoServiceMock.pesquisarSnit.mockReturnValue(paginaSnit([PDM_SINTRA_SNIT]));
      instrumentoServiceMock.criar.mockReturnValue(
        of(instrumento({ id: 'instrumento-snit', designacao: 'PDM de Sintra (SNIT)', emVigor: true })),
      );
      instrumentoServiceMock.disponivel.mockReturnValue(of(disponibilidade({ dinamicasEmCurso: [] })));
      instrumentoServiceMock.painel.mockReturnValue(of(painel([])));
      abrirIgtExistente();
      const componente = fixture.componentInstance;
      componente.selecionarInstrumento('snit:1001');

      componente.avancar();
      fixture.detectChanges();

      expect(instrumentoServiceMock.criar).toHaveBeenCalledWith({ tipoInstrumentoId: 1, designacao: 'PDM de Sintra (SNIT)', idIgtSnit: '1001' });
      expect(instrumentoServiceMock.disponivel).toHaveBeenCalledWith('instrumento-snit');
      expect(componente.instrumentoEscolhidoId()).toBe('instrumento-snit');
      expect(componente.igtSnitEscolhido()).toBeNull();
      expect(componente.igtSnitPorCriar()).toEqual([]);
      expect(componente.paginaInicial()).toBe('acao');
      expect(componente.mensagemSucesso()).toBe('IGT PDM de Sintra (SNIT) criado com sucesso.');
      const acao = fixture.nativeElement.querySelector('app-pagina-acao').textContent;
      expect(acao).toContain('PDM de Sintra (SNIT)');
      expect(acao).toContain('Criar Nova Dinâmica');
    });

    it('um IGT que entretanto já existe dá o 409 no banner e fica na página', () => {
      instrumentoServiceMock.pesquisarSnit.mockReturnValue(paginaSnit([PDM_SINTRA_SNIT]));
      instrumentoServiceMock.criar.mockReturnValue(
        throwError(() => new ApiError('REFERENCIA_DUPLICADA', 'O IGT já existe na SAIGT.', 't1', null)),
      );
      abrirIgtExistente();
      const componente = fixture.componentInstance;
      componente.selecionarInstrumento('snit:1001');

      componente.avancar();
      fixture.detectChanges();

      expect(componente.erro()?.codigo).toBe('REFERENCIA_DUPLICADA');
      expect(componente.paginaInicial()).toBe('igt');
      expect(componente.aCriarIgt()).toBe(false);
    });

    it('sem entidade na sessão não deixa criar o IGT do SNIT e diz porquê', () => {
      authMock.podeCriarInstrumento = () => false;
      instrumentoServiceMock.pesquisarSnit.mockReturnValue(paginaSnit([PDM_SINTRA_SNIT]));
      fixture = TestBed.createComponent(CriarProcessoComponent);
      fixture.detectChanges();
      abrirIgtExistente();
      const componente = fixture.componentInstance;
      componente.selecionarInstrumento('snit:1001');

      expect(componente.podeAvancar()).toBe(false);
      expect(componente.dicaAvancar()).toBe('Só quem age por uma entidade pode criar um IGT.');
      componente.criarIgtDoSnit();
      expect(instrumentoServiceMock.criar).not.toHaveBeenCalled();
    });

    it('com o SNIT indisponível avisa e mantém os IGT da SAIGT', () => {
      instrumentoServiceMock.pesquisarSnit.mockReturnValue(
        throwError(() => new ApiError('INTEGRACAO_INDISPONIVEL', 'Serviço do SNIT indisponível.', 't2', null)),
      );

      abrirIgtExistente();

      const pagina = fixture.nativeElement.querySelector('app-pagina-igt-existente');
      expect(pagina.querySelector('[role="alert"]').textContent).toContain('Não foi possível consultar o SNIT');
      expect(titulosOpcoes()).toEqual(['PDM de Sintra']);
      expect(fixture.componentInstance.aPesquisarSnit()).toBe(false);
    });

    it('sem IGT na SAIGT nem no SNIT diz que não há planos do tipo', () => {
      instrumentoServiceMock.listar.mockReturnValue(of({ content: [], page: 0, size: 100, totalElements: 0, totalPages: 0 }));
      fixture = TestBed.createComponent(CriarProcessoComponent);
      fixture.detectChanges();

      abrirIgtExistente();

      expect(fixture.nativeElement.querySelector('app-pagina-igt-existente').textContent).toContain('Sem planos deste tipo.');
      expect(fixture.componentInstance.dicaAvancar()).toBe('Ainda não há IGT deste tipo: volte e escolha «Novo IGT».');
    });
  });

  describe('IGT Existente: o cartão mostra os concelhos do Instrumento', () => {
    function abrirComInstrumentos(instrumentos: Instrumento[], snit: IgtSnit[] = []): HTMLElement {
      instrumentoServiceMock.listar.mockReturnValue(of({ content: instrumentos, page: 0, size: 100, totalElements: instrumentos.length, totalPages: 1 }));
      instrumentoServiceMock.pesquisarSnit.mockReturnValue(of({ content: snit, page: 0, size: 100, totalElements: snit.length, totalPages: 1 }));
      fixture = TestBed.createComponent(CriarProcessoComponent);
      fixture.detectChanges();
      const componente = fixture.componentInstance;
      componente.selecionarTipo('1');
      componente.avancar();
      componente.escolherOrigem('existente');
      componente.avancar();
      fixture.detectChanges();
      return fixture.nativeElement.querySelector('app-pagina-igt-existente');
    }

    function descricoesDosCartoes(pagina: HTMLElement): (string | null)[] {
      return [...pagina.querySelectorAll('.opcao-texto')].map((cartao) => cartao.querySelector('.opcao-descricao')?.textContent?.trim() ?? null);
    }

    it('aberto pela CCDR, o cartão de um PDM de Sintra mostra o concelho e não a entidade da sessão', () => {
      authMock.entidade = () => ({ nome: 'Comissão de Coordenação e Desenvolvimento Regional de Lisboa e Vale do Tejo, IP', nipc: '600000001' });

      const pagina = abrirComInstrumentos([instrumento({ concelhos: ['1111'], nomesConcelhos: ['SINTRA'] })]);

      expect(descricoesDosCartoes(pagina)).toEqual(['Sintra']);
      expect(pagina.textContent).not.toContain('Comissão de Coordenação');
    });

    it('aberto pela Câmara de Sintra, o cartão mostra o concelho', () => {
      const pagina = abrirComInstrumentos([instrumento({ concelhos: ['1111'], nomesConcelhos: ['SINTRA'] })]);

      expect(descricoesDosCartoes(pagina)).toEqual(['Sintra']);
      expect(pagina.textContent).not.toContain('Câmara Municipal de Sintra');
    });

    it('um plano intermunicipal mostra os nomes dos concelhos formatados e separados por vírgulas, pela ordem de concelhos', () => {
      const pagina = abrirComInstrumentos([
        instrumento({ designacao: 'PIOT', concelhos: ['1105', '1110', '1111'], nomesConcelhos: ['CASCAIS', 'VILA NOVA DE GAIA', 'MONTEMOR-O-NOVO'] }),
      ]);

      expect(descricoesDosCartoes(pagina)).toEqual(['Cascais, Vila Nova de Gaia, Montemor-o-Novo']);
    });

    it('um Instrumento sem concelhos fica sem descrição e os IGT do SNIT mantêm a sua', () => {
      const igtSnit: IgtSnit = { idIgtSnit: '1001', designacao: 'PDM de Mafra (SNIT)', tipoInstrumento: 'PDM', instrumentoId: null };

      const pagina = abrirComInstrumentos([instrumento()], [igtSnit]);

      expect(descricoesDosCartoes(pagina)).toEqual([null, 'No SNIT, ainda não existe na SAIGT']);
    });

    it('o subtítulo diz que são os IGT do tipo disponíveis, sem os ligar à entidade', () => {
      const pagina = abrirComInstrumentos([instrumento()]);

      expect(pagina.querySelector('.subtitulo')?.textContent?.replace(/\s+/g, ' ').trim()).toBe('IGT do tipo PDM disponíveis.');
    });
  });

  it('os passos acompanham a página: IGT Existente, Ação e, só ao criar uma Dinâmica, Nova Dinâmica', () => {
    const componente = fixture.componentInstance;
    componente.selecionarTipo('1');
    componente.selecionarInstrumento('instrumento-1');

    componente.paginaInicial.set('igt');
    fixture.detectChanges();
    expect(etiquetasPassos()).toEqual(['Tipo IGT', 'IGT Existente', 'Ação', 'Formulário']);

    componente.paginaInicial.set('nova');
    fixture.detectChanges();
    expect(etiquetasPassos()).toEqual(['Tipo IGT', 'IGT Existente', 'Ação', 'Nova Dinâmica', 'Formulário']);
    expect(fixture.nativeElement.querySelector('app-passos .passo-atual .passo-etiqueta').textContent.trim()).toBe('Nova Dinâmica');
  });

  it('a Ação só mostra o que o backend permite', () => {
    instrumentoServiceMock.disponivel.mockReturnValue(of(disponibilidade({ dinamicasDisponiveis: [], dinamicasEmCurso: [dinamicaEmCurso()] })));
    const componente = fixture.componentInstance;
    componente.selecionarTipo('1');
    componente.selecionarInstrumento('instrumento-1');
    componente.paginaInicial.set('acao');
    fixture.detectChanges();

    expect(radios().map((radio) => radio.disabled)).toEqual([false]);
    expect(fixture.nativeElement.querySelector('app-pagina-acao').textContent).not.toContain('Criar Nova Dinâmica');
  });

  describe('Novo IGT de um tipo com Elaboração', () => {
    function elaboracao(overrides: Partial<ElaboracaoNova> = {}): ElaboracaoNova {
      return {
        instrumentoDinamicaConfigId: 103,
        nomeDinamica: 'Elaboração',
        procedimento: procedimento({ nomeProcedimento: 'Decisão de elaborar', tentativaEmCurso: null }),
        exigeOpcaoGeoconform: true,
        pedeLocalizacao: 'NAO',
        nutsII: [
          { codigo: 'NORTE', nome: 'NORTE' },
          { codigo: 'LISBOA E VALE DO TEJO', nome: 'LISBOA E VALE DO TEJO' },
        ],
        concelhos: [
          { codigo: '1111', nome: 'Sintra' },
          { codigo: '1106', nome: 'Lisboa' },
        ],
        concelhoEntidade: '1111',
        ...overrides,
      };
    }

    function abrirElaboracaoNova(): void {
      instrumentoServiceMock.elaboracaoNova.mockReturnValue(of(elaboracao()));
      const componente = fixture.componentInstance;
      componente.selecionarTipo('1');
      componente.paginaInicial.set('origem');
      componente.escolherOrigem('novo');
      componente.avancar();
      fixture.detectChanges();
    }

    function seletor(rotulo: string): HTMLSelectElement {
      const seletores = [...fixture.nativeElement.querySelectorAll('app-processo-formulario-inicial app-seletor')] as HTMLElement[];
      const encontrado = seletores.find((elemento) => elemento.querySelector('label')?.textContent?.trim().startsWith(rotulo));
      return encontrado!.querySelector('select') as HTMLSelectElement;
    }

    function escolher(rotulo: string, valor: string): void {
      const select = seletor(rotulo);
      select.value = valor;
      select.dispatchEvent(new Event('change'));
    }

    function escrever(controlo: string, valor: string): void {
      const input = fixture.nativeElement.querySelector(`app-processo-formulario-inicial input[name="${controlo}"]`) as HTMLInputElement;
      input.value = valor;
      input.dispatchEvent(new Event('input'));
    }

    function preencherEscolhas(): void {
      escolher('NUT II', 'LISBOA E VALE DO TEJO');
      escrever('designacao', '  PDM de Sintra  ');
      escolher('Tipo de ato', '5');
      fixture.detectChanges();
    }

    function responderGeoconformNao(): void {
      (fixture.nativeElement.querySelectorAll('app-processo-formulario-inicial .geoconform input[type="radio"]')[1] as HTMLInputElement).click();
      fixture.detectChanges();
    }

    it('com a pergunta do GeoConform sem resposta, o Seguinte não cria o IGT e mostra o erro junto da pergunta', () => {
      abrirElaboracaoNova();
      preencherEscolhas();

      fixture.componentInstance.seguinte();
      fixture.detectChanges();

      expect(instrumentoServiceMock.criarElaboracao).not.toHaveBeenCalled();
      expect(fixture.nativeElement.querySelector('#erro-comGeoconform').textContent.trim()).toBe('Indique se utiliza o GeoConform.');
    });

    it('vai direto para o formulário, como no Figma, sem a página da designação nem a Nova Dinâmica', () => {
      abrirElaboracaoNova();

      expect(instrumentoServiceMock.elaboracaoNova).toHaveBeenCalledWith(1);
      expect(fixture.componentInstance.passoAtual()).toBe(2);
      expect(fixture.nativeElement.querySelector('app-pagina-novo-igt')).toBeNull();
      expect(fixture.nativeElement.querySelector('app-pagina-nova-dinamica')).toBeNull();
      expect(etiquetasPassos()).toEqual(['Tipo IGT', 'Novo IGT', 'Formulário']);
      expect(fixture.nativeElement.querySelector('app-passos .passo-atual .passo-etiqueta').textContent.trim()).toBe('Formulário');
      expect(instrumentoServiceMock.criar).not.toHaveBeenCalled();
      expect(processoServiceMock.criar).not.toHaveBeenCalled();
    });

    it('o formulário pede NUT II, concelho, designação e tipo de ato, com o concelho da câmara já escolhido', () => {
      abrirElaboracaoNova();

      expect([...seletor('NUT II').options].map((opcao) => opcao.textContent?.trim())).toEqual([
        'Selecione…',
        'NORTE',
        'LISBOA E VALE DO TEJO',
      ]);
      expect(seletor('NUT II').value).toBe('');
      expect(seletor('Concelho').value).toBe('1111');
      expect([...seletor('Tipo de ato').options].map((opcao) => opcao.textContent?.trim())).toEqual(['Selecione…', 'Despacho']);
      expect(fixture.nativeElement.querySelector('app-processo-formulario-inicial input[name="designacao"]')).not.toBeNull();
      expect(fixture.nativeElement.querySelector('app-processo-formulario-inicial .geoconform')).not.toBeNull();
    });

    it('sem um concelho da câmara na lista, o concelho fica por escolher', () => {
      instrumentoServiceMock.elaboracaoNova.mockReturnValue(of(elaboracao({ concelhoEntidade: null })));
      const componente = fixture.componentInstance;
      componente.selecionarTipo('1');
      componente.paginaInicial.set('origem');
      componente.escolherOrigem('novo');
      componente.avancar();
      fixture.detectChanges();

      expect(seletor('Concelho').value).toBe('');
    });

    it('guardar sem as escolhas não cria nada e diz o que falta junto de cada campo', () => {
      abrirElaboracaoNova();

      fixture.componentInstance.guardarRascunho();
      fixture.detectChanges();

      expect(instrumentoServiceMock.criarElaboracao).not.toHaveBeenCalled();
      const texto = fixture.nativeElement.querySelector('app-processo-formulario-inicial').textContent;
      expect(texto).toContain('Escolha a NUT II.');
      expect(texto).toContain('Indique a designação do IGT.');
      expect(texto).toContain('Escolha o tipo de ato.');
      expect(texto).not.toContain('Escolha o concelho.');
    });

    it('o primeiro Guardar cria o IGT, a Elaboração e a tentativa de uma só vez e grava o formulário', () => {
      instrumentoServiceMock.criarElaboracao.mockReturnValue(
        of({ instrumentoId: 'instrumento-novo', dinamicaId: 'dinamica-nova', processo: processo() }),
      );
      abrirElaboracaoNova();
      preencherEscolhas();

      fixture.componentInstance.guardarRascunho();
      fixture.detectChanges();

      expect(instrumentoServiceMock.criarElaboracao).toHaveBeenCalledWith({
        tipoInstrumentoId: 1,
        nutIICodigo: 'LISBOA E VALE DO TEJO',
        concelho: '1111',
        designacao: 'PDM de Sintra',
        tipoAtoId: 5,
      });
      expect(instrumentoServiceMock.criar).not.toHaveBeenCalled();
      expect(instrumentoServiceMock.iniciarDinamica).not.toHaveBeenCalled();
      expect(processoServiceMock.criar).not.toHaveBeenCalled();
      expect(processoServiceMock.guardarRascunho).toHaveBeenCalledWith(processo().id, expect.objectContaining({ versao: 0 }));
      expect(processoServiceMock.obter).toHaveBeenCalledWith(processo().id);
      expect(fixture.componentInstance.instrumentoEscolhidoId()).toBe('instrumento-novo');
      // Criada a Elaboração, só fica o seletor do tipo de ato do cabeçalho, com o tipo escolhido.
      const seletores = fixture.nativeElement.querySelectorAll('app-processo-formulario-inicial app-seletor') as NodeListOf<HTMLElement>;
      expect(seletores).toHaveLength(1);
      expect(seletores[0].classList).toContain('tipo-ato');
      expect((seletores[0].querySelector('select') as HTMLSelectElement).value).toBe('5');
      expect(etiquetasPassos()).toEqual(['Tipo IGT', 'Novo IGT', 'Formulário']);
    });

    it('um erro do servidor num campo fica junto dele e o processo não fica criado', () => {
      instrumentoServiceMock.criarElaboracao.mockReturnValue(
        throwError(() => new ApiError('VALIDACAO', 'Payload inválido.', 't2', [{ path: 'concelho', erro: 'Concelho inexistente.' }])),
      );
      abrirElaboracaoNova();
      preencherEscolhas();

      fixture.componentInstance.guardarRascunho();
      fixture.detectChanges();

      expect(fixture.componentInstance.processoCriado()).toBeNull();
      expect(processoServiceMock.guardarRascunho).not.toHaveBeenCalled();
      expect(fixture.nativeElement.querySelector('app-processo-formulario-inicial').textContent).toContain('Concelho inexistente.');
    });

    it('com a localização obrigatória, a freguesia aparece antes de gravar e vai no primeiro Guardar', () => {
      const listaValores = TestBed.inject(ListaValoresService) as unknown as { opcoes: ReturnType<typeof vi.fn> };
      listaValores.opcoes.mockImplementation((tipo: string) =>
        of(tipo === 'FREGUESIA' ? [{ codigo: '111102', descricao: 'Algueirão-Mem Martins' }] : []),
      );
      instrumentoServiceMock.elaboracaoNova.mockReturnValue(of(elaboracao({ pedeLocalizacao: 'OBRIGATORIA' })));
      instrumentoServiceMock.criarElaboracao.mockReturnValue(
        of({ instrumentoId: 'instrumento-novo', dinamicaId: 'dinamica-nova', processo: processo() }),
      );
      const componente = fixture.componentInstance;
      componente.selecionarTipo('1');
      componente.paginaInicial.set('origem');
      componente.escolherOrigem('novo');
      componente.avancar();
      fixture.detectChanges();

      const grupo = fixture.nativeElement.querySelector('app-processo-formulario-inicial fieldset.localizacao') as HTMLFieldSetElement;
      expect(grupo.querySelector('legend')!.textContent).toContain('Localização (obrigatório)');
      expect(grupo.querySelector('.marca-obrigatoria')).not.toBeNull();
      const freguesia = grupo.querySelector('select') as HTMLSelectElement;
      freguesia.value = '111102';
      freguesia.dispatchEvent(new Event('change'));
      preencherEscolhas();

      componente.guardarRascunho();
      fixture.detectChanges();

      expect(processoServiceMock.guardarRascunho).toHaveBeenCalledWith(
        processo().id,
        expect.objectContaining({ localizacao: expect.objectContaining({ freguesia: '111102' }) }),
      );
    });

    it('a Elaboração não pergunta pelos elementos fundamentais afetados, antes nem depois de gravar', () => {
      instrumentoServiceMock.criarElaboracao.mockReturnValue(
        of({ instrumentoId: 'instrumento-novo', dinamicaId: 'dinamica-nova', processo: processo() }),
      );
      processoServiceMock.obter.mockReturnValue(of(processoDetalhe({ exigeElementosAfetados: false })));
      abrirElaboracaoNova();
      expect(fixture.nativeElement.querySelector('app-processo-formulario-inicial .elementos')).toBeNull();

      preencherEscolhas();
      fixture.componentInstance.guardarRascunho();
      fixture.detectChanges();

      expect(processoServiceMock.guardarRascunho).toHaveBeenCalledWith(
        processo().id,
        expect.objectContaining({ elementosAfetados: [] }),
      );
      expect(fixture.nativeElement.querySelector('app-processo-formulario-inicial .elementos')).toBeNull();
    });

    it('sem GeoConform, o Seguinte do formulário abre as Plantas e depois os documentos', () => {
      instrumentoServiceMock.criarElaboracao.mockReturnValue(
        of({ instrumentoId: 'instrumento-novo', dinamicaId: 'dinamica-nova', processo: processo() }),
      );
      processoServiceMock.obter.mockReturnValue(of(processoDetalhe({ exigeElementosAfetados: false, exigePlantasEMetadados: true })));
      abrirElaboracaoNova();
      preencherEscolhas();
      responderGeoconformNao();

      fixture.componentInstance.seguinte();
      fixture.detectChanges();

      expect(fixture.componentInstance.subpagina()).toBe('plantas');
      expect(fixture.nativeElement.querySelector('app-processo-plantas')).not.toBeNull();

      fixture.componentInstance.seguinte();
      fixture.detectChanges();

      expect(plantasServiceMock.gravarPlantas).not.toHaveBeenCalled();
      expect(fixture.componentInstance.subpagina()).toBe('documentos');
    });

    it('com GeoConform, o Seguinte do formulário vai direto aos documentos', () => {
      instrumentoServiceMock.criarElaboracao.mockReturnValue(
        of({ instrumentoId: 'instrumento-novo', dinamicaId: 'dinamica-nova', processo: processo() }),
      );
      processoServiceMock.obter.mockReturnValue(of(processoDetalhe({ exigeElementosAfetados: false, exigePlantasEMetadados: false })));
      abrirElaboracaoNova();
      preencherEscolhas();
      responderGeoconformNao();

      fixture.componentInstance.seguinte();
      fixture.detectChanges();

      expect(fixture.componentInstance.subpagina()).toBe('documentos');
      expect(fixture.nativeElement.querySelector('app-processo-plantas')).toBeNull();
    });

    it('o Voltar antes de gravar regressa à escolha novo ou existente', () => {
      abrirElaboracaoNova();

      fixture.componentInstance.voltarPasso();
      fixture.detectChanges();

      expect(fixture.componentInstance.passoAtual()).toBe(1);
      expect(fixture.componentInstance.paginaInicial()).toBe('origem');
      expect(fixture.componentInstance.elaboracaoNova()).toBeNull();
      expect(etiquetasPassos()).toEqual(['Tipo IGT', 'IGT Novo/Existente', 'Formulário']);
    });

    it('depois de gravado, o Voltar abre o detalhe do processo em vez de criar outro IGT', () => {
      const navegar = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
      instrumentoServiceMock.criarElaboracao.mockReturnValue(
        of({ instrumentoId: 'instrumento-novo', dinamicaId: 'dinamica-nova', processo: processo() }),
      );
      abrirElaboracaoNova();
      preencherEscolhas();
      fixture.componentInstance.guardarRascunho();
      fixture.detectChanges();

      fixture.componentInstance.voltarPasso();

      expect(navegar).toHaveBeenCalledWith(expect.stringContaining(processo().id));
    });
  });

  describe('Novo IGT', () => {
    function abrirNovoIgt(): void {
      const componente = fixture.componentInstance;
      componente.selecionarTipo('1');
      componente.paginaInicial.set('origem');
      componente.escolherOrigem('novo');
      componente.avancar();
      fixture.detectChanges();
    }

    it('abre a criação dentro do assistente, sem mudar de URL, com a entidade da sessão', () => {
      const navegar = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);

      abrirNovoIgt();

      expect(fixture.componentInstance.paginaInicial()).toBe('novo');
      expect(navegar).not.toHaveBeenCalled();
      expect(fixture.nativeElement.querySelector('app-pagina-novo-igt')).not.toBeNull();
      expect(fixture.nativeElement.textContent).toContain('Câmara Municipal de Sintra (500051062)');
      expect(etiquetasPassos()).toEqual(['Tipo IGT', 'IGT Novo/Existente', 'Formulário']);
      expect(instrumentoServiceMock.listarConcelhos).toHaveBeenCalledTimes(1);
      expect(fixture.nativeElement.querySelector('app-pagina-novo-igt app-seletor').textContent).toContain('Sintra');
      expect(fixture.nativeElement.querySelector('app-passos .passo-atual .passo-etiqueta').textContent.trim()).toBe('IGT Novo/Existente');
    });

    it('só deixa criar com a designação e o concelho preenchidos e o Voltar regressa à escolha novo ou existente', () => {
      abrirNovoIgt();
      expect(fixture.componentInstance.podeAvancar()).toBe(false);
      expect(fixture.componentInstance.rotuloAvancar()).toBe('Criar IGT');

      fixture.componentInstance.alterarDesignacaoNovoIgt('   ');
      expect(fixture.componentInstance.podeAvancar()).toBe(false);
      expect(fixture.componentInstance.dicaAvancar()).toBe('Indique a designação do IGT.');

      fixture.componentInstance.alterarDesignacaoNovoIgt('PDM de Odivelas');
      expect(fixture.componentInstance.podeAvancar()).toBe(false);
      expect(fixture.componentInstance.dicaAvancar()).toBe('Escolha o concelho do IGT.');

      fixture.componentInstance.alterarConcelhoNovoIgt('1111');
      expect(fixture.componentInstance.podeAvancar()).toBe(true);

      fixture.componentInstance.voltarPasso();
      expect(fixture.componentInstance.paginaInicial()).toBe('origem');
    });

    it('criado o IGT, segue direto para a Nova Dinâmica com a única dinâmica possível já escolhida', () => {
      const navegar = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
      abrirNovoIgt();
      fixture.componentInstance.alterarDesignacaoNovoIgt('  PDM de Odivelas  ');
      fixture.componentInstance.alterarConcelhoNovoIgt('1111');

      fixture.componentInstance.avancar();
      fixture.detectChanges();

      expect(instrumentoServiceMock.criar).toHaveBeenCalledWith({ tipoInstrumentoId: 1, designacao: 'PDM de Odivelas', concelhos: ['1111'] });
      expect(fixture.componentInstance.concelhoNovoIgt()).toBeNull();
      expect(fixture.componentInstance.paginaInicial()).toBe('nova');
      expect(fixture.componentInstance.instrumentoEscolhidoId()).toBe('instrumento-novo');
      expect(instrumentoServiceMock.disponivel).toHaveBeenCalledWith('instrumento-novo');
      expect(fixture.componentInstance.dinamicaNovaId()).toBe(103);
      expect(fixture.componentInstance.designacaoNovoIgt()).toBe('');
      expect(etiquetasPassos()).toEqual(['Tipo IGT', 'Novo IGT', 'Nova Dinâmica', 'Formulário']);
      expect(fixture.nativeElement.querySelector('app-passos .passo-atual .passo-etiqueta').textContent.trim()).toBe('Nova Dinâmica');
      expect(navegar).not.toHaveBeenCalled();
      expect(fixture.nativeElement.querySelector('.banner-sucesso-acao').textContent).toContain('IGT PDM de Odivelas criado com sucesso.');

      fixture.componentInstance.alterarDescritivoNovo('Elaboração do PDM');
      expect(fixture.componentInstance.podeAvancar()).toBe(true);
    });

    it('criado o IGT sem dinâmicas disponíveis, fica na lista de IGT com o novo escolhido', () => {
      instrumentoServiceMock.disponivel.mockReturnValue(of(disponibilidade({ dinamicasDisponiveis: [], dinamicasEmCurso: [] })));
      abrirNovoIgt();
      fixture.componentInstance.alterarDesignacaoNovoIgt('PDM de Odivelas');
      fixture.componentInstance.alterarConcelhoNovoIgt('1111');

      fixture.componentInstance.avancar();
      fixture.detectChanges();

      expect(fixture.componentInstance.paginaInicial()).toBe('igt');
      expect(fixture.componentInstance.viaNovoIgt()).toBe(false);
      expect(fixture.componentInstance.instrumentoEscolhidoId()).toBe('instrumento-novo');
      const titulos = [...fixture.nativeElement.querySelectorAll('app-lista-opcoes .opcao-titulo')].map((e: Element) => e.textContent);
      expect(titulos).toEqual(['PDM de Odivelas', 'PDM de Sintra']);
    });

    it('o Voltar da Nova Dinâmica de um IGT novo leva à Ação e repõe os passos de um IGT existente', () => {
      abrirNovoIgt();
      fixture.componentInstance.alterarDesignacaoNovoIgt('PDM de Odivelas');
      fixture.componentInstance.alterarConcelhoNovoIgt('1111');
      fixture.componentInstance.avancar();
      fixture.detectChanges();

      fixture.componentInstance.voltarPasso();
      fixture.detectChanges();

      expect(fixture.componentInstance.paginaInicial()).toBe('acao');
      expect(fixture.componentInstance.viaNovoIgt()).toBe(false);
      expect(etiquetasPassos()).toEqual(['Tipo IGT', 'IGT Existente', 'Ação', 'Formulário']);
    });

    it('se a criação falhar fica na página, com a designação escrita e o erro junto do campo', () => {
      instrumentoServiceMock.criar.mockReturnValue(
        throwError(() => new ApiError('VALIDACAO', 'Pedido inválido.', 't1', [{ path: 'designacao', erro: 'Já existe um IGT com esta designação.' }])),
      );
      abrirNovoIgt();
      fixture.componentInstance.alterarDesignacaoNovoIgt('PDM de Sintra');
      fixture.componentInstance.alterarConcelhoNovoIgt('1111');

      fixture.componentInstance.avancar();
      fixture.detectChanges();

      expect(fixture.componentInstance.paginaInicial()).toBe('novo');
      expect(fixture.componentInstance.designacaoNovoIgt()).toBe('PDM de Sintra');
      expect(fixture.componentInstance.aCriarIgt()).toBe(false);
      expect(fixture.componentInstance.erroDesignacao()).toBe('Já existe um IGT com esta designação.');
      expect(fixture.nativeElement.querySelector('app-pagina-novo-igt').textContent).toContain('Já existe um IGT com esta designação.');
    });

    it('sem permissão para criar IGT, a opção Novo IGT fica desativada e o Avançar explica porquê', () => {
      authMock.podeCriarInstrumento = () => false;
      const componente = fixture.componentInstance;
      componente.selecionarTipo('1');
      componente.paginaInicial.set('origem');
      componente.escolherOrigem('novo');
      fixture.detectChanges();

      expect(radios().map((radio) => radio.disabled)).toEqual([true, false]);
      expect(componente.podeAvancar()).toBe(false);
      expect(componente.dicaAvancar()).toBe('Só quem age por uma entidade pode criar um IGT.');
    });

    it('um interno da DGT, sem entidade de sessão, não cria IGT no assistente', () => {
      authMock.perfilInterno = () => 'MASTER';
      fixture.componentInstance.escolherOrigem('novo');

      expect(fixture.componentInstance.podeNovoIgt()).toBe(false);
    });
  });

  describe('o Avançar e o Seguinte', () => {
    it('explicam porque estão desativados, página a página', () => {
      const componente = fixture.componentInstance;
      expect(componente.dicaAvancar()).toBe('Escolha o tipo de IGT para continuar.');

      componente.selecionarTipo('1');
      componente.paginaInicial.set('origem');
      expect(componente.dicaAvancar()).toBe('Escolha se o IGT é novo ou existente.');

      componente.paginaInicial.set('igt');
      expect(componente.dicaAvancar()).toBe('Escolha um IGT para continuar.');
      componente.selecionarTipo('2');
      expect(componente.dicaAvancar()).toBe('Ainda não há IGT deste tipo: volte e escolha «Novo IGT».');

      componente.selecionarTipo('1');
      componente.selecionarInstrumento('instrumento-1');
      componente.paginaInicial.set('acao');
      expect(componente.dicaAvancar()).toBe('Escolha o que pretende fazer.');

      componente.escolherModo('continuar');
      expect(componente.dicaAvancar()).toBeNull();
    });

    it('um IGT sem dinâmicas disponíveis nem em curso diz que não há por onde continuar', () => {
      instrumentoServiceMock.disponivel.mockReturnValue(of(disponibilidade({ dinamicasDisponiveis: [], dinamicasEmCurso: [] })));
      const componente = fixture.componentInstance;
      componente.selecionarTipo('1');
      componente.selecionarInstrumento('instrumento-1');
      componente.paginaInicial.set('acao');

      expect(componente.dicaAvancar()).toBe('Este IGT não tem dinâmicas disponíveis nem em curso.');
    });

    it('a dica aparece no ecrã e desaparece quando se pode avançar', () => {
      expect(fixture.nativeElement.querySelector('.dica-avancar').textContent).toContain('Escolha o tipo de IGT');

      fixture.componentInstance.selecionarTipo('1');
      fixture.detectChanges();

      expect(fixture.nativeElement.querySelector('.dica-avancar')).toBeNull();
    });

    it('o Seguinte do topo e o Avançar fazem o mesmo e ficam ambos ativos ou ambos desativados', () => {
      const botoes = (): { seguinte: HTMLButtonElement; avancar: HTMLButtonElement } => {
        const topo = [...fixture.nativeElement.querySelectorAll('.navegacao-topo button')] as HTMLButtonElement[];
        return { seguinte: topo[1], avancar: fixture.nativeElement.querySelector('.acoes button') as HTMLButtonElement };
      };
      fixture.componentInstance.selecionarTipo('1');
      fixture.componentInstance.paginaInicial.set('origem');
      fixture.detectChanges();
      expect(botoes().seguinte.disabled).toBe(true);
      expect(botoes().avancar.disabled).toBe(true);

      radios()[1].click();
      fixture.detectChanges();
      expect(botoes().seguinte.disabled).toBe(false);
      expect(botoes().avancar.disabled).toBe(false);

      botoes().seguinte.click();
      fixture.detectChanges();
      expect(fixture.componentInstance.paginaInicial()).toBe('igt');
    });

    it('o Seguinte nunca cria o IGT nem inicia a Dinâmica: só o botão com o nome da ação o faz', () => {
      const seguinte = (): HTMLButtonElement => [...fixture.nativeElement.querySelectorAll('.navegacao-topo button')][1] as HTMLButtonElement;
      const componente = fixture.componentInstance;
      componente.selecionarTipo('1');
      componente.alterarDesignacaoNovoIgt('PDM de Odivelas');
      componente.alterarConcelhoNovoIgt('1111');
      componente.paginaInicial.set('novo');
      fixture.detectChanges();

      expect(componente.podeAvancar()).toBe(true);
      expect(seguinte().disabled).toBe(true);

      componente.selecionarInstrumento('instrumento-1');
      componente.selecionarDinamicaNova('103');
      componente.alterarDescritivoNovo('Alteração da planta');
      componente.paginaInicial.set('nova');
      fixture.detectChanges();

      expect(componente.podeAvancar()).toBe(true);
      expect(seguinte().disabled).toBe(true);
      expect(instrumentoServiceMock.criar).not.toHaveBeenCalled();
      expect(instrumentoServiceMock.iniciarDinamica).not.toHaveBeenCalled();
    });

    it('percorre o assistente só com cliques, do Tipo IGT à criação do processo da Decisão de início', () => {
      const avancar = (): void => {
        (fixture.nativeElement.querySelector('.acoes button') as HTMLButtonElement).click();
        fixture.detectChanges();
      };
      radios()[0].click();
      fixture.detectChanges();
      avancar();
      expect(fixture.componentInstance.paginaInicial()).toBe('origem');

      radios()[1].click();
      fixture.detectChanges();
      avancar();
      expect(fixture.componentInstance.paginaInicial()).toBe('igt');

      radios()[0].click();
      fixture.detectChanges();
      avancar();
      expect(fixture.componentInstance.paginaInicial()).toBe('acao');

      radios()[1].click();
      fixture.detectChanges();
      avancar();
      expect(fixture.componentInstance.paginaInicial()).toBe('continuar');

      radios()[0].click();
      fixture.detectChanges();
      expect(fixture.nativeElement.querySelector('app-seletor')).toBeNull();
      avancar();

      // Decisão de início: abre logo o processo, como ao criar a Dinâmica de raiz; o tipo de ato fica para o cabeçalho.
      expect(fixture.componentInstance.confirmacaoAberta()).toBe(false);
      expect(processoServiceMock.criar).toHaveBeenCalledWith(expect.any(String), { instrumentoProcedimentoConfigId: 214 });
    });
  });

  it('o Avançar da Nova Dinâmica é Iniciar Dinâmica e só fica ativo com tipo e descritivo', () => {
    const componente = fixture.componentInstance;
    componente.selecionarTipo('1');
    componente.selecionarInstrumento('instrumento-1');
    componente.paginaInicial.set('nova');

    expect(componente.rotuloAvancar()).toBe('Iniciar Dinâmica');
    expect(componente.podeAvancar()).toBe(false);

    componente.selecionarDinamicaNova('103');
    componente.alterarDescritivoNovo('Alteração da planta');
    expect(componente.podeAvancar()).toBe(true);
  });

  it('o Avançar da Dinâmica a continuar passa a Retomar rascunho quando o Procedimento já tem tentativa', () => {
    completarPasso1ComRetoma();
    fixture.componentInstance.paginaInicial.set('continuar');

    expect(fixture.componentInstance.rotuloAvancar()).toBe('Retomar rascunho');
    expect(fixture.componentInstance.podeAvancar()).toBe(true);
  });

  it('carrega o catálogo de tipos e os planos ao abrir', () => {
    expect(instrumentoServiceMock.listarTipos).toHaveBeenCalled();
    expect(instrumentoServiceMock.listar).toHaveBeenCalledWith(0, 100);
    expect(fixture.componentInstance.tipos()).toEqual(tipos);
  });

  it('filtra os planos pelo tipo de IGT escolhido', () => {
    fixture.componentInstance.selecionarTipo('1');
    expect(fixture.componentInstance.instrumentosDoTipo()).toHaveLength(1);

    fixture.componentInstance.selecionarTipo('2');
    expect(fixture.componentInstance.instrumentosDoTipo()).toHaveLength(0);
  });

  it('pede a disponibilidade assim que o plano é escolhido', () => {
    fixture.componentInstance.selecionarTipo('1');
    fixture.componentInstance.selecionarInstrumento('instrumento-1');

    expect(instrumentoServiceMock.disponivel).toHaveBeenCalledWith('instrumento-1');
    expect(fixture.componentInstance.disponibilidade()?.dinamicasEmCurso).toHaveLength(1);
  });

  function abrirIgtExistente(): void {
    fixture.componentInstance.selecionarTipo('1');
    fixture.componentInstance.selecionarInstrumento('instrumento-1');
    fixture.componentInstance.paginaInicial.set('igt');
    fixture.detectChanges();
  }

  it('não mostra a entidade da sessão nos IGT', () => {
    abrirIgtExistente();

    expect(fixture.nativeElement.querySelector('app-pagina-igt-existente').textContent).not.toContain('Câmara Municipal de Sintra');
  });

  it('ao escolher um IGT não mostra o histórico de dinâmicas do Instrumento', () => {
    abrirIgtExistente();

    expect(fixture.nativeElement.querySelector('.historico')).toBeNull();
    expect(fixture.nativeElement.textContent).not.toContain('Histórico de dinâmicas');
  });

  it('dá a cada Dinâmica o último Procedimento publicado', () => {
    const publicado = procedimentoPainel().processos;
    instrumentoServiceMock.painel.mockReturnValue(
      of(
        painel([
          dinamicaPainel({
            id: 'dinamica-1',
            estado: 'EM_CURSO',
            procedimentos: [
              procedimentoPainel({ tipoProcedimento: 'DECISAO_INICIO', nomeProcedimento: 'Decisão de início', ordem: 1, processos: publicado }),
              procedimentoPainel({ tipoProcedimento: 'ABERTURA_DISCUSSAO_PUBLICA', nomeProcedimento: 'Abertura da discussão pública', ordem: 2, processos: publicado }),
              procedimentoPainel({ tipoProcedimento: 'ALTERACAO', nomeProcedimento: 'Publicação da Alteração', ordem: 3, processos: [{ ...publicado[0], estado: 'EM_SUBMISSAO' }] }),
            ],
          }),
        ]),
      ),
    );
    fixture.componentInstance.selecionarTipo('1');
    fixture.componentInstance.selecionarInstrumento('instrumento-1');
    fixture.componentInstance.paginaInicial.set('continuar');
    fixture.detectChanges();

    expect(fixture.componentInstance.ultimosConcluidos()).toEqual({ 'dinamica-1': 'Abertura da discussão pública' });
    expect(fixture.nativeElement.querySelector('.opcao-nota')?.textContent).toContain('Abertura da discussão pública');
  });

  it('ordena as Dinâmicas em aberto por dataInicio descendente', () => {
    const antiga = dinamicaEmCurso({ dinamicaId: 'antiga', tipoDinamica: 'ANTIGA', nomeDinamica: 'Dinâmica antiga', dataInicio: '2026-01-10T10:00:00Z' });
    const recente = dinamicaEmCurso({ dinamicaId: 'recente', tipoDinamica: 'RECENTE', nomeDinamica: 'Dinâmica recente', dataInicio: '2026-06-10T10:00:00Z' });
    instrumentoServiceMock.disponivel.mockReturnValue(of(disponibilidade({ dinamicasEmCurso: [antiga, recente] })));

    fixture.componentInstance.selecionarTipo('1');
    fixture.componentInstance.selecionarInstrumento('instrumento-1');

    expect(fixture.componentInstance.dinamicasAbertas().map((d) => d.dinamicaId)).toEqual(['recente', 'antiga']);
  });

  describe('Prorrogação numa Dinâmica terminada', () => {
    const prorrogacao = procedimento({
      instrumentoProcedimentoConfigId: 902,
      tipoProcedimento: 'PRORROGACAO_MP',
      nomeProcedimento: 'Prorrogação das medidas preventivas',
      ordem: 2,
      obrigatorio: false,
    });
    const medidasTerminadas = dinamicaEmCurso({
      dinamicaId: 'mp-terminada',
      tipoDinamica: 'MEDIDAS_PREVENTIVAS',
      nomeDinamica: 'Medidas preventivas',
      descritivo: 'Medidas preventivas da zona norte',
      estado: 'TERMINADA',
      dataInicio: '2026-11-01T10:00:00Z',
      procedimentosDisponiveis: [prorrogacao],
    });

    beforeEach(() => {
      instrumentoServiceMock.disponivel.mockReturnValue(
        of(disponibilidade({ dinamicasEmCurso: [dinamicaEmCurso(), medidasTerminadas] })),
      );
      instrumentoServiceMock.painel.mockReturnValue(
        of(
          painel([
            dinamicaPainel(),
            dinamicaPainel({
              id: 'mp-terminada',
              tipoDinamica: 'MEDIDAS_PREVENTIVAS',
              nomeDinamica: 'Medidas preventivas',
              descritivo: 'Medidas preventivas da zona norte',
              dataConclusao: '2026-11-02T10:00:00Z',
            }),
          ]),
        ),
      );
    });

    function abrirContinuar(): void {
      fixture.componentInstance.selecionarTipo('1');
      fixture.componentInstance.selecionarInstrumento('instrumento-1');
      fixture.componentInstance.escolherModo('continuar');
      fixture.componentInstance.paginaInicial.set('continuar');
      fixture.detectChanges();
    }

    it('lista a Dinâmica terminada em Continuar Existente com o selo e, escolhida, a Prorrogação e o alerta informativo', () => {
      abrirContinuar();

      expect(fixture.componentInstance.dinamicasAbertas().map((d) => d.dinamicaId)).toEqual(['mp-terminada', 'dinamica-1']);
      const opcao: HTMLElement = [...fixture.nativeElement.querySelectorAll('app-lista-opcoes label.opcao')].find((e: Element) =>
        e.textContent?.includes('Medidas preventivas'),
      ) as HTMLElement;
      expect(opcao.querySelector('.opcao-selo')?.textContent?.trim()).toBe('Terminada');
      expect(opcao.textContent).toContain('Publicada; pode ser prorrogada.');
      expect(fixture.nativeElement.querySelector('.alerta-terminada')).toBeNull();

      fixture.componentInstance.selecionarDinamica('mp-terminada');
      fixture.detectChanges();

      expect(fixture.componentInstance.procedimentoEscolhido()?.instrumentoProcedimentoConfigId).toBe(902);
      expect(fixture.nativeElement.textContent).toContain('Prorrogação das medidas preventivas');
      const alerta: HTMLElement = fixture.nativeElement.querySelector('app-alerta.alerta-terminada');
      expect(alerta.getAttribute('role')).toBe('status');
      expect(alerta.textContent).toContain('Esta dinâmica já foi publicada. A prorrogação não a reabre: a dinâmica continua terminada.');
    });

    it('sem alerta numa Dinâmica em curso', () => {
      abrirContinuar();
      fixture.componentInstance.selecionarDinamica('dinamica-1');
      fixture.detectChanges();

      expect(fixture.nativeElement.querySelector('.alerta-terminada')).toBeNull();
    });

    it('um 409 ESTADO_INVALIDO na criação mostra a mensagem do envelope e relê a disponibilidade', () => {
      abrirContinuar();
      fixture.componentInstance.selecionarDinamica('mp-terminada');
      fixture.componentInstance.pedirConfirmacaoAvancar();
      processoServiceMock.criar.mockReturnValue(
        throwError(() => new ApiError('ESTADO_INVALIDO', 'Esta dinâmica está terminada.', 't409', null)),
      );
      instrumentoServiceMock.disponivel.mockClear();
      instrumentoServiceMock.disponivel.mockReturnValue(of(disponibilidade({ dinamicasEmCurso: [dinamicaEmCurso()] })));

      fixture.componentInstance.criarProcesso();
      fixture.detectChanges();

      expect(processoServiceMock.criar).toHaveBeenCalledWith('mp-terminada', { instrumentoProcedimentoConfigId: 902 });
      expect(instrumentoServiceMock.disponivel).toHaveBeenCalledWith('instrumento-1');
      expect(fixture.nativeElement.querySelector('app-error-banner')?.textContent).toContain('Esta dinâmica está terminada.');
      expect(fixture.componentInstance.dinamicasAbertas().map((d) => d.dinamicaId)).toEqual(['dinamica-1']);
      expect(fixture.componentInstance.passoAtual()).toBe(1);
    });
  });

  describe('caducidade', () => {
    const texto = (elemento: Element | null | undefined) => elemento?.textContent?.replace(/\s+/g, ' ').trim() ?? '';

    beforeEach(() => {
      vi.useFakeTimers({ toFake: ['Date'] });
      vi.setSystemTime(new Date('2026-11-06T10:00:00Z'));
    });

    afterEach(() => {
      vi.useRealTimers();
    });

    function abrirContinuar(): void {
      fixture.componentInstance.selecionarTipo('1');
      fixture.componentInstance.selecionarInstrumento('instrumento-1');
      fixture.componentInstance.paginaInicial.set('continuar');
      fixture.detectChanges();
    }

    function opcaoDinamica(): HTMLElement {
      return fixture.nativeElement.querySelector('app-pagina-continuar-dinamica label.opcao');
    }

    it('a Dinâmica em curso a 10 dias da data mostra "Caduca em", o selo e, ao escolhê-la, o alerta informativo', () => {
      instrumentoServiceMock.disponivel.mockReturnValue(of(disponibilidade({ dinamicasEmCurso: [dinamicaEmCurso({ dataCaducidade: '2026-11-16' })] })));
      abrirContinuar();

      const prazo = opcaoDinamica().querySelector('.opcao-prazo') as HTMLElement;
      expect(texto(prazo)).toBe('Caduca em 16/11/2026 Faltam 10 dias');
      expect(prazo.querySelector('time')?.getAttribute('datetime')).toBe('2026-11-16');
      expect(prazo.querySelector('app-selo')?.className).toContain('selo-aviso');
      expect(fixture.nativeElement.querySelector('app-pagina-continuar-dinamica app-alerta.alerta-info')).toBeNull();

      (opcaoDinamica().querySelector('input[type="radio"]') as HTMLInputElement).click();
      fixture.detectChanges();

      expect(fixture.componentInstance.dinamicaEscolhida()?.dinamicaId).toBe('dinamica-1');
      const alerta = fixture.nativeElement.querySelector('app-pagina-continuar-dinamica app-alerta.alerta-info') as HTMLElement;
      expect(texto(alerta)).toBe('A Dinâmica caduca em 16/11/2026. A caducidade não impede a submissão.');
      expect(alerta.getAttribute('role')).toBe('status');
    });

    it('a Dinâmica em curso longe da data mostra "Caduca em" sem selo nem alerta', () => {
      instrumentoServiceMock.disponivel.mockReturnValue(of(disponibilidade({ dinamicasEmCurso: [dinamicaEmCurso({ dataCaducidade: '2027-02-06' })] })));
      abrirContinuar();
      fixture.componentInstance.selecionarDinamica('dinamica-1');
      fixture.detectChanges();

      expect(texto(opcaoDinamica().querySelector('.opcao-prazo'))).toBe('Caduca em 06/02/2027');
      expect(opcaoDinamica().querySelector('.opcao-prazo app-selo')).toBeNull();
      expect(fixture.nativeElement.querySelector('app-pagina-continuar-dinamica app-alerta.alerta-info')).toBeNull();
    });

    it('a Dinâmica em curso sem data de caducidade não mostra nenhuma linha de caducidade', () => {
      abrirContinuar();
      fixture.componentInstance.selecionarDinamica('dinamica-1');
      fixture.detectChanges();

      expect(opcaoDinamica().querySelector('.opcao-prazo')).toBeNull();
      expect(texto(fixture.nativeElement.querySelector('app-pagina-continuar-dinamica'))).not.toContain('Caduca');
    });

  });

  describe('com o painel em erro', () => {
    let consoleErro: ReturnType<typeof vi.spyOn>;

    beforeEach(() => {
      consoleErro = vi.spyOn(console, 'error').mockImplementation(() => undefined);
      instrumentoServiceMock.painel.mockReturnValue(throwError(() => new ApiError('ERRO_INTERNO', 'Falhou', 'trace-1', null)));
    });

    afterEach(() => {
      consoleErro.mockRestore();
    });

    it('não mostra o banner e deixa avançar', () => {
      abrirIgtExistente();

      expect(fixture.componentInstance.disponibilidade()).not.toBeNull();
      expect(fixture.componentInstance.painel()).toBeNull();
      expect(fixture.nativeElement.querySelector('app-error-banner')).toBeNull();
      expect(fixture.componentInstance.podeAvancar()).toBe(true);
    });

    it('deixa o log na consola de desenvolvimento só com o código e o traceId', () => {
      abrirIgtExistente();

      expect(consoleErro).toHaveBeenCalledTimes(1);
      expect(consoleErro).toHaveBeenCalledWith('Painel do Instrumento indisponível no assistente', { codigo: 'ERRO_INTERNO', traceId: 'trace-1' });
    });

    it('perde a nota "Último concluído", mas ainda se escolhem a Dinâmica e o Procedimento', () => {
      fixture.componentInstance.selecionarTipo('1');
      fixture.componentInstance.selecionarInstrumento('instrumento-1');
      fixture.componentInstance.paginaInicial.set('continuar');
      fixture.detectChanges();

      expect(fixture.nativeElement.querySelector('.opcao-nota')).toBeNull();

      (radios()[0] as HTMLInputElement).click();
      fixture.detectChanges();
      expect(fixture.componentInstance.dinamicaEscolhida()?.dinamicaId).toBe('dinamica-1');

      const procedimento = radios().find((radio) => radio.value === '214') as HTMLInputElement;
      procedimento.click();
      fixture.detectChanges();
      expect(fixture.componentInstance.procedimentoEscolhido()?.instrumentoProcedimentoConfigId).toBe(214);

      expect(fixture.componentInstance.podeAvancar()).toBe(true);
    });
  });

  it('um erro em /disponivel continua a mostrar o banner', () => {
    instrumentoServiceMock.disponivel.mockReturnValue(throwError(() => new ApiError('ERRO_INTERNO', 'Falhou', 'trace-2', null)));
    abrirIgtExistente();

    expect(fixture.componentInstance.disponibilidade()).toBeNull();
    expect(fixture.nativeElement.querySelector('app-error-banner')).not.toBeNull();
  });

  it('separa as Dinâmicas caducadas das em aberto: só as abertas se podem continuar', () => {
    const caducada = dinamicaEmCurso({ dinamicaId: 'caducada', estado: 'CADUCADA', dataCaducidade: '2026-02-11' });
    instrumentoServiceMock.disponivel.mockReturnValue(of(disponibilidade({ dinamicasEmCurso: [dinamicaEmCurso(), caducada] })));

    fixture.componentInstance.selecionarTipo('1');
    fixture.componentInstance.selecionarInstrumento('instrumento-1');
    fixture.componentInstance.selecionarDinamica('caducada');

    expect(fixture.componentInstance.dinamicasAbertas().map((d) => d.dinamicaId)).toEqual(['dinamica-1']);
    expect(fixture.componentInstance.dinamicasCaducadas().map((d) => d.dinamicaId)).toEqual(['caducada']);
    expect(fixture.componentInstance.dinamicaEscolhida()).toBeNull();
  });

  it('distingue as Dinâmicas em aberto do mesmo tipo pelo descritivo', () => {
    const regulamento = dinamicaEmCurso({ dinamicaId: 'd1', tipoDinamica: 'ALTERACAO', nomeDinamica: 'Alteração', descritivo: 'Alteração do regulamento' });
    const planta = dinamicaEmCurso({ dinamicaId: 'd2', tipoDinamica: 'ALTERACAO', nomeDinamica: 'Alteração', descritivo: 'Alteração da planta' });
    instrumentoServiceMock.disponivel.mockReturnValue(of(disponibilidade({ dinamicasEmCurso: [regulamento, planta] })));

    fixture.componentInstance.selecionarTipo('1');
    fixture.componentInstance.selecionarInstrumento('instrumento-1');
    fixture.componentInstance.paginaInicial.set('continuar');
    fixture.detectChanges();

    const descritivos = [...fixture.nativeElement.querySelectorAll('.opcao-citacao')].map((e: Element) => e.textContent?.trim());
    expect(descritivos).toEqual(expect.arrayContaining(['“Alteração do regulamento”', '“Alteração da planta”']));
  });

  it('só deixa avançar com Dinâmica e Procedimento escolhidos, sem pedir o tipo de ato', () => {
    fixture.componentInstance.selecionarTipo('1');
    fixture.componentInstance.selecionarInstrumento('instrumento-1');
    fixture.componentInstance.escolherModo('continuar');
    expect(fixture.componentInstance.podeAvancarDoPasso1()).toBe(false);

    fixture.componentInstance.selecionarDinamica('dinamica-1');
    expect(fixture.componentInstance.procedimentoEscolhido()?.instrumentoProcedimentoConfigId).toBe(214);
    expect(fixture.componentInstance.podeAvancarDoPasso1()).toBe(true);
  });

  it('o Avançar abre o modal de confirmação sem criar nada', () => {
    completarPasso1();
    fixture.componentInstance.pedirConfirmacaoAvancar();

    expect(fixture.componentInstance.confirmacaoAberta()).toBe(true);
    expect(processoServiceMock.criar).not.toHaveBeenCalled();
    expect(fixture.componentInstance.passoAtual()).toBe(1);
  });

  /** Deixá-los ficar criaria a tentativa no Procedimento errado. */
  it('ao trocar de Dinâmica, repõe o procedimento seguinte', () => {
    const doisProcedimentos = [procedimento(), procedimento({ instrumentoProcedimentoConfigId: 215, tipoProcedimento: 'ELABORACAO', nomeProcedimento: 'Elaboração', ordem: 2 })];
    instrumentoServiceMock.disponivel.mockReturnValue(
      of(disponibilidade({ dinamicasEmCurso: [dinamicaEmCurso({ procedimentosDisponiveis: doisProcedimentos })] })),
    );
    completarPasso1();
    fixture.componentInstance.selecionarDinamica('dinamica-1');

    expect(fixture.componentInstance.procedimentoEscolhido()?.instrumentoProcedimentoConfigId).toBe(215);
  });

  describe('procedimento seguinte pré-selecionado', () => {
    const discussaoPublica = (overrides: Partial<ProcedimentoDisponivel> = {}): ProcedimentoDisponivel =>
      procedimento({
        instrumentoProcedimentoConfigId: 215,
        tipoProcedimento: 'ABERTURA_DISCUSSAO_PUBLICA',
        nomeProcedimento: 'Abertura do período de discussão pública',
        ordem: 2,
        ...overrides,
      });
    const publicacao = (): ProcedimentoDisponivel =>
      procedimento({ instrumentoProcedimentoConfigId: 216, tipoProcedimento: 'PUBLICACAO_ALTERACAO', nomeProcedimento: 'Publicação da Alteração', ordem: 3 });
    const retificacao = (): ProcedimentoDisponivel =>
      procedimento({ instrumentoProcedimentoConfigId: 230, tipoProcedimento: 'RETIFICACAO', nomeProcedimento: 'Retificação do ato e anexos', ordem: 9, atoPontual: true });

    function escolherDinamicaCom(procedimentos: ProcedimentoDisponivel[]): void {
      instrumentoServiceMock.disponivel.mockReturnValue(
        of(disponibilidade({ dinamicasEmCurso: [dinamicaEmCurso({ nomeDinamica: 'Alteração', procedimentosDisponiveis: procedimentos })] })),
      );
      fixture.componentInstance.selecionarTipo('1');
      fixture.componentInstance.selecionarInstrumento('instrumento-1');
      fixture.componentInstance.escolherModo('continuar');
      fixture.componentInstance.paginaInicial.set('continuar');
      fixture.detectChanges();
      fixture.componentInstance.selecionarDinamica('dinamica-1');
      fixture.detectChanges();
    }

    const grupos = (): HTMLElement[] => [...fixture.nativeElement.querySelectorAll('app-pagina-continuar-dinamica app-lista-opcoes')].slice(1);
    const marcado = (grupo: HTMLElement): string | undefined =>
      grupo.querySelector<HTMLInputElement>('input[type="radio"]:checked')?.closest('label')?.textContent?.trim();

    it('com a Decisão de início publicada, fica escolhida a Abertura do período de discussão pública', () => {
      escolherDinamicaCom([discussaoPublica()]);

      expect(fixture.componentInstance.procedimentoEscolhido()?.instrumentoProcedimentoConfigId).toBe(215);
      expect(marcado(grupos()[0])).toContain('Abertura do período de discussão pública');
    });

    it('com a Discussão Pública e a Publicação, fica a Publicação e a Discussão Pública continua no mesmo grupo', () => {
      escolherDinamicaCom([discussaoPublica(), publicacao()]);

      expect(fixture.componentInstance.procedimentoEscolhido()?.instrumentoProcedimentoConfigId).toBe(216);
      const [seguinte] = grupos();
      expect(seguinte.querySelector('legend')!.textContent).toContain('Procedimento a iniciar');
      expect(seguinte.textContent).toContain('Abertura do período de discussão pública');
      expect(marcado(seguinte)).toContain('Publicação da Alteração');
    });

    it('um Procedimento com tentativa em curso fica escolhido e aparece o aviso de rascunho', () => {
      escolherDinamicaCom([discussaoPublica({ tentativaEmCurso: { processoId: 'processo-1', numeroTentativa: 2, estado: 'EM_SUBMISSAO' } }), publicacao()]);

      expect(fixture.componentInstance.procedimentoEscolhido()?.instrumentoProcedimentoConfigId).toBe(215);
      expect(fixture.nativeElement.textContent).toContain('Este Procedimento já tem um rascunho gravado (tentativa n.º 2)');
    });

    it('um ato pontual de ordem maior não é escolhido e fica em Outros atos e anexos', () => {
      escolherDinamicaCom([discussaoPublica(), retificacao()]);

      expect(fixture.componentInstance.procedimentoEscolhido()?.instrumentoProcedimentoConfigId).toBe(215);
      const [seguinte, outros] = grupos();
      expect(seguinte.textContent).not.toContain('Retificação do ato e anexos');
      expect(outros.querySelector('legend')!.textContent).toContain('Outros atos e anexos');
      expect(outros.textContent).toContain('Retificação do ato e anexos');
      expect(marcado(outros)).toBeUndefined();
    });

    it('só com atos pontuais, nada fica escolhido e o grupo do procedimento seguinte diz que não há', () => {
      escolherDinamicaCom([retificacao()]);

      expect(fixture.componentInstance.procedimentoEscolhido()).toBeNull();
      expect(fixture.nativeElement.textContent).toContain('Sem procedimento seguinte disponível nesta Dinâmica.');
    });

    it('trocar o Procedimento no seletor faz prevalecer a escolha do utilizador', () => {
      escolherDinamicaCom([discussaoPublica(), publicacao()]);

      const opcaoDiscussao = [...grupos()[0].querySelectorAll('label')].find((label) => label.textContent?.includes('discussão pública'))!;
      opcaoDiscussao.querySelector<HTMLInputElement>('input[type="radio"]')!.click();
      fixture.detectChanges();

      expect(fixture.componentInstance.procedimentoEscolhido()?.instrumentoProcedimentoConfigId).toBe(215);
      expect(marcado(grupos()[0])).toContain('Abertura do período de discussão pública');
    });

    it('a pré-seleção não tira o foco de onde está', () => {
      instrumentoServiceMock.disponivel.mockReturnValue(
        of(disponibilidade({ dinamicasEmCurso: [dinamicaEmCurso({ procedimentosDisponiveis: [discussaoPublica(), publicacao()] })] })),
      );
      fixture.componentInstance.selecionarTipo('1');
      fixture.componentInstance.selecionarInstrumento('instrumento-1');
      fixture.componentInstance.escolherModo('continuar');
      fixture.componentInstance.paginaInicial.set('continuar');
      fixture.detectChanges();
      const opcaoDinamica = fixture.nativeElement.querySelector('app-pagina-continuar-dinamica input[type="radio"]') as HTMLInputElement;
      opcaoDinamica.focus();

      opcaoDinamica.click();
      fixture.detectChanges();

      expect(fixture.componentInstance.procedimentoEscolhido()?.instrumentoProcedimentoConfigId).toBe(216);
      expect(document.activeElement).toBe(opcaoDinamica);
    });
  });

  describe('outros atos e anexos com o ato publicado a que respeita', () => {
    const DECISAO_INICIO: AtoReferivel = {
      processoId: 'processo-di',
      numeroProcesso: '00005',
      tipoProcedimento: 'DECISAO_INICIO',
      numeroDr: '210/2026',
      dataPublicacaoDr: '2026-11-03',
    };
    const DISCUSSAO_PUBLICA: AtoReferivel = {
      processoId: 'processo-dp',
      numeroProcesso: '00006',
      tipoProcedimento: 'ABERTURA_DISCUSSAO_PUBLICA',
      numeroDr: '215/2026',
      dataPublicacaoDr: '2026-11-10',
    };
    const NOMES_ATOS_PONTUAIS: [number, string, string][] = [
      [231, 'ALTERACAO_ATO_ANEXOS', 'Alteração do ato e anexos'],
      [232, 'RETIFICACAO_ATO_ANEXOS', 'Retificação do ato e anexos'],
      [233, 'REVOGACAO_ATO', 'Revogação do ato'],
      [234, 'PRORROGACAO_ATO', 'Prorrogação do ato'],
      [235, 'DECLARACAO_NULIDADE_JUDICIAL', 'Declaração de nulidade por decisão judicial'],
    ];
    const seguinte = (): ProcedimentoDisponivel =>
      procedimento({ instrumentoProcedimentoConfigId: 215, tipoProcedimento: 'ABERTURA_DISCUSSAO_PUBLICA', nomeProcedimento: 'Abertura do período de discussão pública', ordem: 2 });
    const atosPontuais = (atosReferiveis: AtoReferivel[] = [DECISAO_INICIO]): ProcedimentoDisponivel[] =>
      NOMES_ATOS_PONTUAIS.map(([id, tipo, nome], indice) =>
        procedimento({ instrumentoProcedimentoConfigId: id, tipoProcedimento: tipo, nomeProcedimento: nome, ordem: 20 + indice, atoPontual: true, atosReferiveis }),
      );

    function abrirContinuarCom(procedimentos: ProcedimentoDisponivel[]): void {
      instrumentoServiceMock.disponivel.mockReturnValue(
        of(disponibilidade({ dinamicasEmCurso: [dinamicaEmCurso({ nomeDinamica: 'Alteração', procedimentosDisponiveis: procedimentos })] })),
      );
      fixture.componentInstance.selecionarTipo('1');
      fixture.componentInstance.selecionarInstrumento('instrumento-1');
      fixture.componentInstance.escolherModo('continuar');
      fixture.componentInstance.paginaInicial.set('continuar');
      fixture.componentInstance.selecionarDinamica('dinamica-1');
      fixture.detectChanges();
    }

    const pagina = (): HTMLElement => fixture.nativeElement.querySelector('app-pagina-continuar-dinamica');
    const grupoOutros = (): HTMLElement | undefined =>
      [...pagina().querySelectorAll<HTMLElement>('app-lista-opcoes')].find((grupo) => grupo.querySelector('legend')?.textContent?.includes('Outros atos e anexos'));
    const campoAtoReferido = (): HTMLElement | null => fixture.nativeElement.querySelector('app-campo-ato-referido');
    const seletorAtoReferido = (): HTMLSelectElement => campoAtoReferido()!.querySelector('select')!;
    const botaoAvancar = (): HTMLButtonElement =>
      [...(fixture.nativeElement as HTMLElement).querySelectorAll<HTMLButtonElement>('.acoes button')].find((botao) => botao.textContent?.includes('Avançar'))!;

    function escolherOutroAto(nome: string): void {
      const opcao = [...grupoOutros()!.querySelectorAll('label')].find((label) => label.textContent?.includes(nome))!;
      opcao.querySelector<HTMLInputElement>('input[type="radio"]')!.click();
      fixture.detectChanges();
    }

    /** O `ngModel` escreve o valor no `<select>` só depois de a promessa resolver. */
    async function estabilizar(): Promise<void> {
      await fixture.whenStable();
      fixture.detectChanges();
    }

    function escolherAtoReferido(processoId: string): void {
      seletorAtoReferido().value = processoId;
      seletorAtoReferido().dispatchEvent(new Event('change'));
      fixture.detectChanges();
    }

    it('mostra o procedimento seguinte e, à parte, o grupo «Outros atos e anexos» com os 5 tipos', () => {
      abrirContinuarCom([seguinte(), ...atosPontuais()]);

      const [, seguinteGrupo] = [...pagina().querySelectorAll<HTMLElement>('app-lista-opcoes')];
      expect(seguinteGrupo.querySelector('legend')!.textContent).toContain('Procedimento a iniciar');
      expect([...seguinteGrupo.querySelectorAll('.opcao-titulo')].map((titulo) => titulo.textContent?.trim())).toEqual([
        'Abertura do período de discussão pública',
      ]);
      expect([...grupoOutros()!.querySelectorAll('.opcao-titulo')].map((titulo) => titulo.textContent?.trim())).toEqual(
        NOMES_ATOS_PONTUAIS.map(([, , nome]) => nome),
      );
      expect(campoAtoReferido()).toBeNull();
    });

    it('sem atos pontuais na Dinâmica, o grupo «Outros atos e anexos» não aparece', () => {
      abrirContinuarCom([seguinte()]);

      expect(grupoOutros()).toBeUndefined();
      expect(pagina().textContent).not.toContain('Outros atos e anexos');
    });

    it('com a Decisão de início e a Discussão pública publicadas, o ato a que respeita tem as duas opções com n.º de processo, DR e data', () => {
      abrirContinuarCom([seguinte(), ...atosPontuais([DECISAO_INICIO, DISCUSSAO_PUBLICA])]);

      escolherOutroAto('Retificação do ato e anexos');

      const rotulo = campoAtoReferido()!.querySelector('label')!;
      expect(rotulo.textContent?.replace(/\s+/g, ' ').trim()).toBe('Ato publicado a que respeita *');
      expect(seletorAtoReferido().required).toBe(true);
      const opcoes = [...seletorAtoReferido().options].slice(1).map((opcao) => opcao.textContent?.trim());
      expect(opcoes).toEqual([
        'Decisão de início — processo n.º 00005 — DR 210/2026, 03/11/2026',
        'Abertura do período de discussão pública — processo n.º 00006 — DR 215/2026, 10/11/2026',
      ]);
      expect(seletorAtoReferido().value).toBe('');
      expect(fixture.componentInstance.atoReferido()).toBeNull();
    });

    it('com um só ato publicado, vem pré-escolhido', async () => {
      abrirContinuarCom([seguinte(), ...atosPontuais([DECISAO_INICIO])]);

      escolherOutroAto('Retificação do ato e anexos');
      await estabilizar();

      expect(seletorAtoReferido().value).toBe('processo-di');
      expect(fixture.componentInstance.atoReferido()?.processoId).toBe('processo-di');
      expect(fixture.componentInstance.podeAvancar()).toBe(true);
    });

    it('o ato a que respeita tem a ajuda ligada por aria-describedby', () => {
      abrirContinuarCom([seguinte(), ...atosPontuais([DECISAO_INICIO, DISCUSSAO_PUBLICA])]);

      escolherOutroAto('Revogação do ato');

      const ajuda = campoAtoReferido()!.querySelector('.seletor-ajuda')!;
      expect(ajuda.textContent?.trim()).toBe('Os outros atos e anexos só se aplicam a uma Decisão de início ou a uma Discussão pública já publicadas.');
      expect(seletorAtoReferido().getAttribute('aria-describedby')).toContain(ajuda.id);
    });

    it('um ato pontual sem ato a que respeita deixa o Avançar desativado e diz porquê', () => {
      abrirContinuarCom([seguinte(), ...atosPontuais([DECISAO_INICIO, DISCUSSAO_PUBLICA])]);

      escolherOutroAto('Retificação do ato e anexos');

      expect(fixture.componentInstance.podeAvancar()).toBe(false);
      expect(botaoAvancar().disabled).toBe(true);
      expect(fixture.nativeElement.querySelector('.dica-avancar')?.textContent?.trim()).toBe('Escolha o ato publicado a que respeita.');

      escolherAtoReferido('processo-dp');

      expect(fixture.componentInstance.podeAvancar()).toBe(true);
      expect(botaoAvancar().disabled).toBe(false);
    });

    it('confirmado o pop-up, o POST leva o processoReferidoId e o passo 2 mostra «Ato a que respeita»', () => {
      abrirContinuarCom([seguinte(), ...atosPontuais([DECISAO_INICIO, DISCUSSAO_PUBLICA])]);
      escolherOutroAto('Retificação do ato e anexos');
      escolherAtoReferido('processo-di');
      processoServiceMock.criar.mockReturnValue(of(processo({ id: 'processo-ret', numeroProcesso: '00007', instrumentoProcedimentoConfigId: 232, processoReferidoId: 'processo-di' })));
      processoServiceMock.obter.mockReturnValue(
        of(
          processoDetalhe({
            id: 'processo-ret',
            numeroProcesso: '00007',
            atoPontual: true,
            processoReferidoId: 'processo-di',
            numeroProcessoReferido: '00005',
            tipoProcedimentoReferido: 'DECISAO_INICIO',
          }),
        ),
      );

      fixture.componentInstance.avancar();
      fixture.detectChanges();
      expect(fixture.componentInstance.mensagemConfirmacao()).toBe(
        'Vai criar «Retificação do ato e anexos» sobre o processo n.º 00005 (Decisão de início). ' +
          'Pode contactar a DGT por um pedido de apoio se detetar alguma anomalia.',
      );
      fixture.componentInstance.confirmarPedido();
      fixture.detectChanges();

      expect(processoServiceMock.criar).toHaveBeenCalledWith('dinamica-1', { instrumentoProcedimentoConfigId: 232, processoReferidoId: 'processo-di' });
      expect(fixture.componentInstance.passoAtual()).toBe(2);
      const linha = fixture.nativeElement.querySelector('dl.identificacao .ato-referido');
      expect(linha?.querySelector('dt')?.textContent?.trim()).toBe('Ato a que respeita');
      expect(linha?.querySelector('dd')?.textContent?.trim()).toBe('processo n.º 00005 (Decisão de início)');
    });

    it('o procedimento seguinte cria-se sem processoReferidoId', () => {
      abrirContinuarCom([seguinte(), ...atosPontuais()]);

      fixture.componentInstance.avancar();
      fixture.componentInstance.confirmarPedido();

      expect(processoServiceMock.criar).toHaveBeenCalledWith('dinamica-1', { instrumentoProcedimentoConfigId: 215 });
      expect(Object.keys(processoServiceMock.criar.mock.calls[0][1])).not.toContain('processoReferidoId');
    });

    it('trocar de grupo limpa a escolha anterior e o ato a que respeita, e anuncia-o sem mover o foco', () => {
      abrirContinuarCom([seguinte(), ...atosPontuais([DECISAO_INICIO, DISCUSSAO_PUBLICA])]);
      expect(fixture.componentInstance.procedimentoEscolhido()?.instrumentoProcedimentoConfigId).toBe(215);

      escolherOutroAto('Retificação do ato e anexos');
      const anuncio = pagina().querySelector('[aria-live="polite"]')!;
      expect(anuncio.textContent?.trim()).toBe('O procedimento seguinte deixou de estar escolhido.');
      escolherAtoReferido('processo-dp');
      expect(fixture.componentInstance.atoReferido()?.processoId).toBe('processo-dp');

      const radioSeguinte = [...pagina().querySelectorAll<HTMLInputElement>('app-lista-opcoes input[type="radio"]')].find(
        (radio) => radio.value === '215',
      )!;
      radioSeguinte.focus();
      radioSeguinte.click();
      fixture.detectChanges();

      expect(fixture.componentInstance.procedimentoEscolhido()?.instrumentoProcedimentoConfigId).toBe(215);
      expect(fixture.componentInstance.atoReferidoId()).toBeNull();
      expect(campoAtoReferido()).toBeNull();
      expect(grupoOutros()!.querySelector<HTMLInputElement>('input[type="radio"]:checked')).toBeNull();
      expect(anuncio.textContent?.trim()).toBe('O ato de «Outros atos e anexos» e o ato a que respeita deixaram de estar escolhidos.');
      expect(document.activeElement).toBe(radioSeguinte);

      escolherOutroAto('Retificação do ato e anexos');
      expect(seletorAtoReferido().value).toBe('');
      expect(fixture.componentInstance.atoReferido()).toBeNull();
    });

    it('mudar de um ato pontual para outro também limpa o ato a que respeita', async () => {
      abrirContinuarCom([seguinte(), ...atosPontuais([DECISAO_INICIO, DISCUSSAO_PUBLICA])]);
      escolherOutroAto('Retificação do ato e anexos');
      escolherAtoReferido('processo-di');
      await estabilizar();

      escolherOutroAto('Revogação do ato');
      await estabilizar();

      expect(fixture.componentInstance.atoReferido()).toBeNull();
      expect(seletorAtoReferido().value).toBe('');
    });

    it('um 400 REFERENCIA_INVALIDA mostra o alerta «Ato referido inválido.» e relê o /disponivel', () => {
      abrirContinuarCom([seguinte(), ...atosPontuais([DECISAO_INICIO, DISCUSSAO_PUBLICA])]);
      escolherOutroAto('Retificação do ato e anexos');
      escolherAtoReferido('processo-dp');
      processoServiceMock.criar.mockReturnValue(
        throwError(
          () => new ApiError('REFERENCIA_INVALIDA', 'Ato referido inválido.', 't400', [{ path: 'processoReferidoId', erro: 'Ato referido inválido.' }]),
        ),
      );
      instrumentoServiceMock.disponivel.mockClear();
      instrumentoServiceMock.disponivel.mockReturnValue(
        of(disponibilidade({ dinamicasEmCurso: [dinamicaEmCurso({ procedimentosDisponiveis: [seguinte(), ...atosPontuais([DECISAO_INICIO])] })] })),
      );

      fixture.componentInstance.avancar();
      fixture.componentInstance.confirmarPedido();
      fixture.detectChanges();

      expect(instrumentoServiceMock.disponivel).toHaveBeenCalledWith('instrumento-1');
      expect(fixture.componentInstance.confirmacaoAberta()).toBe(false);
      expect(fixture.componentInstance.passoAtual()).toBe(1);
      const alerta = fixture.nativeElement.querySelector('app-alerta.erro-ato-referido') as HTMLElement;
      expect(alerta.getAttribute('role')).toBe('alert');
      expect(alerta.textContent?.trim()).toBe('Ato referido inválido.');
      expect(fixture.nativeElement.querySelector('app-error-banner')).toBeNull();
      expect(fixture.componentInstance.procedimentoEscolhido()?.instrumentoProcedimentoConfigId).toBe(232);
      expect(fixture.componentInstance.opcoesAtoReferido().map((ato) => ato.processoId)).toEqual(['processo-di']);

      escolherAtoReferido('processo-di');
      expect(fixture.nativeElement.querySelector('app-alerta.erro-ato-referido')).toBeNull();
    });

    it('um ato pontual com rascunho em curso retoma-o sem pedir o ato a que respeita', () => {
      const comRascunho = atosPontuais([DECISAO_INICIO, DISCUSSAO_PUBLICA]).map((item) =>
        item.instrumentoProcedimentoConfigId === 232 ? { ...item, tentativaEmCurso: { processoId: 'processo-ret', numeroTentativa: 1, estado: 'EM_SUBMISSAO' } } : item,
      );
      abrirContinuarCom([seguinte(), ...comRascunho]);

      expect(grupoOutros()!.textContent).toContain('Retomar rascunho n.º 1');
      escolherOutroAto('Retificação do ato e anexos');

      expect(campoAtoReferido()).toBeNull();
      expect(fixture.componentInstance.podeAvancar()).toBe(true);
      expect(fixture.componentInstance.rotuloAvancar()).toBe('Retomar rascunho');
      processoServiceMock.obter.mockReturnValue(of(processoDetalhe({ id: 'processo-ret', atoPontual: true, processoReferidoId: 'processo-di', numeroProcessoReferido: '00005', tipoProcedimentoReferido: 'DECISAO_INICIO' })));
      fixture.componentInstance.avancar();
      fixture.detectChanges();

      expect(processoServiceMock.criar).not.toHaveBeenCalled();
      expect(processoServiceMock.obter).toHaveBeenCalledWith('processo-ret');
      expect(fixture.nativeElement.querySelector('dl.identificacao .ato-referido dd')?.textContent?.trim()).toBe('processo n.º 00005 (Decisão de início)');
    });

    it('o resumo do passo 3 mostra «Ato a que respeita» lido do detalhe', () => {
      processoServiceMock.obter.mockReturnValue(
        of(processoDetalhe({ atoPontual: true, processoReferidoId: 'processo-di', numeroProcessoReferido: '00005', tipoProcedimentoReferido: 'DECISAO_INICIO' })),
      );
      fixture.componentInstance.procedimentoEscolhido.set(procedimento({ tentativaEmCurso: { processoId: 'processo-1', numeroTentativa: 1, estado: 'EM_SUBMISSAO' } }));
      fixture.componentInstance.retomarTentativa();
      fixture.componentInstance.passoAtual.set(3);
      fixture.detectChanges();

      const linha = fixture.nativeElement.querySelector('app-pagina-resumo .ato-referido');
      expect(linha?.querySelector('dt')?.textContent?.trim()).toBe('Ato a que respeita');
      expect(linha?.querySelector('dd')?.textContent?.trim()).toBe('processo n.º 00005 (Decisão de início)');
    });

    it('com um ato pontual escolhido de fora (painel), não cria sem o ato a que respeita', () => {
      abrirContinuarCom([seguinte(), ...atosPontuais([DECISAO_INICIO, DISCUSSAO_PUBLICA])]);
      fixture.componentInstance.selecionarProcedimento('233');
      fixture.detectChanges();

      expect(campoAtoReferido()).not.toBeNull();
      fixture.componentInstance.criarProcesso();
      expect(processoServiceMock.criar).not.toHaveBeenCalled();
    });
  });

  it('o pop-up do Avançar nomeia o procedimento e a Dinâmica e menciona o pedido de apoio', () => {
    completarPasso1();
    fixture.componentInstance.paginaInicial.set('continuar');
    fixture.componentInstance.pedirConfirmacaoAvancar();
    fixture.detectChanges();

    const mensagem = fixture.componentInstance.mensagemConfirmacao();
    expect(mensagem).toContain('Pretende avançar com o pedido «Decisão de início — Alteração simplificada (art. 123.º)»?');
    expect(mensagem).toContain('Confirme que é esta a dinâmica decidida na deliberação.');
    expect(mensagem).toContain('contacte a DGT através de um pedido de apoio');
    const dialogo = fixture.nativeElement.querySelector('app-confirmacao-modal [role="dialog"]') as HTMLElement;
    const descricao = dialogo.querySelector(`#${dialogo.getAttribute('aria-describedby')}`);
    expect(descricao?.textContent).toContain('Pretende avançar com o pedido «Decisão de início — Alteração simplificada (art. 123.º)»?');
  });

  function confirmarPedidoDaElaboracao(procedimentoEscolhido: ProcedimentoDisponivel): string {
    instrumentoServiceMock.disponivel.mockReturnValue(
      of(disponibilidade({ dinamicasEmCurso: [dinamicaEmCurso({ tipoDinamica: 'ELABORACAO', nomeDinamica: 'Elaboração', procedimentosDisponiveis: [procedimentoEscolhido] })] })),
    );
    completarPasso1();
    fixture.componentInstance.paginaInicial.set('continuar');
    fixture.componentInstance.pedirConfirmacaoAvancar();
    fixture.detectChanges();
    const dialogo = fixture.nativeElement.querySelector('app-confirmacao-modal [role="dialog"]') as HTMLElement;
    return dialogo.querySelector(`#${dialogo.getAttribute('aria-describedby')}`)!.textContent!;
  }

  it('no procedimento final, com o nome da própria dinâmica, o pedido diz «Elaboração (procedimento final)»', () => {
    const texto = confirmarPedidoDaElaboracao(procedimento({ tipoProcedimento: 'ELABORACAO', nomeProcedimento: 'Elaboração' }));

    expect(texto).toContain('Pretende avançar com o pedido «Elaboração (procedimento final)»?');
    expect(texto).not.toContain('Elaboração — Elaboração');
  });

  it('num procedimento com outro nome, o pedido continua «procedimento — dinâmica»', () => {
    const texto = confirmarPedidoDaElaboracao(
      procedimento({ tipoProcedimento: 'DISCUSSAO_PUBLICA', nomeProcedimento: 'Abertura do período de discussão pública' }),
    );

    expect(texto).toContain('Pretende avançar com o pedido «Abertura do período de discussão pública — Elaboração»?');
    expect(texto).not.toContain('procedimento final');
  });

  it('um 409 PROCESSO_EM_CURSO na criação fecha o pop-up, mostra o erro no banner e relê a disponibilidade', () => {
    completarPasso1();
    fixture.componentInstance.pedirConfirmacaoAvancar();
    processoServiceMock.criar.mockReturnValue(
      throwError(() => new ApiError('PROCESSO_EM_CURSO', 'Já existe um processo em curso nesta dinâmica.', 't409', null)),
    );
    instrumentoServiceMock.disponivel.mockClear();

    fixture.componentInstance.criarProcesso();
    fixture.detectChanges();

    expect(fixture.componentInstance.confirmacaoAberta()).toBe(false);
    expect(instrumentoServiceMock.disponivel).toHaveBeenCalledTimes(1);
    expect(instrumentoServiceMock.disponivel).toHaveBeenCalledWith('instrumento-1');
    expect(fixture.componentInstance.erro()?.codigo).toBe('PROCESSO_EM_CURSO');
    expect(fixture.nativeElement.querySelector('app-error-banner')?.textContent).toContain('Já existe um processo em curso nesta dinâmica.');
    expect(fixture.componentInstance.dinamicaEscolhida()?.dinamicaId).toBe('dinamica-1');
    expect(fixture.componentInstance.passoAtual()).toBe(1);
  });

  it('um erro da criação que não é 409 mostra o banner sem reler a disponibilidade', () => {
    completarPasso1();
    processoServiceMock.criar.mockReturnValue(throwError(() => new ApiError('TIPO_ATO_NAO_AUTORIZADO', 'Tipo de ato não autorizado.', 't403', null)));
    instrumentoServiceMock.disponivel.mockClear();

    fixture.componentInstance.criarProcesso();
    fixture.detectChanges();

    expect(instrumentoServiceMock.disponivel).not.toHaveBeenCalled();
    expect(fixture.componentInstance.erro()?.codigo).toBe('TIPO_ATO_NAO_AUTORIZADO');
  });

  it('escolher um dos outros atos e anexos desmarca o procedimento seguinte', () => {
    const fora = [
      procedimento({ instrumentoProcedimentoConfigId: 215, tipoProcedimento: 'ABERTURA_DISCUSSAO_PUBLICA', nomeProcedimento: 'Abertura da discussão pública', ordem: 2 }),
      procedimento({ instrumentoProcedimentoConfigId: 216, tipoProcedimento: 'RETIFICACAO', nomeProcedimento: 'Retificação de atos e anexos', ordem: 5, atoPontual: true }),
    ];
    instrumentoServiceMock.disponivel.mockReturnValue(of(disponibilidade({ dinamicasEmCurso: [dinamicaEmCurso({ procedimentosDisponiveis: fora })] })));
    fixture.componentInstance.selecionarTipo('1');
    fixture.componentInstance.selecionarInstrumento('instrumento-1');
    fixture.componentInstance.paginaInicial.set('continuar');
    fixture.componentInstance.selecionarDinamica('dinamica-1');
    fixture.detectChanges();

    const outros = [...fixture.nativeElement.querySelectorAll('app-lista-opcoes')].at(-1);
    outros.querySelector('input[type="radio"]').click();
    fixture.detectChanges();

    expect(fixture.componentInstance.procedimentoEscolhido()?.instrumentoProcedimentoConfigId).toBe(216);
    const seguinte = [...fixture.nativeElement.querySelectorAll('app-lista-opcoes')].at(-2);
    expect(seguinte.querySelector('input[type="radio"]').checked).toBe(false);
  });

  it('com um só Procedimento disponível, escolher a Dinâmica seleciona-o, e também o único Tipo de Ato', () => {
    fixture.componentInstance.selecionarTipo('1');
    fixture.componentInstance.selecionarInstrumento('instrumento-1');
    fixture.componentInstance.escolherModo('continuar');
    fixture.componentInstance.selecionarDinamica('dinamica-1');

    expect(fixture.componentInstance.procedimentoEscolhido()?.instrumentoProcedimentoConfigId).toBe(214);
  });

  it('iniciar uma Dinâmica nova recarrega a disponibilidade e segue pelo ramo continuar', () => {
    fixture.componentInstance.selecionarTipo('1');
    fixture.componentInstance.selecionarInstrumento('instrumento-1');
    fixture.componentInstance.escolherModo('nova');
    fixture.componentInstance.selecionarDinamicaNova('103');
    fixture.componentInstance.alterarDescritivoNovo('  Alteração da planta  ');
    instrumentoServiceMock.disponivel.mockReturnValue(
      of(disponibilidade({ dinamicasEmCurso: [dinamicaEmCurso(), dinamicaEmCurso({ dinamicaId: 'dinamica-2' })] })),
    );

    fixture.componentInstance.iniciarDinamica();

    expect(instrumentoServiceMock.iniciarDinamica).toHaveBeenCalledWith('instrumento-1', {
      instrumentoDinamicaConfigId: 103,
      descritivo: 'Alteração da planta',
    });
    expect(fixture.componentInstance.descritivoNovo()).toBe('');
    expect(fixture.componentInstance.modo()).toBe('continuar');
    expect(fixture.componentInstance.paginaInicial()).toBe('continuar');
    expect(fixture.componentInstance.viaNovaDinamica()).toBe(true);
    expect(fixture.componentInstance.dinamicaEscolhida()?.dinamicaId).toBe('dinamica-2');
  });

  it('não inicia uma Dinâmica nova sem descritivo', () => {
    fixture.componentInstance.selecionarTipo('1');
    fixture.componentInstance.selecionarInstrumento('instrumento-1');
    fixture.componentInstance.escolherModo('nova');
    fixture.componentInstance.selecionarDinamicaNova('103');
    fixture.componentInstance.alterarDescritivoNovo('   ');

    fixture.componentInstance.iniciarDinamica();

    expect(instrumentoServiceMock.iniciarDinamica).not.toHaveBeenCalled();
    expect(fixture.componentInstance.modo()).toBe('nova');
  });

  // --- retomar tentativa ---

  function completarPasso1ComRetoma(): void {
    const procedimentoRetomavel = procedimento({
      tentativaEmCurso: { processoId: 'processo-1', numeroTentativa: 2, estado: 'EM_SUBMISSAO' },
    });
    instrumentoServiceMock.disponivel.mockReturnValue(
      of(disponibilidade({ dinamicasEmCurso: [dinamicaEmCurso({ procedimentosDisponiveis: [procedimentoRetomavel] })] })),
    );
    const componente = fixture.componentInstance;
    componente.selecionarTipo('1');
    componente.selecionarInstrumento('instrumento-1');
    componente.escolherModo('continuar');
    componente.selecionarDinamica('dinamica-1');
    componente.selecionarProcedimento('214');
    fixture.detectChanges();
  }

  it('mostra a marca de retoma no Procedimento e um aviso, sem pedir o tipo de ato', () => {
    completarPasso1ComRetoma();
    fixture.componentInstance.paginaInicial.set('continuar');
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Retomar rascunho n.º 2');
    expect(fixture.nativeElement.textContent).toContain('tentativa n.º 2');
    expect(fixture.nativeElement.querySelector('app-seletor')).toBeNull();
  });

  it('Retomar rascunho vai buscar o Processo existente e abre-o no passo 2, sem criar tentativa nova', () => {
    completarPasso1ComRetoma();

    fixture.componentInstance.retomarTentativa();

    expect(processoServiceMock.obter).toHaveBeenCalledWith('processo-1');
    expect(processoServiceMock.criar).not.toHaveBeenCalled();
    expect(fixture.componentInstance.passoAtual()).toBe(2);
  });

  it('o Resumo mostra a designação do Instrumento e Concelho "—", sem ler o dadosFormulario', () => {
    completarPasso1ComRetoma();
    processoServiceMock.obter.mockReturnValue(
      of(processoDetalhe({ dadosFormulario: '{"designacao":"Alteração retomada","concelho":"Sintra"}' })),
    );
    fixture.componentInstance.retomarTentativa();
    fixture.detectChanges();
    fixture.componentInstance.avancarParaPublicar();
    fixture.detectChanges();

    const resumo = fixture.nativeElement.querySelector('app-pagina-resumo') as HTMLElement;
    const linhas = new Map(
      Array.from(resumo.querySelectorAll('.dados > div')).map((linha) => [
        linha.querySelector('dt')!.textContent!.trim(),
        linha.querySelector('dd')!.textContent!.trim(),
      ]),
    );
    expect(linhas.get('Designação')).toBe('PDM de Sintra');
    expect(linhas.get('Concelho')).toBe('—');
    expect(resumo.textContent).not.toContain('Alteração retomada');
  });

  it('mostra o erro do backend e fica no passo 1 quando retomar falha', () => {
    completarPasso1ComRetoma();
    processoServiceMock.obter.mockReturnValue(throwError(() => new ApiError('NAO_ENCONTRADO', 'Processo não encontrado.', 't1', null)));

    fixture.componentInstance.retomarTentativa();

    expect(fixture.componentInstance.passoAtual()).toBe(1);
    expect(fixture.componentInstance.erro()?.codigo).toBe('NAO_ENCONTRADO');
  });

  /** A resposta do `PUT /dados-formulario` não traz `tipoAtoNome`: funde-se, não se substitui. */
  it('guardar o rascunho de uma tentativa retomada mantém os campos do detalhe', () => {
    completarPasso1ComRetoma();
    processoServiceMock.obter.mockReturnValue(of(processoDetalhe({ tipoAtoNome: 'Aviso n.º 12/2026' })));
    fixture.componentInstance.retomarTentativa();
    fixture.detectChanges();

    fixture.componentInstance.guardarRascunho();

    expect(processoServiceMock.guardarRascunho).toHaveBeenCalledTimes(1);
    expect(fixture.componentInstance.nomeTipoAtoGravado()).toBe('Aviso n.º 12/2026');
  });

  /** O tipo de ato da tentativa retomada é o gravado no processo, lido do detalhe. */
  it('mantém o tipo de ato da tentativa retomada até ao resumo, mesmo depois de gravar', () => {
    completarPasso1ComRetoma();
    processoServiceMock.obter.mockReturnValue(of(processoDetalhe({ tipoAtoNome: 'Aviso n.º 12/2026' })));
    fixture.componentInstance.retomarTentativa();
    fixture.detectChanges();

    expect(fixture.componentInstance.nomeTipoAtoGravado()).toBe('Aviso n.º 12/2026');

    fixture.componentInstance.avancarParaPublicar();
    fixture.detectChanges();

    expect(fixture.componentInstance.passoAtual()).toBe(3);
    expect(fixture.componentInstance.nomeTipoAtoGravado()).toBe('Aviso n.º 12/2026');
  });

  // --- passo 2 ---

  it('confirmar cria a tentativa e passa ao passo 2, sem formulário gerado do schema', () => {
    completarPasso1();
    fixture.componentInstance.criarProcesso();
    fixture.detectChanges();

    expect(processoServiceMock.criar).toHaveBeenCalledWith('dinamica-1', { instrumentoProcedimentoConfigId: 214 });
    expect(fixture.componentInstance.passoAtual()).toBe(2);
    expect(fixture.nativeElement.querySelector('app-processo-formulario')).toBeNull();
    expect(fixture.nativeElement.querySelector('app-processo-formulario-inicial')).not.toBeNull();
  });

  it('no passo 2 o cabeçalho pede o tipo de ato, com as opções do detalhe, e o rascunho leva-o', () => {
    processoServiceMock.obter.mockReturnValue(of(processoDetalhe({ tipoAtoId: null, tipoAtoNome: null })));
    completarPasso1();
    fixture.componentInstance.criarProcesso();
    fixture.detectChanges();

    const select = fixture.nativeElement.querySelector('app-processo-formulario-inicial app-seletor.tipo-ato select') as HTMLSelectElement;
    expect(select.required).toBe(true);
    expect([...select.options].map((opcao) => opcao.textContent?.trim())).toEqual(['Selecionar...', 'Despacho']);
    select.value = '5';
    select.dispatchEvent(new Event('change'));
    fixture.componentInstance.guardarRascunho();

    expect(processoServiceMock.guardarRascunho).toHaveBeenCalledWith('processo-1', expect.objectContaining({ tipoAtoId: 5 }));
  });

  it('com um Programa escolhido no passo 1, o cartão do passo 2 mostra a Entidade instrutora sem recarregar', () => {
    const componente = fixture.componentInstance;
    componente.tipos.update((tipos) => tipos.map((tipo) => (tipo.id === 1 ? { ...tipo, categoria: 'PROGRAMA' as const } : tipo)));
    completarPasso1();
    expect(componente.categoriaInstrumento()).toBe('PROGRAMA');

    componente.passoAtual.set(2);
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('app-processo-formulario-inicial input[formcontrolname="entidadeInstrutora"]')).not.toBeNull();
  });

  it('com um Plano escolhido no passo 1, o cartão do passo 2 não mostra a Entidade instrutora', () => {
    completarPasso1();
    fixture.componentInstance.passoAtual.set(2);
    fixture.detectChanges();

    expect(fixture.componentInstance.categoriaInstrumento()).toBe('PLANO');
    expect(fixture.nativeElement.querySelector('app-processo-formulario-inicial')).not.toBeNull();
    expect(fixture.nativeElement.querySelector('input[formcontrolname="entidadeInstrutora"]')).toBeNull();
  });

  it('depois da primeira gravação, o bloco "Nº Processo SAIGT" mostra o número devolvido, como lista de definição', () => {
    processoServiceMock.criar.mockReturnValue(of(processo({ numeroProcesso: '00042' })));
    completarPasso1();
    fixture.componentInstance.criarProcesso();
    fixture.detectChanges();

    const bloco = fixture.nativeElement.querySelector('dl.identificacao') as HTMLElement;
    expect(bloco.querySelector('dt')!.textContent!.trim()).toBe('Nº Processo SAIGT');
    expect(bloco.querySelector('dd')!.textContent!.trim()).toBe('00042');
  });

  it('antes da primeira gravação, o bloco "Nº Processo SAIGT" não aparece', () => {
    completarPasso1();
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.identificacao')).toBeNull();

    fixture.componentInstance.passoAtual.set(2);
    fixture.detectChanges();

    expect(fixture.componentInstance.processoCriado()).toBeNull();
    expect(fixture.nativeElement.querySelector('.identificacao')).toBeNull();
  });

  it('o bloco "Nº Processo SAIGT" mostra traço quando o número vem vazio', () => {
    processoServiceMock.criar.mockReturnValue(of(processo({ numeroProcesso: '' })));
    completarPasso1();
    fixture.componentInstance.criarProcesso();
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('.identificacao dd')!.textContent!.trim()).toBe('—');
  });

  it('o Resumo fica fora do cartão do passo, com os Dados do processo do Figma e o concelho do processo', () => {
    processoServiceMock.criar.mockReturnValue(of(processo({ numeroProcesso: '00042' })));
    processoServiceMock.obter.mockReturnValue(
      of(processoDetalhe({ numeroProcesso: '00042', territorio: { concelhos: [{ codigo: '1312', nome: 'Porto' }], nutsII: [] } })),
    );
    completarPasso1();
    fixture.componentInstance.criarProcesso();
    fixture.detectChanges();
    fixture.componentInstance.avancarParaPublicar();
    fixture.detectChanges();

    const resumo = fixture.nativeElement.querySelector('app-pagina-resumo') as HTMLElement;
    expect(resumo.closest('app-cartao.cartao-passo')).toBeNull();
    const rotulos = Array.from(resumo.querySelectorAll('.dados dt')).map((dt) => dt.textContent!.trim());
    expect(rotulos).toEqual(['Designação', 'Tipo de instrumento', 'Tipo de procedimento', 'Concelho', 'Destino', 'Submissão do processo n.º']);
    const concelho = Array.from(resumo.querySelectorAll('.dados dt')).find((dt) => dt.textContent!.trim() === 'Concelho');
    expect(concelho!.nextElementSibling!.textContent!.trim()).toBe('Porto');
    expect(resumo.textContent).not.toContain('Parte do DR');
    const nota = resumo.querySelector('.nota')!.textContent!.replace(/\s+/g, ' ');
    expect(nota).toContain('Para finalizar a instrução, assinale a caixa de confirmação e selecione «Submeter».');
    expect(nota).toContain('Se ainda não quer finalizar, saia desta página: o processo fica guardado em «Continuar Processo».');
    expect(nota).not.toContain('#Submeter');
    expect(nota).not.toContain('#voltar ao Menu');
  });

  function submissaoNoResumo(): string {
    const resumo = fixture.nativeElement.querySelector('app-pagina-resumo') as HTMLElement;
    const submissao = Array.from(resumo.querySelectorAll('.dados dt')).find((dt) => dt.textContent!.trim() === 'Submissão do processo n.º');
    return submissao!.nextElementSibling!.textContent!.trim();
  }

  it('num processo por submeter, o Resumo mostra «Submissão do processo n.º 1»', () => {
    processoServiceMock.obter.mockReturnValue(of(processoDetalhe({ submissao: 0 })));
    completarPasso1();
    fixture.componentInstance.criarProcesso();
    fixture.detectChanges();
    fixture.componentInstance.avancarParaPublicar();
    fixture.detectChanges();

    expect(submissaoNoResumo()).toBe('1');
  });

  it('numa segunda Discussão Pública por submeter, o Resumo mostra 1 e não a ocorrência', () => {
    processoServiceMock.criar.mockReturnValue(of(processo({ ocorrencia: 2 })));
    processoServiceMock.guardarRascunho.mockReturnValue(of(processo({ ocorrencia: 2, versao: 1 })));
    processoServiceMock.obter.mockReturnValue(of(processoDetalhe({ ocorrencia: 2, submissao: 0 })));
    completarPasso1();
    fixture.componentInstance.criarProcesso();
    fixture.detectChanges();
    fixture.componentInstance.avancarParaPublicar();
    fixture.detectChanges();

    expect(submissaoNoResumo()).toBe('1');
  });

  it('depois de uma recusa da DGT, o Resumo da correção mostra «Submissão do processo n.º 2»', () => {
    processoServiceMock.obter.mockReturnValue(of(processoDetalhe({ submissao: 1 })));
    completarPasso1();
    fixture.componentInstance.criarProcesso();
    fixture.detectChanges();
    fixture.componentInstance.avancarParaPublicar();
    fixture.detectChanges();

    expect(submissaoNoResumo()).toBe('2');
  });

  /** 2.ª série pede norma legal e tipo de informação; a 1.ª pede suplemento. */
  it('mostra os campos fixos da série do Procedimento', () => {
    completarPasso1();
    fixture.componentInstance.criarProcesso();
    fixture.detectChanges();

    const texto = fixture.nativeElement.textContent as string;
    expect(texto).toContain('Norma legal');
    expect(texto).not.toContain('Publicar suplemento');
  });

  it('logo após criar o processo, antes de gravar, o cartão mostra a NUT II e o Concelho do Instrumento', () => {
    processoServiceMock.obter.mockReturnValue(
      of(processoDetalhe({
        territorio: {
          concelhos: [{ codigo: '1105', nome: 'Cascais' }, { codigo: '1111', nome: 'Sintra' }],
          nutsII: [{ codigo: 'AML', nome: 'Área Metropolitana de Lisboa' }],
        },
      })),
    );
    completarPasso1();
    fixture.componentInstance.criarProcesso();
    fixture.detectChanges();

    expect(processoServiceMock.obter).toHaveBeenCalledWith('processo-1');
    const contexto = fixture.nativeElement.querySelector('app-processo-formulario-inicial dl.contexto')!.textContent as string;
    expect(contexto).toContain('Área Metropolitana de Lisboa');
    expect(contexto).toContain('Cascais, Sintra');
    expect(processoServiceMock.guardarRascunho).not.toHaveBeenCalled();
  });

  it('logo após criar, um Procedimento que pede a Fundamentação RJIGT mostra as opções e o Guardar leva o código marcado', () => {
    processoServiceMock.obter.mockReturnValue(
      of(processoDetalhe({
        pedeFundamentacaoRjigt: true,
        opcoesFundamentacaoRjigt: [{ codigo: 'ART72A', descricao: 'Art. 72.º-A' }],
      })),
    );
    completarPasso1();
    fixture.componentInstance.criarProcesso();
    fixture.detectChanges();

    const caixa = fixture.nativeElement.querySelector('fieldset.fundamentacao input[type="checkbox"]') as HTMLInputElement;
    expect(caixa.parentElement!.textContent).toContain('Art. 72.º-A');
    caixa.click();
    fixture.componentInstance.guardarRascunho();

    expect(processoServiceMock.guardarRascunho).toHaveBeenCalledWith('processo-1', expect.objectContaining({ fundamentacaoRjigt: ['ART72A'] }));
  });

  it('se a leitura do território falhar, o cartão mostra traço e o formulário continua utilizável', () => {
    processoServiceMock.obter.mockReturnValue(throwError(() => new ApiError('ERRO_INTERNO', 'Falha.', 't1', null)));
    completarPasso1();
    fixture.componentInstance.criarProcesso();
    fixture.detectChanges();

    const contexto = fixture.nativeElement.querySelector('app-processo-formulario-inicial dl.contexto')!.textContent as string;
    expect(contexto).toMatch(/NUT II\s*—/);
    expect(fixture.componentInstance.erro()).toBeNull();
    expect(fixture.nativeElement.querySelector('app-processo-formulario-inicial input')).not.toBeNull();
  });

  describe('IDUS no cartão de identificação', () => {
    const IDUS = '01.1111.01.001001.02.90001.01.031.00';

    const rotulosDoCartao = (): string[] =>
      Array.from(fixture.nativeElement.querySelectorAll('.identificacao dt') as NodeListOf<HTMLElement>).map((dt) =>
        dt.textContent!.trim(),
      );

    it('logo após criar o processo mostra o IDUS lido do detalhe, antes do Nº Processo SAIGT', () => {
      processoServiceMock.criar.mockReturnValue(of(processo({ numeroProcesso: '00042' })));
      processoServiceMock.obter.mockReturnValue(of(processoDetalhe({ numeroProcesso: '00042', idus: IDUS })));
      completarPasso1();
      fixture.componentInstance.criarProcesso();
      fixture.detectChanges();

      expect(processoServiceMock.obter).toHaveBeenCalledWith('processo-1');
      expect(rotulosDoCartao()).toEqual(['IDUS', 'Nº Processo SAIGT']);
      const valores = Array.from(fixture.nativeElement.querySelectorAll('.identificacao dd') as NodeListOf<HTMLElement>).map(
        (dd) => dd.textContent!.trim(),
      );
      expect(valores).toEqual([IDUS, '00042']);
    });

    it('sem IDUS no detalhe o cartão só mostra o Nº Processo SAIGT, sem traço no lugar do IDUS', () => {
      processoServiceMock.obter.mockReturnValue(of(processoDetalhe({ idus: null })));
      completarPasso1();
      fixture.componentInstance.criarProcesso();
      fixture.detectChanges();

      expect(rotulosDoCartao()).toEqual(['Nº Processo SAIGT']);
      expect(fixture.nativeElement.querySelector('.identificacao')!.textContent).not.toContain('IDUS');
    });

    it('se a leitura do detalhe falhar, o cartão mostra o Nº Processo SAIGT e não o IDUS', () => {
      processoServiceMock.obter.mockReturnValue(throwError(() => new ApiError('ERRO_INTERNO', 'Falha.', 't1', null)));
      completarPasso1();
      fixture.componentInstance.criarProcesso();
      fixture.detectChanges();

      expect(rotulosDoCartao()).toEqual(['Nº Processo SAIGT']);
      expect(fixture.componentInstance.erro()).toBeNull();
    });

    it('mantém o IDUS depois de gravar o rascunho', () => {
      processoServiceMock.obter.mockReturnValue(of(processoDetalhe({ idus: IDUS })));
      completarPasso1();
      fixture.componentInstance.criarProcesso();
      fixture.detectChanges();

      fixture.componentInstance.guardarRascunho();
      fixture.detectChanges();

      expect(processoServiceMock.guardarRascunho).toHaveBeenCalledTimes(1);
      expect(rotulosDoCartao()).toEqual(['IDUS', 'Nº Processo SAIGT']);
    });

    it('ao retomar um rascunho mostra o IDUS do detalhe', () => {
      completarPasso1ComRetoma();
      processoServiceMock.obter.mockReturnValue(of(processoDetalhe({ idus: IDUS })));
      fixture.componentInstance.retomarTentativa();
      fixture.detectChanges();

      expect(rotulosDoCartao()).toEqual(['IDUS', 'Nº Processo SAIGT']);
      expect(fixture.nativeElement.querySelector('.identificacao dd')!.textContent!.trim()).toBe(IDUS);
    });
  });

  it('o passo Formulário tem um só cartão Informação Geral, com o contexto do processo e sem o cartão Publicação INCM', () => {
    completarPasso1();
    fixture.componentInstance.criarProcesso();
    fixture.detectChanges();

    const cartoes = fixture.nativeElement.querySelectorAll('app-processo-formulario-inicial') as NodeListOf<HTMLElement>;
    expect(cartoes).toHaveLength(1);
    const titulos = Array.from(fixture.nativeElement.querySelectorAll('h2') as NodeListOf<HTMLElement>).map((h) => h.textContent?.trim());
    expect(titulos).toContain('Informação Geral');
    expect(titulos).not.toContain('Publicação INCM');

    const texto = cartoes[0].textContent as string;
    for (const rotulo of ['Tipo de instrumento', 'Tipo de procedimento', 'NUT II', 'Concelho', 'Designação', 'Parte do DR', 'Tipo de ato', 'Norma legal', 'Sumário']) {
      expect(texto).toContain(rotulo);
    }
    expect(texto).toContain('Plano Diretor Municipal');
    expect(texto).toContain('Decisão de início');
    expect(texto).toContain('PDM de Sintra');
    expect(texto).toContain('2.ª Série - H');
    expect(texto).toContain('Despacho');
    expect(texto).not.toContain('Data de assinatura');
  });

  it('Seguinte relê o detalhe depois de gravar e o Resumo parte do `podeSubmeter` do backend', () => {
    completarPasso1();
    fixture.componentInstance.criarProcesso();
    fixture.detectChanges();
    processoServiceMock.obter.mockClear();
    processoServiceMock.obter.mockReturnValue(of(processoDetalhe({ versao: 1, podeSubmeter: false })));

    fixture.componentInstance.avancarParaPublicar();
    fixture.detectChanges();

    expect(processoServiceMock.guardarRascunho).toHaveBeenCalledTimes(1);
    expect(processoServiceMock.obter).toHaveBeenCalledTimes(1);
    expect(processoServiceMock.obter).toHaveBeenCalledWith('processo-1');
    expect(fixture.componentInstance.passoAtual()).toBe(3);
    expect(fixture.componentInstance.podeSubmeter()).toBe(false);
    expect(fixture.nativeElement.textContent).toContain('Preencha os dados obrigatórios do formulário antes de submeter.');
  });

  it('com pendências nos documentos, o Seguinte grava mas fica na página e diz o que falta', () => {
    completarPasso1();
    fixture.componentInstance.criarProcesso();
    fixture.componentInstance.subpagina.set('documentos');
    fixture.detectChanges();
    processoServiceMock.obter.mockReturnValue(
      of(
        processoDetalhe({
          versao: 1,
          podeSubmeter: false,
          pendenciasSubmissao: [
            { pagina: 'DOCUMENTOS', campo: 'TEXTO_ATO_PUB', erro: 'Falta carregar: Texto do Ato.' },
            { pagina: 'FORMULARIO', campo: 'sumario', erro: 'Indique o sumário.' },
          ],
        }),
      ),
    );

    fixture.componentInstance.seguinte();
    fixture.detectChanges();

    expect(processoServiceMock.guardarRascunho).toHaveBeenCalled();
    expect(fixture.componentInstance.passoAtual()).toBe(2);
    expect(fixture.componentInstance.subpagina()).toBe('documentos');
    const aviso = fixture.nativeElement.querySelector('.pendencias-pagina') as HTMLElement;
    expect(aviso.textContent).toContain('Falta carregar: Texto do Ato.');
    expect(aviso.textContent).not.toContain('Indique o sumário.');
  });

  it('sem pendências na página, o Seguinte dos documentos passa ao Resumo', () => {
    completarPasso1();
    fixture.componentInstance.criarProcesso();
    fixture.componentInstance.subpagina.set('documentos');
    fixture.detectChanges();
    processoServiceMock.obter.mockReturnValue(
      of(processoDetalhe({ versao: 1, podeSubmeter: false, pendenciasSubmissao: [{ pagina: 'FORMULARIO', campo: 'sumario', erro: 'Indique o sumário.' }] })),
    );

    fixture.componentInstance.seguinte();
    fixture.detectChanges();

    expect(fixture.componentInstance.passoAtual()).toBe(3);
    expect(fixture.nativeElement.querySelector('.pendencias-pagina')).toBeNull();
  });

  describe('Informação Base incoerente na página dos Documentos', () => {
    const INCOERENTE: PendenciaSubmissao[] = [
      { pagina: 'DOCUMENTOS', campo: 'informacaoBase.textoAto.prazoAnos', erro: 'O prazo tem de ser o mesmo da Deliberação.' },
      {
        pagina: 'DOCUMENTOS',
        campo: 'informacaoBase.textoAto.avaliacaoAmbiental',
        erro: 'A avaliação ambiental tem de ser a mesma da Deliberação.',
      },
      {
        pagina: 'DOCUMENTOS',
        campo: 'informacaoBase.deliberacao.dataDeliberacao',
        erro: 'A data da deliberação tem de ser igual ou anterior à data do texto do ato.',
      },
      { pagina: 'DOCUMENTOS', campo: 'informacaoBase.deliberacao.dataDeliberacao', erro: 'Posterior à data limite da dinâmica (31/08/2026).' },
    ];

    function nosDocumentosCom(pendencias: PendenciaSubmissao[]): void {
      completarPasso1();
      fixture.componentInstance.criarProcesso();
      fixture.componentInstance.subpagina.set('documentos');
      fixture.detectChanges();
      processoServiceMock.obter.mockReturnValue(
        of(processoDetalhe({ versao: 1, podeSubmeter: pendencias.length === 0, pendenciasSubmissao: pendencias })),
      );
    }

    it('o Seguinte não avança e cada mensagem fica na caixa de pendências e junto do campo do bloco', () => {
      nosDocumentosCom(INCOERENTE);

      fixture.componentInstance.seguinte();
      fixture.detectChanges();

      expect(fixture.componentInstance.passoAtual()).toBe(2);
      expect(fixture.componentInstance.subpagina()).toBe('documentos');
      const aviso = fixture.nativeElement.querySelector('.pendencias-pagina') as HTMLElement;
      expect(aviso.textContent).toContain('Preencha o que falta nesta página antes de avançar:');
      for (const pendencia of INCOERENTE) {
        expect(aviso.textContent).toContain(pendencia.erro);
      }
      expect(fixture.componentInstance.errosInformacaoBase()).toEqual({
        'informacaoBase.textoAto.prazoAnos': 'O prazo tem de ser o mesmo da Deliberação.',
        'informacaoBase.textoAto.avaliacaoAmbiental': 'A avaliação ambiental tem de ser a mesma da Deliberação.',
        'informacaoBase.deliberacao.dataDeliberacao':
          'A data da deliberação tem de ser igual ou anterior à data do texto do ato. Posterior à data limite da dinâmica (31/08/2026).',
      });
    });

    it('as pendências de carregamento dos Documentos não vão para junto dos campos do bloco', () => {
      nosDocumentosCom([{ pagina: 'DOCUMENTOS', campo: 'TEXTO_ATO_PUB', erro: 'Falta carregar: Texto do Ato.' }]);

      fixture.componentInstance.seguinte();
      fixture.detectChanges();

      expect(fixture.componentInstance.subpagina()).toBe('documentos');
      expect(fixture.componentInstance.errosInformacaoBase()).toEqual({});
    });

    it('com os valores corrigidos, o Seguinte avança para o Resumo e a mensagem desaparece', () => {
      nosDocumentosCom(INCOERENTE.slice(0, 1));
      fixture.componentInstance.seguinte();
      fixture.detectChanges();
      expect(fixture.componentInstance.errosInformacaoBase()).not.toEqual({});

      processoServiceMock.obter.mockReturnValue(of(processoDetalhe({ versao: 2, podeSubmeter: true, pendenciasSubmissao: [] })));
      fixture.componentInstance.seguinte();
      fixture.detectChanges();

      expect(fixture.componentInstance.passoAtual()).toBe(3);
      expect(fixture.componentInstance.errosInformacaoBase()).toEqual({});
      expect(fixture.nativeElement.querySelector('.pendencias-pagina')).toBeNull();
    });
  });

  it('o Resumo de um rascunho completo não mostra a mensagem de dados em falta', () => {
    completarPasso1();
    fixture.componentInstance.criarProcesso();
    fixture.detectChanges();
    processoServiceMock.obter.mockReturnValue(of(processoDetalhe({ versao: 1, podeSubmeter: true })));

    fixture.componentInstance.avancarParaPublicar();
    fixture.detectChanges();

    expect(fixture.componentInstance.passoAtual()).toBe(3);
    expect(fixture.nativeElement.textContent).not.toContain('Preencha os dados obrigatórios do formulário antes de submeter.');
  });

  it('não abre o Resumo e mostra o erro quando a releitura do detalhe falha', () => {
    completarPasso1();
    fixture.componentInstance.criarProcesso();
    fixture.detectChanges();
    processoServiceMock.obter.mockReturnValue(throwError(() => new ApiError('NAO_ENCONTRADO', 'Processo não encontrado.', 't9', null)));

    fixture.componentInstance.avancarParaPublicar();

    expect(fixture.componentInstance.passoAtual()).toBe(2);
    expect(fixture.componentInstance.erro()?.codigo).toBe('NAO_ENCONTRADO');
    expect(fixture.componentInstance.aGuardar()).toBe(false);
  });

  it('Seguinte, no topo do passo Formulário, grava em cada sub-página e abre o Resumo com os dados do processo', () => {
    completarPasso1();
    fixture.componentInstance.criarProcesso();
    fixture.detectChanges();
    const seguinte = (): HTMLButtonElement =>
      Array.from(fixture.nativeElement.querySelectorAll('.navegacao-topo button') as NodeListOf<HTMLButtonElement>).find((botao) =>
        botao.textContent?.includes('Seguinte'),
      )!;
    assinalarPecasEscritas();

    seguinte().click();
    fixture.detectChanges();

    expect(processoServiceMock.guardarRascunho).toHaveBeenCalledTimes(1);
    expect(fixture.componentInstance.passoAtual()).toBe(2);
    expect(fixture.componentInstance.subpagina()).toBe('documentos');

    seguinte().click();
    fixture.detectChanges();

    expect(processoServiceMock.guardarRascunho).toHaveBeenCalledTimes(2);
    expect(fixture.componentInstance.passoAtual()).toBe(3);
    const texto = fixture.nativeElement.textContent as string;
    for (const rotulo of ['Resumo', 'Designação', 'Tipo de instrumento', 'Tipo de procedimento', 'Concelho']) {
      expect(texto).toContain(rotulo);
    }
    expect(texto).not.toContain('SEGUNDA_H');
  });

  it('o Formulário é o último passo do assistente e o Resumo não tem passos', () => {
    completarPasso1();
    fixture.componentInstance.criarProcesso();
    fixture.detectChanges();

    expect(etiquetasPassos()).toEqual(['Tipo IGT', 'IGT Existente', 'Ação', 'Formulário']);
    expect(fixture.nativeElement.querySelector('app-passos .passo-atual .passo-etiqueta').textContent.trim()).toBe('Formulário');

    fixture.componentInstance.avancarParaPublicar();
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('app-passos')).toBeNull();
  });

  it('o Formulário de uma Dinâmica nova mostra também o passo Nova Dinâmica', () => {
    fixture.componentInstance.selecionarTipo('1');
    fixture.componentInstance.selecionarInstrumento('instrumento-1');
    fixture.componentInstance.escolherModo('nova');
    fixture.componentInstance.selecionarDinamicaNova('103');
    fixture.componentInstance.alterarDescritivoNovo('Alteração da planta');
    instrumentoServiceMock.disponivel.mockReturnValue(of(disponibilidade({ dinamicasEmCurso: [dinamicaEmCurso({ dinamicaId: 'dinamica-2' })] })));
    fixture.componentInstance.iniciarDinamica();
    fixture.componentInstance.criarProcesso();
    fixture.detectChanges();

    expect(fixture.componentInstance.passoAtual()).toBe(2);
    expect(etiquetasPassos()).toEqual(['Tipo IGT', 'IGT Existente', 'Ação', 'Nova Dinâmica', 'Formulário']);
  });

  it('grava as Observações dos documentos depois do rascunho e só então abre o Resumo', () => {
    completarPasso1();
    fixture.componentInstance.criarProcesso();
    fixture.componentInstance.subpagina.set('documentos');
    fixture.detectChanges();
    const gravar = vi.spyOn(fixture.componentInstance.documentosRef()!, 'gravarObservacoes').mockReturnValue(of(true));

    fixture.componentInstance.avancarParaPublicar();
    fixture.detectChanges();

    expect(processoServiceMock.guardarRascunho).toHaveBeenCalledTimes(1);
    expect(gravar).toHaveBeenCalledTimes(1);
    expect(fixture.componentInstance.passoAtual()).toBe(3);
  });

  it('não abre o Resumo quando as Observações falham: o erro fica junto da caixa', () => {
    completarPasso1();
    fixture.componentInstance.criarProcesso();
    fixture.componentInstance.subpagina.set('documentos');
    fixture.detectChanges();
    vi.spyOn(fixture.componentInstance.documentosRef()!, 'gravarObservacoes').mockReturnValue(of(false));

    fixture.componentInstance.avancarParaPublicar();
    fixture.detectChanges();

    expect(fixture.componentInstance.passoAtual()).toBe(2);
    expect(fixture.componentInstance.aGuardar()).toBe(false);
  });

  it('Guardar rascunho, no fim do passo Formulário, grava e fica na mesma sub-página', () => {
    completarPasso1();
    fixture.componentInstance.criarProcesso();
    fixture.detectChanges();
    const guardar = fixture.nativeElement.querySelector('.acoes .guardar-rascunho') as HTMLButtonElement;

    guardar.click();
    fixture.detectChanges();

    expect(guardar.textContent).toContain('Guardar rascunho');
    expect(processoServiceMock.guardarRascunho).toHaveBeenCalledTimes(1);
    expect(fixture.componentInstance.passoAtual()).toBe(2);
    expect(fixture.componentInstance.subpagina()).toBe('cabecalho');
  });

  describe('sub-páginas do Formulário', () => {
    beforeEach(() => {
      completarPasso1();
      fixture.componentInstance.criarProcesso();
      fixture.detectChanges();
    });

    const botaoDeAcao = (rotulo: string): HTMLButtonElement =>
      Array.from(fixture.nativeElement.querySelectorAll('.acoes button') as NodeListOf<HTMLButtonElement>).find((botao) =>
        botao.textContent?.trim() === rotulo,
      )!;

    it('abre no cabeçalho (UI-051), com o N.º Processo SAIGT e sem os documentos', () => {
      expect(fixture.componentInstance.subpagina()).toBe('cabecalho');
      expect(fixture.nativeElement.querySelector('.identificacao')!.textContent).toContain('Nº Processo SAIGT');
      expect(fixture.nativeElement.querySelector('.identificacao')!.textContent).toContain('00001');
      expect(fixture.nativeElement.querySelector('app-processo-documentos')).toBeNull();
      expect(documentoServiceMock.estrutura).not.toHaveBeenCalled();
    });

    it('o Seguinte grava os elementos afetados e só então abre os documentos (UI-052), com o cabeçalho escondido', () => {
      assinalarPecasEscritas();
      botaoDeAcao('Seguinte').click();
      fixture.detectChanges();

      expect(processoServiceMock.guardarRascunho).toHaveBeenCalledTimes(1);
      expect(documentoServiceMock.estrutura).toHaveBeenCalledWith('processo-1');
      expect(fixture.nativeElement.querySelector('app-processo-documentos')).not.toBeNull();
      expect(fixture.nativeElement.querySelector('app-processo-formulario-inicial')!.parentElement.hidden).toBe(true);
      expect(fixture.nativeElement.querySelector('.identificacao')).not.toBeNull();
    });

    it('o Anterior dos documentos grava e volta ao cabeçalho, com o que lá se escreveu', () => {
      fixture.componentInstance.formularioRef()!.form.patchValue({ sumario: 'Escrito no cabeçalho' });
      assinalarPecasEscritas();
      botaoDeAcao('Seguinte').click();
      fixture.detectChanges();

      botaoDeAcao('Anterior').click();
      fixture.detectChanges();

      expect(processoServiceMock.guardarRascunho).toHaveBeenCalledTimes(2);
      expect(fixture.componentInstance.subpagina()).toBe('cabecalho');
      expect(fixture.nativeElement.querySelector('app-processo-formulario-inicial')!.parentElement.hidden).toBe(false);
      expect(fixture.componentInstance.formularioRef()!.form.controls.sumario.value).toBe('Escrito no cabeçalho');
    });

    it('um erro do cabeçalho apanhado ao gravar nos documentos devolve ao cabeçalho, junto do campo', () => {
      fixture.componentInstance.subpagina.set('documentos');
      fixture.detectChanges();
      processoServiceMock.guardarRascunho.mockReturnValue(
        throwError(() => new ApiError('VALIDACAO', 'Payload inválido.', 't1', [{ path: 'sumario', erro: 'Demasiado longo.' }])),
      );

      botaoDeAcao('Seguinte').click();
      fixture.detectChanges();

      expect(fixture.componentInstance.subpagina()).toBe('cabecalho');
      expect(fixture.componentInstance.erro()).toBeNull();
      expect(fixture.componentInstance.formularioRef()!.form.controls.sumario.getError('servidor')).toBe('Demasiado longo.');
    });

    it('o Anterior do cabeçalho volta às escolhas, sem gravar', () => {
      botaoDeAcao('Anterior').click();
      fixture.detectChanges();

      expect(fixture.componentInstance.passoAtual()).toBe(1);
      expect(processoServiceMock.guardarRascunho).not.toHaveBeenCalled();
    });
  });

  it('mostra os documentos do processo na segunda sub-página do Formulário, sem títulos de grupo', () => {
    completarPasso1();
    fixture.componentInstance.criarProcesso();
    fixture.detectChanges();
    assinalarPecasEscritas();
    fixture.componentInstance.seguinte();
    fixture.detectChanges();

    const texto = fixture.nativeElement.textContent as string;
    expect(documentoServiceMock.estrutura).toHaveBeenCalledWith('processo-1');
    expect(texto).not.toContain('Publicação INCM e Depósito DGT');
    expect(fixture.nativeElement.querySelector('.cartao-documentos-cabecalho').textContent).toContain('Informação Geral');
    expect(fixture.nativeElement.querySelector('app-processo-documentos').classList).toContain('embutido');
    const outros = Array.from(fixture.nativeElement.querySelectorAll('app-processo-documentos .acordeao-cabecalho') as NodeListOf<HTMLButtonElement>).find(
      (cabecalho) => cabecalho.textContent?.includes('Outros'),
    )!;
    outros.click();
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('app-processo-documentos input[type="file"]')).not.toBeNull();
  });

  describe('formulário fixo do Procedimento no passo 2', () => {
    const AVISO = 'Este procedimento ainda não tem formulário na aplicação. Contacte a DGT através de um pedido de apoio.';

    const textosDosBotoes = (): (string | undefined)[] =>
      Array.from(fixture.nativeElement.querySelectorAll('button') as NodeListOf<HTMLButtonElement>).map((botao) =>
        botao.textContent?.replace('›', '').trim(),
      );

    function avisoSemFormulario(): HTMLElement | undefined {
      return Array.from(fixture.nativeElement.querySelectorAll('app-alerta') as NodeListOf<HTMLElement>).find(
        (alerta) => alerta.textContent?.trim() === AVISO,
      );
    }

    it('com INFORMACAO_GERAL mostra o cartão Informação Geral, como antes', () => {
      completarPasso1();
      fixture.componentInstance.criarProcesso();
      fixture.detectChanges();

      expect(fixture.nativeElement.querySelector('app-processo-formulario-inicial')).not.toBeNull();
      expect(avisoSemFormulario()).toBeUndefined();
    });

    it('sem formulário no Procedimento escolhido mostra o aviso, com role="status", e não há gravar nem Seguinte', () => {
      instrumentoServiceMock.disponivel.mockReturnValue(
        of(disponibilidade({ dinamicasEmCurso: [dinamicaEmCurso({ procedimentosDisponiveis: [procedimento({ formulario: null })] })] })),
      );
      completarPasso1();
      fixture.componentInstance.criarProcesso();
      fixture.detectChanges();

      expect(fixture.componentInstance.passoAtual()).toBe(2);
      expect(avisoSemFormulario()?.getAttribute('role')).toBe('status');
      expect(fixture.nativeElement.querySelector('app-processo-formulario-inicial')).toBeNull();
      expect(textosDosBotoes()).not.toContain('Guardar rascunho');
      expect(textosDosBotoes()).not.toContain('Seguinte');
    });

    it('com PUBLICACAO_ALTERACAO mostra o cartão Informação Geral e passa-lhe o formulário', () => {
      instrumentoServiceMock.disponivel.mockReturnValue(
        of(disponibilidade({ dinamicasEmCurso: [dinamicaEmCurso({ procedimentosDisponiveis: [procedimento({ formulario: 'PUBLICACAO_ALTERACAO' })] })] })),
      );
      completarPasso1();
      fixture.componentInstance.criarProcesso();
      fixture.detectChanges();

      expect(fixture.nativeElement.querySelector('app-processo-formulario-inicial')).not.toBeNull();
      expect(avisoSemFormulario()).toBeUndefined();
      expect(fixture.componentInstance.formularioRef()!.formulario).toBe('PUBLICACAO_ALTERACAO');
    });

    it('com REN mostra o cartão com a Dinâmica como tipo de procedimento e os motivos do detalhe', () => {
      instrumentoServiceMock.disponivel.mockReturnValue(
        of(disponibilidade({ dinamicasEmCurso: [dinamicaEmCurso({ procedimentosDisponiveis: [procedimento({ formulario: 'REN' })] })] })),
      );
      processoServiceMock.obter.mockReturnValue(
        of(
          processoDetalhe({
            formulario: 'REN',
            nomeDinamica: 'Delimitação',
            motivosProcedimentoDisponiveis: [{ id: 7, codigo: 'M1', descricao: 'Motivo um' }],
            tipoInformacaoFixo: 'TEXTO_INTEGRAL',
          }),
        ),
      );
      completarPasso1();
      fixture.componentInstance.criarProcesso();
      fixture.detectChanges();

      const formulario = fixture.componentInstance.formularioRef()!;
      expect(avisoSemFormulario()).toBeUndefined();
      expect(formulario.formulario).toBe('REN');
      expect(formulario.tipoProcedimento).toBe('Delimitação');
      expect(formulario.motivosProcedimentoDisponiveis).toEqual([{ id: 7, codigo: 'M1', descricao: 'Motivo um' }]);
      expect(formulario.tipoInformacaoFixo).toBe('TEXTO_INTEGRAL');
    });

    it('com SRUP mostra a secção Servidão e o cabeçalho comum sem elementos afetados, grava-o e esconde a secção nos documentos', () => {
      instrumentoServiceMock.disponivel.mockReturnValue(
        of(
          disponibilidade({
            dinamicasEmCurso: [
              dinamicaEmCurso({
                nomeDinamica: 'Delimitação',
                procedimentosDisponiveis: [
                  procedimento({ formulario: 'SRUP', tipoProcedimento: 'PUBLICACAO_SRUP', nomeProcedimento: 'Publicação da servidão' }),
                ],
              }),
            ],
          }),
        ),
      );
      processoServiceMock.obter.mockReturnValue(of(processoDetalhe({ formulario: 'SRUP', exigeElementosAfetados: true })));
      completarPasso1();
      fixture.componentInstance.criarProcesso();
      fixture.detectChanges();

      const servidao = (): HTMLElement | null => fixture.nativeElement.querySelector('app-processo-formulario-srup section.cartao-servidao');
      expect(avisoSemFormulario()).toBeUndefined();
      expect(servidao()!.querySelector('h2')!.textContent!.trim()).toBe('Servidão');
      expect(Array.from(servidao()!.querySelectorAll('dd')).map((dd) => (dd as HTMLElement).textContent!.trim())).toEqual([
        fixture.componentInstance.nomeTipoInstrumento(),
        'Delimitação',
      ]);
      expect(fixture.nativeElement.querySelector('app-processo-formulario-srup app-processo-formulario-inicial')).not.toBeNull();
      expect(fixture.componentInstance.formularioRef()!.formulario).toBe('SRUP');
      expect(fixture.componentInstance.formularioRef()!.pedeElementosAfetados).toBe(false);
      expect(fixture.nativeElement.querySelector('fieldset.elementos')).toBeNull();

      fixture.componentInstance.seguinte();
      fixture.detectChanges();

      expect(processoServiceMock.guardarRascunho).toHaveBeenCalled();
      expect(processoServiceMock.guardarRascunho.mock.calls.at(-1)![1]).toMatchObject({ elementosAfetados: [] });
      expect(fixture.componentInstance.subpagina()).toBe('documentos');
      expect(servidao()).toBeNull();
      expect(fixture.debugElement.query(By.directive(ProcessoDocumentosComponent))).not.toBeNull();
    });

    it('com SRUP sem série escolhe-se a série no cabeçalho: grava o rascunho com serieDr e relê o detalhe com os tipos dela', () => {
      instrumentoServiceMock.disponivel.mockReturnValue(
        of(
          disponibilidade({
            dinamicasEmCurso: [
              dinamicaEmCurso({
                nomeDinamica: 'Delimitação',
                procedimentosDisponiveis: [
                  procedimento({ formulario: 'SRUP', tipoProcedimento: 'PUBLICACAO_SRUP', nomeProcedimento: 'Publicação da servidão', serieDr: null }),
                ],
              }),
            ],
          }),
        ),
      );
      const escolhiveis = ['PRIMEIRA', 'SEGUNDA_H', 'SEGUNDA_C'] as ('PRIMEIRA' | 'SEGUNDA_H' | 'SEGUNDA_C')[];
      processoServiceMock.obter.mockReturnValueOnce(
        of(processoDetalhe({ formulario: 'SRUP', serieDr: null, seriesDrEscolhiveis: escolhiveis, tiposAtoDisponiveis: [], tipoAtoId: null, tipoAtoNome: null })),
      );
      processoServiceMock.obter.mockReturnValue(
        of(
          processoDetalhe({
            formulario: 'SRUP',
            serieDr: 'SEGUNDA_C',
            seriesDrEscolhiveis: escolhiveis,
            tiposAtoDisponiveis: [{ id: 16, codigo: 'DELIB', nome: 'Deliberação' }],
            tipoAtoId: null,
            tipoAtoNome: null,
          }),
        ),
      );
      completarPasso1();
      fixture.componentInstance.criarProcesso();
      fixture.detectChanges();

      const serie = fixture.nativeElement.querySelector('app-seletor.serie-dr select') as HTMLSelectElement;
      expect(serie).not.toBeNull();
      serie.value = 'SEGUNDA_C';
      serie.dispatchEvent(new Event('change'));
      fixture.detectChanges();

      expect(processoServiceMock.guardarRascunho.mock.calls.at(-1)![1]).toMatchObject({ serieDr: 'SEGUNDA_C' });
      const tipos = fixture.nativeElement.querySelector('app-seletor.tipo-ato select') as HTMLSelectElement;
      expect([...tipos.options].map((opcao) => opcao.textContent?.trim())).toEqual(['Selecionar...', 'Deliberação']);
      expect(fixture.componentInstance.serieDrEscolhida()).toBe('SEGUNDA_C');
    });

    it('com RATIFICACAO mostra a secção Ratificação, grava a modalidade com o cabeçalho e esconde a secção nos documentos', () => {
      instrumentoServiceMock.disponivel.mockReturnValue(
        of(
          disponibilidade({
            dinamicasEmCurso: [
              dinamicaEmCurso({
                nomeDinamica: 'Ratificação',
                procedimentosDisponiveis: [
                  procedimento({ formulario: 'RATIFICACAO', tipoProcedimento: 'RATIFICACAO', nomeProcedimento: 'Ratificação', serieDr: 'PRIMEIRA' }),
                ],
              }),
            ],
          }),
        ),
      );
      processoServiceMock.obter.mockReturnValue(
        of(processoDetalhe({ formulario: 'RATIFICACAO', serieDr: 'PRIMEIRA', exigeElementosAfetados: false, modalidadeRatificacao: null })),
      );
      completarPasso1();
      fixture.componentInstance.criarProcesso();
      fixture.detectChanges();

      const seccao = (): HTMLElement | null => fixture.nativeElement.querySelector('app-formulario-ratificacao section.cartao-ratificacao');
      expect(avisoSemFormulario()).toBeUndefined();
      expect(seccao()!.querySelector('h2')!.textContent!.trim()).toBe('Ratificação');
      expect(fixture.nativeElement.querySelector('app-formulario-ratificacao app-processo-formulario-inicial')).not.toBeNull();
      expect(fixture.componentInstance.formularioRef()!.formulario).toBe('RATIFICACAO');
      expect(fixture.nativeElement.querySelector('fieldset.elementos')).toBeNull();
      const opcoes = Array.from(seccao()!.querySelectorAll<HTMLInputElement>('fieldset.modalidade input[type="radio"]'));
      opcoes[0].click();
      const data = fixture.nativeElement.querySelector('input.data-assinatura') as HTMLInputElement;
      data.value = '2026-11-02';
      data.dispatchEvent(new Event('input'));
      fixture.detectChanges();

      fixture.componentInstance.seguinte();
      fixture.detectChanges();

      expect(processoServiceMock.guardarRascunho.mock.calls.at(-1)![1]).toMatchObject({
        modalidadeRatificacao: 'PUBLICACAO',
        dataAssinatura: '2026-11-02',
        elementosAfetados: [],
      });
      expect(fixture.componentInstance.publicacaoIncm()!.dataAssinatura).toBe('2026-11-02');
      expect(fixture.componentInstance.subpagina()).toBe('documentos');
      expect(seccao()).toBeNull();
    });

    it('com DISCUSSAO_PUBLICA mostra o cartão Informação Geral e os documentos recebem a data limite do detalhe', () => {
      instrumentoServiceMock.disponivel.mockReturnValue(
        of(disponibilidade({ dinamicasEmCurso: [dinamicaEmCurso({ procedimentosDisponiveis: [procedimento({ formulario: 'DISCUSSAO_PUBLICA' })] })] })),
      );
      processoServiceMock.obter.mockReturnValue(of(processoDetalhe({ formulario: 'DISCUSSAO_PUBLICA', dataCaducidadeDinamica: '2027-03-31' })));
      completarPasso1();
      fixture.componentInstance.criarProcesso();
      fixture.detectChanges();

      expect(fixture.nativeElement.querySelector('app-processo-formulario-inicial')).not.toBeNull();
      expect(avisoSemFormulario()).toBeUndefined();
      expect(fixture.componentInstance.formularioRef()!.formulario).toBe('DISCUSSAO_PUBLICA');

      assinalarPecasEscritas();
      fixture.componentInstance.seguinte();
      fixture.detectChanges();

      const documentos = fixture.debugElement.query(By.directive(ProcessoDocumentosComponent)).componentInstance as ProcessoDocumentosComponent;
      expect(documentos.dataLimiteDinamica).toBe('2027-03-31');
    });

    it('com DISCUSSAO_PUBLICA numa Dinâmica sem data limite, os documentos recebem null', () => {
      instrumentoServiceMock.disponivel.mockReturnValue(
        of(disponibilidade({ dinamicasEmCurso: [dinamicaEmCurso({ procedimentosDisponiveis: [procedimento({ formulario: 'DISCUSSAO_PUBLICA' })] })] })),
      );
      processoServiceMock.obter.mockReturnValue(of(processoDetalhe({ formulario: 'DISCUSSAO_PUBLICA', dataCaducidadeDinamica: null })));
      completarPasso1();
      fixture.componentInstance.criarProcesso();
      fixture.detectChanges();
      assinalarPecasEscritas();
      fixture.componentInstance.seguinte();
      fixture.detectChanges();

      const documentos = fixture.debugElement.query(By.directive(ProcessoDocumentosComponent)).componentInstance as ProcessoDocumentosComponent;
      expect(documentos.dataLimiteDinamica).toBeNull();
    });

    it('ao retomar um processo sem formulário mostra o aviso em vez de "Não pode editar este processo."', () => {
      completarPasso1ComRetoma();
      processoServiceMock.obter.mockReturnValue(
        of(processoDetalhe({ formulario: null, editavel: false, acoesDisponiveis: [], podeSubmeter: false })),
      );
      fixture.componentInstance.retomarTentativa();
      fixture.detectChanges();

      expect(avisoSemFormulario()).toBeDefined();
      expect(fixture.nativeElement.textContent).not.toContain('Não pode editar este processo.');
      expect(textosDosBotoes()).not.toContain('Guardar rascunho');
    });
  });

  describe('Guardar rascunho', () => {
    afterEach(() => vi.useRealTimers());

    it('relê a estrutura documental depois de gravar, porque os elementos afetados podem ter mudado', () => {
      completarPasso1();
      fixture.componentInstance.criarProcesso();
      fixture.detectChanges();
      assinalarPecasEscritas();
      fixture.componentInstance.seguinte();
      fixture.detectChanges();
      expect(documentoServiceMock.estrutura).toHaveBeenCalledTimes(1);

      fixture.componentInstance.guardarRascunho();
      fixture.detectChanges();

      expect(documentoServiceMock.estrutura).toHaveBeenCalledTimes(2);
    });

    it('mostra a confirmação passageira depois de gravar', () => {
      vi.useFakeTimers();
      completarPasso1();
      fixture.componentInstance.criarProcesso();
      fixture.detectChanges();

      fixture.componentInstance.guardarRascunho();
      fixture.detectChanges();

      expect(fixture.nativeElement.querySelector('.banner-sucesso-acao')?.textContent).toContain('Rascunho guardado com sucesso.');
      expect(fixture.componentInstance.passoAtual()).toBe(2);

      vi.advanceTimersByTime(4000);
      fixture.detectChanges();

      expect(fixture.nativeElement.querySelector('.banner-sucesso-acao')).toBeNull();
    });

    it('grava os campos INCM preenchidos num só pedido, sem exigir os restantes', () => {
      completarPasso1();
      fixture.componentInstance.criarProcesso();
      fixture.detectChanges();
      fixture.componentInstance.formularioRef()!.form.patchValue({ sumario: 'Sumário a meio' });

      fixture.componentInstance.guardarRascunho();

      expect(processoServiceMock.guardarRascunho).toHaveBeenCalledTimes(1);
      expect(processoServiceMock.guardarRascunho).toHaveBeenCalledWith('processo-1', {
        versao: 0,
        sumario: 'Sumário a meio',
        dataAssinatura: null,
        normaLegal: null,
        tipoInformacao: 'TEXTO_INTEGRAL',
        publicarSuplemento: null,
        dataSuplemento: null,
        contactoTramitacao: null,
        entidadeInstrutora: null,
        entidadeEmitente: null,
        contactoPublicacao: null,
        elementosAfetados: [],
        fundamentacaoRjigt: [],
        localizacao: null,
        conformaHierarquiaSuperior: null,
        motivosAlteracao: [],
        dinamicaAssociadaId: null,
        informacaoBase: null,
      });
    });

    it('grava a Informação Base e a data de assinatura do bloco num só pedido, sem o componente gravar', () => {
      completarPasso1();
      fixture.componentInstance.criarProcesso();
      fixture.componentInstance.subpagina.set('documentos');
      fixture.detectChanges();
      fixture.componentInstance.documentosRef()!.form.patchValue({
        deliberacao: { prazoAnos: 2, avaliacaoAmbiental: true, dataDeliberacao: '2026-09-01', textoPublicar: 'Texto.' },
        textoAto: { prazoMeses: 6, avaliacaoAmbiental: false },
        dataAssinatura: '2026-09-02',
      });

      fixture.componentInstance.guardarRascunho();

      expect(processoServiceMock.guardarRascunho).toHaveBeenCalledTimes(1);
      expect(processoServiceMock.guardarRascunho).toHaveBeenCalledWith(
        'processo-1',
        expect.objectContaining({
          dataAssinatura: '2026-09-02',
          informacaoBase: {
            deliberacao: {
              prazoAnos: 2,
              prazoMeses: null,
              prazoDias: null,
              avaliacaoAmbiental: true,
              dataDeliberacao: '2026-09-01',
              textoPublicar: 'Texto.',
            },
            textoAto: { prazoAnos: null, prazoMeses: 6, prazoDias: null, avaliacaoAmbiental: false },
          },
        }),
      );
    });

    it('desativa os campos do cartão enquanto grava e reativa-os, com a mensagem do servidor, se falhar', () => {
      completarPasso1();
      fixture.componentInstance.criarProcesso();
      fixture.detectChanges();
      const gravacao = new Subject<Processo>();
      processoServiceMock.guardarRascunho.mockReturnValue(gravacao);
      const camposDoCartao = () =>
        Array.from(
          fixture.nativeElement.querySelectorAll('app-processo-formulario-inicial input, app-processo-formulario-inicial textarea'),
        ) as (HTMLInputElement | HTMLTextAreaElement)[];

      fixture.componentInstance.guardarRascunho();
      fixture.detectChanges();

      expect(camposDoCartao().length).toBeGreaterThan(0);
      expect(camposDoCartao().every((campo) => campo.disabled)).toBe(true);

      gravacao.error(new ApiError('VALIDACAO', 'Payload inválido.', 't1', [{ path: 'sumario', erro: 'Demasiado longo.' }]));
      fixture.detectChanges();

      expect(camposDoCartao().some((campo) => campo.disabled)).toBe(false);
      expect(fixture.nativeElement.querySelector('app-processo-formulario-inicial .erro-campo')?.textContent).toContain('Demasiado longo.');
    });

    it('um 400 com o caminho da Informação Base aparece junto do campo e não no banner', () => {
      completarPasso1();
      fixture.componentInstance.criarProcesso();
      fixture.detectChanges();
      processoServiceMock.guardarRascunho.mockReturnValue(
        throwError(
          () =>
            new ApiError('VALIDACAO', 'Payload inválido.', 't1', [
              { path: 'informacaoBase.textoAto.prazoAnos', erro: 'O prazo tem de ser um número inteiro não negativo.' },
            ]),
        ),
      );

      fixture.componentInstance.guardarRascunho();
      fixture.detectChanges();

      expect(fixture.componentInstance.errosInformacaoBase()).toEqual({
        'informacaoBase.textoAto.prazoAnos': 'O prazo tem de ser um número inteiro não negativo.',
      });
      expect(fixture.nativeElement.querySelector('.informacao-base .erro-campo')?.textContent).toContain('número inteiro não negativo');
      expect(fixture.componentInstance.erro()).toBeNull();
    });

    it('um 400 em dataAssinatura aparece junto da data do Texto do Ato', () => {
      completarPasso1();
      fixture.componentInstance.criarProcesso();
      fixture.detectChanges();
      processoServiceMock.guardarRascunho.mockReturnValue(
        throwError(() => new ApiError('VALIDACAO', 'Payload inválido.', 't1', [{ path: 'dataAssinatura', erro: 'Indique uma data válida.' }])),
      );

      fixture.componentInstance.guardarRascunho();
      fixture.detectChanges();

      expect(fixture.nativeElement.textContent).toContain('Indique uma data válida.');
      expect(fixture.componentInstance.erro()).toBeNull();
    });

    it('depois de gravar com sucesso limpa os erros do bloco', () => {
      completarPasso1();
      fixture.componentInstance.criarProcesso();
      fixture.detectChanges();
      processoServiceMock.guardarRascunho.mockReturnValueOnce(
        throwError(() => new ApiError('VALIDACAO', 'Payload inválido.', 't1', [{ path: 'dataAssinatura', erro: 'Indique uma data válida.' }])),
      );
      fixture.componentInstance.guardarRascunho();

      fixture.componentInstance.guardarRascunho();

      expect(fixture.componentInstance.errosInformacaoBase()).toEqual({});
    });

    it('não confirma quando a gravação falha', () => {
      completarPasso1();
      fixture.componentInstance.criarProcesso();
      fixture.detectChanges();
      processoServiceMock.guardarRascunho.mockReturnValue(
        throwError(() => new ApiError('VALIDACAO', 'Payload inválido.', 't1', null)),
      );

      fixture.componentInstance.guardarRascunho();

      expect(fixture.componentInstance.mensagemSucesso()).toBeNull();
      expect(fixture.componentInstance.erro()?.codigo).toBe('VALIDACAO');
    });

    it('um 400 assinala o campo na Publicação INCM, junto do respetivo campo', () => {
      completarPasso1();
      fixture.componentInstance.criarProcesso();
      fixture.detectChanges();
      processoServiceMock.guardarRascunho.mockReturnValue(
        throwError(() => new ApiError('VALIDACAO', 'Payload inválido.', 't1', [{ path: 'tipoInformacao', erro: 'Valor inválido.' }])),
      );

      fixture.componentInstance.guardarRascunho();
      fixture.detectChanges();

      expect(fixture.componentInstance.formularioRef()!.form.controls.tipoInformacao.getError('servidor')).toBe('Valor inválido.');
      expect(fixture.nativeElement.textContent).toContain('Valor inválido.');
      expect(fixture.componentInstance.erro()).toBeNull();
      expect(fixture.componentInstance.mensagemSucesso()).toBeNull();
    });

    it('com um campo conhecido e outro sem controlo, assinala o primeiro e mostra também o banner', () => {
      completarPasso1();
      fixture.componentInstance.criarProcesso();
      fixture.detectChanges();
      const erro = new ApiError('VALIDACAO', 'Payload inválido.', 't1', [
        { path: 'sumario', erro: 'Demasiado longo.' },
        { path: 'dataSuplemento', erro: 'Data inválida.' },
      ]);
      processoServiceMock.guardarRascunho.mockReturnValue(throwError(() => erro));

      fixture.componentInstance.guardarRascunho();

      expect(fixture.componentInstance.formularioRef()!.form.controls.sumario.getError('servidor')).toBe('Demasiado longo.');
      expect(fixture.componentInstance.erro()).toBe(erro);
    });

    it('um erro de campo sem controlo correspondente cai no banner', () => {
      completarPasso1();
      fixture.componentInstance.criarProcesso();
      fixture.detectChanges();
      const erro = new ApiError('VALIDACAO', 'Payload inválido.', 't1', [{ path: 'desconhecido', erro: 'x' }]);
      processoServiceMock.guardarRascunho.mockReturnValue(throwError(() => erro));

      fixture.componentInstance.guardarRascunho();

      expect(fixture.componentInstance.erro()).toBe(erro);
    });

    it('VERSAO_DESATUALIZADA mostra o aviso e relê o processo para a próxima escrita partir da versao certa', () => {
      completarPasso1();
      fixture.componentInstance.criarProcesso();
      fixture.detectChanges();
      const erro = new ApiError('VERSAO_DESATUALIZADA', 'O processo foi alterado; recarregue.', 't1', null);
      processoServiceMock.guardarRascunho.mockReturnValue(throwError(() => erro));
      processoServiceMock.obter.mockReturnValue(of(processoDetalhe({ versao: 7 })));

      fixture.componentInstance.guardarRascunho();

      expect(fixture.componentInstance.erro()).toBe(erro);
      expect(processoServiceMock.obter).toHaveBeenCalledWith('processo-1');
      expect(fixture.componentInstance.processoCriado()?.versao).toBe(7);
    });

    it('CAMPO_BLOQUEADO mostra a mensagem no banner, sem erro junto do campo, e relê o processo com os campos bloqueados', () => {
      completarPasso1();
      fixture.componentInstance.criarProcesso();
      fixture.detectChanges();
      const erro = new ApiError('CAMPO_BLOQUEADO', 'Desbloqueie o campo antes de o alterar.', 't1', [
        { path: 'sumario', erro: 'Campo bloqueado depois da primeira submissão.' },
      ]);
      processoServiceMock.guardarRascunho.mockReturnValue(throwError(() => erro));
      processoServiceMock.obter.mockReturnValue(of(processoDetalhe({ versao: 8, camposBloqueados: ['sumario'] })));

      fixture.componentInstance.guardarRascunho();
      fixture.detectChanges();

      expect(fixture.componentInstance.erro()).toBe(erro);
      expect(processoServiceMock.obter).toHaveBeenCalledWith('processo-1');
      expect(fixture.componentInstance.processoCriado()?.camposBloqueados).toEqual(['sumario']);
      expect(fixture.componentInstance.formularioRef()!.form.controls.sumario.disabled).toBe(true);
      expect(fixture.componentInstance.formularioRef()!.form.controls.sumario.errors).toBeNull();
    });
  });

  it('retomar uma tentativa volta a preencher os campos INCM gravados', () => {
    completarPasso1ComRetoma();
    processoServiceMock.obter.mockReturnValue(
      of(processoDetalhe({ sumario: 'Sumário gravado', dataAssinatura: '2026-09-01', normaLegal: 'Art. 89.º', tipoInformacao: 'EXTRATO' })),
    );

    fixture.componentInstance.retomarTentativa();
    fixture.detectChanges();

    expect(fixture.componentInstance.formularioRef()!.form.getRawValue()).toEqual({
      sumario: 'Sumário gravado',
      normaLegal: 'Art. 89.º',
      tipoInformacao: 'EXTRATO',
      publicarSuplemento: false,
      contactoTramitacao: '',
      entidadeInstrutora: '',
      entidadeEmitente: '',
      contactoPublicacao: '',
      elementosAfetados: { PECAS_ESCRITAS: false, PECAS_GRAFICAS: false, OUTROS: false },
      fundamentacaoRjigt: [],
      comGeoconform: null,
      introduzPlantaNova: null,
      localizacao: { aplicavelTodaArea: false, freguesia: '', toponimo: '' },
      conformaHierarquiaSuperior: null,
      motivosAlteracao: [],
      dinamicaAssociadaId: null,
      tipoAtoId: '5',
      serieDr: null,
      redesDefesa: [],
      motivoProcedimentoId: null,
      diplomaCorrigido: '',
      regiaoCodigo: null,
      municipioCodigo: null,
      regiao: null,
      cimAm: null,
    });
  });

  it('retomar uma tentativa volta a preencher a Informação Base e a data de assinatura do bloco', () => {
    completarPasso1ComRetoma();
    processoServiceMock.obter.mockReturnValue(
      of(
        processoDetalhe({
          dataAssinatura: '2026-09-02',
          dadosFormulario: JSON.stringify({
            informacaoBase: {
              deliberacao: { prazoAnos: 1, prazoMeses: 2, prazoDias: 3, avaliacaoAmbiental: true, dataDeliberacao: '2026-09-01', textoPublicar: 'Texto.' },
              textoAto: { prazoAnos: 4, prazoMeses: null, prazoDias: null, avaliacaoAmbiental: false },
            },
          }),
        }),
      ),
    );

    fixture.componentInstance.retomarTentativa();
    fixture.componentInstance.subpagina.set('documentos');
    fixture.detectChanges();

    expect(fixture.componentInstance.documentosRef()!.form.getRawValue()).toEqual({
      deliberacao: { prazoAnos: 1, prazoMeses: 2, prazoDias: 3, avaliacaoAmbiental: true, dataDeliberacao: '2026-09-01', textoPublicar: 'Texto.' },
      textoAto: { prazoAnos: 4, prazoMeses: null, prazoDias: null, avaliacaoAmbiental: false },
      dataAssinatura: '2026-09-02',
    });
  });

  it('retomar e gravar sem mexer no bloco reenvia a Informação Base e a data já gravadas', () => {
    completarPasso1ComRetoma();
    const informacaoBase = {
      deliberacao: { prazoAnos: 1, prazoMeses: null, prazoDias: null, avaliacaoAmbiental: null, dataDeliberacao: null, textoPublicar: null },
      textoAto: null,
    };
    processoServiceMock.obter.mockReturnValue(
      of(processoDetalhe({ dataAssinatura: '2026-09-02', dadosFormulario: JSON.stringify({ informacaoBase }) })),
    );
    fixture.componentInstance.retomarTentativa();
    fixture.detectChanges();

    fixture.componentInstance.guardarRascunho();

    expect(processoServiceMock.guardarRascunho).toHaveBeenCalledWith(
      'processo-1',
      expect.objectContaining({ dataAssinatura: '2026-09-02', informacaoBase }),
    );
  });

  describe('Procedimento sem Informação Base do Texto do Ato', () => {
    function retomarSemInformacaoBase(overrides: Partial<ProcessoDetalhe> = {}): void {
      completarPasso1ComRetoma();
      processoServiceMock.obter.mockReturnValue(
        of(processoDetalhe({ dataAssinaturaNoFormulario: true, elementosAfetados: ['PECAS_ESCRITAS'], ...overrides })),
      );
      fixture.componentInstance.retomarTentativa();
      fixture.detectChanges();
    }

    const campoData = (): HTMLInputElement | null => fixture.nativeElement.querySelector('app-processo-formulario-inicial input.data-assinatura');

    it('o cartão Informação Geral pede a data de assinatura do ato', () => {
      retomarSemInformacaoBase({ dataAssinatura: '2026-11-10' });

      expect(fixture.componentInstance.formularioRef()!.dataAssinaturaNoFormulario).toBe(true);
      expect(campoData()!.value).toBe('2026-11-10');
    });

    it('sem a data, «Seguinte» não grava nem avança e diz junto do campo o que falta', () => {
      retomarSemInformacaoBase();
      processoServiceMock.guardarRascunho.mockClear();

      fixture.componentInstance.seguinte();
      fixture.detectChanges();

      expect(processoServiceMock.guardarRascunho).not.toHaveBeenCalled();
      expect(fixture.componentInstance.subpagina()).toBe('cabecalho');
      expect(fixture.nativeElement.querySelector('#erro-dataAssinatura').textContent.trim()).toBe('Indique a data de assinatura do ato.');
    });

    it('«Seguinte» nos Documentos reenvia a data do cartão e não a apaga', () => {
      retomarSemInformacaoBase({ dataAssinatura: '2026-11-10' });
      fixture.componentInstance.subpagina.set('documentos');
      fixture.detectChanges();

      fixture.componentInstance.seguinte();

      expect(processoServiceMock.guardarRascunho).toHaveBeenCalledWith(
        'processo-1',
        expect.objectContaining({ dataAssinatura: '2026-11-10' }),
      );
    });

    it('com a Informação Base no Procedimento o cartão não tem a data e a gravação leva a do bloco', () => {
      retomarSemInformacaoBase({ dataAssinaturaNoFormulario: false, dataAssinatura: '2026-09-02' });

      expect(campoData()).toBeNull();
      fixture.componentInstance.guardarRascunho();
      expect(processoServiceMock.guardarRascunho).toHaveBeenCalledWith(
        'processo-1',
        expect.objectContaining({ dataAssinatura: '2026-09-02' }),
      );
    });
  });

  it('voltar do passo 3 repõe os campos INCM gravados', () => {
    completarPasso1();
    fixture.componentInstance.criarProcesso();
    fixture.detectChanges();
    fixture.componentInstance.formularioRef()!.form.patchValue({ sumario: 'Sumário X' });
    fixture.componentInstance.avancarParaPublicar();
    fixture.detectChanges();

    fixture.componentInstance.voltarPasso();
    fixture.detectChanges();

    expect(fixture.componentInstance.formularioRef()!.form.controls.sumario.value).toBe('Sumário X');
  });

  it('grava o rascunho com os campos vazios a null, nunca a string vazia', () => {
    completarPasso1();
    fixture.componentInstance.criarProcesso();
    fixture.detectChanges();
    fixture.componentInstance.formularioRef()!.form.patchValue({ sumario: '', normaLegal: '' });

    fixture.componentInstance.guardarRascunho();

    expect(processoServiceMock.guardarRascunho).toHaveBeenCalledWith(
      'processo-1',
      expect.objectContaining({ sumario: null, normaLegal: null }),
    );
  });

  it('grava o rascunho ao seguir para o passo 3', () => {
    completarPasso1();
    fixture.componentInstance.criarProcesso();
    fixture.detectChanges();
    fixture.componentInstance.avancarParaPublicar();

    expect(processoServiceMock.guardarRascunho).toHaveBeenCalled();
    expect(fixture.componentInstance.passoAtual()).toBe(3);
  });

  it('não passa ao passo 3 quando a gravação do rascunho falha', () => {
    completarPasso1();
    fixture.componentInstance.criarProcesso();
    fixture.detectChanges();
    processoServiceMock.guardarRascunho.mockReturnValue(
      throwError(() => new ApiError('VALIDACAO', 'Payload inválido.', 't1', null)),
    );

    fixture.componentInstance.avancarParaPublicar();

    expect(fixture.componentInstance.passoAtual()).toBe(2);
    expect(fixture.componentInstance.erro()?.codigo).toBe('VALIDACAO');
  });

  // --- passo 3 ---

  it('exige a caixa de confirmação antes de submeter', () => {
    completarPasso1();
    fixture.componentInstance.criarProcesso();
    fixture.detectChanges();
    fixture.componentInstance.avancarParaPublicar();

    fixture.componentInstance.submeter();
    expect(processoServiceMock.submeter).not.toHaveBeenCalled();

    fixture.componentInstance.definirConfirmacao(true);
    fixture.componentInstance.submeter();
    expect(processoServiceMock.submeter).toHaveBeenCalled();
  });

  it('submete com os campos de publicação e mostra o recibo', () => {
    completarPasso1();
    fixture.componentInstance.criarProcesso();
    fixture.detectChanges();
    fixture.componentInstance.formularioRef()!.form.patchValue({
      sumario: 'Decisão de alterar o PDM de Sintra',
      normaLegal: 'Artigo 89.º do RJIGT',
      tipoInformacao: 'TEXTO_INTEGRAL',
    });
    fixture.componentInstance.subpagina.set('documentos');
    fixture.detectChanges();
    fixture.componentInstance.documentosRef()!.form.patchValue({ dataAssinatura: '2026-09-10' });
    processoServiceMock.obter.mockReturnValue(of(processoDetalhe({ versao: 1 })));
    fixture.componentInstance.avancarParaPublicar();
    fixture.componentInstance.definirConfirmacao(true);

    fixture.componentInstance.submeter();
    fixture.detectChanges();

    expect(processoServiceMock.submeter).toHaveBeenCalledWith('processo-1', {
      versao: 1,
      sumario: 'Decisão de alterar o PDM de Sintra',
      dataAssinatura: '2026-09-10',
      normaLegal: 'Artigo 89.º do RJIGT',
      tipoInformacao: 'TEXTO_INTEGRAL',
      publicarSuplemento: undefined,
      dataSuplemento: undefined,
      confirmacoes: ['CONTEUDO_PROCESSO'],
    });
    expect(fixture.nativeElement.querySelector('app-recibo-submissao')).not.toBeNull();
  });

  it('o Resumo mostra os ficheiros remetidos com nome, tipo, data de envio, estado e total', () => {
    documentoServiceMock.listar.mockReturnValue(
      of({
        content: [documento(), documento({ id: 'doc-2', nomeFicheiro: 'planta.dwg', extensao: 'dwg' })],
        page: 0,
        size: 100,
        totalElements: 2,
        totalPages: 1,
      }),
    );
    completarPasso1();
    fixture.componentInstance.criarProcesso();
    fixture.detectChanges();
    fixture.componentInstance.avancarParaPublicar();
    fixture.detectChanges();

    const texto = fixture.nativeElement.textContent as string;
    expect(documentoServiceMock.listar).toHaveBeenCalledWith('processo-1', {}, 0, 100);
    expect(texto).toContain('Ficheiros remetidos');
    expect(texto).toContain('deliberacao.pdf');
    expect(texto).toContain('PDF');
    expect(texto).toContain('DWG');
    expect(texto).toContain('20/10/2026');
    expect(texto).toContain('Recebido');
    expect(texto).toContain('2 ficheiros');
  });

  it('o submeter recusado por documentos em falta mostra cada item no banner, sem Recarregar', () => {
    processoServiceMock.submeter.mockReturnValue(
      throwError(
        () =>
          new ApiError('DOCUMENTOS_OBRIGATORIOS_EM_FALTA', 'Faltam documentos obrigatórios.', 't3', [
            { path: 'documentos.DELIBERACAO_PUB', erro: 'Falta carregar: Deliberação (Publicação).' },
            { path: 'documentos.ATA_DEP', erro: 'Falta carregar: Ata (Depósito).' },
          ]),
      ),
    );
    completarPasso1();
    fixture.componentInstance.criarProcesso();
    fixture.detectChanges();
    fixture.componentInstance.avancarParaPublicar();
    fixture.componentInstance.definirConfirmacao(true);

    fixture.componentInstance.submeter();
    fixture.detectChanges();

    const itens = Array.from(fixture.nativeElement.querySelectorAll('.error-banner li')).map((li) => (li as HTMLElement).textContent);
    expect(itens).toEqual(['Falta carregar: Deliberação (Publicação).', 'Falta carregar: Ata (Depósito).']);
    expect(fixture.nativeElement.textContent).not.toContain('Recarregar');
    expect(fixture.componentInstance.submissaoConcluida()).toBeNull();
  });

  it('o recibo mostra o IDUS relido do detalhe depois de submeter, não o identificador técnico', () => {
    completarPasso1();
    fixture.componentInstance.criarProcesso();
    fixture.detectChanges();
    fixture.componentInstance.avancarParaPublicar();
    fixture.componentInstance.definirConfirmacao(true);
    processoServiceMock.obter.mockClear();
    processoServiceMock.obter.mockReturnValue(of(processoDetalhe({ idus: 'SAIGT.1111.PDM.42.1.1.1.DI.2' })));

    fixture.componentInstance.submeter();
    fixture.detectChanges();

    expect(processoServiceMock.obter).toHaveBeenCalledWith('processo-1');
    const recibo = fixture.nativeElement.querySelector('app-recibo-submissao') as HTMLElement;
    expect(recibo.textContent).toContain('IDUS');
    expect(recibo.textContent).toContain('SAIGT.1111.PDM.42.1.1.1.DI.2');
    expect(recibo.textContent).not.toContain('Identificador do processo');
  });

  it('o recibo mostra o número da submissão relido do detalhe depois de submeter', () => {
    completarPasso1();
    fixture.componentInstance.criarProcesso();
    fixture.detectChanges();
    fixture.componentInstance.avancarParaPublicar();
    fixture.componentInstance.definirConfirmacao(true);
    processoServiceMock.obter.mockReturnValue(of(processoDetalhe({ ocorrencia: 1, submissao: 2, idus: '01.1111.01.001001.02.90001.01.031.02' })));

    fixture.componentInstance.submeter();
    fixture.detectChanges();

    const recibo = fixture.nativeElement.querySelector('app-recibo-submissao') as HTMLElement;
    const rotulo = Array.from(recibo.querySelectorAll('dt')).find((dt) => dt.textContent!.trim() === 'Submissão do processo n.º');
    expect(rotulo!.nextElementSibling!.textContent!.trim()).toBe('2');
  });

  it('o recibo aparece sem a linha do IDUS quando o detalhe não se lê depois de submeter', () => {
    completarPasso1();
    fixture.componentInstance.criarProcesso();
    fixture.detectChanges();
    fixture.componentInstance.avancarParaPublicar();
    fixture.componentInstance.definirConfirmacao(true);
    processoServiceMock.obter.mockReturnValue(throwError(() => new ApiError('ERRO_INTERNO', 'Falhou.', 't9', null)));

    fixture.componentInstance.submeter();
    fixture.detectChanges();

    const recibo = fixture.nativeElement.querySelector('app-recibo-submissao') as HTMLElement;
    expect(recibo).not.toBeNull();
    expect(recibo.textContent).not.toContain('IDUS');
    expect(fixture.componentInstance.aSubmeter()).toBe(false);
  });

  it('o recibo mostra os ficheiros remetidos do processo submetido', () => {
    completarPasso1();
    fixture.componentInstance.criarProcesso();
    fixture.detectChanges();
    fixture.componentInstance.avancarParaPublicar();
    fixture.componentInstance.definirConfirmacao(true);

    fixture.componentInstance.submeter();
    fixture.detectChanges();

    const recibo = fixture.nativeElement.querySelector('app-recibo-submissao') as HTMLElement;
    expect(recibo.textContent).toContain('Ficheiros remetidos');
    expect(recibo.textContent).toContain('deliberacao.pdf');
    expect(recibo.textContent).toContain('Total de ficheiros remetidos');
  });

  describe('ecrãs do Figma da Elaboração', () => {
    it('o Tipo de Processo agrupa os IGT e mostra a descrição de cada tipo', () => {
      fixture.detectChanges();

      const pagina = fixture.nativeElement.querySelector('app-pagina-tipo-igt') as HTMLElement;
      expect(pagina.querySelector('.grupo-rotulo')!.textContent!.trim()).toBe('Processo com associação a IGT');
      expect(pagina.textContent).toContain('Instrumento de gestão territorial de âmbito municipal');
    });

    it('com Novo IGT escolhido avisa que vai ser criada uma dinâmica de elaboração; com IGT Existente não', () => {
      fixture.componentInstance.selecionarTipo('1');
      fixture.componentInstance.avancar();
      fixture.componentInstance.escolherOrigem('novo');
      fixture.detectChanges();

      const faixa = fixture.nativeElement.querySelector('.faixa-dinamica') as HTMLElement;
      expect(faixa.textContent!.replace(/\s+/g, ' ').trim()).toBe('Vai ser criada uma nova dinâmica de elaboração');
      expect(faixa.querySelector('strong')!.textContent).toBe('dinâmica de elaboração');

      fixture.componentInstance.escolherOrigem('existente');
      fixture.detectChanges();
      expect(fixture.nativeElement.querySelector('.faixa-dinamica')).toBeNull();
    });

    it('a Ação mostra só as opções disponíveis', () => {
      instrumentoServiceMock.disponivel.mockReturnValue(of(disponibilidade({ dinamicasDisponiveis: [] })));
      fixture.componentInstance.selecionarTipo('1');
      fixture.componentInstance.selecionarInstrumento('instrumento-1');
      fixture.componentInstance.paginaInicial.set('acao');
      fixture.detectChanges();

      const titulos = Array.from(fixture.nativeElement.querySelectorAll('app-pagina-acao .opcao-titulo')).map((t) =>
        (t as HTMLElement).textContent!.trim(),
      );
      expect(titulos).toEqual(['Continuar Fase da Dinâmica']);
    });

    it('a página de continuar chama-se Continuar Fase da Dinâmica', () => {
      fixture.componentInstance.selecionarTipo('1');
      fixture.componentInstance.selecionarInstrumento('instrumento-1');
      fixture.componentInstance.paginaInicial.set('continuar');
      fixture.detectChanges();

      expect(fixture.nativeElement.querySelector('app-pagina-continuar-dinamica h2')!.textContent!.trim()).toBe(
        'Continuar Fase da Dinâmica',
      );
    });
  });

  describe('formulário conforme o Figma da Elaboração', () => {
    const pergunta = (): HTMLElement | null => fixture.nativeElement.querySelector('app-processo-formulario-inicial .geoconform');
    const opcoes = (): HTMLInputElement[] =>
      Array.from(fixture.nativeElement.querySelectorAll('app-processo-formulario-inicial .geoconform input[type="radio"]'));
    const pedidoGravar = (): { comGeoconform?: boolean | null } => processoServiceMock.guardarRascunho.mock.calls[0][1];

    function abrirFormulario(detalhe: Partial<ProcessoDetalhe>): void {
      processoServiceMock.obter.mockReturnValue(of(processoDetalhe(detalhe)));
      completarPasso1();
      fixture.componentInstance.criarProcesso();
      fixture.detectChanges();
    }

    it('pergunta pelo GeoConform no fim da Informação Geral quando o Procedimento a exige, e grava a resposta', () => {
      abrirFormulario({ exigeOpcaoGeoconform: true });

      expect(pergunta()!.querySelector('legend')!.textContent).toContain(
        'Vai fazer uso da plataforma Geoconform para a validação da informação e emissão das plantas?',
      );
      expect(opcoes()).toHaveLength(2);
      opcoes()[1].click();
      fixture.detectChanges();
      fixture.componentInstance.guardarRascunho();

      expect(pedidoGravar().comGeoconform).toBe(false);
    });

    it('parte da resposta gravada na Dinâmica', () => {
      abrirFormulario({ exigeOpcaoGeoconform: true, comGeoconform: true });

      expect(opcoes().map((opcao) => opcao.checked)).toEqual([true, false]);
    });

    it('sem a pergunta no Procedimento, não a mostra nem envia `comGeoconform`', () => {
      abrirFormulario({ exigeOpcaoGeoconform: false });

      expect(pergunta()).toBeNull();
      fixture.componentInstance.guardarRascunho();
      expect('comGeoconform' in pedidoGravar()).toBe(false);
    });

    it('com SE_PECAS_GRAFICAS, «Seguinte» não avança sem resposta depois de assinalar «Peças gráficas»', () => {
      abrirFormulario({ perguntaGeoconform: 'SE_PECAS_GRAFICAS', exigeOpcaoGeoconform: false });
      expect(pergunta()).toBeNull();
      const graficas = Array.from(fixture.nativeElement.querySelectorAll('app-processo-formulario-inicial label.campo-checkbox') as NodeListOf<HTMLElement>)
        .find((opcao) => opcao.textContent!.trim() === 'Peças gráficas (Plantas)')!
        .querySelector('input')!;
      graficas.click();
      fixture.detectChanges();
      expect(pergunta()).not.toBeNull();

      fixture.componentInstance.seguinte();
      fixture.detectChanges();

      expect(processoServiceMock.guardarRascunho).not.toHaveBeenCalled();
      expect(fixture.componentInstance.subpagina()).toBe('cabecalho');
      expect(pergunta()!.querySelector('#erro-comGeoconform')!.textContent!.trim()).toBe('Indique se utiliza o GeoConform.');

      opcoes()[1].click();
      fixture.detectChanges();
      fixture.componentInstance.seguinte();

      expect(pedidoGravar()).toMatchObject({ elementosAfetados: ['PECAS_GRAFICAS'], comGeoconform: false });
    });

    it('mantém a pergunta dos elementos afetados em qualquer Dinâmica de um plano (EF v1.5)', () => {
      abrirFormulario({ tipoDinamica: 'ELABORACAO' });

      expect(fixture.nativeElement.querySelector('app-processo-formulario-inicial .elementos legend').textContent).toContain(
        'A alteração incide sobre que elementos fundamentais?',
      );
    });

    it('sem nenhum elemento afetado, «Seguinte» não grava nem passa aos documentos e mostra a mensagem junto dos elementos', () => {
      abrirFormulario({});

      fixture.componentInstance.seguinte();
      fixture.detectChanges();

      expect(processoServiceMock.guardarRascunho).not.toHaveBeenCalled();
      expect(fixture.componentInstance.subpagina()).toBe('cabecalho');
      expect(fixture.nativeElement.querySelector('fieldset.elementos #erro-elementosAfetados').textContent.trim()).toBe(
        'Escolha pelo menos um elemento fundamental.',
      );
    });

    it('«Guardar rascunho» grava o formulário incompleto sem as mensagens do «Seguinte»', () => {
      abrirFormulario({});

      fixture.componentInstance.guardarRascunho();
      fixture.detectChanges();

      expect(processoServiceMock.guardarRascunho).toHaveBeenCalledTimes(1);
      expect(fixture.nativeElement.querySelector('#erro-elementosAfetados')).toBeNull();
    });

    it('na Publicação da Alteração, com «Peças gráficas» e a planta nova sem resposta, «Seguinte» não avança para as Plantas', () => {
      instrumentoServiceMock.disponivel.mockReturnValue(
        of(disponibilidade({ dinamicasEmCurso: [dinamicaEmCurso({ procedimentosDisponiveis: [procedimento({ formulario: 'PUBLICACAO_ALTERACAO' })] })] })),
      );
      abrirFormulario({ formulario: 'PUBLICACAO_ALTERACAO', comGeoconform: false, exigePlantasEMetadados: true });
      Array.from(fixture.nativeElement.querySelectorAll('app-processo-formulario-inicial label.campo-checkbox') as NodeListOf<HTMLElement>)
        .find((opcao) => opcao.textContent!.trim() === 'Peças gráficas (Plantas)')!
        .querySelector('input')!
        .click();
      fixture.detectChanges();

      fixture.componentInstance.seguinte();
      fixture.detectChanges();

      expect(processoServiceMock.guardarRascunho).not.toHaveBeenCalled();
      expect(fixture.componentInstance.subpagina()).toBe('cabecalho');
      expect(fixture.nativeElement.querySelector('#erro-introduzPlantaNova').textContent.trim()).toBe(
        'Indique se a alteração introduz alguma planta nova.',
      );

      (fixture.nativeElement.querySelectorAll('fieldset.planta-nova input[type="radio"]')[1] as HTMLInputElement).click();
      fixture.detectChanges();
      fixture.componentInstance.seguinte();
      fixture.detectChanges();

      expect(processoServiceMock.guardarRascunho).toHaveBeenCalledTimes(1);
      expect(fixture.componentInstance.subpagina()).toBe('plantas');
    });

    it('a página dos documentos tem a Nota do RJIGT, o AVISO antes de Guardar e os acordeões sem títulos de grupo', () => {
      abrirFormulario({});
      fixture.componentInstance.subpagina.set('documentos');
      fixture.detectChanges();

      const texto = fixture.nativeElement.textContent as string;
      expect(fixture.nativeElement.querySelector('.nota-rjigt').textContent).toContain('artigos 191º e 194º do RJIGT');
      expect(fixture.nativeElement.querySelector('.aviso-documentos').textContent).toContain(
        'os últimos ficheiros introduzidos serão removidos e terão que ser carregados novamente',
      );
      expect(texto).not.toContain('Publicação INCM e Depósito DGT');
    });
  });

  it('volta ao Painel Principal a partir do recibo', () => {
    const router = TestBed.inject(Router);
    const navegar = vi.spyOn(router, 'navigateByUrl');

    fixture.componentInstance.voltarAoMenu();

    expect(navegar).toHaveBeenCalledWith('/painel');
  });

  describe('aberto a partir do painel do Instrumento', () => {
    const INSTRUMENTO = '3f2b8c1e-5d4a-4b7c-9e0f-1a2b3c4d5e6f';
    const DINAMICA = '9a8b7c6d-5e4f-4a3b-8c2d-1e0f9a8b7c6d';
    const COM_DINAMICA = `instrumentoId=${INSTRUMENTO}&dinamicaId=${DINAMICA}`;

    const disponivelComDinamica = (): InstrumentoDisponibilidade =>
      disponibilidade({
        dinamicasEmCurso: [
          dinamicaEmCurso({
            dinamicaId: DINAMICA,
            procedimentosDisponiveis: [
              procedimento(),
              procedimento({ instrumentoProcedimentoConfigId: 215, tipoProcedimento: 'DISCUSSAO_PUBLICA', ordem: 2 }),
            ],
          }),
        ],
      });

    async function abrirPeloPainel(consulta: string, disponivel = disponivelComDinamica()): Promise<CriarProcessoComponent> {
      instrumentoServiceMock.painel.mockReturnValue(of({ ...painel(), id: INSTRUMENTO }));
      instrumentoServiceMock.disponivel.mockReturnValue(of(disponivel));
      await TestBed.inject(Router).navigateByUrl(`/?${consulta}`);
      fixture = TestBed.createComponent(CriarProcessoComponent);
      fixture.detectChanges();
      return fixture.componentInstance;
    }

    it('com Instrumento, Dinâmica e Procedimento abre no passo Continuar com tudo já escolhido', async () => {
      const componente = await abrirPeloPainel(`${COM_DINAMICA}&procedimentoId=215`);

      expect(instrumentoServiceMock.painel).toHaveBeenCalledWith(INSTRUMENTO);
      expect(instrumentoServiceMock.disponivel).toHaveBeenCalledWith(INSTRUMENTO);
      expect(componente.tipoEscolhidoId()).toBe(1);
      expect(componente.instrumentoEscolhidoId()).toBe(INSTRUMENTO);
      expect(componente.paginaInicial()).toBe('continuar');
      expect(componente.modo()).toBe('continuar');
      expect(componente.dinamicaEscolhida()?.dinamicaId).toBe(DINAMICA);
      expect(componente.procedimentoEscolhido()?.instrumentoProcedimentoConfigId).toBe(215);
      expect(componente.avisoProcedimentoIndisponivel()).toBe(false);
      expect(componente.passoAtual()).toBe(1);
    });

    it('acrescenta o Instrumento à lista para o plano aparecer escolhido, com o nome do tipo', async () => {
      const componente = await abrirPeloPainel(COM_DINAMICA);

      expect(componente.instrumentoEscolhido()?.designacao).toBe('PDM de Sintra');
      expect(componente.nomeTipoInstrumento()).toBe('Plano Diretor Municipal');
      expect(componente.instrumentos().filter((item) => item.id === INSTRUMENTO)).toHaveLength(1);
    });

    it('não duplica o Instrumento que já está na página carregada', async () => {
      instrumentoServiceMock.listar.mockReturnValue(
        of({ content: [instrumento({ id: INSTRUMENTO })], page: 0, size: 100, totalElements: 1, totalPages: 1 }),
      );

      const componente = await abrirPeloPainel(COM_DINAMICA);

      expect(componente.instrumentos().filter((item) => item.id === INSTRUMENTO)).toHaveLength(1);
    });

    it('sem procedimentoId fica escolhido o seguinte da Dinâmica, o de maior ordem', async () => {
      const componente = await abrirPeloPainel(COM_DINAMICA);

      expect(componente.procedimentoEscolhido()?.instrumentoProcedimentoConfigId).toBe(215);
      expect(componente.avisoProcedimentoIndisponivel()).toBe(false);
    });

    it('só com o Instrumento abre na página da Ação, com o tipo e o plano já escolhidos', async () => {
      const componente = await abrirPeloPainel(`instrumentoId=${INSTRUMENTO}`);

      expect(componente.paginaInicial()).toBe('acao');
      expect(componente.tipoEscolhidoId()).toBe(1);
      expect(componente.instrumentoEscolhidoId()).toBe(INSTRUMENTO);
      expect(componente.dinamicaEscolhida()).toBeNull();
    });

    it('com um Procedimento que já não está disponível mostra o aviso e deixa escolher normalmente', async () => {
      const componente = await abrirPeloPainel(`${COM_DINAMICA}&procedimentoId=999`);

      expect(fixture.nativeElement.textContent).toContain('O Procedimento escolhido já não está disponível. Escolha outro.');
      expect(componente.procedimentoEscolhido()?.instrumentoProcedimentoConfigId).toBe(215);

      componente.selecionarProcedimento('214');
      fixture.detectChanges();

      expect(fixture.nativeElement.textContent).not.toContain('já não está disponível');
    });

    it('com uma Dinâmica que o disponivel não oferece ignora-a e abre na Ação', async () => {
      const componente = await abrirPeloPainel(`instrumentoId=${INSTRUMENTO}&dinamicaId=1c2d3e4f-0a1b-4c2d-8e3f-4a5b6c7d8e9f&procedimentoId=214`);

      expect(componente.paginaInicial()).toBe('acao');
      expect(componente.dinamicaEscolhida()).toBeNull();
      expect(componente.avisoProcedimentoIndisponivel()).toBe(false);
    });

    it('ignora uma Dinâmica caducada: continuá-la não tem ecrã', async () => {
      const componente = await abrirPeloPainel(
        COM_DINAMICA,
        disponibilidade({ dinamicasEmCurso: [dinamicaEmCurso({ dinamicaId: DINAMICA, estado: 'CADUCADA' })] }),
      );

      expect(componente.paginaInicial()).toBe('acao');
      expect(componente.dinamicaEscolhida()).toBeNull();
    });

    it('com o Instrumento mal formado ignora a entrada e abre no passo inicial, sem erro', async () => {
      const componente = await abrirPeloPainel(`instrumentoId=nao-e-um-uuid&dinamicaId=${DINAMICA}&procedimentoId=214`);

      expect(instrumentoServiceMock.painel).not.toHaveBeenCalled();
      expect(componente.paginaInicial()).toBe('tipo');
      expect(componente.tipoEscolhidoId()).toBeNull();
      expect(componente.erro()).toBeNull();
    });

    it.each(['abc', '0', '-3', '1.5', ''])('com o procedimentoId %j ignora-o em vez de avisar', async (valor) => {
      const componente = await abrirPeloPainel(`${COM_DINAMICA}&procedimentoId=${valor}`);

      expect(componente.procedimentoEscolhido()?.instrumentoProcedimentoConfigId).toBe(215);
      expect(componente.avisoProcedimentoIndisponivel()).toBe(false);
    });

    it('com a Dinâmica mal formada abre na Ação em vez de a escolher', async () => {
      const componente = await abrirPeloPainel(`instrumentoId=${INSTRUMENTO}&dinamicaId=xyz&procedimentoId=214`);

      expect(componente.paginaInicial()).toBe('acao');
      expect(componente.dinamicaEscolhida()).toBeNull();
    });

    it('o processoId tem precedência e os novos parâmetros são ignorados', async () => {
      const componente = await abrirPeloPainel(`processoId=processo-1&${COM_DINAMICA}&procedimentoId=215`);

      expect(componente.passoAtual()).toBe(3);
      expect(instrumentoServiceMock.painel).not.toHaveBeenCalled();
      expect(componente.instrumentoEscolhidoId()).toBeNull();
    });

    it('se o Instrumento não existe mostra o erro e abre no passo inicial', async () => {
      instrumentoServiceMock.painel.mockReturnValue(throwError(() => new ApiError('NAO_ENCONTRADO', 'Instrumento não encontrado.', 't', null)));
      await TestBed.inject(Router).navigateByUrl(`/?${COM_DINAMICA}`);
      fixture = TestBed.createComponent(CriarProcessoComponent);
      fixture.detectChanges();

      expect(fixture.componentInstance.erro()?.codigo).toBe('NAO_ENCONTRADO');
      expect(fixture.componentInstance.paginaInicial()).toBe('tipo');
    });

    it('o assistente já não pede o tipo de ato e continua a pedir a confirmação antes de criar o processo', async () => {
      const componente = await abrirPeloPainel(COM_DINAMICA);

      expect(componente.podeAvancarDoPasso1()).toBe(true);
      expect(processoServiceMock.criar).not.toHaveBeenCalled();
    });
  });

  describe('aberto a partir do detalhe (?processoId=)', () => {
    async function abrirPeloDetalhe(): Promise<CriarProcessoComponent> {
      await TestBed.inject(Router).navigateByUrl('/?processoId=processo-1');
      fixture = TestBed.createComponent(CriarProcessoComponent);
      fixture.detectChanges();
      return fixture.componentInstance;
    }

    it('abre direto no passo 3 com o resumo lido do Processo gravado', async () => {
      processoServiceMock.obter.mockReturnValue(
        of(
          processoDetalhe({
            sumario: 'Decisão de alterar',
          }),
        ),
      );

      const componente = await abrirPeloDetalhe();

      expect(processoServiceMock.obter).toHaveBeenCalledWith('processo-1');
      expect(componente.passoAtual()).toBe(3);
      expect(componente.referenciaPlano()).toBe('PDM de Sintra');
      expect(componente.nomeTipoInstrumento()).toBe('Plano Diretor Municipal');
      expect(componente.tipoProcedimento()).toBe('Decisão de início');
      expect(componente.parteDr()).toBe('2.ª Série - H');
      expect(componente.nomeTipoAtoGravado()).toBe('Despacho');
      expect(componente.publicacaoIncm()?.sumario).toBe('Decisão de alterar');
    });

    it('submete com os campos de publicação já gravados', async () => {
      processoServiceMock.obter.mockReturnValue(of(processoDetalhe({ sumario: 'Decisão de alterar', normaLegal: 'Art. 89.º' })));
      const componente = await abrirPeloDetalhe();

      componente.definirConfirmacao(true);
      componente.submeter();

      expect(processoServiceMock.submeter).toHaveBeenCalledWith(
        'processo-1',
        expect.objectContaining({ versao: 0, sumario: 'Decisão de alterar', normaLegal: 'Art. 89.º', confirmacoes: ['CONTEUDO_PROCESSO'] }),
      );
    });

    describe('confirmação do conteúdo do processo', () => {
      const MENSAGEM = 'Confirmação obrigatória.';

      function caixaConfirmacao(): HTMLInputElement {
        return fixture.nativeElement.querySelector('#confirmacao-conteudo-processo') as HTMLInputElement;
      }

      async function submeterRecusadaSemConfirmacao(): Promise<CriarProcessoComponent> {
        processoServiceMock.submeter.mockReturnValue(
          throwError(
            () =>
              new ApiError('CONFIRMACAO_EM_FALTA', 'Confirme os campos assinalados.', 't7', [
                { path: 'confirmacoes.CONTEUDO_PROCESSO', erro: MENSAGEM },
              ]),
          ),
        );
        const componente = await abrirPeloDetalhe();
        componente.definirConfirmacao(true);
        fixture.detectChanges();
        botaoSubmeter().click();
        fixture.detectChanges();
        await fixture.whenStable();
        fixture.detectChanges();
        return componente;
      }

      it('a caixa marcada leva a confirmação no pedido de submissão', async () => {
        const componente = await abrirPeloDetalhe();
        fixture.detectChanges();
        caixaConfirmacao().click();
        fixture.detectChanges();

        expect(componente.confirmado()).toBe(true);
        botaoSubmeter().click();

        expect(processoServiceMock.submeter.mock.calls[0][1].confirmacoes).toEqual(['CONTEUDO_PROCESSO']);
      });

      it('com `CONFIRMACAO_EM_FALTA` mostra o erro junto da caixa, anunciado, desmarca-a e põe-lhe o foco, fora do banner', async () => {
        const componente = await submeterRecusadaSemConfirmacao();

        expect(componente.passoAtual()).toBe(3);
        const erro = fixture.nativeElement.querySelector('#erro-confirmacao-conteudo-processo') as HTMLElement;
        expect(erro.textContent?.trim()).toBe(MENSAGEM);
        expect(erro.getAttribute('role')).toBe('alert');
        const caixa = caixaConfirmacao();
        expect(caixa.getAttribute('aria-describedby')).toBe('erro-confirmacao-conteudo-processo');
        expect(caixa.getAttribute('aria-invalid')).toBe('true');
        expect(caixa.checked).toBe(false);
        expect(componente.confirmado()).toBe(false);
        expect(document.activeElement).toBe(caixa);
        expect(botaoSubmeter().disabled).toBe(true);
        expect(fixture.nativeElement.querySelector('app-error-banner')).toBeNull();
      });

      it('voltar a marcar a caixa limpa o erro e permite submeter de novo', async () => {
        const componente = await submeterRecusadaSemConfirmacao();

        caixaConfirmacao().click();
        fixture.detectChanges();

        expect(componente.erroConfirmacao()).toBeNull();
        expect(fixture.nativeElement.querySelector('#erro-confirmacao-conteudo-processo')).toBeNull();
        expect(caixaConfirmacao().getAttribute('aria-describedby')).toBeNull();
        expect(botaoSubmeter().disabled).toBe(false);
      });
    });

    describe('declaração de término na Revogação do IGT', () => {
      const MENSAGEM = 'Declare que a revogação termina os procedimentos em curso.';
      const ALTERACAO = { dinamicaId: 'din-alt', numeroDinamica: 'PDM-0004', tipoDinamica: 'Alteração', descritivo: 'Alteração ao regulamento' };

      function caixaConfirmacao(): HTMLInputElement {
        return fixture.nativeElement.querySelector('#confirmacao-conteudo-processo') as HTMLInputElement;
      }

      function caixaDeclaracao(): HTMLInputElement | null {
        return fixture.nativeElement.querySelector('#declaracao-termino') as HTMLInputElement | null;
      }

      async function abrirRevogacao(): Promise<CriarProcessoComponent> {
        processoServiceMock.obter.mockReturnValue(of(processoDetalhe({ dinamicasATerminar: [ALTERACAO] })));
        const componente = await abrirPeloDetalhe();
        fixture.detectChanges();
        return componente;
      }

      it('mostra a declaração com as Dinâmicas em curso e só deixa submeter com as duas caixas marcadas', async () => {
        const componente = await abrirRevogacao();

        const declaracao = fixture.nativeElement.querySelector('app-declaracao-termino') as HTMLElement;
        expect(declaracao.querySelector('legend')?.textContent?.trim()).toBe('Declaração de término');
        expect(declaracao.querySelector('li')?.textContent?.trim()).toBe('PDM-0004 · Alteração · Alteração ao regulamento');
        caixaConfirmacao().click();
        fixture.detectChanges();
        expect(botaoSubmeter().disabled).toBe(true);

        caixaDeclaracao()!.click();
        fixture.detectChanges();
        expect(componente.declaracaoTermino()).toBe(true);
        expect(botaoSubmeter().disabled).toBe(false);
        botaoSubmeter().click();

        expect(processoServiceMock.submeter.mock.calls[0][1].confirmacoes).toEqual(['CONTEUDO_PROCESSO', 'DECLARACAO_TERMINO']);
      });

      it('noutro processo a declaração não aparece e o pedido não a leva', async () => {
        await abrirPeloDetalhe();
        fixture.detectChanges();

        expect(fixture.nativeElement.querySelector('app-declaracao-termino')).toBeNull();
        caixaConfirmacao().click();
        fixture.detectChanges();
        botaoSubmeter().click();

        expect(processoServiceMock.submeter.mock.calls[0][1].confirmacoes).toEqual(['CONTEUDO_PROCESSO']);
      });

      it('com `CONFIRMACAO_EM_FALTA` em `confirmacoes` relê o detalhe e põe o foco na caixa com a mensagem, fora do banner', async () => {
        const componente = await abrirRevogacao();
        const outra = { dinamicaId: 'din-nova', numeroDinamica: 'PDM-0005', tipoDinamica: 'Revisão', descritivo: 'Revisão nova' };
        processoServiceMock.submeter.mockReturnValue(
          throwError(() => new ApiError('CONFIRMACAO_EM_FALTA', MENSAGEM, 't9', [{ path: 'confirmacoes', erro: MENSAGEM }])),
        );
        processoServiceMock.obter.mockReturnValue(of(processoDetalhe({ dinamicasATerminar: [ALTERACAO, outra] })));
        processoServiceMock.obter.mockClear();
        caixaConfirmacao().click();
        caixaDeclaracao()!.click();
        fixture.detectChanges();

        botaoSubmeter().click();
        fixture.detectChanges();
        await fixture.whenStable();
        fixture.detectChanges();

        expect(processoServiceMock.obter).toHaveBeenCalledWith('processo-1');
        expect(fixture.nativeElement.querySelectorAll('app-declaracao-termino li')).toHaveLength(2);
        const erro = fixture.nativeElement.querySelector('#erro-declaracao-termino') as HTMLElement;
        expect(erro.textContent?.trim()).toBe(MENSAGEM);
        expect(erro.getAttribute('role')).toBe('alert');
        const caixa = caixaDeclaracao()!;
        expect(caixa.getAttribute('aria-describedby')).toBe('erro-declaracao-termino');
        expect(caixa.checked).toBe(false);
        expect(document.activeElement).toBe(caixa);
        expect(componente.confirmado()).toBe(true);
        expect(componente.erroConfirmacao()).toBeNull();
        expect(botaoSubmeter().disabled).toBe(true);
        expect(fixture.nativeElement.querySelector('app-error-banner')).toBeNull();

        caixa.click();
        fixture.detectChanges();
        expect(fixture.nativeElement.querySelector('#erro-declaracao-termino')).toBeNull();
        expect(botaoSubmeter().disabled).toBe(false);
      });
    });

    function botoes(): string[] {
      return Array.from(fixture.nativeElement.querySelectorAll('button')).map((b) => (b as HTMLElement).textContent!.trim());
    }

    it('mostra Submeter quando `submeter` está em acoesDisponiveis', async () => {
      processoServiceMock.obter.mockReturnValue(of(processoDetalhe({ acoesDisponiveis: ['submeter'] })));

      await abrirPeloDetalhe();

      expect(botoes()).toContain('Submeter');
      expect(fixture.nativeElement.querySelector('input[type="checkbox"]')).not.toBeNull();
    });

    function botaoSubmeter(): HTMLButtonElement {
      const botoesDoEcra = Array.from(fixture.nativeElement.querySelectorAll('button')) as HTMLButtonElement[];
      return botoesDoEcra.find((botao) => botao.textContent?.trim() === 'Submeter')!;
    }

    it('com `podeSubmeter` falso o botão Submeter fica desativado e a mensagem explica porquê, mesmo com a caixa marcada', async () => {
      processoServiceMock.obter.mockReturnValue(of(processoDetalhe({ podeSubmeter: false })));
      const componente = await abrirPeloDetalhe();

      componente.definirConfirmacao(true);
      fixture.detectChanges();

      expect(botaoSubmeter().disabled).toBe(true);
      expect(botaoSubmeter().getAttribute('aria-describedby')).toBe('aviso-pode-submeter');
      expect(fixture.nativeElement.querySelector('#aviso-pode-submeter').textContent).toContain(
        'Preencha os dados obrigatórios do formulário antes de submeter.',
      );
      componente.submeter();
      expect(processoServiceMock.submeter).not.toHaveBeenCalled();
    });

    it('com `podeSubmeter` verdadeiro o botão Submeter só fica ativo com a caixa marcada, e então submete', async () => {
      const componente = await abrirPeloDetalhe();

      expect(botaoSubmeter().disabled).toBe(true);
      expect(fixture.nativeElement.querySelector('#aviso-pode-submeter')).toBeNull();

      componente.definirConfirmacao(true);
      fixture.detectChanges();

      expect(botaoSubmeter().disabled).toBe(false);
      botaoSubmeter().click();
      expect(processoServiceMock.submeter).toHaveBeenCalledTimes(1);
    });

    describe('pendências da submissão no Resumo', () => {
      const PENDENCIAS: PendenciaSubmissao[] = [
        { pagina: 'DOCUMENTOS', campo: 'TEXTO_ATO_PUB', erro: 'Falta carregar: Texto do Ato.' },
        {
          pagina: 'METADADOS',
          campo: 'metadados.conjunto.c1',
          erro: 'Planta de condicionantes: falta a Folha N na tabela Plantas SAIGT SNIT.',
        },
      ];

      function ligacao(nome: string): HTMLButtonElement {
        const botoesDoEcra = Array.from(fixture.nativeElement.querySelectorAll('button')) as HTMLButtonElement[];
        return botoesDoEcra.find((botao) => botao.textContent?.trim() === nome)!;
      }

      it('com pendências em duas páginas, o aviso agrupa-as com uma ligação por página e «Submeter» fica desativado', async () => {
        processoServiceMock.obter.mockReturnValue(
          of(processoDetalhe({ podeSubmeter: false, exigePlantasEMetadados: true, pendenciasSubmissao: PENDENCIAS })),
        );
        const componente = await abrirPeloDetalhe();
        componente.definirConfirmacao(true);
        fixture.detectChanges();

        const aviso = fixture.nativeElement.querySelector('#aviso-pode-submeter') as HTMLElement;
        expect(aviso.getAttribute('role')).toBe('alert');
        expect(aviso.textContent).toContain('Para submeter, falta:');
        expect(aviso.textContent).not.toContain('Preencha os dados obrigatórios');
        const grupos = Array.from(aviso.querySelectorAll('.grupo-titulo')).map((titulo) => titulo.textContent?.trim());
        expect(grupos).toEqual(['Documentos', 'Metadados']);
        expect(Array.from(aviso.querySelectorAll('ul li')).map((item) => item.textContent?.trim())).toEqual([
          'Falta carregar: Texto do Ato.',
          'Planta de condicionantes: falta a Folha N na tabela Plantas SAIGT SNIT.',
        ]);
        expect(ligacao('Ir para Documentos')).toBeDefined();
        expect(ligacao('Ir para Metadados')).toBeDefined();
        expect(botaoSubmeter().disabled).toBe(true);
        expect(botaoSubmeter().getAttribute('aria-describedby')).toBe('aviso-pode-submeter');
      });

      it('«Ir para Metadados» abre a página dos Metadados do assistente', async () => {
        processoServiceMock.obter.mockReturnValue(
          of(processoDetalhe({ podeSubmeter: false, exigePlantasEMetadados: true, pendenciasSubmissao: PENDENCIAS })),
        );
        const componente = await abrirPeloDetalhe();

        ligacao('Ir para Metadados').click();
        fixture.detectChanges();
        await fixture.whenStable();
        fixture.detectChanges();

        expect(componente.passoAtual()).toBe(2);
        expect(componente.subpagina()).toBe('metadados');
        expect(fixture.nativeElement.querySelector('app-processo-metadados')).not.toBeNull();
        expect(fixture.nativeElement.querySelector('app-pagina-resumo')).toBeNull();
      });

      it('«Ir para Documentos» abre a página dos Documentos', async () => {
        processoServiceMock.obter.mockReturnValue(
          of(processoDetalhe({ podeSubmeter: false, exigePlantasEMetadados: true, pendenciasSubmissao: PENDENCIAS })),
        );
        const componente = await abrirPeloDetalhe();

        ligacao('Ir para Documentos').click();
        fixture.detectChanges();

        expect(componente.passoAtual()).toBe(2);
        expect(componente.subpagina()).toBe('documentos');
      });

      it('sem pendências não há aviso e «Submeter» fica disponível depois da confirmação', async () => {
        processoServiceMock.obter.mockReturnValue(of(processoDetalhe({ podeSubmeter: true, pendenciasSubmissao: [] })));
        const componente = await abrirPeloDetalhe();

        expect(fixture.nativeElement.querySelector('#aviso-pode-submeter')).toBeNull();
        expect(botaoSubmeter().disabled).toBe(true);
        componente.definirConfirmacao(true);
        fixture.detectChanges();
        expect(botaoSubmeter().disabled).toBe(false);
      });
    });

    describe('opção GeoConform', () => {
      const pedidoSubmeter = (): { comGeoconform?: boolean } => processoServiceMock.submeter.mock.calls[0][1];

      it('a submissão nunca leva `comGeoconform`: a resposta já está gravada na Dinâmica', async () => {
        processoServiceMock.obter.mockReturnValue(of(processoDetalhe({ exigeOpcaoGeoconform: true, comGeoconform: true })));
        const componente = await abrirPeloDetalhe();

        expect(fixture.nativeElement.querySelector('app-pagina-resumo .geoconform')).toBeNull();
        componente.definirConfirmacao(true);
        fixture.detectChanges();
        expect(botaoSubmeter().disabled).toBe(false);
        componente.submeter();
        expect(pedidoSubmeter().comGeoconform).toBeUndefined();
      });

      it('com `PLANTAS_METADADOS_EM_FALTA` nas plantas volta à página das Plantas e mostra o que falta', async () => {
        processoServiceMock.obter.mockReturnValue(of(processoDetalhe({ exigePlantasEMetadados: true })));
        processoServiceMock.submeter.mockReturnValue(
          throwError(
            () =>
              new ApiError('PLANTAS_METADADOS_EM_FALTA', 'Faltam as plantas.', 't6', [
                { path: 'plantas.cartograma', erro: 'Carregue o cartograma das folhas.' },
                { path: 'metadados.resumo', erro: 'Preencha este campo da ficha de metadados.' },
              ]),
          ),
        );
        const componente = await abrirPeloDetalhe();
        componente.definirConfirmacao(true);

        componente.submeter();
        fixture.detectChanges();

        expect(componente.passoAtual()).toBe(2);
        expect(componente.subpagina()).toBe('plantas');
        expect(fixture.nativeElement.querySelector('app-processo-plantas .pendencias').textContent).toContain(
          'Carregue o cartograma das folhas.',
        );
      });

      it('com `PLANTAS_METADADOS_EM_FALTA` só nos metadados vai à página dos Metadados', async () => {
        processoServiceMock.obter.mockReturnValue(of(processoDetalhe({ exigePlantasEMetadados: true })));
        processoServiceMock.submeter.mockReturnValue(
          throwError(
            () =>
              new ApiError('PLANTAS_METADADOS_EM_FALTA', 'Faltam os metadados.', 't7', [
                { path: 'metadados.resumo', erro: 'Preencha este campo da ficha de metadados.' },
              ]),
          ),
        );
        const componente = await abrirPeloDetalhe();
        componente.definirConfirmacao(true);

        componente.submeter();
        fixture.detectChanges();

        expect(componente.passoAtual()).toBe(2);
        expect(componente.subpagina()).toBe('metadados');
        expect(componente.errosMetadados()).toEqual([{ path: 'metadados.resumo', erro: 'Preencha este campo da ficha de metadados.' }]);
      });

      it('na página dos Metadados mostra a Série MOTU onde aplicaFichaMotu é verdadeiro e a ficha única nos outros', async () => {
        processoServiceMock.obter.mockReturnValue(of(processoDetalhe({ exigePlantasEMetadados: true })));
        plantasServiceMock.obterMetadadosMotu.mockReturnValue(
          of({
            aplicaFichaMotu: true,
            responsavel: {},
            registos: [{ id: 'm1', tipo: 'SERIE', opcional: false, completo: false, titulo: 'PDM', dados: {} }],
          }),
        );
        processoServiceMock.submeter.mockReturnValue(
          throwError(() => new ApiError('PLANTAS_METADADOS_EM_FALTA', 'Faltam os metadados.', 't8', [])),
        );
        const componente = await abrirPeloDetalhe();
        componente.definirConfirmacao(true);

        componente.submeter();
        fixture.detectChanges();
        await fixture.whenStable();
        fixture.detectChanges();

        expect(componente.subpagina()).toBe('metadados');
        expect(fixture.nativeElement.querySelector('app-processo-metadados-motu')).not.toBeNull();
        expect(fixture.nativeElement.querySelector('app-processo-metadados')).toBeNull();
      });

      it('a recusa por falta da entidade instrutora de um Programa volta ao formulário com o erro anunciado junto do campo', async () => {
        processoServiceMock.obter.mockReturnValue(of(processoDetalhe({ categoriaInstrumento: 'PROGRAMA', entidadeInstrutora: null })));
        processoServiceMock.submeter.mockReturnValue(
          throwError(
            () =>
              new ApiError('VALIDACAO', 'O formulário tem campos em falta ou inválidos.', 't9', [
                { path: 'entidadeInstrutora', erro: 'Indique a entidade instrutora.' },
                { path: 'entidadeEmitente', erro: 'Indique a identificação da entidade emitente.' },
              ]),
          ),
        );
        const componente = await abrirPeloDetalhe();
        componente.definirConfirmacao(true);

        componente.submeter();
        fixture.detectChanges();
        await fixture.whenStable();
        fixture.detectChanges();

        expect(componente.passoAtual()).toBe(2);
        expect(componente.subpagina()).toBe('cabecalho');
        const erro = fixture.nativeElement.querySelector('#erro-entidadeInstrutora') as HTMLElement;
        expect(erro.textContent?.trim()).toBe('Indique a entidade instrutora.');
        expect(erro.getAttribute('role')).toBe('alert');
        expect(fixture.nativeElement.querySelector('#erro-entidadeEmitente')).not.toBeNull();
        expect(fixture.nativeElement.querySelector('app-error-banner')).toBeNull();
      });

      it('com `OPCAO_GEOCONFORM_EM_FALTA` volta ao formulário e mostra o erro junto da pergunta, fora do banner', async () => {
        processoServiceMock.obter.mockReturnValue(of(processoDetalhe({ exigeOpcaoGeoconform: true })));
        processoServiceMock.submeter.mockReturnValue(
          throwError(
            () =>
              new ApiError('OPCAO_GEOCONFORM_EM_FALTA', 'Dados em falta.', 't5', [
                { path: 'comGeoconform', erro: 'Indique se utiliza o GeoConform.' },
              ]),
          ),
        );
        const componente = await abrirPeloDetalhe();
        componente.definirConfirmacao(true);

        componente.submeter();
        fixture.detectChanges();

        expect(componente.passoAtual()).toBe(2);
        expect(componente.subpagina()).toBe('cabecalho');
        const erro = fixture.nativeElement.querySelector('#erro-comGeoconform') as HTMLElement;
        expect(erro.textContent?.trim()).toBe('Indique se utiliza o GeoConform.');
        expect(erro.getAttribute('role')).toBe('alert');
        expect(fixture.nativeElement.querySelector('.error-banner')).toBeNull();
      });
    });

    describe('Associação a um IGT em curso (REN)', () => {
      const MENSAGEM = 'A dinâmica indicada não está disponível para associação.';
      const alteracaoPdm: DinamicaAssociavel = {
        dinamicaId: 'dpdm',
        numeroDinamica: 'DIN-2026-0007',
        descritivo: 'Alteração do PDM de Sintra',
        tipoDinamica: 'ALTERACAO',
        estado: 'TERMINADA',
        instrumento: { id: 'ipdm', designacao: 'PDM de Sintra', tipoInstrumento: 'PDM' },
      };

      afterEach(() => fixture.nativeElement.remove());

      it('400 DINAMICA_ASSOCIADA_INVALIDA ao submeter volta ao formulário, mostra a mensagem junto do seletor, fora do banner, e foca-o', async () => {
        processoServiceMock.obter.mockReturnValue(
          of(processoDetalhe({ aceitaDinamicaAssociada: true, dinamicaAssociadaId: 'dpdm', dinamicaAssociada: alteracaoPdm })),
        );
        processoServiceMock.submeter.mockReturnValue(
          throwError(() => new ApiError('DINAMICA_ASSOCIADA_INVALIDA', MENSAGEM, 't7', [{ path: 'dinamicaAssociadaId', erro: MENSAGEM }])),
        );
        const componente = await abrirPeloDetalhe();
        document.body.appendChild(fixture.nativeElement);
        componente.definirConfirmacao(true);
        componente.submeter();
        fixture.detectChanges();
        fixture.detectChanges();

        expect(componente.passoAtual()).toBe(2);
        expect(componente.subpagina()).toBe('cabecalho');
        const bloco = fixture.nativeElement.querySelector('.associacao-igt') as HTMLElement;
        const select = bloco.querySelector('select') as HTMLSelectElement;
        const erro = bloco.querySelector('.seletor-erro')!;
        expect(erro.textContent!.trim()).toBe(MENSAGEM);
        expect(select.getAttribute('aria-describedby')).toContain(erro.id);
        expect(select.value).toBe('dpdm');
        expect(fixture.nativeElement.querySelector('app-error-banner')).toBeNull();
        expect(document.activeElement).toBe(select);
      });

      it('o passo 2 grava a escolha: dinamicaAssociadaId e dadosFormulario.simultaneoComPmot', async () => {
        processoServiceMock.obter.mockReturnValue(of(processoDetalhe({ aceitaDinamicaAssociada: true })));
        processoServiceMock.dinamicasAssociaveis!.mockReturnValue(
          of({ content: [{ ...alteracaoPdm, estado: 'EM_CURSO' }], page: 0, size: 20, totalElements: 1, totalPages: 1 }),
        );
        const componente = await abrirPeloDetalhe();
        componente.passoAtual.set(2);
        componente.subpagina.set('cabecalho');
        fixture.detectChanges();
        const bloco = fixture.nativeElement.querySelector('.associacao-igt') as HTMLElement;
        (bloco.querySelector('input[value="SIM"]') as HTMLInputElement).click();
        fixture.detectChanges();
        const select = bloco.querySelector('select') as HTMLSelectElement;
        select.value = 'dpdm';
        select.dispatchEvent(new Event('change'));
        fixture.detectChanges();

        componente.guardarRascunho();

        const pedido = processoServiceMock.guardarRascunho.mock.calls[0][1] as Record<string, unknown>;
        expect(pedido['dinamicaAssociadaId']).toBe('dpdm');
        expect(pedido['dadosFormulario']).toEqual({ simultaneoComPmot: 'SIM' });
      });
    });

    describe('Publicação da Alteração', () => {
      const MENSAGEM_EM_FALTA = 'Indique se a alteração introduz alguma planta nova.';
      const MENSAGEM_REGULAMENTO = 'Introduzir uma planta nova exige alteração ao regulamento: assinale as Peças Escritas.';

      const publicacaoAlteracao = (overrides: Partial<ProcessoDetalhe> = {}): ProcessoDetalhe =>
        processoDetalhe({
          formulario: 'PUBLICACAO_ALTERACAO',
          comGeoconform: false,
          elementosAfetados: ['PECAS_GRAFICAS'],
          dadosFormulario: null,
          ...overrides,
        });
      const recusaDaSubmissao = (campos: { path: string; erro: string }[]) =>
        throwError(() => new ApiError('VALIDACAO', 'A Publicação da Alteração tem campos inválidos.', 't6', campos));

      async function submeterRecusada(
        processo: ProcessoDetalhe,
        campos: { path: string; erro: string }[],
      ): Promise<CriarProcessoComponent> {
        processoServiceMock.obter.mockReturnValue(of(processo));
        processoServiceMock.submeter.mockReturnValue(recusaDaSubmissao(campos));
        const componente = await abrirPeloDetalhe();
        document.body.appendChild(fixture.nativeElement);
        componente.definirConfirmacao(true);
        componente.submeter();
        fixture.detectChanges();
        fixture.detectChanges();
        return componente;
      }

      afterEach(() => fixture.nativeElement.remove());

      it('a recusa em elementosAfetados volta ao formulário, mostra o erro junto dos elementos, fora do banner, e foca o primeiro campo com erro', async () => {
        const componente = await submeterRecusada(
          publicacaoAlteracao({ dadosFormulario: '{"introduzPlantaNova":true}' }),
          [{ path: 'elementosAfetados', erro: MENSAGEM_REGULAMENTO }],
        );

        expect(componente.passoAtual()).toBe(2);
        expect(componente.subpagina()).toBe('cabecalho');
        expect(fixture.nativeElement.querySelector('fieldset.elementos #erro-elementosAfetados').textContent.trim()).toBe(MENSAGEM_REGULAMENTO);
        expect(fixture.nativeElement.querySelector('app-error-banner')).toBeNull();
        const ativo = document.activeElement as HTMLInputElement;
        expect(ativo.type).toBe('checkbox');
        expect(ativo.closest('fieldset')!.classList.contains('elementos')).toBe(true);
      });

      it('a recusa em introduzPlantaNova mostra o erro junto da pergunta e foca a primeira opção', async () => {
        await submeterRecusada(publicacaoAlteracao(), [{ path: 'introduzPlantaNova', erro: MENSAGEM_EM_FALTA }]);

        const erro = fixture.nativeElement.querySelector('fieldset.planta-nova #erro-introduzPlantaNova') as HTMLElement;
        expect(erro.textContent?.trim()).toBe(MENSAGEM_EM_FALTA);
        expect(erro.getAttribute('role')).toBe('alert');
        const ativo = document.activeElement as HTMLInputElement;
        expect(ativo.type).toBe('radio');
        expect(ativo.name).toBe('introduzPlantaNova');
        expect(fixture.nativeElement.querySelector('app-error-banner')).toBeNull();
      });

      it('os erros dos dois campos aparecem de uma vez', async () => {
        const componente = await submeterRecusada(publicacaoAlteracao({ comGeoconform: true }), [
          { path: 'elementosAfetados', erro: 'Numa dinâmica com GeoConform, a alteração afeta sempre as Peças Gráficas.' },
          { path: 'introduzPlantaNova', erro: 'Este procedimento não pergunta se a alteração introduz uma planta nova.' },
        ]);

        expect(componente.errosFormularioSubmissao()).toHaveLength(2);
        expect(fixture.nativeElement.querySelectorAll('.erro-campo').length).toBeGreaterThanOrEqual(2);
        expect(fixture.nativeElement.querySelector('app-error-banner')).toBeNull();
      });

      it('na Ratificação, a recusa da modalidade volta ao cabeçalho e fica junto do grupo, sem banner', async () => {
        const mensagem = 'Indique se a ratificação é uma Publicação ou uma Alteração.';
        const componente = await submeterRecusada(
          publicacaoAlteracao({ formulario: 'RATIFICACAO', serieDr: 'PRIMEIRA', exigeElementosAfetados: false, modalidadeRatificacao: null }),
          [{ path: 'modalidadeRatificacao', erro: mensagem }],
        );

        expect(componente.passoAtual()).toBe(2);
        expect(componente.subpagina()).toBe('cabecalho');
        expect(componente.erroModalidadeRatificacao()).toBe(mensagem);
        const erro = fixture.nativeElement.querySelector('app-formulario-ratificacao fieldset.modalidade .erro-campo');
        expect(erro.textContent.trim()).toBe(mensagem);
        expect(erro.getAttribute('role')).toBe('alert');
        expect(fixture.nativeElement.querySelector('app-error-banner')).toBeNull();
      });

      it('na Ratificação, a falta da data de assinatura volta ao cabeçalho e fica junto do campo, sem banner', async () => {
        const mensagem = 'Indique a data de assinatura do ato.';
        const componente = await submeterRecusada(
          publicacaoAlteracao({ formulario: 'RATIFICACAO', serieDr: 'PRIMEIRA', exigeElementosAfetados: false, modalidadeRatificacao: 'ALTERACAO' }),
          [{ path: 'dataAssinatura', erro: mensagem }],
        );
        await fixture.whenStable();
        fixture.detectChanges();

        expect(componente.passoAtual()).toBe(2);
        expect(componente.subpagina()).toBe('cabecalho');
        const campo = fixture.nativeElement.querySelector('input.data-assinatura') as HTMLInputElement;
        expect(campo.getAttribute('aria-describedby')).toContain('erro-dataAssinatura');
        expect(fixture.nativeElement.querySelector('#erro-dataAssinatura').textContent.trim()).toBe(mensagem);
        expect(fixture.nativeElement.querySelector('app-error-banner')).toBeNull();
      });

      it('uma recusa por outro campo continua no banner do passo 3', async () => {
        const componente = await submeterRecusada(publicacaoAlteracao(), [{ path: 'sumario', erro: 'Obrigatório na submissão.' }]);

        expect(componente.passoAtual()).toBe(3);
        expect(componente.errosFormularioSubmissao()).toEqual([]);
        expect(fixture.nativeElement.querySelector('app-error-banner')).not.toBeNull();
      });

      it('uma recusa que mistura campos do formulário com outros continua no banner', async () => {
        const componente = await submeterRecusada(publicacaoAlteracao(), [
          { path: 'introduzPlantaNova', erro: MENSAGEM_EM_FALTA },
          { path: 'sumario', erro: 'Obrigatório na submissão.' },
        ]);

        expect(componente.passoAtual()).toBe(3);
        expect(componente.errosFormularioSubmissao()).toEqual([]);
      });

      it('com outro código de erro nos mesmos campos não volta ao formulário', async () => {
        processoServiceMock.obter.mockReturnValue(of(publicacaoAlteracao()));
        processoServiceMock.submeter.mockReturnValue(
          throwError(() => new ApiError('ELEMENTOS_AFETADOS_EM_FALTA', 'Indique os elementos.', 't7', [{ path: 'elementosAfetados', erro: 'Indique.' }])),
        );
        const componente = await abrirPeloDetalhe();
        document.body.appendChild(fixture.nativeElement);
        componente.definirConfirmacao(true);

        componente.submeter();
        fixture.detectChanges();

        expect(componente.passoAtual()).toBe(3);
        expect(componente.errosFormularioSubmissao()).toEqual([]);
      });
    });

    describe('Discussão Pública com a data da deliberação depois da data limite', () => {
      const CAMINHO_DATA = 'informacaoBase.deliberacao.dataDeliberacao';
      const ERRO_DATA = 'Posterior à data limite da dinâmica (31/03/2027).';
      const MENSAGEM = 'A data da deliberação tem de ser igual ou anterior à data limite da dinâmica.';

      async function submeterRecusada(campos: { path: string; erro: string }[]): Promise<CriarProcessoComponent> {
        processoServiceMock.obter.mockReturnValue(
          of(processoDetalhe({ formulario: 'DISCUSSAO_PUBLICA', dataCaducidadeDinamica: '2027-03-31' })),
        );
        processoServiceMock.submeter.mockReturnValue(throwError(() => new ApiError('VALIDACAO', MENSAGEM, 't276', campos)));
        const componente = await abrirPeloDetalhe();
        document.body.appendChild(fixture.nativeElement);
        componente.definirConfirmacao(true);
        componente.submeter();
        fixture.detectChanges();
        fixture.detectChanges();
        return componente;
      }

      const campoDaData = (): HTMLElement | null =>
        (fixture.nativeElement.querySelector('#informacao-base-data-deliberacao') as HTMLElement | null)?.closest('.campo') ?? null;

      afterEach(() => fixture.nativeElement.remove());

      it('o 400 volta aos documentos com o erro junto de «Data da deliberação», anunciado, e não no banner', async () => {
        const componente = await submeterRecusada([{ path: CAMINHO_DATA, erro: ERRO_DATA }]);

        expect(componente.passoAtual()).toBe(2);
        expect(componente.subpagina()).toBe('documentos');
        expect(componente.errosInformacaoBase()).toEqual({ [CAMINHO_DATA]: ERRO_DATA });
        const erro = campoDaData()!.querySelector('.erro-campo')!;
        expect(erro.textContent).toBe(ERRO_DATA);
        expect(erro.getAttribute('role')).toBe('alert');
        expect(campoDaData()!.querySelector('.ajuda-campo')!.textContent).toContain('31/03/2027');
        expect(componente.erro()).toBeNull();
        const bannersComTexto = Array.from(fixture.nativeElement.querySelectorAll('app-error-banner') as NodeListOf<HTMLElement>).filter(
          (banner) => banner.textContent!.trim() !== '',
        );
        expect(bannersComTexto).toEqual([]);
        expect(fixture.nativeElement.textContent).not.toContain(MENSAGEM);
      });

      it('um 400 que junta a data e outro campo mostra a data junto do campo e o resto no banner', async () => {
        const componente = await submeterRecusada([
          { path: CAMINHO_DATA, erro: ERRO_DATA },
          { path: 'sumario', erro: 'Obrigatório na submissão.' },
        ]);

        expect(componente.subpagina()).toBe('documentos');
        expect(campoDaData()!.querySelector('.erro-campo')!.textContent).toBe(ERRO_DATA);
        expect(componente.erro()?.campos).toEqual([{ path: 'sumario', erro: 'Obrigatório na submissão.' }]);
        expect(fixture.nativeElement.querySelector('app-error-banner')).not.toBeNull();
      });

      it('um 400 com outro caminho continua no banner do passo 3', async () => {
        const componente = await submeterRecusada([{ path: 'sumario', erro: 'Obrigatório na submissão.' }]);

        expect(componente.passoAtual()).toBe(3);
        expect(componente.errosInformacaoBase()).toEqual({});
        expect(fixture.nativeElement.querySelector('app-error-banner')).not.toBeNull();
      });

      it('um 400 em dataAssinatura na submissão é do cartão INCM e não volta aos documentos', async () => {
        const componente = await submeterRecusada([{ path: 'dataAssinatura', erro: 'Indique uma data válida.' }]);

        expect(componente.passoAtual()).toBe(3);
        expect(componente.errosInformacaoBase()).toEqual({});
      });
    });

    describe('procedimento final com a Informação Base incoerente', () => {
      const ERRO_PRAZO = 'O prazo tem de ser o mesmo da Deliberação.';
      const ERRO_AVALIACAO = 'A avaliação ambiental tem de ser a mesma da Deliberação.';
      const ERRO_DATA = 'A data da deliberação tem de ser igual ou anterior à data do texto do ato.';
      const ERRO_DATA_LIMITE = 'Posterior à data limite da dinâmica (31/03/2027).';

      afterEach(() => fixture.nativeElement.remove());

      it('o 400 volta aos documentos com cada erro no seu campo, as duas mensagens da data juntas, e não avança', async () => {
        processoServiceMock.obter.mockReturnValue(
          of(processoDetalhe({ formulario: 'INFORMACAO_GERAL', dataCaducidadeDinamica: '2027-03-31' })),
        );
        processoServiceMock.submeter.mockReturnValue(
          throwError(
            () =>
              new ApiError('VALIDACAO', 'A Informação Base tem campos incoerentes.', 't301', [
                { path: 'informacaoBase.textoAto.prazoAnos', erro: ERRO_PRAZO },
                { path: 'informacaoBase.textoAto.avaliacaoAmbiental', erro: ERRO_AVALIACAO },
                { path: 'informacaoBase.deliberacao.dataDeliberacao', erro: ERRO_DATA },
                { path: 'informacaoBase.deliberacao.dataDeliberacao', erro: ERRO_DATA_LIMITE },
              ]),
          ),
        );
        const componente = await abrirPeloDetalhe();
        document.body.appendChild(fixture.nativeElement);
        componente.definirConfirmacao(true);
        componente.submeter();
        fixture.detectChanges();
        fixture.detectChanges();

        expect(componente.submissaoConcluida()).toBeNull();
        expect(componente.passoAtual()).toBe(2);
        expect(componente.subpagina()).toBe('documentos');
        expect(componente.errosInformacaoBase()).toEqual({
          'informacaoBase.textoAto.prazoAnos': ERRO_PRAZO,
          'informacaoBase.textoAto.avaliacaoAmbiental': ERRO_AVALIACAO,
          'informacaoBase.deliberacao.dataDeliberacao': `${ERRO_DATA} ${ERRO_DATA_LIMITE}`,
        });
        const textoAto = fixture.nativeElement.querySelector('.informacao-base[aria-label="Informação Base — Texto do Ato"]') as HTMLElement;
        expect(Array.from(textoAto.querySelectorAll('.erro-campo')).map((erro) => erro.textContent!.trim())).toEqual([ERRO_PRAZO, ERRO_AVALIACAO]);
        const campoData = (fixture.nativeElement.querySelector('#informacao-base-data-deliberacao') as HTMLElement).closest('.campo')!;
        expect(campoData.querySelector('.erro-campo')!.textContent).toBe(`${ERRO_DATA} ${ERRO_DATA_LIMITE}`);
        expect(campoData.querySelector('.ajuda-campo')!.textContent).toContain('Tem de ser igual ou anterior a 31/03/2027');
        expect(componente.erro()).toBeNull();
      });
    });

    it('não mostra Submeter a quem não tem `submeter` na lista, mesmo com o rascunho em EM_SUBMISSAO', async () => {
      processoServiceMock.obter.mockReturnValue(of(processoDetalhe({ estado: 'EM_SUBMISSAO', acoesDisponiveis: [] })));

      await abrirPeloDetalhe();

      expect(botoes()).not.toContain('Submeter');
      expect(fixture.nativeElement.querySelector('input[type="checkbox"]')).toBeNull();
      expect(fixture.nativeElement.textContent).toContain('Não pode submeter este processo.');
    });

    it('sem `editavel` o passo 2 não mostra campos editáveis nem deixa avançar', async () => {
      processoServiceMock.obter.mockReturnValue(of(processoDetalhe({ editavel: false, acoesDisponiveis: [] })));
      const componente = await abrirPeloDetalhe();

      componente.voltarPasso();
      fixture.detectChanges();

      expect(componente.passoAtual()).toBe(2);
      expect(fixture.nativeElement.querySelector('app-processo-formulario')).toBeNull();
      expect(fixture.nativeElement.textContent).toContain('Não pode editar este processo.');
      expect(botoes()).not.toContain('Guardar rascunho');
    });

    it('o Anterior do passo 2 percorre as sub-páginas e volta ao detalhe, não ao passo 1 vazio', async () => {
      const componente = await abrirPeloDetalhe();
      const navegar = vi.spyOn(TestBed.inject(Router), 'navigateByUrl');

      componente.voltarPasso();
      fixture.detectChanges();
      expect(componente.passoAtual()).toBe(2);
      expect(componente.subpagina()).toBe('documentos');
      componente.voltarPasso();
      fixture.detectChanges();
      expect(componente.subpagina()).toBe('cabecalho');
      expect(navegar).not.toHaveBeenCalled();
      componente.voltarPasso();

      expect(navegar).toHaveBeenCalledWith('/processos/processo-1');
    });

    it('mostra o erro e não avança quando o Processo não abre', async () => {
      processoServiceMock.obter.mockReturnValue(
        throwError(() => new ApiError('NAO_ENCONTRADO', 'Não encontrado.', 't3', null)),
      );

      const componente = await abrirPeloDetalhe();

      expect(componente.passoAtual()).toBe(1);
      expect(componente.erro()?.codigo).toBe('NAO_ENCONTRADO');
    });
  });

  it('mostra o erro do backend em vez de avançar', () => {
    completarPasso1();
    processoServiceMock.criar.mockReturnValue(
      throwError(() => new ApiError('SEM_PERMISSAO', 'Sem permissão.', 't2', null)),
    );

    fixture.componentInstance.criarProcesso();

    expect(fixture.componentInstance.passoAtual()).toBe(1);
    expect(fixture.componentInstance.erro()?.codigo).toBe('SEM_PERMISSAO');
    expect(fixture.componentInstance.confirmacaoAberta()).toBe(false);
  });

  describe('processo sem associação a IGT', () => {
    function configuracao(id: number, codigo: string, nome: string, associacao: 'UM' | 'UM_OU_MAIS' | 'OPCIONAL') {
      return { instrumentoDinamicaConfigId: id, tipoInstrumentoId: id, tipoInstrumentoCodigo: codigo, tipoInstrumentoNome: nome, associacaoInstrumentos: associacao };
    }

    const SUSPENSAO_GOVERNO: TipoDinamicaSemIgt = {
      tipoDinamicaId: 15,
      codigo: 'SUSPENSAO_GOVERNO',
      nome: 'Suspensão (iniciativa do Governo)',
      configuracoes: [configuracao(901, 'PDM', 'Plano Diretor Municipal', 'UM_OU_MAIS'), configuracao(902, 'PU', 'Plano de Urbanização', 'UM_OU_MAIS')],
    };
    const MEDIDAS_PREVENTIVAS_GOVERNO: TipoDinamicaSemIgt = {
      tipoDinamicaId: 16,
      codigo: 'MEDIDAS_PREVENTIVAS_GOVERNO',
      nome: 'Medidas Preventivas (iniciativa do Governo)',
      configuracoes: [configuracao(911, 'PDM', 'Plano Diretor Municipal', 'UM_OU_MAIS')],
    };
    const RATIFICACAO: TipoDinamicaSemIgt = {
      tipoDinamicaId: 17,
      codigo: 'RATIFICACAO',
      nome: 'Ratificação',
      configuracoes: [configuracao(931, 'PDM', 'Plano Diretor Municipal', 'UM'), configuracao(932, 'PDIM', 'Plano Diretor Intermunicipal', 'UM')],
    };
    const SUSPENSAO_CCDR: TipoDinamicaSemIgt = {
      tipoDinamicaId: 18,
      codigo: 'SUSPENSAO_CCDR',
      nome: 'Suspensão (iniciativa da CCDR)',
      configuracoes: [configuracao(941, 'PDM', 'Plano Diretor Municipal', 'OPCIONAL')],
    };
    const MEDIDAS_PREVENTIVAS_MUNICIPIO: TipoDinamicaSemIgt = {
      tipoDinamicaId: 9,
      codigo: 'MEDIDAS_PREVENTIVAS',
      nome: 'Medidas Preventivas (iniciativa do município)',
      configuracoes: [configuracao(951, 'PDM', 'Plano Diretor Municipal', 'OPCIONAL'), configuracao(952, 'PU', 'Plano de Urbanização', 'OPCIONAL')],
    };
    const TIPOS_CM: TipoInstrumento[] = [...tipos, { id: 3, codigo: 'PP', nome: 'Plano de Pormenor', categoria: 'PLANO', entidadeInstrutoraObrigatoria: true }];

    function dinamicaSemIgt(overrides: Partial<DinamicaSemIgtEmCurso> = {}): DinamicaSemIgtEmCurso {
      return {
        dinamicaId: 'dinamica-sem-igt',
        numeroDinamica: '00031',
        tipoDinamica: 'SUSPENSAO_GOVERNO',
        nomeDinamica: 'Suspensão (iniciativa do Governo)',
        descritivo: 'Suspensão parcial PDM Sintra',
        estado: 'EM_CURSO',
        dataInicio: '2026-11-01T10:00:00Z',
        dataCaducidade: null,
        instrumentos: [{ id: 'i-sintra', designacao: 'PDM de Sintra', tipoInstrumento: 'PDM', concelhos: ['1111'] }],
        atoPublicado: { processoId: 'p-pub', numeroDr: 'DR II S. n.º 10/2026', dataPublicacaoDr: '2026-11-05' },
        procedimentosDisponiveis: [
          procedimento({ instrumentoProcedimentoConfigId: 802, tipoProcedimento: 'PRORROGACAO', nomeProcedimento: 'Prorrogação', ordem: 2, obrigatorio: false }),
        ],
        ...overrides,
      };
    }

    function abrir(tiposProcesso: TiposProcesso, dinamicas: DinamicaSemIgtEmCurso[] = [], tiposInstrumento: TipoInstrumento[] = tipos): CriarProcessoComponent {
      instrumentoServiceMock.tiposProcesso.mockReturnValue(of(tiposProcesso));
      instrumentoServiceMock.listarTipos.mockReturnValue(of(tiposInstrumento));
      dinamicaServiceMock.disponivel.mockReturnValue(of({ dinamicasEmCurso: dinamicas }));
      fixture.destroy();
      fixture = TestBed.createComponent(CriarProcessoComponent);
      fixture.detectChanges();
      return fixture.componentInstance;
    }

    function legendas(): string[] {
      return [...fixture.nativeElement.querySelectorAll('app-pagina-tipo-igt fieldset > legend')].map((e: Element) => e.textContent?.trim() ?? '');
    }

    function titulos(seletor: string): string[] {
      return [...fixture.nativeElement.querySelectorAll(`${seletor} .opcao-titulo`)].map((e: Element) => e.textContent?.trim() ?? '');
    }

    function escreverDescritivo(texto: string): void {
      const campo = fixture.nativeElement.querySelector('app-pagina-dados-dinamica-sem-igt app-campo-texto input') as HTMLInputElement;
      campo.value = texto;
      campo.dispatchEvent(new Event('input'));
      fixture.detectChanges();
    }

    function abrirDadosDaDinamica(componente: CriarProcessoComponent, codigo: string): void {
      componente.selecionarTipoSemIgt(codigo);
      componente.avancar();
      fixture.detectChanges();
    }

    it('numa sessão SEC_ESTADO o primeiro passo só tem o grupo sem IGT, com as Dinâmicas do Governo e a Ratificação (UI-018)', () => {
      abrir({ comAssociacaoIgt: [], semAssociacaoIgt: [SUSPENSAO_GOVERNO, MEDIDAS_PREVENTIVAS_GOVERNO, RATIFICACAO] });

      expect(legendas()).toEqual(['Processo sem associação a IGT']);
      expect(titulos('app-pagina-tipo-igt')).toEqual([
        'Suspensão (iniciativa do Governo)',
        'Medidas Preventivas (iniciativa do Governo)',
        'Ratificação',
      ]);
    });

    it('a Câmara vê PDM, PU e PP com IGT e as Medidas Preventivas do município sem IGT (UI-002)', () => {
      abrir(
        {
          comAssociacaoIgt: TIPOS_CM.map((tipo) => ({ tipoInstrumentoId: tipo.id, codigo: tipo.codigo, nome: tipo.nome })),
          semAssociacaoIgt: [MEDIDAS_PREVENTIVAS_MUNICIPIO],
        },
        [],
        TIPOS_CM,
      );

      expect(legendas()).toEqual(['Processo com associação a IGT', 'Processo sem associação a IGT']);
      const etiquetas = [...fixture.nativeElement.querySelectorAll('app-pagina-tipo-igt .opcao-etiqueta')].map((e: Element) => e.textContent);
      expect(etiquetas).toEqual(['PDM', 'PU', 'PP']);
      expect(titulos('app-pagina-tipo-igt')).toContain('Medidas Preventivas (iniciativa do município)');
    });

    it('sem resposta dos tipos de processo mostra todos os tipos com IGT, como antes', () => {
      instrumentoServiceMock.tiposProcesso.mockReturnValue(throwError(() => new ApiError('SEM_PERMISSAO', 'Sem permissão.', 't', null)));
      fixture.destroy();
      fixture = TestBed.createComponent(CriarProcessoComponent);
      fixture.detectChanges();

      expect(legendas()).toEqual(['Processo com associação a IGT']);
      expect(fixture.componentInstance.erro()).toBeNull();
    });

    it('na Suspensão da CCDR não pede tipo de instrumento nem Instrumentos e envia só a configuração e a designação', () => {
      dinamicaServiceMock.iniciar.mockReturnValue(of({ id: 'dinamica-ccdr' }));
      const componente = abrir({ comAssociacaoIgt: [], semAssociacaoIgt: [SUSPENSAO_CCDR] });

      abrirDadosDaDinamica(componente, 'SUSPENSAO_CCDR');

      expect(componente.paginaInicial()).toBe('semIgt');
      expect(etiquetasPassos()).toEqual(['Tipo IGT', 'Dinâmica', 'Formulário']);
      const pagina = fixture.nativeElement.querySelector('app-pagina-dados-dinamica-sem-igt') as HTMLElement;
      expect(pagina.querySelectorAll('app-lista-opcoes')).toHaveLength(0);
      expect(pagina.querySelectorAll('input')).toHaveLength(1);
      expect(fixture.nativeElement.querySelector('app-pagina-igt-existente')).toBeNull();
      expect(pagina.querySelector('label')?.textContent).toContain('Designação');
      expect(componente.podeAvancar()).toBe(false);

      escreverDescritivo('Suspensão parcial PDM Sintra e Cascais');
      expect(componente.podeAvancar()).toBe(true);

      componente.avancar();
      expect(componente.confirmacaoAberta()).toBe(true);
      expect(componente.mensagemConfirmacao()).toContain(
        '«Suspensão (iniciativa da CCDR)» com a designação «Suspensão parcial PDM Sintra e Cascais»',
      );
      componente.confirmarPedido();

      expect(dinamicaServiceMock.iniciar).toHaveBeenCalledWith({ instrumentoDinamicaConfigId: 941, descritivo: 'Suspensão parcial PDM Sintra e Cascais' });
      expect(dinamicaServiceMock.iniciar.mock.calls[0][0]).not.toHaveProperty('instrumentoIds');
      expect(instrumentoServiceMock.iniciarDinamica).not.toHaveBeenCalled();
    });

    it('na Suspensão do Governo o Avançar só fica ativo com a designação, sem exigir Instrumentos', () => {
      const componente = abrir({ comAssociacaoIgt: [], semAssociacaoIgt: [SUSPENSAO_GOVERNO] });
      abrirDadosDaDinamica(componente, 'SUSPENSAO_GOVERNO');

      expect(componente.configuracaoSemIgtId()).toBe(901);
      expect(componente.podeAvancar()).toBe(false);
      expect(componente.dicaAvancar()).toBe('Escreva a designação da dinâmica.');
      escreverDescritivo('   ');
      expect(componente.podeAvancar()).toBe(false);
      escreverDescritivo('Suspensão Sintra');
      expect(componente.podeAvancar()).toBe(true);
      expect(componente.instrumentoEscolhidoId()).toBeNull();
    });

    it('na Ratificação pede o tipo de instrumento e envia a configuração do PDIM escolhido', () => {
      dinamicaServiceMock.iniciar.mockReturnValue(of({ id: 'dinamica-rat' }));
      const componente = abrir({ comAssociacaoIgt: [], semAssociacaoIgt: [RATIFICACAO] });
      abrirDadosDaDinamica(componente, 'RATIFICACAO');

      expect(titulos('app-pagina-dados-dinamica-sem-igt')).toEqual(['Plano Diretor Municipal', 'Plano Diretor Intermunicipal']);
      expect(fixture.nativeElement.querySelector('app-pagina-dados-dinamica-sem-igt legend')?.textContent?.trim()).toBe('Tipo de instrumento');
      escreverDescritivo('Ratificação do PDIM');
      expect(componente.podeAvancar()).toBe(false);
      expect(componente.dicaAvancar()).toBe('Escolha o tipo de instrumento.');

      const pdim = [...fixture.nativeElement.querySelectorAll('app-pagina-dados-dinamica-sem-igt input[type="radio"]')].find(
        (radio) => (radio as HTMLInputElement).value === '932',
      ) as HTMLInputElement;
      pdim.click();
      fixture.detectChanges();
      expect(componente.podeAvancar()).toBe(true);
      expect(fixture.nativeElement.querySelector('app-pagina-dados-dinamica-sem-igt label.campo-texto-rotulo')?.textContent).toContain(
        'Descritivo da dinâmica',
      );

      componente.avancar();
      componente.confirmarPedido();
      expect(dinamicaServiceMock.iniciar).toHaveBeenCalledWith({ instrumentoDinamicaConfigId: 932, descritivo: 'Ratificação do PDIM' });
    });

    it('nas Medidas Preventivas do município cria o processo do primeiro Procedimento e o formulário diz «Sem IGT associado»', () => {
      const publicacao = procedimento({ instrumentoProcedimentoConfigId: 961, tipoProcedimento: 'PUBLICACAO', nomeProcedimento: 'Publicação', ordem: 1 });
      dinamicaServiceMock.iniciar.mockReturnValue(of({ id: 'dinamica-mp', instrumentoId: null, instrumentoIds: [] }));
      const componente = abrir({ comAssociacaoIgt: [], semAssociacaoIgt: [MEDIDAS_PREVENTIVAS_MUNICIPIO] });
      dinamicaServiceMock.disponivel.mockReturnValue(
        of({
          dinamicasEmCurso: [
            dinamicaSemIgt({
              dinamicaId: 'dinamica-mp',
              tipoDinamica: 'MEDIDAS_PREVENTIVAS',
              instrumentos: [],
              atoPublicado: null,
              procedimentosDisponiveis: [publicacao],
            }),
          ],
        }),
      );
      processoServiceMock.criar.mockReturnValue(of(processo({ dinamicaId: 'dinamica-mp', instrumentoProcedimentoConfigId: 961 })));
      processoServiceMock.obter.mockReturnValue(
        of(processoDetalhe({ instrumentoId: null, numeroInstrumento: null, designacaoInstrumento: 'MP Sintra', instrumentosAbrangidos: [] })),
      );

      abrirDadosDaDinamica(componente, 'MEDIDAS_PREVENTIVAS');
      expect(componente.configuracaoSemIgtId()).toBe(951);
      escreverDescritivo('MP Sintra');
      componente.avancar();
      expect(componente.mensagemConfirmacao()).toContain('com o descritivo «MP Sintra»');
      componente.confirmarPedido();
      fixture.detectChanges();

      expect(dinamicaServiceMock.iniciar).toHaveBeenCalledWith({ instrumentoDinamicaConfigId: 951, descritivo: 'MP Sintra' });
      expect(processoServiceMock.criar).toHaveBeenCalledWith('dinamica-mp', { instrumentoProcedimentoConfigId: 961 });
      expect(componente.passoAtual()).toBe(2);
      expect(componente.confirmacaoAberta()).toBe(false);
      const cabecalho = fixture.nativeElement.querySelector('app-processo-formulario-inicial .instrumentos-associados') as HTMLElement;
      expect(cabecalho.textContent).toContain('Instrumentos associados');
      expect(cabecalho.textContent).toContain('Sem IGT associado');
      const rotulos = Array.from(
        fixture.nativeElement.querySelectorAll('app-processo-formulario-inicial dl.contexto dt') as NodeListOf<HTMLElement>,
      ).map((dt) => dt.textContent!.trim());
      expect(rotulos).toContain('Tipo de instrumento');
      expect(rotulos).toContain('Parte do DR');
      expect(rotulos).not.toContain('Designação');
      expect(rotulos).not.toContain('NUT II');
      expect(rotulos).not.toContain('Concelho');
    });

    it('com mais de um tipo de ato cria logo o processo: o tipo escolhe-se no cabeçalho', () => {
      dinamicaServiceMock.iniciar.mockReturnValue(of({ id: 'dinamica-gov' }));
      const componente = abrir({ comAssociacaoIgt: [], semAssociacaoIgt: [SUSPENSAO_GOVERNO] });
      const publicacao = procedimento({
        instrumentoProcedimentoConfigId: 801,
        nomeProcedimento: 'Publicação',
        ordem: 1,
        tiposAtoDisponiveis: [
          { id: 5, codigo: 'DESP', nome: 'Despacho' },
          { id: 6, codigo: 'RES', nome: 'Resolução' },
        ],
      });
      dinamicaServiceMock.disponivel.mockReturnValue(
        of({ dinamicasEmCurso: [dinamicaSemIgt({ dinamicaId: 'dinamica-gov', instrumentos: [], atoPublicado: null, procedimentosDisponiveis: [publicacao] })] }),
      );

      abrirDadosDaDinamica(componente, 'SUSPENSAO_GOVERNO');
      escreverDescritivo('Suspensão Sintra');
      componente.avancar();
      componente.confirmarPedido();
      fixture.detectChanges();

      expect(processoServiceMock.criar).toHaveBeenCalledWith('dinamica-gov', { instrumentoProcedimentoConfigId: 801 });
      expect(componente.dinamicaEscolhida()?.dinamicaId).toBe('dinamica-gov');
      expect(componente.procedimentoEscolhido()?.instrumentoProcedimentoConfigId).toBe(801);
      expect(componente.passoAtual()).toBe(2);
    });

    it('mostra o 409 DESIGNACAO_DUPLICADA junto da designação e não no banner', () => {
      const mensagem = 'Já existe uma dinâmica deste tipo com esta designação.';
      dinamicaServiceMock.iniciar.mockReturnValue(
        throwError(() => new ApiError('DESIGNACAO_DUPLICADA', mensagem, 't9', [{ path: 'descritivo', erro: mensagem }])),
      );
      const componente = abrir({ comAssociacaoIgt: [], semAssociacaoIgt: [SUSPENSAO_GOVERNO] });
      abrirDadosDaDinamica(componente, 'SUSPENSAO_GOVERNO');
      escreverDescritivo('Suspensão Sintra');
      componente.avancar();
      componente.confirmarPedido();
      fixture.detectChanges();

      expect(componente.paginaInicial()).toBe('semIgt');
      expect(componente.confirmacaoAberta()).toBe(false);
      expect(fixture.nativeElement.querySelector('app-error-banner')).toBeNull();
      expect(fixture.nativeElement.querySelector('app-pagina-dados-dinamica-sem-igt .campo-texto-erro')?.textContent).toContain(mensagem);
      expect(fixture.nativeElement.querySelector('app-pagina-dados-dinamica-sem-igt [role="alert"]')?.textContent).toContain(mensagem);
      expect(fixture.nativeElement.querySelector('app-pagina-dados-dinamica-sem-igt input')?.getAttribute('aria-invalid')).toBe('true');

      escreverDescritivo('Suspensão Sintra 2');
      expect(componente.erro()).toBeNull();
    });

    it('mostra DINAMICA_NAO_ELEGIVEL no banner', () => {
      dinamicaServiceMock.iniciar.mockReturnValue(
        throwError(() => new ApiError('DINAMICA_NAO_ELEGIVEL', 'Dinâmica não elegível.', 't8', null)),
      );
      const componente = abrir({ comAssociacaoIgt: [], semAssociacaoIgt: [SUSPENSAO_GOVERNO] });
      abrirDadosDaDinamica(componente, 'SUSPENSAO_GOVERNO');
      escreverDescritivo('Suspensão Sintra');
      componente.avancar();
      componente.confirmarPedido();
      fixture.detectChanges();

      expect(fixture.nativeElement.querySelector('app-error-banner')).not.toBeNull();
      expect(componente.erroDescritivoSemIgt()).toBeNull();
    });

    it('continua uma Suspensão do Governo filtrada pelo concelho, com o ato publicado e a Prorrogação', () => {
      instrumentoServiceMock.listarConcelhos.mockReturnValue(of([{ codigo: '1111', nome: 'Sintra' }, { codigo: '1106', nome: 'Lisboa' }]));
      const componente = abrir({ comAssociacaoIgt: [], semAssociacaoIgt: [SUSPENSAO_GOVERNO] }, [
        dinamicaSemIgt(),
        dinamicaSemIgt({
          dinamicaId: 'dinamica-lisboa',
          descritivo: 'Suspensão Lisboa',
          instrumentos: [{ id: 'i-lisboa', designacao: 'PDM de Lisboa', tipoInstrumento: 'PDM', concelhos: ['1106'] }],
        }),
      ]);

      const ligacao = [...fixture.nativeElement.querySelectorAll('app-pagina-tipo-igt button')].find((b) =>
        (b as HTMLElement).textContent?.includes('Continuar uma dinâmica sem associação a IGT'),
      ) as HTMLButtonElement;
      ligacao.click();
      fixture.detectChanges();
      expect(componente.paginaInicial()).toBe('continuarSemIgt');
      expect(titulos('app-pagina-continuar-sem-igt')).toHaveLength(2);

      const pagina = fixture.debugElement.query(By.css('app-pagina-continuar-sem-igt')).componentInstance as {
        opcoesConcelho: () => { valor: string; rotulo: string }[];
        filtrarPorConcelho: (valor: string | null) => void;
      };
      expect(pagina.opcoesConcelho()).toEqual([
        { valor: '1106', rotulo: 'Lisboa' },
        { valor: '1111', rotulo: 'Sintra' },
      ]);
      pagina.filtrarPorConcelho('1111');
      fixture.detectChanges();

      const opcoes = fixture.nativeElement.querySelectorAll('app-pagina-continuar-sem-igt app-lista-opcoes .opcao') as NodeListOf<HTMLElement>;
      expect(opcoes).toHaveLength(1);
      expect(opcoes[0].textContent).toContain('Suspensão parcial PDM Sintra');
      expect(opcoes[0].textContent).toContain('PDM de Sintra (PDM)');
      expect(opcoes[0].textContent).toContain('DR II S. n.º 10/2026 de 05/11/2026');

      componente.selecionarDinamica('dinamica-sem-igt');
      fixture.detectChanges();
      expect(componente.procedimentoEscolhido()?.nomeProcedimento).toBe('Prorrogação');
      expect(titulos('app-pagina-continuar-sem-igt')).toContain('Prorrogação');

      componente.avancar();
      expect(componente.confirmacaoAberta()).toBe(true);
      componente.confirmarPedido();
      expect(processoServiceMock.criar).toHaveBeenCalledWith('dinamica-sem-igt', { instrumentoProcedimentoConfigId: 802 });
    });

    it('sem Dinâmicas sem IGT em curso não mostra a ligação para as continuar', () => {
      abrir({ comAssociacaoIgt: [], semAssociacaoIgt: [SUSPENSAO_GOVERNO] });
      expect(fixture.nativeElement.querySelector('app-pagina-tipo-igt').textContent).not.toContain('Continuar uma dinâmica sem associação a IGT');
    });

    it('voltar da página sem IGT regressa ao primeiro passo com a escolha feita', () => {
      const componente = abrir({ comAssociacaoIgt: [], semAssociacaoIgt: [SUSPENSAO_GOVERNO] });
      abrirDadosDaDinamica(componente, 'SUSPENSAO_GOVERNO');
      componente.voltarPasso();
      expect(componente.paginaInicial()).toBe('tipo');
      expect(componente.viaSemIgt()).toBe(false);
      expect(componente.tipoSemIgtEscolhido()).toBe('SUSPENSAO_GOVERNO');
    });

    it('escolher um tipo com IGT desfaz a escolha sem IGT', () => {
      const componente = abrir({ comAssociacaoIgt: [{ tipoInstrumentoId: 1, codigo: 'PDM', nome: 'PDM' }], semAssociacaoIgt: [SUSPENSAO_GOVERNO] });
      componente.selecionarTipoSemIgt('SUSPENSAO_GOVERNO');
      componente.selecionarTipo('1');
      expect(componente.tipoSemIgt()).toBeNull();
      componente.avancar();
      expect(componente.paginaInicial()).toBe('origem');
    });
  });
});
