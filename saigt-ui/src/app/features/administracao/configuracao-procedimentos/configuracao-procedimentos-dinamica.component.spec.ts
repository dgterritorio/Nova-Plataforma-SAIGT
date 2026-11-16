import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { describe, expect, it, vi } from 'vitest';
import { ApiError } from '../../../core/models/error-response.model';
import { PageResponse } from '../../../core/models/page-response.model';
import { DinamicaConfig, ProcedimentoConfig, TipoInstrumentoConfig, TipoProcedimentoConfig } from '../../../models/configuracao.model';
import { ConfiguracaoService } from '../../../services/configuracao.service';
import { ConfiguracaoProcedimentosDinamicaComponent } from './configuracao-procedimentos-dinamica.component';
import { AJUDA_TIPO_PROCEDIMENTO_EM_USO, AVISO_SEM_FORMULARIO } from './procedimento-config-modal.component';

const PDM: TipoInstrumentoConfig = { id: 1, codigo: 'PDM', nome: 'Plano Diretor Municipal', categoria: 'PLANO', ativo: true };
const PROT: TipoInstrumentoConfig = { id: 2, codigo: 'PROT', nome: 'Programa Regional', categoria: 'PROGRAMA', ativo: true };
const PU: TipoInstrumentoConfig = { id: 3, codigo: 'PU', nome: 'Plano de Urbanização', categoria: 'PLANO', ativo: true };

function dinamica(id: number, tipoInstrumento: TipoInstrumentoConfig, codigo: string, nome: string, ativo = true): DinamicaConfig {
  return {
    id,
    tipoInstrumento: { id: tipoInstrumento.id, codigo: tipoInstrumento.codigo, nome: tipoInstrumento.nome },
    tipoDinamica: { id: id + 1000, codigo, nome },
    associacaoInstrumentos: 'UM',
    ativo,
    emUso: false,
  };
}

const DINAMICAS_PDM = [dinamica(101, PDM, 'ALTERACAO', 'Alteração'), dinamica(102, PDM, 'REVISAO', 'Revisão', false)];
const DINAMICAS_PU = [dinamica(3103, PU, 'ALTERACAO', 'Alteração'), dinamica(3104, PU, 'ELABORACAO', 'Elaboração')];
const DINAMICAS_PROT = [dinamica(201, PROT, 'REVISAO', 'Revisão')];

const TIPOS_PROCEDIMENTO: TipoProcedimentoConfig[] = [
  { id: 1, codigo: 'DECISAO_INICIO', nome: 'Decisão de início', ativo: true, formulario: 'INFORMACAO_GERAL' },
  { id: 2, codigo: 'PARECER_NOVO', nome: 'Parecer novo', ativo: true, formulario: null },
];

const DECISAO: ProcedimentoConfig = {
  id: 501,
  instrumentoDinamicaConfigId: 101,
  tipoProcedimento: { id: 1, codigo: 'DECISAO_INICIO', nome: 'Decisão de início' },
  ordem: 1,
  obrigatorio: true,
  serieDr: 'SEGUNDA_H',
  tamanhoMaximoProcesso: 5368709120,
  repetivel: false,
  atoPontual: false,
  ativo: true,
  formulario: 'INFORMACAO_GERAL',
  emUso: true,
  visivelNaDinamica: false,
};
const PARECER: ProcedimentoConfig = {
  ...DECISAO,
  id: 502,
  tipoProcedimento: { id: 2, codigo: 'PARECER_NOVO', nome: 'Parecer novo' },
  ordem: 2,
  obrigatorio: false,
  serieDr: 'PRIMEIRA',
  tamanhoMaximoProcesso: null,
  repetivel: true,
  atoPontual: true,
  ativo: false,
  formulario: null,
  emUso: false,
  visivelNaDinamica: false,
};

function pagina<T>(linhas: T[]): PageResponse<T> {
  return { content: linhas, page: 0, size: 20, totalElements: linhas.length, totalPages: 1 };
}

