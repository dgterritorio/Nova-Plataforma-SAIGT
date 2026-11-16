import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { ActivatedRoute, ParamMap, Router, convertToParamMap, provideRouter } from '@angular/router';
import { BehaviorSubject, Subject, of, throwError } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ProcessoDetalheComponent } from './processo-detalhe.component';
import { ProcessoDocumentosComponent } from '../processo-documentos/processo-documentos.component';
import { AuthService } from '../../../core/auth/auth.service';
import { ApiError } from '../../../core/models/error-response.model';
import { DocumentoProcesso } from '../../../models/documento.model';
import { DinamicaAssociavel, ProcessoDetalhe } from '../../../models/processo.model';
import { DocumentoService } from '../../../services/documento.service';
import { EntidadeService } from '../../../services/entidade.service';
import { InstrumentoService } from '../../../services/instrumento.service';
import { ListaValoresService } from '../../../services/lista-valores.service';
import { ReferenciaSnitService } from '../../../services/referencia-snit.service';
import { NotificacaoService } from '../../../services/notificacao.service';
import { ProcessoService } from '../../../services/processo.service';
import { UtilizadorService } from '../../../services/utilizador.service';

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
    criadoPorNome: 'Helena Marques',
    tags: [],
    justificacaoDesbloqueio: null,
    ...parcial,
  };
}

