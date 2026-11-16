import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { Subject, of, throwError } from 'rxjs';
import { describe, expect, it, vi } from 'vitest';
import {
  AlteracaoOwnerComponent,
  MSG_NUMERO_EM_FALTA,
  MSG_NUMERO_INVALIDO,
  MSG_PEDIDO_APOIO,
  MSG_PEDIDO_RECUSADO,
  MSG_PEDIDO_RESOLVIDO,
  MSG_PROCESSO_INEXISTENTE,
  MSG_SEM_CANDIDATOS,
  MSG_VERSAO_DESATUALIZADA,
} from './alteracao-owner.component';
import { AuthService } from '../../../core/auth/auth.service';
import { ApiError } from '../../../core/models/error-response.model';
import { PageResponse } from '../../../core/models/page-response.model';
import { ProcessoDetalhe, ProcessoListagem } from '../../../models/processo.model';
import { Utilizador } from '../../../models/utilizador.model';
import { ProcessoService } from '../../../services/processo.service';
import { UtilizadorService } from '../../../services/utilizador.service';

type Mock = ReturnType<typeof vi.fn>;

const PROCESSO_ID = '0b8f2c1e-3d4a-4b5c-8d6e-7f8091a2b3c4';
const OUTRO_PROCESSO_ID = '1c9f3d2e-4e5b-4c6d-9e7f-8091a2b3c4d5';
const PEDIDO_ID = '7a1b2c3d-4e5f-4a6b-8c7d-9e0f1a2b3c4d';
const ROTA = '/administracao/alteracao-owner';

function pagina<T>(content: T[]): PageResponse<T> {
  return { content, page: 0, size: content.length, totalElements: content.length, totalPages: 1 };
}

/** Só os campos que o ecrã lê; dados fictícios. */
function detalhe(alteracoes: Partial<ProcessoDetalhe> = {}): ProcessoDetalhe {
  return {
    id: PROCESSO_ID,
    numeroProcesso: '20260000123',
    versao: 5,
    estado: 'EM_SUBMISSAO',
    criadoPorId: 'u-helena',
    autorNome: 'Helena Marques',
    entidadeId: 7,
    entidadeNome: 'Câmara Municipal de Sintra',
    designacaoInstrumento: 'Plano Diretor Municipal de Sintra',
    numeroInstrumento: 'PDM-1111',
    nomeDinamica: 'Revisão',
    nomeProcedimento: 'Deliberação de início',
    acoesDisponiveis: [],
    ...alteracoes,
  } as ProcessoDetalhe;
}

function utilizador(id: string, nome: string, email: string): Utilizador {
  return { id, nome, email, origemAutenticacao: 'INCM', perfilInterno: null, cargo: null, entidades: [], ultimoLoginEm: null };
}

const HELENA = utilizador('u-helena', 'Helena Marques', 'helena@exemplo.pt');
const RICARDO = utilizador('u-ricardo', 'Ricardo Cascais', 'ricardo@exemplo.pt');
const SOFIA = utilizador('u-sofia', 'Sofia Lemos', 'sofia@exemplo.pt');

interface Montagem {
  harness: RouterTestingHarness;
  processoService: Record<'listar' | 'obter' | 'reatribuirAutor', Mock>;
  utilizadorService: Record<'listar', Mock>;
  el: () => HTMLElement;
  detectar: () => void;
}

