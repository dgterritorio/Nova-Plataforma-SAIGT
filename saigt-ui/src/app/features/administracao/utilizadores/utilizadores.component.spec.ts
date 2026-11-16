import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { Observable, of, throwError } from 'rxjs';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { ESPERA_PESQUISA_ENTIDADE_MS, UtilizadoresComponent } from './utilizadores.component';
import { MATRIZ_PERMISSOES_PERFIS } from './matriz-permissoes-perfis';
import { AuthService } from '../../../core/auth/auth.service';
import { ApiError } from '../../../core/models/error-response.model';
import { PageResponse } from '../../../core/models/page-response.model';
import { Entidade } from '../../../models/entidade.model';
import { Utilizador, UtilizadorFiltro } from '../../../models/utilizador.model';
import { EntidadeService } from '../../../services/entidade.service';
import { UtilizadorService } from '../../../services/utilizador.service';

const ID_SESSAO = 'u-sessao';

const ANA: Utilizador = {
  id: 'u1',
  email: 'ana.ferreira@dgt.pt',
  origemAutenticacao: 'AD_DGT',
  perfilInterno: 'TECNICO',
  nome: 'Ana Ferreira',
  cargo: null,
  entidades: [],
  ultimoLoginEm: '2026-09-10T08:00:00Z',
};

const RUI_SEM_EMAIL: Utilizador = {
  id: 'u3',
  email: null,
  origemAutenticacao: 'AD_DGT',
  perfilInterno: 'VISITANTE',
  nome: 'rui.costa',
  cargo: null,
  entidades: [],
  ultimoLoginEm: null,
};

const MIGUEL: Utilizador = {
  id: 'u2',
  email: 'm.santos@cm-sintra.pt',
  origemAutenticacao: 'INCM',
  perfilInterno: null,
  nome: 'Miguel Santos',
  cargo: 'Técnico superior',
  entidades: [{ entidadeId: 11, entidadeNome: 'Câmara Municipal de Sintra', responsavel: true }],
  ultimoLoginEm: '2026-09-20T09:00:00Z',
};

const CONTAGENS = { MASTER: 2, TECNICO: 18, VISITANTE: 31 } as const;

function pagina<T>(conteudo: T[], total = conteudo.length): PageResponse<T> {
  return { content: conteudo, page: 0, size: 20, totalElements: total, totalPages: 1 };
}

