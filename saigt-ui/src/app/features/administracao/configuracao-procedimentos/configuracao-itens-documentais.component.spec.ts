import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { describe, expect, it, vi } from 'vitest';
import { ApiError } from '../../../core/models/error-response.model';
import { PageResponse } from '../../../core/models/page-response.model';
import { DinamicaConfig, DocumentoConfig, ProcedimentoConfig, TipoInstrumentoConfig } from '../../../models/configuracao.model';
import { ConfiguracaoService } from '../../../services/configuracao.service';
import { ConfiguracaoItensDocumentaisComponent, SelecaoProcedimento } from './configuracao-itens-documentais.component';

const PDM: TipoInstrumentoConfig = { id: 1, codigo: 'PDM', nome: 'Plano Diretor Municipal', categoria: 'PLANO', ativo: true };
const PU: TipoInstrumentoConfig = { id: 3, codigo: 'PU', nome: 'Plano de Urbanização', categoria: 'PLANO', ativo: true };

function dinamica(id: number, tipo: TipoInstrumentoConfig, nome: string, ativo = true): DinamicaConfig {
  return {
    id,
    tipoInstrumento: { id: tipo.id, codigo: tipo.codigo, nome: tipo.nome },
    tipoDinamica: { id: id + 1000, codigo: nome.toUpperCase(), nome },
    associacaoInstrumentos: 'UM',
    ativo,
    emUso: false,
  };
}

function procedimento(id: number, dinamicaId: number, nome: string, ordem: number, ativo = true): ProcedimentoConfig {
  return {
    id,
    instrumentoDinamicaConfigId: dinamicaId,
    tipoProcedimento: { id: id + 2000, codigo: nome.toUpperCase(), nome },
    ordem,
    obrigatorio: true,
    serieDr: 'SEGUNDA_H',
    tamanhoMaximoProcesso: null,
    repetivel: false,
    atoPontual: false,
    ativo,
    formulario: null,
    emUso: false,
    visivelNaDinamica: false,
  };
}

const DINAMICAS: Record<number, DinamicaConfig[]> = {
  1: [dinamica(101, PDM, 'Alteração'), dinamica(102, PDM, 'Revisão', false)],
  3: [dinamica(301, PU, 'Elaboração')],
};
const PROCEDIMENTOS: Record<number, ProcedimentoConfig[]> = {
  101: [procedimento(501, 101, 'Decisão de início', 1), procedimento(502, 101, 'Discussão pública', 2, false)],
  102: [procedimento(601, 102, 'Decisão de início', 1)],
  301: [procedimento(701, 301, 'Decisão de início', 1)],
};

const DELIBERACAO: DocumentoConfig = {
  id: 11,
  instrumentoProcedimentoConfigId: 501,
  codigo: 'DELIBERACAO_PUB',
  nome: 'Deliberação',
  separador: 'Deliberação',
  descricao: null,
  categoria: 'PUBLICACAO',
  tipoElemento: 'PECA_ESCRITA',
  formato: 'EDITAVEL',
  extensoesAceites: ['pdf', 'docx'],
  obrigatorio: true,
  ordem: 1,
  elementoInformacaoBase: 'DELIBERACAO',
  tamanhoMaximoFicheiro: null,
  tamanhoMaximoTotal: null,
  numeroMaximoFicheiros: null,
  linkPublicoPermitido: true,
  ativo: true,
  regrasTiff: null,
  regraNomenclatura: 'GERAL',
};
const PLANTA: DocumentoConfig = {
  ...DELIBERACAO,
  id: 12,
  codigo: 'PLANTA_DEP',
  nome: 'Planta de ordenamento',
  separador: 'Planta de ordenamento',
  categoria: 'DEPOSITO',
  tipoElemento: 'PECA_GRAFICA',
  formato: 'NAO_EDITAVEL',
  extensoesAceites: ['zip'],
  obrigatorio: false,
  ordem: 2,
  elementoInformacaoBase: null,
  tamanhoMaximoFicheiro: 524288000,
  linkPublicoPermitido: false,
  ativo: false,
  regrasTiff: {
    sistemasReferencia: ['EPSG:3763'],
    compressoesAceites: ['NONE'],
    bandaAlfaTolerada: false,
    modeloCor: null,
    bitsPorBanda: null,
    resolucaoDpi: null,
    elementosLegenda: [],
  },
  regraNomenclatura: 'SGIFR',
};

