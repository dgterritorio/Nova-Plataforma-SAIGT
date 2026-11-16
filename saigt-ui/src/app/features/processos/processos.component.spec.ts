import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { Observable, Subject, of, throwError } from 'rxjs';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ProcessosComponent } from './processos.component';
import { AuthService } from '../../core/auth/auth.service';
import { ApiError } from '../../core/models/error-response.model';
import { PageResponse } from '../../core/models/page-response.model';
import { OpcaoLista, ProcessoListagem } from '../../models/processo.model';
import { ListaValoresService } from '../../services/lista-valores.service';
import { ExportacaoProcessos, ProcessoService } from '../../services/processo.service';

describe('ProcessosComponent', () => {
  let fixture: ComponentFixture<ProcessosComponent>;
  let processoServiceMock: { listar: ReturnType<typeof vi.fn>; exportar: ReturnType<typeof vi.fn> };
  let opcoesPorTipo: Partial<Record<string, OpcaoLista[]>>;
  let opcoes: ReturnType<typeof vi.fn>;
  let perfilInterno: string | null;
  let ator: string | null;

  function paginaVazia(totalPages = 1): PageResponse<ProcessoListagem> {
    return { content: [], page: 0, size: 20, totalElements: 0, totalPages };
  }

  function linha(parcial: Partial<ProcessoListagem>): ProcessoListagem {
    return {
      id: 'p1',
      numeroProcesso: '1042',
      designacaoInstrumento: 'PDM de Sintra',
      numeroInstrumento: '1',
      tipoInstrumento: 'PDM',
      tipoDinamica: 'ALTERACAO',
      nomeDinamica: 'Alteração',
      tipoProcedimento: 'DECISAO_INICIO',
      nomeProcedimento: 'Decisão de início',
      entidadeNif: '500051062',
      entidadeNome: 'Câmara Municipal de Sintra',
      estado: 'EM_VALIDACAO_DGT',
      atualizadoEm: '2026-10-20T09:30:00Z',
      autorNome: 'Helena Marques',
      concelhos: [],
      ...parcial,
    };
  }

  function celulaConcelhoDaPrimeiraLinha(processo: ProcessoListagem): HTMLElement {
    ligacaoDaPrimeiraLinha(processo);
    return fixture.nativeElement.querySelectorAll('tbody tr:first-child td')[5];
  }

  function ligacaoDaPrimeiraLinha(processo: ProcessoListagem): string {
    processoServiceMock.listar.mockReturnValue(
      of({ content: [processo], page: 0, size: 20, totalElements: 1, totalPages: 1 }),
    );
    fixture = TestBed.createComponent(ProcessosComponent);
    fixture.detectChanges();
    return fixture.nativeElement.querySelector('tbody a.ligacao-processo').getAttribute('href');
  }

  beforeEach(() => {
    ator = 'DGT';
    perfilInterno = 'MASTER';
    opcoesPorTipo = {};
    opcoes = vi.fn((tipo: string) => of(opcoesPorTipo[tipo] ?? []));
    processoServiceMock = { listar: vi.fn().mockReturnValue(of(paginaVazia())), exportar: vi.fn() };

    TestBed.configureTestingModule({
      imports: [ProcessosComponent],
      providers: [
        provideRouter([]),
        { provide: ProcessoService, useValue: processoServiceMock },
        { provide: ListaValoresService, useValue: { opcoes } },
        {
          provide: AuthService,
          useValue: {
            nome: () => 'Ana Teixeira',
            origem: () => 'AD_DGT',
            entidade: () => null,
            perfilInterno: () => perfilInterno,
            ator: () => ator,
          },
        },
      ],
    });

    fixture = TestBed.createComponent(ProcessosComponent);
    fixture.detectChanges();
  });

  it('a DGT abre um processo em EM_VALIDACAO_DGT na vista de validação', () => {
    expect(ligacaoDaPrimeiraLinha(linha({ id: 'p7', estado: 'EM_VALIDACAO_DGT' }))).toBe('/processos/p7/validacao');
  });

  it('a DGT abre um processo VALIDADO_DGT na vista de validação, onde está o Resumo Validados', () => {
    expect(ligacaoDaPrimeiraLinha(linha({ id: 'p7', estado: 'VALIDADO_DGT' }))).toBe('/processos/p7/validacao');
  });

  it('a DGT abre um processo noutro estado no detalhe', () => {
    expect(ligacaoDaPrimeiraLinha(linha({ id: 'p7', estado: 'ENVIADO_DGT' }))).toBe('/processos/p7');
  });

  it.each([['ENTIDADE'], [null]])('quem não é DGT (ator %s) abre um processo em validação no detalhe', (outroAtor) => {
    ator = outroAtor;

    expect(ligacaoDaPrimeiraLinha(linha({ id: 'p7', estado: 'EM_VALIDACAO_DGT' }))).toBe('/processos/p7');
  });

  describe('coluna Concelho', () => {
    it('mostra o concelho de um PDM de Sintra com a designação do SNIT formatada', () => {
      const celula = celulaConcelhoDaPrimeiraLinha(linha({ concelhos: ['SINTRA'] }));

      expect(celula.textContent?.trim()).toBe('Sintra');
      expect(celula.querySelector('[data-campo="concelhos"]')?.getAttribute('title')).toBe('Sintra');
    });

    it('com cinco concelhos mostra três e «+2», com os cinco no title e para os leitores de ecrã', () => {
      const celula = celulaConcelhoDaPrimeiraLinha(
        linha({ concelhos: ['CASCAIS', 'LISBOA', 'LOURES', 'OEIRAS', 'VILA NOVA DE GAIA'] }),
      );
      const todos = 'Cascais, Lisboa, Loures, Oeiras, Vila Nova de Gaia';

      expect(celula.querySelector('[aria-hidden="true"]')?.textContent?.replace(/\s+/g, ' ').trim()).toBe(
        'Cascais, Lisboa, Loures +2',
      );
      expect(celula.querySelector('[data-campo="concelhos"]')?.getAttribute('title')).toBe(todos);
      expect(celula.querySelector('.visualmente-oculto')?.textContent?.trim()).toBe(todos);
    });

    it('sem concelhos (processo sem IGT) mostra «—»', () => {
      const celula = celulaConcelhoDaPrimeiraLinha(linha({ instrumentoId: undefined, concelhos: [] }));

      expect(celula.textContent?.trim()).toBe('—');
      expect(celula.querySelector('[data-campo="concelhos"]')).toBeNull();
    });
  });

  it('pede a primeira página sem filtros ao iniciar', () => {
    expect(processoServiceMock.listar).toHaveBeenCalledWith({}, 0, 20);
  });

  it('mostra o estado vazio real quando a primeira página sem filtros vem vazia — nunca processos fabricados', () => {
    const linhas = fixture.nativeElement.querySelectorAll('tbody tr');
    expect(linhas).toHaveLength(1);
    expect(fixture.nativeElement.textContent).toContain('Sem processos para os filtros indicados.');
    expect(fixture.nativeElement.textContent).not.toContain('PDM-2026-014');
  });

  it('mostra o estado vazio real quando a página vazia é o resultado de um filtro', () => {
    processoServiceMock.listar.mockReturnValue(of(paginaVazia()));
    fixture.componentInstance.form.controls.estado.setValue('ANULADO');
    fixture.componentInstance.pesquisar();
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Sem processos para os filtros indicados.');
    expect(fixture.nativeElement.textContent).not.toContain('PDM-2026-014');
  });

  it('mostra o estado vazio real numa página seguinte vazia', () => {
    processoServiceMock.listar.mockReturnValue(of(paginaVazia(2)));
    fixture.componentInstance.irParaPagina(1);
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Sem processos para os filtros indicados.');
    expect(fixture.nativeElement.textContent).not.toContain('PDM-2026-014');
  });

  it('volta a pedir a listagem com o filtro de estado quando o formulário é submetido', () => {
    fixture.componentInstance.form.controls.estado.setValue('ENVIADO_DGT');
    fixture.componentInstance.pesquisar();

    expect(processoServiceMock.listar).toHaveBeenLastCalledWith({ estado: 'ENVIADO_DGT' }, 0, 20);
  });

  describe('filtro por motivo da alteração', () => {
    function reabrir(): void {
      fixture.destroy();
      opcoes.mockClear();
      fixture = TestBed.createComponent(ProcessosComponent);
      fixture.detectChanges();
      fixture.componentInstance.filtrosAbertos.set(true);
      fixture.detectChanges();
    }

    function seletorMotivo(): HTMLSelectElement | null {
      return fixture.nativeElement.querySelector('select[aria-label="Motivo da alteração"]');
    }

    it('um perfil interno com opções vê os motivos agrupados por Lei, Programa e Plano e o motivo vai no pedido', () => {
      opcoesPorTipo = {
        MOTIVO_ALTERACAO_LEI: [{ codigo: 'L1', descricao: 'Lei de Bases' }],
        MOTIVO_ALTERACAO_PROGRAMA: [{ codigo: 'PNEC', descricao: 'Programa Nacional Energia e Clima' }],
      };
      reabrir();

      expect(opcoes.mock.calls.map(([tipo]) => tipo)).toEqual([
        'MOTIVO_ALTERACAO_LEI',
        'MOTIVO_ALTERACAO_PROGRAMA',
        'MOTIVO_ALTERACAO_PLANO',
      ]);
      const select = seletorMotivo()!;
      expect([...select.querySelectorAll('optgroup')].map((grupo) => grupo.label)).toEqual(['Lei', 'Programa']);
      expect([...select.options].map((opcao) => opcao.value)).toEqual([
        '',
        'MOTIVO_ALTERACAO_LEI:L1',
        'MOTIVO_ALTERACAO_PROGRAMA:PNEC',
      ]);

      processoServiceMock.listar.mockReturnValue(
        of({ content: [linha({ id: 'p9' })], page: 0, size: 20, totalElements: 1, totalPages: 1 }),
      );
      select.value = 'MOTIVO_ALTERACAO_PROGRAMA:PNEC';
      select.dispatchEvent(new Event('change'));
      fixture.detectChanges();

      expect(processoServiceMock.listar).toHaveBeenLastCalledWith(
        { motivoAlteracao: 'MOTIVO_ALTERACAO_PROGRAMA:PNEC' },
        0,
        20,
      );
      expect(fixture.componentInstance.totalElementos()).toBe(1);

      fixture.componentInstance.limparTudo();
      expect(processoServiceMock.listar).toHaveBeenLastCalledWith({}, 0, 20);
    });

    it('um perfil interno sem opções nas três listas não vê o filtro', () => {
      reabrir();

      expect(opcoes).toHaveBeenCalledTimes(3);
      expect(seletorMotivo()).toBeNull();
    });

    it('um utilizador de entidade não vê o filtro nem pede as opções', () => {
      perfilInterno = null;
      opcoesPorTipo = { MOTIVO_ALTERACAO_LEI: [{ codigo: 'L1', descricao: 'Lei de Bases' }] };
      reabrir();

      expect(opcoes).not.toHaveBeenCalled();
      expect(seletorMotivo()).toBeNull();
    });
  });

  describe('filtro «Situação PCM»', () => {
    function reabrir(): void {
      fixture.destroy();
      fixture = TestBed.createComponent(ProcessosComponent);
      fixture.detectChanges();
      fixture.componentInstance.filtrosAbertos.set(true);
      fixture.detectChanges();
    }

    function seletor(): HTMLSelectElement | null {
      return fixture.nativeElement.querySelector('#filtro-situacao-pcm');
    }

    it('um MASTER escolhe «Devolvidos pela PCM», o pedido leva situacaoPcm=DEVOLVIDO e a linha tem o selo', () => {
      reabrir();
      const select = seletor()!;
      const rotulo = fixture.nativeElement.querySelector('label[for="filtro-situacao-pcm"]') as HTMLLabelElement;
      expect(rotulo.textContent).toContain('Situação PCM');
      expect([...select.options].map((opcao) => [opcao.value, opcao.textContent?.trim()])).toEqual([
        ['', 'Todas'],
        ['COM_PCM', 'Com a PCM'],
        ['DEVOLVIDO', 'Devolvidos pela PCM'],
        ['SEM_PCM', 'Sem passagem pela PCM'],
      ]);

      processoServiceMock.listar.mockReturnValue(
        of({
          content: [linha({ id: 'pc', estado: 'VALIDADO_DGT', situacaoPcm: 'DEVOLVIDO', pcmDevolvidoEm: '2026-11-14T10:00:00Z' })],
          page: 0,
          size: 20,
          totalElements: 1,
          totalPages: 1,
        }),
      );
      select.value = 'DEVOLVIDO';
      select.dispatchEvent(new Event('change'));
      fixture.detectChanges();

      expect(processoServiceMock.listar).toHaveBeenLastCalledWith({ situacaoPcm: 'DEVOLVIDO' }, 0, 20);
      const linhas = fixture.nativeElement.querySelectorAll('tbody tr');
      expect(linhas).toHaveLength(1);
      expect(linhas[0].querySelector('app-selo[data-campo="devolvido-pcm"]')?.textContent?.trim()).toBe('Devolvido pela PCM');

      fixture.componentInstance.limparTudo();
      expect(processoServiceMock.listar).toHaveBeenLastCalledWith({}, 0, 20);
    });

    it('sem a situação DEVOLVIDO, ou fora de VALIDADO_DGT, não há selo', () => {
      processoServiceMock.listar.mockReturnValue(
        of({
          content: [
            linha({ id: 'pa', estado: 'VALIDADO_DGT', situacaoPcm: 'SEM_PCM' }),
            linha({ id: 'pb', estado: 'VALIDADO_DGT', situacaoPcm: 'COM_PCM' }),
            linha({ id: 'pd', estado: 'EM_VALIDACAO_INCM', situacaoPcm: 'DEVOLVIDO' }),
          ],
          page: 0,
          size: 20,
          totalElements: 3,
          totalPages: 1,
        }),
      );
      reabrir();

      expect(fixture.nativeElement.querySelectorAll('tbody tr')).toHaveLength(3);
      expect(fixture.nativeElement.querySelector('app-selo[data-campo="devolvido-pcm"]')).toBeNull();
    });

    it('um utilizador de entidade não vê o filtro', () => {
      perfilInterno = null;
      ator = 'ENTIDADE';
      reabrir();

      expect(seletor()).toBeNull();
    });
  });

  it('repõe a página a 0 ao aplicar um novo filtro', () => {
    fixture.componentInstance.irParaPagina(2);
    expect(processoServiceMock.listar).toHaveBeenLastCalledWith({}, 2, 20);

    fixture.componentInstance.pesquisar();
    expect(processoServiceMock.listar).toHaveBeenLastCalledWith({}, 0, 20);
  });

  it('pede a página seguinte quando se navega a paginação', () => {
    fixture.componentInstance.irParaPagina(1);
    expect(processoServiceMock.listar).toHaveBeenLastCalledWith({}, 1, 20);
    expect(fixture.componentInstance.pagina()).toBe(1);
  });

  it('não inclui campos de filtro vazios no pedido', () => {
    fixture.componentInstance.form.controls.designacaoInstrumento.setValue('PDM-2026-014');
    fixture.componentInstance.pesquisar();

    expect(processoServiceMock.listar).toHaveBeenLastCalledWith({ designacaoInstrumento: 'PDM-2026-014' }, 0, 20);
  });

  it('pesquisa automaticamente pela Referência ao escrever, sem precisar de clicar em "Filtrar"', () => {
    vi.useFakeTimers();
    try {
      processoServiceMock.listar.mockClear();

      fixture.componentInstance.form.controls.designacaoInstrumento.setValue('Cascais');
      expect(processoServiceMock.listar).not.toHaveBeenCalled();

      vi.advanceTimersByTime(400);

      expect(processoServiceMock.listar).toHaveBeenLastCalledWith({ designacaoInstrumento: 'Cascais' }, 0, 20);
    } finally {
      vi.useRealTimers();
    }
  });

  it('mostra só Referência e Estado no painel de filtros', () => {
    fixture.componentInstance.filtrosAbertos.set(true);
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[formcontrolname="entidadeNif"]')).toBeNull();
    expect(fixture.nativeElement.querySelector('[formcontrolname="dataDe"]')).toBeNull();
    expect(fixture.nativeElement.querySelector('[formcontrolname="dataAte"]')).toBeNull();
    expect(fixture.nativeElement.querySelector('[formcontrolname="estado"]')?.tagName).toBe('SELECT');
  });

  it('lista todos os estados conhecidos como opções do select Estado', () => {
    fixture.componentInstance.filtrosAbertos.set(true);
    fixture.detectChanges();

    const select: HTMLSelectElement = fixture.nativeElement.querySelector('[formcontrolname="estado"]');
    const opcoes = [...select.options].map((opcao) => opcao.value).filter((valor) => valor);
    expect(opcoes).toEqual(fixture.componentInstance.estadosProcesso);
  });

  it('mostra a designação e o número do instrumento, com a designação como link', () => {
    processoServiceMock.listar.mockReturnValue(
      of({
        content: [
          {
            id: 'p1',
            instrumentoId: 'i1',
            designacaoInstrumento: 'PDM-2025-001',
            numeroInstrumento: '1',
            tipoInstrumento: 'PDM',
            tipoDinamica: 'ELABORACAO',
            nomeDinamica: 'Elaboração',
            tipoProcedimento: 'DECISAO',
            nomeProcedimento: 'Decisão',
            entidadeNif: '501234567',
            entidadeNome: 'Câmara Municipal de Lisboa',
            estado: 'ENVIADO_DGT',
            atualizadoEm: '2026-08-01T10:00:00Z',
            autorNome: 'Ana Ferreira',
          },
        ],
        page: 0,
        size: 20,
        totalElements: 1,
        totalPages: 1,
      }),
    );
    fixture.componentInstance.pesquisar();
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('td.coluna-designacaoInstrumento .designacao-instrumento')?.textContent?.trim()).toBe('PDM-2025-001');
    expect(fixture.nativeElement.querySelector('td.coluna-designacaoInstrumento a')).toBeNull();
    expect(fixture.nativeElement.querySelector('td.coluna-designacaoInstrumento .numero-instrumento')?.textContent?.trim()).toBe('n.º 1');
  });

  it('numa Dinâmica iniciada sem IGT mostra o descritivo sem o «n.º» vazio', () => {
    processoServiceMock.listar.mockReturnValue(
      of({
        content: [
          {
            id: 'p1',
            designacaoInstrumento: 'MP Sintra',
            numeroInstrumento: null,
            tipoInstrumento: 'PDM',
            tipoDinamica: 'MEDIDAS_PREVENTIVAS',
            nomeDinamica: 'Medidas Preventivas (iniciativa do município)',
            tipoProcedimento: 'PUBLICACAO',
            nomeProcedimento: 'Publicação',
            entidadeNif: '500051062',
            entidadeNome: 'Câmara Municipal de Sintra',
            estado: 'EM_SUBMISSAO',
            atualizadoEm: '2026-11-12T10:00:00Z',
            autorNome: 'Helena Sintra',
          },
        ],
        page: 0,
        size: 20,
        totalElements: 1,
        totalPages: 1,
      }),
    );
    fixture.componentInstance.pesquisar();
    fixture.detectChanges();

    const celula = fixture.nativeElement.querySelector('td.coluna-designacaoInstrumento') as HTMLElement;
    expect(celula.textContent?.trim()).toBe('MP Sintra');
    expect(celula.querySelector('.numero-instrumento')).toBeNull();
  });

  function carregarDoisProcessos(): void {
    processoServiceMock.listar.mockReturnValue(
      of({
        content: [
          {
            id: 'p1',
            designacaoInstrumento: 'PDM-2025-001',
            numeroInstrumento: '1',
            tipoInstrumento: 'PDM',
            tipoDinamica: 'ELABORACAO',
            nomeDinamica: 'Elaboração',
            tipoProcedimento: 'DECISAO',
            nomeProcedimento: 'Decisão',
            entidadeNif: '501234567',
            entidadeNome: 'Câmara Municipal de Lisboa',
            estado: 'VALIDADO_DGT',
            atualizadoEm: '2026-08-01T10:00:00Z',
            autorNome: 'Maria Santos',
          },
          {
            id: 'p2',
            designacaoInstrumento: 'PP-2025-002',
            numeroInstrumento: '1',
            tipoInstrumento: 'PP',
            tipoDinamica: 'ALTERACAO',
            nomeDinamica: 'Alteração',
            tipoProcedimento: 'DISCUSSAO',
            nomeProcedimento: 'Discussão',
            entidadeNif: '501234568',
            entidadeNome: 'Câmara Municipal do Porto',
            estado: 'ESTADO_DESCONHECIDO',
            atualizadoEm: '2026-08-01T10:00:00Z',
            autorNome: 'João Pereira',
          },
        ],
        page: 0,
        size: 20,
        totalElements: 2,
        totalPages: 1,
      }),
    );
    fixture.componentInstance.pesquisar();
    fixture.detectChanges();
  }

  it('mostra o badge de estado com o rótulo mapeado, e o valor em bruto quando não há mapeamento', () => {
    carregarDoisProcessos();

    const badges = [...fixture.nativeElement.querySelectorAll('app-selo')].map((el: Element) => el.textContent?.trim());
    expect(badges).toEqual(['Validado DGT', 'ESTADO_DESCONHECIDO']);
  });

  it('mostra em Designação a designação do Instrumento e deixa a coluna Concelho vazia', () => {
    carregarDoisProcessos();

    const linhas = fixture.nativeElement.querySelectorAll('tbody tr');
    const celulasLinha1 = [...linhas[0].querySelectorAll('td')].map((td: Element) => td.textContent?.trim());
    const celulasLinha2 = [...linhas[1].querySelectorAll('td')].map((td: Element) => td.textContent?.trim());
    const cabecalhos = [...fixture.nativeElement.querySelectorAll('thead th')].map((th: Element) => th.textContent?.trim());
    expect(cabecalhos[2]).toBe('Designação');
    expect(cabecalhos[5]).toBe('Concelho');
    expect(celulasLinha1[2]).toContain('PDM-2025-001');
    expect(celulasLinha1[5]).toBe('—');
    expect(celulasLinha2[2]).toContain('PP-2025-002');
    expect(celulasLinha2[5]).toBe('—');
  });

  it('segue a ordem de colunas do Figma, com a Referência à frente', () => {
    const cabecalhos = [...fixture.nativeElement.querySelectorAll('thead th')].map((th: Element) => th.textContent?.trim());
    expect(cabecalhos).toEqual(['Referência', 'Tipo IGT', 'Designação', 'Dinâmica', 'Procedimento', 'Concelho', 'Estado', 'Utilizador']);
  });

  it('pesquisa pela designação na barra de pesquisa', () => {
    const pesquisa: HTMLInputElement = fixture.nativeElement.querySelector('.barra app-campo-pesquisa input');
    pesquisa.value = 'Cascais';
    pesquisa.dispatchEvent(new Event('input'));

    expect(fixture.componentInstance.form.controls.designacaoInstrumento.value).toBe('Cascais');
    expect(pesquisa.placeholder).toBe('Pesquisar por designação…');
  });

  it('pede a página certa à API quando a paginação muda de página', () => {
    processoServiceMock.listar.mockReturnValue(of({ content: [linha({})], page: 0, size: 20, totalElements: 45, totalPages: 3 }));
    fixture.componentInstance.pesquisar();
    fixture.detectChanges();

    const pagina3 = fixture.nativeElement.querySelector('app-paginacao button[aria-label="Página 3"]') as HTMLButtonElement;
    pagina3.click();

    expect(processoServiceMock.listar).toHaveBeenLastCalledWith({}, 2, 20);
  });

  it('não mostra nenhum ícone de atraso numa linha real', () => {
    carregarDoisProcessos();

    expect(fixture.nativeElement.querySelector('.icone-atraso')).toBeNull();
  });

  it('mostra o autor real de cada linha, vindo da API', () => {
    carregarDoisProcessos();

    const linhas = fixture.nativeElement.querySelectorAll('tbody tr');
    expect(linhas[0].textContent).toContain('Maria Santos');
    expect(linhas[1].textContent).toContain('João Pereira');
  });

  /** O backend não pesquisa por texto livre: filtra só a página carregada. */
  it('filtra localmente a página já carregada ao escrever na pesquisa', () => {
    carregarDoisProcessos();

    fixture.componentInstance.termoPesquisaLocal.set('PP-2025-002');
    fixture.detectChanges();

    const linhas = fixture.nativeElement.querySelectorAll('tbody tr');
    expect(linhas).toHaveLength(1);
    expect(linhas[0].textContent).toContain('PP-2025-002');
  });

  it('filtra localmente a página já carregada por Tipo IGT, Dinâmica e Procedimento', () => {
    carregarDoisProcessos();

    fixture.componentInstance.filtroTipoIgtLocal.set('PP');
    fixture.detectChanges();
    let linhas = fixture.nativeElement.querySelectorAll('tbody tr');
    expect(linhas).toHaveLength(1);
    expect(linhas[0].textContent).toContain('PP-2025-002');

    fixture.componentInstance.filtroTipoIgtLocal.set('');
    fixture.componentInstance.filtroDinamicaLocal.set('Elaboração');
    fixture.detectChanges();
    linhas = fixture.nativeElement.querySelectorAll('tbody tr');
    expect(linhas).toHaveLength(1);
    expect(linhas[0].textContent).toContain('PDM-2025-001');

    fixture.componentInstance.filtroDinamicaLocal.set('');
    fixture.componentInstance.filtroProcedimentoLocal.set('Discussão');
    fixture.detectChanges();
    linhas = fixture.nativeElement.querySelectorAll('tbody tr');
    expect(linhas).toHaveLength(1);
    expect(linhas[0].textContent).toContain('PP-2025-002');
  });

  it('deriva as opções dos dropdowns locais a partir dos valores distintos da página carregada', () => {
    carregarDoisProcessos();

    expect(fixture.componentInstance.tiposIgtDisponiveis()).toEqual(['PDM', 'PP']);
    expect(fixture.componentInstance.dinamicasDisponiveis()).toEqual(['Alteração', 'Elaboração']);
    expect(fixture.componentInstance.procedimentosDisponiveis()).toEqual(['Decisão', 'Discussão']);
  });

  it('identifica cada dropdown pelo nome do campo já na opção de reset', () => {
    carregarDoisProcessos();
    fixture.componentInstance.filtrosAbertos.set(true);
    fixture.detectChanges();

    const selects = [...fixture.nativeElement.querySelectorAll('.filtros select')] as HTMLSelectElement[];
    const opcoesReset = selects.map((select) => select.options[0].textContent?.trim());
    // A «Situação PCM» (perfil interno) tem o rótulo visível e a opção «Todas», como a US a define.
    expect(opcoesReset).toEqual(['Tipo IGT (Todos)', 'Dinâmica (Todos)', 'Procedimento (Todos)', 'Estado (Todos)', 'Todas']);
    expect(selects.every((select) => select.options[0].value === '')).toBe(true);
  });

  /** Um `<select>` nativo ligado a um valor sem opção correspondente (ex. `null`) fica sem nada selecionado. */
  it('cada dropdown mostra a opção de reset selecionada por omissão, nunca em branco', () => {
    carregarDoisProcessos();
    fixture.componentInstance.filtrosAbertos.set(true);
    fixture.detectChanges();

    const selects = [...fixture.nativeElement.querySelectorAll('.filtros select')] as HTMLSelectElement[];
    expect(selects.every((select) => select.selectedIndex === 0)).toBe(true);
    expect(selects.map((select) => select.value)).toEqual(['', '', '', '', '']);
  });

  it('abre/fecha o painel de filtros ao clicar em "Filtros"', () => {
    expect(fixture.nativeElement.querySelector('.filtros')).toBeNull();

    const botaoFiltros = [...fixture.nativeElement.querySelectorAll('.barra button')].find((botao: Element) =>
      botao.textContent?.includes('Filtros'),
    ) as HTMLButtonElement;
    botaoFiltros.click();
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('.filtros')).not.toBeNull();
  });

  it('"Limpar tudo" repõe todos os filtros e volta a pedir a listagem sem filtros', () => {
    carregarDoisProcessos();
    fixture.componentInstance.filtrosAbertos.set(true);
    fixture.detectChanges();

    fixture.componentInstance.filtroTipoIgtLocal.set('PP');
    fixture.componentInstance.filtroDinamicaLocal.set('Alteração');
    fixture.componentInstance.filtroProcedimentoLocal.set('Discussão');
    fixture.componentInstance.form.controls.designacaoInstrumento.setValue('PP-2025-002');
    fixture.componentInstance.form.controls.estado.setValue('ESTADO_DESCONHECIDO');
    fixture.componentInstance.form.controls.situacaoPcm.setValue('DEVOLVIDO');
    processoServiceMock.listar.mockClear();
    processoServiceMock.listar.mockReturnValue(of({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 1 }));

    fixture.componentInstance.limparTudo();
    fixture.detectChanges();

    expect(fixture.componentInstance.filtroTipoIgtLocal()).toBe('');
    expect(fixture.componentInstance.filtroDinamicaLocal()).toBe('');
    expect(fixture.componentInstance.filtroProcedimentoLocal()).toBe('');
    expect(fixture.componentInstance.form.getRawValue()).toEqual({
      designacaoInstrumento: '',
      estado: '',
      motivoAlteracao: '',
      situacaoPcm: '',
    });
    expect(processoServiceMock.listar).toHaveBeenLastCalledWith({}, 0, 20);
  });

  it('escolher um estado filtra logo no servidor, sem carregar em "Filtrar"', () => {
    processoServiceMock.listar.mockClear();

    fixture.componentInstance.form.controls.estado.setValue('PUBLICADO');

    expect(processoServiceMock.listar).toHaveBeenCalledTimes(1);
    expect(processoServiceMock.listar).toHaveBeenLastCalledWith({ estado: 'PUBLICADO' }, 0, 20);
  });

  it('"Limpar tudo" com um estado escolhido pede a listagem uma só vez', () => {
    fixture.componentInstance.form.controls.estado.setValue('PUBLICADO');
    processoServiceMock.listar.mockClear();

    fixture.componentInstance.limparTudo();

    expect(processoServiceMock.listar).toHaveBeenCalledTimes(1);
    expect(processoServiceMock.listar).toHaveBeenLastCalledWith({}, 0, 20);
  });
  describe('Exportar', () => {
    let criarUrl: ReturnType<typeof vi.spyOn>;
    let revogarUrl: ReturnType<typeof vi.spyOn>;
    let clicarLigacao: ReturnType<typeof vi.spyOn>;
    let ligacoesClicadas: HTMLAnchorElement[];

    function botaoExportar(): HTMLButtonElement {
      return fixture.nativeElement.querySelector('app-menu-exportar button[aria-haspopup="menu"]');
    }

    function menu(): HTMLElement {
      return fixture.nativeElement.querySelector('[role="menu"]');
    }

    function itensMenu(): HTMLButtonElement[] {
      return [...fixture.nativeElement.querySelectorAll('[role="menuitem"]')];
    }

    function escolherFormato(rotulo: string): void {
      botaoExportar().click();
      fixture.detectChanges();
      itensMenu()
        .find((item) => item.textContent?.trim() === rotulo)!
        .click();
      fixture.detectChanges();
    }

    function teclar(alvo: HTMLElement, key: string): void {
      alvo.dispatchEvent(new KeyboardEvent('keydown', { key, bubbles: true }));
      fixture.detectChanges();
    }

    function ficheiro(nomeFicheiro: string): ExportacaoProcessos {
      return { ficheiro: new Blob(['x']), nomeFicheiro };
    }

    beforeEach(() => {
      criarUrl = vi.spyOn(URL, 'createObjectURL').mockReturnValue('blob:exportacao');
      revogarUrl = vi.spyOn(URL, 'revokeObjectURL').mockImplementation(() => undefined);
      ligacoesClicadas = [];
      clicarLigacao = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(function (this: HTMLAnchorElement) {
        ligacoesClicadas.push(this);
      });
    });

    afterEach(() => vi.restoreAllMocks());

    it('o botão está ativo, sem «ainda não disponível», e anuncia um menu', () => {
      const botao = botaoExportar();

      expect(botao.getAttribute('aria-label')).toBe('Exportar');
      expect(fixture.nativeElement.innerHTML).not.toContain('ainda não disponível');
      expect(botao.disabled).toBe(false);
      expect(botao.getAttribute('aria-expanded')).toBe('false');
      expect(menu().hidden).toBe(true);
    });

    it('abre o menu com Excel e PDF e põe o foco na primeira opção', () => {
      botaoExportar().click();
      fixture.detectChanges();

      expect(botaoExportar().getAttribute('aria-expanded')).toBe('true');
      expect(menu().hidden).toBe(false);
      expect(itensMenu().map((item) => item.textContent?.trim())).toEqual(['Excel', 'PDF']);
      expect(document.activeElement).toBe(itensMenu()[0]);
    });

    it('opera-se por teclado: setas percorrem, Escape fecha e devolve o foco ao botão', () => {
      teclar(botaoExportar(), 'ArrowDown');
      expect(document.activeElement).toBe(itensMenu()[0]);

      teclar(itensMenu()[0], 'ArrowDown');
      expect(document.activeElement).toBe(itensMenu()[1]);

      teclar(itensMenu()[1], 'ArrowDown');
      expect(document.activeElement).toBe(itensMenu()[0]);

      teclar(itensMenu()[0], 'End');
      expect(document.activeElement).toBe(itensMenu()[1]);

      teclar(itensMenu()[1], 'Escape');
      expect(menu().hidden).toBe(true);
      expect(document.activeElement).toBe(botaoExportar());
    });

    it('a seta para cima no botão abre o menu na última opção', () => {
      teclar(botaoExportar(), 'ArrowUp');

      expect(menu().hidden).toBe(false);
      expect(document.activeElement).toBe(itensMenu()[1]);
    });

    it('fecha o menu ao clicar fora', () => {
      botaoExportar().click();
      fixture.detectChanges();

      document.body.click();
      fixture.detectChanges();

      expect(menu().hidden).toBe(true);
    });

    it('Excel leva a pesquisa e o estado aplicados', () => {
      processoServiceMock.exportar.mockReturnValue(of(ficheiro('processos.xlsx')));
      fixture.componentInstance.form.controls.designacaoInstrumento.setValue('Lisboa');
      fixture.componentInstance.form.controls.estado.setValue('EM_VALIDACAO_DGT');

      escolherFormato('Excel');

      expect(processoServiceMock.exportar).toHaveBeenCalledWith({ designacaoInstrumento: 'Lisboa', estado: 'EM_VALIDACAO_DGT' }, 'EXCEL');
    });

    it('usa os filtros da última pesquisa, não o texto ainda por pesquisar', () => {
      processoServiceMock.exportar.mockReturnValue(of(ficheiro('processos.pdf')));
      fixture.componentInstance.form.controls.estado.setValue('ENVIADO_DGT');
      fixture.componentInstance.form.controls.designacaoInstrumento.setValue('Porto', { emitEvent: false });

      escolherFormato('PDF');

      expect(processoServiceMock.exportar).toHaveBeenCalledWith({ estado: 'ENVIADO_DGT' }, 'PDF');
    });

    it('descarrega o ficheiro com o nome recebido e liberta o URL temporário', async () => {
      const resposta = ficheiro('processos-2026.xlsx');
      processoServiceMock.exportar.mockReturnValue(of(resposta));

      escolherFormato('Excel');

      expect(criarUrl).toHaveBeenCalledWith(resposta.ficheiro);
      expect(clicarLigacao).toHaveBeenCalledTimes(1);
      expect(ligacoesClicadas[0].download).toBe('processos-2026.xlsx');
      expect(ligacoesClicadas[0].getAttribute('href')).toBe('blob:exportacao');
      expect(ligacoesClicadas[0].isConnected).toBe(false);

      await new Promise((resolve) => setTimeout(resolve));
      expect(revogarUrl).toHaveBeenCalledWith('blob:exportacao');
    });

    it('com o pedido em curso o botão fica desativado e com aria-busy, sem segundo pedido', () => {
      const pedido = new Subject<ExportacaoProcessos>();
      processoServiceMock.exportar.mockReturnValue(pedido);

      escolherFormato('Excel');

      expect(botaoExportar().disabled).toBe(true);
      expect(botaoExportar().getAttribute('aria-busy')).toBe('true');
      botaoExportar().click();
      fixture.componentInstance.exportar('PDF');
      expect(processoServiceMock.exportar).toHaveBeenCalledTimes(1);

      pedido.next(ficheiro('processos.xlsx'));
      pedido.complete();
      fixture.detectChanges();

      expect(botaoExportar().disabled).toBe(false);
      expect(botaoExportar().hasAttribute('aria-busy')).toBe(false);
    });

    it('a recusa por limite mostra a mensagem da API, não descarrega e mantém a tabela', () => {
      const mensagem = 'A exportação excede o limite de 5000 linhas. Aplique mais filtros e tente de novo.';
      processoServiceMock.exportar.mockReturnValue(throwError(() => new ApiError('EXPORTACAO_LIMITE_EXCEDIDO', mensagem, 't1', [], 400)));

      escolherFormato('Excel');

      expect(fixture.nativeElement.querySelector('app-error-banner').textContent).toContain(mensagem);
      expect(criarUrl).not.toHaveBeenCalled();
      expect(clicarLigacao).not.toHaveBeenCalled();
      expect(fixture.nativeElement.querySelector('tbody')).not.toBeNull();
      expect(botaoExportar().disabled).toBe(false);
    });

    it('um erro do servidor aparece no banner e o botão volta a ficar ativo', () => {
      const mensagem = 'Não foi possível concluir a operação. Tente novamente.';
      processoServiceMock.exportar.mockReturnValue(throwError(() => new ApiError('ERRO_DESCONHECIDO', mensagem, 'unknown', null, 500)));

      escolherFormato('PDF');

      expect(fixture.nativeElement.querySelector('app-error-banner').textContent).toContain(mensagem);
      expect(botaoExportar().disabled).toBe(false);
    });

    it('uma nova pesquisa limpa o erro da exportação anterior', () => {
      processoServiceMock.exportar.mockReturnValue(throwError(() => new ApiError('ERRO_DESCONHECIDO', 'Falhou.', 'unknown', null, 500)));
      escolherFormato('PDF');

      fixture.componentInstance.form.controls.estado.setValue('ANULADO');
      fixture.detectChanges();

      expect(fixture.nativeElement.querySelector('app-error-banner')).toBeNull();
    });

    it.each([
      ['MASTER', 'DGT'],
      ['TECNICO', 'DGT'],
      ['VISITANTE', 'DGT'],
      [null, 'ENTIDADE'],
    ])('o perfil %s (ator %s) vê o botão ativo', (perfil, outroAtor) => {
      perfilInterno = perfil;
      ator = outroAtor;
      fixture.destroy();
      fixture = TestBed.createComponent(ProcessosComponent);
      fixture.detectChanges();

      expect(botaoExportar().disabled).toBe(false);
    });
  });
});