async function montar(
  opcoes: { url?: string; processoService?: Partial<Record<'listar' | 'obter' | 'reatribuirAutor', Mock>>; candidatos?: Mock } = {},
): Promise<Montagem> {
  const processoService = {
    listar: vi.fn().mockReturnValue(of(pagina([{ id: PROCESSO_ID } as ProcessoListagem]))),
    obter: vi.fn().mockReturnValue(of(detalhe())),
    reatribuirAutor: vi.fn().mockReturnValue(of(detalhe({ criadoPorId: 'u-ricardo', versao: 6 }))),
    ...opcoes.processoService,
  };
  const utilizadorService = { listar: opcoes.candidatos ?? vi.fn().mockReturnValue(of(pagina([HELENA, RICARDO, SOFIA]))) };
  TestBed.configureTestingModule({
    providers: [
      provideRouter([{ path: 'administracao/alteracao-owner', component: AlteracaoOwnerComponent }]),
      { provide: ProcessoService, useValue: processoService },
      { provide: UtilizadorService, useValue: utilizadorService },
      {
        provide: AuthService,
        useValue: {
          perfilInterno: vi.fn().mockReturnValue('MASTER'),
          nome: vi.fn().mockReturnValue('Ana Silva'),
          origem: vi.fn().mockReturnValue('AD_DGT'),
          entidade: vi.fn().mockReturnValue(null),
        },
      },
    ],
  });
  const harness = await RouterTestingHarness.create();
  await harness.navigateByUrl(opcoes.url ?? ROTA, AlteracaoOwnerComponent);
  const el = () => harness.routeNativeElement as HTMLElement;
  return { harness, processoService, utilizadorService, el, detectar: () => harness.detectChanges() };
}

function escreverNumero(m: Montagem, valor: string): void {
  const input = m.el().querySelector<HTMLInputElement>('app-campo-texto input')!;
  input.value = valor;
  input.dispatchEvent(new Event('input'));
  m.detectar();
}

/** Como o Enter no campo: o `submit` do formulário. */
function submeter(m: Montagem): void {
  m.el().querySelector('form')!.dispatchEvent(new Event('submit', { cancelable: true }));
  m.detectar();
}

async function procurar(m: Montagem, valor = '20260000123'): Promise<void> {
  escreverNumero(m, valor);
  submeter(m);
  await m.harness.fixture.whenStable();
  m.detectar();
}

function escolherOwner(m: Montagem, id: string): void {
  const select = m.el().querySelector<HTMLSelectElement>('app-seletor select')!;
  select.value = id;
  select.dispatchEvent(new Event('change'));
  m.detectar();
}

const botaoGravar = (m: Montagem) => m.el().querySelector<HTMLButtonElement>('button.gravar')!;
const erroDoCampo = (m: Montagem) => m.el().querySelector('.campo-texto-erro')?.textContent?.trim() ?? null;
const banner = (m: Montagem) => m.el().querySelector('.error-banner')?.textContent?.trim() ?? null;
const avisoPedido = (m: Montagem) => m.el().querySelector('app-alerta.pedido-apoio')?.textContent?.trim() ?? null;
const opcoes = (m: Montagem) => [...m.el().querySelectorAll('app-seletor option')].map((o) => o.textContent?.trim());