function pagina<T>(linhas: T[]): PageResponse<T> {
  return { content: linhas, page: 0, size: 20, totalElements: linhas.length, totalPages: 1 };
}

describe('ConfiguracaoItensDocumentaisComponent', () => {
  let fixture: ComponentFixture<ConfiguracaoItensDocumentaisComponent>;
  let servico: Record<string, ReturnType<typeof vi.fn>>;

  function configurar(selecao: SelecaoProcedimento | null = null): void {
    servico = {
      listarTiposInstrumento: vi.fn().mockReturnValue(of(pagina([PU, PDM]))),
      listarDinamicasConfig: vi.fn((filtro: { tipoInstrumentoId: number }) => of(pagina(DINAMICAS[filtro.tipoInstrumentoId] ?? []))),
      listarProcedimentosConfig: vi.fn((filtro: { instrumentoDinamicaConfigId: number }) =>
        of(pagina(PROCEDIMENTOS[filtro.instrumentoDinamicaConfigId] ?? [])),
      ),
      listarDocumentoConfig: vi.fn().mockReturnValue(of(pagina([DELIBERACAO, PLANTA]))),
      criarDocumentoConfig: vi.fn(),
      atualizarDocumentoConfig: vi.fn(),
    };
    TestBed.configureTestingModule({
      imports: [ConfiguracaoItensDocumentaisComponent],
      providers: [{ provide: ConfiguracaoService, useValue: servico }],
    });
    fixture = TestBed.createComponent(ConfiguracaoItensDocumentaisComponent);
    fixture.componentRef.setInput('selecaoInicial', selecao);
    fixture.detectChanges();
  }

  const el = (): HTMLElement => fixture.nativeElement as HTMLElement;
  const dialogo = (): HTMLElement | null => document.querySelector('[role="dialog"]');
  const botao = (texto: string, raiz: ParentNode = el()): HTMLButtonElement =>
    [...raiz.querySelectorAll<HTMLButtonElement>('button')].find((b) => b.textContent?.trim() === texto)!;
  const campo = <T extends HTMLElement>(rotulo: string, raiz: ParentNode = el()): T => {
    const label = [...raiz.querySelectorAll('label')].find((l) => l.textContent?.trim().startsWith(rotulo))!;
    return (label.htmlFor ? document.getElementById(label.htmlFor) : label.querySelector('input')) as T;
  };
  const opcoes = (seletor: HTMLSelectElement): string[] => [...seletor.options].map((o) => o.textContent?.trim() ?? '').slice(1);
  const celula = (linha: number, coluna: string): string =>
    el().querySelectorAll('tbody tr')[linha].querySelector(`.coluna-${coluna}`)?.textContent?.trim() ?? '';

  function clicar(alvo: HTMLElement): void {
    alvo.click();
    fixture.detectChanges();
  }

  function escolher(entrada: HTMLSelectElement, valor: string): void {
    entrada.value = valor;
    entrada.dispatchEvent(new Event('change'));
    fixture.detectChanges();
  }

  function escrever(entrada: HTMLInputElement, valor: string): void {
    entrada.value = valor;
    entrada.dispatchEvent(new Event('input'));
    fixture.detectChanges();
  }

  function abrirMenu(linha: number, acao: string): void {
    clicar(el().querySelectorAll<HTMLButtonElement>('.btn-acoes')[linha]);
    clicar(botao(acao));
  }

  it('abre no PDM, na primeira Dinâmica e no primeiro Procedimento e lista os itens com os valores por extenso', () => {
    configurar();

    expect(campo<HTMLSelectElement>('Tipo de Instrumento').value).toBe('1');
    expect(campo<HTMLSelectElement>('Dinâmica').value).toBe('101');
    expect(opcoes(campo('Dinâmica'))).toEqual(['Alteração', 'Revisão (inativa)']);
    expect(campo<HTMLSelectElement>('Procedimento').value).toBe('501');
    expect(opcoes(campo('Procedimento'))).toEqual(['Decisão de início', 'Discussão pública (inativo)']);
    expect(servico['listarProcedimentosConfig']).toHaveBeenCalledWith({ instrumentoDinamicaConfigId: 101 }, 0, 100);
    expect(servico['listarDocumentoConfig']).toHaveBeenCalledWith({ instrumentoProcedimentoConfigId: 501, ativo: undefined }, 0, 20);
    expect([...el().querySelectorAll('th[scope="col"]')].map((th) => th.textContent?.trim())).toEqual([
      'Ordem',
      'Separador',
      'Item',
      'Caixa',
      'Tipo',
      'Formato',
      'Extensões',
      'Obrigatório',
      'Informação Base',
      'Estado',
      'Ações',
    ]);
    expect(celula(0, 'ordem')).toBe('1');
    expect(celula(0, 'item')).toBe('Deliberação');
    expect(celula(0, 'caixa')).toBe('Publicação');
    expect(celula(0, 'tipo')).toBe('Peça escrita');
    expect(celula(0, 'formato')).toBe('Editável');
    expect(celula(0, 'extensoes')).toBe('pdf, docx');
    expect(celula(0, 'obrigatorio')).toBe('Sim');
    expect(celula(0, 'informacaoBase')).toBe('Deliberação');
    expect(celula(0, 'estado')).toBe('Ativo');
    expect(celula(1, 'caixa')).toBe('Depósito');
    expect(celula(1, 'tipo')).toBe('Peça gráfica');
    expect(celula(1, 'formato')).toBe('Não editável');
    expect(celula(1, 'obrigatorio')).toBe('Não');
    expect(celula(1, 'informacaoBase')).toBe('—');
    expect(celula(1, 'estado')).toBe('Inativo');
    expect(el().querySelector('.contador')?.textContent).toContain('2 itens');
    expect(el().querySelector('caption')?.textContent).toContain('Itens documentais de PDM — Alteração — Decisão de início');
  });

  it('mudar o tipo limpa a Dinâmica e o Procedimento e escolhe os primeiros; mudar a Dinâmica escolhe o primeiro Procedimento', () => {
    configurar();
    escolher(campo('Tipo de Instrumento'), '3');

    expect(campo<HTMLSelectElement>('Dinâmica').value).toBe('301');
    expect(campo<HTMLSelectElement>('Procedimento').value).toBe('701');
    expect(servico['listarDocumentoConfig']).toHaveBeenLastCalledWith({ instrumentoProcedimentoConfigId: 701, ativo: undefined }, 0, 20);

    escolher(campo('Tipo de Instrumento'), '1');
    escolher(campo('Dinâmica'), '102');
    expect(campo<HTMLSelectElement>('Procedimento').value).toBe('601');
    expect(servico['listarDocumentoConfig']).toHaveBeenLastCalledWith({ instrumentoProcedimentoConfigId: 601, ativo: undefined }, 0, 20);

    escolher(campo('Procedimento'), '601');
    expect(servico['listarDocumentoConfig']).toHaveBeenLastCalledWith({ instrumentoProcedimentoConfigId: 601, ativo: undefined }, 0, 20);
  });

  it('a entrada pela ação dos Procedimentos abre com o tipo, a Dinâmica e o Procedimento escolhidos', () => {
    configurar({ tipoInstrumentoId: 1, dinamicaConfigId: 101, procedimentoConfigId: 502 });

    expect(campo<HTMLSelectElement>('Tipo de Instrumento').value).toBe('1');
    expect(campo<HTMLSelectElement>('Dinâmica').value).toBe('101');
    expect(campo<HTMLSelectElement>('Procedimento').value).toBe('502');
    expect(servico['listarDocumentoConfig']).toHaveBeenLastCalledWith({ instrumentoProcedimentoConfigId: 502, ativo: undefined }, 0, 20);

    escolher(campo('Tipo de Instrumento'), '3');
    escolher(campo('Tipo de Instrumento'), '1');
    expect(campo<HTMLSelectElement>('Procedimento').value).toBe('501');
  });

  it('filtros de estado: Ativos e Inativos listam na página 0 com ativo; o vazio tem a mensagem do Procedimento', () => {
    configurar();
    servico['listarDocumentoConfig'].mockReturnValue(of(pagina([])));
    clicar(botao('Inativos'));

    expect(servico['listarDocumentoConfig']).toHaveBeenLastCalledWith({ instrumentoProcedimentoConfigId: 501, ativo: false }, 0, 20);
    expect(botao('Inativos').getAttribute('aria-pressed')).toBe('true');
    expect(el().textContent).toContain('Este Procedimento ainda não tem itens documentais.');

    clicar(botao('Ativos'));
    expect(servico['listarDocumentoConfig']).toHaveBeenLastCalledWith({ instrumentoProcedimentoConfigId: 501, ativo: true }, 0, 20);
  });

  it('Desativar pede a confirmação, faz PUT com ativo=false sem regrasTiff e anuncia o sucesso; o selo diz Inativo', () => {
    configurar();
    servico['atualizarDocumentoConfig'].mockReturnValue(of({ ...DELIBERACAO, ativo: false }));

    abrirMenu(0, 'Desativar');
    expect(dialogo()?.textContent).toContain(
      'Desativar o item Deliberação? Deixa de ser pedido, também nos processos em curso deste Procedimento; os ficheiros já carregados mantêm-se.',
    );
    servico['listarDocumentoConfig'].mockReturnValue(of(pagina([{ ...DELIBERACAO, ativo: false }, PLANTA])));
    clicar(botao('Sim', dialogo()!));

    const [id, pedido] = servico['atualizarDocumentoConfig'].mock.lastCall!;
    expect(id).toBe(11);
    expect(pedido).toEqual({
      nome: 'Deliberação',
      separador: 'Deliberação',
      descricao: null,
      categoria: 'PUBLICACAO',
      tipoElemento: 'PECA_ESCRITA',
      formato: 'EDITAVEL',
      extensoesAceites: ['pdf', 'docx'],
      obrigatorio: true,
      ordem: 1,
      elementoInformacaoBase: 'DELIBERACAO',
      tamanhoMaximoFicheiro: null,
      tamanhoMaximoTotal: null,
      numeroMaximoFicheiros: null,
      linkPublicoPermitido: true,
      ativo: false,
      regraNomenclatura: 'GERAL',
    });
    expect(el().querySelector('app-alerta[role="status"]')?.textContent).toContain('Item documental desativado.');
    expect(celula(0, 'estado')).toBe('Inativo');

    abrirMenu(1, 'Reativar');
    expect(dialogo()?.textContent).toContain('Reativar o item Planta de ordenamento?');
    clicar(botao('Sim', dialogo()!));
    expect(servico['atualizarDocumentoConfig']).toHaveBeenLastCalledWith(12, expect.objectContaining({ ativo: true, regraNomenclatura: 'SGIFR' }));
  });

  it('Adicionar item: o POST leva o Procedimento e o código; a tabela recarrega e o sucesso é anunciado', () => {
    configurar();
    servico['criarDocumentoConfig'].mockReturnValue(of(DELIBERACAO));
    clicar(botao('Adicionar item'));
    const modal = dialogo()!;
    expect(modal.querySelector('h2')?.textContent).toContain('Novo item documental');
    expect(modal.textContent).toContain('PDM — Alteração — Decisão de início');

    escrever(campo('Código', modal), 'RELATORIO_PONDERACAO');
    escrever(campo('Nome', modal), 'Relatório de Ponderação');
    escrever(campo('Separador', modal), 'Relatório de Ponderação');
    escolher(campo('Tipo de elemento', modal), 'PECA_ESCRITA');
    escolher(campo('Caixa', modal), 'PUBLICACAO');
    escrever(campo('Extensões aceites', modal), 'pdf');
    escrever(campo('Ordem', modal), '9');
    clicar(botao('Guardar', modal));

    expect(servico['criarDocumentoConfig']).toHaveBeenCalledWith(
      expect.objectContaining({ instrumentoProcedimentoConfigId: 501, codigo: 'RELATORIO_PONDERACAO', formato: 'EDITAVEL', ordem: 9 }),
    );
    expect(dialogo()).toBeNull();
    expect(el().querySelector('app-alerta[role="status"]')?.textContent).toContain('Item documental criado.');
  });

  it('Editar abre o modal do item; 409 EM_USO fica no topo do modal, que continua aberto com os valores', () => {
    configurar();
    servico['atualizarDocumentoConfig'].mockReturnValue(
      throwError(() => new ApiError('EM_USO', 'O registo já está a ser usado e não pode mudar.', 't', null)),
    );
    abrirMenu(1, 'Editar');
    const modal = dialogo()!;
    expect(modal.querySelector('h2')?.textContent).toContain('Editar Planta de ordenamento');

    escolher(campo('Caixa', modal), 'PUBLICACAO');
    clicar(botao('Guardar', modal));

    expect(servico['atualizarDocumentoConfig']).toHaveBeenCalledWith(12, expect.objectContaining({ categoria: 'PUBLICACAO' }));
    expect(dialogo()?.querySelector('app-alerta[role="alert"]')?.textContent).toContain('O registo já está a ser usado e não pode mudar.');
    expect(campo<HTMLSelectElement>('Caixa', dialogo()!).value).toBe('PUBLICACAO');
  });

  it('menu de ações: nome acessível, aria-expanded e Editar e Desativar alcançáveis por botão', () => {
    configurar();
    const botaoMenu = el().querySelectorAll<HTMLButtonElement>('.btn-acoes')[0];
    expect(botaoMenu.getAttribute('aria-label')).toBe('Mais opções para Deliberação (Publicação)');
    clicar(botaoMenu);
    expect(botaoMenu.getAttribute('aria-expanded')).toBe('true');
    expect([...el().querySelectorAll('.dropdown-menu button')].map((b) => b.textContent?.trim())).toEqual(['Editar', 'Desativar']);
  });

  it('itens com o mesmo nome na Publicação e no Depósito têm botões de ações com nomes diferentes', () => {
    configurar();
    servico['listarDocumentoConfig'].mockReturnValue(of(pagina([DELIBERACAO, { ...DELIBERACAO, id: 13, codigo: 'DELIBERACAO_DEP', categoria: 'DEPOSITO' }])));
    clicar(botao('Ativos'));

    expect([...el().querySelectorAll('.btn-acoes')].map((b) => b.getAttribute('aria-label'))).toEqual([
      'Mais opções para Deliberação (Publicação)',
      'Mais opções para Deliberação (Depósito)',
    ]);
    expect(el().querySelector('.contador')?.textContent).toContain('2 itens');
  });

  it('desativar o único item da última página com o filtro Ativos volta à página anterior; o contador diz «1 item»', () => {
    configurar();
    const comTotal = (linhas: DocumentoConfig[], pagina: number, total: number): PageResponse<DocumentoConfig> => ({
      content: linhas,
      page: pagina,
      size: 20,
      totalElements: total,
      totalPages: Math.ceil(total / 20),
    });
    servico['listarDocumentoConfig'].mockReturnValue(of(comTotal([DELIBERACAO], 1, 21)));
    clicar(botao('Ativos'));
    fixture.componentInstance.irParaPagina(1);
    fixture.detectChanges();
    expect(servico['listarDocumentoConfig']).toHaveBeenLastCalledWith({ instrumentoProcedimentoConfigId: 501, ativo: true }, 1, 20);

    servico['atualizarDocumentoConfig'].mockReturnValue(of({ ...DELIBERACAO, ativo: false }));
    servico['listarDocumentoConfig'].mockImplementation((_filtro: unknown, pagina: number) =>
      of(pagina === 1 ? comTotal([], 1, 20) : comTotal([PLANTA], 0, 20)),
    );
    abrirMenu(0, 'Desativar');
    clicar(botao('Sim', dialogo()!));

    expect(servico['listarDocumentoConfig']).toHaveBeenLastCalledWith({ instrumentoProcedimentoConfigId: 501, ativo: true }, 0, 20);
    expect(fixture.componentInstance.pagina()).toBe(0);
    expect(celula(0, 'item')).toBe('Planta de ordenamento');

    servico['listarDocumentoConfig'].mockReturnValue(of(pagina([PLANTA])));
    clicar(botao('Todos'));
    expect(el().querySelector('.contador')?.textContent?.trim()).toBe('1 item');
  });

  it('403 ao gravar fecha o modal e mostra o banner de ação do separador', () => {
    configurar();
    servico['atualizarDocumentoConfig'].mockReturnValue(
      throwError(() => new ApiError('SEM_PERMISSAO', 'Não tem permissão para esta operação.', 't', null)),
    );
    abrirMenu(0, 'Editar');
    clicar(botao('Guardar', dialogo()!));

    expect(dialogo()).toBeNull();
    expect(el().querySelector('app-error-banner .error-banner')).not.toBeNull();
  });

  it('falha ao listar mostra o app-error-banner', () => {
    configurar();
    servico['listarDocumentoConfig'].mockReturnValue(throwError(() => new ApiError('ERRO_REDE', 'Sem ligação.', 't', null)));
    clicar(botao('Ativos'));
    expect(el().querySelector('app-error-banner .error-banner')).not.toBeNull();
  });
});
