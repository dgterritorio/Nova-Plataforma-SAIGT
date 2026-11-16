import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { AlteracoesModalComponent, AcaoAlteracao } from './alteracoes-modal.component';
import { AuthService } from '../../../core/auth/auth.service';
import { ApiError } from '../../../core/models/error-response.model';
import { AcaoProcesso, ProcessoDetalhe } from '../../../models/processo.model';
import { Utilizador } from '../../../models/utilizador.model';
import { ProcessoService } from '../../../services/processo.service';
import { UtilizadorService } from '../../../services/utilizador.service';

describe('AlteracoesModalComponent', () => {
  let fixture: ComponentFixture<AlteracoesModalComponent>;

  const ACOES_DGT_POR_ESTADO: Record<string, AcaoProcesso[]> = {
    ENVIADO_DGT: ['capturar'],
    EM_VALIDACAO_DGT: ['validar', 'rejeitar'],
    VALIDADO_DGT: ['enviar-incm', 'enviar-pcm'],
  };

  /** Os estados em que o owner age: só neles o backend lista `reatribuir-autor`. */
  const ESTADOS_TRANSFERIVEIS = ['EM_SUBMISSAO', 'ENVIADO_DGT', 'REJEITADO_DGT', 'ANULADO_INCM'];

  /** O que o backend lista para o ator DGT; só o `MASTER` anula (em validação) e reatribui o autor. */
  function acoesDoAtorDgt(estado: string, souMaster: boolean): AcaoProcesso[] {
    const acoes = [...(ACOES_DGT_POR_ESTADO[estado] ?? [])];
    if (souMaster) {
      if (estado === 'EM_VALIDACAO_DGT') {
        acoes.push('anular');
      }
      if (ESTADOS_TRANSFERIVEIS.includes(estado)) {
        acoes.push('reatribuir-autor');
      }
    }
    return acoes;
  }

  const perfilInterno = signal<string | null>('MASTER');

  /** Só os campos que o modal lê. */
  function processo(estado: string, acoesDisponiveis: AcaoProcesso[]): ProcessoDetalhe {
    return {
      id: 'p1',
      versao: 5,
      estado,
      autorNome: 'Helena Marques',
      criadoPorId: 'u-helena',
      entidadeId: 7,
      acoesDisponiveis,
    } as ProcessoDetalhe;
  }

  /** Por omissão com as ações do `MASTER`, que vê todos os cartões; `acoes` fixa a lista do backend. */
  function montar(estado: string, opcoes: { souMaster?: boolean; acoes?: AcaoProcesso[] } = {}): void {
    perfilInterno.set((opcoes.souMaster ?? true) ? 'MASTER' : 'TECNICO');
    fixture = TestBed.createComponent(AlteracoesModalComponent);
    fixture.componentRef.setInput('processo', processo(estado, opcoes.acoes ?? acoesDoAtorDgt(estado, opcoes.souMaster ?? true)));
    fixture.componentRef.setInput('aberto', true);
    fixture.detectChanges();
  }

  function cartao(acao: AcaoAlteracao): HTMLButtonElement {
    return fixture.nativeElement.querySelector(`[data-acao="${acao}"]`) as HTMLButtonElement;
  }

  function botao(texto: string): HTMLButtonElement {
    const encontrado = Array.from(fixture.nativeElement.querySelectorAll('button')).find(
      (b) => (b as HTMLElement).textContent?.trim() === texto,
    ) as HTMLButtonElement | undefined;
    if (!encontrado) {
      throw new Error(`Botão "${texto}" não encontrado.`);
    }
    return encontrado;
  }

  function clicar(elemento: HTMLElement): void {
    elemento.click();
    fixture.detectChanges();
  }

  let processoServiceMock: Record<
    'capturar' | 'validar' | 'rejeitar' | 'enviarIncm' | 'anular' | 'reatribuirAutor',
    ReturnType<typeof vi.fn>
  >;
  let utilizadorServiceMock: { listar: ReturnType<typeof vi.fn> };

  function utilizador(id: string, nome: string, email: string): Utilizador {
    return { id, nome, email, origemAutenticacao: 'INCM', perfilInterno: null, cargo: null, entidades: [], ultimoLoginEm: null };
  }

  function pagina(content: Utilizador[]) {
    return of({ content, page: 0, size: 100, totalElements: content.length, totalPages: 1 });
  }

  beforeEach(() => {
    processoServiceMock = {
      capturar: vi.fn().mockReturnValue(of({ estado: 'EM_VALIDACAO_DGT' })),
      validar: vi.fn().mockReturnValue(of({ estado: 'VALIDADO_DGT' })),
      rejeitar: vi.fn().mockReturnValue(of({ estado: 'REJEITADO_DGT' })),
      enviarIncm: vi.fn().mockReturnValue(of({ estado: 'EM_VALIDACAO_INCM' })),
      anular: vi.fn().mockReturnValue(of({ estado: 'ANULADO' })),
      reatribuirAutor: vi.fn().mockReturnValue(of({ estado: 'ENVIADO_DGT', criadoPorId: 'u-ricardo' })),
    };
    utilizadorServiceMock = {
      listar: vi.fn().mockReturnValue(
        pagina([
          utilizador('u-helena', 'Helena Marques', 'helena.sintra@incm.pt'),
          utilizador('u-ricardo', 'Ricardo Nunes', 'ricardo.silva@incm.pt'),
        ]),
      ),
    };
    TestBed.configureTestingModule({
      imports: [AlteracoesModalComponent],
      providers: [
        { provide: ProcessoService, useValue: processoServiceMock },
        { provide: UtilizadorService, useValue: utilizadorServiceMock },
        { provide: AuthService, useValue: { perfilInterno } },
      ],
    });
  });

  it('não mostra nada enquanto fechado', () => {
    fixture = TestBed.createComponent(AlteracoesModalComponent);
    fixture.componentRef.setInput('processo', processo('ENVIADO_DGT', ['capturar']));
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[role="dialog"]')).toBeNull();
  });

  it('mostra ao MASTER os três cartões com as linhas de contexto preenchidas pelo Processo', () => {
    montar('EM_VALIDACAO_DGT', { acoes: ['validar', 'rejeitar', 'anular', 'reatribuir-autor'] });

    expect(cartao('OWNER').textContent).toContain('Alterar Owner');
    expect(cartao('OWNER').textContent).toContain('Owner atual: Helena Marques');
    expect(cartao('ESTADO').textContent).toContain('Alterar estado');
    expect(cartao('ESTADO').textContent).toContain('Estado atual: Em Validação DGT');
    expect(cartao('ANULAR').textContent).toContain('Anular processo');
    expect(cartao('ANULAR').textContent).toContain('Ação irreversível. Requer motivo obrigatório.');
    expect(fixture.nativeElement.querySelectorAll('[data-acao]')).toHaveLength(3);
  });

  it.each(['ENVIADO_DGT', 'EM_VALIDACAO_DGT', 'VALIDADO_DGT'])(
    'esconde Alterar Owner e Anular quando não estão em acoesDisponiveis (TECNICO), em %s',
    (estado) => {
      montar(estado, { souMaster: false });

      expect(cartao('OWNER')).toBeNull();
      expect(cartao('ANULAR')).toBeNull();
      expect(cartao('ESTADO').disabled).toBe(false);
    },
  );

  it('o cartão Anular depende de `anular` e o Alterar Owner ativo de `reatribuir-autor`', () => {
    montar('EM_VALIDACAO_DGT', { acoes: ['validar', 'rejeitar', 'anular'] });
    expect(cartao('ANULAR')).not.toBeNull();
    expect(cartao('ANULAR').disabled).toBe(false);
    expect(cartao('OWNER').disabled).toBe(true);

    montar('ENVIADO_DGT', { acoes: ['reatribuir-autor'] });
    expect(cartao('OWNER').disabled).toBe(false);
    expect(cartao('ANULAR')).toBeNull();
  });

  it.each(['ENVIADO_DGT', 'VALIDADO_DGT', 'PUBLICADO', 'ANULADO'])('não mostra Anular em %s, onde `anular` não está listada', (estado) => {
    montar(estado);

    expect(cartao('ANULAR')).toBeNull();
  });

  it.each(['ENVIADO_DGT', 'EM_VALIDACAO_DGT', 'VALIDADO_DGT'])('Alterar estado fica ativo em %s, que tem transições da DGT', (estado) => {
    montar(estado);
    expect(cartao('ESTADO').disabled).toBe(false);
  });

  it.each(['EM_SUBMISSAO', 'PUBLICADO', 'ANULADO', 'REJEITADO_DGT'])(
    'Alterar estado aparece desativado, com o motivo, em %s',
    (estado) => {
      montar(estado);

      expect(cartao('ESTADO').disabled).toBe(true);
      expect(cartao('ESTADO').textContent).toContain('Sem transições disponíveis no estado atual.');
    },
  );

  it.each(['ENVIADO_DGT', 'EM_VALIDACAO_DGT', 'VALIDADO_DGT', 'PUBLICADO'])(
    'não mostra o cartão Alterar identificador único em %s',
    (estado) => {
      montar(estado);

      expect(fixture.nativeElement.querySelector('[data-acao="IDUS"]')).toBeNull();
      expect(fixture.nativeElement.textContent).not.toContain('Alterar identificador único');
    },
  );

  it.each(ESTADOS_TRANSFERIVEIS)('Alterar Owner fica ativo em %s, em que o owner age', (estado) => {
    montar(estado);
    expect(cartao('OWNER').disabled).toBe(false);
    expect(cartao('OWNER').textContent).not.toContain('O processo não pode ser transferido neste estado.');
  });

  it.each(['EM_VALIDACAO_DGT', 'VALIDADO_DGT', 'ENVIADO_INCM', 'EM_VALIDACAO_INCM', 'PUBLICADO', 'ANULADO'])(
    'ao MASTER, Alterar Owner aparece desativado em %s, com a razão',
    (estado) => {
      montar(estado);

      expect(cartao('OWNER').disabled).toBe(true);
      expect(cartao('OWNER').textContent).toContain('O processo não pode ser transferido neste estado.');
      clicar(cartao('OWNER'));
      expect(botao('Confirmar').disabled).toBe(true);
    },
  );

  it('o botão primário diz Confirmar e fica desativado até escolher um cartão', () => {
    montar('ENVIADO_DGT');

    expect(fixture.nativeElement.textContent).not.toContain('Criar notificação');
    expect(botao('Confirmar').disabled).toBe(true);

    clicar(cartao('OWNER'));

    expect(cartao('OWNER').classList).toContain('selecionado');
    expect(botao('Confirmar').disabled).toBe(false);
  });

  it('escolher um cartão não avança sozinho — só Confirmar', () => {
    montar('EM_VALIDACAO_DGT');

    clicar(cartao('ANULAR'));

    expect(fixture.componentInstance.acaoAberta()).toBeNull();
    expect(cartao('OWNER')).not.toBeNull();
  });

  it('Confirmar avança para o sub-ecrã da ação escolhida, dentro do mesmo modal', () => {
    montar('ENVIADO_DGT');
    clicar(cartao('OWNER'));

    clicar(botao('Confirmar'));

    expect(fixture.componentInstance.acaoAberta()).toBe('OWNER');
    expect(fixture.nativeElement.querySelector('h2').textContent).toContain('Alterações');
    expect(fixture.nativeElement.querySelector('.modal-corpo').textContent).toContain('Novo owner');
    expect(fixture.nativeElement.querySelector('.cartoes')).toBeNull();
  });

  it('Voltar regressa aos cartões', () => {
    montar('EM_VALIDACAO_DGT');
    clicar(cartao('ESTADO'));
    clicar(botao('Confirmar'));

    clicar(botao('Voltar'));

    expect(fixture.componentInstance.acaoAberta()).toBeNull();
    expect(fixture.nativeElement.querySelector('.cartoes')).not.toBeNull();
  });

  it('Cancelar emite fechar', () => {
    montar('EM_VALIDACAO_DGT');
    const fechar = vi.fn();
    fixture.componentInstance.fechar.subscribe(fechar);

    clicar(botao('Cancelar'));

    expect(fechar).toHaveBeenCalledOnce();
  });

  it('reabrir recomeça nos cartões, sem a escolha anterior', () => {
    montar('EM_VALIDACAO_DGT');
    clicar(cartao('ANULAR'));
    clicar(botao('Confirmar'));

    fixture.componentRef.setInput('aberto', false);
    fixture.detectChanges();
    fixture.componentRef.setInput('aberto', true);
    fixture.detectChanges();

    expect(fixture.componentInstance.acaoAberta()).toBeNull();
    expect(fixture.componentInstance.acaoSelecionada()).toBeNull();
  });

  describe('Alterar estado (issue #151)', () => {
    function abrirAlterarEstado(estado: string): void {
      montar(estado);
      clicar(cartao('ESTADO'));
      clicar(botao('Confirmar'));
    }

    function select(campo: 'novo-estado' | 'motivo-rejeicao'): HTMLSelectElement | null {
      return fixture.nativeElement.querySelector(`app-seletor[data-campo="${campo}"] select`);
    }

    /** Exclui o "Selecione…" inicial, que está desativado. */
    function opcoes(campo: 'novo-estado' | 'motivo-rejeicao'): string[] {
      return Array.from(select(campo)!.querySelectorAll('option:not([disabled])')).map(
        (o) => (o as HTMLOptionElement).textContent!.trim(),
      );
    }

    function escolher(campo: 'novo-estado' | 'motivo-rejeicao', valor: string): void {
      const elemento = select(campo)!;
      elemento.value = valor;
      elemento.dispatchEvent(new Event('change'));
      fixture.detectChanges();
    }

    it('mostra o badge do estado atual', () => {
      abrirAlterarEstado('ENVIADO_DGT');

      expect(fixture.nativeElement.querySelector('.estado-atual').textContent).toContain('Enviado DGT');
      expect(fixture.nativeElement.querySelector('.estado-atual app-estado-processo-badge')).not.toBeNull();
    });

    it.each([
      ['ENVIADO_DGT', ['Em Validação DGT']],
      ['EM_VALIDACAO_DGT', ['Validado DGT']],
      ['VALIDADO_DGT', ['Em validação INCM']],
    ])('em %s o select oferece só %j, por esta ordem', (estado, esperadas) => {
      abrirAlterarEstado(estado);

      expect(opcoes('novo-estado')).toEqual(esperadas);
    });

    it('só oferece as transições cujas ações estão listadas, e nunca enviar-pcm', () => {
      montar('EM_VALIDACAO_DGT', { acoes: ['validar'] });
      clicar(cartao('ESTADO'));
      clicar(botao('Confirmar'));
      expect(opcoes('novo-estado')).toEqual(['Validado DGT']);

      montar('VALIDADO_DGT', { acoes: ['enviar-pcm'] });
      expect(cartao('ESTADO').disabled).toBe(true);
    });

    it('num VALIDADO_DGT com enviar-pcm não há cartão «Enviar PCM»: o envio tem ponto de entrada no Resumo Validados', () => {
      montar('VALIDADO_DGT', { acoes: ['enviar-incm', 'enviar-pcm'] });

      const titulos = Array.from(fixture.nativeElement.querySelectorAll('.cartao .titulo-cartao')).map((t) =>
        (t as HTMLElement).textContent?.trim(),
      );
      expect(titulos).not.toContain('Enviar PCM');
      expect(fixture.nativeElement.textContent).not.toContain('Enviar PCM');
      clicar(cartao('ESTADO'));
      clicar(botao('Confirmar'));
      expect(opcoes('novo-estado')).toEqual(['Em validação INCM']);
    });

    it.each([
      ['ENVIADO_DGT', 'EM_VALIDACAO_DGT', 'capturar'],
      ['EM_VALIDACAO_DGT', 'VALIDADO_DGT', 'validar'],
      ['VALIDADO_DGT', 'EM_VALIDACAO_INCM', 'enviarIncm'],
    ] as const)('quem não é MASTER também executa a transição de %s para %s', (estado, destino, metodo) => {
      montar(estado, { souMaster: false });
      clicar(cartao('ESTADO'));
      clicar(botao('Confirmar'));
      escolher('novo-estado', destino);

      clicar(botao('Confirmar'));

      expect(processoServiceMock[metodo]).toHaveBeenCalledWith('p1', 5);
    });

    it('um estado sem transições da DGT não tem opções', () => {
      montar('PUBLICADO');
      fixture.componentInstance.acaoAberta.set('ESTADO');
      fixture.detectChanges();

      expect(opcoes('novo-estado')).toEqual([]);
    });

    it('Confirmar fica desativado até haver um novo estado escolhido', () => {
      abrirAlterarEstado('ENVIADO_DGT');
      expect(botao('Confirmar').disabled).toBe(true);

      escolher('novo-estado', 'EM_VALIDACAO_DGT');

      expect(botao('Confirmar').disabled).toBe(false);
    });

    it('em EM_VALIDACAO_DGT não oferece Rejeitado DGT nem o motivo, mesmo com `rejeitar` nas ações', () => {
      abrirAlterarEstado('EM_VALIDACAO_DGT');

      expect(opcoes('novo-estado')).not.toContain('Rejeitado DGT');
      expect(select('motivo-rejeicao')).toBeNull();
      escolher('novo-estado', 'VALIDADO_DGT');
      expect(select('motivo-rejeicao')).toBeNull();
      clicar(botao('Confirmar'));
      expect(processoServiceMock.rejeitar).not.toHaveBeenCalled();
    });

    it.each([
      ['ENVIADO_DGT', 'EM_VALIDACAO_DGT', 'capturar'],
      ['EM_VALIDACAO_DGT', 'VALIDADO_DGT', 'validar'],
      ['VALIDADO_DGT', 'EM_VALIDACAO_INCM', 'enviarIncm'],
    ] as const)('em %s, escolher %s chama %s', (estado, destino, metodo) => {
      abrirAlterarEstado(estado);
      escolher('novo-estado', destino);

      clicar(botao('Confirmar'));

      expect(processoServiceMock[metodo]).toHaveBeenCalledWith('p1', 5);
      for (const outro of Object.keys(processoServiceMock).filter((m) => m !== metodo)) {
        expect(processoServiceMock[outro as keyof typeof processoServiceMock]).not.toHaveBeenCalled();
      }
    });

    it('com sucesso, emite a resposta em alterado', () => {
      const alterado = vi.fn();
      abrirAlterarEstado('ENVIADO_DGT');
      fixture.componentInstance.alterado.subscribe(alterado);
      escolher('novo-estado', 'EM_VALIDACAO_DGT');

      clicar(botao('Confirmar'));

      expect(alterado).toHaveBeenCalledWith({ estado: 'EM_VALIDACAO_DGT' });
    });

    it.each(['EM_VALIDACAO_INCM', 'ANULADO_INCM'])('enviar à INCM emite o estado que a API devolve, %s, e não o do botão', (devolvido) => {
      processoServiceMock.enviarIncm.mockReturnValue(of({ estado: devolvido }));
      const alterado = vi.fn();
      abrirAlterarEstado('VALIDADO_DGT');
      fixture.componentInstance.alterado.subscribe(alterado);
      escolher('novo-estado', 'EM_VALIDACAO_INCM');

      clicar(botao('Confirmar'));

      expect(alterado).toHaveBeenCalledExactlyOnceWith({ estado: devolvido });
    });

    it('com a INCM indisponível mostra a mensagem do backend, mantém o modal e deixa repetir o envio', () => {
      const mensagem = 'A INCM não respondeu ao envio do ato. Tente novamente mais tarde.';
      processoServiceMock.enviarIncm.mockReturnValueOnce(
        throwError(() => new ApiError('INTEGRACAO_INDISPONIVEL', mensagem, 't1', null)),
      );
      const alterado = vi.fn();
      const recarregar = vi.fn();
      const desatualizado = vi.fn();
      abrirAlterarEstado('VALIDADO_DGT');
      fixture.componentInstance.alterado.subscribe(alterado);
      fixture.componentInstance.recarregar.subscribe(recarregar);
      fixture.componentInstance.desatualizado.subscribe(desatualizado);
      escolher('novo-estado', 'EM_VALIDACAO_INCM');

      clicar(botao('Confirmar'));

      expect(fixture.nativeElement.querySelector('.error-banner')?.textContent).toContain(mensagem);
      expect(fixture.nativeElement.querySelector('[role="dialog"]')).not.toBeNull();
      expect(alterado).not.toHaveBeenCalled();
      expect(recarregar).not.toHaveBeenCalled();
      expect(desatualizado).not.toHaveBeenCalled();
      expect(fixture.nativeElement.textContent).not.toContain('Recarregar');
      expect(botao('Confirmar').disabled).toBe(false);

      clicar(botao('Confirmar'));

      expect(processoServiceMock.enviarIncm).toHaveBeenCalledTimes(2);
      expect(alterado).toHaveBeenCalledExactlyOnceWith({ estado: 'EM_VALIDACAO_INCM' });
    });

    it('um 409 mostra a mensagem do backend e oferece Recarregar, sem repetir a ação', () => {
      processoServiceMock.capturar.mockReturnValue(
        throwError(() => new ApiError('ESTADO_INVALIDO', 'Esta tentativa foi alterada por outro pedido — recarregue antes de repetir.', 't1', null)),
      );
      const recarregar = vi.fn();
      abrirAlterarEstado('ENVIADO_DGT');
      fixture.componentInstance.recarregar.subscribe(recarregar);
      escolher('novo-estado', 'EM_VALIDACAO_DGT');

      clicar(botao('Confirmar'));

      expect(fixture.nativeElement.textContent).toContain('Esta tentativa foi alterada por outro pedido');
      expect(botao('Confirmar').disabled).toBe(true);
      clicar(botao('Recarregar'));
      expect(recarregar).toHaveBeenCalledOnce();
      expect(processoServiceMock.capturar).toHaveBeenCalledOnce();
    });

    it('VERSAO_DESATUALIZADA passa o erro ao detalhe em vez de o mostrar no modal, sem repetir a ação', () => {
      const erro = new ApiError('VERSAO_DESATUALIZADA', 'O processo foi alterado; recarregue.', 't1', null);
      processoServiceMock.capturar.mockReturnValue(throwError(() => erro));
      const desatualizado = vi.fn();
      abrirAlterarEstado('ENVIADO_DGT');
      fixture.componentInstance.desatualizado.subscribe(desatualizado);
      escolher('novo-estado', 'EM_VALIDACAO_DGT');

      clicar(botao('Confirmar'));

      expect(desatualizado).toHaveBeenCalledExactlyOnceWith(erro);
      expect(fixture.nativeElement.textContent).not.toContain('O processo foi alterado');
      expect(processoServiceMock.capturar).toHaveBeenCalledOnce();
    });

    it('um erro que não é 409 mostra a mensagem, sem Recarregar', () => {
      processoServiceMock.validar.mockReturnValue(
        throwError(() => new ApiError('SEM_PERMISSAO', 'Sem permissão.', 't1', null)),
      );
      abrirAlterarEstado('EM_VALIDACAO_DGT');
      escolher('novo-estado', 'VALIDADO_DGT');

      clicar(botao('Confirmar'));

      expect(fixture.nativeElement.textContent).toContain('Não tem permissão para executar esta ação.');
      expect(fixture.nativeElement.textContent).not.toContain('Recarregar');
    });

    it('com itens por validar, validar o processo mostra a mensagem do backend e o processo não muda de estado', () => {
      processoServiceMock.validar.mockReturnValue(
        throwError(() => new ApiError('ITENS_POR_REVER', 'Reveja todos os documentos antes de continuar.', 't1', null)),
      );
      const alterado = vi.fn();
      abrirAlterarEstado('EM_VALIDACAO_DGT');
      fixture.componentInstance.alterado.subscribe(alterado);
      escolher('novo-estado', 'VALIDADO_DGT');

      clicar(botao('Confirmar'));

      expect(fixture.nativeElement.textContent).toContain('Reveja todos os documentos antes de continuar.');
      expect(alterado).not.toHaveBeenCalled();
      expect(processoServiceMock.validar).toHaveBeenCalledOnce();
    });

    it('com todos os itens validados, validar o processo emite o processo em Validado DGT', () => {
      const alterado = vi.fn();
      abrirAlterarEstado('EM_VALIDACAO_DGT');
      fixture.componentInstance.alterado.subscribe(alterado);
      escolher('novo-estado', 'VALIDADO_DGT');

      clicar(botao('Confirmar'));

      expect(alterado).toHaveBeenCalledExactlyOnceWith({ estado: 'VALIDADO_DGT' });
    });

    it('Voltar limpa a escolha do sub-ecrã', () => {
      abrirAlterarEstado('ENVIADO_DGT');
      escolher('novo-estado', 'EM_VALIDACAO_DGT');

      clicar(botao('Voltar'));

      expect(fixture.componentInstance.transicaoEscolhida()).toBeNull();
    });
  });

  describe('Alterar Owner (issue #153)', () => {
    function abrirOwner(estado = 'ENVIADO_DGT'): void {
      montar(estado);
      clicar(cartao('OWNER'));
      clicar(botao('Confirmar'));
    }

    function select(): HTMLSelectElement {
      return fixture.nativeElement.querySelector('app-seletor[data-campo="novo-owner"] select') as HTMLSelectElement;
    }

    function escolher(id: string): void {
      select().value = id;
      select().dispatchEvent(new Event('change'));
      fixture.detectChanges();
    }

    it('mostra o owner atual, o seletor com rótulo e sem campo de motivo, e pede os utilizadores da entidade', () => {
      abrirOwner();

      expect(fixture.nativeElement.querySelector('[data-campo="owner-atual"]').textContent).toContain('Helena Marques');
      const corpo = fixture.nativeElement.querySelector('.modal-corpo') as HTMLElement;
      expect(corpo.textContent).toContain('Novo owner *');
      expect(corpo.textContent).not.toContain('Motivo');
      expect(corpo.querySelector('textarea')).toBeNull();
      const rotulo = corpo.querySelector(`label[for="${select().id}"]`);
      expect(rotulo?.textContent).toContain('Novo owner');
      expect(utilizadorServiceMock.listar).toHaveBeenCalledWith({ entidadeId: 7 }, 0, 100);
    });

    it('lista os candidatos com nome e email, sem o autor atual', () => {
      abrirOwner();

      const opcoes = Array.from(select().querySelectorAll('option')).filter((o) => o.value !== '');
      expect(opcoes.map((o) => o.value)).toEqual(['u-ricardo']);
      expect(opcoes[0].textContent).toContain('Ricardo Nunes');
      expect(opcoes[0].textContent).toContain('ricardo.silva@incm.pt');
    });

    it('sem outros candidatos mostra o aviso e Confirmar fica desativado', () => {
      utilizadorServiceMock.listar.mockReturnValue(pagina([utilizador('u-helena', 'Helena Marques', 'helena.sintra@incm.pt')]));
      abrirOwner();

      expect(fixture.nativeElement.textContent).toContain('Nenhum outro utilizador desta entidade.');
      expect(select().disabled).toBe(true);
      expect(botao('Confirmar').disabled).toBe(true);
    });

    it('Confirmar fica ativo só com um novo owner escolhido, sem motivo', () => {
      abrirOwner();
      expect(botao('Confirmar').disabled).toBe(true);

      escolher('u-ricardo');
      expect(botao('Confirmar').disabled).toBe(false);
    });

    it('chama reatribuirAutor sem motivo e emite a resposta com o nome do novo autor', () => {
      const alterado = vi.fn();
      abrirOwner();
      fixture.componentInstance.alterado.subscribe(alterado);
      escolher('u-ricardo');

      clicar(botao('Confirmar'));

      expect(processoServiceMock.reatribuirAutor).toHaveBeenCalledWith('p1', 5, 'u-ricardo');
      expect(alterado).toHaveBeenCalledWith({ estado: 'ENVIADO_DGT', criadoPorId: 'u-ricardo', autorNome: 'Ricardo Nunes' });
    });

    it.each([
      ['estado não elegível', 'O processo não pode ser transferido neste estado.'],
      ['mesmo autor', 'O utilizador indicado já é o autor do Processo.'],
    ])('um 409 (%s) mostra a mensagem da API no app-error-banner e oferece Recarregar, sem repetir a ação', (_caso, mensagem) => {
      processoServiceMock.reatribuirAutor.mockReturnValue(throwError(() => new ApiError('ESTADO_INVALIDO', mensagem, 't1', null)));
      const recarregar = vi.fn();
      abrirOwner();
      fixture.componentInstance.recarregar.subscribe(recarregar);
      escolher('u-ricardo');

      clicar(botao('Confirmar'));

      const banner = fixture.nativeElement.querySelector('app-error-banner [role="alert"]') as HTMLElement;
      expect(banner.textContent).toContain(mensagem);
      expect(botao('Confirmar').disabled).toBe(true);
      clicar(botao('Recarregar'));
      expect(recarregar).toHaveBeenCalledOnce();
      expect(processoServiceMock.reatribuirAutor).toHaveBeenCalledOnce();
    });

    it('o 400 de um owner sem vínculo à entidade fica no seletor, ligado por aria-describedby', () => {
      const mensagem = 'O utilizador indicado não pertence à entidade do Instrumento.';
      processoServiceMock.reatribuirAutor.mockReturnValue(
        throwError(() => new ApiError('VALIDACAO', mensagem, 't1', [{ path: 'novoUtilizadorId', erro: mensagem }])),
      );
      abrirOwner();
      escolher('u-ricardo');

      clicar(botao('Confirmar'));

      const idsDescricao = select().getAttribute('aria-describedby') ?? '';
      expect(idsDescricao).not.toBe('');
      expect(fixture.nativeElement.querySelector(`#${idsDescricao}`).textContent).toContain(mensagem);
      expect(select().getAttribute('aria-invalid')).toBe('true');
    });

    it('Voltar limpa a escolha e devolve o foco ao cartão Alterar Owner', async () => {
      abrirOwner();
      document.body.appendChild(fixture.nativeElement);
      escolher('u-ricardo');

      clicar(botao('Voltar'));
      await fixture.whenStable();
      fixture.detectChanges();

      expect(fixture.componentInstance.novoOwnerId()).toBeNull();
      expect(document.activeElement).toBe(cartao('OWNER'));
      fixture.nativeElement.remove();
    });
  });

  describe('Anular processo (issue #152)', () => {
    function abrirAnular(): void {
      montar('EM_VALIDACAO_DGT');
      clicar(cartao('ANULAR'));
      clicar(botao('Confirmar'));
    }

    function motivo(): HTMLTextAreaElement {
      return fixture.nativeElement.querySelector('textarea[data-campo="motivo-anulacao"]') as HTMLTextAreaElement;
    }

    function escrever(texto: string): void {
      motivo().value = texto;
      motivo().dispatchEvent(new Event('input'));
      fixture.detectChanges();
    }

    it('mostra o estado atual, o aviso de ação irreversível e o Motivo obrigatório', () => {
      abrirAnular();

      const corpo = fixture.nativeElement.querySelector('.modal-corpo') as HTMLElement;
      expect(corpo.querySelector('app-estado-processo-badge')?.textContent).toContain('Em Validação DGT');
      expect(corpo.textContent).toContain('Ação irreversível');
      expect(corpo.textContent).toContain('Motivo *');
      expect(motivo()).not.toBeNull();
    });

    it.each(['', '   ', '\n\t '])('Confirmar fica desativado com o motivo %j', (texto) => {
      abrirAnular();

      escrever(texto);

      expect(botao('Confirmar').disabled).toBe(true);
    });

    it('chama anular com o motivo aparado e emite a resposta em alterado', () => {
      const alterado = vi.fn();
      abrirAnular();
      fixture.componentInstance.alterado.subscribe(alterado);
      escrever('  Pedido duplicado  ');

      clicar(botao('Confirmar'));

      expect(processoServiceMock.anular).toHaveBeenCalledWith('p1', 5, 'Pedido duplicado');
      expect(alterado).toHaveBeenCalledWith({ estado: 'ANULADO' });
    });

    it('um 409 mostra a mensagem do backend e oferece Recarregar, sem repetir a ação', () => {
      processoServiceMock.anular.mockReturnValue(
        throwError(() => new ApiError('ESTADO_INVALIDO', 'O processo já não está em validação — recarregue.', 't1', null)),
      );
      const recarregar = vi.fn();
      abrirAnular();
      fixture.componentInstance.recarregar.subscribe(recarregar);
      escrever('Pedido duplicado');

      clicar(botao('Confirmar'));

      expect(fixture.nativeElement.textContent).toContain('O processo já não está em validação');
      expect(botao('Confirmar').disabled).toBe(true);
      clicar(botao('Recarregar'));
      expect(recarregar).toHaveBeenCalledOnce();
      expect(processoServiceMock.anular).toHaveBeenCalledOnce();
    });

    it('Voltar limpa o motivo', () => {
      abrirAnular();
      escrever('Pedido duplicado');

      clicar(botao('Voltar'));

      expect(fixture.componentInstance.motivoAnulacao()).toBe('');
    });
  });
});
