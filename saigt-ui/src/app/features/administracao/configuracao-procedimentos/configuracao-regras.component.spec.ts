import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { ApiError } from '../../../core/models/error-response.model';
import { PageResponse } from '../../../core/models/page-response.model';
import {
  DinamicaConfig,
  ProcedimentoConfig,
  RegraConfig,
  TipoDinamicaConfig,
  TipoInstrumentoConfig,
  TipoProcedimentoConfig,
} from '../../../models/configuracao.model';
import { ConfiguracaoService } from '../../../services/configuracao.service';
import { ConfiguracaoRegrasComponent, SEM_REGRA_PROCEDIMENTO, SEM_REGRAS_DINAMICA } from './configuracao-regras.component';

const PDM: TipoInstrumentoConfig = { id: 1, codigo: 'PDM', nome: 'Plano Diretor Municipal', categoria: 'PLANO', ativo: true };
const PROT: TipoInstrumentoConfig = { id: 2, codigo: 'PROT', nome: 'Programa Regional', categoria: 'PROGRAMA', ativo: true };

function dinamica(id: number, tipoInstrumento: TipoInstrumentoConfig, codigo: string, nome: string): DinamicaConfig {
  return {
    id,
    tipoInstrumento: { id: tipoInstrumento.id, codigo: tipoInstrumento.codigo, nome: tipoInstrumento.nome },
    tipoDinamica: { id: id + 1000, codigo, nome },
    associacaoInstrumentos: 'UM',
    ativo: true,
    emUso: false,
  };
}

const ELABORACAO_PDM = dinamica(101, PDM, 'ELABORACAO', 'Elaboração');
const REVISAO_PDM = dinamica(102, PDM, 'REVISAO', 'Revisão');

const TIPOS_DINAMICA: TipoDinamicaConfig[] = [
  { id: 1, codigo: 'ELABORACAO', nome: 'Elaboração', ativo: true },
  { id: 2, codigo: 'REVISAO', nome: 'Revisão', ativo: true },
];
const TIPOS_PROCEDIMENTO: TipoProcedimentoConfig[] = [
  { id: 11, codigo: 'DECISAO_INICIO', nome: 'Decisão de início', ativo: true, formulario: 'INFORMACAO_GERAL' },
  { id: 12, codigo: 'DISCUSSAO_PUBLICA', nome: 'Discussão pública', ativo: false, formulario: null },
];

function procedimento(id: number, ordem: number, tipo: TipoProcedimentoConfig, instrumentoDinamicaConfigId = 101): ProcedimentoConfig {
  return {
    id,
    instrumentoDinamicaConfigId,
    tipoProcedimento: { id: tipo.id, codigo: tipo.codigo, nome: tipo.nome },
    ordem,
    obrigatorio: true,
    serieDr: 'SEGUNDA_H',
    tamanhoMaximoProcesso: null,
    repetivel: false,
    atoPontual: false,
    ativo: true,
    formulario: null,
    emUso: false,
    visivelNaDinamica: false,
  };
}

const DECISAO = procedimento(501, 1, TIPOS_PROCEDIMENTO[0]);
const DISCUSSAO = procedimento(502, 2, TIPOS_PROCEDIMENTO[1]);