describe('ConfiguracaoProcedimentosDinamicaComponent', () => {
  let fixture: ComponentFixture<ConfiguracaoProcedimentosDinamicaComponent>;
  let servico: Record<string, ReturnType<typeof vi.fn>>;

  function configurar(): void {
    const porTipo: Record<number, DinamicaConfig[]> = { 1: DINAMICAS_PDM, 2: DINAMICAS_PROT, 3: DINAMICAS_PU };
    servico = {
      listarTiposInstrumento: vi.fn().mockReturnValue(of(pagina([PU, PROT, PDM]))),
      listarTiposProcedimento: vi.fn().mockReturnValue(of(pagina(TIPOS_PROCEDIMENTO))),
      listarDinamicasConfig: vi.fn((filtro: { tipoInstrumentoId: number }) => of(pagina(porTipo[filtro.tipoInstrumentoId] ?? []))),
      listarProcedimentosConfig: vi.fn().mockReturnValue(of(pagina([DECISAO, PARECER]))),
      criarProcedimentoConfig: vi.fn(),
      atualizarProcedimentoConfig: vi.fn(),
      listarPermissoes: vi.fn().mockReturnValue(of(pagina([]))),
    };
    TestBed.configureTestingModule({
      imports: [ConfiguracaoProcedimentosDinamicaComponent],
      providers: [{ provide: ConfiguracaoService, useValue: servico }],
    });
    fixture = TestBed.createComponent(ConfiguracaoProcedimentosDinamicaComponent);
    fixture.detectChanges();
  }

  const el = (): HTMLElement => fixture.nativeElement as HTMLElement;
  const dialogo = (): HTMLElement | null => document.querySelector('[role="dialog"]');
  const botao = (texto: string, raiz: ParentNode = el()): HTMLButtonElement =>
    [...raiz.querySelectorAll<HTMLButtonElement>('button')].find((b) => b.textContent?.trim() === texto)!;
  const campo = <T extends HTMLElement>(rotulo: string, raiz: ParentNode): T => {
    const label = [...raiz.querySelectorAll('label')].find((l) => l.textContent?.trim().startsWith(rotulo))!;
    return document.getElementById(label.htmlFor) as T;
  };
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

  it('abre no PDM e na primeira Dinâmica (Alteração) e lista os Procedimentos por ordem com todos os valores por extenso', () => {
    configurar();

    expect(servico['listarTiposProcedimento']).toHaveBeenCalledWith({ ativo: true }, 0, 100);
    expect(servico['listarDinamicasConfig']).toHaveBeenCalledWith({ tipoInstrumentoId: 1 }, 0, 100);
    expect(servico['listarProcedimentosConfig']).toHaveBeenCalledWith({ instrumentoDinamicaConfigId: 101, ativo: undefined }, 0, 20);
    expect(campo<HTMLSelectElement>('Tipo de Instrumento', el()).value).toBe('1');
    const seletorDinamica = campo<HTMLSelectElement>('Dinâmica', el());
    expect(seletorDinamica.value).toBe('101');
    expect([...seletorDinamica.options].map((o) => o.textContent?.trim()).slice(1)).toEqual(['Alteração', 'Revisão (inativa)']);
    expect([...el().querySelectorAll('th[scope="col"]')].map((th) => th.textContent?.trim())).toEqual([
      'Ordem',
      'Procedimento',
      'Obrigatório',
      'Série do DR',
      'Repetível',
      'Ato pontual',
      'Limite do processo',
      'Formulário',
      'Estado',
      'Ações',
    ]);
    expect(celula(0, 'ordem')).toBe('1');
    expect(celula(0, 'procedimento')).toBe('Decisão de início');
    expect(celula(0, 'obrigatorio')).toBe('Sim');
    expect(celula(0, 'serie')).toBe('2.ª série — parte H');
    expect(celula(0, 'repetivel')).toBe('Não');
    expect(celula(0, 'atoPontual')).toBe('Não');
    expect(celula(0, 'limite')).toBe('5 GB');
    expect(celula(0, 'formulario')).toBe('Informação Geral');
    expect(celula(0, 'estado')).toBe('Ativo');
    expect(celula(1, 'obrigatorio')).toBe('Não');
    expect(celula(1, 'serie')).toBe('1.ª série');
    expect(celula(1, 'repetivel')).toBe('Sim');
    expect(celula(1, 'limite')).toBe('Sem limite');
    expect(el().querySelectorAll('tbody tr')[1].querySelector('.coluna-formulario app-selo')?.textContent?.trim()).toBe('Sem formulário');
    expect(celula(1, 'estado')).toBe('Inativo');
    expect(el().querySelector('.contador')?.textContent).toContain('2 Procedimentos');
    expect(el().querySelector('caption')?.textContent).toContain('Procedimentos de PDM — Alteração');
  });

  it('mudar para PU: o seletor Dinâmica passa a ter as do PU e a tabela recarrega com a primeira', () => {
    configurar();
    escolher(campo('Tipo de Instrumento', el()), '3');

    expect(servico['listarDinamicasConfig']).toHaveBeenLastCalledWith({ tipoInstrumentoId: 3 }, 0, 100);
    const seletorDinamica = campo<HTMLSelectElement>('Dinâmica', el());
    expect([...seletorDinamica.options].map((o) => o.textContent?.trim()).slice(1)).toEqual(['Alteração', 'Elaboração']);
    expect(seletorDinamica.value).toBe('3103');
    expect(servico['listarProcedimentosConfig']).toHaveBeenLastCalledWith({ instrumentoDinamicaConfigId: 3103, ativo: undefined }, 0, 20);
  });

  it('mudar a Dinâmica, o filtro ou a página lista de novo', () => {
    configurar();
    escolher(campo('Dinâmica', el()), '102');
    expect(servico['listarProcedimentosConfig']).toHaveBeenLastCalledWith({ instrumentoDinamicaConfigId: 102, ativo: undefined }, 0, 20);
    clicar(botao('Inativos'));
    expect(servico['listarProcedimentosConfig']).toHaveBeenLastCalledWith({ instrumentoDinamicaConfigId: 102, ativo: false }, 0, 20);
    fixture.componentInstance.irParaPagina(1);
    expect(servico['listarProcedimentosConfig']).toHaveBeenLastCalledWith({ instrumentoDinamicaConfigId: 102, ativo: false }, 1, 20);
  });

  it('um tipo de Instrumento sem Dinâmicas: tabela vazia e Adicionar Procedimento desativado', () => {
    configurar();
    servico['listarDinamicasConfig'].mockReturnValue(of(pagina([])));
    servico['listarProcedimentosConfig'].mockClear();
    escolher(campo('Tipo de Instrumento', el()), '2');

    expect(servico['listarProcedimentosConfig']).not.toHaveBeenCalled();
    expect(el().querySelector('.contador')?.textContent).toContain('0 Procedimentos');
    expect(botao('Adicionar Procedimento').disabled).toBe(true);
  });

  it('Adicionar Procedimento na Revisão do PROT: POST com 5368709120 bytes e a linha aparece', () => {
    configurar();
    escolher(campo('Tipo de Instrumento', el()), '2');
    const criado: ProcedimentoConfig = { ...DECISAO, id: 777, instrumentoDinamicaConfigId: 201, emUso: false };
    servico['criarProcedimentoConfig'].mockReturnValue(of(criado));
    servico['listarProcedimentosConfig'].mockReturnValue(of(pagina([criado])));

    clicar(botao('Adicionar Procedimento'));
    expect(dialogo()?.textContent).toContain('PROT — Revisão');
    escolher(campo('Tipo de Procedimento', dialogo()!), '1');
    escrever(campo('Ordem', dialogo()!), '1');
    escolher(campo('Série do DR', dialogo()!), 'SEGUNDA_H');
    escrever(campo('Limite agregado do processo (MB)', dialogo()!), '5120');
    clicar(botao('Guardar', dialogo()!));

    expect(servico['criarProcedimentoConfig']).toHaveBeenCalledWith({
      instrumentoDinamicaConfigId: 201,
      tipoProcedimentoId: 1,
      ordem: 1,
      serieDr: 'SEGUNDA_H',
      tamanhoMaximoProcesso: 5368709120,
      obrigatorio: true,
      repetivel: false,
      atoPontual: false,
      visivelNaDinamica: false,
      ativo: true,
    });
    expect(dialogo()).toBeNull();
    expect(el().querySelector('app-alerta[role="status"]')?.textContent).toContain('Procedimento criado.');
    expect(celula(0, 'procedimento')).toBe('Decisão de início');
    expect(celula(0, 'limite')).toBe('5 GB');
  });

  it('no modal, escolher um tipo sem formulário mostra o aviso', () => {
    configurar();
    clicar(botao('Adicionar Procedimento'));
    escolher(campo('Tipo de Procedimento', dialogo()!), '2');
    expect(dialogo()?.textContent).toContain(AVISO_SEM_FORMULARIO);
  });

  it('editar um Procedimento com emUso: tipo desativado com a ajuda; 409 EM_USO no alerta do modal, que fica aberto', () => {
    configurar();
    servico['atualizarProcedimentoConfig'].mockReturnValue(throwError(() => new ApiError('EM_USO', 'Configuração em uso.', 't', null)));

    abrirMenu(0, 'Editar');
    expect(campo<HTMLSelectElement>('Tipo de Procedimento', dialogo()!).disabled).toBe(true);
    expect(dialogo()?.textContent).toContain(AJUDA_TIPO_PROCEDIMENTO_EM_USO);
    clicar(botao('Guardar', dialogo()!));

    expect(servico['atualizarProcedimentoConfig']).toHaveBeenCalledWith(501, expect.objectContaining({ tipoProcedimentoId: 1, ativo: true }));
    expect(dialogo()?.querySelector('app-alerta[role="alert"]')?.textContent).toContain('Configuração em uso.');
  });

  it('Desativar pede a confirmação com o texto do Procedimento e faz PUT com ativo=false; Reativar o inverso', () => {
    configurar();
    servico['atualizarProcedimentoConfig'].mockReturnValue(of(DECISAO));

    abrirMenu(0, 'Desativar');
    expect(dialogo()?.textContent).toContain(
      'Desativar o Procedimento Decisão de início? Deixa de ser oferecido e de entrar no sequenciamento; os processos em curso não são afetados.',
    );
    clicar(botao('Sim', dialogo()!));
    expect(servico['atualizarProcedimentoConfig']).toHaveBeenLastCalledWith(501, {
      instrumentoDinamicaConfigId: 101,
      tipoProcedimentoId: 1,
      ordem: 1,
      obrigatorio: true,
      serieDr: 'SEGUNDA_H',
      tamanhoMaximoProcesso: 5368709120,
      repetivel: false,
      atoPontual: false,
      ativo: false,
      visivelNaDinamica: false,
    });
    expect(el().querySelector('app-alerta[role="status"]')?.textContent).toContain('Procedimento desativado.');

    abrirMenu(1, 'Reativar');
    clicar(botao('Sim', dialogo()!));
    expect(servico['atualizarProcedimentoConfig']).toHaveBeenLastCalledWith(502, expect.objectContaining({ ativo: true, tamanhoMaximoProcesso: null }));
  });

  it('Permissões abre o modal das permissões desse Procedimento', () => {
    configurar();
    abrirMenu(0, 'Permissões');

    expect(dialogo()?.querySelector('h2')?.textContent).toContain('Permissões — Decisão de início');
    expect(servico['listarPermissoes']).toHaveBeenCalledWith({ instrumentoProcedimentoConfigId: 501 }, 0, 100);
    clicar(botao('Fechar', dialogo()!));
    expect(dialogo()).toBeNull();
  });

  it('Itens documentais emite o tipo de Instrumento, a Dinâmica e o Procedimento da linha', () => {
    configurar();
    const emitidos: unknown[] = [];
    fixture.componentInstance.itensDocumentais.subscribe((selecao) => emitidos.push(selecao));

    abrirMenu(1, 'Itens documentais');

    expect(emitidos).toEqual([{ tipoInstrumentoId: 1, dinamicaConfigId: 101, procedimentoConfigId: 502 }]);
    expect(el().querySelector('.dropdown-menu')).toBeNull();
  });

  it('menu de ações: aria-expanded e as quatro ações alcançáveis por botão', () => {
    configurar();
    const botaoMenu = el().querySelectorAll<HTMLButtonElement>('.btn-acoes')[0];
    expect(botaoMenu.getAttribute('aria-label')).toBe('Mais opções para Decisão de início');
    expect(botaoMenu.getAttribute('aria-expanded')).toBe('false');
    clicar(botaoMenu);
    expect(botaoMenu.getAttribute('aria-expanded')).toBe('true');
    expect([...el().querySelectorAll('.dropdown-menu button')].map((b) => b.textContent?.trim())).toEqual(['Editar', 'Permissões', 'Itens documentais', 'Desativar']);
  });

  it('falha ao listar: app-error-banner e os seletores continuam a funcionar', () => {
    configurar();
    servico['listarProcedimentosConfig'].mockReturnValue(throwError(() => new ApiError('ERRO_REDE', 'Sem ligação.', 't', null)));
    clicar(botao('Ativos'));
    expect(el().querySelector('app-error-banner .error-banner')).not.toBeNull();

    servico['listarProcedimentosConfig'].mockReturnValue(of(pagina([DECISAO])));
    escolher(campo('Tipo de Instrumento', el()), '3');
    expect(el().querySelector('app-tabela')).not.toBeNull();
  });
});