describe('UtilizadoresComponent', () => {
  let fixture: ComponentFixture<UtilizadoresComponent>;
  let utilizadorService: {
    listar: ReturnType<typeof vi.fn>;
    revogarSessoes: ReturnType<typeof vi.fn>;
    alterarPerfilInterno: ReturnType<typeof vi.fn>;
  };
  let entidadeService: { listar: ReturnType<typeof vi.fn> };
  let authService: Record<string, ReturnType<typeof vi.fn>>;
  let rosterMiguel: Utilizador;

  const el = (): HTMLElement => fixture.nativeElement as HTMLElement;
  const texto = (seletor: string): string[] =>
    [...el().querySelectorAll(seletor)].map((no) => (no.textContent ?? '').replace(/\s+/g, ' ').trim());

  /** Resposta da API por filtro: contagens por perfil, lista DGT, lista INCM e o roster. */
  function respostaListar(filtro: UtilizadorFiltro): Observable<PageResponse<Utilizador>> {
    if (filtro.perfilInterno) {
      return of(pagina<Utilizador>([], CONTAGENS[filtro.perfilInterno]));
    }
    if (filtro.origemAutenticacao === 'AD_DGT') {
      return of(pagina([ANA, RUI_SEM_EMAIL], 4));
    }
    if (filtro.entidadeId) {
      return of(pagina([rosterMiguel]));
    }
    return of(pagina([MIGUEL], 7));
  }

  function configurar(): void {
    rosterMiguel = { ...MIGUEL, ultimaAtuacaoEm: '2026-09-10T10:00:00Z' };
    utilizadorService = {
      listar: vi.fn((filtro: UtilizadorFiltro) => respostaListar(filtro)),
      revogarSessoes: vi.fn().mockReturnValue(of({ sessoesRevogadas: 3 })),
      alterarPerfilInterno: vi.fn().mockReturnValue(of({ ...ANA, perfilInterno: 'MASTER', ultimoLoginEm: null })),
    };
    const entidade = { id: 11, descricaoSaigt: 'Câmara Municipal de Sintra', descricaoIncm: 'CM SINTRA' } as Entidade;
    entidadeService = { listar: vi.fn().mockReturnValue(of(pagina([entidade]))) };
    authService = {
      perfilInterno: vi.fn().mockReturnValue('MASTER'),
      utilizadorId: vi.fn().mockReturnValue(ID_SESSAO),
      nome: vi.fn().mockReturnValue('Ana Teixeira'),
      origem: vi.fn().mockReturnValue('AD_DGT'),
      entidade: vi.fn().mockReturnValue(null),
      email: vi.fn().mockReturnValue(null),
      logout: vi.fn().mockReturnValue(of(undefined)),
    };

    TestBed.configureTestingModule({
      imports: [UtilizadoresComponent],
      providers: [
        provideRouter([]),
        { provide: UtilizadorService, useValue: utilizadorService },
        { provide: EntidadeService, useValue: entidadeService },
        { provide: AuthService, useValue: authService },
      ],
    });
    fixture = TestBed.createComponent(UtilizadoresComponent);
    fixture.detectChanges();
  }

  function abrirSeparador(etiqueta: string): void {
    const separador = [...el().querySelectorAll<HTMLButtonElement>('[role="tab"]')].find((s) => s.textContent?.includes(etiqueta))!;
    separador.click();
    fixture.detectChanges();
  }

  function abrirMenu(indice: number): string[] {
    el().querySelectorAll<HTMLButtonElement>('.btn-acoes')[indice].click();
    fixture.detectChanges();
    return texto('.dropdown-menu button');
  }

  function escolherNoMenu(indice: number, opcao: string): void {
    abrirMenu(indice);
    [...el().querySelectorAll<HTMLButtonElement>('.dropdown-menu button')].find((b) => b.textContent?.trim() === opcao)!.click();
    fixture.detectChanges();
  }

  /** O jsdom não submete o formulário pelo clique no botão (sem `requestSubmit`): dispara-se o `submit`. */
  function aplicar(): void {
    const botao = el().querySelector<HTMLButtonElement>('.botao-aplicar')!;
    expect(botao.type).toBe('submit');
    expect(botao.disabled).toBe(false);
    el().querySelector('form.filtros-entidade')!.dispatchEvent(new Event('submit'));
    fixture.detectChanges();
  }

  afterEach(() => vi.useRealTimers());

  describe('abertura', () => {
    it('mostra os três separadores com os contadores e o total registado, sem «Convidar» nem «Guardar alterações»', () => {
      configurar();

      expect(texto('[role="tab"]')).toEqual(['Perfis e permissões', 'Utilizadores DGT 4', 'Utilizadores INCM 7']);
      expect(el().querySelector('.subtitulo')?.textContent?.trim()).toBe('11 utilizadores registados');
      expect(el().textContent).not.toContain('Convidar');
      expect(el().textContent).not.toContain('Guardar alterações');
      expect(el().querySelector('[role="tabpanel"]')?.getAttribute('aria-labelledby')).toBe(
        el().querySelector('[role="tab"][aria-selected="true"]')?.id,
      );
    });

    it('pede a lista DGT, a lista INCM e a contagem de cada perfil DGT com size=1', () => {
      configurar();

      expect(utilizadorService.listar).toHaveBeenCalledWith({ origemAutenticacao: 'AD_DGT' }, 0, 20);
      expect(utilizadorService.listar).toHaveBeenCalledWith({ origemAutenticacao: 'INCM' }, 0, 20);
      for (const perfil of ['MASTER', 'TECNICO', 'VISITANTE']) {
        expect(utilizadorService.listar).toHaveBeenCalledWith({ origemAutenticacao: 'AD_DGT', perfilInterno: perfil }, 0, 1);
      }
    });
  });

  describe('Perfis e permissões', () => {
    it('mostra os três perfis com as contagens da API e o Master escolhido', () => {
      configurar();
      abrirSeparador('Perfis e permissões');

      expect(texto('.perfil .perfil-nome')).toEqual(['Master', 'Técnico', 'Visitante']);
      expect(texto('.perfil .perfil-contador')).toEqual(['2 utilizadores', '18 utilizadores', '31 utilizadores']);
      expect([...el().querySelectorAll('.perfil')].map((b) => b.getAttribute('aria-pressed'))).toEqual(['true', 'false', 'false']);
      expect(el().querySelector('.permissoes-titulo')?.textContent?.trim()).toBe('Master');
      expect(el().querySelector('.permissoes-contador-numero')?.textContent?.trim()).toBe('2');
      expect(el().textContent).toContain('a autorização é sempre verificada pelo servidor');
    });

    it('a matriz segue a EF v1.5 em cada perfil, com os grupos do Figma', () => {
      const esperado: Record<string, [string, string, string]> = {
        'Consultar qualquer processo': ['Permitido', 'Permitido', 'Permitido'],
        'Pesquisar, filtrar e exportar processos': ['Permitido', 'Permitido', 'Permitido'],
        'Criar / continuar / corrigir processo': ['Sem acesso', 'Sem acesso', 'Sem acesso'],
        'Validar processo': ['Permitido', 'Permitido', 'Sem acesso'],
        'Enviar PCM': ['Permitido', 'Permitido', 'Sem acesso'],
        'Enviar INCM para publicação': ['Permitido', 'Permitido', 'Sem acesso'],
        'Aceder ao Centro de Notificações': ['Permitido', 'Sem acesso', 'Sem acesso'],
        'Criar notícias gerais': ['Permitido', 'Sem acesso', 'Sem acesso'],
        'Criar pedidos de resposta obrigatória no processo': ['Permitido', 'Sem acesso', 'Sem acesso'],
        'Responder a pedidos de apoio e de regularização': ['Permitido', 'Sem acesso', 'Sem acesso'],
        'Receber alertas de sistema e de erros de integração': ['Permitido', 'Sem acesso', 'Sem acesso'],
        'Aceder ao BackOffice': ['Permitido', 'Parcial', 'Sem acesso'],
        'Análises e Relatórios (KPIs)': ['Permitido', 'Sem acesso', 'Sem acesso'],
        'Listar e pesquisar processos no BackOffice': ['Permitido', 'Permitido', 'Sem acesso'],
        'Ver estatísticas e histórico': ['Permitido', 'Permitido', 'Sem acesso'],
        'Alterar owner do processo': ['Permitido', 'Sem acesso', 'Sem acesso'],
        'Alterar estado do processo': ['Permitido', 'Sem acesso', 'Sem acesso'],
        'Alterar IDUS': ['Permitido', 'Sem acesso', 'Sem acesso'],
        'Anular processo (motivo obrigatório)': ['Permitido', 'Sem acesso', 'Sem acesso'],
        'Consultar e atualizar Plantas SAIGT/SNIT': ['Permitido', 'Permitido', 'Sem acesso'],
        'Configurar workflows e precedências': ['Permitido', 'Sem acesso', 'Sem acesso'],
        'Gerir utilizadores INCM/DGT e atribuir perfil DGT': ['Permitido', 'Sem acesso', 'Sem acesso'],
        'Gerir entidades (criar entidade em falta e atualizar)': ['Permitido', 'Sem acesso', 'Sem acesso'],
        'Gerir listas de valores': ['Permitido', 'Sem acesso', 'Sem acesso'],
        'Editar dados pessoais (email, telefone e morada; nome não)': ['Permitido', 'Permitido', 'Permitido'],
      };
      configurar();
      abrirSeparador('Perfis e permissões');

      expect(texto('.grupo-titulo')).toEqual(['Processos', 'Validação', 'Centro de Notificações', 'BackOffice', 'Administração', 'Conta']);
      expect(MATRIZ_PERMISSOES_PERFIS.flatMap((g) => g.permissoes.map((p) => p.nome))).toEqual(Object.keys(esperado));
      [...el().querySelectorAll<HTMLButtonElement>('.perfil')].forEach((botao, indice) => {
        botao.click();
        fixture.detectChanges();
        const linhas = [...el().querySelectorAll('.permissao')].map((linha) => [
          linha.querySelector('.permissao-nome')?.textContent?.trim() ?? '',
          linha.querySelector('app-selo')?.textContent?.trim() ?? '',
        ]);
        expect(linhas).toEqual(Object.entries(esperado).map(([nome, acessos]) => [nome, acessos[indice]]));
      });
    });

    it('no Técnico, «Aceder ao BackOffice» é «Parcial» com a nota, e o botão fica premido', () => {
      configurar();
      abrirSeparador('Perfis e permissões');

      el().querySelectorAll<HTMLButtonElement>('.perfil')[1].click();
      fixture.detectChanges();

      const linha = [...el().querySelectorAll('.permissao')].find((l) => l.textContent?.includes('Aceder ao BackOffice'))!;
      expect(linha.querySelector('app-selo')?.textContent?.trim()).toBe('Parcial');
      expect(linha.querySelector('app-selo')?.classList).toContain('acesso-parcial');
      expect(linha.querySelector('.permissao-nota')?.textContent?.trim()).toBe('Master: acesso total; Técnico: acesso parcial.');
      expect(el().querySelectorAll('.perfil')[1].getAttribute('aria-pressed')).toBe('true');
      expect(el().querySelector('.permissoes-titulo')?.textContent?.trim()).toBe('Técnico');
      expect(el().querySelector('.permissoes-contador-numero')?.textContent?.trim()).toBe('18');
    });
  });

  describe('Utilizadores DGT', () => {
    it('cada linha tem iniciais, nome, email, selo do perfil e último acesso', () => {
      configurar();

      expect(texto('.lista-dgt .avatar')).toEqual(['AF', 'RC']);
      expect(texto('.lista-dgt .nome')).toEqual(['Ana Ferreira', 'rui.costa']);
      expect(texto('.lista-dgt app-selo.papel')).toEqual(['Técnico', 'Visitante']);
      expect(texto('.lista-dgt .ultimo-acesso')).toEqual(['Último acesso: 10/09/2026', 'Último acesso: —']);
      expect(texto('.lista-dgt .email')[0]).toBe('ana.ferreira@dgt.pt');
    });

    it('sem email mostra «—» e diz «Sem email» ao leitor de ecrã', () => {
      configurar();

      const email = el().querySelectorAll('.lista-dgt .email')[1];
      expect(email.querySelector('[aria-hidden="true"]')?.textContent).toBe('—');
      expect(email.querySelector('.visualmente-oculto')?.textContent).toBe('Sem email');
    });

    it('o menu «⋯» tem nome acessível e só «Alterar perfil» e «Terminar sessões»', () => {
      configurar();

      expect([...el().querySelectorAll('.btn-acoes')].map((b) => b.getAttribute('aria-label'))).toEqual([
        'Mais opções para Ana Ferreira',
        'Mais opções para rui.costa',
      ]);
      expect(abrirMenu(0)).toEqual(['Alterar perfil', 'Terminar sessões']);
      expect(el().querySelector('.btn-acoes')?.getAttribute('aria-expanded')).toBe('true');
    });

    it('filtra pela pesquisa local sem novo pedido', () => {
      configurar();
      const pedidos = utilizadorService.listar.mock.calls.length;

      fixture.componentInstance.dgt.pesquisa.set('rui');
      fixture.detectChanges();

      expect(texto('.lista-dgt .nome')).toEqual(['rui.costa']);
      expect(utilizadorService.listar.mock.calls.length).toBe(pedidos);
    });
  });

  describe('Alterar perfil', () => {
    it('envia só o perfil, sem motivo, atualiza a linha e os contadores e mostra o alerta durante 4 s', () => {
      vi.useFakeTimers();
      configurar();
      escolherNoMenu(0, 'Alterar perfil');
      expect(el().querySelector('app-alterar-perfil-modal [role="dialog"]')).not.toBeNull();
      expect(el().querySelector('app-alterar-perfil-modal textarea')).toBeNull();
      utilizadorService.listar.mockClear();

      fixture.componentInstance.confirmarAlterarPerfil({ perfilInterno: 'MASTER' });
      fixture.detectChanges();

      expect(utilizadorService.alterarPerfilInterno).toHaveBeenCalledWith('u1', { perfilInterno: 'MASTER' });
      expect(Object.keys(utilizadorService.alterarPerfilInterno.mock.calls[0][1])).toEqual(['perfilInterno']);
      expect(texto('.lista-dgt app-selo.papel')).toEqual(['Master', 'Visitante']);
      expect(texto('.lista-dgt .ultimo-acesso')[0]).toBe('Último acesso: 10/09/2026');
      expect(utilizadorService.listar).toHaveBeenCalledTimes(3);
      expect(utilizadorService.listar.mock.calls.every(([filtro]) => (filtro as UtilizadorFiltro).perfilInterno)).toBe(true);
      expect(el().querySelector('app-alterar-perfil-modal [role="dialog"]')).toBeNull();
      expect(el().querySelector('.alerta-sucesso')?.textContent?.trim()).toBe('Perfil de Ana Ferreira alterado para Master.');

      vi.advanceTimersByTime(4000);
      fixture.detectChanges();
      expect(el().querySelector('.alerta-sucesso')).toBeNull();
    });

    it('com 409 do último Master, mostra o banner e a linha não muda', () => {
      configurar();
      utilizadorService.alterarPerfilInterno.mockReturnValue(
        throwError(() => new ApiError('ESTADO_INVALIDO', 'Operação deixaria a plataforma sem administrador.', 't-1', null)),
      );
      escolherNoMenu(0, 'Alterar perfil');

      fixture.componentInstance.confirmarAlterarPerfil({ perfilInterno: 'VISITANTE' });
      fixture.detectChanges();

      expect(el().querySelector('.erro-acao [role="alert"]')?.textContent?.trim()).toBe(
        'Operação deixaria a plataforma sem administrador.',
      );
      expect(texto('.lista-dgt app-selo.papel')).toEqual(['Técnico', 'Visitante']);
      expect(el().querySelector('.alerta-sucesso')).toBeNull();
    });

    it('o banner do erro sai ao mudar de separador', () => {
      configurar();
      utilizadorService.alterarPerfilInterno.mockReturnValue(
        throwError(() => new ApiError('ESTADO_INVALIDO', 'Operação deixaria a plataforma sem administrador.', 't-1', null)),
      );
      escolherNoMenu(0, 'Alterar perfil');
      fixture.componentInstance.confirmarAlterarPerfil({ perfilInterno: 'VISITANTE' });
      fixture.detectChanges();
      expect(el().querySelector('.erro-acao [role="alert"]')).not.toBeNull();

      abrirSeparador('Utilizadores INCM');

      expect(el().querySelector('.erro-acao [role="alert"]')).toBeNull();
    });

    it('alterar o próprio perfil termina a sessão e vai para o login', () => {
      configurar();
      const router = TestBed.inject(Router);
      const navegar = vi.spyOn(router, 'navigate').mockResolvedValue(true);
      fixture.componentInstance.selecionarAlterarPerfil({ ...ANA, id: ID_SESSAO }, document.createElement('button'));

      fixture.componentInstance.confirmarAlterarPerfil({ perfilInterno: 'TECNICO' });

      expect(authService['logout']).toHaveBeenCalled();
      expect(navegar).toHaveBeenCalledWith(['/login'], { queryParams: { erro: 'PERFIL_ALTERADO' } });
    });
  });

  describe('Terminar sessões', () => {
    it('pede confirmação, envia sem corpo e mostra o número de sessões terminadas', () => {
      configurar();
      escolherNoMenu(0, 'Terminar sessões');
      expect(el().querySelector('app-confirmacao-modal .mensagem')?.textContent).toContain('Terminar as sessões ativas de Ana Ferreira?');
      expect(el().querySelector('app-confirmacao-modal')?.textContent).not.toContain('Motivo');

      el().querySelector<HTMLButtonElement>('app-confirmacao-modal .botao-sim')!.click();
      fixture.detectChanges();

      expect(utilizadorService.revogarSessoes).toHaveBeenCalledWith('u1');
      expect(utilizadorService.revogarSessoes.mock.calls[0]).toHaveLength(1);
      expect(el().querySelector('.alerta-sucesso')?.textContent?.trim()).toBe('3 sessões terminadas.');
      expect(el().querySelector('app-confirmacao-modal [role="dialog"]')).toBeNull();
    });

    it('com uma sessão, diz «1 sessão terminada.»', () => {
      configurar();
      utilizadorService.revogarSessoes.mockReturnValue(of({ sessoesRevogadas: 1 }));
      escolherNoMenu(1, 'Terminar sessões');

      fixture.componentInstance.confirmarTerminarSessoes();
      fixture.detectChanges();

      expect(el().querySelector('.alerta-sucesso')?.textContent?.trim()).toBe('1 sessão terminada.');
    });
  });

  describe('Utilizadores INCM', () => {
    it('mostra nome, email, cargo, entidade e último acesso, e o menu só com «Terminar sessões»', () => {
      configurar();
      abrirSeparador('Utilizadores INCM');

      expect(texto('.lista-incm .nome')).toEqual(['Miguel Santos']);
      expect(texto('.lista-incm .cargo')).toEqual(['Técnico superior']);
      expect(texto('.lista-incm .entidade-nome')).toEqual(['Câmara Municipal de Sintra']);
      expect(texto('.lista-incm .ultimo-acesso')).toEqual(['Último acesso: 20/09/2026']);
      expect(el().querySelector('.lista-incm .ultima-atuacao')).toBeNull();
      expect(abrirMenu(0)).toEqual(['Terminar sessões']);
    });

    it('a pesquisa de entidade só faz um pedido 400 ms depois de parar de escrever', () => {
      vi.useFakeTimers();
      configurar();
      abrirSeparador('Utilizadores INCM');
      const campo = el().querySelector<HTMLInputElement>('.filtro-pesquisa-entidade input')!;

      for (const termo of ['S', 'Si', 'Sin', 'Sintra']) {
        campo.value = termo;
        campo.dispatchEvent(new Event('input'));
        vi.advanceTimersByTime(200);
      }
      vi.advanceTimersByTime(ESPERA_PESQUISA_ENTIDADE_MS - 201);
      expect(entidadeService.listar).not.toHaveBeenCalled();

      vi.advanceTimersByTime(1);
      fixture.detectChanges();

      expect(entidadeService.listar).toHaveBeenCalledTimes(1);
      expect(entidadeService.listar).toHaveBeenCalledWith({ nome: 'Sintra' }, 0, 20);
      expect(texto('.filtro-entidade option:not([disabled])')).toEqual(['Câmara Municipal de Sintra']);
    });

    it('com a entidade chama com entidadeId; com «Atuou desde» chama com atuouDesde e mostra a última atuação; «Limpar» repõe', () => {
      vi.useFakeTimers();
      configurar();
      abrirSeparador('Utilizadores INCM');
      expect(el().querySelector<HTMLButtonElement>('.botao-aplicar')!.disabled).toBe(true);
      const campo = el().querySelector<HTMLInputElement>('.filtro-pesquisa-entidade input')!;
      campo.value = 'Sintra';
      campo.dispatchEvent(new Event('input'));
      vi.advanceTimersByTime(ESPERA_PESQUISA_ENTIDADE_MS);
      fixture.detectChanges();
      const seletor = el().querySelector<HTMLSelectElement>('.filtro-entidade select')!;
      seletor.value = '11';
      seletor.dispatchEvent(new Event('change'));
      fixture.detectChanges();

      aplicar();
      fixture.detectChanges();
      expect(utilizadorService.listar).toHaveBeenLastCalledWith({ origemAutenticacao: 'INCM', entidadeId: 11 }, 0, 20);
      expect(el().querySelector('.filtro-resumo')?.textContent?.trim()).toBe('Utilizadores com vínculo à entidade.');
      expect(el().querySelector('.lista-incm .ultima-atuacao')).toBeNull();

      const data = el().querySelector<HTMLInputElement>('.filtro-data input')!;
      data.value = '2026-09-01';
      data.dispatchEvent(new Event('input'));
      aplicar();
      fixture.detectChanges();
      expect(utilizadorService.listar).toHaveBeenLastCalledWith(
        { origemAutenticacao: 'INCM', entidadeId: 11, atuouDesde: '2026-09-01' },
        0,
        20,
      );
      expect(el().querySelector('.filtro-resumo')?.textContent?.trim()).toBe('Quem atuou pela entidade desde 01/09/2026.');
      expect(texto('.lista-incm .ultima-atuacao')).toEqual(['Última atuação nesta entidade: 10/09/2026']);
      expect(texto('[role="tab"]')[2]).toBe('Utilizadores INCM 7');

      el().querySelector<HTMLButtonElement>('.botao-limpar')!.click();
      fixture.detectChanges();
      expect(utilizadorService.listar).toHaveBeenLastCalledWith({ origemAutenticacao: 'INCM' }, 0, 20);
      expect(el().querySelector('.filtro-resumo')).toBeNull();
      expect(el().querySelector('.lista-incm .ultima-atuacao')).toBeNull();
      expect(el().querySelector<HTMLInputElement>('.filtro-pesquisa-entidade input')!.value).toBe('');
      expect(el().querySelector<HTMLInputElement>('.filtro-data input')!.value).toBe('');
      expect(el().querySelector<HTMLButtonElement>('.botao-aplicar')!.disabled).toBe(true);
    });
  });
});