describe('ProcessosComponent — ecrãs por grupo (Continuar e Corrigir)', () => {
  let fixture: ComponentFixture<ProcessosComponent>;
  let processoServiceMock: { listar: ReturnType<typeof vi.fn>; exportar: ReturnType<typeof vi.fn> };

  const PAGINA_VAZIA = { content: [], page: 0, size: 20, totalElements: 0, totalPages: 1 };

  function abrirComRota(data: Record<string, string> | undefined, resposta: Observable<unknown> = of(PAGINA_VAZIA)): void {
    processoServiceMock = { listar: vi.fn().mockReturnValue(resposta), exportar: vi.fn() };
    TestBed.configureTestingModule({
      imports: [ProcessosComponent],
      providers: [
        provideRouter([]),
        { provide: ProcessoService, useValue: processoServiceMock },
        {
          provide: AuthService,
          useValue: { nome: () => 'Ana', origem: () => 'ENTIDADE', entidade: () => null, perfilInterno: () => null, ator: () => 'ENTIDADE' },
        },
        { provide: ListaValoresService, useValue: { opcoes: vi.fn().mockReturnValue(of([])) } },
        { provide: ActivatedRoute, useValue: { snapshot: { queryParamMap: convertToParamMap({}), data } } },
      ],
    });
    fixture = TestBed.createComponent(ProcessosComponent);
    fixture.detectChanges();
  }

  function opcoesEstado(): string[] {
    fixture.componentInstance.filtrosAbertos.set(true);
    fixture.detectChanges();
    const select: HTMLSelectElement = fixture.nativeElement.querySelector('[formcontrolname="estado"]');
    return [...select.options].map((opcao) => opcao.value).filter((valor) => valor);
  }

  it('sem data da rota mantém o modo geral', () => {
    abrirComRota(undefined);

    expect(fixture.nativeElement.querySelector('h1').textContent.trim()).toBe('Consultar Processos');
    expect(processoServiceMock.listar).toHaveBeenCalledWith({}, 0, 20);
    expect(opcoesEstado()).toEqual(fixture.componentInstance.estadosProcesso);
  });

  it('Continuar: título, grupo no pedido e só os estados do grupo', () => {
    abrirComRota({ grupo: 'EM_CURSO', titulo: 'Continuar Processos' });

    expect(fixture.nativeElement.querySelector('h1').textContent.trim()).toBe('Continuar Processos');
    expect(processoServiceMock.listar).toHaveBeenCalledWith({ grupo: 'EM_CURSO' }, 0, 20);
    expect(opcoesEstado()).toEqual(['EM_SUBMISSAO', 'ENVIADO_DGT']);
  });

  it('Corrigir: título, grupo no pedido e só os estados do grupo', () => {
    abrirComRota({ grupo: 'PARA_CORRECAO', titulo: 'Corrigir Processos' });

    expect(fixture.nativeElement.querySelector('h1').textContent.trim()).toBe('Corrigir Processos');
    expect(processoServiceMock.listar).toHaveBeenCalledWith({ grupo: 'PARA_CORRECAO' }, 0, 20);
    expect(opcoesEstado()).toEqual(['REJEITADO_DGT', 'ANULADO_INCM']);
  });

  it('mantém o grupo depois de filtrar e de "Limpar tudo"', () => {
    abrirComRota({ grupo: 'PARA_CORRECAO', titulo: 'Corrigir Processos' });
    processoServiceMock.listar.mockClear();

    fixture.componentInstance.form.controls.estado.setValue('ANULADO_INCM');
    expect(processoServiceMock.listar).toHaveBeenLastCalledWith({ grupo: 'PARA_CORRECAO', estado: 'ANULADO_INCM' }, 0, 20);

    fixture.componentInstance.limparTudo();
    expect(processoServiceMock.listar).toHaveBeenLastCalledWith({ grupo: 'PARA_CORRECAO' }, 0, 20);
  });

  it.each([
    ['EM_CURSO', 'Não tem processos em curso.'],
    ['PARA_CORRECAO', 'Não tem processos para corrigir.'],
  ])('lista vazia em %s mostra a mensagem própria', (grupo, mensagem) => {
    abrirComRota({ grupo, titulo: 'x' });

    expect(fixture.nativeElement.textContent).toContain(mensagem);
  });

  it('Corrigir: a exportação em PDF leva o grupo do ecrã', () => {
    abrirComRota({ grupo: 'PARA_CORRECAO', titulo: 'Corrigir Processos' });
    processoServiceMock.exportar.mockReturnValue(of({ ficheiro: new Blob(['x']), nomeFicheiro: 'processos.pdf' }));
    vi.spyOn(URL, 'createObjectURL').mockReturnValue('blob:x');
    vi.spyOn(URL, 'revokeObjectURL').mockImplementation(() => undefined);
    vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => undefined);

    fixture.componentInstance.exportar('PDF');

    expect(processoServiceMock.exportar).toHaveBeenCalledWith({ grupo: 'PARA_CORRECAO' }, 'PDF');
    vi.restoreAllMocks();
  });

  it('com filtros ativos e lista vazia mostra a mensagem de filtros', () => {
    abrirComRota({ grupo: 'EM_CURSO', titulo: 'x' });
    fixture.componentInstance.form.controls.designacaoInstrumento.setValue('PDM');
    fixture.componentInstance.pesquisar();
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Sem processos para os filtros indicados.');
  });

  it('mostra o erro do envelope quando a API responde 400', () => {
    const erro = new ApiError('VALIDACAO', 'Grupo não aplicável ao perfil.', 't1', [{ path: 'grupo', erro: 'não se aplica' }]);
    abrirComRota({ grupo: 'PARA_CORRECAO', titulo: 'Corrigir Processos' }, throwError(() => erro));

    expect(fixture.nativeElement.querySelector('app-error-banner')).not.toBeNull();
    expect(fixture.nativeElement.querySelector('app-error-banner').textContent).toContain('Grupo não aplicável ao perfil.');
    expect(fixture.nativeElement.querySelector('tbody')).toBeNull();
  });

  it('distingue Rejeitado DGT de Anulado INCM por texto e liga a Referência ao detalhe', () => {
    const base = {
      designacaoInstrumento: 'PDM',
      numeroInstrumento: '1',
      tipoInstrumento: 'PDM',
      tipoDinamica: 'A',
      nomeDinamica: 'A',
      tipoProcedimento: 'P',
      nomeProcedimento: 'P',
      entidadeNif: '1',
      entidadeNome: 'E',
      atualizadoEm: '2026-08-01T10:00:00Z',
      autorNome: 'Ana',
    };
    abrirComRota(
      { grupo: 'PARA_CORRECAO', titulo: 'Corrigir Processos' },
      of({
        content: [
          { ...base, id: 'a', numeroProcesso: '00005', estado: 'ANULADO_INCM' },
          { ...base, id: 'b', numeroProcesso: '00012', estado: 'REJEITADO_DGT' },
        ],
        page: 0,
        size: 20,
        totalElements: 2,
        totalPages: 1,
      }),
    );

    const selos = [...fixture.nativeElement.querySelectorAll('app-selo')].map((el: Element) => el.textContent?.trim());
    expect(selos).toEqual(['Anulado INCM', 'Rejeitado DGT']);
    const ligacoes = [...fixture.nativeElement.querySelectorAll('tbody a.ligacao-processo')];
    expect(ligacoes.map((a: Element) => a.textContent?.trim())).toEqual(['#00005', '#00012']);
    expect(ligacoes[0].getAttribute('href')).toBe('/processos/a');
  });

  it.each([
    ['EM_CURSO', 'Continuar Processos'],
    ['PARA_CORRECAO', 'Corrigir Processos'],
  ])('%s: a coluna Concelho mostra o concelho do PDM de Sintra', (grupo, titulo) => {
    abrirComRota(
      { grupo, titulo },
      of({
        content: [{ id: 'a', numeroProcesso: '00005', designacaoInstrumento: 'PDM de Sintra', estado: 'REJEITADO_DGT', concelhos: ['SINTRA'] }],
        page: 0,
        size: 20,
        totalElements: 1,
        totalPages: 1,
      }),
    );

    expect(fixture.nativeElement.querySelector('[data-campo="concelhos"]').textContent.trim()).toBe('Sintra');
  });
});