describe('ProcessoDetalheComponent', () => {
  let fixture: ComponentFixture<ProcessoDetalheComponent>;
  let documentoServiceMock: {
    estrutura: ReturnType<typeof vi.fn>;
    listar: ReturnType<typeof vi.fn>;
    historico: ReturnType<typeof vi.fn>;
  };
  let processoServiceMock: {
    obter: ReturnType<typeof vi.fn>;
    guardarRascunho: ReturnType<typeof vi.fn>;
    resgatar: ReturnType<typeof vi.fn>;
    corrigir: ReturnType<typeof vi.fn>;
    capturar: ReturnType<typeof vi.fn>;
    submeter: ReturnType<typeof vi.fn>;
    enviarIncm: ReturnType<typeof vi.fn>;
    estatisticas: ReturnType<typeof vi.fn>;
    dinamicasAssociaveis: ReturnType<typeof vi.fn>;
    desbloquearCampo: ReturnType<typeof vi.fn>;
    devolverDgt: ReturnType<typeof vi.fn>;
  };
  let utilizadorIdMock: string | null;
  let atorMock: string | null;
  let perfilInternoMock: string | null;
  let notificacaoServiceMock: { criar: ReturnType<typeof vi.fn> };
  let instrumentoServiceMock: { listarTipos: ReturnType<typeof vi.fn> };

  /** Clique real de DOM: as ações dependem de `estado`/autoria, não de uma chamada direta a método. */
  function clicar(texto: string): void {
    const botao = Array.from(fixture.nativeElement.querySelectorAll('button')).find(
      (b) => (b as HTMLElement).textContent?.trim() === texto,
    ) as HTMLElement | undefined;
    if (!botao) {
      throw new Error(`Botão "${texto}" não encontrado.`);
    }
    botao.click();
    fixture.detectChanges();
  }

  function detalhe(parcial: Partial<ProcessoDetalhe> = {}): ProcessoDetalhe {
    return {
      id: 'p1',
      dinamicaId: 'd1',
      instrumentoProcedimentoConfigId: 210,
      estado: 'ENVIADO_DGT',
      numeroProcesso: '00001',
      ocorrencia: 2,
      versao: 3,
      origemDados: 'NATIVO',
      tipoAtoId: 4,
      criadoPorId: 'u1',
      dadosFormulario: '{"designacao":"Abertura da discussão pública","concelho":"Sintra"}',
      dataSubmissao: '2026-09-18T14:11:15Z',
      pcmAtribuidoAId: null,
      camposBloqueados: [],
      instrumentoId: 'i1',
      designacaoInstrumento: 'PDM de Sintra',
      numeroInstrumento: '1',
      tipoInstrumento: 'PDM',
      instrumentosAbrangidos: [{ id: 'i1', designacao: 'PDM de Sintra', tipoInstrumento: 'PDM' }],
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
      tipoDinamica: 'ALTERACAO',
      nomeDinamica: 'Alteração',
      numeroDinamica: '00001',
      descritivoDinamica: 'Alteração da planta',
      estadoDinamica: 'EM_CURSO',
      dataCaducidadeDinamica: null,
      tipoProcedimento: 'ABERTURA_DISCUSSAO_PUBLICA',
      nomeProcedimento: 'Abertura de discussão pública',
      serieDr: 'SEGUNDA_H',
      formulario: 'INFORMACAO_GERAL',
      tipoAtoNome: 'Aviso',
      tiposAtoDisponiveis: [{ id: 4, codigo: 'AVISO', nome: 'Aviso' }],
      autorNome: 'Helena Marques',
      sumario: 'Abertura do período de discussão pública',
      dataAssinatura: '2026-09-10',
      normaLegal: 'Artigo 89.º do RJIGT',
      tipoInformacao: 'TEXTO_INTEGRAL',
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
      criadoEm: '2026-09-18T14:05:00Z',
      atualizadoEm: '2026-09-18T14:11:15Z',
      ultimaRejeicaoDgt: null,
      ultimaRejeicaoIncm: null,
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
      ...respostaAoAutor(parcial.estado ?? 'ENVIADO_DGT'),
      ...parcial,
    };
  }

  /** O que o backend responde ao autor em cada estado; os testes de outros utilizadores sobrepõem-no. */
  function respostaAoAutor(estado: string): Pick<ProcessoDetalhe, 'editavel' | 'acoesDisponiveis' | 'podeSubmeter'> {
    if (estado === 'EM_SUBMISSAO') {
      return { editavel: true, acoesDisponiveis: ['submeter'], podeSubmeter: true };
    }
    if (estado === 'REJEITADO_DGT' || estado === 'ANULADO_INCM') {
      return { editavel: false, acoesDisponiveis: ['corrigir'], podeSubmeter: false };
    }
    return { editavel: false, acoesDisponiveis: estado === 'ENVIADO_DGT' ? ['resgatar'] : [], podeSubmeter: false };
  }

  const SEM_PERMISSOES: Pick<ProcessoDetalhe, 'editavel' | 'acoesDisponiveis' | 'podeSubmeter'> = {
    editavel: false,
    acoesDisponiveis: [],
    podeSubmeter: false,
  };

  let parametrosRota: BehaviorSubject<ParamMap>;

  function configurar(processoId: string | null = 'p1'): void {
    parametrosRota = new BehaviorSubject(convertToParamMap(processoId ? { processoId } : {}));
    TestBed.configureTestingModule({
      imports: [ProcessoDetalheComponent],
      providers: [
        provideRouter([]),
        { provide: ProcessoService, useValue: processoServiceMock },
        { provide: DocumentoService, useValue: documentoServiceMock },
        { provide: ListaValoresService, useValue: { listar: vi.fn().mockReturnValue(of([])), opcoes: vi.fn().mockReturnValue(of([])) } },
        { provide: ReferenciaSnitService, useValue: { nutsII: vi.fn().mockReturnValue(of([])), concelhos: vi.fn().mockReturnValue(of([])) } },
        { provide: NotificacaoService, useValue: notificacaoServiceMock },
        {
          provide: EntidadeService,
          useValue: {
            listar: vi.fn(),
            emitentes: vi.fn().mockReturnValue(
              of([
                { id: 8001, nome: 'Comissão de Coordenação e Desenvolvimento Regional de Lisboa e Vale do Tejo, IP' },
                { id: 8005, nome: 'Comissão de Coordenação e Desenvolvimento Regional do Norte, IP' },
              ]),
            ),
          },
        },
        { provide: InstrumentoService, useValue: instrumentoServiceMock },
        { provide: UtilizadorService, useValue: { listar: vi.fn() } },
        {
          provide: AuthService,
          useValue: {
            nome: () => 'Helena Marques',
            origem: () => 'INCM',
            entidade: () => null,
            perfilInterno: () => perfilInternoMock,
            utilizadorId: () => utilizadorIdMock,
            ator: () => atorMock,
          },
        },
        {
          provide: ActivatedRoute,
          useValue: { snapshot: { paramMap: parametrosRota.value }, paramMap: parametrosRota },
        },
      ],
    });

    fixture = TestBed.createComponent(ProcessoDetalheComponent);
    fixture.detectChanges();
  }

  beforeEach(() => {
    notificacaoServiceMock = { criar: vi.fn().mockReturnValue(of({ notificacoes: [], loteId: null })) };
    utilizadorIdMock = 'u1';
    atorMock = 'ENTIDADE';
    perfilInternoMock = null;
    documentoServiceMock = {
      estrutura: vi.fn().mockReturnValue(
        of({
          separadores: [
            { separador: 'Deliberação', elementoInformacaoBase: 'DELIBERACAO', publicacao: [], deposito: [] },
            { separador: 'Texto do Ato', elementoInformacaoBase: 'TEXTO_ATO', publicacao: [], deposito: [] },
          ],
          outros: [],
          pendencias: [],
        }),
      ),
      listar: vi.fn().mockReturnValue(of({ content: [documento()], page: 0, size: 100, totalElements: 1, totalPages: 1 })),
      historico: vi.fn().mockReturnValue(of({ processos: [] })),
    };
    instrumentoServiceMock = { listarTipos: vi.fn().mockReturnValue(of([])) };
    processoServiceMock = {
      obter: vi.fn().mockReturnValue(of(detalhe())),
      guardarRascunho: vi.fn().mockReturnValue(of(detalhe({ versao: 4 }))),
      resgatar: vi.fn().mockReturnValue(of(detalhe({ estado: 'EM_SUBMISSAO' }))),
      corrigir: vi.fn().mockReturnValue(of({ id: 'p1', estado: 'EM_SUBMISSAO', versao: 4 })),
      capturar: vi.fn().mockReturnValue(of({ id: 'p1', estado: 'EM_VALIDACAO_DGT', versao: 4 })),
      submeter: vi.fn().mockReturnValue(of({ id: 'p1', estado: 'ENVIADO_DGT', versao: 5 })),
      enviarIncm: vi.fn().mockReturnValue(of({ id: 'p1', estado: 'EM_VALIDACAO_INCM', versao: 4 })),
      estatisticas: vi.fn().mockReturnValue(of({ numeroSubmissoes: 1, recusas: [] })),
      dinamicasAssociaveis: vi.fn().mockReturnValue(of({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 })),
      desbloquearCampo: vi.fn(),
      devolverDgt: vi.fn().mockReturnValue(of({ id: 'p1', estado: 'VALIDADO_DGT', versao: 5, pcmAtribuidoAId: null })),
    };
  });

  it('mostra as recusas numeradas por baixo dos detalhes do processo', () => {
    processoServiceMock.estatisticas.mockReturnValue(
      of({
        numeroSubmissoes: 2,
        recusas: [
          { sequencia: 1, origem: 'DGT', submissao: 1, data: '2026-06-25T15:51:05Z', notasGerais: null, motivoIncm: [], itens: [] },
        ],
      }),
    );
    configurar('p1');

    expect(processoServiceMock.estatisticas).toHaveBeenCalledWith('p1');
    const recusas = fixture.nativeElement.querySelector('app-processo-recusas') as HTMLElement;
    expect(recusas.querySelector('[data-campo="nome"]')?.textContent?.trim()).toBe('Recusa 1');
  });

  it('pede o processo do id que vem na rota', () => {
    configurar('abc-123');

    expect(processoServiceMock.obter).toHaveBeenCalledWith('abc-123');
  });

  it('mostra a designação, o tipo de instrumento e o estado no cabeçalho', () => {
    configurar();

    const texto = fixture.nativeElement.textContent;
    expect(texto).toContain('PDM de Sintra');
    expect(texto).toContain('Número');
    expect(texto).toContain('PDM');
    expect(texto).toContain('Enviado DGT');
  });

  it('mostra os campos do detalhe que vêm de colunas', () => {
    configurar();

    const texto = fixture.nativeElement.textContent;
    expect(texto).toContain('Abertura de discussão pública');
    expect(texto).not.toContain('ABERTURA_DISCUSSAO_PUBLICA');
    expect(texto).toContain('Câmara Municipal de Sintra');
    expect(texto).toContain('Helena Marques');
    expect(texto).toContain('18/09/2026');
  });

  function valorDaLinha(rotulo: string): string | undefined {
    const linha = Array.from(fixture.nativeElement.querySelectorAll('dl > div')).find(
      (el) => (el as HTMLElement).querySelector('dt')?.textContent?.trim() === rotulo,
    ) as HTMLElement | undefined;
    return linha?.querySelector('dd')?.textContent?.trim();
  }

  it('mostra o n.º do processo e a Dinâmica pelo número e descritivo', () => {
    configurar();

    expect(valorDaLinha('N.º do processo')).toBe('00001');
    expect(valorDaLinha('Dinâmica')).toBe('00001 — Alteração da planta');
  });

  it('a designação do Instrumento nos detalhes é só texto, sem ligação', () => {
    configurar();

    expect(valorDaLinha('Instrumento')).toBe('PDM de Sintra');
    expect(fixture.nativeElement.querySelector('dl a.ligacao-instrumento')).toBeNull();
  });

  it('os detalhes listam os Instrumentos associados à Dinâmica, só em leitura', () => {
    processoServiceMock.obter.mockReturnValue(
      of(
        detalhe({
          instrumentosAbrangidos: [
            { id: 'i1', designacao: 'PDM de Sintra', tipoInstrumento: 'PDM' },
            { id: 'i2', designacao: 'PDM de Cascais', tipoInstrumento: 'PDM' },
          ],
        }),
      ),
    );
    configurar();

    const itens = [...fixture.nativeElement.querySelectorAll('dl .instrumentos-associados li')].map((li: Element) => li.textContent?.trim());
    expect(itens).toEqual(['PDM de Sintra (PDM)', 'PDM de Cascais (PDM)']);
    expect(fixture.nativeElement.querySelector('dl .instrumentos-associados a, dl .instrumentos-associados button')).toBeNull();
  });

  it('os detalhes mostram a linha do destino', () => {
    processoServiceMock.obter.mockReturnValue(of(detalhe({ destino: 'SO_DEPOSITO' })));
    configurar();

    expect(valorDaLinha('Destino')).toBe('Só depósito');
  });

  it('os detalhes mostram o número da submissão e não a ocorrência', () => {
    processoServiceMock.obter.mockReturnValue(of(detalhe({ ocorrencia: 1, submissao: 2 })));
    configurar();

    expect(valorDaLinha('Submissão do Processo n.º')).toBe('2');
  });

  it('sem Instrumentos associados os detalhes dizem «Sem IGT associado»', () => {
    processoServiceMock.obter.mockReturnValue(of(detalhe({ instrumentoId: null, instrumentosAbrangidos: [] })));
    configurar();

    expect(valorDaLinha('Instrumentos associados')).toBe('Sem IGT associado');
  });

  it('numa Dinâmica iniciada sem IGT mostra o descritivo sem ligação a um Instrumento', () => {
    processoServiceMock.obter.mockReturnValue(
      of(detalhe({ instrumentoId: null, numeroInstrumento: null, designacaoInstrumento: 'MP Sintra' })),
    );
    configurar();

    expect(fixture.nativeElement.querySelector('dl a.ligacao-instrumento')).toBeNull();
    const instrumento = [...fixture.nativeElement.querySelectorAll('dl > div')].find(
      (linha: Element) => linha.querySelector('dt')?.textContent?.trim() === 'Instrumento',
    ) as HTMLElement;
    expect(instrumento.querySelector('dd')?.textContent?.trim()).toBe('MP Sintra');
  });

  describe('ato pontual: «Ato a que respeita»', () => {
    const ATO_PONTUAL: Partial<ProcessoDetalhe> = {
      id: 'p-ret',
      numeroProcesso: '00007',
      tipoProcedimento: 'RETIFICACAO_ATO_ANEXOS',
      nomeProcedimento: 'Retificação do ato e anexos',
      atoPontual: true,
      processoReferidoId: 'p-di',
      numeroProcessoReferido: '00005',
      tipoProcedimentoReferido: 'DECISAO_INICIO',
    };

    it('mostra o processo visado com ligação para o seu detalhe', () => {
      processoServiceMock.obter.mockReturnValue(of(detalhe(ATO_PONTUAL)));
      configurar('p-ret');

      expect(valorDaLinha('Ato a que respeita')).toBe('processo n.º 00005 (Decisão de início)');
      const ligacao = fixture.nativeElement.querySelector('dl a.ligacao-ato-referido') as HTMLAnchorElement;
      expect(ligacao.getAttribute('href')).toBe('/processos/p-di');
    });

    it('fora dos atos pontuais não mostra a linha', () => {
      configurar();

      expect(fixture.nativeElement.querySelector('dl .ato-referido')).toBeNull();
    });

    it('seguir a ligação na mesma rota lê e mostra o processo visado', () => {
      processoServiceMock.obter.mockReturnValue(of(detalhe(ATO_PONTUAL)));
      configurar('p-ret');
      processoServiceMock.obter.mockReturnValue(of(detalhe({ id: 'p-di', numeroProcesso: '00005', tipoProcedimento: 'DECISAO_INICIO', nomeProcedimento: 'Decisão de início' })));

      parametrosRota.next(convertToParamMap({ processoId: 'p-di' }));
      fixture.detectChanges();

      expect(processoServiceMock.obter).toHaveBeenLastCalledWith('p-di');
      expect(valorDaLinha('N.º do processo')).toBe('00005');
      expect(fixture.nativeElement.querySelector('dl .ato-referido')).toBeNull();
    });
  });

  describe('publicação no Diário da República', () => {
    const LINK_DR = 'https://diariodarepublica.pt/dr/detalhe/aviso/1234-2026';

    function ligacaoDr(): HTMLAnchorElement | null {
      return fixture.nativeElement.querySelector('dl a.ligacao-dr');
    }

    it('num processo publicado pela INCM mostra o n.º, a data e a ligação, que abre noutro separador', () => {
      processoServiceMock.obter.mockReturnValue(
        of(detalhe({ estado: 'PUBLICADO', numeroDr: '142/2026', dataPublicacaoDr: '2026-10-21', linkDr: LINK_DR })),
      );
      configurar();

      expect(valorDaLinha('N.º do Diário da República')).toBe('142/2026');
      expect(valorDaLinha('Data de publicação')).toBe('21/10/2026');
      const ligacao = ligacaoDr()!;
      expect(ligacao.textContent?.trim()).toBe('Ver publicação');
      expect(ligacao.getAttribute('href')).toBe(LINK_DR);
      expect(ligacao.getAttribute('target')).toBe('_blank');
      expect(ligacao.getAttribute('rel')).toBe('noopener noreferrer');
      expect(ligacao.getAttribute('aria-label')).toContain('abre noutra janela');
    });

    it('num registo externo sem linkDr mostra o n.º e a data, sem a ligação', () => {
      processoServiceMock.obter.mockReturnValue(
        of(detalhe({ estado: 'PUBLICADO', origemDados: 'REGISTO_EXTERNO', numeroDr: '88/2026', dataPublicacaoDr: '2026-06-02' })),
      );
      configurar();

      expect(valorDaLinha('N.º do Diário da República')).toBe('88/2026');
      expect(valorDaLinha('Data de publicação')).toBe('02/06/2026');
      expect(valorDaLinha('Publicação')).toBeUndefined();
      expect(ligacaoDr()).toBeNull();
    });

    it('num processo ainda não publicado não mostra nenhum campo da publicação', () => {
      configurar();

      expect(fixture.nativeElement.querySelector('.publicacao-dr')).toBeNull();
      expect(fixture.nativeElement.textContent).not.toContain('Ver publicação');
    });

    it('não liga a um linkDr que não seja https', () => {
      processoServiceMock.obter.mockReturnValue(
        of(detalhe({ estado: 'PUBLICADO', numeroDr: '142/2026', dataPublicacaoDr: '2026-10-21', linkDr: 'javascript:alert(1)' })),
      );
      configurar();

      expect(valorDaLinha('N.º do Diário da República')).toBe('142/2026');
      expect(ligacaoDr()).toBeNull();
    });
  });

  describe('Validar documentos', () => {
    function ligacaoValidar(): HTMLAnchorElement | null {
      return fixture.nativeElement.querySelector('.acoes-cabecalho a.validar-documentos');
    }

    it('a DGT vê, num processo em validação, a ligação para a validação', () => {
      atorMock = 'DGT';
      perfilInternoMock = 'TECNICO';
      processoServiceMock.obter.mockReturnValue(of(detalhe({ estado: 'EM_VALIDACAO_DGT', ...SEM_PERMISSOES })));
      configurar();

      const ligacao = ligacaoValidar()!;
      expect(ligacao.textContent?.trim()).toBe('Validar documentos');
      expect(ligacao.getAttribute('href')).toBe('/processos/p1/validacao');
    });

    it.each(['ENVIADO_DGT', 'VALIDADO_DGT', 'PUBLICADO'])('a DGT não a vê com o processo em %s', (estado) => {
      atorMock = 'DGT';
      processoServiceMock.obter.mockReturnValue(of(detalhe({ estado, ...SEM_PERMISSOES })));
      configurar();

      expect(ligacaoValidar()).toBeNull();
    });

    it('um utilizador de entidade não a vê, mesmo com o processo em validação', () => {
      processoServiceMock.obter.mockReturnValue(of(detalhe({ estado: 'EM_VALIDACAO_DGT', ...SEM_PERMISSOES })));
      configurar();

      expect(ligacaoValidar()).toBeNull();
    });
  });

  it('mostra em Designação a designação do Instrumento e em Município "—" sem concelhos, sem ler o dadosFormulario', () => {
    configurar();

    expect(valorDaLinha('Designação')).toBe('PDM de Sintra');
    expect(valorDaLinha('Município')).toBe('—');
    expect(fixture.nativeElement.textContent).not.toContain('Abertura da discussão pública');
  });

  it('mostra em Município o concelho do Instrumento, com a designação do SNIT formatada', () => {
    const territorio = { concelhos: [{ codigo: '1111', nome: 'SINTRA' }], nutsII: [{ codigo: 'PT1A', nome: 'Grande Lisboa' }] };
    processoServiceMock.obter.mockReturnValue(of(detalhe({ territorio })));
    configurar();

    expect(valorDaLinha('Município')).toBe('Sintra');
  });

  it('mostra em Município os concelhos separados por vírgula, com o código quando a réplica não tem o nome', () => {
    const territorio = {
      concelhos: [
        { codigo: '1105', nome: 'VILA NOVA DE GAIA' },
        { codigo: '1111', nome: 'MONTEMOR-O-NOVO' },
        { codigo: '1106', nome: null },
      ],
      nutsII: [],
    };
    processoServiceMock.obter.mockReturnValue(of(detalhe({ territorio })));
    configurar();

    expect(valorDaLinha('Município')).toBe('Vila Nova de Gaia, Montemor-o-Novo, 1106');
  });

  it('não rebenta com um dadosFormulario ilegível', () => {
    processoServiceMock.obter.mockReturnValue(of(detalhe({ dadosFormulario: '{isto não é json' })));

    configurar();

    expect(valorDaLinha('Designação')).toBe('PDM de Sintra');
  });

  it('mostra traço na data de submissão de uma tentativa ainda por submeter', () => {
    processoServiceMock.obter.mockReturnValue(of(detalhe({ estado: 'EM_SUBMISSAO', dataSubmissao: null })));

    configurar();

    expect(fixture.nativeElement.textContent).toContain('Em submissão');
    expect(fixture.nativeElement.textContent).toContain('—');
  });

  /** Um Processo fora da visibilidade do ator responde 404. */
  it('mostra o erro e nenhum detalhe quando o processo não é visível', () => {
    const erro: ApiError = { codigo: 'NAO_ENCONTRADO', mensagem: 'Processo não encontrado.' } as ApiError;
    processoServiceMock.obter.mockReturnValue(throwError(() => erro));

    configurar();

    expect(fixture.nativeElement.querySelector('dl')).toBeNull();
    // O banner tem texto próprio por código; não repete a mensagem do backend.
    expect(fixture.nativeElement.textContent).toContain('Este recurso não existe ou não está disponível para si.');
  });

  it('não chama a API quando a rota não traz id', () => {
    configurar(null);

    expect(processoServiceMock.obter).not.toHaveBeenCalled();
    expect(fixture.nativeElement.querySelector('dl')).toBeNull();
  });

  it('não mostra prazo legal, NUT III nem separadores sem dados por trás', () => {
    configurar();

    const texto = fixture.nativeElement.textContent;
    expect(texto).not.toContain('Prazo Legal');
    expect(texto).not.toContain('NUT III');
    expect(texto).not.toContain('Documentos');
    expect(texto).not.toContain('Comunicações');
    expect(texto).not.toContain('Exportar');
  });

  it('depois de alterar o owner mostra o novo Autor e o antigo deixa de ver Editar rascunho', () => {
    const reatribuido = detalhe({ estado: 'EM_SUBMISSAO', criadoPorId: 'u2', autorNome: 'Ricardo Nunes', ...SEM_PERMISSOES });
    processoServiceMock.obter
      .mockReturnValueOnce(of(detalhe({ estado: 'EM_SUBMISSAO' })))
      .mockReturnValueOnce(of(reatribuido));
    configurar();
    expect(fixture.nativeElement.textContent).toContain('Editar rascunho');

    fixture.componentInstance.aoAlterar({ ...reatribuido, criadoPorId: 'u2', autorNome: 'Ricardo Nunes' });
    fixture.detectChanges();

    // A navbar também mostra "Helena Marques", o nome da sessão: comparar só o campo Autor.
    const texto = fixture.nativeElement.textContent;
    expect(texto).toContain('AutorRicardo Nunes');
    expect(texto).not.toContain('AutorHelena Marques');
    expect(texto).not.toContain('Editar rascunho');
  });

  // --- editar rascunho / resgatar submissão ---

  it('em edição mostra o grupo de documentos e permite carregar', () => {
    processoServiceMock.obter.mockReturnValue(of(detalhe({ estado: 'EM_SUBMISSAO' })));
    configurar();

    clicar('Editar rascunho');

    expect(documentoServiceMock.estrutura).toHaveBeenCalledWith('p1');
    expect(fixture.nativeElement.textContent).toContain('Publicação INCM e Depósito DGT');
    expect(fixture.nativeElement.querySelector('app-processo-documentos input[type="file"]')).not.toBeNull();
    expect(fixture.nativeElement.querySelector('app-ficheiros-processo')).toBeNull();
  });

  it('Guardar na edição grava as Observações depois do rascunho e só então confirma', () => {
    processoServiceMock.obter.mockReturnValue(of(detalhe({ estado: 'EM_SUBMISSAO' })));
    configurar();
    clicar('Editar rascunho');
    const gravar = vi.spyOn(fixture.componentInstance.documentosRef()!, 'gravarObservacoes').mockReturnValue(of(true));

    clicar('Guardar');

    expect(processoServiceMock.guardarRascunho).toHaveBeenCalledTimes(1);
    expect(gravar).toHaveBeenCalledTimes(1);
    expect(fixture.componentInstance.mensagemSucesso()).toBe('Rascunho guardado com sucesso.');
  });

  it('Guardar na edição fica no formulário quando as Observações falham', () => {
    processoServiceMock.obter.mockReturnValue(of(detalhe({ estado: 'EM_SUBMISSAO' })));
    configurar();
    clicar('Editar rascunho');
    vi.spyOn(fixture.componentInstance.documentosRef()!, 'gravarObservacoes').mockReturnValue(of(false));

    clicar('Guardar');

    expect(fixture.componentInstance.mensagemSucesso()).toBeNull();
    expect(fixture.componentInstance.emEdicao()).toBe(true);
    expect(fixture.componentInstance.aGuardar()).toBe(false);
  });

  it('Guardar na edição relê a estrutura documental', () => {
    processoServiceMock.obter.mockReturnValue(of(detalhe({ estado: 'EM_SUBMISSAO' })));
    configurar();
    documentoServiceMock.estrutura.mockClear();
    clicar('Editar rascunho');
    expect(documentoServiceMock.estrutura).toHaveBeenCalledTimes(1);

    clicar('Guardar');

    expect(documentoServiceMock.estrutura).toHaveBeenCalledTimes(2);
  });

  it('fora da edição mostra os ficheiros remetidos só para leitura, em ENVIADO_DGT', () => {
    configurar();

    const bloco = fixture.nativeElement.querySelector('app-ficheiros-processo') as HTMLElement;
    expect(documentoServiceMock.listar).toHaveBeenCalledWith('p1', {}, 0, 20);
    expect(bloco.textContent).toContain('Ficheiros remetidos');
    expect(bloco.textContent).toContain('deliberacao.pdf');
    expect(bloco.textContent).toContain('1 ficheiro');
    expect(fixture.nativeElement.querySelector('app-processo-documentos')).toBeNull();
    expect(fixture.nativeElement.querySelector('input[type="file"]')).toBeNull();
  });

  it('mostra o histórico documental fora da edição só quando o Procedimento tem mais de um processo', () => {
    configurar();
    expect(documentoServiceMock.historico).toHaveBeenCalledWith('p1');
    expect(fixture.nativeElement.querySelector('app-historico-documental app-cartao')).toBeNull();

    TestBed.resetTestingModule();
    documentoServiceMock.historico.mockReturnValue(
      of({
        processos: [
          { processoId: 'p0', numeroProcesso: 'P-0', ocorrencia: 1, estado: 'ANULADO', dataSubmissao: null, numeroDr: null, dataPublicacaoDr: null, linkDr: null, atual: false, documentos: [] },
          { processoId: 'p1', numeroProcesso: 'P-1', ocorrencia: 2, estado: 'ENVIADO_DGT', dataSubmissao: null, numeroDr: null, dataPublicacaoDr: null, linkDr: null, atual: true, documentos: [] },
        ],
      }),
    );
    configurar();

    expect(fixture.nativeElement.querySelector('app-historico-documental').textContent).toContain('Histórico documental');
  });

  it('não pede o histórico documental durante a edição', () => {
    processoServiceMock.obter.mockReturnValue(of(detalhe({ estado: 'EM_SUBMISSAO' })));
    configurar();
    documentoServiceMock.historico.mockClear();

    clicar('Editar rascunho');

    expect(fixture.nativeElement.querySelector('app-historico-documental')).toBeNull();
    expect(documentoServiceMock.historico).not.toHaveBeenCalled();
  });

  it('mostra os ficheiros remetidos só para leitura a quem não é o autor', () => {
    processoServiceMock.obter.mockReturnValue(of(detalhe({ estado: 'EM_SUBMISSAO', ...SEM_PERMISSOES })));
    utilizadorIdMock = 'outro';
    configurar();

    expect(fixture.nativeElement.querySelector('app-ficheiros-processo')).not.toBeNull();
    expect(fixture.nativeElement.querySelector('app-processo-documentos')).toBeNull();
  });

  it('mostra Editar rascunho quando EM_SUBMISSAO e o utilizador é o autor', () => {
    processoServiceMock.obter.mockReturnValue(of(detalhe({ estado: 'EM_SUBMISSAO' })));

    configurar();

    expect(fixture.nativeElement.textContent).toContain('Editar rascunho');
  });

  it('não mostra Editar rascunho quando o utilizador não é o autor', () => {
    processoServiceMock.obter.mockReturnValue(
      of(detalhe({ estado: 'EM_SUBMISSAO', criadoPorId: 'outro-utilizador', ...SEM_PERMISSOES })),
    );

    configurar();

    expect(fixture.nativeElement.textContent).not.toContain('Editar rascunho');
    expect(fixture.nativeElement.textContent).not.toContain('Avançar para Submissão');
  });

  it('a edição segue `editavel`, não o estado nem a autoria: sem ele não há Editar rascunho nem campos', () => {
    processoServiceMock.obter.mockReturnValue(of(detalhe({ estado: 'EM_SUBMISSAO', editavel: false, acoesDisponiveis: [] })));

    configurar();

    expect(fixture.nativeElement.textContent).not.toContain('Editar rascunho');
    expect(fixture.nativeElement.querySelector('app-processo-formulario')).toBeNull();
  });

  describe('formulário fixo do Procedimento', () => {
    const AVISO = 'Este procedimento ainda não tem formulário na aplicação. Contacte a DGT através de um pedido de apoio.';
    const SEM_FORMULARIO = (estado: string): ProcessoDetalhe =>
      detalhe({ estado, formulario: null, editavel: false, acoesDisponiveis: [], podeSubmeter: false });

    it('com INFORMACAO_GERAL a edição abre o cartão Informação Geral, como antes', () => {
      processoServiceMock.obter.mockReturnValue(of(detalhe({ estado: 'EM_SUBMISSAO' })));
      configurar();

      clicar('Editar rascunho');

      expect(fixture.nativeElement.querySelector('app-processo-formulario-inicial')).not.toBeNull();
      expect(fixture.nativeElement.textContent).not.toContain(AVISO);
    });

    it('o cartão Informação Geral mostra a NUT II e o Concelho do Instrumento', () => {
      const territorio = {
        concelhos: [{ codigo: '1111', nome: 'Sintra' }],
        nutsII: [{ codigo: 'AML', nome: 'Área Metropolitana de Lisboa' }],
      };
      processoServiceMock.obter.mockReturnValue(of(detalhe({ estado: 'EM_SUBMISSAO', territorio })));
      configurar();

      clicar('Editar rascunho');

      const contexto = fixture.nativeElement.querySelector('app-processo-formulario-inicial dl.contexto')!.textContent;
      expect(contexto).toContain('Área Metropolitana de Lisboa');
      expect(contexto).toContain('Sintra');
    });

    it.each(['EM_SUBMISSAO'])(
      'sem formulário, em %s, a entidade vê o aviso com role="status" e não tem gravar nem submeter',
      (estado) => {
        atorMock = 'ENTIDADE';
        processoServiceMock.obter.mockReturnValue(of(SEM_FORMULARIO(estado)));
        configurar();

        const aviso = Array.from(fixture.nativeElement.querySelectorAll('app-alerta') as NodeListOf<HTMLElement>).find(
          (alerta) => alerta.textContent?.trim() === AVISO,
        );
        expect(aviso).toBeDefined();
        expect(aviso?.getAttribute('role')).toBe('status');
        expect(fixture.nativeElement.querySelector('app-processo-formulario-inicial')).toBeNull();
        const textos = Array.from(fixture.nativeElement.querySelectorAll('button')).map((b) => (b as HTMLElement).textContent?.trim());
        for (const texto of ['Editar rascunho', 'Corrigir', 'Guardar', 'Avançar para Submissão', 'Voltar a submeter']) {
          expect(textos).not.toContain(texto);
        }
      },
    );

    it('sem formulário, fora dos estados editáveis ou para a DGT, não há aviso', () => {
      for (const estado of ['ENVIADO_DGT', 'REJEITADO_DGT']) {
        TestBed.resetTestingModule();
        atorMock = 'ENTIDADE';
        processoServiceMock.obter.mockReturnValue(of(SEM_FORMULARIO(estado)));
        configurar();
        expect(fixture.nativeElement.textContent).not.toContain(AVISO);
      }

      TestBed.resetTestingModule();
      atorMock = 'DGT';
      processoServiceMock.obter.mockReturnValue(of(SEM_FORMULARIO('EM_SUBMISSAO')));
      configurar();
      expect(fixture.nativeElement.textContent).not.toContain(AVISO);
    });

    it('com DISCUSSAO_PUBLICA a edição abre o cartão Informação Geral e os documentos recebem a data limite da Dinâmica', () => {
      processoServiceMock.obter.mockReturnValue(
        of(detalhe({ estado: 'EM_SUBMISSAO', formulario: 'DISCUSSAO_PUBLICA', dataCaducidadeDinamica: '2027-03-31' })),
      );
      configurar();

      clicar('Editar rascunho');

      expect(fixture.nativeElement.querySelector('app-processo-formulario-inicial')).not.toBeNull();
      expect(fixture.nativeElement.textContent).not.toContain(AVISO);
      expect(fixture.componentInstance.formularioRef()!.formulario).toBe('DISCUSSAO_PUBLICA');
      const documentos = fixture.debugElement.query(By.directive(ProcessoDocumentosComponent)).componentInstance as ProcessoDocumentosComponent;
      expect(documentos.dataLimiteDinamica).toBe('2027-03-31');
    });

    it('sem data limite na Dinâmica, os documentos recebem null', () => {
      processoServiceMock.obter.mockReturnValue(of(detalhe({ estado: 'EM_SUBMISSAO', formulario: 'DISCUSSAO_PUBLICA' })));
      configurar();

      clicar('Editar rascunho');

      const documentos = fixture.debugElement.query(By.directive(ProcessoDocumentosComponent)).componentInstance as ProcessoDocumentosComponent;
      expect(documentos.dataLimiteDinamica).toBeNull();
    });

    describe('com SRUP', () => {
      const srup = (parcial: Partial<ProcessoDetalhe> = {}): ProcessoDetalhe =>
        detalhe({
          estado: 'EM_SUBMISSAO',
          formulario: 'SRUP',
          tipoInstrumento: 'SRUP-T',
          categoriaInstrumento: 'SRUP',
          nomeDinamica: 'Delimitação',
          exigeElementosAfetados: false,
          sumario: 'Delimitação da servidão',
          ...parcial,
        });
      const servidao = (): HTMLElement | null => fixture.nativeElement.querySelector('app-processo-formulario-srup section.cartao-servidao');

      it('antes de editar não desenha o formulário', () => {
        processoServiceMock.obter.mockReturnValue(of(srup()));
        configurar();

        expect(fixture.nativeElement.querySelector('app-processo-formulario-srup')).toBeNull();
      });

      it('a edição mostra a secção Servidão só de leitura e a Informação Geral sem elementos afetados', () => {
        processoServiceMock.obter.mockReturnValue(of(srup()));
        instrumentoServiceMock.listarTipos.mockReturnValue(
          of([{ id: 9, codigo: 'SRUP-T', nome: 'Servidão de teste', categoria: 'SRUP', entidadeInstrutoraObrigatoria: false }]),
        );
        configurar();

        clicar('Editar rascunho');

        expect(fixture.nativeElement.textContent).not.toContain(AVISO);
        expect(servidao()!.querySelector('h2')!.textContent!.trim()).toBe('Servidão');
        const rotulos = Array.from(servidao()!.querySelectorAll('dt')).map((dt) => dt.textContent!.trim());
        const valores = Array.from(servidao()!.querySelectorAll('dd')).map((dd) => dd.textContent!.trim());
        expect(rotulos).toEqual(['Tipo de instrumento', 'Dinâmica']);
        expect(valores).toEqual(['Servidão de teste', 'Delimitação']);
        expect(servidao()!.querySelector('input, select, textarea')).toBeNull();
        expect(fixture.nativeElement.querySelector('app-processo-formulario-inicial dl.contexto dd')!.textContent!.trim()).toBe(
          'Servidão de teste',
        );
        const inicial = fixture.nativeElement.querySelector('app-processo-formulario-srup app-processo-formulario-inicial');
        expect(inicial).not.toBeNull();
        expect(inicial.querySelector('fieldset.elementos')).toBeNull();
        expect(fixture.componentInstance.formularioRef()!.formulario).toBe('SRUP');
      });

      it('sem o catálogo dos tipos mostra o código do tipo de Instrumento', () => {
        processoServiceMock.obter.mockReturnValue(of(srup()));
        instrumentoServiceMock.listarTipos.mockReturnValue(throwError(() => new Error('rede')));
        configurar();

        clicar('Editar rascunho');

        expect(servidao()!.querySelector('dd')!.textContent!.trim()).toBe('SRUP-T');
        expect(fixture.nativeElement.querySelector('app-processo-formulario-srup app-processo-formulario-inicial')).not.toBeNull();
      });

      it('não pergunta pelos elementos afetados mesmo que o servidor os peça ao tipo de Instrumento', () => {
        processoServiceMock.obter.mockReturnValue(of(srup({ exigeElementosAfetados: true })));
        configurar();

        clicar('Editar rascunho');

        expect(fixture.componentInstance.formularioRef()!.pedeElementosAfetados).toBe(false);
        expect(fixture.nativeElement.querySelector('fieldset.elementos')).toBeNull();
      });

      it('Guardar grava o cabeçalho comum pelo mesmo pedido, sem elementos afetados', () => {
        processoServiceMock.obter.mockReturnValue(of(srup()));
        configurar();
        clicar('Editar rascunho');

        clicar('Guardar');

        expect(processoServiceMock.guardarRascunho).toHaveBeenCalledTimes(1);
        expect(processoServiceMock.guardarRascunho.mock.calls[0][0]).toBe('p1');
        expect(processoServiceMock.guardarRascunho.mock.calls[0][1]).toMatchObject({
          versao: 3,
          sumario: 'Delimitação da servidão',
          elementosAfetados: [],
        });
      });

      it('Guardar nunca envia redesDefesa, que o servidor recusa fora do formulário SGIFR', () => {
        processoServiceMock.obter.mockReturnValue(of(srup({ redesDefesa: null, redesDefesaDisponiveis: null })));
        configurar();
        clicar('Editar rascunho');

        clicar('Guardar');

        expect(processoServiceMock.guardarRascunho).toHaveBeenCalledTimes(1);
        expect(processoServiceMock.guardarRascunho.mock.calls[0][1]).not.toHaveProperty('redesDefesa');
      });

      it('escolher a série grava o rascunho com serieDr, relê o detalhe e o Tipo de ato mostra só os tipos dessa série', () => {
        const relido = srup({
          serieDr: 'PRIMEIRA',
          seriesDrEscolhiveis: ['PRIMEIRA', 'SEGUNDA_H', 'SEGUNDA_C'],
          tiposAtoDisponiveis: [{ id: 12, codigo: 'DECRETO', nome: 'Decreto' }],
          tipoAtoId: null,
          tipoAtoNome: null,
          versao: 4,
        });
        processoServiceMock.obter.mockReturnValueOnce(
          of(srup({ serieDr: null, seriesDrEscolhiveis: ['PRIMEIRA', 'SEGUNDA_H', 'SEGUNDA_C'], tiposAtoDisponiveis: [] })),
        );
        processoServiceMock.obter.mockReturnValue(of(relido));
        configurar();
        clicar('Editar rascunho');

        const serie = fixture.nativeElement.querySelector('app-seletor.serie-dr select') as HTMLSelectElement;
        expect(serie).not.toBeNull();
        serie.value = 'PRIMEIRA';
        serie.dispatchEvent(new Event('change'));
        fixture.detectChanges();

        expect(processoServiceMock.guardarRascunho).toHaveBeenCalledTimes(1);
        expect(processoServiceMock.guardarRascunho.mock.calls[0][1]).toMatchObject({ serieDr: 'PRIMEIRA' });
        expect(processoServiceMock.obter).toHaveBeenCalledTimes(2);
        const tipos = fixture.nativeElement.querySelector('app-seletor.tipo-ato select') as HTMLSelectElement;
        expect([...tipos.options].map((opcao) => opcao.textContent?.trim())).toEqual(['Selecionar...', 'Decreto']);
        expect((fixture.nativeElement.querySelector('app-seletor.serie-dr select') as HTMLSelectElement).value).toBe('PRIMEIRA');
        expect(processoServiceMock.guardarRascunho).toHaveBeenCalledTimes(1);
      });

      it('num Procedimento com série fixa a Parte do DR fica só de leitura', () => {
        processoServiceMock.obter.mockReturnValue(of(srup({ serieDr: 'SEGUNDA_H', seriesDrEscolhiveis: null })));
        configurar();
        clicar('Editar rascunho');

        expect(fixture.nativeElement.querySelector('app-seletor.serie-dr')).toBeNull();
        expect(fixture.nativeElement.querySelector('app-processo-formulario-inicial').textContent).toContain('Parte do DR');
      });
    });

    describe('com RATIFICACAO', () => {
      const ratificacao = (parcial: Partial<ProcessoDetalhe> = {}): ProcessoDetalhe =>
        detalhe({
          estado: 'EM_SUBMISSAO',
          formulario: 'RATIFICACAO',
          serieDr: 'PRIMEIRA',
          tipoInstrumento: 'PDM',
          categoriaInstrumento: 'PLANO',
          exigeElementosAfetados: false,
          designacaoInstrumento: 'PDM de Sintra',
          instrumentosAbrangidos: [{ id: 'i1', designacao: 'PDM de Sintra', tipoInstrumento: 'PDM' }],
          territorio: { nutsII: [{ codigo: '1B', nome: 'Grande Lisboa' }], concelhos: [{ codigo: '1111', nome: 'Sintra' }] },
          modalidadeRatificacao: 'ALTERACAO',
          sumario: 'Ratificação do PDM',
          publicarSuplemento: false,
          dataAssinatura: '2026-10-01',
          ...parcial,
        });
      const seccao = (): HTMLElement | null => fixture.nativeElement.querySelector('app-formulario-ratificacao section.cartao-ratificacao');
      const opcoes = (): HTMLInputElement[] =>
        Array.from(seccao()!.querySelectorAll<HTMLInputElement>('fieldset.modalidade input[type="radio"]'));

      beforeEach(() => {
        instrumentoServiceMock.listarTipos.mockReturnValue(
          of([{ id: 1, codigo: 'PDM', nome: 'Plano Diretor Municipal', categoria: 'PLANO', entidadeInstrutoraObrigatoria: false }]),
        );
      });

      it('a edição mostra a secção Ratificação com a modalidade gravada e os campos da 1.ª série da EF', () => {
        processoServiceMock.obter.mockReturnValue(of(ratificacao()));
        configurar();

        clicar('Editar rascunho');

        expect(fixture.nativeElement.textContent).not.toContain(AVISO);
        expect(seccao()!.querySelector('h2')!.textContent!.trim()).toBe('Ratificação');
        expect(seccao()!.querySelector('.tipo-instrumento')!.textContent!.trim()).toBe('Plano Diretor Municipal');
        expect(seccao()!.querySelector('.designacao')!.textContent!.trim()).toBe('PDM de Sintra');
        expect(opcoes()[1].checked).toBe(true);
        const inicial: HTMLElement = fixture.nativeElement.querySelector('app-formulario-ratificacao app-processo-formulario-inicial');
        expect(inicial.querySelector('dl.contexto .tipo-procedimento')).toBeNull();
        expect(inicial.querySelector('input[formcontrolname="normaLegal"]')).not.toBeNull();
        expect(inicial.querySelectorAll('input[formcontrolname="tipoInformacao"]')).toHaveLength(2);
        const publicacao = inicial.querySelector('fieldset.publicacao')!;
        expect(publicacao.querySelector('legend')!.textContent!.trim()).toBe('Publicação');
        expect(Array.from(publicacao.querySelectorAll('label')).map((label) => label.textContent!.trim())).toEqual(['Diário', 'Suplemento']);
        const tipoInformacao = inicial.querySelector('input[formcontrolname="tipoInformacao"]')!;
        const normaLegal = inicial.querySelector('input[formcontrolname="normaLegal"]')!;
        expect(normaLegal.compareDocumentPosition(publicacao) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
        const dataAssinatura = inicial.querySelector<HTMLInputElement>('input.data-assinatura')!;
        expect(dataAssinatura.type).toBe('date');
        expect(dataAssinatura.getAttribute('aria-required')).toBe('true');
        expect(dataAssinatura.closest('label')!.textContent).toContain('Data de Assinatura do ato');
        expect(dataAssinatura.value).toBe('2026-10-01');
        expect(dataAssinatura.compareDocumentPosition(publicacao) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
        expect(publicacao.compareDocumentPosition(tipoInformacao) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
        expect(inicial.querySelector('fieldset.elementos')).toBeNull();
        expect(fixture.componentInstance.formularioRef()!.pedeElementosAfetados).toBe(false);
      });

      it('Guardar envia a modalidade escolhida e a data de assinatura do formulário com o cabeçalho comum', () => {
        processoServiceMock.obter.mockReturnValue(of(ratificacao({ modalidadeRatificacao: null, dataAssinatura: null })));
        configurar();
        clicar('Editar rascunho');
        opcoes()[1].click();
        const data = fixture.nativeElement.querySelector('input.data-assinatura') as HTMLInputElement;
        data.value = '2026-11-02';
        data.dispatchEvent(new Event('input'));
        fixture.detectChanges();

        clicar('Guardar');

        expect(processoServiceMock.guardarRascunho.mock.calls[0][1]).toMatchObject({
          versao: 3,
          modalidadeRatificacao: 'ALTERACAO',
          dataAssinatura: '2026-11-02',
          publicarSuplemento: false,
          elementosAfetados: [],
        });
      });

      it('o erro 400 em modalidadeRatificacao fica junto do grupo e não no banner', () => {
        processoServiceMock.obter.mockReturnValue(of(ratificacao({ modalidadeRatificacao: null })));
        processoServiceMock.guardarRascunho.mockReturnValue(
          throwError(
            () =>
              new ApiError('VALIDACAO', 'Valores aceites: PUBLICACAO, ALTERACAO.', 't9', [
                { path: 'modalidadeRatificacao', erro: 'Valores aceites: PUBLICACAO, ALTERACAO.' },
              ]),
          ),
        );
        configurar();
        clicar('Editar rascunho');

        clicar('Guardar');

        const erro = seccao()!.querySelector<HTMLElement>('.erro-campo')!;
        expect(erro.textContent!.trim()).toBe('Valores aceites: PUBLICACAO, ALTERACAO.');
        expect(erro.getAttribute('role')).toBe('alert');
        expect(fixture.componentInstance.erro()).toBeNull();
      });

      it('depois da primeira submissão a modalidade segue o bloqueio dos campos', () => {
        processoServiceMock.obter.mockReturnValue(of(ratificacao({ camposBloqueados: ['modalidadeRatificacao', 'sumario'] })));
        configurar();

        clicar('Editar rascunho');

        expect(opcoes().every((opcao) => opcao.disabled)).toBe(true);
        expect(seccao()!.querySelector('.aviso-bloqueado')!.textContent!.trim()).toBe('Bloqueado depois da primeira submissão');
      });

      it('desbloqueada pela DGT, a modalidade volta a poder mudar', () => {
        processoServiceMock.obter.mockReturnValue(of(ratificacao({ camposBloqueados: ['sumario'] })));
        configurar();

        clicar('Editar rascunho');

        expect(opcoes().every((opcao) => !opcao.disabled)).toBe(true);
        expect(seccao()!.querySelector('.aviso-bloqueado')).toBeNull();
      });
    });

    describe('com SUSPENSAO_CCDR', () => {
      const suspensao = (parcial: Partial<ProcessoDetalhe> = {}): ProcessoDetalhe =>
        detalhe({
          estado: 'EM_SUBMISSAO',
          formulario: 'SUSPENSAO_CCDR',
          serieDr: 'SEGUNDA_C',
          tipoInstrumento: 'PDM',
          categoriaInstrumento: 'PLANO',
          nomeProcedimento: 'Suspensão (CCDR)',
          exigeElementosAfetados: false,
          designacaoInstrumento: 'Suspensão dos PDM da Grande Lisboa',
          instrumentosAbrangidos: [
            { id: 'i1', designacao: 'PDM de Sintra', tipoInstrumento: 'PDM' },
            { id: 'i2', designacao: 'PDM de Cascais', tipoInstrumento: 'PDM' },
          ],
          entidadeEmitente: 'Comissão de Coordenação e Desenvolvimento Regional de Lisboa e Vale do Tejo, IP',
          sumario: 'Suspensão parcial',
          normaLegal: 'Artigo 126.º do RJIGT',
          tipoInformacao: 'TEXTO_INTEGRAL',
          dataAssinatura: '2026-11-02',
          ...parcial,
        });
      const seccao = (): HTMLElement | null =>
        fixture.nativeElement.querySelector('app-formulario-suspensao-ccdr section.cartao-suspensao');

      beforeEach(() => {
        instrumentoServiceMock.listarTipos.mockReturnValue(
          of([{ id: 1, codigo: 'PDM', nome: 'Plano Diretor Municipal', categoria: 'PLANO', entidadeInstrutoraObrigatoria: false }]),
        );
      });

      it('a edição mostra a secção da Suspensão, a lista das CCDR, a data de assinatura e nenhum elemento afetado', () => {
        processoServiceMock.obter.mockReturnValue(of(suspensao()));
        configurar();

        clicar('Editar rascunho');

        expect(fixture.nativeElement.textContent).not.toContain(AVISO);
        expect(seccao()!.querySelector('h2')!.textContent!.trim()).toBe('Suspensão da iniciativa da CCDR');
        expect(seccao()!.querySelector('.tipo-instrumento')!.textContent!.trim()).toBe('Plano Diretor Municipal');
        expect(seccao()!.querySelector('.tipo-procedimento')!.textContent!.trim()).toBe('Suspensão (CCDR)');
        expect(seccao()!.querySelector('.designacao')!.textContent!.trim()).toBe('Suspensão dos PDM da Grande Lisboa');
        const inicial: HTMLElement = fixture.nativeElement.querySelector('app-formulario-suspensao-ccdr app-processo-formulario-inicial');
        expect(inicial.querySelector('dl.contexto .tipo-procedimento')).toBeNull();
        expect((inicial.querySelector('.entidade-emitente-lista select') as HTMLSelectElement).value).toBe('Comissão de Coordenação e Desenvolvimento Regional de Lisboa e Vale do Tejo, IP');
        expect((inicial.querySelector('input.data-assinatura') as HTMLInputElement).value).toBe('2026-11-02');
        expect(inicial.querySelector('fieldset.publicacao')).toBeNull();
        expect(inicial.querySelector('fieldset.elementos')).toBeNull();
      });

      it('Guardar envia a entidade emitente escolhida da lista e a data de assinatura do formulário', () => {
        processoServiceMock.obter.mockReturnValue(of(suspensao()));
        configurar();
        clicar('Editar rascunho');
        const select = fixture.nativeElement.querySelector('.entidade-emitente-lista select') as HTMLSelectElement;
        select.value = 'Comissão de Coordenação e Desenvolvimento Regional do Norte, IP';
        select.dispatchEvent(new Event('change'));
        fixture.detectChanges();

        clicar('Guardar');

        expect(processoServiceMock.guardarRascunho.mock.calls[0][1]).toMatchObject({
          versao: 3,
          entidadeEmitente: 'Comissão de Coordenação e Desenvolvimento Regional do Norte, IP',
          dataAssinatura: '2026-11-02',
          normaLegal: 'Artigo 126.º do RJIGT',
          tipoInformacao: 'TEXTO_INTEGRAL',
          elementosAfetados: [],
        });
      });
    });

    describe('com os formulários do Governo', () => {
      const governo = (parcial: Partial<ProcessoDetalhe> = {}): ProcessoDetalhe =>
        detalhe({
          estado: 'EM_SUBMISSAO',
          formulario: 'SUSPENSAO_MP_GOVERNO',
          serieDr: 'PRIMEIRA',
          tipoInstrumento: 'PDM',
          categoriaInstrumento: 'PLANO',
          nomeProcedimento: 'Publicação da suspensão (Governo)',
          exigeElementosAfetados: false,
          designacaoInstrumento: 'Suspensão dos PDM da Grande Lisboa',
          instrumentosAbrangidos: [
            { id: 'i1', designacao: 'PDM de Sintra', tipoInstrumento: 'PDM' },
            { id: 'i2', designacao: 'PDM de Cascais', tipoInstrumento: 'PDM' },
          ],
          entidadeEmitente: 'Secretaria de Estado do Ordenamento do Território',
          contactoTramitacao: 'tramitacao@governo-teste.pt',
          contactoPublicacao: 'Gabinete, 210 000 000',
          sumario: 'Suspensão parcial',
          normaLegal: 'Artigo 126.º do RJIGT',
          tipoInformacao: 'TEXTO_INTEGRAL',
          publicarSuplemento: false,
          dataAssinatura: '2026-11-02',
          ...parcial,
        });
      const seccao = (): HTMLElement | null => fixture.nativeElement.querySelector('app-formulario-governo section.cartao-governo');

      beforeEach(() => {
        instrumentoServiceMock.listarTipos.mockReturnValue(
          of([{ id: 1, codigo: 'PDM', nome: 'Plano Diretor Municipal', categoria: 'PLANO', entidadeInstrutoraObrigatoria: false }]),
        );
      });

      it('a edição da Publicação mostra a secção do Governo com os Instrumentos associados e o cartão com a Publicação', () => {
        processoServiceMock.obter.mockReturnValue(of(governo()));
        configurar();

        clicar('Editar rascunho');

        expect(fixture.nativeElement.textContent).not.toContain(AVISO);
        expect(seccao()!.querySelector('h2')!.textContent!.trim()).toBe('Suspensão / Medidas Preventivas (Governo)');
        expect(seccao()!.querySelector('.tipo-instrumento')!.textContent!.trim()).toBe('Plano Diretor Municipal');
        expect(seccao()!.querySelector('.designacao')!.textContent!.trim()).toBe('Suspensão dos PDM da Grande Lisboa');
        expect(seccao()!.querySelectorAll('.instrumentos-associados li')).toHaveLength(2);
        const inicial: HTMLElement = fixture.nativeElement.querySelector('app-formulario-governo app-processo-formulario-inicial');
        expect(inicial.querySelector('fieldset.publicacao')).not.toBeNull();
        expect((inicial.querySelector('input.data-assinatura') as HTMLInputElement).value).toBe('2026-11-02');
        expect(inicial.querySelector('fieldset.elementos')).toBeNull();
      });

      it('Guardar na Publicação envia a Publicação escolhida e a data de assinatura do formulário', () => {
        processoServiceMock.obter.mockReturnValue(of(governo()));
        configurar();
        clicar('Editar rascunho');

        clicar('Guardar');

        expect(processoServiceMock.guardarRascunho.mock.calls[0][1]).toMatchObject({
          versao: 3,
          entidadeEmitente: 'Secretaria de Estado do Ordenamento do Território',
          publicarSuplemento: false,
          dataAssinatura: '2026-11-02',
          normaLegal: 'Artigo 126.º do RJIGT',
          tipoInformacao: 'TEXTO_INTEGRAL',
          elementosAfetados: [],
        });
      });

      it('a Prorrogação mostra a secção da Prorrogação sem a Publicação e não a envia', () => {
        processoServiceMock.obter.mockReturnValue(
          of(governo({ formulario: 'PRORROGACAO_GOVERNO', nomeProcedimento: 'Prorrogação das medidas preventivas (Governo)' })),
        );
        configurar();
        clicar('Editar rascunho');

        expect(seccao()!.querySelector('h2')!.textContent!.trim()).toBe('Prorrogação da Suspensão / Medidas Preventivas (Governo)');
        const inicial: HTMLElement = fixture.nativeElement.querySelector('app-formulario-governo app-processo-formulario-inicial');
        expect(inicial.querySelector('fieldset.publicacao')).toBeNull();
        expect(inicial.querySelector('input[formcontrolname="publicarSuplemento"]')).toBeNull();

        clicar('Guardar');
        expect(processoServiceMock.guardarRascunho.mock.calls[0][1]).toMatchObject({ publicarSuplemento: null, dataSuplemento: null });
      });
    });

    describe('com PUBLICACAO_ALTERACAO', () => {
      const publicacaoAlteracao = (parcial: Partial<ProcessoDetalhe> = {}): ProcessoDetalhe =>
        detalhe({
          estado: 'EM_SUBMISSAO',
          formulario: 'PUBLICACAO_ALTERACAO',
          comGeoconform: false,
          elementosAfetados: ['PECAS_GRAFICAS', 'PECAS_ESCRITAS'],
          dadosFormulario: '{"introduzPlantaNova":true}',
          ...parcial,
        });
      const pergunta = (): HTMLElement | null => fixture.nativeElement.querySelector('fieldset.planta-nova');
      const pedidoGravado = (): Record<string, unknown> => processoServiceMock.guardarRascunho.mock.calls[0][1];

      it('a edição abre o cartão Informação Geral com a pergunta da planta nova e a resposta gravada', () => {
        processoServiceMock.obter.mockReturnValue(of(publicacaoAlteracao()));
        configurar();

        clicar('Editar rascunho');

        expect(fixture.nativeElement.querySelector('app-processo-formulario-inicial')).not.toBeNull();
        expect(fixture.nativeElement.textContent).not.toContain(AVISO);
        expect(fixture.componentInstance.formularioRef()!.formulario).toBe('PUBLICACAO_ALTERACAO');
        expect(pergunta()!.querySelector('legend')!.textContent!.trim()).toBe('A alteração introduz alguma planta nova?');
        const sim = Array.from(pergunta()!.querySelectorAll('input[type="radio"]') as NodeListOf<HTMLInputElement>)[0];
        expect(sim.checked).toBe(true);
      });

      it('com GeoConform as Peças Gráficas ficam fixas e não há pergunta da planta nova nem do GeoConform', () => {
        processoServiceMock.obter.mockReturnValue(
          of(publicacaoAlteracao({ comGeoconform: true, elementosAfetados: ['PECAS_GRAFICAS'], dadosFormulario: null })),
        );
        configurar();

        clicar('Editar rascunho');

        const graficas = fixture.nativeElement.querySelector('fieldset.elementos input[type="checkbox"]:checked:disabled');
        expect(graficas).not.toBeNull();
        expect(fixture.nativeElement.querySelector('#ajuda-pecasGraficas')).not.toBeNull();
        expect(pergunta()).toBeNull();
        expect(fixture.nativeElement.querySelector('fieldset.geoconform')).toBeNull();
      });

      it('Guardar envia a resposta no pedido, com as Peças Gráficas da Dinâmica com GeoConform', () => {
        processoServiceMock.obter.mockReturnValue(of(publicacaoAlteracao()));
        configurar();
        clicar('Editar rascunho');

        clicar('Guardar');

        expect(pedidoGravado()).toMatchObject({ introduzPlantaNova: true, elementosAfetados: ['PECAS_ESCRITAS', 'PECAS_GRAFICAS'] });
      });

      it('Guardar numa Dinâmica com GeoConform envia a resposta nula e as Peças Gráficas', () => {
        processoServiceMock.obter.mockReturnValue(
          of(publicacaoAlteracao({ comGeoconform: true, elementosAfetados: ['PECAS_GRAFICAS'], dadosFormulario: null })),
        );
        configurar();
        clicar('Editar rascunho');

        clicar('Guardar');

        expect(pedidoGravado()).toMatchObject({ introduzPlantaNova: null, elementosAfetados: ['PECAS_GRAFICAS'] });
      });

      it('com INFORMACAO_GERAL o pedido não leva introduzPlantaNova', () => {
        processoServiceMock.obter.mockReturnValue(of(detalhe({ estado: 'EM_SUBMISSAO', dadosFormulario: '{"introduzPlantaNova":true}' })));
        configurar();
        clicar('Editar rascunho');

        clicar('Guardar');

        expect('introduzPlantaNova' in pedidoGravado()).toBe(false);
        expect(pergunta()).toBeNull();
      });

      it('depois da primeira submissão a pergunta fica desativada, com o aviso', () => {
        processoServiceMock.obter.mockReturnValue(
          of(publicacaoAlteracao({ camposBloqueados: ['elementosAfetados', 'introduzPlantaNova'] })),
        );
        configurar();

        clicar('Editar rascunho');

        const radios = Array.from(pergunta()!.querySelectorAll('input[type="radio"]') as NodeListOf<HTMLInputElement>);
        expect(radios.every((radio) => radio.disabled)).toBe(true);
        expect(pergunta()!.querySelector('#bloqueado-introduzPlantaNova')!.textContent!.trim()).toBe(
          'Bloqueado depois da primeira submissão',
        );
      });

      it('o erro 400 do campo introduzPlantaNova aparece junto da pergunta', () => {
        processoServiceMock.obter.mockReturnValue(of(publicacaoAlteracao({ dadosFormulario: null })));
        processoServiceMock.guardarRascunho.mockReturnValue(
          throwError(
            () =>
              new ApiError('VALIDACAO', 'Indique se a alteração introduz alguma planta nova.', 't9', [
                { path: 'introduzPlantaNova', erro: 'Indique se a alteração introduz alguma planta nova.' },
              ]),
          ),
        );
        configurar();
        clicar('Editar rascunho');

        clicar('Guardar');

        expect(pergunta()!.querySelector('#erro-introduzPlantaNova')!.textContent!.trim()).toBe(
          'Indique se a alteração introduz alguma planta nova.',
        );
        expect(fixture.componentInstance.erro()).toBeNull();
      });
    });
  });

  it('sem `submeter` na lista o editor não oferece Avançar para Submissão', () => {
    processoServiceMock.obter.mockReturnValue(of(detalhe({ estado: 'EM_SUBMISSAO', editavel: true, acoesDisponiveis: [] })));
    configurar();

    clicar('Editar rascunho');

    expect(fixture.nativeElement.textContent).toContain('Guardar');
    expect(fixture.nativeElement.textContent).not.toContain('Avançar para Submissão');
  });

  it('com `podeSubmeter` falso o editor continua a oferecer Avançar para Submissão: o Resumo explica o que falta', () => {
    processoServiceMock.obter.mockReturnValue(of(detalhe({ estado: 'EM_SUBMISSAO', podeSubmeter: false })));
    configurar();

    clicar('Editar rascunho');

    expect(fixture.nativeElement.textContent).toContain('Avançar para Submissão');
  });

  it('não mostra Editar rascunho fora de EM_SUBMISSAO', () => {
    configurar(); // estado por omissão: ENVIADO_DGT

    expect(fixture.nativeElement.textContent).not.toContain('Editar rascunho');
  });

  it('Editar rascunho não mostra o formulário gerado a partir do schema', () => {
    processoServiceMock.obter.mockReturnValue(of(detalhe({ estado: 'EM_SUBMISSAO' })));
    configurar();

    clicar('Editar rascunho');

    expect(fixture.nativeElement.querySelector('app-processo-formulario')).toBeNull();
    expect(fixture.nativeElement.querySelector('app-processo-formulario-inicial')).not.toBeNull();
  });

  it('Guardar grava o rascunho com PUT sobre a mesma tentativa, sem criar uma nova', () => {
    processoServiceMock.obter.mockReturnValue(of(detalhe({ estado: 'EM_SUBMISSAO' })));
    configurar();
    clicar('Editar rascunho');

    clicar('Guardar');

    expect(processoServiceMock.guardarRascunho).toHaveBeenCalledTimes(1);
    expect(processoServiceMock.guardarRascunho.mock.calls[0][0]).toBe('p1');
  });

  it('Editar rascunho de um Programa mostra a Entidade instrutora gravada; num Plano o campo não aparece', () => {
    processoServiceMock.obter.mockReturnValue(
      of(detalhe({ estado: 'EM_SUBMISSAO', categoriaInstrumento: 'PROGRAMA', entidadeInstrutora: 'CCDR de Lisboa' })),
    );
    configurar();
    clicar('Editar rascunho');

    const campo = fixture.nativeElement.querySelector('input[formcontrolname="entidadeInstrutora"]') as HTMLInputElement;
    expect(campo.value).toBe('CCDR de Lisboa');
  });

  it('Editar rascunho de um Plano não mostra a Entidade instrutora', () => {
    processoServiceMock.obter.mockReturnValue(of(detalhe({ estado: 'EM_SUBMISSAO' })));
    configurar();
    clicar('Editar rascunho');

    expect(fixture.nativeElement.querySelector('app-processo-formulario-inicial')).not.toBeNull();
    expect(fixture.nativeElement.querySelector('input[formcontrolname="entidadeInstrutora"]')).toBeNull();
  });

  it('Editar rascunho mostra o cartão Informação Geral, sem o cartão Publicação INCM, com os valores gravados', () => {
    processoServiceMock.obter.mockReturnValue(of(detalhe({ estado: 'EM_SUBMISSAO' })));
    configurar();

    clicar('Editar rascunho');

    const titulos = Array.from(fixture.nativeElement.querySelectorAll('h2') as NodeListOf<HTMLElement>).map((h) => h.textContent?.trim());
    expect(titulos).toContain('Informação Geral');
    expect(titulos).not.toContain('Publicação INCM');
    expect(fixture.nativeElement.querySelectorAll('app-processo-formulario-inicial')).toHaveLength(1);
    expect(fixture.componentInstance.formularioRef()!.form.getRawValue()).toEqual({
      sumario: 'Abertura do período de discussão pública',
      normaLegal: 'Artigo 89.º do RJIGT',
      tipoInformacao: 'TEXTO_INTEGRAL',
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
      tipoAtoId: '4',
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

  it('Editar rascunho mostra a Informação Base gravada e a data de assinatura no bloco do Texto do Ato', () => {
    processoServiceMock.obter.mockReturnValue(
      of(
        detalhe({
          estado: 'EM_SUBMISSAO',
          dadosFormulario: JSON.stringify({
            informacaoBase: {
              deliberacao: { prazoAnos: 1, prazoMeses: 2, prazoDias: 3, avaliacaoAmbiental: true, dataDeliberacao: '2026-09-01', textoPublicar: 'Texto.' },
              textoAto: { prazoAnos: 4, prazoMeses: null, prazoDias: null, avaliacaoAmbiental: false },
            },
          }),
        }),
      ),
    );
    configurar();

    clicar('Editar rascunho');

    expect(fixture.componentInstance.documentosRef()!.form.getRawValue()).toEqual({
      deliberacao: { prazoAnos: 1, prazoMeses: 2, prazoDias: 3, avaliacaoAmbiental: true, dataDeliberacao: '2026-09-01', textoPublicar: 'Texto.' },
      textoAto: { prazoAnos: 4, prazoMeses: null, prazoDias: null, avaliacaoAmbiental: false },
      dataAssinatura: '2026-09-10',
    });
    expect(fixture.nativeElement.textContent).toContain('Data de Assinatura do ato');
  });

  it('Guardar grava a Informação Base e a data de assinatura do bloco no mesmo PUT', () => {
    processoServiceMock.obter.mockReturnValue(of(detalhe({ estado: 'EM_SUBMISSAO' })));
    configurar();
    clicar('Editar rascunho');
    fixture.componentInstance.documentosRef()!.form.patchValue({
      deliberacao: { prazoMeses: 3, textoPublicar: 'Novo texto.' },
      textoAto: { avaliacaoAmbiental: true },
      dataAssinatura: '2026-10-01',
    });

    clicar('Guardar');

    expect(processoServiceMock.guardarRascunho).toHaveBeenCalledTimes(1);
    expect(processoServiceMock.guardarRascunho).toHaveBeenCalledWith(
      'p1',
      expect.objectContaining({
        dataAssinatura: '2026-10-01',
        informacaoBase: {
          deliberacao: { prazoAnos: null, prazoMeses: 3, prazoDias: null, avaliacaoAmbiental: null, dataDeliberacao: null, textoPublicar: 'Novo texto.' },
          textoAto: { prazoAnos: null, prazoMeses: null, prazoDias: null, avaliacaoAmbiental: true },
        },
      }),
    );
    expect(fixture.componentInstance.processo()?.dataAssinatura).toBe('2026-10-01');
  });

  it('um 400 com o caminho da Informação Base aparece junto do campo e não no banner', () => {
    processoServiceMock.obter.mockReturnValue(of(detalhe({ estado: 'EM_SUBMISSAO' })));
    processoServiceMock.guardarRascunho.mockReturnValue(
      throwError(
        () =>
          new ApiError('VALIDACAO', 'Payload inválido.', 't1', [
            { path: 'informacaoBase.textoAto.prazoAnos', erro: 'O prazo tem de ser um número inteiro não negativo.' },
          ]),
      ),
    );
    configurar();
    clicar('Editar rascunho');

    clicar('Guardar');

    expect(fixture.nativeElement.querySelector('.informacao-base .erro-campo')?.textContent).toContain('número inteiro não negativo');
    expect(fixture.componentInstance.erro()).toBeNull();
  });

  it('um 400 por Informação Base não prevista aparece no bloco do elemento e não no banner', () => {
    processoServiceMock.obter.mockReturnValue(of(detalhe({ estado: 'EM_SUBMISSAO' })));
    processoServiceMock.guardarRascunho.mockReturnValue(
      throwError(
        () =>
          new ApiError('VALIDACAO', 'Informação Base não prevista neste procedimento.', 't1', [
            { path: 'informacaoBase.textoAto', erro: 'Este procedimento não prevê Informação Base no Texto do ato.' },
          ]),
      ),
    );
    configurar();
    clicar('Editar rascunho');

    clicar('Guardar');

    expect(fixture.nativeElement.querySelector('.informacao-base .erro-campo')?.textContent).toContain('não prevê Informação Base no Texto do ato');
    expect(fixture.componentInstance.erro()).toBeNull();
  });

  it('o formulário de 2.ª série pede norma legal e não suplemento', () => {
    processoServiceMock.obter.mockReturnValue(of(detalhe({ estado: 'EM_SUBMISSAO' })));
    configurar();

    clicar('Editar rascunho');

    expect(fixture.nativeElement.textContent).toContain('Norma legal');
    expect(fixture.nativeElement.textContent).not.toContain('Publicar suplemento');
  });

  describe('formulário SGIFR', () => {
    const REDES_PSA = ['AEMGC', 'APPS', 'RPA', 'RSFGC', 'RVDI', 'RVF'].map((codigo) => ({ codigo, nome: `Rede ${codigo}` }));
    const sgifr = (parcial: Partial<ProcessoDetalhe> = {}): ProcessoDetalhe =>
      detalhe({
        estado: 'EM_SUBMISSAO',
        formulario: 'SGIFR',
        categoriaInstrumento: 'PROGRAMA',
        tipoInstrumento: 'PSA',
        exigeElementosAfetados: false,
        redesDefesa: [{ codigo: 'APPS', nome: 'Rede APPS' }],
        redesDefesaDisponiveis: REDES_PSA,
        ...parcial,
      });
    const caixas = (): HTMLInputElement[] => [
      ...fixture.nativeElement.querySelectorAll('fieldset.redes-defesa input[type="checkbox"]'),
    ];

    it('Editar rascunho mostra as redes que o programa aceita, com as gravadas marcadas, e o texto integral fixo', () => {
      processoServiceMock.obter.mockReturnValue(of(sgifr()));
      configurar();

      clicar('Editar rascunho');

      expect(fixture.componentInstance.formularioRef()!.formulario).toBe('SGIFR');
      expect(caixas()).toHaveLength(6);
      expect(caixas().filter((c) => c.checked).map((c) => c.value)).toEqual(['APPS']);
      expect(fixture.nativeElement.querySelector('input[formcontrolname="tipoInformacao"]')).toBeNull();
      expect(fixture.nativeElement.querySelector('fieldset.elementos')).toBeNull();
    });

    it('Guardar leva os códigos das redes marcadas e o detalhe passa a ter as redes com o nome', () => {
      processoServiceMock.obter.mockReturnValue(of(sgifr()));
      configurar();
      clicar('Editar rascunho');
      caixas().find((c) => c.value === 'RVF')!.click();
      fixture.detectChanges();

      clicar('Guardar');

      const pedido = processoServiceMock.guardarRascunho.mock.calls[0][1] as Record<string, unknown>;
      expect(pedido['redesDefesa']).toEqual(['APPS', 'RVF']);
      expect(pedido['tipoInformacao']).toBe('TEXTO_INTEGRAL');
      expect(fixture.componentInstance.processo()!.redesDefesa).toEqual([
        { codigo: 'APPS', nome: 'Rede APPS' },
        { codigo: 'RVF', nome: 'Rede RVF' },
      ]);
    });

    it('num PRA, com uma só rede, não há caixas e o pedido não leva redesDefesa', () => {
      processoServiceMock.obter.mockReturnValue(
        of(sgifr({ tipoInstrumento: 'PRA', redesDefesa: [], redesDefesaDisponiveis: [{ codigo: 'RPFGC', nome: 'Rede primária' }] })),
      );
      configurar();
      clicar('Editar rascunho');

      expect(caixas()).toHaveLength(0);
      expect(fixture.nativeElement.textContent).toContain('Rede primária');

      clicar('Guardar');

      expect(processoServiceMock.guardarRascunho.mock.calls[0][1]).not.toHaveProperty('redesDefesa');
    });

    describe('Região e CIM / AM', () => {
      const territorio: Partial<ProcessoDetalhe> = {
        pedeRegiao: true,
        opcoesRegiao: [
          { codigo: 'NORTE', descricao: 'Norte' },
          { codigo: 'CENTRO', descricao: 'Centro' },
        ],
        regiao: 'NORTE',
        regiaoDescricao: 'Norte',
        pedeCimAm: true,
        opcoesCimAm: [
          { codigo: 'ALTO_MINHO', descricao: 'CIM do Alto Minho', regiao: 'NORTE' },
          { codigo: 'COIMBRA', descricao: 'CIM da Região de Coimbra', regiao: 'CENTRO' },
        ],
        cimAm: 'ALTO_MINHO',
        cimAmDescricao: 'CIM do Alto Minho',
      };

      function escolher(classe: string, valor: string): void {
        const select = fixture.nativeElement.querySelector(`app-seletor.${classe} select`) as HTMLSelectElement;
        select.value = valor;
        select.dispatchEvent(new Event('change'));
        fixture.detectChanges();
      }

      it('Guardar leva a Região e a CIM / AM escolhidas e o detalhe fica com as descrições novas', () => {
        processoServiceMock.obter.mockReturnValue(of(sgifr(territorio)));
        configurar();
        clicar('Editar rascunho');

        escolher('regiao', 'CENTRO');
        escolher('cim-am', 'COIMBRA');
        clicar('Guardar');

        const pedido = processoServiceMock.guardarRascunho.mock.calls[0][1] as Record<string, unknown>;
        expect(pedido['regiao']).toBe('CENTRO');
        expect(pedido['cimAm']).toBe('COIMBRA');
        expect(fixture.componentInstance.processo()).toMatchObject({
          regiao: 'CENTRO',
          regiaoDescricao: 'Centro',
          cimAm: 'COIMBRA',
          cimAmDescricao: 'CIM da Região de Coimbra',
        });
      });

      it('o 400 da CIM / AM aparece junto do campo e não no banner', () => {
        processoServiceMock.obter.mockReturnValue(of(sgifr(territorio)));
        processoServiceMock.guardarRascunho.mockReturnValue(
          throwError(
            () =>
              new ApiError('VALIDACAO', 'A CIM / AM não pertence à Região escolhida.', 't9', [
                { path: 'cimAm', erro: 'A CIM / AM não pertence à Região escolhida.' },
              ]),
          ),
        );
        configurar();
        clicar('Editar rascunho');

        clicar('Guardar');

        const campo = fixture.nativeElement.querySelector('app-seletor.cim-am') as HTMLElement;
        expect(campo.querySelector('.seletor-erro')!.textContent).toContain('A CIM / AM não pertence à Região escolhida.');
        expect(fixture.componentInstance.erro()).toBeNull();
      });

      it('num processo submetido os Detalhes mostram a Região e a CIM / AM em texto', () => {
        processoServiceMock.obter.mockReturnValue(of(sgifr({ ...territorio, estado: 'ENVIADO_DGT', acoesDisponiveis: [] })));
        configurar();

        expect(fixture.nativeElement.querySelector('.regiao-sgifr dd')!.textContent.trim()).toBe('Norte');
        expect(fixture.nativeElement.querySelector('.cim-am-sgifr dd')!.textContent.trim()).toBe('CIM do Alto Minho');
        expect(fixture.nativeElement.querySelector('app-seletor.regiao')).toBeNull();
      });
    });
  });

  describe('formulário REN', () => {
    const MOTIVOS = [
      { id: 11, codigo: 'M1', descricao: 'Adaptação a plano' },
      { id: 12, codigo: 'M2', descricao: 'Correção de limites' },
    ];
    const ren = (parcial: Partial<ProcessoDetalhe> = {}): ProcessoDetalhe =>
      detalhe({
        estado: 'EM_SUBMISSAO',
        formulario: 'REN',
        categoriaInstrumento: 'SRUP',
        tipoInstrumento: 'REN',
        nomeDinamica: 'Correção Material ou Retificação',
        exigeElementosAfetados: false,
        motivoProcedimento: MOTIVOS[1],
        motivosProcedimentoDisponiveis: MOTIVOS,
        pedeDiplomaCorrigido: true,
        diplomaCorrigido: 'Aviso n.º 1/2026',
        tipoInformacaoFixo: null,
        regiaoCodigo: null,
        municipioCodigo: null,
        ...parcial,
      });

    it('Editar rascunho mostra o formulário REN com a Dinâmica, o motivo gravado e o diploma', () => {
      processoServiceMock.obter.mockReturnValue(of(ren()));
      configurar();

      clicar('Editar rascunho');

      const formulario = fixture.componentInstance.formularioRef()!;
      expect(formulario.formulario).toBe('REN');
      expect(formulario.tipoProcedimento).toBe('Correção Material ou Retificação');
      expect(fixture.nativeElement.textContent).toContain('Motivo do procedimento');
      expect(fixture.nativeElement.textContent).toContain('Diploma que corrige ou retifica');
      expect(fixture.nativeElement.querySelector('fieldset.elementos')).toBeNull();
    });

    it('Guardar leva o motivo e o diploma', () => {
      processoServiceMock.obter.mockReturnValue(of(ren()));
      configurar();
      clicar('Editar rascunho');

      clicar('Guardar');

      const pedido = processoServiceMock.guardarRascunho.mock.calls[0][1] as Record<string, unknown>;
      expect(pedido['motivoProcedimentoId']).toBe(12);
      expect(pedido['diplomaCorrigido']).toBe('Aviso n.º 1/2026');
    });

    it('com a lista de motivos vazia o pedido não leva o motivo', () => {
      processoServiceMock.obter.mockReturnValue(of(ren({ motivoProcedimento: null, motivosProcedimentoDisponiveis: [] })));
      configurar();
      clicar('Editar rascunho');

      expect(fixture.nativeElement.textContent).not.toContain('Motivo do procedimento');
      clicar('Guardar');

      expect(processoServiceMock.guardarRascunho.mock.calls[0][1]).not.toHaveProperty('motivoProcedimentoId');
    });

    it('fora da edição mostra o motivo e o diploma gravados nos detalhes', () => {
      processoServiceMock.obter.mockReturnValue(of(ren({ estado: 'ENVIADO_DGT' })));
      configurar();

      const conteudo = fixture.nativeElement.textContent as string;
      expect(conteudo).toContain('Correção de limites');
      expect(conteudo).toContain('Aviso n.º 1/2026');
    });
  });

  it('o formulário de 1.ª série pede suplemento e não norma legal', () => {
    processoServiceMock.obter.mockReturnValue(of(detalhe({ estado: 'EM_SUBMISSAO', serieDr: 'PRIMEIRA' })));
    configurar();

    clicar('Editar rascunho');

    expect(fixture.nativeElement.textContent).not.toContain('Norma legal');
    expect(fixture.nativeElement.textContent).toContain('Publicar suplemento');
  });

  it('Guardar grava os campos INCM num só PUT, com a versao lida', () => {
    processoServiceMock.obter.mockReturnValue(of(detalhe({ estado: 'EM_SUBMISSAO' })));
    configurar();
    clicar('Editar rascunho');
    fixture.componentInstance.formularioRef()!.form.patchValue({ sumario: 'Sumário revisto', normaLegal: '' });

    clicar('Guardar');

    expect(processoServiceMock.guardarRascunho).toHaveBeenCalledTimes(1);
    expect(processoServiceMock.guardarRascunho).toHaveBeenCalledWith('p1', {
      versao: 3,
      sumario: 'Sumário revisto',
      dataAssinatura: '2026-09-10',
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
      tipoAtoId: 4,
      informacaoBase: null,
    });
    expect(fixture.componentInstance.processo()?.sumario).toBe('Sumário revisto');
    expect(fixture.componentInstance.processo()?.versao).toBe(4);
  });

  it('Guardar leva o tipo de ato escolhido no cabeçalho e fica com o nome dele', () => {
    const tipos = [
      { id: 4, codigo: 'AVISO', nome: 'Aviso' },
      { id: 6, codigo: 'EDITAL', nome: 'Edital' },
    ];
    processoServiceMock.obter.mockReturnValue(of(detalhe({ estado: 'EM_SUBMISSAO', tipoAtoId: null, tipoAtoNome: null, tiposAtoDisponiveis: tipos })));
    configurar();
    clicar('Editar rascunho');
    const select = fixture.nativeElement.querySelector('app-seletor.tipo-ato select') as HTMLSelectElement;
    expect([...select.options].map((opcao) => opcao.textContent?.trim())).toEqual(['Selecionar...', 'Aviso', 'Edital']);
    select.value = '6';
    select.dispatchEvent(new Event('change'));

    clicar('Guardar');

    expect(processoServiceMock.guardarRascunho).toHaveBeenCalledWith('p1', expect.objectContaining({ tipoAtoId: 6 }));
    expect(fixture.componentInstance.processo()?.tipoAtoNome).toBe('Edital');
  });

  it('o 403 TIPO_ATO_NAO_AUTORIZADO da gravação fica junto do seletor do tipo de ato e não no banner', () => {
    processoServiceMock.obter.mockReturnValue(of(detalhe({ estado: 'EM_SUBMISSAO' })));
    processoServiceMock.guardarRascunho.mockReturnValue(
      throwError(() => new ApiError('TIPO_ATO_NAO_AUTORIZADO', 'Tipo de ato não autorizado ao utilizador pela INCM.', 't1', null)),
    );
    configurar();
    clicar('Editar rascunho');

    clicar('Guardar');
    fixture.detectChanges();

    expect(fixture.componentInstance.erro()).toBeNull();
    const erro = fixture.nativeElement.querySelector('app-seletor.tipo-ato .seletor-erro') as HTMLElement;
    expect(erro.textContent).toContain('Tipo de ato não autorizado ao utilizador pela INCM.');
  });

  it('não confirma quando a gravação falha', () => {
    processoServiceMock.obter.mockReturnValue(of(detalhe({ estado: 'EM_SUBMISSAO' })));
    processoServiceMock.guardarRascunho.mockReturnValue(
      throwError(() => new ApiError('VALIDACAO', 'Payload inválido.', 't1', null)),
    );
    configurar();
    clicar('Editar rascunho');

    clicar('Guardar');

    expect(fixture.componentInstance.mensagemSucesso()).toBeNull();
    expect(fixture.componentInstance.erro()?.codigo).toBe('VALIDACAO');
  });

  it('um 400 assinala o campo no formulário, junto do respetivo campo', () => {
    processoServiceMock.obter.mockReturnValue(of(detalhe({ estado: 'EM_SUBMISSAO' })));
    processoServiceMock.guardarRascunho.mockReturnValue(
      throwError(() => new ApiError('VALIDACAO', 'Payload inválido.', 't1', [{ path: 'sumario', erro: 'Demasiado longo.' }])),
    );
    configurar();
    clicar('Editar rascunho');

    clicar('Guardar');

    expect(fixture.componentInstance.formularioRef()!.form.controls.sumario.getError('servidor')).toBe('Demasiado longo.');
    expect(fixture.nativeElement.textContent).toContain('Demasiado longo.');
    expect(fixture.componentInstance.erro()).toBeNull();
  });

  it('desativa os campos do cartão enquanto grava e reativa-os, com a mensagem do servidor, se falhar', () => {
    const gravacao = new Subject<ProcessoDetalhe>();
    processoServiceMock.obter.mockReturnValue(of(detalhe({ estado: 'EM_SUBMISSAO' })));
    processoServiceMock.guardarRascunho.mockReturnValue(gravacao);
    configurar();
    clicar('Editar rascunho');
    const camposDoCartao = () =>
      Array.from(
        fixture.nativeElement.querySelectorAll('app-processo-formulario-inicial input, app-processo-formulario-inicial textarea'),
      ) as (HTMLInputElement | HTMLTextAreaElement)[];

    clicar('Guardar');

    expect(camposDoCartao().length).toBeGreaterThan(0);
    expect(camposDoCartao().every((campo) => campo.disabled)).toBe(true);

    gravacao.error(new ApiError('VALIDACAO', 'Payload inválido.', 't1', [{ path: 'sumario', erro: 'Demasiado longo.' }]));
    fixture.detectChanges();

    expect(camposDoCartao().some((campo) => campo.disabled)).toBe(false);
    expect(fixture.nativeElement.querySelector('app-processo-formulario-inicial .erro-campo')?.textContent).toContain('Demasiado longo.');
  });

  it('mostra o erro do servidor na data de assinatura', () => {
    processoServiceMock.obter.mockReturnValue(of(detalhe({ estado: 'EM_SUBMISSAO' })));
    processoServiceMock.guardarRascunho.mockReturnValue(
      throwError(() => new ApiError('VALIDACAO', 'Payload inválido.', 't1', [{ path: 'dataAssinatura', erro: 'Data inválida.' }])),
    );
    configurar();
    clicar('Editar rascunho');

    clicar('Guardar');

    expect(fixture.nativeElement.textContent).toContain('Data inválida.');
  });

  it('Guardar com sucesso mostra a confirmação e desativa o próprio botão', () => {
    processoServiceMock.obter.mockReturnValue(of(detalhe({ estado: 'EM_SUBMISSAO' })));
    configurar();
    clicar('Editar rascunho');

    clicar('Guardar');

    expect(fixture.nativeElement.textContent).toContain('Rascunho guardado com sucesso.');
    const botaoGuardar = Array.from(fixture.nativeElement.querySelectorAll('button')).find(
      (b) => (b as HTMLElement).textContent?.trim() === 'Guardar',
    ) as HTMLButtonElement;
    expect(botaoGuardar.disabled).toBe(true);
  });

  it('ao fim de alguns segundos com a confirmação visível, volta à lista de Processos', () => {
    vi.useFakeTimers();
    try {
      processoServiceMock.obter.mockReturnValue(of(detalhe({ estado: 'EM_SUBMISSAO' })));
      configurar();
      const router = TestBed.inject(Router);
      // `provideRouter([])` não tem nenhuma rota que corresponda a '/processos'.
      const navegar = vi.spyOn(router, 'navigate').mockResolvedValue(true);
      clicar('Editar rascunho');
      clicar('Guardar');
      expect(navegar).not.toHaveBeenCalled();

      vi.advanceTimersByTime(4000);

      expect(navegar).toHaveBeenCalledWith(['/processos']);
    } finally {
      vi.useRealTimers();
    }
  });

  it('Avançar para Submissão grava o rascunho e abre o wizard no passo 3 desta tentativa', () => {
    processoServiceMock.obter.mockReturnValue(of(detalhe({ estado: 'EM_SUBMISSAO', elementosAfetados: ['PECAS_ESCRITAS'] })));
    configurar();
    const navegar = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    clicar('Editar rascunho');

    clicar('Avançar para Submissão');

    expect(processoServiceMock.guardarRascunho).toHaveBeenCalledTimes(1);
    expect(processoServiceMock.guardarRascunho).toHaveBeenCalledWith('p1', expect.objectContaining({ versao: 3 }));
    expect(navegar).toHaveBeenCalledWith(['/processos/criar'], { queryParams: { processoId: 'p1' } });
  });

  it('Avançar para Submissão não grava nem sai com a pergunta do GeoConform visível e sem resposta', () => {
    processoServiceMock.obter.mockReturnValue(
      of(detalhe({ estado: 'EM_SUBMISSAO', perguntaGeoconform: 'SE_PECAS_GRAFICAS', elementosAfetados: ['PECAS_GRAFICAS'] })),
    );
    configurar();
    const navegar = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    clicar('Editar rascunho');

    clicar('Avançar para Submissão');

    expect(processoServiceMock.guardarRascunho).not.toHaveBeenCalled();
    expect(navegar).not.toHaveBeenCalled();
    expect(fixture.nativeElement.querySelector('#erro-comGeoconform').textContent.trim()).toBe('Indique se utiliza o GeoConform.');
  });

  it('Avançar para Submissão não grava nem sai sem nenhum elemento afetado e mostra a mensagem junto dos elementos', () => {
    processoServiceMock.obter.mockReturnValue(of(detalhe({ estado: 'EM_SUBMISSAO', elementosAfetados: [] })));
    configurar();
    const navegar = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    clicar('Editar rascunho');

    clicar('Avançar para Submissão');

    expect(processoServiceMock.guardarRascunho).not.toHaveBeenCalled();
    expect(navegar).not.toHaveBeenCalled();
    expect(fixture.nativeElement.querySelector('#erro-elementosAfetados').textContent.trim()).toBe('Escolha pelo menos um elemento fundamental.');
  });

  it('Avançar para Submissão não sai do detalhe quando a gravação falha', () => {
    processoServiceMock.obter.mockReturnValue(of(detalhe({ estado: 'EM_SUBMISSAO', elementosAfetados: ['PECAS_ESCRITAS'] })));
    processoServiceMock.guardarRascunho.mockReturnValue(
      throwError(() => new ApiError('VALIDACAO', 'Payload inválido.', 't1', null)),
    );
    configurar();
    const navegar = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    clicar('Editar rascunho');

    clicar('Avançar para Submissão');

    expect(navegar).not.toHaveBeenCalled();
    expect(fixture.componentInstance.emEdicao()).toBe(true);
  });

  it('Cancelar fecha o formulário sem gravar', () => {
    processoServiceMock.obter.mockReturnValue(of(detalhe({ estado: 'EM_SUBMISSAO' })));
    configurar();
    clicar('Editar rascunho');

    clicar('Cancelar');

    expect(processoServiceMock.guardarRascunho).not.toHaveBeenCalled();
    expect(fixture.componentInstance.emEdicao()).toBe(false);
  });

  it('mostra Resgatar submissão quando ENVIADO_DGT e ainda não capturado pela DGT', () => {
    configurar(); // estado por omissão: ENVIADO_DGT

    expect(fixture.nativeElement.textContent).toContain('Resgatar submissão');
  });

  /** Um colega da mesma entidade vê o Processo mas não o criou. */
  it('não mostra Resgatar submissão a quem não é o autor da tentativa', () => {
    processoServiceMock.obter.mockReturnValue(of(detalhe({ criadoPorId: 'outro-utilizador', ...SEM_PERMISSOES })));

    configurar();

    expect(fixture.nativeElement.textContent).not.toContain('Resgatar submissão');
  });

  it('não mostra nenhuma das duas ações depois de a DGT capturar a submissão', () => {
    processoServiceMock.obter.mockReturnValue(of(detalhe({ estado: 'EM_VALIDACAO_DGT' })));

    configurar();

    const texto = fixture.nativeElement.textContent;
    expect(texto).not.toContain('Resgatar submissão');
    expect(texto).not.toContain('Editar rascunho');
  });

  it('Resgatar submissão pede confirmação antes de chamar a API', () => {
    configurar();

    clicar('Resgatar submissão');

    expect(fixture.componentInstance.confirmacaoResgatarAberta()).toBe(true);
    expect(processoServiceMock.resgatar).not.toHaveBeenCalled();
  });

  it('"Não" no modal fecha sem resgatar', () => {
    configurar();
    clicar('Resgatar submissão');

    clicar('Não');

    expect(processoServiceMock.resgatar).not.toHaveBeenCalled();
    expect(fixture.componentInstance.confirmacaoResgatarAberta()).toBe(false);
  });

  it('confirmar o resgate chama resgatar() e deixa o utilizador no formulário editável da mesma tentativa', () => {
    configurar();
    clicar('Resgatar submissão');

    clicar('Sim');

    expect(processoServiceMock.resgatar).toHaveBeenCalledWith('p1', 3);
    expect(fixture.componentInstance.emEdicao()).toBe(true);
    expect(fixture.nativeElement.textContent).toContain('Guardar');
  });

  describe('VERSAO_DESATUALIZADA', () => {
    const aviso = 'O processo foi alterado; recarregue.';

    function desatualizado(): ApiError {
      return new ApiError('VERSAO_DESATUALIZADA', aviso, 't1', null);
    }

    it('um resgate com a versao antiga mostra o aviso e relê o detalhe, sem repetir o pedido', () => {
      processoServiceMock.obter
        .mockReturnValueOnce(of(detalhe()))
        .mockReturnValueOnce(of(detalhe({ estado: 'EM_VALIDACAO_DGT', versao: 4 })));
      processoServiceMock.resgatar.mockReturnValue(throwError(() => desatualizado()));
      configurar();
      clicar('Resgatar submissão');

      clicar('Sim');

      expect(fixture.nativeElement.textContent).toContain(aviso);
      expect(processoServiceMock.obter).toHaveBeenCalledTimes(2);
      expect(processoServiceMock.resgatar).toHaveBeenCalledOnce();
      expect(fixture.componentInstance.processo()!.versao).toBe(4);
      expect(fixture.componentInstance.processo()!.estado).toBe('EM_VALIDACAO_DGT');
      expect(fixture.componentInstance.confirmacaoResgatarAberta()).toBe(false);
    });

    it('um Guardar com a versao antiga mostra o aviso, fecha a edição e relê o detalhe', () => {
      processoServiceMock.obter
        .mockReturnValueOnce(of(detalhe({ estado: 'EM_SUBMISSAO' })))
        .mockReturnValueOnce(of(detalhe({ estado: 'EM_SUBMISSAO', versao: 4 })));
      processoServiceMock.guardarRascunho.mockReturnValue(throwError(() => desatualizado()));
      configurar();
      clicar('Editar rascunho');

      clicar('Guardar');

      expect(fixture.nativeElement.textContent).toContain(aviso);
      expect(processoServiceMock.obter).toHaveBeenCalledTimes(2);
      expect(processoServiceMock.guardarRascunho).toHaveBeenCalledOnce();
      expect(fixture.componentInstance.emEdicao()).toBe(false);
      expect(fixture.componentInstance.processo()!.versao).toBe(4);
    });

    it('o único PUT parte da versao lida, e é a que ele devolve que fica no detalhe', () => {
      processoServiceMock.obter.mockReturnValue(of(detalhe({ estado: 'EM_SUBMISSAO' })));
      configurar();
      clicar('Editar rascunho');

      clicar('Guardar');

      expect(processoServiceMock.guardarRascunho).toHaveBeenCalledOnce();
      expect(processoServiceMock.guardarRascunho).toHaveBeenCalledWith('p1', expect.objectContaining({ versao: 3 }));
      expect(fixture.componentInstance.processo()!.versao).toBe(4);
    });
  });

  describe('CAMPO_BLOQUEADO', () => {
    const mensagem = 'Desbloqueie o campo antes de o alterar.';

    it('um Guardar recusado mostra a mensagem, fecha a edição e relê o detalhe com os campos bloqueados', () => {
      const erro = new ApiError('CAMPO_BLOQUEADO', mensagem, 't1', [{ path: 'sumario', erro: 'Campo bloqueado depois da primeira submissão.' }]);
      processoServiceMock.obter
        .mockReturnValueOnce(of(detalhe({ estado: 'EM_SUBMISSAO' })))
        .mockReturnValueOnce(of(detalhe({ estado: 'EM_SUBMISSAO', versao: 5, camposBloqueados: ['sumario'] })));
      processoServiceMock.guardarRascunho.mockReturnValue(throwError(() => erro));
      configurar();
      clicar('Editar rascunho');

      clicar('Guardar');

      expect(fixture.nativeElement.textContent).toContain(mensagem);
      expect(processoServiceMock.obter).toHaveBeenCalledTimes(2);
      expect(processoServiceMock.guardarRascunho).toHaveBeenCalledOnce();
      expect(fixture.componentInstance.emEdicao()).toBe(false);
      expect(fixture.componentInstance.processo()!.versao).toBe(5);
      expect(fixture.componentInstance.processo()!.camposBloqueados).toEqual(['sumario']);
      expect(fixture.nativeElement.querySelector('.erro-campo')).toBeNull();
    });
  });

  describe('Alterações (issue #150)', () => {
    function botaoAlteracoes(): HTMLElement | undefined {
      return Array.from(fixture.nativeElement.querySelectorAll('button')).find(
        (b) => (b as HTMLElement).textContent?.trim() === 'Alterações',
      ) as HTMLElement | undefined;
    }

    it.each(['MASTER', 'TECNICO'])('mostra o botão Alterações ao ator DGT (%s)', (perfil) => {
      atorMock = 'DGT';
      perfilInternoMock = perfil;

      configurar();

      expect(botaoAlteracoes()).toBeDefined();
    });

    it.each(['ENTIDADE', 'PCM', null])('não mostra o botão Alterações ao ator %s', (ator) => {
      atorMock = ator;

      configurar();

      expect(botaoAlteracoes()).toBeUndefined();
    });

    it('Alterações abre o modal com os dados do Processo', () => {
      atorMock = 'DGT';
      perfilInternoMock = 'MASTER';
      processoServiceMock.obter.mockReturnValue(
        of(detalhe({ estado: 'EM_VALIDACAO_DGT', acoesDisponiveis: ['validar', 'rejeitar', 'anular', 'reatribuir-autor'] })),
      );
      configurar();

      clicar('Alterações');

      const modal = fixture.nativeElement.querySelector('app-alteracoes-modal') as HTMLElement;
      expect(modal.textContent).toContain('Owner atual: Helena Marques');
      expect(modal.textContent).toContain('Estado atual: Em Validação DGT');
      expect(modal.querySelector('[data-acao="ANULAR"]')).not.toBeNull();
    });

    it('ao TECNICO o modal mostra Alterar estado, sem Alterar Owner nem Anular', () => {
      atorMock = 'DGT';
      perfilInternoMock = 'TECNICO';
      processoServiceMock.obter.mockReturnValue(
        of(detalhe({ estado: 'EM_VALIDACAO_DGT', editavel: false, acoesDisponiveis: ['validar', 'rejeitar'] })),
      );
      configurar();

      clicar('Alterações');

      const modal = fixture.nativeElement.querySelector('app-alteracoes-modal') as HTMLElement;
      expect(modal.querySelector('[data-acao="ESTADO"]')).not.toBeNull();
      expect(modal.querySelector('[data-acao="OWNER"]')).toBeNull();
      expect(modal.querySelector('[data-acao="ANULAR"]')).toBeNull();
    });

    it('Cancelar fecha o modal sem chamar a API', () => {
      atorMock = 'DGT';
      configurar();
      clicar('Alterações');

      clicar('Cancelar');

      expect(fixture.componentInstance.alteracoesAbertas()).toBe(false);
      expect(fixture.nativeElement.querySelector('app-alteracoes-modal [role="dialog"]')).toBeNull();
      expect(processoServiceMock.guardarRascunho).not.toHaveBeenCalled();
      expect(processoServiceMock.resgatar).not.toHaveBeenCalled();
    });
  });

  describe('Alterar estado (issue #151)', () => {
    /** MASTER que é também o autor: único caso em que Resgatar e Alterações coexistem. */
    function capturarPeloModal(): void {
      clicar('Alterações');
      (fixture.nativeElement.querySelector('[data-acao="ESTADO"]') as HTMLElement).click();
      fixture.detectChanges();
      clicar('Confirmar');
      const select = fixture.nativeElement.querySelector('app-seletor[data-campo="novo-estado"] select') as HTMLSelectElement;
      select.value = 'EM_VALIDACAO_DGT';
      select.dispatchEvent(new Event('change'));
      fixture.detectChanges();
      clicar('Confirmar');
    }

    /** Autor e `MASTER` ao mesmo tempo: o backend lista Resgatar e Capturar juntos. */
    const ENVIADO_PARA_CAPTURAR = detalhe({ acoesDisponiveis: ['resgatar', 'capturar', 'reatribuir-autor'] });
    const CAPTURADO = (): ProcessoDetalhe =>
      detalhe({ estado: 'EM_VALIDACAO_DGT', versao: 4, editavel: false, acoesDisponiveis: ['validar', 'rejeitar', 'anular'] });

    beforeEach(() => {
      atorMock = 'DGT';
    });

    it('uma captura com a versao antiga fecha o modal, mostra o aviso e relê o detalhe', () => {
      processoServiceMock.obter
        .mockReturnValueOnce(of(ENVIADO_PARA_CAPTURAR))
        .mockReturnValueOnce(of(detalhe({ estado: 'EM_SUBMISSAO', versao: 4 })));
      processoServiceMock.capturar.mockReturnValue(
        throwError(() => new ApiError('VERSAO_DESATUALIZADA', 'O processo foi alterado; recarregue.', 't1', null)),
      );
      configurar();

      capturarPeloModal();

      expect(processoServiceMock.capturar).toHaveBeenCalledOnce();
      expect(fixture.componentInstance.alteracoesAbertas()).toBe(false);
      expect(fixture.nativeElement.textContent).toContain('O processo foi alterado; recarregue.');
      expect(processoServiceMock.obter).toHaveBeenCalledTimes(2);
      expect(fixture.componentInstance.processo()!.estado).toBe('EM_SUBMISSAO');
      expect(fixture.componentInstance.processo()!.versao).toBe(4);
    });

    it('com sucesso, fecha o modal e relê o detalhe, que traz as ações do novo estado', () => {
      processoServiceMock.obter.mockReturnValueOnce(of(ENVIADO_PARA_CAPTURAR)).mockReturnValueOnce(of(CAPTURADO()));
      configurar();

      capturarPeloModal();

      expect(processoServiceMock.capturar).toHaveBeenCalledWith('p1', 3);
      expect(processoServiceMock.obter).toHaveBeenCalledTimes(2);
      expect(fixture.componentInstance.alteracoesAbertas()).toBe(false);
      const processo = fixture.componentInstance.processo()!;
      expect(processo.estado).toBe('EM_VALIDACAO_DGT');
      expect(processo.versao).toBe(4);
      expect(processo.acoesDisponiveis).toEqual(['validar', 'rejeitar', 'anular']);
      expect(processo.designacaoInstrumento).toBe('PDM de Sintra');
      expect(fixture.nativeElement.textContent).toContain('Em Validação DGT');
    });

    it('depois de capturar, Resgatar submissão deixa de aparecer', () => {
      processoServiceMock.obter.mockReturnValueOnce(of(ENVIADO_PARA_CAPTURAR)).mockReturnValueOnce(of(CAPTURADO()));
      configurar();
      expect(fixture.nativeElement.textContent).toContain('Resgatar submissão');

      capturarPeloModal();

      expect(fixture.nativeElement.textContent).not.toContain('Resgatar submissão');
    });

    it('o TECNICO que captura vê Validar documentos no detalhe recarregado, sem sair da página', () => {
      perfilInternoMock = 'TECNICO';
      processoServiceMock.obter.mockReturnValueOnce(of(ENVIADO_PARA_CAPTURAR)).mockReturnValueOnce(of(CAPTURADO()));
      configurar();
      expect(fixture.nativeElement.textContent).not.toContain('Validar documentos');

      capturarPeloModal();

      const ligacao = fixture.nativeElement.querySelector('a.validar-documentos') as HTMLAnchorElement;
      expect(ligacao.getAttribute('href')).toBe('/processos/p1/validacao');
    });

    it('Recarregar volta a pedir o detalhe e fecha o modal', () => {
      configurar();
      fixture.componentInstance.alteracoesAbertas.set(true);
      processoServiceMock.obter.mockReturnValue(of(detalhe({ estado: 'EM_SUBMISSAO' })));

      fixture.componentInstance.recarregar();
      fixture.detectChanges();

      expect(processoServiceMock.obter).toHaveBeenCalledTimes(2);
      expect(fixture.componentInstance.processo()!.estado).toBe('EM_SUBMISSAO');
      expect(fixture.componentInstance.alteracoesAbertas()).toBe(false);
    });
  });

  describe('selo «Com a PCM»', () => {
    beforeEach(() => {
      atorMock = 'DGT';
      perfilInternoMock = 'MASTER';
    });

    function selo(): HTMLElement | null {
      return fixture.nativeElement.querySelector('app-selo[data-campo="com-a-pcm"]');
    }

    it('num VALIDADO_DGT atribuído à PCM mostra o selo, em texto, junto do estado', () => {
      processoServiceMock.obter.mockReturnValue(
        of(detalhe({ estado: 'VALIDADO_DGT', pcmAtribuidoAId: 'u-pcm', ...SEM_PERMISSOES })),
      );
      configurar();

      expect(selo()?.textContent?.trim()).toBe('Com a PCM');
      expect(selo()?.closest('.titulo-linha')?.querySelector('app-estado-processo-badge')).not.toBeNull();
    });

    it('num VALIDADO_DGT sem pcmAtribuidoAId não há selo', () => {
      processoServiceMock.obter.mockReturnValue(of(detalhe({ estado: 'VALIDADO_DGT', pcmAtribuidoAId: null, ...SEM_PERMISSOES })));
      configurar();

      expect(selo()).toBeNull();
    });

    it('fora de VALIDADO_DGT não há selo, mesmo com a atribuição gravada', () => {
      processoServiceMock.obter.mockReturnValue(of(detalhe({ estado: 'EM_VALIDACAO_INCM', pcmAtribuidoAId: 'u-pcm', ...SEM_PERMISSOES })));
      configurar();

      expect(selo()).toBeNull();
    });
  });

  describe('devolvido pela PCM', () => {
    const DEVOLVIDO: Partial<ProcessoDetalhe> = {
      estado: 'VALIDADO_DGT',
      pcmAtribuidoAId: null,
      pcmDevolvidoEm: '2026-11-14T10:30:00Z',
      ...SEM_PERMISSOES,
    };

    beforeEach(() => {
      atorMock = 'DGT';
      perfilInternoMock = 'TECNICO';
    });

    function alerta(): HTMLElement | null {
      return fixture.nativeElement.querySelector('app-alerta[data-campo="alerta-devolvido-pcm"]');
    }

    function anuncio(): string {
      return (fixture.nativeElement.querySelector('p.so-leitor[aria-live="polite"]') as HTMLElement).textContent?.trim() ?? '';
    }

    it('com o documento da PCM por validar mostra a data, o pedido de validação e a ligação à vista de validação, anunciados', () => {
      processoServiceMock.obter.mockReturnValue(of(detalhe({ ...DEVOLVIDO, revalidacaoPcm: true })));
      configurar();

      expect(alerta()?.getAttribute('role')).toBe('status');
      expect(alerta()?.textContent).toMatch(/Devolvido pela PCM em \d{2}\/\d{2}\/\d{4} \d{2}:\d{2}\./);
      expect(alerta()?.textContent).toContain('Valide os documentos acrescentados pela PCM antes de enviar à INCM.');
      const ligacao = alerta()?.querySelector('a.ligacao-revalidacao') as HTMLAnchorElement;
      expect(ligacao.getAttribute('href')).toBe('/processos/p1/validacao');
      expect(anuncio()).toMatch(/^Devolvido pela PCM em .+ Valide os documentos acrescentados pela PCM antes de enviar à INCM\.$/);
      expect(fixture.nativeElement.querySelector('app-selo[data-campo="com-a-pcm"]')).toBeNull();
    });

    it('com os documentos já validados só diz a data da devolução', () => {
      processoServiceMock.obter.mockReturnValue(of(detalhe({ ...DEVOLVIDO, revalidacaoPcm: false })));
      configurar();

      expect(alerta()?.textContent).toContain('Devolvido pela PCM em');
      expect(alerta()?.textContent).not.toContain('Valide os documentos');
      expect(alerta()?.querySelector('a')).toBeNull();
    });

    it('não aparece a um utilizador de entidade, fora de VALIDADO_DGT nem sem a data da devolução', () => {
      const casos: { perfil: string | null; ator: string; parcial: Partial<ProcessoDetalhe> }[] = [
        { perfil: null, ator: 'ENTIDADE', parcial: DEVOLVIDO },
        { perfil: 'TECNICO', ator: 'DGT', parcial: { ...DEVOLVIDO, estado: 'EM_VALIDACAO_INCM' } },
        { perfil: 'TECNICO', ator: 'DGT', parcial: { ...DEVOLVIDO, pcmDevolvidoEm: null } },
      ];
      for (const caso of casos) {
        TestBed.resetTestingModule();
        perfilInternoMock = caso.perfil;
        atorMock = caso.ator;
        processoServiceMock.obter.mockReturnValue(of(detalhe(caso.parcial)));
        configurar();

        expect(alerta()).toBeNull();
        expect(anuncio()).toBe('');
      }
    });
  });

  describe('Corrigir Processo pela PCM', () => {
    const PCM_ATRIBUIDA: Partial<ProcessoDetalhe> = {
      estado: 'VALIDADO_DGT',
      pcmAtribuidoAId: 'u-pcm',
      editavel: true,
      acoesDisponiveis: ['devolver-dgt'],
      podeSubmeter: false,
      camposBloqueados: ['sumario'],
    };

    beforeEach(() => {
      atorMock = 'PCM';
      utilizadorIdMock = 'u-pcm';
      processoServiceMock.obter.mockReturnValue(of(detalhe(PCM_ATRIBUIDA)));
      processoServiceMock.guardarRascunho.mockReturnValue(of(detalhe({ ...PCM_ATRIBUIDA, versao: 4 })));
    });

    function alerta(): HTMLElement | null {
      return fixture.nativeElement.querySelector('app-alerta[data-campo="alerta-pcm"]');
    }

    function documentos(): ProcessoDocumentosComponent | undefined {
      return fixture.debugElement.query(By.directive(ProcessoDocumentosComponent))?.componentInstance;
    }

    function temBotaoComTexto(texto: string): boolean {
      return Array.from(fixture.nativeElement.querySelectorAll('button')).some(
        (b) => (b as HTMLElement).textContent?.trim() === texto,
      );
    }

    function anuncio(): string {
      return (fixture.nativeElement.querySelector('p.so-leitor[aria-live="polite"]') as HTMLElement).textContent?.trim() ?? '';
    }

    it('com o processo atribuído abre logo a edição, com o alerta, Guardar e a zona de carregamento em modo acrescentar', () => {
      configurar();

      expect(fixture.componentInstance.pcmPodeAcrescentar()).toBe(true);
      expect(alerta()?.getAttribute('role')).toBe('status');
      expect(alerta()?.textContent).toContain('anexe o texto final da RCM');
      expect(anuncio()).toContain('anexe o texto final da RCM');
      expect(fixture.componentInstance.emEdicao()).toBe(true);
      expect(fixture.nativeElement.querySelector('app-processo-formulario-inicial')).not.toBeNull();
      expect(documentos()?.editavel).toBe(true);
      expect(documentos()?.modoAcrescentar).toBe(true);
      expect(temBotaoComTexto('Guardar')).toBe(true);
      expect(temBotaoComTexto('Submeter à DGT')).toBe(true);
      expect(temBotaoComTexto('Avançar para Submissão')).toBe(false);
    });

    it('sem a atribuição ao utilizador, noutro estado ou noutro ator não há alerta nem modo acrescentar', () => {
      const casos: { ator: string; utilizador: string; parcial: Partial<ProcessoDetalhe> }[] = [
        { ator: 'PCM', utilizador: 'u-outro', parcial: PCM_ATRIBUIDA },
        { ator: 'PCM', utilizador: 'u-pcm', parcial: { ...PCM_ATRIBUIDA, estado: 'EM_VALIDACAO_INCM', ...SEM_PERMISSOES } },
        { ator: 'DGT', utilizador: 'u-pcm', parcial: { ...PCM_ATRIBUIDA, ...SEM_PERMISSOES } },
      ];
      for (const caso of casos) {
        TestBed.resetTestingModule();
        atorMock = caso.ator;
        utilizadorIdMock = caso.utilizador;
        processoServiceMock.obter.mockReturnValue(of(detalhe(caso.parcial)));
        configurar();

        expect(fixture.componentInstance.pcmPodeAcrescentar()).toBe(false);
        expect(alerta()).toBeNull();
        expect(anuncio()).toBe('');
      }
    });

    it('no ProcessoDocumentos do autor o modo acrescentar fica desligado', () => {
      atorMock = 'ENTIDADE';
      utilizadorIdMock = 'u1';
      processoServiceMock.obter.mockReturnValue(of(detalhe({ estado: 'EM_SUBMISSAO' })));
      configurar();
      clicar('Editar rascunho');

      expect(documentos()?.modoAcrescentar).toBe(false);
      expect(temBotaoComTexto('Submeter à DGT')).toBe(false);
    });

    it('Guardar grava o formulário e fica no processo, sem voltar à lista', () => {
      vi.useFakeTimers();
      try {
        configurar();
        const navegar = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);

        clicar('Guardar');
        vi.advanceTimersByTime(5000);

        expect(processoServiceMock.guardarRascunho).toHaveBeenCalledWith('p1', expect.objectContaining({ versao: 3 }));
        expect(navegar).not.toHaveBeenCalled();
        expect(fixture.componentInstance.emEdicao()).toBe(true);
      } finally {
        vi.useRealTimers();
      }
    });

    it('Submeter à DGT confirma, grava, devolve com a versão nova e volta à lista Para Correção', () => {
      vi.useFakeTimers();
      try {
        configurar();
        const navegar = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);

        clicar('Submeter à DGT');
        expect(fixture.nativeElement.querySelector('[role="dialog"]')?.textContent).toContain('deixa de estar disponível');
        clicar('Submeter');

        expect(processoServiceMock.guardarRascunho).toHaveBeenCalledTimes(1);
        expect(processoServiceMock.devolverDgt).toHaveBeenCalledWith('p1', 4);
        expect(fixture.nativeElement.querySelector('.banner-sucesso-acao').textContent).toContain('Processo submetido à DGT.');
        expect(alerta()).toBeNull();
        expect(fixture.componentInstance.emEdicao()).toBe(false);
        expect(temBotaoComTexto('Submeter à DGT')).toBe(false);

        vi.advanceTimersByTime(4000);
        expect(navegar).toHaveBeenCalledWith(['/processos/corrigir']);
      } finally {
        vi.useRealTimers();
      }
    });

    it('fora da edição Submeter à DGT devolve sem gravar o formulário', () => {
      configurar();
      clicar('Cancelar');

      clicar('Submeter à DGT');
      clicar('Submeter');

      expect(processoServiceMock.guardarRascunho).not.toHaveBeenCalled();
      expect(processoServiceMock.devolverDgt).toHaveBeenCalledWith('p1', 3);
    });

    it('sem a ação devolver-dgt nas ações não há Submeter à DGT', () => {
      processoServiceMock.obter.mockReturnValue(of(detalhe({ ...PCM_ATRIBUIDA, acoesDisponiveis: [] })));
      configurar();

      expect(temBotaoComTexto('Submeter à DGT')).toBe(false);
    });

    it('um 409 da devolução mostra a mensagem do backend e não sai do processo', () => {
      processoServiceMock.devolverDgt.mockReturnValue(
        throwError(
          () => new ApiError('PCM_DOCUMENTOS_OBRIGATORIOS_EM_FALTA', 'Falta anexar documentos obrigatórios.', 't1', null),
        ),
      );
      configurar();
      const navegar = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
      clicar('Cancelar');

      clicar('Submeter à DGT');
      clicar('Submeter');

      expect(fixture.nativeElement.querySelector('app-error-banner').textContent).toContain(
        'Falta anexar documentos obrigatórios.',
      );
      expect(navegar).not.toHaveBeenCalled();
      expect(fixture.componentInstance.pcmPodeAcrescentar()).toBe(true);
    });
  });

  describe('Enviar à INCM (issue #102)', () => {
    const VALIDADO = (): ProcessoDetalhe =>
      detalhe({ estado: 'VALIDADO_DGT', editavel: false, acoesDisponiveis: ['enviar-incm', 'enviar-pcm'] });

    function enviarPeloModal(): void {
      clicar('Alterações');
      (fixture.nativeElement.querySelector('[data-acao="ESTADO"]') as HTMLElement).click();
      fixture.detectChanges();
      clicar('Confirmar');
      const select = fixture.nativeElement.querySelector('app-seletor[data-campo="novo-estado"] select') as HTMLSelectElement;
      select.value = 'EM_VALIDACAO_INCM';
      select.dispatchEvent(new Event('change'));
      fixture.detectChanges();
      clicar('Confirmar');
    }

    function botaoConfirmar(): HTMLButtonElement {
      return Array.from(fixture.nativeElement.querySelectorAll('app-alteracoes-modal button')).find(
        (b) => (b as HTMLElement).textContent?.trim() === 'Confirmar',
      ) as HTMLButtonElement;
    }

    beforeEach(() => {
      atorMock = 'DGT';
      perfilInternoMock = 'TECNICO';
    });

    it.each([
      ['EM_VALIDACAO_INCM', 'Em validação INCM'],
      ['ANULADO_INCM', 'Anulado INCM'],
    ])('com sucesso mostra o estado devolvido (%s) e a mensagem de sucesso com esse estado', (estado, rotulo) => {
      processoServiceMock.obter
        .mockReturnValueOnce(of(VALIDADO()))
        .mockReturnValueOnce(of(detalhe({ estado, versao: 4, editavel: false, acoesDisponiveis: [] })));
      processoServiceMock.enviarIncm.mockReturnValue(of({ id: 'p1', estado, versao: 4 }));
      configurar();

      enviarPeloModal();

      expect(processoServiceMock.enviarIncm).toHaveBeenCalledWith('p1', 3);
      expect(fixture.componentInstance.alteracoesAbertas()).toBe(false);
      expect(fixture.componentInstance.processo()!.estado).toBe(estado);
      expect(fixture.nativeElement.querySelector('.banner-sucesso-acao')?.textContent).toContain(`«${rotulo}»`);
    });

    it('com a INCM indisponível mostra a mensagem, mantém o processo em Validado DGT e deixa repetir o envio', () => {
      const mensagem = 'A INCM não respondeu ao envio do ato. Tente novamente mais tarde.';
      processoServiceMock.obter.mockReturnValue(of(VALIDADO()));
      processoServiceMock.enviarIncm.mockReturnValue(
        throwError(() => new ApiError('INTEGRACAO_INDISPONIVEL', mensagem, 't1', null)),
      );
      configurar();

      enviarPeloModal();

      expect(fixture.nativeElement.querySelector('app-alteracoes-modal .error-banner')?.textContent).toContain(mensagem);
      expect(fixture.componentInstance.alteracoesAbertas()).toBe(true);
      const processo = fixture.componentInstance.processo()!;
      expect(processo.estado).toBe('VALIDADO_DGT');
      expect(processo.acoesDisponiveis).toContain('enviar-incm');
      expect(processoServiceMock.obter).toHaveBeenCalledOnce();
      expect(fixture.nativeElement.querySelector('.banner-sucesso-acao')).toBeNull();
      expect(botaoConfirmar().disabled).toBe(false);
    });
  });

  describe('Corrigir após rejeição da DGT ou anulação pela INCM', () => {
    const REJEICAO = {
      motivoCodigo: 'DOC_INCOMPLETA',
      motivoDescricao: 'Documentação incompleta',
      data: '2026-09-20T10:30:00Z',
      notasGerais: null,
    };
    const RECUSA_INCM = { mensagens: ['ImageURLList cannot be empty.', 'Outra mensagem.'], data: '2026-09-22T08:15:00Z' };

    function rejeitado(parcial: Partial<ProcessoDetalhe> = {}): ProcessoDetalhe {
      return detalhe({ estado: 'REJEITADO_DGT', ultimaRejeicaoDgt: REJEICAO, ...parcial });
    }

    function anulado(parcial: Partial<ProcessoDetalhe> = {}): ProcessoDetalhe {
      return detalhe({ estado: 'ANULADO_INCM', ultimaRejeicaoIncm: RECUSA_INCM, ...parcial });
    }

    function emCorrecao(parcial: Partial<ProcessoDetalhe> = {}): ProcessoDetalhe {
      return detalhe({ estado: 'EM_SUBMISSAO', versao: 4, ...parcial });
    }

    function temBotao(texto: string): boolean {
      return Array.from(fixture.nativeElement.querySelectorAll('button')).some(
        (b) => (b as HTMLElement).textContent?.trim() === texto,
      );
    }

    function avisos(): HTMLElement[] {
      return Array.from(fixture.nativeElement.querySelectorAll('.aviso-rejeicao'));
    }

    beforeEach(() => {
      processoServiceMock.obter.mockReturnValue(of(rejeitado()));
    });

    describe('aviso da recusa', () => {
      it('o autor vê o aviso "Rejeitado pela DGT" com o motivo e a data', () => {
        configurar();

        const aviso = avisos()[0];
        expect(aviso.textContent).toContain('Rejeitado pela DGT');
        expect(aviso.textContent).toContain('Documentação incompleta');
        expect(aviso.textContent).toMatch(/20\/09\/2026 \d{2}:30/);
      });

      it('numa recusa com itens, mostra a data e as notas gerais, sem «Motivo»', () => {
        processoServiceMock.obter.mockReturnValue(
          of(
            rejeitado({
              ultimaRejeicaoDgt: {
                motivoCodigo: null,
                motivoDescricao: null,
                data: '2026-11-09T10:30:00Z',
                notasGerais: 'Rever o regulamento.',
              },
            }),
          ),
        );

        configurar();

        const aviso = avisos()[0];
        expect(aviso.textContent).toContain('Rejeitado pela DGT');
        expect(aviso.textContent).toMatch(/09\/11\/2026 \d{2}:30/);
        expect(aviso.textContent).toContain('Notas gerais: Rever o regulamento.');
        expect(aviso.textContent).not.toContain('Motivo');
      });

      it('numa recusa sem notas gerais, mostra só a data', () => {
        processoServiceMock.obter.mockReturnValue(
          of(rejeitado({ ultimaRejeicaoDgt: { motivoCodigo: null, motivoDescricao: null, data: '2026-11-09T10:30:00Z', notasGerais: null } })),
        );

        configurar();

        expect(avisos()[0].textContent).not.toContain('Notas gerais');
        expect(avisos()[0].textContent).not.toContain('Motivo');
      });

      it('sem descrição do motivo, mostra o código', () => {
        processoServiceMock.obter.mockReturnValue(of(rejeitado({ ultimaRejeicaoDgt: { ...REJEICAO, motivoDescricao: null } })));

        configurar();

        expect(avisos()[0].textContent).toContain('DOC_INCOMPLETA');
      });

      it('não mostra o aviso fora de REJEITADO_DGT e ANULADO_INCM, mesmo com recusas antigas', () => {
        processoServiceMock.obter.mockReturnValue(
          of(detalhe({ estado: 'ENVIADO_DGT', ultimaRejeicaoDgt: REJEICAO, ultimaRejeicaoIncm: RECUSA_INCM })),
        );

        configurar();

        expect(avisos()).toHaveLength(0);
      });

      it('em ANULADO_INCM mostra "Anulado pela INCM", a data e cada mensagem numa lista', () => {
        processoServiceMock.obter.mockReturnValue(of(anulado()));

        configurar();

        expect(avisos()).toHaveLength(1);
        const aviso = avisos()[0];
        expect(aviso.getAttribute('role')).toBe('status');
        expect(aviso.textContent).toContain('Anulado pela INCM');
        expect(aviso.textContent).toMatch(/22\/09\/2026 \d{2}:15/);
        const mensagens = Array.from(aviso.querySelectorAll('ul > li')).map((li) => li.textContent?.trim());
        expect(mensagens).toEqual(['ImageURLList cannot be empty.', 'Outra mensagem.']);
      });

      it.each([
        ['sem recusa registada', null],
        ['com a lista de mensagens vazia', { mensagens: [], data: '2026-09-22T08:15:00Z' }],
      ])('em ANULADO_INCM %s mostra "Motivo não disponível."', (_descricao, recusa) => {
        processoServiceMock.obter.mockReturnValue(of(anulado({ ultimaRejeicaoIncm: recusa })));

        configurar();

        expect(avisos()[0].textContent).toContain('Anulado pela INCM');
        expect(avisos()[0].textContent).toContain('Motivo não disponível.');
        expect(avisos()[0].querySelector('ul')).toBeNull();
      });

      it('em correção mantém o aviso da recusa da DGT posterior à última submissão', () => {
        processoServiceMock.obter.mockReturnValue(of(emCorrecao({ ultimaRejeicaoDgt: REJEICAO })));

        configurar();

        expect(avisos()).toHaveLength(1);
        expect(avisos()[0].textContent).toContain('Rejeitado pela DGT');
      });

      it('em correção mostra o aviso da recusa mais recente, a da INCM quando é posterior à da DGT', () => {
        processoServiceMock.obter.mockReturnValue(
          of(emCorrecao({ ultimaRejeicaoDgt: REJEICAO, ultimaRejeicaoIncm: RECUSA_INCM })),
        );

        configurar();

        expect(avisos()).toHaveLength(1);
        expect(avisos()[0].textContent).toContain('Anulado pela INCM');
        expect(avisos()[0].textContent).toContain('ImageURLList cannot be empty.');
      });

      it('um processo resgatado, sem recusa posterior à última submissão, não mostra aviso', () => {
        processoServiceMock.obter.mockReturnValue(
          of(
            emCorrecao({
              dataSubmissao: '2026-09-25T09:00:00Z',
              ultimaRejeicaoDgt: REJEICAO,
              ultimaRejeicaoIncm: RECUSA_INCM,
            }),
          ),
        );

        configurar();

        expect(avisos()).toHaveLength(0);
      });

      it('um rascunho nunca submetido não mostra aviso', () => {
        processoServiceMock.obter.mockReturnValue(of(emCorrecao({ dataSubmissao: null })));

        configurar();

        expect(avisos()).toHaveLength(0);
      });
    });

    describe('botão Corrigir Processo', () => {
      it.each([
        ['REJEITADO_DGT', () => rejeitado()],
        ['ANULADO_INCM', () => anulado()],
      ])('o autor vê Corrigir Processo em %s, sem Editar rascunho nem Voltar a submeter', (_estado, processo) => {
        processoServiceMock.obter.mockReturnValue(of(processo()));

        configurar();

        expect(temBotao('Corrigir Processo')).toBe(true);
        expect(temBotao('Editar rascunho')).toBe(false);
        expect(temBotao('Voltar a submeter')).toBe(false);
      });

      it('o botão fica por baixo dos cartões, alinhado à direita', () => {
        configurar();

        const botao = fixture.nativeElement.querySelector('.acao-corrigir button') as HTMLElement;
        expect(botao.textContent?.trim()).toBe('Corrigir Processo');
        const cartao = fixture.nativeElement.querySelector('app-cartao') as HTMLElement;
        expect(cartao.compareDocumentPosition(botao) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
      });

      it.each([
        ['REJEITADO_DGT', () => rejeitado(SEM_PERMISSOES)],
        ['ANULADO_INCM', () => anulado(SEM_PERMISSOES)],
      ])('um colega da mesma entidade vê o aviso em %s, mas não Corrigir Processo', (_estado, processo) => {
        utilizadorIdMock = 'u2';
        processoServiceMock.obter.mockReturnValue(of(processo()));

        configurar();

        expect(avisos()).toHaveLength(1);
        expect(temBotao('Corrigir Processo')).toBe(false);
      });

      it.each(['ENVIADO_DGT', 'EM_VALIDACAO_DGT'])('não há Corrigir Processo em %s', (estado) => {
        processoServiceMock.obter.mockReturnValue(of(detalhe({ estado })));

        configurar();

        expect(temBotao('Corrigir Processo')).toBe(false);
      });

      it('clicar abre a confirmação sem fazer o pedido', () => {
        configurar();

        clicar('Corrigir Processo');

        expect(fixture.nativeElement.querySelector('[role="dialog"]')).not.toBeNull();
        expect(fixture.nativeElement.textContent).toContain(
          'O processo volta a Em Submissão para o corrigir e submeter de novo à DGT. Pretende continuar?',
        );
        expect(processoServiceMock.corrigir).not.toHaveBeenCalled();
      });

      it('Cancelar fecha sem pedido e o foco volta ao botão', () => {
        vi.useFakeTimers();
        try {
          configurar();
          document.body.appendChild(fixture.nativeElement);
          const botao = fixture.nativeElement.querySelector('.acao-corrigir button') as HTMLElement;
          botao.focus();
          clicar('Corrigir Processo');
          vi.runAllTimers();

          clicar('Cancelar');

          expect(processoServiceMock.corrigir).not.toHaveBeenCalled();
          expect(fixture.componentInstance.confirmacaoCorrigirAberta()).toBe(false);
          expect(fixture.nativeElement.querySelector('[role="dialog"]')).toBeNull();
          expect(document.activeElement).toBe(botao);
        } finally {
          fixture.nativeElement.remove();
          vi.useRealTimers();
        }
      });
    });

    describe('confirmar', () => {
      it('chama a API com a versão lida, relê o detalhe e abre o formulário com a mensagem de correção', () => {
        processoServiceMock.obter
          .mockReturnValueOnce(of(anulado()))
          .mockReturnValueOnce(of(emCorrecao({ ultimaRejeicaoIncm: RECUSA_INCM })));
        configurar();

        clicar('Corrigir Processo');
        clicar('Corrigir');

        expect(processoServiceMock.corrigir).toHaveBeenCalledWith('p1', 3);
        expect(processoServiceMock.obter).toHaveBeenCalledTimes(2);
        expect(fixture.componentInstance.processo()!.estado).toBe('EM_SUBMISSAO');
        expect(fixture.nativeElement.querySelector('app-estado-processo-badge').textContent).toContain('Em submissão');
        expect(fixture.componentInstance.emEdicao()).toBe(true);
        expect(fixture.nativeElement.querySelector('[role="dialog"]')).toBeNull();
        expect(fixture.nativeElement.querySelector('.banner-sucesso-acao').textContent).toContain(
          'Processo em correção. Corrija e volte a submeter.',
        );
        expect(temBotao('Corrigir Processo')).toBe(false);
      });

      it('o foco vai para o título «Informação Geral» do formulário', () => {
        processoServiceMock.obter
          .mockReturnValueOnce(of(anulado()))
          .mockReturnValueOnce(of(emCorrecao({ ultimaRejeicaoIncm: RECUSA_INCM })));
        configurar();
        document.body.appendChild(fixture.nativeElement);
        try {
          clicar('Corrigir Processo');
          clicar('Corrigir');
          fixture.detectChanges();

          const ativo = document.activeElement as HTMLElement;
          expect(ativo.textContent).toContain('Informação Geral');
          expect(ativo.closest('h2')).not.toBeNull();
        } finally {
          fixture.nativeElement.remove();
        }
      });

      it('em correção o aviso da recusa mantém-se com o formulário aberto', () => {
        processoServiceMock.obter
          .mockReturnValueOnce(of(rejeitado()))
          .mockReturnValueOnce(of(emCorrecao({ ultimaRejeicaoDgt: REJEICAO })));
        configurar();

        clicar('Corrigir Processo');
        clicar('Corrigir');

        expect(avisos()).toHaveLength(1);
        expect(avisos()[0].textContent).toContain('Rejeitado pela DGT');
      });

      it('versão desatualizada mostra o erro, fecha a confirmação, recarrega e não repete o pedido', () => {
        processoServiceMock.corrigir.mockReturnValue(
          throwError(() => new ApiError('VERSAO_DESATUALIZADA', 'O processo foi alterado; recarregue.', 't1', null)),
        );
        configurar();

        clicar('Corrigir Processo');
        clicar('Corrigir');

        expect(fixture.nativeElement.textContent).toContain('O processo foi alterado; recarregue.');
        expect(fixture.nativeElement.querySelector('[role="dialog"]')).toBeNull();
        expect(fixture.componentInstance.emEdicao()).toBe(false);
        expect(processoServiceMock.obter).toHaveBeenCalledTimes(2);
        expect(processoServiceMock.corrigir).toHaveBeenCalledOnce();
      });

      it('estado inválido oferece Recarregar e não abre o formulário', () => {
        processoServiceMock.corrigir.mockReturnValue(
          throwError(() => new ApiError('ESTADO_INVALIDO', 'Este processo não pode ser corrigido neste estado.', 't1', null)),
        );
        configurar();

        clicar('Corrigir Processo');
        clicar('Corrigir');

        expect(temBotao('Recarregar')).toBe(true);
        expect(fixture.nativeElement.querySelector('[role="dialog"]')).toBeNull();
        expect(fixture.componentInstance.emEdicao()).toBe(false);
      });

      it('um 403 mostra o banner de erro e fecha a confirmação', () => {
        processoServiceMock.corrigir.mockReturnValue(
          throwError(() => new ApiError('SEM_PERMISSAO', 'Não tem permissão para esta ação.', 't1', null)),
        );
        configurar();

        clicar('Corrigir Processo');
        clicar('Corrigir');

        expect(fixture.nativeElement.querySelector('.error-banner')).not.toBeNull();
        expect(fixture.nativeElement.querySelector('[role="dialog"]')).toBeNull();
        expect(temBotao('Recarregar')).toBe(false);
      });
    });

    describe('em correção (EM_SUBMISSAO depois de corrigir)', () => {
      beforeEach(() => {
        processoServiceMock.obter.mockReturnValue(of(emCorrecao({ ultimaRejeicaoDgt: REJEICAO })));
      });

      it('o autor edita como num rascunho: Editar rascunho, sem Corrigir Processo nem Voltar a submeter', () => {
        configurar();

        expect(temBotao('Editar rascunho')).toBe(true);
        expect(temBotao('Corrigir Processo')).toBe(false);
        expect(temBotao('Voltar a submeter')).toBe(false);
      });

      it('com o formulário aberto oferece Avançar para Submissão, que leva ao passo 3 do assistente', () => {
        processoServiceMock.obter.mockReturnValue(of(emCorrecao({ ultimaRejeicaoDgt: REJEICAO, elementosAfetados: ['PECAS_ESCRITAS'] })));
        configurar();
        const navegar = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
        clicar('Editar rascunho');

        clicar('Avançar para Submissão');

        expect(processoServiceMock.guardarRascunho).toHaveBeenCalledWith('p1', expect.objectContaining({ versao: 4 }));
        expect(navegar).toHaveBeenCalledWith(expect.anything(), { queryParams: { processoId: 'p1' } });
      });

      it('a edição mostra a Fundamentação RJIGT gravada e o Guardar reenvia-a', () => {
        processoServiceMock.obter.mockReturnValue(
          of(emCorrecao({
            ultimaRejeicaoDgt: REJEICAO,
            pedeFundamentacaoRjigt: true,
            fundamentacaoRjigt: ['ART72B'],
            opcoesFundamentacaoRjigt: [
              { codigo: 'ART72A', descricao: 'Art. 72.º-A' },
              { codigo: 'ART72B', descricao: 'Art. 72.º-B' },
            ],
          })),
        );
        configurar();
        clicar('Editar rascunho');

        const caixas = Array.from(
          fixture.nativeElement.querySelectorAll('fieldset.fundamentacao input[type="checkbox"]') as NodeListOf<HTMLInputElement>,
        );
        expect(caixas.map((caixa) => caixa.checked)).toEqual([false, true]);

        clicar('Guardar');

        expect(processoServiceMock.guardarRascunho).toHaveBeenCalledWith('p1', expect.objectContaining({ fundamentacaoRjigt: ['ART72B'] }));
      });

      it('os campos bloqueados aparecem desativados e o Guardar reenvia os valores gravados', () => {
        const bloqueados = [
          'sumario', 'dataAssinatura', 'normaLegal', 'tipoInformacao', 'publicarSuplemento', 'dataSuplemento', 'contactoTramitacao', 'entidadeEmitente', 'contactoPublicacao', 'elementosAfetados',
          ...['prazoAnos', 'prazoMeses', 'prazoDias', 'avaliacaoAmbiental', 'dataDeliberacao', 'textoPublicar'].map((c) => `informacaoBase.deliberacao.${c}`),
          ...['prazoAnos', 'prazoMeses', 'prazoDias', 'avaliacaoAmbiental'].map((c) => `informacaoBase.textoAto.${c}`),
        ];
        processoServiceMock.obter.mockReturnValue(of(emCorrecao({ camposBloqueados: bloqueados, ultimaRejeicaoDgt: REJEICAO })));
        configurar();
        clicar('Editar rascunho');

        expect(fixture.componentInstance.formularioRef()!.form.controls.sumario.disabled).toBe(true);
        expect(fixture.componentInstance.formularioRef()!.form.controls.elementosAfetados.disabled).toBe(true);
        expect(fixture.componentInstance.documentosRef()!.form.controls.dataAssinatura.disabled).toBe(true);
        expect(fixture.nativeElement.querySelector('app-processo-formulario-inicial .aviso-bloqueado')?.textContent).toContain(
          'Bloqueado depois da primeira submissão',
        );

        clicar('Guardar');

        expect(processoServiceMock.guardarRascunho).toHaveBeenCalledWith(
          'p1',
          expect.objectContaining({
            sumario: 'Abertura do período de discussão pública',
            normaLegal: 'Artigo 89.º do RJIGT',
            dataAssinatura: '2026-09-10',
          }),
        );
      });

      it('sem Informação Base no Procedimento a data bloqueada fica no cartão, com o aviso, e o Guardar reenvia-a', () => {
        processoServiceMock.obter.mockReturnValue(
          of(emCorrecao({ dataAssinaturaNoFormulario: true, camposBloqueados: ['dataAssinatura'], ultimaRejeicaoDgt: REJEICAO })),
        );
        configurar();
        clicar('Editar rascunho');

        const data = fixture.nativeElement.querySelector('app-processo-formulario-inicial input.data-assinatura') as HTMLInputElement;
        expect(data.disabled).toBe(true);
        expect(data.value).toBe('2026-09-10');
        expect(fixture.nativeElement.querySelector('app-processo-formulario-inicial #bloqueado-dataAssinatura')).not.toBeNull();

        clicar('Guardar');

        expect(processoServiceMock.guardarRascunho).toHaveBeenCalledWith('p1', expect.objectContaining({ dataAssinatura: '2026-09-10' }));
      });

      describe('desbloquear um campo', () => {
        const BLOQUEADOS = ['sumario', 'dataAssinatura', 'normaLegal', 'tipoInformacao', 'publicarSuplemento', 'dataSuplemento'];

        function botaoDesbloquear(rotulo: string): HTMLButtonElement | null {
          return fixture.nativeElement.querySelector(`app-desbloquear-campo button[aria-label="Desbloquear ${rotulo}"]`);
        }

        function avisoDoSumario(): HTMLElement | null {
          return fixture.nativeElement.querySelector('#bloqueado-sumario');
        }

        it('sem «desbloquear-campo» nas ações, os campos bloqueados mostram o aviso e não a ação', () => {
          processoServiceMock.obter.mockReturnValue(of(emCorrecao({ camposBloqueados: BLOQUEADOS })));
          configurar();
          clicar('Editar rascunho');

          expect(avisoDoSumario()).not.toBeNull();
          expect(fixture.nativeElement.querySelectorAll('app-desbloquear-campo button')).toHaveLength(0);
        });

        it('com «desbloquear-campo», cada campo bloqueado mostra o aviso e «Desbloquear»', () => {
          processoServiceMock.obter.mockReturnValue(
            of(emCorrecao({ camposBloqueados: BLOQUEADOS, acoesDisponiveis: ['submeter', 'desbloquear-campo'] })),
          );
          configurar();
          clicar('Editar rascunho');

          expect(avisoDoSumario()!.textContent!.trim()).toBe('Bloqueado depois da primeira submissão');
          expect(botaoDesbloquear('Sumário conteúdo do ato')).not.toBeNull();
          expect(botaoDesbloquear('Norma legal ou mera conveniência')).not.toBeNull();
          expect(botaoDesbloquear('Data de Assinatura do ato')).not.toBeNull();
        });

        it('confirmar o motivo chama o PATCH, o campo fica editável e, gravado com outro valor, volta a aparecer bloqueado', () => {
          processoServiceMock.obter.mockReturnValue(
            of(emCorrecao({ camposBloqueados: BLOQUEADOS, acoesDisponiveis: ['submeter', 'desbloquear-campo'] })),
          );
          const semSumario = BLOQUEADOS.filter((chave) => chave !== 'sumario');
          processoServiceMock.desbloquearCampo.mockReturnValue(
            of({ id: 'p1', estado: 'EM_SUBMISSAO', versao: 5, camposBloqueados: semSumario }),
          );
          configurar();
          clicar('Editar rascunho');
          const formulario = fixture.componentInstance.formularioRef()!;
          expect(formulario.form.controls.sumario.disabled).toBe(true);

          botaoDesbloquear('Sumário conteúdo do ato')!.click();
          fixture.detectChanges();
          const motivo = fixture.nativeElement.querySelector('#motivo-desbloqueio-campo') as HTMLTextAreaElement;
          motivo.value = 'A DGT recusou o sumário.';
          motivo.dispatchEvent(new Event('input'));
          fixture.detectChanges();
          clicar('Confirmar');

          expect(processoServiceMock.desbloquearCampo).toHaveBeenCalledWith('p1', {
            versao: 4,
            campo: 'sumario',
            motivo: 'A DGT recusou o sumário.',
          });
          expect(formulario.form.controls.sumario.disabled).toBe(false);
          expect(avisoDoSumario()).toBeNull();
          expect(fixture.componentInstance.processo()!.versao).toBe(5);

          formulario.form.controls.sumario.setValue('Resumo corrigido');
          processoServiceMock.guardarRascunho.mockReturnValue(
            of({ id: 'p1', estado: 'EM_SUBMISSAO', versao: 6, camposBloqueados: BLOQUEADOS }),
          );
          clicar('Guardar');

          expect(processoServiceMock.guardarRascunho).toHaveBeenCalledWith(
            'p1',
            expect.objectContaining({ versao: 5, sumario: 'Resumo corrigido' }),
          );
          expect(avisoDoSumario()).not.toBeNull();
          expect(formulario.form.controls.sumario.disabled).toBe(true);
          expect(formulario.form.controls.sumario.value).toBe('Resumo corrigido');
        });
      });
    });
  });

  describe('pedir apoio', () => {
    function botaoPedirApoio(): HTMLButtonElement | undefined {
      return Array.from(fixture.nativeElement.querySelectorAll('button')).find(
        (b) => (b as HTMLElement).textContent?.trim() === 'Pedir apoio',
      ) as HTMLButtonElement | undefined;
    }

    it('o utilizador externo que vê o processo tem «Pedir apoio», que abre o modal no pedido de apoio com o processo fixo', () => {
      configurar();

      clicar('Pedir apoio');

      const dialogo = fixture.nativeElement.querySelector('[role="dialog"]') as HTMLElement;
      expect(dialogo.textContent).toContain('Pedido de apoio');
      expect(dialogo.querySelector<HTMLInputElement>('[data-campo="tipo-pedido-apoio"]')?.checked).toBe(true);
      const processo = dialogo.querySelector<HTMLInputElement>('[data-campo="processo-fixo"] input')!;
      expect(processo.value).toBe('00001 — PDM de Sintra — Abertura de discussão pública');
      expect(processo.disabled).toBe(true);
      expect(dialogo.querySelector('[data-campo="pesquisa-processo"]')).toBeNull();
    });

    it('depois de enviar fecha o modal e confirma «Pedido enviado à DGT.»', () => {
      configurar();
      clicar('Pedir apoio');
      const dialogo = fixture.nativeElement.querySelector('[role="dialog"]') as HTMLElement;
      for (const [campo, valor] of [['titulo', 'Ajuda'], ['texto', 'Não consigo avançar.']]) {
        const entrada = dialogo.querySelector<HTMLInputElement | HTMLTextAreaElement>(`[data-campo="${campo}"] input, [data-campo="${campo}"] textarea`)!;
        entrada.value = valor;
        entrada.dispatchEvent(new Event('input'));
      }
      fixture.detectChanges();

      (dialogo.querySelector('[data-acao="criar"]') as HTMLButtonElement).click();
      fixture.detectChanges();

      expect(notificacaoServiceMock.criar).toHaveBeenCalledWith(
        expect.objectContaining({ tipo: 'PEDIDO_APOIO', processoId: 'p1', destinatarios: [] }),
      );
      expect(fixture.nativeElement.querySelector('[role="dialog"]')).toBeNull();
      expect(fixture.nativeElement.querySelector('.banner-sucesso-acao')?.textContent?.trim()).toBe('Pedido enviado à DGT.');
    });

    it.each(['MASTER', 'TECNICO', 'VISITANTE'])('o perfil interno %s não tem «Pedir apoio»', (perfil) => {
      perfilInternoMock = perfil;
      atorMock = perfil === 'VISITANTE' ? null : 'DGT';

      configurar();

      expect(botaoPedirApoio()).toBeUndefined();
      expect(fixture.nativeElement.querySelector('app-criar-notificacao-modal')).toBeNull();
    });
  });

  describe('associação a um IGT em curso (REN)', () => {
    const alteracaoPdm: DinamicaAssociavel = {
      dinamicaId: 'dpdm',
      numeroDinamica: 'DIN-2026-0007',
      descritivo: 'Alteração do PDM de Sintra',
      tipoDinamica: 'ALTERACAO',
      estado: 'EM_CURSO',
      instrumento: { id: 'ipdm', designacao: 'PDM de Sintra', tipoInstrumento: 'PDM' },
    };

    function pedido(): Record<string, unknown> {
      return processoServiceMock.guardarRascunho.mock.calls[0][1];
    }

    function bloco(): HTMLElement | null {
      return fixture.nativeElement.querySelector('.associacao-igt');
    }

    function responder(valor: 'SIM' | 'NAO'): void {
      (bloco()!.querySelector(`input[value="${valor}"]`) as HTMLInputElement).click();
      fixture.detectChanges();
    }

    function escolher(dinamicaId: string): void {
      const select = bloco()!.querySelector('select') as HTMLSelectElement;
      select.value = dinamicaId;
      select.dispatchEvent(new Event('change'));
      fixture.detectChanges();
    }

    beforeEach(() => {
      processoServiceMock.dinamicasAssociaveis.mockReturnValue(
        of({ content: [alteracaoPdm, { ...alteracaoPdm, dinamicaId: 'dpp', numeroDinamica: 'DIN-2026-0009' }], page: 0, size: 20, totalElements: 2, totalPages: 1 }),
      );
    });

    it('sem aceitaDinamicaAssociada (ex.: Correção Material da REN) não mostra o bloco, não pede a lista e grava dinamicaAssociadaId nulo', () => {
      processoServiceMock.obter.mockReturnValue(of(detalhe({ estado: 'EM_SUBMISSAO', aceitaDinamicaAssociada: false })));
      configurar();
      clicar('Editar rascunho');

      expect(bloco()).toBeNull();
      clicar('Guardar');

      expect(processoServiceMock.dinamicasAssociaveis).not.toHaveBeenCalled();
      expect(pedido()['dinamicaAssociadaId']).toBeNull();
      expect('dinamicaAssociadaId' in pedido()).toBe(true);
      expect('dadosFormulario' in pedido()).toBe(false);
    });

    it('com aceitaDinamicaAssociada abre em «Não», sem pedir a lista; «Sim» mostra as duas opções', () => {
      processoServiceMock.obter.mockReturnValue(of(detalhe({ estado: 'EM_SUBMISSAO', aceitaDinamicaAssociada: true })));
      configurar();
      clicar('Editar rascunho');

      expect(bloco()!.querySelector('h3')!.textContent!.trim()).toBe('Associação a um IGT em curso');
      expect((bloco()!.querySelector('input[value="NAO"]') as HTMLInputElement).checked).toBe(true);
      expect(processoServiceMock.dinamicasAssociaveis).not.toHaveBeenCalled();

      responder('SIM');

      expect(processoServiceMock.dinamicasAssociaveis).toHaveBeenCalledWith('p1', 0, 20);
      const opcoes = [...bloco()!.querySelectorAll('option')].slice(1).map((o) => o.textContent!.trim());
      expect(opcoes).toEqual([
        'DIN-2026-0007 · ALTERACAO · Alteração do PDM de Sintra — PDM de Sintra (PDM)',
        'DIN-2026-0009 · ALTERACAO · Alteração do PDM de Sintra — PDM de Sintra (PDM)',
      ]);
    });

    it('«Sim» e uma Dinâmica: o PUT leva o id e dadosFormulario.simultaneoComPmot = SIM', () => {
      processoServiceMock.obter.mockReturnValue(of(detalhe({ estado: 'EM_SUBMISSAO', aceitaDinamicaAssociada: true })));
      configurar();
      clicar('Editar rascunho');
      responder('SIM');
      escolher('dpdm');

      clicar('Guardar');

      expect(pedido()['dinamicaAssociadaId']).toBe('dpdm');
      expect(pedido()['dadosFormulario']).toEqual({ simultaneoComPmot: 'SIM' });
    });

    it('ao voltar a abrir um processo com associação, «Sim» vem escolhido e a opção selecionada', () => {
      processoServiceMock.obter.mockReturnValue(
        of(detalhe({ estado: 'EM_SUBMISSAO', aceitaDinamicaAssociada: true, dinamicaAssociadaId: 'dpdm', dinamicaAssociada: alteracaoPdm })),
      );
      configurar();
      clicar('Editar rascunho');

      expect((bloco()!.querySelector('input[value="SIM"]') as HTMLInputElement).checked).toBe(true);
      expect((bloco()!.querySelector('select') as HTMLSelectElement).value).toBe('dpdm');
    });

    it('passar de uma Dinâmica escolhida a «Não» esconde o campo e o PUT leva dinamicaAssociadaId nulo', () => {
      processoServiceMock.obter.mockReturnValue(
        of(detalhe({ estado: 'EM_SUBMISSAO', aceitaDinamicaAssociada: true, dinamicaAssociadaId: 'dpdm', dinamicaAssociada: alteracaoPdm })),
      );
      configurar();
      clicar('Editar rascunho');

      responder('NAO');
      expect(bloco()!.querySelector('select')).toBeNull();
      clicar('Guardar');

      expect(pedido()['dinamicaAssociadaId']).toBeNull();
      expect(pedido()['dadosFormulario']).toEqual({ simultaneoComPmot: 'NAO' });
    });

    it('400 DINAMICA_ASSOCIADA_INVALIDA ao gravar: a mensagem fica junto do seletor, sem banner, e o foco vai para ele', async () => {
      processoServiceMock.obter.mockReturnValue(
        of(detalhe({ estado: 'EM_SUBMISSAO', aceitaDinamicaAssociada: true, dinamicaAssociadaId: 'dpdm', dinamicaAssociada: alteracaoPdm })),
      );
      processoServiceMock.guardarRascunho.mockReturnValue(
        throwError(
          () =>
            new ApiError('DINAMICA_ASSOCIADA_INVALIDA', 'A dinâmica indicada não está disponível para associação.', 't1', [
              { path: 'dinamicaAssociadaId', erro: 'A dinâmica indicada não está disponível para associação.' },
            ]),
        ),
      );
      configurar();
      document.body.appendChild(fixture.nativeElement);
      try {
        clicar('Editar rascunho');
        clicar('Guardar');
        await fixture.whenStable();
        fixture.detectChanges();

        const select = bloco()!.querySelector('select') as HTMLSelectElement;
        const erro = bloco()!.querySelector('.seletor-erro')!;
        expect(erro.textContent!.trim()).toBe('A dinâmica indicada não está disponível para associação.');
        expect(select.getAttribute('aria-describedby')).toContain(erro.id);
        expect(fixture.nativeElement.querySelector('app-error-banner .error-banner')).toBeNull();
        expect(document.activeElement).toBe(select);
      } finally {
        fixture.nativeElement.remove();
      }
    });

    it('500 na lista: banner com «Tentar novamente» e o resto do formulário continua editável', () => {
      processoServiceMock.obter.mockReturnValue(of(detalhe({ estado: 'EM_SUBMISSAO', aceitaDinamicaAssociada: true })));
      processoServiceMock.dinamicasAssociaveis.mockReturnValue(
        throwError(() => new ApiError('ERRO_INTERNO', 'Ocorreu um erro inesperado.', 't2', null)),
      );
      configurar();
      clicar('Editar rascunho');
      responder('SIM');

      expect(bloco()!.querySelector('.error-banner')!.textContent!.trim()).toBe('Ocorreu um erro inesperado.');
      expect([...bloco()!.querySelectorAll('button')].some((b) => b.textContent!.trim() === 'Tentar novamente')).toBe(true);
      expect((fixture.nativeElement.querySelector('textarea[formcontrolname="sumario"]') as HTMLTextAreaElement).disabled).toBe(false);
      expect((bloco()!.querySelector('input[value="NAO"]') as HTMLInputElement).disabled).toBe(false);
    });

    it('depois da primeira submissão a pergunta e o seletor ficam desativados com a associação gravada', () => {
      processoServiceMock.obter.mockReturnValue(
        of(
          detalhe({
            estado: 'EM_SUBMISSAO',
            aceitaDinamicaAssociada: true,
            dinamicaAssociadaId: 'dpdm',
            dinamicaAssociada: alteracaoPdm,
            camposBloqueados: ['dinamicaAssociadaId'],
          }),
        ),
      );
      configurar();
      clicar('Editar rascunho');

      expect((bloco()!.querySelector('input[value="SIM"]') as HTMLInputElement).disabled).toBe(true);
      const select = bloco()!.querySelector('select') as HTMLSelectElement;
      expect(select.disabled).toBe(true);
      expect(select.value).toBe('dpdm');
      clicar('Guardar');
      expect(pedido()['dinamicaAssociadaId']).toBe('dpdm');
    });

    it('em leitura, os detalhes mostram «IGT associado» com o número e a designação; sem associação, nada', () => {
      processoServiceMock.obter.mockReturnValue(of(detalhe({ dinamicaAssociadaId: 'dpdm', dinamicaAssociada: alteracaoPdm })));
      configurar();

      const linha = fixture.nativeElement.querySelector('.detalhes .igt-associado') as HTMLElement;
      expect(linha.querySelector('dt')!.textContent!.trim()).toBe('IGT associado');
      expect(linha.querySelector('dd')!.textContent!.trim()).toBe('DIN-2026-0007 · PDM de Sintra');

      processoServiceMock.obter.mockReturnValue(of(detalhe()));
      fixture.componentInstance.recarregar();
      fixture.detectChanges();
      expect(fixture.nativeElement.querySelector('.igt-associado')).toBeNull();
    });
  });
});
