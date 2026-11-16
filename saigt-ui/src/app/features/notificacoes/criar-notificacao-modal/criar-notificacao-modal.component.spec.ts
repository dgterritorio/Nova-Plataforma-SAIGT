import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { PerfilInterno } from '../../../core/auth/auth.models';
import { AuthService } from '../../../core/auth/auth.service';
import { ApiError } from '../../../core/models/error-response.model';
import { PageResponse } from '../../../core/models/page-response.model';
import { Entidade } from '../../../models/entidade.model';
import { CriarNotificacaoRequest, CriarNotificacaoResponse } from '../../../models/notificacao.model';
import { ProcessoListagem } from '../../../models/processo.model';
import { Utilizador } from '../../../models/utilizador.model';
import { EntidadeService } from '../../../services/entidade.service';
import { NotificacaoService } from '../../../services/notificacao.service';
import { ProcessoService } from '../../../services/processo.service';
import { UtilizadorService } from '../../../services/utilizador.service';
import { ESPERA_PESQUISA_MS } from '../../../shared/ui/campo-pesquisa/campo-pesquisa.component';
import {
  AJUDA_ANEXOS_LOTE,
  CriarNotificacaoModalComponent,
  NotificacaoCriada,
  ProcessoFixo,
  fimDoDiaEmLisboa,
} from './criar-notificacao-modal.component';

const ENTIDADES = [
  { id: 7, nipc: '500000000', nome: 'Município de Ourém', ativo: true },
  { id: 3, nipc: '500000001', nome: 'Câmara Municipal de Sintra', ativo: true },
] as Entidade[];

const UTILIZADORES = [
  { id: 'u1', email: 'ana@exemplo.pt', nome: 'Ana Teixeira' },
  { id: 'u2', email: 'rui@exemplo.pt', nome: 'Rui Matos' },
] as Utilizador[];

const PROCESSO = {
  id: 'p1',
  designacaoInstrumento: 'PDM de Cascais',
  nomeProcedimento: 'Decisão de início',
  entidadeNome: 'Câmara Municipal de Cascais',
  estado: 'PUBLICADO',
} as ProcessoListagem;

function pagina<T>(content: T[]): PageResponse<T> {
  return { content, page: 0, size: 100, totalElements: content.length, totalPages: 1 };
}

