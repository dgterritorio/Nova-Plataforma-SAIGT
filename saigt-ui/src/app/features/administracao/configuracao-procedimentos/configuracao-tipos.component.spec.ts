import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { ApiError } from '../../../core/models/error-response.model';
import { PageResponse } from '../../../core/models/page-response.model';
import { TipoDinamicaConfig, TipoInstrumentoConfig, TipoProcedimentoConfig } from '../../../models/configuracao.model';
import { ConfiguracaoService } from '../../../services/configuracao.service';
import { ConfiguracaoTiposComponent } from './configuracao-tipos.component';

type Mock = Record<
  | 'listarTiposInstrumento'
  | 'listarTiposDinamica'
  | 'listarTiposProcedimento'
  | 'criarTipoInstrumento'
  | 'atualizarTipoInstrumento'
  | 'criarTipoDinamica'
  | 'atualizarTipoDinamica'
  | 'criarTipoProcedimento'
  | 'atualizarTipoProcedimento',
  ReturnType<typeof vi.fn>
>;

const PDM: TipoInstrumentoConfig = { id: 1, codigo: 'PDM', nome: 'Plano Diretor Municipal', categoria: 'PLANO', ativo: true };
const PROT: TipoInstrumentoConfig = { id: 2, codigo: 'PROT', nome: 'Programa Regional', categoria: 'PROGRAMA', ativo: false };
const ALTERACAO: TipoDinamicaConfig = { id: 5, codigo: 'ALTERACAO', nome: 'Alteração', ativo: true };
const DECISAO: TipoProcedimentoConfig = { id: 7, codigo: 'DECISAO_INICIO', nome: 'Decisão de início', ativo: true, formulario: 'INFORMACAO_GERAL' };
const PARECER: TipoProcedimentoConfig = { id: 8, codigo: 'PARECER', nome: 'Parecer', ativo: true, formulario: null };

function pagina<T>(linhas: T[], total = linhas.length): PageResponse<T> {
  return { content: linhas, page: 0, size: 20, totalElements: total, totalPages: Math.ceil(total / 20) };
}