const ELABORACAO_UNICA: RegraConfig = {
  id: 301,
  instrumentoDinamicaConfigId: 101,
  instrumentoProcedimentoConfigId: null,
  substituiSequenciamento: false,
  operador: 'AND',
  descricao: 'Elaboração é única',
  ativo: true,
  condicoes: [{ id: 401, avaliador: 'OCORRENCIA_NAO_EXISTE', parametros: { tipo_ocorrencia: 'DINAMICA', codigo: 'ELABORACAO' }, grupo: 1, ativo: true }],
};
const REVISAO_PDM_REGRA: RegraConfig = {
  ...ELABORACAO_UNICA,
  id: 302,
  instrumentoDinamicaConfigId: 102,
  descricao: 'Requer plano em vigor; só 1 Revisão em curso',
  condicoes: [
    { id: 402, avaliador: 'INSTRUMENTO_EM_VIGOR', parametros: {}, grupo: 1, ativo: true },
    { id: 403, avaliador: 'OCORRENCIA_NAO_EXISTE', parametros: { tipo_ocorrencia: 'DINAMICA', codigo: 'REVISAO', estado: 'EM_CURSO' }, grupo: 1, ativo: true },
  ],
};
const REGRA_DISCUSSAO: RegraConfig = {
  id: 320,
  instrumentoDinamicaConfigId: null,
  instrumentoProcedimentoConfigId: 502,
  substituiSequenciamento: true,
  operador: 'AND',
  descricao: null,
  ativo: true,
  condicoes: [
    {
      id: 420,
      avaliador: 'OCORRENCIA_EM_ESTADO',
      parametros: { tipo_ocorrencia: 'PROCESSO', codigo: 'DECISAO_INICIO', estado: 'PUBLICADO' },
      grupo: 1,
      ativo: true,
    },
  ],
};

function pagina<T>(linhas: T[], numero = 0, totalPages = 1): PageResponse<T> {
  return { content: linhas, page: numero, size: 100, totalElements: linhas.length, totalPages };
}