describe('CriarNotificacaoModalComponent', () => {
  let fixture: ComponentFixture<CriarNotificacaoModalComponent>;
  let criar: ReturnType<typeof vi.fn>;
  let anexar: ReturnType<typeof vi.fn>;
  let listarProcessos: ReturnType<typeof vi.fn>;
  let listarEntidades: ReturnType<typeof vi.fn>;
  let listarUtilizadores: ReturnType<typeof vi.fn>;
  let criadas: NotificacaoCriada[];
  let fechos: number;
  const perfil = signal<PerfilInterno | null>('MASTER');

  const el = () => fixture.nativeElement as HTMLElement;
  const campo = (nome: string) => el().querySelector<HTMLElement>(`[data-campo="${nome}"]`);
  const botao = (acao: string) => el().querySelector<HTMLButtonElement>(`[data-acao="${acao}"]`)!;

  function detetar(): void {
    fixture.detectChanges();
  }

  function radio(nome: string): void {
    campo(nome)!.click();
    detetar();
  }

  function escrever(nome: string, valor: string): void {
    const entrada = campo(nome)!.querySelector<HTMLInputElement | HTMLTextAreaElement>('input, textarea')!;
    entrada.value = valor;
    entrada.dispatchEvent(new Event('input'));
    entrada.dispatchEvent(new Event('blur'));
    detetar();
  }

  function escolher(nome: string, valor: string): void {
    const seletor = campo(nome)!.querySelector('select')!;
    seletor.value = valor;
    seletor.dispatchEvent(new Event('change'));
    detetar();
  }

  function preencherTextos(): void {
    escrever('titulo', 'Aviso importante');
    escrever('texto', 'Texto da mensagem');
  }

  function pedidoEnviado(): CriarNotificacaoRequest {
    expect(criar).toHaveBeenCalledTimes(1);
    return criar.mock.calls[0][0] as CriarNotificacaoRequest;
  }

  beforeEach(() => {
    criar = vi.fn(() => of({ notificacoes: [], loteId: null }));
    anexar = vi.fn(() => of({ id: 'd1', nomeFicheiro: 'oficio.pdf', tamanhoBytes: 3, criadoEm: '2026-11-13T10:00:00Z' }));
    listarProcessos = vi.fn(() => of(pagina([PROCESSO])));
    listarEntidades = vi.fn(() => of(pagina(ENTIDADES)));
    listarUtilizadores = vi.fn(() => of(pagina(UTILIZADORES)));
    criadas = [];
    fechos = 0;
    perfil.set('MASTER');
    TestBed.configureTestingModule({
      imports: [CriarNotificacaoModalComponent],
      providers: [
        { provide: NotificacaoService, useValue: { criar, anexar } },
        { provide: EntidadeService, useValue: { listar: listarEntidades } },
        { provide: UtilizadorService, useValue: { listar: listarUtilizadores } },
        { provide: ProcessoService, useValue: { listar: listarProcessos } },
        { provide: AuthService, useValue: { perfilInterno: perfil } },
      ],
    });
    fixture = TestBed.createComponent(CriarNotificacaoModalComponent);
    fixture.componentInstance.criada.subscribe((resposta) => criadas.push(resposta));
    fixture.componentInstance.fechar.subscribe(() => fechos++);
    document.body.appendChild(fixture.nativeElement);
    fixture.componentRef.setInput('aberto', true);
    detetar();
  });

  function abrirComoEntidade(processoFixo: ProcessoFixo | null = null): void {
    perfil.set(null);
    listarEntidades.mockClear();
    listarUtilizadores.mockClear();
    fixture.componentRef.setInput('processoFixo', processoFixo);
    fixture.componentRef.setInput('aberto', false);
    detetar();
    fixture.componentRef.setInput('aberto', true);
    detetar();
  }

  afterEach(() => {
    fixture.nativeElement.remove();
    vi.useRealTimers();
  });

  it('abre na Estática para todos, carrega as entidades ativas e os utilizadores, sem Tipologia', () => {
    const texto = el().textContent ?? '';

    expect(texto).toContain('Criar nova notificação');
    expect(texto).toContain('Persiste até o Master a remover');
    expect(texto).toContain('Visível até o destinatário responder');
    expect(texto).not.toContain('ADMIN_DGT');
    expect(texto).not.toContain('Tipologia');
    expect((campo('tipo-estatica') as HTMLInputElement).checked).toBe(true);
    expect((campo('modo-todos') as HTMLInputElement).checked).toBe(true);
    expect(listarEntidades).toHaveBeenCalledWith({ ativo: true }, 0, 100);
    expect(listarUtilizadores).toHaveBeenCalledWith({}, 0, 100);
    expect(el().querySelectorAll('[role="radiogroup"]')).toHaveLength(2);
  });

  it('junta todas as páginas de entidades, para se poder escolher qualquer uma', () => {
    listarEntidades.mockImplementation((_filtro: object, numero: number) =>
      of({ content: [ENTIDADES[numero]], page: numero, size: 100, totalElements: 2, totalPages: 2 }),
    );
    fixture.componentRef.setInput('aberto', false);
    detetar();
    fixture.componentInstance.entidades.set([]);
    fixture.componentRef.setInput('aberto', true);
    detetar();
    radio('modo-entidades');

    expect(listarEntidades).toHaveBeenCalledWith({ ativo: true }, 1, 100);
    const opcoes = [...campo('entidade-a-adicionar')!.querySelectorAll('option')].map((o) => o.textContent?.trim());
    expect(opcoes).toEqual(['Selecione…', 'Câmara Municipal de Sintra', 'Município de Ourém']);
  });

  it('sem título nem mensagem o botão «Criar notificação» está desativado', () => {
    expect(botao('criar').disabled).toBe(true);

    preencherTextos();

    expect(botao('criar').disabled).toBe(false);
  });

  it('na Estática com «1+ Entidades» sem nenhuma adicionada, o botão fica desativado e o campo mostra o erro', () => {
    preencherTextos();
    radio('modo-entidades');

    expect(botao('criar').disabled).toBe(true);
    const seletor = campo('entidade-a-adicionar')!.querySelector('select')!;
    const erro = el().querySelector(`#${seletor.getAttribute('aria-describedby')}`);
    expect(erro?.textContent?.trim()).toBe('Adicione pelo menos uma entidade.');
  });

  it('envia uma Estática para duas entidades com o prazo no fim de 31/12 em Lisboa', () => {
    radio('modo-entidades');
    escolher('entidade-a-adicionar', '7');
    botao('adicionar-entidade').click();
    detetar();
    escolher('entidade-a-adicionar', '3');
    botao('adicionar-entidade').click();
    detetar();
    preencherTextos();
    escrever('prazo', '2026-12-31');

    botao('criar').click();
    detetar();

    expect(pedidoEnviado()).toEqual({
      tipo: 'ESTATICA',
      titulo: 'Aviso importante',
      texto: 'Texto da mensagem',
      prazo: '2026-12-31T23:59:59.999+00:00',
      destinatarios: [{ entidadeId: 7 }, { entidadeId: 3 }],
    });
    expect(criadas).toHaveLength(1);
  });

  it('adiciona utilizadores como selos e remove-os pelo botão «Remover {nome}»', () => {
    radio('modo-utilizadores');
    escolher('utilizador-a-adicionar', 'u1');
    botao('adicionar-utilizador').click();
    detetar();
    escolher('utilizador-a-adicionar', 'u2');
    botao('adicionar-utilizador').click();
    detetar();

    el().querySelector<HTMLButtonElement>('[aria-label="Remover Ana Teixeira"]')!.click();
    detetar();
    preencherTextos();
    botao('criar').click();

    expect(pedidoEnviado().destinatarios).toEqual([{ utilizadorId: 'u2' }]);
  });

  it('a opção do utilizador mostra o nome e o email', () => {
    radio('modo-utilizadores');

    const opcoes = [...campo('utilizador-a-adicionar')!.querySelectorAll('option')].map((o) => o.textContent?.trim());
    expect(opcoes).toContain('Ana Teixeira — ana@exemplo.pt');
  });

  it('na Resposta obrigatória com a entidade, título e mensagem, sem processo nem ID IGT, envia o pedido', () => {
    radio('tipo-resposta');
    expect(el().textContent).toContain('Data limite de resposta (opcional)');
    preencherTextos();
    expect(botao('criar').disabled).toBe(true);

    escolher('destinatario-unico-entidade', '3');
    expect(botao('criar').disabled).toBe(false);
    botao('criar').click();

    expect(pedidoEnviado()).toEqual({
      tipo: 'RESPOSTA_OBRIGATORIA',
      titulo: 'Aviso importante',
      texto: 'Texto da mensagem',
      prazo: undefined,
      processoId: undefined,
      idIgtSnit: undefined,
      descricaoIgt: undefined,
      destinatarios: [{ entidadeId: 3 }],
    });
  });

  it('na Resposta obrigatória pesquisa o processo pela designação e envia-o com o ID IGT', () => {
    vi.useFakeTimers();
    radio('tipo-resposta');
    radio('destinatario-utilizador');
    escolher('destinatario-unico-utilizador', 'u1');
    const pesquisa = campo('pesquisa-processo')!.querySelector('input')!;
    pesquisa.value = 'Cascais';
    pesquisa.dispatchEvent(new Event('input'));
    vi.advanceTimersByTime(ESPERA_PESQUISA_MS);
    detetar();

    expect(listarProcessos).toHaveBeenCalledWith({ designacaoInstrumento: 'Cascais' }, 0, 20);
    const opcoes = [...campo('processo')!.querySelectorAll('option')].map((o) => o.textContent?.trim());
    expect(opcoes).toContain('PDM de Cascais — Decisão de início — Câmara Municipal de Cascais — Publicado');

    escolher('processo', 'p1');
    escrever('id-igt-snit', 'IGT-1');
    preencherTextos();
    botao('criar').click();

    expect(pedidoEnviado()).toMatchObject({ processoId: 'p1', idIgtSnit: 'IGT-1', destinatarios: [{ utilizadorId: 'u1' }] });
  });

  it('com 404 da API fica aberto e mostra a mensagem do envelope', () => {
    criar.mockReturnValue(throwError(() => new ApiError('NOTIF_DESTINATARIO_INEXISTENTE', 'Destinatário inexistente.', 't', null)));
    preencherTextos();

    botao('criar').click();
    detetar();

    expect(criadas).toHaveLength(0);
    expect(fechos).toBe(0);
    expect(el().querySelector('app-alerta[role="alert"]')?.textContent?.trim()).toBe('Destinatário inexistente.');
    expect(botao('criar').disabled).toBe(false);
  });

  it('associa os campos de um 400 de validação ao campo do formulário', () => {
    criar.mockReturnValue(
      throwError(() => new ApiError('VALIDACAO', 'Payload inválido.', 't', [{ path: 'titulo', erro: 'Máximo 200 caracteres.' }])),
    );
    preencherTextos();

    botao('criar').click();
    detetar();

    const entrada = campo('titulo')!.querySelector('input')!;
    const erro = el().querySelector(`#${entrada.getAttribute('aria-describedby')?.split(' ').pop()}`);
    expect(erro?.textContent?.trim()).toBe('Máximo 200 caracteres.');
    expect(entrada.getAttribute('aria-invalid')).toBe('true');
  });

  it('mostra os contadores de caracteres do título e da mensagem', () => {
    escrever('titulo', 'Olá');

    expect(campo('titulo')!.textContent).toContain('3/200 caracteres');
    expect(campo('texto')!.textContent).toContain('0/5000 caracteres');
  });

  it('«Cancelar» sem dados fecha logo', () => {
    botao('cancelar').click();

    expect(fechos).toBe(1);
  });

  it('«Cancelar» depois de preencher pede confirmação antes de descartar', () => {
    preencherTextos();

    botao('cancelar').click();
    detetar();
    expect(fechos).toBe(0);
    expect(el().textContent).toContain('Tem dados preenchidos. Quer descartá-los?');

    botao('continuar').click();
    detetar();
    expect(fechos).toBe(0);
    expect(botao('criar')).toBeTruthy();

    botao('cancelar').click();
    detetar();
    botao('descartar').click();
    expect(fechos).toBe(1);
  });

  it('os rádios do tipo partilham o grupo e os campos têm rótulos', () => {
    const tipos = el().querySelectorAll<HTMLInputElement>('input[type="radio"][value="ESTATICA"], input[type="radio"][value="RESPOSTA_OBRIGATORIA"]');
    expect(new Set([...tipos].map((r) => r.name)).size).toBe(1);
    for (const entrada of el().querySelectorAll<HTMLInputElement>('app-campo-texto input, app-campo-texto textarea')) {
      expect(el().querySelector(`label[for="${entrada.id}"]`)).not.toBeNull();
    }
  });

  describe('utilizador externo', () => {
    it('vê só os cartões «Pedido de apoio» e «Regularização de procedimento», sem destinatários nem prazo', () => {
      abrirComoEntidade();
      const texto = el().textContent ?? '';

      expect(campo('tipo-pedido-apoio')).not.toBeNull();
      expect(campo('tipo-regularizacao')).not.toBeNull();
      expect(campo('tipo-estatica')).toBeNull();
      expect(campo('tipo-resposta')).toBeNull();
      expect(texto).toContain('Resolvido quando o Master responde. Pode ser reaberto.');
      expect(texto).toContain('Procedimento publicado diretamente no DR, sem passar pela SAIGT.');
      expect(texto).not.toContain('Destinatário');
      expect(campo('prazo')).toBeNull();
      expect((campo('tipo-pedido-apoio') as HTMLInputElement).checked).toBe(true);
      expect(listarEntidades).not.toHaveBeenCalled();
      expect(listarUtilizadores).not.toHaveBeenCalled();
    });

    it('o pedido de apoio geral leva só o título e a mensagem, sem processo, prazo nem destinatários', () => {
      abrirComoEntidade();
      preencherTextos();
      botao('criar').click();

      expect(pedidoEnviado()).toEqual({
        tipo: 'PEDIDO_APOIO',
        titulo: 'Aviso importante',
        texto: 'Texto da mensagem',
        processoId: undefined,
        idIgtSnit: undefined,
        descricaoIgt: undefined,
        destinatarios: [],
      });
    });

    it('o pedido de apoio pesquisa o processo e leva-o com o ID e a descrição IGT', () => {
      abrirComoEntidade();
      vi.useFakeTimers();
      const pesquisa = campo('pesquisa-processo')!.querySelector('input')!;
      pesquisa.value = 'Cascais';
      pesquisa.dispatchEvent(new Event('input'));
      vi.advanceTimersByTime(ESPERA_PESQUISA_MS);
      detetar();
      escolher('processo', 'p1');
      escrever('id-igt-snit', 'IGT-9');
      escrever('descricao-igt', 'PDM de Cascais');
      preencherTextos();
      botao('criar').click();

      expect(listarProcessos).toHaveBeenCalledWith({ designacaoInstrumento: 'Cascais' }, 0, 20);
      expect(pedidoEnviado()).toMatchObject({
        tipo: 'PEDIDO_APOIO',
        processoId: 'p1',
        idIgtSnit: 'IGT-9',
        descricaoIgt: 'PDM de Cascais',
      });
    });

    it('na regularização sem «Descrição IGT» o botão fica desativado e o erro fica ligado ao campo', () => {
      abrirComoEntidade();
      radio('tipo-regularizacao');
      preencherTextos();

      expect(campo('processo')).toBeNull();
      expect(campo('descricao-igt')!.textContent).not.toContain('(opcional)');
      expect(campo('texto')!.textContent).toContain('Indique o motivo e a publicação no DR.');
      expect(botao('criar').disabled).toBe(true);
      escrever('descricao-igt', '   ');
      const entrada = campo('descricao-igt')!.querySelector('input')!;
      const erro = el().querySelector(`#${entrada.getAttribute('aria-describedby')?.split(' ').pop()}`);
      expect(erro?.textContent?.trim()).toBe('Indique a descrição IGT.');
      expect(entrada.getAttribute('aria-invalid')).toBe('true');

      escrever('descricao-igt', 'PDM de Ourém');
      expect(botao('criar').disabled).toBe(false);
      botao('criar').click();
      expect(pedidoEnviado()).toEqual({
        tipo: 'REGULARIZACAO_PROCEDIMENTO',
        titulo: 'Aviso importante',
        texto: 'Texto da mensagem',
        processoId: undefined,
        idIgtSnit: undefined,
        descricaoIgt: 'PDM de Ourém',
        destinatarios: [],
      });
    });

    it('o erro da «Descrição IGT» aparece ao sair do campo vazio, mesmo depois de outro campo já ter sido tocado', () => {
      abrirComoEntidade();
      radio('tipo-regularizacao');
      campo('tipo-regularizacao')!.dispatchEvent(new Event('blur'));
      const entrada = campo('descricao-igt')!.querySelector('input')!;
      entrada.dispatchEvent(new Event('blur'));
      detetar();

      const erro = el().querySelector(`#${entrada.getAttribute('aria-describedby')?.split(' ').pop()}`);
      expect(erro?.textContent?.trim()).toBe('Indique a descrição IGT.');
    });

    it('voltar ao pedido de apoio torna a «Descrição IGT» opcional', () => {
      abrirComoEntidade();
      radio('tipo-regularizacao');
      radio('tipo-pedido-apoio');
      preencherTextos();

      expect(botao('criar').disabled).toBe(false);
    });

    it('com o processo fixo abre no pedido de apoio com o processo preenchido e não editável', () => {
      abrirComoEntidade({ id: 'p9', rotulo: '2026000777 — PDM de Teste — Decisão de início' });
      const processo = campo('processo-fixo')!.querySelector('input')!;

      expect(processo.value).toBe('2026000777 — PDM de Teste — Decisão de início');
      expect(processo.disabled).toBe(true);
      expect(campo('pesquisa-processo')).toBeNull();
      expect(campo('tipo-regularizacao')).toBeNull();
      preencherTextos();
      botao('criar').click();
      expect(pedidoEnviado()).toMatchObject({ tipo: 'PEDIDO_APOIO', processoId: 'p9', destinatarios: [] });
    });

    it('os dois cartões são rádios do mesmo grupo, com rótulo', () => {
      abrirComoEntidade();
      const radios = [...el().querySelectorAll<HTMLInputElement>('input[type="radio"][name="tipo"]')];

      expect(radios).toHaveLength(2);
      expect(radios.every((r) => r.closest('label') !== null)).toBe(true);
      expect(el().querySelector('fieldset[role="radiogroup"] legend')?.textContent?.trim()).toBe('Tipo');
    });
  });


  describe('Documentos (opcional)', () => {
    function escolherFicheiros(...ficheiros: File[]): void {
      const entrada = campo('documentos')!.querySelector<HTMLInputElement>('input[type="file"]')!;
      Object.defineProperty(entrada, 'files', { value: ficheiros, configurable: true });
      entrada.dispatchEvent(new Event('change'));
      detetar();
    }

    const resumo = (id: string) => ({ id }) as CriarNotificacaoResponse['notificacoes'][number];

    it('numa Notícia para todos cria a notificação e só depois sobe o ficheiro para ela', () => {
      criar.mockReturnValue(of({ notificacoes: [resumo('n9')], loteId: null }));
      const ficheiro = new File(['abc'], 'oficio.pdf', { type: 'application/pdf' });
      preencherTextos();
      escolherFicheiros(ficheiro);

      expect(el().textContent).toContain('oficio.pdf');
      botao('criar').click();
      detetar();

      expect(criar).toHaveBeenCalledTimes(1);
      expect(anexar).toHaveBeenCalledWith('n9', ficheiro);
      expect(criar.mock.invocationCallOrder[0]).toBeLessThan(anexar.mock.invocationCallOrder[0]);
      expect(criadas).toEqual([{ notificacoes: [resumo('n9')], loteId: null, anexosFalhados: 0 }]);
    });

    it('conta os ficheiros que falharam, para a página avisar', () => {
      criar.mockReturnValue(of({ notificacoes: [resumo('n9')], loteId: null }));
      anexar.mockReturnValueOnce(throwError(() => new ApiError('NOTIF_EXTENSAO_NAO_ACEITE', 'Extensão não aceite.', 't', null, 400)));
      preencherTextos();
      escolherFicheiros(new File(['a'], 'a.pdf'), new File(['b'], 'b.pdf'));

      botao('criar').click();
      detetar();

      expect(anexar).toHaveBeenCalledTimes(2);
      expect(criadas[0].anexosFalhados).toBe(1);
    });

    it('numa Notícia para duas entidades a área fica desativada com a ajuda do destinatário único', () => {
      radio('modo-entidades');
      escolher('entidade-a-adicionar', '7');
      botao('adicionar-entidade').click();
      detetar();
      escolher('entidade-a-adicionar', '3');
      botao('adicionar-entidade').click();
      detetar();

      const entrada = campo('documentos')!.querySelector<HTMLInputElement>('input[type="file"]')!;
      expect(entrada.disabled).toBe(true);
      const ajuda = el().querySelector(`#${entrada.getAttribute('aria-describedby')}`);
      expect(ajuda?.textContent?.trim()).toBe(AJUDA_ANEXOS_LOTE);
    });

    it('na regularização da entidade com dois documentos cria a notificação e sobe os dois para ela, um pedido cada', () => {
      criar.mockReturnValue(of({ notificacoes: [resumo('r1')], loteId: null }));
      abrirComoEntidade();
      radio('tipo-regularizacao');
      escrever('descricao-igt', 'PDM de Ourém');
      preencherTextos();
      const oficio = new File(['abc'], 'oficio.pdf', { type: 'application/pdf' });
      const aviso = new File(['de'], 'aviso-dr.pdf', { type: 'application/pdf' });
      escolherFicheiros(oficio, aviso);

      expect(campo('documentos')!.textContent).toContain('Documentos (opcional)');
      expect(el().textContent).toContain('aviso-dr.pdf');
      botao('criar').click();
      detetar();

      expect(pedidoEnviado()).toMatchObject({ tipo: 'REGULARIZACAO_PROCEDIMENTO', descricaoIgt: 'PDM de Ourém', destinatarios: [] });
      expect(anexar).toHaveBeenCalledTimes(2);
      expect(anexar.mock.calls).toEqual([
        ['r1', oficio],
        ['r1', aviso],
      ]);
      expect(criar.mock.invocationCallOrder[0]).toBeLessThan(anexar.mock.invocationCallOrder[0]);
      expect(criadas).toEqual([{ notificacoes: [resumo('r1')], loteId: null, anexosFalhados: 0 }]);
    });

    it('o pedido de apoio não tem «Documentos», nem do Centro de Notificações nem com o processo fixo', () => {
      abrirComoEntidade();
      expect(campo('documentos')).toBeNull();
      radio('tipo-regularizacao');
      expect(campo('documentos')).not.toBeNull();
      radio('tipo-pedido-apoio');
      expect(campo('documentos')).toBeNull();

      abrirComoEntidade({ id: 'p9', rotulo: '2026000777 — PDM de Teste — Decisão de início' });
      expect(campo('documentos')).toBeNull();
    });

    it('ficheiros escolhidos na regularização não sobem se o tipo voltar ao pedido de apoio', () => {
      criar.mockReturnValue(of({ notificacoes: [resumo('a1')], loteId: null }));
      abrirComoEntidade();
      radio('tipo-regularizacao');
      escolherFicheiros(new File(['abc'], 'oficio.pdf'));
      radio('tipo-pedido-apoio');
      preencherTextos();

      botao('criar').click();
      detetar();

      expect(pedidoEnviado()).toMatchObject({ tipo: 'PEDIDO_APOIO' });
      expect(anexar).not.toHaveBeenCalled();
      expect(criadas).toEqual([{ notificacoes: [resumo('a1')], loteId: null }]);
    });

    it('sem ficheiros não sobe nada', () => {
      criar.mockReturnValue(of({ notificacoes: [resumo('n9')], loteId: null }));
      preencherTextos();

      botao('criar').click();
      detetar();

      expect(anexar).not.toHaveBeenCalled();
      expect(criadas).toHaveLength(1);
    });
  });
});

describe('fimDoDiaEmLisboa', () => {
  it('usa +00:00 no inverno e +01:00 no verão', () => {
    expect(fimDoDiaEmLisboa('2026-12-31')).toBe('2026-12-31T23:59:59.999+00:00');
    expect(fimDoDiaEmLisboa('2026-07-15')).toBe('2026-07-15T23:59:59.999+01:00');
  });
});