describe('ProcessosComponent — chegada via query params (issues #131 e #174)', () => {
  let fixture: ComponentFixture<ProcessosComponent>;
  let processoServiceMock: { listar: ReturnType<typeof vi.fn> };

  function abrirComQuery(query: Record<string, string>): void {
    processoServiceMock = { listar: vi.fn().mockReturnValue(of({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 1 })) };

    TestBed.configureTestingModule({
      imports: [ProcessosComponent],
      providers: [
        provideRouter([]),
        { provide: ProcessoService, useValue: processoServiceMock },
        {
          provide: AuthService,
          useValue: { nome: () => 'Ana Teixeira', origem: () => 'AD_DGT', entidade: () => null, perfilInterno: () => 'MASTER' },
        },
        { provide: ListaValoresService, useValue: { opcoes: vi.fn().mockReturnValue(of([])) } },
        {
          provide: ActivatedRoute,
          useValue: { snapshot: { queryParamMap: convertToParamMap(query) } },
        },
      ],
    });

    fixture = TestBed.createComponent(ProcessosComponent);
    fixture.detectChanges();
  }

  describe('com ?grupo=PARA_CORRECAO', () => {
    beforeEach(() => abrirComQuery({ grupo: 'PARA_CORRECAO' }));

    it('aplica o grupo da query string no carregamento inicial', () => {
      expect(processoServiceMock.listar).toHaveBeenCalledWith({ grupo: 'PARA_CORRECAO' }, 0, 20);
    });

    it('deixa de aplicar o grupo da query string depois de uma pesquisa explícita do utilizador', () => {
      processoServiceMock.listar.mockClear();
      fixture.componentInstance.pesquisar();

      expect(processoServiceMock.listar).toHaveBeenLastCalledWith({}, 0, 20);
    });

    it('mantém o grupo de chegada ao paginar sem pesquisa explícita', () => {
      processoServiceMock.listar.mockClear();
      fixture.componentInstance.irParaPagina(1);

      expect(processoServiceMock.listar).toHaveBeenLastCalledWith({ grupo: 'PARA_CORRECAO' }, 1, 20);
    });
  });

  it('lê a situação PCM da query string para o filtro e o pedido', () => {
    abrirComQuery({ situacaoPcm: 'DEVOLVIDO' });

    expect(processoServiceMock.listar).toHaveBeenCalledWith({ situacaoPcm: 'DEVOLVIDO' }, 0, 20);
    expect(fixture.componentInstance.form.controls.situacaoPcm.value).toBe('DEVOLVIDO');
    expect((fixture.nativeElement.querySelector('#filtro-situacao-pcm') as HTMLSelectElement).value).toBe('DEVOLVIDO');
  });

  it('ignora uma situação PCM desconhecida na query string', () => {
    abrirComQuery({ situacaoPcm: 'OUTRO' });

    expect(processoServiceMock.listar).toHaveBeenCalledWith({}, 0, 20);
  });

  it('ignora um grupo desconhecido na query string', () => {
    abrirComQuery({ grupo: 'INEXISTENTE' });

    expect(processoServiceMock.listar).toHaveBeenCalledWith({}, 0, 20);
  });

  it('já não lê o parâmetro "estado" da query string', () => {
    abrirComQuery({ estado: 'REJEITADO_DGT' });

    expect(processoServiceMock.listar).toHaveBeenCalledWith({}, 0, 20);
  });
});
