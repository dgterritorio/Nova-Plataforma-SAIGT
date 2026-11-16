import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { ListaValoresComponent, MENSAGEM_LISTA_VAZIA } from './lista-valores.component';
import { MENSAGEM_ULTIMO_ATIVO } from './valor-lista-modal.component';
import { AuthService } from '../../../core/auth/auth.service';
import { ApiError } from '../../../core/models/error-response.model';
import { Catalogo } from '../../../models/catalogo.model';
import { ListaValoresItem, TIPOS_LISTA_VALORES, TipoListaValores } from '../../../models/lista-valores.model';
import { CatalogoService } from '../../../services/catalogo.service';
import { ListaValoresService } from '../../../services/lista-valores.service';

type Mock = ReturnType<typeof vi.fn>;

const TIPOS: TipoListaValores[] = [
  { tipo: 'TOPONIMO', descricao: 'Topónimo' },
  { tipo: 'FREGUESIA', descricao: 'Freguesia' },
  { tipo: 'DOMINIO_SRUP', descricao: 'Domínio SRUP' },
  { tipo: 'CLASSE_SRUP', descricao: 'Classe SRUP' },
];

function valor(campos: Partial<ListaValoresItem>): ListaValoresItem {
  return { id: 1, tipo: 'FREGUESIA', codigo: 'LISBOA', descricao: 'Lisboa', ordem: 1, ativo: true, paiId: null, ...campos };
}