describe('ConfiguracaoRegrasComponent', () => {
  let fixture: ComponentFixture<ConfiguracaoRegrasComponent>;
  let servico: Record<string, ReturnType<typeof vi.fn>>;
  let regrasPorDinamica: Record<number, RegraConfig[]>;

  afterEach(() => fixture?.destroy());

  function configurar(): void {
    regrasPorDinamica = { 101: [ELABORACAO_UNICA], 102: [REVISAO_PDM_REGRA] };
    servico = {
      listarTiposInstrumento: vi.fn().mockReturnValue(of(pagina([PROT, PDM]))),
      listarTiposDinamica: vi.fn().mockReturnValue(of(pagina(TIPOS_DINAMICA))),
      listarTiposProcedimento: vi.fn((_filtro, numero: number) =>
        of(numero === 0 ? pagina([TIPOS_PROCEDIMENTO[0]], 0, 2) : pagina([TIPOS_PROCEDIMENTO[1]], 1, 2)),
      ),
      listarDinamicasConfig: vi.fn((filtro: { tipoInstrumentoId: number }) =>
        of(pagina(filtro.tipoInstrumentoId === 1 ? [ELABORACAO_PDM, REVISAO_PDM] : [])),
      ),
      listarProcedimentosConfig: vi.fn((filtro: { instrumentoDinamicaConfigId: number }) =>
        of(pagina(filtro.instrumentoDinamicaConfigId === 101 ? [DISCUSSAO, DECISAO] : [])),
      ),
      listarRegras: vi.fn((filtro: { instrumentoDinamicaConfigId?: number; dinamicaDosProcedimentosId?: number }) => {
        if (filtro.dinamicaDosProcedimentosId !== undefined) {
          return of(pagina(filtro.dinamicaDosProcedimentosId === 101 ? [REGRA_DISCUSSAO] : []));
        }
        return of(pagina(regrasPorDinamica[filtro.instrumentoDinamicaConfigId!] ?? []));
      }),
      criarRegra: vi.fn(),
      atualizarRegra: vi.fn((id: number, pedido) => of({ ...pedido, id })),
    };
    TestBed.configureTestingModule({
      imports: [ConfiguracaoRegrasComponent],
      providers: [{ provide: ConfiguracaoService, useValue: servico }],
    });
    fixture = TestBed.createComponent(ConfiguracaoRegrasComponent);
    fixture.detectChanges();
  }

  const el = (): HTMLElement => fixture.nativeElement as HTMLElement;
  const dialogo = (): HTMLElement | null => document.querySelector('[role="dialog"]');
  const campo = <T extends HTMLElement>(rotulo: string, raiz: ParentNode): T => {
    const label = [...raiz.querySelectorAll('label')].find((l) => l.textContent?.replace('*', '').trim() === rotulo)!;
    return document.getElementById(label.htmlFor) as T;
  };
  const botao = (texto: string, raiz: ParentNode = el()): HTMLButtonElement =>
    [...raiz.querySelectorAll<HTMLButtonElement>('button')].find((b) => b.textContent?.trim() === texto)!;
  const linhasTabela = (): HTMLTableRowElement[] => [...el().querySelectorAll<HTMLTableRowElement>('.cartao-excecoes tbody tr')];
  const celula = (linha: number, coluna: string): HTMLElement => linhasTabela()[linha].querySelector<HTMLElement>(`.coluna-${coluna}`)!;
  const elegibilidade = (): HTMLElement => el().querySelector<HTMLElement>('.cartao-elegibilidade')!;

  function clicar(alvo: HTMLElement): void {
    alvo.click();
    fixture.detectChanges();
  }

  function escolher(entrada: HTMLSelectElement, valor: string): void {
    entrada.value = valor;
    entrada.dispatchEvent(new Event('change'));
    fixture.detectChanges();
  }

  it('abre no PDM e na Elaboração: a regra "Elaboração é única" com o resumo "(não existe Dinâmica Elaboração)"', () => {
    configurar();

    expect(campo<HTMLSelectElement>('Tipo de Instrumento', el()).value).toBe('1');
    expect(campo<HTMLSelectElement>('Dinâmica', el()).value).toBe('101');
    expect(elegibilidade().querySelector('.cartao-titulo')?.textContent?.trim()).toBe('Quando a Dinâmica pode ser iniciada');
    const regra = elegibilidade().querySelector('.regra')!;
    expect(regra.querySelector('.regra-descricao')?.textContent?.trim()).toBe('Elaboração é única');
    expect(regra.querySelector('.regra-resumo')?.textContent?.trim()).toBe('(não existe Dinâmica Elaboração)');
    expect(regra.querySelector('app-selo')?.textContent?.trim()).toBe('Ativa');
    expect(botao('Editar', regra).getAttribute('aria-label')).toBe('Editar a regra Elaboração é única');
    expect(botao('Desativar', regra)).toBeDefined();
  });

  it('os tipos de Dinâmica e de Procedimento carregam-se todos (inativos incluídos), página a página', () => {
    configurar();
    expect(servico['listarTiposDinamica']).toHaveBeenCalledWith({}, 0, 100);
    expect(servico['listarTiposProcedimento']).toHaveBeenCalledWith({}, 0, 100);
    expect(servico['listarTiposProcedimento']).toHaveBeenCalledWith({}, 1, 100);
    expect(fixture.componentInstance.tiposProcedimento().map((t) => t.codigo)).toEqual(['DECISAO_INICIO', 'DISCUSSAO_PUBLICA']);
  });

  it('mudar de Dinâmica faz os três pedidos (regras da Dinâmica, Procedimentos e dinamicaDosProcedimentosId) e atualiza os dois cartões', () => {
    configurar();
    servico['listarRegras'].mockClear();
    servico['listarProcedimentosConfig'].mockClear();

    escolher(campo<HTMLSelectElement>('Dinâmica', el()), '102');

    expect(servico['listarRegras']).toHaveBeenCalledWith({ instrumentoDinamicaConfigId: 102 }, 0, 100);
    expect(servico['listarProcedimentosConfig']).toHaveBeenCalledWith({ instrumentoDinamicaConfigId: 102 }, 0, 100);
    expect(servico['listarRegras']).toHaveBeenCalledWith({ dinamicaDosProcedimentosId: 102 }, 0, 100);
    expect(servico['listarRegras']).toHaveBeenCalledTimes(2);
    expect(elegibilidade().querySelector('.regra-resumo')?.textContent?.trim()).toBe(
      '(o Instrumento está em vigor e não existe Dinâmica Revisão em curso)',
    );
    expect(linhasTabela()).toHaveLength(1);
    expect(linhasTabela()[0].textContent).toContain('A Dinâmica não tem Procedimentos configurados.');
  });

  it('sem regras de elegibilidade, diz que a Dinâmica pode ser iniciada sempre', () => {
    configurar();
    regrasPorDinamica[102] = [];
    escolher(campo<HTMLSelectElement>('Dinâmica', el()), '102');
    expect(elegibilidade().querySelector('.sem-regras')?.textContent?.trim()).toBe(SEM_REGRAS_DINAMICA);
  });

  it('exceções: Procedimentos pela ordem; sem regra → "Sem regra: segue a ordem dos Procedimentos" e "Adicionar regra"; com regra → resumo, efeito, "Editar" e "Desativar"', () => {
    configurar();

    expect([...el().querySelectorAll('.cartao-excecoes th[scope="col"]')].map((th) => th.textContent?.trim())).toEqual([
      'Ordem',
      'Procedimento',
      'Regra',
      'Efeito',
      'Estado',
      'Ações',
    ]);
    expect(celula(0, 'procedimento').textContent?.trim()).toBe('Decisão de início');
    expect(celula(0, 'regra').textContent?.trim()).toBe(SEM_REGRA_PROCEDIMENTO);
    expect(celula(0, 'efeito').textContent?.trim()).toBe('—');
    expect([...celula(0, 'acoes').querySelectorAll('button')].map((b) => b.textContent?.trim())).toEqual(['Adicionar regra']);
    expect(botao('Adicionar regra', celula(0, 'acoes')).getAttribute('aria-label')).toBe('Adicionar regra a Decisão de início');

    expect(celula(1, 'procedimento').textContent?.trim()).toBe('Discussão pública');
    expect(celula(1, 'regra').textContent?.trim()).toBe('(existe processo de Decisão de início em Publicado)');
    expect(celula(1, 'efeito').textContent?.trim()).toBe('Substitui a ordem');
    expect(celula(1, 'estado').textContent?.trim()).toBe('Ativa');
    expect([...celula(1, 'acoes').querySelectorAll('button')].map((b) => b.textContent?.trim())).toEqual(['Editar', 'Desativar']);
  });

  it('"Adicionar regra" num Procedimento abre o editor de uma exceção nova; "Editar" abre o da regra gravada', () => {
    configurar();
    clicar(botao('Adicionar regra', celula(0, 'acoes')));
    expect(dialogo()?.querySelector('.modal-titulo')?.textContent?.trim()).toBe('Exceção ao sequenciamento — Decisão de início');
    expect(dialogo()?.textContent).toContain('Substitui a ordem dos Procedimentos');
    expect(fixture.componentInstance.editor()).toEqual({
      alvo: { tipo: 'PROCEDIMENTO', id: 501, nome: 'Decisão de início', contexto: 'PDM — Elaboração' },
      regra: null,
    });
    clicar(botao('Cancelar', dialogo()!));
    expect(dialogo()).toBeNull();

    clicar(botao('Editar', celula(1, 'acoes')));
    expect(fixture.componentInstance.editor()?.regra).toBe(REGRA_DISCUSSAO);
    clicar(botao('Cancelar', dialogo()!));

    clicar(botao('Adicionar regra', elegibilidade()));
    expect(dialogo()?.querySelector('.modal-titulo')?.textContent?.trim()).toBe('Regra de elegibilidade — Elaboração');
    expect(dialogo()?.textContent).not.toContain('Substitui a ordem dos Procedimentos');
  });

  it('"Desativar" na regra "Elaboração é única": confirmação, PUT com a regra completa e ativo = false, sucesso e recarga com o selo "Inativa"', () => {
    configurar();
    clicar(botao('Desativar', elegibilidade()));
    expect(dialogo()?.textContent).toContain('Desativar a regra Elaboração é única? Deixa de ser avaliada de imediato.');

    regrasPorDinamica[101] = [{ ...ELABORACAO_UNICA, ativo: false }];
    servico['listarRegras'].mockClear();
    clicar(botao('Sim', dialogo()!));

    expect(servico['atualizarRegra']).toHaveBeenCalledWith(301, {
      instrumentoDinamicaConfigId: 101,
      instrumentoProcedimentoConfigId: null,
      substituiSequenciamento: false,
      operador: 'AND',
      descricao: 'Elaboração é única',
      ativo: false,
      condicoes: [{ id: 401, avaliador: 'OCORRENCIA_NAO_EXISTE', parametros: { tipo_ocorrencia: 'DINAMICA', codigo: 'ELABORACAO' }, grupo: 1, ativo: true }],
    });
    expect(servico['listarRegras']).toHaveBeenCalledTimes(2);
    expect(el().querySelector('app-alerta')?.textContent).toContain('Regra desativada.');
    expect(elegibilidade().querySelector('app-selo')?.textContent?.trim()).toBe('Inativa');
    expect(botao('Reativar', elegibilidade())).toBeDefined();
  });

  it('"Reativar" uma regra inativa sem descrição usa o resumo na confirmação e envia ativo = true', () => {
    configurar();
    regrasPorDinamica[101] = [{ ...ELABORACAO_UNICA, descricao: null, ativo: false }];
    escolher(campo<HTMLSelectElement>('Dinâmica', el()), '102');
    escolher(campo<HTMLSelectElement>('Dinâmica', el()), '101');

    clicar(botao('Reativar', elegibilidade()));
    expect(dialogo()?.textContent).toContain('Reativar a regra (não existe Dinâmica Elaboração)? Volta a ser avaliada de imediato.');
    clicar(botao('Sim', dialogo()!));
    expect(servico['atualizarRegra'].mock.calls[0][1].ativo).toBe(true);
    expect(el().querySelector('app-alerta')?.textContent).toContain('Regra reativada.');
  });

  it('"Desativar" a regra de um Procedimento também envia ativo = false; um erro do servidor aparece por cima dos cartões', () => {
    configurar();
    servico['atualizarRegra'].mockReturnValue(throwError(() => new ApiError('SEM_PERMISSAO', 'Sem permissão.', 't', null)));
    clicar(botao('Desativar', celula(1, 'acoes')));
    clicar(botao('Sim', dialogo()!));

    expect(servico['atualizarRegra'].mock.calls[0][0]).toBe(320);
    expect(servico['atualizarRegra'].mock.calls[0][1]).toMatchObject({ instrumentoProcedimentoConfigId: 502, substituiSequenciamento: true, ativo: false });
    expect(el().querySelector('app-error-banner')?.textContent).toContain('Não tem permissão para executar esta ação.');
    expect(dialogo()).toBeNull();
  });

  it('depois de gravar no editor: alerta de sucesso durante 4 s e recarga dos dois cartões', () => {
    vi.useFakeTimers();
    try {
      configurar();
      clicar(botao('Editar', elegibilidade()));
      servico['listarRegras'].mockClear();
      servico['listarProcedimentosConfig'].mockClear();
      clicar(botao('Guardar', dialogo()!));

      expect(servico['atualizarRegra']).toHaveBeenCalledWith(301, expect.objectContaining({ instrumentoDinamicaConfigId: 101 }));
      expect(dialogo()).toBeNull();
      expect(servico['listarRegras']).toHaveBeenCalledTimes(2);
      expect(servico['listarProcedimentosConfig']).toHaveBeenCalledTimes(1);
      expect(el().querySelector('app-alerta')?.textContent).toContain('Regra atualizada.');

      vi.advanceTimersByTime(4000);
      fixture.detectChanges();
      expect(el().querySelector('app-alerta[tipo="sucesso"], .alerta-sucesso')).toBeNull();
    } finally {
      vi.useRealTimers();
    }
  });
});
