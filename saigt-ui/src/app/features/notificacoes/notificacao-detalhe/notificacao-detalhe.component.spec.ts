import { Component, WritableSignal, signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import type { PerfilInterno } from '../../../core/auth/auth.models';
import { AuthService } from '../../../core/auth/auth.service';
import { ApiError } from '../../../core/models/error-response.model';
import { NotificacaoDetalhe } from '../../../models/notificacao.model';
import { LOCALIZACAO, MENSAGEM_ERRO_DESCARGA } from '../../../services/descarga.service';
import { NotificacaoService } from '../../../services/notificacao.service';
import { NotificacoesNaoLidasService } from '../../../services/notificacoes-nao-lidas.service';
import { ROUTES } from '../../../shared/routes';
import { NotificacaoDetalheComponent } from './notificacao-detalhe.component';

const PROCESSO = 'a1b2c3d4-0000-4000-8000-000000000001';
const PEDIDO = 'c3d4e5f6-0000-4000-8000-000000000009';

@Component({ template: '' })
class DestinoComponent {}

const BASE: NotificacaoDetalhe = {
  id: 'n1',
  tipo: 'RESPOSTA_OBRIGATORIA',
  titulo: 'Regularização necessária — PDM Cascais',
  texto: 'Foi detetada a publicação.\nSolicitamos a regularização.',
  processoId: null,
  numeroProcesso: null,
  idIgtSnit: null,
  descricaoIgt: null,
  prazo: null,
  estado: 'ATIVA',
  criadoEm: '2026-07-20T10:00:00Z',
  lidoEm: null,
  resolvidoEm: null,
  criadoPorNome: 'Ana Teixeira',
  numeroMensagens: 0,
  numeroDocumentos: 0,
  perfilDestino: null,
  destinatarioUtilizadorNome: null,
  destinatarioEntidadeNome: null,
  mensagens: [],
  documentos: [],
  acoesDisponiveis: [],
};

describe('NotificacaoDetalheComponent', () => {
  let fixture: ComponentFixture<NotificacaoDetalheComponent>;
  let obter: ReturnType<typeof vi.fn>;
  let responder: ReturnType<typeof vi.fn>;
  let reabrir: ReturnType<typeof vi.fn>;
  let ler: ReturnType<typeof vi.fn>;
  let resolver: ReturnType<typeof vi.fn>;
  let remover: ReturnType<typeof vi.fn>;
  let anexar: ReturnType<typeof vi.fn>;
  let urlDownload: ReturnType<typeof vi.fn>;
  let assign: ReturnType<typeof vi.fn>;
  let atualizarNaoLidas: ReturnType<typeof vi.fn>;
  let perfil: WritableSignal<PerfilInterno | null>;

  function criar(notificacao: Partial<NotificacaoDetalhe> = {}): void {
    obter.mockReturnValue(of({ ...BASE, ...notificacao }));
    fixture = TestBed.createComponent(NotificacaoDetalheComponent);
    fixture.componentRef.setInput('notificacaoId', 'n1');
    fixture.detectChanges();
  }

  const el = () => fixture.nativeElement as HTMLElement;
  const campos = () =>
    Object.fromEntries(
      [...el().querySelectorAll('.campo')].map((campo) => [
        campo.querySelector('dt')?.textContent?.trim(),
        campo.querySelector('dd')?.textContent?.trim(),
      ]),
    );

  beforeEach(() => {
    obter = vi.fn();
    responder = vi.fn();
    reabrir = vi.fn();
    ler = vi.fn();
    resolver = vi.fn();
    remover = vi.fn();
    anexar = vi.fn();
    urlDownload = vi.fn();
    assign = vi.fn();
    atualizarNaoLidas = vi.fn();
    perfil = signal<PerfilInterno | null>(null);
    TestBed.configureTestingModule({
      providers: [
        provideRouter([{ path: 'administracao/alteracao-owner', component: DestinoComponent }]),
        { provide: AuthService, useValue: { perfilInterno: perfil } },
        { provide: NotificacaoService, useValue: { obter, responder, reabrir, ler, resolver, remover, anexar, urlDownload } },
        { provide: LOCALIZACAO, useValue: { assign } },
        { provide: NotificacoesNaoLidasService, useValue: { atualizar: atualizarNaoLidas } },
      ],
    });
  });

  it('mostra só os campos com valor', () => {
    criar();

    expect(campos()).toEqual({
      Remetente: 'Ana Teixeira',
      Destinatários: 'Todos',
      'Data de envio': '2026-07-20 11:00',
    });
    expect(el().querySelector('h2')?.textContent?.trim()).toBe('Regularização necessária — PDM Cascais');
    expect(el().querySelector('section')?.getAttribute('aria-live')).toBe('polite');
  });

  it('com todos os campos: destinatários, prazo em âmbar, IGT e ligação ao processo', () => {
    criar({
      criadoPorNome: null,
      destinatarioEntidadeNome: 'CM Cascais',
      destinatarioUtilizadorNome: 'Maria Santos',
      prazo: '2026-08-20T00:00:00Z',
      idIgtSnit: 'IGT-2024-00291',
      descricaoIgt: 'PDM Cascais — Revisão',
      processoId: PROCESSO,
      numeroProcesso: 'PDM-CAS-2025-001',
    });

    expect(campos()).toEqual({
      Remetente: 'SAIGT — Sistema',
      Destinatários: 'CM Cascais — Maria Santos',
      'Data de envio': '2026-07-20 11:00',
      Prazo: '2026-08-20',
      'ID IGT SNIT': 'IGT-2024-00291',
      'Descrição IGT': 'PDM Cascais — Revisão',
      Processo: 'PDM-CAS-2025-001',
    });
    expect(el().querySelector('dd.prazo')).not.toBeNull();
    expect(el().querySelector('.campo a')?.getAttribute('href')).toBe(ROUTES.PROCESSO_DETALHE(PROCESSO));
  });

  it('«Master» quando o destino é o perfil MASTER', () => {
    criar({ perfilDestino: 'MASTER' });

    expect(campos()['Destinatários']).toBe('Master');
  });

  it('o texto aparece literal, sem interpretar HTML, e mantém as quebras de linha', () => {
    criar({ texto: 'Linha <b>um</b>\nLinha dois' });

    const texto = el().querySelector('.texto')!;
    expect(texto.querySelector('b')).toBeNull();
    expect(texto.textContent).toBe('Linha <b>um</b>\nLinha dois');
    expect(getComputedStyle(texto).whiteSpace).toBe('pre-line');
  });

  it('RESPOSTA_OBRIGATORIA sem mensagens: «Sem respostas ainda.» e nenhuma ação de resposta, resolução ou eliminação', () => {
    criar();

    expect(el().querySelector('.sem-respostas')?.textContent?.trim()).toBe('Sem respostas ainda.');
    expect(el().querySelector('textarea')).toBeNull();
    const botoes = [...el().querySelectorAll('button')].map((b) => b.textContent?.trim() || b.getAttribute('aria-label'));
    expect(botoes).toEqual(['Fechar detalhe']);
  });

  it('lista as mensagens e os anexos, sem ligação de descarga', () => {
    criar({
      tipo: 'PEDIDO_APOIO',
      mensagens: [{ id: 'm1', autorId: 'u1', autorNome: 'Carlos Mendes', texto: 'Obrigado.', criadoEm: '2026-07-21T09:00:00Z' }],
      documentos: [{ id: 'd1', nomeFicheiro: 'Extrato_DR_142_2024.pdf', tamanhoBytes: 911_360, criadoEm: '2026-07-20T10:00:00Z' }],
    });

    expect(el().querySelector('.mensagem-autor')?.textContent?.replace(/\s+/g, ' ').trim()).toBe('Carlos Mendes · 2026-07-21 10:00');
    expect(el().querySelector('.mensagem-texto')?.textContent?.trim()).toBe('Obrigado.');
    expect(el().querySelector('.documento-nome')?.textContent?.trim()).toBe('Extrato_DR_142_2024.pdf');
    expect(el().querySelector('.documento-tamanho')?.textContent?.trim()).toBe('890 KB');
    expect(el().querySelector('.documentos a')).toBeNull();
  });

  it('sem histórico conversacional nos tipos sem conversação', () => {
    criar({ tipo: 'ESTATICA' });

    expect([...el().querySelectorAll('h3')].map((h) => h.textContent?.trim())).not.toContain('Histórico conversacional');
  });

  it('«Fechar detalhe» emite fechar', () => {
    criar();
    const fechou = vi.fn();
    fixture.componentInstance.fechar.subscribe(fechou);

    el().querySelector<HTMLButtonElement>('[aria-label="Fechar detalhe"]')!.click();

    expect(fechou).toHaveBeenCalled();
  });

  it('404 NOTIF_NAO_ENCONTRADA emite naoEncontrada', () => {
    obter.mockReturnValue(throwError(() => new ApiError('NOTIF_NAO_ENCONTRADA', 'Não existe.', 't', null)));
    fixture = TestBed.createComponent(NotificacaoDetalheComponent);
    const naoEncontrada = vi.fn();
    fixture.componentInstance.naoEncontrada.subscribe(naoEncontrada);
    fixture.componentRef.setInput('notificacaoId', 'nx');
    fixture.detectChanges();

    expect(naoEncontrada).toHaveBeenCalled();
  });

  it('outro erro mostra a mensagem do envelope', () => {
    obter.mockReturnValue(throwError(() => new ApiError('ERRO_INTERNO', 'Erro inesperado.', 't', null)));
    fixture = TestBed.createComponent(NotificacaoDetalheComponent);
    fixture.componentRef.setInput('notificacaoId', 'nx');
    fixture.detectChanges();

    expect(el().querySelector('app-alerta')?.textContent?.trim()).toBe('Erro inesperado.');
  });

  it('volta a pedir quando muda o id', () => {
    criar();
    fixture.componentRef.setInput('notificacaoId', 'n2');
    fixture.detectChanges();

    expect(obter.mock.calls.map(([id]) => id)).toEqual(['n1', 'n2']);
  });

  describe('resposta', () => {
    const MENSAGEM = { id: 'm9', autorId: 'u9', autorNome: 'Marta Master', texto: 'Pedido tratado.', criadoEm: '2026-07-22T09:00:00Z' };
    const caixa = () => el().querySelector<HTMLTextAreaElement>('.resposta textarea');
    const enviar = () => el().querySelector<HTMLButtonElement>('.resposta button[type="submit"]')!;
    const escrever = (valor: string) => {
      caixa()!.value = valor;
      caixa()!.dispatchEvent(new Event('input'));
      fixture.detectChanges();
    };
    const selos = () => [...el().querySelectorAll('.selos app-selo')].map((selo) => selo.textContent?.trim());

    it('sem RESPONDER não há caixa nem «Enviar resposta»', () => {
      criar({ tipo: 'PEDIDO_APOIO', acoesDisponiveis: [] });

      expect(caixa()).toBeNull();
      expect(el().textContent).not.toContain('Enviar resposta');
    });

    it('com RESPONDER: campo com rótulo «Resposta», contador e botão desativado enquanto vazio', () => {
      criar({ tipo: 'PEDIDO_APOIO', acoesDisponiveis: ['RESPONDER'] });

      const rotulo = el().querySelector('.resposta label')!;
      expect(rotulo.textContent?.replace('*', '').trim()).toBe('Resposta');
      expect(rotulo.getAttribute('for')).toBe(caixa()!.id);
      expect(caixa()!.getAttribute('placeholder')).toBe('Escrever resposta...');
      expect(caixa()!.getAttribute('maxlength')).toBe('5000');
      expect(el().querySelector('.resposta .campo-texto-ajuda')?.textContent?.trim()).toBe('0/5000 caracteres');
      expect(enviar().textContent?.trim()).toBe('Enviar resposta');
      expect(enviar().disabled).toBe(true);
    });

    it('só com espaços o botão fica desativado e o erro de obrigatório fica associado ao campo', () => {
      criar({ tipo: 'PEDIDO_APOIO', acoesDisponiveis: ['RESPONDER'] });

      escrever('   ');
      caixa()!.dispatchEvent(new FocusEvent('focusout', { bubbles: true }));
      fixture.detectChanges();

      expect(enviar().disabled).toBe(true);
      const erro = el().querySelector('.resposta .campo-texto-erro')!;
      expect(erro.textContent?.trim()).toBe('A resposta é obrigatória.');
      expect(caixa()!.getAttribute('aria-describedby')).toContain(erro.id);
      expect(caixa()!.getAttribute('aria-invalid')).toBe('true');
      expect(responder).not.toHaveBeenCalled();
    });

    it('envia o texto sem espaços nas pontas, acrescenta a mensagem, limpa o campo, muda o selo e anuncia', () => {
      criar({ tipo: 'PEDIDO_APOIO', acoesDisponiveis: ['RESPONDER'] });
      responder.mockReturnValue(of({ mensagem: MENSAGEM, estado: 'RESOLVIDA' }));
      obter.mockReturnValue(of({ ...BASE, tipo: 'PEDIDO_APOIO', estado: 'RESOLVIDA', mensagens: [MENSAGEM], acoesDisponiveis: [] }));
      const alterada = vi.fn();
      fixture.componentInstance.alterada.subscribe(alterada);

      escrever('  Pedido tratado.  ');
      expect(enviar().disabled).toBe(false);
      enviar().click();
      fixture.detectChanges();

      expect(responder).toHaveBeenCalledWith('n1', 'Pedido tratado.');
      expect([...el().querySelectorAll('.mensagem-texto')].map((m) => m.textContent?.trim())).toEqual(['Pedido tratado.']);
      expect(selos()).toContain('Resolvida');
      const anuncio = el().querySelector<HTMLElement>('[role="status"]')!;
      expect(anuncio.textContent?.trim()).toBe('Resposta enviada.');
      expect(anuncio.getAttribute('aria-live')).toBe('polite');
      expect(anuncio.classList).toContain('visualmente-oculto');
      expect(getComputedStyle(anuncio).position).toBe('absolute');
      expect(atualizarNaoLidas).toHaveBeenCalled();
      expect(alterada).toHaveBeenCalled();
      expect(obter).toHaveBeenCalledTimes(2);
      expect(caixa()).toBeNull();
    });

    it('depois de enviar numa notificação que continua a aceitar respostas, o campo fica vazio', () => {
      criar({ tipo: 'RESPOSTA_OBRIGATORIA', acoesDisponiveis: ['RESPONDER'] });
      responder.mockReturnValue(of({ mensagem: MENSAGEM, estado: 'ATIVA' }));

      escrever('Lembrete');
      enviar().click();
      fixture.detectChanges();

      expect(caixa()!.value).toBe('');
      expect(enviar().disabled).toBe(true);
      expect(el().querySelector('.resposta .campo-texto-erro')).toBeNull();
    });

    it('409: mostra o alerta com a mensagem do envelope, relê o detalhe e a caixa sai sem RESPONDER', () => {
      criar({ tipo: 'PEDIDO_APOIO', acoesDisponiveis: ['RESPONDER'] });
      responder.mockReturnValue(
        throwError(() => new ApiError('NOTIF_ESTADO_INVALIDO', 'A notificação já não aceita mensagens ou anexos.', 't', null)),
      );
      obter.mockReturnValue(of({ ...BASE, tipo: 'PEDIDO_APOIO', estado: 'RESOLVIDA', acoesDisponiveis: [] }));

      escrever('Olá');
      enviar().click();
      fixture.detectChanges();

      expect(el().querySelector('.resposta-alerta')?.textContent?.trim()).toBe('A notificação já não aceita mensagens ou anexos.');
      expect(obter).toHaveBeenCalledTimes(2);
      expect(caixa()).toBeNull();
      expect(selos()).toContain('Resolvida');
    });

    it('400 VALIDACAO no texto mostra o erro no campo', () => {
      criar({ tipo: 'PEDIDO_APOIO', acoesDisponiveis: ['RESPONDER'] });
      responder.mockReturnValue(
        throwError(() => new ApiError('VALIDACAO', 'Payload inválido.', 't', [{ path: 'texto', erro: 'tamanho deve ser entre 0 e 5000' }])),
      );

      escrever('Olá');
      enviar().click();
      fixture.detectChanges();

      expect(el().querySelector('.resposta .campo-texto-erro')?.textContent?.trim()).toBe('tamanho deve ser entre 0 e 5000');
      expect(el().querySelector('.resposta-alerta')).toBeNull();
    });

    it('as mensagens aparecem literais, sem interpretar HTML, com as quebras de linha', () => {
      criar({
        tipo: 'PEDIDO_APOIO',
        mensagens: [{ ...MENSAGEM, texto: 'Linha <b>um</b>\nLinha dois' }],
      });

      const mensagem = el().querySelector('.mensagem-texto')!;
      expect(mensagem.querySelector('b')).toBeNull();
      expect(mensagem.textContent).toBe('Linha <b>um</b>\nLinha dois');
      expect(getComputedStyle(mensagem).whiteSpace).toBe('pre-line');
    });

    it('o botão é alcançável por Tab depois do campo', () => {
      criar({ tipo: 'PEDIDO_APOIO', acoesDisponiveis: ['RESPONDER'] });
      escrever('Olá');

      expect(enviar().getAttribute('tabindex')).toBeNull();
      expect(enviar().disabled).toBe(false);
      const focaveis = [...el().querySelectorAll<HTMLElement>('textarea, button')].filter((e) => !(e as HTMLButtonElement).disabled);
      expect(focaveis.indexOf(enviar())).toBe(focaveis.indexOf(caixa()!) + 1);
    });
  });
  describe('reabrir pedido', () => {
    const RESOLVIDO: Partial<NotificacaoDetalhe> = {
      tipo: 'PEDIDO_APOIO',
      estado: 'RESOLVIDA',
      resolvidoEm: '2026-07-21T10:00:00Z',
      perfilDestino: 'MASTER',
      acoesDisponiveis: ['REABRIR'],
    };
    const botaoReabrir = () =>
      [...el().querySelectorAll<HTMLButtonElement>('button')].find((b) => b.textContent?.trim() === 'Reabrir pedido');
    const selos = () => [...el().querySelectorAll('.selos app-selo')].map((selo) => selo.textContent?.trim());

    it('sem REABRIR não há «Reabrir pedido»', () => {
      criar({ ...RESOLVIDO, acoesDisponiveis: [] });

      expect(botaoReabrir()).toBeUndefined();
    });

    it('reabre: o selo passa a «Pend. Resposta», volta a caixa de resposta e anuncia «Pedido reaberto.»', () => {
      criar(RESOLVIDO);
      reabrir.mockReturnValue(of({ ...BASE, tipo: 'PEDIDO_APOIO', estado: 'ATIVA', resolvidoEm: null }));
      obter.mockReturnValue(of({ ...BASE, ...RESOLVIDO, estado: 'ATIVA', resolvidoEm: null, acoesDisponiveis: ['RESPONDER'] }));
      const alterada = vi.fn();
      fixture.componentInstance.alterada.subscribe(alterada);
      expect(selos()).toContain('Resolvida');
      expect(el().querySelector('.resposta textarea')).toBeNull();

      botaoReabrir()!.click();
      fixture.detectChanges();

      expect(reabrir).toHaveBeenCalledWith('n1');
      expect(selos()).toContain('Pend. Resposta');
      expect(el().querySelector('.resposta textarea')).not.toBeNull();
      const anuncio = el().querySelector<HTMLElement>('[role="status"]')!;
      expect(anuncio.textContent?.trim()).toBe('Pedido reaberto.');
      expect(anuncio.getAttribute('aria-live')).toBe('polite');
      expect(botaoReabrir()).toBeUndefined();
      expect(alterada).toHaveBeenCalled();
      expect(obter).toHaveBeenCalledTimes(2);
    });

    it('409 ou 403: mostra o alerta com a mensagem do envelope e relê o detalhe', () => {
      criar(RESOLVIDO);
      reabrir.mockReturnValue(
        throwError(() => new ApiError('NOTIF_ESTADO_INVALIDO', 'Só um pedido de apoio resolvido pode ser reaberto.', 't', null)),
      );
      obter.mockReturnValue(of({ ...BASE, ...RESOLVIDO, estado: 'ATIVA', resolvidoEm: null, acoesDisponiveis: [] }));

      botaoReabrir()!.click();
      fixture.detectChanges();

      expect(el().querySelector('.reabrir-alerta')?.textContent?.trim()).toBe('Só um pedido de apoio resolvido pode ser reaberto.');
      expect(obter).toHaveBeenCalledTimes(2);
      expect(botaoReabrir()).toBeUndefined();
    });
  });

  describe('alterar owner a partir de um pedido de apoio', () => {
    const PEDIDO_APOIO: Partial<NotificacaoDetalhe> = {
      id: PEDIDO,
      tipo: 'PEDIDO_APOIO',
      estado: 'ATIVA',
      perfilDestino: 'MASTER',
      processoId: PROCESSO,
      numeroProcesso: '2026/000123',
      criadoPorNome: 'Helena Marques',
      acoesDisponiveis: ['RESPONDER'],
    };
    const botao = () => el().querySelector<HTMLAnchorElement>('.rodape a.alterar-owner');

    it('MASTER num pedido ativo com processo: «Alterar owner do processo» com o processo e o pedido na rota', () => {
      perfil.set('MASTER');
      criar(PEDIDO_APOIO);

      expect(botao()?.textContent?.trim()).toBe('Alterar owner do processo');
      expect(botao()?.getAttribute('href')).toBe(
        `${ROUTES.ADMINISTRACAO_ALTERACAO_OWNER}?processoId=${PROCESSO}&pedidoApoioId=${PEDIDO}`,
      );
    });

    it('MASTER num pedido geral: só o pedido na rota', () => {
      perfil.set('MASTER');
      criar({ ...PEDIDO_APOIO, processoId: null, numeroProcesso: null });

      expect(botao()?.getAttribute('href')).toBe(`${ROUTES.ADMINISTRACAO_ALTERACAO_OWNER}?pedidoApoioId=${PEDIDO}`);
    });

    it('o clique abre o ecrã de Alteração de Owner com o processo e o pedido', async () => {
      perfil.set('MASTER');
      criar(PEDIDO_APOIO);

      botao()!.click();
      await fixture.whenStable();

      const url = TestBed.inject(Router).parseUrl(TestBed.inject(Router).url);
      expect(url.root.children['primary']?.toString()).toBe('administracao/alteracao-owner');
      expect(url.queryParams).toEqual({ processoId: PROCESSO, pedidoApoioId: PEDIDO });
    });

    it.each<[string, PerfilInterno | null]>([
      ['a entidade que o criou', null],
      ['um TECNICO', 'TECNICO'],
      ['um VISITANTE', 'VISITANTE'],
    ])('o mesmo pedido visto por %s: sem o botão', (_quem, perfilInterno) => {
      perfil.set(perfilInterno);
      criar(PEDIDO_APOIO);

      expect(botao()).toBeNull();
      expect(el().querySelector('.rodape')).toBeNull();
    });

    it.each<[string, Partial<NotificacaoDetalhe>]>([
      ['um pedido já resolvido', { estado: 'RESOLVIDA', acoesDisponiveis: [] }],
      ['outro tipo de notificação', { tipo: 'REGULARIZACAO_PROCEDIMENTO' }],
      ['um pedido que não se dirige aos Master', { perfilDestino: 'TECNICO' }],
    ])('MASTER em %s: sem o botão', (_caso, alteracoes) => {
      perfil.set('MASTER');
      criar({ ...PEDIDO_APOIO, ...alteracoes });

      expect(botao()).toBeNull();
    });

    it('depois de o Master responder e o pedido ficar resolvido, o botão sai', () => {
      perfil.set('MASTER');
      criar(PEDIDO_APOIO);
      expect(botao()).not.toBeNull();
      responder.mockReturnValue(
        of({
          mensagem: { id: 'm1', autorId: 'u1', autorNome: 'Ana Silva', texto: 'Tratado.', criadoEm: '2026-07-22T09:00:00Z' },
          estado: 'RESOLVIDA',
        }),
      );
      obter.mockReturnValue(of({ ...BASE, ...PEDIDO_APOIO, estado: 'RESOLVIDA', acoesDisponiveis: [] }));
      const caixa = el().querySelector<HTMLTextAreaElement>('.resposta textarea')!;
      caixa.value = 'Tratado.';
      caixa.dispatchEvent(new Event('input'));
      fixture.detectChanges();

      el().querySelector<HTMLButtonElement>('.resposta button[type="submit"]')!.click();
      fixture.detectChanges();

      expect(botao()).toBeNull();
    });
  });

  describe('ler, resolver e remover', () => {
    const botaoComTexto = (rotulo: string) =>
      [...el().querySelectorAll<HTMLButtonElement>('button')].find((b) => b.textContent?.trim() === rotulo) ?? null;
    const operacoes = () => [...el().querySelectorAll('.operacoes button')].map((b) => b.textContent?.trim());
    const selos = () => [...el().querySelectorAll('.selos app-selo')].map((s) => s.textContent?.trim());
    const anuncio = () => el().querySelector('[role="status"]')?.textContent?.trim();
    const dialogo = () => el().querySelector('[role="dialog"]');

    const resumo = (campos: Partial<NotificacaoDetalhe>) => ({ ...BASE, ...campos, acoesDisponiveis: [] });

    it('cada botão só aparece com a ação correspondente em acoesDisponiveis', () => {
      criar({ tipo: 'PEDIDO_APOIO', acoesDisponiveis: ['RESPONDER'] });
      expect(el().querySelector('.operacoes')).toBeNull();

      criar({ tipo: 'TEMPORARIA', acoesDisponiveis: ['LER'] });
      expect(operacoes()).toEqual(['Marcar como lida']);

      criar({ tipo: 'ALERTA', acoesDisponiveis: ['LER', 'REMOVER'] });
      expect(operacoes()).toEqual(['Marcar como lida', 'Eliminar']);
      expect(el().querySelector('.operacoes')?.getAttribute('role')).toBe('group');

      criar({ tipo: 'PEDIDO_APOIO', acoesDisponiveis: ['RESPONDER', 'RESOLVER'] });
      expect(operacoes()).toEqual(['Marcar como resolvida']);
    });

    it('os botões ficam no rodapé, como no mockup, antes de «Alterar owner do processo»', () => {
      perfil.set('MASTER');
      criar({ tipo: 'PEDIDO_APOIO', perfilDestino: 'MASTER', acoesDisponiveis: ['RESPONDER', 'RESOLVER'] });

      const rodape = el().querySelector('.rodape')!;
      expect(el().querySelector('.corpo .operacoes')).toBeNull();
      expect([...rodape.querySelectorAll('button, a')].map((b) => b.textContent?.trim())).toEqual([
        'Marcar como resolvida',
        'Alterar owner do processo',
      ]);
    });

    it('na RESPOSTA_OBRIGATORIA do Master autor não há «Marcar como resolvida»', () => {
      perfil.set('MASTER');
      criar({ tipo: 'RESPOSTA_OBRIGATORIA', acoesDisponiveis: ['RESPONDER'] });

      expect(botaoComTexto('Marcar como resolvida')).toBeNull();
    });

    it('«Marcar como lida» muda o selo para «Lida», anuncia, baixa o contador e avisa a lista', () => {
      criar({ tipo: 'TEMPORARIA', acoesDisponiveis: ['LER'] });
      expect(selos()).toContain('Não lida');
      const lida = resumo({ tipo: 'TEMPORARIA', estado: 'LIDA', lidoEm: '2026-07-21T09:00:00Z' });
      ler.mockReturnValue(of(lida));
      obter.mockReturnValue(of({ ...BASE, ...lida, acoesDisponiveis: [] }));
      const alterada = vi.fn();
      fixture.componentInstance.alterada.subscribe(alterada);

      botaoComTexto('Marcar como lida')!.click();
      fixture.detectChanges();

      expect(ler).toHaveBeenCalledWith('n1');
      expect(selos()).toContain('Lida');
      expect(el().querySelector('.operacoes')).toBeNull();
      expect(anuncio()).toBe('Notificação marcada como lida.');
      expect(atualizarNaoLidas).toHaveBeenCalled();
      expect(alterada).toHaveBeenCalled();
      expect(obter).toHaveBeenCalledTimes(2);
    });

    it('«Marcar como resolvida» pede confirmação; confirmada, o selo passa a «Resolvida» e anuncia', () => {
      perfil.set('MASTER');
      criar({ tipo: 'PEDIDO_APOIO', perfilDestino: 'MASTER', acoesDisponiveis: ['RESPONDER', 'RESOLVER'] });
      const resolvido = resumo({ tipo: 'PEDIDO_APOIO', estado: 'RESOLVIDA', resolvidoEm: '2026-07-21T09:00:00Z' });
      resolver.mockReturnValue(of(resolvido));
      obter.mockReturnValue(of({ ...BASE, ...resolvido }));

      botaoComTexto('Marcar como resolvida')!.click();
      fixture.detectChanges();
      expect(resolver).not.toHaveBeenCalled();
      expect(dialogo()?.textContent).toContain('Marcar este pedido de apoio como resolvido sem responder?');

      dialogo()!.querySelector<HTMLButtonElement>('.botao-sim')!.click();
      fixture.detectChanges();

      expect(resolver).toHaveBeenCalledWith('n1');
      expect(dialogo()).toBeNull();
      expect(selos()).toContain('Resolvida');
      expect(anuncio()).toBe('Pedido de apoio resolvido.');
    });

    it('cancelar a confirmação não faz o pedido e devolve o foco ao botão', async () => {
      perfil.set('MASTER');
      criar({ tipo: 'PEDIDO_APOIO', acoesDisponiveis: ['RESOLVER'] });
      const botao = botaoComTexto('Marcar como resolvida')!;
      botao.focus();

      botao.click();
      fixture.detectChanges();
      dialogo()!.querySelector<HTMLButtonElement>('.botao-nao')!.click();
      fixture.detectChanges();
      await fixture.whenStable();

      expect(dialogo()).toBeNull();
      expect(resolver).not.toHaveBeenCalled();
      expect(document.activeElement).toBe(botao);
    });

    it('«Eliminar» numa Notícia avisa que deixa de estar visível para todos os destinatários', () => {
      perfil.set('MASTER');
      criar({ tipo: 'ESTATICA', acoesDisponiveis: ['REMOVER'] });
      remover.mockReturnValue(of({ linhasAtualizadas: 3 }));
      obter.mockReturnValue(of({ ...BASE, tipo: 'ESTATICA', estado: 'REMOVIDA' }));
      const alterada = vi.fn();
      fixture.componentInstance.alterada.subscribe(alterada);

      botaoComTexto('Eliminar')!.click();
      fixture.detectChanges();
      expect(dialogo()?.textContent).toContain('A notícia deixa de estar visível para todos os destinatários.');

      dialogo()!.querySelector<HTMLButtonElement>('.botao-sim')!.click();
      fixture.detectChanges();

      expect(remover).toHaveBeenCalledWith('n1');
      expect(selos()).toContain('Removida');
      expect(anuncio()).toBe('Notificação removida.');
      expect(alterada).toHaveBeenCalled();
    });

    it('«Eliminar» num Alerta geral pergunta «Descartar este alerta?»', () => {
      perfil.set('MASTER');
      criar({ tipo: 'ALERTA', acoesDisponiveis: ['LER', 'REMOVER'] });

      botaoComTexto('Eliminar')!.click();
      fixture.detectChanges();

      expect(dialogo()?.textContent).toContain('Descartar este alerta?');
      expect(dialogo()?.textContent).not.toContain('destinatários');
    });

    it('um 409 mostra a mensagem do envelope e relê o detalhe', () => {
      perfil.set('MASTER');
      criar({ tipo: 'ALERTA', acoesDisponiveis: ['LER', 'REMOVER'] });
      ler.mockReturnValue(throwError(() => new ApiError('NOTIF_ESTADO_INVALIDO', 'A notificação já não está ativa.', 't', null)));
      obter.mockReturnValue(of({ ...BASE, tipo: 'ALERTA', estado: 'LIDA' }));

      botaoComTexto('Marcar como lida')!.click();
      fixture.detectChanges();

      expect(el().querySelector('.operacao-alerta')?.textContent?.trim()).toBe('A notificação já não está ativa.');
      expect(obter).toHaveBeenCalledTimes(2);
      expect(el().querySelector('.operacoes')).toBeNull();
      expect(atualizarNaoLidas).not.toHaveBeenCalled();
    });

    it('um 404 ao reler depois do erro emite naoEncontrada', () => {
      perfil.set('MASTER');
      criar({ tipo: 'ESTATICA', acoesDisponiveis: ['REMOVER'] });
      remover.mockReturnValue(throwError(() => new ApiError('NOTIF_NAO_ENCONTRADA', 'Não existe.', 't', null)));
      obter.mockReturnValue(throwError(() => new ApiError('NOTIF_NAO_ENCONTRADA', 'Não existe.', 't', null)));
      const naoEncontrada = vi.fn();
      fixture.componentInstance.naoEncontrada.subscribe(naoEncontrada);

      botaoComTexto('Eliminar')!.click();
      fixture.detectChanges();
      dialogo()!.querySelector<HTMLButtonElement>('.botao-sim')!.click();
      fixture.detectChanges();

      expect(naoEncontrada).toHaveBeenCalled();
    });

    it('só para leitura (histórico) não mostra operações nem a caixa de resposta', () => {
      obter.mockReturnValue(of({ ...BASE, estado: 'RESOLVIDA', acoesDisponiveis: ['RESPONDER'] }));
      fixture = TestBed.createComponent(NotificacaoDetalheComponent);
      fixture.componentRef.setInput('notificacaoId', 'n1');
      fixture.componentRef.setInput('soLeitura', true);
      fixture.detectChanges();

      expect(el().querySelector('.operacoes')).toBeNull();
      expect(el().querySelector('textarea')).toBeNull();
    });
  });
  describe('anexos', () => {
    const DOCUMENTO = { id: 'd1', nomeFicheiro: 'Extrato_DR.pdf', tamanhoBytes: 911360, criadoEm: '2026-07-20T10:00:00Z' };

    function escolherFicheiros(...ficheiros: File[]): void {
      const entrada = el().querySelector<HTMLInputElement>('input[type="file"]')!;
      Object.defineProperty(entrada, 'files', { value: ficheiros, configurable: true });
      entrada.dispatchEvent(new Event('change'));
      fixture.detectChanges();
    }

    it('«Descarregar {nome}» pede o URL do ticket e navega logo para ele', () => {
      urlDownload.mockReturnValue(of({ url: 'http://localhost:8080/api/v1/descargas/tkt' }));
      criar({ documentos: [DOCUMENTO], numeroDocumentos: 1 });

      el().querySelector<HTMLButtonElement>('button[aria-label="Descarregar Extrato_DR.pdf"]')!.click();
      fixture.detectChanges();

      expect(urlDownload).toHaveBeenCalledWith('n1', 'd1');
      expect(assign).toHaveBeenCalledWith('http://localhost:8080/api/v1/descargas/tkt');
    });

    it('se o pedido do ticket falhar mostra a mensagem do envelope', () => {
      urlDownload.mockReturnValue(
        throwError(() => new ApiError('NOTIF_NAO_ENCONTRADA', 'A notificação não existe ou não está disponível.', 't', null, 404)),
      );
      criar({ documentos: [DOCUMENTO], numeroDocumentos: 1 });

      el().querySelector<HTMLButtonElement>('button[aria-label="Descarregar Extrato_DR.pdf"]')!.click();
      fixture.detectChanges();

      expect(assign).not.toHaveBeenCalled();
      expect(el().querySelector('.documentos-alerta')?.textContent).toContain('A notificação não existe ou não está disponível.');
      expect(MENSAGEM_ERRO_DESCARGA).toBeTruthy();
    });

    it('sem ANEXAR não há botão «Anexar»', () => {
      criar({ acoesDisponiveis: ['RESPONDER'] });

      expect([...el().querySelectorAll('button')].some((b) => b.textContent?.trim() === 'Anexar')).toBe(false);
    });

    it('com ANEXAR, dois ficheiros sobem, o detalhe é relido e «2 documento(s) anexado(s).» é anunciado', () => {
      anexar.mockImplementation((_id: string, ficheiro: File) =>
        of({ id: ficheiro.name, nomeFicheiro: ficheiro.name, tamanhoBytes: ficheiro.size, criadoEm: '2026-07-20T10:00:00Z' }),
      );
      criar({ acoesDisponiveis: ['RESPONDER', 'ANEXAR'] });
      const botaoAnexar = [...el().querySelectorAll<HTMLButtonElement>('.resposta-barra button')].find(
        (b) => b.textContent?.trim() === 'Anexar',
      );
      expect(botaoAnexar).toBeDefined();
      expect(obter).toHaveBeenCalledTimes(1);

      escolherFicheiros(new File(['a'], 'oficio.pdf'), new File(['b'], 'mapa.xlsx'));

      expect(anexar.mock.calls.map((c) => (c[1] as File).name)).toEqual(['oficio.pdf', 'mapa.xlsx']);
      expect(obter).toHaveBeenCalledTimes(2);
      expect(el().querySelector('[role="status"]')?.textContent?.trim()).toBe('2 documento(s) anexado(s).');
    });

    it('mostra a falha de cada ficheiro com a mensagem do envelope', () => {
      anexar.mockReturnValue(throwError(() => new ApiError('NOTIF_ESTADO_INVALIDO', 'A notificação já não aceita mensagens ou anexos.', 't', null, 409)));
      criar({ acoesDisponiveis: ['RESPONDER', 'ANEXAR'] });

      escolherFicheiros(new File(['a'], 'oficio.pdf'), new File(['x'], 'programa.exe'));

      expect(anexar).toHaveBeenCalledTimes(1);
      const erros = el().querySelector('.anexar-erros')?.textContent ?? '';
      expect(erros).toContain('programa.exe: extensão não aceite.');
      expect(erros).toContain('oficio.pdf: A notificação já não aceita mensagens ou anexos.');
    });

    it('na Notícia, sem caixa de resposta, «Anexar» fica no bloco de documentos', () => {
      criar({ tipo: 'ESTATICA', acoesDisponiveis: ['ANEXAR', 'REMOVER'] });

      const bloco = [...el().querySelectorAll('.bloco')].find((b) => b.textContent?.includes('Documentos anexos'));
      expect(bloco?.querySelector('.anexar-botao')?.textContent?.trim()).toBe('Anexar');
    });
  });
});