describe('ListaValoresComponent', () => {
  let fixture: ComponentFixture<ListaValoresComponent>;
  let listaValores: Record<'tipos' | 'listar' | 'criar' | 'atualizar', Mock>;

  const catalogos: Catalogo[] = [
    { titulo: 'Tipos IGT', itens: ['PDM', 'PP', 'PU', 'PROT', 'PEOT', 'PMOT', 'PIMOT'] },
    { titulo: 'Tipos de procedimento', itens: ['Elaboração', 'Revisão'] },
  ];

  const el = () => fixture.nativeElement as HTMLElement;
  const cartaoLista = (tipo: string) => el().querySelector<HTMLElement>(`.cartao-lista[data-tipo="${tipo}"]`)!;
  const itensDe = (tipo: string) =>
    [...cartaoLista(tipo).querySelectorAll('.item-valor .texto-valor > span:first-child')].map((e) => e.textContent?.trim());
  const botao = (rotulo: string) => el().querySelector<HTMLButtonElement>(`button[aria-label="${rotulo}"]`);
  const dialogo = () => el().querySelector<HTMLElement>('app-valor-lista-modal [role="dialog"]');
  const campo = (nome: string) => el().querySelector<HTMLInputElement>(`app-valor-lista-modal [data-campo="${nome}"] input`)!;
  const erroDe = (nome: string) =>
    el()
      .querySelector(`app-valor-lista-modal [data-campo="${nome}"] .campo-texto-erro, app-valor-lista-modal [data-campo="${nome}"] .seletor-erro`)
      ?.textContent?.trim();

  function configurar(
    perfil: string,
    valores: ListaValoresItem[] = [],
    catalogoServiceMock?: { listar: Mock },
    tipos: readonly TipoListaValores[] = TIPOS,
  ): void {
    listaValores = {
      tipos: vi.fn().mockReturnValue(of([...tipos])),
      listar: vi.fn().mockReturnValue(of(valores)),
      criar: vi.fn(),
      atualizar: vi.fn(),
    };
    TestBed.configureTestingModule({
      imports: [ListaValoresComponent],
      providers: [
        provideRouter([]),
        { provide: CatalogoService, useValue: catalogoServiceMock ?? { listar: vi.fn().mockReturnValue(of(catalogos)) } },
        { provide: ListaValoresService, useValue: listaValores },
        {
          provide: AuthService,
          useValue: {
            perfilInterno: vi.fn().mockReturnValue(perfil),
            nome: vi.fn().mockReturnValue('Ana Teixeira'),
            origem: vi.fn().mockReturnValue('AD_DGT'),
            entidade: vi.fn().mockReturnValue(null),
          },
        },
      ],
    });
    fixture = TestBed.createComponent(ListaValoresComponent);
    fixture.detectChanges();
  }

  function escrever(nome: string, texto: string): void {
    const input = campo(nome);
    input.value = texto;
    input.dispatchEvent(new Event('input'));
    input.dispatchEvent(new Event('blur'));
    fixture.detectChanges();
  }

  function submeter(): void {
    el().querySelector('app-valor-lista-modal form')!.dispatchEvent(new Event('submit'));
    fixture.detectChanges();
  }

  function clicar(rotulo: string): void {
    botao(rotulo)!.click();
    fixture.detectChanges();
  }

  afterEach(() => TestBed.resetTestingModule());

  describe('catálogos globais (em leitura)', () => {
    it('mostra os cartões devolvidos por GET /api/v1/catalogos, com os respetivos itens', () => {
      configurar('MASTER');

      const titulos = [...el().querySelectorAll('.cabecalho-cartao h2')].map((e) => e.textContent?.trim());
      expect(titulos).toEqual(['Tipos IGT', 'Tipos de procedimento']);
      const itensTiposIgt = [...el().querySelectorAll('.cartao')[0].querySelectorAll('.item')].map((e) => e.textContent?.trim());
      expect(itensTiposIgt).toEqual(['PDM', 'PP', 'PU', 'PROT', 'PEOT', 'PMOT', 'PIMOT']);
    });

    it('mostra "Sem itens." para um catálogo vazio', () => {
      configurar('MASTER', [], { listar: vi.fn().mockReturnValue(of([{ titulo: 'Tipos IGT', itens: [] }])) });

      expect(el().textContent).toContain('Sem itens.');
    });

    it('mostra o banner de erro quando o pedido falha', () => {
      configurar('MASTER', [], { listar: vi.fn().mockReturnValue(throwError(() => ({ codigo: 'SEM_PERMISSAO', mensagem: 'erro' }))) });

      expect(el().querySelector('.error-banner')).not.toBeNull();
    });

    it('colapsa um cartão com muitos itens e expande/colapsa ao clicar em "Mostrar mais"/"Mostrar menos"', () => {
      const tiposProcedimento = Array.from({ length: 12 }, (_, i) => `Procedimento ${i + 1}`);
      configurar('MASTER', [], { listar: vi.fn().mockReturnValue(of([{ titulo: 'Tipos de procedimento', itens: tiposProcedimento }])) });

      const cartao = el().querySelector('.cartao')!;
      expect(cartao.querySelectorAll('.item')).toHaveLength(8);
      const expandir = cartao.querySelector<HTMLButtonElement>('.botao-expandir')!;
      expect(expandir.textContent?.trim()).toBe('Mostrar mais (4)');
      expect(expandir.getAttribute('aria-expanded')).toBe('false');

      expandir.click();
      fixture.detectChanges();
      expect(cartao.querySelectorAll('.item')).toHaveLength(12);
      expect(expandir.textContent?.trim()).toBe('Mostrar menos');

      expandir.click();
      fixture.detectChanges();
      expect(cartao.querySelectorAll('.item')).toHaveLength(8);
    });

    it('os catálogos globais não têm «+» nem «Editar», nem para o Master', () => {
      configurar('MASTER');

      const catalogo = el().querySelectorAll('.grelha-cartoes')[0];
      expect(catalogo.querySelector('.botao-acrescentar, .botao-editar')).toBeNull();
    });
  });

  describe('listas de valores', () => {
    it('um cartão por tipo, ordenados pelo rótulo, cada um com os valores do tipo por ordem', () => {
      configurar('MASTER', [
        valor({ id: 1, codigo: 'SINTRA', descricao: 'Sintra', ordem: 2 }),
        valor({ id: 2, codigo: 'ALVALADE', descricao: 'Alvalade', ordem: 1 }),
      ]);

      const titulos = [...el().querySelectorAll('.cartao-lista h3')].map((e) => e.textContent?.trim());
      expect(titulos).toEqual(['Classe SRUP', 'Domínio SRUP', 'Freguesia', 'Topónimo']);
      expect(listaValores.listar).toHaveBeenCalledWith();
      expect(itensDe('FREGUESIA')).toEqual(['Alvalade', 'Sintra']);
    });

    it('a lista FREGUESIA sem valores mostra o aviso de que o campo não aparece nos formulários', () => {
      configurar('MASTER');

      const cartao = cartaoLista('FREGUESIA');
      expect(cartao.querySelector('h3')?.textContent?.trim()).toBe('Freguesia');
      expect(cartao.querySelector('.item.vazio')?.textContent?.trim()).toBe(MENSAGEM_LISTA_VAZIA);
      expect(MENSAGEM_LISTA_VAZIA).toBe('Sem valores. Enquanto a lista estiver vazia, o campo não aparece nos formulários.');
    });

    it('o Master vê os inativos marcados «Inativo»', () => {
      configurar('MASTER', [valor({ id: 1, ativo: false })]);

      const item = cartaoLista('FREGUESIA').querySelector('.item-valor')!;
      expect(item.classList).toContain('item-inativo');
      expect(item.querySelector('app-selo')?.textContent?.trim()).toBe('Inativo');
    });

    it('o «Mostrar mais (n)» aparece a partir de 8 valores', () => {
      configurar('MASTER', Array.from({ length: 10 }, (_, i) => valor({ id: i + 1, codigo: `F${i}`, descricao: `Freguesia ${i}`, ordem: i })));

      expect(itensDe('FREGUESIA')).toHaveLength(8);
      expect(cartaoLista('FREGUESIA').querySelector('.botao-expandir')?.textContent?.trim()).toBe('Mostrar mais (2)');
    });

    it('sem a lista dos valores, mostra a faixa de erro e não diz que as listas estão vazias', () => {
      configurar('MASTER');
      listaValores.listar.mockReturnValue(throwError(() => new ApiError('SEM_PERMISSAO', 'x', 't', null)));
      fixture.componentInstance.ngOnInit();
      fixture.detectChanges();

      expect(el().querySelector('.zona-listas .error-banner')?.textContent).toContain('Não tem permissão');
      expect(el().textContent).not.toContain(MENSAGEM_LISTA_VAZIA);
    });
  });

  describe('Técnico', () => {
    it('vê as listas sem «+» nem «Editar», e sem os valores inativos', () => {
      configurar('TECNICO', [valor({ id: 1, descricao: 'Lisboa' }), valor({ id: 2, codigo: 'VELHA', descricao: 'Velha', ativo: false })]);

      expect(itensDe('FREGUESIA')).toEqual(['Lisboa']);
      expect(el().querySelector('.botao-acrescentar')).toBeNull();
      expect(el().querySelector('.botao-editar')).toBeNull();
    });

    it('não pede os catálogos globais, que a API reserva ao Master', () => {
      const catalogoServiceMock = { listar: vi.fn().mockReturnValue(of(catalogos)) };
      configurar('TECNICO', [], catalogoServiceMock);

      expect(catalogoServiceMock.listar).not.toHaveBeenCalled();
      expect(el().querySelector('.error-banner')).toBeNull();
      expect(el().querySelectorAll('.cartao-lista').length).toBe(TIPOS.length);
    });
  });

  describe('acrescentar um valor', () => {
    it('o «+» abre o modal e grava ALVALADE, que aparece no cartão', () => {
      configurar('MASTER', [valor({ id: 1, ordem: 4 })]);
      const criado = valor({ id: 9, codigo: 'ALVALADE', descricao: 'Alvalade', ordem: 5 });
      listaValores.criar.mockReturnValue(of(criado));

      clicar('Acrescentar valor a Freguesia');
      expect(dialogo()).not.toBeNull();
      escrever('codigo', 'ALVALADE');
      escrever('descricao', 'Alvalade');
      submeter();

      expect(listaValores.criar).toHaveBeenCalledWith({ tipo: 'FREGUESIA', codigo: 'ALVALADE', descricao: 'Alvalade', ordem: 5 });
      expect(dialogo()).toBeNull();
      expect(itensDe('FREGUESIA')).toEqual(['Lisboa', 'Alvalade']);
    });

    it('a ordem escrita vai como número', () => {
      configurar('MASTER');
      listaValores.criar.mockReturnValue(of(valor({ id: 9 })));

      clicar('Acrescentar valor a Freguesia');
      escrever('codigo', 'ALVALADE');
      escrever('descricao', 'Alvalade');
      escrever('ordem', '3');
      submeter();

      expect(listaValores.criar).toHaveBeenCalledWith({ tipo: 'FREGUESIA', codigo: 'ALVALADE', descricao: 'Alvalade', ordem: 3 });
    });

    it('o código só aceita maiúsculas, algarismos e _, sem espaços, e não grava sem código nem descrição', () => {
      configurar('MASTER');

      clicar('Acrescentar valor a Freguesia');
      escrever('codigo', 'alva lade');
      expect(erroDe('codigo')).toBe('Use só maiúsculas, algarismos e _, sem espaços.');
      submeter();

      expect(listaValores.criar).not.toHaveBeenCalled();
      expect(el().querySelector<HTMLButtonElement>('app-valor-lista-modal button.gravar')?.disabled).toBe(true);
    });

    it('o código repetido (409) aparece junto ao campo código e o modal fica aberto sem mexer no cartão', () => {
      configurar('MASTER', [valor({ id: 1, codigo: 'ALVALADE', descricao: 'Alvalade' })]);
      listaValores.criar.mockReturnValue(
        throwError(() => new ApiError('REFERENCIA_DUPLICADA', 'Já existe um item com este código para este tipo.', 't', null)),
      );

      clicar('Acrescentar valor a Freguesia');
      escrever('codigo', 'ALVALADE');
      escrever('descricao', 'Alvalade de novo');
      submeter();

      expect(erroDe('codigo')).toBe('Já existe um item com este código para este tipo.');
      expect(campo('codigo').getAttribute('aria-invalid')).toBe('true');
      expect(dialogo()).not.toBeNull();
      expect(itensDe('FREGUESIA')).toEqual(['Alvalade']);
    });

    it('um 400 VALIDACAO aparece junto ao campo indicado', () => {
      configurar('MASTER');
      listaValores.criar.mockReturnValue(
        throwError(() => new ApiError('VALIDACAO', 'Payload inválido.', 't', [{ path: 'descricao', erro: 'Descrição inválida.' }])),
      );

      clicar('Acrescentar valor a Freguesia');
      escrever('codigo', 'ALVALADE');
      escrever('descricao', 'Alvalade');
      submeter();

      expect(erroDe('descricao')).toBe('Descrição inválida.');
    });

    it('um 403 mostra a faixa de erro habitual dentro do modal', () => {
      configurar('MASTER');
      listaValores.criar.mockReturnValue(throwError(() => new ApiError('SEM_PERMISSAO', 'Só MASTER pode gerir listas de valores.', 't', null)));

      clicar('Acrescentar valor a Freguesia');
      escrever('codigo', 'ALVALADE');
      escrever('descricao', 'Alvalade');
      submeter();

      expect(el().querySelector('app-valor-lista-modal .error-banner')?.textContent).toContain('Não tem permissão para executar esta ação.');
    });
  });

  describe('hierarquia das SRUP', () => {
    const dominios = [
      valor({ id: 10, tipo: 'DOMINIO_SRUP', codigo: 'REC_HIDRICOS', descricao: 'Recursos hídricos' }),
      valor({ id: 11, tipo: 'DOMINIO_SRUP', codigo: 'ANTIGO', descricao: 'Antigo', ativo: false }),
    ];

    it('ao acrescentar uma Classe SRUP tem de escolher o domínio pai, só entre os ativos', () => {
      configurar('MASTER', dominios);
      listaValores.criar.mockReturnValue(of(valor({ id: 20, tipo: 'CLASSE_SRUP', codigo: 'LEITOS', descricao: 'Leitos', paiId: 10 })));

      clicar('Acrescentar valor a Classe SRUP');
      const select = el().querySelector<HTMLSelectElement>('app-valor-lista-modal [data-campo="pai"] select')!;
      expect(el().querySelector('app-valor-lista-modal [data-campo="pai"] label')?.textContent).toContain('Domínio SRUP');
      expect([...select.options].filter((o) => o.value).map((o) => o.textContent?.trim())).toEqual(['Recursos hídricos']);

      escrever('codigo', 'LEITOS');
      escrever('descricao', 'Leitos');
      expect(el().querySelector<HTMLButtonElement>('app-valor-lista-modal button.gravar')?.disabled).toBe(true);
      submeter();
      expect(listaValores.criar).not.toHaveBeenCalled();

      select.value = '10';
      select.dispatchEvent(new Event('change'));
      fixture.detectChanges();
      submeter();

      expect(listaValores.criar).toHaveBeenCalledWith({ tipo: 'CLASSE_SRUP', codigo: 'LEITOS', descricao: 'Leitos', ordem: 1, paiId: 10 });
      expect(cartaoLista('CLASSE_SRUP').querySelector('.pai-valor')?.textContent?.trim()).toBe('Domínio SRUP: Recursos hídricos');
    });

    it('nas listas sem hierarquia não pede pai nem envia paiId', () => {
      configurar('MASTER', dominios);
      listaValores.criar.mockReturnValue(of(valor({ id: 30 })));

      clicar('Acrescentar valor a Freguesia');
      expect(el().querySelector('app-valor-lista-modal [data-campo="pai"]')).toBeNull();
      escrever('codigo', 'ALVALADE');
      escrever('descricao', 'Alvalade');
      submeter();

      expect(listaValores.criar.mock.calls[0][0]).not.toHaveProperty('paiId');
    });
  });

  describe('Região e CIM / AM do SGIFR (US-486)', () => {
    const regioes = [
      valor({ id: 40, tipo: 'REGIAO', codigo: 'NORTE', descricao: 'Norte' }),
      valor({ id: 41, tipo: 'REGIAO', codigo: 'ANTIGA', descricao: 'Antiga', ativo: false }),
    ];

    it('com os tipos reais, a Região e a CIM / AM aparecem vazias, com o aviso de lista vazia', () => {
      configurar('MASTER', [], undefined, TIPOS_LISTA_VALORES);

      expect(cartaoLista('REGIAO').querySelector('h3')?.textContent?.trim()).toBe('Região');
      expect(cartaoLista('CIM_AM').querySelector('h3')?.textContent?.trim()).toBe('CIM / AM');
      expect(cartaoLista('REGIAO').querySelector('.item.vazio')?.textContent?.trim()).toBe(MENSAGEM_LISTA_VAZIA);
      expect(cartaoLista('CIM_AM').querySelector('.item.vazio')?.textContent?.trim()).toBe(MENSAGEM_LISTA_VAZIA);
    });

    it('a Região acrescenta-se sem pai', () => {
      configurar('MASTER', [], undefined, TIPOS_LISTA_VALORES);
      listaValores.criar.mockReturnValue(of(valor({ id: 40, tipo: 'REGIAO', codigo: 'NORTE', descricao: 'Norte' })));

      clicar('Acrescentar valor a Região');
      expect(el().querySelector('app-valor-lista-modal [data-campo="pai"]')).toBeNull();
      escrever('codigo', 'NORTE');
      escrever('descricao', 'Norte');
      submeter();

      expect(listaValores.criar).toHaveBeenCalledWith({ tipo: 'REGIAO', codigo: 'NORTE', descricao: 'Norte', ordem: 1 });
      expect(itensDe('REGIAO')).toEqual(['Norte']);
    });

    it('ao acrescentar uma CIM / AM tem de escolher a Região, só entre as ativas, e o cartão mostra-a', () => {
      configurar('MASTER', regioes, undefined, TIPOS_LISTA_VALORES);
      listaValores.criar.mockReturnValue(
        of(valor({ id: 50, tipo: 'CIM_AM', codigo: 'ALTO_MINHO', descricao: 'CIM do Alto Minho', paiId: 40 })),
      );

      clicar('Acrescentar valor a CIM / AM');
      const select = el().querySelector<HTMLSelectElement>('app-valor-lista-modal [data-campo="pai"] select')!;
      expect(el().querySelector('app-valor-lista-modal [data-campo="pai"] label')?.textContent).toContain('Região');
      expect([...select.options].filter((o) => o.value).map((o) => o.textContent?.trim())).toEqual(['Norte']);

      escrever('codigo', 'ALTO_MINHO');
      escrever('descricao', 'CIM do Alto Minho');
      submeter();
      expect(listaValores.criar).not.toHaveBeenCalled();

      select.value = '40';
      select.dispatchEvent(new Event('change'));
      fixture.detectChanges();
      submeter();

      expect(listaValores.criar).toHaveBeenCalledWith({
        tipo: 'CIM_AM',
        codigo: 'ALTO_MINHO',
        descricao: 'CIM do Alto Minho',
        ordem: 1,
        paiId: 40,
      });
      expect(cartaoLista('CIM_AM').querySelector('.pai-valor')?.textContent?.trim()).toBe('Região: Norte');
    });

    it('o Técnico vê a Região e a CIM / AM sem «+» nem «Editar»', () => {
      configurar(
        'TECNICO',
        [regioes[0], valor({ id: 50, tipo: 'CIM_AM', codigo: 'ALTO_MINHO', descricao: 'CIM do Alto Minho', paiId: 40 })],
        undefined,
        TIPOS_LISTA_VALORES,
      );

      expect(itensDe('REGIAO')).toEqual(['Norte']);
      expect(itensDe('CIM_AM')).toEqual(['CIM do Alto Minho']);
      expect(cartaoLista('CIM_AM').querySelector('.pai-valor')?.textContent?.trim()).toBe('Região: Norte');
      expect(botao('Acrescentar valor a Região')).toBeNull();
      expect(botao('Acrescentar valor a CIM / AM')).toBeNull();
      expect(el().querySelector('.botao-editar')).toBeNull();
    });
  });

  describe('corrigir, ordenar e desativar', () => {
    it('«Editar» corrige a descrição e a ordem, sem deixar mudar o código', () => {
      configurar('MASTER', [valor({ id: 1, descricao: 'Lisboa', ordem: 1 }), valor({ id: 2, codigo: 'SINTRA', descricao: 'Sintra', ordem: 2 })]);
      listaValores.atualizar.mockReturnValue(of(valor({ id: 1, descricao: 'Lisboa (cidade)', ordem: 3 })));

      clicar('Editar Lisboa');
      expect(el().querySelector('app-valor-lista-modal [data-campo="codigo"] input')).toBeNull();
      expect(el().querySelector('app-valor-lista-modal [data-campo="codigo"]')?.textContent).toContain('LISBOA');
      escrever('descricao', 'Lisboa (cidade)');
      escrever('ordem', '3');
      submeter();

      expect(listaValores.atualizar).toHaveBeenCalledWith(1, { descricao: 'Lisboa (cidade)', ordem: 3 });
      expect(itensDe('FREGUESIA')).toEqual(['Sintra', 'Lisboa (cidade)']);
    });

    it('marcar inativo com outros valores ativos grava logo e o valor fica «Inativo»', () => {
      configurar('MASTER', [valor({ id: 1 }), valor({ id: 2, codigo: 'SINTRA', descricao: 'Sintra', ordem: 2 })]);
      listaValores.atualizar.mockReturnValue(of(valor({ id: 1, ativo: false })));

      clicar('Editar Lisboa');
      const ativo = el().querySelector<HTMLInputElement>('app-valor-lista-modal [data-campo="ativo"] input')!;
      ativo.click();
      fixture.detectChanges();
      submeter();

      expect(listaValores.atualizar).toHaveBeenCalledWith(1, { ativo: false });
      expect(el().textContent).not.toContain(MENSAGEM_ULTIMO_ATIVO);
      expect(cartaoLista('FREGUESIA').querySelector('.item-inativo app-selo')?.textContent?.trim()).toBe('Inativo');
    });

    it('desativar o último valor ativo pede confirmação e só grava depois do «Desativar»', () => {
      configurar('MASTER', [valor({ id: 1 }), valor({ id: 2, codigo: 'VELHA', descricao: 'Velha', ativo: false })]);
      listaValores.atualizar.mockReturnValue(of(valor({ id: 1, ativo: false })));

      clicar('Editar Lisboa');
      el().querySelector<HTMLInputElement>('app-valor-lista-modal [data-campo="ativo"] input')!.click();
      fixture.detectChanges();
      submeter();

      expect(listaValores.atualizar).not.toHaveBeenCalled();
      expect(el().textContent).toContain(MENSAGEM_ULTIMO_ATIVO);
      expect(MENSAGEM_ULTIMO_ATIVO).toBe('Ao desativar este valor, a lista fica vazia e o campo deixa de aparecer nos formulários.');

      el().querySelector<HTMLButtonElement>('app-confirmacao-modal .botao-sim')!.click();
      fixture.detectChanges();

      expect(listaValores.atualizar).toHaveBeenCalledWith(1, { ativo: false });
      expect(dialogo()).toBeNull();
    });

    it('cancelar a confirmação não grava e deixa o modal aberto', () => {
      configurar('MASTER', [valor({ id: 1 })]);

      clicar('Editar Lisboa');
      el().querySelector<HTMLInputElement>('app-valor-lista-modal [data-campo="ativo"] input')!.click();
      fixture.detectChanges();
      submeter();
      el().querySelector<HTMLButtonElement>('app-confirmacao-modal .botao-nao')!.click();
      fixture.detectChanges();

      expect(listaValores.atualizar).not.toHaveBeenCalled();
      expect(el().textContent).not.toContain(MENSAGEM_ULTIMO_ATIVO);
      expect(dialogo()).not.toBeNull();
    });

    it('um 409 ao desativar (dependentes ativos) mostra a mensagem do servidor', () => {
      configurar('MASTER', [valor({ id: 10, tipo: 'DOMINIO_SRUP', codigo: 'D', descricao: 'Domínio A' }), valor({ id: 11, tipo: 'DOMINIO_SRUP', codigo: 'E', descricao: 'Domínio B' })]);
      listaValores.atualizar.mockReturnValue(
        throwError(() => new ApiError('ESTADO_INVALIDO', 'Desative primeiro os valores que dependem deste.', 't', null)),
      );

      clicar('Editar Domínio A');
      el().querySelector<HTMLInputElement>('app-valor-lista-modal [data-campo="ativo"] input')!.click();
      fixture.detectChanges();
      submeter();

      expect(el().querySelector('app-valor-lista-modal .error-banner')?.textContent).toContain('Desative primeiro os valores que dependem deste.');
    });
  });

  describe('acessibilidade', () => {
    it('o modal é um diálogo com título, as ações têm rótulos e o Esc fecha-o', () => {
      configurar('MASTER', [valor({ id: 1 })]);

      expect(botao('Acrescentar valor a Freguesia')).not.toBeNull();
      expect(botao('Editar Lisboa')).not.toBeNull();
      clicar('Acrescentar valor a Freguesia');
      const caixa = dialogo()!;
      expect(caixa.getAttribute('aria-modal')).toBe('true');
      const titulo = el().querySelector(`#${caixa.getAttribute('aria-labelledby')}`);
      expect(titulo?.textContent?.trim()).toBe('Acrescentar valor');

      document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }));
      fixture.detectChanges();

      expect(dialogo()).toBeNull();
      expect(listaValores.criar).not.toHaveBeenCalled();
    });
  });
});
