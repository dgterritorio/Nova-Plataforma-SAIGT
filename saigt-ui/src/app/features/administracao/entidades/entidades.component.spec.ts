import { readFileSync } from 'node:fs';
import { join } from 'node:path';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { EntidadesComponent } from './entidades.component';
import { AuthService } from '../../../core/auth/auth.service';
import { ApiError } from '../../../core/models/error-response.model';
import { PageResponse } from '../../../core/models/page-response.model';
import { Contacto, Entidade } from '../../../models/entidade.model';
import { EntidadeService } from '../../../services/entidade.service';
import { ReferenciaSnitService } from '../../../services/referencia-snit.service';

type Mock = ReturnType<typeof vi.fn>;

describe('EntidadesComponent', () => {
  let fixture: ComponentFixture<EntidadesComponent>;
  let entidadeServiceMock: Record<
    'listar' | 'criar' | 'atualizar' | 'eliminar' | 'listarContactos' | 'criarContacto' | 'editarContacto',
    Mock
  >;

  const entidadeAtiva: Entidade = {
    id: 1,
    codigo: '1106',
    nipc: '501234567',
    dico: '1106',
    descricaoIncm: 'MUNICIPIO DE LISBOA',
    descricaoSaigt: 'Câmara Municipal de Lisboa',
    tipoEntidade: 'CM',
    telefone: null,
    email: null,
    nome: 'MUNICIPIO DE LISBOA',
    ativo: true,
    atualizadoEm: '2026-08-05T10:00:00Z',
    utilizadorNome: null,
    utilizadorEmail: null,
    utilizadorCargo: null,
    contactoPrincipal: { id: 10, descricao: 'Departamento de Urbanismo', email: 'urbanismo@cm-lisboa.pt', telefone: '912345678' },
  };
  const entidadeInativa: Entidade = {
    ...entidadeAtiva,
    id: 2,
    codigo: '9100',
    nipc: null,
    dico: null,
    descricaoIncm: 'PRESIDENCIA DO CONSELHO DE MINISTROS',
    descricaoSaigt: 'Presidência do Conselho de Ministros',
    tipoEntidade: 'PCM',
    nome: 'PRESIDENCIA DO CONSELHO DE MINISTROS',
    ativo: false,
    atualizadoEm: null,
    contactoPrincipal: null,
  };

  const contactoA: Contacto = {
    id: 10,
    email: 'a@cm-lisboa.pt',
    descricao: 'Urbanismo',
    telefone: '912345678',
    principal: true,
    ativo: true,
    criadoEm: '2026-11-01T10:00:00Z',
  };
  const contactoB: Contacto = { ...contactoA, id: 11, email: 'b@cm-lisboa.pt', descricao: null, telefone: null, principal: false };
  const contactoInativo: Contacto = { ...contactoB, id: 12, email: 'c@cm-lisboa.pt', ativo: false };

  const el = () => fixture.nativeElement as HTMLElement;
  const texto = () => el().textContent ?? '';

  function pagina(entidades: Entidade[]): PageResponse<Entidade> {
    return { content: entidades, page: 0, size: 20, totalElements: entidades.length, totalPages: 1 };
  }

  function configurar(perfilInterno: string | null, entidades: Entidade[] = [entidadeAtiva, entidadeInativa]): void {
    entidadeServiceMock = {
      listar: vi.fn().mockReturnValue(of(pagina(entidades))),
      criar: vi.fn().mockReturnValue(of(entidadeAtiva)),
      atualizar: vi.fn().mockReturnValue(of(entidadeAtiva)),
      eliminar: vi.fn().mockReturnValue(of(undefined)),
      listarContactos: vi.fn().mockReturnValue(of([contactoA, contactoB, contactoInativo])),
      criarContacto: vi.fn().mockReturnValue(of(contactoB)),
      editarContacto: vi.fn().mockReturnValue(of(contactoB)),
    };

    TestBed.configureTestingModule({
      imports: [EntidadesComponent],
      providers: [
        provideRouter([]),
        { provide: EntidadeService, useValue: entidadeServiceMock },
        { provide: ReferenciaSnitService, useValue: { concelhos: vi.fn().mockReturnValue(of([])) } },
        {
          provide: AuthService,
          useValue: {
            perfilInterno: vi.fn().mockReturnValue(perfilInterno),
            nome: vi.fn().mockReturnValue('Ana Teixeira'),
            origem: vi.fn().mockReturnValue('AD_DGT'),
            entidade: vi.fn().mockReturnValue(null),
          },
        },
      ],
    });

    fixture = TestBed.createComponent(EntidadesComponent);
    fixture.detectChanges();
  }

  function linhas(): string[][] {
    return Array.from(el().querySelectorAll('app-cartao app-tabela tbody tr')).map((tr) =>
      Array.from(tr.querySelectorAll('td')).map((td) => (td as HTMLElement).textContent?.replace(/\s+/g, ' ').trim() ?? ''),
    );
  }

  function abrirMenu(indice = 0): HTMLButtonElement[] {
    (el().querySelectorAll<HTMLButtonElement>('.btn-acoes')[indice]).click();
    fixture.detectChanges();
    return Array.from(el().querySelectorAll<HTMLButtonElement>('.dropdown-menu button'));
  }

  function itemMenu(rotulo: string, indice = 0): void {
    abrirMenu(indice)
      .find((botao) => botao.textContent?.trim() === rotulo)!
      .click();
    fixture.detectChanges();
  }

  function clicar(seletor: string, textoBotao?: string): void {
    const botoes = Array.from(el().querySelectorAll<HTMLButtonElement>(seletor));
    const botao = textoBotao ? botoes.find((b) => b.textContent?.trim() === textoBotao) : botoes[0];
    botao!.click();
    fixture.detectChanges();
  }

  afterEach(() => {
    vi.useRealTimers();
  });

  describe('listagem', () => {
    it('mostra o botão primário "Adicionar Entidade" para MASTER', () => {
      configurar('MASTER');
      const botao = el().querySelector('button.adicionar');
      expect(botao?.textContent).toContain('Adicionar Entidade');
      expect(botao?.classList).toContain('botao-primario');
    });

    it('não mostra o botão "Adicionar Entidade" nem o "···" para TECNICO', () => {
      configurar('TECNICO');
      expect(texto()).not.toContain('Adicionar Entidade');
      expect(el().querySelectorAll('.btn-acoes')).toHaveLength(0);
    });

    it('mostra as colunas do mockup', () => {
      configurar('MASTER');
      const cabecalhos = Array.from(el().querySelectorAll('app-cartao app-tabela thead th')).map((th) => th.textContent?.trim());
      expect(cabecalhos).toEqual(['Entidade', 'Contacto Institucional', 'E-mail', 'Última atualização', 'Estado', 'Ações']);
    });

    it('cada linha mostra a descrição SAIGT e «{código} · {tipo}»', () => {
      configurar('MASTER');
      const nomes = Array.from(el().querySelectorAll('.celula-nome')).map((n) => n.textContent?.trim());
      const notas = Array.from(el().querySelectorAll('.celula-nota')).map((n) => n.textContent?.trim());
      expect(nomes).toEqual(['Câmara Municipal de Lisboa', 'Presidência do Conselho de Ministros']);
      expect(notas).toEqual(['1106 · CM', '9100 · PCM']);
      expect(el().querySelector('.selo-sem-codigo')).toBeNull();
    });

    it('sem código ou sem tipo, mostra o selo de aviso «Sem código ou tipo»', () => {
      configurar('MASTER', [{ ...entidadeAtiva, codigo: null }, { ...entidadeInativa, tipoEntidade: null }]);
      const selos = Array.from(el().querySelectorAll('app-selo.selo-sem-codigo'));
      expect(selos).toHaveLength(2);
      expect(selos[0].textContent?.trim()).toBe('Sem código ou tipo');
      expect(selos[0].classList).toContain('selo-aviso');
    });

    it('mostra o telefone e o e-mail do contacto principal, a data e o estado, e "—" quando faltam', () => {
      configurar('MASTER');
      const [primeira, segunda] = linhas();
      expect(primeira.slice(1, 5)).toEqual(['912345678', 'urbanismo@cm-lisboa.pt', '05/08/2026', 'Ativa']);
      expect(segunda.slice(1, 5)).toEqual(['—', '—', '—', 'Inativa']);
    });

    it('pede a listagem com ativo=true ao clicar em "Ativas", e ativo=false ao clicar em "Inativas"', () => {
      configurar('MASTER');
      entidadeServiceMock.listar.mockClear();

      fixture.componentInstance.mudarFiltroEstado('ativas');
      expect(entidadeServiceMock.listar).toHaveBeenLastCalledWith({ ativo: true, nome: undefined, tipoEntidade: undefined }, 0, 20);

      fixture.componentInstance.mudarFiltroEstado('inativas');
      expect(entidadeServiceMock.listar).toHaveBeenLastCalledWith({ ativo: false, nome: undefined, tipoEntidade: undefined }, 0, 20);
    });

    it('o filtro «Tipo de entidade» em PCM pede só as PCM, da primeira página; «Todos os tipos» volta a pedir todas', () => {
      configurar('MASTER');
      fixture.componentInstance.irParaPagina(2);
      entidadeServiceMock.listar.mockClear();
      const select = el().querySelector<HTMLSelectElement>('.filtro-tipo select')!;
      expect(el().querySelector(`label[for="${select.id}"]`)?.textContent?.trim()).toBe('Tipo de entidade');
      expect(select.options[0].textContent?.trim()).toBe('Todos os tipos');

      select.value = 'PCM';
      select.dispatchEvent(new Event('change'));
      expect(entidadeServiceMock.listar).toHaveBeenLastCalledWith({ ativo: undefined, nome: undefined, tipoEntidade: 'PCM' }, 0, 20);

      select.value = '';
      select.dispatchEvent(new Event('change'));
      expect(entidadeServiceMock.listar).toHaveBeenLastCalledWith({ ativo: undefined, nome: undefined, tipoEntidade: undefined }, 0, 20);
    });

    it('pesquisa com debounce — não pede a cada tecla, só depois de parar de escrever', () => {
      vi.useFakeTimers();
      configurar('MASTER');
      entidadeServiceMock.listar.mockClear();
      const entrada = el().querySelector('app-campo-pesquisa input') as HTMLInputElement;

      for (const valor of ['Lis', 'Lisboa']) {
        entrada.value = valor;
        entrada.dispatchEvent(new Event('input'));
      }
      expect(entidadeServiceMock.listar).not.toHaveBeenCalled();

      vi.advanceTimersByTime(300);
      expect(entidadeServiceMock.listar).toHaveBeenCalledTimes(1);
      expect(entidadeServiceMock.listar).toHaveBeenCalledWith({ ativo: undefined, nome: 'Lisboa', tipoEntidade: undefined }, 0, 20);
    });

    it('os filtros de estado dizem qual está ativo com aria-pressed', () => {
      configurar('MASTER');
      const botoes = () => Array.from(el().querySelectorAll<HTMLElement>('.filtros-estado button'));
      expect(botoes().map((b) => b.getAttribute('aria-pressed'))).toEqual(['true', 'false', 'false']);
      botoes()[1].click();
      fixture.detectChanges();
      expect(botoes().map((b) => b.getAttribute('aria-pressed'))).toEqual(['false', 'true', 'false']);
    });

    it('a pesquisa usa o campo partilhado, que mostra o anel de foco', () => {
      configurar('MASTER');
      const ler = (caminho: string) => readFileSync(join(process.cwd(), caminho), 'utf-8');
      expect(el().querySelector('app-campo-pesquisa input[type="search"]')).not.toBeNull();
      expect(ler('src/app/shared/ui/campo-pesquisa/campo-pesquisa.component.css')).toMatch(
        /:host\(:focus-within\)\s*{[^}]*outline: var\(--anel-foco\)/,
      );
      expect(ler('src/app/features/administracao/entidades/entidades.component.css')).not.toMatch(/outline:\s*none/);
    });
  });

  describe('menu «⋯»', () => {
    it('tem nome acessível com a descrição SAIGT e as opções Editar entidade, Contactos e Eliminar', () => {
      configurar('MASTER');
      const botoes = Array.from(el().querySelectorAll<HTMLButtonElement>('.btn-acoes'));
      expect(botoes.map((b) => b.getAttribute('aria-label'))).toEqual([
        'Mais opções para Câmara Municipal de Lisboa',
        'Mais opções para Presidência do Conselho de Ministros',
      ]);
      const itens = abrirMenu();
      expect(botoes[0].getAttribute('aria-expanded')).toBe('true');
      expect(itens.map((b) => b.textContent?.trim())).toEqual(['Editar entidade', 'Contactos', 'Eliminar']);
    });

    it('ao escolher uma opção, o foco passa para o «⋯», para o modal o devolver a ele ao fechar', () => {
      configurar('MASTER');
      document.body.appendChild(fixture.nativeElement);
      itemMenu('Eliminar');
      expect(document.activeElement).toBe(el().querySelector('.btn-acoes'));
      fixture.nativeElement.remove();
    });

    it('fecha ao clicar fora', () => {
      configurar('MASTER');
      abrirMenu();
      document.body.click();
      fixture.detectChanges();
      expect(fixture.componentInstance.menuAbertoId()).toBeNull();
    });
  });

  describe('criar e editar', () => {
    it('«Adicionar Entidade» abre o modal no modo criar', () => {
      configurar('MASTER');
      clicar('button.adicionar');
      expect(fixture.componentInstance.modoModal()).toBe('criar');
      expect(fixture.componentInstance.entidadeEmEdicao()).toBeNull();
      expect(texto()).toContain('Adicionar entidade');
    });

    it('«Editar entidade» abre o modal no modo editar com a entidade da linha', () => {
      configurar('MASTER');
      itemMenu('Editar entidade');
      expect(fixture.componentInstance.menuAbertoId()).toBeNull();
      expect(fixture.componentInstance.modoModal()).toBe('editar');
      expect(fixture.componentInstance.entidadeEmEdicao()).toEqual(entidadeAtiva);
      expect(el().querySelector('app-entidade-modal .modal-titulo')?.textContent?.trim()).toBe('Editar entidade');
    });

    it('depois de gravar, fecha, recarrega e mostra o alerta de sucesso durante 4 s', () => {
      vi.useFakeTimers();
      configurar('MASTER');
      clicar('button.adicionar');
      entidadeServiceMock.listar.mockClear();

      fixture.componentInstance.aoGravarEntidade();
      fixture.detectChanges();

      expect(fixture.componentInstance.entidadeModalAberto()).toBe(false);
      expect(entidadeServiceMock.listar).toHaveBeenCalledTimes(1);
      const alerta = el().querySelector('app-alerta.alerta-sucesso');
      expect(alerta?.textContent).toContain('Entidade criada.');
      expect(alerta?.getAttribute('role')).toBe('status');

      vi.advanceTimersByTime(4000);
      fixture.detectChanges();
      expect(el().querySelector('app-alerta.alerta-sucesso')).toBeNull();
    });
  });

  describe('eliminar', () => {
    it('pede confirmação com a descrição SAIGT e, com 204, recarrega e mostra o sucesso', () => {
      configurar('MASTER');
      itemMenu('Eliminar', 1);
      expect(texto()).toContain(
        'Eliminar a entidade Presidência do Conselho de Ministros? Esta ação não pode ser desfeita.',
      );
      entidadeServiceMock.listar.mockReturnValue(of(pagina([entidadeAtiva])));

      clicar('app-confirmacao-modal .botao-sim');

      expect(entidadeServiceMock.eliminar).toHaveBeenCalledWith(2);
      expect(fixture.componentInstance.entidadeAEliminar()).toBeNull();
      expect(texto()).toContain('Entidade eliminada.');
      expect(linhas()).toHaveLength(1);
    });

    it('com registos (409), mostra o banner com a mensagem e a linha mantém-se', () => {
      configurar('MASTER');
      entidadeServiceMock.eliminar.mockReturnValue(
        throwError(() => new ApiError('ESTADO_INVALIDO', 'A entidade tem registos associados e não pode ser eliminada.', 't', null)),
      );
      itemMenu('Eliminar');
      entidadeServiceMock.listar.mockClear();

      clicar('app-confirmacao-modal .botao-sim');

      expect(el().querySelector('.error-banner')?.textContent).toContain(
        'A entidade tem registos associados e não pode ser eliminada.',
      );
      expect(entidadeServiceMock.listar).not.toHaveBeenCalled();
      expect(linhas()).toHaveLength(2);
      expect(texto()).not.toContain('Entidade eliminada.');
    });

    it('«Não» fecha sem eliminar', () => {
      configurar('MASTER');
      itemMenu('Eliminar');
      clicar('app-confirmacao-modal .botao-nao');
      expect(entidadeServiceMock.eliminar).not.toHaveBeenCalled();
      expect(fixture.componentInstance.entidadeAEliminar()).toBeNull();
    });
  });

  describe('contactos', () => {
    const linhasContactos = () =>
      Array.from(el().querySelectorAll('app-modal[largura="xl"] app-tabela tbody tr')).map((tr) =>
        Array.from(tr.querySelectorAll('td')).map((td) => (td as HTMLElement).textContent?.replace(/\s+/g, ' ').trim() ?? ''),
      );
    const acao = (rotulo: string) => el().querySelector<HTMLButtonElement>(`.acao-contacto[aria-label="${rotulo}"]`);

    it('ao abrir chama o GET e mostra ativos e inativos com os selos', () => {
      configurar('MASTER');
      itemMenu('Contactos');

      expect(entidadeServiceMock.listarContactos).toHaveBeenCalledWith(1);
      expect(el().querySelector('app-modal[largura="xl"] .modal-titulo')?.textContent?.trim()).toBe(
        'Contactos de Câmara Municipal de Lisboa',
      );
      expect(texto()).toContain('retire primeiro o principal atual e depois marque o novo');
      const [a, b, c] = linhasContactos();
      expect(a.slice(0, 5)).toEqual(['a@cm-lisboa.pt', 'Urbanismo', '912345678', 'Principal', 'Ativo']);
      expect(b.slice(0, 5)).toEqual(['b@cm-lisboa.pt', '—', '—', 'Não principal—', 'Ativo']);
      expect(c[4]).toBe('Inativo');
      expect(acao('Retirar principal a a@cm-lisboa.pt')).not.toBeNull();
      expect(acao('Marcar como principal b@cm-lisboa.pt')).not.toBeNull();
      expect(acao('Marcar como principal c@cm-lisboa.pt')).toBeNull();
      expect(acao('Reativar c@cm-lisboa.pt')).not.toBeNull();
    });

    it('trocar o principal faz dois PATCH e recarrega o modal e a lista', () => {
      configurar('MASTER');
      itemMenu('Contactos');
      entidadeServiceMock.listar.mockClear();
      entidadeServiceMock.listarContactos.mockClear();

      acao('Retirar principal a a@cm-lisboa.pt')!.click();
      fixture.detectChanges();
      entidadeServiceMock.listarContactos.mockReturnValue(
        of([{ ...contactoB, principal: true }, { ...contactoA, principal: false }, contactoInativo]),
      );
      acao('Marcar como principal b@cm-lisboa.pt')!.click();
      fixture.detectChanges();

      expect(entidadeServiceMock.editarContacto.mock.calls).toEqual([
        [1, 10, { principal: false }],
        [1, 11, { principal: true }],
      ]);
      expect(entidadeServiceMock.listarContactos).toHaveBeenCalledTimes(2);
      expect(entidadeServiceMock.listar).toHaveBeenCalledTimes(2);
      const principais = linhasContactos().filter((linha) => linha[3] === 'Principal');
      expect(principais.map((linha) => linha[0])).toEqual(['b@cm-lisboa.pt']);
    });

    it('«Desativar» pede confirmação e envia só {ativo: false}; o selo passa a «Inativo»', () => {
      configurar('MASTER');
      itemMenu('Contactos');
      acao('Desativar b@cm-lisboa.pt')!.click();
      fixture.detectChanges();
      expect(texto()).toContain('Desativar o contacto b@cm-lisboa.pt?');
      entidadeServiceMock.listarContactos.mockReturnValue(of([contactoA, { ...contactoB, ativo: false }, contactoInativo]));

      const confirmar = Array.from(el().querySelectorAll<HTMLButtonElement>('app-confirmacao-modal .botao-sim')).find(
        (b) => b.textContent?.trim() === 'Desativar',
      )!;
      confirmar.click();
      fixture.detectChanges();

      expect(entidadeServiceMock.editarContacto).toHaveBeenCalledWith(1, 11, { ativo: false });
      expect(fixture.componentInstance.contactoADesativar()).toBeNull();
      expect(linhasContactos()[1][4]).toBe('Inativo');
    });

    it('um 409 numa ação aparece no modal e a tabela não muda', () => {
      configurar('MASTER');
      itemMenu('Contactos');
      entidadeServiceMock.listarContactos.mockClear();
      entidadeServiceMock.editarContacto.mockReturnValue(
        throwError(() => new ApiError('REFERENCIA_DUPLICADA', 'Contacto duplicado ou principal já definido.', 't', null)),
      );

      acao('Marcar como principal b@cm-lisboa.pt')!.click();
      fixture.detectChanges();

      expect(el().querySelector('app-alerta.alerta-contactos')?.textContent).toContain('Contacto duplicado ou principal já definido.');
      expect(entidadeServiceMock.listarContactos).not.toHaveBeenCalled();
      expect(linhasContactos()[0][3]).toBe('Principal');
    });

    it('depois de uma ação recarrega a lista sem a trocar pelo esqueleto, para o «⋯» manter o foco', () => {
      configurar('MASTER');
      document.body.appendChild(fixture.nativeElement);
      itemMenu('Contactos');
      const botaoMenu = el().querySelector('.btn-acoes');
      acao('Marcar como principal b@cm-lisboa.pt')!.click();
      fixture.detectChanges();

      expect(fixture.componentInstance.loading()).toBe(false);
      expect(el().querySelector('.btn-acoes')).toBe(botaoMenu);
      clicar('app-modal[largura="xl"] .modal-rodape button', 'Fechar');
      expect(document.activeElement).toBe(botaoMenu);
      fixture.nativeElement.remove();
    });

    it('«Adicionar contacto» abre o modal de contacto sem contacto e, ao gravar, recarrega', () => {
      configurar('MASTER');
      itemMenu('Contactos');
      clicar('.adicionar-contacto');
      expect(fixture.componentInstance.contactoModalAberto()).toBe(true);
      expect(fixture.componentInstance.contactoEmEdicao()).toBeNull();
      expect(el().querySelector('app-contacto-modal .modal-titulo')?.textContent?.trim()).toBe('Adicionar contacto');
      entidadeServiceMock.listarContactos.mockClear();

      fixture.componentInstance.aoGravarContacto();
      expect(fixture.componentInstance.contactoModalAberto()).toBe(false);
      expect(entidadeServiceMock.listarContactos).toHaveBeenCalledWith(1);
    });

    it('«Editar» abre o modal de contacto com o contacto da linha', () => {
      configurar('MASTER');
      itemMenu('Contactos');
      acao('Editar b@cm-lisboa.pt')!.click();
      fixture.detectChanges();
      expect(fixture.componentInstance.contactoEmEdicao()).toEqual(contactoB);
      expect(el().querySelector('app-contacto-modal .modal-titulo')?.textContent?.trim()).toBe('Editar contacto');
    });

    it('se a lista de contactos falhar, mostra o erro no modal', () => {
      configurar('MASTER');
      entidadeServiceMock.listarContactos.mockReturnValue(throwError(() => new ApiError('ERRO_INTERNO', 'Erro ao obter os contactos.', 't', null)));
      itemMenu('Contactos');
      expect(el().querySelector('app-alerta.alerta-contactos')?.textContent).toContain('Erro ao obter os contactos.');
      expect(el().querySelector('app-modal[largura="xl"] app-tabela')).toBeNull();
    });
  });
});