describe('AlteracaoOwnerComponent', () => {
  it('mostra o título, o subtítulo e o campo do n.º, sem cartão do processo', async () => {
    const m = await montar();

    expect(m.el().querySelector('h1')?.textContent?.trim()).toBe('Alteração de Owner');
    expect(m.el().querySelector('.subtitulo')?.textContent?.trim()).toBe(
      'Transferir a autoria de um processo para outro utilizador da mesma entidade.',
    );
    const input = m.el().querySelector('app-campo-texto input')!;
    expect(input.getAttribute('inputmode')).toBe('numeric');
    expect(m.el().querySelector('app-campo-texto label')?.textContent).toContain('N.º do processo');
    expect(m.el().querySelector('.processo')).toBeNull();
  });

  describe('pesquisa', () => {
    it.each([
      ['letras', '12a4', MSG_NUMERO_INVALIDO],
      ['mais de 20 dígitos', '123456789012345678901', MSG_NUMERO_INVALIDO],
      ['vazio', '   ', MSG_NUMERO_EM_FALTA],
    ])('%s: a mensagem fica no campo e o pedido não é feito', async (_caso, valor, mensagem) => {
      const m = await montar();

      await procurar(m, valor);

      expect(m.processoService.listar).not.toHaveBeenCalled();
      expect(erroDoCampo(m)).toBe(mensagem);
      const input = m.el().querySelector('app-campo-texto input')!;
      expect(input.getAttribute('aria-invalid')).toBe('true');
      expect(input.getAttribute('aria-describedby')).toBe(m.el().querySelector('.campo-texto-erro')!.id);
    });

    it('um número sem processo: a mensagem no campo e nenhum cartão', async () => {
      const m = await montar({ processoService: { listar: vi.fn().mockReturnValue(of(pagina([]))) } });

      await procurar(m, '99999');

      expect(m.processoService.listar).toHaveBeenCalledWith({ numeroProcesso: '99999' }, 0, 1);
      expect(m.processoService.obter).not.toHaveBeenCalled();
      expect(erroDoCampo(m)).toBe(MSG_PROCESSO_INEXISTENTE);
      expect(m.el().querySelector('.processo')).toBeNull();
    });

    it('um número existente (Enter): lê o detalhe e mostra o cartão com os dados e o owner atual, com o foco no cartão', async () => {
      const m = await montar();

      await procurar(m, ' 20260000123 ');

      expect(m.processoService.listar).toHaveBeenCalledWith({ numeroProcesso: '20260000123' }, 0, 1);
      expect(m.processoService.obter).toHaveBeenCalledWith(PROCESSO_ID);
      const cartao = m.el().querySelector<HTMLElement>('.processo')!;
      const texto = cartao.textContent ?? '';
      expect(cartao.querySelector('h2')?.textContent?.trim()).toBe('Processo n.º 20260000123');
      expect(texto).toContain('Plano Diretor Municipal de Sintra · PDM-1111');
      expect(texto).toContain('Revisão');
      expect(texto).toContain('Deliberação de início');
      expect(texto).toContain('Câmara Municipal de Sintra');
      expect(cartao.querySelector('app-estado-processo-badge')?.textContent?.trim()).toBe('Em submissão');
      expect(cartao.querySelector('.owner-atual')?.textContent?.replace(/\s+/g, ' ').trim()).toBe('Owner atual: Helena Marques');
      expect(cartao.querySelector('a')?.getAttribute('href')).toBe(`/processos/${PROCESSO_ID}`);
      expect(cartao.querySelector('a')?.textContent?.trim()).toBe('Abrir processo');
      expect(document.activeElement).toBe(cartao);
    });

    it('um 400 da pesquisa vai para o campo; outros erros para o banner', async () => {
      const erro400 = new ApiError('VALIDACAO', 'Existem campos por corrigir.', 't', [{ path: 'numeroProcesso', erro: 'Formato inválido.' }]);
      const m = await montar({ processoService: { listar: vi.fn().mockReturnValue(throwError(() => erro400)) } });

      await procurar(m, '123');
      expect(erroDoCampo(m)).toBe('Formato inválido.');
      expect(banner(m)).toBeNull();

      m.processoService.listar.mockReturnValue(throwError(() => new ApiError('SEM_PERMISSAO', 'x', 't', null)));
      await procurar(m, '124');
      expect(erroDoCampo(m)).toBeNull();
      expect(banner(m)).toBe('Não tem permissão para executar esta ação.');
    });

    it('uma nova pesquisa limpa o cartão, o seletor e as mensagens', async () => {
      const m = await montar();
      await procurar(m);
      escolherOwner(m, 'u-ricardo');
      botaoGravar(m).click();
      m.detectar();
      expect(m.el().querySelector('app-alerta')).not.toBeNull();

      m.processoService.listar.mockReturnValue(of(pagina([])));
      await procurar(m, '555');

      expect(m.el().querySelector('.processo')).toBeNull();
      expect(m.el().querySelector('app-alerta')).toBeNull();
      expect(erroDoCampo(m)).toBe(MSG_PROCESSO_INEXISTENTE);
    });
  });

  describe('candidatos', () => {
    it('lista os utilizadores com vínculo à entidade do processo, sem o owner atual, como «nome — email»', async () => {
      const m = await montar();
      await procurar(m);

      expect(m.utilizadorService.listar).toHaveBeenCalledWith({ entidadeId: 7 }, 0, 100);
      expect(opcoes(m)).toEqual(['Selecione…', 'Ricardo Cascais — ricardo@exemplo.pt', 'Sofia Lemos — sofia@exemplo.pt']);
      expect(m.el().querySelector('app-seletor label')?.textContent).toContain('Novo owner');
      expect(m.el().querySelector<HTMLSelectElement>('app-seletor select')!.disabled).toBe(false);
    });

    it('enquanto carregam: «A carregar…» e o seletor desativado', async () => {
      const pendente = new Subject<PageResponse<Utilizador>>();
      const m = await montar({ candidatos: vi.fn().mockReturnValue(pendente) });
      await procurar(m);

      expect(opcoes(m)).toEqual(['A carregar…']);
      expect(m.el().querySelector<HTMLSelectElement>('app-seletor select')!.disabled).toBe(true);
    });

    it('sem outros utilizadores: a mensagem e o seletor desativado', async () => {
      const m = await montar({ candidatos: vi.fn().mockReturnValue(of(pagina([HELENA]))) });
      await procurar(m);

      expect(opcoes(m)).toEqual([MSG_SEM_CANDIDATOS]);
      expect(m.el().querySelector<HTMLSelectElement>('app-seletor select')!.disabled).toBe(true);
      expect(botaoGravar(m).disabled).toBe(true);
    });

    it('se a lista falha: «Indisponível» e o erro no banner', async () => {
      const m = await montar({
        candidatos: vi.fn().mockReturnValue(throwError(() => new ApiError('SEM_PERMISSAO', 'x', 't', null))),
      });
      await procurar(m);

      expect(opcoes(m)).toEqual(['Indisponível']);
      expect(banner(m)).toBe('Não tem permissão para executar esta ação.');
    });
  });

  describe('gravar', () => {
    it('«Gravar» só fica ativo com um novo owner escolhido', async () => {
      const m = await montar();
      await procurar(m);

      expect(botaoGravar(m).disabled).toBe(true);
      escolherOwner(m, 'u-ricardo');
      expect(botaoGravar(m).disabled).toBe(false);
    });

    it('envia a versao e o novo owner, sem motivo; mostra «Owner alterado para <nome>.», relê o cartão e foca a mensagem', async () => {
      const m = await montar();
      await procurar(m);
      m.processoService.obter.mockReturnValue(of(detalhe({ criadoPorId: 'u-ricardo', autorNome: 'Ricardo Cascais', versao: 6 })));
      escolherOwner(m, 'u-ricardo');

      botaoGravar(m).click();
      m.detectar();
      await m.harness.fixture.whenStable();
      m.detectar();

      expect(m.processoService.reatribuirAutor).toHaveBeenCalledWith(PROCESSO_ID, 5, 'u-ricardo');
      expect(m.processoService.reatribuirAutor.mock.calls[0]).toHaveLength(3);
      const alerta = m.el().querySelector('app-alerta.alerta-sucesso')!;
      expect(alerta.getAttribute('role')).toBe('status');
      expect(alerta.textContent?.trim()).toBe('Owner alterado para Ricardo Cascais.');
      expect(m.el().querySelector('.mensagens')?.getAttribute('aria-live')).toBe('polite');
      expect(document.activeElement).toBe(m.el().querySelector('.mensagens'));
      expect(m.processoService.obter).toHaveBeenCalledTimes(2);
      expect(m.el().querySelector('.owner-atual')?.textContent?.replace(/\s+/g, ' ').trim()).toBe('Owner atual: Ricardo Cascais');
      expect(opcoes(m)).toEqual(['Selecione…', 'Helena Marques — helena@exemplo.pt', 'Sofia Lemos — sofia@exemplo.pt']);
      expect(botaoGravar(m).disabled).toBe(true);
    });

    it('409 VERSAO_DESATUALIZADA: o aviso e o processo relido, sem banner', async () => {
      const erro = new ApiError('VERSAO_DESATUALIZADA', 'O processo foi alterado; recarregue.', 't', null);
      const m = await montar({ processoService: { reatribuirAutor: vi.fn().mockReturnValue(throwError(() => erro)) } });
      await procurar(m);
      m.processoService.obter.mockReturnValue(of(detalhe({ versao: 7 })));
      escolherOwner(m, 'u-ricardo');

      botaoGravar(m).click();
      m.detectar();
      await m.harness.fixture.whenStable();
      m.detectar();

      const aviso = m.el().querySelector('app-alerta.alerta-aviso')!;
      expect(aviso.textContent?.trim()).toBe(MSG_VERSAO_DESATUALIZADA);
      expect(aviso.getAttribute('role')).toBe('status');
      expect(m.processoService.obter).toHaveBeenCalledTimes(2);
      expect(banner(m)).toBeNull();

      escolherOwner(m, 'u-ricardo');
      botaoGravar(m).click();
      expect(m.processoService.reatribuirAutor).toHaveBeenLastCalledWith(PROCESSO_ID, 7, 'u-ricardo');
    });

    it.each([
      ['mesmo autor', 'O utilizador indicado já é o autor do Processo.'],
      ['estado não elegível', 'O processo não pode ser transferido neste estado.'],
    ])('409 ESTADO_INVALIDO (%s): a mensagem da API no app-error-banner', async (_caso, mensagem) => {
      const erro = new ApiError('ESTADO_INVALIDO', mensagem, 't', null);
      const m = await montar({ processoService: { reatribuirAutor: vi.fn().mockReturnValue(throwError(() => erro)) } });
      await procurar(m);
      escolherOwner(m, 'u-ricardo');

      botaoGravar(m).click();
      m.detectar();

      expect(banner(m)).toBe(mensagem);
      expect(m.el().querySelector('app-alerta')).toBeNull();
      expect(m.processoService.obter).toHaveBeenCalledTimes(1);
    });

    it('400 em novoUtilizadorId: o erro fica no seletor, ligado por aria-describedby, sem banner', async () => {
      const erro = new ApiError('VALIDACAO', 'Existem campos por corrigir.', 't', [
        { path: 'novoUtilizadorId', erro: 'O utilizador indicado não pertence à entidade do Instrumento.' },
      ]);
      const m = await montar({ processoService: { reatribuirAutor: vi.fn().mockReturnValue(throwError(() => erro)) } });
      await procurar(m);
      escolherOwner(m, 'u-ricardo');

      botaoGravar(m).click();
      m.detectar();

      const select = m.el().querySelector('app-seletor select')!;
      const erroSeletor = m.el().querySelector('.seletor-erro')!;
      expect(erroSeletor.textContent?.trim()).toBe('O utilizador indicado não pertence à entidade do Instrumento.');
      expect(select.getAttribute('aria-describedby')).toBe(erroSeletor.id);
      expect(select.getAttribute('aria-invalid')).toBe('true');
      expect(banner(m)).toBeNull();
    });

    it.each([
      ['SEM_PERMISSAO', 'Não tem permissão para executar esta ação.'],
      ['NAO_ENCONTRADO', 'Este recurso não existe ou não está disponível para si.'],
    ])('%s: vai para o banner', async (codigo, mensagem) => {
      const m = await montar({
        processoService: { reatribuirAutor: vi.fn().mockReturnValue(throwError(() => new ApiError(codigo, 'x', 't', null))) },
      });
      await procurar(m);
      escolherOwner(m, 'u-ricardo');

      botaoGravar(m).click();
      m.detectar();

      expect(banner(m)).toBe(mensagem);
    });
  });

  describe('entrada com processoId', () => {
    it('salta a pesquisa: lê o detalhe, preenche o número e mostra o cartão', async () => {
      const m = await montar({ url: `${ROTA}?processoId=${PROCESSO_ID}` });
      m.detectar();

      expect(m.processoService.listar).not.toHaveBeenCalled();
      expect(m.processoService.obter).toHaveBeenCalledWith(PROCESSO_ID);
      expect(m.el().querySelector<HTMLInputElement>('app-campo-texto input')!.value).toBe('20260000123');
      expect(m.el().querySelector('.processo')).not.toBeNull();
    });

    it('um processoId que não é UUID é ignorado', async () => {
      const m = await montar({ url: `${ROTA}?processoId=abc` });

      expect(m.processoService.obter).not.toHaveBeenCalled();
      expect(m.el().querySelector('.processo')).toBeNull();
    });
  });
  describe('a partir de um pedido de apoio', () => {
    const ROTA_PEDIDO = `${ROTA}?processoId=${PROCESSO_ID}&pedidoApoioId=${PEDIDO_ID}`;

    async function gravarCom(m: Montagem, owner = 'u-ricardo'): Promise<void> {
      escolherOwner(m, owner);
      botaoGravar(m).click();
      m.detectar();
      await m.harness.fixture.whenStable();
      m.detectar();
    }

    it('com processoId e pedidoApoioId: o cartão sem pesquisa e o aviso visível', async () => {
      const m = await montar({ url: ROTA_PEDIDO });
      m.detectar();

      expect(m.processoService.listar).not.toHaveBeenCalled();
      expect(m.el().querySelector('.processo')).not.toBeNull();
      expect(avisoPedido(m)).toBe(MSG_PEDIDO_APOIO);
      expect(m.el().querySelector('app-alerta.pedido-apoio')?.getAttribute('role')).toBe('status');
    });

    it('grava com o pedidoApoioId e mostra que o pedido ficou resolvido; o aviso sai e o pedido não volta a ir', async () => {
      const m = await montar({ url: ROTA_PEDIDO });
      m.detectar();
      m.processoService.obter.mockReturnValue(of(detalhe({ criadoPorId: 'u-ricardo', autorNome: 'Ricardo Cascais', versao: 6 })));

      await gravarCom(m);

      expect(m.processoService.reatribuirAutor).toHaveBeenCalledWith(PROCESSO_ID, 5, 'u-ricardo', PEDIDO_ID);
      const alerta = m.el().querySelector('app-alerta.alerta-sucesso')!;
      expect(alerta.getAttribute('role')).toBe('status');
      expect(alerta.textContent?.trim()).toBe(`Owner alterado para Ricardo Cascais. ${MSG_PEDIDO_RESOLVIDO}`);
      expect(document.activeElement).toBe(m.el().querySelector('.mensagens'));
      expect(avisoPedido(m)).toBeNull();

      await gravarCom(m, 'u-helena');
      expect(m.processoService.reatribuirAutor).toHaveBeenLastCalledWith(PROCESSO_ID, 6, 'u-helena');
      expect(m.processoService.reatribuirAutor.mock.calls[1]).toHaveLength(3);
    });

    it('400 em pedidoApoioId: a mensagem da API no aviso, sem banner, e o pedido seguinte já não o leva', async () => {
      const erro = new ApiError('VALIDACAO', 'Existem campos por corrigir.', 't', [
        { path: 'pedidoApoioId', erro: 'O pedido de apoio indicado não está pendente.' },
      ]);
      const m = await montar({ url: ROTA_PEDIDO, processoService: { reatribuirAutor: vi.fn().mockReturnValue(throwError(() => erro)) } });
      m.detectar();

      await gravarCom(m);

      expect(m.processoService.reatribuirAutor).toHaveBeenCalledWith(PROCESSO_ID, 5, 'u-ricardo', PEDIDO_ID);
      const aviso = m.el().querySelector('app-alerta.alerta-aviso')!;
      expect(aviso.textContent?.trim()).toBe(`O pedido de apoio indicado não está pendente. ${MSG_PEDIDO_RECUSADO}`);
      expect(aviso.getAttribute('role')).toBe('status');
      expect(document.activeElement).toBe(m.el().querySelector('.mensagens'));
      expect(banner(m)).toBeNull();
      expect(m.el().querySelector('.seletor-erro')).toBeNull();
      expect(avisoPedido(m)).toBeNull();

      m.processoService.reatribuirAutor.mockReturnValue(of(detalhe({ criadoPorId: 'u-ricardo', versao: 6 })));
      await gravarCom(m);
      expect(m.processoService.reatribuirAutor).toHaveBeenLastCalledWith(PROCESSO_ID, 5, 'u-ricardo');
      expect(m.processoService.reatribuirAutor.mock.calls[1]).toHaveLength(3);
      expect(m.el().querySelector('app-alerta.alerta-sucesso')?.textContent?.trim()).toBe('Owner alterado para Ricardo Cascais.');
    });

    it('409 VERSAO_DESATUALIZADA com pedido: relê e a gravação seguinte ainda leva o pedido', async () => {
      const erro = new ApiError('VERSAO_DESATUALIZADA', 'O processo foi alterado; recarregue.', 't', null);
      const m = await montar({ url: ROTA_PEDIDO, processoService: { reatribuirAutor: vi.fn().mockReturnValue(throwError(() => erro)) } });
      m.detectar();
      m.processoService.obter.mockReturnValue(of(detalhe({ versao: 7 })));

      await gravarCom(m);

      expect(avisoPedido(m)).toBe(MSG_PEDIDO_APOIO);
      escolherOwner(m, 'u-ricardo');
      botaoGravar(m).click();
      expect(m.processoService.reatribuirAutor).toHaveBeenLastCalledWith(PROCESSO_ID, 7, 'u-ricardo', PEDIDO_ID);
    });

    it('uma nova pesquisa do mesmo processo mantém o pedido; outro processo deixa de o enviar', async () => {
      const m = await montar({ url: ROTA_PEDIDO });
      m.detectar();

      await procurar(m, '20260000123');
      expect(avisoPedido(m)).toBe(MSG_PEDIDO_APOIO);

      m.processoService.listar.mockReturnValue(of(pagina([{ id: OUTRO_PROCESSO_ID } as ProcessoListagem])));
      m.processoService.obter.mockReturnValue(of(detalhe({ id: OUTRO_PROCESSO_ID, numeroProcesso: '20260000999' })));
      await procurar(m, '20260000999');

      expect(avisoPedido(m)).toBeNull();
      await gravarCom(m);
      expect(m.processoService.reatribuirAutor).toHaveBeenCalledWith(OUTRO_PROCESSO_ID, 5, 'u-ricardo');
      expect(m.processoService.reatribuirAutor.mock.calls[0]).toHaveLength(3);
    });

    it('um número sem processo não larga o pedido', async () => {
      const m = await montar({ url: ROTA_PEDIDO });
      m.detectar();
      m.processoService.listar.mockReturnValue(of(pagina([])));

      await procurar(m, '555');

      expect(avisoPedido(m)).toBe(MSG_PEDIDO_APOIO);
    });

    it('pedido geral (só pedidoApoioId): o aviso, a pesquisa normal e a gravação com o pedido', async () => {
      const m = await montar({ url: `${ROTA}?pedidoApoioId=${PEDIDO_ID}` });
      m.detectar();

      expect(avisoPedido(m)).toBe(MSG_PEDIDO_APOIO);
      expect(m.processoService.obter).not.toHaveBeenCalled();
      expect(m.el().querySelector('.processo')).toBeNull();

      await procurar(m);
      expect(avisoPedido(m)).toBe(MSG_PEDIDO_APOIO);
      await gravarCom(m);
      expect(m.processoService.reatribuirAutor).toHaveBeenCalledWith(PROCESSO_ID, 5, 'u-ricardo', PEDIDO_ID);
    });

    it('um pedidoApoioId que não é UUID é ignorado: sem aviso e sem o pedido na gravação', async () => {
      const m = await montar({ url: `${ROTA}?processoId=${PROCESSO_ID}&pedidoApoioId=abc` });
      m.detectar();

      expect(avisoPedido(m)).toBeNull();
      await gravarCom(m);
      expect(m.processoService.reatribuirAutor.mock.calls[0]).toHaveLength(3);
    });
  });
});