describe('ConfiguracaoTiposComponent', () => {
  let fixture: ComponentFixture<ConfiguracaoTiposComponent>;
  let servico: Mock;

  function configurar(): void {
    servico = {
      listarTiposInstrumento: vi.fn().mockReturnValue(of(pagina([PDM, PROT], 25))),
      listarTiposDinamica: vi.fn().mockReturnValue(of(pagina([ALTERACAO]))),
      listarTiposProcedimento: vi.fn().mockReturnValue(of(pagina([DECISAO, PARECER]))),
      criarTipoInstrumento: vi.fn(),
      atualizarTipoInstrumento: vi.fn(),
      criarTipoDinamica: vi.fn(),
      atualizarTipoDinamica: vi.fn(),
      criarTipoProcedimento: vi.fn(),
      atualizarTipoProcedimento: vi.fn(),
    };
    TestBed.configureTestingModule({
      imports: [ConfiguracaoTiposComponent],
      providers: [{ provide: ConfiguracaoService, useValue: servico }],
    });
    fixture = TestBed.createComponent(ConfiguracaoTiposComponent);
    fixture.detectChanges();
  }

  afterEach(() => vi.useRealTimers());

  const el = (): HTMLElement => fixture.nativeElement as HTMLElement;
  const dialogo = (): HTMLElement | null => document.querySelector('[role="dialog"]');
  const cabecalhos = (): string[] => [...el().querySelectorAll('th[scope="col"]')].map((th) => th.textContent!.trim());
  const botao = (texto: string, raiz: ParentNode = el()): HTMLButtonElement =>
    [...raiz.querySelectorAll<HTMLButtonElement>('button')].find((b) => b.textContent?.trim() === texto)!;
  const campo = (rotulo: string): HTMLInputElement => {
    const label = [...dialogo()!.querySelectorAll('label')].find((l) => l.textContent?.trim().startsWith(rotulo))!;
    return document.getElementById(label.htmlFor) as HTMLInputElement;
  };

  function clicar(alvo: HTMLElement): void {
    alvo.click();
    fixture.detectChanges();
  }

  function escrever(entrada: HTMLInputElement, valor: string): void {
    entrada.value = valor;
    entrada.dispatchEvent(new Event('input'));
    fixture.detectChanges();
  }

  function abrirMenu(indice = 0): HTMLButtonElement {
    const botaoMenu = el().querySelectorAll<HTMLButtonElement>('.btn-acoes')[indice];
    botaoMenu.focus();
    clicar(botaoMenu);
    return botaoMenu;
  }

  it('abre nos tipos de Instrumento, 20 por página, com Código, Nome, Categoria, Estado e Ações', () => {
    configurar();

    expect(servico.listarTiposInstrumento).toHaveBeenCalledWith({ ativo: undefined }, 0, 20);
    expect(cabecalhos()).toEqual(['Código', 'Nome', 'Categoria', 'Estado', 'Ações']);
    expect(el().querySelector('caption')?.textContent).toContain('Tipos de Instrumento');
    const linhas = el().querySelectorAll('tbody tr');
    expect(linhas[0].querySelector('.coluna-categoria')?.textContent?.trim()).toBe('Plano');
    expect(linhas[1].querySelector('.coluna-categoria')?.textContent?.trim()).toBe('Programa');
    expect(linhas[0].querySelector('.coluna-estado')?.textContent?.trim()).toBe('Ativo');
    expect(linhas[1].querySelector('.coluna-estado')?.textContent?.trim()).toBe('Inativo');
    expect(el().querySelector('.contador')?.textContent).toContain('25 tipos');
    const catalogo = el().querySelector('[role="group"][aria-label="Catálogo"]')!;
    expect(botao('Tipos de Instrumento', catalogo).getAttribute('aria-pressed')).toBe('true');
  });

  it('Tipos de Procedimento: coluna Formulário com "Informação Geral" e o selo "Sem formulário"; sem Categoria', () => {
    configurar();
    clicar(botao('Tipos de Procedimento'));

    expect(servico.listarTiposProcedimento).toHaveBeenCalledWith({ ativo: undefined }, 0, 20);
    expect(cabecalhos()).toEqual(['Código', 'Nome', 'Formulário', 'Estado', 'Ações']);
    const linhas = el().querySelectorAll('tbody tr');
    expect(linhas[0].querySelector('.coluna-formulario')?.textContent?.trim()).toBe('Informação Geral');
    expect(linhas[1].querySelector('.coluna-formulario app-selo')?.textContent?.trim()).toBe('Sem formulário');
    expect(botao('Tipos de Procedimento').getAttribute('aria-pressed')).toBe('true');
  });

  it('os outros formulários fixos aparecem por extenso', () => {
    configurar();
    servico.listarTiposProcedimento.mockReturnValue(
      of(pagina([
        { ...PARECER, id: 20, formulario: 'DISCUSSAO_PUBLICA' },
        { ...PARECER, id: 21, formulario: 'PUBLICACAO_ALTERACAO' },
      ])),
    );
    clicar(botao('Tipos de Procedimento'));
    expect([...el().querySelectorAll('.coluna-formulario')].map((td) => td.textContent?.trim())).toEqual([
      'Discussão Pública',
      'Publicação da Alteração',
    ]);
  });

  it('Tipos de Dinâmica: sem Categoria nem Formulário', () => {
    configurar();
    clicar(botao('Tipos de Dinâmica'));
    expect(servico.listarTiposDinamica).toHaveBeenCalledWith({ ativo: undefined }, 0, 20);
    expect(cabecalhos()).toEqual(['Código', 'Nome', 'Estado', 'Ações']);
  });

  it('filtro Inativos pede ativo=false na página 0; Ativos pede ativo=true', () => {
    configurar();
    fixture.componentInstance.irParaPagina(1);
    clicar(botao('Inativos'));
    expect(servico.listarTiposInstrumento).toHaveBeenLastCalledWith({ ativo: false }, 0, 20);
    expect(botao('Inativos').getAttribute('aria-pressed')).toBe('true');
    clicar(botao('Ativos'));
    expect(servico.listarTiposInstrumento).toHaveBeenLastCalledWith({ ativo: true }, 0, 20);
  });

  it('mudar de página lista essa página', () => {
    configurar();
    fixture.componentInstance.irParaPagina(1);
    expect(servico.listarTiposInstrumento).toHaveBeenLastCalledWith({ ativo: undefined }, 1, 20);
  });

  it('cria um tipo de Dinâmica: POST, o modal fecha, "Tipo criado." e a lista recarrega', () => {
    vi.useFakeTimers();
    configurar();
    clicar(botao('Tipos de Dinâmica'));
    servico.criarTipoDinamica.mockReturnValue(of({ id: 9, codigo: 'ALTERACAO_X', nome: 'Alteração X', ativo: true }));
    servico.listarTiposDinamica.mockReturnValue(of(pagina([ALTERACAO, { id: 9, codigo: 'ALTERACAO_X', nome: 'Alteração X', ativo: true }])));

    clicar(botao('Adicionar tipo'));
    expect(dialogo()?.textContent).toContain('Adicionar tipo');
    escrever(campo('Código'), 'ALTERACAO_X');
    escrever(campo('Nome'), 'Alteração X');
    clicar(botao('Guardar', dialogo()!));

    expect(servico.criarTipoDinamica).toHaveBeenCalledWith({ codigo: 'ALTERACAO_X', nome: 'Alteração X', ativo: true });
    expect(dialogo()).toBeNull();
    expect(el().querySelector('app-alerta[role="status"]')?.textContent).toContain('Tipo criado.');
    expect(el().textContent).toContain('ALTERACAO_X');
    vi.advanceTimersByTime(4000);
    fixture.detectChanges();
    expect(el().querySelector('app-alerta[role="status"]')).toBeNull();
  });

  it('código "alteracao x": erro no campo Código e nenhum pedido', () => {
    configurar();
    clicar(botao('Adicionar tipo'));
    escrever(campo('Código'), 'alteracao x');
    clicar(botao('Guardar', dialogo()!));
    expect(dialogo()?.textContent).toContain('maiúsculas');
    expect(servico.criarTipoInstrumento).not.toHaveBeenCalled();
  });

  it('código repetido: 400 com campos[0].path = "codigo" fica no campo e o modal continua aberto', () => {
    configurar();
    clicar(botao('Tipos de Dinâmica'));
    servico.criarTipoDinamica.mockReturnValue(
      throwError(() => new ApiError('VALIDACAO', 'Pedido inválido.', 't', [{ path: 'codigo', erro: 'Código já existe.' }])),
    );
    clicar(botao('Adicionar tipo'));
    escrever(campo('Código'), 'ALTERACAO');
    escrever(campo('Nome'), 'Alteração');
    clicar(botao('Guardar', dialogo()!));

    expect(dialogo()).not.toBeNull();
    const ids = campo('Código').getAttribute('aria-describedby')!.split(' ');
    expect(ids.map((id) => document.getElementById(id)?.textContent?.trim())).toContain('Código já existe.');
  });

  it('editar o código de ALTERACAO com 409 EM_USO: alerta de erro no modal, que não fecha', () => {
    configurar();
    clicar(botao('Tipos de Dinâmica'));
    servico.atualizarTipoDinamica.mockReturnValue(throwError(() => new ApiError('EM_USO', 'O código já está em uso.', 't', null)));

    abrirMenu();
    clicar(botao('Editar'));
    expect(campo('Código').value).toBe('ALTERACAO');
    escrever(campo('Código'), 'ALTERACAO_2');
    clicar(botao('Guardar', dialogo()!));

    expect(servico.atualizarTipoDinamica).toHaveBeenCalledWith(5, { codigo: 'ALTERACAO_2', nome: 'Alteração', ativo: true });
    expect(dialogo()?.querySelector('app-alerta[role="alert"]')?.textContent).toContain('O código já está em uso.');
  });

  it('menu de ações: aria-label com o código, aria-expanded e Editar/Desativar ou Reativar', () => {
    configurar();
    const botoes = el().querySelectorAll<HTMLButtonElement>('.btn-acoes');
    expect(botoes[0].getAttribute('aria-label')).toBe('Mais opções para PDM');
    expect(botoes[0].getAttribute('aria-expanded')).toBe('false');
    abrirMenu(1);
    expect(botoes[1].getAttribute('aria-expanded')).toBe('true');
    expect(botao('Reativar')).toBeTruthy();
    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }));
    fixture.detectChanges();
    expect(el().querySelector('.dropdown-menu')).toBeNull();
    expect(document.activeElement).toBe(botoes[1]);
  });

  it('Desativar pede confirmação e faz PUT com ativo=false e os restantes campos da linha', () => {
    configurar();
    servico.atualizarTipoInstrumento.mockReturnValue(of({ ...PDM, ativo: false }));
    servico.listarTiposInstrumento.mockReturnValue(of(pagina([{ ...PDM, ativo: false }, PROT])));

    abrirMenu();
    clicar(botao('Desativar'));
    expect(dialogo()?.textContent).toContain(
      'Desativar o tipo Plano Diretor Municipal? Deixa de ser oferecido em novos processos; os processos em curso não são afetados.',
    );
    clicar(botao('Sim', dialogo()!));

    expect(servico.atualizarTipoInstrumento).toHaveBeenCalledWith(1, { codigo: 'PDM', nome: 'Plano Diretor Municipal', ativo: false, categoria: 'PLANO' });
    expect(dialogo()).toBeNull();
    expect(el().querySelectorAll('tbody tr')[0].querySelector('.coluna-estado')?.textContent?.trim()).toBe('Inativo');
  });

  it('Reativar faz PUT com ativo=true', () => {
    configurar();
    servico.atualizarTipoInstrumento.mockReturnValue(of({ ...PROT, ativo: true }));
    abrirMenu(1);
    clicar(botao('Reativar'));
    clicar(botao('Sim', dialogo()!));
    expect(servico.atualizarTipoInstrumento).toHaveBeenCalledWith(2, { codigo: 'PROT', nome: 'Programa Regional', ativo: true, categoria: 'PROGRAMA' });
  });

  it('403 ao desativar: a mensagem aparece no banner de ação e a tabela continua', () => {
    configurar();
    servico.atualizarTipoInstrumento.mockReturnValue(throwError(() => new ApiError('SEM_PERMISSAO', 'Sem permissão.', 't', null)));
    abrirMenu();
    clicar(botao('Desativar'));
    clicar(botao('Sim', dialogo()!));
    expect(el().querySelector('.error-banner')?.textContent).toContain('Não tem permissão');
    expect(el().querySelector('app-tabela')).not.toBeNull();
  });

  it('falha de rede a listar: app-error-banner no lugar da tabela e os filtros continuam a funcionar', () => {
    configurar();
    servico.listarTiposDinamica.mockReturnValue(throwError(() => new ApiError('ERRO_REDE', 'Sem ligação ao servidor.', 't', null)));
    clicar(botao('Tipos de Dinâmica'));

    expect(el().querySelector('app-error-banner .error-banner')).not.toBeNull();
    expect(el().querySelector('app-tabela')).toBeNull();
    clicar(botao('Tipos de Instrumento'));
    expect(el().querySelector('app-tabela')).not.toBeNull();
  });

  it('Esc fecha o modal e o foco volta ao botão de origem', async () => {
    configurar();
    const adicionar = botao('Adicionar tipo');
    adicionar.focus();
    clicar(adicionar);
    await new Promise((resolve) => setTimeout(resolve));
    expect(dialogo()?.contains(document.activeElement)).toBe(true);

    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }));
    fixture.detectChanges();

    expect(dialogo()).toBeNull();
    expect(document.activeElement).toBe(adicionar);
  });

  it('depois de gravar a edição, a lista recarrega sem esqueleto e o foco volta ao botão "···" da linha', async () => {
    configurar();
    servico.atualizarTipoInstrumento.mockReturnValue(of(PDM));
    const botaoMenu = abrirMenu();
    clicar(botao('Editar'));
    await new Promise((resolve) => setTimeout(resolve));
    clicar(botao('Guardar', dialogo()!));

    expect(servico.listarTiposInstrumento).toHaveBeenCalledTimes(2);
    expect(dialogo()).toBeNull();
    expect(document.activeElement).toBe(botaoMenu);
    expect(botaoMenu.isConnected).toBe(true);
  });

  it('o modal aberto a partir do menu devolve o foco ao botão "···" da linha', async () => {
    configurar();
    const botaoMenu = abrirMenu();
    clicar(botao('Editar'));
    await new Promise((resolve) => setTimeout(resolve));
    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }));
    fixture.detectChanges();
    expect(document.activeElement).toBe(botaoMenu);
  });
});
