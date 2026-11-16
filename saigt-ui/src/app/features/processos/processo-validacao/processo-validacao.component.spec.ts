import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Subject, of, throwError } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ProcessoValidacaoComponent } from './processo-validacao.component';
import { ApiError } from '../../../core/models/error-response.model';
import { DocumentoProcesso, EstruturaDocumental, ItemDocumental } from '../../../models/documento.model';
import { ItemPlantas, Plantas } from '../../../models/plantas.model';
import { AcaoProcesso, EstadoValidacao, ProcessoDetalhe, ValidacaoItensResponse } from '../../../models/processo.model';
import { DescargaService } from '../../../services/descarga.service';
import { DocumentoService } from '../../../services/documento.service';
import { ListaValoresService } from '../../../services/lista-valores.service';
import { PlantasService } from '../../../services/plantas.service';
import { ProcessoService } from '../../../services/processo.service';

function documento(parcial: Partial<DocumentoProcesso> = {}): DocumentoProcesso {
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

function item(parcial: Partial<ItemDocumental>): ItemDocumental {
  return {
    documentoConfigId: 1,
    codigo: 'DELIBERACAO_PUB',
    nome: 'Deliberação',
    categoria: 'PUBLICACAO',
    tipoElemento: 'PECA_ESCRITA',
    formato: 'EDITAVEL',
    obrigatorio: true,
    extensoesAceites: ['docx', 'pdf'],
    ficheiros: [],
    observacoes: null,
    pendente: false,
    obtidoDoGeoconform: false,
    ...parcial,
  };
}

/** Dois ficheiros na Deliberação e os dois blocos da Informação Base: quatro itens por validar. */
function estruturaCom(estados: Record<string, EstadoValidacao> = {}): EstruturaDocumental {
  return {
    separadores: [
      {
        separador: 'Deliberação',
        elementoInformacaoBase: 'DELIBERACAO',
        publicacao: [item({ ficheiros: [documento({ estadoValidacao: estados['d-pub'] ?? 'POR_VALIDAR' })] })],
        deposito: [
          item({
            documentoConfigId: 2,
            codigo: 'DELIBERACAO_DEP',
            categoria: 'DEPOSITO',
            obrigatorio: false,
            ficheiros: [
              documento({
                id: 'd-dep',
                documentoConfigId: 2,
                nomeFicheiro: 'deposito.pdf',
                categoria: 'DEPOSITO',
                estadoValidacao: estados['d-dep'] ?? 'POR_VALIDAR',
              }),
            ],
          }),
        ],
      },
      { separador: 'Texto do Ato', elementoInformacaoBase: 'TEXTO_ATO', publicacao: [], deposito: [] },
    ],
    outros: [],
    pendencias: [],
  };
}

const ITENS = [
  { tipo: 'DOCUMENTO', itemId: 'd-pub' },
  { tipo: 'DOCUMENTO', itemId: 'd-dep' },
  { tipo: 'INFORMACAO_BASE', itemId: 'ib-delib' },
  { tipo: 'INFORMACAO_BASE', itemId: 'ib-texto' },
] as const;

/** A resposta do `PUT` traz todos os itens do processo, não só o pedido. */
function resposta(validados: string[], rejeitados: string[] = []): ValidacaoItensResponse {
  const itens = ITENS.map((i) => {
    const rejeitado = rejeitados.includes(i.itemId);
    return {
      ...i,
      estadoValidacao: (rejeitado ? 'REJEITADO' : validados.includes(i.itemId) ? 'VALIDADO' : 'POR_VALIDAR') as EstadoValidacao,
      rejeicao: rejeitado ? { categoriaId: 1, motivoId: 5, notas: 'Ilegível.' } : null,
    };
  });
  return { itens, todosRevistos: itens.every((i) => i.estadoValidacao !== 'POR_VALIDAR') };
}

function processo(parcial: Partial<ProcessoDetalhe> = {}): ProcessoDetalhe {
  return {
    id: 'p1',
    versao: 3,
    estado: 'EM_VALIDACAO_DGT',
    dadosFormulario: null,
    dataAssinatura: null,
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

/** Os quatro ficheiros da página das Plantas de um PDM: os dois ZIP, o TXT dos nomes e o cartograma. */
const PLANTAS = [
  { id: 'pl-ord', configId: 41, item: 'Planta de Ordenamento', ficheiro: 'Planta_de_Ordenamento.zip' },
  { id: 'pl-cond', configId: 42, item: 'Planta de Condicionantes', ficheiro: 'Planta_de_Condicionantes.zip' },
  { id: 'pl-txt', configId: 43, item: 'Nomes das plantas', ficheiro: 'nomes_plantas.txt' },
  { id: 'pl-carto', configId: 44, item: 'Cartograma', ficheiro: 'cartograma.zip' },
] as const;

function itemPlantas(planta: (typeof PLANTAS)[number], comFicheiro = true): ItemPlantas {
  return {
    documentoConfigId: planta.configId,
    codigo: `PLANTAS_${planta.configId}`,
    nome: planta.item,
    descricao: null,
    categoria: 'DEPOSITO',
    obrigatorio: true,
    extensoesAceites: ['zip', 'txt'],
    ficheiro: comFicheiro ? { id: planta.id, nomeFicheiro: planta.ficheiro } : null,
  };
}

function paginaPlantas(semFicheiro: string[] = []): Plantas {
  return {
    exigePlantas: true,
    itens: PLANTAS.map((planta) => itemPlantas(planta, !semFicheiro.includes(planta.id))),
    linhas: [],
    errosTxt: [],
    exigeCartograma: true,
    cartogramaPdfDisponivel: false,
  };
}

/** A lista de documentos traz os das Plantas e os dos separadores, todos por ordem de upload. */
function documentosPlantas(estados: Record<string, EstadoValidacao> = {}): DocumentoProcesso[] {
  return PLANTAS.map((planta) =>
    documento({
      id: planta.id,
      documentoConfigId: planta.configId,
      nomeFicheiro: planta.ficheiro,
      categoria: 'DEPOSITO',
      estadoValidacao: estados[planta.id] ?? 'POR_VALIDAR',
      criadoPorNome: 'Rui Fontes',
    }),
  );
}

function pagina(conteudo: DocumentoProcesso[]) {
  return { content: conteudo, page: 0, size: 100, totalElements: conteudo.length, totalPages: 1 };
}

/** Resposta do `PUT` com os oito itens do processo com plantas. */
function respostaComPlantas(validados: string[], rejeitados: string[] = []): ValidacaoItensResponse {
  const itens = [...PLANTAS.map((planta) => ({ tipo: 'DOCUMENTO' as const, itemId: planta.id })), ...ITENS].map((i) => {
    const rejeitado = rejeitados.includes(i.itemId);
    return {
      ...i,
      estadoValidacao: (rejeitado ? 'REJEITADO' : validados.includes(i.itemId) ? 'VALIDADO' : 'POR_VALIDAR') as EstadoValidacao,
      rejeicao: rejeitado ? { categoriaId: 1, motivoId: 5, notas: 'Ilegível.' } : null,
    };
  });
  return { itens, todosRevistos: itens.every((i) => i.estadoValidacao !== 'POR_VALIDAR') };
}

describe('ProcessoValidacaoComponent', () => {
  let fixture: ComponentFixture<ProcessoValidacaoComponent>;
  let estruturaPedida: Subject<EstruturaDocumental>;
  let estrutura: ReturnType<typeof vi.fn>;
  let listar: ReturnType<typeof vi.fn>;
  let obterPlantas: ReturnType<typeof vi.fn>;
  let descarregar: ReturnType<typeof vi.fn>;
  let gravarValidacaoItens: ReturnType<typeof vi.fn>;
  let alterado: ReturnType<typeof vi.fn>;

  /** A estrutura chega depois do primeiro ciclo, como no HTTP: os ficheiros emitidos ao carregar entram no resumo. */
  function abrir(parcial: Partial<ProcessoDetalhe> = {}, conteudo: EstruturaDocumental = estruturaCom()): void {
    fixture = TestBed.createComponent(ProcessoValidacaoComponent);
    fixture.componentRef.setInput('processo', processo(parcial));
    fixture.componentInstance.processoAlterado.subscribe(alterado);
    fixture.detectChanges();
    estruturaPedida.next(conteudo);
    fixture.detectChanges();
  }

  function texto(): string {
    return fixture.nativeElement.textContent as string;
  }

  function botoes(rotulo: string): HTMLButtonElement[] {
    const todos = Array.from(fixture.nativeElement.querySelectorAll('button')) as HTMLButtonElement[];
    return todos.filter((b) => b.textContent?.trim() === rotulo);
  }

  function clicar(botao: HTMLButtonElement): void {
    botao.click();
    fixture.detectChanges();
  }

  function botaoDoBloco(elemento: 'Deliberação' | 'Texto do Ato'): HTMLButtonElement {
    return fixture.nativeElement.querySelector(`section[aria-label="Informação Base — ${elemento}"] button`) as HTMLButtonElement;
  }

  beforeEach(() => {
    estruturaPedida = new Subject<EstruturaDocumental>();
    estrutura = vi.fn().mockReturnValue(estruturaPedida);
    listar = vi.fn().mockReturnValue(of(pagina(documentosPlantas())));
    obterPlantas = vi.fn().mockReturnValue(of(paginaPlantas()));
    descarregar = vi.fn();
    gravarValidacaoItens = vi.fn().mockReturnValue(of(resposta(['d-pub'])));
    alterado = vi.fn();
    TestBed.configureTestingModule({
      providers: [
        { provide: ProcessoService, useValue: { gravarValidacaoItens } },
        { provide: DocumentoService, useValue: { estrutura, listar } },
        { provide: PlantasService, useValue: { obterPlantas } },
        { provide: DescargaService, useValue: { descarregar, emCurso: () => null, erro: () => null } },
        { provide: ListaValoresService, useValue: { listar: vi.fn().mockReturnValue(of([])) } },
      ],
    });
  });

  it('com `validar` na lista, cada ficheiro e cada bloco da Informação Base tem «Validar»', () => {
    abrir();

    expect(botoes('Validar')).toHaveLength(4);
    expect(botaoDoBloco('Deliberação').textContent?.trim()).toBe('Validar');
    expect(botaoDoBloco('Texto do Ato').textContent?.trim()).toBe('Validar');
  });

  it.each([
    ['TECNICO', ['validar', 'rejeitar']],
    ['MASTER', ['validar', 'rejeitar', 'anular', 'reatribuir-autor']],
  ] as const)('um %s vê os mesmos quatro «Validar»', (_perfil, acoes) => {
    abrir({ acoesDisponiveis: [...acoes] as AcaoProcesso[] });

    expect(botoes('Validar')).toHaveLength(4);
  });

  it.each([
    ['um autor da entidade, num processo noutro estado', ['resgatar']],
    ['um VISITANTE (lista vazia)', []],
  ] as const)('%s não vê «Validar» nem o resumo, mas lê os documentos e a Informação Base', (_quem, acoes) => {
    abrir({ acoesDisponiveis: [...acoes] as AcaoProcesso[] });

    expect(botoes('Validar')).toHaveLength(0);
    expect(texto()).not.toContain('itens validados');
    expect(texto()).toContain('deliberacao.docx');
    expect(texto()).toContain('deposito.pdf');
    expect(fixture.nativeElement.querySelectorAll('.informacao-base')).toHaveLength(2);
  });

  it('mostra «Justificação do desbloqueio» com o texto na linha de um ficheiro desbloqueado na correção', () => {
    const conteudo = estruturaCom();
    conteudo.separadores[0].publicacao[0].ficheiros = [
      documento({ justificacaoDesbloqueio: 'A legenda da planta estava desatualizada.' }),
    ];
    abrir({}, conteudo);

    const linha = fixture.nativeElement.querySelector('.ficheiro') as HTMLElement;
    expect(linha.textContent).toContain('Justificação do desbloqueio: A legenda da planta estava desatualizada.');
    expect(texto()).not.toContain('Desbloqueado:');
    expect(texto()).not.toContain('Desbloquear ficheiro');
  });

  it('não tem «Guardar validação» nem «Bloquear»', () => {
    abrir();

    for (const rotulo of ['Guardar validação', 'Bloquear']) {
      expect(texto()).not.toContain(rotulo);
    }
  });

  it('cada item por validar tem «Validar» e «Rejeitar», como no UI-034', () => {
    abrir();

    expect(botoes('Rejeitar')).toHaveLength(4);
  });

  it('«Rejeitar» abre o modal com o nome do ficheiro, ou com o nome do elemento da Informação Base', () => {
    abrir();

    clicar(botoes('Rejeitar')[0]);
    expect(fixture.nativeElement.querySelector('[role="dialog"]')).not.toBeNull();
    expect(fixture.nativeElement.querySelector('[data-campo="nome-item"]').textContent.trim()).toBe('deliberacao.docx');

    clicar(botoes('Cancelar')[0]);
    expect(fixture.nativeElement.querySelector('[role="dialog"]')).toBeNull();

    const rejeitarTexto = fixture.nativeElement.querySelectorAll(
      'section[aria-label="Informação Base — Texto do Ato"] button',
    )[1] as HTMLButtonElement;
    clicar(rejeitarTexto);
    expect(fixture.nativeElement.querySelector('[data-campo="nome-item"]').textContent.trim()).toBe('Texto do Ato');
  });

  it('confirmar no modal grava REJEITADO, fecha o modal e o item passa a «Rejeitado»', () => {
    gravarValidacaoItens.mockReturnValue(of(resposta([], ['d-pub'])));
    abrir();
    clicar(botoes('Rejeitar')[0]);

    const area = fixture.nativeElement.querySelector('[data-campo="notas"] textarea') as HTMLTextAreaElement;
    area.value = 'Página 3 ilegível.';
    area.dispatchEvent(new Event('input'));
    fixture.detectChanges();
    clicar(botoes('Confirmar Rejeição')[0]);

    expect(gravarValidacaoItens).toHaveBeenCalledExactlyOnceWith('p1', 3, [
      { tipo: 'DOCUMENTO', itemId: 'd-pub', decisao: 'REJEITADO', categoriaId: null, motivoId: null, notas: 'Página 3 ilegível.' },
    ]);
    expect(fixture.nativeElement.querySelector('[role="dialog"]')).toBeNull();
    expect(fixture.nativeElement.querySelector('.rejeitado').textContent.trim()).toBe('✕ Rejeitado');
    const rejeitadas = fixture.nativeElement.querySelectorAll('li.ficheiro-rejeitado');
    expect(rejeitadas).toHaveLength(1);
    expect(rejeitadas[0].textContent).toContain('deliberacao.docx');
    expect(botoes('Rejeitar')).toHaveLength(3);
    expect(alterado).toHaveBeenCalledOnce();
  });

  it('ao voltar à vista, os itens do rascunho continuam «Rejeitado»', () => {
    abrir(
      { rejeicoesEmValidacao: [{ tipo: 'DOCUMENTO', itemId: 'd-dep', categoriaId: 1, motivoId: 5, notas: 'Ilegível.' }] },
      estruturaCom({ 'd-dep': 'REJEITADO' }),
    );

    expect(fixture.nativeElement.querySelectorAll('.rejeitado')).toHaveLength(1);
    expect(botoes('Validar')).toHaveLength(3);
  });

  it('um item do rascunho conta como rejeitado mesmo que o estado lido ainda não o diga', () => {
    abrir({
      rejeicoesEmValidacao: [{ tipo: 'INFORMACAO_BASE', itemId: 'ib-delib', categoriaId: null, motivoId: null, notas: 'Sem data.' }],
    });

    expect(fixture.nativeElement.querySelectorAll('.rejeitado')).toHaveLength(1);
    expect(botaoDoBloco('Deliberação')).toBeNull();
  });

  it('com um item rejeitado e os restantes validados, o resumo não diz que já pode validar o processo', () => {
    gravarValidacaoItens.mockReturnValue(of(resposta(['d-dep', 'ib-delib', 'ib-texto'], ['d-pub'])));
    abrir();

    clicar(botoes('Validar')[1]);

    expect(texto()).toContain('3 de 4 itens validados');
    expect(texto()).not.toContain('Todos os itens estão validados. Carregue em «Seguinte» para abrir o resumo.');
  });

  it('um 409 no modal fecha-o e relê o processo e os ficheiros', () => {
    gravarValidacaoItens.mockReturnValue(
      throwError(() => new ApiError('VERSAO_DESATUALIZADA', 'O processo foi alterado entretanto.', 't1', null)),
    );
    abrir();
    clicar(botoes('Rejeitar')[0]);
    const area = fixture.nativeElement.querySelector('[data-campo="notas"] textarea') as HTMLTextAreaElement;
    area.value = 'Notas.';
    area.dispatchEvent(new Event('input'));
    fixture.detectChanges();
    clicar(botoes('Confirmar Rejeição')[0]);

    expect(texto()).toContain('O processo foi alterado entretanto.');
    clicar(botoes('Recarregar')[0]);

    expect(fixture.nativeElement.querySelector('[role="dialog"]')).toBeNull();
    expect(alterado).toHaveBeenCalledOnce();
    expect(estrutura).toHaveBeenCalledTimes(2);
  });

  it('«Validar» num ficheiro grava logo esse item com a versão do detalhe e passa a «Validado»', () => {
    abrir();

    clicar(botoes('Validar')[0]);

    expect(gravarValidacaoItens).toHaveBeenCalledExactlyOnceWith('p1', 3, [
      { tipo: 'DOCUMENTO', itemId: 'd-pub', decisao: 'VALIDADO' },
    ]);
    expect(botoes('Validar')).toHaveLength(3);
    expect(fixture.nativeElement.querySelectorAll('.validado')).toHaveLength(1);
    expect(fixture.nativeElement.querySelector('.validado').textContent.trim()).toBe('Validado');
  });

  it('«Validar» num bloco da Informação Base grava o elemento com o tipo INFORMACAO_BASE', () => {
    gravarValidacaoItens.mockReturnValue(of(resposta(['ib-texto'])));
    abrir();

    clicar(botaoDoBloco('Texto do Ato'));

    expect(gravarValidacaoItens).toHaveBeenCalledExactlyOnceWith('p1', 3, [
      { tipo: 'INFORMACAO_BASE', itemId: 'ib-texto', decisao: 'VALIDADO' },
    ]);
    expect(botaoDoBloco('Texto do Ato')).toBeNull();
    expect(botaoDoBloco('Deliberação').textContent?.trim()).toBe('Validar');
  });

  it('depois de validar emite processoAlterado, sem payload, para o ecrã reler o detalhe', () => {
    abrir();

    clicar(botoes('Validar')[0]);

    expect(alterado).toHaveBeenCalledExactlyOnceWith(undefined);
  });

  it('enquanto grava, os «Validar» ficam desativados', () => {
    const pedido = new Subject<ValidacaoItensResponse>();
    gravarValidacaoItens.mockReturnValue(pedido);
    abrir();

    clicar(botoes('Validar')[1]);

    expect(botoes('Validar').every((b) => b.disabled)).toBe(true);
    pedido.next(resposta(['d-dep']));
    pedido.complete();
    fixture.detectChanges();
    expect(botoes('Validar').some((b) => b.disabled)).toBe(false);
    expect(botoes('Validar')).toHaveLength(3);
  });

  it('o resumo conta os itens validados e só diz que já pode validar o processo com todos revistos', () => {
    abrir();
    expect(texto()).toContain('0 de 4 itens validados');
    expect(texto()).not.toContain('Todos os itens estão validados. Carregue em «Seguinte» para abrir o resumo.');

    clicar(botoes('Validar')[0]);
    expect(texto()).toContain('1 de 4 itens validados');
    expect(texto()).not.toContain('Todos os itens estão validados. Carregue em «Seguinte» para abrir o resumo.');

    gravarValidacaoItens.mockReturnValue(of(resposta(['d-pub', 'd-dep', 'ib-delib', 'ib-texto'])));
    clicar(botoes('Validar')[0]);
    expect(texto()).toContain('4 de 4 itens validados');
    expect(texto()).toContain('Todos os itens estão validados. Carregue em «Seguinte» para abrir o resumo.');
    expect(botoes('Validar')).toHaveLength(0);
  });

  it('um processo com tudo já validado mostra «Validado» em todos os itens e a indicação de validar', () => {
    abrir(
      {
        todosItensRevistos: true,
        informacaoBase: [
          { id: 'ib-delib', elemento: 'DELIBERACAO', estadoValidacao: 'VALIDADO' },
          { id: 'ib-texto', elemento: 'TEXTO_ATO', estadoValidacao: 'VALIDADO' },
        ],
      },
      estruturaCom({ 'd-pub': 'VALIDADO', 'd-dep': 'VALIDADO' }),
    );

    expect(botoes('Validar')).toHaveLength(0);
    expect(fixture.nativeElement.querySelectorAll('.validado')).toHaveLength(4);
    expect(texto()).toContain('4 de 4 itens validados');
    expect(texto()).toContain('Todos os itens estão validados. Carregue em «Seguinte» para abrir o resumo.');
  });

  it('sem o elemento da Informação Base no detalhe, o bloco não tem «Validar»', () => {
    abrir({ informacaoBase: [] });

    expect(botoes('Validar')).toHaveLength(2);
    expect(botaoDoBloco('Deliberação')).toBeNull();
    expect(texto()).toContain('0 de 2 itens validados');
  });

  it('sem separadores, os elementos da Informação Base aparecem à parte e podem ser validados', () => {
    gravarValidacaoItens.mockReturnValue(of(resposta(['ib-delib'])));
    abrir({}, { separadores: [], outros: [], pendencias: [] });

    expect(texto()).toContain('0 de 2 itens validados');
    expect(botoes('Validar')).toHaveLength(2);

    clicar(botaoDoBloco('Deliberação'));

    expect(gravarValidacaoItens).toHaveBeenCalledExactlyOnceWith('p1', 3, [
      { tipo: 'INFORMACAO_BASE', itemId: 'ib-delib', decisao: 'VALIDADO' },
    ]);
    expect(texto()).toContain('1 de 2 itens validados');
    expect(botoes('Validar')).toHaveLength(1);
  });

  it('com uma versão antiga mostra a mensagem do backend e Recarregar, sem repetir o pedido', () => {
    gravarValidacaoItens.mockReturnValue(
      throwError(() => new ApiError('VERSAO_DESATUALIZADA', 'O processo foi alterado entretanto.', 't1', null)),
    );
    abrir();

    clicar(botoes('Validar')[0]);

    expect(texto()).toContain('O processo foi alterado entretanto.');
    expect(botoes('Recarregar')).toHaveLength(1);
    expect(botoes('Validar').every((b) => b.disabled)).toBe(true);
    expect(gravarValidacaoItens).toHaveBeenCalledOnce();
  });

  it('um processo que já não está em validação também mostra a mensagem e Recarregar', () => {
    gravarValidacaoItens.mockReturnValue(
      throwError(() => new ApiError('ESTADO_INVALIDO', 'O processo já não está em validação.', 't1', null)),
    );
    abrir();

    clicar(botoes('Validar')[0]);

    expect(texto()).toContain('O processo já não está em validação.');
    expect(botoes('Recarregar')).toHaveLength(1);
  });

  it('Recarregar pede ao ecrã o detalhe, lê os ficheiros de novo e volta a deixar validar', () => {
    gravarValidacaoItens.mockReturnValue(
      throwError(() => new ApiError('VERSAO_DESATUALIZADA', 'O processo foi alterado entretanto.', 't1', null)),
    );
    abrir();
    clicar(botoes('Validar')[0]);

    clicar(botoes('Recarregar')[0]);

    expect(alterado).toHaveBeenCalledOnce();
    expect(estrutura).toHaveBeenCalledTimes(2);
    expect(texto()).not.toContain('O processo foi alterado entretanto.');
    expect(botoes('Recarregar')).toHaveLength(0);
    expect(botoes('Validar').some((b) => b.disabled)).toBe(false);
    expect(gravarValidacaoItens).toHaveBeenCalledOnce();
  });

  it('uma falha que não é conflito mostra o erro, sem Recarregar, e o botão volta a ficar ativo', () => {
    gravarValidacaoItens.mockReturnValue(throwError(() => new ApiError('SEM_PERMISSAO', 'Sem permissão.', 't1', null)));
    abrir();

    clicar(botoes('Validar')[0]);

    expect(texto()).toContain('Não tem permissão para executar esta ação.');
    expect(botoes('Recarregar')).toHaveLength(0);
    expect(botoes('Validar')).toHaveLength(4);
    expect(botoes('Validar').some((b) => b.disabled)).toBe(false);
  });

  it('um clique seguinte com o pedido anterior em curso não faz um segundo pedido', () => {
    gravarValidacaoItens.mockReturnValue(new Subject<ValidacaoItensResponse>());
    abrir();

    clicar(botoes('Validar')[0]);
    fixture.componentInstance.validar({ tipo: 'DOCUMENTO', itemId: 'd-dep', estado: 'POR_VALIDAR' });

    expect(gravarValidacaoItens).toHaveBeenCalledOnce();
  });

  it('mostra «A carregar…» enquanto os ficheiros chegam e «Sem documentos carregados» quando não há nenhum', () => {
    fixture = TestBed.createComponent(ProcessoValidacaoComponent);
    fixture.componentRef.setInput('processo', processo());
    fixture.detectChanges();
    expect(texto()).toContain('A carregar…');

    estruturaPedida.next({ separadores: [{ separador: 'Deliberação', elementoInformacaoBase: 'DELIBERACAO', publicacao: [], deposito: [] }], outros: [], pendencias: [] });
    fixture.detectChanges();

    expect(texto()).toContain('Sem documentos carregados');
    expect(botoes('Validar')).toHaveLength(2);
    expect(botaoDoBloco('Deliberação').textContent?.trim()).toBe('Validar');
    expect(botaoDoBloco('Texto do Ato').textContent?.trim()).toBe('Validar');
  });

  it('se os documentos não carregam, mostra o erro e Tentar novamente', () => {
    estrutura.mockReturnValueOnce(throwError(() => new ApiError('ERRO_INTERNO', 'Serviço indisponível.', 't1', null)));
    fixture = TestBed.createComponent(ProcessoValidacaoComponent);
    fixture.componentRef.setInput('processo', processo());
    fixture.detectChanges();

    expect(texto()).toContain('Serviço indisponível.');
    expect(botoes('Tentar novamente')).toHaveLength(1);
  });
  describe('«Seguinte»', () => {
    it('aparece com todos os itens revistos e pelo menos um rejeitado, e emite seguinte para a rejeição', () => {
      gravarValidacaoItens.mockReturnValue(of(resposta(['d-dep', 'ib-delib', 'ib-texto'], ['d-pub'])));
      abrir();
      const seguinte = vi.fn();
      fixture.componentInstance.seguinte.subscribe(seguinte);
      expect(botoes('Seguinte')).toHaveLength(0);

      clicar(botoes('Validar')[1]);
      clicar(botoes('Seguinte')[0]);

      expect(seguinte).toHaveBeenCalledExactlyOnceWith('REJEICAO');
    });

    it('aparece ao reabrir a vista com o rascunho a dizer que o item rejeitado é o único que faltava', () => {
      abrir(
        {
          informacaoBase: [
            { id: 'ib-delib', elemento: 'DELIBERACAO', estadoValidacao: 'VALIDADO' },
            { id: 'ib-texto', elemento: 'TEXTO_ATO', estadoValidacao: 'POR_VALIDAR' },
          ],
          rejeicoesEmValidacao: [{ tipo: 'INFORMACAO_BASE', itemId: 'ib-texto', categoriaId: null, motivoId: null, notas: 'Sem texto.' }],
        },
        estruturaCom({ 'd-pub': 'VALIDADO', 'd-dep': 'VALIDADO' }),
      );

      expect(botoes('Seguinte')).toHaveLength(1);
    });

    it('não aparece com um item por validar, mesmo havendo rejeitados', () => {
      gravarValidacaoItens.mockReturnValue(of(resposta(['d-dep'], ['d-pub'])));
      abrir();

      clicar(botoes('Validar')[1]);

      expect(botoes('Seguinte')).toHaveLength(0);
    });

    it('aparece com todos os itens validados e emite seguinte para o Resumo Validados', () => {
      abrir(
        {
          todosItensRevistos: true,
          informacaoBase: [
            { id: 'ib-delib', elemento: 'DELIBERACAO', estadoValidacao: 'VALIDADO' },
            { id: 'ib-texto', elemento: 'TEXTO_ATO', estadoValidacao: 'VALIDADO' },
          ],
        },
        estruturaCom({ 'd-pub': 'VALIDADO', 'd-dep': 'VALIDADO' }),
      );
      const seguinte = vi.fn();
      fixture.componentInstance.seguinte.subscribe(seguinte);

      expect(botoes('Seguinte')).toHaveLength(1);
      clicar(botoes('Seguinte')[0]);
      expect(seguinte).toHaveBeenCalledExactlyOnceWith('VALIDADOS');
    });

    it('aparece depois de validar o último item, sem nenhum rejeitado', () => {
      gravarValidacaoItens.mockReturnValue(of(resposta(['d-pub', 'd-dep', 'ib-delib', 'ib-texto'])));
      abrir();
      expect(botoes('Seguinte')).toHaveLength(0);

      clicar(botoes('Validar')[0]);

      expect(botoes('Seguinte')).toHaveLength(1);
    });

    it('não aparece a quem não pode validar', () => {
      abrir(
        {
          acoesDisponiveis: [],
          informacaoBase: [
            { id: 'ib-delib', elemento: 'DELIBERACAO', estadoValidacao: 'REJEITADO' },
            { id: 'ib-texto', elemento: 'TEXTO_ATO', estadoValidacao: 'VALIDADO' },
          ],
        },
        estruturaCom({ 'd-pub': 'VALIDADO', 'd-dep': 'VALIDADO' }),
      );

      expect(botoes('Seguinte')).toHaveLength(0);
    });
  });

  describe('processo devolvido pela PCM (revalidacaoPcm)', () => {
    const IB_VALIDADA = [
      { id: 'ib-delib', elemento: 'DELIBERACAO', estadoValidacao: 'VALIDADO' },
      { id: 'ib-texto', elemento: 'TEXTO_ATO', estadoValidacao: 'VALIDADO' },
    ] as ProcessoDetalhe['informacaoBase'];

    function abrirDevolvido(): void {
      abrir(
        {
          estado: 'VALIDADO_DGT',
          acoesDisponiveis: [],
          revalidacaoPcm: true,
          todosItensRevistos: false,
          informacaoBase: IB_VALIDADA,
        },
        estruturaCom({ 'd-dep': 'VALIDADO' }),
      );
    }

    it('o documento acrescentado pela PCM só tem «Validar», com o nome no nome acessível, e os outros ficam «Validado»', () => {
      abrirDevolvido();

      const validar = botoes('Validar');
      expect(validar).toHaveLength(1);
      expect(validar[0].getAttribute('aria-label')).toBe('Validar deliberacao.docx');
      expect(botoes('Rejeitar')).toHaveLength(0);
      expect(fixture.nativeElement.querySelectorAll('.validado')).toHaveLength(3);
      expect(texto()).toContain('Valide os documentos acrescentados pela PCM.');
      expect(botoes('Seguinte')).toHaveLength(0);
    });

    it('«Validar» grava logo o item como VALIDADO e a vista diz que o processo já pode seguir', () => {
      gravarValidacaoItens.mockReturnValue(of(resposta(['d-pub', 'd-dep', 'ib-delib', 'ib-texto'])));
      abrirDevolvido();

      clicar(botoes('Validar')[0]);

      expect(gravarValidacaoItens).toHaveBeenCalledExactlyOnceWith('p1', 3, [
        { tipo: 'DOCUMENTO', itemId: 'd-pub', decisao: 'VALIDADO' },
      ]);
      expect(alterado).toHaveBeenCalledOnce();
      expect(botoes('Validar')).toHaveLength(0);
      expect(texto()).toContain('Já pode enviar o processo à INCM ou de novo à PCM.');
      expect(fixture.nativeElement.querySelector('.resumo')?.getAttribute('role')).toBe('status');
      expect(botoes('Seguinte')).toHaveLength(1);
    });

    it('o detalhe relido, já sem revalidacaoPcm, mantém a mensagem e o «Seguinte» leva ao Resumo Validados', () => {
      gravarValidacaoItens.mockReturnValue(of(resposta(['d-pub', 'd-dep', 'ib-delib', 'ib-texto'])));
      abrirDevolvido();
      const seguinte = vi.fn();
      fixture.componentInstance.seguinte.subscribe(seguinte);
      clicar(botoes('Validar')[0]);

      fixture.componentRef.setInput(
        'processo',
        processo({ estado: 'VALIDADO_DGT', acoesDisponiveis: ['enviar-incm', 'enviar-pcm'], revalidacaoPcm: false, informacaoBase: IB_VALIDADA }),
      );
      fixture.detectChanges();

      expect(texto()).toContain('Já pode enviar o processo à INCM ou de novo à PCM.');
      clicar(botoes('Seguinte')[0]);
      expect(seguinte).toHaveBeenCalledExactlyOnceWith('VALIDADOS');
    });

    it('um VALIDADO_DGT sem revalidacaoPcm não tem «Validar» nem resumo', () => {
      abrir({ estado: 'VALIDADO_DGT', acoesDisponiveis: ['enviar-incm'], informacaoBase: IB_VALIDADA }, estruturaCom({ 'd-dep': 'VALIDADO' }));

      expect(botoes('Validar')).toHaveLength(0);
      expect(texto()).not.toContain('itens validados');
    });
  });

  describe('ficheiros da página das Plantas', () => {
    function blocoPlantas(): HTMLElement | null {
      return fixture.nativeElement.querySelector('section.plantas') as HTMLElement | null;
    }

    function linhasPlantas(): HTMLElement[] {
      return Array.from(fixture.nativeElement.querySelectorAll('section.plantas li')) as HTMLElement[];
    }

    function botaoNaLinha(linha: HTMLElement, rotulo: string): HTMLButtonElement {
      return Array.from(linha.querySelectorAll('button')).find((b) => b.textContent?.trim() === rotulo) as HTMLButtonElement;
    }

    function abrirComPlantas(parcial: Partial<ProcessoDetalhe> = {}, conteudo: EstruturaDocumental = estruturaCom()): void {
      abrir({ exigePlantasEMetadados: true, ...parcial }, conteudo);
    }

    it('mostra o bloco «Plantas» por cima dos documentos, com uma linha por ficheiro carregado', () => {
      abrirComPlantas();

      const bloco = blocoPlantas();
      expect(bloco).not.toBeNull();
      const documentos = fixture.nativeElement.querySelector('app-processo-documentos') as HTMLElement;
      expect(bloco!.compareDocumentPosition(documentos) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
      expect(obterPlantas).toHaveBeenCalledExactlyOnceWith('p1');
      expect(listar).toHaveBeenCalledExactlyOnceWith('p1', {}, 0, 100);

      const linhas = linhasPlantas();
      expect(linhas).toHaveLength(4);
      PLANTAS.forEach((planta, indice) => {
        const linha = linhas[indice];
        expect(linha.textContent).toContain(planta.item);
        expect(linha.textContent).toContain(planta.ficheiro);
        expect(linha.textContent).toMatch(/Carregado em \d{2}\/\d{2}\/2026 \d{2}:\d{2} por Rui Fontes/);
        for (const rotulo of ['Descarregar', 'Validar', 'Rejeitar']) {
          expect(botaoNaLinha(linha, rotulo)).toBeDefined();
        }
      });
    });

    it('«Descarregar» pede a descarga do documento da planta', () => {
      abrirComPlantas();

      clicar(botaoNaLinha(linhasPlantas()[0], 'Descarregar'));

      expect(descarregar).toHaveBeenCalledExactlyOnceWith('p1', 'pl-ord');
    });

    it('a contagem junta os ficheiros das Plantas aos documentos e à Informação Base', () => {
      abrirComPlantas();

      expect(texto()).toContain('0 de 8 itens validados');
      expect(botoes('Validar')).toHaveLength(8);
    });

    it('um documento das Plantas que também viesse num separador não conta duas vezes', () => {
      const conteudo = estruturaCom();
      conteudo.outros = [documentosPlantas()[0]];
      abrirComPlantas({}, conteudo);

      expect(texto()).toContain('0 de 8 itens validados');
    });

    it('«Validar» grava o documento da planta, a linha passa a «Validado» e a contagem sobe', () => {
      gravarValidacaoItens.mockReturnValue(of(respostaComPlantas(['pl-ord'])));
      abrirComPlantas();

      clicar(botaoNaLinha(linhasPlantas()[0], 'Validar'));

      expect(gravarValidacaoItens).toHaveBeenCalledExactlyOnceWith('p1', 3, [
        { tipo: 'DOCUMENTO', itemId: 'pl-ord', decisao: 'VALIDADO' },
      ]);
      expect(linhasPlantas()[0].querySelector('.validado')?.textContent?.trim()).toBe('Validado');
      expect(botaoNaLinha(linhasPlantas()[0], 'Validar')).toBeUndefined();
      expect(texto()).toContain('1 de 8 itens validados');
      expect(alterado).toHaveBeenCalledOnce();
    });

    it('«Rejeitar» abre o modal com o nome do ficheiro; confirmado, a linha passa a «Rejeitado» e aparece «Seguinte»', () => {
      const restantes = [...PLANTAS.slice(1).map((planta) => planta.id), ...ITENS.map((i) => i.itemId)];
      gravarValidacaoItens.mockReturnValue(of(respostaComPlantas(restantes, ['pl-ord'])));
      abrirComPlantas();
      const seguinte = vi.fn();
      fixture.componentInstance.seguinte.subscribe(seguinte);

      clicar(botaoNaLinha(linhasPlantas()[0], 'Rejeitar'));
      expect(fixture.nativeElement.querySelector('[data-campo="nome-item"]').textContent.trim()).toBe('Planta_de_Ordenamento.zip');
      const area = fixture.nativeElement.querySelector('[data-campo="notas"] textarea') as HTMLTextAreaElement;
      area.value = 'Legenda ilegível.';
      area.dispatchEvent(new Event('input'));
      fixture.detectChanges();
      clicar(botoes('Confirmar Rejeição')[0]);

      expect(gravarValidacaoItens).toHaveBeenCalledExactlyOnceWith('p1', 3, [
        { tipo: 'DOCUMENTO', itemId: 'pl-ord', decisao: 'REJEITADO', categoriaId: null, motivoId: null, notas: 'Legenda ilegível.' },
      ]);
      const linha = linhasPlantas()[0];
      expect(linha.querySelector('.rejeitado')?.textContent?.trim()).toBe('✕ Rejeitado');
      expect(linha.classList).toContain('rejeitado-linha');
      expect(texto()).toContain('7 de 8 itens validados');

      clicar(botoes('Seguinte')[0]);
      expect(seguinte).toHaveBeenCalledOnce();
    });

    it('com os itens dos separadores validados e uma planta por validar, não diz que todos estão validados', () => {
      listar.mockReturnValue(of(pagina(documentosPlantas({ 'pl-ord': 'VALIDADO', 'pl-cond': 'VALIDADO', 'pl-txt': 'VALIDADO' }))));
      abrirComPlantas(
        {
          todosItensRevistos: false,
          informacaoBase: [
            { id: 'ib-delib', elemento: 'DELIBERACAO', estadoValidacao: 'VALIDADO' },
            { id: 'ib-texto', elemento: 'TEXTO_ATO', estadoValidacao: 'VALIDADO' },
          ],
        },
        estruturaCom({ 'd-pub': 'VALIDADO', 'd-dep': 'VALIDADO' }),
      );

      expect(texto()).toContain('7 de 8 itens validados');
      expect(texto()).not.toContain('Todos os itens estão validados. Carregue em «Seguinte» para abrir o resumo.');
      expect(botaoNaLinha(linhasPlantas()[3], 'Validar')).toBeDefined();
    });

    it('depois de validar a última planta, o resumo diz que já pode validar o processo', () => {
      const todos = [...PLANTAS.map((planta) => planta.id), ...ITENS.map((i) => i.itemId)];
      gravarValidacaoItens.mockReturnValue(of(respostaComPlantas(todos)));
      abrirComPlantas();

      clicar(botaoNaLinha(linhasPlantas()[3], 'Validar'));

      expect(texto()).toContain('8 de 8 itens validados');
      expect(texto()).toContain('Todos os itens estão validados. Carregue em «Seguinte» para abrir o resumo.');
    });

    it('num processo sem página das Plantas não há bloco nem pedidos, e a contagem é a de sempre', () => {
      abrir({ exigePlantasEMetadados: false });

      expect(blocoPlantas()).toBeNull();
      expect(obterPlantas).not.toHaveBeenCalled();
      expect(listar).not.toHaveBeenCalled();
      expect(texto()).toContain('0 de 4 itens validados');
    });

    it('um item da página das Plantas sem ficheiro não aparece nem conta', () => {
      obterPlantas.mockReturnValue(of(paginaPlantas(['pl-carto'])));
      listar.mockReturnValue(of(pagina(documentosPlantas().slice(0, 3))));
      abrirComPlantas();

      expect(linhasPlantas()).toHaveLength(3);
      expect(blocoPlantas()!.textContent).not.toContain('Cartograma');
      expect(texto()).toContain('0 de 7 itens validados');
    });

    it('sem nenhum ficheiro nas Plantas o bloco não aparece', () => {
      obterPlantas.mockReturnValue(of(paginaPlantas(PLANTAS.map((planta) => planta.id))));
      listar.mockReturnValue(of(pagina([])));
      abrirComPlantas();

      expect(blocoPlantas()).toBeNull();
      expect(texto()).toContain('0 de 4 itens validados');
    });

    it('um 409 ao validar uma planta mostra a mensagem do backend e «Recarregar», que volta a ler as Plantas', () => {
      gravarValidacaoItens.mockReturnValue(
        throwError(() => new ApiError('VERSAO_DESATUALIZADA', 'O processo foi alterado entretanto.', 't1', null)),
      );
      abrirComPlantas();

      clicar(botaoNaLinha(linhasPlantas()[1], 'Validar'));

      expect(texto()).toContain('O processo foi alterado entretanto.');
      expect(botoes('Recarregar')).toHaveLength(1);
      expect(botoes('Validar').every((b) => b.disabled)).toBe(true);

      clicar(botoes('Recarregar')[0]);

      expect(obterPlantas).toHaveBeenCalledTimes(2);
      expect(listar).toHaveBeenCalledTimes(2);
      expect(botoes('Recarregar')).toHaveLength(0);
      expect(gravarValidacaoItens).toHaveBeenCalledOnce();
    });

    it('se a página das Plantas não carrega, o bloco mostra o erro', () => {
      obterPlantas.mockReturnValue(throwError(() => new ApiError('ERRO_INTERNO', 'Serviço indisponível.', 't1', null)));
      abrirComPlantas();

      expect(blocoPlantas()!.textContent).toContain('Serviço indisponível.');
      expect(linhasPlantas()).toHaveLength(0);
    });

    it('é uma região com o título «Plantas» e cada botão tem o nome do ficheiro no nome acessível', () => {
      abrirComPlantas();

      const bloco = blocoPlantas()!;
      const titulo = fixture.nativeElement.querySelector(`#${bloco.getAttribute('aria-labelledby')}`) as HTMLElement;
      expect(titulo.tagName).toBe('H2');
      expect(titulo.textContent?.trim()).toBe('Plantas');
      const linha = linhasPlantas()[0];
      expect(botaoNaLinha(linha, 'Descarregar').getAttribute('aria-label')).toBe('Descarregar Planta_de_Ordenamento.zip');
      expect(botaoNaLinha(linha, 'Validar').getAttribute('aria-label')).toBe('Validar Planta_de_Ordenamento.zip');
      expect(botaoNaLinha(linha, 'Rejeitar').getAttribute('aria-label')).toBe('Rejeitar Planta_de_Ordenamento.zip');
      expect(botaoNaLinha(linha, 'Validar').closest('[aria-live="polite"]')).not.toBeNull();
    });

    it('quem não pode validar vê os ficheiros das Plantas sem «Validar» nem «Rejeitar»', () => {
      abrirComPlantas({ acoesDisponiveis: [] });

      expect(linhasPlantas()).toHaveLength(4);
      expect(botoes('Validar')).toHaveLength(0);
      expect(botaoNaLinha(linhasPlantas()[0], 'Descarregar')).toBeDefined();
    });
  });
});
